#!/usr/bin/env node
// Validate JSON files; print first syntax error with line/column and context.
// Usage: node json-validate.mjs <file-or-dir> [--fix-indent]

import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const SKIP_DIRS = new Set(['node_modules', '.git', 'dist', 'target', 'build']);
const args = process.argv.slice(2);
const fixIndent = args.includes('--fix-indent');
const target = args.find((a) => !a.startsWith('--'));

if (!target) {
  console.error('Usage: node json-validate.mjs <file-or-dir> [--fix-indent]');
  process.exit(2);
}

let st;
try {
  st = statSync(target);
} catch {
  console.error(`Path not found: ${target}`);
  process.exit(2);
}

function toLineCol(source, pos) {
  const before = source.slice(0, pos);
  const line = before.split('\n').length;
  const col = pos - before.lastIndexOf('\n');
  return { line, col };
}

function context(source, line, col) {
  const lines = source.split('\n');
  const text = lines[line - 1] ?? '';
  return `${text}\n${' '.repeat(Math.max(col - 1, 0))}^`;
}

// Node >=20 often omits "position N"; locate the culprit ourselves.
const SMELLS = [
  { re: /,(\s*[\]}])/g, hint: 'trailing comma before "$1"' },
  { re: /'[^']*'/g, hint: 'single-quoted string (use double quotes)' },
  { re: /[{,]\s*(\w+)\s*:/g, hint: 'unquoted key "$1"' },
  { re: /\/\/[^\n]*|\/\*[\s\S]*?\*\//g, hint: 'comment (JSON has none)' },
];

function locateError(source, message) {
  const pos = Number(/position (\d+)/.exec(message)?.[1] ?? NaN);
  const lineCol = /line (\d+) column (\d+)/.exec(message);
  if (!Number.isNaN(pos) && pos <= source.length) {
    return { lineCol: toLineCol(source, pos), hint: null };
  }
  if (lineCol) {
    return { lineCol: { line: Number(lineCol[1]), col: Number(lineCol[2]) }, hint: null };
  }
  let best = null;
  for (const smell of SMELLS) {
    smell.re.lastIndex = 0;
    const m = smell.re.exec(source);
    if (m && (best === null || m.index < best.index)) {
      best = { index: m.index, hint: smell.hint.replace('$1', m[1] ?? '') };
    }
  }
  if (best) {
    return { lineCol: toLineCol(source, best.index), hint: best.hint };
  }
  return null;
}

function validateFile(path) {
  const source = readFileSync(path, 'utf8');
  try {
    const parsed = JSON.parse(source);
    if (fixIndent) writeFileSync(path, JSON.stringify(parsed, null, 2) + '\n');
    return { ok: true, path };
  } catch (e) {
    const short = e.message.split(/ in JSON| is not valid JSON/)[0];
    const loc = locateError(source, e.message);
    if (!loc) return { ok: false, path, message: short };
    const { line, col } = loc.lineCol;
    const hint = loc.hint ? ` (${loc.hint})` : '';
    return {
      ok: false,
      path,
      message: `${short}${hint} at ${line}:${col}\n    ${context(source, line, col)}`,
    };
  }
}

function collectJsonFiles(dir) {
  const out = [];
  for (const entry of readdirSync(dir)) {
    if (entry.startsWith('.') && entry !== '.') continue;
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      if (!SKIP_DIRS.has(entry)) out.push(...collectJsonFiles(full));
    } else if (entry.toLowerCase().endsWith('.json')) {
      out.push(full);
    }
  }
  return out;
}

const files = st.isDirectory() ? collectJsonFiles(target) : [target];
let failed = 0;
for (const file of files) {
  const res = validateFile(file);
  if (res.ok) {
    console.log(`OK   ${file}`);
  } else {
    failed += 1;
    console.log(`FAIL ${file}: ${res.message}`);
  }
}
console.log(`--- ${files.length} file(s), ${failed} broken`);
process.exit(failed > 0 ? 1 : 0);
