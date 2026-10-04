# 2-Step Upload Wizard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Переделать страницу публикации элемента в визард из двух шагов: сначала загрузка архива с клиентским парсингом `manifest.json`, потом форма метаданных, предзаполненная из манифеста.

**Architecture:** Новый модуль `ui/src/lib/archive.ts` парсит zip в браузере через `fflate` (достаёт `manifest.json` и список записей). `UploadPage` переписывается на MUI `Stepper`: шаг 1 — dropzone + карточка манифеста + валидация, шаг 2 — форма метаданных с предзаполнением + список содержимого архива + публикация. Публикация использует существующие `elements.create` + `elements.publishVersion`. Бэкенд не меняется.

**Tech Stack:** React 18, MUI v6, @tanstack/react-query, fflate (новая зависимость), Vitest + Testing Library.

## Global Constraints

- Изменения только в `ui/`; бэкенд, эндпоинты и БД не трогаем.
- Единственная новая зависимость: `fflate`.
- Семвер-валидация должна совпадать с серверной (`ArchiveService.SEMVER`): `^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$`.
- Обязательные поля манифеста (как на сервере): `manifest.json` в корне архива, непустые `name` и `version`.
- Все тексты интерфейса — на русском, в стиле существующих страниц.
- Код без комментариев.
- Тесты запускаются из каталога `ui`: `npm test` (vitest run), сборка/типчек: `npm run build` (tsc && vite build).

---

### Task 1: Модуль парсинга архива `ui/src/lib/archive.ts`

**Files:**
- Create: `ui/src/lib/archive.ts`
- Test: `ui/src/lib/archive.test.ts`

**Interfaces:**
- Consumes: `fflate` (`unzipSync`, `strFromU8`), стандартный `File`.
- Produces (используется Task 2/3):
  ```ts
  export const SEMVER_PATTERN: RegExp; // /^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$/
  export interface ArchiveEntry { path: string; size: number }
  export interface ManifestInfo { name: string; version: string; description: string; type: string }
  export type ArchiveParseResult =
    | { ok: true; manifest: ManifestInfo; entries: ArchiveEntry[] }
    | { ok: false; error: string };
  export function inspectArchive(file: File): Promise<ArchiveParseResult>;
  ```

- [ ] **Step 1: Установить fflate**

Run (workdir `ui`): `npm install fflate`
Expected: package.json получает `"fflate"` в dependencies.

- [ ] **Step 2: Написать падающие тесты**

Создать `ui/src/lib/archive.test.ts`:

```ts
import { zipSync, strToU8 } from 'fflate';
import { inspectArchive, SEMVER_PATTERN } from './archive';

const VALID = { name: 'pdf-skill', version: '1.2.3', description: 'Desc', type: 'SKILL' };

function zipFile(entries: Record<string, string>): File {
  const files: Record<string, Uint8Array> = {};
  for (const [k, v] of Object.entries(entries)) files[k] = strToU8(v);
  return new File([zipSync(files)], 'element.zip', { type: 'application/zip' });
}

function withManifest(manifest: unknown, extra: Record<string, string> = {}): File {
  return zipFile(manifest == null ? extra : { 'manifest.json': JSON.stringify(manifest), ...extra });
}

test('parses manifest and entry list from a valid archive', async () => {
  const res = await inspectArchive(withManifest(VALID, { 'SKILL.md': '# hi', 'scripts/run.sh': 'echo' }));
  if (!res.ok) throw new Error(res.error);
  expect(res.manifest).toEqual(VALID);
  expect(res.entries).toEqual(
    expect.arrayContaining([
      expect.objectContaining({ path: 'SKILL.md' }),
      expect.objectContaining({ path: 'scripts/run.sh' }),
    ])
  );
});

test('defaults description and type to empty strings', async () => {
  const res = await inspectArchive(withManifest({ name: 'x', version: '1.0.0' }));
  if (!res.ok) throw new Error(res.error);
  expect(res.manifest.description).toBe('');
  expect(res.manifest.type).toBe('');
});

test('rejects archive without manifest.json in root', async () => {
  const res = await inspectArchive(withManifest(null, { 'SKILL.md': '# hi' }));
  expect(res).toEqual({ ok: false, error: 'В корне архива нет manifest.json' });
});

test('ignores manifest.json outside root', async () => {
  const res = await inspectArchive(zipFile({ 'docs/manifest.json': '{"name":"x","version":"1.0.0"}' }));
  expect(res).toEqual({ ok: false, error: 'В корне архива нет manifest.json' });
});

test('rejects invalid manifest json', async () => {
  const res = await inspectArchive(zipFile({ 'manifest.json': '{not json' }));
  expect(res).toEqual({ ok: false, error: 'manifest.json не является корректным JSON' });
});

test('rejects manifest without name', async () => {
  const res = await inspectArchive(withManifest({ version: '1.0.0' }));
  expect(res).toEqual({ ok: false, error: 'manifest.json: поле name обязательно' });
});

test('rejects manifest without version', async () => {
  const res = await inspectArchive(withManifest({ name: 'x' }));
  expect(res).toEqual({ ok: false, error: 'manifest.json: поле version обязательно' });
});

test('rejects non-semver version', async () => {
  const res = await inspectArchive(withManifest({ name: 'x', version: '1.0' }));
  expect(res).toEqual({
    ok: false,
    error: 'manifest.json: version должна быть в формате semver, например 1.2.3',
  });
});

test('rejects file that is not a zip', async () => {
  const res = await inspectArchive(new File(['hello'], 'x.zip', { type: 'application/zip' }));
  expect(res).toEqual({ ok: false, error: 'Файл не является корректным ZIP-архивом' });
});

test('semver pattern matches server rule', () => {
  expect(SEMVER_PATTERN.test('1.2.3')).toBe(true);
  expect(SEMVER_PATTERN.test('1.2.3-beta.1')).toBe(true);
  expect(SEMVER_PATTERN.test('1.2')).toBe(false);
  expect(SEMVER_PATTERN.test('v1.2.3')).toBe(false);
});
```

