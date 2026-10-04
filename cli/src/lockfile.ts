import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

export interface LockEntry {
  version: string;
  installedAt: string;
  kind?: 'pack';
}

export interface LockFile {
  packages: Record<string, LockEntry>;
}

const EMPTY: LockFile = { packages: {} };

export function readLockfile(root: string): LockFile {
  const path = join(root, 'skillhub.lock');
  if (!existsSync(path)) return { packages: {} };
  const raw = JSON.parse(readFileSync(path, 'utf8')) as LockFile;
  return { packages: raw.packages ?? {} };
}

export function writeLockfile(root: string, lock: LockFile): void {
  writeFileSync(join(root, 'skillhub.lock'), JSON.stringify(lock, null, 2) + '\n');
}
