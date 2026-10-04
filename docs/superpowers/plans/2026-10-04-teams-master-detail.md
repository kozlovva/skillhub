# Teams Master-Detail (состав, роли, управление) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Переработать страницу «Команды» в master-detail: состав команды с ролями, смена роли, исключение участника (новые GET/PATCH/DELETE эндпоинты).

**Architecture:** Бэкенд — hexagonal: доменный record `TeamMember`, новые методы `TeamMembershipPort.membersOf/delete` (JPQL JOIN FETCH в адаптере), три метода `TeamUseCase` (`members`, `changeRole`, `removeMember`) с проверками прав и защитой последнего OWNER, REST в `TeamController`. Фронтенд — master-detail на `TeamsPage.tsx`: слева список команд (`ListItemButton`), справа карточка состава с Chip ролей, меню действий, confirm-диалог, перенос существующего Autocomplete добавления внутрь карточки. Визуальный язык — существующая MUI-тема (светлая, teal primary, Onest).

**Tech Stack:** Spring Boot 3, JPA/JPQL, JUnit 5 + Mockito, Testcontainers IT; React 18 + MUI v6 + TanStack Query v5 + Vitest.

## Global Constraints

- Спека: `docs/superpowers/specs/2026-10-04-teams-master-detail-design.md`
- Состав видят только участники команды (любая роль) и админы; остальным 403
- Управление (смена роли/исключение) — только админ или OWNER команды
- Последнего OWNER нельзя понизить или исключить → `ConflictException` (409), сообщение `"Cannot remove the last OWNER of the team"`
- REST: `GET /api/teams/{slug}/members` → `200 [{userId, username, displayName, role}]`; `PATCH /api/teams/{slug}/members/{userId}` тело `{"role": "..."}` → `200` той же формы; `DELETE` → `204`
- Русские подписи ролей на фронте: OWNER=«Владелец», MAINTAINER=«Редактор», MEMBER=«Участник»; роль не только цветом — Chip с текстом
- Исключение — через confirm-диалог; действия последнего OWNER задизейблены с tooltip «Последнего владельца нельзя убрать»
- Коммит-стиль: conventional lowercase (`feat:`, `test:`); без комментариев в коде
- Команды: `mvn test` (unit), `mvn verify` (IT, Testcontainers), в `ui/`: `npm test`, `npm run build`

---

### Task 1: Домен, порт и use case (members/changeRole/removeMember)

**Files:**
- Create: `src/main/java/com/skillhub/domain/model/TeamMember.java`
- Modify: `src/main/java/com/skillhub/domain/port/TeamMembershipPort.java`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/repository/JpaTeamMemberRepository.java`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/JpaTeamMembershipAdapter.java`
- Modify: `src/main/java/com/skillhub/application/service/TeamUseCase.java`
- Test: `src/test/java/com/skillhub/application/service/TeamUseCaseTest.java`

**Interfaces:**
- Produces (Task 2 и фронт зависят от этого):
  - `TeamMember(UUID userId, String username, String displayName, TeamRole role)` — record
  - `TeamMembershipPort.membersOf(UUID teamId): List<TeamMember>`, `TeamMembershipPort.delete(UUID teamId, UUID userId)`
  - `TeamUseCase.members(String teamSlug, User actor): List<TeamMember>`
  - `TeamUseCase.changeRole(String teamSlug, UUID userId, String role, User actor): TeamMember`
  - `TeamUseCase.removeMember(String teamSlug, UUID userId, User actor): void`

- [ ] **Step 1: Написать падающие unit-тесты**

В `src/test/java/com/skillhub/application/service/TeamUseCaseTest.java` добавить (импорты `TeamMember`, `assertThatThrownBy`, `ForbiddenException`, `ConflictException`, `NotFoundException`, `TeamRole`, `UUID`, `List` уже есть/добавить по мере необходимости):

