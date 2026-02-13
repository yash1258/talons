import Docker from 'dockerode';

const DOCKER_HOST = process.env.DOCKER_HOST || undefined;

export const docker = DOCKER_HOST 
  ? new Docker({ host: DOCKER_HOST, port: 2375 })
  : new Docker();

export const OPENCLAW_IMAGE = process.env.OPENCLAW_IMAGE || 'openclaw/openclaw:latest';
export const BASE_PORT = parseInt(process.env.BASE_PORT || '20000');
export const MAX_CONTAINERS = parseInt(process.env.MAX_CONTAINERS || '10');

export async function pullLatestImage(): Promise<void> {
  try {
    console.log(`Pulling latest OpenClaw image: ${OPENCLAW_IMAGE}`);
    await docker.pull(OPENCLAW_IMAGE);
    console.log('Image pulled successfully');
  } catch (error) {
    console.error('Failed to pull image:', error);
    throw error;
  }
}

export async function getAvailablePort(): Promise<number> {
  const containers = await docker.listContainers({ all: true });
  const usedPorts = new Set(
    containers
      .filter(c => c.Ports.some(p => p.PublicPort))
      .map(c => c.Ports.find(p => p.PublicPort)?.PublicPort)
  );

  for (let port = BASE_PORT; port < BASE_PORT + MAX_CONTAINERS; port++) {
    if (!usedPorts.has(port)) {
      return port;
    }
  }

  throw new Error('No available ports');
}

export async function getContainerStats(): Promise<{ running: number; total: number }> {
  const containers = await docker.listContainers({ all: true });
  return {
    running: containers.filter(c => c.State === 'running').length,
    total: containers.length,
  };
}
