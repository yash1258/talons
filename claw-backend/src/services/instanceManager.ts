import { prisma } from '../db/prisma.js';
import { docker, OPENCLAW_IMAGE, getAvailablePort } from './docker.js';
import { generateConfig, writeConfigToContainer } from './configGenerator.js';
import type { Instance } from '@prisma/client';
import { randomBytes } from 'crypto';

export async function createInstance(userId: string): Promise<Instance> {
  const port = await getAvailablePort();
  const instanceId = randomBytes(8).toString('hex');
  const containerName = `claw-${instanceId}`;
  const gatewayToken = randomBytes(32).toString('hex');

  const user = await prisma.user.findUnique({ where: { id: userId } });
  const subscription = user?.subscription || 'FREE';

  // Generate initial OpenClaw config with gateway auth token
  const initialConfig = {
    model: subscription === 'FREE' ? 'openrouter/free' : 'anthropic/claude-sonnet-4-20250514',
    channels: {},
  };
  const openclawConfig = generateConfig(subscription, initialConfig);

  // Add gateway auth settings to the config
  const fullConfig: Record<string, any> = {
    ...openclawConfig as Record<string, any>,
    gateway: {
      auth: {
        token: gatewayToken,
      },
    },
  };

  const container = await docker.createContainer({
    name: containerName,
    Image: OPENCLAW_IMAGE,
    Cmd: ['openclaw', '--yes'],
    Env: [
      `OPENCLAW_PROFILE=${instanceId}`,
      `PORT=18789`,
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

  // Start container briefly to initialize the volume, then write config
  await container.start();

  // Wait a moment for filesystem to be ready
  await new Promise(resolve => setTimeout(resolve, 2000));

  // Write the openclaw.json config into the container
  const configJson = JSON.stringify(fullConfig, null, 2);
  const exec = await container.exec({
    Cmd: ['sh', '-c', `mkdir -p /home/node/.openclaw && echo '${configJson.replace(/'/g, "'\\''")}' > /home/node/.openclaw/openclaw.json`],
    AttachStdout: true,
    AttachStderr: true,
    User: 'node',
  });
  await exec.start({});

  // Restart so openclaw daemon picks up the config
  await container.restart();

  const instance = await prisma.instance.create({
    data: {
      userId,
      containerId: container.id,
      dockerPort: port,
      status: 'RUNNING',
      openclawVersion: 'latest',
      configJson: JSON.stringify(initialConfig),
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
