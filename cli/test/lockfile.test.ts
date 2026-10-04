import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { readLockfile, writeLockfile } from '../src/lockfile';

let tmp: string;
beforeEach(() => {
  tmp = mkdtempSync(join(tmpdir(), 'skillhub-lock-'));
});
afterEach(() => rmSync(tmp, { recursive: true, force: true }));

describe('lockfile', () => {
  it('returns empty lock when file missing', () => {
    expect(readLockfile(tmp)).toEqual({ packages: {} });
  });

  it('writes and reads back', () => {
    const lock = { packages: { pdf: { version: '1.0.0', installedAt: '2026-10-04T00:00:00Z' } } };
    writeLockfile(tmp, lock);
    expect(readLockfile(tmp)).toEqual(lock);
  });
});
