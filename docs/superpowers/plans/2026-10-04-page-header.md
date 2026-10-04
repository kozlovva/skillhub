# Unified Page Header Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Единый паттерн оформления страниц: общий `PageHeader` (h4 + subtitle) над карточками, заголовки вынесены из Paper, верхний отступ Инструкции выровнен.

**Architecture:** Новый компонент `ui/src/components/PageHeader.tsx` (Box → Typography h4 component="h1" + опциональный subtitle). Скелет страницы: `Stack spacing={3}` → `PageHeader` → Paper-карточки `p: 3`. Переводятся 8 страниц; ElementPage остаётся исключением (градиентная шапка, спека).

**Tech Stack:** React 18, MUI 6, Vitest + Testing Library. Без новых зависимостей.

## Global Constraints

- Единый скелет: `Stack spacing={3}` → `PageHeader` → `Paper sx={{ p: 3 }}` (спека §2).
- `PageHeader` props: `title: string`, `subtitle?: ReactNode`; Typography `variant="h4" component="h1" sx={{ mb: 0.5 }}`, subtitle `color="text.secondary"`.
- ElementPage НЕ меняется (спека §2.6).
- Внутренние заголовки секций карточек (h6 + иконка) остаются без изменений.
- Тексты заголовков не меняются (тесты страниц ищут по тексту/роли).
- npm-команды из `ui/`; коммит-стиль `feat:`/`refactor:`/`test:`; TS strict.

---

### Task 1: Компонент `PageHeader`

**Files:**
- Create: `ui/src/components/PageHeader.tsx`
- Test: `ui/src/components/PageHeader.test.tsx`

**Interfaces:**
- Produces: `export default function PageHeader({ title, subtitle }: { title: string; subtitle?: ReactNode }): JSX.Element` — используют Tasks 2–3.

- [ ] **Step 1: Write the failing test**

`ui/src/components/PageHeader.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import PageHeader from './PageHeader';

test('renders title as level-1 heading', () => {
  render(<PageHeader title="Избранное" />);
  expect(screen.getByRole('heading', { level: 1, name: 'Избранное' })).toBeInTheDocument();
});

test('renders optional subtitle', () => {
  render(<PageHeader title="Команды" subtitle="Управление командами" />);
  expect(screen.getByText('Управление командами')).toBeInTheDocument();
});

test('renders without subtitle', () => {
  render(<PageHeader title="Избранное" />);
  expect(screen.queryByText('Управление командами')).not.toBeInTheDocument();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- PageHeader`
Expected: FAIL — cannot resolve `./PageHeader`.

- [ ] **Step 3: Write `ui/src/components/PageHeader.tsx`**

