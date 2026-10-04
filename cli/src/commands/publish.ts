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
}

export async function runPublish(
  dir: string,
  cfg: SkillhubConfig,
  deps: PublishDeps = {},
): Promise<{ slug: string; version: string }> {
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
      console.log(`Created element ${slug}`);
    } else {
      throw e;
    }
  }

  const zip = zipDirectory(dir);
  await client.publishVersion(slug, zip, manifest.changelog);
  console.log(`Published ${slug}@${manifest.version}`);
  return { slug, version: manifest.version };
}

export function register(program: Command): void {
  program
    .command('publish')
    .description('Publish a directory as a new element version')
    .argument('<dir>', 'directory containing manifest.json')
    .option('--json', 'machine-readable output')
    .action(async (dir: string, opts: { json?: boolean }) => {
      const result = await runPublish(dir, resolveConfig());
      if (opts.json) console.log(JSON.stringify(result, null, 2));
    });
}
