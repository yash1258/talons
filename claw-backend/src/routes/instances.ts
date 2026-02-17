import { Router } from 'express';
import { prisma } from '../db/prisma.js';
import { authenticate, AuthRequest } from '../middleware/auth.js';
import { docker } from '../services/docker.js';
import { createInstance, getInstanceStatus, startInstance, stopInstance, deleteInstance } from '../services/instanceManager.js';

export const instancesRouter = Router();

instancesRouter.use(authenticate);

instancesRouter.post('/', async (req: AuthRequest, res) => {
  try {
    const existing = await prisma.instance.findFirst({
      where: { userId: req.userId, status: { not: 'DELETED' } },
    });

    if (existing) {
      return res.json(existing);
    }

    const { provider, apiKey, model } = req.body || {};
    const instance = await createInstance(req.userId!, { provider, apiKey, model });
    res.status(201).json(instance);
  } catch (error) {
    console.error('Create instance error:', error);
    res.status(500).json({ error: 'Failed to create instance' });
  }
});

instancesRouter.get('/', async (req: AuthRequest, res) => {
  const instances = await prisma.instance.findMany({
    where: { userId: req.userId, status: { not: 'DELETED' } },
    orderBy: { createdAt: 'desc' },
  });
  res.json(instances);
});

instancesRouter.get('/:id', async (req: AuthRequest, res) => {
  const instance = await prisma.instance.findFirst({
    where: { id: req.params.id as string, userId: req.userId },
  });

  if (!instance) {
    return res.status(404).json({ error: 'Instance not found' });
  }

  const status = await getInstanceStatus(instance);
  res.json({ ...instance, runtimeStatus: status });
});

instancesRouter.post('/:id/start', async (req: AuthRequest, res) => {
  const instance = await prisma.instance.findFirst({
    where: { id: req.params.id as string, userId: req.userId },
  });

  if (!instance) {
    return res.status(404).json({ error: 'Instance not found' });
  }

  const updated = await startInstance(instance);
  res.json(updated);
});

instancesRouter.post('/:id/stop', async (req: AuthRequest, res) => {
  const instance = await prisma.instance.findFirst({
    where: { id: req.params.id as string, userId: req.userId },
  });

  if (!instance) {
    return res.status(404).json({ error: 'Instance not found' });
  }

  const updated = await stopInstance(instance);
  res.json(updated);
});

instancesRouter.delete('/:id', async (req: AuthRequest, res) => {
  const instance = await prisma.instance.findFirst({
    where: { id: req.params.id as string, userId: req.userId },
  });

  if (!instance) {
    return res.status(404).json({ error: 'Instance not found' });
  }

  await deleteInstance(instance);
  res.json({ success: true });
});

// Health check endpoint
instancesRouter.get('/:id/health', async (req: AuthRequest, res) => {
  try {
    const instance = await prisma.instance.findFirst({
      where: { id: req.params.id as string, userId: req.userId },
    });

    if (!instance) {
      return res.status(404).json({ error: 'Instance not found' });
    }

    if (!instance.containerId) {
      return res.json({
        status: 'no_container',
        healthy: false,
        instance: { id: instance.id, status: instance.status },
      });
    }

    try {
      const container = docker.getContainer(instance.containerId);
      const info = await container.inspect();

      const isRunning = info.State.Running;
      const startedAt = info.State.StartedAt;

      // Try HTTP health check on the gateway port
      let gatewayReachable = false;
      if (isRunning && instance.dockerPort) {
        try {
          const controller = new AbortController();
          const timeout = setTimeout(() => controller.abort(), 3000);
          const healthRes = await fetch(`http://localhost:${instance.dockerPort}/`, {
            signal: controller.signal,
          });
          clearTimeout(timeout);
          gatewayReachable = healthRes.ok || healthRes.status < 500;
        } catch {
          gatewayReachable = false;
        }
      }

      res.json({
        status: isRunning ? 'running' : 'stopped',
        healthy: isRunning && gatewayReachable,
        container: {
          running: isRunning,
          startedAt,
          gatewayReachable,
        },
        instance: { id: instance.id, status: instance.status, dockerPort: instance.dockerPort },
      });
    } catch (dockerError) {
      res.json({
        status: 'error',
        healthy: false,
        error: 'Container not found or Docker unavailable',
        instance: { id: instance.id, status: instance.status },
      });
    }
  } catch (error) {
    console.error('Health check error:', error);
    res.status(500).json({ error: 'Health check failed' });
  }
});
