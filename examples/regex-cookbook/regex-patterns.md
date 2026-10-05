# Regex Patterns — Full Reference

Flavors covered: Java (`java.util.regex`), JavaScript (ES2018+), PCRE (grep -P).

## Character Classes

| Pattern | Meaning |
|---------|---------|
| `\d` `\w` `\s` | digit, word char `[A-Za-z0-9_]`, whitespace |
| `\D` `\W` `\S` | negated versions |
| `[abc]` `[^abc]` | set, negated set |
| `[a-z0-9_]` | range in set |
| `[\s\S]` | any char including newline |
| `.` | any char except newline (unless `s` flag) |

## Quantifiers

| Pattern | Meaning |
|---------|---------|
| `*` `+` `?` | 0+, 1+, 0 or 1 |
| `{2}` `{2,}` `{2,5}` | exact, 2+, 2..5 |
| `*?` `+?` `??` | lazy versions (stop at first match) |
| `*+` `++` | possessive (Java/PCRE): no backtracking |

## Anchors and Boundaries

| Pattern | Meaning | Note |
|---------|---------|------|
| `^` `$` | start/end of line (multiline flag) or string | differs by flavor flags |
| `\A` `\z` | absolute string start/end | Java: `\z`, JavaScript: no `\z`, use `$` without `m` |
| `\b` `\B` | word boundary / not boundary | `-` inside a "word" breaks `\b` |
| `\K` | reset match start (PCRE) | not in JavaScript |

## Groups

| Pattern | Meaning |
|---------|---------|
| `(...)` | capturing group |
| `(?:...)` | non-capturing group |
| `(?<name>...)` | named group (Java: `(?<name>...)`, JS same) |
| `\1` / `$1` | backreference / replacement reference |
| `(?=\...)` `(?!...)` | lookahead positive / negative |
| `(?<=...)` `(?<!...)` | lookbehind positive / negative (JS: ES2018+) |

## Flags

| Flag | Java | JavaScript | Meaning |
|------|------|------------|---------|
| case-insensitive | `CASE_INSENSITIVE` | `i` | |
| multiline `^$` per line | `MULTILINE` | `m` | |
| dot matches newline | `DOTALL` | `s` | |
| unicode | `UNICODE_CASE` | `u` | |
| global replace | loop | `g` | Java uses `replaceAll` |

## Recipes

### Strict IPv4
```
\b(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}\b
```

### ISO 8601 date (strict, real month/day ranges)
```
\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])
```

### Email (practical, not RFC-complete)
```
^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$
```

### Java package and class from import line
```
^import\s+(?:static\s+)?([\w.]+)\.(\w+|*);
```
group 1 = package, group 2 = class or `*`

### Key=value with quoted values
```
(\w[\w.-]*)=("(?:[^"\\]|\\.)*"|'[^']*'|\S+)
```

### Stack trace frame (Java)
```
at\s+([\w.$]+)\.([\w$<>]+)\(([\w.]+):?(\d+)?\)
```
groups: class, method, file, line

### Extract JSON string field (flat, one level)
```
"key"\s*:\s*"((?:[^"\\]|\\.)*)"
```

## Escaping Rules by Context

| Context | Digit class | Backslash itself |
|---------|-------------|------------------|
| grep -E | `[0-9]` or `\d` (grep -P) | `\\` |
| Java string literal | `"\\d"` | `"\\\\"` |
| JavaScript literal | `/\d/` | `/\\/` |
| JSON config value | `"\\d"` | `"\\\\"` |

## Performance

- Catastrophic backtracking comes from nested quantifiers: `(a+)+`, `(.*)*` — rewrite with possessive or atomic groups where supported
- Prefer `[^"]*` over `.*?` inside quotes — faster and clearer
- Anchor patterns when possible; unanchored scans the whole input
- In Java, reuse compiled `Pattern` objects; compiling per call is slow
