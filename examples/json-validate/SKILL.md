---
name: json-validate
description: Use when JSON files or API responses fail to parse, when the user asks to validate or pretty-print JSON, or when checking config files for syntax errors
---

# JSON Validate

Find and show the exact position of the first syntax error in JSON files.

## When to Use

- "This JSON doesn't parse"
- Check a directory of config/fixture files before commit
- Pretty-print a minified response

## Usage

```bash
node <skill-dir>/scripts/json-validate.mjs <file-or-dir> [--fix-indent]
```

- `<file>` — validate one file, print `OK` or error with line/column and context
- `<dir>` — recursively validate all `*.json`, exit 1 if any file is broken
- `--fix-indent` — rewrite the file with 2-space indentation (only if valid)

## Output Example

```
FAIL config.json (trailing comma before "]") at 3:12
      "retries": 10,
                ^
```

## Common Mistakes

| Symptom | Cause |
|---------|-------|
| Trailing comma error | JSON (unlike JS objects) forbids `[1,2,]` |
| Single quotes | JSON strings use double quotes only |
| Unquoted keys | `{a: 1}` is invalid, use `{"a": 1}` |
| Comments `//` | JSON has no comments; use `.jsonc` tooling |

## Notes

- Skips `node_modules`, `.git`, `dist`, `target`
- Exit codes: 0 = all valid, 1 = at least one broken file, 2 = path not found
