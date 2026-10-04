import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, afterAll, describe, expect, it, vi } from 'vitest';
import AdmZip from 'adm-zip';
import { runPublish, zipDirectory } from '../src/commands/publish';
import { ApiError } from '../src/api';

describe('zipDirectory', () => {
  const tmp = mkdtempSync(join(tmpdir(), 'skillhub-publish-'));
  afterAll(() => rmSync(tmp, { recursive: true, force: true }));

  it('zips all files including manifest', () => {
    const dir = join(tmp, 'elem');
    mkdirSync(dir, { recursive: true });
    writeFileSync(join(dir, 'manifest.json'), '{}');
    writeFileSync(join(dir, 'SKILL.md'), '# x');
    const zip = new AdmZip(zipDirectory(dir));
    const names = zip.getEntries().map((e) => e.entryName).sort();
    expect(names).toEqual(['SKILL.md', 'manifest.json']);
  });
});

describe('runPublish', () => {
  const tmp = mkdtempSync(join(tmpdir(), 'skillhub-runpublish-'));
  afterEach(() => rmSync(join(tmp, 'elem'), { recursive: true, force: true }));
  afterAll(() => rmSync(tmp, { recursive: true, force: true }));

  function makeDir(manifest: Record<string, unknown>): string {
    const dir = join(tmp, 'elem');
    mkdirSync(dir, { recursive: true });
    writeFileSync(join(dir, 'manifest.json'), JSON.stringify(manifest));
    writeFileSync(join(dir, 'SKILL.md'), '# skill');
    return dir;
  }

  const cfg = { url: 'http://x', token: 'skh_t' };

  it('creates element when missing and publishes version', async () => {
    const dir = makeDir({ name: 'PDF Export', version: '1.0.0', type: 'SKILL', changelog: 'first' });
    const client = {
      getElement: vi.fn().mockRejectedValue(new ApiError(404, 'NOT_FOUND', 'no')),
      createElement: vi.fn().mockResolvedValue({ slug: 'pdf-export' }),
      publishVersion: vi.fn().mockResolvedValue({ version: '1.0.0' }),
    };
    const result = await runPublish(dir, cfg, { client: client as never });
    expect(result).toEqual({ slug: 'pdf-export', version: '1.0.0' });
    expect(client.createElement).toHaveBeenCalledWith(
      expect.objectContaining({ slug: 'pdf-export', type: 'SKILL', name: 'PDF Export', visibility: 'PUBLIC' }),
    );
    expect(client.publishVersion).toHaveBeenCalledWith('pdf-export', expect.any(Buffer), 'first');
  });

  it('reuses existing element', async () => {
    const dir = makeDir({ name: 'PDF Export', version: '1.1.0', type: 'SKILL' });
    const client = {
      getElement: vi.fn().mockResolvedValue({ slug: 'pdf-export', type: 'SKILL' }),
      createElement: vi.fn(),
      publishVersion: vi.fn().mockResolvedValue({ version: '1.1.0' }),
    };
    await runPublish(dir, cfg, { client: client as never });
    expect(client.createElement).not.toHaveBeenCalled();
  });

  it('rejects invalid manifest before any API call', async () => {
    const dir = makeDir({ name: 'X', version: 'oops', type: 'SKILL' });
    const client = {
      getElement: vi.fn(),
      createElement: vi.fn(),
      publishVersion: vi.fn(),
    };
    await expect(runPublish(dir, cfg, { client: client as never })).rejects.toThrow(/semver/);
    expect(client.getElement).not.toHaveBeenCalled();
  });

  it('rejects undervivable slug before any API call', async () => {
    const dir = makeDir({ name: 'Экспорт', version: '1.0.0', type: 'SKILL' });
    const client = {
      getElement: vi.fn(),
      createElement: vi.fn(),
      publishVersion: vi.fn(),
    };
    await expect(runPublish(dir, cfg, { client: client as never })).rejects.toThrow(/slug/i);
    expect(client.getElement).not.toHaveBeenCalled();
  });

  it('requires token', async () => {
    const dir = makeDir({ name: 'X', version: '1.0.0', type: 'SKILL' });
    await expect(runPublish(dir, { url: 'http://x' }, {})).rejects.toThrow(/login/);
  });
});