- [ ] **Step 3: Запустить тесты, убедиться что падают**

Run (workdir `ui`): `npm test -- src/lib/archive.test.ts`
Expected: FAIL — cannot find module `./archive`.

- [ ] **Step 4: Реализовать модуль**

Создать `ui/src/lib/archive.ts`:

```ts
import { unzipSync, strFromU8 } from 'fflate';

export const SEMVER_PATTERN = /^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$/;

export interface ArchiveEntry {
  path: string;
  size: number;
}

export interface ManifestInfo {
  name: string;
  version: string;
  description: string;
  type: string;
}

export type ArchiveParseResult =
  | { ok: true; manifest: ManifestInfo; entries: ArchiveEntry[] }
  | { ok: false; error: string };

export async function inspectArchive(file: File): Promise<ArchiveParseResult> {
  let data: Uint8Array;
  try {
    data = new Uint8Array(await file.arrayBuffer());
  } catch {
    return { ok: false, error: 'Не удалось прочитать файл' };
  }
  const entries: ArchiveEntry[] = [];
  let contents: Record<string, Uint8Array>;
  try {
    contents = unzipSync(data, {
      filter: (f) => {
        if (!f.name.endsWith('/')) entries.push({ path: f.name, size: f.originalSize });
        return f.name === 'manifest.json';
      },
    });
  } catch {
    return { ok: false, error: 'Файл не является корректным ZIP-архивом' };
  }
  const raw = contents['manifest.json'];
  if (!raw) return { ok: false, error: 'В корне архива нет manifest.json' };
  let obj: Record<string, unknown>;
  try {
    obj = JSON.parse(strFromU8(raw));
  } catch {
    return { ok: false, error: 'manifest.json не является корректным JSON' };
  }
  if (!obj || typeof obj !== 'object') {
    return { ok: false, error: 'manifest.json не является корректным JSON' };
  }
  const name = typeof obj.name === 'string' ? obj.name.trim() : '';
  if (!name) return { ok: false, error: 'manifest.json: поле name обязательно' };
  const version = typeof obj.version === 'string' ? obj.version.trim() : '';
  if (!version) return { ok: false, error: 'manifest.json: поле version обязательно' };
  if (!SEMVER_PATTERN.test(version)) {
    return { ok: false, error: 'manifest.json: version должна быть в формате semver, например 1.2.3' };
  }
  const description = typeof obj.description === 'string' ? obj.description : '';
  const type = typeof obj.type === 'string' ? obj.type : '';
  return { ok: true, manifest: { name, version, description, type }, entries };
}
```

