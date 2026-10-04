# Favorites Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the list of the current user's favorited elements on a new `/favorites` page backed by `GET /api/me/favorites`.

**Architecture:** Backend extends `FavoriteRepositoryPort` with `findAllByUserId`, `SocialUseCase.favorites(viewer)` maps favorite element ids to visible `Element`s, `MeController` exposes the endpoint. UI adds `me.favorites()` API, `FavoritesPage` reusing `ElementCard`, route + auth-gated nav item.

**Tech Stack:** Spring Boot 3 (Flyway none needed, table exists), React 18 + MUI 6 + react-query, vitest.

**Spec:** `docs/superpowers/specs/2026-10-04-favorites-page-design.md`

## Global Constraints

- Build/test from repo root: `mvn -q test`; UI from `ui/`: `npx vitest run`, `npm run build`.
- UI copy in Russian: «Избранное», «Пока ничего в избранном».
- No code comments. Follow existing mock-based test patterns.
- `favorites` table and toggle endpoint already exist; only read path is new.

## File Structure

Backend:
- Modify: `src/main/java/com/skillhub/domain/port/FavoriteRepositoryPort.java`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/repository/JpaFavoriteRepository.java`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/JpaFavoriteRepositoryAdapter.java`
- Modify: `src/main/java/com/skillhub/domain/port/ElementRepositoryPort.java` (add findById)
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/JpaElementRepositoryAdapter.java`
- Modify: `src/main/java/com/skillhub/application/service/SocialUseCase.java`
- Modify: `src/main/java/com/skillhub/adapters/in/rest/MeController.java`
- Test: `src/test/java/com/skillhub/application/service/SocialUseCaseTest.java`

UI:
- Modify: `ui/src/api/me.ts`
- Create: `ui/src/pages/FavoritesPage.tsx`, `ui/src/pages/FavoritesPage.test.tsx`
- Modify: `ui/src/App.tsx`, `ui/src/layout/AppLayout.tsx`, `ui/src/layout/AppLayout.test.tsx`

---

### Task 1: Backend — GET /api/me/favorites

**Files:**
- Modify: `src/main/java/com/skillhub/domain/port/FavoriteRepositoryPort.java`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/repository/JpaFavoriteRepository.java`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/JpaFavoriteRepositoryAdapter.java`
- Modify: `src/main/java/com/skillhub/domain/port/ElementRepositoryPort.java`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/JpaElementRepositoryAdapter.java`
- Modify: `src/main/java/com/skillhub/application/service/SocialUseCase.java`
- Modify: `src/main/java/com/skillhub/adapters/in/rest/MeController.java`
- Test: `src/test/java/com/skillhub/application/service/SocialUseCaseTest.java`

**Interfaces:**
- Consumes: existing `favorites` table, `ElementRepositoryPort`, `AccessService.canRead`.
- Produces: `GET /api/me/favorites` → `200 [{slug, type, name, ..., team: string|null, visibility}]` (`ElementResponse[]`, newest first); hidden (TEAM, non-member) favorites are silently omitted.

- [ ] **Step 1: Write failing test**

