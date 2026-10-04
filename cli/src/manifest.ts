import { readFileSync } from 'node:fs';
import { join } from 'node:path';

const VALID_TYPES = ['SKILL', 'SCRIPT', 'AGENT', 'HOOK', 'PACK', 'OTHER'];
const SEMVER = /^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/;

export class ManifestError extends Error {
  constructor(public problems: string[]) {
    super('Invalid manifest.json:\n  - ' + problems.join('\n  - '));
  }
}

export interface Manifest {
  name: string;
  slug?: string;
  version: string;
  description?: string;
  type: string;
  team?: string;
  category?: string;
  tags?: string[];
  changelog?: string;
  visibility?: 'PUBLIC' | 'TEAM';
}

export function deriveSlug(name: string): string {
  return name
    .trim()
    .toLowerCase()
    .split(/\s+/)
    .map((word) => word.replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '') || '-')
    .join('-');
}

export function parseManifest(raw: unknown): Manifest {
  const problems: string[] = [];
  const m = (typeof raw === 'object' && raw !== null ? raw : {}) as Record<string, unknown>;

  const name = typeof m.name === 'string' ? m.name.trim() : '';
  if (!name) problems.push('name is required');

  const version = typeof m.version === 'string' ? m.version.trim() : '';
  if (!SEMVER.test(version)) problems.push('version must be semver (e.g. 1.2.3)');

  const type = typeof m.type === 'string' ? m.type : '';
  if (!VALID_TYPES.includes(type)) {
    problems.push(`type must be one of: ${VALID_TYPES.join(', ')}`);
  }

  const visibility = m.visibility as string | undefined;
  if (visibility !== undefined && visibility !== 'PUBLIC' && visibility !== 'TEAM') {
    problems.push('visibility must be PUBLIC or TEAM');
  }

  if (problems.length > 0) throw new ManifestError(problems);

  return {
    name,
    slug: typeof m.slug === 'string' ? m.slug : undefined,
    version,
    description: typeof m.description === 'string' ? m.description : undefined,
    type,
    team: typeof m.team === 'string' ? m.team : undefined,
    category: typeof m.category === 'string' ? m.category : undefined,
    tags: Array.isArray(m.tags) ? (m.tags as string[]) : undefined,
    changelog: typeof m.changelog === 'string' ? m.changelog : undefined,
    visibility: visibility as Manifest['visibility'],
  };
}

export function readManifest(dir: string): Manifest {
  const path = join(dir, 'manifest.json');
  let raw: unknown;
  try {
    raw = JSON.parse(readFileSync(path, 'utf8'));
  } catch (e) {
    throw new ManifestError([`cannot read ${path}: ${(e as Error).message}`]);
  }
  return parseManifest(raw);
}