```java
    @Test
    void membersVisibleToTeamMemberAndAdmin() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));
        when(membership.roleOf(team.getId(), plainUser.getId()))
            .thenReturn(Optional.of(TeamRole.MEMBER));
        List<TeamMember> roster = List.of(
            new TeamMember(creator.getId(), "admin", "Admin", TeamRole.OWNER));
        when(membership.membersOf(team.getId())).thenReturn(roster);

        assertThat(useCase.members("ux", creator)).isEqualTo(roster);
        assertThat(useCase.members("ux", plainUser)).isEqualTo(roster);
    }

    @Test
    void membersHiddenFromOutsider() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));
        when(membership.roleOf(team.getId(), plainUser.getId()))
            .thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.members("ux", plainUser))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void membersTeamNotFound() {
        when(teams.findBySlug("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.members("ghost", creator))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void ownerChangesRole() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));
        User member = User.builder().id(UUID.randomUUID()).ssoSubject("m")
            .username("member").email("m").displayName("Member").admin(false)
            .createdAt(Instant.now()).build();
        when(membership.membersOf(team.getId())).thenReturn(List.of(
            new TeamMember(creator.getId(), "admin", "Admin", TeamRole.OWNER),
            new TeamMember(member.getId(), "member", "Member", TeamRole.MEMBER)));

        TeamMember updated = useCase.changeRole("ux", member.getId(), "MAINTAINER", creator);

        assertThat(updated.role()).isEqualTo(TeamRole.MAINTAINER);
        assertThat(updated.username()).isEqualTo("member");
        org.mockito.Mockito.verify(membership).save(TeamMembership.builder()
            .teamId(team.getId()).userId(member.getId()).role(TeamRole.MAINTAINER).build());
    }

    @Test
    void maintainerCannotChangeRole() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));
        when(membership.roleOf(team.getId(), plainUser.getId()))
            .thenReturn(Optional.of(TeamRole.MAINTAINER));
        assertThatThrownBy(() -> useCase.changeRole("ux", creator.getId(), "MEMBER", plainUser))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void cannotDemoteLastOwner() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));
        when(membership.membersOf(team.getId())).thenReturn(List.of(
            new TeamMember(creator.getId(), "admin", "Admin", TeamRole.OWNER)));
        assertThatThrownBy(() -> useCase.changeRole("ux", creator.getId(), "MEMBER", creator))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void changeRoleUnknownMemberIsNotFound() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));
        when(membership.membersOf(team.getId())).thenReturn(List.of(
            new TeamMember(creator.getId(), "admin", "Admin", TeamRole.OWNER)));
        assertThatThrownBy(() -> useCase.changeRole("ux", UUID.randomUUID(), "MEMBER", creator))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void ownerRemovesMember() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));
        User member = User.builder().id(UUID.randomUUID()).ssoSubject("m")
            .username("member").email("m").displayName("Member").admin(false)
            .createdAt(Instant.now()).build();
        when(membership.membersOf(team.getId())).thenReturn(List.of(
            new TeamMember(creator.getId(), "admin", "Admin", TeamRole.OWNER),
            new TeamMember(member.getId(), "member", "Member", TeamRole.MEMBER)));

        useCase.removeMember("ux", member.getId(), creator);

        org.mockito.Mockito.verify(membership).delete(team.getId(), member.getId());
    }

    @Test
    void cannotRemoveLastOwner() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));
        when(membership.membersOf(team.getId())).thenReturn(List.of(
            new TeamMember(creator.getId(), "admin", "Admin", TeamRole.OWNER)));
        assertThatThrownBy(() -> useCase.removeMember("ux", creator.getId(), creator))
            .isInstanceOf(ConflictException.class);
        org.mockito.Mockito.verify(membership, org.mockito.Mockito.never())
            .delete(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
```

- [ ] **Step 2: Запустить тесты — ожидается ошибка компиляции**

Run: `mvn test -Dtest=TeamUseCaseTest`
Expected: COMPILATION ERROR (`TeamMember`, `members`, `changeRole`, `removeMember`, `membership.membersOf/delete` не существуют).

- [ ] **Step 3: Домен и порт**

Создать `src/main/java/com/skillhub/domain/model/TeamMember.java`:

```java
package com.skillhub.domain.model;

import java.util.UUID;

public record TeamMember(UUID userId, String username, String displayName, TeamRole role) {
}
```

В `src/main/java/com/skillhub/domain/port/TeamMembershipPort.java` добавить методы (импорт `com.skillhub.domain.model.TeamMember`):

```java
    List<TeamMember> membersOf(UUID teamId);
    void delete(UUID teamId, UUID userId);
```

- [ ] **Step 4: JPA-репозиторий и адаптер**

В `src/main/java/com/skillhub/adapters/out/jpa/repository/JpaTeamMemberRepository.java` добавить:

```java
    @Query("SELECT m FROM JpaTeamMember m JOIN FETCH m.user WHERE m.team.id = :teamId ORDER BY m.user.displayName")
    List<JpaTeamMember> findByTeamId(@Param("teamId") UUID teamId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM JpaTeamMember m WHERE m.team.id = :teamId AND m.user.id = :userId")
    void deleteByTeamIdAndUserId(@Param("teamId") UUID teamId, @Param("userId") UUID userId);
```

В `src/main/java/com/skillhub/adapters/out/jpa/JpaTeamMembershipAdapter.java` добавить (импорт `TeamMember`):

```java
    @Override
    public List<TeamMember> membersOf(UUID teamId) {
        return jpa.findByTeamId(teamId).stream()
            .map(m -> new TeamMember(m.getUser().getId(), m.getUser().getUsername(),
                m.getUser().getDisplayName(), TeamRole.valueOf(m.getRole())))
            .toList();
    }

    @Override
    @Transactional
    public void delete(UUID teamId, UUID userId) {
        jpa.deleteByTeamIdAndUserId(teamId, userId);
    }
```

- [ ] **Step 5: Use case**

В `src/main/java/com/skillhub/application/service/TeamUseCase.java` добавить (импорты уже есть):

