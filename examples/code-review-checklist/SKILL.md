---
name: code-review-checklist
description: Use when reviewing a merge request or pull request, when asked to review code, or when the user says "review this", "посмотри MR", "check my PR"
---

# Code Review Checklist

Review in two passes: first correctness, then quality. Never mix them — style nitpicks hide logic bugs.

## Pass 1: Correctness

1. **Read the description first.** What is this change supposed to do?
2. **Trace the main flow** — follow the happy path end to end.
3. **Check error paths:** what happens on null, empty, network failure, timeout?
4. **Check boundaries:** off-by-one, empty collections, zero, negative, huge input.
5. **Check concurrency:** shared state, race conditions, locks.
6. **Check security:** injection, unsanitized output, secrets in code, missing auth.

## Pass 2: Quality

- Naming: does the name lie about behavior?
- Duplication: copy-pasted logic that will diverge?
- Tests: do they test behavior or implementation details?
- Dead code, commented-out code, TODO without ticket.

## Verdict Format

```
BLOCKING:    must fix before merge (bugs, security, data loss)
SUGGESTION:  better way, author's call
NIT:         cosmetic, batch or skip
```

Every blocking comment must cite file:line and a failing scenario.

## Red Flags - do not approve

- You cannot explain what a changed function does after reading it twice
- Tests were deleted or weakened to make the build pass
- New dependency added for a one-line utility
- The diff is huge and the description is one line — ask to split it first
