import type { Command } from 'commander';
import { ApiClient, type MeInfo } from '../api';
import { resolveConfig, type SkillhubConfig } from '../config';

export async function runWhoami(cfg: SkillhubConfig, json = false): Promise<MeInfo> {
  const me = await new ApiClient(cfg.url, cfg.token).me();
  if (json) {
    console.log(JSON.stringify(me, null, 2));
  } else {
    const teams = me.teams.map((t) => `${t.slug} (${t.role})`).join(', ');
    console.log(`${me.username}${me.admin ? ' [admin]' : ''}${teams ? ` — teams: ${teams}` : ''}`);
  }
  return me;
}

export function register(program: Command): void {
  program
    .command('whoami')
    .description('Show current user')
    .option('--json', 'machine-readable output')
    .action(async (opts: { json?: boolean }) => {
      await runWhoami(resolveConfig(), opts.json);
    });
}
