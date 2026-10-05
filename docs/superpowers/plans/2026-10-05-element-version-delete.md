# Удаление элементов и версий (мягкое) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Добавить мягкое удаление элементов и версий с правами «админ / OWNER команды / автор личного элемента», блокировкой по связям с паками и confirm-диалогом в UI.

**Architecture:** Flyway-миграция добавляет `deleted_at` в `elements` и `element_versions`; удаление — простановка `deleted_at`, строки остаются в БД. Все читающие выборки (элементы, версии, поиск, latest) исключают удалённые. Use-case слои `ElementUseCase.delete` и `VersionUseCase.deleteVersion` проверяют права через новый `AccessService.canDelete` и блокируют удаление при связях с паками (409). REST отдаёт `DELETE`-эндпоинты; UI добавляет кнопки удаления и confirm-диалог.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Spring Data JPA, Flyway, PostgreSQL 16 (Testcontainers), React 18 + TypeScript + MUI + TanStack Query.

**Спецификация:** `docs/superpowers/specs/2026-10-05-element-version-delete-design.md`

## Global Constraints

- Гексагональная архитектура: `domain/model`, `domain/port`, `application/service`, `adapters/in|out`; не ломать слои.
- Мягкое удаление: `deleted_at TIMESTAMPTZ NULL`; строки из БД не удаляются, объекты S3 не трогаем.
- Права удаления: админ всегда; элемент команды — только OWNER (MAINTAINER НЕ может); личный элемент (`team == null`) — только автор.
- `getBySlug` удалённого элемента → 404 для всех (включая админа). `listVersions`/`getVersion`/`findLatestPublished` не возвращают удалённые версии.
- Колонка `element_versions.s3_key` входит в `findAllS3Keys()` как есть (включая удалённые) — иначе reconciller удалит объекты S3 и сломает обратимость.
- Публикация версии с уже удалённым номером по-прежнему конфликтует: `UNIQUE (element_id, version)`; проверка дублей в `publish` использует поиск без фильтра `deleted_at`.
- UI-тексты на русском; стиль существующих MUI-компонентов (`size="small"`, confirm-`Dialog`).
- Тесты: `mvn test` в корне; UI: `npm run test` и `npm run build` в `ui/`.
- Не коммитить файлы, не относящиеся к задаче.

---

### Task 1: Миграция V7 и поле `deleted_at` в домене, сущностях, мапперах

**Files:**
- Create: `src/main/resources/db/migration/V7__soft_delete.sql`
- Modify: `src/main/java/com/skillhub/domain/model/Element.java`
- Modify: `src/main/java/com/skillhub/domain/model/ElementVersion.java`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/entity/JpaElement.java`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/entity/JpaElementVersion.java`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/mapper/ElementJpaMapper.java`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/mapper/ElementVersionJpaMapper.java`

**Interfaces:**
- Consumes: схема `elements`, `element_versions` из `V1__init.sql`.
- Produces (используются Task 2, 5, 6):
  - `Element.getDeletedAt() : Instant` / `setDeletedAt(Instant)` (Lombok).
  - `ElementVersion.getDeletedAt() : Instant` / `setDeletedAt(Instant)` (Lombok).
  - Колонки `elements.deleted_at`, `element_versions.deleted_at`.

- [ ] **Step 1: Создать файл миграции**

```sql
ALTER TABLE elements ADD COLUMN deleted_at TIMESTAMPTZ NULL;
ALTER TABLE element_versions ADD COLUMN deleted_at TIMESTAMPTZ NULL;
```

- [ ] **Step 2: Добавить поле в доменные модели**

В `src/main/java/com/skillhub/domain/model/Element.java` добавить поле последним в классе (`Instant` уже импортирован):

```java
    private Instant deletedAt;
```

В `src/main/java/com/skillhub/domain/model/ElementVersion.java` добавить поле последним в классе:

```java
    private Instant deletedAt;
```

- [ ] **Step 3: Добавить колонку в JPA-сущности**

В `JpaElement.java` после поля `updatedAt`:

```java
    @Column(name = "deleted_at") private Instant deletedAt;
```

В `JpaElementVersion.java` после поля `publishedAt`:

```java
    @Column(name = "deleted_at") private Instant deletedAt;
```

- [ ] **Step 4: Прокинуть поле в мапперах**

В `ElementJpaMapper.toDomain` добавить строку (перед `.build()`):

```java
            .deletedAt(e.getDeletedAt())
```

В `ElementJpaMapper.toEntity` добавить строку (перед `.build()`):

```java
            .deletedAt(d.getDeletedAt())
```

В `ElementVersionJpaMapper.toDomain` добавить строку (перед `.build()`):

```java
            .deletedAt(e.getDeletedAt())
```

В `ElementVersionJpaMapper.toEntity` добавить строку (перед `.build()`):

```java
            .deletedAt(d.getDeletedAt())
```

- [ ] **Step 5: Запустить тесты**

Run: `mvn test -Dtest=JpaElementVersionRepositoryAdapterIT,JpaElementRepositoryAdapterIT`
Expected: BUILD SUCCESS. Flyway применит V7, `ddl-auto: validate` подтвердит соответствие колонок.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V7__soft_delete.sql src/main/java/com/skillhub/domain/model/Element.java src/main/java/com/skillhub/domain/model/ElementVersion.java src/main/java/com/skillhub/adapters/out/jpa/entity/JpaElement.java src/main/java/com/skillhub/adapters/out/jpa/entity/JpaElementVersion.java src/main/java/com/skillhub/adapters/out/jpa/mapper/ElementJpaMapper.java src/main/java/com/skillhub/adapters/out/jpa/mapper/ElementVersionJpaMapper.java
git commit -m "feat: add deleted_at column and domain field for soft delete"
```

---

### Task 2: Скрытие удалённых версий и элементов в читающих выборках

**Files:**
- Modify: `src/main/java/com/skillhub/domain/port/ElementVersionRepositoryPort.java`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/repository/JpaElementVersionRepository.java`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/JpaElementVersionRepositoryAdapter.java`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/SearchAdapter.java`
- Modify: `src/main/java/com/skillhub/application/service/ElementUseCase.java`

