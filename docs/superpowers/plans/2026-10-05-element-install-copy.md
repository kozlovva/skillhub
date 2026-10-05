# Копирование команды установки — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Добавить на страницу элемента копирование команды `skillhub install <slug>` (последняя версия) и `skillhub install <slug>@<version>` для каждой версии.

**Architecture:** Кнопка в шапке `ElementPage` копирует команду для latest напрямую из `element.slug`. `VersionTable` получает новый проп `onCopyInstall(version)` и рендерит иконку-кнопку копирования в строке; `ElementPage` передаёт обработчик, который формирует `skillhub install <slug>@<version>` и вызывает clipboard + snackbar. Компонент таблицы остаётся «глупым» (без знания slug/clipboard).

**Tech Stack:** React 18 + TypeScript, MUI v6, TanStack Query, Vitest + Testing Library (jsdom).

**Спецификация:** `docs/superpowers/specs/2026-10-05-element-install-copy-design.md`

## Global Constraints

- UI-тексты на русском; стиль — существующие MUI-компоненты (`size="small"`), как на `TokensPage`/`ElementPage`.
- Команда latest: `skillhub install <slug>`; команда версии: `skillhub install <slug>@<version>` (для всех версий, включая последнюю, — всегда с `@`).
- Копирование через `navigator.clipboard?.writeText(...)`; после копирования — `showSuccess('Команда скопирована')`.
- `VersionTable` не знает про slug и clipboard — получает только `onCopyInstall(version)`.
- Изменять только: `ui/src/pages/ElementPage.tsx`, `ui/src/components/VersionTable.tsx`, `ui/src/pages/ElementPage.test.tsx`.
- Не коммитить ничего, кроме файлов задачи; не коммитить намеренно изменённый `ui/src/theme.ts` и `docker/keycloak/realm-skillhub.json`.
- Проверка: `npm run test` (vitest) и `npm run build` (tsc + vite) в `ui/`.

---

### Task 1: Кнопка «Команда установки» в шапке элемента

**Files:**
- Modify: `ui/src/pages/ElementPage.tsx`
- Test: `ui/src/pages/ElementPage.test.tsx`

**Interfaces:**
- Consumes: `element.slug`, `useSnackbar().showSuccess`, `navigator.clipboard`.
- Produces: кнопка с доступным именем «Команда установки», клик копирует `skillhub install <slug>`.

- [ ] **Step 1: Написать падающий тест**

Добавить в `ui/src/pages/ElementPage.test.tsx` в конец файла. Тест мокает clipboard (jsdom его не реализует):

```tsx
test('hero install button copies skillhub install command', async () => {
  const writeText = vi.fn();
  Object.defineProperty(navigator, 'clipboard', {
    value: { writeText }, configurable: true,
  });
  renderPage();
  const btn = await screen.findByRole('button', { name: 'Команда установки' });
  await userEvent.click(btn);
  expect(writeText).toHaveBeenCalledWith('skillhub install pdf-skill');
});
```

- [ ] **Step 2: Запустить тест и убедиться, что он падает**

Run (в `ui/`): `npx vitest run src/pages/ElementPage.test.tsx`
Expected: FAIL — `Unable to find role "button" with name "Команда установки"`.

- [ ] **Step 3: Добавить кнопку в шапку**

В `ui/src/pages/ElementPage.tsx`:

1. Добавить в список импортов `@mui/icons-material`:

```tsx
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
```

2. В строке заголовка, сразу после `<FavoriteButton ... />` (перед `</Stack>`), добавить кнопку:

```tsx
          <Button
            size="small"
            variant="outlined"
            startIcon={<ContentCopyIcon />}
            onClick={() => {
              void navigator.clipboard?.writeText(`skillhub install ${element.slug}`);
              showSuccess('Команда скопирована');
            }}
          >
            Команда установки
          </Button>
```

- [ ] **Step 4: Запустить тест и убедиться, что он проходит**

Run (в `ui/`): `npx vitest run src/pages/ElementPage.test.tsx`
Expected: PASS (оба теста файла зелёные).

- [ ] **Step 5: Commit**

```bash
git add ui/src/pages/ElementPage.tsx ui/src/pages/ElementPage.test.tsx
git commit -m "feat: copy install command button on element page"
```

---

