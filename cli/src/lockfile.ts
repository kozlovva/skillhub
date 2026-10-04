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

export function readLockfile(root: string): LockFile {
  const path = join(root, 'skillhub.lock');
  if (!existsSync(path)) return { packages: {} };
  try {
    const raw = JSON.parse(readFileSync(path, 'utf8')) as LockFile;
    if (raw === null || typeof raw !== 'object' || Array.isArray(raw)) return { packages: {} };
    return { packages: raw.packages ?? {} };
  } catch {
    return { packages: {} };
  }
}

export function writeLockfile(root: string, lock: LockFile): void {
  writeFileSync(join(root, 'skillhub.lock'), JSON.stringify(lock, null, 2) + '\n');
}
