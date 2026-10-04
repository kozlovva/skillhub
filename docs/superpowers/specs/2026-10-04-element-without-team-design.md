# Design: Element Without Team

Date: 2026-10-04

## Problem

A team is currently mandatory for elements at every layer: `@NotBlank` on
`CreateElementRequest.team`, `team_id NOT NULL` in the `elements` table,
publication rights checked via team membership, and the UI team select
cannot be cleared once a team is chosen. But an element may legitimately
belong to no team (a personal element).

## Decisions

- Team becomes optional end-to-end (DB, domain, REST, UI).
- Any authenticated user may create an element without a team.
- An element without a team must have visibility `PUBLIC` — `TEAM`
  visibility without a team is rejected with 422 (meaningless without a
  team).
- New versions of a team-less element may be published by its **author or
  an admin** (no team roles exist to check).
- S3 key for team-less element versions: `personal/<elementSlug>/<version>.zip`
  (element slugs are globally unique, so no collision).
- UI: team select gets a «Без команды» empty option (clearable at any
  time); while no team is selected, the `TEAM` visibility option is
  disabled and visibility is forced to `PUBLIC`.

## Backend Changes

1. **Migration** `V4__element_without_team.sql`:
   `ALTER TABLE elements ALTER COLUMN team_id DROP NOT NULL;`
2. **`JpaElement`**: drop `nullable = false` from the `team_id` join column.
3. **`TeamJpaMapper`**: null guards in `toDomain`/`toEntity` (return null
   for null input) so `ElementJpaMapper` handles team-less elements.
4. **`CreateElementRequest`**: remove `@NotBlank` from `team`.
5. **`ElementUseCase.create`**: `teamSlug == null` → team not set, no
   `canPublish` check (any authenticated user); `visibility == TEAM`
   without `teamSlug` → `UnprocessableException` (422).
6. **`AccessService`**: new method
   `canPublishPersonal(Element, User)` — true for admin or the element's
   author.
7. **`VersionUseCase.publish`**: if `element.getTeam() == null` → check
   `canPublishPersonal`, S3 key `personal/<slug>/<version>.zip`; otherwise
   existing team logic unchanged.
8. **`ElementResponse.from`**: `team` is null when `e.getTeam()` is null.

Search visibility SQL (`visibility = 'PUBLIC' OR team_id IN (...)`) already
works: team-less elements are always PUBLIC.

## UI Changes

1. `types.ts`: `ElementResponse.team: string | null`.
2. `api/elements.ts`: `create` body `team?: string`.
3. `UploadPage`: team select optional with empty option «Без команды»
   (selecting it clears the team); when team is empty the `TEAM` visibility
   option is disabled and visibility resets to `PUBLIC`; submit sends
   `team: undefined` when empty.
4. `ElementPage.canPublish`: team-less elements → show publish form to any
   authenticated user (backend enforces author/admin).
5. `ElementCard`: render «Личный» instead of empty when team is null.

## Error Handling

- 422 for `TEAM` visibility without team (consistent with existing enum
  validation → 422 mapping).

## Testing

- `ElementUseCaseTest`: create without team succeeds (team null, no team
  lookup); `TEAM` visibility without team → `UnprocessableException`.
- `VersionUseCaseTest`: author publishes team-less element → S3 key
  `personal/my-skill/1.0.0.zip`; non-author → `ForbiddenException`.
- `UploadPage.test.tsx`: team can be cleared after selection; `TEAM`
  option disabled without team; submit without team calls create with
  `team: undefined`.

## Out of Scope

- Changing team-based access for existing team elements.
- Editing team/visibility of existing elements (no update endpoint).