- [ ] **Step 5: Запустить тесты, убедиться что проходят**

Run (workdir `ui`): `npm test -- src/lib/archive.test.ts`
Expected: PASS, все тесты зелёные.

- [ ] **Step 6: Commit**

```bash
git add ui/src/lib/archive.ts ui/src/lib/archive.test.ts ui/package.json ui/package-lock.json
git commit -m "feat: client-side archive manifest parsing for upload wizard"
```

---

### Task 2: Визард на UploadPage (шаг 1 — архив, шаг 2 — метаданные и публикация)

**Files:**
- Modify: `ui/src/pages/UploadPage.tsx` (полная перезапись)
- Modify: `ui/src/pages/UploadPage.test.tsx` (полная перезапись)

**Interfaces:**
- Consumes: `inspectArchive(file: File): Promise<ArchiveParseResult>` из Task 1; существующие `elements.create`, `elements.publishVersion`, `me.get`, `categories.list`, `toApiError`, `PageHeader`, `useSnackbar`, `useAuth`.
- Produces: страница `UploadPage` с тем же дефолтным экспортом; тестовые маркеры `data-testid="version-file"` (input file), `data-testid="archive-summary"`, `data-testid="archive-error"`, `data-testid="archive-entries"`.

- [ ] **Step 1: Перезаписать UploadPage.tsx**

Полностью заменить содержимое `ui/src/pages/UploadPage.tsx`:

