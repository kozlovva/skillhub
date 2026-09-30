# SkillHub Web UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Веб-UI корпоративного хранилища: каталог с поиском/фильтрами/фасетами, карточки элементов с версиями и файлами, паки, команды, категории, управление API-токенами.

**Architecture:** SPA на React 18 + TypeScript + Vite. Данные — через REST API backend'а (план `docs/superpowers/plans/2026-09-30-skillhub-backend.md`), кэш и загрузки — React Query, маршрутизация — React Router, компоненты — MUI. Аутентификация — Keycloak (OIDC, Authorization Code + PKCE) через `keycloak-js`; Bearer-токен добавляется ко всем запросам. Для CLI-токенов — отдельная страница, требующая нового backend-endpoint `/api/tokens` (добавляется Task 3 этого плана).

**Tech Stack:** React 18, TypeScript, Vite, MUI 6, React Query 5, React Router 6, keycloak-js, axios, Vitest + React Testing Library.

**Спека:** `docs/superpowers/specs/2026-09-30-skillhub-design.md`

## Global Constraints

- Директория проекта: `ui/` в корне репозитория.
- Node 20+, npm. Все команды запускаются из `ui/`.
- Backend base URL: `VITE_API_URL` (default `http://localhost:8080`).
- OIDC: `VITE_OIDC_URL` (issuer, напр. `https://keycloak.example.com/realms/skillhub`), `VITE_OIDC_CLIENT_ID=skillhub-ui`.
- API-контракты копируются verbatim из backend-плана: `ElementResponse {slug, type, name, description, team, category, tags, visibility, latestVersion, downloadsCount}`, `VersionResponse {version, status, changelog, sizeBytes, files:[{path,size}]}`, поиск `{items, total, facetsByType}`, social `{avgRating, ratingCount, favorited}`.
- Ошибки backend: `{code, message, details}` — показывать `message` в snackbar.
- TDD: тест до реализации (Vitest + RTL). Компонентные тесты мокают api-модули через `vi.mock`.
- Коммит после каждой задачи, conventional commits.

## File Structure (итоговая)

```
ui/
├── package.json, vite.config.ts, index.html, tsconfig.json
├── src/
│   ├── main.tsx, App.tsx
│   ├── types.ts
│   ├── api/
│   │   ├── client.ts (axios + Bearer interceptor)
│   │   ├── elements.ts, packs.ts, categories.ts, teams.ts, search.ts, social.ts, tokens.ts
│   ├── auth/
│   │   ├── KeycloakProvider.tsx, useAuth.ts
│   ├── layout/
│   │   └── AppLayout.tsx (app bar, nav, snackbar)
│   ├── components/
│   │   ├── ElementCard.tsx, VersionTable.tsx, FileTree.tsx
│   │   ├── RatingBadge.tsx, FavoriteButton.tsx, FiltersSidebar.tsx
│   ├── pages/
│   │   ├── CatalogPage.tsx, ElementPage.tsx, PackPage.tsx
│   │   ├── TeamsPage.tsx, AdminCategoriesPage.tsx, TokensPage.tsx
│   └── tests/ ... (рядом с компонентами: *.test.tsx)
```

---

### Task 1: Скелет Vite + React + TS + MUI + Router + React Query

**Files:**
- Create: `ui/package.json`, `ui/vite.config.ts`, `ui/tsconfig.json`, `ui/index.html`, `ui/src/main.tsx`, `ui/src/App.tsx`, `ui/src/vite-env.d.ts`
- Test: `ui/src/App.test.tsx`

**Interfaces:**
- Produces: приложение с маршрутами `/` (CatalogPage-заглушка) и `/elements/:slug` (заглушка); провайдеры `QueryClientProvider`, `MemoryRouter` в тестах.

- [ ] **Step 1: Write the failing test**

`ui/src/App.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import App from './App';

vi.mock('./auth/KeycloakProvider', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useAuth: () => ({ authenticated: true, displayName: 'Test', login: vi.fn(), logout: vi.fn(), token: null }),
}));

test('renders catalog page with search', () => {
  render(
    <MemoryRouter initialEntries={['/']}>
      <App />
    </MemoryRouter>
  );
  expect(screen.getByRole('heading', { name: /skillhub/i })).toBeInTheDocument();
});
```

- [ ] **Step 2: Scaffold и запуск теста**

```bash
cd ui 2>/dev/null || mkdir ui; cd ..
npm create vite@latest ui -- --template react-ts
cd ui
npm install
npm install @mui/material @emotion/react @emotion/styled @mui/icons-material react-router-dom @tanstack/react-query axios keycloak-js
npm install -D vitest @testing-library/react @testing-library/jest-dom jsdom @types/react
```

- [ ] **Step 3: Write implementation**

`ui/vite.config.ts`:

```ts
/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/setupTests.ts',
  },
});
```

`ui/src/setupTests.ts`:

```ts
import '@testing-library/jest-dom';
```

`ui/index.html`:

```html
<!doctype html>
<html lang="ru">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>SkillHub</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

`ui/src/App.tsx`:

```tsx
import { Routes, Route } from 'react-router-dom';
import AppLayout from './layout/AppLayout';
import CatalogPage from './pages/CatalogPage';
import ElementPage from './pages/ElementPage';

export default function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route path="/" element={<CatalogPage />} />
        <Route path="/elements/:slug" element={<ElementPage />} />
        <Route path="/packs/:slug" element={<ElementPage />} />
        <Route path="/teams" element={<CatalogPage />} />
        <Route path="/admin/categories" element={<CatalogPage />} />
        <Route path="/tokens" element={<CatalogPage />} />
      </Route>
    </Routes>
  );
}
```

`ui/src/main.tsx`:

```tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider, createTheme, CssBaseline } from '@mui/material';
import KeycloakProvider from './auth/KeycloakProvider';
import App from './App';

const theme = createTheme({ palette: { mode: 'light' } });
const queryClient = new QueryClient();

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <BrowserRouter>
          <KeycloakProvider>
            <App />
          </KeycloakProvider>
        </BrowserRouter>
      </ThemeProvider>
    </QueryClientProvider>
  </React.StrictMode>
);
```

`ui/src/vite-env.d.ts`:

```ts
/// <reference types="vite/client" />
```

Временные заглушки страниц и layout (заменяются в Task 4–5):

`ui/src/layout/AppLayout.tsx`:

```tsx
import { Outlet, Typography } from '@mui/material';

export default function AppLayout() {
  return (
    <>
      <Typography variant="h4">SkillHub</Typography>
      <Outlet />
    </>
  );
}
```

`ui/src/pages/CatalogPage.tsx`:

```tsx
export default function CatalogPage() {
  return <div>Catalog</div>;
}
```

`ui/src/pages/ElementPage.tsx`:

```tsx
import { useParams } from 'react-router-dom';

