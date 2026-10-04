# Element Without Team Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make element team optional end-to-end: elements may have no team (personal), created by any authenticated user with PUBLIC visibility; versions published by author or admin.

**Architecture:** Postgres migration drops NOT NULL on `elements.team_id`; domain/service layers get null-safe team handling and a personal-publish rule; REST DTO accepts empty team; UI team select becomes optional/clearable with dependent visibility.

**Tech Stack:** Spring Boot 3 (Flyway, JPA, JUnit/Mockito), React 18 + MUI 6 + vitest.

**Spec:** `docs/superpowers/specs/2026-10-04-element-without-team-design.md`

## Global Constraints

- Build/test from repo root: `mvn -q test` (unit), full: `mvn -q verify`; UI from `ui/`: `npx vitest run`, `npm run build`.
- No code comments in new code.
- Follow existing patterns: Lombok builders, record DTOs, mock-based unit tests (see ElementUseCaseTest / VersionUseCaseTest).
- 422 for `TEAM` visibility without team (UnprocessableException), consistent with existing enum validation mapping.
- Do not change team-based access for elements that HAVE a team.

## File Structure

- Create: `src/main/resources/db/migration/V4__element_without_team.sql`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/entity/JpaElement.java`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/mapper/TeamJpaMapper.java`
- Modify: `src/main/java/com/skillhub/adapters/in/rest/dto/CreateElementRequest.java`
- Modify: `src/main/java/com/skillhub/application/service/ElementUseCase.java`
- Modify: `src/main/java/com/skillhub/domain/service/AccessService.java`
- Modify: `src/main/java/com/skillhub/application/service/VersionUseCase.java`
- Modify: `src/main/java/com/skillhub/adapters/in/rest/dto/ElementResponse.java`
- Test: `src/test/java/com/skillhub/application/service/ElementUseCaseTest.java`
- Test: `src/test/java/com/skillhub/application/service/VersionUseCaseTest.java`
- Modify: `ui/src/types.ts`, `ui/src/api/elements.ts`
- Modify: `ui/src/pages/UploadPage.tsx`, `ui/src/pages/UploadPage.test.tsx`
- Modify: `ui/src/components/ElementCard.tsx`, `ui/src/pages/ElementPage.tsx`

---

### Task 1: Backend — nullable team

**Files:**
- Create: `src/main/resources/db/migration/V4__element_without_team.sql`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/entity/JpaElement.java:21`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/mapper/TeamJpaMapper.java`
- Modify: `src/main/java/com/skillhub/adapters/in/rest/dto/CreateElementRequest.java:13`
- Modify: `src/main/java/com/skillhub/application/service/ElementUseCase.java:41-67`
- Modify: `src/main/java/com/skillhub/domain/service/AccessService.java`
- Modify: `src/main/java/com/skillhub/application/service/VersionUseCase.java:58-75`
- Modify: `src/main/java/com/skillhub/adapters/in/rest/dto/ElementResponse.java:19`
- Test: `src/test/java/com/skillhub/application/service/ElementUseCaseTest.java`
- Test: `src/test/java/com/skillhub/application/service/VersionUseCaseTest.java`

**Interfaces:**
- Consumes: existing `ElementUseCase.CreateCommand(slug, type, name, description, teamSlug, categorySlug, tags, visibility)` — `teamSlug` becomes nullable; existing `AccessService.canPublish`.
- Produces: `AccessService.canPublishPersonal(Element, User): boolean` (used by VersionUseCase); `ElementResponse.team` nullable; REST `POST /api/elements` accepts missing/null `team`.

- [ ] **Step 1: Write failing tests**

In `src/test/java/com/skillhub/application/service/ElementUseCaseTest.java` add imports `com.skillhub.core.exception.UnprocessableException`, `org.mockito.Mockito.verify`, `org.mockito.Mockito.never`, `org.mockito.ArgumentMatchers.anyString`, and two tests:

```java
    @Test
    void createWithoutTeamSucceeds() {
        ElementUseCase.CreateCommand solo = new ElementUseCase.CreateCommand(
            "solo", ElementType.SCRIPT, "Solo", "d", null, null, new String[0], Visibility.PUBLIC);
        Element created = useCase.create(solo, owner);
        assertThat(created.getTeam()).isNull();
        verify(teams, never()).findBySlug(any());
    }

    @Test
    void teamVisibilityWithoutTeamIsUnprocessable() {
        ElementUseCase.CreateCommand solo = new ElementUseCase.CreateCommand(
            "solo", ElementType.SCRIPT, "Solo", "d", null, null, new String[0], Visibility.TEAM);
        assertThatThrownBy(() -> useCase.create(solo, owner))
            .isInstanceOf(UnprocessableException.class);
    }
```

In `src/test/java/com/skillhub/application/service/VersionUseCaseTest.java` add:

```java
    @Test
    void authorPublishesTeamLessElement() {
        Element personal = Element.builder().id(UUID.randomUUID()).slug("my-skill")
            .type(ElementType.SKILL).name("my-skill").description("").team(null)
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(elements.findBySlug("my-skill")).thenReturn(Optional.of(personal));
        ElementVersion published = useCase.publish("my-skill", zip("1.0.0"), "init", owner);
        assertThat(published.getS3_key()).isEqualTo("personal/my-skill/1.0.0.zip");
    }

    @Test
    void nonAuthorCannotPublishTeamLessElement() {
        User stranger = User.builder().id(UUID.randomUUID()).ssoSubject("s2").email("e2")
            .displayName("Stranger").admin(false).createdAt(Instant.now()).build();
        Element personal = Element.builder().id(UUID.randomUUID()).slug("my-skill")
            .type(ElementType.SKILL).name("my-skill").description("").team(null)
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(elements.findBySlug("my-skill")).thenReturn(Optional.of(personal));
        assertThatThrownBy(() -> useCase.publish("my-skill", zip("1.0.0"), "init", stranger))
            .isInstanceOf(ForbiddenException.class);
    }
```

(`ForbiddenException` is already imported in that test file.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -Dtest='ElementUseCaseTest,VersionUseCaseTest'`
Expected: FAIL (create throws NotFoundException for null team; version publish throws ForbiddenException via canPublish(null,...) NPE or failure)

- [ ] **Step 3: Implement**

`src/main/resources/db/migration/V4__element_without_team.sql`:

```sql
ALTER TABLE elements ALTER COLUMN team_id DROP NOT NULL;
```

`JpaElement.java` line 21 — change:

```java
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "team_id")
    private JpaTeam team;
```

`TeamJpaMapper.java` — add null guards at the top of both methods:

```java
    public static Team toDomain(JpaTeam e) {
        if (e == null) return null;
        return Team.builder()
            .id(e.getId()).slug(e.getSlug()).name(e.getName()).createdAt(e.getCreatedAt())
            .build();
    }

    public static JpaTeam toEntity(Team d) {
        if (d == null) return null;
        return JpaTeam.builder()
            .id(d.getId()).slug(d.getSlug()).name(d.getName()).createdAt(d.getCreatedAt())
            .build();
    }
```

`CreateElementRequest.java` — remove `@NotBlank` from `team`:

```java
    String team,
```

`ElementUseCase.java` — replace the team-resolution block in `create` (lines 42-47) with:

```java
        if (cmd.teamSlug() == null && cmd.visibility() == Visibility.TEAM) {
            throw new UnprocessableException("TEAM visibility requires a team");
        }
        Team team = null;
        if (cmd.teamSlug() != null) {
            team = teams.findBySlug(cmd.teamSlug())
                .orElseThrow(() -> new NotFoundException("Team not found: " + cmd.teamSlug()));
            if (!access.canPublish(team, author)) {
                throw new ForbiddenException(
                    "Only OWNER/MAINTAINER can publish to team " + team.getSlug());
            }
        }
```

Add import `com.skillhub.core.exception.UnprocessableException`.

`AccessService.java` — add method:

```java
    public boolean canPublishPersonal(Element element, User user) {
        if (user == null) {
            return false;
        }
        return user.isAdmin() || (element.getAuthor() != null
            && user.getId().equals(element.getAuthor().getId()));
    }
```

`VersionUseCase.java` — replace lines 62-64 (the access check) and lines 73-74 (s3Key) with:

```java
        if (element.getTeam() != null && !access.canPublish(element.getTeam(), publisher)) {
            throw new ForbiddenException("Only OWNER/MAINTAINER can publish to this team");
        }
```

and after `ArchiveInfo info = archiveService.inspect(zipBytes);` and the version-conflict check, compute:

```java
        String s3Key = (element.getTeam() == null ? "personal/" + element.getSlug()
            : element.getTeam().getSlug() + "/" + element.getSlug())
            + "/" + info.manifestVersion() + ".zip";
```

For team-less elements, add at the top of `publish` (right after the element lookup) the personal check:

```java
        if (element.getTeam() == null && !access.canPublishPersonal(element, publisher)) {
            throw new ForbiddenException("Only the author can publish versions of a personal element");
        }
```

`ElementResponse.java` line 19 — change:

```java
            e.getTeam() == null ? null : e.getTeam().getSlug(),
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q test`
Expected: PASS (all unit tests, including the 4 new ones)

Run: `mvn -q verify`
Expected: PASS (ITs + Flyway migration applies cleanly)

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V4__element_without_team.sql src/main/java/com/skillhub src/test/java/com/skillhub
git commit -m "feat: allow elements without a team (personal elements)"
```

---

### Task 2: UI — optional clearable team

**Files:**
- Modify: `ui/src/types.ts:8`
- Modify: `ui/src/api/elements.ts:11-16`
- Modify: `ui/src/pages/UploadPage.tsx`
- Test: `ui/src/pages/UploadPage.test.tsx`
- Modify: `ui/src/components/ElementCard.tsx:50`
- Modify: `ui/src/pages/ElementPage.tsx:47`