```tsx
import { useRef, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  Alert, Autocomplete, Box, Button, List, ListItem, ListItemText, MenuItem, Paper, Stack,
  Step, StepLabel, Stepper, TextField, Typography,
} from '@mui/material';
import UploadIcon from '@mui/icons-material/Upload';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { elements as elementsApi } from '../api/elements';
import { categories as categoriesApi } from '../api/categories';
import { me as meApi } from '../api/me';
import { toApiError } from '../api/client';
import { useSnackbar } from '../layout/SnackbarContext';
import { useAuth } from '../auth/KeycloakProvider';
import { inspectArchive, type ArchiveParseResult } from '../lib/archive';
import PageHeader from '../components/PageHeader';
import type { ElementType } from '../types';

const ELEMENT_TYPES: ElementType[] = ['SKILL', 'SCRIPT', 'AGENT', 'HOOK', 'PACK', 'OTHER'];
const SLUG_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const STEPS = ['Архив', 'Метаданные'];

const slugify = (value: string) =>
  value.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');

export default function UploadPage() {
  const { authenticated } = useAuth();
  const { showError, showSuccess } = useSnackbar();
  const navigate = useNavigate();

  const [activeStep, setActiveStep] = useState(0);
  const [file, setFile] = useState<File | null>(null);
  const [archive, setArchive] = useState<ArchiveParseResult | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

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
  const [changelog, setChangelog] = useState('');

  const { data: meInfo } = useQuery({
    queryKey: ['me'],
    queryFn: meApi.get,
    enabled: authenticated,
  });
  const { data: categories } = useQuery({ queryKey: ['categories'], queryFn: categoriesApi.list });

  const createdRef = useRef<Set<string>>(new Set());
  const stepRef = useRef<'create' | 'publish'>('create');

  const submitMutation = useMutation({
    mutationFn: async () => {
      if (!createdRef.current.has(slug)) {
        stepRef.current = 'create';
        await elementsApi.create({
          slug,
          type,
          name,
          description: description || undefined,
          team: team || undefined,
          category: category || undefined,
          tags,
          visibility,
        });
        createdRef.current.add(slug);
      }
      stepRef.current = 'publish';
      return elementsApi.publishVersion(slug, file as File, changelog || undefined);
    },
    onSuccess: () => {
      showSuccess('Элемент опубликован');
      navigate(`/elements/${slug}`);
    },
    onError: (e) => {
      const err = toApiError(e);
      if (stepRef.current === 'publish') {
        showError(`Элемент создан, но не удалось загрузить версию: ${err.message}`);
      } else if (err.status === 409) {
        setSlugError('Элемент с таким slug уже существует');
      } else {
        showError(err.message);
      }
    },
  });

  if (!authenticated) return <Navigate to="/" replace />;

  const applyArchive = (parsed: ArchiveParseResult) => {
    setArchive(parsed);
    if (!parsed.ok) return;
    setName(parsed.manifest.name);
    setSlug(slugify(parsed.manifest.name));
    if ((ELEMENT_TYPES as string[]).includes(parsed.manifest.type)) {
      setType(parsed.manifest.type as ElementType);
    }
    setDescription(parsed.manifest.description);
  };

  const selectFile = (next: File) => {
    setFile(next);
    setArchive(null);
    inspectArchive(next).then(applyArchive);
  };

  const canProceed = archive?.ok === true;
  const canSubmit = canProceed && !!name.trim() && !!type
    && SLUG_PATTERN.test(slug) && (!team || !!visibility);
  const slugInvalid = slug.length > 0 && !SLUG_PATTERN.test(slug);
  const totalSize = archive?.ok ? archive.entries.reduce((sum, e) => sum + e.size, 0) : 0;

  const handleSubmit = () => {
    setSlugError(null);
    submitMutation.mutate();
  };

  return (
    <Stack spacing={3}>
      <PageHeader title="Загрузить элемент" />
      <Stepper activeStep={activeStep}>
        {STEPS.map((label) => (
          <Step key={label}><StepLabel>{label}</StepLabel></Step>
        ))}
      </Stepper>
      {activeStep === 0 && (
        <Paper sx={{ p: 3, maxWidth: 720 }}>
          <Stack spacing={2}>
            <Box
              onClick={() => fileInputRef.current?.click()}
              onDragOver={(e) => {
                e.preventDefault();
                setDragOver(true);
              }}
              onDragLeave={() => setDragOver(false)}
              onDrop={(e) => {
                e.preventDefault();
                setDragOver(false);
                const dropped = e.dataTransfer.files?.[0];
                if (dropped) selectFile(dropped);
              }}
              sx={{
                border: 2,
                borderStyle: 'dashed',
                borderColor: dragOver ? 'primary.main' : 'divider',
                borderRadius: 1,
                p: 3,
                cursor: 'pointer',
                textAlign: 'center',
                bgcolor: dragOver ? 'action.hover' : 'transparent',
                '&:hover': { borderColor: 'primary.main' },
              }}
            >
              <input
                ref={fileInputRef}
                hidden
                type="file"
                data-testid="version-file"
                onChange={(e) => {
                  const next = e.target.files?.[0];
                  if (next) selectFile(next);
                }}
              />
              <Stack alignItems="center" spacing={1}>
                <UploadIcon color={dragOver ? 'primary' : 'disabled'} />
                <Typography>
                  {dragOver ? 'Отпустите файл здесь' : 'Перетащите файл сюда или нажмите для выбора'}
                </Typography>
                {file && (
                  <Typography variant="body2" color="text.secondary">
                    {file.name}
                  </Typography>
                )}
              </Stack>
            </Box>
            {archive && !archive.ok && (
              <Alert severity="error" data-testid="archive-error">{archive.error}</Alert>
            )}
            {archive?.ok && (
              <Paper variant="outlined" data-testid="archive-summary" sx={{ p: 2 }}>
                <Typography variant="subtitle1">{archive.manifest.name}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {archive.manifest.version}
                  {archive.manifest.type ? ` · ${archive.manifest.type}` : ''}
                </Typography>
                {archive.manifest.description && (
                  <Typography variant="body2" sx={{ mt: 1 }}>{archive.manifest.description}</Typography>
                )}
                <Typography variant="caption" color="text.secondary">
                  {archive.entries.length} файлов, {totalSize} байт
                </Typography>
              </Paper>
            )}
            <Alert severity="info">
              <Typography variant="subtitle2" gutterBottom>Требования к архиву</Typography>
              <Box component="ul" sx={{ m: 0, pl: 2.5, typography: 'body2' }}>
                <li>ZIP-архив до 50 МБ (распакованное содержимое до 200 МБ, не более 5000 файлов)</li>
                <li>
                  В корне архива — <code>manifest.json</code> с обязательными полями:{' '}
                  <code>name</code> и <code>version</code> (semver, например 1.2.3)
                </li>
                <li>Название и описание на шаге 2 подставятся из манифеста</li>
                <li>Повторная загрузка той же версии элемента вернёт ошибку</li>
              </Box>
              <Box
                component="pre"
                sx={{ mt: 1, mb: 0, p: 1, borderRadius: 1, bgcolor: 'action.hover', overflowX: 'auto', typography: 'body2' }}
              >
{`{
  "name": "my-skill",
  "version": "1.0.0",
  "description": "Описание",
  "type": "SKILL"
}`}
              </Box>
            </Alert>
            <Box>
              <Button variant="contained" onClick={() => setActiveStep(1)} disabled={!archive?.ok}>
                Далее
              </Button>
            </Box>
          </Stack>
        </Paper>
      )}
      {activeStep === 1 && archive?.ok && (
        <Paper sx={{ p: 3, maxWidth: 720 }}>
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
                setSlugError(null);
              }}
              error={!!slugError || slugInvalid}
              helperText={
                slugError ??
                (slugInvalid ? 'Только строчные латинские буквы, цифры и дефисы' : undefined)
              }
            />
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
              <TextField
                select
                label="Тип"
                value={type}
                required
                onChange={(e) => setType(e.target.value as ElementType)}
              >
                {ELEMENT_TYPES.map((t) => (
                  <MenuItem key={t} value={t}>{t}</MenuItem>
                ))}
              </TextField>
              <TextField
                select
                label="Категория"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
              >
                <MenuItem value="">—</MenuItem>
                {(categories ?? []).map((c) => (
                  <MenuItem key={c.slug} value={c.slug}>{c.name}</MenuItem>
                ))}
              </TextField>
            </Box>
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
              <TextField
                select
                label="Команда"
                value={team}
                onChange={(e) => {
                  const next = e.target.value;
                  setTeam(next);
                  if (!next) setVisibility('PUBLIC');
                }}
              >
                <MenuItem value="">Без команды</MenuItem>
                {(meInfo?.teams ?? []).map((t) => (
                  <MenuItem key={t.slug} value={t.slug}>{t.name}</MenuItem>
                ))}
              </TextField>
              <TextField
                select
                label="Видимость"
                value={visibility}
                required={!!team}
                onChange={(e) => setVisibility(e.target.value as 'PUBLIC' | 'TEAM')}
              >
                <MenuItem value="PUBLIC">PUBLIC — доступен всем</MenuItem>
                <MenuItem value="TEAM" disabled={!team}>TEAM — только команде</MenuItem>
              </TextField>
            </Box>
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
            <Paper variant="outlined" sx={{ maxHeight: 240, overflow: 'auto' }}>
              <List dense data-testid="archive-entries">
                {archive.entries.map((e) => (
                  <ListItem key={e.path} disablePadding>
                    <ListItemText primary={e.path} secondary={`${e.size} B`} />
                  </ListItem>
                ))}
              </List>
            </Paper>
            <TextField label="Changelog" value={changelog} onChange={(e) => setChangelog(e.target.value)} />
            <Box sx={{ display: 'flex', gap: 1 }}>
              <Button startIcon={<ArrowBackIcon />} onClick={() => setActiveStep(0)}>
                Назад
              </Button>
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
      )}
    </Stack>
  );
}
```

