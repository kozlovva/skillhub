# Design: Element Upload Page (`/upload`)

Date: 2026-10-04

## Problem

The API client already supports creating elements and publishing versions
(`elements.create`, `elements.publishVersion` in `ui/src/api/elements.ts`),
but there is no UI page for uploading elements. Users cannot create or
publish elements through the interface.

## Goal

A single page at `/upload` where an authorized user creates an element and
uploads its first version file, then is redirected to the element page.

## Decisions

- Single combined form (metadata + file), not a wizard.
- Route `/upload`, entry button «Загрузить элемент» in AppLayout, visible
  only to authenticated users.
- Team is selected from the user's teams via `me.get()`.
- Any file is accepted (typically `.zip`); format validation is left to the
  backend.
- Create-only page; publishing subsequent versions stays out of scope.

## Route and Entry Point

- `App.tsx`: add `<Route path="/upload" element={<UploadPage />} />` inside
  the AppLayout route group.
- `AppLayout.tsx`: add an «Загрузить элемент» navigation button, shown only
  when the user is authenticated (same auth signal already used by the
  layout).

## Form Fields

| Field       | Control            | Required | Source / validation                          |
|-------------|--------------------|----------|----------------------------------------------|
| slug        | TextField          | yes      | auto-generated from name (lowercase, hyphens), pattern `^[a-z0-9]+(-[a-z0-9]+)*$` |
| name        | TextField          | yes      | free text                                    |
| type        | Select             | yes      | `ElementType`: SKILL, SCRIPT, AGENT, HOOK, PACK, OTHER |
| team        | Select             | yes      | `me.get().teams` (slug + name)               |
| description | TextField (multiline) | no    | free text                                    |
| category    | Select             | no       | `categories.list()`                          |
| tags        | Chips input        | no       | array of strings                             |
| visibility  | Select             | yes      | PUBLIC / TEAM                                |
| file        | native file input (MUI wrapper) | yes | any file                            |
| changelog   | TextField          | no       | sent as query param to publishVersion        |

## Data Flow

1. On mount, load `me.get()` and `categories.list()` in parallel.
2. On submit:
   - `elements.create({ slug, type, name, description?, team, category?, tags?, visibility })`
   - `elements.publishVersion(slug, file, changelog?)`
   - `navigate('/elements/' + slug)` and success snackbar via SnackbarContext.
3. API errors are shown via SnackbarContext; field-level errors (e.g. slug
   already taken) are displayed under the corresponding field.

## Authorization

Unauthenticated users are redirected away from `/upload` (to catalog or
login) using the same auth mechanism as other personal pages.

## Error Handling

- Backend validation errors mapped to form fields where possible
  (400 BAD_REQUEST → message under the form / relevant field).
- Create succeeded but version upload failed: user stays on the page with
  an error snackbar explaining the element was created but the version
  failed; retry of publishVersion alone is attempted from the same form
  (element already exists → the create step is skipped on retry).

## Testing

`ui/src/pages/UploadPage.test.tsx` (pattern: existing page tests):
- renders all fields; loads teams and categories on mount;
- slug auto-generation from name;
- validation errors on empty required fields / bad slug;
- submit calls `elements.create` then `elements.publishVersion` and
  navigates to `/elements/:slug`;
- API error is surfaced via snackbar.

AppLayout test updated for the new button (visible when authenticated,
hidden otherwise).

## Out of Scope

- Publishing subsequent versions from this page (separate follow-up).
- Drag-and-drop file upload, file size checks on the client.