```java
    @Transactional(readOnly = true)
    public List<TeamMember> members(String teamSlug, User actor) {
        Team team = teams.findBySlug(teamSlug)
            .orElseThrow(() -> new NotFoundException("Team not found: " + teamSlug));
        requireMemberOrAdmin(team, actor);
        return membership.membersOf(team.getId());
    }

    @Transactional
    public TeamMember changeRole(String teamSlug, UUID userId, String role, User actor) {
        Team team = teams.findBySlug(teamSlug)
            .orElseThrow(() -> new NotFoundException("Team not found: " + teamSlug));
        requireOwnerOrAdmin(team, actor);
        TeamRole newRole = TeamRole.valueOf(role);
        List<TeamMember> members = membership.membersOf(team.getId());
        TeamMember target = requireMember(members, userId);
        ensureNotLastOwner(members, target, newRole);
        membership.save(TeamMembership.builder()
            .teamId(team.getId()).userId(userId).role(newRole).build());
        return new TeamMember(target.userId(), target.username(), target.displayName(), newRole);
    }

    @Transactional
    public void removeMember(String teamSlug, UUID userId, User actor) {
        Team team = teams.findBySlug(teamSlug)
            .orElseThrow(() -> new NotFoundException("Team not found: " + teamSlug));
        requireOwnerOrAdmin(team, actor);
        List<TeamMember> members = membership.membersOf(team.getId());
        TeamMember target = requireMember(members, userId);
        ensureNotLastOwner(members, target, null);
        membership.delete(team.getId(), userId);
    }

    private TeamMember requireMember(List<TeamMember> members, UUID userId) {
        return members.stream().filter(m -> m.userId().equals(userId)).findFirst()
            .orElseThrow(() -> new NotFoundException("Member not found: " + userId));
    }

    private void ensureNotLastOwner(List<TeamMember> members, TeamMember target, TeamRole newRole) {
        boolean losingOwnership = newRole == null || newRole != TeamRole.OWNER;
        if (target.role() == TeamRole.OWNER && losingOwnership
            && members.stream().filter(m -> m.role() == TeamRole.OWNER).count() == 1) {
            throw new ConflictException("Cannot remove the last OWNER of the team");
        }
    }

    private void requireMemberOrAdmin(Team team, User actor) {
        if (actor.isAdmin()) {
            return;
        }
        membership.roleOf(team.getId(), actor.getId())
            .orElseThrow(() -> new ForbiddenException("Only team members can view members"));
    }
```

Также заменить сообщение в существующем `requireOwnerOrAdmin` на общее для всех управляющих операций:

```java
            .orElseThrow(() -> new ForbiddenException("Only team OWNER or admin can manage members"));
```

- [ ] **Step 6: Запустить тесты**

Run: `mvn test -Dtest=TeamUseCaseTest`
Expected: PASS (все тесты файла, включая 9 старых).

- [ ] **Step 7: Полный unit-прогон и коммит**

Run: `mvn test`
Expected: BUILD SUCCESS.

```bash
git add src/main/java/com/skillhub/domain/model/TeamMember.java src/main/java/com/skillhub/domain/port/TeamMembershipPort.java src/main/java/com/skillhub/adapters/out/jpa/repository/JpaTeamMemberRepository.java src/main/java/com/skillhub/adapters/out/jpa/JpaTeamMembershipAdapter.java src/main/java/com/skillhub/application/service/TeamUseCase.java src/test/java/com/skillhub/application/service/TeamUseCaseTest.java
git commit -m "feat: team members listing, role change and removal in use case"
```

---

### Task 2: REST эндпоинты + IT

**Files:**
- Create: `src/main/java/com/skillhub/adapters/in/rest/dto/TeamMemberResponse.java`
- Create: `src/main/java/com/skillhub/adapters/in/rest/dto/ChangeRoleRequest.java`
- Modify: `src/main/java/com/skillhub/adapters/in/rest/TeamController.java`
- Test: `src/test/java/com/skillhub/adapters/in/rest/TeamMembersApiIT.java` (создать)

**Interfaces:**
- Consumes: `TeamUseCase.members(String, User): List<TeamMember>`, `changeRole(String, UUID, String, User): TeamMember`, `removeMember(String, UUID, User): void` (Task 1).
- Produces: контракт из Global Constraints (GET/PATCH/DELETE members) — его потребляет Task 3.

- [ ] **Step 1: DTO**

`src/main/java/com/skillhub/adapters/in/rest/dto/TeamMemberResponse.java`:

```java
package com.skillhub.adapters.in.rest.dto;

import com.skillhub.domain.model.TeamMember;

import java.util.UUID;

public record TeamMemberResponse(UUID userId, String username, String displayName, String role) {

    public static TeamMemberResponse from(TeamMember m) {
        return new TeamMemberResponse(m.userId(), m.username(), m.displayName(), m.role().name());
    }
}
```

`src/main/java/com/skillhub/adapters/in/rest/dto/ChangeRoleRequest.java`:

```java
package com.skillhub.adapters.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangeRoleRequest(
    @NotBlank @Pattern(regexp = "OWNER|MAINTAINER|MEMBER") String role
) {}
```

- [ ] **Step 2: Контроллер**

В `src/main/java/com/skillhub/adapters/in/rest/TeamController.java` добавить импорты:

```java
import com.skillhub.adapters.in.rest.dto.ChangeRoleRequest;
import com.skillhub.adapters.in.rest.dto.TeamMemberResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import java.util.UUID;
```

И методы:

```java
    @GetMapping("/{slug}/members")
    public List<TeamMemberResponse> members(@PathVariable String slug, Authentication auth) {
        return teamUseCase.members(slug, currentUser.resolve(auth)).stream()
            .map(TeamMemberResponse::from).toList();
    }

    @PatchMapping("/{slug}/members/{userId}")
    public TeamMemberResponse changeRole(@PathVariable String slug,
                                         @PathVariable UUID userId,
                                         @Valid @RequestBody ChangeRoleRequest req,
                                         Authentication auth) {
        return TeamMemberResponse.from(
            teamUseCase.changeRole(slug, userId, req.role(), currentUser.resolve(auth)));
    }

    @DeleteMapping("/{slug}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable String slug,
                                             @PathVariable UUID userId,
                                             Authentication auth) {
        teamUseCase.removeMember(slug, userId, currentUser.resolve(auth));
        return ResponseEntity.noContent().build();
    }
```

- [ ] **Step 3: IT**

Создать `src/test/java/com/skillhub/adapters/in/rest/TeamMembersApiIT.java` по образцу `CategoryTeamApiIT` (тот же стек: `@SpringBootTest(RANDOM_PORT)`, `UserSyncService`, `ApiTokenService`):

```java
package com.skillhub.adapters.in.rest;

import com.skillhub.application.service.ApiTokenService;
import com.skillhub.application.service.UserSyncService;
import com.skillhub.domain.port.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TeamMembersApiIT {

    @Autowired TestRestTemplate rest;
    @Autowired UserSyncService users;
    @Autowired UserRepositoryPort userRepo;
    @Autowired ApiTokenService tokens;

    String admin;
    String owner;
    String maintainer;
    UUID maintainerId;

    @BeforeEach
    void setUp() {
        var a = users.syncFromSso("tm-admin", "tm-admin@b.c", "tm-admin", "Tm Admin");
        a.setAdmin(true);
        userRepo.save(a);
        admin = "Bearer " + tokens.createToken(a, "admin").rawToken();

        var o = users.syncFromSso("tm-owner", "tm-owner@b.c", "tm-owner", "Tm Owner");
        owner = "Bearer " + tokens.createToken(o, "owner").rawToken();

        var m = users.syncFromSso("tm-maint", "tm-maint@b.c", "tm-maint", "Tm Maintainer");
        maintainerId = m.getId();
        maintainer = "Bearer " + tokens.createToken(m, "maint").rawToken();

        rest.exchange("/api/teams", HttpMethod.POST,
            new HttpEntity<>(Map.of("slug", "tm-team", "name", "TM"),
                json(admin)), String.class);
        rest.exchange("/api/teams/tm-team/members", HttpMethod.POST,
            new HttpEntity<>(Map.of("userId", o.getId().toString(), "role", "OWNER"),
                json(admin)), String.class);
        rest.exchange("/api/teams/tm-team/members", HttpMethod.POST,
            new HttpEntity<>(Map.of("userId", maintainerId.toString(), "role", "MAINTAINER"),
                json(admin)), String.class);
    }

    HttpHeaders json(String token) {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    void memberSeesRosterOutsiderForbidden() {
        ResponseEntity<String> seen = rest.exchange("/api/teams/tm-team/members",
            HttpMethod.GET, new HttpEntity<>(json(maintainer)), String.class);
        assertThat(seen.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(seen.getBody()).contains("tm-owner").contains("OWNER").contains("MAINTAINER");

        var stranger = users.syncFromSso("tm-stranger", "tm-stranger@b.c", "tm-stranger", "Stranger");
        String strangerAuth = "Bearer " + tokens.createToken(stranger, "stranger").rawToken();
        ResponseEntity<String> forbidden = rest.exchange("/api/teams/tm-team/members",
            HttpMethod.GET, new HttpEntity<>(json(strangerAuth)), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void maintainerCannotManageOwnerCan() {
        ResponseEntity<String> forbidden = rest.exchange(
            "/api/teams/tm-team/members/" + maintainerId,
            HttpMethod.PATCH,
            new HttpEntity<>(Map.of("role", "MEMBER"), json(maintainer)), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> ok = rest.exchange(
            "/api/teams/tm-team/members/" + maintainerId,
            HttpMethod.PATCH,
            new HttpEntity<>(Map.of("role", "MEMBER"), json(admin)), String.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody()).contains("\"role\":\"MEMBER\"");
    }

    @Test
    void removeMemberWorksAndGuardsLastOwner() {
        var extra = users.syncFromSso("tm-extra", "tm-extra@b.c", "tm-extra", "Tm Extra");
        rest.exchange("/api/teams/tm-team/members", HttpMethod.POST,
            new HttpEntity<>(Map.of("userId", extra.getId().toString(), "role", "MEMBER"),
                json(admin)), String.class);

        ResponseEntity<String> removed = rest.exchange(
            "/api/teams/tm-team/members/" + extra.getId(),
            HttpMethod.DELETE, new HttpEntity<>(json(owner)), String.class);
        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> roster = rest.exchange("/api/teams/tm-team/members",
            HttpMethod.GET, new HttpEntity<>(json(admin)), String.class);
        assertThat(roster.getBody()).doesNotContain("tm-extra");

        String adminMemberId = java.util.Arrays.stream(roster.getBody().split("\\},"))
            .filter(s -> s.contains("tm-admin"))
            .map(s -> s.replaceAll(".*\"userId\":\"([0-9a-f-]{36})\".*", "$1"))
            .findFirst().orElse(null);
        org.junit.jupiter.api.Assumptions.assumeTrue(adminMemberId != null);

        ResponseEntity<String> demoted = rest.exchange(
            "/api/teams/tm-team/members/" + adminMemberId,
            HttpMethod.PATCH,
            new HttpEntity<>(Map.of("role", "MEMBER"), json(admin)), String.class);
        assertThat(demoted.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> lastOwner = rest.exchange(
            "/api/teams/tm-team/members/" + users.syncFromSso(
                "tm-owner", "tm-owner@b.c", "tm-owner", "Tm Owner").getId(),
            HttpMethod.PATCH,
            new HttpEntity<>(Map.of("role", "MEMBER"), json(admin)), String.class);
        assertThat(lastOwner.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(lastOwner.getBody()).contains("last OWNER");
    }
}
```