- [ ] **Step 2: Перезаписать UploadPage.test.tsx**

Полностью заменить содержимое `ui/src/pages/UploadPage.test.tsx`:

```tsx
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { zipSync, strToU8 } from 'fflate';
import UploadPage from './UploadPage';
import { SnackbarProvider } from '../layout/SnackbarContext';

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

const VALID_MANIFEST = {
  name: 'Test Element',
  version: '1.0.0',
  description: 'Из манифеста',
  type: 'SKILL',
};

function zipFile(entries: Record<string, string>): File {
  const files: Record<string, Uint8Array> = {};
  for (const [k, v] of Object.entries(entries)) files[k] = strToU8(v);
  return new File([zipSync(files)], 'element.zip', { type: 'application/zip' });
}

function makeZip(manifest?: unknown, extra: Record<string, string> = {}): File {
  return zipFile(manifest == null ? extra : { 'manifest.json': JSON.stringify(manifest), ...extra });
}

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/upload']}>
        <SnackbarProvider>
          <Routes>
            <Route path="/upload" element={<UploadPage />} />
            <Route path="/elements/:slug" element={<div>element-page</div>} />
          </Routes>
        </SnackbarProvider>
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

async function passStep1(file: File) {
  await userEvent.upload(screen.getByTestId('version-file'), file);
  await screen.findByTestId('archive-summary');
  await userEvent.click(screen.getByRole('button', { name: 'Далее' }));
  await screen.findByLabelText(/^Команда/);
}

async function fillMetadata() {
  await userEvent.click(screen.getByLabelText(/^Команда/));
  await userEvent.click(await screen.findByRole('option', { name: 'Core Team' }));
  await userEvent.click(screen.getByLabelText(/^Видимость/));
  await userEvent.click(await screen.findByRole('option', { name: /PUBLIC — доступен всем/ }));
}

test('step 1: valid archive shows summary and enables next', async () => {
  renderPage();
  await screen.findByText('Требования к архиву');
  expect(screen.getByRole('button', { name: 'Далее' })).toBeDisabled();
  await userEvent.upload(
    screen.getByTestId('version-file'),
    makeZip(VALID_MANIFEST, { 'SKILL.md': '# hi' })
  );
  const summary = await screen.findByTestId('archive-summary');
  expect(summary).toHaveTextContent('Test Element');
  expect(summary).toHaveTextContent('1.0.0');
  expect(screen.getByRole('button', { name: 'Далее' })).toBeEnabled();
});

test('step 1: archive without manifest.json shows error and blocks step 2', async () => {
  renderPage();
  await screen.findByText('Требования к архиву');
  await userEvent.upload(
    screen.getByTestId('version-file'),
    makeZip(undefined, { 'SKILL.md': '# hi' })
  );
  expect(await screen.findByTestId('archive-error')).toHaveTextContent(/manifest\.json/i);
  expect(screen.getByRole('button', { name: 'Далее' })).toBeDisabled();
});

test('step 1: manifest validation errors block step 2', async () => {
  const cases: { manifest?: unknown; raw?: string; error: RegExp }[] = [
    { manifest: { version: '1.0.0' }, error: /поле name обязательно/ },
    { manifest: { name: 'x' }, error: /поле version обязательно/ },
    { manifest: { name: 'x', version: '1.0' }, error: /semver/ },
    { raw: '{not json', error: /корректным JSON/ },
  ];
  for (const c of cases) {
    const view = renderPage();
    const file = 'raw' in c ? zipFile({ 'manifest.json': c.raw }) : makeZip(c.manifest);
    await userEvent.upload(screen.getByTestId('version-file'), file);
    expect(await screen.findByTestId('archive-error')).toHaveTextContent(c.error);
    view.unmount();
  }
});
```

