---
name: code-stats
description: Use when the user asks for lines of code count, project size overview, file statistics by extension, or "how big is this codebase"
---

# Code Stats

Count files and lines of code grouped by extension.

## When to Use

- "How big is this codebase?"
- "Show lines of code by file type"
- "Which extensions dominate the project?"
- Before refactoring, to see what you are dealing with

## Usage

Run the bundled script from the project root:

```bash
node <skill-dir>/scripts/code-stats.mjs [dir]
```

`dir` defaults to the current directory. Node >= 18 required.

## Output

```
ext        files      lines
.ts         120      24500
.tsx         45       9800
.json        30       3200
--- total: 195 files, 37500 lines
```

## Notes

- Skips `node_modules`, `.git`, `dist`, `target`, `build` and dot-directories
- Binary extensions (images, archives) are skipped by list, not by sniffing
- Exit code 0 on success, 1 if the directory does not exist

## Common Mistakes

| Mistake | Fix |
|---------|-----|
| Running from wrong cwd | Pass the project dir as the first argument |
| Missing counts for new binary types | Add the extension to `SKIP_EXT` in the script |
