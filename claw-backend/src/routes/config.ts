import { Router, Response } from 'express';
import { prisma } from '../db/prisma.js';
import { authenticate, AuthRequest } from '../middleware/auth.js';
import { updateInstanceConfig } from '../services/configGenerator.js';
import { z } from 'zod';

export const configRouter = Router();

configRouter.use(authenticate);

const channelSchema = z.object({
    telegram: z.object({
        botToken: z.string().min(1),
    }).optional(),
    whatsapp: z.object({
        phoneNumberId: z.string().min(1),
        accessToken: z.string().min(1),
        verifyToken: z.string().min(1),
    }).optional(),
    discord: z.object({
        botToken: z.string().min(1),
    }).optional(),
});

const configSchema = z.object({
    model: z.string().optional(),
    apiKey: z.string().optional(),
    provider: z.string().optional(),
    channels: channelSchema.optional(),
});

// GET /api/instances/:instanceId/config
configRouter.get('/:instanceId/config', async (req: AuthRequest, res: Response) => {
    try {
        const instance = await prisma.instance.findFirst({
            where: { id: req.params.instanceId as string, userId: req.userId },
        });

        if (!instance) {
            return res.status(404).json({ error: 'Instance not found' });
        }

        const config = instance.configJson ? JSON.parse(instance.configJson) : {};
        res.json(config);
    } catch (error) {
        console.error('Get config error:', error);
        res.status(500).json({ error: 'Failed to get config' });
    }
});

// PUT /api/instances/:instanceId/config
configRouter.put('/:instanceId/config', async (req: AuthRequest, res: Response) => {
    try {
        const instance = await prisma.instance.findFirst({
            where: { id: req.params.instanceId as string, userId: req.userId },
        });

        if (!instance) {
            return res.status(404).json({ error: 'Instance not found' });
        }

        const userConfig = configSchema.parse(req.body);

        // Merge with existing config
        const existingConfig = instance.configJson ? JSON.parse(instance.configJson) : {};
        const mergedConfig = {
            ...existingConfig,
            ...userConfig,
            channels: {
                ...existingConfig.channels,
                ...userConfig.channels,
            },
        };

        await updateInstanceConfig(instance, mergedConfig);

        res.json({ success: true, config: mergedConfig });
    } catch (error: unknown) {
        if (error instanceof z.ZodError) {
            return res.status(400).json({ error: error.errors });
        }
        console.error('Update config error:', error);
        res.status(500).json({ error: 'Failed to update config' });
    }
});