Оставшиеся тесты файла:

```tsx
test('step 1: non-zip file shows error and blocks step 2', async () => {
  renderPage();
  await userEvent.upload(
    screen.getByTestId('version-file'),
    new File(['hello'], 'broken.zip', { type: 'application/zip' })
  );
  expect(await screen.findByTestId('archive-error')).toHaveTextContent(/ZIP-архивом/);
  expect(screen.getByRole('button', { name: 'Далее' })).toBeDisabled();
});

test('accepts a file via drag and drop', async () => {
  renderPage();
  await screen.findByText(/Перетащите файл сюда/);
  fireEvent.drop(screen.getByText(/Перетащите файл сюда/), {
    dataTransfer: { files: [makeZip(VALID_MANIFEST)] },
  });
  expect(await screen.findByText('element.zip')).toBeInTheDocument();
});

test('step 2: prefills name, slug, type and description from manifest', async () => {
  renderPage();
  await passStep1(makeZip(VALID_MANIFEST, { 'SKILL.md': '# hi' }));
  expect(screen.getByLabelText(/^Название/)).toHaveValue('Test Element');
  expect(screen.getByLabelText(/^Slug/)).toHaveValue('test-element');
  expect(screen.getByLabelText(/^Тип/)).toHaveTextContent('SKILL');
  expect(screen.getByLabelText(/Описание/)).toHaveValue('Из манифеста');
  expect(screen.getByTestId('archive-entries')).toHaveTextContent('SKILL.md');
});

test('step 2: publish stays disabled until type is chosen for unknown manifest type', async () => {
  renderPage();
  await passStep1(makeZip({ ...VALID_MANIFEST, type: 'WIDGET' }));
  const publish = screen.getByRole('button', { name: /Опубликовать/i });
  expect(publish).toBeDisabled();
  await userEvent.click(screen.getByLabelText(/^Тип/));
  await userEvent.click(await screen.findByRole('option', { name: 'SCRIPT' }));
  expect(screen.getByRole('button', { name: /Опубликовать/i })).toBeEnabled();
});

test('creates element, publishes version and navigates to element page', async () => {
  renderPage();
  await passStep1(makeZip(VALID_MANIFEST));
  await fillMetadata();
  await userEvent.click(screen.getByRole('button', { name: /Опубликовать/i }));

  await waitFor(() =>
    expect(createMock).toHaveBeenCalledWith({
      slug: 'test-element',
      type: 'SKILL',
      name: 'Test Element',
      description: 'Из манифеста',
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
  await passStep1(makeZip(VALID_MANIFEST));
  await fillMetadata();
  await userEvent.click(screen.getByRole('button', { name: /Опубликовать/i }));

  expect(await screen.findByText(/уже существует/i)).toBeInTheDocument();
  expect(publishMock).not.toHaveBeenCalled();
});

test('shows snackbar with api message on non-409 publish error', async () => {
  publishMock.mockRejectedValue(Object.assign(new Error('Некорректный файл'), { status: 400 }));
  renderPage();
  await passStep1(makeZip(VALID_MANIFEST));
  await fillMetadata();
  await userEvent.click(screen.getByRole('button', { name: /Опубликовать/i }));

  expect(await screen.findByText(/Некорректный файл/)).toBeInTheDocument();
});

test('publish 409 shows publish-step snackbar and no slug error', async () => {
  publishMock.mockRejectedValue({ status: 409 });
  renderPage();
  await passStep1(makeZip(VALID_MANIFEST));
  await fillMetadata();
  await userEvent.click(screen.getByRole('button', { name: /Опубликовать/i }));

  expect(
    await screen.findByText(/Элемент создан, но не удалось загрузить версию/)
  ).toBeInTheDocument();
  expect(screen.queryByText(/уже существует/i)).not.toBeInTheDocument();
});

test('slug auto-generates from manifest name until edited manually', async () => {
  renderPage();
  await passStep1(makeZip(VALID_MANIFEST));
  const slugField = screen.getByLabelText(/^Slug/);
  expect(slugField).toHaveValue('test-element');
  await userEvent.type(slugField, 'x');
  await userEvent.type(screen.getByLabelText(/^Название/), '!');
  expect(slugField).toHaveValue('test-elementx');
});

test('team can be cleared after selection', async () => {
  renderPage();
  await passStep1(makeZip(VALID_MANIFEST));
  await userEvent.click(screen.getByLabelText('Команда'));
  await userEvent.click(await screen.findByRole('option', { name: 'Core Team' }));
  await userEvent.click(screen.getByLabelText('Команда'));
  await userEvent.click(await screen.findByRole('option', { name: 'Без команды' }));
  await userEvent.click(screen.getByLabelText(/^Видимость/));
  await userEvent.click(await screen.findByRole('option', { name: /PUBLIC — доступен всем/ }));
  await userEvent.click(screen.getByRole('button', { name: /Опубликовать/i }));
  await waitFor(() => expect(createMock).toHaveBeenCalled());
  expect(createMock.mock.calls[0][0].team).toBeUndefined();
  expect(createMock.mock.calls[0][0].visibility).toBe('PUBLIC');
});

test('TEAM visibility is disabled without a team', async () => {
  renderPage();
  await passStep1(makeZip(VALID_MANIFEST));
  await userEvent.click(screen.getByLabelText(/^Видимость/));
  expect(await screen.findByRole('option', { name: /TEAM — только команде/ }))
    .toHaveAttribute('aria-disabled', 'true');
});

test('selecting a team enables TEAM visibility', async () => {
  renderPage();
  await passStep1(makeZip(VALID_MANIFEST));
  await userEvent.click(screen.getByLabelText('Команда'));
  await userEvent.click(await screen.findByRole('option', { name: 'Core Team' }));
  await userEvent.click(screen.getByLabelText(/^Видимость/));
  expect(await screen.findByRole('option', { name: /TEAM — только команде/ }))
    .not.toHaveAttribute('aria-disabled', 'true');
});
```