**Interfaces:**
- Consumes: `deleted_at` из Task 1.
- Produces (используются Task 6):
  - `ElementVersionRepositoryPort.findActiveByElementIdAndVersion(UUID elementId, String version) : Optional<ElementVersion>` — исключает удалённые.
  - `ElementVersionRepositoryPort.findLatestPublished(UUID elementId)` — теперь исключает удалённые.
  - `ElementVersionRepositoryPort.findAllByElementIdOrderByCreatedAtDesc(UUID elementId)` — теперь исключает удалённые.
  - `ElementVersionRepositoryPort.findByElementIdAndVersion(UUID, String)` — без изменений (включает удалённые; нужен для проверки дублей при публикации).

- [ ] **Step 1: Написать падающий тест на скрытие удалённой версии**

Добавить в `src/test/java/com/skillhub/application/service/VersionUseCaseTest.java`:

```java
    @Test
    void deletedVersionIsNotReturnedByGetVersion() {
        when(versions.findActiveByElementIdAndVersion(element.getId(), "1.0.0"))
            .thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.getVersion("my-skill", "1.0.0", owner))
            .isInstanceOf(com.skillhub.core.exception.NotFoundException.class);
    }
```

И добавить в `src/test/java/com/skillhub/application/service/ElementUseCaseTest.java`:

```java
    @Test
    void deletedElementIsNotFound() {
        Element deleted = Element.builder().id(UUID.randomUUID()).slug("gone")
            .type(ElementType.SKILL).name("gone").description("").team(team)
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now())
            .deletedAt(Instant.parse("2026-02-01T00:00:00Z")).build();
        when(elements.findBySlug("gone")).thenReturn(Optional.of(deleted));
        assertThatThrownBy(() -> useCase.getBySlug("gone", owner))
            .isInstanceOf(NotFoundException.class);
    }
```

- [ ] **Step 2: Запустить тесты, убедиться что падают**

Run: `mvn test -Dtest=VersionUseCaseTest,ElementUseCaseTest`
Expected: FAIL — компиляция падает: `findActiveByElementIdAndVersion` не существует (для VersionUseCaseTest); `deletedElementIsNotFound` падает с `ForbiddenException`/успехом, т.к. фильтра нет.

- [ ] **Step 3: Обновить порт и JPA-репозиторий версий**

В `ElementVersionRepositoryPort.java` добавить метод после `findByElementIdAndVersion`:

```java
    Optional<ElementVersion> findActiveByElementIdAndVersion(UUID elementId, String version);
```

В `JpaElementVersionRepository.java` заменить интерфейсные методы на:

```java
    Optional<JpaElementVersion> findByElementIdAndVersion(UUID elementId, String version);
    Optional<JpaElementVersion> findByElementIdAndVersionAndDeletedAtIsNull(
        UUID elementId, String version);
    Optional<JpaElementVersion> findFirstByElementIdAndStatusAndDeletedAtIsNullOrderByPublishedAtDesc(
        UUID elementId, String status);
    List<JpaElementVersion> findAllByElementIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID elementId);

    @Query("select v.s3Key from JpaElementVersion v")
    List<String> findAllS3Keys();
```

(Метод `findAllS3Keys` оставляем без фильтра — включая удалённые.)

- [ ] **Step 4: Обновить адаптер версий**

В `JpaElementVersionRepositoryAdapter.java` добавить реализацию и переписать существующие:

```java
    @Override
    @Transactional(readOnly = true)
    public Optional<ElementVersion> findByElementIdAndVersion(UUID elementId, String version) {
        return jpa.findByElementIdAndVersion(elementId, version)
            .map(ElementVersionJpaMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ElementVersion> findActiveByElementIdAndVersion(UUID elementId, String version) {
        return jpa.findByElementIdAndVersionAndDeletedAtIsNull(elementId, version)
            .map(ElementVersionJpaMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ElementVersion> findLatestPublished(UUID elementId) {
        return jpa.findFirstByElementIdAndStatusAndDeletedAtIsNullOrderByPublishedAtDesc(
                elementId, VersionStatus.PUBLISHED.name())
            .map(ElementVersionJpaMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ElementVersion> findAllByElementIdOrderByCreatedAtDesc(UUID elementId) {
        return jpa.findAllByElementIdAndDeletedAtIsNullOrderByCreatedAtDesc(elementId).stream()
            .map(ElementVersionJpaMapper::toDomain)
            .toList();
    }
```

- [ ] **Step 5: `VersionUseCase.getVersion` читает только активные версии**

В `src/main/java/com/skillhub/application/service/VersionUseCase.java` в методе `getVersion` заменить:

```java
            : versions.findByElementIdAndVersion(element.getId(), version)
```

на:

```java
            : versions.findActiveByElementIdAndVersion(element.getId(), version)
```

(Метод `publish` не меняем — проверка дублей остаётся по `findByElementIdAndVersion`, включая удалённые.)

- [ ] **Step 6: Скрыть удалённые элементы в `ElementUseCase`**

В `ElementUseCase.getBySlug` после `findBySlug` добавить проверку:

```java
        if (element.getDeletedAt() != null) {
            throw new NotFoundException("Element not found: " + slug);
        }
```

В `ElementUseCase.listVisible` добавить фильтр:

```java
            .filter(e -> e.getDeletedAt() == null)
```

Полный метод после правки:

```java
    @Transactional(readOnly = true)
    public List<Element> listVisible(User viewer) {
        return elements.findAll().stream()
            .filter(e -> e.getDeletedAt() == null)
            .filter(e -> access.canRead(e, viewer))
            .toList();
    }
```

- [ ] **Step 7: Скрыть удалённые элементы в поиске**

В `SearchAdapter.BASE_WHERE` добавить строку в начало условий:

```java
        e.deleted_at IS NULL
        AND (e.visibility = 'PUBLIC'
         OR :admin = true
         OR e.team_id IN (SELECT tm.team_id FROM team_members tm WHERE tm.user_id = :userId))
        AND (CAST(:category AS text) IS NULL OR e.category_id = (SELECT c.id FROM categories c WHERE c.slug = :category))
        AND (:q = '' OR e.search_vector @@ plainto_tsquery('russian', :q)
             OR e.name ILIKE ('%' || :q || '%'))
        """;
```

- [ ] **Step 8: Запустить тесты**

Run: `mvn test -Dtest=VersionUseCaseTest,ElementUseCaseTest,SearchAdapterIT`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/skillhub/domain/port/ElementVersionRepositoryPort.java src/main/java/com/skillhub/adapters/out/jpa/repository/JpaElementVersionRepository.java src/main/java/com/skillhub/adapters/out/jpa/JpaElementVersionRepositoryAdapter.java src/main/java/com/skillhub/application/service/VersionUseCase.java src/main/java/com/skillhub/application/service/ElementUseCase.java src/main/java/com/skillhub/adapters/out/jpa/SearchAdapter.java src/test/java/com/skillhub/application/service/VersionUseCaseTest.java src/test/java/com/skillhub/application/service/ElementUseCaseTest.java
git commit -m "feat: hide soft-deleted elements and versions from reads"
```

---

### Task 3: `AccessService.canDelete`

**Files:**
- Modify: `src/main/java/com/skillhub/domain/service/AccessService.java`
- Test: `src/test/java/com/skillhub/domain/service/AccessServiceTest.java`

**Interfaces:**
- Consumes: `TeamMembershipPort.roleOf(UUID, UUID)`.
- Produces (используется Task 5, 6): `AccessService.canDelete(Element element, User user) : boolean`.

- [ ] **Step 1: Написать падающие тесты**

Добавить в `AccessServiceTest.java`:

```java
    @Test
    void onlyOwnerOrAdminCanDeleteTeamElement() {
        element.setVisibility(Visibility.PUBLIC);
        when(membership.roleOf(team.getId(), user.getId()))
            .thenReturn(Optional.of(TeamRole.MAINTAINER));
        assertThat(access.canDelete(element, user)).isFalse();

        when(membership.roleOf(team.getId(), user.getId()))
            .thenReturn(Optional.of(TeamRole.OWNER));
        assertThat(access.canDelete(element, user)).isTrue();

        when(membership.roleOf(team.getId(), user.getId())).thenReturn(Optional.empty());
        user.setAdmin(true);
        assertThat(access.canDelete(element, user)).isTrue();
    }

    @Test
    void authorCanDeletePersonalElement() {
        Element personal = Element.builder().id(UUID.randomUUID()).slug("p")
            .type(ElementType.SKILL).name("p").description("").team(null)
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(user)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        assertThat(access.canDelete(personal, user)).isTrue();

        User other = User.builder().id(UUID.randomUUID()).ssoSubject("o").email("o")
            .displayName("o").admin(false).createdAt(Instant.now()).build();
        assertThat(access.canDelete(personal, other)).isFalse();
    }

    @Test
    void nullUserCannotDelete() {
        assertThat(access.canDelete(element, null)).isFalse();
    }
```

- [ ] **Step 2: Запустить тесты, убедиться что падают**

Run: `mvn test -Dtest=AccessServiceTest`
Expected: FAIL — компиляция: `canDelete` не существует.

- [ ] **Step 3: Реализовать `canDelete`**

В `AccessService.java` добавить метод после `canPublishPersonal`:

```java
    public boolean canDelete(Element element, User user) {
        if (user == null) {
            return false;
        }
        if (user.isAdmin()) {
            return true;
        }
        if (element.getTeam() == null) {
            return element.getAuthor() != null
                && user.getId().equals(element.getAuthor().getId());
        }
        return membership.roleOf(element.getTeam().getId(), user.getId())
            .map(r -> r == TeamRole.OWNER)
            .orElse(false);
    }
```

- [ ] **Step 4: Запустить тесты**

Run: `mvn test -Dtest=AccessServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skillhub/domain/service/AccessService.java src/test/java/com/skillhub/domain/service/AccessServiceTest.java
git commit -m "feat: add canDelete access rule (admin, team OWNER, personal author)"
```

---

### Task 4: Поиск паков по элементу (`findAllByElementId`)

**Files:**
- Modify: `src/main/java/com/skillhub/domain/port/PackContentRepositoryPort.java`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/repository/JpaPackContentRepository.java`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/JpaPackContentRepositoryAdapter.java`

**Interfaces:**
- Consumes: `JpaPackContent.element` (`element_id`).
- Produces (используется Task 5, 6): `PackContentRepositoryPort.findAllByElementId(UUID elementId) : List<PackContent>` — все записи паков, где данный элемент является содержимым.

- [ ] **Step 1: Добавить метод в порт**

В `PackContentRepositoryPort.java`:

```java
    List<PackContent> findAllByElementId(UUID elementId);
```

- [ ] **Step 2: Добавить запрос в JPA-репозиторий**

В `JpaPackContentRepository.java`:

```java
    List<JpaPackContent> findAllByElementId(UUID elementId);
```

- [ ] **Step 3: Реализовать в адаптере**

В `JpaPackContentRepositoryAdapter.java`:

```java
    @Override
    @Transactional(readOnly = true)
    public List<PackContent> findAllByElementId(UUID elementId) {
        return jpa.findAllByElementId(elementId).stream()
            .map(this::toDomain)
            .toList();
    }
```

- [ ] **Step 4: Компиляция**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skillhub/domain/port/PackContentRepositoryPort.java src/main/java/com/skillhub/adapters/out/jpa/repository/JpaPackContentRepository.java src/main/java/com/skillhub/adapters/out/jpa/JpaPackContentRepositoryAdapter.java
git commit -m "feat: find pack contents by contained element"
```

---

### Task 5: `ElementUseCase.delete`

**Files:**
- Modify: `src/main/java/com/skillhub/application/service/ElementUseCase.java`
- Test: `src/test/java/com/skillhub/application/service/ElementUseCaseTest.java`