In `src/test/java/com/skillhub/application/service/SocialUseCaseTest.java` (read it first; reuse its mock fields/setUp) add a test: two favorites saved by the user, one PUBLIC element and one TEAM element the viewer is not a member of; `favorites(viewer)` returns only the PUBLIC one. Example (adapt mock names to the file's existing style):

```java
    @Test
    void favoritesReturnOnlyVisibleElements() {
        Element pub = Element.builder().id(UUID.randomUUID()).slug("pub")
            .type(ElementType.SKILL).name("pub").description("").team(null)
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        Element hidden = Element.builder().id(UUID.randomUUID()).slug("hidden")
            .type(ElementType.SKILL).name("hidden").description("").team(team)
            .tags(new String[0]).visibility(Visibility.TEAM).author(owner)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(favorites.findAllByUserId(viewer.getId())).thenReturn(List.of(
            new Favorite(viewer.getId(), hidden.getId(), Instant.now()),
            new Favorite(viewer.getId(), pub.getId(), Instant.now())));
        when(elements.findById(pub.getId())).thenReturn(Optional.of(pub));
        when(elements.findById(hidden.getId())).thenReturn(Optional.of(hidden));
        when(membership.roleOf(any(), any())).thenReturn(Optional.empty());
        List<Element> result = useCase.favorites(viewer);
        assertThat(result).extracting(Element::getSlug).containsExactly("pub");
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `mvn -q test -Dtest='SocialUseCaseTest'`
Expected: FAIL — `findAllByUserId`/`findById` do not exist (compile error) or method missing.

- [ ] **Step 3: Implement**

`FavoriteRepositoryPort.java` — add:

```java
    List<Favorite> findAllByUserId(UUID userId);
```

`JpaFavoriteRepository.java` — add:

```java
    List<com.skillhub.adapters.out.jpa.entity.JpaFavorite> findByUserIdOrderByCreatedAtDesc(UUID userId);
```

`JpaFavoriteRepositoryAdapter.java` — add method (map each to `new Favorite(f.getUserId(), f.getElementId(), f.getCreatedAt())`):

```java
    @Override
    public List<Favorite> findAllByUserId(UUID userId) {
        return jpa.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(f -> new Favorite(f.getUserId(), f.getElementId(), f.getCreatedAt()))
            .toList();
    }
```

`ElementRepositoryPort.java` — add:

```java
    Optional<Element> findById(UUID id);
```

`JpaElementRepositoryAdapter.java` — implement with the JPA repository's existing `findById` (follow the file's mapping pattern, e.g. `jpa.findById(id).map(ElementJpaMapper::toDomain)`).

`SocialUseCase.java` — add constructor dependency `ElementRepositoryPort elements` (and `AccessService` if not already present; check the class), then:

```java
    @Transactional(readOnly = true)
    public List<Element> favorites(User viewer) {
        return favorites.findAllByUserId(viewer.getId()).stream()
            .map(f -> elements.findById(f.elementId()))
            .flatMap(Optional::stream)
            .filter(e -> access.canRead(e, viewer))
            .toList();
    }
```

`MeController.java` — inject `SocialUseCase` (or reuse existing dependency pattern) and add:

```java
    @GetMapping("/favorites")
    public List<ElementResponse> favorites(Authentication auth) {
        return socialUseCase.favorites(currentUser.resolve(auth)).stream()
            .map(ElementResponse::from)
            .toList();
    }
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test`
Expected: PASS (all unit tests incl. the new one)

- [ ] **Step 5: Commit**

```bash
git add src/main/java src/test/java
git commit -m "feat: GET /api/me/favorites endpoint"
```

---

### Task 2: UI — /favorites page and nav item

**Files:**
- Modify: `ui/src/api/me.ts`
- Create: `ui/src/pages/FavoritesPage.tsx`, Test: `ui/src/pages/FavoritesPage.test.tsx`
- Modify: `ui/src/App.tsx`, `ui/src/layout/AppLayout.tsx`, `ui/src/layout/AppLayout.test.tsx`

**Interfaces:**
- Consumes: `GET /api/me/favorites` → `ElementResponse[]` (Task 1); `ElementCard` props `{ element, avgRating?, ratingCount?, categoryNames? }`; `useAuth().authenticated`.
- Produces: route `/favorites`, nav link «Избранное» (authenticated only).

- [ ] **Step 1: Write failing tests**

Create `ui/src/pages/FavoritesPage.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import FavoritesPage from './FavoritesPage';

const { getMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
}));

vi.mock('../api/client', () => ({
  api: { get: getMock },
  toApiError: (e: unknown) => ({
    status: 0, code: 'ERROR',
    message: e instanceof Error ? e.message : String(e), details: null,
  }),
}));