- [ ] **Step 2: Запустить тесты, убедиться что проходят**

Run (workdir `ui`): `npm test -- src/pages/UploadPage.test.tsx`
Expected: PASS (все тесты файла зелёные).

- [ ] **Step 3: Прогнать типчек/сборку**

Run (workdir `ui`): `npm run build`
Expected: без ошибок tsc/vite.

- [ ] **Step 4: Commit**

```bash
git add ui/src/pages/UploadPage.tsx ui/src/pages/UploadPage.test.tsx
git commit -m "feat: two-step upload wizard with manifest prefill"
```

---

### Task 3: Не перетирать ручной ввод при замене файла

**Files:**
- Modify: `ui/src/pages/UploadPage.tsx`
- Test: `ui/src/pages/UploadPage.test.tsx` (добавить один тест)

**Interfaces:**
- Consumes: состояние формы из Task 2 (`name`, `slug`, `type`, `description` и их поля).
- Produces: флаги `nameTouched`, `typeTouched`, `descriptionTouched` (плюс существующий `slugTouched`); `applyArchive` перезаписывает только нетронутые поля.

- [ ] **Step 1: Добавить падающий тест**

В конец `ui/src/pages/UploadPage.test.tsx` добавить:

```tsx
test('replacing the file overwrites only untouched fields', async () => {
  renderPage();
  await passStep1(makeZip(VALID_MANIFEST));
  const nameField = screen.getByLabelText(/^Название/);
  await userEvent.clear(nameField);
  await userEvent.type(nameField, 'Ручное имя');
  await userEvent.click(screen.getByRole('button', { name: 'Назад' }));
  await userEvent.upload(
    screen.getByTestId('version-file'),
    makeZip({ name: 'Other', version: '2.0.0', description: 'Другое', type: 'SCRIPT' })
  );
  await screen.findByTestId('archive-summary');
  await userEvent.click(screen.getByRole('button', { name: 'Далее' }));

  expect(screen.getByLabelText(/^Название/)).toHaveValue('Ручное имя');
  expect(screen.getByLabelText(/^Тип/)).toHaveTextContent('SCRIPT');
  expect(screen.getByLabelText(/Описание/)).toHaveValue('Другое');
  expect(screen.getByLabelText(/^Slug/)).toHaveValue('other');
});
```

