import Docker from 'dockerode';

const DOCKER_SOCKET = process.env.DOCKER_HOST || '/var/run/docker.sock';

export const docker = DOCKER_SOCKET.startsWith('unix:') || DOCKER_SOCKET.startsWith('/')
  ? new Docker({ socketPath: DOCKER_SOCKET.replace('unix://', '') })
  : new Docker({ host: DOCKER_SOCKET, port: 2375 });

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

/**
 * Returns a set of ports currently used by ANY container (running or stopped).
 */
export async function getUsedDockerPorts(): Promise<Set<number>> {
  const containers = await docker.listContainers({ all: true });
  const usedPorts = new Set<number>();

  for (const container of containers) {
    if (container.Ports) {
      for (const p of container.Ports) {
        if (p.PublicPort) {
          usedPorts.add(p.PublicPort);
        }
      }
    }
  }

  return usedPorts;
}


export async function getContainerStats(): Promise<{ running: number; total: number }> {
  const containers = await docker.listContainers({ all: true });
  return {
    running: containers.filter(c => c.State === 'running').length,
    total: containers.length,
  };
}
