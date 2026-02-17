import { docker, executeInContainer } from './docker.js';
import type { Instance } from '@prisma/client';
import { prisma } from '../db/prisma.js';

const OPENROUTER_FREE_KEY = process.env.OPENROUTER_FREE_KEY || '';

interface ChannelConfig {
  telegram?: { botToken: string };
  whatsapp?: { phoneNumberId: string; accessToken: string; verifyToken: string };
  discord?: { botToken: string };
}

interface ClawConfig {
  model?: string;
  apiKey?: string;
  provider?: string;
  channels?: ChannelConfig;
}

/**
 * Builds an openclaw-compatible JSON config from user parameters.
 * 
 * Format matches the real openclaw.json structure:
 * - models.providers.{provider} for API keys
 * - agents.defaults.model.primary for model selection
 * - channels.{name} for messaging channels
 * - gateway.auth.token for WebSocket authentication
 */
export function generateConfig(
  subscription: string,
  userConfig: ClawConfig
): object {
  const isFree = subscription === 'FREE';

  const model = userConfig.model || (isFree ? 'openrouter/auto' : 'anthropic/claude-sonnet-4-20250514');
  const apiKey = isFree ? OPENROUTER_FREE_KEY : (userConfig.apiKey || '');

  // Determine provider from model string
  let provider = userConfig.provider || 'openrouter';
  if (model.startsWith('anthropic/')) provider = 'anthropic';
  else if (model.startsWith('openai/') || model.startsWith('gpt-')) provider = 'openai';
  else if (model.startsWith('openrouter/')) provider = 'openrouter';

  // Build config in real openclaw.json format
  const config: Record<string, any> = {
    models: {
      providers: {
        [provider]: {
          apiKey,
          ...(provider === 'openrouter' && {
            baseUrl: 'https://openrouter.ai/api/v1',
            api: 'openai-completions',
          }),
          ...(provider === 'anthropic' && {
            api: 'anthropic-messages',
          }),
          ...(provider === 'openai' && {
            api: 'openai-responses',
          }),
        },
      },
    },
    agents: {
      defaults: {
        model: {
          primary: model,
        },
        maxConcurrent: 2,
      },
    },
    channels: {},
    commands: {
      native: 'auto',
    },
  };

  // Add channel configs
  if (userConfig.channels?.telegram) {
    config.channels.telegram = {
      enabled: true,
      botToken: userConfig.channels.telegram.botToken,
      dmPolicy: 'pairing',
      groupPolicy: 'allowlist',
    };
  }

  if (userConfig.channels?.whatsapp) {
    config.channels.whatsapp = {
      enabled: true,
      ...userConfig.channels.whatsapp,
    };
  }

  if (userConfig.channels?.discord) {
    config.channels.discord = {
      enabled: true,
      token: userConfig.channels.discord.botToken,
      dmPolicy: 'pairing',
      groupPolicy: 'disabled',
    };
  }

  return config;
}

/**
 * Reads the current openclaw.json from inside the container.
 */
async function readContainerConfig(instance: Instance): Promise<Record<string, any>> {
  if (!instance.containerId) throw new Error('Instance has no container ID');
  const data = await executeInContainer(instance.containerId, ['cat', '/home/node/.openclaw/openclaw.json']);
  try {
    const jsonStart = data.indexOf('{');
    if (jsonStart === -1) return {};
    return JSON.parse(data.slice(jsonStart));
  } catch (error) {
    console.error('Failed to parse config from container:', error);
    return {};
  }
}

/**
 * Writes config JSON back into the container's openclaw.json.
 */
async function writeContainerConfig(instance: Instance, config: Record<string, any>): Promise<void> {
  if (!instance.containerId) throw new Error('Instance has no container ID');
  const configJson = JSON.stringify(config, null, 2);
  // Use heredoc to safely write JSON with newlines and quotes
  await executeInContainer(instance.containerId, [
    'sh', '-c',
    `cat > /home/node/.openclaw/openclaw.json << 'CLAWEOF'\n${configJson}\nCLAWEOF`
  ]);
}

/**
 * Updates the instance config by reading the existing openclaw.json,
 * merging in the user's changes, writing it back, and restarting the daemon.
 */
export async function updateInstanceConfig(
  instance: Instance,
  userConfig: ClawConfig
): Promise<void> {
  // Read existing config from container (created by onboarding)
  const existing = await readContainerConfig(instance);

  // Merge channel settings
  if (userConfig.channels?.telegram) {
    existing.channels = existing.channels || {};
    existing.channels.telegram = {
      ...existing.channels.telegram,
      enabled: true,
      botToken: userConfig.channels.telegram.botToken,
      dmPolicy: existing.channels?.telegram?.dmPolicy || 'pairing',
      groupPolicy: existing.channels?.telegram?.groupPolicy || 'allowlist',
    };
  }

  // Ensure gateway bind is lan for Docker accessibility
  existing.gateway = existing.gateway || {};
  existing.gateway.bind = 'lan';

  if (userConfig.channels?.discord) {
    existing.channels = existing.channels || {};
    existing.channels.discord = {
      ...existing.channels.discord,
      enabled: true,
      token: userConfig.channels.discord.botToken,
      dmPolicy: 'pairing',
      groupPolicy: 'disabled',
    };
  }

  // Merge model settings if provided
  if (userConfig.model) {
    existing.agents = existing.agents || {};
    existing.agents.defaults = existing.agents.defaults || {};
    existing.agents.defaults.model = existing.agents.defaults.model || {};
    existing.agents.defaults.model.primary = userConfig.model;
  }

  // Write merged config back
  await writeContainerConfig(instance, existing);

  // Store user-facing config in database
  await prisma.instance.update({
    where: { id: instance.id },
    data: { configJson: JSON.stringify(userConfig) },
  });

  // Restart container so daemon picks up the new config
  if (instance.containerId) {
    const container = docker.getContainer(instance.containerId);
    await container.restart();
  }
}
