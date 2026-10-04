# Element Upload Page (`/upload`) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an `/upload` page where an authorized user creates an element and uploads its first version file, then is redirected to the element page.

**Architecture:** Single MUI form page (`UploadPage.tsx`) using react-query (`useQuery` for user teams + categories, `useMutation` for the submit flow: `elements.create` → `elements.publishVersion` → navigate). Route registered in `App.tsx`; entry button added to `AppLayout` behind the `authenticated` flag.

**Tech Stack:** React 18, TypeScript, MUI 6, @tanstack/react-query 5, react-router-dom 6, vitest + @testing-library/react.

**Spec:** `docs/superpowers/specs/2026-10-04-upload-page-design.md`

## Global Constraints

- UI copy in Russian, matching existing pages (e.g. «Загрузить элемент», «Опубликовать»).
- Follow existing page patterns: `Paper` container, `useSnackbar` for toasts, `toApiError` for error mapping, react-query for data fetching.
- No code comments in new code.
- Existing API client signatures must be reused as-is: `elements.create(body)`, `elements.publishVersion(slug, file, changelog?)`, `me.get()`, `categories.list()` (see `ui/src/api/elements.ts`, `ui/src/api/me.ts`, `ui/src/api/categories.ts`).
- Tests use vitest globals (`test`, `vi` are global; `describe` not used in existing tests).
- All commands run from the `ui/` directory.

## File Structure

- Create: `ui/src/pages/UploadPage.tsx` — the upload form page (single responsibility: form state + submit flow).
- Create: `ui/src/pages/UploadPage.test.tsx` — page tests.
- Modify: `ui/src/App.tsx` — register the `/upload` route.
- Modify: `ui/src/layout/AppLayout.tsx` — add «Загрузить элемент» button for authenticated users.
- Modify: `ui/src/layout/AppLayout.test.tsx` — button visibility tests.

---

### Task 1: UploadPage with form and submit flow

**Files:**
- Create: `ui/src/pages/UploadPage.tsx`
- Test: `ui/src/pages/UploadPage.test.tsx`

**Interfaces:**
- Consumes: `elements.create({ slug, type, name, description?, team, category?, tags?, visibility })` → `Promise<ElementResponse>`; `elements.publishVersion(slug: string, file: File, changelog?: string)` → `Promise<VersionResponse>` (from `../api/elements`); `me.get()` → `Promise<MeResponse>` with `teams: { slug, name, role }[]` (from `../api/me`); `categories.list()` → `Promise<CategoryResponse[]>` (from `../api/categories`); `toApiError(e)` → `{ status, code, message, details }` (from `../api/client`); `useSnackbar()` → `{ showError, showSuccess }`; `useAuth()` → `{ authenticated, ... }`.
- Produces: default-exported `UploadPage` React component registered at `/upload` in Task 2.

- [ ] **Step 1: Write the failing test**

Create `ui/src/pages/UploadPage.test.tsx`:

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import UploadPage from './UploadPage';

const { createMock, publishMock, meMock, categoriesMock } = vi.hoisted(() => ({
  createMock: vi.fn(),
  publishMock: vi.fn(),
  meMock: vi.fn(),
  categoriesMock: vi.fn(),
}));

vi.mock('../api/elements', () => ({
  elements: { create: createMock, publishVersion: publishMock },
}));

vi.mock('../api/me', () => ({
  me: { get: meMock },
}));

vi.mock('../api/categories', () => ({
  categories: { list: categoriesMock },
}));

vi.mock('../api/client', () => ({
  toApiError: (e: unknown) => ({
    status: (e as { status?: number }).status ?? 0,
    code: 'ERROR',
    message: e instanceof Error ? e.message : String(e),
    details: null,
  }),
}));