**Interfaces:**
- Consumes: `AccessService.canDelete` (Task 3), `PackContentRepositoryPort.findAllByElementId` (Task 4), `Element.setDeletedAt` (Task 1).
- Produces (используется Task 7): `ElementUseCase.delete(String slug, User user) : void`.

- [ ] **Step 1: Обновить конструктор в тесте и написать падающие тесты**

В `ElementUseCaseTest.java` добавить поле и мок `PackContentRepositoryPort`:

```java
    PackContentRepositoryPort packContents;
```

В `setUp()` добавить мок и обновить конструктор:

```java
        packContents = mock(PackContentRepositoryPort.class);
        when(packContents.findAllByElementId(any())).thenReturn(java.util.List.of());
        useCase = new ElementUseCase(elements, teams, categories,
            new AccessService(membership), clock, packContents, mock(AuditService.class));
```

Добавить тесты:

```java
    @Test
    void ownerDeletesElement() {
        Element e = Element.builder().id(UUID.randomUUID()).slug("my-skill")
            .type(ElementType.SKILL).name("my-skill").description("").team(team)
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(elements.findBySlug("my-skill")).thenReturn(Optional.of(e));
        useCase.delete("my-skill", owner);
        assertThat(e.getDeletedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        verify(elements).save(e);
    }

    @Test
    void maintainerCannotDeleteElement() {
        Element e = Element.builder().id(UUID.randomUUID()).slug("my-skill")
            .type(ElementType.SKILL).name("my-skill").description("").team(team)
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(elements.findBySlug("my-skill")).thenReturn(Optional.of(e));
        when(membership.roleOf(team.getId(), owner.getId()))
            .thenReturn(Optional.of(TeamRole.MAINTAINER));
        assertThatThrownBy(() -> useCase.delete("my-skill", owner))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void elementInPackCannotBeDeleted() {
        Element e = Element.builder().id(UUID.randomUUID()).slug("my-skill")
            .type(ElementType.SKILL).name("my-skill").description("").team(team)
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(elements.findBySlug("my-skill")).thenReturn(Optional.of(e));
        Element pack = Element.builder().id(UUID.randomUUID()).slug("the-pack")
            .type(ElementType.PACK).name("The Pack").description("")
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(packContents.findAllByElementId(e.getId())).thenReturn(java.util.List.of(
            PackContent.builder().packElement(pack).element(e).versionConstraint("latest").build()));
        assertThatThrownBy(() -> useCase.delete("my-skill", owner))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void alreadyDeletedElementIsNotFound() {
        Element e = Element.builder().id(UUID.randomUUID()).slug("my-skill")
            .type(ElementType.SKILL).name("my-skill").description("").team(team)
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now())
            .deletedAt(Instant.parse("2026-02-01T00:00:00Z")).build();
        when(elements.findBySlug("my-skill")).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> useCase.delete("my-skill", owner))
            .isInstanceOf(NotFoundException.class);
    }
```

- [ ] **Step 2: Запустить тесты, убедиться что падают**

Run: `mvn test -Dtest=ElementUseCaseTest`
Expected: FAIL — компиляция: конструктор `ElementUseCase` и метод `delete` не соответствуют.

- [ ] **Step 3: Изменить конструктор и добавить `delete`**

В `ElementUseCase.java` добавить импорты:

```java
import com.skillhub.domain.port.PackContentRepositoryPort;
import java.util.Map;
import java.util.stream.Collectors;
```

Заменить поля и конструктор:

```java
    private final ElementRepositoryPort elements;
    private final TeamRepositoryPort teams;
    private final CategoryRepositoryPort categories;
    private final AccessService access;
    private final ClockPort clock;
    private final PackContentRepositoryPort packContents;
    private final AuditService audit;

    public ElementUseCase(ElementRepositoryPort elements, TeamRepositoryPort teams,
                          CategoryRepositoryPort categories, AccessService access,
                          ClockPort clock, PackContentRepositoryPort packContents,
                          AuditService audit) {
        this.elements = elements;
        this.teams = teams;
        this.categories = categories;
        this.access = access;
        this.clock = clock;
        this.packContents = packContents;
        this.audit = audit;
    }
```

Добавить метод:

```java
    @Transactional
    public void delete(String slug, User user) {
        Element element = elements.findBySlug(slug)
            .orElseThrow(() -> new NotFoundException("Element not found: " + slug));
        if (element.getDeletedAt() != null) {
            throw new NotFoundException("Element not found: " + slug);
        }
        if (!access.canDelete(element, user)) {
            throw new ForbiddenException("Not allowed to delete element: " + slug);
        }
        var refs = packContents.findAllByElementId(element.getId());
        if (!refs.isEmpty()) {
            String packs = refs.stream()
                .map(rc -> rc.getPackElement().getSlug())
                .distinct().sorted()
                .collect(Collectors.joining(", "));
            throw new ConflictException("Element is used in packs: " + packs);
        }
        element.setDeletedAt(clock.now());
        element.setUpdatedAt(clock.now());
        elements.save(element);
        audit.log(user, "DELETE_ELEMENT", element.getId(), Map.of("slug", slug));
    }
```

- [ ] **Step 4: Запустить тесты**

Run: `mvn test -Dtest=ElementUseCaseTest,PackUseCaseTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skillhub/application/service/ElementUseCase.java src/test/java/com/skillhub/application/service/ElementUseCaseTest.java
git commit -m "feat: soft-delete element with pack-reference guard"
```

---

### Task 6: `VersionUseCase.deleteVersion`

**Files:**
- Modify: `src/main/java/com/skillhub/application/service/VersionUseCase.java`
- Test: `src/test/java/com/skillhub/application/service/VersionUseCaseTest.java`

**Interfaces:**
- Consumes: `AccessService.canDelete` (Task 3), `PackContentRepositoryPort.findAllByElementId` (Task 4), `findActiveByElementIdAndVersion` (Task 2), `ElementVersion.setDeletedAt` (Task 1).
- Produces (используется Task 7): `VersionUseCase.deleteVersion(String slug, String version, User user) : void`.

- [ ] **Step 1: Обновить конструктор в тесте и написать падающие тесты**

В `VersionUseCaseTest.java` добавить поле и мок:

