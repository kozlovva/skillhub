# SkillHub CLI — Design Document

Дата: 2026-10-04
Статус: Approved

## 1. Обзор

CLI для SkillHub — консольная утилита для людей: установка скиллов в проекты, поиск по каталогу, публикация новых версий. Дистрибуция через npm (запуск `npx` или глобальная установка).

### Пользователи и сценарии

- **Основной сценарий**: установка скиллов в директорию скиллов проекта (`install`).
- Вторичные: поиск по каталогу, проверка токена, публикация элементов и версий из терминала.
- Пользователи — люди; вывод человекочитаемый, ключевые команды детерминированы и пригодны для вызова из скриптов/агентов.

### Ключевые решения

| Решение | Выбор |
|---|---|
| Стек | TypeScript + Node 18+, `commander`, `adm-zip`, нативный `fetch` |
| Расположение | `cli/` в монорепе SkillHub |
| Дистрибуция | npm-пакет `@skillhub/cli` (публичный или приватный registry), `bin: skillhub` |
| Аутентификация | API-токен (создаётся в веб-UI), хранение в `~/.skillhub/config.json` |
| Команды v1 | `login`, `whoami`, `search`, `install`, `publish` |
| Тесты | Vitest, юнит-тесты модулей с моками fetch/FS |

## 2. Команды

```
skillhub login [--url <api-url>]
skillhub whoami
skillhub search <query> [--type <type>] [--team <slug>] [--limit <n>]
skillhub install <slug>@<version|latest> [--target <dir>] [--all] [--force]
skillhub publish <dir>
```

Все команды поддерживают `--json` для машинного вывода.

### login

- Интерактивно запрашивает URL API (если не передан `--url`) и API-токен.
- Токен создаётся в веб-UI (`POST /api/tokens` из-под SSO).
- Сохраняет `url` и `token` в `~/.skillhub/config.json` (chmod 600 на POSIX).
- Опционально сразу проверяет токен через `GET /api/me` и сообщает имя пользователя.

### whoami

- `GET /api/me` — вывод email/display_name; проверка настроенного токена.

### search

- `GET /api/search?q=...&type=...&team=...` (limit по умолчанию 20).
- Вывод таблицей: slug, name, type, latest_version, downloads_count.
- Работает без токена (PUBLIC-элементы); с токеном — включая TEAM.

### install — ключевая логика

1. Парсит аргумент `<slug>[@<version>]`; без версии — `latest` (последняя PUBLISHED).
2. Скачивает zip: `GET /api/elements/{slug}/versions/{v}/download` либо `GET /api/packs/{slug}/versions/{v}/download` (пак определяется по типу элемента из ответа API / по наличию в поиске как PACK). Bearer-токен в заголовке.
3. Распаковывает во временную папку.
4. Определяет цель установки (авто-детект, см. ниже).
5. Кладёт скилл в `<target>/<slug>/`; пак — с сохранением структуры zip пака: `<target>/<pack-slug>/<element-slug>/...`.
6. Обновляет `skillhub.lock` в корне проекта.

**Поведение при повторной установке:**

- В `skillhub.lock` уже записана та же версия → «уже установлена vX.y.z» (код выхода 0).
- Другая версия → обновление с сообщением old → new.
- `--force` — перезапись безусловно.

**Авто-детект цели:**

- Есть `.opencode/` в корне проекта → `.opencode/skills/`.
- Есть `.claude/` или `CLAUDE.md` → `.claude/skills/`.
- Найдено несколько платформ → интерактивный выбор; `--all` ставит во все; `--target <dir>` переопределяет детект полностью.
- Ничего не найдено → интерактивный выбор из списка платформ с созданием директории.

### publish

