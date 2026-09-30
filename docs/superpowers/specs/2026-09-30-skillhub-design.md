# SkillHub — Design Document

Дата: 2026-09-30
Статус: Approved

## 1. Обзор

SkillHub — корпоративное хранилище скиллов, скриптов, агентов, хуков и других артефактов для AI-агентов и людей. Предоставляет веб-UI для просмотра и управления, REST API для CLI/агентов, версионирование по semver, паки (наборы элементов) и категории.

### Пользователи и сценарии

- **Люди**: просмотр каталога через веб-UI, поиск, скачивание любых версий, публикация новых элементов и версий, рейтинги/отзывы/избранное.
- **AI-агенты (CLI)**: скачивание элементов и паков по API с API-токеном, публикация через `manifest.json`.

### Ключевые решения

| Решение | Выбор |
|---|---|
| Пользователи | Люди + AI-агенты |
| Масштаб | Единый корпоративный хаб с командами; элементы PUBLIC или TEAM |
| Версионирование | Semver, immutable-версии |
| Хранение контента | PostgreSQL (метаданные) + S3/MinIO (zip-архивы версий) |
| Стек | Java Spring Boot, PostgreSQL, MinIO, React/TypeScript |
| Аутентификация | Корпоративный SSO (OIDC, Keycloak); API-токены для CLI |
| Модерация | Статусы DRAFT/PUBLISHED/DEPRECATED, без аппрув-ревью |
| Поиск | Полнотекстовый (PG tsvector ru+en) + рейтинг/отзывы/избранное |
| Развёртывание | Docker Compose (dev), Helm/K8s (prod) |

## 2. Архитектура

Монолит Spring Boot (модульный) + PostgreSQL + MinIO + React UI.

```
┌─────────────┐     ┌──────────────────────────────────────┐
│  Web UI     │────▶│           SkillHub API                │
│  (React)    │     │        (Spring Boot)                  │
└─────────────┘     │  ┌─────────┐ ┌──────┐ ┌───────────┐  │
                    │  │ Auth    │ │ Core │ │ Search    │  │
┌─────────────┐     │  │ (OIDC)  │ │ API  │ │ (PG FTS)  │  │
│  CLI/Agent  │────▶│  └─────────┘ └──────┘ └───────────┘  │
└─────────────┘     └───────┬──────────────┬───────────────┘
                            ▼              ▼
                     ┌────────────┐  ┌──────────┐
                     │ PostgreSQL │  │ S3/MinIO │
                     │(метаданные)│  │ (архивы) │
                     └────────────┘  └──────────┘
```

Компоненты:

- **Auth** — OIDC через Keycloak для UI; API-токены (Bearer) для CLI/агентов; синхронизация пользователей из SSO.
- **Core API** — элементы, версии, паки, категории, команды, права.
- **Search** — полнотекстовый поиск PG с фасетами.
- **Storage** — S3/MinIO: архивы версий, раздача через presigned-URL с коротким TTL после проверки прав.
- **Web UI** — каталог, карточки, browse файлов, админка.

## 3. Модель данных

### Element
Единая сущность для всех типов артефактов (скилл, скрипт, агент, пак).

- `id, slug (уникален в рамках team), type (SKILL|SCRIPT|AGENT|HOOK|PACK|OTHER)`
- `name, description, team_id, category_id, tags (text[])`
- `visibility (PUBLIC|TEAM)`
- `author_id, created_at, updated_at, downloads_count, latest_version`

### ElementVersion
Immutable-версия элемента.

- `id, element_id, version (semver), status (DRAFT|PUBLISHED|DEPRECATED)`
- `changelog, s3_key, size_bytes`
- `file_index (jsonb — дерево файлов с размерами)`
- `published_by, created_at, published_at`
- Уникальность: `(element_id, version)`

### Category
Иерархические категории (родитель → дети): «Работа с документами», «Дизайн», «UI/UX», «Разработка» и т.д. Управляются админом.

- `id, name, parent_id, slug, icon`

### Team / TeamMember
- Team: `id, name, slug`
- TeamMember: `team_id, user_id, role (OWNER|MAINTAINER|MEMBER)`

