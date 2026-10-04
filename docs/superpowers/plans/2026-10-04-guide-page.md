# Guide Page (инструкция для пользователя) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Отдельная страница `/guide` в UI с полной инструкцией для конечного пользователя (markdown + react-markdown, видна всем).

**Architecture:** Статический markdown `ui/src/content/user-guide.md`, импортируемый через Vite `?raw`, рендерится `react-markdown` + `remark-gfm` с маппингом на MUI-компоненты (тема light/dark подхватывается автоматически). Роут `/guide` в `App.tsx`, пункт «Инструкция» в общем `navItems` в `AppLayout.tsx`.

**Tech Stack:** React 18, MUI 6, react-markdown ^10, remark-gfm ^4, Vitest + Testing Library.

## Global Constraints

- Новые зависимости только: `react-markdown`, `remark-gfm` (спека «Зависимости»).
- Пункт «Инструкция» виден всем, включая неавторизованных (спека «Видимость»); роут `/guide`.
- Только цвета из темы (`theme.ts`) — никаких хардкодов цвета, кроме существующего стиля кодовых блоков, согласованного с AppBar (`#23272d` фон — как активные пункты меню; допустимо, т.к. это нейтральный тёмный фон для кода в обеих темах).
- Команды в гайде обобщённые; адрес хаба — плейсхолдер `<адрес хаба>` (спека «Содержание»).
- TypeScript strict; все npm-команды — из `ui/`.
- Коммит-стиль: `feat:`, `test:`, `chore:`.

---

### Task 1: Dependencies + content + GuidePage + роут

**Files:**
- Create: `ui/src/content/user-guide.md`
- Create: `ui/src/pages/GuidePage.tsx`
- Modify: `ui/src/App.tsx` (импорт + роут)
- Test: `ui/src/pages/GuidePage.test.tsx`

**Interfaces:**
- Consumes: ничего (статический контент).
- Produces: `GuidePage` (default export) — later Task 2 не зависит от него напрямую (только роут через App.tsx).

- [ ] **Step 1: Install dependencies**

Run: `npm install react-markdown remark-gfm`
Expected: добавлены в `dependencies` в `ui/package.json`.

- [ ] **Step 2: Verify `?raw` type support**

Проверить `ui/src/vite-env.d.ts` — должна быть строка `/// <reference types="vite/client" />` (она даёт типы для `*.md?raw`). Если её нет — добавить.

- [ ] **Step 3: Write the failing test**

`ui/src/pages/GuidePage.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import GuidePage from './GuidePage';

function renderPage() {
  return render(
    <MemoryRouter>
      <GuidePage />
    </MemoryRouter>
  );
}

test('renders guide sections', () => {
  renderPage();
  expect(screen.getByRole('heading', { name: 'Инструкция' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Быстрый старт: установка скиллов' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Поиск' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Публикация' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'FAQ' })).toBeInTheDocument();
});

test('renders markdown as components, not raw text', () => {
  renderPage();
  expect(screen.getByRole('heading', { level: 1, name: 'Инструкция' })).toBeInTheDocument();
  expect(screen.getByText(/skillhub install/)).toBeInTheDocument();
  expect(screen.getByText(/skillhub publish/)).toBeInTheDocument();
});

test('renders command reference table (remark-gfm)', () => {
  renderPage();
  expect(screen.getByRole('table')).toBeInTheDocument();
  expect(screen.getByText('Команда')).toBeInTheDocument();
});
```

- [ ] **Step 4: Run test to verify it fails**

Run: `npm test -- GuidePage`
Expected: FAIL — cannot resolve `./GuidePage`.

- [ ] **Step 5: Write `ui/src/content/user-guide.md`**

````markdown
# Инструкция

SkillHub — корпоративное хранилище скиллов, скриптов, агентов и паков для AI-агентов и людей. Найдите нужный элемент в каталоге, установите его в проект одной командой или скачайте архивом. Свои элементы можно опубликовать через веб или CLI.

## Быстрый старт: установка скиллов

1. Установите CLI (нужен Node.js 18+):

```bash
npm install -g @skillhub/cli
```

2. Выполните вход (адрес хаба спросите у коллег или возьмите в корпоративном портале). Токен создаётся на странице «API-токены»:

```bash
skillhub login --url <адрес хаба>
```

3. Установите скилл в проект:

```bash
skillhub install <slug>@<version>
```

CLI сам определит, куда класть скиллы: `.opencode/skills/` (если в проекте есть `.opencode/`) или `.claude/skills/` (если есть `.claude/` или `CLAUDE.md`). Список установленных версий фиксируется в файле `skillhub.lock` в корне проекта.

Без CLI: откройте страницу элемента в каталоге, выберите версию и нажмите «Скачать» — получите zip-архив.

## Поиск

В каталоге работает поиск по названию и описанию, фильтры по типу элемента и сортировка по релевантности, рейтингу и загрузкам.

Из терминала:

```bash
skillhub search <query>
```

## Публикация

### Через веб

Нажмите «Загрузить элемент» в шапке, заполните форму и загрузите zip-архив с `manifest.json`. Шаблон manifest показан прямо на странице загрузки.

### Через CLI

Подготовьте папку с элементом и `manifest.json` внутри:

```json
{
  "name": "Мой скилл",
  "version": "1.0.0",
  "description": "Что делает скилл",
  "type": "SKILL"
}
```

Затем опубликуйте:

```bash
skillhub publish ./my-skill
```

Для публикации нужен API-токен: создайте его на странице «API-токены» и сохраните через `skillhub login`.

## Справочник команд

| Команда | Что делает |
| --- | --- |
| `skillhub login --url <адрес хаба>` | Сохранить адрес и API-токен |
| `skillhub search <query>` | Поиск по каталогу |
| `skillhub install <slug>@<version>` | Установить элемент или пак в проект |
| `skillhub install <slug> --all` | Установить во все обнаруженные платформы |
| `skillhub publish <dir>` | Опубликовать новую версию из папки |
| `skillhub whoami` | Проверить, под кем выполнен вход |

## FAQ

**Где взять API-токен?**
Войдите через SSO, откройте страницу «API-токены» и создайте токен. Затем выполните `skillhub login`.

**Куда устанавливаются скиллы?**
В `.opencode/skills/` или `.claude/skills/` внутри проекта (авто-детект). Можно указать папку вручную: `skillhub install <slug> --target <путь>`.

**Как обновить скилл на новую версию?**
Выполните `skillhub install <slug>@<новая версия>`. Чтобы переустановить ту же версию — добавьте `--force`.

**CLI пишет, что нет токена (401)?**
Выполните `skillhub login` заново или задайте переменные окружения `SKILLHUB_URL` и `SKILLHUB_TOKEN` — они переопределяют сохранённую конфигурацию (удобно в CI).
````

- [ ] **Step 6: Write `ui/src/pages/GuidePage.tsx`**

```tsx
import ReactMarkdown, { type Components } from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Box, Link, Paper, Typography } from '@mui/material';
import guide from '../content/user-guide.md?raw';

const heading =
  (variant: 'h4' | 'h5' | 'h6') =>
  ({ children }: { children?: React.ReactNode }) => (
    <Typography variant={variant} sx={{ mt: 4, mb: 1.5 }} gutterBottom>
      {children}
    </Typography>
  );

const components: Components = {
  h1: heading('h4'),
  h2: heading('h5'),
  h3: heading('h6'),
  p: ({ children }) => (
    <Typography variant="body1" sx={{ mb: 1.5 }}>
      {children}
    </Typography>
  ),
  a: ({ href, children }) => (
    <Link href={href} target="_blank" rel="noreferrer">
      {children}
    </Link>
  ),
  code: ({ className, children }) =>
    /language-/.test(className ?? '') ? (
      <code className={className}>{children}</code>
    ) : (
      <Box
        component="code"
        sx={{
          fontFamily: 'monospace',
          fontSize: '0.875em',
          bgcolor: 'action.hover',
          borderRadius: 0.5,
          px: 0.5,
          py: 0.25,
          wordBreak: 'break-word',
        }}
      >
        {children}
      </Box>
    ),
  pre: ({ children }) => (
    <Paper
      variant="outlined"
      sx={{
        mb: 2,
        bgcolor: '#23272d',
        color: '#ece9e2',
        borderRadius: 1,
      }}
    >
      <Box component="pre" sx={{ m: 0, p: 2, overflowX: 'auto', fontFamily: 'monospace', fontSize: 14 }}>
        {children}
      </Box>
    </Paper>
  ),
  table: ({ children }) => (
    <Box component="table" sx={{ display: 'table', width: '100%', mb: 2, borderCollapse: 'collapse' }}>
      {children}
    </Box>
  ),
  th: ({ children }) => (
    <Box
      component="th"
      sx={{
        border: 1,
        borderColor: 'divider',
        p: 1,
        textAlign: 'left',
        bgcolor: 'action.hover',
        fontWeight: 600,
      }}
    >
      {children}
    </Box>
  ),
  td: ({ children }) => (
    <Box component="td" sx={{ border: 1, borderColor: 'divider', p: 1, verticalAlign: 'top' }}>
      {children}
    </Box>
  ),
};

export default function GuidePage() {
  return (
    <Box>
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>
        {guide}
      </ReactMarkdown>
    </Box>
  );
}
```

