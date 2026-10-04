import { unzipSync, strFromU8 } from 'fflate';

export const SEMVER_PATTERN = /^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$/;
export const MAX_ARCHIVE_BYTES = 50 * 1024 * 1024;
export const MAX_ENTRY_BYTES = 200 * 1024 * 1024;
export const MAX_FILES = 5000;

export interface ArchiveEntry {
  path: string;
  size: number;
}

export interface ManifestInfo {
  name: string;
  version: string;
  description: string;
  type: string;
}

export type ArchiveParseResult =
  | { ok: true; manifest: ManifestInfo; entries: ArchiveEntry[] }
  | { ok: false; error: string };

export async function inspectArchive(file: File): Promise<ArchiveParseResult> {
  if (file.size > MAX_ARCHIVE_BYTES) {
    return { ok: false, error: 'Архив больше 50 МБ' };
  }
  let data: Uint8Array;
  try {
    data = new Uint8Array(await file.arrayBuffer());
  } catch {
    return { ok: false, error: 'Не удалось прочитать файл' };
  }
  const entries: ArchiveEntry[] = [];
  let entryTooBig = false;
  let contents: Record<string, Uint8Array>;
  try {
    contents = unzipSync(data, {
      filter: (f) => {
        if (f.originalSize > MAX_ENTRY_BYTES) {
          entryTooBig = true;
          return false;
        }
        if (!f.name.endsWith('/')) entries.push({ path: f.name, size: f.originalSize });
        return f.name === 'manifest.json';
      },
    });
  } catch {
    return { ok: false, error: 'Файл не является корректным ZIP-архивом' };
  }
  if (entryTooBig) {
    return { ok: false, error: 'Распакованное содержимое архива превышает 200 МБ' };
  }
  if (entries.length > MAX_FILES) {
    return { ok: false, error: 'В архиве больше 5000 файлов' };
  }
  const raw = contents['manifest.json'];
  if (!raw) return { ok: false, error: 'В корне архива нет manifest.json' };
  let obj: Record<string, unknown>;
  try {
    obj = JSON.parse(strFromU8(raw));
  } catch {
    return { ok: false, error: 'manifest.json не является корректным JSON' };
  }
  if (!obj || typeof obj !== 'object') {
    return { ok: false, error: 'manifest.json не является корректным JSON' };
  }
  const name = typeof obj.name === 'string' ? obj.name.trim() : '';
  if (!name) return { ok: false, error: 'manifest.json: поле name обязательно' };
  const version = typeof obj.version === 'string' ? obj.version.trim() : '';
  if (!version) return { ok: false, error: 'manifest.json: поле version обязательно' };
  if (!SEMVER_PATTERN.test(version)) {
    return { ok: false, error: 'manifest.json: version должна быть в формате semver, например 1.2.3' };
  }
  const description = typeof obj.description === 'string' ? obj.description : '';
  const type = typeof obj.type === 'string' ? obj.type : '';
  return { ok: true, manifest: { name, version, description, type }, entries };
}
