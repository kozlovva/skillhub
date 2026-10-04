import type { Command } from 'commander';
import { ApiClient, type ElementInfo, type SearchResult } from '../api';
import { resolveConfig, type SkillhubConfig } from '../config';

export function filterByTeam(items: ElementInfo[], team?: string): ElementInfo[] {
  if (!team) return items;
  return items.filter((i) => i.team === team);
}

export function formatSearchTable(items: ElementInfo[]): string {
  if (items.length === 0) return 'No results';
  const rows = items.map((i) => [
    i.slug,
    i.type,
    i.latestVersion ?? '-',
    String(i.downloadsCount),
  ]);
  const header = ['slug', 'type', 'latest', 'downloads'];
  const widths = [0, 1, 2, 3].map((c) =>
    Math.max(header[c].length, ...rows.map((r) => r[c].length)),
  );
  const line = (cells: string[]) =>
    cells.map((cell, i) => cell.padEnd(widths[i])).join('  ');
  return [line(header), ...rows.map(line)].join('\n');
}

export async function runSearch(
  query: string,
  opts: { type?: string; team?: string; limit?: string; json?: boolean },
  cfg: SkillhubConfig,
): Promise<SearchResult> {
  const client = new ApiClient(cfg.url, cfg.token);
  const result = await client.search(query, {
    type: opts.type,
    limit: opts.limit ? parseInt(opts.limit, 10) : 20,
  });
  const items = filterByTeam(result.items, opts.team);
  const filtered: SearchResult = { ...result, items };
  if (opts.json) {
    console.log(JSON.stringify(filtered, null, 2));
  } else {
    console.log(formatSearchTable(items));
  }
  return filtered;
}

export function register(program: Command): void {
  program
    .command('search')
    .description('Search the SkillHub catalog')
    .argument('<query>')
    .option('--type <type>', 'filter by element type')
    .option('--team <slug>', 'filter by team slug (client-side)')
    .option('--limit <n>', 'max results', '20')
    .option('--json', 'machine-readable output')
    .action(async (query: string, opts) => {
      await runSearch(query, opts, resolveConfig());
    });
}
