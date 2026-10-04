import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { ManifestError, deriveSlug, parseManifest, readManifest } from '../src/manifest';

const tmp = mkdtempSync(join(tmpdir(), 'skillhub-manifest-'));
afterEach(() => rmSync(join(tmp, 'm'), { recursive: true, force: true }));

describe('manifest', () => {
  it('accepts a valid manifest', () => {
    const m = parseManifest({ name: 'PDF Export', version: '1.2.3', type: 'SKILL' });
    expect(m.version).toBe('1.2.3');
    expect(m.type).toBe('SKILL');
  });

  it('collects all problems', () => {
    expect.assertions(2);
    expect(() => parseManifest({ name: '', version: 'abc', type: 'NOPE' })).toThrow(ManifestError);
    try {
      parseManifest({});
    } catch (e) {
      expect((e as ManifestError).problems).toHaveLength(3); // name, version, type
    }
  });

  it('validates semver and visibility', () => {
    expect(() => parseManifest({ name: 'X', version: '1.0', type: 'SKILL' })).toThrow(ManifestError);
    expect(() => parseManifest({ name: 'X', version: '1.0.0', type: 'SKILL', visibility: 'INTERNAL' })).toThrow(ManifestError);
  });

  it('readManifest reads from dir', () => {
    const dir = join(tmp, 'm');
    mkdirSync(dir, { recursive: true });
    writeFileSync(
      join(dir, 'manifest.json'),
      JSON.stringify({ name: 'My Skill', version: '0.1.0', type: 'SKILL' }),
    );
    expect(readManifest(dir).name).toBe('My Skill');
  });

  it('deriveSlug converts name to kebab-case', () => {
    expect(deriveSlug('PDF Export Skill!')).toBe('pdf-export-skill');
    expect(deriveSlug('  Мой  Скилл ')).toBe('---');
    expect(deriveSlug('a  b')).toBe('a-b');
  });
});
