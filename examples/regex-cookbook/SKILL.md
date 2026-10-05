---
name: regex-cookbook
description: Use when writing or debugging regular expressions, when a pattern does not match as expected, or when extracting structured data from text and logs
---

# Regex Cookbook

Common regex recipes for Java, JavaScript and grep. Full pattern reference: `regex-patterns.md` in this directory.

## Quick Reference

| Task | Pattern | Note |
|------|---------|------|
| IPv4 | `\b\d{1,3}(\.\d{1,3}){3}\b` | validate separately, this matches loosely |
| ISO date | `\d{4}-\d{2}-\d{2}` | see reference for strict validation |
| Key=value | `(\w+)=("[^"]*"|\S+)` | group 1 key, group 2 value |
| Log level | `\b(ERROR\|WARN\|INFO\|DEBUG)\b` | grep -P / Java |
| Tagged log line | `^\[?([\d:.\- ]+)\]?\s+(\w+)\s+(.*)$` | ts, level, message |
| Trailing whitespace | `[ \t]+$` | multiline flag |
| Duplicate words | `\b(\w+)\s+\1\b` | case-sensitive; add `i` flag to ignore |
| Currency | `[$€₽]\s?\d[\d,]*\.?\d{0,2}` | adjust symbols per locale |

## Debugging a Non-Matching Pattern

1. Paste the pattern into regex101.com (select the right flavor)
2. Check the flavor: `\d` works everywhere, but lookbehind `(?<=...)` is
   unsupported in older JavaScript and Safari
3. Escaping: in Java string literals `\\d` — in JSON configs `\\\\d`
4. Anchors: add `^...$` to rule out partial matches
5. Greedy vs lazy: `.*` eats too much? Use `.*?`

## Common Mistakes

| Mistake | Reality |
|---------|---------|
| Regex for nested JSON/XML | Use a parser; regex cannot count brackets |
| `\bword\b` with word containing `-` | Hyphen breaks word boundary; use lookarounds |
| Testing only the happy path | Test empty string, no match, partial match |
| `.` expecting newlines to match | Add `s` flag (dotall) or use `[\s\S]` |