### PackContent
Состав пака: `pack_element_id, element_id, version_constraint (точная версия или диапазон)`.
Скачивание пака = один zip: манифест пака + все элементы с зафиксированными версиями в подпапках.

### Rating / Review / Favorite
- Rating: `element_id, user_id, rating (1–5)` — один на пользователя на элемент.
- Review: `element_id, user_id, rating, text`.
- Favorite: `user_id, element_id`.

### User / ApiToken
- User (синхронизируется из SSO): `id, sso_subject, email, display_name, avatar_url`
- ApiToken: `id, user_id, name, token_hash, created_at, last_used_at, expires_at`

### Права доступа

- **Чтение**: PUBLIC — все сотрудники; TEAM — только участники команды.
- **Публикация/удаление**: OWNER/MAINTAINER команды.
- **Админ**: всё (категории, команды, пользователи).

## 4. Ключевые сценарии

### Публикация версии (CLI или UI)

1. `POST /api/elements` — создать элемент (или найти существующий по slug в команде).
2. `POST /api/elements/{slug}/versions` — multipart-загрузка zip-архива:
   - сервер распаковывает, читает `manifest.json` (имя, версия, описание);
   - строит `file_index` (дерево файлов с размерами);
   - кладёт архив в S3 по ключу `{team}/{element}/{version}.zip`;
   - пишет `ElementVersion` со статусом DRAFT, затем PUBLISHED.
   - Валидация: semver-формат, лимит размера, обязательный manifest.
3. Транзакционность: сначала S3, потом PG; orphan-объекты в S3 чистятся фоновым job'ом.

### Скачивание

- `GET /api/elements/{slug}/versions/{version}/download` — архив версии; `latest` — последняя PUBLISHED.
- `GET /api/packs/{slug}/versions/{v}/download` — zip пака (манифест + элементы в подпапках).
- `GET /api/elements/{slug}/versions/{v}/files?path=...` — отдельный файл из архива по `file_index`.

### CLI для агентов

- `skillhub install <element>@<version|latest>` / установка пака — скачивание и распаковка в рабочую директорию.
- `skillhub publish <dir>` — читает `manifest.json`, пакует и публикует.
- Аутентификация: API-токен, заголовок `Authorization: Bearer`; токены создаются в UI из-под SSO.

### Поиск

`GET /api/search?q=...&category=...&type=...&team=...` — полнотекстовый поиск PG (name/description/changelog, tsvector ru+en), фасеты по категории/типу/тегам, сортировка по релевантности/рейтингу/загрузкам.

### Страницы UI

- Каталог: поиск, фильтры, дерево категорий.
- Карточка элемента: описание/README, дерево файлов версии, список версий, рейтинги, отзывы, «Добавить в пак».
- Страница пака: состав, версионные ограничения.
- Профиль команды.
- Админка: категории, команды, пользователи.

## 5. Обработка ошибок

- Единый формат ошибок: `{code, message, details}`.
- HTTP-коды: 401/403 (auth), 404, 409 (конфликт версии/слага), 422 (невалидный manifest/semver).
- Загрузка архива: лимит размера, защита от zip-bomb (лимит распакованного размера и числа файлов), защита от path traversal.

## 6. Безопасность

- OIDC (Keycloak) для UI; JWT-сессия; API-токены хешируются.
- Проверка visibility на каждом запросе (фильтрация на уровне сервиса).
- Presigned-URL с коротким TTL — только после проверки прав.
- Аудит-лог публикаций и удалений.

## 7. Тестирование

- Unit-тесты сервисов: JUnit 5 + Mockito.
- Интеграционные: Testcontainers (PostgreSQL + MinIO).
- API-тесты: MockMvc.
- Ключевые сценарии: публикация → скачивание любой версии, права доступа, публикация и скачивание пака.
- E2E для UI — вне первой итерации.

## 8. Развёртывание

- Dev: Docker Compose (api, postgres, minio, ui).
- Prod: Helm-чарт для Kubernetes.
- Конфигурация через переменные окружения.

## 9. Out of scope (v1)

- Реестр зависимостей между элементами.
- Веб-хуки / уведомления.
- Статистика использования по агентам.
- Криптографические подписи артефактов.
