import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { detectTargets } from '../src/detect-target';

let tmp: string;
beforeEach(() => {
  tmp = mkdtempSync(join(tmpdir(), 'skillhub-detect-'));
});
afterEach(() => rmSync(tmp, { recursive: true, force: true }));

describe('detectTargets', () => {
  it('detects nothing in empty project', () => {
    expect(detectTargets(tmp)).toEqual([]);
  });

  it('detects opencode', () => {
    mkdirSync(join(tmp, '.opencode'));
    expect(detectTargets(tmp)).toEqual([
      { platform: 'opencode', dir: join(tmp, '.opencode', 'skills') },
    ]);
  });

  it('detects claude via .claude dir', () => {
    mkdirSync(join(tmp, '.claude'));
    const t = detectTargets(tmp);
    expect(t.map((x) => x.platform)).toEqual(['claude']);
  });

  it('detects claude via CLAUDE.md', () => {
    writeFileSync(join(tmp, 'CLAUDE.md'), '');
    const t = detectTargets(tmp);
    expect(t.map((x) => x.platform)).toEqual(['claude']);
  });

  it('detects both platforms', () => {
    mkdirSync(join(tmp, '.opencode'));
    mkdirSync(join(tmp, '.claude'));
    expect(detectTargets(tmp)).toHaveLength(2);
  });
});
