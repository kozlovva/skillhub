# Добавление участника через автокомплит — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Заменить добавление участника команды по `ssoSubject` на автокомплит-поиск по корпоративному логину/имени с добавлением по `userId`.

**Architecture:** Новая колонка `users.username` (клейм `preferred_username` Keycloak), team-scoped endpoint `GET /api/teams/{slug}/member-candidates?q=` с проверкой прав как у `addMember` (админ или OWNER), `POST /api/teams/{slug}/members` принимает `userId`. Фронт: MUI `Autocomplete` с debounce, ручной ввод отключён. Спека: `docs/superpowers/specs/2026-10-04-add-member-by-username-design.md` (ревизия 2).

**Tech Stack:** Spring Boot 3 (hexagonal: domain/port/adapters, Lombok, Flyway), JUnit 5 + Mockito (unit), Testcontainers Postgres (IT, failsafe), PostgreSQL `ILIKE`; React 18 + MUI v6 + TanStack Query v5 + Vite.

## Global Constraints

- Команда сборки: `mvn test` — unit-тесты; `mvn verify` — IT (Testcontainers, нужен Docker); фронт: `npm run build`, `npm test` (из каталога `ui/`).
- Maven wrapper отсутствует — использовать `mvn`.
- Стиль коммитов: conventional commits строчными (`feat:`, `test:`, `refactor:`).
- Роли: добавлять участника может только OWNER команды или админ (не менять).
- Поиск по `username`/`display_name`, **без** email (приватность).
- Лимит кандидатов — 10; минимальная длина запроса — 2 символа (после `trim()`).
- Спецификация колонок: миграция `V5__add_username.sql`, `username TEXT` nullable + unique index, бэкфилл `username = display_name`.
- Комментарии в коде не добавлять.

---

### Task 1: Колонка `users.username` и синхронизация из SSO

**Files:**
- Create: `src/main/resources/db/migration/V5__add_username.sql`
- Modify: `src/main/java/com/skillhub/domain/model/User.java`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/entity/JpaUser.java`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/mapper/UserJpaMapper.java`
- Modify: `src/main/java/com/skillhub/application/service/UserSyncService.java`
- Modify: `src/main/java/com/skillhub/adapters/in/security/CurrentUserResolver.java`
- Modify: `scripts/seed-demo.ps1:29-31`
- Test: `src/test/java/com/skillhub/adapters/in/security/CurrentUserResolverTest.java`
- Test: `src/test/java/com/skillhub/application/service/UserSyncServiceTest.java` (создать)
- Test: обновить вызовы `syncFromSso` в IT (список в шаге 6)

**Interfaces:**
- Produces: `UserSyncService.syncFromSso(String subject, String email, String username, String displayName)` — 4 аргумента; `User.getUsername()`; поле `username` в `JpaUser`/`UserJpaMapper` (нужно Task 2, 4).
- Consumes: существующие `UserRepositoryPort.save/findBySsoSubject`, `ClockPort.now()`.

- [ ] **Step 1: Миграция**

Создать `src/main/resources/db/migration/V5__add_username.sql`:

```sql
ALTER TABLE users ADD COLUMN username TEXT;
CREATE UNIQUE INDEX idx_users_username ON users (username);
UPDATE users SET username = display_name WHERE username IS NULL;
```

- [ ] **Step 2: Обновить модель и JPA-сущность**

`src/main/java/com/skillhub/domain/model/User.java` — добавить поле после `ssoSubject`:

```java
    private String username;
```

`src/main/java/com/skillhub/adapters/out/jpa/entity/JpaUser.java` — добавить поле после `ssoSubject`:

```java
    @Column private String username;
```

- [ ] **Step 3: Обновить маппер**

`src/main/java/com/skillhub/adapters/out/jpa/mapper/UserJpaMapper.java`:

```java
    public static User toDomain(JpaUser e) {
        return User.builder()
            .id(e.getId()).ssoSubject(e.getSsoSubject()).username(e.getUsername())
            .email(e.getEmail())
            .displayName(e.getDisplayName()).avatarUrl(e.getAvatarUrl())
            .admin(e.isAdmin()).createdAt(e.getCreatedAt())
            .build();
    }

    public static JpaUser toEntity(User d) {
        return JpaUser.builder()
            .id(d.getId()).ssoSubject(d.getSsoSubject()).username(d.getUsername())
            .email(d.getEmail())
            .displayName(d.getDisplayName()).avatarUrl(d.getAvatarUrl())
            .admin(d.isAdmin()).createdAt(d.getCreatedAt())
            .build();
    }
```

- [ ] **Step 4: Обновить UserSyncService и CurrentUserResolver**

`src/main/java/com/skillhub/application/service/UserSyncService.java` — заменить метод `syncFromSso`:

```java
    @Transactional
    public User syncFromSso(String subject, String email, String username, String displayName) {
        return users.findBySsoSubject(subject)
            .map(u -> {
                u.setEmail(email);
                u.setUsername(username);
                u.setDisplayName(displayName);
                return users.save(u);
            })
            .orElseGet(() -> users.save(User.builder()
                .ssoSubject(subject)
                .username(username)
                .email(email)
                .displayName(displayName)
                .admin(false)
                .createdAt(clock.now())
                .build()));
    }
```

