# Добавление участника команды через автокомплит — Design Document

Дата: 2026-10-04
Статус: Approved (ревизия 2 — автокомплит вместо ручного ввода логина)

## 1. Обзор

Сейчас участника в команду можно добавить только по `ssoSubject` — непрозрачному UUID из клейма `sub` JWT-токена Keycloak. Человек не может узнать его наизусть, что делает функцию практически нерабочей. Заменяем ручной ввод на **автокомплит по корпоративному логину и имени**: тот, кто добавляет участника, ищет его по подстроке и выбирает из списка. Бэкенд ищет кандидатов только для пользователей с правом добавления (админ или OWNER команды).

**Ключевые решения (ревизия 2):**

| Решение | Выбор |
|---|---|
| Поле для идентификации пользователя | Новая колонка `users.username` из клейма `preferred_username` |
| Поиск кандидатов | Team-scoped endpoint `GET /api/teams/{slug}/member-candidates?q=` |
| Кто может искать | Только те, кто может добавлять: админ или OWNER команды (текущее поведение `addMember` сохраняется) |
| Что ищем и показываем | Логин + displayName; без email (приватность) |
| Режим ввода | Только выбор из списка, ручной ввод отключён |
| Идентификатор в `addMember` | `userId` (UUID) — пользователь из списка гарантированно существует |
| Бэкфилл существующих записей | `username = display_name` в миграции (сейчас displayName и есть preferred_username) |

## 2. Бэкенд

### 2.1. Миграция `V5__add_username.sql`

```sql
ALTER TABLE users ADD COLUMN username TEXT;
CREATE UNIQUE INDEX idx_users_username ON users (username);
UPDATE users SET username = display_name WHERE username IS NULL;
```

Колонка nullable (но после бэкфилла заполнена у всех текущих строк) — unique index в PostgreSQL допускает несколько NULL, так что вход без клейма `preferred_username` не сломается.

### 2.2. Модель и синхронизация

- `User.java`, `JpaUser.java` — новое поле `username`.
- `CurrentUserResolver.resolve()` — уже читает клейм `preferred_username` (fallback: `subject`); теперь передаёт его отдельным аргументом, а не в displayName.
- `UserSyncService.syncFromSso(subject, email, username, displayName)` — 4 аргумента; при каждом входе обновляет `username` вместе с `email`/`displayName`.

### 2.3. Поиск кандидатов

- `UserRepositoryPort` — новые методы:
  - `List<User> searchCandidates(String q, UUID excludeTeamId, int limit)` — `ILIKE` по `username` ИЛИ `display_name`, исключая участников команды `excludeTeamId`; реализация в `JpaUserRepositoryAdapter` (JPQL/нативный запрос).
  - `Optional<User> findById(UUID id)` — для `addMember`.
- `TeamUseCase.searchCandidates(teamSlug, q, actor)`:
  - команда не найдена → 404;
  - не админ и не OWNER команды → 403 (та же проверка, что в `addMember`);
  - `q` короче 2 символов → пустой список (запрос к БД не выполняется);
  - лимит — 10 кандидатов.

### 2.4. Добавление и REST

- `TeamUseCase.addMember(teamSlug, UUID userId, role, actor)` — поиск через `findById`; не найден → 404. Проверка прав без изменений.
- `AddMemberRequest` — поле `ssoSubject` → `userId: UUID` (`@NotNull`).
- `TeamController`:
  - `GET /{slug}/member-candidates?q=...` → `List<CandidateResponse>` (`userId`, `username`, `displayName`);
  - `addMember` — ответ `{"userId": <UUID>, "role": "..."}`; заодно исправляем баг, когда в поле `ssoSubject` по ошибке клался UUID membership.

## 3. API-контракт

```
GET /api/teams/{slug}/member-candidates?q=<мин. 2 символа>
  200: [{ "userId": "...", "username": "vpetrov", "displayName": "Владимир Петров" }]
  403 — не OWNER/админ; 404 — команда не найдена

POST /api/teams/{slug}/members
  запрос:  { "userId": "<UUID>", "role": "MEMBER" }
  ответ:   { "userId": "<UUID>", "role": "MEMBER" }
```

## 4. Фронтенд

- `ui/src/api/teams.ts`:
  - `searchCandidates(slug, q): Promise<Candidate[]>`;
  - `addMember(slug, userId, role)` отправляет `{ userId, role }`.
- `TeamsPage.tsx`:
  - TextField «SSO subject» → MUI `Autocomplete` с `freeSolo={false}` (только выбор из списка);
  - поиск с debounce ~300 мс, запрос отправляется при длине ≥ 2 символов;
  - опция отображается как `displayName (username)`;
  - кнопка «Добавить» активна только когда кандидат выбран;
  - сброс выбора после успешного добавления.

## 5. Ошибки

| Ситуация | Ответ |
|---|---|
| Запрос короче 2 символов | 200 `[]` (фронт просто не шлёт запрос) |
| Не админ и не OWNER команды | 403 (как сейчас) |
| `userId` несуществующий | 404 |
| Участник уже в команде | Текущее поведение (409/500 от constraint) не меняем |

## 6. Тесты

- `TeamUseCaseTest` — `searchCandidates`: проверка прав (OWNER/админ/403), пустой список при коротком `q`, исключение существующих участников, лимит; `addMember` по `userId` (успех + 404).
- Тесты `TeamController`/DTO — новый endpoint, контракт `userId` в запросе и ответе.
- `UserSyncServiceTest` — 4 аргумента, сохранение `username`, fallback на `subject`.
- IT `CategoryTeamApiIT` — сценарий «поиск → добавление».

## 7. Не делаем

- Показ email в подсказках и поиск по email (приватность).
- Поиск для пользователей без права добавления участников.
- Создание пользователей через Keycloak Admin API.
- Изменение логики ролей (по-прежнему добавляет только OWNER или админ) и приглашений.
- Фильтрацию дубликатов участников на уровне constraint (текущее поведение сохраняется).