### Task 2: Копирование команды по каждой версии

**Files:**
- Modify: `ui/src/components/VersionTable.tsx`
- Modify: `ui/src/pages/ElementPage.tsx`
- Test: `ui/src/pages/ElementPage.test.tsx`

**Interfaces:**
- Consumes: `VersionTable` gets new required prop `onCopyInstall: (version: string) => void`.
- Produces: в каждой строке версии — `IconButton` с доступным именем «Скопировать команду установки», клик вызывает `onCopyInstall(v.version)`; `ElementPage` копирует `skillhub install <slug>@<version>`.

- [ ] **Step 1: Написать падающий тест**

Добавить в `ui/src/pages/ElementPage.test.tsx` в конец файла:

```tsx
test('version copy button copies versioned install command', async () => {
  const writeText = vi.fn();
  Object.defineProperty(navigator, 'clipboard', {
    value: { writeText }, configurable: true,
  });
  renderPage();
  const btn = await screen.findByRole('button', { name: 'Скопировать команду установки' });
  await userEvent.click(btn);
  expect(writeText).toHaveBeenCalledWith('skillhub install pdf-skill@1.0.0');
});
```

- [ ] **Step 2: Запустить тест и убедиться, что он падает**

Run (в `ui/`): `npx vitest run src/pages/ElementPage.test.tsx`
Expected: FAIL — кнопка с именем «Скопировать команду установки» не найдена.

- [ ] **Step 3: Обновить VersionTable**

Полная замена содержимого `ui/src/components/VersionTable.tsx`:

```tsx
import {
  Table, TableHead, TableRow, TableCell, TableBody, Button, Chip, IconButton, Tooltip, Stack,
} from '@mui/material';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import type { VersionResponse } from '../types';

export default function VersionTable({ versions, onDownload, onCopyInstall }: {
  versions: VersionResponse[];
  onDownload: (version: string) => void;
  onCopyInstall: (version: string) => void;
}) {
  return (
    <Table size="small">
      <TableHead>
        <TableRow>
          <TableCell>Версия</TableCell>
          <TableCell>Статус</TableCell>
          <TableCell>Changelog</TableCell>
          <TableCell>Размер</TableCell>
          <TableCell>Действия</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {versions.map((v) => (
          <TableRow key={v.version}>
            <TableCell>{v.version}</TableCell>
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
              <Stack direction="row" spacing={1} alignItems="center">
                <Button size="small" onClick={() => onDownload(v.version)}>Скачать</Button>
                <Tooltip title="Скопировать команду установки">
                  <IconButton
                    size="small"
                    aria-label="Скопировать команду установки"
                    onClick={() => onCopyInstall(v.version)}
                  >
                    <ContentCopyIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              </Stack>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
```

- [ ] **Step 4: Передать обработчик в ElementPage**

В `ui/src/pages/ElementPage.tsx` в вызове `<VersionTable` добавить проп после `onDownload`:

```tsx
            onCopyInstall={(v) => {
              void navigator.clipboard?.writeText(`skillhub install ${element.slug}@${v}`);
              showSuccess('Команда скопирована');
            }}
```

- [ ] **Step 5: Запустить тесты и убедиться, что они проходят**

Run (в `ui/`): `npx vitest run src/pages/ElementPage.test.tsx`
Expected: PASS (все тесты файла зелёные, включая новый).

- [ ] **Step 6: Полная сборка и все тесты UI**

Run (в `ui/`): `npm run test` и `npm run build`
Expected: все тесты проходят, сборка без ошибок типов.

- [ ] **Step 7: Commit**

```bash
git add ui/src/components/VersionTable.tsx ui/src/pages/ElementPage.tsx ui/src/pages/ElementPage.test.tsx
git commit -m "feat: per-version install command copy in version table"
```

---

### Task 3: Финальная верификация

**Files:**
- Нет новых файлов.

- [ ] **Step 1: Полный прогон UI**

Run (в `ui/`): `npm run test`
Expected: все тесты проходят.

- [ ] **Step 2: Ручная проверка (опционально, если ui запущен в докере)**

1. Открыть страницу любого элемента: кнопка «Команда установки» копирует `skillhub install <slug>`.
2. В таблице версий у строки нажать иконку копирования — буфер получает `skillhub install <slug>@<version>`.
