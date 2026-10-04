import AdmZip from 'adm-zip';
import type { Command } from 'commander';
import { ApiClient, ApiError } from '../api';
import { resolveConfig, type SkillhubConfig } from '../config';
import { deriveSlug, readManifest } from '../manifest';

export function zipDirectory(dir: string): Buffer {
  const zip = new AdmZip();
  zip.addLocalFolder(dir);
  return zip.toBuffer();
}

export interface PublishDeps {
  client?: ApiClient;
  json?: boolean;
}

export async function runPublish(
  dir: string,
  cfg: SkillhubConfig,
  deps: PublishDeps = {},
): Promise<{ slug: string; version: string }> {
  const json = deps.json ?? false;
  if (!cfg.token) {
    throw new Error('Not logged in. Run `skillhub login` or set SKILLHUB_TOKEN.');
  }
  const manifest = readManifest(dir);
  const client = deps.client ?? new ApiClient(cfg.url, cfg.token);
  const slug = manifest.slug ?? deriveSlug(manifest.name);
  if (!slug || /^-+|-+$/.test(slug)) {
    throw new Error(
      `Cannot derive slug from name "${manifest.name}" — set "slug" in manifest.json`,
    );
  }

  try {
    await client.getElement(slug);
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) {
      await client.createElement({
        slug,
        type: manifest.type,
        name: manifest.name,
        description: manifest.description,
        team: manifest.team,
        category: manifest.category,
        tags: manifest.tags,
        visibility: manifest.visibility ?? 'PUBLIC',
      });
      if (!json) console.log(`Created element ${slug}`);
    } else {
      throw e;
    }
  }

  const zip = zipDirectory(dir);
  await client.publishVersion(slug, zip, manifest.changelog);
  if (!json) console.log(`Published ${slug}@${manifest.version}`);
  if (json) console.log(JSON.stringify({ slug, version: manifest.version }, null, 2));
  return { slug, version: manifest.version };
}

export function register(program: Command): void {
  program
    .command('publish')
    .description('Publish a directory as a new element version')
    .argument('<dir>', 'directory containing manifest.json')
    .option('--json', 'machine-readable output')
    .action(async (dir: string, opts: { json?: boolean }) => {
      await runPublish(dir, resolveConfig(), { json: opts.json });
    });
}
