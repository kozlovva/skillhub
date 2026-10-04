import { describe, expect, it } from 'vitest';
import { filterByTeam, formatSearchTable } from '../src/commands/search';
import type { ElementInfo } from '../src/api';

const items: ElementInfo[] = [
  { slug: 'pdf-export', type: 'SKILL', name: 'PDF', team: 'docs', visibility: 'TEAM', downloadsCount: 12, latestVersion: '1.1.0' },
  { slug: 'lint-helper', type: 'SCRIPT', name: 'Lint', team: null, visibility: 'PUBLIC', downloadsCount: 3, latestVersion: '0.2.0' },
];

describe('search command helpers', () => {
  it('filters by team client-side', () => {
    expect(filterByTeam(items, 'docs')).toHaveLength(1);
    expect(filterByTeam(items, undefined)).toHaveLength(2);
    expect(filterByTeam(items, 'nope')).toHaveLength(0);
  });

  it('formats a table with header', () => {
    const out = formatSearchTable(items);
    expect(out).toContain('slug');
    expect(out).toContain('pdf-export');
    expect(out).toContain('1.1.0');
  });

  it('returns message for empty results', () => {
    expect(formatSearchTable([])).toContain('No results');
  });
});
