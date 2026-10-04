import { createInterface } from 'node:readline/promises';
import type { Command } from 'commander';
import { ApiClient } from '../api';
import { defaultConfigPath, saveConfig, type SkillhubConfig } from '../config';

export async function runLogin(urlArg?: string, cfgPath?: string): Promise<SkillhubConfig> {
  const rl = createInterface({ input: process.stdin, output: process.stdout });
  const urlRaw = urlArg ?? (await rl.question('SkillHub API URL: '));
  const token = (await rl.question('API token (create it in the web UI): ')).trim();
  rl.close();
  const cfg: SkillhubConfig = { url: urlRaw.trim().replace(/\/+$/, ''), token };
  try {
    const me = await new ApiClient(cfg.url, cfg.token).me();
    console.log(`Logged in as ${me.username}`);
  } catch (e) {
    console.warn(`Warning: token check failed: ${(e as Error).message}`);
  }
  const path = cfgPath ?? defaultConfigPath();
  saveConfig(cfg, path);
  console.log(`Credentials saved to ${path}`);
  return cfg;
}

export function register(program: Command): void {
  program
    .command('login')
    .description('Save SkillHub URL and API token')
    .option('--url <url>', 'SkillHub API URL')
    .action(async (opts: { url?: string }) => {
      await runLogin(opts.url);
    });
}
