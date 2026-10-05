---
name: commit-messages
description: Use when writing or amending a git commit message, when the user asks to commit, or when commit messages in history are inconsistent
---

# Commit Messages

Follow Conventional Commits. Template lives in `templates/commit-template.txt` next to this file.

## Format

```
<type>(<scope>): <subject in imperative, max 72 chars>

<body: why, not what; wrap at 100 chars>
<footer: BREAKING CHANGE, refs>
```

## Types

| type | when |
|------|------|
| feat | new user-visible behavior |
| fix | bug fix |
| refactor | no behavior change |
| perf | performance fix |
| test | tests only |
| docs | documentation |
| chore | build, deps, tooling |

## Rules

- Subject: imperative mood ("add", not "added" / "adds"), no trailing period
- Body explains WHY the change was needed; the diff already shows what
- One logical change per commit — if the body needs "and", split the commit

## Examples

```
fix(auth): refresh token before API call instead of after

The old flow called the API with an already-expired token on slow
connections, causing intermittent 401s. Refresh first, then call.

Closes: FCS-1234
```

```
refactor(packs): extract archive copying into copyElementArchive

No behavior change; prepares for streaming large pack archives.
```

## Common Mistakes

| Mistake | Fix |
|---------|-----|
| "fix bug" as subject | Say what bug, where, and symptom |
| Body retells the diff | Body explains motivation and tradeoffs |
| Mixed feature + reformat in one commit | Split: reformat first, feature second |