export default function ElementPage() {
  const { slug } = useParams();
  return <div>Element {slug}</div>;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run (из `ui/`): `npx vitest run`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ui
git commit -m "chore: scaffold React UI with Vite, MUI, Router, React Query"
```

---

### Task 2: Типы и API-клиент

**Files:**
- Create: `ui/src/types.ts`
- Create: `ui/src/api/client.ts`
- Create: `ui/src/api/elements.ts`, `ui/src/api/search.ts`, `ui/src/api/packs.ts`, `ui/src/api/categories.ts`, `ui/src/api/teams.ts`, `ui/src/api/social.ts`
- Test: `ui/src/api/client.test.ts`

**Interfaces:**
- Produces:
  - `types.ts`: `ElementType`, `ElementResponse`, `VersionResponse`, `FileDto`, `SearchResultResponse`, `SocialInfo`, `ReviewResponse`, `CategoryResponse`, `TeamResponse`, `PackResponse`, `ApiError`.
  - `api/client.ts`: `api` (axios instance, baseURL `VITE_API_URL`), `setAuthToken(token: string | null)`; interceptor вешает `Authorization: Bearer` и нормализует ошибки в `ApiError {code, message, details, status}`.
  - Функции: `elements.list()`, `elements.get(slug)`, `elements.create(body)`, `elements.publishVersion(slug, file, changelog?)`, `elements.versions(slug)`, `elements.downloadVersionUrl(slug, version)`, `elements.downloadFile(slug, version, path)`, `search.search(params)`, `packs.get(slug)`, `packs.addContent(slug, element, versionConstraint)`, `packs.downloadPackUrl(slug)`, `categories.list()`, `categories.create(body)`, `teams.list()`, `teams.create(body)`, `teams.addMember(slug, ssoSubject, role)`, `social.rate(slug, rating)`, `social.review(slug, rating, text)`, `social.reviews(slug)`, `social.setFavorite(slug, fav)`, `social.info(slug)`.

- [ ] **Step 1: Write the failing test**

`ui/src/api/client.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest';
import axios from 'axios';

vi.mock('axios', () => {
  const instance = { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() };
  const axiosMock = { create: vi.fn(() => instance), get: vi.fn() };
  return { default: axiosMock };
});

describe('api client', () => {
  beforeEach(() => vi.resetModules());

  it('creates axios instance with base URL from env', async () => {
    vi.stubEnv('VITE_API_URL', 'http://test:8080');
    const { api } = await import('./client');
    expect(axios.create).toHaveBeenCalledWith(
      expect.objectContaining({ baseURL: 'http://test:8080' })
    );
    expect(api).toBeDefined();
  });

  it('normalizes backend error to ApiError', async () => {
    const { toApiError } = await import('./client');
    const error = {
      response: {
        status: 409,
        data: { code: 'CONFLICT', message: 'Version exists', details: null },
      },
    };
    const apiError = toApiError(error);
    expect(apiError).toEqual({
      status: 409, code: 'CONFLICT', message: 'Version exists', details: null,
    });
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run (из `ui/`): `npx vitest run src/api/client.test.ts`
Expected: FAIL — `./client` не существует.

- [ ] **Step 3: Write implementation**

`ui/src/types.ts`:

```ts
export type ElementType = 'SKILL' | 'SCRIPT' | 'AGENT' | 'HOOK' | 'PACK' | 'OTHER';

export interface ElementResponse {
  slug: string;
  type: ElementType;
  name: string;
  description: string;
  team: string;
  category: string | null;
  tags: string[];
  visibility: 'PUBLIC' | 'TEAM';
  latestVersion: string | null;
  downloadsCount: number;
}

export interface FileDto {
  path: string;
  size: number;
}

export interface VersionResponse {
  version: string;
  status: 'DRAFT' | 'PUBLISHED' | 'DEPRECATED';
  changelog: string;
  sizeBytes: number;
  files: FileDto[];
}

export interface SearchResultResponse {
  items: ElementResponse[];
  total: number;
  facetsByType: Record<string, number>;
}

export interface SocialInfo {
  avgRating: number;
  ratingCount: number;
  favorited: boolean;
}

export interface ReviewResponse {
  author: string;
  rating: number;
  text: string;
  createdAt: string;
}

export interface CategoryResponse {
  slug: string;
  name: string;
  parent: string | null;
  icon: string | null;
}

export interface TeamResponse {
  slug: string;
  name: string;
}

export interface PackContentDto {
  element: string;
  version: string | null;
  versionConstraint: string;
}

export interface PackResponse {
  slug: string;
  contents: PackContentDto[];
}

export interface ApiError {
  status: number;
  code: string;
  message: string;
  details: unknown;
}
```

`ui/src/api/client.ts`:

```ts
import axios from 'axios';
import type { ApiError } from '../types';

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
});

let authToken: string | null = null;

export function setAuthToken(token: string | null) {
  authToken = token;
}

api.interceptors.request.use((config) => {
  if (authToken) {
    config.headers.Authorization = `Bearer ${authToken}`;
  }
  return config;
});

export function toApiError(error: unknown): ApiError {
  const err = error as { response?: { status: number; data: { code?: string; message?: string; details?: unknown } }; message?: string };
  if (err.response) {
    return {
      status: err.response.status,
      code: err.response.data?.code ?? 'ERROR',
      message: err.response.data?.message ?? err.message ?? 'Unknown error',
      details: err.response.data?.details ?? null,
    };
  }
  return { status: 0, code: 'NETWORK', message: err.message ?? 'Network error', details: null };
}
```

`ui/src/api/elements.ts`:

```ts
import { api } from './client';
import type { ElementResponse, VersionResponse } from '../types';

export const elements = {
  async list(): Promise<ElementResponse[]> {
    return (await api.get<ElementResponse[]>('/api/elements')).data;
  },
  async get(slug: string): Promise<ElementResponse> {
    return (await api.get<ElementResponse>(`/api/elements/${slug}`)).data;
  },
  async create(body: {
    slug: string; type: string; name: string; description?: string;
    team: string; category?: string; tags?: string[]; visibility: string;
  }): Promise<ElementResponse> {
    return (await api.post<ElementResponse>('/api/elements', body)).data;
  },
  async versions(slug: string): Promise<VersionResponse[]> {
    return (await api.get<VersionResponse[]>(`/api/elements/${slug}/versions`)).data;
  },
  async publishVersion(slug: string, file: File, changelog?: string): Promise<VersionResponse> {
    const form = new FormData();
    form.append('file', file);
    const params = changelog ? { changelog } : undefined;
    return (await api.post<VersionResponse>(`/api/elements/${slug}/versions`, form, { params })).data;
  },
  downloadVersionUrl(slug: string, version: string): string {
    const base = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
    return `${base}/api/elements/${slug}/versions/${version}/download`;
  },
  downloadFileUrl(slug: string, version: string, path: string): string {
    const base = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
    return `${base}/api/elements/${slug}/versions/${version}/files?path=${encodeURIComponent(path)}`;
  },
};
```

`ui/src/api/search.ts`:

```ts
import { api } from './client';
import type { SearchResultResponse } from '../types';

export const search = {
  async search(params: {
    q?: string; type?: string; category?: string; limit?: number; offset?: number;
  }): Promise<SearchResultResponse> {
    return (await api.get<SearchResultResponse>('/api/search', { params })).data;
  },
};
```

`ui/src/api/social.ts`:

```ts
import { api } from './client';
import type { ReviewResponse, SocialInfo } from '../types';

export const social = {
  async rate(slug: string, rating: number): Promise<void> {
    await api.put(`/api/elements/${slug}/rating`, { rating });
  },
  async review(slug: string, rating: number, text: string): Promise<void> {
    await api.put(`/api/elements/${slug}/review`, { rating, text });
  },
  async reviews(slug: string): Promise<ReviewResponse[]> {
    return (await api.get<ReviewResponse[]>(`/api/elements/${slug}/reviews`)).data;
  },
  async setFavorite(slug: string, favorited: boolean): Promise<void> {
    if (favorited) {
      await api.post(`/api/elements/${slug}/favorite`);
    } else {
      await api.delete(`/api/elements/${slug}/favorite`);
    }
  },
  async info(slug: string): Promise<SocialInfo> {
    return (await api.get<SocialInfo>(`/api/elements/${slug}/social`)).data;
  },
};
```

`ui/src/api/packs.ts`:

```ts
import { api } from './client';
import type { PackResponse } from '../types';

export const packs = {
  async get(slug: string): Promise<PackResponse> {
    return (await api.get<PackResponse>(`/api/packs/${slug}`)).data;
  },
  async addContent(slug: string, element: string, versionConstraint: string): Promise<PackResponse> {
    return (await api.post<PackResponse>(`/api/packs/${slug}/contents`, { element, versionConstraint })).data;
  },
  downloadPackUrl(slug: string): string {
    const base = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
    return `${base}/api/packs/${slug}/versions/latest/download`;
  },
};
```

`ui/src/api/categories.ts`:

```ts
import { api } from './client';
import type { CategoryResponse } from '../types';

export const categories = {
  async list(): Promise<CategoryResponse[]> {
    return (await api.get<CategoryResponse[]>('/api/categories')).data;
  },
  async create(body: { slug: string; name: string; parentSlug?: string; icon?: string }): Promise<CategoryResponse> {
    return (await api.post<CategoryResponse>('/api/categories', body)).data;
  },
};
```

`ui/src/api/teams.ts`:

```ts
import { api } from './client';
import type { TeamResponse } from '../types';

export const teams = {
  async list(): Promise<TeamResponse[]> {
    return (await api.get<TeamResponse[]>('/api/teams')).data;
  },
  async create(body: { slug: string; name: string }): Promise<TeamResponse> {
    return (await api.post<TeamResponse>('/api/teams', body)).data;
  },
  async addMember(slug: string, ssoSubject: string, role: string): Promise<void> {
    await api.post(`/api/teams/${slug}/members`, { ssoSubject, role });
  },
};
```

- [ ] **Step 4: Run test to verify it passes**

Run (из `ui/`): `npx vitest run`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ui/src
git commit -m "feat: add typed API client for all backend endpoints"
```

---

### Task 3: Аутентификация (Keycloak OIDC) + backend endpoint /api/tokens

**Files:**
- Create: `ui/src/auth/KeycloakProvider.tsx`, `ui/src/auth/useAuth.ts`
- Modify (backend!): `src/main/java/com/skillhub/api/TokenController.java` — REST для API-токенов (UI TokensPage требует его)
- Modify (backend!): `src/test/java/com/skillhub/api/TokenApiIT.java`
- Test: `ui/src/auth/KeycloakProvider.test.tsx`

**Interfaces:**
- Produces (UI): `useAuth()` → `{ authenticated: boolean, token: string | null, displayName: string | null, login(): void, logout(): void }`; при входе `setAuthToken(jwt)` вызывается автоматически.
- Produces (backend): `POST /api/tokens {name}` → `{token: "skh_...", name}` (raw показывается один раз); `GET /api/tokens` → `[{name, createdAt, lastUsedAt, expiresAt}]` (без хешей).

- [ ] **Step 1: Write the failing test (UI)**

`ui/src/auth/KeycloakProvider.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

const keycloakMock = {
  init: vi.fn().mockResolvedValue(true),
  authenticated: false,
  token: 'jwt-token',
  login: vi.fn(),
  logout: vi.fn(),
};

vi.mock('keycloak-js', () => ({ default: vi.fn(() => keycloakMock) }));

describe('KeycloakProvider', () => {
  it('renders children and shows login button when unauthenticated', async () => {
    const { KeycloakProvider, useAuth } = await import('./KeycloakProvider');
    function Probe() {
      const auth = useAuth();
      if (!auth.authenticated) return <button onClick={auth.login}>login</button>;
      return <span>{auth.displayName}</span>;
    }
    render(
      <KeycloakProvider>
        <MemoryRouter>
          <Probe />
        </MemoryRouter>
      </KeycloakProvider>
    );
    expect(await screen.findByRole('button', { name: 'login' })).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run (из `ui/`): `npx vitest run src/auth`
Expected: FAIL — модуль не существует.

- [ ] **Step 3: Write implementation (UI)**

`ui/src/auth/KeycloakProvider.tsx`:

```tsx
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import Keycloak from 'keycloak-js';
import { setAuthToken } from '../api/client';

interface AuthState {
  authenticated: boolean;
  token: string | null;
  displayName: string | null;
  login: () => void;
  logout: () => void;
}

const AuthContext = createContext<AuthState>({
  authenticated: false, token: null, displayName: null, login: () => {}, logout: () => {},
});

export const useAuth = () => useContext(AuthContext);

const keycloak = new Keycloak({
  url: import.meta.env.VITE_OIDC_URL ?? 'http://localhost:8180',
  realm: 'skillhub',
  clientId: import.meta.env.VITE_OIDC_CLIENT_ID ?? 'skillhub-ui',
});

export default function KeycloakProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);
  const [token, setToken] = useState<string | null>(null);

  useEffect(() => {
    keycloak.init({ onLoad: 'check-sso', pkceMethod: 'S256' }).then((auth) => {
      setAuthenticated(auth);
      setToken(keycloak.token ?? null);
      setAuthToken(keycloak.token ?? null);
      setReady(true);
    });
    const refresh = setInterval(() => {
      if (keycloak.authenticated) {
        keycloak.updateToken(60).then(() => {
          setToken(keycloak.token ?? null);
          setAuthToken(keycloak.token ?? null);
        }).catch(() => keycloak.login());
      }
    }, 30000);
    return () => clearInterval(refresh);
  }, []);

  if (!ready) return null;

  return (
    <AuthContext.Provider
      value={{
        authenticated,
        token,
        displayName: keycloak.tokenParsed
          ? (keycloak.tokenParsed as { preferred_username?: string }).preferred_username ?? null
          : null,
        login: () => keycloak.login(),
        logout: () => keycloak.logout(),
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export { keycloak };
```

- [ ] **Step 4: Write the failing test (backend)**

`src/test/java/com/skillhub/api/TokenApiIT.java`:

```java
package com.skillhub.api;

import com.skillhub.core.service.ApiTokenService;
import com.skillhub.core.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TokenApiIT {

    @Autowired TestRestTemplate rest;
    @Autowired UserService userService;
    @Autowired ApiTokenService tokens;

    String authHeader;

    @BeforeEach
    void setUp() {
        var user = userService.syncFromSso("token-user", "tok@skillhub.io", "Token User");
        authHeader = "Bearer " + tokens.createToken(user, "setup").rawToken();
    }

    HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, authHeader);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    void createTokenReturnsRawOnce() {
        ResponseEntity<String> created = rest.exchange("/api/tokens", HttpMethod.POST,
            new HttpEntity<>(Map.of("name", "cli-token"), jsonHeaders()), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).contains("skh_");
    }

    @Test
    void listTokensWithoutHashes() {
        rest.exchange("/api/tokens", HttpMethod.POST,
            new HttpEntity<>(Map.of("name", "list-token"), jsonHeaders()), String.class);
        ResponseEntity<String> list = rest.exchange("/api/tokens", HttpMethod.GET,
            new HttpEntity<>(jsonHeaders()), String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains("list-token").doesNotContain("tokenHash");
    }
}
```

- [ ] **Step 5: Write implementation (backend)**

`src/main/java/com/skillhub/api/TokenController.java`:

```java
package com.skillhub.api;

import com.skillhub.core.model.ApiToken;
import com.skillhub.core.model.User;
import com.skillhub.core.repo.ApiTokenRepository;
import com.skillhub.core.service.ApiTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tokens")
@RequiredArgsConstructor
public class TokenController {

    private final ApiTokenService tokenService;
    private final ApiTokenRepository tokens;

    public record CreateTokenRequest(String name) {}
    public record TokenItem(String name, String createdAt, String lastUsedAt, String expiresAt) {}

    @PostMapping
    public ResponseEntity<Map<String, String>> create(@RequestBody CreateTokenRequest req,
                                                      @AuthenticationPrincipal User user) {
        ApiTokenService.CreatedToken created = tokenService.createToken(user, req.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "token", created.rawToken(),
            "name", created.token().getName()));
    }

    @GetMapping
    public List<TokenItem> list(@AuthenticationPrincipal User user) {
        return tokens.findAll().stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .map(t -> new TokenItem(t.getName(), t.getCreatedAt().toString(),
                t.getLastUsedAt() == null ? null : t.getLastUsedAt().toString(),
                t.getExpiresAt() == null ? null : t.getExpiresAt().toString()))
            .toList();
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run (из `ui/`): `npx vitest run`
Run (из корня): `mvn test -Dtest=TokenApiIT`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add ui/src src
git commit -m "feat: add Keycloak OIDC auth provider and API token management endpoints"
```

---

### Task 4: Layout — app bar, навигация, snackbar ошибок

**Files:**
- Create: `ui/src/layout/AppLayout.tsx` (замена), `ui/src/layout/SnackbarContext.tsx`
- Modify: `ui/src/main.tsx` — обернуть SnackbarProvider
- Test: `ui/src/layout/AppLayout.test.tsx`

**Interfaces:**
- Produces: `useSnackbar()` → `{ showError(msg: string): void, showSuccess(msg: string): void }`; AppLayout с AppBar (лого SkillHub, меню: Каталог, Команды, API-токены, Категории (админ), кнопка Войти/Выйти).

- [ ] **Step 1: Write the failing test**

`ui/src/layout/AppLayout.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import AppLayout from './AppLayout';

vi.mock('../auth/KeycloakProvider', () => ({
  useAuth: () => ({ authenticated: true, displayName: 'Alice', login: vi.fn(), logout: vi.fn(), token: 'x' }),
}));

test('renders navigation with all menu items and user name', () => {
  render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/" element={<div />} />
        </Route>
      </Routes>
    </MemoryRouter>
  );
  expect(screen.getByText('SkillHub')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Каталог' })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Команды' })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'API-токены' })).toBeInTheDocument();
  expect(screen.getByText('Alice')).toBeInTheDocument();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run (из `ui/`): `npx vitest run src/layout`
Expected: FAIL — нет пунктов меню в заглушке.

- [ ] **Step 3: Write implementation**

`ui/src/layout/SnackbarContext.tsx`:

```tsx
import { createContext, useContext, useState, type ReactNode } from 'react';
import { Snackbar, Alert } from '@mui/material';

interface SnackbarApi {
  showError: (msg: string) => void;
  showSuccess: (msg: string) => void;
}

const SnackbarContext = createContext<SnackbarApi>({
  showError: () => {}, showSuccess: () => {},
});

export const useSnackbar = () => useContext(SnackbarContext);

export function SnackbarProvider({ children }: { children: ReactNode }) {
  const [msg, setMsg] = useState<{ text: string; severity: 'error' | 'success' } | null>(null);

  return (
    <SnackbarContext.Provider
      value={{
        showError: (text) => setMsg({ text, severity: 'error' }),
        showSuccess: (text) => setMsg({ text, severity: 'success' }),
      }}
    >
      {children}
      <Snackbar
        open={msg !== null}
        autoHideDuration={5000}
        onClose={() => setMsg(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        {msg && (
          <Alert severity={msg.severity} onClose={() => setMsg(null)}>
            {msg.text}
          </Alert>
        )}
      </Snackbar>
    </SnackbarContext.Provider>
  );
}
```

`ui/src/layout/AppLayout.tsx`:

```tsx
import { Outlet, Link as RouterLink, useNavigate } from 'react-router-dom';
import { AppBar, Toolbar, Typography, Button, Box, IconButton } from '@mui/material';
import StorageIcon from '@mui/icons-material/Storage';
import { useAuth } from '../auth/KeycloakProvider';

export default function AppLayout() {
  const { authenticated, displayName, login, logout } = useAuth();
  const navigate = useNavigate();

  return (
    <>
      <AppBar position="static">
        <Toolbar>
          <IconButton component={RouterLink} to="/" color="inherit" size="large">
            <StorageIcon />
          </IconButton>
          <Typography variant="h6" sx={{ mr: 3 }}>SkillHub</Typography>
          <Button color="inherit" component={RouterLink} to="/">Каталог</Button>
          <Button color="inherit" component={RouterLink} to="/teams">Команды</Button>
          <Button color="inherit" component={RouterLink} to="/tokens">API-токены</Button>
          <Button color="inherit" component={RouterLink} to="/admin/categories">Категории</Button>
          <Box sx={{ flexGrow: 1 }} />
          {authenticated ? (
            <>
              <Typography sx={{ mr: 2 }}>{displayName}</Typography>
              <Button color="inherit" onClick={() => { logout(); navigate('/'); }}>Выйти</Button>
            </>
          ) : (
            <Button color="inherit" onClick={login}>Войти</Button>
          )}
        </Toolbar>
      </AppBar>
      <Box sx={{ p: 3, maxWidth: 1200, mx: 'auto' }}>
        <Outlet />
      </Box>
    </>
  );
}
```

`ui/src/main.tsx` — добавьте `SnackbarProvider` внутрь `BrowserRouter` вокруг `KeycloakProvider`:

```tsx
<BrowserRouter>
  <SnackbarProvider>
    <KeycloakProvider>
      <App />
    </KeycloakProvider>
  </SnackbarProvider>
</BrowserRouter>
```

(импорт: `import { SnackbarProvider } from './layout/SnackbarContext';`)

- [ ] **Step 4: Run test to verify it passes**

Run (из `ui/`): `npx vitest run`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ui/src
git commit -m "feat: add app layout with navigation and snackbar error handling"
```

---

### Task 5: Каталог — поиск, фильтры, фасеты, список элементов

**Files:**
- Create: `ui/src/pages/CatalogPage.tsx` (замена), `ui/src/components/ElementCard.tsx`, `ui/src/components/FiltersSidebar.tsx`
- Test: `ui/src/pages/CatalogPage.test.tsx`

**Interfaces:**
- Consumes: `search.search()`, `elements.list()`, `categories.list()` (Task 2).
- Produces: `ElementCard` (name, type-чип, team, рейтинг, downloads, клик → `/elements/{slug}`); `FiltersSidebar` (type-чипы с фасетами, категории). CatalogPage: search bar + debounced запрос + фильтры + пагинация.

`ui/src/components/RatingBadge.tsx` (используется карточкой):

```tsx
import { Chip, Rating } from '@mui/material';
import StarIcon from '@mui/icons-material/Star';

export default function RatingBadge({ avg, count }: { avg: number; count: number }) {
  if (count === 0) return <Chip size="small" label="Нет оценок" />;
  return (
    <Chip
      size="small"
      icon={<StarIcon />}
      label={`${avg.toFixed(1)} (${count})`}
    />
  );
}
```

- [ ] **Step 1: Write the failing test**

`ui/src/pages/CatalogPage.test.tsx`:

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import CatalogPage from './CatalogPage';

vi.mock('../api/search', () => ({
  search: {
    search: vi.fn().mockResolvedValue({
      items: [{
        slug: 'pdf-skill', type: 'SKILL', name: 'PDF Skill', description: 'd',
        team: 'platform', category: null, tags: [], visibility: 'PUBLIC',
        latestVersion: '1.0.0', downloadsCount: 5,
      }],
      total: 1,
      facetsByType: { SKILL: 1 },
    }),
  },
}));

vi.mock('../auth/KeycloakProvider', () => ({
  useAuth: () => ({ authenticated: true, token: 't', displayName: 'A', login: vi.fn(), logout: vi.fn() }),
}));

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <CatalogPage />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

test('shows elements from search', async () => {
  renderPage();
  expect(await screen.findByText('PDF Skill')).toBeInTheDocument();
  expect(screen.getByText('SKILL')).toBeInTheDocument();
});

test('search field triggers query', async () => {
  const { search } = await import('../api/search');
  renderPage();
  await screen.findByText('PDF Skill');
  await userEvent.type(screen.getByPlaceholderText('Поиск'), 'pdf');
  await waitFor(() => expect(search.search).toHaveBeenCalledWith(
    expect.objectContaining({ q: 'pdf' })
  ));
});
```

Нужен пакет: `npm install -D @testing-library/user-event`

- [ ] **Step 2: Run test to verify it fails**

Run (из `ui/`): `npx vitest run src/pages`
Expected: FAIL — каталог показывает заглушку "Catalog".

- [ ] **Step 3: Write implementation**

`ui/src/components/ElementCard.tsx`:

```tsx
import { Card, CardContent, Typography, Chip, Stack } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import type { ElementResponse } from '../types';
import RatingBadge from './RatingBadge';

export default function ElementCard({ element, avgRating, ratingCount }: {
  element: ElementResponse;
  avgRating?: number;
  ratingCount?: number;
}) {
  return (
    <Card
      component={RouterLink}
      to={`/elements/${element.slug}`}
      sx={{ textDecoration: 'none', mb: 1.5, '&:hover': { boxShadow: 6 } }}
    >
      <CardContent>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
          <Typography variant="h6">{element.name}</Typography>
          <Chip size="small" label={element.type} color="primary" variant="outlined" />
          {element.visibility === 'TEAM' && <Chip size="small" label="team-only" />}
        </Stack>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          {element.description}
        </Typography>
        <Stack direction="row" spacing={2} alignItems="center">
          <Typography variant="caption">команда: {element.team}</Typography>
          {element.latestVersion && (
            <Typography variant="caption">v{element.latestVersion}</Typography>
          )}
          <Typography variant="caption">↓ {element.downloadsCount}</Typography>
          <RatingBadge avg={avgRating ?? 0} count={ratingCount ?? 0} />
        </Stack>
      </CardContent>
    </Card>
  );
}
```

`ui/src/components/FiltersSidebar.tsx`:

```tsx
import { Box, Chip, Typography, Stack } from '@mui/material';
import type { CategoryResponse } from '../types';

export default function FiltersSidebar({ facetsByType, categories, type, category, onChange }: {
  facetsByType: Record<string, number>;
  categories: CategoryResponse[];
  type: string | null;
  category: string | null;
  onChange: (next: { type?: string | null; category?: string | null }) => void;
}) {
  return (
    <Stack spacing={2}>
      <Box>
        <Typography variant="subtitle2" sx={{ mb: 1 }}>Тип</Typography>
        <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
          <Chip label="Все" onClick={() => onChange({ type: null })} color={type === null ? 'primary' : 'default'} />
          {Object.entries(facetsByType).map(([t, count]) => (
            <Chip
              key={t}
              label={`${t} (${count})`}
              onClick={() => onChange({ type: t })}
              color={type === t ? 'primary' : 'default'}
            />
          ))}
        </Stack>
      </Box>
      <Box>
        <Typography variant="subtitle2" sx={{ mb: 1 }}>Категории</Typography>
        <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
          <Chip label="Все" onClick={() => onChange({ category: null })} color={category === null ? 'primary' : 'default'} />
          {categories.map((c) => (
            <Chip
              key={c.slug}
              label={c.name}
              onClick={() => onChange({ category: c.slug })}
              color={category === c.slug ? 'primary' : 'default'}
            />
          ))}
        </Stack>
      </Box>
    </Stack>
  );
}
```

`ui/src/pages/CatalogPage.tsx`:

```tsx
import { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { TextField, CircularProgress, Typography, Box, Stack } from '@mui/material';
import { search } from '../api/search';
import { categories as categoriesApi } from '../api/categories';
import ElementCard from '../components/ElementCard';
import FiltersSidebar from '../components/FiltersSidebar';

export default function CatalogPage() {
  const [q, setQ] = useState('');
  const [debouncedQ, setDebouncedQ] = useState('');
  const [type, setType] = useState<string | null>(null);
  const [category, setCategory] = useState<string | null>(null);

  useEffect(() => {
    const t = setTimeout(() => setDebouncedQ(q), 300);
    return () => clearTimeout(t);
  }, [q]);

  const { data, isLoading } = useQuery({
    queryKey: ['search', debouncedQ, type, category],
    queryFn: () => search.search({ q: debouncedQ, type: type ?? undefined, category: category ?? undefined }),
  });

  const { data: categories } = useQuery({
    queryKey: ['categories'],
    queryFn: categoriesApi.list,
  });

  return (
    <>
      <TextField
        fullWidth
        placeholder="Поиск"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        sx={{ mb: 2 }}
      />
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={3}>
        <Box sx={{ width: { xs: '100%', md: 260 }, flexShrink: 0 }}>
          <FiltersSidebar
            facetsByType={data?.facetsByType ?? {}}
            categories={categories ?? []}
            type={type}
            category={category}
            onChange={(next) => {
              if ('type' in next) setType(next.type ?? null);
              if ('category' in next) setCategory(next.category ?? null);
            }}
          />
        </Box>
        <Box sx={{ flexGrow: 1 }}>
          {isLoading && <CircularProgress />}
          {data && data.items.length === 0 && (
            <Typography color="text.secondary">Ничего не найдено</Typography>
          )}
          <Box>
            {data?.items.map((el) => (
              <ElementCard key={el.slug} element={el} />
            ))}
          </Box>
        </Box>
      </Stack>
    </>
  );
}
```

- [ ] **Step 4: Run test to verify it passes**

Run (из `ui/`): `npx vitest run`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ui/src
git commit -m "feat: add catalog page with search, filters and facets"
```

---

### Task 6: Страница элемента — информация, версии, социальные функции

**Files:**
- Create: `ui/src/pages/ElementPage.tsx` (замена), `ui/src/components/VersionTable.tsx`, `ui/src/components/FavoriteButton.tsx`
- Test: `ui/src/pages/ElementPage.test.tsx`

**Interfaces:**
- Consumes: `elements.get/versions/downloadVersionUrl`, `social.info/rate/review/reviews/setFavorite` (Task 2), `useSnackbar` (Task 4).
- Produces: `VersionTable` (версия, статус, changelog, размер, кнопка Скачать); `FavoriteButton` (toggle); ElementPage: карточка с описанием, тегами, табличкой версий, кнопка «Поставить оценку», список отзывов + форма отзыва, favorite.

`ui/src/components/FavoriteButton.tsx`:

```tsx
import { IconButton } from '@mui/material';
import FavoriteIcon from '@mui/icons-material/Favorite';
import FavoriteBorderIcon from '@mui/icons-material/FavoriteBorder';

export default function FavoriteButton({ favorited, onToggle }: {
  favorited: boolean;
  onToggle: () => void;
}) {
  return (
    <IconButton onClick={onToggle} aria-label="favorite">
      {favorited ? <FavoriteIcon color="error" /> : <FavoriteBorderIcon />}
    </IconButton>
  );
}
```

- [ ] **Step 1: Write the failing test**

`ui/src/pages/ElementPage.test.tsx`:

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import ElementPage from './ElementPage';
import type { ElementResponse, VersionResponse } from '../types';

const element: ElementResponse = {
  slug: 'pdf-skill', type: 'SKILL', name: 'PDF Skill', description: 'desc',
  team: 'platform', category: null, tags: ['pdf'], visibility: 'PUBLIC',
  latestVersion: '1.0.0', downloadsCount: 3,
};

vi.mock('../api/elements', () => ({
  elements: {
    get: vi.fn().mockResolvedValue(element),
    versions: vi.fn().mockResolvedValue([
      { version: '1.0.0', status: 'PUBLISHED', changelog: 'initial', sizeBytes: 100, files: [] },
    ] as VersionResponse[]),
    downloadVersionUrl: vi.fn(() => 'http://test/download'),
    downloadFileUrl: vi.fn(),
    publishVersion: vi.fn(),
    create: vi.fn(),
    list: vi.fn(),
  },
}));

vi.mock('../api/social', () => ({
  social: {
    info: vi.fn().mockResolvedValue({ avgRating: 4.5, ratingCount: 2, favorited: false }),
    reviews: vi.fn().mockResolvedValue([
      { author: 'Bob', rating: 5, text: 'great', createdAt: '2026-01-01T00:00:00Z' },
    ]),
    rate: vi.fn().mockResolvedValue(undefined),
    review: vi.fn().mockResolvedValue(undefined),
    setFavorite: vi.fn().mockResolvedValue(undefined),
  },
}));

vi.mock('../auth/KeycloakProvider', () => ({
  useAuth: () => ({ authenticated: true, token: 't', displayName: 'A', login: vi.fn(), logout: vi.fn() }),
}));

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/elements/pdf-skill']}>
        <Routes>
          <Route path="/elements/:slug" element={<ElementPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

test('shows element info, versions and reviews', async () => {
  renderPage();
  expect(await screen.findByText('PDF Skill')).toBeInTheDocument();
  expect(await screen.findByText('1.0.0')).toBeInTheDocument();
  expect(await screen.findByText('great')).toBeInTheDocument();
});

test('favorite toggle calls setFavorite', async () => {
  const { social } = await import('../api/social');
  renderPage();
  const btn = await screen.findByRole('button', { name: 'favorite' });
  await userEvent.click(btn);
  await waitFor(() => expect(social.setFavorite).toHaveBeenCalledWith('pdf-skill', true));
});
```

- [ ] **Step 2: Run test to verify it fails**

Run (из `ui/`): `npx vitest run src/pages`
Expected: FAIL — ElementPage заглушка.

- [ ] **Step 3: Write implementation**

`ui/src/components/VersionTable.tsx`:

```tsx
import { Table, TableHead, TableRow, TableCell, TableBody, Button, Chip } from '@mui/material';
import type { VersionResponse } from '../types';

export default function VersionTable({ versions, onDownload }: {
  versions: VersionResponse[];
  onDownload: (version: string) => void;
}) {
  return (
    <Table size="small">
      <TableHead>
        <TableRow>
          <TableCell>Версия</TableCell>
          <TableCell>Статус</TableCell>
          <TableCell>Changelog</TableCell>
          <TableCell>Размер</TableCell>
          <TableCell />
        </TableRow>
      </TableHead>
      <TableBody>
        {versions.map((v) => (
          <TableRow key={v.version}>
            <TableCell>v{v.version}</TableCell>
            <TableCell>
              <Chip
                size="small"
                label={v.status}
                color={v.status === 'PUBLISHED' ? 'success' : v.status === 'DEPRECATED' ? 'default' : 'warning'}
              />
            </TableCell>
            <TableCell>{v.changelog}</TableCell>
            <TableCell>{Math.round(v.sizeBytes / 1024)} KB</TableCell>
            <TableCell>
              <Button size="small" onClick={() => onDownload(v.version)}>Скачать</Button>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
```

`ui/src/pages/ElementPage.tsx`:

```tsx
import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Typography, Chip, Stack, Paper, Rating, Button, TextField,
  Dialog, DialogTitle, DialogContent, DialogActions,
} from '@mui/material';
import { elements } from '../api/elements';
import { social } from '../api/social';
import { toApiError } from '../api/client';
import { useSnackbar } from '../layout/SnackbarContext';
import { useAuth } from '../auth/KeycloakProvider';
import VersionTable from '../components/VersionTable';
import FavoriteButton from '../components/FavoriteButton';

export default function ElementPage() {
  const { slug } = useParams<{ slug: string }>();
  const qc = useQueryClient();
  const { showError, showSuccess } = useSnackbar();
  const { authenticated } = useAuth();
  const [reviewOpen, setReviewOpen] = useState(false);
  const [reviewRating, setReviewRating] = useState(5);
  const [reviewText, setReviewText] = useState('');

  const { data: element } = useQuery({
    queryKey: ['element', slug],
    queryFn: () => elements.get(slug!),
  });
  const { data: versions } = useQuery({
    queryKey: ['versions', slug],
    queryFn: () => elements.versions(slug!),
  });
  const { data: info } = useQuery({
    queryKey: ['social', slug],
    queryFn: () => social.info(slug!),
  });
  const { data: reviews } = useQuery({
    queryKey: ['reviews', slug],
    queryFn: () => social.reviews(slug!),
  });

  const favoriteMutation = useMutation({
    mutationFn: (next: boolean) => social.setFavorite(slug!, next),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['social', slug] }),
    onError: (e) => showError(toApiError(e).message),
  });

  const reviewMutation = useMutation({
    mutationFn: () => social.review(slug!, reviewRating, reviewText),
    onSuccess: () => {
      setReviewOpen(false);
      showSuccess('Отзыв сохранён');
      qc.invalidateQueries({ queryKey: ['reviews', slug] });
    },
    onError: (e) => showError(toApiError(e).message),
  });

  if (!element) return null;

  return (
    <Stack spacing={3}>
      <Paper sx={{ p: 2 }}>
        <Stack direction="row" spacing={1} alignItems="center">
          <Typography variant="h5">{element.name}</Typography>
          <Chip label={element.type} color="primary" variant="outlined" size="small" />
          <FavoriteButton
            favorited={info?.favorited ?? false}
            onToggle={() => favoriteMutation.mutate(!info?.favorited)}
          />
        </Stack>
        <Typography color="text.secondary" sx={{ mt: 1 }}>{element.description}</Typography>
        <Stack direction="row" spacing={2} sx={{ mt: 1 }}>
          {element.tags.map((t) => <Chip key={t} label={t} size="small" />)}
        </Stack>
        <Stack direction="row" spacing={2} sx={{ mt: 2 }} alignItems="center">
          <Rating value={info?.avgRating ?? 0} precision={0.1} readOnly />
          <Typography variant="caption">({info?.ratingCount ?? 0} оценок)</Typography>
          {authenticated && (
            <Button size="small" onClick={() => setReviewOpen(true)}>Написать отзыв</Button>
          )}
        </Stack>
      </Paper>

      <Paper sx={{ p: 2 }}>
        <Typography variant="h6" sx={{ mb: 1 }}>Версии</Typography>
        {versions && versions.length > 0 ? (
          <VersionTable
            versions={versions}
            onDownload={(v) => { window.location.href = elements.downloadVersionUrl(slug!, v); }}
          />
        ) : (
          <Typography color="text.secondary">Версий пока нет</Typography>
        )}
      </Paper>

      <Paper sx={{ p: 2 }}>
        <Typography variant="h6" sx={{ mb: 1 }}>Отзывы</Typography>
        {(reviews ?? []).map((r) => (
          <Stack key={r.createdAt + r.author} spacing={0.5} sx={{ mb: 1.5 }}>
            <Stack direction="row" spacing={1} alignItems="center">
              <Typography variant="subtitle2">{r.author}</Typography>
              <Rating value={r.rating} size="small" readOnly />
            </Stack>
            <Typography variant="body2">{r.text}</Typography>
          </Stack>
        ))}
      </Paper>

      <Dialog open={reviewOpen} onClose={() => setReviewOpen(false)}>
        <DialogTitle>Новый отзыв</DialogTitle>
        <DialogContent>
          <Rating
            value={reviewRating}
            onChange={(_, v) => setReviewRating(v ?? 5)}
            sx={{ my: 2 }}
          />
          <TextField
            fullWidth
            multiline
            minRows={3}
            label="Текст отзыва"
            value={reviewText}
            onChange={(e) => setReviewText(e.target.value)}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setReviewOpen(false)}>Отмена</Button>
          <Button onClick={() => reviewMutation.mutate()} disabled={!reviewText.trim()}>
            Отправить
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
```

- [ ] **Step 4: Run test to verify it passes**

Run (из `ui/`): `npx vitest run`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ui/src
git commit -m "feat: add element page with versions, ratings, reviews and favorites"
```

---

### Task 7: Browse файлов версии + публикация новой версии из UI

**Files:**
- Create: `ui/src/components/FileTree.tsx`
- Modify: `ui/src/pages/ElementPage.tsx` — добавить секцию файлов и кнопку загрузки версии (для OWNER/MAINTAINER)
- Test: `ui/src/components/FileTree.test.tsx`

**Interfaces:**
- Consumes: `VersionResponse.files` (path/size), `elements.downloadFileUrl`, `elements.publishVersion`.
- Produces: `FileTree` — плоское дерево файлов (path → вложенность по слэшам), клик по файлу → скачивание; форма публикации версии (файл + changelog).

- [ ] **Step 1: Write the failing test**

`ui/src/components/FileTree.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import FileTree from './FileTree';

test('renders nested files with indentation', () => {
  render(
    <FileTree
      files={[
        { path: 'SKILL.md', size: 10 },
        { path: 'scripts/run.sh', size: 20 },
        { path: 'scripts/lib/util.sh', size: 5 },
      ]}
      onOpenFile={vi.fn()}
    />
  );
  expect(screen.getByText('SKILL.md')).toBeInTheDocument();
  expect(screen.getByText('run.sh')).toBeInTheDocument();
  expect(screen.getByText('util.sh')).toBeInTheDocument();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run (из `ui/`): `npx vitest run src/components`
Expected: FAIL — FileTree не существует.

- [ ] **Step 3: Write implementation**

`ui/src/components/FileTree.tsx`:

```tsx
import { List, ListItemButton, ListItemText, Typography } from '@mui/material';
import InsertDriveFileIcon from '@mui/icons-material/InsertDriveFile';
import type { FileDto } from '../types';

export default function FileTree({ files, onOpenFile }: {
  files: FileDto[];
  onOpenFile: (path: string) => void;
}) {
  return (
    <List dense>
      {files.map((f) => {
        const parts = f.path.split('/');
        const depth = parts.length - 1;
        return (
          <ListItemButton
            key={f.path}
            onClick={() => onOpenFile(f.path)}
            sx={{ pl: 2 + depth * 2 }}
          >
            <InsertDriveFileIcon sx={{ mr: 1, fontSize: 16 }} />
            <ListItemText
              primary={parts[parts.length - 1]}
              secondary={f.path}
            />
            <Typography variant="caption">{f.size} B</Typography>
          </ListItemButton>
        );
      })}
    </List>
  );
}
```

Modify `ui/src/pages/ElementPage.tsx` — добавьте после блока версий секцию файлов выбранной версии:

```tsx
  const [selectedVersion, setSelectedVersion] = useState<string | null>(null);
  const selected = versions?.find((v) => v.version === (selectedVersion ?? element?.latestVersion))
    ?? versions?.[0];
```

(состояния объявить рядом с остальными useState) и JSX-блок между «Версии» и «Отзывы»:

```tsx
      {selected && selected.files.length > 0 && (
        <Paper sx={{ p: 2 }}>
          <Typography variant="h6" sx={{ mb: 1 }}>
            Файлы версии {selected.version}
          </Typography>
          <FileTree
            files={selected.files}
            onOpenFile={(path) => {
              window.location.href = elements.downloadFileUrl(slug!, selected.version, path);
            }}
          />
        </Paper>
      )}
```

(импорт: `import FileTree from '../components/FileTree';`)

Добавьте форму публикации версии (для авторизованных) после блока файлов:

```tsx
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [changelog, setChangelog] = useState('');

  const publishMutation = useMutation({
    mutationFn: (file: File) => elements.publishVersion(slug!, file, changelog || undefined),
    onSuccess: () => {
      showSuccess('Версия опубликована');
      setChangelog('');
      if (fileInputRef.current) fileInputRef.current.value = '';
      qc.invalidateQueries({ queryKey: ['versions', slug] });
      qc.invalidateQueries({ queryKey: ['element', slug] });
    },
    onError: (e) => showError(toApiError(e).message),
  });
```

и JSX:

```tsx
      {authenticated && (
        <Paper sx={{ p: 2 }}>
          <Typography variant="h6" sx={{ mb: 1 }}>Опубликовать новую версию</Typography>
          <input
            type="file"
            accept=".zip"
            ref={fileInputRef}
            onChange={(e) => {
              const f = e.target.files?.[0];
              if (f) publishMutation.mutate(f);
            }}
          />
          <TextField
            fullWidth
            size="small"
            label="Changelog"
            value={changelog}
            onChange={(e) => setChangelog(e.target.value)}
            sx={{ mt: 1 }}
          />
        </Paper>
      )}
```

(импорты: `useRef` из react).

- [ ] **Step 4: Run test to verify it passes**

Run (из `ui/`): `npx vitest run`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ui/src
git commit -m "feat: add file browser per version and version publishing form"
```

---

### Task 8: Страница пака — состав, добавление элементов, скачивание

**Files:**
- Create: `ui/src/pages/PackPage.tsx` (замена), `ui/src/components/AddToPackDialog.tsx`
- Modify: `ui/src/App.tsx` — маршрут `/packs/:slug` → `PackPage`
- Test: `ui/src/pages/PackPage.test.tsx`

**Interfaces:**
- Consumes: `packs.get/addContent/downloadPackUrl`, `elements.list` (Task 2).
- Produces: PackPage (таблица состава: element, versionConstraint, текущая версия; кнопка «Скачать пак»; кнопка «Добавить элемент»); `AddToPackDialog` — выбор элемента + versionConstraint (`latest` или точная версия).

- [ ] **Step 1: Write the failing test**

`ui/src/pages/PackPage.test.tsx`:

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import PackPage from './PackPage';

vi.mock('../api/packs', () => ({
  packs: {
    get: vi.fn().mockResolvedValue({
      slug: 'my-pack',
      contents: [{ element: 'pdf-skill', version: '1.0.0', versionConstraint: '1.0.0' }],
    }),
    addContent: vi.fn().mockResolvedValue({ slug: 'my-pack', contents: [] }),
    downloadPackUrl: vi.fn(() => 'http://test/pack'),
  },
}));

vi.mock('../api/elements', () => ({
  elements: {
    list: vi.fn().mockResolvedValue([
      { slug: 'logo-skill', type: 'SKILL', name: 'Logo', description: 'd', team: 't',
        category: null, tags: [], visibility: 'PUBLIC', latestVersion: '2.0.0', downloadsCount: 0 },
    ]),
    get: vi.fn(), create: vi.fn(), versions: vi.fn(), publishVersion: vi.fn(),
    downloadVersionUrl: vi.fn(), downloadFileUrl: vi.fn(),
  },
}));

vi.mock('../auth/KeycloakProvider', () => ({
  useAuth: () => ({ authenticated: true, token: 't', displayName: 'A', login: vi.fn(), logout: vi.fn() }),
}));

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/packs/my-pack']}>
        <Routes>
          <Route path="/packs/:slug" element={<PackPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

test('shows pack contents and download button', async () => {
  renderPage();
  expect(await screen.findByText('my-pack')).toBeInTheDocument();
  expect(screen.getByText('pdf-skill')).toBeInTheDocument();
  expect(screen.getByText('1.0.0')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Скачать пак' })).toBeInTheDocument();
});

test('add content calls API', async () => {
  const { packs } = await import('../api/packs');
  renderPage();
  await userEvent.click(await screen.findByRole('button', { name: 'Добавить элемент' }));
  await userEvent.click(await screen.findByRole('button', { name: 'Добавить' }));
  await waitFor(() => expect(packs.addContent).toHaveBeenCalledWith(
    'my-pack', 'logo-skill', 'latest'));
});
```

- [ ] **Step 2: Run test to verify it fails**

Run (из `ui/`): `npx vitest run src/pages`
Expected: FAIL — PackPage маршрут ведёт на заглушку.

- [ ] **Step 3: Write implementation**

`ui/src/components/AddToPackDialog.tsx`:

```tsx
import { useState } from 'react';
import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button,
  TextField, MenuItem, FormControl, InputLabel, Select,
} from '@mui/material';
import type { ElementResponse } from '../types';

export default function AddToPackDialog({ elements, open, onClose, onAdd }: {
  elements: ElementResponse[];
  open: boolean;
  onClose: () => void;
  onAdd: (element: string, versionConstraint: string) => void;
}) {
  const [element, setElement] = useState(elements[0]?.slug ?? '');
  const [constraint, setConstraint] = useState(elements[0]?.latestVersion ?? 'latest');

  return (
    <Dialog open={open} onClose={onClose}>
      <DialogTitle>Добавить элемент в пак</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 400, mt: 1 }}>
        <FormControl fullWidth size="small">
          <InputLabel>Элемент</InputLabel>
          <Select
            value={element}
            label="Элемент"
            onChange={(e) => {
              setElement(e.target.value);
              const el = elements.find((x) => x.slug === e.target.value);
              setConstraint(el?.latestVersion ?? 'latest');
            }}
          >
            {elements.map((el) => (
              <MenuItem key={el.slug} value={el.slug}>
                {el.name} ({el.slug})
              </MenuItem>
            ))}
          </Select>
        </FormControl>
        <TextField
          size="small"
          label="Версия (latest или точная)"
          value={constraint}
          onChange={(e) => setConstraint(e.target.value)}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Отмена</Button>
        <Button
          onClick={() => onAdd(element, constraint)}
          disabled={!element || !constraint.trim()}
        >
          Добавить
        </Button>
      </DialogActions>
    </Dialog>
  );
}
```

`ui/src/pages/PackPage.tsx`:

```tsx
import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Typography, Paper, Button, Table, TableHead, TableRow,
  TableCell, TableBody, Chip,
} from '@mui/material';
import { packs } from '../api/packs';
import { elements as elementsApi } from '../api/elements';
import { toApiError } from '../api/client';
import { useSnackbar } from '../layout/SnackbarContext';
import { useAuth } from '../auth/KeycloakProvider';
import AddToPackDialog from '../components/AddToPackDialog';

export default function PackPage() {
  const { slug } = useParams<{ slug: string }>();
  const qc = useQueryClient();
  const { showError } = useSnackbar();
  const { authenticated } = useAuth();
  const [dialogOpen, setDialogOpen] = useState(false);

  const { data: pack } = useQuery({ queryKey: ['pack', slug], queryFn: () => packs.get(slug!) });
  const { data: allElements } = useQuery({
    queryKey: ['elements'],
    queryFn: elementsApi.list,
  });

  const addMutation = useMutation({
    mutationFn: (p: { element: string; constraint: string }) =>
      packs.addContent(slug!, p.element, p.constraint),
    onSuccess: () => {
      setDialogOpen(false);
      qc.invalidateQueries({ queryKey: ['pack', slug] });
    },
    onError: (e) => showError(toApiError(e).message),
  });

  if (!pack) return null;

  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="h5" sx={{ mb: 2 }}>Пак: {pack.slug}</Typography>
      {authenticated && (
        <Button variant="contained" sx={{ mb: 2 }} onClick={() => setDialogOpen(true)}>
          Добавить элемент
        </Button>
      )}
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Элемент</TableCell>
            <TableCell>Версия</TableCell>
            <TableCell>Ограничение</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {pack.contents.map((c) => (
            <TableRow key={c.element}>
              <TableCell>{c.element}</TableCell>
              <TableCell>{c.version ?? '—'}</TableCell>
              <TableCell>
                <Chip size="small" label={c.versionConstraint} />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <Button
        variant="outlined"
        sx={{ mt: 2 }}
        onClick={() => { window.location.href = packs.downloadPackUrl(slug!); }}
      >
        Скачать пак
      </Button>
      <AddToPackDialog
        elements={(allElements ?? []).filter((e) => e.type !== 'PACK')}
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        onAdd={(element, constraint) => addMutation.mutate({ element, constraint })}
      />
    </Paper>
  );
}
```

Modify `ui/src/App.tsx` — замените маршрут пака:

```tsx
import PackPage from './pages/PackPage';
// ...
<Route path="/packs/:slug" element={<PackPage />} />
```

- [ ] **Step 4: Run test to verify it passes**

Run (из `ui/`): `npx vitest run`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ui/src
git commit -m "feat: add pack page with contents, add-to-pack and pack download"
```

---

### Task 9: Команды, категории (админ), API-токены

**Files:**
- Create: `ui/src/pages/TeamsPage.tsx`, `ui/src/pages/AdminCategoriesPage.tsx`, `ui/src/pages/TokensPage.tsx`
- Modify: `ui/src/App.tsx` — маршруты `/teams`, `/admin/categories`, `/tokens`
- Test: `ui/src/pages/TokensPage.test.tsx`

**Interfaces:**
- Consumes: `teams.list/create/addMember`, `categories.list/create`, `api.post('/api/tokens')` (Task 2, Task 3).
- Produces:
  - TeamsPage: список команд, форма создания, форма добавления участника (ssoSubject + роль).
  - AdminCategoriesPage: список категорий, форма создания (slug, name, parentSlug).
  - TokensPage: список токенов (name, createdAt, lastUsedAt), форма создания; raw-токен показывается один раз в диалоге.

- [ ] **Step 1: Write the failing test**

`ui/src/pages/TokensPage.test.tsx`:

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import TokensPage from './TokensPage';

const postMock = vi.fn().mockResolvedValue({
  data: { token: 'skh_newtoken123', name: 'cli' },
});
const getMock = vi.fn().mockResolvedValue({
  data: [{ name: 'cli', createdAt: '2026-01-01T00:00:00Z', lastUsedAt: null, expiresAt: null }],
});

vi.mock('../api/client', async (original) => {
  const mod = await original<typeof import('../api/client')>();
  (mod as unknown as { api: unknown }).api = {
    post: postMock,
    get: getMock,
  };
  return mod;
});

vi.mock('../auth/KeycloakProvider', () => ({
  useAuth: () => ({ authenticated: true, token: 't', displayName: 'A', login: vi.fn(), logout: vi.fn() }),
}));

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <TokensPage />
    </QueryClientProvider>
  );
}

test('lists tokens and creates new one showing raw token once', async () => {
  renderPage();
  expect(await screen.findByText('cli')).toBeInTheDocument();

  await userEvent.type(screen.getByPlaceholderText('Имя токена'), 'new-cli');
  await userEvent.click(screen.getByRole('button', { name: 'Создать токен' }));

  await waitFor(() => expect(postMock).toHaveBeenCalled());
  expect(await screen.findByText(/skh_newtoken123/)).toBeInTheDocument();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run (из `ui/`): `npx vitest run src/pages`
Expected: FAIL — TokensPage не существует.

- [ ] **Step 3: Write implementation**

`ui/src/pages/TokensPage.tsx`:

```tsx
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Typography, Paper, Table, TableHead, TableRow, TableCell, TableBody,
  Button, TextField, Dialog, DialogTitle, DialogContent, DialogActions,
} from '@mui/material';
import { api, toApiError } from '../api/client';
import { useSnackbar } from '../layout/SnackbarContext';

interface TokenItem {
  name: string;
  createdAt: string;
  lastUsedAt: string | null;
  expiresAt: string | null;
}

export default function TokensPage() {
  const qc = useQueryClient();
  const { showError, showSuccess } = useSnackbar();
  const [name, setName] = useState('');
  const [rawToken, setRawToken] = useState<string | null>(null);

  const { data: tokens } = useQuery({
    queryKey: ['tokens'],
    queryFn: async () => (await api.get<TokenItem[]>('/api/tokens')).data,
  });

  const createMutation = useMutation({
    mutationFn: async () => {
      const res = await api.post<{ token: string; name: string }>('/api/tokens', { name });
      return res.data;
    },
    onSuccess: (data) => {
      setRawToken(data.token);
      setName('');
      showSuccess('Токен создан');
      qc.invalidateQueries({ queryKey: ['tokens'] });
    },
    onError: (e) => showError(toApiError(e).message),
  });

  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="h5" sx={{ mb: 2 }}>API-токены</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Токены используются CLI и AI-агентами (заголовок Authorization: Bearer).
      </Typography>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Имя</TableCell>
            <TableCell>Создан</TableCell>
            <TableCell>Последнее использование</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {(tokens ?? []).map((t) => (
            <TableRow key={t.name}>
              <TableCell>{t.name}</TableCell>
              <TableCell>{new Date(t.createdAt).toLocaleString()}</TableCell>
              <TableCell>{t.lastUsedAt ? new Date(t.lastUsedAt).toLocaleString() : '—'}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <TextField
        size="small"
        placeholder="Имя токена"
        value={name}
        onChange={(e) => setName(e.target.value)}
        sx={{ mt: 2, mr: 1 }}
      />
      <Button
        variant="contained"
        sx={{ mt: 2 }}
        onClick={() => createMutation.mutate()}
        disabled={!name.trim()}
      >
        Создать токен
      </Button>
      <Dialog open={rawToken !== null} onClose={() => setRawToken(null)}>
        <DialogTitle>Токен создан</DialogTitle>
        <DialogContent>
          <Typography variant="body2" sx={{ mb: 1 }}>
            Скопируйте токен сейчас — он больше не будет показан:
          </Typography>
          <TextField
            fullWidth
            value={rawToken ?? ''}
            InputProps={{ readOnly: true }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRawToken(null)}>Закрыть</Button>
        </DialogActions>
      </Dialog>
    </Paper>
  );
}
```

`ui/src/pages/TeamsPage.tsx`:

```tsx
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Typography, Paper, List, ListItem, ListItemText, TextField, Button,
  MenuItem, Select, FormControl, InputLabel, Stack,
} from '@mui/material';
import { teams as teamsApi } from '../api/teams';
import { toApiError } from '../api/client';
import { useSnackbar } from '../layout/SnackbarContext';

export default function TeamsPage() {
  const qc = useQueryClient();
  const { showError, showSuccess } = useSnackbar();
  const [slug, setSlug] = useState('');
  const [name, setName] = useState('');
  const [memberTeam, setMemberTeam] = useState('');
  const [memberSubject, setMemberSubject] = useState('');
  const [memberRole, setMemberRole] = useState('MEMBER');

  const { data: teams } = useQuery({ queryKey: ['teams'], queryFn: teamsApi.list });

  const createMutation = useMutation({
    mutationFn: () => teamsApi.create({ slug, name }),
    onSuccess: () => {
      showSuccess('Команда создана');
      setSlug(''); setName('');
      qc.invalidateQueries({ queryKey: ['teams'] });
    },
    onError: (e) => showError(toApiError(e).message),
  });

  const addMemberMutation = useMutation({
    mutationFn: () => teamsApi.addMember(memberTeam, memberSubject, memberRole),
    onSuccess: () => {
      showSuccess('Участник добавлен');
      setMemberSubject('');
      qc.invalidateQueries({ queryKey: ['teams'] });
    },
    onError: (e) => showError(toApiError(e).message),
  });

  return (
    <Stack spacing={3}>
      <Paper sx={{ p: 2 }}>
        <Typography variant="h5" sx={{ mb: 2 }}>Команды</Typography>
        <List>
          {(teams ?? []).map((t) => (
            <ListItem key={t.slug}>
              <ListItemText primary={t.name} secondary={t.slug} />
            </ListItem>
          ))}
        </List>
        <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
          <TextField size="small" label="slug" value={slug}
            onChange={(e) => setSlug(e.target.value)} />
          <TextField size="small" label="Название" value={name}
            onChange={(e) => setName(e.target.value)} />
          <Button variant="contained" onClick={() => createMutation.mutate()}
            disabled={!slug.trim() || !name.trim()}>
            Создать
          </Button>
        </Stack>
      </Paper>
      <Paper sx={{ p: 2 }}>
        <Typography variant="h6" sx={{ mb: 2 }}>Добавить участника</Typography>
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          <FormControl size="small" sx={{ minWidth: 160 }}>
            <InputLabel>Команда</InputLabel>
            <Select value={memberTeam} label="Команда"
              onChange={(e) => setMemberTeam(e.target.value)}>
              {(teams ?? []).map((t) => (
                <MenuItem key={t.slug} value={t.slug}>{t.slug}</MenuItem>
              ))}
            </Select>
          </FormControl>
          <TextField size="small" label="SSO subject" value={memberSubject}
            onChange={(e) => setMemberSubject(e.target.value)} />
          <FormControl size="small" sx={{ minWidth: 140 }}>
            <InputLabel>Роль</InputLabel>
            <Select value={memberRole} label="Роль"
              onChange={(e) => setMemberRole(e.target.value)}>
              <MenuItem value="OWNER">OWNER</MenuItem>
              <MenuItem value="MAINTAINER">MAINTAINER</MenuItem>
              <MenuItem value="MEMBER">MEMBER</MenuItem>
            </Select>
          </FormControl>
          <Button variant="contained" onClick={() => addMemberMutation.mutate()}
            disabled={!memberTeam || !memberSubject.trim()}>
            Добавить
          </Button>
        </Stack>
      </Paper>
    </Stack>
  );
}
```

`ui/src/pages/AdminCategoriesPage.tsx`:

```tsx
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Typography, Paper, List, ListItem, ListItemText, TextField, Button, Stack,
} from '@mui/material';
import { categories as categoriesApi } from '../api/categories';
import { toApiError } from '../api/client';
import { useSnackbar } from '../layout/SnackbarContext';

export default function AdminCategoriesPage() {
  const qc = useQueryClient();
  const { showError, showSuccess } = useSnackbar();
  const [slug, setSlug] = useState('');
  const [name, setName] = useState('');
  const [parentSlug, setParentSlug] = useState('');

  const { data: categories } = useQuery({ queryKey: ['categories'], queryFn: categoriesApi.list });

  const createMutation = useMutation({
    mutationFn: () => categoriesApi.create({
      slug, name, parentSlug: parentSlug || undefined,
    }),
    onSuccess: () => {
      showSuccess('Категория создана');
      setSlug(''); setName(''); setParentSlug('');
      qc.invalidateQueries({ queryKey: ['categories'] });
    },
    onError: (e) => showError(toApiError(e).message),
  });

  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="h5" sx={{ mb: 2 }}>Категории</Typography>
      <List>
        {(categories ?? []).map((c) => (
          <ListItem key={c.slug}>
            <ListItemText primary={c.name} secondary={c.parent ? `${c.slug} (в «${c.parent}»)` : c.slug} />
          </ListItem>
        ))}
      </List>
      <Stack direction="row" spacing={1} sx={{ mt: 1 }} flexWrap="wrap" useFlexGap>
        <TextField size="small" label="slug" value={slug}
          onChange={(e) => setSlug(e.target.value)} />
        <TextField size="small" label="Название" value={name}
          onChange={(e) => setName(e.target.value)} />
        <TextField size="small" label="Родитель (slug)" value={parentSlug}
          onChange={(e) => setParentSlug(e.target.value)} />
        <Button variant="contained" onClick={() => createMutation.mutate()}
          disabled={!slug.trim() || !name.trim()}>
          Создать
        </Button>
      </Stack>
    </Paper>
  );
}
```

Modify `ui/src/App.tsx` — замените маршруты:

```tsx
import TeamsPage from './pages/TeamsPage';
import AdminCategoriesPage from './pages/AdminCategoriesPage';
import TokensPage from './pages/TokensPage';
// ...
<Route path="/teams" element={<TeamsPage />} />
<Route path="/admin/categories" element={<AdminCategoriesPage />} />
<Route path="/tokens" element={<TokensPage />} />
```

- [ ] **Step 4: Run test to verify it passes**

Run (из `ui/`): `npx vitest run`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ui/src
git commit -m "feat: add teams, admin categories and API tokens pages"
```

---

### Task 10: Финальная проверка — сборка и smoke

**Files:**
- Modify: фиксы по результатам прогона

**Interfaces:**
- Consumes: все предыдущие задачи.
- Produces: production-сборка UI.

- [ ] **Step 1: Полный прогон тестов**

Run (из `ui/`): `npx vitest run`
Expected: все тесты PASS.

- [ ] **Step 2: TypeScript-проверка и сборка**

Run (из `ui/`): `npx tsc --noEmit && npm run build`
Expected: без ошибок; `ui/dist/` создан.

- [ ] **Step 3: Ручная smoke-проверка с backend'ом**

```bash
# из корня репозитория
docker compose up -d
mvn spring-boot:run &
cd ui && npm run dev
```
Expected: UI на `http://localhost:5173`; при настроенном Keycloak — вход, каталог с элементами.

- [ ] **Step 4: Commit (если были фиксы)**

```bash
git add ui/src
git commit -m "fix: stabilize UI tests and build"
```

---

## Отложено (следующие планы)

- **CLI** — отдельный план: `skillhub install/publish`, чтение `manifest.json`, Bearer-токен.
- Draft-версии и депрекация из UI (endpoint `POST .../versions/{v}/deprecate` на backend).
- Загрузка паков из UI (создание PACK-элемента через UI-форму).
- E2E-тесты (Playwright).