vi.mock('../auth/KeycloakProvider', () => ({
  useAuth: () => ({ authenticated: true, token: 't', displayName: 'A', login: vi.fn(), logout: vi.fn() }),
}));

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/upload']}>
        <Routes>
          <Route path="/upload" element={<UploadPage />} />
          <Route path="/elements/:slug" element={<div>element-page</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

beforeEach(() => {
  createMock.mockReset().mockResolvedValue({ slug: 'test-element' });
  publishMock.mockReset().mockResolvedValue({ version: '1.0.0', status: 'PUBLISHED' });
  meMock.mockReset().mockResolvedValue({
    username: 'alice',
    admin: false,
    teams: [{ slug: 'core', name: 'Core Team', role: 'OWNER' }],
  });
  categoriesMock.mockReset().mockResolvedValue([
    { slug: 'dev-tools', name: 'Dev Tools', parent: null, icon: null },
  ]);
});

async function fillForm() {
  await userEvent.type(await screen.findByLabelText('Название'), 'Test Element');
  await userEvent.click(screen.getByLabelText('Тип'));
  await userEvent.click(await screen.findByRole('option', { name: 'SKILL' }));
  await userEvent.click(screen.getByLabelText('Команда'));
  await userEvent.click(await screen.findByRole('option', { name: 'Core Team' }));
  await userEvent.click(screen.getByLabelText('Видимость'));
  await userEvent.click(await screen.findByRole('option', { name: /PUBLIC — доступен всем/ }));
  await userEvent.upload(
    screen.getByTestId('version-file'),
    new File(['data'], 'element.zip', { type: 'application/zip' })
  );
}

test('renders form and loads teams and categories', async () => {
  renderPage();
  expect(await screen.findByLabelText('Название')).toBeInTheDocument();
  expect(meMock).toHaveBeenCalled();
  expect(categoriesMock).toHaveBeenCalled();
  await userEvent.click(screen.getByLabelText('Команда'));
  expect(await screen.findByRole('option', { name: 'Core Team' })).toBeInTheDocument();
  await userEvent.click(screen.getByLabelText('Категория'));
  expect(await screen.findByRole('option', { name: 'Dev Tools' })).toBeInTheDocument();
});

test('auto-generates slug from name until slug is edited manually', async () => {
  renderPage();
  const slugField = await screen.findByLabelText('Slug');
  await userEvent.type(screen.getByLabelText('Название'), 'Test Element');
  expect(slugField).toHaveValue('test-element');
  await userEvent.type(slugField, 'x');
  expect(slugField).toHaveValue('test-elementx');
  await userEvent.type(screen.getByLabelText('Название'), '!');
  expect(slugField).toHaveValue('test-elementx');
});

test('disables submit until required fields are filled', async () => {
  renderPage();
  await screen.findByLabelText('Название');
  expect(screen.getByRole('button', { name: /Опубликовать/i })).toBeDisabled();
  expect(createMock).not.toHaveBeenCalled();
});

test('creates element, publishes version and navigates to element page', async () => {
  renderPage();
  await fillForm();
  await userEvent.click(screen.getByRole('button', { name: /Опубликовать/i }));

  await waitFor(() =>
    expect(createMock).toHaveBeenCalledWith({
      slug: 'test-element',
      type: 'SKILL',
      name: 'Test Element',
      description: undefined,
      team: 'core',
      category: undefined,
      tags: [],
      visibility: 'PUBLIC',
    })
  );
  expect(publishMock).toHaveBeenCalledTimes(1);
  expect(publishMock.mock.calls[0][0]).toBe('test-element');
  expect(publishMock.mock.calls[0][1]).toBeInstanceOf(File);
  expect(await screen.findByText('element-page')).toBeInTheDocument();
});

test('shows slug field error on 409 conflict', async () => {
  createMock.mockRejectedValue({ status: 409 });
  renderPage();
  await fillForm();
  await userEvent.click(screen.getByRole('button', { name: /Опубликовать/i }));

  expect(await screen.findByText(/уже существует/i)).toBeInTheDocument();
  expect(publishMock).not.toHaveBeenCalled();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run src/pages/UploadPage.test.tsx`
Expected: FAIL — "Failed to resolve import ./UploadPage" (page does not exist yet).

- [ ] **Step 3: Implement UploadPage**

Create `ui/src/pages/UploadPage.tsx`:

```tsx
import { useRef, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  Autocomplete, Box, Button, MenuItem, Paper, Stack, TextField, Typography,
} from '@mui/material';
import UploadIcon from '@mui/icons-material/Upload';
import { elements as elementsApi } from '../api/elements';
import { categories as categoriesApi } from '../api/categories';
import { me as meApi } from '../api/me';
import { toApiError } from '../api/client';
import { useSnackbar } from '../layout/SnackbarContext';
import { useAuth } from '../auth/KeycloakProvider';
import type { ElementType } from '../types';

const ELEMENT_TYPES: ElementType[] = ['SKILL', 'SCRIPT', 'AGENT', 'HOOK', 'PACK', 'OTHER'];
const SLUG_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/;

const slugify = (value: string) =>
  value.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');

export default function UploadPage() {
  const { authenticated } = useAuth();
  const { showError, showSuccess } = useSnackbar();
  const navigate = useNavigate();

  const [name, setName] = useState('');
  const [slug, setSlug] = useState('');
  const [slugTouched, setSlugTouched] = useState(false);
  const [slugError, setSlugError] = useState<string | null>(null);
  const [type, setType] = useState<ElementType | ''>('');
  const [team, setTeam] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('');
  const [tags, setTags] = useState<string[]>([]);
  const [visibility, setVisibility] = useState<'PUBLIC' | 'TEAM' | ''>('');
  const [file, setFile] = useState<File | null>(null);
  const [changelog, setChangelog] = useState('');

  const { data: meInfo } = useQuery({
    queryKey: ['me'],
    queryFn: meApi.get,
    enabled: authenticated,
  });
  const { data: categories } = useQuery({ queryKey: ['categories'], queryFn: categoriesApi.list });

  const createdRef = useRef<Set<string>>(new Set());

  const submitMutation = useMutation({
    mutationFn: async () => {
      if (!createdRef.current.has(slug)) {
        await elementsApi.create({
          slug,
          type,
          name,
          description: description || undefined,
          team,
          category: category || undefined,
          tags,
          visibility,
        });
        createdRef.current.add(slug);
      }
      return elementsApi.publishVersion(slug, file as File, changelog || undefined);
    },
    onSuccess: () => {
      showSuccess('Элемент опубликован');
      navigate(`/elements/${slug}`);
    },
    onError: (e) => {
      const err = toApiError(e);
      if (err.status === 409) {
        setSlugError('Элемент с таким slug уже существует');
      } else {
        showError(err.message);
      }
    },
  });

  if (!authenticated) return <Navigate to="/" replace />;

  const canSubmit =
    !!name.trim() && SLUG_PATTERN.test(slug) && !!type && !!team && !!visibility && !!file;
  const slugInvalid = slug.length > 0 && !SLUG_PATTERN.test(slug);

  const handleSubmit = () => {
    setSlugError(null);
    submitMutation.mutate();
  };

  return (
    <Paper sx={{ p: 3, maxWidth: 720 }}>
      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2 }}>
        <UploadIcon sx={{ color: 'primary.main' }} />
        <Typography variant="h4" component="h1">Загрузить элемент</Typography>
      </Stack>
      <Stack spacing={2}>
        <TextField
          label="Название"
          value={name}
          required
          onChange={(e) => {
            setName(e.target.value);
            if (!slugTouched) setSlug(slugify(e.target.value));
          }}
        />
        <TextField
          label="Slug"
          value={slug}
          required
          onChange={(e) => {
            setSlugTouched(true);
            setSlug(e.target.value);
          }}
          error={!!slugError || slugInvalid}
          helperText={
            slugError ??
            (slugInvalid ? 'Только строчные латинские буквы, цифры и дефисы' : undefined)
          }
        />
        <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap>
          <TextField
            select
            label="Тип"
            value={type}
            required
            sx={{ minWidth: 160 }}
            onChange={(e) => setType(e.target.value as ElementType)}
          >
            {ELEMENT_TYPES.map((t) => (
              <MenuItem key={t} value={t}>{t}</MenuItem>
            ))}
          </TextField>
          <TextField
            select
            label="Команда"
            value={team}
            required
            sx={{ minWidth: 200 }}
            onChange={(e) => setTeam(e.target.value)}
          >
            {(meInfo?.teams ?? []).map((t) => (
              <MenuItem key={t.slug} value={t.slug}>{t.name}</MenuItem>
            ))}
          </TextField>
          <TextField
            select
            label="Видимость"
            value={visibility}
            required
            sx={{ minWidth: 220 }}
            onChange={(e) => setVisibility(e.target.value as 'PUBLIC' | 'TEAM')}
          >
            <MenuItem value="PUBLIC">PUBLIC — доступен всем</MenuItem>
            <MenuItem value="TEAM">TEAM — только команде</MenuItem>
          </TextField>
        </Stack>
        <TextField
          select
          label="Категория"
          value={category}
          sx={{ maxWidth: 240 }}
          onChange={(e) => setCategory(e.target.value)}
        >
          <MenuItem value="">—</MenuItem>
          {(categories ?? []).map((c) => (
            <MenuItem key={c.slug} value={c.slug}>{c.name}</MenuItem>
          ))}
        </TextField>
        <TextField
          label="Описание"
          value={description}
          multiline
          minRows={3}
          onChange={(e) => setDescription(e.target.value)}
        />
        <Autocomplete
          multiple
          freeSolo
          options={[]}
          value={tags}
          onChange={(_, newValue) => setTags(newValue as string[])}
          renderInput={(params) => (
            <TextField {...params} label="Теги" placeholder="Введите тег и нажмите Enter" />
          )}
        />
        <Stack direction="row" spacing={2} alignItems="center">
          <Button variant="outlined" component="label">
            Выбрать файл
            <input
              hidden
              type="file"
              data-testid="version-file"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
          </Button>
          <Typography variant="body2" color="text.secondary">
            {file ? file.name : 'Файл первой версии (например, .zip)'}
          </Typography>
        </Stack>
        <TextField label="Changelog" value={changelog} onChange={(e) => setChangelog(e.target.value)} />
        <Box>
          <Button
            variant="contained"
            startIcon={<UploadIcon />}
            onClick={handleSubmit}
            disabled={!canSubmit || submitMutation.isPending}
          >
            Опубликовать
          </Button>
        </Box>
      </Stack>
    </Paper>
  );
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run src/pages/UploadPage.test.tsx`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add ui/src/pages/UploadPage.tsx ui/src/pages/UploadPage.test.tsx
git commit -m "feat: add upload page for creating elements with first version"
```

---

### Task 2: Register route and add navigation button

**Files:**
- Modify: `ui/src/App.tsx`
- Modify: `ui/src/layout/AppLayout.tsx:11-19,74`
- Test: `ui/src/layout/AppLayout.test.tsx`

**Interfaces:**
- Consumes: default export `UploadPage` from `../pages/UploadPage` (Task 1).
- Produces: route `/upload` reachable from the header via «Загрузить элемент» link (visible only when `authenticated`).

- [ ] **Step 1: Write the failing test**

Replace the mock at the top of `ui/src/layout/AppLayout.test.tsx` and add two tests. Full updated file:

```tsx
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import AppLayout from './AppLayout';

const { authState } = vi.hoisted(() => ({
  authState: { authenticated: true, displayName: 'Alice' as string | null },
}));

vi.mock('../auth/KeycloakProvider', () => ({
  useAuth: () => ({ token: 'x', isAdmin: false, login: vi.fn(), logout: vi.fn(), ...authState }),
}));

function renderLayout() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/" element={<div />} />
        </Route>
      </Routes>
    </MemoryRouter>
  );
}

test('renders navigation with all menu items and user name', () => {
  authState.authenticated = true;
  renderLayout();
  expect(screen.getByText('SkillHub')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Каталог' })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Команды' })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'API-токены' })).toBeInTheDocument();
  expect(screen.getByText('Alice')).toBeInTheDocument();
});

test('shows upload button when authenticated', () => {
  authState.authenticated = true;
  renderLayout();
  expect(screen.getByRole('link', { name: 'Загрузить элемент' })).toBeInTheDocument();
});

test('hides upload button when not authenticated', () => {
  authState.authenticated = false;
  renderLayout();
  expect(screen.queryByRole('link', { name: 'Загрузить элемент' })).not.toBeInTheDocument();
  expect(screen.queryByText('Alice')).not.toBeInTheDocument();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run src/layout/AppLayout.test.tsx`
Expected: FAIL — "shows upload button when authenticated" cannot find the link.

- [ ] **Step 3: Implement route and button**

In `ui/src/App.tsx` add the import and route:

```tsx
import UploadPage from './pages/UploadPage';
```

and inside the AppLayout route group, after the `/tokens` route:

```tsx
        <Route path="/upload" element={<UploadPage />} />
```

In `ui/src/layout/AppLayout.tsx`:

1. Add import next to the other icon imports:

```tsx
import UploadIcon from '@mui/icons-material/Upload';
```

2. Add `RouterLink` is already imported. After the nav items `<Box ...>` block (after line 74, before `<Box sx={{ flexGrow: 1 }} />`), insert:

```tsx
          {authenticated && (
            <Button
              component={RouterLink}
              to="/upload"
              startIcon={<UploadIcon />}
              sx={{
                color: '#9b968c',
                px: 2,
                borderRadius: 1,
                whiteSpace: 'nowrap',
                '&:hover': { backgroundColor: '#23272d', color: '#ece9e2' },
              }}
            >
              Загрузить элемент
            </Button>
          )}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `npx vitest run src/layout/AppLayout.test.tsx src/App.test.tsx`
Expected: PASS (all tests)

- [ ] **Step 5: Run the full suite and typecheck**

Run: `npx vitest run`
Expected: PASS (all existing tests still pass)

Run: `npm run build`
Expected: completes without TypeScript errors

- [ ] **Step 6: Commit**

```bash
git add ui/src/App.tsx ui/src/layout/AppLayout.tsx ui/src/layout/AppLayout.test.tsx
git commit -m "feat: add /upload route and header upload button"
```
