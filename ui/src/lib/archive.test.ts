import { zipSync, strToU8 } from 'fflate';
import { inspectArchive, SEMVER_PATTERN, MAX_ARCHIVE_BYTES, MAX_FILES } from './archive';

const VALID = { name: 'pdf-skill', version: '1.2.3', description: 'Desc', type: 'SKILL' };

function zipFile(entries: Record<string, string>): File {
  const files: Record<string, Uint8Array> = {};
  for (const [k, v] of Object.entries(entries)) files[k] = strToU8(v);
  return new File([zipSync(files)], 'element.zip', { type: 'application/zip' });
}

function withManifest(manifest: unknown, extra: Record<string, string> = {}): File {
  return zipFile(manifest == null ? extra : { 'manifest.json': JSON.stringify(manifest), ...extra });
}

test('parses manifest and entry list from a valid archive', async () => {
  const res = await inspectArchive(withManifest(VALID, { 'SKILL.md': '# hi', 'scripts/run.sh': 'echo' }));
  if (!res.ok) throw new Error(res.error);
  expect(res.manifest).toEqual(VALID);
  expect(res.entries).toEqual(
    expect.arrayContaining([
      expect.objectContaining({ path: 'SKILL.md' }),
      expect.objectContaining({ path: 'scripts/run.sh' }),
    ])
  );
});

test('defaults description and type to empty strings', async () => {
  const res = await inspectArchive(withManifest({ name: 'x', version: '1.0.0' }));
  if (!res.ok) throw new Error(res.error);
  expect(res.manifest.description).toBe('');
  expect(res.manifest.type).toBe('');
});

test('accepts Explorer-style archive wrapped in single root folder', async () => {
  const res = await inspectArchive(
    zipFile({
      'demo-skill/manifest.json': JSON.stringify({ name: 'demo-skill', version: '1.0.0', type: 'SKILL' }),
      'demo-skill/SKILL.md': '# hi',
      'demo-skill/scripts/run.sh': 'echo',
    })
  );
  if (!res.ok) throw new Error(res.error);
  expect(res.manifest.name).toBe('demo-skill');
  expect(res.entries.map((e) => e.path)).toEqual(
    expect.arrayContaining(['SKILL.md', 'scripts/run.sh'])
  );
});

test('does not strip prefix when files are outside the wrapped folder', async () => {
  const res = await inspectArchive(
    zipFile({ 'manifest.json': JSON.stringify(VALID), 'SKILL.md': '# hi', 'extra/readme.md': 'hi' })
  );
  if (!res.ok) throw new Error(res.error);
  expect(res.entries.map((e) => e.path)).toEqual(
    expect.arrayContaining(['manifest.json', 'SKILL.md', 'extra/readme.md'])
  );
});

test('rejects archive without manifest.json in root', async () => {
  const res = await inspectArchive(withManifest(null, { 'SKILL.md': '# hi' }));
  expect(res).toEqual({ ok: false, error: 'В корне архива нет manifest.json' });
});

test('rejects when manifest is nested but other files are outside that folder', async () => {
  const res = await inspectArchive(
    zipFile({ 'docs/manifest.json': '{"name":"x","version":"1.0.0"}', 'SKILL.md': '# hi' })
  );
  expect(res).toEqual({ ok: false, error: 'В корне архива нет manifest.json' });
});

test('rejects invalid manifest json', async () => {
  const res = await inspectArchive(zipFile({ 'manifest.json': '{not json' }));
  expect(res).toEqual({ ok: false, error: 'manifest.json не является корректным JSON' });
});

test('rejects manifest without name', async () => {
  const res = await inspectArchive(withManifest({ version: '1.0.0' }));
  expect(res).toEqual({ ok: false, error: 'manifest.json: поле name обязательно' });
});

test('rejects manifest without version', async () => {
  const res = await inspectArchive(withManifest({ name: 'x' }));
  expect(res).toEqual({ ok: false, error: 'manifest.json: поле version обязательно' });
});

test('rejects non-semver version', async () => {
  const res = await inspectArchive(withManifest({ name: 'x', version: '1.0' }));
  expect(res).toEqual({
    ok: false,
    error: 'manifest.json: version должна быть в формате semver, например 1.2.3',
  });
});

test('rejects file that is not a zip', async () => {
  const res = await inspectArchive(new File(['hello'], 'x.zip', { type: 'application/zip' }));
  expect(res).toEqual({ ok: false, error: 'Файл не является корректным ZIP-архивом' });
});

test('rejects archive larger than 50 MB', async () => {
  const file = new File(
    [new Uint8Array(MAX_ARCHIVE_BYTES + 1)],
    'big.zip',
    { type: 'application/zip' }
  );
  const res = await inspectArchive(file);
  expect(res).toEqual({ ok: false, error: 'Архив больше 50 МБ' });
});

test('rejects archive with more than 5000 files', async () => {
  const files: Record<string, Uint8Array> = { 'manifest.json': strToU8(JSON.stringify(VALID)) };
  for (let i = 0; i <= MAX_FILES; i += 1) files[`f/${i}.txt`] = strToU8('x');
  const res = await inspectArchive(new File([zipSync(files)], 'many.zip', { type: 'application/zip' }));
  expect(res).toEqual({ ok: false, error: 'В архиве больше 5000 файлов' });
});

test('semver pattern matches server rule', () => {
  expect(SEMVER_PATTERN.test('1.2.3')).toBe(true);
  expect(SEMVER_PATTERN.test('1.2.3-beta.1')).toBe(true);
  expect(SEMVER_PATTERN.test('1.2')).toBe(false);
  expect(SEMVER_PATTERN.test('v1.2.3')).toBe(false);
});
