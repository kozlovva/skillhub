#!/usr/bin/env node
// Count files and lines of code grouped by extension.
// Usage: node code-stats.mjs [dir]

import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

const SKIP_DIRS = new Set([
  'node_modules', '.git', 'dist', 'target', 'build', '.next', 'coverage',
]);
const SKIP_EXT = new Set([
  '.png', '.jpg', '.jpeg', '.gif', '.svg', '.ico', '.webp',
  '.zip', '.jar', '.gz', '.tar', '.woff', '.woff2', '.ttf', '.eot',
  '.pdf', '.exe', '.dll', '.class', '.lock',
]);

function collect(dir, stats) {
  for (const entry of readdirSync(dir)) {
    if (entry.startsWith('.') && entry !== '.') continue;
    const full = join(dir, entry);
    let st;
    try {
      st = statSync(full);
    } catch {
      continue; // broken symlink or vanished file
    }
    if (st.isDirectory()) {
      if (!SKIP_DIRS.has(entry)) collect(full, stats);
      continue;
    }
    const dot = entry.lastIndexOf('.');
    const ext = dot > 0 ? entry.slice(dot).toLowerCase() : '(no ext)';
    if (SKIP_EXT.has(ext)) continue;
    let lines = 0;
    try {
      lines = readFileSync(full, 'utf8').split('\n').length;
    } catch {
      continue; // not valid utf8 — treat as binary
    }
    const s = stats.get(ext) ?? { files: 0, lines: 0 };
    s.files += 1;
    s.lines += lines;
    stats.set(ext, s);
  }
}

const dir = process.argv[2] ?? '.';
let root;
try {
  root = statSync(dir);
} catch {
  console.error(`Directory not found: ${dir}`);
  process.exit(1);
}
if (!root.isDirectory()) {
  console.error(`Not a directory: ${dir}`);
  process.exit(1);
}

const stats = new Map();
collect(dir, stats);

const rows = [...stats.entries()].sort((a, b) => b[1].lines - a[1].lines);
const wExt = Math.max(...rows.map(([e]) => e.length), 3);
const wFiles = Math.max(...rows.map(([, s]) => String(s.files).length), 5);
const wLines = Math.max(...rows.map(([, s]) => String(s.lines).length), 5);

console.log(
  'ext'.padEnd(wExt + 2) + 'files'.padStart(wFiles + 2) + 'lines'.padStart(wLines + 2),
);
let totalFiles = 0;
let totalLines = 0;
for (const [ext, s] of rows) {
  totalFiles += s.files;
  totalLines += s.lines;
  console.log(
    ext.padEnd(wExt + 2) + String(s.files).padStart(wFiles + 2) + String(s.lines).padStart(wLines + 2),
  );
}
console.log(
  'total'.padEnd(wExt + 2) + String(totalFiles).padStart(wFiles + 2) + String(totalLines).padStart(wLines + 2),
);