```tsx
import type { ReactNode } from 'react';
import { Box, Typography } from '@mui/material';

interface PageHeaderProps {
  title: string;
  subtitle?: ReactNode;
}

export default function PageHeader({ title, subtitle }: PageHeaderProps) {
  return (
    <Box>
      <Typography variant="h4" component="h1" sx={{ mb: 0.5 }}>
        {title}
      </Typography>
      {subtitle && <Typography color="text.secondary">{subtitle}</Typography>}
    </Box>
  );
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- PageHeader`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add ui/src/components/PageHeader.tsx ui/src/components/PageHeader.test.tsx
git commit -m "feat: shared PageHeader component"
```

---

### Task 2: Заголовки из карточек наружу (AdminCategories, Pack, Tokens, Favorites, Upload)

**Files:**
- Modify: `ui/src/pages/AdminCategoriesPage.tsx`
- Modify: `ui/src/pages/PackPage.tsx`
- Modify: `ui/src/pages/TokensPage.tsx`
- Modify: `ui/src/pages/FavoritesPage.tsx`
- Modify: `ui/src/pages/UploadPage.tsx`

**Interfaces:**
- Consumes: `PageHeader` из Task 1 (`import PageHeader from '../components/PageHeader';`).

Каждая страница: `<Stack spacing={3}>` → `<PageHeader …/>` → существующий `<Paper sx={{ p: 3 }}>…</Paper>` (без заголовка внутри). Тексты и данные не меняются.

- [ ] **Step 1: AdminCategoriesPage**

Заменить return-блок:

```tsx
return (
  <Stack spacing={3}>
    <PageHeader title="Категории" />
    <Paper sx={{ p: 3 }}>
      <List disablePadding sx={{ mb: 2 }}>
```

(внутри Paper убрать прежний `<Stack direction="row" …>` с CategoryIcon + Typography h4; Paper закрывается как раньше, после него —-dialog? Нет: у этой страницы только Paper. Закрыть `</Paper></Stack>`.)

Добавить импорты: `Stack` (если отсутствует в import из '@mui/material') и `import PageHeader from '../components/PageHeader';`. CategoryIcon остаётся только если используется ниже; если использовался только в заголовке — удалить неиспользуемый импорт.

- [ ] **Step 2: TokensPage**

```tsx
return (
  <Stack spacing={3}>
    <PageHeader
      title="API-токены"
      subtitle="Токены используются CLI и AI-агентами (заголовок Authorization: Bearer)."
    />
    <Paper sx={{ p: 3 }}>
      <Table size="small">
```

Убрать из Paper прежний заголовочный Stack (KeyIcon + Typography h4) и `<Typography variant="body2" …>` (subtitle переехал в PageHeader). KeyIcon удалить, если больше не используется.

- [ ] **Step 3: FavoritesPage**

```tsx
return (
  <Stack spacing={3}>
    <PageHeader title="Избранное" />
    <Paper sx={{ p: 3 }}>
      {(favorites ?? []).length === 0 ? (
        <Typography color="text.secondary">Пока ничего в избранном</Typography>
      ) : (
        favorites!.map((e) => <ElementCard key={e.slug} element={e} />)
      )}
    </Paper>
  </Stack>
);
```

Убрать `sx={{ mb: 2 }}` вместе с бывшим заголовком. Импорт `PageHeader` добавить.

- [ ] **Step 4: UploadPage**

```tsx
return (
  <Stack spacing={3}>
    <PageHeader title="Загрузить элемент" />
    <Paper sx={{ p: 3, maxWidth: 720 }}>
      <Stack spacing={2}>
```

Убрать прежний заголовочный Stack (UploadIcon + Typography h4) из Paper. UploadIcon оставить (используется в другом месте страницы).

- [ ] **Step 5: PackPage**

Заголовок и кнопки разделяются: `PageHeader` снаружи, кнопки остаются в карточке.

```tsx
return (
  <Stack spacing={3}>
    <PageHeader
      title={`Пак: ${pack.slug}`}
      subtitle={`${pack.contents.length} элементов`}
    />
    <Paper sx={{ p: 3 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'center' }} sx={{ mb: 2 }}>
        <Box sx={{ flexGrow: 1 }} />
        {authenticated && (
          <Button variant="contained" startIcon={<LibraryAddIcon />} onClick={() => setDialogOpen(true)}>
            Добавить элемент
          </Button>
        )}
        <Button variant="outlined" startIcon={<DownloadIcon />} onClick={() => { void handleDownload(); }}>
          Скачать пак
        </Button>
      </Stack>
      <Table size="small">
```

(прежний `<Box sx={{ flexGrow: 1 }}><Typography h4>…</Typography><Typography color="text.secondary">…</Typography></Box>` внутри этой Stack удаляется; Box с flexGrow сохраняем, чтобы кнопки уехали вправо.)

- [ ] **Step 6: Run tests + build**

Run: `npm test`
Expected: PASS — все существующие тесты страниц зелёные (они ищут заголовки по тексту/роли, не по вложенности). Если какой-то тест завязан на вложенность заголовка в Paper — поправить ассерт на текстовый/ролевой поиск без изменения семантики.
Run: `npm run build`
Expected: без ошибок (в т.ч. no unused imports — tsc strict).

- [ ] **Step 7: Commit**

```bash
git add ui/src/pages/AdminCategoriesPage.tsx ui/src/pages/PackPage.tsx ui/src/pages/TokensPage.tsx ui/src/pages/FavoritesPage.tsx ui/src/pages/UploadPage.tsx
git commit -m "refactor: move page titles out of cards onto PageHeader"
```

---

### Task 3: Каталог, Команды, Инструкция — на PageHeader

**Files:**
- Modify: `ui/src/pages/CatalogPage.tsx` (строки 78–84)
- Modify: `ui/src/pages/TeamsPage.tsx` (строки 51–57)
- Modify: `ui/src/pages/GuidePage.tsx`
- Modify: `ui/src/content/user-guide.md` (строка 1)
- Test: `ui/src/pages/GuidePage.test.tsx` (без изменений — уже совместим)

**Interfaces:**
- Consumes: `PageHeader` из Task 1.

- [ ] **Step 1: CatalogPage**

Заменить:

```tsx
<Box>
  <Typography variant="h4" sx={{ mb: 0.5 }}>Каталог скилов</Typography>
  <Typography color="text.secondary">
    {data ? `Найдено: ${data.total}` : 'Библиотека элементов для вашей команды'}
  </Typography>
</Box>
```

на:

```tsx
<PageHeader
  title="Каталог скилов"
  subtitle={data ? `Найдено: ${data.total}` : 'Библиотека элементов для вашей команды'}
/>
```

Импорт: `import PageHeader from '../components/PageHeader';`.

- [ ] **Step 2: TeamsPage**

Заменить:

```tsx
<Box>
  <Typography variant="h4" sx={{ mb: 0.5 }}>Команды</Typography>
  <Typography color="text.secondary">
    Управление командами и доступом к элементам
  </Typography>
</Box>
```

на:

```tsx
<PageHeader
  title="Команды"
  subtitle="Управление командами и доступом к элементам"
/>
```

Импорт: `import PageHeader from '../components/PageHeader';`.

- [ ] **Step 3: GuidePage + user-guide.md**

1. В `ui/src/content/user-guide.md` удалить первую строку `# Инструкция` и следующую за ней пустую строку (файл начинается с `SkillHub — корпоративное хранилище…`).
2. В `ui/src/pages/GuidePage.tsx`: добавить `import PageHeader from '../components/PageHeader';`, убрать из `components`-маппинга `h1` (заголовок h1 в markdown больше не встречается; type `Components` позволяет отсутствие ключа) — удалить строку `h1: heading('h4'),`, и вернуть:

```tsx
export default function GuidePage() {
  return (
    <Stack spacing={3}>
      <PageHeader title="Инструкция" />
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>
        {guide}
      </ReactMarkdown>
    </Stack>
  );
}
```

(вместо прежнего `<Box>`-обёртки; `Stack` добавить в импорт из '@mui/material'.)

Первый заголовок markdown теперь `## Быстрый старт…` → Typography h5 — верхний отступ страницы задаёт PageHeader, лишний `mt: 4` исчезает.

- [ ] **Step 4: Run tests + build**

Run: `npm test`
Expected: PASS — в `GuidePage.test.tsx` ассерт `getByRole('heading', { level: 1, name: 'Инструкция' })` продолжает проходить (PageHeader рендерит component="h1"). Если тест `renders guide sections` падает из-за отсутствия `# Инструкция` в md — это должен покрыть PageHeader, не md.
Run: `npm run build`
Expected: без ошибок.

- [ ] **Step 5: Commit**

```bash
git add ui/src/pages/CatalogPage.tsx ui/src/pages/TeamsPage.tsx ui/src/pages/GuidePage.tsx ui/src/content/user-guide.md
git commit -m "refactor: catalog, teams and guide pages use PageHeader"
```

---

## Self-Review

**1. Spec coverage:** PageHeader компонент → Task 1; перевод 5 страниц из карточек → Task 2; Catalog/Teams на PageHeader + Guide отступ → Task 3; ElementPage не трогается (Global Constraints); тесты Guide (level 1) → Task 3 Step 4.
**2. Placeholder scan:** нет TBD/TODO; все изменения показаны кодом (для AdminCategories/Tokens описаны точечно — какие блоки удалить, с итоговой структурой).
**3. Type consistency:** `PageHeader({ title, subtitle? })` одинаков в Tasks 1–3; `heading('h4')` в GuidePage остаётся для h2/h3 (variant h5/h6) — после удаления строки `h1` маппинг не ссылается на несуществующее.