1. Читает `<dir>/manifest.json`: `name`, `version`, `description`, `type` (SKILL|SCRIPT|AGENT|HOOK|PACK|OTHER), опционально `team`, `category`, `tags`, `changelog`.
2. Отсутствующий/невалидный manifest — ошибка с перечнем проблем (до обращения к API).
3. Пакует содержимое `<dir>` в zip, включая сам `manifest.json`.
4. `POST /api/elements` — создаёт элемент, если его ещё нет (поиск по slug в команде/личных); если существует — переиспользует.
5. `POST /api/elements/{slug}/versions` — multipart-загрузка zip.
6. Ошибки API выводятся человекочитаемо: 409 (версия существует), 422 (невалидный semver/manifest), 403 (нет прав).

## 3. Архитектура

```
cli/
  src/
    index.ts          # commander, регистрация команд
    commands/         # login.ts, whoami.ts, search.ts, install.ts, publish.ts
    api.ts            # ApiClient: fetch + Bearer, парсинг ошибок {code, message, details}
    config.ts         # ~/.skillhub/config.json; env SKILLHUB_URL / SKILLHUB_TOKEN
    detect-target.ts  # авто-детект платформ
    lockfile.ts       # чтение/запись skillhub.lock
    manifest.ts       # чтение и валидация manifest.json
  package.json        # bin: { skillhub: dist/index.js }
  tsconfig.json
```

- Сборка: `tsc` → `dist/`, CommonJS, `engines: { node: >=18 }`.
- Зависимости: `commander`, `adm-zip`. Остальное — нативные модули (`fetch`, `fs`, `path`, `os`, `readline`).
- Модули независимы и тестируемы: `api.ts` принимает injectable fetch, `detect-target`/`lockfile`/`manifest` работают с переданным путём/FS.

## 4. Конфигурация и аутентификация

- Приоритет источников конфигурации:
  1. Переменные окружения `SKILLHUB_URL`, `SKILLHUB_TOKEN` (CI/агенты).
  2. `~/.skillhub/config.json`.
- `search` работает без токена; `install` PUBLIC-элементов — без токена; `install` TEAM-элементов, `publish`, `whoami` — требуют токен (понятная ошибка «выполните skillhub login»).
- Токен не логируется, не попадает в сообщения об ошибках.

## 5. skillhub.lock

Файл в корне проекта (JSON):

```json
{
  "packages": {
    "pdf-export": { "version": "1.2.0", "installedAt": "2026-10-04T10:00:00Z" },
    "team-tools": { "version": "0.3.0", "installedAt": "2026-10-04T10:05:00Z", "kind": "pack" }
  }
}
```

Назначение: повторные install, апгрейды, base для будущих `uninstall`/`upgrade`. В v1 — ручное чтение человеком, git-коммитится.

## 6. Обработка ошибок

- Понятные сообщения на stderr; при ошибке API — `code` + `message` от бэкенда (единый формат `{code, message, details}`).
- Коды выхода: `0` — успех; `1` — ошибка пользователя (невалидный manifest, нет токена, 404/409/422); `2` — сетевая/серверная ошибка (таймаут, 5xx).
- Токен из ответов/ошибок маскируется.

## 7. Дистрибуция

- `npm publish` — публичный npmjs или приватный registry (`--registry`). Имя пакета `@skillhub/cli`; короткий алиас `skillhub` — опционально при первой публикации.
- Запуск: `npx @skillhub/cli install ...` или `npm i -g @skillhub/cli`.
- Версионирование CLI независимо от бэкенда (semver), changelog в релизах.

## 8. Тестирование

- Юнит-тесты (Vitest): `api.ts` (мок fetch: Bearer-заголовок, парсинг ошибок, коды выхода), `detect-target.ts` (комбинации .opencode/.claude/CLAUDE.md/ничего), `lockfile.ts` (чтение/запись/сравнение версий), `manifest.ts` (валидные/невалидные manifest.json), команды — с моками модулей.
- Интеграционный смоук против docker-compose (publish → install → проверка файлов) — вручную, вне первой итерации.

## 9. Out of scope (v1)

- `uninstall`, `upgrade`, `outdated`, автодополнение shell.
- Межэлементные зависимости, резолвер версионных диапазонов паков.
- OIDC-флоу в терминале (только API-токены).
- Криптоподписи артефактов.
- E2E-интеграционные тесты в CI.