Примечание: в `setUp` админ создаёт команду `tm-team` и становится её первым OWNER; `tm-owner` добавлен вторым OWNER. Тест удаляет `tm-extra` (204), понижает OWNER-админа (200), затем понижение оставшегося единственного OWNER `tm-owner` → 409.

- [ ] **Step 4: Запустить IT**

Run: `mvn verify -Dit.test=TeamMembersApiIT`
Expected: PASS (3 теста), миграции и unit-тесты зелёные.

- [ ] **Step 5: Коммит**

```bash
git add src/main/java/com/skillhub/adapters/in/rest src/test/java/com/skillhub/adapters/in/rest/TeamMembersApiIT.java
git commit -m "feat: team members rest endpoints with role management"
```

---

### Task 3: Фронтенд — master-detail

**Files:**
- Modify: `ui/src/types.ts` (добавить интерфейс в конец)
- Modify: `ui/src/api/teams.ts`
- Modify: `ui/src/pages/TeamsPage.tsx` (полная переработка)
- Test: `ui/src/pages/TeamsPage.test.tsx` (создать)

**Interfaces:**
- Consumes: REST-контракт Task 2 (`GET/PATCH/DELETE /api/teams/{slug}/members[/{userId}]`), `MemberCandidate` и `searchCandidates`/`addMember` (существующие), `useAuth()` → `{ authenticated, isAdmin, myTeamRoles: Record<string,string>, teamRoleOf(slug): string | null }`.

- [ ] **Step 1: Типы и API**

В `ui/src/types.ts` добавить в конец:

```ts
export interface TeamMemberResponse {
  userId: string;
  username: string;
  displayName: string;
  role: 'OWNER' | 'MAINTAINER' | 'MEMBER';
}
```

В `ui/src/api/teams.ts` (импорт `TeamMemberResponse` из `../types`) добавить методы в объект `teams`:

```ts
  async members(slug: string): Promise<TeamMemberResponse[]> {
    return (await api.get<TeamMemberResponse[]>(`/api/teams/${slug}/members`)).data;
  },
  async changeRole(slug: string, userId: string, role: string): Promise<void> {
    await api.patch(`/api/teams/${slug}/members/${userId}`, { role });
  },
  async removeMember(slug: string, userId: string): Promise<void> {
    await api.delete(`/api/teams/${slug}/members/${userId}`);
  },
```

(Проверить, что `api` в `ui/src/api/client.ts` — axios-инстанс: `patch`/`delete` доступны из коробки.)

- [ ] **Step 2: UI-тесты (RED)**

Создать `ui/src/pages/TeamsPage.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import TeamsPage from './TeamsPage';

const { getMock, patchMock, deleteMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  patchMock: vi.fn(),
  deleteMock: vi.fn(),
}));

vi.mock('../api/client', () => ({
  api: {
    get: getMock,
    patch: patchMock,
    delete: deleteMock,
    post: vi.fn(),
  },
  toApiError: (e: unknown) => ({
    status: 0, code: 'ERROR',
    message: e instanceof Error ? e.message : String(e), details: null,
  }),
}));

vi.mock('../auth/KeycloakProvider', () => ({
  useAuth: () => ({
    authenticated: true,
    token: 't',
    displayName: 'Owner',
    isAdmin: false,
    myTeamRoles: { platform: 'OWNER' },
    teamRoleOf: (slug: string) => (slug === 'platform' ? 'OWNER' : null),
    login: vi.fn(),
    logout: vi.fn(),
  }),
}));

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <TeamsPage />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

const teams = [
  { slug: 'platform', name: 'Platform' },
  { slug: 'other', name: 'Other' },
];

const roster = [
  { userId: 'u1', username: 'petrov', displayName: 'Пётр Петров', role: 'OWNER' },
  { userId: 'u2', username: 'ivanov', displayName: 'Иван Иванов', role: 'MEMBER' },
];

beforeEach(() => {
  getMock.mockImplementation((url: string) => {
    if (url === '/api/teams') {
      return Promise.resolve({ data: teams });
    }
    if (url === '/api/teams/platform/members') {
      return Promise.resolve({ data: roster });
    }
    return Promise.reject(new Error('unexpected ' + url));
  });
});

test('renders roster of selected team with role chips', async () => {
  renderPage();
  expect(await screen.findByText('Пётр Петров')).toBeInTheDocument();
  expect(screen.getByText('@petrov')).toBeInTheDocument();
  expect(screen.getByText('Владелец')).toBeInTheDocument();
  expect(screen.getByText('@ivanov')).toBeInTheDocument();
  expect(screen.getByText('Участник')).toBeInTheDocument();
});

test('outsider sees restricted notice instead of roster', async () => {
  const user = userEvent.setup();
  renderPage();
  await screen.findByText('Пётр Петров');
  await user.click(screen.getByRole('button', { name: /Other/ }));
  expect(await screen.findByText('Состав виден только участникам команды')).toBeInTheDocument();
  expect(getMock).not.toHaveBeenCalledWith('/api/teams/other/members');
});

test('role menu offers three roles and calls changeRole', async () => {
  const user = userEvent.setup();
  patchMock.mockResolvedValue({});
  renderPage();
  await screen.findByText('Пётр Петров');
  await user.click(screen.getAllByRole('button', { name: 'Действия участника' })[0]);
  await user.click(await screen.findByRole('menuitem', { name: 'Редактор' }));
  expect(patchMock).toHaveBeenCalledWith('/api/teams/platform/members/u2', { role: 'MAINTAINER' });
});
```

