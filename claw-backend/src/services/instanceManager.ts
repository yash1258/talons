import { prisma } from '../db/prisma.js';
import { docker, OPENCLAW_IMAGE, getUsedDockerPorts, BASE_PORT, MAX_CONTAINERS } from './docker.js';
import type { Instance } from '@prisma/client';
import { randomBytes } from 'crypto';

/**
 * Finds a port that is not used by any Docker container OR any active instance in the DB.
 */
async function getAvailablePort(): Promise<number> {
  const usedDockerPorts = await getUsedDockerPorts();
  const activeInstances = await prisma.instance.findMany({
    where: { status: { not: 'DELETED' } },
    select: { dockerPort: true },
  });
  const usedDbPorts = new Set(activeInstances.map(i => i.dockerPort));

  for (let port = BASE_PORT; port < BASE_PORT + MAX_CONTAINERS; port++) {
    if (!usedDockerPorts.has(port) && !usedDbPorts.has(port)) {
      return port;
    }
  }
  throw new Error('No available ports');
}

/**
 * Resolves the --auth-choice and --<provider>-api-key flags for openclaw onboard.
 */
function resolveAuthFlags(provider?: string, apiKey?: string): string {
  if (!apiKey) return '--auth-choice skip';

  const providerMap: Record<string, { choice: string; flag: string }> = {
    'openrouter': { choice: 'openrouter-api-key', flag: '--openrouter-api-key' },
    'anthropic': { choice: 'apiKey', flag: '--anthropic-api-key' },
    'openai': { choice: 'openai-api-key', flag: '--openai-api-key' },
    'gemini': { choice: 'gemini-api-key', flag: '--gemini-api-key' },
  };

  const entry = providerMap[provider || 'openrouter'] || providerMap['openrouter'];
  return `--auth-choice ${entry.choice} ${entry.flag} "${apiKey}"`;
}

export interface CreateInstanceOptions {
  provider?: string;   // e.g. 'openrouter', 'anthropic', 'openai'
  apiKey?: string;     // user's API key (BYOK) or empty for free tier
  model?: string;      // e.g. 'anthropic/claude-sonnet-4-20250514'
}

export async function createInstance(userId: string, opts: CreateInstanceOptions = {}): Promise<Instance> {
  const port = await getAvailablePort();
  const instanceId = randomBytes(8).toString('hex');
  const containerName = `claw-${instanceId}`;
  const gatewayToken = randomBytes(32).toString('hex');

  const user = await prisma.user.findUnique({ where: { id: userId } });
  const subscription = user?.subscription || 'FREE';

  // For free tier, use the pooled OpenRouter key
  const OPENROUTER_FREE_KEY = process.env.OPENROUTER_FREE_KEY || '';
  const isFree = subscription === 'FREE';
  const provider = opts.provider || 'openrouter';
  const apiKey = isFree ? OPENROUTER_FREE_KEY : (opts.apiKey || '');
  const authFlags = resolveAuthFlags(provider, apiKey);

  const OPENCLAW_BIN = '/home/node/.npm-global/bin/openclaw';

  // Build the onboarding command using absolute path to avoid PATH issues
  const onboardCmd = [
    `${OPENCLAW_BIN} onboard`,
    '--non-interactive',
    '--accept-risk',
    authFlags,
    `--gateway-token "${gatewayToken}"`,
    '--gateway-bind lan',
    '--gateway-port 18789',
    '--skip-daemon',
    '--skip-health',
    '--skip-ui',
    '--skip-skills',
    '--json',
  ].join(' ');

  // Entrypoint script: onboard if not yet done, then start daemon using 'gateway run'
  const entrypoint = [
    'sh', '-c',
    `if [ ! -f "/home/node/.openclaw/openclaw.json" ]; then ${onboardCmd} || echo "Onboard exited with $?"; fi && exec ${OPENCLAW_BIN} gateway run --bind lan`,
  ];

  const container = await docker.createContainer({
    name: containerName,
    Image: OPENCLAW_IMAGE,
    Cmd: entrypoint,
    Env: [
      `OPENCLAW_PROFILE=${instanceId}`,
      `OPENCLAW_GATEWAY_TOKEN=${gatewayToken}`,
      `OPENCLAW_SKIP_BROWSER_CONTROL_SERVER=1`,
      `OPENCLAW_SKIP_CANVAS_HOST=1`,
      `OPENCLAW_SKIP_CRON=1`,
    ],
    HostConfig: {
      PortBindings: {
        '18789/tcp': [{ HostPort: port.toString() }],
      },
      Binds: [
        `claw-${instanceId}-data:/home/node/.openclaw`,
      ],
      Memory: 2 * 1024 * 1024 * 1024,
      NanoCpus: 2 * 1e9,
      RestartPolicy: { Name: 'unless-stopped' },
    },
    Labels: {
      'claw.instance': instanceId,
      'claw.user': userId,
    },
  });

  await container.start();

  const instance = await prisma.instance.create({
    data: {
      userId,
      containerId: container.id,
      dockerPort: port,
      status: 'RUNNING',
      openclawVersion: 'latest',
      configJson: JSON.stringify({ provider, model: opts.model }),
      gatewayToken,
    },
  });

  return instance;
}

export async function getInstanceStatus(instance: Instance): Promise<{
  running: boolean;
  containerStatus: string;
  uptime?: number;
}> {
  if (!instance.containerId) {
    return { running: false, containerStatus: 'no-container' };
  }

  try {
    const container = docker.getContainer(instance.containerId);
    const info = await container.inspect();

    return {
      running: info.State.Running,
      containerStatus: info.State.Status,
      uptime: info.State.StartedAt ? Date.now() - new Date(info.State.StartedAt).getTime() : undefined,
    };
  } catch {
    return { running: false, containerStatus: 'not-found' };
  }
}

export async function startInstance(instance: Instance): Promise<Instance> {
  if (!instance.containerId) {
    throw new Error('No container attached');
  }

  const container = docker.getContainer(instance.containerId);
  await container.start();

  await prisma.instance.update({
    where: { id: instance.id },
    data: { status: 'RUNNING' },
  });

  return prisma.instance.findUnique({ where: { id: instance.id } }) as Promise<Instance>;
}

export async function stopInstance(instance: Instance): Promise<Instance> {
  if (!instance.containerId) {
    throw new Error('No container attached');
  }

  const container = docker.getContainer(instance.containerId);
  await container.stop();

  await prisma.instance.update({
    where: { id: instance.id },
    data: { status: 'STOPPED' },
  });

  return prisma.instance.findUnique({ where: { id: instance.id } }) as Promise<Instance>;
}

export async function deleteInstance(instance: Instance): Promise<void> {
  if (instance.containerId) {
    try {
      const container = docker.getContainer(instance.containerId);
      await container.stop();
      await container.remove();
    } catch (error) {
      console.error('Failed to remove container:', error);
    }
  }

  await prisma.instance.update({
    where: { id: instance.id },
    data: { status: 'DELETED' },
  });
}
