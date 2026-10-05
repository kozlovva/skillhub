# Дизайн: копирование команды установки на странице элемента

Дата: 2026-10-05
Статус: утверждён пользователем

## Проблема

Пользователю приходится вручную собирать команду `skillhub install <slug>[@<version>]`. На странице элемента нет готовой команды установки — можно скопировать только slug (клик по нему).

CLI поддерживает `skillhub install <ref>`, где `<ref>` = `<slug>` (последняя версия) или `<slug>@<version>` (`parseRef` в `cli/src/commands/install.ts`).

## Решение

### 1. Шапка страницы элемента (`ui/src/pages/ElementPage.tsx`)

- В строке заголовка (там же, где slug, чипы типа/категории и кнопка избранного) добавить `Button`:
  - `size="small"`, `variant="outlined"`, `startIcon={<ContentCopyIcon />}`, текст «Команда установки».
  - По клику: `navigator.clipboard?.writeText('skillhub install ' + element.slug)` и snackbar «Команда скопирована».
- Существующий клик по slug (копирует только сам slug) сохраняется.
- Импорт `ContentCopyIcon` из `@mui/icons-material/ContentCopy`.

### 2. Таблица версий (`ui/src/components/VersionTable.tsx`)

- Новый обязательный проп: `onCopyInstall: (version: string) => void`.
- В ячейке действий (`<TableCell />` заголовка «Действия») рядом с кнопкой «Скачать» добавить `IconButton size="small"` с `ContentCopyIcon`, обёрнутую в `Tooltip title="Скопировать команду установки"`.
- Клик вызывает `onCopyInstall(v.version)`.
- Компонент остаётся «глупым»: не знает про slug и clipboard.

### 3. Обработчик в `ElementPage.tsx`

- В `VersionTable` передать `onCopyInstall={(v) => { navigator.clipboard?.writeText('skillhub install ' + element.slug + '@' + v); showSuccess('Команда скопирована'); }}`.
- Для всех версий команда всегда содержит `@<version>` (включая последнюю) — однозначно и предсказуемо.

### 4. Тесты (`ui/src/pages/ElementPage.test.tsx`)

- Мок `navigator.clipboard.writeText` (jsdom его не реализует).
- Тест 1: клик по кнопке «Команда установки» в шапке → `writeText` вызван с `skillhub install <slug>`.
- Тест 2: клик по кнопке копирования в строке версии → `writeText` вызван с `skillhub install <slug>@<version>`.

## Отклонённые альтернативы

- **Отдельная секция «Установка»** — занимает больше места, дублирует информацию у версий.
- **Кликабельный код-чип в шапке вместо кнопки** — менее заметно, чем явная кнопка.

## Вне области

- Страница паков (`PackPage`) — отдельная задача при необходимости.
- Копирование URL/скачивание, изменения CLI.
- Копирование `skillhub install <pack-slug>` для паков.