Замечание: `React` для типа `React.ReactNode` — если strict tsc ругается на отсутствие импорта, добавьте `import type { ReactNode } from 'react';` и используйте `ReactNode` вместо `React.ReactNode`.

- [ ] **Step 7: Register route in `ui/src/App.tsx`**

```tsx
import GuidePage from './pages/GuidePage';
// внутри <Route element={<AppLayout />}>:
<Route path="/guide" element={<GuidePage />} />
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `npm test -- GuidePage`
Expected: PASS (3 tests). Затем полный прогон: `npm test` — PASS без новых падений.

- [ ] **Step 9: Build check**

Run: `npm run build`
Expected: tsc + vite build без ошибок.

- [ ] **Step 10: Commit**

```bash
git add ui/src/content/user-guide.md ui/src/pages/GuidePage.tsx ui/src/pages/GuidePage.test.tsx ui/src/App.tsx ui/package.json ui/package-lock.json
git commit -m "feat: guide page with user instructions rendered from markdown"
```

---

### Task 2: Пункт навигации «Инструкция»

**Files:**
- Modify: `ui/src/layout/AppLayout.tsx` (navItems)
- Test: `ui/src/layout/AppLayout.test.tsx`

**Interfaces:**
- Consumes: существующий рендер `navItems` (без изменений кода рендера).
- Produces: ссылка «Инструкция» → `/guide`, видимая всем.

- [ ] **Step 1: Write the failing test**

Добавить в `ui/src/layout/AppLayout.test.tsx`:

```tsx
test('shows guide nav item for all users', () => {
  authState.authenticated = false;
  renderLayout();
  expect(screen.getByRole('link', { name: 'Инструкция' })).toBeInTheDocument();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- AppLayout`
Expected: FAIL — ссылка «Инструкция» не найдена.

- [ ] **Step 3: Add nav item in `ui/src/layout/AppLayout.tsx`**

```tsx
const navItems = [
  { to: '/', label: 'Каталог' },
  { to: '/guide', label: 'Инструкция' },
  { to: '/teams', label: 'Команды' },
  { to: '/tokens', label: 'API-токены' },
];
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `npm test` (полный набор)
Expected: PASS — все существующие тесты навигации и новый.

- [ ] **Step 5: Build + full verification**

Run: `npm run build`
Expected: без ошибок.

- [ ] **Step 6: Commit**

```bash
git add ui/src/layout/AppLayout.tsx ui/src/layout/AppLayout.test.tsx
git commit -m "feat: guide nav entry visible to all users"
```

---

## Self-Review

**1. Spec coverage:** контент (все 5 разделов) → Task 1 Step 5; react-markdown+remark-gfm → Task 1; роут → Task 1 Step 7; навигация для всех → Task 2; тесты страницы и навигации → оба таска; табличка справочника (gfm) → Task 1 тест.
**2. Placeholder scan:** «`<адрес хаба>`» и `<slug>` — это плановые плейсхолдеры в тексте гайда (спека их требует), не пропуски плана. TODO/TBD отсутствуют.
**3. Type consistency:** `Components` из react-markdown в Task 1 согласован; `GuidePage` default export используется в App.tsx.
