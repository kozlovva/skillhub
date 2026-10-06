# SkillHub

Внутренний каталог скиллов для AI-ассистентов (OpenCode, Claude Code и др.):
публикация, поиск, версионирование и установка элементов командой через CLI
или веб-интерфейс.

## Возможности

- **Каталог элементов** — скиллы, скрипты, агенты, хуки и паки; типы:
  `SKILL`, `SCRIPT`, `AGENT`, `HOOK`, `PACK`, `OTHER`
- **Версионирование** — semver-версии, changelog к каждому релизу,
  скачивание любой версии, удаление версий (soft delete)
- **Поиск** — полнотекстовый поиск с морфологией RU/EN (tsvector)
- **Команды (Teams)** — публикация и доступ на уровне команды, роли
  участников (OWNER/MAINTAINER и др.)
- **Паки (PACK)** — составные элементы: набор скиллов, устанавливаемый
  одной командой; ограничение версий `latest` или точный semver
- **Социальное** — избранное, рейтинг, отзывы
- **API-токены** — токены вида `skh_...` для CLI и CI, отзыв токенов
- **CLI** — `login`, `search`, `publish`, `install` (с lockfile) и др.
- **Веб-UI** — каталог, страница элемента с README/версиями/отзывами,
  загрузка, управление командами, токенами и категориями

## Технологический стек

### Backend (`/backend`, Java 21)

| Компонент | Технология |
|-----------|-----------|
| Фреймворк | Spring Boot 3.3 (Web, Validation, Actuator) |
| Данные | Spring Data JPA, PostgreSQL 16, Flyway-миграции |
| Безопасность | Spring Security + OAuth2 Resource Server (JWT от Keycloak) |
| Хранение файлов | S3-совместимое хранилище (AWS SDK v2), локально MinIO |
| Архивы | Apache Commons Compress (zip) |
| Документация API | springdoc-openapi (Swagger UI) |
| Тесты | JUnit 5, Testcontainers (PostgreSQL), Failsafe для IT |
| Прочее | Lombok, Jackson |

Архитектура — слоевая: `adapters/in/rest` → `application/service` →
`domain/model` + `domain/port` (порты к БД и хранилищу).

### Frontend (`/ui`)

| Компонент | Технология |
|-----------|-----------|
| Фреймворк | React 18 + TypeScript 5.6 |
| Сборка | Vite 5 |
| UI-библиотека | MUI 6 (Material UI) + Emotion |
| Состояние/запросы | TanStack React Query 5, Axios |
| Маршрутизация | React Router 6 |
| Аутентификация | Keycloak JS-адаптер |
| Markdown | react-markdown + remark-gfm |
| Тесты | Vitest 2 + Testing Library (jsdom) |

### CLI (`/cli`)

Node.js ≥ 18, TypeScript, Commander, adm-zip; тесты — Vitest.

### Инфраструктура

Docker Compose поднимает полный стенд: **PostgreSQL 16**, **MinIO**,
**Keycloak 26** (realm `skillhub` импортируется автоматически), **API** и **UI**
(Nginx). `backend/Dockerfile` — образ API, `ui/Dockerfile` — образ UI.

## Структура репозитория

```
├── backend/                # Backend (Spring Boot, Maven)
│   ├── src/
│   │   └── main/resources/db/migration/   # Flyway-миграции (V1–V8)
│   ├── pom.xml
│   └── Dockerfile          # образ API
├── ui/                     # Frontend (React + Vite)
├── cli/                    # CLI (@skillhub/cli)
├── docker/                 # конфиги инфраструктуры (Keycloak realm)
├── scripts/seed-demo.ps1   # наполнение каталога демо-данными
├── skills/                 # скиллы проекта (code-stats)
├── examples/               # примеры элементов разного типа (см. examples/README.md)
└── docker-compose.yml      # полный стенд
```

## Быстрый старт

```bash
docker compose up -d --build
```

| Сервис | URL |
|--------|-----|
| Веб-UI | http://localhost:3000 |
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Keycloak | http://localhost:8180 (admin/admin) |
| MinIO Console | http://localhost:9001 (minioadmin/minioadmin) |
| PostgreSQL | localhost:5433 (skillhub/skillhub) |

Пользователи Keycloak создаются из realm-импорта (`docker/keycloak/realm-skillhub.json`).

## Локальная разработка

```bash
# инфраструктура без API и UI
docker compose up -d postgres minio keycloak

# backend (Java 21 + Maven)
cd backend && mvn spring-boot:run

# frontend
cd ui && npm install && npm run dev

# CLI
cd cli && npm install && npm run build
```

## CLI

```bash
npx @skillhub/cli --help          # или npm i -g @skillhub/cli

skillhub login --url http://localhost:8080   # токен из веб-UI (Settings → API tokens)
skillhub search pdf --limit 10
skillhub publish ./my-skill                  # нужна папка с manifest.json
skillhub install code-stats@1.0.0            # в .opencode/skills или .claude/skills (авто-детект)
```

`install` ведёт lockfile (`skillhub.lock` в корне проекта), повторный вызов не
переустанавливает ту же версию; `--force` — переустановить, `--all` — во все
обнаруженные платформы, `--target <dir>` — в произвольную папку.
Платформы детектятся по наличию `.opencode/` или `.claude/`/`CLAUDE.md`.

Примеры элементов для публикации — в [`examples/`](examples/README.md):
скиллы со скриптами и без, reference-скилл, элемент типа SCRIPT.

## API

REST API документирован через OpenAPI: `http://localhost:8080/swagger-ui.html`.
Основные группы ресурсов:

- `elements` — CRUD элементов, версии, файлы, скачивание
- `search` — полнотекстовый поиск
- `packs` — содержимое и скачивание паков
- `teams` — команды и участники
- `tokens` — API-токены
- `social` — избранное, рейтинг, отзывы
- `categories`, `me` — справочники и профиль

Аутентификация: JWT (Bearer) от Keycloak либо API-токен `skh_...`.

## Тесты

```bash
cd backend && mvn verify   # backend: unit + integration (Testcontainers)
cd ui  && npm test         # frontend: vitest
cd cli && npm test         # cli: vitest
```

## Конфигурация API (переменные окружения)

| Переменная | Назначение |
|------------|-----------|
| `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD` | PostgreSQL |
| `S3_ENDPOINT`, `S3_PRESIGN_ENDPOINT`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_BUCKET`, `S3_ENSURE_BUCKET` | S3/MinIO |
| `OIDC_ISSUER`, `OIDC_JWK_SET_URI` | Keycloak (проверка JWT) |
