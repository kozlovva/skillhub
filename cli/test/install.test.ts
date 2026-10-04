import AdmZip from 'adm-zip';
import { mkdtempSync, existsSync, readFileSync, rmSync, mkdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, afterAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { decideVersion, extractArchive, parseRef, runInstall } from '../src/commands/install';
import type { LockFile } from '../src/lockfile';

function zipWith(files: Record<string, string>): Buffer {
  const zip = new AdmZip();
  for (const [name, content] of Object.entries(files)) zip.addFile(name, Buffer.from(content));
  return zip.toBuffer();
}

describe('parseRef', () => {
  it('splits slug and version', () => {
    expect(parseRef('pdf-export@1.2.3')).toEqual({ slug: 'pdf-export', version: '1.2.3' });
    expect(parseRef('pdf-export')).toEqual({ slug: 'pdf-export' });
    expect(parseRef('pdf@latest')).toEqual({ slug: 'pdf' });
  });
});

describe('decideVersion', () => {
  const empty: LockFile = { packages: {} };
  const installed: LockFile = { packages: { pdf: { version: '1.0.0', installedAt: 'x' } } };

  it('installs when absent', () => {
    expect(decideVersion(empty, 'pdf', '2.0.0', false)).toEqual({ action: 'install', version: '2.0.0' });
  });

  it('reports already installed on same version', () => {
    expect(decideVersion(installed, 'pdf', '1.0.0', false)).toEqual({ action: 'already', version: '1.0.0' });
  });

  it('reports update on new version', () => {
    expect(decideVersion(installed, 'pdf', '1.1.0', false)).toEqual({ action: 'update', version: '1.1.0', from: '1.0.0' });
  });

  it('force reinstalls same version', () => {
    expect(decideVersion(installed, 'pdf', '1.0.0', true).action).toBe('update');
  });
});

describe('extractArchive', () => {
  const tmp = mkdtempSync(join(tmpdir(), 'skillhub-install-'));
  afterAll(() => rmSync(tmp, { recursive: true, force: true }));

  it('extracts files to destination', () => {
    const dest = join(tmp, 'out');
    extractArchive(zipWith({ 'SKILL.md': '# hi', 'scripts/run.sh': 'echo' }), dest);
    expect(readFileSync(join(dest, 'SKILL.md'), 'utf8')).toBe('# hi');
    expect(existsSync(join(dest, 'scripts', 'run.sh'))).toBe(true);
  });
});

describe('runInstall', () => {
  const tmp = mkdtempSync(join(tmpdir(), 'skillhub-runinstall-'));
  beforeEach(() => {
    mkdirSync(join(tmp, '.opencode'), { recursive: true });
    vi.spyOn(process, 'cwd').mockReturnValue(tmp);
  });
  afterEach(() => {
    rmSync(join(tmp, '.opencode', 'skills'), { recursive: true, force: true });
    rmSync(join(tmp, 'skillhub.lock'), { force: true });
    vi.restoreAllMocks();
  });
  afterAll(() => rmSync(tmp, { recursive: true, force: true }));

  const cfg = { url: 'http://x', token: 'skh_t' };

  it('downloads and installs a skill, writes lockfile', async () => {
    const bytes = zipWith({ 'SKILL.md': '# skill' });
    const client = {
      getElement: vi.fn().mockResolvedValue({ slug: 'pdf-export', type: 'SKILL', name: 'PDF', visibility: 'PUBLIC', downloadsCount: 0, latestVersion: '1.0.0' }),
      downloadElementArchive: vi.fn().mockResolvedValue(bytes),
      downloadPackArchive: vi.fn(),
    };
    const result = await runInstall('pdf-export', {}, cfg, {
      client: client as never,
      picker: (targets) => [targets[0]],
    });
    expect(result.action).toBe('install');
    expect(existsSync(join(tmp, '.opencode', 'skills', 'pdf-export', 'SKILL.md'))).toBe(true);
    const lock = JSON.parse(readFileSync(join(tmp, 'skillhub.lock'), 'utf8'));
    expect(lock.packages['pdf-export'].version).toBe('1.0.0');
  });

  it('pack installs under pack slug dir', async () => {
    const bytes = zipWith({ 'team-skills/pdf-export/SKILL.md': '# s' });
    const client = {
      getElement: vi.fn().mockResolvedValue({ slug: 'team-skills', type: 'PACK', name: 'Team', visibility: 'PUBLIC', downloadsCount: 0, latestVersion: '0.3.0' }),
      downloadElementArchive: vi.fn(),
      downloadPackArchive: vi.fn().mockResolvedValue(bytes),
    };
    const result = await runInstall('team-skills@0.3.0', {}, cfg, {
      client: client as never,
      picker: (targets) => [targets[0]],
    });
    expect(result.version).toBe('0.3.0');
    expect(existsSync(join(tmp, '.opencode', 'skills', 'team-skills', 'team-skills', 'pdf-export', 'SKILL.md'))).toBe(false);
    expect(existsSync(join(tmp, '.opencode', 'skills', 'team-skills', 'pdf-export', 'SKILL.md'))).toBe(true);
  });

  it('second install of same version reports already', async () => {
    const bytes = zipWith({ 'SKILL.md': '# skill' });
    const client = {
      getElement: vi.fn().mockResolvedValue({ slug: 'pdf-export', type: 'SKILL', name: 'PDF', visibility: 'PUBLIC', downloadsCount: 0, latestVersion: '1.0.0' }),
      downloadElementArchive: vi.fn().mockResolvedValue(bytes),
      downloadPackArchive: vi.fn(),
    };
    const deps = { client: client as never, picker: (targets: unknown[]) => [targets[0]] };
    await runInstall('pdf-export', {}, cfg, deps);
    const second = await runInstall('pdf-export', {}, cfg, deps);
    expect(second.action).toBe('already');
  });
});
