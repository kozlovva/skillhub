import { existsSync } from 'node:fs';
import { join } from 'node:path';

export interface Target {
  platform: 'opencode' | 'claude';
  dir: string;
}

export function detectTargets(projectRoot: string): Target[] {
  const targets: Target[] = [];
  if (existsSync(join(projectRoot, '.opencode'))) {
    targets.push({ platform: 'opencode', dir: join(projectRoot, '.opencode', 'skills') });
  }
  if (existsSync(join(projectRoot, '.claude')) || existsSync(join(projectRoot, 'CLAUDE.md'))) {
    targets.push({ platform: 'claude', dir: join(projectRoot, '.claude', 'skills') });
  }
  return targets;
}
