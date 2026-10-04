# Страница команд: состав, роли, управление участниками — Design Document

Дата: 2026-10-04
Статус: Approved

## 1. Обзор

Страница «Команды» показывает только названия команд: никто — даже админ — не видит, кто в какой команде, кто владелец, кто участник. Причина не только в UI: в бэкенде нет эндпоинта списка участников. Перерабатываем страницу в master-detail с полным составом и управлением (смена роли, исключение). Визуальный язык — существующая тема приложения (MUI, светлая, teal primary, Onest); из ui-ux-pro-max берём паттерны master-detail: видимый фокус, клавиатурная навигация, hover/focus states, повышенная плотность.

**Ключевые решения:**

| Решение | Выбор |
|---|---|
| Объём | Просмотр + управление (смена роли, исключение участника) |
| Видимость состава | Только участники команды (любая роль) и админы; остальным 403 |
| Структура страницы | Master-detail на одной странице (без переходов) |
| Смена роли / исключение | Только админ или OWNER команды |
| Последний OWNER | Нельзя понизить или исключить (409) |

## 2. Бэкенд

### 2.1. Модель

`TeamMember` (доменный слой, `com.skillhub.domain.model`): record `(UUID userId, String username, String displayName, TeamRole role)`.

### 2.2. Порт

`TeamMembershipPort` — новые методы:

- `List<TeamMember> membersOf(UUID teamId)` — join `team_members` + `users` (username, display_name, role)
- `void delete(UUID teamId, UUID userId)`

Подсчёт владельцев — фильтр по `membersOf` в use case, отдельного запроса нет.

### 2.3. Use case (`TeamUseCase`)

- `List<TeamMember> members(String teamSlug, User actor)` — команда не найдена → 404; актёр не админ и не участник команды (любая роль через `membership.roleOf`) → 403.
- `TeamMembership changeRole(String teamSlug, UUID userId, String role, User actor)` — права: админ или OWNER (существующий helper `requireOwnerOrAdmin`); роль невалидна → 422/400 (valueOf); целевой пользователь не в команде → 404; понижение/смена последнего OWNER → `ConflictException` (409). Сохранение — существующий `membership.save`.
- `void removeMember(String teamSlug, UUID userId, User actor)` — те же права; не в команде → 404; последний OWNER → 409; иначе `membership.delete`.

Защита последнего OWNER: перед сменой роли/удалением, если цель — OWNER и в команде ровно один OWNER и цель === этот OWNER → 409.

### 2.4. REST (`TeamController`)

```
GET    /api/teams/{slug}/members              → 200 [{userId, username, displayName, role}]
PATCH  /api/teams/{slug}/members/{userId}     тело { "role": "MAINTAINER" } → 200 {userId, username, displayName, role}
DELETE /api/teams/{slug}/members/{userId}     → 204
```

DTO: `ChangeRoleRequest(@NotBlank @Pattern(regexp = "OWNER|MAINTAINER|MEMBER") String role)`. Ответ PATCH — `TeamMemberResponse` (тот же набор полей, что и в списке).

### 2.5. Ошибки

| Ситуация | Ответ |
|---|---|
| Не участник и не админ — просмотр состава | 403 |
| Не админ/OWNER — смена роли/удаление | 403 |
| Последний OWNER — понижение/исключение | 409 «Последнего владельца нельзя убрать» |
| Команда/участник не найдены | 404 |

## 3. Фронтенд

### 3.1. API (`ui/src/api/teams.ts`)

- `members(slug): Promise<TeamMemberResponse[]>` — GET
- `changeRole(slug, userId, role)` — PATCH
- `removeMember(slug, userId)` — DELETE
- тип `TeamMemberResponse { userId, username, displayName, role }` в `ui/src/types.ts`

### 3.2. Страница (`ui/src/pages/TeamsPage.tsx`) — master-detail

**Левая панель** (Paper):
- Форма создания команды (только админ) — компактно сверху
- Список команд: `ListItemButton` (клавиатура из коробки), avatar-инициал (как сейчас), название, slug вторым текстом; выбранная — выделение
- Пустой список — текущий текст «Команд пока нет»

**Правая карточка выбранной команды** (Paper):
- Заголовок: название + slug + Chip «Ваша роль» (роль актёра в этой команде; для админа без участия — «Админ»)
- Список участников (`useQuery(['team-members', slug])`): avatar-инициал, `displayName`, вторая строка `@username`, Chip роли с цветовой кодировкой (OWNER — primary/заполненный, MAINTAINER — secondary, MEMBER — default/outline)
- Меню действий (IconButton ⋮, только для управляющих): «Сменить роль» → три MenuItem с русской отметкой текущей, «Исключить» → confirm-диалог, красная кнопка
- Действия на последнем OWNER задизейблены, tooltip «Последнего владельца нельзя убрать»
- Блок «Добавить участника»: существующий Autocomplete (кандидаты, debounce) + select роли + кнопка — переносится из старой формы внутрь карточки
- Не-участник (не админ) выбрал чужую команду: заглушка «Состав виден только участникам команды» вместо списка (запрос можно не слать — фронт знает свои роли из JWT)

**Адаптивность:** на узких экранах панели в столбец (detail под списком); мастер-список не прячется.

**Инвалидация:** после add/changeRole/remove — `invalidateQueries(['team-members', slug])` и `['teams']`.

### 3.3. Доступность

- Видимый фокус на всех элементах управления (MUI по умолчанию), tab-order = визуальный порядок
- IconButton-меню с `aria-label="Действия участника"`; пункты меню — текстовые
- Роль — не только цветом: Chip с текстом
- Кнопка исключения — в confirm-диалоге (не деструктив по одному клику)

## 4. Тесты

- `TeamUseCaseTest`: members — участник видит, не-участник 403, админ видит чужую; changeRole — смена роли, не-OWNER 403, последний OWNER 409, не-в-команде 404; removeMember — удаление, последний OWNER 409
- IT `TeamMembersApiIT`: цикл «создать команду → посмотреть состав → сменить роль → исключить», права (MEMBER читает состав, MAINTAINER не управляет, чужой 403), 409 последнего OWNER
- UI-тесты `TeamsPage`: состав рендерится, роль-Chip, меню ролей, последний OWNER задизейблен, заглушка не-участника

## 5. Не делаем

- Приглашения по email, история изменений
- Самостоятельный выход из команды (leave) и передача владения отдельным флоу
- Пагинация состава (команды малы)
- Массовые операции
