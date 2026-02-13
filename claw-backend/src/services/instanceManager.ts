import { prisma } from '../db/prisma.js';
import { docker, OPENCLAW_IMAGE, getAvailablePort } from './docker.js';
import { generateConfig, writeConfigToContainer } from './configGenerator.js';
import type { Instance } from '@prisma/client';
import { randomBytes } from 'crypto';

export async function createInstance(userId: string): Promise<Instance> {
  const port = await getAvailablePort();
  const instanceId = randomBytes(8).toString('hex');
  const containerName = `claw-${instanceId}`;

  const user = await prisma.user.findUnique({ where: { id: userId } });
  const subscription = user?.subscription || 'FREE';

  // Generate initial config based on subscription tier
  const initialConfig = {
    model: subscription === 'FREE' ? 'openrouter/free' : 'anthropic/claude-sonnet-4-20250514',
    channels: {},
  };
  const openclawConfig = generateConfig(subscription, initialConfig);

  const container = await docker.createContainer({
    name: containerName,
    Image: OPENCLAW_IMAGE,
    Env: [
      `OPENCLAW_PROFILE=${instanceId}`,
      `PORT=${port}`,
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

  const instance = await prisma.instance.create({
    data: {
      userId,
      containerId: container.id,
      dockerPort: port,
      status: 'STARTING',
      openclawVersion: 'latest',
    },
  });

  await container.start();

  await prisma.instance.update({
    where: { id: instance.id },
    data: { status: 'RUNNING' },
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