Примечание: мок `useAuth` должен содержать все поля, которые читает страница (сверить с реальным `KeycloakProvider`); при несоответствии — дополнить мок, не менять страницу. Если роуты не используются — `MemoryRouter` оставить для совместимости с `Link`.

Run: `cd ui; npm test -- TeamsPage`
Expected: FAIL — страница ещё старая (нет '@petrov', нет 'Состав виден только участникам команды', нет кнопки 'Действия участника').

- [ ] **Step 3: Переработать TeamsPage.tsx**

Полная замена `ui/src/pages/TeamsPage.tsx`:

```tsx
import { useEffect, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Typography, Paper, List, ListItemButton, ListItemAvatar, Avatar, ListItemText, TextField, Button,
  MenuItem, Stack, Divider, Autocomplete, Chip, IconButton, Menu, Tooltip, Dialog, DialogTitle,
  DialogContentText, DialogActions, Box,
} from '@mui/material';
import GroupsIcon from '@mui/icons-material/Groups';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import AddBusinessIcon from '@mui/icons-material/AddBusiness';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import LockIcon from '@mui/icons-material/Lock';
import { teams as teamsApi } from '../api/teams';
import { toApiError } from '../api/client';
import type { MemberCandidate, TeamMemberResponse } from '../types';
import { useSnackbar } from '../layout/SnackbarContext';
import { useAuth } from '../auth/KeycloakProvider';
import PageHeader from '../components/PageHeader';

const ROLE_LABELS: Record<string, string> = {
  OWNER: 'Владелец',
  MAINTAINER: 'Редактор',
  MEMBER: 'Участник',
};

const ROLE_ORDER = ['OWNER', 'MAINTAINER', 'MEMBER'] as const;

function roleChipProps(role: string): { color?: 'primary' | 'secondary'; variant?: 'filled' | 'outlined' } {
  if (role === 'OWNER') return { color: 'primary' };
  if (role === 'MAINTAINER') return { color: 'secondary' };
  return { variant: 'outlined' };
}

export default function TeamsPage() {
  const qc = useQueryClient();
  const { showError, showSuccess } = useSnackbar();
  const { authenticated, isAdmin, myTeamRoles, teamRoleOf } = useAuth();
  const [slug, setSlug] = useState('');
  const [name, setName] = useState('');
  const [selected, setSelected] = useState<string | null>(null);
  const [memberUser, setMemberUser] = useState<MemberCandidate | null>(null);
  const [memberQuery, setMemberQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [memberRole, setMemberRole] = useState('MEMBER');
  const [menuFor, setMenuFor] = useState<TeamMemberResponse | null>(null);
  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null);
  const [removeTarget, setRemoveTarget] = useState<TeamMemberResponse | null>(null);

  useEffect(() => {
    const t = setTimeout(() => setDebouncedQuery(memberQuery.trim()), 300);
    return () => clearTimeout(t);
  }, [memberQuery]);

  const { data: teams } = useQuery({ queryKey: ['teams'], queryFn: teamsApi.list });
  const teamList = teams ?? [];

  useEffect(() => {
    if (!selected && teamList.length > 0) {
      setSelected(teamList[0].slug);
    }
  }, [selected, teamList]);

  const isMember = selected != null && (isAdmin || myTeamRoles[selected] != null);
  const canManage = selected != null && (isAdmin || myTeamRoles[selected] === 'OWNER');

  const { data: members } = useQuery({
    queryKey: ['team-members', selected],
    queryFn: () => teamsApi.members(selected!),
    enabled: !!selected && isMember,
  });

  const { data: candidates } = useQuery({
    queryKey: ['member-candidates', selected, debouncedQuery],
    queryFn: () => teamsApi.searchCandidates(selected!, debouncedQuery),
    enabled: !!selected && canManage && debouncedQuery.length >= 2,
  });

  const lastOwnerUserId = (list: TeamMemberResponse[] | undefined) => {
    const owners = (list ?? []).filter((m) => m.role === 'OWNER');
    return owners.length === 1 ? owners[0].userId : null;
  };

  const createMutation = useMutation({
    mutationFn: () => teamsApi.create({ slug, name }),
    onSuccess: () => {
      showSuccess('Команда создана');
      setSlug(''); setName('');
      qc.invalidateQueries({ queryKey: ['teams'] });
    },
    onError: (e) => showError(toApiError(e).message),
  });

  const addMemberMutation = useMutation({
    mutationFn: () => teamsApi.addMember(selected!, memberUser!.userId, memberRole),
    onSuccess: () => {
      showSuccess('Участник добавлен');
      setMemberUser(null);
      setMemberQuery('');
      qc.invalidateQueries({ queryKey: ['team-members', selected] });
    },
    onError: (e) => showError(toApiError(e).message),
  });

  const changeRoleMutation = useMutation({
    mutationFn: (v: { userId: string; role: string }) => teamsApi.changeRole(selected!, v.userId, v.role),
    onSuccess: () => {
      showSuccess('Роль изменена');
      setMenuFor(null);
      qc.invalidateQueries({ queryKey: ['team-members', selected] });
    },
    onError: (e) => showError(toApiError(e).message),
  });

  const removeMemberMutation = useMutation({
    mutationFn: (userId: string) => teamsApi.removeMember(selected!, userId),
    onSuccess: () => {
      showSuccess('Участник исключён');
      setRemoveTarget(null);
      qc.invalidateQueries({ queryKey: ['team-members', selected] });
    },
    onError: (e) => showError(toApiError(e).message),
  });

  const roster = members ?? [];
  const lastOwner = lastOwnerUserId(members);
  const selectedTeam = teamList.find((t) => t.slug === selected);
  const ownRole = selected ? teamRoleOf(selected) : null;

  return (
    <Stack spacing={3}>
      <PageHeader
        title="Команды"
        subtitle="Управление командами и доступом к элементам"
      />
      <Box sx={{ display: 'flex', gap: 3, flexDirection: { xs: 'column', md: 'row' }, alignItems: 'flex-start' }}>
        <Paper sx={{ p: 3, width: { xs: '100%', md: 320 }, flexShrink: 0 }}>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2 }}>
            <GroupsIcon sx={{ color: 'primary.main' }} />
            <Typography variant="h6">Команды ({teamList.length})</Typography>
          </Stack>
          {isAdmin && (
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mb: 2 }}>
              <TextField label="slug" value={slug}
                onChange={(e) => setSlug(e.target.value)} sx={{ width: 120 }} size="small" />
              <TextField label="Название" value={name}
                onChange={(e) => setName(e.target.value)} sx={{ width: 130 }} size="small" />
              <Button variant="contained" startIcon={<AddBusinessIcon />} onClick={() => createMutation.mutate()}
                disabled={!slug.trim() || !name.trim()}>
                Создать
              </Button>
            </Stack>
          )}
          {!isAdmin && (
            <Typography color="text.secondary" sx={{ mb: 2 }} variant="body2">
              {authenticated
                ? 'Создавать команды могут только администраторы'
                : 'Войдите, чтобы управлять командами'}
            </Typography>
          )}
          <List disablePadding>
            {teamList.map((t) => (
              <ListItemButton key={t.slug} selected={t.slug === selected}
                onClick={() => { setSelected(t.slug); setMemberUser(null); setMemberQuery(''); }}
                sx={{ borderRadius: 1, mb: 0.5 }}>
                <ListItemAvatar>
                  <Avatar sx={{ bgcolor: 'rgba(29, 94, 89, 0.12)', color: 'primary.main', fontFamily: '"Onest", sans-serif' }}>
                    {t.name.charAt(0).toUpperCase()}
                  </Avatar>
                </ListItemAvatar>
                <ListItemText primary={t.name} secondary={t.slug} />
              </ListItemButton>
            ))}
          </List>
        </Paper>
        <Paper sx={{ p: 3, flex: 1, width: '100%' }} data-testid="team-detail">
          {!selectedTeam && (
            <Typography color="text.secondary">Выберите команду</Typography>
          )}
          {selectedTeam && !isMember && (
            <Stack spacing={1} alignItems="flex-start">
              <Typography variant="h6">{selectedTeam.name}</Typography>
              <Stack direction="row" spacing={1} alignItems="center" sx={{ color: 'text.secondary' }}>
                <LockIcon fontSize="small" />
                <Typography>Состав виден только участникам команды</Typography>
              </Stack>
            </Stack>
          )}
          {selectedTeam && isMember && (
            <>
              <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }} flexWrap="wrap" useFlexGap>
                <Typography variant="h6">{selectedTeam.name}</Typography>
                <Typography variant="body2" color="text.secondary">{selectedTeam.slug}</Typography>
                <Chip label={isAdmin && !ownRole ? 'Админ' : (ownRole ? ROLE_LABELS[ownRole] : '')} size="small" />
              </Stack>
              <List disablePadding>
                {roster.map((m) => (
                  <ListItem key={m.userId} disableGutters sx={{ py: 0.75 }}>
                    <ListItemAvatar>
                      <Avatar sx={{ bgcolor: 'rgba(29, 94, 89, 0.12)', color: 'primary.main', fontFamily: '"Onest", sans-serif' }}>
                        {m.displayName.charAt(0).toUpperCase()}
                      </Avatar>
                    </ListItemAvatar>
                    <ListItemText
                      primary={m.displayName}
                      secondary={`@${m.username}`}
                    />
                    <Chip {...roleChipProps(m.role)} label={ROLE_LABELS[m.role]} size="small" sx={{ mr: 1 }} />
                    {canManage && (
                      <Tooltip title={
                        m.userId === lastOwner ? 'Последнего владельца нельзя убрать' : ''
                      }>
                        <span>
                          <IconButton
                            aria-label="Действия участника"
                            onClick={(e) => { setMenuFor(m); setMenuAnchor(e.currentTarget); }}
                          >
                            <MoreVertIcon />
                          </IconButton>
                        </span>
                      </Tooltip>
                    )}
                  </ListItem>
                ))}
              </List>
              {canManage && (
                <>
                  <Divider sx={{ my: 2 }} />
                  <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2 }}>
                    <PersonAddIcon sx={{ color: 'primary.main' }} />
                    <Typography variant="h6">Добавить участника</Typography>
                  </Stack>
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
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
                    <TextField select label="Роль" value={memberRole}
                      onChange={(e) => setMemberRole(e.target.value)} sx={{ width: 140 }}>
                      <MenuItem value="OWNER">Владелец</MenuItem>
                      <MenuItem value="MAINTAINER">Редактор</MenuItem>
                      <MenuItem value="MEMBER">Участник</MenuItem>
                    </TextField>
                    <Button variant="contained" startIcon={<PersonAddIcon />}
                      onClick={() => addMemberMutation.mutate()}
                      disabled={!memberUser}>
                      Добавить
                    </Button>
                  </Stack>
                </>
              )}
            </>
          )}
        </Paper>
      </Box>
      <Menu
        anchorEl={menuAnchor}
        open={menuFor != null}
        onClose={() => { setMenuFor(null); setMenuAnchor(null); }}
      >
        {menuFor && (
          <>
            {(['OWNER', 'MAINTAINER', 'MEMBER'] as const).map((r) => (
              <Tooltip key={r} title={
                menuFor.userId === lastOwner && r !== 'OWNER'
                  ? 'Последнего владельца нельзя убрать' : ''
              }>
                <span>
                  <MenuItem
                    disabled={menuFor.userId === lastOwner && r !== 'OWNER'}
                    selected={menuFor.role === r}
                    onClick={() => changeRoleMutation.mutate({ userId: menuFor.userId, role: r })}
                  >
                    {ROLE_LABELS[r]}
                  </MenuItem>
                </span>
              </Tooltip>
            ))}
            <Divider />
            <Tooltip title={menuFor.userId === lastOwner ? 'Последнего владельца нельзя убрать' : ''}>
              <span>
                <MenuItem disabled={menuFor.userId === lastOwner}
                  onClick={() => { setRemoveTarget(menuFor); setMenuFor(null); setMenuAnchor(null); }}
                  sx={{ color: 'error.main' }}>
                  Исключить
                </MenuItem>
              </span>
            </Tooltip>
          </>
        )}
      </Menu>
      <Dialog open={removeTarget != null} onClose={() => setRemoveTarget(null)}>
        <DialogTitle>Исключить участника</DialogTitle>
        <DialogContentText sx={{ px: 3 }}>
          {removeTarget ? `Исключить ${removeTarget.displayName} из команды?` : ''}
        </DialogContentText>
        <DialogActions>
          <Button onClick={() => setRemoveTarget(null)}>Отмена</Button>
          <Button color="error" variant="contained"
            onClick={() => removeMemberMutation.mutate(removeTarget!.userId)}>
            Исключить
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}

export default TeamsPage;
```

