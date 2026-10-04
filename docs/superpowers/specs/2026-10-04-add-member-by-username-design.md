# Добавление участника команды по корпоративному логину — Design Document

Дата: 2026-10-04
Статус: Approved

## 1. Обзор

Сейчас участника в команду можно добавить только по `ssoSubject` — непрозрачному UUID из клейма `sub` JWT-токена Keycloak. Человек не может узнать его наизусть, что делает функцию практически нерабочей. Заменяем ввод `ssoSubject` на ввод корпоративного логина (клейм `preferred_username` из Keycloak, например `vpetrov`).

**Ключевые решения:**

| Решение | Выбор |
|---|---|
| Поле для добавления | Новая колонка `users.username` из клейма `preferred_username` |
| Поиск пользователей | Не делаем (отклонено из-за приватности) |
| Незарегистрированный сотрудник | Понятная ошибка 404; пользователь заходит в SkillHub один раз, после чего его можно добавить |
| Бэкфилл существующих записей | `username = display_name` в миграции (сейчас displayName и есть preferred_username) |

## 2. Бэкенд

### 2.1. Миграция `V4__add_username.sql`

```sql
ALTER TABLE users ADD COLUMN username TEXT;
CREATE UNIQUE INDEX idx_users_username ON users (username);
UPDATE users SET username = display_name WHERE username IS NULL;
```

Колонка nullable (но после бэкфилла заполнена у всех текущих строк) — unique index в PostgreSQL допускает несколько NULL, так что вход без клейма `preferred_username` не сломается.

### 2.2. Модель и синхронизация

- `User.java`, `JpaUser.java` — новое поле `username`.
- `CurrentUserResolver.resolve()` — читает клейм `preferred_username` (fallback: `subject`), передаёт в `UserSyncService`.
- `UserSyncService.syncFromSso(subject, email, username, displayName)` — при каждом входе обновляет `username` вместе с `email`/`displayName`.

### 2.3. Поиск и добавление

- `UserRepositoryPort` — новый метод `Optional<User> findByUsername(String username)`; реализация в `JpaUserRepositoryAdapter`.
- `TeamUseCase.addMember(teamSlug, username, role, actor)` — ищет пользователя через `findByUsername`; если не найден → `NotFoundException("Пользователь " + username + " ещё не входил в SkillHub")`. Проверка прав (админ или OWNER команды) без изменений.
- `AddMemberRequest` — поле `ssoSubject` → `username` (`@NotBlank`); `TeamController.addMember` передаёт username в use case.
- Ответ `TeamController.addMember` — сейчас в поле `ssoSubject` по ошибке кладётся UUID membership; исправляем на `{"userId": <UUID пользователя>, "role": "..."}`.

## 3. Фронтенд

- `ui/src/api/teams.ts` — `addMember(slug, username, role)` отправляет `{ username, role }`.
- `ui/src/pages/TeamsPage.tsx` — TextField «SSO subject» → «Корпоративный логин» (state, валидация и сброс без изменений).

## 4. Ошибки

| Ситуация | Ответ |
|---|---|
| Логина нет в БД (не заходил) | 404 «Пользователь vpetrov ещё не входил в SkillHub» |
| Не админ и не OWNER команды | 403 (как сейчас) |
| Участник уже в команде | Текущее поведение (409/500 от constraint) не меняем |

## 5. Тесты

- `TeamUseCaseTest` — добавление по username; 404 с новым текстом для незарегистрированного; права OWNER/админ.
- `UserSyncServiceTest` — синхронизация `username` при повторном входе; fallback на `subject`, если клейма нет.
- Тесты `TeamController`/DTO — запрос с `username`, ответ с `userId`.

## 6. Не делаем

- Поиск/автокомплит по зарегистрированным пользователям (приватность email/displayName).
- Создание пользователей через Keycloak Admin API.
- Изменение логики ролей и приглашений.