`src/main/java/com/skillhub/adapters/in/security/CurrentUserResolver.java` — заменить тело ветки `Jwt`:

```java
        if (auth.getPrincipal() instanceof Jwt jwt) {
            String subject = jwt.getSubject();
            String username = jwt.getClaimAsString("preferred_username");
            String name = jwt.getClaimAsString("name");
            String email = jwt.getClaimAsString("email");
            return userSyncService.syncFromSso(subject, email != null ? email : subject,
                username != null ? username : subject,
                name != null ? name : (username != null ? username : subject));
        }
```

(Сохраняет текущее поведение: если клейма `name` нет, displayName = preferred_username.)

- [ ] **Step 5: Обновить/добавить unit-тесты**

`src/test/java/com/skillhub/application/service/UserSyncServiceTest.java` (новый файл):

```java
package com.skillhub.application.service;

import com.skillhub.domain.model.User;
import com.skillhub.domain.port.ClockPort;
import com.skillhub.domain.port.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserSyncServiceTest {

    UserRepositoryPort users;
    ClockPort clock;
    UserSyncService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepositoryPort.class);
        clock = mock(ClockPort.class);
        when(clock.now()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        service = new UserSyncService(users, clock);
    }

    @Test
    void createsUserWithUsername() {
        when(users.findBySsoSubject("sub-1")).thenReturn(Optional.empty());
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = service.syncFromSso("sub-1", "v@b.c", "vpetrov", "Владимир Петров");

        assertThat(result.getUsername()).isEqualTo("vpetrov");
        assertThat(result.getDisplayName()).isEqualTo("Владимир Петров");
    }

    @Test
    void updatesUsernameOnResync() {
        User existing = User.builder().id(UUID.randomUUID()).ssoSubject("sub-1")
            .username("old").email("old@b.c").displayName("Old").admin(false)
            .createdAt(Instant.now()).build();
        when(users.findBySsoSubject("sub-1")).thenReturn(Optional.of(existing));
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = service.syncFromSso("sub-1", "new@b.c", "vpetrov", "Владимир Петров");

        assertThat(result.getUsername()).isEqualTo("vpetrov");
        assertThat(result.getEmail()).isEqualTo("new@b.c");
        verify(users).save(existing);
    }
}
```

`src/test/java/com/skillhub/adapters/in/security/CurrentUserResolverTest.java` — заменить первые два теста (третий `anonymousReturnsNull` не меняется):

```java
    @Test
    void usesEmailClaimAndPreferredUsername() {
        UserSyncService sync = mock(UserSyncService.class);
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("sub-1")
            .claim("email", "real@skillhub.io").claim("preferred_username", "vlad")
            .build();
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(jwt);
        User synced = User.builder().ssoSubject("sub-1")
            .email("real@skillhub.io").username("vlad").displayName("vlad").build();
        when(sync.syncFromSso("sub-1", "real@skillhub.io", "vlad", "vlad")).thenReturn(synced);

        User result = new CurrentUserResolver(sync).resolve(auth);

        assertThat(result).isSameAs(synced);
        verify(sync).syncFromSso("sub-1", "real@skillhub.io", "vlad", "vlad");
    }

    @Test
    void fallsBackToSubjectWhenClaimsMissing() {
        UserSyncService sync = mock(UserSyncService.class);
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("sub-2").build();
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(jwt);
        when(sync.syncFromSso("sub-2", "sub-2", "sub-2", "sub-2"))
            .thenReturn(User.builder().build());

        new CurrentUserResolver(sync).resolve(auth);

        verify(sync).syncFromSso("sub-2", "sub-2", "sub-2", "sub-2");
    }

    @Test
    void usesNameClaimForDisplayNameWhenPresent() {
        UserSyncService sync = mock(UserSyncService.class);
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("sub-3")
            .claim("preferred_username", "vpetrov").claim("name", "Владимир Петров")
            .build();
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(jwt);
        when(sync.syncFromSso("sub-2", "sub-2", "vpetrov", "Владимир Петров"))
            .thenReturn(User.builder().build());

        new CurrentUserResolver(sync).resolve(auth);

        verify(sync).syncFromSso("sub-2", "sub-2", "vpetrov", "Владимир Петров");
    }
```

- [ ] **Step 6: Обновить вызовы syncFromSso в IT (4-й аргумент username = ssoSubject)**

Каждую трёхаргументную заменяем на четырёхаргументную (username = subject):

- `src/test/java/com/skillhub/adapters/in/security/SecurityApiIT.java:25`
  `users.syncFromSso("sec-user", "sec@skillhub.io", "Sec User")` →
  `users.syncFromSso("sec-user", "sec@skillhub.io", "sec-user", "Sec User")`
- `src/test/java/com/skillhub/adapters/in/security/PublicReadAccessIT.java:46`
  `users.syncFromSso("anon-user-" + UUID.randomUUID(), "anon@skillhub.io", "Anon User")` →
  `users.syncFromSso("anon-user-" + UUID.randomUUID(), "anon@skillhub.io", "anon-user-" + UUID.randomUUID(), "Anon User")`