**Interfaces:**
- Consumes: REST `POST /api/elements` with nullable `team` (Task 1); `ElementResponse.team` now `string | null`.
- Produces: unchanged component interfaces.

- [ ] **Step 1: Write failing tests**

In `ui/src/pages/UploadPage.test.tsx` add three tests (helpers `fillForm` and mocks already exist there):

```tsx
test('team can be cleared after selection', async () => {
  renderPage();
  await userEvent.click(await screen.findByLabelText('Команда'));
  await userEvent.click(await screen.findByRole('option', { name: 'Core Team' }));
  await userEvent.click(screen.getByLabelText('Команда'));
  await userEvent.click(await screen.findByRole('option', { name: 'Без команды' }));
  await userEvent.type(await screen.findByLabelText('Название'), 'Solo');
  await userEvent.click(screen.getByLabelText('Тип'));
  await userEvent.click(await screen.findByRole('option', { name: 'SCRIPT' }));
  await userEvent.upload(
    screen.getByTestId('version-file'),
    new File(['data'], 'element.zip', { type: 'application/zip' })
  );
  await userEvent.click(screen.getByRole('button', { name: /Опубликовать/i }));
  await waitFor(() => expect(createMock).toHaveBeenCalled());
  expect(createMock.mock.calls[0][0].team).toBeUndefined();
  expect(createMock.mock.calls[0][0].visibility).toBe('PUBLIC');
});

test('TEAM visibility is disabled without a team', async () => {
  renderPage();
  await userEvent.click(await screen.findByLabelText('Видимость'));
  expect(await screen.findByRole('option', { name: /TEAM — только команде/ }))
    .toHaveAttribute('aria-disabled', 'true');
});

test('selecting a team enables TEAM visibility', async () => {
  renderPage();
  await userEvent.click(await screen.findByLabelText('Команда'));
  await userEvent.click(await screen.findByRole('option', { name: 'Core Team' }));
  await userEvent.click(screen.getByLabelText('Видимость'));
  expect(await screen.findByRole('option', { name: /TEAM — только команде/ }))
    .not.toHaveAttribute('aria-disabled', 'true');
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npx vitest run src/pages/UploadPage.test.tsx`
Expected: FAIL (no «Без команды» option; TEAM not disabled; create called with team: 'core')

- [ ] **Step 3: Implement**

`ui/src/types.ts` line 8 — change:

```ts
  team: string | null;
```

`ui/src/api/elements.ts` — change the create signature body type:

```ts
  async create(body: {
    slug: string; type: string; name: string; description?: string;
    team?: string; category?: string; tags?: string[]; visibility: string;
  }): Promise<ElementResponse> {
```

`ui/src/pages/UploadPage.tsx`:

1. Team MenuItem list — add the empty option first:

```tsx
            <MenuItem value="">Без команды</MenuItem>
            {(meInfo?.teams ?? []).map((t) => (
              <MenuItem key={t.slug} value={t.slug}>{t.name}</MenuItem>
            ))}
```

2. Team onChange — clear dependent state (replace the existing onChange):

```tsx
            onChange={(e) => {
              const next = e.target.value;
              setTeam(next);
              if (!next) setVisibility('PUBLIC');
            }}
```

3. Visibility select — make `required` conditional and disable TEAM without team (replace the two MenuItems):

```tsx
          <TextField
            select
            label="Видимость"
            value={visibility}
            required={!!team}
            sx={{ minWidth: 220 }}
            onChange={(e) => setVisibility(e.target.value as 'PUBLIC' | 'TEAM')}
          >
            <MenuItem value="PUBLIC">PUBLIC — доступен всем</MenuItem>
            <MenuItem value="TEAM" disabled={!team}>TEAM — только команде</MenuItem>
          </TextField>
```

4. `canSubmit` — team is no longer required:

```tsx
  const canSubmit =
    !!name.trim() && SLUG_PATTERN.test(slug) && !!type
    && (!team || !!visibility) && !!file;
```

5. Submit payload — team omitted when empty (in `mutationFn`):

```tsx
        await elementsApi.create({
          slug,
          type,
          name,
          description: description || undefined,
          team: team || undefined,
          category: category || undefined,
          tags,
          visibility,
        });
```

`ui/src/components/ElementCard.tsx` line 50 — replace:

```tsx
            <Typography variant="caption">{element.team ?? 'Личный'}</Typography>
```

`ui/src/pages/ElementPage.tsx` line 47 — replace:

```tsx
  const canPublish =
    isAdmin
    || (element != null && element.team == null)
    || ['OWNER', 'MAINTAINER'].includes(teamRoleOf(element?.team ?? '') ?? '');
```

(the existing `element?.team ?? ''` lookup becomes conditional on team being present).

- [ ] **Step 4: Run tests to verify they pass**

Run: `npx vitest run`
Expected: PASS (all UI tests including 8 UploadPage tests)

Run: `npm run build`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ui/src
git commit -m "feat: allow uploading personal elements without a team"
```