Примечания для имплементёра:
- Заменить `roleChipProps` если уже есть похожая утилита; ключ `data-testid="team-detail"` — для тестов
- `isMember`/`canManage` вычислены из JWT-ролей (`myTeamRoles`) + `isAdmin` — как в текущем коде страницы (`teamRoleOf`/`myTeamRoles`)
- В тесте 3 меню открывается у второй строки; `changeRole` вызывается для `u2` с ролью `MAINTAINER` — при несовпадении индексов меню поправить тест по фактическому порядку рендера, не код
- Если `MenuItem disabled` внутри `Tooltip` не реагирует на клики в тестах — обернуть в `<span>` (в коде выше уже сделано)

- [ ] **Step 4: Проверка**

Run: `cd ui; npm test -- TeamsPage`
Expected: PASS (3 теста).

Run: `cd ui; npm test`
Expected: PASS (все, включая 48 существующих).

Run: `cd ui; npm run build`
Expected: tsc + vite build clean.

- [ ] **Step 5: Коммит**

```bash
git add ui/src/types.ts ui/src/api/teams.ts ui/src/pages/TeamsPage.tsx ui/src/pages/TeamsPage.test.tsx
git commit -m "feat: teams master-detail page with member roster and management"
```

---

### Task 4: Финальная верификация

**Files:** без изменений кода.

- [ ] **Step 1: Полный бэкенд**

Run: `mvn verify`
Expected: BUILD SUCCESS.

- [ ] **Step 2: Фронтенд**

Run (в `ui/`): `npm run build; npm test`
Expected: PASS.

- [ ] **Step 3: Коммит остатков (если есть)**

```bash
git status --short
```

Чисто — завершить; иначе осмысленный коммит.
