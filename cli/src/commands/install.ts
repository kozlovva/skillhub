import AdmZip from 'adm-zip';
import { mkdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { createInterface } from 'node:readline/promises';
import type { Command } from 'commander';
import { ApiClient, type ElementInfo } from '../api';
import { resolveConfig, type SkillhubConfig } from '../config';
import { detectTargets } from '../detect-target';
import { readLockfile, writeLockfile, type LockFile } from '../lockfile';

export interface InstallResult {
  slug: string;
  version: string;
  action: 'already' | 'install' | 'update';
  targets: string[];
}

export function parseRef(ref: string): { slug: string; version?: string } {
  const at = ref.lastIndexOf('@');
  if (at <= 0) return { slug: ref };
  const version = ref.slice(at + 1);
  if (version === 'latest') return { slug: ref.slice(0, at) };
  return { slug: ref.slice(0, at), version };
}

export function decideVersion(
  lock: LockFile,
  slug: string,
  version: string,
  force: boolean,
): { action: 'already' | 'install' | 'update'; version: string; from?: string } {
  const prev = lock.packages[slug];
  if (prev && prev.version === version) {
    if (force) return { action: 'update', version, from: prev.version };
    return { action: 'already', version };
  }
  if (prev) return { action: 'update', version, from: prev.version };
  return { action: 'install', version };
}

export function extractArchive(zip: Buffer, destDir: string): void {
  new AdmZip(zip).extractAllTo(destDir, true);
}

export interface InstallDeps {
  client?: ApiClient;
  picker?: (targets: { platform: string; dir: string }[]) => { platform: string; dir: string }[];
  cwd?: string;
}

async function chooseInteractively(targets: { platform: string; dir: string }[]) {
  const rl = createInterface({ input: process.stdin, output: process.stdout });
  const list = targets.map((t, i) => `${i + 1}. ${t.platform} → ${t.dir}`).join('\n');
  const answer = await rl.question(`Where to install?\n${list}\n> `);
  rl.close();
  const idx = parseInt(answer, 10) - 1;
  const chosen = targets[idx];
  if (!chosen) throw new Error('Invalid choice');
  return [chosen];
}

export async function runInstall(
  ref: string,
  opts: { target?: string; all?: boolean; force?: boolean; json?: boolean },
  cfg: SkillhubConfig,
  deps: InstallDeps = {},
): Promise<InstallResult> {
  const cwd = deps.cwd ?? process.cwd();
  const client = deps.client ?? new ApiClient(cfg.url, cfg.token);
  const choose = deps.picker ?? chooseInteractively;
  const { slug, version: refVersion } = parseRef(ref);

  const element: ElementInfo = await client.getElement(slug);
  const isPack = element.type === 'PACK';
  const version =
    refVersion && refVersion !== 'latest' ? refVersion : element.latestVersion;
  if (!version) throw new Error(`Element ${slug} has no published versions`);

  const lock = readLockfile(cwd);
  const decision = decideVersion(lock, slug, version, opts.force ?? false);
  if (decision.action === 'already' && !opts.force) {
    if (!opts.json) {
      console.log(`${slug}@${version} is already installed (use --force to reinstall)`);
    }
    return { slug, version, action: 'already', targets: [] };
  }

  const bytes = isPack
    ? await client.downloadPackArchive(slug, version)
    : await client.downloadElementArchive(slug, version);

  let targets = opts.target
    ? [{ platform: 'custom', dir: resolve(opts.target) }]
    : detectTargets(cwd);
  if (targets.length === 0) {
    targets = [
      { platform: 'opencode', dir: join(cwd, '.opencode', 'skills') },
      { platform: 'claude', dir: join(cwd, '.claude', 'skills') },
    ];
    if (!opts.all) targets = await choose(targets);
  } else if (targets.length > 1 && !opts.all && !opts.target) {
    targets = await choose(targets);
  }

  // Pack zips already contain a top-level <pack-slug>/ directory, so extract
  // them directly into the target dir to avoid a doubled slug in the path.
  const installed: string[] = [];
  for (const target of targets) {
    const dest = isPack ? target.dir : join(target.dir, slug);
    mkdirSync(dest, { recursive: true });
    extractArchive(bytes, dest);
    installed.push(dest);
    if (!opts.json) console.log(`Installed ${slug}@${version} → ${dest}`);
  }

  const entry: LockFile['packages'][string] = { version, installedAt: new Date().toISOString() };
  if (isPack) entry.kind = 'pack';
  writeLockfile(cwd, {
    packages: { ...lock.packages, [slug]: entry },
  });

  if (decision.action === 'update' && decision.from !== version && !opts.json) {
    console.log(`Updated ${slug}: ${decision.from} → ${version}`);
  }
  return { slug, version, action: decision.action, targets: installed };
}

export function register(program: Command): void {
  program
    .command('install')
    .description('Install an element or pack into project skill directories')
    .argument('<ref>', 'element or pack slug[@version]')
    .option('--target <dir>', 'install into this directory instead of auto-detect')
    .option('--all', 'install into all detected platforms')
    .option('--force', 'reinstall even if same version present')
    .option('--json', 'machine-readable output')
    .action(async (ref: string, opts) => {
      const result = await runInstall(ref, opts, resolveConfig());
      if (opts.json) console.log(JSON.stringify(result, null, 2));
    });
}