```java
    PackContentRepositoryPort packContents;
```

В `setUp()` добавить:

```java
        packContents = mock(PackContentRepositoryPort.class);
        when(packContents.findAllByElementId(any())).thenReturn(java.util.List.of());
```

И обновить конструктор (добавить `packContents` последним аргументом):

```java
        useCase = new VersionUseCase(elements, versions, storage, audit,
            new AccessService(membership), new ArchiveService(200, 10), clock,
            java.time.Duration.ofMinutes(10), packContents);
```

Добавить тесты:

```java
    @Test
    void ownerDeletesVersionAndRecomputesLatest() {
        ElementVersion v2 = ElementVersion.builder().id(UUID.randomUUID()).element(element)
            .version("2.0.0").status(VersionStatus.PUBLISHED).changelog("two")
            .s3_key("platform/my-skill/2.0.0.zip").sizeBytes(1).fileIndex("{}")
            .publishedBy(owner).createdAt(Instant.now()).publishedAt(Instant.now()).build();
        ElementVersion v1 = ElementVersion.builder().id(UUID.randomUUID()).element(element)
            .version("1.0.0").status(VersionStatus.PUBLISHED).changelog("one")
            .s3_key("platform/my-skill/1.0.0.zip").sizeBytes(1).fileIndex("{}")
            .publishedBy(owner).createdAt(Instant.now()).publishedAt(Instant.now()).build();
        element.setLatestVersion("2.0.0");
        when(versions.findActiveByElementIdAndVersion(element.getId(), "2.0.0"))
            .thenReturn(Optional.of(v2));
        when(versions.findAllByElementIdOrderByCreatedAtDesc(element.getId()))
            .thenReturn(java.util.List.of(v1));
        useCase.deleteVersion("my-skill", "2.0.0", owner);
        assertThat(v2.getDeletedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(element.getLatestVersion()).isEqualTo("1.0.0");
        assertThat(element.getLatestChangelog()).isEqualTo("one");
        verify(elements).save(element);
    }

    @Test
    void deletingOnlyVersionClearsLatest() {
        ElementVersion v1 = ElementVersion.builder().id(UUID.randomUUID()).element(element)
            .version("1.0.0").status(VersionStatus.PUBLISHED).changelog("one")
            .s3_key("platform/my-skill/1.0.0.zip").sizeBytes(1).fileIndex("{}")
            .publishedBy(owner).createdAt(Instant.now()).publishedAt(Instant.now()).build();
        element.setLatestVersion("1.0.0");
        when(versions.findActiveByElementIdAndVersion(element.getId(), "1.0.0"))
            .thenReturn(Optional.of(v1));
        when(versions.findAllByElementIdOrderByCreatedAtDesc(element.getId()))
            .thenReturn(java.util.List.of());
        useCase.deleteVersion("my-skill", "1.0.0", owner);
        assertThat(element.getLatestVersion()).isNull();
        assertThat(element.getLatestChangelog()).isEqualTo("");
        verify(elements).save(element);
    }

    @Test
    void versionPinnedByPackCannotBeDeleted() {
        ElementVersion v1 = ElementVersion.builder().id(UUID.randomUUID()).element(element)
            .version("1.0.0").status(VersionStatus.PUBLISHED).changelog("one")
            .s3_key("platform/my-skill/1.0.0.zip").sizeBytes(1).fileIndex("{}")
            .publishedBy(owner).createdAt(Instant.now()).publishedAt(Instant.now()).build();
        when(versions.findActiveByElementIdAndVersion(element.getId(), "1.0.0"))
            .thenReturn(Optional.of(v1));
        Element pack = Element.builder().id(UUID.randomUUID()).slug("the-pack")
            .type(ElementType.PACK).name("The Pack").description("")
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(packContents.findAllByElementId(element.getId())).thenReturn(java.util.List.of(
            PackContent.builder().packElement(pack).element(element)
                .versionConstraint("1.0.0").build()));
        assertThatThrownBy(() -> useCase.deleteVersion("my-skill", "1.0.0", owner))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void latestConstraintDoesNotBlockVersionDelete() {
        ElementVersion v1 = ElementVersion.builder().id(UUID.randomUUID()).element(element)
            .version("1.0.0").status(VersionStatus.PUBLISHED).changelog("one")
            .s3_key("platform/my-skill/1.0.0.zip").sizeBytes(1).fileIndex("{}")
            .publishedBy(owner).createdAt(Instant.now()).publishedAt(Instant.now()).build();
        element.setLatestVersion("2.0.0");
        when(versions.findActiveByElementIdAndVersion(element.getId(), "1.0.0"))
            .thenReturn(Optional.of(v1));
        Element pack = Element.builder().id(UUID.randomUUID()).slug("the-pack")
            .type(ElementType.PACK).name("The Pack").description("")
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(packContents.findAllByElementId(element.getId())).thenReturn(java.util.List.of(
            PackContent.builder().packElement(pack).element(element)
                .versionConstraint("latest").build()));
        useCase.deleteVersion("my-skill", "1.0.0", owner);
        assertThat(v1.getDeletedAt()).isNotNull();
    }

    @Test
    void maintainerCannotDeleteVersion() {
        when(membership.roleOf(team.getId(), owner.getId()))
            .thenReturn(Optional.of(TeamRole.MAINTAINER));
        assertThatThrownBy(() -> useCase.deleteVersion("my-skill", "1.0.0", owner))
            .isInstanceOf(ForbiddenException.class);
    }
```

- [ ] **Step 2: Запустить тесты, убедиться что падают**

Run: `mvn test -Dtest=VersionUseCaseTest`
Expected: FAIL — компиляция: конструктор и метод `deleteVersion` не соответствуют.

- [ ] **Step 3: Добавить `PackContentRepositoryPort` и метод `deleteVersion`**

В `VersionUseCase.java` добавить импорт:

```java
import com.skillhub.domain.port.PackContentRepositoryPort;
```

Добавить поле и параметр конструктора (последним):

```java
    private final PackContentRepositoryPort packContents;
```