- `src/test/java/com/skillhub/adapters/in/security/PublicReadAccessIT.java:131-132`
  ```java
  var outsider = users.syncFromSso(
      "anon-outsider-" + UUID.randomUUID(), "outsider@skillhub.io", "Outsider");
  ```
  →
  ```java
  var outsider = users.syncFromSso(
      "anon-outsider-" + UUID.randomUUID(), "outsider@skillhub.io",
      "anon-outsider-" + UUID.randomUUID(), "Outsider");
  ```
- `src/test/java/com/skillhub/adapters/in/rest/ElementApiIT.java:30`
  → `users.syncFromSso("elem-user", "el@skillhub.io", "elem-user", "Element User")`
- `src/test/java/com/skillhub/adapters/in/rest/CategoryTeamApiIT.java:31`
  → `users.syncFromSso(adminSubject, "admin@skillhub.io", "cat-admin", "Admin")`
- `src/test/java/com/skillhub/adapters/in/rest/CategoryTeamApiIT.java:36`
  → `users.syncFromSso("cat-member", "member@skillhub.io", "cat-member", "Member")`
- `src/test/java/com/skillhub/adapters/in/rest/SocialApiIT.java:30`
  → `users.syncFromSso("soc-user", "soc@skillhub.io", "soc-user", "Social User")`
- `src/test/java/com/skillhub/adapters/in/rest/SearchApiIT.java:28`
  → `users.syncFromSso("search-api-user", "search-api@skillhub.io", "search-api-user", "Search Api")`
- `src/test/java/com/skillhub/adapters/in/rest/TokenApiIT.java:27`
  → `users.syncFromSso("token-user", "tok@skillhub.io", "token-user", "Token User")`
- `src/test/java/com/skillhub/adapters/in/rest/VersionPublishIT.java:67`
  → `users.syncFromSso("pub-user", "pub@skillhub.io", "pub-user", "Publisher")`

- [ ] **Step 7: Обновить seed-скрипт**

`scripts/seed-demo.ps1` строка 29-31 — добавить `username`:

```powershell
INSERT INTO users (id, sso_subject, username, email, display_name, is_admin)
VALUES ('$demoUserId', 'seed-admin', 'seed-admin', 'admin@skillhub.io', 'Demo Admin', TRUE)
ON CONFLICT (sso_subject) DO UPDATE SET is_admin = TRUE;
```

- [ ] **Step 8: Запустить unit-тесты**

Run: `mvn test -Dtest="UserSyncServiceTest,CurrentUserResolverTest"`
Expected: PASS (3 теста resolver + 2 sync).

- [ ] **Step 9: Компиляция всего проекта**

Run: `mvn test-compile`
Expected: BUILD SUCCESS (все вызовы `syncFromSso` обновлены).

- [ ] **Step 10: Commit**

```bash
git add src/main/resources/db/migration/V5__add_username.sql src/main/java/com/skillhub src/test scripts/seed-demo.ps1
git commit -m "feat: store corporate username from preferred_username claim"
```

---

### Task 2: Поиск кандидатов в репозитории (`findById` + `searchCandidates`)

**Files:**
- Modify: `src/main/java/com/skillhub/domain/port/UserRepositoryPort.java`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/repository/JpaUserRepository.java`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/JpaUserRepositoryAdapter.java`
- Test: `src/test/java/com/skillhub/adapters/out/jpa/JpaUserRepositoryAdapterIT.java` (создать)

**Interfaces:**
- Consumes: `User.username` (Task 1), таблицы `users` и `team_members` (team_id, user_id).
- Produces: `UserRepositoryPort.findById(UUID id): Optional<User>` и `UserRepositoryPort.searchCandidates(String q, UUID excludeTeamId, int limit): List<User>` (нужно Task 3).

- [ ] **Step 1: Обновить порт**

`src/main/java/com/skillhub/domain/port/UserRepositoryPort.java`:

```java
package com.skillhub.domain.port;

import com.skillhub.domain.model.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {
    User save(User user);
    Optional<User> findById(UUID id);
    Optional<User> findBySsoSubject(String ssoSubject);
    List<User> searchCandidates(String query, UUID excludeTeamId, int limit);
}
```

- [ ] **Step 2: Написать IT (fallback: тест не скомпилируется, пока нет реализации — это ожидаемый красный шаг)**

Создать `src/test/java/com/skillhub/adapters/out/jpa/JpaUserRepositoryAdapterIT.java`:

```java
package com.skillhub.adapters.out.jpa;

import com.skillhub.domain.model.*;
import com.skillhub.domain.port.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class JpaUserRepositoryAdapterIT {

    @Autowired UserRepositoryPort users;
    @Autowired TeamRepositoryPort teams;
    @Autowired TeamMembershipPort membership;

    private User user(String subject, String username, String displayName) {
        return users.save(User.builder()
            .ssoSubject(subject).email(subject + "@b.c").username(username)
            .displayName(displayName).admin(false).createdAt(Instant.now()).build());
    }

    private Team team(String slug) {
        return teams.save(Team.builder().slug(slug).name(slug)
            .createdAt(Instant.now()).build());
    }

    @Test
    void findByIdReturnsSavedUser() {
        User saved = user("find-sub", "finduser", "Find User");
        assertThat(users.findById(saved.getId())).hasValueSatisfying(
            u -> assertThat(u.getUsername()).isEqualTo("finduser"));
        assertThat(users.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void searchFindsByUsernameAndDisplayNameCaseInsensitive() {
        user("s1", "vpetrov", "Иванов Пётр");
        user("s2", "sidorov", "Vasily Petrov");

        List<User> byUsername = users.searchCandidates("PET", null, 10);
        List<User> byName = users.searchCandidates("petrov", null, 10);

        assertThat(byUsername).extracting(User::getUsername)
            .containsExactlyInAnyOrder("vpetrov", "sidorov");
        assertThat(byName).extracting(User::getUsername)
            .containsExactlyInAnyOrder("vpetrov", "sidorov");
    }

    @Test
    void searchExcludesTeamMembers() {
        User member = user("m1", "member1", "Member One");
        User outsider = user("o1", "outsider1", "Outsider One");
        Team t = team("search-team");
        membership.save(TeamMembership.builder()
            .teamId(t.getId()).userId(member.getId()).role(TeamRole.MEMBER).build());

        List<User> result = users.searchCandidates("1", t.getId(), 10);

        assertThat(result).extracting(User::getUsername).containsExactly("outsider1");
    }

    @Test
    void searchRespectsLimit() {
        user("l1", "aa1", "L1");
        user("l2", "aa2", "L2");
        user("l3", "aa3", "L3");

        List<User> result = users.searchCandidates("aa", null, 2);

        assertThat(result).hasSize(2);
    }
}
```

Примечание: ILIKE в PostgreSQL учитывает локу БД — кириллические паттерны в тестах не используем, только Latin.

- [ ] **Step 3: Реализация в JPA-адаптере**

`src/main/java/com/skillhub/adapters/out/jpa/repository/JpaUserRepository.java`:

```java
package com.skillhub.adapters.out.jpa.repository;

import com.skillhub.adapters.out.jpa.entity.JpaUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaUserRepository extends JpaRepository<JpaUser, UUID> {
    Optional<JpaUser> findBySsoSubject(String ssoSubject);

    @Query(value = """
        SELECT * FROM users u
        WHERE (u.username ILIKE :pattern OR u.display_name ILIKE :pattern)
          AND NOT EXISTS (
            SELECT 1 FROM team_members tm
            WHERE tm.team_id = :excludeTeamId AND tm.user_id = u.id
          )
        ORDER BY u.display_name
        LIMIT :limit
        """, nativeQuery = true)
    List<JpaUser> searchCandidates(String pattern, UUID excludeTeamId, int limit);
}
```

`src/main/java/com/skillhub/adapters/out/jpa/JpaUserRepositoryAdapter.java` — добавить методы:

```java
    @Override
    public Optional<User> findById(UUID id) {
        return jpa.findById(id).map(UserJpaMapper::toDomain);
    }

    @Override
    public List<User> searchCandidates(String query, UUID excludeTeamId, int limit) {
        return jpa.searchCandidates("%" + query + "%", excludeTeamId, limit)
            .stream().map(UserJpaMapper::toDomain).toList();
    }
```

(Добавить импорты `java.util.List`.)

- [ ] **Step 4: Запустить IT**

Run: `mvn verify -Dit.test=JpaUserRepositoryAdapterIT`
Expected: PASS (4 теста `JpaUserRepositoryAdapterIT`; unit-тесты и остальные IT тоже должны пройти — они не затронуты).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skillhub/domain/port/UserRepositoryPort.java src/main/java/com/skillhub/adapters/out/jpa src/test/java/com/skillhub/adapters/out/jpa/JpaUserRepositoryAdapterIT.java
git commit -m "feat: user repository searchCandidates and findById"
```

---

### Task 3: TeamUseCase — поиск кандидатов и добавление по userId

**Files:**
- Modify: `src/main/java/com/skillhub/application/service/TeamUseCase.java`
- Test: `src/test/java/com/skillhub/application/service/TeamUseCaseTest.java`

**Interfaces:**
- Consumes: `UserRepositoryPort.findById(UUID)`, `UserRepositoryPort.searchCandidates(String, UUID, int)` (Task 2).
- Produces: `TeamUseCase.searchCandidates(String teamSlug, String query, User actor): List<User>`; `TeamUseCase.addMember(String teamSlug, UUID userId, String role, User actor): TeamMembership` (нужно Task 4).

- [ ] **Step 1: Обновить unit-тесты**

В `src/test/java/com/skillhub/application/service/TeamUseCaseTest.java` заменить тесты `onlyOwnerAddsMember` и `unknownUserIsNotFound` и добавить новые. Полный файл после правок:

```java
package com.skillhub.application.service;