vi.mock('../auth/KeycloakProvider', () => ({
  useAuth: () => ({ authenticated: true, token: 't', displayName: 'A', login: vi.fn(), logout: vi.fn() }),
}));

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <FavoritesPage />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

test('renders favorite elements', async () => {
  getMock.mockResolvedValue({
    data: [{ slug: 'my-skill', type: 'SKILL', name: 'My Skill', description: 'd',
      team: null, category: null, tags: [], visibility: 'PUBLIC',
      latestVersion: '1.0.0', downloadsCount: 1 }],
  });
  renderPage();
  expect(await screen.findByText('My Skill')).toBeInTheDocument();
  expect(screen.getByText('my-skill')).toBeInTheDocument();
});

test('shows empty state without favorites', async () => {
  getMock.mockResolvedValue({ data: [] });
  renderPage();
  expect(await screen.findByText('Пока ничего в избранном')).toBeInTheDocument();
});
```

In `ui/src/layout/AppLayout.test.tsx` add:

```tsx
test('shows favorites nav item when authenticated', () => {
  authState.authenticated = true;
  renderLayout();
  expect(screen.getByRole('link', { name: 'Избранное' })).toBeInTheDocument();
});

test('hides favorites nav item when not authenticated', () => {
  authState.authenticated = false;
  renderLayout();
  expect(screen.queryByRole('link', { name: 'Избранное' })).not.toBeInTheDocument();
});
```

- [ ] **Step 2: Run to verify failure**

Run: `npx vitest run src/pages/FavoritesPage.test.tsx src/layout/AppLayout.test.tsx`
Expected: FAIL — module/pages/nav item missing.

- [ ] **Step 3: Implement**

`ui/src/api/me.ts` — add import of the type and method:

```ts
import type { ElementResponse } from '../types';
```

and inside the `me` object:

```ts
  async favorites(): Promise<ElementResponse[]> {
    return (await api.get<ElementResponse[]>('/api/me/favorites')).data;
  },
```

Create `ui/src/pages/FavoritesPage.tsx`:

```tsx
import { Navigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Typography, Paper, Stack } from '@mui/material';
import FavoriteIcon from '@mui/icons-material/Favorite';
import { me as meApi } from '../api/me';
import { useAuth } from '../auth/KeycloakProvider';
import ElementCard from '../components/ElementCard';

export default function FavoritesPage() {
  const { authenticated } = useAuth();
  const { data: favorites } = useQuery({
    queryKey: ['favorites'],
    queryFn: meApi.favorites,
    enabled: authenticated,
  });

  if (!authenticated) return <Navigate to="/" replace />;

  return (
    <Paper sx={{ p: 3 }}>
      <Typography variant="h4" component="h1" sx={{ mb: 2 }}>Избранное</Typography>
      {(favorites ?? []).length === 0 ? (
        <Typography color="text.secondary">Пока ничего в избранном</Typography>
      ) : (
        favorites!.map((e) => <ElementCard key={e.slug} element={e} />)
      )}
    </Paper>
  );
}
```

`ui/src/App.tsx` — add import and route (inside the AppLayout group):

```tsx
import FavoritesPage from './pages/FavoritesPage';
```

```tsx
        <Route path="/favorites" element={<FavoritesPage />} />
```

`ui/src/layout/AppLayout.tsx` — in the nav items rendering, show «Избранное» only when authenticated. Change the items mapping source:

```tsx
            {[
              ...navItems,
              ...(authenticated ? [{ to: '/favorites', label: 'Избранное' }] : []),
              ...(isAdmin ? adminNavItems : []),
            ].map((item) => (
```

(replacing the existing `(isAdmin ? [...navItems, ...adminNavItems] : navItems).map(...)` line; keep the Button JSX inside unchanged).

- [ ] **Step 4: Run tests to verify pass**

Run: `npx vitest run`
Expected: PASS (all UI tests)

Run: `npm run build`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ui/src
git commit -m "feat: favorites page with nav entry"
```
