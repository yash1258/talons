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

/**
 * Resiliently executes a command in the container with retries and status checks.
 */
export async function executeInContainer(containerId: string, cmd: string[], retries = 10): Promise<string> {
  const container = docker.getContainer(containerId);

  for (let i = 0; i < retries; i++) {
    try {
      const info = await container.inspect();

      // If container is not running or is restarting, wait and retry
      if (!info.State.Running || info.State.Restarting) {
        console.log(`Container ${containerId} is ${info.State.Status}. Waiting 3s... (attempt ${i + 1}/${retries})`);
        await new Promise(resolve => setTimeout(resolve, 3000));
        continue;
      }

      const exec = await container.exec({
        Cmd: cmd,
        AttachStdout: true,
        AttachStderr: true,
      });

      const stream = await exec.start({});

      return await new Promise((resolve, reject) => {
        let output = '';
        // Docker multiplexed stream: each chunk has an 8-byte header
        // [1 byte stream type][3 bytes padding][4 bytes payload size]
        stream.on('data', (chunk: Buffer) => {
          let offset = 0;
          while (offset < chunk.length) {
            if (chunk.length - offset < 8) break;
            const size = chunk.readUInt32BE(offset + 4);
            output += chunk.slice(offset + 8, offset + 8 + size).toString();
            offset += 8 + size;
          }
        });
        stream.on('end', () => resolve(output));
        stream.on('error', reject);
      });
    } catch (error: any) {
      if ((error.statusCode === 409 || error.message?.includes('restarting')) && i < retries - 1) {
        await new Promise(resolve => setTimeout(resolve, 3000));
        continue;
      }
      throw error;
    }
  }
  throw new Error(`Failed to execute command in container after ${retries} attempts`);
}