- [ ] **Step 2: Запустить тест, убедиться что падает**

Run (workdir `ui`): `npm test -- src/pages/UploadPage.test.tsx`
Expected: FAIL — «Название» содержит 'Other' (авто-перезапись затирает ручной ввод).

- [ ] **Step 3: Добавить touched-флаги в UploadPage.tsx**

Рядом с существующими `useState` формы добавить:

```tsx
const [nameTouched, setNameTouched] = useState(false);
const [typeTouched, setTypeTouched] = useState(false);
const [descriptionTouched, setDescriptionTouched] = useState(false);
```

Заменить `applyArchive`:

```tsx
const applyArchive = (parsed: ArchiveParseResult) => {
  setArchive(parsed);
  if (!parsed.ok) return;
  if (!nameTouched) setName(parsed.manifest.name);
  if (!slugTouched) setSlug(slugify(parsed.manifest.name));
  if (!typeTouched && (ELEMENT_TYPES as string[]).includes(parsed.manifest.type)) {
    setType(parsed.manifest.type as ElementType);
  }
  if (!descriptionTouched) setDescription(parsed.manifest.description);
};
```

Обновить `onChange` соответствующих полей (шаг 2):

- Название:
```tsx
onChange={(e) => {
  setNameTouched(true);
  setName(e.target.value);
  if (!slugTouched) setSlug(slugify(e.target.value));
}}
```
- Тип (select):
```tsx
onChange={(e) => {
  setTypeTouched(true);
  setType(e.target.value as ElementType);
}}
```
- Описание:
```tsx
onChange={(e) => {
  setDescriptionTouched(true);
  setDescription(e.target.value);
}}
```

- [ ] **Step 4: Запустить тесты, убедиться что проходят**

Run (workdir `ui`): `npm test -- src/pages/UploadPage.test.tsx`
Expected: PASS, включая новый тест.

- [ ] **Step 5: Полный прогон и сборка**

Run (workdir `ui`): `npm test`
Expected: все тесты UI проходят.

Run (workdir `ui`): `npm run build`
Expected: без ошибок.

- [ ] **Step 6: Commit**

```bash
git add ui/src/pages/UploadPage.tsx ui/src/pages/UploadPage.test.tsx
git commit -m "feat: preserve hand-edited fields when replacing archive"
```
