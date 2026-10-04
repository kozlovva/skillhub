# Design: Favorites Page

Date: 2026-10-04

## Problem

Users can favorite elements (heart on element page, stored in `favorites`
table), but nowhere see the list of their favorited elements, and there is
no endpoint to fetch it.

## Decision

Separate page «Избранное» at `/favorites` fed by a new
`GET /api/me/favorites` endpoint. Catalog filtering postponed.

## Backend

1. `FavoriteRepositoryPort.findAllByUserId(UUID userId)` → `List<Favorite>`.
2. `JpaFavoriteRepository.findByUserIdOrderByCreatedAtDesc(UUID userId)`;
   adapter maps to domain.
3. `SocialUseCase.favorites(User viewer)` → resolves elements by id
   (`ElementRepositoryPort.findById`), filters with `access.canRead`
   (element may have become TEAM-invisible), returns `List<Element>`.
4. `MeController`: `GET /api/me/favorites` → `List<ElementResponse>`
   (auth required, same as /api/me).

## UI

1. `api/me.ts`: `favorites(): Promise<ElementResponse[]>`.
2. New `FavoritesPage.tsx`: list of `ElementCard` (same as catalog),
   empty state «Пока ничего в избранном», query enabled only when
   authenticated; unauthenticated → redirect to `/`.
3. Route `/favorites` in `App.tsx`; nav item «Избранное» in AppLayout
   shown only when `authenticated`.

## Testing

- `SocialUseCaseTest`: favorites returns only elements visible to viewer.
- `FavoritesPage.test.tsx`: renders cards from API; empty state.
- `AppLayout.test.tsx`: «Избранное» nav item visible when authenticated,
  hidden otherwise.

## Out of Scope

- Catalog filter «только избранные».
