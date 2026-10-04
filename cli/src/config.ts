import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join, dirname } from 'node:path';

export interface SkillhubConfig {
  url: string;
  token?: string;
}

export const DEFAULT_URL = 'http://localhost:8080';

export function defaultConfigPath(): string {
  return join(homedir(), '.skillhub', 'config.json');
}

export function loadConfig(path: string = defaultConfigPath()): SkillhubConfig | null {
  if (!existsSync(path)) return null;
  let raw: Record<string, unknown>;
  try {
    raw = JSON.parse(readFileSync(path, 'utf8')) as Record<string, unknown>;
  } catch {
    return null;
  }
  if (raw === null || typeof raw !== 'object') return null;
  if (typeof raw.url !== 'string') return null;
  return {
    url: raw.url,
    token: typeof raw.token === 'string' ? raw.token : undefined,
  };
}

export function saveConfig(cfg: SkillhubConfig, path: string = defaultConfigPath()): void {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, JSON.stringify(cfg, null, 2) + '\n', { mode: 0o600 });
}

export function resolveConfig(path: string = defaultConfigPath()): SkillhubConfig {
  const file = loadConfig(path);
  return {
    url: process.env.SKILLHUB_URL ?? file?.url ?? DEFAULT_URL,
    token: process.env.SKILLHUB_TOKEN ?? file?.token,
  };
}
