import { docker } from './docker.js';
import type { Instance } from '@prisma/client';
import { prisma } from '../db/prisma.js';

const DEFAULT_FREE_MODEL = 'openrouter/free';
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
 * The config is written to the container's data volume at:
 *   /home/node/.openclaw/openclaw.json
 * 
 * Free tier users get a pooled OpenRouter key with a free model.
 * Pro/Premium users provide their own key (BYOK).
 */
export function generateConfig(
  subscription: string,
  userConfig: ClawConfig
): object {
  const isFree = subscription === 'FREE';

  const model = userConfig.model || (isFree ? DEFAULT_FREE_MODEL : 'anthropic/claude-sonnet-4-20250514');
  const apiKey = isFree ? OPENROUTER_FREE_KEY : (userConfig.apiKey || '');

  // Determine provider from model string or explicit setting
  let provider = userConfig.provider || 'openrouter';
  if (model.startsWith('anthropic/')) provider = 'anthropic';
  else if (model.startsWith('openai/') || model.startsWith('gpt-')) provider = 'openai';
  else if (model.startsWith('openrouter/')) provider = 'openrouter';

  // Build base config
  const config: Record<string, any> = {
    llm: {
      provider,
      model: model.replace(/^(openrouter|anthropic|openai)\//, ''),
      ...(provider === 'openrouter' && { apiKey }),
      ...(provider === 'anthropic' && { apiKey }),
      ...(provider === 'openai' && { apiKey }),
    },
    channels: {},
  };

  // Add channel configs
  if (userConfig.channels?.telegram) {
    config.channels.telegram = {
      enabled: true,
      botToken: userConfig.channels.telegram.botToken,
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
      botToken: userConfig.channels.discord.botToken,
    };
  }

  return config;
}

/**
 * Writes the generated config into the container's data volume.
 * Uses docker exec to write the file since the volume is mounted.
 */
export async function writeConfigToContainer(
  instance: Instance,
  config: object
): Promise<void> {
  if (!instance.containerId) {
    throw new Error('Instance has no container ID — is it running?');
  }
  const container = docker.getContainer(instance.containerId);
  const configJson = JSON.stringify(config, null, 2);

  // Write config via docker exec
  const exec = await container.exec({
    Cmd: ['sh', '-c', `echo '${configJson.replace(/'/g, "'\\''")}' > /home/node/.openclaw/openclaw.json`],
    AttachStdout: true,
    AttachStderr: true,
  });

  await exec.start({});
}

/**
 * Updates the instance's stored config in the database and writes it to the container.
 */
export async function updateInstanceConfig(
  instance: Instance,
  userConfig: ClawConfig
): Promise<void> {
  const user = await prisma.user.findUnique({ where: { id: instance.userId } });
  if (!user) throw new Error('User not found');

  const config = generateConfig(user.subscription, userConfig);

  // Store config choices in database
  await prisma.instance.update({
    where: { id: instance.id },
    data: { configJson: JSON.stringify(userConfig) },
  });

  // Write to container
  await writeConfigToContainer(instance, config);
}
