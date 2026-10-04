import { unzipSync, strFromU8 } from 'fflate';

export const SEMVER_PATTERN = /^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$/;

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
  let data: Uint8Array;
  try {
    data = new Uint8Array(await file.arrayBuffer());
  } catch {
    return { ok: false, error: 'Не удалось прочитать файл' };
  }
  const entries: ArchiveEntry[] = [];
  let contents: Record<string, Uint8Array>;
  try {
    contents = unzipSync(data, {
      filter: (f) => {
        if (!f.name.endsWith('/')) entries.push({ path: f.name, size: f.originalSize });
        return f.name === 'manifest.json';
      },
    });
  } catch {
    return { ok: false, error: 'Файл не является корректным ZIP-архивом' };
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