```java
                          @Value("${skillhub.storage.presign-ttl:10m}") Duration presignTtl,
                          PackContentRepositoryPort packContents) {
        this.elements = elements;
        this.versions = versions;
        this.storage = storage;
        this.audit = audit;
        this.access = access;
        this.archiveService = archiveService;
        this.clock = clock;
        this.presignTtl = presignTtl;
        this.packContents = packContents;
    }
```

Добавить метод после `publish`:

```java
    @Transactional
    public void deleteVersion(String slug, String version, User user) {
        Element element = elements.findBySlug(slug)
            .orElseThrow(() -> new NotFoundException("Element not found: " + slug));
        if (element.getDeletedAt() != null) {
            throw new NotFoundException("Element not found: " + slug);
        }
        if (!access.canDelete(element, user)) {
            throw new ForbiddenException("Not allowed to delete versions of: " + slug);
        }
        ElementVersion target = versions.findActiveByElementIdAndVersion(element.getId(), version)
            .orElseThrow(() -> new NotFoundException(
                "Version not found: " + slug + "@" + version));

        boolean pinned = packContents.findAllByElementId(element.getId()).stream()
            .anyMatch(rc -> version.equals(rc.getVersionConstraint()));
        if (pinned) {
            throw new ConflictException(
                "Version " + version + " is pinned by a pack");
        }

        target.setDeletedAt(clock.now());
        versions.save(target);

        if (version.equals(element.getLatestVersion())) {
            ElementVersion next = versions
                .findAllByElementIdOrderByCreatedAtDesc(element.getId()).stream()
                .filter(x -> x.getStatus() == VersionStatus.PUBLISHED)
                .findFirst().orElse(null);
            element.setLatestVersion(next == null ? null : next.getVersion());
            element.setLatestChangelog(next == null ? "" : next.getChangelog());
            element.setUpdatedAt(clock.now());
            elements.save(element);
        }
        audit.log(user, "DELETE_VERSION", element.getId(),
            Map.of("version", version));
    }
```

- [ ] **Step 4: Запустить тесты**

Run: `mvn test -Dtest=VersionUseCaseTest,PackUseCaseTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skillhub/application/service/VersionUseCase.java src/test/java/com/skillhub/application/service/VersionUseCaseTest.java
git commit -m "feat: soft-delete version with pack-pin guard and latest recompute"
```

---

### Task 7: REST `DELETE`-эндпоинты и DTO-поля `authorId`/`userId`

**Files:**
- Modify: `src/main/java/com/skillhub/adapters/in/rest/ElementController.java`
- Modify: `src/main/java/com/skillhub/adapters/in/rest/VersionController.java`
- Modify: `src/main/java/com/skillhub/adapters/in/rest/dto/ElementResponse.java`
- Modify: `src/main/java/com/skillhub/adapters/in/rest/MeController.java`
- Test: `src/test/java/com/skillhub/adapters/in/rest/ElementApiIT.java`
- Test: `src/test/java/com/skillhub/adapters/in/rest/VersionPublishIT.java`

**Interfaces:**
- Consumes: `ElementUseCase.delete` (Task 5), `VersionUseCase.deleteVersion` (Task 6).
- Produces (используются Task 8, 9):
  - `DELETE /api/elements/{slug}` → 204.
  - `DELETE /api/elements/{slug}/versions/{version}` → 204.
  - `ElementResponse.authorId : String` (UUID автора или null).
  - `MeResponse.userId : String` (UUID текущего пользователя).

- [ ] **Step 1: Написать падающие IT-тесты**

В `ElementApiIT.java` добавить:

```java
    @Test
    void ownerDeletesElementThenItIsGone() {
        createCategory();
        rest.exchange("/api/elements", HttpMethod.POST,
            new HttpEntity<>(skillRequest("del-skill"), jsonHeaders()), String.class);
        ResponseEntity<String> del = rest.exchange("/api/elements/del-skill", HttpMethod.DELETE,
            new HttpEntity<>(jsonHeaders()), String.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<String> got = rest.exchange("/api/elements/del-skill", HttpMethod.GET,
            new HttpEntity<>(jsonHeaders()), String.class);
        assertThat(got.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
```

В `VersionPublishIT.java` добавить:

```java
    @Test
    void ownerDeletesVersionAndListHidesIt() {
        publish("pub-skill", "3.0.0", "three");
        ResponseEntity<String> del = rest.exchange(
            "/api/elements/pub-skill/versions/3.0.0", HttpMethod.DELETE,
            new HttpEntity<>(jsonHeaders()), String.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> list = rest.exchange(
            "/api/elements/pub-skill/versions", HttpMethod.GET,
            new HttpEntity<>(jsonHeaders()), String.class);
        assertThat(list.getBody()).doesNotContain("3.0.0");
    }
```

- [ ] **Step 2: Запустить, убедиться что падают**

Run: `mvn test -Dtest=ElementApiIT,VersionPublishIT`
Expected: FAIL — `DELETE` возвращает 405 (эндпоинтов нет).

- [ ] **Step 3: Добавить эндпоинт удаления элемента**

В `ElementController.java` добавить:

```java
    @DeleteMapping("/{slug}")
    public ResponseEntity<Void> delete(@PathVariable String slug, Authentication auth) {
        elementUseCase.delete(slug, currentUser.resolve(auth));
        return ResponseEntity.noContent().build();
    }
```

- [ ] **Step 4: Добавить эндпоинт удаления версии**

В `VersionController.java` добавить:

```java
    @DeleteMapping("/{version}")
    public ResponseEntity<Void> delete(@PathVariable String slug,
                                       @PathVariable String version,
                                       Authentication auth) {
        versionUseCase.deleteVersion(slug, version, currentUser.resolve(auth));
        return ResponseEntity.noContent().build();
    }
```

- [ ] **Step 5: Добавить `authorId` в `ElementResponse`**

Заменить запись и фабрику:

```java
public record ElementResponse(
    String slug, String type, String name, String description,
    String team, String category, String[] tags, String visibility,
    String latestVersion, long downloadsCount,
    Double avgRating, Long ratingCount, String authorId
) {
    public static ElementResponse from(Element e) {
        return from(e, null);
    }

    public static ElementResponse from(Element e, RatingSummary rating) {
        return new ElementResponse(
            e.getSlug(), e.getType().name(), e.getName(), e.getDescription(),
            e.getTeam() == null ? null : e.getTeam().getSlug(),
            e.getCategory() == null ? null : e.getCategory().getSlug(),
            e.getTags(), e.getVisibility().name(),
            e.getLatestVersion(), e.getDownloadsCount(),
            rating == null ? null : rating.avg(),
            rating == null ? null : rating.count(),
            e.getAuthor() == null ? null : e.getAuthor().getId().toString());
    }
}
```

- [ ] **Step 6: Добавить `userId` в `MeResponse`**

В `MeController.java` заменить запись и возврат:

```java
    public record MeResponse(String username, String userId, boolean admin,
                             List<TeamRoleItem> teams) {}
```

```java
        return new MeResponse(user.getDisplayName(), user.getId().toString(),
            user.isAdmin(), teams);
```

- [ ] **Step 7: Запустить тесты**

Run: `mvn test -Dtest=ElementApiIT,VersionPublishIT,TokenApiIT`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/skillhub/adapters/in/rest/ElementController.java src/main/java/com/skillhub/adapters/in/rest/VersionController.java src/main/java/com/skillhub/adapters/in/rest/dto/ElementResponse.java src/main/java/com/skillhub/adapters/in/rest/MeController.java src/test/java/com/skillhub/adapters/in/rest/ElementApiIT.java src/test/java/com/skillhub/adapters/in/rest/VersionPublishIT.java
git commit -m "feat: DELETE endpoints for elements and versions, expose authorId/userId"
```

---

### Task 8: UI — API-клиент, типы, `userId` в контексте авторизации

**Files:**
- Modify: `ui/src/api/elements.ts`
- Modify: `ui/src/api/me.ts`
- Modify: `ui/src/types.ts`
- Modify: `ui/src/auth/KeycloakProvider.tsx`

**Interfaces:**
- Consumes: REST из Task 7 (`authorId`, `userId`).
- Produces (используются Task 9):
  - `elements.remove(slug) : Promise<void>`, `elements.removeVersion(slug, version) : Promise<void>`.
  - `ElementResponse.authorId: string | null` в `types.ts`.
  - `AuthContextValue.userId: string | null`.

- [ ] **Step 1: Добавить методы удаления в API-клиент**

В `ui/src/api/elements.ts` после `publishVersion`:

```ts
  async remove(slug: string): Promise<void> {
    await api.delete(`/api/elements/${slug}`);
  },
  async removeVersion(slug: string, version: string): Promise<void> {
    await api.delete(`/api/elements/${slug}/versions/${version}`);
  },
```

- [ ] **Step 2: Добавить `authorId` в тип элемента**

В `ui/src/types.ts` в `ElementResponse` после `ratingCount?`:

```ts
  authorId: string | null;
```

- [ ] **Step 3: Добавить `userId` в тип `MeResponse`**

Проверить и обновить `ui/src/api/me.ts` — тип `MeResponse` должен содержать `userId`:

```ts
interface MeResponse {
  username: string;
  userId: string;
  admin: boolean;
  teams: { slug: string; name: string; role: string }[];
}
```

(Если тип объявлен иначе — привести к этому виду, сохранив существующие поля.)

- [ ] **Step 4: Прокинуть `userId` через `KeycloakProvider`**

В `ui/src/auth/KeycloakProvider.tsx`:
- в `AuthContextValue` добавить поле и дефолт:

```ts
  userId: string | null;
```

```ts
  userId: null,
```

- добавить state:

```ts
  const [userId, setUserId] = useState<string | null>(null);
```

- в блоке сброса (`if (!authenticated)`) добавить `setUserId(null);`
- в `meApi.get().then(...)` добавить `setUserId(info.userId);`
- в `catch` добавить `setUserId(null);`
- в value провайдера добавить `userId,`

- [ ] **Step 5: Проверить сборку**

Run: `npm run build`
Expected: успешная сборка (TypeScript без ошибок).

- [ ] **Step 6: Commit**

```bash
git add ui/src/api/elements.ts ui/src/api/me.ts ui/src/types.ts ui/src/auth/KeycloakProvider.tsx
git commit -m "feat: delete API client methods and userId in auth context"
```

---

### Task 9: UI — удаление элемента и версий на странице элемента

**Files:**
- Modify: `ui/src/components/VersionTable.tsx`
- Modify: `ui/src/pages/ElementPage.tsx`
- Test: `ui/src/pages/ElementPage.test.tsx`

**Interfaces:**
- Consumes: `elements.remove` / `elements.removeVersion` (Task 8), `ElementResponse.authorId` (Task 8), `useAuth().userId` (Task 8).
- Produces: кнопка удаления элемента и действие удаления в строке версии с confirm-диалогом.

- [ ] **Step 1: Написать падающие UI-тесты**

В `ui/src/pages/ElementPage.test.tsx`:
- в `element` (hoisted) добавить `authorId: 'u-1'`;
- в мок `../api/elements` добавить `remove: vi.fn().mockResolvedValue(undefined)` и `removeVersion: vi.fn().mockResolvedValue(undefined)`;
- в мок `../auth/KeycloakProvider` в `useAuth` добавить `userId: 'u-1'`;
- добавить тесты:

```tsx
test('owner sees delete element button and confirms', async () => {
  const { elements } = await import('../api/elements');
  renderPage();
  const btn = await screen.findByRole('button', { name: 'Удалить элемент' });
  await userEvent.click(btn);
  const confirm = await screen.findByRole('button', { name: 'Удалить' });
  await userEvent.click(confirm);
  await waitFor(() => expect(elements.remove).toHaveBeenCalledWith('pdf-skill'));
});