import com.skillhub.core.exception.ConflictException;
import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.core.exception.NotFoundException;
import com.skillhub.domain.model.*;
import com.skillhub.domain.port.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TeamUseCaseTest {

    TeamRepositoryPort teams;
    TeamMembershipPort membership;
    UserRepositoryPort users;
    ClockPort clock;
    TeamUseCase useCase;

    User creator = User.builder().id(UUID.randomUUID()).ssoSubject("s").email("e")
        .displayName("Creator").admin(true).createdAt(Instant.now()).build();

    User plainUser = User.builder().id(UUID.randomUUID()).ssoSubject("plain").email("p")
        .displayName("Plain").admin(false).createdAt(Instant.now()).build();

    @BeforeEach
    void setUp() {
        teams = mock(TeamRepositoryPort.class);
        membership = mock(TeamMembershipPort.class);
        users = mock(UserRepositoryPort.class);
        clock = mock(ClockPort.class);
        when(clock.now()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(teams.save(any())).thenAnswer(inv -> {
            Team t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        useCase = new TeamUseCase(teams, membership, users, clock);
    }

    @Test
    void createTeamMakesCreatorOwner() {
        Team team = useCase.create("design", "Design", creator);
        assertThat(team.getSlug()).isEqualTo("design");
        org.mockito.Mockito.verify(membership).save(TeamMembership.builder()
            .teamId(team.getId()).userId(creator.getId()).role(TeamRole.OWNER).build());
    }

    @Test
    void onlyAdminCreatesTeam() {
        assertThatThrownBy(() -> useCase.create("design", "Design", plainUser))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void duplicateTeamConflicts() {
        when(teams.findBySlug("design")).thenReturn(Optional.of(Team.builder().build()));
        assertThatThrownBy(() -> useCase.create("design", "Design", creator))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void onlyOwnerAddsMember() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));
        User newMember = User.builder().id(UUID.randomUUID()).ssoSubject("m")
            .email("m").displayName("M").admin(false).createdAt(Instant.now()).build();
        when(users.findById(newMember.getId())).thenReturn(Optional.of(newMember));
        when(membership.roleOf(team.getId(), plainUser.getId()))
            .thenReturn(Optional.of(TeamRole.MEMBER));

        assertThatThrownBy(() -> useCase.addMember("ux", newMember.getId(), "MEMBER", plainUser))
            .isInstanceOf(ForbiddenException.class);

        when(membership.roleOf(team.getId(), plainUser.getId()))
            .thenReturn(Optional.of(TeamRole.OWNER));
        TeamMembership added = useCase.addMember("ux", newMember.getId(), "MEMBER", plainUser);
        assertThat(added.role()).isEqualTo(TeamRole.MEMBER);
    }

    @Test
    void unknownUserIsNotFound() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));
        when(membership.roleOf(team.getId(), plainUser.getId()))
            .thenReturn(Optional.of(TeamRole.OWNER));
        UUID ghost = UUID.randomUUID();
        when(users.findById(ghost)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.addMember("ux", ghost, "MEMBER", plainUser))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void candidatesRequireOwner() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));
        when(membership.roleOf(team.getId(), plainUser.getId()))
            .thenReturn(Optional.of(TeamRole.MEMBER));

        assertThatThrownBy(() -> useCase.searchCandidates("ux", "pet", plainUser))
            .isInstanceOf(ForbiddenException.class);

        when(membership.roleOf(team.getId(), plainUser.getId()))
            .thenReturn(Optional.of(TeamRole.OWNER));
        when(users.searchCandidates("pet", team.getId(), 10)).thenReturn(List.of());
        assertThat(useCase.searchCandidates("ux", "pet", plainUser)).isEmpty();
    }

    @Test
    void candidatesShortQueryReturnsEmptyWithoutSearch() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));

        assertThat(useCase.searchCandidates("ux", "p", creator)).isEmpty();
        assertThat(useCase.searchCandidates("ux", "   ", creator)).isEmpty();
        verifyNoInteractions(users);
    }

    @Test
    void candidatesTrimQueryAndSearch() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));
        User found = User.builder().id(UUID.randomUUID()).ssoSubject("p")
            .username("vpetrov").email("p").displayName("Petrov").admin(false)
            .createdAt(Instant.now()).build();
        when(users.searchCandidates("pet", team.getId(), 10)).thenReturn(List.of(found));

        List<User> result = useCase.searchCandidates("ux", "  pet  ", creator);

        assertThat(result).containsExactly(found);
        verify(users).searchCandidates("pet", team.getId(), 10);
    }

    @Test
    void candidatesTeamNotFound() {
        when(teams.findBySlug("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.searchCandidates("ghost", "pet", creator))
            .isInstanceOf(NotFoundException.class);
    }
}
```

- [ ] **Step 2: Запустить тесты — ожидаем падение компиляции**

Run: `mvn test -Dtest=TeamUseCaseTest`
Expected: COMPILATION ERROR (`searchCandidates`, `addMember(UUID)`, `findById` ещё не существуют).

- [ ] **Step 3: Реализовать TeamUseCase**

`src/main/java/com/skillhub/application/service/TeamUseCase.java`:

```java
package com.skillhub.application.service;

import com.skillhub.core.exception.ConflictException;
import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.core.exception.NotFoundException;
import com.skillhub.domain.model.*;
import com.skillhub.domain.port.ClockPort;
import com.skillhub.domain.port.TeamMembershipPort;
import com.skillhub.domain.port.TeamRepositoryPort;
import com.skillhub.domain.port.UserRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TeamUseCase {

    private static final int CANDIDATES_LIMIT = 10;

    private final TeamRepositoryPort teams;
    private final TeamMembershipPort membership;
    private final UserRepositoryPort users;
    private final ClockPort clock;

    public TeamUseCase(TeamRepositoryPort teams, TeamMembershipPort membership,
                       UserRepositoryPort users, ClockPort clock) {
        this.teams = teams;
        this.membership = membership;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public Team create(String slug, String name, User creator) {
        if (!creator.isAdmin()) {
            throw new ForbiddenException("Only admin can create teams");
        }
        if (teams.findBySlug(slug).isPresent()) {
            throw new ConflictException("Team already exists: " + slug);
        }
        Team team = teams.save(Team.builder()
            .slug(slug).name(name).createdAt(clock.now()).build());
        membership.save(TeamMembership.builder()
            .teamId(team.getId()).userId(creator.getId()).role(TeamRole.OWNER).build());
        return team;
    }

    @Transactional(readOnly = true)
    public List<Team> list() {
        return teams.findAll();
    }

    @Transactional(readOnly = true)
    public List<User> searchCandidates(String teamSlug, String query, User actor) {
        Team team = teams.findBySlug(teamSlug)
            .orElseThrow(() -> new NotFoundException("Team not found: " + teamSlug));
        requireOwnerOrAdmin(team, actor);
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }
        return users.searchCandidates(query.trim(), team.getId(), CANDIDATES_LIMIT);
    }

    @Transactional
    public TeamMembership addMember(String teamSlug, UUID userId, String role, User actor) {
        Team team = teams.findBySlug(teamSlug)
            .orElseThrow(() -> new NotFoundException("Team not found: " + teamSlug));
        requireOwnerOrAdmin(team, actor);
        User newMember = users.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found: " + userId));
        TeamRole teamRole = TeamRole.valueOf(role);
        membership.save(TeamMembership.builder()
            .teamId(team.getId()).userId(newMember.getId()).role(teamRole).build());
        return new TeamMembership(team.getId(), newMember.getId(), teamRole);
    }

    private void requireOwnerOrAdmin(Team team, User actor) {
        if (actor.isAdmin()) {
            return;
        }
        membership.roleOf(team.getId(), actor.getId())
            .filter(r -> r == TeamRole.OWNER)
            .orElseThrow(() -> new ForbiddenException("Only team OWNER can add members"));
    }
}
```

- [ ] **Step 4: Запустить тесты**

Run: `mvn test -Dtest=TeamUseCaseTest`
Expected: PASS (9 тестов).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skillhub/application/service/TeamUseCase.java src/test/java/com/skillhub/application/service/TeamUseCaseTest.java
git commit -m "feat: search team member candidates and add member by userId"
```

---

### Task 4: REST — endpoint кандидатов, `userId` в запросе/ответе

**Files:**
- Create: `src/main/java/com/skillhub/adapters/in/rest/dto/CandidateResponse.java`
- Modify: `src/main/java/com/skillhub/adapters/in/rest/dto/AddMemberRequest.java`
- Modify: `src/main/java/com/skillhub/adapters/in/rest/TeamController.java`
- Test: `src/test/java/com/skillhub/adapters/in/rest/CategoryTeamApiIT.java`

**Interfaces:**
- Consumes: `TeamUseCase.searchCandidates(String, String, User): List<User>`, `TeamUseCase.addMember(String, UUID, String, User): TeamMembership` (Task 3); `User.getUsername()` (Task 1).
- Produces: `GET /api/teams/{slug}/member-candidates?q=...` → `200 [{userId, username, displayName}]`; `POST /api/teams/{slug}/members` тело `{userId, role}`, ответ `{userId, role}` (нужно Task 5).

- [ ] **Step 1: DTO**

Создать `src/main/java/com/skillhub/adapters/in/rest/dto/CandidateResponse.java`:

```java
package com.skillhub.adapters.in.rest.dto;

import com.skillhub.domain.model.User;

import java.util.UUID;

public record CandidateResponse(UUID userId, String username, String displayName) {
    public static CandidateResponse from(User u) {
        return new CandidateResponse(u.getId(), u.getUsername(), u.getDisplayName());
    }
}
```

Заменить `src/main/java/com/skillhub/adapters/in/rest/dto/AddMemberRequest.java`:

```java
package com.skillhub.adapters.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record AddMemberRequest(
    @NotNull UUID userId,
    @NotBlank @Pattern(regexp = "OWNER|MAINTAINER|MEMBER") String role
) {}
```

- [ ] **Step 2: Контроллер**

`src/main/java/com/skillhub/adapters/in/rest/TeamController.java` — добавить импорт, endpoint и заменить ответ `addMember`:

Импорты (добавить к существующим):

```java
import com.skillhub.adapters.in.rest.dto.CandidateResponse;
import com.skillhub.domain.model.User;
```

Методы:

```java
    @GetMapping("/{slug}/member-candidates")
    public List<CandidateResponse> memberCandidates(@PathVariable String slug,
                                                    @RequestParam String q,
                                                    Authentication auth) {
        return teamUseCase.searchCandidates(slug, q, currentUser.resolve(auth))
            .stream().map(CandidateResponse::from).toList();
    }

    @PostMapping("/{slug}/members")
    public ResponseEntity<Map<String, String>> addMember(@PathVariable String slug,
                                                         @Valid @RequestBody AddMemberRequest req,
                                                         Authentication auth) {
        TeamMembership member = teamUseCase.addMember(
            slug, req.userId(), req.role(), currentUser.resolve(auth));
        return ResponseEntity.ok(Map.of(
            "userId", member.userId().toString(),
            "role", member.role().name()));
    }
```

- [ ] **Step 3: Обновить IT**

В `src/test/java/com/skillhub/adapters/in/rest/CategoryTeamApiIT.java`:

1. Поля класса — добавить:

```java
    UUID adminId;
    User memberUser;
```

(импорты: `com.skillhub.domain.model.User`, `java.util.UUID`; `import java.util.Map;` уже есть.)

2. `setUp()` — заменить строки синхронизации:

```java
    @BeforeEach
    void setUp() {
        var admin = users.syncFromSso(adminSubject, "admin@skillhub.io", "cat-admin", "Admin");
        admin.setAdmin(true);
        userRepo.save(admin);
        adminId = admin.getId();
        adminHeader = "Bearer " + tokens.createToken(admin, "admin").rawToken();

        memberUser = users.syncFromSso("cat-member", "member@skillhub.io", "cat-member", "Member");
        memberHeader = "Bearer " + tokens.createToken(memberUser, "member").rawToken();
    }
```

3. Тест `ownerAddsMember` — заменить тела запросов:

```java
    @Test
    void ownerAddsMember() {
        rest.exchange("/api/teams", HttpMethod.POST,
            new HttpEntity<>(Map.of("slug", "ux-team", "name", "UX"),
                headers(adminHeader)), String.class);

        ResponseEntity<String> promote = rest.exchange("/api/teams/ux-team/members",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("userId", memberUser.getId().toString(), "role", "OWNER"),
                headers(adminHeader)), String.class);
        assertThat(promote.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(promote.getBody()).contains(memberUser.getId().toString());

        ResponseEntity<String> added = rest.exchange("/api/teams/ux-team/members",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("userId", adminId.toString(), "role", "MEMBER"),
                headers(memberHeader)), String.class);
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
```

4. Тест `meReturnsAdminFlagAndTeamRoles` — заменить тело второго запроса:

```java
        rest.exchange("/api/teams/me-team/members", HttpMethod.POST,
            new HttpEntity<>(Map.of("userId", memberUser.getId().toString(), "role", "MAINTAINER"),
                headers(adminHeader)), String.class);
```

5. Добавить новый тест:

```java
    @Test
    void memberCandidatesRequireOwnerAndReturnMatches() {
        rest.exchange("/api/teams", HttpMethod.POST,
            new HttpEntity<>(Map.of("slug", "cand-team", "name", "Cand"),
                headers(adminHeader)), String.class);

        ResponseEntity<String> forbidden = rest.exchange(
            "/api/teams/cand-team/member-candidates?q=cat-m", HttpMethod.GET,
            new HttpEntity<>(headers(memberHeader)), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> found = rest.exchange(
            "/api/teams/cand-team/member-candidates?q=cat-m", HttpMethod.GET,
            new HttpEntity<>(headers(adminHeader)), String.class);
        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody()).contains("cat-member").contains("Member");
    }
```

- [ ] **Step 4: Запустить IT**

Run: `mvn verify` (Testcontainers поднимет Postgres; прогоняются все IT).
Expected: PASS, включая обновлённый `CategoryTeamApiIT` и миграцию V5 (`FlywayMigrationIT`).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skillhub/adapters/in/rest src/test/java/com/skillhub/adapters/in/rest/CategoryTeamApiIT.java
git commit -m "feat: member-candidates endpoint and userId-based add member api"
```

---

### Task 5: Фронтенд — Autocomplete вместо ручного ввода

**Files:**
- Modify: `ui/src/types.ts` (добавить интерфейс в конец файла)
- Modify: `ui/src/api/teams.ts`
- Modify: `ui/src/pages/TeamsPage.tsx`

**Interfaces:**
- Consumes: `GET /api/teams/{slug}/member-candidates?q=` и `POST /api/teams/{slug}/members {userId, role}` (Task 4).
- Produces: `teams.searchCandidates(slug: string, q: string): Promise<MemberCandidate[]>`; `teams.addMember(slug: string, userId: string, role: string): Promise<void>`; тип `MemberCandidate`.

- [ ] **Step 1: Типы и API-клиент**

`ui/src/types.ts` — добавить в конец файла:

```ts
export interface MemberCandidate {
  userId: string;
  username: string;
  displayName: string;
}
```

Заменить `ui/src/api/teams.ts` целиком:

```ts
import { api } from './client';
import type { TeamResponse, MemberCandidate } from '../types';

export const teams = {
  async list(): Promise<TeamResponse[]> {
    return (await api.get<TeamResponse[]>('/api/teams')).data;
  },
  async create(body: { slug: string; name: string }): Promise<TeamResponse> {
    return (await api.post<TeamResponse>('/api/teams', body)).data;
  },
  async searchCandidates(slug: string, q: string): Promise<MemberCandidate[]> {
    return (await api.get<MemberCandidate[]>(`/api/teams/${slug}/member-candidates`, { params: { q } })).data;
  },
  async addMember(slug: string, userId: string, role: string): Promise<void> {
    await api.post(`/api/teams/${slug}/members`, { userId, role });
  },
};
```

- [ ] **Step 2: TeamsPage — Autocomplete**

В `ui/src/pages/TeamsPage.tsx`:

1. Импорты — привести к виду:

```tsx
import { useEffect, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Typography, Paper, List, ListItem, ListItemAvatar, Avatar, ListItemText, TextField, Button,
  MenuItem, Stack, Divider, Autocomplete,
} from '@mui/material';
```

и добавить импорт типа:

```tsx
import type { MemberCandidate } from '../types';
```

2. Состояние — заменить `const [memberSubject, setMemberSubject] = useState('');` на:

```tsx
  const [memberUser, setMemberUser] = useState<MemberCandidate | null>(null);
  const [memberQuery, setMemberQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
```

3. Debounce — добавить после объявления состояний (перед `useQuery` команд):

```tsx
  useEffect(() => {
    const t = setTimeout(() => setDebouncedQuery(memberQuery.trim()), 300);
    return () => clearTimeout(t);
  }, [memberQuery]);
```

4. Запрос кандидатов — добавить рядом с запросом `teams`:

```tsx
  const { data: candidates } = useQuery({
    queryKey: ['member-candidates', memberTeam, debouncedQuery],
    queryFn: () => teamsApi.searchCandidates(memberTeam, debouncedQuery),
    enabled: !!memberTeam && debouncedQuery.length >= 2,
  });
```

5. Мутация — заменить `addMemberMutation`:

```tsx
  const addMemberMutation = useMutation({
    mutationFn: () => teamsApi.addMember(memberTeam, memberUser!.userId, memberRole),
    onSuccess: () => {
      showSuccess('Участник добавлен');
      setMemberUser(null);
      setMemberQuery('');
      qc.invalidateQueries({ queryKey: ['teams'] });
    },
    onError: (e) => showError(toApiError(e).message),
  });
```

6. Форма — заменить `<TextField label="SSO subject" ... />` на:

```tsx
            <Autocomplete
              sx={{ width: 260 }}
              options={candidates ?? []}
              value={memberUser}
              onChange={(_, v) => setMemberUser(v)}
              onInputChange={(_, v) => setMemberQuery(v)}
              getOptionLabel={(o) => `${o.displayName} (${o.username})`}
              isOptionEqualToValue={(o, v) => o.userId === v.userId}
              filterOptions={(o) => o}
              freeSolo={false}
              renderInput={(params) => (
                <TextField {...params} label="Пользователь"
                  placeholder="Начните вводить имя или логин" />
              )}
            />
```

(`filterOptions={(o) => o}` отключает локальную фильтрацию — фильтрует сервер.)

7. Кнопка — заменить условие disabled:

```tsx
            <Button variant="contained" onClick={() => addMemberMutation.mutate()}
              disabled={!memberTeam || !memberUser}>
              Добавить
            </Button>
```

- [ ] **Step 3: Проверка сборки и типов**

Run (из каталога `ui/`): `npm run build`
Expected: сборка без ошибок TS (`tsc` + `vite build`).

Run (из каталога `ui/`): `npm test`
Expected: PASS (существующие vitest-тесты не задеты).

- [ ] **Step 4: Commit**

```bash
git add ui/src
git commit -m "feat: add team member via autocomplete over member candidates"
```

---

### Task 6: Финальная проверка

**Files:** без изменений кода.

- [ ] **Step 1: Полный бэкенд**

Run: `mvn verify`
Expected: BUILD SUCCESS (unit + IT, включая `FlywayMigrationIT` на миграции V5).

- [ ] **Step 2: Фронтенд**

Run (из каталога `ui/`): `npm run build; npm test`
Expected: PASS.

- [ ] **Step 3: Ручная проверка (если запущено окружение)**

1. Открыть страницу «Команды», ввести в поле «Пользователь» ≥ 2 символа — появляется список кандидатов с форматом `Имя (логин)`, уже состоящие в команде отсутствуют.
2. Выбрать кандидата, добавить — снекбар «Участник добавлен», список обновился.
3. Ручной ввод произвольного текста без выбора — кнопка «Добавить» неактивна.

- [ ] **Step 4: Финальный коммит (если остались изменения)**

```bash
git status --short
```

Если чисто — завершить; иначе закоммитить остатки с осмысленным сообщением.