test('version delete calls removeVersion after confirm', async () => {
  const { elements } = await import('../api/elements');
  renderPage();
  const btn = await screen.findByRole('button', { name: 'Удалить версию 1.0.0' });
  await userEvent.click(btn);
  const confirm = await screen.findByRole('button', { name: 'Удалить' });
  await userEvent.click(confirm);
  await waitFor(() => expect(elements.removeVersion).toHaveBeenCalledWith('pdf-skill', '1.0.0'));
});
```

- [ ] **Step 2: Запустить тесты, убедиться что падают**

Run: `npm run test -- ElementPage`
Expected: FAIL — кнопки не найдены.

- [ ] **Step 3: Добавить действие удаления в `VersionTable`**

В `ui/src/components/VersionTable.tsx`:
- добавить импорт:

```tsx
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
```

- расширить пропсы:

```tsx
export default function VersionTable({ versions, onDownload, onCopyInstall, onDelete }: {
  versions: VersionResponse[];
  onDownload: (version: string) => void;
  onCopyInstall: (version: string) => void;
  onDelete?: (version: string) => void;
}) {
```

- в блоке действий после кнопки копирования добавить:

```tsx
                {onDelete && (
                  <Tooltip title="Удалить версию">
                    <IconButton
                      size="small"
                      aria-label={`Удалить версию ${v.version}`}
                      onClick={() => onDelete(v.version)}
                    >
                      <DeleteOutlineIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                )}
```

- [ ] **Step 4: Добавить удаление на страницу элемента**

В `ui/src/pages/ElementPage.tsx`:
- расширить импорты:

```tsx
import { useNavigate } from 'react-router-dom';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
```

- взять `userId` и `navigate`:

```tsx
  const navigate = useNavigate();
  const { authenticated, isAdmin, teamRoleOf, userId } = useAuth();
```

- добавить состояние:

```tsx
  const [elementDeleteOpen, setElementDeleteOpen] = useState(false);
  const [versionToDelete, setVersionToDelete] = useState<string | null>(null);
```

- посчитать права (после `const canPublish = ...`):

```tsx
  const canDelete =
    authenticated && (
      isAdmin
      || (element?.team == null && element?.authorId != null && element.authorId === userId)
      || teamRoleOf(element?.team ?? '') === 'OWNER'
    );
```

- добавить мутации (после `publishMutation`):

```tsx
  const deleteElementMutation = useMutation({
    mutationFn: () => elements.remove(slug!),
    onSuccess: () => {
      showSuccess('Элемент удалён');
      qc.invalidateQueries({ queryKey: ['elements'] });
      navigate('/catalog');
    },
    onError: (e) => showError(toApiError(e).message),
  });

  const deleteVersionMutation = useMutation({
    mutationFn: (version: string) => elements.removeVersion(slug!, version),
    onSuccess: () => {
      showSuccess('Версия удалена');
      qc.invalidateQueries({ queryKey: ['versions', slug] });
      qc.invalidateQueries({ queryKey: ['element', slug] });
    },
    onError: (e) => showError(toApiError(e).message),
  });
```

- в шапке, перед кнопкой install, добавить кнопку удаления (внутри `Stack direction="row"` после `<Box sx={{ flexGrow: 1 }} />`):

```tsx
          {canDelete && (
            <Tooltip title="Удалить элемент">
              <Button
                size="small"
                color="error"
                variant="outlined"
                startIcon={<DeleteOutlineIcon />}
                aria-label="Удалить элемент"
                onClick={() => setElementDeleteOpen(true)}
              >
                Удалить
              </Button>
            </Tooltip>
          )}
```

- импортировать `Tooltip` из `@mui/material` (добавить в существующий список импортов).
- передать `onDelete` в таблицу версий:

```tsx
            onDelete={canDelete ? (v) => setVersionToDelete(v) : undefined}
```

- добавить диалоги перед закрывающим `</Stack>` (после диалога отзыва):

```tsx
      <Dialog open={elementDeleteOpen} onClose={() => setElementDeleteOpen(false)}>
        <DialogTitle>Удалить элемент {element.name}?</DialogTitle>
        <DialogContent>
          <Typography>Элемент будет скрыт из каталога и поиска. Действие необратимо в интерфейсе.</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setElementDeleteOpen(false)}>Отмена</Button>
          <Button color="error" onClick={() => {
            setElementDeleteOpen(false);
            deleteElementMutation.mutate();
          }}>
            Удалить
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={versionToDelete != null} onClose={() => setVersionToDelete(null)}>
        <DialogTitle>Удалить версию {versionToDelete}?</DialogTitle>
        <DialogContent>
          <Typography>Версия будет скрыта. Последняя версия пересчитается автоматически.</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setVersionToDelete(null)}>Отмена</Button>
          <Button color="error" onClick={() => {
            if (versionToDelete) deleteVersionMutation.mutate(versionToDelete);
            setVersionToDelete(null);
          }}>
            Удалить
          </Button>
        </DialogActions>
      </Dialog>
```

- [ ] **Step 5: Запустить UI-тесты**

Run: `npm run test -- ElementPage`
Expected: PASS.

- [ ] **Step 6: Полная проверка UI**

Run: `npm run test` затем `npm run build`
Expected: все тесты PASS, сборка успешна.

- [ ] **Step 7: Commit**

```bash
git add ui/src/components/VersionTable.tsx ui/src/pages/ElementPage.tsx ui/src/pages/ElementPage.test.tsx
git commit -m "feat: delete element and version actions with confirm dialog"
```

---

## Self-Review

- **Покрытие спецификации:** миграция и домен (Task 1); скрытие удалённого в `getBySlug`/`listVisible`/`SearchAdapter`/версиях (Task 2); права `canDelete` (Task 3); проверка связей с паками для элемента и версии (Task 4, 5, 6); пересчёт `latestVersion` (Task 6); REST (Task 7); UI-кнопки и confirm-диалог (Task 8, 9). Пусто: ввод slug для подтверждения и показ удалённых с пометкой — явно вне области.
- **Согласованность типов:** `findActiveByElementIdAndVersion`, `findAllByElementId`, `canDelete`, `delete`, `deleteVersion`, `authorId`, `userId` используются одинаково во всех задачах.
- **Плейсхолдеры:** отсутствуют; код приведён полностью.
