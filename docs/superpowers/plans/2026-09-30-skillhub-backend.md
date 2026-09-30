# SkillHub Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Backend API корпоративного хранилища скиллов на чистой (гексагональной) архитектуре: домен отделён от реализации; элементы с semver-версиями (zip в S3), паки, категории, команды, права, полнотекстовый поиск, рейтинги/отзывы/избранное, SSO + API-токены.

**Architecture:** Гексагональная (Ports & Adapters). **domain/** — чистая Java (модели, порты-интерфейсы, доменные сервисы), без Spring/JPA/S3. **application/** — use cases, зависят только от портов (из Spring — только `@Transactional`). **adapters/in/** — REST-контроллеры и security-фильтры; **adapters/out/** — jpa-адаптер (реализация репозиторий-портов через Spring Data), s3-адаптер (StoragePort), clock-адаптер. Замена S3/БД/IdP = новая реализация порта, домен не меняется.

**Tech Stack:** Java 21, Spring Boot 3.3.x (Maven), PostgreSQL 16, Flyway, Spring Data JPA, Spring Security (OAuth2 Resource Server + API-токен фильтр), AWS SDK v2 (S3), commons-compress, Lombok, springdoc-openapi, Testcontainers (PostgreSQL + MinIO через GenericContainer), Mockito.

**Спека:** `docs/superpowers/specs/2026-09-30-skillhub-design.md`

## Global Constraints

- Java 21, Spring Boot 3.3.x, Maven. Группа `com.skillhub`, артефакт `skillhub-api`.
- **Правило зависимостей (гексагональная архитектура):**
  - `domain/` — чистая Java: НЕТ зависимостей на Spring, JPA, S3, servlet.
  - `application/` — зависит только от `domain/`; из Spring допустимы только `@Transactional` и `@Service`.
  - `adapters/` — зависят от `application/` и `domain/`; домен и application НИКОГДА не зависят от adapters.
  - Порты (`domain/port/`) — интерфейсы, определяемые доменом; адаптеры (`adapters/out/`) их реализуют; wiring — в `config/`.
  - Доменные модели — POJO (Lombok), НЕ JPA-сущности. JPA-сущности живут только в `adapters/out/jpa/`.
- Ошибки — единый формат JSON: `{"code", "message", "details"}`; HTTP: 401/403, 404, 409, 422.
- Версии: semver, regex `^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$`; immutable; DRAFT → PUBLISHED → DEPRECATED.
- Лимиты архива: загрузка ≤ 50MB, распаковка ≤ 200MB (209715200), ≤ 5000 файлов, запрет path traversal.
- API-токены: префикс `skh_`, в БД SHA-256 hex-хеш.
- Slug элемента — глобально уникальный (упрощает URL `/api/elements/{slug}`).
- Поиск: PG tsvector `simple` (ru+en), generated column, GIN — за портом `SearchPort`.
- Публикация: сначала S3, потом PG; orphan-объекты S3 чистятся фоновым job'ом (отложено).
- `latest` = последняя PUBLISHED по `published_at`. Visibility: PUBLIC — всем, TEAM — участникам; публикация — OWNER/MAINTAINER/админ.
- TDD: тест до реализации. Use cases — unit (Mockito, без Spring); адаптеры — IT (Testcontainers, нужен Docker).
- Коммит после каждой задачи, conventional commits.

## File Structure (итоговая)

```
src/main/java/com/skillhub/
├── SkillHubApplication.java
├── domain/                      — ЯДРО: чистая Java
│   ├── model/                   Element, ElementVersion, VersionStatus, ElementType,
│   │                            Visibility, Team, TeamRole, TeamMembership, Category,
│   │                            User, ApiToken, PackContent, Rating, Review, Favorite,
│   │                            AuditEntry, FileEntry, ArchiveInfo
│   ├── port/                    ElementRepositoryPort, ElementVersionRepositoryPort,
│   │                            TeamRepositoryPort, TeamMembershipPort, CategoryRepositoryPort,
│   │                            UserRepositoryPort, ApiTokenRepositoryPort, PackContentRepositoryPort,
│   │                            RatingRepositoryPort, ReviewRepositoryPort, FavoriteRepositoryPort,
│   │                            AuditPort, StoragePort, ClockPort, SearchPort
│   └── service/                 ArchiveService, AccessService
├── application/
│   ├── dto/                     SearchQuery, SearchQueryResult
│   └── service/                 ElementUseCase, VersionUseCase, PackUseCase, TeamUseCase,
│                                CategoryUseCase, SocialUseCase, ApiTokenService,
│                                UserSyncService, AuditService
├── adapters/
│   ├── in/
│   │   ├── rest/                ElementController, VersionController, PackController,
│   │   │                        CategoryController, TeamController, SocialController,
│   │   │                        TokenController, SearchController
│   │   │                        dto/ (ElementResponse, VersionResponse, ...)
│   │   └── security/            SecurityConfig, ApiTokenAuthFilter, CurrentUserResolver
│   └── out/
│       ├── jpa/                 entity/ (JpaElement, ...), repository/ (Spring Data),
│       │                        mapper/ (ElementJpaMapper, ...),
│       │                        JpaElementRepositoryAdapter, ... (реализации портов)
│       │                        SearchAdapter (SearchPort)
│       ├── s3/                  S3StorageAdapter (StoragePort)
│       └── clock/               SystemClockAdapter (ClockPort)
└── config/                      StorageProperties, AppConfig (wiring)
```

---

### Task 1: Скелет проекта, docker-compose, smoke-тест

**Files:**
- Create: `pom.xml`, `src/main/java/com/skillhub/SkillHubApplication.java`, `src/main/resources/application.yml`, `docker-compose.yml`, `.gitignore`
- Test: `src/test/java/com/skillhub/SmokeTest.java`

**Interfaces:**
- Produces: приложение `com.skillhub.SkillHubApplication`; конфиг-ключи `skillhub.storage.*`, `skillhub.api-token-prefix`, `skillhub.upload.*` — используются задачами 6–9.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/skillhub/SmokeTest.java`:

```java
package com.skillhub;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SmokeTest {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test`
Expected: FAIL — зависимостей нет.

- [ ] **Step 3: Write implementation**

`pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>
    <groupId>com.skillhub</groupId>
    <artifactId>skillhub-api</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <properties>
        <java.version>21</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>s3</artifactId>
            <version>2.29.29</version>
        </dependency>
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-compress</artifactId>
            <version>1.27.1</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.6.0</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

`src/main/java/com/skillhub/SkillHubApplication.java`:

```java
package com.skillhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SkillHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkillHubApplication.class, args);
    }
}
```

`src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/skillhub
    username: ${DB_USER:skillhub}
    password: ${DB_PASSWORD:skillhub}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 60MB
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${OIDC_ISSUER:https://keycloak.example.com/realms/skillhub}

skillhub:
  storage:
    endpoint: ${S3_ENDPOINT:http://localhost:9000}
    access-key: ${S3_ACCESS_KEY:minioadmin}
    secret-key: ${S3_SECRET_KEY:minioadmin}
    bucket: ${S3_BUCKET:skillhub}
    presign-ttl: 10m
  api-token-prefix: skh_
  upload:
    max-uncompressed-bytes: 209715200
    max-files: 5000

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

`docker-compose.yml`:

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: skillhub
      POSTGRES_USER: skillhub
      POSTGRES_PASSWORD: skillhub
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
  minio:
    image: minio/minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - miniodata:/data
volumes:
  pgdata:
  miniodata:
```

`.gitignore`:

```
target/
.idea/
*.iml
.env
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test`
Expected: PASS (contextLoads).

- [ ] **Step 5: Commit**

```bash
git add pom.xml src docker-compose.yml .gitignore
git commit -m "chore: scaffold Spring Boot project with dev docker-compose"
```

---

### Task 2: Схема БД (Flyway V1 + V2)

**Files:**
- Create: `src/main/resources/db/migration/V1__init.sql`, `V2__search_vector.sql`
- Test: `src/test/java/com/skillhub/migration/FlywayMigrationIT.java`

**Interfaces:**
- Produces: таблицы `users, api_tokens, teams, team_members, categories, elements, element_versions, pack_contents, ratings, reviews, favorites, audit_log`; колонка `elements.search_vector`. Используются Task 4+.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/skillhub/migration/FlywayMigrationIT.java`:

```java
package com.skillhub.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FlywayMigrationIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void allTablesExist() {
        List<String> tables = jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema='public'",
            String.class);
        assertThat(tables).contains(
            "users", "api_tokens", "teams", "team_members", "categories",
            "elements", "element_versions", "pack_contents",
            "ratings", "reviews", "favorites", "audit_log");
    }

    @Test
    void searchVectorColumnExists() {
        List<String> columns = jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns WHERE table_name='elements'",
            String.class);
        assertThat(columns).contains("search_vector");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=FlywayMigrationIT`
Expected: FAIL — таблиц нет.

- [ ] **Step 3: Write implementation**

`src/main/resources/db/migration/V1__init.sql`:

```sql
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  sso_subject TEXT NOT NULL UNIQUE,
  email TEXT NOT NULL,
  display_name TEXT NOT NULL,
  avatar_url TEXT,
  is_admin BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE api_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  token_hash TEXT NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_used_at TIMESTAMPTZ,
  expires_at TIMESTAMPTZ
);

CREATE TABLE teams (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  slug TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE team_members (
  team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role TEXT NOT NULL CHECK (role IN ('OWNER','MAINTAINER','MEMBER')),
  PRIMARY KEY (team_id, user_id)
);

CREATE TABLE categories (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  slug TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  parent_id UUID REFERENCES categories(id),
  icon TEXT
);

CREATE TABLE elements (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  slug TEXT NOT NULL UNIQUE,
  type TEXT NOT NULL CHECK (type IN ('SKILL','SCRIPT','AGENT','HOOK','PACK','OTHER')),
  name TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  team_id UUID NOT NULL REFERENCES teams(id),
  category_id UUID REFERENCES categories(id),
  tags TEXT[] NOT NULL DEFAULT '{}',
  visibility TEXT NOT NULL CHECK (visibility IN ('PUBLIC','TEAM')),
  author_id UUID NOT NULL REFERENCES users(id),
  latest_version TEXT,
  downloads_count BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE element_versions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  element_id UUID NOT NULL REFERENCES elements(id) ON DELETE CASCADE,
  version TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('DRAFT','PUBLISHED','DEPRECATED')),
  changelog TEXT NOT NULL DEFAULT '',
  s3_key TEXT NOT NULL,
  size_bytes BIGINT NOT NULL,
  file_index JSONB NOT NULL,
  published_by UUID NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ,
  UNIQUE (element_id, version)
);

CREATE TABLE pack_contents (
  pack_element_id UUID NOT NULL REFERENCES elements(id) ON DELETE CASCADE,
  element_id UUID NOT NULL REFERENCES elements(id) ON DELETE CASCADE,
  version_constraint TEXT NOT NULL,
  PRIMARY KEY (pack_element_id, element_id)
);

CREATE TABLE ratings (
  element_id UUID NOT NULL REFERENCES elements(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
  PRIMARY KEY (element_id, user_id)
);

CREATE TABLE reviews (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  element_id UUID NOT NULL REFERENCES elements(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
  text TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (element_id, user_id)
);

CREATE TABLE favorites (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  element_id UUID NOT NULL REFERENCES elements(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, element_id)
);

CREATE TABLE audit_log (
  id BIGSERIAL PRIMARY KEY,
  user_id UUID REFERENCES users(id),
  action TEXT NOT NULL,
  element_id UUID,
  details JSONB NOT NULL DEFAULT '{}',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_elements_team ON elements(team_id);
CREATE INDEX idx_elements_category ON elements(category_id);
CREATE INDEX idx_versions_element ON element_versions(element_id);
```

`src/main/resources/db/migration/V2__search_vector.sql`:

```sql
ALTER TABLE elements ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
  setweight(to_tsvector('simple', coalesce(name, '')), 'A') ||
  setweight(to_tsvector('simple', coalesce(description, '')), 'B') ||
  setweight(to_tsvector('simple', array_to_string(tags, ' ')), 'C')
) STORED;

CREATE INDEX idx_elements_search ON elements USING GIN (search_vector);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=FlywayMigrationIT`
Expected: PASS (нужен Docker).

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration src/test
git commit -m "feat: add Flyway schema V1 (all tables) and V2 (tsvector search)"
```

---

### Task 3: Домен — модели, порты, ArchiveService, AccessService

**Files:**
- Create: `src/main/java/com/skillhub/domain/model/` — `User.java`, `ApiToken.java`, `Team.java`, `TeamRole.java`, `TeamMembership.java`, `Category.java`, `ElementType.java`, `Visibility.java`, `Element.java`, `VersionStatus.java`, `ElementVersion.java`, `PackContent.java`, `Rating.java`, `Review.java`, `Favorite.java`, `AuditEntry.java`, `FileEntry.java`, `ArchiveInfo.java`
- Create: `src/main/java/com/skillhub/domain/port/` — `ElementRepositoryPort.java`, `ElementVersionRepositoryPort.java`, `TeamRepositoryPort.java`, `TeamMembershipPort.java`, `CategoryRepositoryPort.java`, `UserRepositoryPort.java`, `ApiTokenRepositoryPort.java`, `PackContentRepositoryPort.java`, `RatingRepositoryPort.java`, `ReviewRepositoryPort.java`, `FavoriteRepositoryPort.java`, `AuditPort.java`, `StoragePort.java`, `ClockPort.java`, `SearchPort.java`
- Create: `src/main/java/com/skillhub/domain/service/ArchiveService.java`, `AccessService.java`
- Test: `src/test/java/com/skillhub/domain/service/ArchiveServiceTest.java`
- Test: `src/test/java/com/skillhub/domain/service/AccessServiceTest.java`

**Interfaces:**
- Consumes: ничего (чистый домен).
- Produces (используются Task 4+):
  - Модели-POJO с Lombok (`@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`): `Element` (`id, slug, type:ElementType, name, description, team:Team, category:Category, tags:String[], visibility:Visibility, author:User, latestVersion, downloadsCount, createdAt, updatedAt`), `ElementVersion` (`id, element:Element, version, status:VersionStatus, changelog, s3_key, sizeBytes, fileIndex:String, publishedBy:User, createdAt, publishedAt`), `Team` (`id, slug, name, createdAt`), `TeamMembership` (`teamId, userId, role:TeamRole`), `Category` (`id, slug, name, parent:Category, icon`), `User` (`id, ssoSubject, email, displayName, avatarUrl, admin:boolean, createdAt`), `ApiToken` (`id, user:User, name, tokenHash, createdAt, lastUsedAt, expiresAt`), `PackContent` (`packElement:Element, element:Element, versionConstraint`), `Rating` (`elementId, userId, rating`), `Review` (`id, element:Element, user:User, rating, text, createdAt`), `Favorite` (`userId, elementId, createdAt`), `AuditEntry` (`user:User, action, elementId, detailsJson, createdAt`), `FileEntry` (record: `path, size`), `ArchiveInfo` (record: `manifestName, manifestVersion, manifestDescription, manifestType, files:List<FileEntry>, totalSize`).
  - Порты:

```java
public interface ElementRepositoryPort {
    Element save(Element element);
    Optional<Element> findBySlug(String slug);
    boolean existsBySlug(String slug);
}
public interface ElementVersionRepositoryPort {
    ElementVersion save(ElementVersion version);
    Optional<ElementVersion> findByElementIdAndVersion(UUID elementId, String version);
    Optional<ElementVersion> findLatestPublished(UUID elementId);
    List<ElementVersion> findAllByElementIdOrderByCreatedAtDesc(UUID elementId);
}
public interface TeamRepositoryPort {
    Team save(Team team);
    Optional<Team> findBySlug(String slug);
}
public interface TeamMembershipPort {
    Optional<TeamRole> roleOf(UUID teamId, UUID userId);
}
public interface CategoryRepositoryPort {
    Category save(Category category);
    Optional<Category> findBySlug(String slug);
    boolean existsBySlug(String slug);
    List<Category> findAllOrderedByName();
}
public interface UserRepositoryPort {
    User save(User user);
    Optional<User> findBySsoSubject(String ssoSubject);
}
public interface ApiTokenRepositoryPort {
    ApiToken save(ApiToken token);
    Optional<ApiToken> findByTokenHash(String tokenHash);
}
public interface PackContentRepositoryPort {
    PackContent save(PackContent content);
    List<PackContent> findAllByPackId(UUID packId);
}
public interface RatingRepositoryPort {
    Rating save(Rating rating);
    Optional<Rating> findByElementIdAndUserId(UUID elementId, UUID userId);
    double avgRating(UUID elementId);
    long countByElementId(UUID elementId);
}
public interface ReviewRepositoryPort {
    Review save(Review review);
    Optional<Review> findByElementIdAndUserId(UUID elementId, UUID userId);
    List<Review> findAllByElementIdOrderByCreatedAtDesc(UUID elementId);
}
public interface FavoriteRepositoryPort {
    Favorite save(Favorite favorite);
    void delete(Favorite favorite);
    Optional<Favorite> findByUserIdAndElementId(UUID userId, UUID elementId);
}
public interface AuditPort {
    void log(User user, String action, UUID elementId, String detailsJson);
}
public interface StoragePort {
    void upload(String key, byte[] content);
    byte[] download(String key);
    void delete(String key);
    String presignedGetUrl(String key, java.time.Duration ttl);
}
public interface ClockPort {
    java.time.Instant now();
}
public interface SearchPort {  // реализуется в Task 14
    com.skillhub.application.dto.SearchQueryResult search(com.skillhub.application.dto.SearchQuery query);
}
```

  - `ArchiveService` (чистый, в `domain/service`): конструктор `(long maxUncompressedBytes, int maxFiles)`; `ArchiveInfo inspect(byte[] zipBytes)`; `public static final Pattern SEMVER`. Бросает `UnprocessableException` (см. Task 5): не zip; нет `manifest.json`; нет name/version; version не semver; path traversal; превышены лимиты.
  - `AccessService` (чистый, в `domain/service`): зависит от `TeamMembershipPort`; `boolean canRead(Element, User)`, `boolean canPublish(Team, User)`, `boolean isTeamMember(Team, User)`.

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/skillhub/domain/service/ArchiveServiceTest.java`:

```java
package com.skillhub.domain.service;

import com.skillhub.core.exception.UnprocessableException;
import com.skillhub.domain.service.ArchiveService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchiveServiceTest {

    ArchiveService service = new ArchiveService(200, 10);

    static byte[] zip(Map<String, String> entries) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(bos)) {
            entries.forEach((name, content) -> {
                try {
                    zos.putNextEntry(new ZipEntry(name));
                    zos.write(content.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            zos.finish();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static Map<String, String> validEntries() {
        Map<String, String> entries = new HashMap<>();
        entries.put("manifest.json", "{\"name\":\"pdf-skill\",\"version\":\"1.2.3\","
            + "\"description\":\"PDF tools\",\"type\":\"SKILL\"}");
        entries.put("SKILL.md", "# skill");
        entries.put("scripts/run.sh", "echo hi");
        return entries;
    }

    @Test
    void inspectValidArchive() {
        ArchiveService.ArchiveInfo info = service.inspect(zip(validEntries()));
        assertThat(info.manifestName()).isEqualTo("pdf-skill");
        assertThat(info.manifestVersion()).isEqualTo("1.2.3");
        assertThat(info.manifestType()).isEqualTo("SKILL");
        assertThat(info.files()).extracting(com.skillhub.domain.model.FileEntry::path)
            .containsExactlyInAnyOrder("manifest.json", "SKILL.md", "scripts/run.sh");
        assertThat(info.totalSize()).isPositive();
    }

    @Test
    void rejectsMissingManifest() {
        Map<String, String> entries = new HashMap<>();
        entries.put("SKILL.md", "# skill");
        assertThatThrownBy(() -> service.inspect(zip(entries)))
            .isInstanceOf(UnprocessableException.class)
            .hasMessageContaining("manifest.json");
    }

    @Test
    void rejectsInvalidSemverInManifest() {
        Map<String, String> entries = new HashMap<>(validEntries());
        entries.put("manifest.json",
            "{\"name\":\"x\",\"version\":\"1.2\",\"description\":\"\",\"type\":\"SKILL\"}");
        assertThatThrownBy(() -> service.inspect(zip(entries)))
            .isInstanceOf(UnprocessableException.class)
            .hasMessageContaining("version");
    }

    @Test
    void rejectsPathTraversal() {
        Map<String, String> entries = new HashMap<>(validEntries());
        entries.put("../evil.sh", "rm -rf");
        assertThatThrownBy(() -> service.inspect(zip(entries)))
            .isInstanceOf(UnprocessableException.class)
            .hasMessageContaining("traversal");
    }

    @Test
    void rejectsTooManyFiles() {
        Map<String, String> entries = validEntries();
        for (int i = 0; i < 11; i++) {
            entries.put("f" + i + ".txt", "x");
        }
        assertThatThrownBy(() -> service.inspect(zip(entries)))
            .isInstanceOf(UnprocessableException.class)
            .hasMessageContaining("files");
    }

    @Test
    void rejectsNotAZip() {
        assertThatThrownBy(() -> service.inspect("not a zip".getBytes()))
            .isInstanceOf(UnprocessableException.class);
    }
}
```

`src/test/java/com/skillhub/domain/service/AccessServiceTest.java`:

```java
package com.skillhub.domain.service;

import com.skillhub.domain.model.*;
import com.skillhub.domain.port.TeamMembershipPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessServiceTest {

    TeamMembershipPort membership;
    AccessService access;

    Team team = Team.builder().id(UUID.randomUUID()).slug("t").name("T")
        .createdAt(Instant.now()).build();
    User user = User.builder().id(UUID.randomUUID()).ssoSubject("s").email("e")
        .displayName("n").admin(false).createdAt(Instant.now()).build();
    Element element = Element.builder().id(UUID.randomUUID()).slug("el")
        .type(ElementType.SKILL).name("n").description("").team(team)
        .tags(new String[0]).visibility(Visibility.TEAM).author(user)
        .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();

    @BeforeEach
    void setUp() {
        membership = mock(TeamMembershipPort.class);
        access = new AccessService(membership);
    }

    @Test
    void publicElementReadableByAnyone() {
        element.setVisibility(Visibility.PUBLIC);
        assertThat(access.canRead(element, null)).isTrue();
    }

    @Test
    void teamElementReadableByMember() {
        when(membership.roleOf(team.getId(), user.getId()))
            .thenReturn(Optional.of(TeamRole.MEMBER));
        assertThat(access.canRead(element, user)).isTrue();
    }

    @Test
    void teamElementNotReadableByOutsider() {
        when(membership.roleOf(team.getId(), user.getId())).thenReturn(Optional.empty());
        assertThat(access.canRead(element, user)).isFalse();
    }

    @Test
    void adminReadsEverything() {
        when(membership.roleOf(team.getId(), user.getId())).thenReturn(Optional.empty());
        user.setAdmin(true);
        assertThat(access.canRead(element, user)).isTrue();
    }

    @Test
    void onlyOwnerMaintainerOrAdminPublish() {
        when(membership.roleOf(team.getId(), user.getId()))
            .thenReturn(Optional.of(TeamRole.MEMBER));
        assertThat(access.canPublish(team, user)).isFalse();

        when(membership.roleOf(team.getId(), user.getId()))
            .thenReturn(Optional.of(TeamRole.MAINTAINER));
        assertThat(access.canPublish(team, user)).isTrue();

        when(membership.roleOf(team.getId(), user.getId())).thenReturn(Optional.empty());
        user.setAdmin(true);
        assertThat(access.canPublish(team, user)).isTrue();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest='ArchiveServiceTest,AccessServiceTest'`
Expected: FAIL — классы не существуют.

- [ ] **Step 3: Write implementation**

Исключения (создаются сейчас, используются везде; формально адаптерный код, но нужны домену — кладём в `core/exception/`):

```java
// src/main/java/com/skillhub/core/exception/NotFoundException.java
package com.skillhub.core.exception;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}

// src/main/java/com/skillhub/core/exception/ConflictException.java
package com.skillhub.core.exception;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}

// src/main/java/com/skillhub/core/exception/ForbiddenException.java
package com.skillhub.core.exception;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) { super(message); }
}

// src/main/java/com/skillhub/core/exception/UnprocessableException.java
package com.skillhub.core.exception;

public class UnprocessableException extends RuntimeException {
    private final Object details;
    public UnprocessableException(String message, Object details) {
        super(message);
        this.details = details;
    }
    public Object getDetails() { return details; }
}
```

Модели (`domain/model/`, все — POJO с Lombok `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`, без jakarta.persistence):

```java
// ElementType.java
package com.skillhub.domain.model;

public enum ElementType { SKILL, SCRIPT, AGENT, HOOK, PACK, OTHER }

// Visibility.java
package com.skillhub.domain.model;

public enum Visibility { PUBLIC, TEAM }

// VersionStatus.java
package com.skillhub.domain.model;

public enum VersionStatus { DRAFT, PUBLISHED, DEPRECATED }

// TeamRole.java
package com.skillhub.domain.model;

public enum TeamRole { OWNER, MAINTAINER, MEMBER }

// FileEntry.java
package com.skillhub.domain.model;

public record FileEntry(String path, long size) {}

// ArchiveInfo.java
package com.skillhub.domain.model;

import java.util.List;

public record ArchiveInfo(String manifestName, String manifestVersion,
                          String manifestDescription, String manifestType,
                          List<FileEntry> files, long totalSize) {}

// Team.java
package com.skillhub.domain.model;

import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Team {
    private UUID id;
    private String slug;
    private String name;
    private Instant createdAt;
}

// TeamMembership.java
package com.skillhub.domain.model;

import java.util.UUID;

public record TeamMembership(UUID teamId, UUID userId, TeamRole role) {}

// User.java
package com.skillhub.domain.model;

import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class User {
    private UUID id;
    private String ssoSubject;
    private String email;
    private String displayName;
    private String avatarUrl;
    private boolean admin;
    private Instant createdAt;
}

// ApiToken.java
package com.skillhub.domain.model;

import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ApiToken {
    private UUID id;
    private User user;
    private String name;
    private String tokenHash;
    private Instant createdAt;
    private Instant lastUsedAt;
    private Instant expiresAt;
}

// Category.java
package com.skillhub.domain.model;

import lombok.*;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Category {
    private UUID id;
    private String slug;
    private String name;
    private Category parent;
    private String icon;
}

// Element.java
package com.skillhub.domain.model;

import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Element {
    private UUID id;
    private String slug;
    private ElementType type;
    private String name;
    private String description;
    private Team team;
    private Category category;
    private String[] tags;
    private Visibility visibility;
    private User author;
    private String latestVersion;
    private long downloadsCount;
    private Instant createdAt;
    private Instant updatedAt;
}

// ElementVersion.java
package com.skillhub.domain.model;

import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ElementVersion {
    private UUID id;
    private Element element;
    private String version;
    private VersionStatus status;
    private String changelog;
    private String s3_key;
    private long sizeBytes;
    private String fileIndex;
    private User publishedBy;
    private Instant createdAt;
    private Instant publishedAt;
}

// PackContent.java
package com.skillhub.domain.model;

import lombok.*;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PackContent {
    private Element packElement;
    private Element element;
    private String versionConstraint;
}

// Rating.java
package com.skillhub.domain.model;

import java.util.UUID;

public record Rating(UUID elementId, UUID userId, int rating) {}

// Review.java
package com.skillhub.domain.model;

import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Review {
    private UUID id;
    private Element element;
    private User user;
    private int rating;
    private String text;
    private Instant createdAt;
}

// Favorite.java
package com.skillhub.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Favorite(UUID userId, UUID elementId, Instant createdAt) {}

// AuditEntry.java
package com.skillhub.domain.model;

public record AuditEntry(User user, String action, java.util.UUID elementId,
                         String detailsJson, java.time.Instant createdAt) {}
```

Порты (`domain/port/` — сигнатуры приведены в блоке Produces выше; каждый — отдельный файл с пакетом `com.skillhub.domain.port` и нужными импортами `com.skillhub.domain.model.*`).

`domain/service/ArchiveService.java`:

```java
package com.skillhub.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillhub.core.exception.UnprocessableException;
import com.skillhub.domain.model.ArchiveInfo;
import com.skillhub.domain.model.FileEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ArchiveService {

    public static final Pattern SEMVER =
        Pattern.compile("^\\d+\\.\\d+\\.\\d+(-[0-9A-Za-z.-]+)?$");
    private static final String MANIFEST = "manifest.json";

    private final long maxUncompressedBytes;
    private final int maxFiles;
    private final ObjectMapper mapper = new ObjectMapper();

    public ArchiveService(long maxUncompressedBytes, int maxFiles) {
        this.maxUncompressedBytes = maxUncompressedBytes;
        this.maxFiles = maxFiles;
    }

    public ArchiveInfo inspect(byte[] zipBytes) {
        List<FileEntry> files = new ArrayList<>();
        byte[] manifestBytes = null;
        long total = 0;
        int count = 0;

        try (ZipArchiveInputStream zin = new ZipArchiveInputStream(
                new ByteArrayInputStream(zipBytes), "UTF-8", true, true)) {
            ZipArchiveEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zin.getNextZipEntry()) != null) {
                String path = normalize(entry.getName());
                if (path == null) {
                    throw new UnprocessableException(
                        "Path traversal detected in archive", entry.getName());
                }
                if (entry.isDirectory()) {
                    continue;
                }
                count++;
                if (count > maxFiles) {
                    throw new UnprocessableException(
                        "Archive contains more than " + maxFiles + " files", null);
                }
                long size = 0;
                int read;
                while ((read = zin.read(buffer)) != -1) {
                    size += read;
                    total += read;
                    if (total > maxUncompressedBytes) {
                        throw new UnprocessableException(
                            "Uncompressed archive exceeds " + maxUncompressedBytes + " bytes", null);
                    }
                }
                if (path.equals(MANIFEST)) {
                    manifestBytes = zin.readAllBytes();
                }
                files.add(new FileEntry(path, size));
            }
        } catch (IOException e) {
            throw new UnprocessableException("Not a valid zip archive", e.getMessage());
        }

        if (manifestBytes == null) {
            throw new UnprocessableException(
                "Archive must contain " + MANIFEST + " in its root", null);
        }

        JsonNode manifest = parseManifest(new String(manifestBytes, StandardCharsets.UTF_8));
        String name = requiredText(manifest, "name");
        String version = requiredText(manifest, "version");
        if (!SEMVER.matcher(version).matches()) {
            throw new UnprocessableException(
                "manifest version must be semver (e.g. 1.2.3)", version);
        }
        return new ArchiveInfo(name, version,
            manifest.path("description").asText(""),
            manifest.path("type").asText("OTHER"),
            files, total);
    }

    private String normalize(String name) {
        String cleaned = name.replace('\\', '/');
        if (cleaned.startsWith("/") || cleaned.equals("..")
                || cleaned.contains("../") || cleaned.contains("/..")) {
            return null;
        }
        return cleaned;
    }

    private JsonNode parseManifest(String json) {
        try {
            return mapper.readTree(json);
        } catch (IOException e) {
            throw new UnprocessableException("manifest.json is not valid JSON", e.getMessage());
        }
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            throw new UnprocessableException("manifest." + field + " is required", null);
        }
        return value;
    }
}
```

`domain/service/AccessService.java`:

```java
package com.skillhub.domain.service;

import com.skillhub.domain.model.*;
import com.skillhub.domain.port.TeamMembershipPort;

import java.util.Optional;

public class AccessService {

    private final TeamMembershipPort membership;

    public AccessService(TeamMembershipPort membership) {
        this.membership = membership;
    }

    public boolean canRead(Element element, User user) {
        if (element.getVisibility() == Visibility.PUBLIC) {
            return true;
        }
        return user != null && (user.isAdmin() || isTeamMember(element.getTeam(), user));
    }

    public boolean canPublish(Team team, User user) {
        if (user == null) {
            return false;
        }
        if (user.isAdmin()) {
            return true;
        }
        return membership.roleOf(team.getId(), user.getId())
            .map(r -> r == TeamRole.OWNER || r == TeamRole.MAINTAINER)
            .orElse(false);
    }

    public boolean isTeamMember(Team team, User user) {
        return membership.roleOf(team.getId(), user.getId()).isPresent();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest='ArchiveServiceTest,AccessServiceTest'`
Expected: PASS. (Домен чистый: в `domain/` нет ни одного Spring-импорта.)

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat: add pure domain layer with models, ports, ArchiveService and AccessService"
```

---

### Task 4: JPA-адаптер — сущности, мапперы, реализации портов

**Files:**
- Create: `src/main/java/com/skillhub/adapters/out/jpa/entity/` — `JpaUser.java`, `JpaApiToken.java`, `JpaTeam.java`, `JpaTeamMember.java`, `JpaCategory.java`, `JpaElement.java`, `JpaElementVersion.java`, `JpaPackContent.java`, `JpaPackContentId.java`, `JpaReview.java`, `JpaAuditLog.java`
- Create: `src/main/java/com/skillhub/adapters/out/jpa/repository/` — Spring Data интерфейсы: `JpaUserRepository`, `JpaApiTokenRepository`, `JpaTeamRepository`, `JpaTeamMemberRepository`, `JpaCategoryRepository`, `JpaElementRepository`, `JpaElementVersionRepository`, `JpaPackContentRepository`, `JpaRatingRepository`, `JpaReviewRepository`, `JpaFavoriteRepository`, `JpaAuditLogRepository`
- Create: `src/main/java/com/skillhub/adapters/out/jpa/mapper/` — `ElementJpaMapper.java`, `ElementVersionJpaMapper.java`, `UserJpaMapper.java`, `TeamJpaMapper.java`, `CategoryJpaMapper.java`, `ReviewJpaMapper.java`
- Create: `src/main/java/com/skillhub/adapters/out/jpa/` — `JpaElementRepositoryAdapter.java`, `JpaElementVersionRepositoryAdapter.java`, `JpaTeamRepositoryAdapter.java`, `JpaTeamMembershipAdapter.java`, `JpaCategoryRepositoryAdapter.java`, `JpaUserRepositoryAdapter.java`, `JpaApiTokenRepositoryAdapter.java`, `JpaPackContentRepositoryAdapter.java`, `JpaRatingRepositoryAdapter.java`, `JpaReviewRepositoryAdapter.java`, `JpaFavoriteRepositoryAdapter.java`, `JpaAuditAdapter.java`
- Test: `src/test/java/com/skillhub/adapters/out/jpa/JpaElementRepositoryAdapterIT.java`

**Interfaces:**
- Consumes: порты и модели (Task 3), схема БД (Task 2).
- Produces: Spring-бины всех `*RepositoryPort`, `TeamMembershipPort`, `AuditPort` (аннотированы `@Repository`/`@Component`, сканируются автоматически). JPA-сущности существуют ТОЛЬКО здесь.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/skillhub/adapters/out/jpa/JpaElementRepositoryAdapterIT.java`:

```java
package com.skillhub.adapters.out.jpa;

import com.skillhub.domain.model.*;
import com.skillhub.domain.port.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class JpaElementRepositoryAdapterIT {

    @Autowired ElementRepositoryPort elements;
    @Autowired TeamRepositoryPort teams;
    @Autowired CategoryRepositoryPort categories;
    @Autowired UserRepositoryPort users;
    @Autowired ElementVersionRepositoryPort versions;
    @Autowired TeamMembershipPort membership;

    @Test
    void saveAndLoadElementGraph() {
        User author = users.save(User.builder()
            .ssoSubject("sub-1").email("a@b.c").displayName("Alice")
            .admin(true).createdAt(Instant.now()).build());
        Team team = teams.save(Team.builder()
            .slug("platform").name("Platform").createdAt(Instant.now()).build());
        Category cat = categories.save(Category.builder()
            .slug("dev").name("Разработка").build());

        Element saved = elements.save(Element.builder()
            .slug("pdf-skill").type(ElementType.SKILL).name("PDF Skill").description("d")
            .team(team).category(cat).tags(new String[]{"pdf", "docs"})
            .visibility(Visibility.PUBLIC).author(author)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build());

        versions.save(ElementVersion.builder()
            .element(saved).version("1.0.0").status(VersionStatus.PUBLISHED)
            .changelog("initial").s3_key("platform/pdf-skill/1.0.0.zip")
            .sizeBytes(10).fileIndex("{\"files\":[]}")
            .publishedBy(author).createdAt(Instant.now()).publishedAt(Instant.now()).build());

        Element loaded = elements.findBySlug("pdf-skill").orElseThrow();
        assertThat(loaded.getTeam().getSlug()).isEqualTo("platform");
        assertThat(loaded.getTags()).containsExactly("pdf", "docs");
        assertThat(loaded.getAuthor().getDisplayName()).isEqualTo("Alice");

        assertThat(versions.findLatestPublished(loaded.getId()))
            .as("latest published version").isPresent();

        assertThat(membership.roleOf(team.getId(), author.getId()))
            .as("membership from raw SQL is empty until seeded").isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=JpaElementRepositoryAdapterIT`
Expected: FAIL — адаптеры не существуют.

- [ ] **Step 3: Write implementation**

JPA-сущности (`adapters/out/jpa/entity/`, все с Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`):

```java
// JpaUser.java
package com.skillhub.adapters.out.jpa.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JpaUser {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "sso_subject", nullable = false, unique = true)
    private String ssoSubject;
    @Column(nullable = false) private String email;
    @Column(name = "display_name", nullable = false) private String displayName;
    @Column(name = "avatar_url") private String avatarUrl;
    @Column(name = "is_admin", nullable = false) private boolean admin;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}

// JpaTeam.java
package com.skillhub.adapters.out.jpa.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "teams")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JpaTeam {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true) private String slug;
    @Column(nullable = false) private String name;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}

// JpaTeamMember.java
package com.skillhub.adapters.out.jpa.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "team_members")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JpaTeamMember {
    @Id @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "team_id")
    private JpaTeam team;
    @Id @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id")
    private JpaUser user;
    @Column(nullable = false) private String role;
}

// JpaCategory.java
package com.skillhub.adapters.out.jpa.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "categories")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JpaCategory {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true) private String slug;
    @Column(nullable = false) private String name;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "parent_id")
    private JpaCategory parent;
    private String icon;
}

// JpaElement.java
package com.skillhub.adapters.out.jpa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "elements")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JpaElement {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true) private String slug;
    @Column(nullable = false) private String type;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String description;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "team_id", nullable = false)
    private JpaTeam team;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "category_id")
    private JpaCategory category;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] tags;
    @Column(nullable = false) private String visibility;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "author_id", nullable = false)
    private JpaUser author;
    @Column(name = "latest_version") private String latestVersion;
    @Column(name = "downloads_count", nullable = false) private long downloadsCount;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}

// JpaElementVersion.java
package com.skillhub.adapters.out.jpa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "element_versions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JpaElementVersion {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "element_id", nullable = false)
    private JpaElement element;
    @Column(nullable = false) private String version;
    @Column(nullable = false) private String status;
    @Column(nullable = false) private String changelog;
    @Column(name = "s3_key", nullable = false) private String s3Key;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "file_index", nullable = false, columnDefinition = "jsonb")
    private String fileIndex;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "published_by", nullable = false)
    private JpaUser publishedBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "published_at") private Instant publishedAt;
}

// JpaPackContentId.java
package com.skillhub.adapters.out.jpa.entity;

import lombok.*;
import java.io.Serializable;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class JpaPackContentId implements Serializable {
    private UUID packElementId;
    private UUID elementId;
}

// JpaPackContent.java
package com.skillhub.adapters.out.jpa.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pack_contents")
@IdClass(JpaPackContentId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JpaPackContent {
    @Id @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "pack_element_id")
    private JpaElement packElement;
    @Id @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "element_id")
    private JpaElement element;
    @Column(name = "version_constraint", nullable = false) private String versionConstraint;
}

// JpaReview.java
package com.skillhub.adapters.out.jpa.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reviews")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JpaReview {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "element_id", nullable = false)
    private JpaElement element;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private JpaUser user;
    @Column(nullable = false) private int rating;
    @Column(nullable = false) private String text;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}

// JpaAuditLog.java
package com.skillhub.adapters.out.jpa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JpaAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id")
    private JpaUser user;
    @Column(nullable = false) private String action;
    private UUID elementId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String details;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}
```

Spring Data репозитории (`adapters/out/jpa/repository/`):

```java
// JpaUserRepository.java
package com.skillhub.adapters.out.jpa.repository;

import com.skillhub.adapters.out.jpa.entity.JpaUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface JpaUserRepository extends JpaRepository<JpaUser, UUID> {
    Optional<JpaUser> findBySsoSubject(String ssoSubject);
}

// JpaApiTokenRepository.java
package com.skillhub.adapters.out.jpa.repository;

import com.skillhub.adapters.out.jpa.entity.JpaApiToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaApiTokenRepository extends JpaRepository<JpaApiToken, UUID> {
    Optional<JpaApiToken> findByTokenHash(String tokenHash);
    List<JpaApiToken> findAllByUserId(UUID userId);
}

// JpaTeamRepository.java
package com.skillhub.adapters.out.jpa.repository;

import com.skillhub.adapters.out.jpa.entity.JpaTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface JpaTeamRepository extends JpaRepository<JpaTeam, UUID> {
    Optional<JpaTeam> findBySlug(String slug);
}

// JpaTeamMemberRepository.java
package com.skillhub.adapters.out.jpa.repository;

import com.skillhub.adapters.out.jpa.entity.JpaTeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface JpaTeamMemberRepository extends JpaRepository<JpaTeamMember, JpaTeamMember.JpaTeamMemberId> {
    @Query("SELECT m.role FROM JpaTeamMember m WHERE m.team.id = :teamId AND m.user.id = :userId")
    Optional<String> findRole(@Param("teamId") UUID teamId, @Param("userId") UUID userId);

    @Query("SELECT COUNT(m) > 0 FROM JpaTeamMember m WHERE m.team.id = :teamId AND m.user.id = :userId")
    boolean existsMember(@Param("teamId") UUID teamId, @Param("userId") UUID userId);
}

// JpaCategoryRepository.java
package com.skillhub.adapters.out.jpa.repository;

import com.skillhub.adapters.out.jpa.entity.JpaCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaCategoryRepository extends JpaRepository<JpaCategory, UUID> {
    Optional<JpaCategory> findBySlug(String slug);
    boolean existsBySlug(String slug);
    List<JpaCategory> findAllByOrderByNameAsc();
}

// JpaElementRepository.java
package com.skillhub.adapters.out.jpa.repository;

import com.skillhub.adapters.out.jpa.entity.JpaElement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface JpaElementRepository extends JpaRepository<JpaElement, UUID> {
    Optional<JpaElement> findBySlug(String slug);
    boolean existsBySlug(String slug);
}

// JpaElementVersionRepository.java
package com.skillhub.adapters.out.jpa.repository;

import com.skillhub.adapters.out.jpa.entity.JpaElementVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaElementVersionRepository extends JpaRepository<JpaElementVersion, UUID> {
    Optional<JpaElementVersion> findByElementIdAndVersion(UUID elementId, String version);
    Optional<JpaElementVersion> findFirstByElementIdAndStatusOrderByPublishedAtDesc(
        UUID elementId, String status);
    List<JpaElementVersion> findAllByElementIdOrderByCreatedAtDesc(UUID elementId);
}

// JpaPackContentRepository.java
package com.skillhub.adapters.out.jpa.repository;

import com.skillhub.adapters.out.jpa.entity.JpaPackContent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface JpaPackContentRepository extends JpaRepository<JpaPackContent, JpaPackContent.JpaPackContentId> {
    List<JpaPackContent> findAllByPackElementId(UUID packElementId);
}

// JpaRatingRepository.java
package com.skillhub.adapters.out.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface JpaRatingRepository extends JpaRepository<com.skillhub.adapters.out.jpa.entity.JpaRating, com.skillhub.adapters.out.jpa.entity.JpaRatingId> {
    Optional<com.skillhub.adapters.out.jpa.entity.JpaRating> findByElementIdAndUserId(UUID elementId, UUID userId);

    @Query("SELECT COALESCE(AVG(r.rating), 0.0) FROM JpaRating r WHERE r.elementId = :elementId")
    double avgRating(@Param("elementId") UUID elementId);

    long countByElementId(UUID elementId);
}

// JpaReviewRepository.java
package com.skillhub.adapters.out.jpa.repository;

import com.skillhub.adapters.out.jpa.entity.JpaReview;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaReviewRepository extends JpaRepository<JpaReview, UUID> {
    Optional<JpaReview> findByElementIdAndUserId(UUID elementId, UUID userId);
    List<JpaReview> findAllByElementIdOrderByCreatedAtDesc(UUID elementId);
}

// JpaFavoriteRepository.java
package com.skillhub.adapters.out.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface JpaFavoriteRepository extends JpaRepository<com.skillhub.adapters.out.jpa.entity.JpaFavorite, com.skillhub.adapters.out.jpa.entity.JpaFavoriteId> {
    Optional<com.skillhub.adapters.out.jpa.entity.JpaFavorite> findByUserIdAndElementId(UUID userId, UUID elementId);
}

// JpaAuditLogRepository.java
package com.skillhub.adapters.out.jpa.repository;

import com.skillhub.adapters.out.jpa.entity.JpaAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaAuditLogRepository extends JpaRepository<JpaAuditLog, Long> {
}
```

Замечание: композитные ID. `JpaTeamMember.JpaTeamMemberId` — вложенный статический класс:

```java
// внутри JpaTeamMember.java
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class JpaTeamMemberId implements java.io.Serializable {
        private java.util.UUID team;
        private java.util.UUID user;
    }
```

Дополнительные сущности для ratings/favorites (JPA не умеет record-ключи из домена):

```java
// JpaRatingId.java
package com.skillhub.adapters.out.jpa.entity;

import lombok.*;
import java.io.Serializable;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class JpaRatingId implements Serializable {
    private UUID elementId;
    private UUID userId;
}

// JpaRating.java
package com.skillhub.adapters.out.jpa.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ratings")
@IdClass(JpaRatingId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JpaRating {
    @Id @Column(name = "element_id") private UUID elementId;
    @Id @Column(name = "user_id") private UUID userId;
    @Column(nullable = false) private int rating;
}

// JpaFavoriteId.java
package com.skillhub.adapters.out.jpa.entity;

import lombok.*;
import java.io.Serializable;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class JpaFavoriteId implements Serializable {
    private UUID userId;
    private UUID elementId;
}

// JpaFavorite.java
package com.skillhub.adapters.out.jpa.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "favorites")
@IdClass(JpaFavoriteId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JpaFavorite {
    @Id @Column(name = "user_id") private UUID userId;
    @Id @Column(name = "element_id") private UUID elementId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}

// JpaApiToken.java
package com.skillhub.adapters.out.jpa.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_tokens")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JpaApiToken {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private JpaUser user;
    @Column(nullable = false) private String name;
    @Column(name = "token_hash", nullable = false, unique = true) private String tokenHash;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "last_used_at") private Instant lastUsedAt;
    @Column(name = "expires_at") private Instant expiresAt;
}
```

Мапперы (`adapters/out/jpa/mapper/`):

```java
// UserJpaMapper.java
package com.skillhub.adapters.out.jpa.mapper;

import com.skillhub.adapters.out.jpa.entity.JpaUser;
import com.skillhub.domain.model.User;

public final class UserJpaMapper {
    private UserJpaMapper() {}

    public static User toDomain(JpaUser e) {
        return User.builder()
            .id(e.getId()).ssoSubject(e.getSsoSubject()).email(e.getEmail())
            .displayName(e.getDisplayName()).avatarUrl(e.getAvatarUrl())
            .admin(e.isAdmin()).createdAt(e.getCreatedAt())
            .build();
    }

    public static JpaUser toEntity(User d) {
        return JpaUser.builder()
            .id(d.getId()).ssoSubject(d.getSsoSubject()).email(d.getEmail())
            .displayName(d.getDisplayName()).avatarUrl(d.getAvatarUrl())
            .admin(d.isAdmin()).createdAt(d.getCreatedAt())
            .build();
    }
}

// TeamJpaMapper.java
package com.skillhub.adapters.out.jpa.mapper;

import com.skillhub.adapters.out.jpa.entity.JpaTeam;
import com.skillhub.domain.model.Team;

public final class TeamJpaMapper {
    private TeamJpaMapper() {}

    public static Team toDomain(JpaTeam e) {
        return Team.builder()
            .id(e.getId()).slug(e.getSlug()).name(e.getName()).createdAt(e.getCreatedAt())
            .build();
    }

    public static JpaTeam toEntity(Team d) {
        return JpaTeam.builder()
            .id(d.getId()).slug(d.getSlug()).name(d.getName()).createdAt(d.getCreatedAt())
            .build();
    }
}

// CategoryJpaMapper.java
package com.skillhub.adapters.out.jpa.mapper;

import com.skillhub.adapters.out.jpa.entity.JpaCategory;
import com.skillhub.domain.model.Category;

public final class CategoryJpaMapper {
    private CategoryJpaMapper() {}

    public static Category toDomain(JpaCategory e) {
        return Category.builder()
            .id(e.getId()).slug(e.getSlug()).name(e.getName())
            .parent(e.getParent() == null ? null : toDomain(e.getParent()))
            .icon(e.getIcon())
            .build();
    }

    public static JpaCategory toEntity(Category d) {
        return JpaCategory.builder()
            .id(d.getId()).slug(d.getSlug()).name(d.getName())
            .parent(d.getParent() == null ? null : toEntity(d.getParent()))
            .icon(d.getIcon())
            .build();
    }
}

// ElementJpaMapper.java
package com.skillhub.adapters.out.jpa.mapper;

import com.skillhub.adapters.out.jpa.entity.JpaElement;
import com.skillhub.domain.model.Element;
import com.skillhub.domain.model.ElementType;
import com.skillhub.domain.model.Visibility;

public final class ElementJpaMapper {
    private ElementJpaMapper() {}

    public static Element toDomain(JpaElement e) {
        return Element.builder()
            .id(e.getId()).slug(e.getSlug())
            .type(ElementType.valueOf(e.getType()))
            .name(e.getName()).description(e.getDescription())
            .team(TeamJpaMapper.toDomain(e.getTeam()))
            .category(e.getCategory() == null ? null : CategoryJpaMapper.toDomain(e.getCategory()))
            .tags(e.getTags())
            .visibility(Visibility.valueOf(e.getVisibility()))
            .author(UserJpaMapper.toDomain(e.getAuthor()))
            .latestVersion(e.getLatestVersion())
            .downloadsCount(e.getDownloadsCount())
            .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt())
            .build();
    }

    public static JpaElement toEntity(Element d) {
        return JpaElement.builder()
            .id(d.getId()).slug(d.getSlug()).type(d.getType().name())
            .name(d.getName()).description(d.getDescription())
            .team(TeamJpaMapper.toEntity(d.getTeam()))
            .category(d.getCategory() == null ? null : CategoryJpaMapper.toEntity(d.getCategory()))
            .tags(d.getTags()).visibility(d.getVisibility().name())
            .author(UserJpaMapper.toEntity(d.getAuthor()))
            .latestVersion(d.getLatestVersion())
            .downloadsCount(d.getDownloadsCount())
            .createdAt(d.getCreatedAt()).updatedAt(d.getUpdatedAt())
            .build();
    }
}

// ElementVersionJpaMapper.java
package com.skillhub.adapters.out.jpa.mapper;

import com.skillhub.adapters.out.jpa.entity.JpaElementVersion;
import com.skillhub.domain.model.ElementVersion;
import com.skillhub.domain.model.VersionStatus;

public final class ElementVersionJpaMapper {
    private ElementVersionJpaMapper() {}

    public static ElementVersion toDomain(JpaElementVersion e) {
        return ElementVersion.builder()
            .id(e.getId())
            .element(ElementJpaMapper.toDomain(e.getElement()))
            .version(e.getVersion())
            .status(VersionStatus.valueOf(e.getStatus()))
            .changelog(e.getChangelog())
            .s3_key(e.getS3Key())
            .sizeBytes(e.getSizeBytes())
            .fileIndex(e.getFileIndex())
            .publishedBy(UserJpaMapper.toDomain(e.getPublishedBy()))
            .createdAt(e.getCreatedAt()).publishedAt(e.getPublishedAt())
            .build();
    }

    public static JpaElementVersion toEntity(ElementVersion d) {
        return JpaElementVersion.builder()
            .id(d.getId())
            .element(com.skillhub.adapters.out.jpa.mapper.ElementJpaMapper.toEntity(d.getElement()))
            .version(d.getVersion())
            .status(d.getStatus().name())
            .changelog(d.getChangelog())
            .s3Key(d.getS3_key())
            .sizeBytes(d.getSizeBytes())
            .fileIndex(d.getFileIndex())
            .publishedBy(UserJpaMapper.toEntity(d.getPublishedBy()))
            .createdAt(d.getCreatedAt()).publishedAt(d.getPublishedAt())
            .build();
    }
}

// ReviewJpaMapper.java
package com.skillhub.adapters.out.jpa.mapper;

import com.skillhub.adapters.out.jpa.entity.JpaReview;
import com.skillhub.domain.model.Review;

public final class ReviewJpaMapper {
    private ReviewJpaMapper() {}

    public static Review toDomain(JpaReview e) {
        return Review.builder()
            .id(e.getId())
            .element(ElementJpaMapper.toDomain(e.getElement()))
            .user(UserJpaMapper.toDomain(e.getUser()))
            .rating(e.getRating()).text(e.getText()).createdAt(e.getCreatedAt())
            .build();
    }

    public static JpaReview toEntity(Review d) {
        return JpaReview.builder()
            .id(d.getId())
            .element(ElementJpaMapper.toEntity(d.getElement()))
            .user(UserJpaMapper.toEntity(d.getUser()))
            .rating(d.getRating()).text(d.getText()).createdAt(d.getCreatedAt())
            .build();
    }
}
```

Адаптеры портов (`adapters/out/jpa/`, аннотированы `@Repository`/`@Component`):

```java
// JpaUserRepositoryAdapter.java
package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.mapper.UserJpaMapper;
import com.skillhub.adapters.out.jpa.repository.JpaUserRepository;
import com.skillhub.domain.model.User;
import com.skillhub.domain.port.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JpaUserRepositoryAdapter implements UserRepositoryPort {

    private final JpaUserRepository jpa;

    @Override
    public User save(User user) {
        return UserJpaMapper.toDomain(jpa.save(UserJpaMapper.toEntity(user)));
    }

    @Override
    public Optional<User> findBySsoSubject(String ssoSubject) {
        return jpa.findBySsoSubject(ssoSubject).map(UserJpaMapper::toDomain);
    }
}

// JpaApiTokenRepositoryAdapter.java
package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.entity.JpaApiToken;
import com.skillhub.adapters.out.jpa.entity.JpaUser;
import com.skillhub.adapters.out.jpa.mapper.UserJpaMapper;
import com.skillhub.adapters.out.jpa.repository.JpaApiTokenRepository;
import com.skillhub.domain.model.ApiToken;
import com.skillhub.domain.model.User;
import com.skillhub.domain.port.ApiTokenRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JpaApiTokenRepositoryAdapter implements ApiTokenRepositoryPort {

    private final JpaApiTokenRepository jpa;

    @Override
    public ApiToken save(ApiToken token) {
        JpaUser user = JpaUser.builder().id(token.getUser().getId()).build();
        JpaApiToken saved = jpa.save(JpaApiToken.builder()
            .id(token.getId())
            .user(user)
            .name(token.getName())
            .tokenHash(token.getTokenHash())
            .createdAt(token.getCreatedAt())
            .lastUsedAt(token.getLastUsedAt())
            .expiresAt(token.getExpiresAt())
            .build());
        return ApiToken.builder()
            .id(saved.getId())
            .user(UserJpaMapper.toDomain(saved.getUser()))
            .name(saved.getName())
            .tokenHash(saved.getTokenHash())
            .createdAt(saved.getCreatedAt())
            .lastUsedAt(saved.getLastUsedAt())
            .expiresAt(saved.getExpiresAt())
            .build();
    }

    @Override
    public Optional<ApiToken> findByTokenHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash).map(t -> ApiToken.builder()
            .id(t.getId())
            .user(UserJpaMapper.toDomain(t.getUser()))
            .name(t.getName())
            .tokenHash(t.getTokenHash())
            .createdAt(t.getCreatedAt())
            .lastUsedAt(t.getLastUsedAt())
            .expiresAt(t.getExpiresAt())
            .build());
    }
}

// JpaTeamRepositoryAdapter.java
package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.mapper.TeamJpaMapper;
import com.skillhub.adapters.out.jpa.repository.JpaTeamRepository;
import com.skillhub.domain.model.Team;
import com.skillhub.domain.port.TeamRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JpaTeamRepositoryAdapter implements TeamRepositoryPort {

    private final JpaTeamRepository jpa;

    @Override
    public Team save(Team team) {
        return TeamJpaMapper.toDomain(jpa.save(TeamJpaMapper.toEntity(team)));
    }

    @Override
    public Optional<Team> findBySlug(String slug) {
        return jpa.findBySlug(slug).map(TeamJpaMapper::toDomain);
    }
}

// JpaTeamMembershipAdapter.java
package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.repository.JpaTeamMemberRepository;
import com.skillhub.domain.model.TeamRole;
import com.skillhub.domain.port.TeamMembershipPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaTeamMembershipAdapter implements TeamMembershipPort {

    private final JpaTeamMemberRepository jpa;

    @Override
    public Optional<TeamRole> roleOf(UUID teamId, UUID userId) {
        return jpa.findRole(teamId, userId).map(TeamRole::valueOf);
    }
}

// JpaCategoryRepositoryAdapter.java
package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.mapper.CategoryJpaMapper;
import com.skillhub.adapters.out.jpa.repository.JpaCategoryRepository;
import com.skillhub.domain.model.Category;
import com.skillhub.domain.port.CategoryRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JpaCategoryRepositoryAdapter implements CategoryRepositoryPort {

    private final JpaCategoryRepository jpa;

    @Override
    public Category save(Category category) {
        return CategoryJpaMapper.toDomain(jpa.save(CategoryJpaMapper.toEntity(category)));
    }

    @Override
    public Optional<Category> findBySlug(String slug) {
        return jpa.findBySlug(slug).map(CategoryJpaMapper::toDomain);
    }

    @Override
    public boolean existsBySlug(String slug) {
        return jpa.existsBySlug(slug);
    }

    @Override
    public List<Category> findAllOrderedByName() {
        return jpa.findAllByOrderByNameAsc().stream()
            .map(CategoryJpaMapper::toDomain)
            .toList();
    }
}

// JpaElementRepositoryAdapter.java
package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.mapper.ElementJpaMapper;
import com.skillhub.adapters.out.jpa.repository.JpaElementRepository;
import com.skillhub.domain.model.Element;
import com.skillhub.domain.port.ElementRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JpaElementRepositoryAdapter implements ElementRepositoryPort {

    private final JpaElementRepository jpa;

    @Override
    @Transactional
    public Element save(Element element) {
        return ElementJpaMapper.toDomain(jpa.save(ElementJpaMapper.toEntity(element)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Element> findBySlug(String slug) {
        return jpa.findBySlug(slug).map(ElementJpaMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySlug(String slug) {
        return jpa.existsBySlug(slug);
    }
}

// JpaElementVersionRepositoryAdapter.java
package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.mapper.ElementVersionJpaMapper;
import com.skillhub.adapters.out.jpa.repository.JpaElementVersionRepository;
import com.skillhub.domain.model.ElementVersion;
import com.skillhub.domain.model.VersionStatus;
import com.skillhub.domain.port.ElementVersionRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaElementVersionRepositoryAdapter implements ElementVersionRepositoryPort {

    private final JpaElementVersionRepository jpa;

    @Override
    @Transactional
    public ElementVersion save(ElementVersion version) {
        return ElementVersionJpaMapper.toDomain(jpa.save(ElementVersionJpaMapper.toEntity(version)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ElementVersion> findByElementIdAndVersion(UUID elementId, String version) {
        return jpa.findByElementIdAndVersion(elementId, version)
            .map(ElementVersionJpaMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ElementVersion> findLatestPublished(UUID elementId) {
        return jpa.findFirstByElementIdAndStatusOrderByPublishedAtDesc(
                elementId, VersionStatus.PUBLISHED.name())
            .map(ElementVersionJpaMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ElementVersion> findAllByElementIdOrderByCreatedAtDesc(UUID elementId) {
        return jpa.findAllByElementIdOrderByCreatedAtDesc(elementId).stream()
            .map(ElementVersionJpaMapper::toDomain)
            .toList();
    }
}

// JpaPackContentRepositoryAdapter.java
package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.entity.JpaElement;
import com.skillhub.adapters.out.jpa.entity.JpaPackContent;
import com.skillhub.adapters.out.jpa.repository.JpaElementRepository;
import com.skillhub.adapters.out.jpa.repository.JpaPackContentRepository;
import com.skillhub.domain.model.Element;
import com.skillhub.domain.model.PackContent;
import com.skillhub.domain.port.PackContentRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaPackContentRepositoryAdapter implements PackContentRepositoryPort {

    private final JpaPackContentRepository jpa;
    private final JpaElementRepository elementRepository;

    @Override
    @Transactional
    public PackContent save(PackContent content) {
        JpaElement pack = elementRepository.getReferenceById(content.getPackElement().getId());
        JpaElement element = elementRepository.getReferenceById(content.getElement().getId());
        jpa.save(JpaPackContent.builder()
            .packElement(pack)
            .element(element)
            .versionConstraint(content.getVersionConstraint())
            .build());
        return content;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PackContent> findAllByPackId(UUID packId) {
        return jpa.findAllByPackElementId(packId).stream()
            .map(this::toDomain)
            .toList();
    }

    private PackContent toDomain(JpaPackContent e) {
        return PackContent.builder()
            .packElement(com.skillhub.adapters.out.jpa.mapper.ElementJpaMapper.toDomain(e.getPackElement()))
            .element(com.skillhub.adapters.out.jpa.mapper.ElementJpaMapper.toDomain(e.getElement()))
            .versionConstraint(e.getVersionConstraint())
            .build();
    }
}

// JpaRatingRepositoryAdapter.java
package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.entity.JpaRating;
import com.skillhub.adapters.out.jpa.repository.JpaRatingRepository;
import com.skillhub.domain.model.Rating;
import com.skillhub.domain.port.RatingRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaRatingRepositoryAdapter implements RatingRepositoryPort {

    private final JpaRatingRepository jpa;

    @Override
    @Transactional
    public Rating save(Rating rating) {
        jpa.save(JpaRating.builder()
            .elementId(rating.elementId())
            .userId(rating.userId())
            .rating(rating.rating())
            .build());
        return rating;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Rating> findByElementIdAndUserId(UUID elementId, UUID userId) {
        return jpa.findByElementIdAndUserId(elementId, userId)
            .map(r -> new Rating(r.getElementId(), r.getUserId(), r.getRating()));
    }

    @Override
    @Transactional(readOnly = true)
    public double avgRating(UUID elementId) {
        return jpa.avgRating(elementId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByElementId(UUID elementId) {
        return jpa.countByElementId(elementId);
    }
}

// JpaReviewRepositoryAdapter.java
package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.mapper.ReviewJpaMapper;
import com.skillhub.adapters.out.jpa.repository.JpaReviewRepository;
import com.skillhub.domain.model.Review;
import com.skillhub.domain.port.ReviewRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaReviewRepositoryAdapter implements ReviewRepositoryPort {

    private final JpaReviewRepository jpa;

    @Override
    @Transactional
    public Review save(Review review) {
        return ReviewJpaMapper.toDomain(jpa.save(ReviewJpaMapper.toEntity(review)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Review> findByElementIdAndUserId(UUID elementId, UUID userId) {
        return jpa.findByElementIdAndUserId(elementId, userId).map(ReviewJpaMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Review> findAllByElementIdOrderByCreatedAtDesc(UUID elementId) {
        return jpa.findAllByElementIdOrderByCreatedAtDesc(elementId).stream()
            .map(ReviewJpaMapper::toDomain)
            .toList();
    }
}

// JpaFavoriteRepositoryAdapter.java
package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.entity.JpaFavorite;
import com.skillhub.adapters.out.jpa.repository.JpaFavoriteRepository;
import com.skillhub.domain.model.Favorite;
import com.skillhub.domain.port.FavoriteRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaFavoriteRepositoryAdapter implements FavoriteRepositoryPort {

    private final JpaFavoriteRepository jpa;

    @Override
    @Transactional
    public Favorite save(Favorite favorite) {
        jpa.save(JpaFavorite.builder()
            .userId(favorite.userId())
            .elementId(favorite.elementId())
            .createdAt(favorite.createdAt())
            .build());
        return favorite;
    }

    @Override
    @Transactional
    public void delete(Favorite favorite) {
        jpa.findByUserIdAndElementId(favorite.userId(), favorite.elementId())
            .ifPresent(jpa::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Favorite> findByUserIdAndElementId(UUID userId, UUID elementId) {
        return jpa.findByUserIdAndElementId(userId, elementId)
            .map(f -> new Favorite(f.getUserId(), f.getElementId(), f.getCreatedAt()));
    }
}

// JpaAuditAdapter.java
package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.entity.JpaAuditLog;
import com.skillhub.adapters.out.jpa.entity.JpaUser;
import com.skillhub.adapters.out.jpa.repository.JpaAuditLogRepository;
import com.skillhub.domain.model.User;
import com.skillhub.domain.port.AuditPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class JpaAuditAdapter implements AuditPort {

    private final JpaAuditLogRepository jpa;

    @Override
    @Transactional
    public void log(User user, String action, java.util.UUID elementId, String detailsJson) {
        try {
            JpaUser ref = user == null ? null
                : JpaUser.builder().id(user.getId()).build();
            jpa.save(JpaAuditLog.builder()
                .user(ref)
                .action(action)
                .elementId(elementId)
                .details(detailsJson == null ? "{}" : detailsJson)
                .createdAt(Instant.now())
                .build());
        } catch (Exception e) {
            log.warn("Failed to write audit log", e);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=JpaElementRepositoryAdapterIT`
Expected: PASS. (Если `ddl-auto: validate` ругается — править маппинг, НЕ схему.)

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat: add JPA adapter implementing domain repository ports"
```

---

### Task 5: Единая обработка ошибок (adapters/in/rest)

**Files:**
- Create: `src/main/java/com/skillhub/adapters/in/rest/ErrorResponse.java`
- Create: `src/main/java/com/skillhub/adapters/in/rest/GlobalExceptionHandler.java`
- Test: `src/test/java/com/skillhub/adapters/in/rest/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: исключения `core/exception/` (Task 3).
- Produces: `ErrorResponse(String code, String message, Object details)`; хендлеры `NotFoundException→404`, `ConflictException→409`, `UnprocessableException→422`, `ForbiddenException→403`, `MaxUploadSizeExceededException→422 ARCHIVE_TOO_LARGE`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/skillhub/adapters/in/rest/GlobalExceptionHandlerTest.java`:

```java
package com.skillhub.adapters.in.rest;

import com.skillhub.core.exception.ConflictException;
import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.core.exception.NotFoundException;
import com.skillhub.core.exception.UnprocessableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void notFoundMapsTo404() {
        ResponseEntity<ErrorResponse> r = handler.handleNotFound(new NotFoundException("no element"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(r.getBody().code()).isEqualTo("NOT_FOUND");
        assertThat(r.getBody().message()).isEqualTo("no element");
    }

    @Test
    void conflictMapsTo409() {
        ResponseEntity<ErrorResponse> r = handler.handleConflict(new ConflictException("dup"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody().code()).isEqualTo("CONFLICT");
    }

    @Test
    void unprocessableMapsTo422() {
        ResponseEntity<ErrorResponse> r = handler.handleUnprocessable(
            new UnprocessableException("bad manifest", "field=version"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(r.getBody().code()).isEqualTo("UNPROCESSABLE");
        assertThat(r.getBody().details()).isEqualTo("field=version");
    }

    @Test
    void forbiddenMapsTo403() {
        ResponseEntity<ErrorResponse> r = handler.handleForbidden(new ForbiddenException("denied"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(r.getBody().code()).isEqualTo("FORBIDDEN");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=GlobalExceptionHandlerTest`
Expected: FAIL — классов нет.

- [ ] **Step 3: Write implementation**

`adapters/in/rest/ErrorResponse.java`:

```java
package com.skillhub.adapters.in.rest;

public record ErrorResponse(String code, String message, Object details) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null);
    }
}
```

`adapters/in/rest/GlobalExceptionHandler.java`:

```java
package com.skillhub.adapters.in.rest;

import com.skillhub.core.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of("CONFLICT", e.getMessage()));
    }

    @ExceptionHandler(UnprocessableException.class)
    public ResponseEntity<ErrorResponse> handleUnprocessable(UnprocessableException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(new ErrorResponse("UNPROCESSABLE", e.getMessage(), e.getDetails()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse.of("FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse.of("ARCHIVE_TOO_LARGE", "Upload exceeds 50MB limit"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOther(Exception e) {
        log.error("Unhandled error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of("INTERNAL", "Unexpected error"));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=GlobalExceptionHandlerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat: add unified REST error handling"
```

---

### Task 6: S3-адаптер (StoragePort) + ClockPort + конфигурация

**Files:**
- Create: `src/main/java/com/skillhub/config/StorageProperties.java`
- Create: `src/main/java/com/skillhub/adapters/out/s3/S3StorageAdapter.java`
- Create: `src/main/java/com/skillhub/adapters/out/clock/SystemClockAdapter.java`
- Test: `src/test/java/com/skillhub/adapters/out/s3/S3StorageAdapterIT.java`

**Interfaces:**
- Consumes: порт `StoragePort`, `ClockPort` (Task 3); конфиг `skillhub.storage.*` (Task 1).
- Produces: Spring-бины `StoragePort` (S3StorageAdapter), `ClockPort` (SystemClockAdapter); `config/StorageProperties` — record из конфига. Используется Task 9–11.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/skillhub/adapters/out/s3/S3StorageAdapterIT.java`:

```java
package com.skillhub.adapters.out.s3;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class S3StorageAdapterIT {

    static GenericContainer<?> minio = new GenericContainer<>(DockerImageName.parse("minio/minio"))
        .withCommand("server /data")
        .withEnv("MINIO_ROOT_USER", "minioadmin")
        .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
        .withExposedPorts(9000)
        .waitingFor(Wait.forHttp("/minio/health/live").forStatusCode(200));

    static S3StorageAdapter storage;

    @BeforeAll
    static void setUp() {
        minio.start();
        storage = new S3StorageAdapter(
            "http://" + minio.getHost() + ":" + minio.getMappedPort(9000),
            "minioadmin", "minioadmin", "test-bucket");
        storage.ensureBucket();
    }

    @Test
    void uploadDownloadRoundtrip() {
        byte[] data = "hello skillhub".getBytes();
        storage.upload("team/el/1.0.0.zip", data);
        assertThat(storage.download("team/el/1.0.0.zip")).isEqualTo(data);
    }

    @Test
    void presignedUrlContainsKey() {
        storage.upload("team/el/2.0.0.zip", "x".getBytes());
        String url = storage.presignedGetUrl("team/el/2.0.0.zip", Duration.ofMinutes(5));
        assertThat(url).contains("team/el/2.0.0.zip").contains("X-Amz-Signature");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=S3StorageAdapterIT`
Expected: FAIL — класс не существует.

- [ ] **Step 3: Write implementation**

`config/StorageProperties.java`:

```java
package com.skillhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "skillhub.storage")
@Getter @Setter
public class StorageProperties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;
    private String presignTtl;
}
```

(нужны импорты `lombok.Getter`, `lombok.Setter`)

`adapters/out/s3/S3StorageAdapter.java`:

```java
package com.skillhub.adapters.out.s3;

import com.skillhub.domain.port.StoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.time.Duration;

@Component
public class S3StorageAdapter implements StoragePort {

    private final String bucket;
    private final S3Client client;
    private final S3Presigner presigner;

    public S3StorageAdapter(@Value("${skillhub.storage.endpoint}") String endpoint,
                            @Value("${skillhub.storage.access-key}") String accessKey,
                            @Value("${skillhub.storage.secret-key}") String secretKey,
                            @Value("${skillhub.storage.bucket}") String bucket) {
        this.bucket = bucket;
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKey, secretKey));
        this.client = S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.US_EAST_1)
            .credentialsProvider(credentials)
            .forcePathStyle(true)
            .build();
        this.presigner = S3Presigner.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.US_EAST_1)
            .credentialsProvider(credentials)
            .build();
    }

    public void ensureBucket() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }

    @Override
    public void upload(String key, byte[] content) {
        client.putObject(PutObjectRequest.builder()
            .bucket(bucket).key(key).build(), RequestBody.fromBytes(content));
    }

    @Override
    public byte[] download(String key) {
        return client.getObjectAsBytes(GetObjectRequest.builder()
            .bucket(bucket).key(key).build()).asByteArray();
    }

    @Override
    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder()
            .bucket(bucket).key(key).build());
    }

    @Override
    public String presignedGetUrl(String key, Duration ttl) {
        PresignedGetObjectRequest request = presigner.presignGetObject(
            b -> b.getObjectRequest(GetObjectRequest.builder()
                    .bucket(bucket).key(key).build())
                .signatureDuration(ttl));
        return request.url().toString();
    }
}
```

`adapters/out/clock/SystemClockAdapter.java`:

```java
package com.skillhub.adapters.out.clock;

import com.skillhub.domain.port.ClockPort;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SystemClockAdapter implements ClockPort {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=S3StorageAdapterIT`
Expected: PASS (нужен Docker).

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat: add S3 storage adapter (StoragePort) and system clock adapter"
```

---

### Task 7: Security-адаптер + ApiTokenService + UserSyncService + TokenController

**Files:**
- Create: `src/main/java/com/skillhub/application/service/ApiTokenService.java`
- Create: `src/main/java/com/skillhub/application/service/UserSyncService.java`
- Modify: `src/main/java/com/skillhub/domain/port/ApiTokenRepositoryPort.java` — метод `findByUserId`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/JpaApiTokenRepositoryAdapter.java` — реализация `findByUserId`
- Create: `src/main/java/com/skillhub/adapters/in/security/ApiTokenAuthFilter.java`
- Create: `src/main/java/com/skillhub/adapters/in/security/SecurityConfig.java`
- Create: `src/main/java/com/skillhub/adapters/in/security/CurrentUserResolver.java`
- Create: `src/main/java/com/skillhub/adapters/in/rest/TokenController.java`
- Test: `src/test/java/com/skillhub/application/service/ApiTokenServiceTest.java` (unit, Mockito)
- Test: `src/test/java/com/skillhub/adapters/in/security/SecurityApiIT.java` (IT)

**Interfaces:**
- Consumes: порты `ApiTokenRepositoryPort`, `UserRepositoryPort`, `ClockPort` (Task 3).
- Produces:
  - `ApiTokenService` (`application/service/`, `@Service`): `record CreatedToken(ApiToken token, String rawToken)`; `CreatedToken createToken(User user, String name)`; `Optional<User> authenticate(String rawToken)`; `static String hash(String value)` (SHA-256 hex). raw = `skh_` + base64url(32 байта).
  - `UserSyncService` (`application/service/`, `@Service`): `User syncFromSso(String subject, String email, String displayName)` — создаёт или обновляет пользователя.
  - `CurrentUserResolver` (`adapters/in/security/`, `@Component`): `User resolve(org.springframework.security.core.Authentication auth)` — null-безопасно: `User` principal (API-токен) возвращается как есть; `Jwt` → `userSyncService.syncFromSso(sub, sub, preferred_username)`.
  - REST: `POST /api/tokens {name}` → 201 `{token, name}` (raw показывается один раз); `GET /api/tokens` → `[{name, createdAt, lastUsedAt, expiresAt}]` — только свои токены.

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/skillhub/application/service/ApiTokenServiceTest.java`:

```java
package com.skillhub.application.service;

import com.skillhub.domain.model.*;
import com.skillhub.domain.port.ApiTokenRepositoryPort;
import com.skillhub.domain.port.ClockPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiTokenServiceTest {

    ApiTokenRepositoryPort tokens;
    ClockPort clock;
    ApiTokenService service;

    User user = User.builder().id(UUID.randomUUID()).ssoSubject("s").email("e")
        .displayName("n").admin(false).createdAt(Instant.now()).build();

    @BeforeEach
    void setUp() {
        tokens = mock(ApiTokenRepositoryPort.class);
        clock = mock(ClockPort.class);
        when(clock.now()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(tokens.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new ApiTokenService(tokens, clock, "skh_");
    }

    @Test
    void rawTokenHasPrefixAndHashDiffers() {
        ApiTokenService.CreatedToken created = service.createToken(user, "cli");
        assertThat(created.rawToken()).startsWith("skh_");
        assertThat(created.token().getTokenHash()).hasSize(64);
        assertThat(created.token().getTokenHash()).isNotEqualTo(created.rawToken());
    }

    @Test
    void hashIsDeterministic() {
        assertThat(ApiTokenService.hash("abc")).isEqualTo(ApiTokenService.hash("abc")).hasSize(64);
    }

    @Test
    void authenticateResolvesUserByHash() {
        ApiTokenService.CreatedToken created = service.createToken(user, "cli");
        when(tokens.findByTokenHash(created.token().getTokenHash()))
            .thenReturn(Optional.of(created.token()));
        assertThat(service.authenticate(created.rawToken())).contains(user);
    }

    @Test
    void expiredTokenIsRejected() {
        ApiTokenService.CreatedToken created = service.createToken(user, "cli");
        created.token().setExpiresAt(Instant.parse("2020-01-01T00:00:00Z"));
        when(tokens.findByTokenHash(created.token().getTokenHash()))
            .thenReturn(Optional.of(created.token()));
        assertThat(service.authenticate(created.rawToken())).isEmpty();
    }
}
```

`src/test/java/com/skillhub/adapters/in/security/SecurityApiIT.java`:

```java
package com.skillhub.adapters.in.security;

import com.skillhub.application.service.ApiTokenService;
import com.skillhub.application.service.UserSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityApiIT {

    @Autowired TestRestTemplate rest;
    @Autowired ApiTokenService tokens;
    @Autowired UserSyncService users;

    String authHeader;

    @BeforeEach
    void setUp() {
        var user = users.syncFromSso("sec-user", "sec@skillhub.io", "Sec User");
        authHeader = "Bearer " + tokens.createToken(user, "sec").rawToken();
    }

    @Test
    void tokenManagementEndpointRequiresAuth() {
        ResponseEntity<String> noAuth = rest.getForEntity("/api/tokens", String.class);
        assertThat(noAuth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authHeader.substring(7));
        ResponseEntity<String> ok = rest.exchange("/api/tokens", HttpMethod.GET,
            new HttpEntity<>(headers), String.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void invalidTokenIsUnauthorized() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("skh_invalid");
        ResponseEntity<String> r = rest.exchange("/api/tokens", HttpMethod.GET,
            new HttpEntity<>(headers), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createTokenEndpointReturnsRawToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authHeader.substring(7));
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> created = rest.exchange("/api/tokens", HttpMethod.POST,
            new HttpEntity<>("{\"name\":\"cli-new\"}", headers), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).contains("skh_");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest='ApiTokenServiceTest,SecurityApiIT'`
Expected: FAIL — классы не существуют.

- [ ] **Step 3: Write implementation**

`application/service/ApiTokenService.java`:

```java
package com.skillhub.application.service;

import com.skillhub.domain.model.ApiToken;
import com.skillhub.domain.model.User;
import com.skillhub.domain.port.ApiTokenRepositoryPort;
import com.skillhub.domain.port.ClockPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class ApiTokenService {

    public record CreatedToken(ApiToken token, String rawToken) {}

    private final ApiTokenRepositoryPort tokens;
    private final ClockPort clock;
    private final String prefix;
    private final SecureRandom random = new SecureRandom();

    public ApiTokenService(ApiTokenRepositoryPort tokens,
                           ClockPort clock,
                           @Value("${skillhub.api-token-prefix:skh_}") String prefix) {
        this.tokens = tokens;
        this.clock = clock;
        this.prefix = prefix;
    }

    @Transactional
    public CreatedToken createToken(User user, String name) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ApiToken saved = tokens.save(ApiToken.builder()
            .user(user)
            .name(name)
            .tokenHash(hash(raw))
            .createdAt(clock.now())
            .build());
        return new CreatedToken(saved, raw);
    }

    @Transactional
    public Optional<User> authenticate(String rawToken) {
        if (rawToken == null || !rawToken.startsWith(prefix)) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(hash(rawToken))
            .filter(t -> t.getExpiresAt() == null || t.getExpiresAt().isAfter(clock.now()))
            .map(t -> {
                t.setLastUsedAt(clock.now());
                return tokens.save(t).getUser();
            });
    }

    public static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
```

`application/service/UserSyncService.java`:

```java
package com.skillhub.application.service;

import com.skillhub.domain.model.User;
import com.skillhub.domain.port.ClockPort;
import com.skillhub.domain.port.UserRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSyncService {

    private final UserRepositoryPort users;
    private final ClockPort clock;

    public UserSyncService(UserRepositoryPort users, ClockPort clock) {
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public User syncFromSso(String subject, String email, String displayName) {
        return users.findBySsoSubject(subject)
            .map(u -> {
                u.setEmail(email);
                u.setDisplayName(displayName);
                return users.save(u);
            })
            .orElseGet(() -> users.save(User.builder()
                .ssoSubject(subject)
                .email(email)
                .displayName(displayName)
                .admin(false)
                .createdAt(clock.now())
                .build()));
    }
}
```

`adapters/in/security/CurrentUserResolver.java`:

```java
package com.skillhub.adapters.in.security;

import com.skillhub.application.service.UserSyncService;
import com.skillhub.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final UserSyncService userSyncService;

    public User resolve(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        if (auth.getPrincipal() instanceof User user) {
            return user;
        }
        if (auth.getPrincipal() instanceof Jwt jwt) {
            String subject = jwt.getSubject();
            String username = jwt.getClaimAsString("preferred_username");
            return userSyncService.syncFromSso(subject, subject,
                username != null ? username : subject);
        }
        return null;
    }
}
```

`adapters/in/security/ApiTokenAuthFilter.java`:

```java
package com.skillhub.adapters.in.security;

import com.skillhub.application.service.ApiTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ApiTokenAuthFilter extends OncePerRequestFilter {

    private final ApiTokenService apiTokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer skh_")) {
            apiTokenService.authenticate(header.substring(7)).ifPresent(user -> {
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    user, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        chain.doFilter(request, response);
    }
}
```

`adapters/in/security/SecurityConfig.java`:

```java
package com.skillhub.adapters.in.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ApiTokenAuthFilter apiTokenAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .addFilterBefore(apiTokenAuthFilter, AbstractPreAuthenticatedProcessingFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}))
            .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
```

`adapters/in/rest/TokenController.java`:

```java
package com.skillhub.adapters.in.rest;

import com.skillhub.adapters.in.security.CurrentUserResolver;
import com.skillhub.application.service.ApiTokenService;
import com.skillhub.domain.model.ApiToken;
import com.skillhub.domain.model.User;
import com.skillhub.domain.port.ApiTokenRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tokens")
@RequiredArgsConstructor
public class TokenController {

    public record CreateTokenRequest(String name) {}
    public record TokenItem(String name, String createdAt, String lastUsedAt, String expiresAt) {}

    private final ApiTokenService tokenService;
    private final ApiTokenRepositoryPort tokens;
    private final CurrentUserResolver currentUser;

    @PostMapping
    public ResponseEntity<Map<String, String>> create(@RequestBody CreateTokenRequest req,
                                                      Authentication auth) {
        User user = currentUser.resolve(auth);
        ApiTokenService.CreatedToken created = tokenService.createToken(user, req.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "token", created.rawToken(),
            "name", created.token().getName()));
    }

    @GetMapping
    public List<TokenItem> list(Authentication auth) {
        User user = currentUser.resolve(auth);
        return tokens.findByUserId(user.getId()).stream()
            .map(t -> new TokenItem(t.getName(), t.getCreatedAt().toString(),
                t.getLastUsedAt() == null ? null : t.getLastUsedAt().toString(),
                t.getExpiresAt() == null ? null : t.getExpiresAt().toString()))
            .toList();
    }
}
```

Метод `findByUserId` отсутствует в порте — добавьте его в `ApiTokenRepositoryPort` (Task 3, дополните интерфейс):

```java
    java.util.List<ApiToken> findByUserId(java.util.UUID userId);
```

и в `JpaApiTokenRepositoryAdapter` (Task 4):

```java
    @Override
    @Transactional(readOnly = true)
    public java.util.List<ApiToken> findByUserId(java.util.UUID userId) {
        return jpa.findAllByUserId(userId).stream().map(t -> ApiToken.builder()
            .id(t.getId())
            .user(UserJpaMapper.toDomain(t.getUser()))
            .name(t.getName())
            .tokenHash(t.getTokenHash())
            .createdAt(t.getCreatedAt())
            .lastUsedAt(t.getLastUsedAt())
            .expiresAt(t.getExpiresAt())
            .build()).toList();
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest='ApiTokenServiceTest,SecurityApiIT'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat: add security adapters, token service and token management API"
```

---

### Task 8: ElementUseCase + ElementController

**Files:**
- Create: `src/main/java/com/skillhub/application/service/ElementUseCase.java`
- Modify: `src/main/java/com/skillhub/domain/port/ElementRepositoryPort.java` — метод `findAll`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/JpaElementRepositoryAdapter.java` — реализация `findAll`
- Create: `src/main/java/com/skillhub/adapters/in/rest/dto/ElementResponse.java`, `CreateElementRequest.java`
- Create: `src/main/java/com/skillhub/adapters/in/rest/ElementController.java`
- Test: `src/test/java/com/skillhub/application/service/ElementUseCaseTest.java` (unit, Mockito)
- Test: `src/test/java/com/skillhub/adapters/in/rest/ElementApiIT.java` (IT)

**Interfaces:**
- Consumes: `ElementRepositoryPort`, `TeamRepositoryPort`, `CategoryRepositoryPort` (Task 3), `AccessService` (Task 3), `ClockPort` (Task 6).
- Produces:
  - `ElementUseCase` (`application/service/`, `@Service`): `Element create(CreateCommand cmd, User author)`; `Element getBySlug(String slug, User viewer)`; `List<Element> listVisible(User viewer)`.
    - `record CreateCommand(String slug, ElementType type, String name, String description, String teamSlug, String categorySlug, String[] tags, Visibility visibility)`
  - REST: `POST /api/elements` → 201; `GET /api/elements/{slug}` → 200; `GET /api/elements` → 200.
  - `adapters/in/rest/dto/ElementResponse.from(Element)` — маппинг домен → DTO (только в адаптере).

- [ ] **Step 1: Write the failing unit test**

`src/test/java/com/skillhub/application/service/ElementUseCaseTest.java`:

```java
package com.skillhub.application.service;

import com.skillhub.core.exception.ConflictException;
import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.core.exception.NotFoundException;
import com.skillhub.domain.model.*;
import com.skillhub.domain.port.*;
import com.skillhub.domain.service.AccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ElementUseCaseTest {

    ElementRepositoryPort elements;
    TeamRepositoryPort teams;
    CategoryRepositoryPort categories;
    ClockPort clock;
    TeamMembershipPort membership;
    ElementUseCase useCase;

    User owner = User.builder().id(UUID.randomUUID()).ssoSubject("s").email("e")
        .displayName("Owner").admin(false).createdAt(Instant.now()).build();
    Team team = Team.builder().id(UUID.randomUUID()).slug("platform").name("Platform")
        .createdAt(Instant.now()).build();

    @BeforeEach
    void setUp() {
        elements = mock(ElementRepositoryPort.class);
        teams = mock(TeamRepositoryPort.class);
        categories = mock(CategoryRepositoryPort.class);
        clock = mock(ClockPort.class);
        membership = mock(TeamMembershipPort.class);
        when(clock.now()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(teams.findBySlug("platform")).thenReturn(Optional.of(team));
        when(membership.roleOf(team.getId(), owner.getId()))
            .thenReturn(Optional.of(TeamRole.OWNER));
        when(elements.save(any())).thenAnswer(inv -> {
            Element e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });
        useCase = new ElementUseCase(elements, teams, categories,
            new AccessService(membership), clock);
    }

    ElementUseCase.CreateCommand cmd(String slug) {
        return new ElementUseCase.CreateCommand(slug, ElementType.SKILL, slug, "d",
            "platform", null, new String[0], Visibility.PUBLIC);
    }

    @Test
    void createByOwnerSucceeds() {
        Element created = useCase.create(cmd("my-skill"), owner);
        assertThat(created.getSlug()).isEqualTo("my-skill");
        assertThat(created.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void duplicateSlugConflicts() {
        when(elements.existsBySlug("my-skill")).thenReturn(true);
        assertThatThrownBy(() -> useCase.create(cmd("my-skill"), owner))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void memberCannotCreate() {
        when(membership.roleOf(team.getId(), owner.getId()))
            .thenReturn(Optional.of(TeamRole.MEMBER));
        assertThatThrownBy(() -> useCase.create(cmd("my-skill"), owner))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void unknownTeamIsNotFound() {
        when(teams.findBySlug("platform")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.create(cmd("my-skill"), owner))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getHiddenElementForbiddenForOutsider() {
        Element hidden = Element.builder().id(UUID.randomUUID()).slug("h")
            .type(ElementType.SKILL).name("h").description("").team(team)
            .tags(new String[0]).visibility(Visibility.TEAM).author(owner)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(elements.findBySlug("h")).thenReturn(Optional.of(hidden));
        assertThatThrownBy(() -> useCase.getBySlug("h", null))
            .isInstanceOf(ForbiddenException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ElementUseCaseTest`
Expected: FAIL — класс не существует.

- [ ] **Step 3: Write implementation**

`application/service/ElementUseCase.java`:

```java
package com.skillhub.application.service;

import com.skillhub.core.exception.ConflictException;
import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.core.exception.NotFoundException;
import com.skillhub.domain.model.*;
import com.skillhub.domain.port.CategoryRepositoryPort;
import com.skillhub.domain.port.ClockPort;
import com.skillhub.domain.port.ElementRepositoryPort;
import com.skillhub.domain.port.TeamRepositoryPort;
import com.skillhub.domain.service.AccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ElementUseCase {

    public record CreateCommand(String slug, ElementType type, String name,
                                String description, String teamSlug, String categorySlug,
                                String[] tags, Visibility visibility) {}

    private final ElementRepositoryPort elements;
    private final TeamRepositoryPort teams;
    private final CategoryRepositoryPort categories;
    private final AccessService access;
    private final ClockPort clock;

    public ElementUseCase(ElementRepositoryPort elements, TeamRepositoryPort teams,
                          CategoryRepositoryPort categories, AccessService access,
                          ClockPort clock) {
        this.elements = elements;
        this.teams = teams;
        this.categories = categories;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public Element create(CreateCommand cmd, User author) {
        Team team = teams.findBySlug(cmd.teamSlug())
            .orElseThrow(() -> new NotFoundException("Team not found: " + cmd.teamSlug()));
        if (!access.canPublish(team, author)) {
            throw new ForbiddenException(
                "Only OWNER/MAINTAINER can publish to team " + team.getSlug());
        }
        if (elements.existsBySlug(cmd.slug())) {
            throw new ConflictException("Element already exists: " + cmd.slug());
        }
        Category category = cmd.categorySlug() == null ? null
            : categories.findBySlug(cmd.categorySlug())
                .orElseThrow(() -> new NotFoundException(
                    "Category not found: " + cmd.categorySlug()));
        return elements.save(Element.builder()
            .slug(cmd.slug())
            .type(cmd.type())
            .name(cmd.name())
            .description(cmd.description() == null ? "" : cmd.description())
            .team(team)
            .category(category)
            .tags(cmd.tags() == null ? new String[0] : cmd.tags())
            .visibility(cmd.visibility())
            .author(author)
            .createdAt(clock.now())
            .updatedAt(clock.now())
            .build());
    }

    @Transactional(readOnly = true)
    public Element getBySlug(String slug, User viewer) {
        Element element = elements.findBySlug(slug)
            .orElseThrow(() -> new NotFoundException("Element not found: " + slug));
        if (!access.canRead(element, viewer)) {
            throw new ForbiddenException("Element is not visible to you: " + slug);
        }
        return element;
    }

    @Transactional(readOnly = true)
    public List<Element> listVisible(User viewer) {
        return elements.findAll().stream()
            .filter(e -> access.canRead(e, viewer))
            .toList();
    }
}
```

Метод `findAll()` отсутствует в порте — добавьте в `ElementRepositoryPort` (Task 3):

```java
    java.util.List<Element> findAll();
```

и в `JpaElementRepositoryAdapter` (Task 4):

```java
    @Override
    @Transactional(readOnly = true)
    public java.util.List<Element> findAll() {
        return jpa.findAll().stream().map(ElementJpaMapper::toDomain).toList();
    }
```

`adapters/in/rest/dto/ElementResponse.java`:

```java
package com.skillhub.adapters.in.rest.dto;

import com.skillhub.domain.model.Element;

public record ElementResponse(
    String slug, String type, String name, String description,
    String team, String category, String[] tags, String visibility,
    String latestVersion, long downloadsCount
) {
    public static ElementResponse from(Element e) {
        return new ElementResponse(
            e.getSlug(), e.getType().name(), e.getName(), e.getDescription(),
            e.getTeam().getSlug(),
            e.getCategory() == null ? null : e.getCategory().getSlug(),
            e.getTags(), e.getVisibility().name(),
            e.getLatestVersion(), e.getDownloadsCount());
    }
}
```

`adapters/in/rest/dto/CreateElementRequest.java`:

```java
package com.skillhub.adapters.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateElementRequest(
    @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]*$", message = "slug must be kebab-case")
    String slug,
    @NotBlank String type,
    @NotBlank String name,
    String description,
    @NotBlank String team,
    String category,
    String[] tags,
    @NotBlank String visibility
) {}
```

`adapters/in/rest/ElementController.java`:

```java
package com.skillhub.adapters.in.rest;

import com.skillhub.adapters.in.rest.dto.CreateElementRequest;
import com.skillhub.adapters.in.rest.dto.ElementResponse;
import com.skillhub.adapters.in.security.CurrentUserResolver;
import com.skillhub.application.service.ElementUseCase;
import com.skillhub.domain.model.ElementType;
import com.skillhub.domain.model.Visibility;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/elements")
@RequiredArgsConstructor
public class ElementController {

    private final ElementUseCase elementUseCase;
    private final CurrentUserResolver currentUser;

    @PostMapping
    public ResponseEntity<ElementResponse> create(@Valid @RequestBody CreateElementRequest req,
                                                  Authentication auth) {
        var cmd = new ElementUseCase.CreateCommand(
            req.slug(), ElementType.valueOf(req.type()), req.name(),
            req.description(), req.team(), req.category(), req.tags(),
            Visibility.valueOf(req.visibility()));
        ElementResponse created = ElementResponse.from(
            elementUseCase.create(cmd, currentUser.resolve(auth)));
        return ResponseEntity.status(HttpStatus.CREATED)
            .location(URI.create("/api/elements/" + created.slug()))
            .body(created);
    }

    @GetMapping("/{slug}")
    public ElementResponse get(@PathVariable String slug, Authentication auth) {
        return ElementResponse.from(elementUseCase.getBySlug(slug, currentUser.resolve(auth)));
    }

    @GetMapping
    public List<ElementResponse> list(Authentication auth) {
        return elementUseCase.listVisible(currentUser.resolve(auth)).stream()
            .map(ElementResponse::from)
            .toList();
    }
}
```

- [ ] **Step 4: Write the failing IT and run**

`src/test/java/com/skillhub/adapters/in/rest/ElementApiIT.java`:

```java
package com.skillhub.adapters.in.rest;

import com.skillhub.application.service.ApiTokenService;
import com.skillhub.application.service.UserSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ElementApiIT {

    @Autowired TestRestTemplate rest;
    @Autowired UserSyncService users;
    @Autowired ApiTokenService tokens;
    @Autowired JdbcTemplate jdbc;

    String authHeader;

    @BeforeEach
    void setUp() {
        var user = users.syncFromSso("elem-user", "el@skillhub.io", "Element User");
        authHeader = "Bearer " + tokens.createToken(user, "elem").rawToken();

        UUID teamId = UUID.randomUUID();
        jdbc.update("INSERT INTO teams (id, slug, name) VALUES (?, ?, ?) ON CONFLICT (slug) DO NOTHING",
            teamId, "platform-team", "Platform");
        UUID resolvedTeamId = jdbc.queryForObject(
            "SELECT id FROM teams WHERE slug = 'platform-team'", UUID.class);
        jdbc.update("""
            INSERT INTO team_members (team_id, user_id, role)
            SELECT ?, ?, 'OWNER'
            WHERE NOT EXISTS (SELECT 1 FROM team_members WHERE team_id = ? AND user_id = ?)
            """, resolvedTeamId, user.getId(), resolvedTeamId, user.getId());
    }

    Map<String, Object> skillRequest(String slug) {
        return Map.of(
            "slug", slug, "type", "SKILL", "name", slug,
            "description", "d", "team", "platform-team",
            "category", "dev", "tags", new String[]{}, "visibility", "PUBLIC");
    }

    HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, authHeader);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    void createCategory() {
        jdbc.update("""
            INSERT INTO categories (slug, name) VALUES ('dev', 'Разработка')
            ON CONFLICT (slug) DO NOTHING
            """);
    }

    @Test
    void createAndGetElement() {
        createCategory();
        ResponseEntity<String> created = rest.exchange("/api/elements", HttpMethod.POST,
            new HttpEntity<>(skillRequest("my-skill"), jsonHeaders()), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> got = rest.exchange("/api/elements/my-skill", HttpMethod.GET,
            new HttpEntity<>(jsonHeaders()), String.class);
        assertThat(got.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(got.getBody()).contains("platform-team");
    }

    @Test
    void duplicateSlugConflicts() {
        createCategory();
        rest.exchange("/api/elements", HttpMethod.POST,
            new HttpEntity<>(skillRequest("dup-skill"), jsonHeaders()), String.class);
        ResponseEntity<String> second = rest.exchange("/api/elements", HttpMethod.POST,
            new HttpEntity<>(skillRequest("dup-skill"), jsonHeaders()), String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void invalidSlugIsUnprocessable() {
        createCategory();
        ResponseEntity<String> r = rest.exchange("/api/elements", HttpMethod.POST,
            new HttpEntity<>(skillRequest("Bad Slug!"), jsonHeaders()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void unknownSlugIsNotFound() {
        ResponseEntity<String> r = rest.exchange("/api/elements/nope-404", HttpMethod.GET,
            new HttpEntity<>(jsonHeaders()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listReturnsVisibleElements() {
        createCategory();
        rest.exchange("/api/elements", HttpMethod.POST,
            new HttpEntity<>(skillRequest("public-skill"), jsonHeaders()), String.class);
        ResponseEntity<String> list = rest.exchange("/api/elements", HttpMethod.GET,
            new HttpEntity<>(jsonHeaders()), String.class);
        assertThat(list.getBody()).contains("public-skill");
    }
}
```

Run: `mvn test -Dtest='ElementUseCaseTest,ElementApiIT'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat: add element use case and REST controller"
```

---

### Task 9: VersionUseCase — публикация версии + AuditService + контроллер

**Files:**
- Create: `src/main/java/com/skillhub/application/service/AuditService.java`
- Create: `src/main/java/com/skillhub/application/service/VersionUseCase.java`
- Create: `src/main/java/com/skillhub/adapters/in/rest/dto/VersionResponse.java`
- Create: `src/main/java/com/skillhub/adapters/in/rest/VersionController.java`
- Test: `src/test/java/com/skillhub/application/service/VersionUseCaseTest.java` (unit, Mockito)
- Test: `src/test/java/com/skillhub/adapters/in/rest/VersionPublishIT.java` (IT)

**Interfaces:**
- Consumes: `ElementRepositoryPort`, `ElementVersionRepositoryPort` (Task 3), `ArchiveService` (Task 3), `AccessService` (Task 3), `StoragePort` (Task 6), `AuditPort` (Task 4), `ClockPort` (Task 6), `ElementUseCase.getBySlug` (Task 8).
- Produces:
  - `AuditService` (`application/service/`, `@Service`): `log(User user, String action, UUID elementId, Map<String, Object> details)` — сериализует details в JSON, делегирует `AuditPort`, никогда не бросает.
  - `VersionUseCase` (`application/service/`, `@Service`): `ElementVersion publish(String slug, byte[] zipBytes, String changelog, User publisher)`:
    1. элемент по slug (404); 2. `access.canPublish` → иначе 403; 3. `archiveService.inspect(zipBytes)` (422); 4. версия есть → 409; 5. `s3Key = "{teamSlug}/{elementSlug}/{version}.zip"`, `storage.upload(s3Key, zipBytes)` — сначала S3; 6. сохранить ElementVersion (PUBLISHED, fileIndex=JSON, sizeBytes, publishedAt=clock.now()), обновить `element.latestVersion`; 7. audit.
  - REST: `POST /api/elements/{slug}/versions?changelog=...` (multipart `file`) → 201 `VersionResponse`.
  - `adapters/in/rest/dto/VersionResponse.from(ElementVersion)` — парсит fileIndex.

`adapters/in/rest/dto/VersionResponse.java`:

```java
package com.skillhub.adapters.in.rest.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillhub.domain.model.ElementVersion;

import java.util.ArrayList;
import java.util.List;

public record VersionResponse(String version, String status, String changelog,
                              long sizeBytes, List<FileDto> files) {

    public record FileDto(String path, long size) {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static VersionResponse from(ElementVersion v) {
        List<FileDto> files = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(v.getFileIndex());
            for (JsonNode node : root.path("files")) {
                files.add(new FileDto(node.path("path").asText(), node.path("size").asLong()));
            }
        } catch (Exception ignored) {
        }
        return new VersionResponse(v.getVersion(), v.getStatus().name(),
            v.getChangelog(), v.getSizeBytes(), files);
    }
}
```

- [ ] **Step 1: Write the failing unit test**

`src/test/java/com/skillhub/application/service/VersionUseCaseTest.java`:

```java
package com.skillhub.application.service;

import com.skillhub.core.exception.ConflictException;
import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.domain.model.*;
import com.skillhub.domain.port.*;
import com.skillhub.domain.service.AccessService;
import com.skillhub.domain.service.ArchiveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VersionUseCaseTest {

    ElementRepositoryPort elements;
    ElementVersionRepositoryPort versions;
    StoragePort storage;
    AuditService audit;
    ClockPort clock;
    TeamMembershipPort membership;
    VersionUseCase useCase;

    User owner = User.builder().id(UUID.randomUUID()).ssoSubject("s").email("e")
        .displayName("Owner").admin(false).createdAt(Instant.now()).build();
    Team team = Team.builder().id(UUID.randomUUID()).slug("platform").name("Platform")
        .createdAt(Instant.now()).build();
    Element element = Element.builder().id(UUID.randomUUID()).slug("my-skill")
        .type(ElementType.SKILL).name("my-skill").description("").team(team)
        .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
        .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();

    static byte[] zip(String version) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(("{\"name\":\"my-skill\",\"version\":\"" + version
                + "\",\"description\":\"d\",\"type\":\"SKILL\"}")
                .getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("SKILL.md"));
            zos.write("# skill".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.finish();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() {
        elements = mock(ElementRepositoryPort.class);
        versions = mock(ElementVersionRepositoryPort.class);
        storage = mock(StoragePort.class);
        audit = mock(AuditService.class);
        clock = mock(ClockPort.class);
        membership = mock(TeamMembershipPort.class);
        when(clock.now()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(elements.findBySlug("my-skill")).thenReturn(Optional.of(element));
        when(membership.roleOf(team.getId(), owner.getId()))
            .thenReturn(Optional.of(TeamRole.OWNER));
        when(versions.findByElementIdAndVersion(any(), anyString()))
            .thenReturn(Optional.empty());
        when(versions.save(any())).thenAnswer(inv -> {
            ElementVersion v = inv.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });
        useCase = new VersionUseCase(elements, versions, storage, audit,
            new AccessService(membership), new ArchiveService(200, 10), clock);
    }

    @Test
    void publishUploadsToS3ThenSavesVersion() {
        ElementVersion published = useCase.publish("my-skill", zip("1.0.0"), "init", owner);
        assertThat(published.getVersion()).isEqualTo("1.0.0");
        assertThat(published.getS3_key()).isEqualTo("platform/my-skill/1.0.0.zip");
        verify(storage).upload(
            org.mockito.ArgumentMatchers.eq("platform/my-skill/1.0.0.zip"),
            any(byte[].class));
        assertThat(element.getLatestVersion()).isEqualTo("1.0.0");
        verify(audit).log(any(), anyString(), any(), any());
    }

    @Test
    void duplicateVersionConflicts() {
        when(versions.findByElementIdAndVersion(element.getId(), "1.0.0"))
            .thenReturn(Optional.of(ElementVersion.builder().build()));
        assertThatThrownBy(() -> useCase.publish("my-skill", zip("1.0.0"), null, owner))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void nonPublisherForbidden() {
        when(membership.roleOf(team.getId(), owner.getId()))
            .thenReturn(Optional.of(TeamRole.MEMBER));
        assertThatThrownBy(() -> useCase.publish("my-skill", zip("1.0.0"), null, owner))
            .isInstanceOf(ForbiddenException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=VersionUseCaseTest`
Expected: FAIL — класс не существует.

- [ ] **Step 3: Write implementation**

`application/service/AuditService.java`:

```java
package com.skillhub.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillhub.domain.model.User;
import com.skillhub.domain.port.AuditPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class AuditService {

    private final AuditPort auditPort;
    private final ObjectMapper mapper = new ObjectMapper();

    public AuditService(AuditPort auditPort) {
        this.auditPort = auditPort;
    }

    public void log(User user, String action, UUID elementId, Map<String, Object> details) {
        try {
            auditPort.log(user, action, elementId,
                mapper.writeValueAsString(details == null ? Map.of() : details));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit details", e);
        }
    }
}
```

`application/service/VersionUseCase.java`:

```java
package com.skillhub.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillhub.core.exception.ConflictException;
import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.core.exception.NotFoundException;
import com.skillhub.domain.model.*;
import com.skillhub.domain.port.ElementRepositoryPort;
import com.skillhub.domain.port.ElementVersionRepositoryPort;
import com.skillhub.domain.port.ClockPort;
import com.skillhub.domain.port.StoragePort;
import com.skillhub.domain.service.AccessService;
import com.skillhub.domain.service.ArchiveService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class VersionUseCase {

    private final ElementRepositoryPort elements;
    private final ElementVersionRepositoryPort versions;
    private final StoragePort storage;
    private final AuditService audit;
    private final AccessService access;
    private final ArchiveService archiveService;
    private final ClockPort clock;
    private final ObjectMapper mapper = new ObjectMapper();

    public VersionUseCase(ElementRepositoryPort elements,
                          ElementVersionRepositoryPort versions,
                          StoragePort storage, AuditService audit,
                          AccessService access, ArchiveService archiveService,
                          ClockPort clock) {
        this.elements = elements;
        this.versions = versions;
        this.storage = storage;
        this.audit = audit;
        this.access = access;
        this.archiveService = archiveService;
        this.clock = clock;
    }

    @Transactional
    public ElementVersion publish(String slug, byte[] zipBytes, String changelog, User publisher) {
        Element element = elements.findBySlug(slug)
            .orElseThrow(() -> new NotFoundException("Element not found: " + slug));
        if (!access.canPublish(element.getTeam(), publisher)) {
            throw new ForbiddenException("Only OWNER/MAINTAINER can publish to this team");
        }

        ArchiveInfo info = archiveService.inspect(zipBytes);

        if (versions.findByElementIdAndVersion(element.getId(), info.manifestVersion()).isPresent()) {
            throw new ConflictException(
                "Version " + info.manifestVersion() + " already exists for " + slug);
        }

        String s3Key = element.getTeam().getSlug() + "/" + element.getSlug()
            + "/" + info.manifestVersion() + ".zip";
        storage.upload(s3Key, zipBytes);

        ElementVersion version = versions.save(ElementVersion.builder()
            .element(element)
            .version(info.manifestVersion())
            .status(VersionStatus.PUBLISHED)
            .changelog(changelog == null ? "" : changelog)
            .s3_key(s3Key)
            .sizeBytes(zipBytes.length)
            .fileIndex(buildFileIndex(info))
            .publishedBy(publisher)
            .createdAt(clock.now())
            .publishedAt(clock.now())
            .build());

        element.setLatestVersion(info.manifestVersion());
        element.setUpdatedAt(clock.now());
        elements.save(element);

        audit.log(publisher, "PUBLISH_VERSION", element.getId(),
            Map.of("version", info.manifestVersion(), "s3Key", s3Key));
        return version;
    }

    private String buildFileIndex(ArchiveInfo info) {
        ObjectNode root = mapper.createObjectNode();
        root.put("totalSize", info.totalSize());
        ArrayNode files = root.putArray("files");
        for (FileEntry f : info.files()) {
            ObjectNode node = files.addObject();
            node.put("path", f.path());
            node.put("size", f.size());
        }
        return root.toString();
    }
}
```

`adapters/in/rest/VersionController.java` (в этой задаче — только publish):

```java
package com.skillhub.adapters.in.rest;

import com.skillhub.adapters.in.rest.dto.VersionResponse;
import com.skillhub.adapters.in.security.CurrentUserResolver;
import com.skillhub.application.service.VersionUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/elements/{slug}/versions")
@RequiredArgsConstructor
public class VersionController {

    private final VersionUseCase versionUseCase;
    private final CurrentUserResolver currentUser;

    @PostMapping
    public ResponseEntity<VersionResponse> publish(@PathVariable String slug,
                                                   @RequestParam("file") MultipartFile file,
                                                   @RequestParam(value = "changelog", required = false) String changelog,
                                                   Authentication auth) throws IOException {
        VersionResponse published = VersionResponse.from(
            versionUseCase.publish(slug, file.getBytes(), changelog, currentUser.resolve(auth)));
        return ResponseEntity.status(HttpStatus.CREATED).body(published);
    }
}
```

- [ ] **Step 4: Write the failing IT and run**

`src/test/java/com/skillhub/adapters/in/rest/VersionPublishIT.java`:

```java
package com.skillhub.adapters.in.rest;

import com.skillhub.application.service.ApiTokenService;
import com.skillhub.application.service.UserSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VersionPublishIT {

    @Autowired protected TestRestTemplate rest;
    @Autowired protected UserSyncService users;
    @Autowired protected ApiTokenService tokens;
    @Autowired protected JdbcTemplate jdbc;

    protected String authHeader;

    @BeforeEach
    void setUp() {
        var user = users.syncFromSso("pub-user", "pub@skillhub.io", "Publisher");
        authHeader = "Bearer " + tokens.createToken(user, "pub").rawToken();

        jdbc.update("""
            INSERT INTO teams (slug, name) VALUES ('pub-team', 'Pub')
            ON CONFLICT (slug) DO NOTHING
            """);
        UUID teamId = jdbc.queryForObject("SELECT id FROM teams WHERE slug='pub-team'", UUID.class);
        jdbc.update("""
            INSERT INTO team_members (team_id, user_id, role)
            SELECT ?, ?, 'OWNER'
            WHERE NOT EXISTS (SELECT 1 FROM team_members WHERE team_id = ? AND user_id = ?)
            """, teamId, user.getId(), teamId, user.getId());

        if (!elementExists("pub-skill")) {
            rest.exchange("/api/elements", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                    "slug", "pub-skill", "type", "SKILL", "name", "pub-skill",
                    "description", "d", "team", "pub-team",
                    "tags", new String[]{}, "visibility", "PUBLIC"),
                    jsonHeaders()), String.class);
        }
    }

    boolean elementExists(String slug) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM elements WHERE slug = ?", Integer.class, slug);
        return count != null && count > 0;
    }

    static byte[] zip(String version) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(("{\"name\":\"pub-skill\",\"version\":\"" + version
                + "\",\"description\":\"d\",\"type\":\"SKILL\"}")
                .getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("SKILL.md"));
            zos.write("# skill".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.finish();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    ResponseEntity<String> publish(String slug, String version, String changelog) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(zip(version)) {
            @Override
            public String getFilename() {
                return slug + "-" + version + ".zip";
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authHeader);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        String url = "/api/elements/" + slug + "/versions"
            + (changelog == null ? "" : "?changelog=" + changelog);
        return rest.postForEntity(url, new HttpEntity<>(body, headers), String.class);
    }

    HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, authHeader);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    void publishVersionCreatesMetadata() {
        ResponseEntity<String> r = publish("pub-skill", "1.0.0", "initial release");
        assertThat(r.getStatusCode().value()).isEqualTo(201);
        assertThat(r.getBody()).contains("\"version\":\"1.0.0\"").contains("SKILL.md");

        String latest = jdbc.queryForObject(
            "SELECT latest_version FROM elements WHERE slug='pub-skill'", String.class);
        assertThat(latest).isEqualTo("1.0.0");
    }

    @Test
    void duplicateVersionConflicts() {
        publish("pub-skill", "2.0.0", null);
        ResponseEntity<String> r = publish("pub-skill", "2.0.0", null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void invalidSemverIsUnprocessable() {
        ResponseEntity<String> r = publish("pub-skill", "not-semver", null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
```

Run: `mvn test -Dtest='VersionUseCaseTest,VersionPublishIT'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat: add version publishing use case with S3 upload and audit"
```

---

### Task 10: VersionUseCase — скачивание, latest, отдельный файл

**Files:**
- Modify: `src/main/java/com/skillhub/application/service/VersionUseCase.java` — `getVersion`, `listVersions`, `getArchive`, `getFile`
- Modify: `src/main/java/com/skillhub/adapters/in/rest/VersionController.java` — download endpoints
- Test: `src/test/java/com/skillhub/adapters/in/rest/VersionDownloadIT.java`

**Interfaces:**
- Consumes: `VersionUseCase.publish` (Task 9), `StoragePort.download`, `AccessService.canRead`.
- Produces:
  - `ElementVersion getVersion(String slug, String version, User viewer)` — `version == "latest"` → `findLatestPublished`; viewer=null — права пропускаются (для паков); 404/403.
  - `List<ElementVersion> listVersions(String slug, User viewer)`.
  - `byte[] getArchive(ElementVersion)` — из S3 + инкремент `elements.downloads_count`.
  - `byte[] getFile(ElementVersion, String path)` — извлечение из архива (path traversal → 422; нет пути → 404).
  - REST: `GET /api/elements/{slug}/versions` → список; `GET .../versions/{version}/download` → zip; `GET .../versions/{version}/files?path=...` → байты.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/skillhub/adapters/in/rest/VersionDownloadIT.java`:

```java
package com.skillhub.adapters.in.rest;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class VersionDownloadIT extends VersionPublishIT {

    HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, authHeader);
        return h;
    }

    @Test
    void downloadSpecificAndLatestVersion() {
        publish("pub-skill", "3.0.0", "three");
        publish("pub-skill", "3.1.0", "three-one");

        ResponseEntity<byte[]> latest = rest.exchange(
            "/api/elements/pub-skill/versions/latest/download", HttpMethod.GET,
            new HttpEntity<>(authHeaders()), byte[].class);
        assertThat(latest.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(latest.getHeaders().getContentType().toString()).contains("zip");

        ResponseEntity<byte[]> specific = rest.exchange(
            "/api/elements/pub-skill/versions/3.0.0/download", HttpMethod.GET,
            new HttpEntity<>(authHeaders()), byte[].class);
        assertThat(specific.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(specific.getBody(), StandardCharsets.ISO_8859_1)).contains("3.0.0");
    }

    @Test
    void listVersionsReturnsAll() {
        publish("pub-skill", "3.2.0", null);
        publish("pub-skill", "3.2.1", null);
        ResponseEntity<String> list = rest.exchange(
            "/api/elements/pub-skill/versions", HttpMethod.GET,
            new HttpEntity<>(authHeaders()), String.class);
        assertThat(list.getBody()).contains("3.2.0").contains("3.2.1");
    }

    @Test
    void downloadSingleFile() {
        publish("pub-skill", "4.0.0", "four");
        ResponseEntity<byte[]> file = rest.exchange(
            "/api/elements/pub-skill/versions/4.0.0/files?path=SKILL.md", HttpMethod.GET,
            new HttpEntity<>(authHeaders()), byte[].class);
        assertThat(file.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(file.getBody(), StandardCharsets.UTF_8)).isEqualTo("# skill");
    }

    @Test
    void unknownVersionIsNotFound() {
        ResponseEntity<String> r = rest.exchange(
            "/api/elements/pub-skill/versions/9.9.9/download", HttpMethod.GET,
            new HttpEntity<>(authHeaders()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void downloadsCounterIncrements() {
        publish("pub-skill", "5.0.0", "five");
        rest.exchange("/api/elements/pub-skill/versions/5.0.0/download", HttpMethod.GET,
            new HttpEntity<>(authHeaders()), byte[].class);
        Long count = jdbc.queryForObject(
            "SELECT downloads_count FROM elements WHERE slug='pub-skill'", Long.class);
        assertThat(count).isGreaterThanOrEqualTo(1);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=VersionDownloadIT`
Expected: FAIL — endpoints не существуют (404).

- [ ] **Step 3: Write implementation**

Добавьте в `application/service/VersionUseCase.java` (импорты: `java.io.ByteArrayInputStream`, `java.io.IOException`, `java.util.List`, `org.apache.commons.compress.archivers.zip.ZipArchiveInputStream`, `ZipArchiveEntry`, `com.skillhub.core.exception.UnprocessableException`):

```java
    @Transactional(readOnly = true)
    public ElementVersion getVersion(String slug, String version, User viewer) {
        Element element = elements.findBySlug(slug)
            .orElseThrow(() -> new NotFoundException("Element not found: " + slug));
        if (viewer != null && !access.canRead(element, viewer)) {
            throw new ForbiddenException("Element is not visible to you: " + slug);
        }
        return "latest".equals(version)
            ? versions.findLatestPublished(element.getId())
                .orElseThrow(() -> new NotFoundException("No published versions for: " + slug))
            : versions.findByElementIdAndVersion(element.getId(), version)
                .orElseThrow(() -> new NotFoundException(
                    "Version not found: " + slug + "@" + version));
    }

    @Transactional(readOnly = true)
    public List<ElementVersion> listVersions(String slug, User viewer) {
        Element element = elements.findBySlug(slug)
            .orElseThrow(() -> new NotFoundException("Element not found: " + slug));
        if (viewer != null && !access.canRead(element, viewer)) {
            throw new ForbiddenException("Element is not visible to you: " + slug);
        }
        return versions.findAllByElementIdOrderByCreatedAtDesc(element.getId());
    }

    @Transactional
    public byte[] getArchive(ElementVersion version) {
        byte[] data = storage.download(version.getS3_key());
        Element element = version.getElement();
        element.setDownloadsCount(element.getDownloadsCount() + 1);
        elements.save(element);
        return data;
    }

    @Transactional(readOnly = true)
    public byte[] getFile(ElementVersion version, String path) {
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.equals("..")
                || normalized.contains("../") || normalized.contains("/..")) {
            throw new UnprocessableException("Illegal file path", path);
        }
        byte[] archive = storage.download(version.getS3_key());
        try (ZipArchiveInputStream zin = new ZipArchiveInputStream(
                new ByteArrayInputStream(archive), "UTF-8", true, true)) {
            ZipArchiveEntry entry;
            while ((entry = zin.getNextZipEntry()) != null) {
                if (!entry.isDirectory()
                        && entry.getName().replace('\\', '/').equals(normalized)) {
                    return zin.readAllBytes();
                }
            }
        } catch (IOException e) {
            throw new UnprocessableException("Stored archive is corrupted", e.getMessage());
        }
        throw new NotFoundException("File not found in version: " + path);
    }
```

Добавьте в `adapters/in/rest/VersionController.java` (импорты: `com.skillhub.domain.model.ElementVersion`, `org.springframework.http.HttpHeaders`, `org.springframework.http.MediaType`, `java.util.List`):

```java
    @GetMapping
    public List<VersionResponse> list(@PathVariable String slug, Authentication auth) {
        return versionUseCase.listVersions(slug, currentUser.resolve(auth)).stream()
            .map(VersionResponse::from)
            .toList();
    }

    @GetMapping("/{version}/download")
    public ResponseEntity<byte[]> download(@PathVariable String slug,
                                           @PathVariable String version,
                                           Authentication auth) {
        ElementVersion v = versionUseCase.getVersion(slug, version, currentUser.resolve(auth));
        byte[] data = versionUseCase.getArchive(v);
        String filename = slug + "-" + v.getVersion() + ".zip";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(data);
    }

    @GetMapping("/{version}/files")
    public ResponseEntity<byte[]> file(@PathVariable String slug,
                                       @PathVariable String version,
                                       @RequestParam("path") String path,
                                       Authentication auth) {
        ElementVersion v = versionUseCase.getVersion(slug, version, currentUser.resolve(auth));
        byte[] data = versionUseCase.getFile(v, path);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(data);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=VersionDownloadIT`
Expected: PASS (вместе с родительскими тестами публикации).

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat: add version download, single-file access and downloads counter"
```

---

### Task 11: PackUseCase + PackController

**Files:**
- Create: `src/main/java/com/skillhub/application/service/PackUseCase.java`
- Create: `src/main/java/com/skillhub/adapters/in/rest/dto/PackResponse.java`, `PackContentRequest.java`
- Create: `src/main/java/com/skillhub/adapters/in/rest/PackController.java`
- Test: `src/test/java/com/skillhub/application/service/PackUseCaseTest.java` (unit, Mockito)
- Test: `src/test/java/com/skillhub/adapters/in/rest/PackApiIT.java` (IT)

**Interfaces:**
- Consumes: `ElementUseCase.getBySlug` (Task 8), `VersionUseCase.getVersion/getArchive` (Task 10), `AccessService` (Task 3), `PackContentRepositoryPort` (Task 3).
- Produces:
  - `PackUseCase` (`application/service/`, `@Service`): `PackContent addContent(String packSlug, String elementSlug, String versionConstraint, User user)` — элемент не PACK → 422, constraint не `latest`/semver → 422, нет прав → 403; `List<PackContent> getContents(String packSlug, User viewer)`; `byte[] downloadPack(String packSlug, User viewer)` — zip: `manifest.json` пака (`{"pack":"slug","contents":[{"element":"...","version":"..."}]}`) + файлы каждого элемента в подпапке `{element-slug}-{version}/`.
  - REST: `POST /api/packs/{slug}/contents` → 201; `GET /api/packs/{slug}` → 200; `GET /api/packs/{slug}/versions/{version}/download` → 200 zip.

`adapters/in/rest/dto/PackContentRequest.java`:

```java
package com.skillhub.adapters.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record PackContentRequest(
    @NotBlank String element,
    @NotBlank String versionConstraint
) {}
```

`adapters/in/rest/dto/PackResponse.java`:

```java
package com.skillhub.adapters.in.rest.dto;

import com.skillhub.domain.model.PackContent;

import java.util.List;

public record PackResponse(String slug, List<ContentDto> contents) {

    public record ContentDto(String element, String version, String versionConstraint) {}

    public static PackResponse from(String slug, List<PackContent> contents) {
        return new PackResponse(slug, contents.stream()
            .map(c -> new ContentDto(c.getElement().getSlug(),
                c.getElement().getLatestVersion(),
                c.getVersionConstraint()))
            .toList());
    }
}
```

- [ ] **Step 1: Write the failing unit test**

`src/test/java/com/skillhub/application/service/PackUseCaseTest.java`:

```java
package com.skillhub.application.service;

import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.core.exception.UnprocessableException;
import com.skillhub.domain.model.*;
import com.skillhub.domain.port.*;
import com.skillhub.domain.service.AccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PackUseCaseTest {

    ElementUseCase elementUseCase;
    VersionUseCase versionUseCase;
    PackContentRepositoryPort packContents;
    TeamMembershipPort membership;
    PackUseCase useCase;

    User owner = User.builder().id(UUID.randomUUID()).ssoSubject("s").email("e")
        .displayName("Owner").admin(false).createdAt(Instant.now()).build();
    Team team = Team.builder().id(UUID.randomUUID()).slug("t").name("T")
        .createdAt(Instant.now()).build();

    Element packElement() {
        return Element.builder().id(UUID.randomUUID()).slug("my-pack")
            .type(ElementType.PACK).name("my-pack").description("").team(team)
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    Element skillElement() {
        return Element.builder().id(UUID.randomUUID()).slug("skill-a")
            .type(ElementType.SKILL).name("skill-a").description("").team(team)
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
            .latestVersion("1.0.0").downloadsCount(0)
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    ElementVersion versionOf(Element el) {
        return ElementVersion.builder().id(UUID.randomUUID()).element(el)
            .version("1.0.0").status(VersionStatus.PUBLISHED).changelog("")
            .s3_key("t/skill-a/1.0.0.zip").sizeBytes(1).fileIndex("{}")
            .publishedBy(owner).createdAt(Instant.now()).publishedAt(Instant.now()).build();
    }

    @BeforeEach
    void setUp() {
        elementUseCase = mock(ElementUseCase.class);
        versionUseCase = mock(VersionUseCase.class);
        packContents = mock(PackContentRepositoryPort.class);
        membership = mock(TeamMembershipPort.class);
        when(membership.roleOf(team.getId(), owner.getId()))
            .thenReturn(Optional.of(TeamRole.OWNER));
        when(packContents.save(any())).thenAnswer(inv -> inv.getArgument(0));
        useCase = new PackUseCase(packContents, elementUseCase, versionUseCase,
            new AccessService(membership));
    }

    @Test
    void addContentToPack() {
        when(elementUseCase.getBySlug("my-pack", owner)).thenReturn(packElement());
        when(elementUseCase.getBySlug("skill-a", owner)).thenReturn(skillElement());

        PackContent content = useCase.addContent("my-pack", "skill-a", "1.0.0", owner);
        assertThat(content.getElement().getSlug()).isEqualTo("skill-a");
        assertThat(content.getVersionConstraint()).isEqualTo("1.0.0");
    }

    @Test
    void nonPackTargetIsUnprocessable() {
        when(elementUseCase.getBySlug("skill-a", owner)).thenReturn(skillElement());
        assertThatThrownBy(() -> useCase.addContent("skill-a", "skill-a", "latest", owner))
            .isInstanceOf(UnprocessableException.class);
    }

    @Test
    void invalidConstraintIsUnprocessable() {
        when(elementUseCase.getBySlug("my-pack", owner)).thenReturn(packElement());
        when(elementUseCase.getBySlug("skill-a", owner)).thenReturn(skillElement());
        assertThatThrownBy(() -> useCase.addContent("my-pack", "skill-a", "1.x", owner))
            .isInstanceOf(UnprocessableException.class);
    }

    @Test
    void nonPublisherForbidden() {
        when(elementUseCase.getBySlug("my-pack", owner)).thenReturn(packElement());
        when(elementUseCase.getBySlug("skill-a", owner)).thenReturn(skillElement());
        when(membership.roleOf(team.getId(), owner.getId()))
            .thenReturn(Optional.of(TeamRole.MEMBER));
        assertThatThrownBy(() -> useCase.addContent("my-pack", "skill-a", "latest", owner))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void downloadPackBuildsZipWithManifestAndElements() throws Exception {
        Element pack = packElement();
        Element skill = skillElement();
        when(elementUseCase.getBySlug("my-pack", owner)).thenReturn(pack);
        when(packContents.findAllByPackId(pack.getId()))
            .thenReturn(List.of(PackContent.builder()
                .packElement(pack).element(skill).versionConstraint("1.0.0").build()));
        when(versionUseCase.getVersion("skill-a", "1.0.0", null)).thenReturn(versionOf(skill));
        when(versionUseCase.getArchive(any())).thenReturn(zipOf(Map.of("SKILL.md", "# skill")));

        byte[] packZip = useCase.downloadPack("my-pack", owner);

        boolean foundManifest = false;
        boolean foundSkill = false;
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(packZip))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (entry.getName().equals("manifest.json")) {
                    foundManifest = true;
                }
                if (entry.getName().equals("skill-a-1.0.0/SKILL.md")) {
                    foundSkill = true;
                }
            }
        }
        assertThat(foundManifest).isTrue();
        assertThat(foundSkill).isTrue();
    }

    static byte[] zipOf(Map<String, String> entries) {
        try (var bos = new java.io.ByteArrayOutputStream();
             var zos = new java.util.zip.ZipOutputStream(bos)) {
            entries.forEach((name, content) -> {
                try {
                    zos.putNextEntry(new ZipEntry(name));
                    zos.write(content.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            zos.finish();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=PackUseCaseTest`
Expected: FAIL — класс не существует.

- [ ] **Step 3: Write implementation**

`application/service/PackUseCase.java`:

```java
package com.skillhub.application.service;

import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.core.exception.UnprocessableException;
import com.skillhub.domain.model.*;
import com.skillhub.domain.port.PackContentRepositoryPort;
import com.skillhub.domain.service.AccessService;
import com.skillhub.domain.service.ArchiveService;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class PackUseCase {

    private final PackContentRepositoryPort packContents;
    private final ElementUseCase elementUseCase;
    private final VersionUseCase versionUseCase;
    private final AccessService access;

    public PackUseCase(PackContentRepositoryPort packContents,
                       ElementUseCase elementUseCase,
                       VersionUseCase versionUseCase,
                       AccessService access) {
        this.packContents = packContents;
        this.elementUseCase = elementUseCase;
        this.versionUseCase = versionUseCase;
        this.access = access;
    }

    @Transactional
    public PackContent addContent(String packSlug, String elementSlug,
                                  String versionConstraint, User user) {
        Element pack = elementUseCase.getBySlug(packSlug, user);
        if (pack.getType() != ElementType.PACK) {
            throw new UnprocessableException("Element is not a PACK: " + packSlug, null);
        }
        if (!access.canPublish(pack.getTeam(), user)) {
            throw new ForbiddenException("Only OWNER/MAINTAINER can modify this pack");
        }
        Element element = elementUseCase.getBySlug(elementSlug, user);
        validateConstraint(versionConstraint);
        return packContents.save(PackContent.builder()
            .packElement(pack)
            .element(element)
            .versionConstraint(versionConstraint)
            .build());
    }

    @Transactional(readOnly = true)
    public List<PackContent> getContents(String packSlug, User viewer) {
        Element pack = elementUseCase.getBySlug(packSlug, viewer);
        return packContents.findAllByPackId(pack.getId());
    }

    @Transactional(readOnly = true)
    public byte[] downloadPack(String packSlug, User viewer) {
        Element pack = elementUseCase.getBySlug(packSlug, viewer);
        List<PackContent> contents = packContents.findAllByPackId(pack.getId());

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipArchiveOutputStream zos = new ZipArchiveOutputStream(bos)) {

            StringBuilder manifest = new StringBuilder("{\"pack\":\"")
                .append(pack.getSlug()).append("\",\"contents\":[");
            boolean first = true;
            for (PackContent content : contents) {
                ElementVersion version = versionUseCase.getVersion(
                    content.getElement().getSlug(), content.getVersionConstraint(), null);
                if (!first) {
                    manifest.append(",");
                }
                first = false;
                manifest.append("{\"element\":\"").append(content.getElement().getSlug())
                    .append("\",\"version\":\"").append(version.getVersion()).append("\"}");
                copyElementArchive(zos, version,
                    content.getElement().getSlug() + "-" + version.getVersion() + "/");
            }
            manifest.append("]}");

            zos.putArchiveEntry(new ZipArchiveEntry("manifest.json"));
            zos.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeArchiveEntry();
            zos.finish();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UnprocessableException("Failed to build pack archive", e.getMessage());
        }
    }

    private void copyElementArchive(ZipArchiveOutputStream zos, ElementVersion version,
                                    String prefix) throws IOException {
        byte[] archive = versionUseCase.getArchive(version);
        try (ZipArchiveInputStream zin = new ZipArchiveInputStream(
                new ByteArrayInputStream(archive), "UTF-8", true, true)) {
            ZipArchiveEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zin.getNextZipEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                zos.putArchiveEntry(new ZipArchiveEntry(prefix + entry.getName()));
                int read;
                while ((read = zin.read(buffer)) != -1) {
                    zos.write(buffer, 0, read);
                }
                zos.closeArchiveEntry();
            }
        }
    }

    private void validateConstraint(String constraint) {
        if (!"latest".equals(constraint)
                && !ArchiveService.SEMVER.matcher(constraint).matches()) {
            throw new UnprocessableException(
                "versionConstraint must be 'latest' or exact semver", constraint);
        }
    }
}
```

`adapters/in/rest/PackController.java`:

```java
package com.skillhub.adapters.in.rest;

import com.skillhub.adapters.in.rest.dto.PackContentRequest;
import com.skillhub.adapters.in.rest.dto.PackResponse;
import com.skillhub.adapters.in.security.CurrentUserResolver;
import com.skillhub.application.service.PackUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/packs/{slug}")
@RequiredArgsConstructor
public class PackController {

    private final PackUseCase packUseCase;
    private final CurrentUserResolver currentUser;

    @PostMapping("/contents")
    public ResponseEntity<PackResponse> addContent(@PathVariable String slug,
                                                   @Valid @RequestBody PackContentRequest req,
                                                   Authentication auth) {
        packUseCase.addContent(slug, req.element(), req.versionConstraint(), currentUser.resolve(auth));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(PackResponse.from(slug, packUseCase.getContents(slug, currentUser.resolve(auth))));
    }

    @GetMapping
    public PackResponse get(@PathVariable String slug, Authentication auth) {
        return PackResponse.from(slug, packUseCase.getContents(slug, currentUser.resolve(auth)));
    }

    @GetMapping("/versions/{version}/download")
    public ResponseEntity<byte[]> download(@PathVariable String slug,
                                           @PathVariable String version,
                                           Authentication auth) {
        byte[] data = packUseCase.downloadPack(slug, currentUser.resolve(auth));
        String filename = slug + "-" + version + ".zip";
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(data);
    }
}
```

- [ ] **Step 4: Write the failing IT and run**

`src/test/java/com/skillhub/adapters/in/rest/PackApiIT.java`:

```java
package com.skillhub.adapters.in.rest;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PackApiIT extends VersionPublishIT {

    void createPack(String slug) {
        rest.exchange("/api/elements", HttpMethod.POST,
            new HttpEntity<>(Map.of(
                "slug", slug, "type", "PACK", "name", slug, "description", "pack",
                "team", "pub-team", "tags", new String[]{}, "visibility", "PUBLIC"),
                jsonHeaders()), String.class);
    }

    void addContent(String packSlug, String elementSlug, String constraint) {
        rest.exchange("/api/packs/" + packSlug + "/contents", HttpMethod.POST,
            new HttpEntity<>(Map.of("element", elementSlug, "versionConstraint", constraint),
                jsonHeaders()), String.class);
    }

    @Test
    void addContentAndDownloadPack() {
        publish("pub-skill", "6.0.0", "six");
        createPack("my-pack");
        addContent("my-pack", "pub-skill", "6.0.0");

        ResponseEntity<byte[]> download = rest.exchange(
            "/api/packs/my-pack/versions/latest/download", HttpMethod.GET,
            new HttpEntity<>(jsonHeaders()), byte[].class);
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);

        boolean foundManifest = false;
        boolean foundSkill = false;
        try (ZipInputStream zin = new ZipInputStream(
                new ByteArrayInputStream(download.getBody()))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (entry.getName().equals("manifest.json")) {
                    foundManifest = true;
                }
                if (entry.getName().equals("pub-skill-6.0.0/SKILL.md")) {
                    foundSkill = true;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertThat(foundManifest).isTrue();
        assertThat(foundSkill).isTrue();
    }

    @Test
    void getPackShowsContents() {
        publish("pub-skill", "7.0.0", "seven");
        createPack("listed-pack");
        addContent("listed-pack", "pub-skill", "latest");
        ResponseEntity<String> r = rest.exchange("/api/packs/listed-pack", HttpMethod.GET,
            new HttpEntity<>(jsonHeaders()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("pub-skill").contains("latest");
    }

    @Test
    void nonPackElementIsUnprocessable() {
        publish("pub-skill", "8.0.0", "eight");
        ResponseEntity<String> r = rest.exchange("/api/packs/pub-skill/contents",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("element", "pub-skill", "versionConstraint", "latest"),
                jsonHeaders()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
```

Run: `mvn test -Dtest='PackUseCaseTest,PackApiIT'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat: add pack use case and REST endpoints"
```

---

### Task 12: TeamUseCase + CategoryUseCase + контроллеры

**Files:**
- Create: `src/main/java/com/skillhub/application/service/TeamUseCase.java`
- Create: `src/main/java/com/skillhub/application/service/CategoryUseCase.java`
- Modify: `src/main/java/com/skillhub/domain/port/TeamMembershipPort.java` — метод `save`
- Modify: `src/main/java/com/skillhub/domain/port/TeamRepositoryPort.java` — метод `findAll`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/JpaTeamMembershipAdapter.java` — реализация `save`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/JpaTeamRepositoryAdapter.java` — реализация `findAll`
- Create: `src/main/java/com/skillhub/adapters/in/rest/dto/TeamResponse.java`, `CreateTeamRequest.java`, `AddMemberRequest.java`, `CategoryResponse.java`, `CreateCategoryRequest.java`
- Create: `src/main/java/com/skillhub/adapters/in/rest/TeamController.java`, `CategoryController.java`
- Test: `src/test/java/com/skillhub/application/service/TeamUseCaseTest.java` (unit, Mockito)
- Test: `src/test/java/com/skillhub/adapters/in/rest/CategoryTeamApiIT.java` (IT)

**Interfaces:**
- Consumes: `TeamRepositoryPort`, `TeamMembershipPort`, `CategoryRepositoryPort`, `UserRepositoryPort` (Task 3), `ClockPort` (Task 6).
- Produces:
  - `TeamUseCase`: `Team create(String slug, String name, User creator)` (дубликат → 409, creator = OWNER); `List<Team> list()`; `TeamMembership addMember(String teamSlug, String ssoSubject, String role, User actor)` (только OWNER или админ → иначе 403; пользователь не найден → 404).
  - `CategoryUseCase`: `Category create(String slug, String name, String parentSlug, String icon, User actor)` (не админ → 403, дубликат → 409); `List<Category> list()`.
  - REST: `GET/POST /api/categories`, `GET/POST /api/teams`, `POST /api/teams/{slug}/members`.

`adapters/in/rest/dto/TeamResponse.java`:

```java
package com.skillhub.adapters.in.rest.dto;

import com.skillhub.domain.model.Team;

public record TeamResponse(String slug, String name) {
    public static TeamResponse from(Team t) {
        return new TeamResponse(t.getSlug(), t.getName());
    }
}
```

`adapters/in/rest/dto/CreateTeamRequest.java`:

```java
package com.skillhub.adapters.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateTeamRequest(
    @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]*$", message = "slug must be kebab-case")
    String slug,
    @NotBlank String name
) {}
```

`adapters/in/rest/dto/AddMemberRequest.java`:

```java
package com.skillhub.adapters.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AddMemberRequest(
    @NotBlank String ssoSubject,
    @NotBlank @Pattern(regexp = "OWNER|MAINTAINER|MEMBER") String role
) {}
```

`adapters/in/rest/dto/CategoryResponse.java`:

```java
package com.skillhub.adapters.in.rest.dto;

import com.skillhub.domain.model.Category;

public record CategoryResponse(String slug, String name, String parent, String icon) {
    public static CategoryResponse from(Category c) {
        return new CategoryResponse(c.getSlug(), c.getName(),
            c.getParent() == null ? null : c.getParent().getSlug(), c.getIcon());
    }
}
```

`adapters/in/rest/dto/CreateCategoryRequest.java`:

```java
package com.skillhub.adapters.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCategoryRequest(
    @NotBlank String slug,
    @NotBlank String name,
    String parentSlug,
    String icon
) {}
```

- [ ] **Step 1: Write the failing unit test**

`src/test/java/com/skillhub/application/service/TeamUseCaseTest.java`:

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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeamUseCaseTest {

    TeamRepositoryPort teams;
    TeamMembershipPort membership;
    UserRepositoryPort users;
    ClockPort clock;
    TeamUseCase useCase;

    User creator = User.builder().id(UUID.randomUUID()).ssoSubject("s").email("e")
        .displayName("Creator").admin(false).createdAt(Instant.now()).build();

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
    void duplicateTeamConflicts() {
        when(teams.findBySlug("design")).thenReturn(Optional.of(Team.builder().build()));
        assertThatThrownBy(() -> useCase.create("design", "Design", creator))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void onlyOwnerAddsMember() {
        Team team = useCase.create("ux", "UX", creator);
        User newMember = User.builder().id(UUID.randomUUID()).ssoSubject("m")
            .email("m").displayName("M").admin(false).createdAt(Instant.now()).build();
        when(users.findBySsoSubject("m")).thenReturn(Optional.of(newMember));
        when(membership.roleOf(team.getId(), creator.getId()))
            .thenReturn(Optional.of(TeamRole.MEMBER));

        assertThatThrownBy(() -> useCase.addMember("ux", "m", "MEMBER", creator))
            .isInstanceOf(ForbiddenException.class);

        when(membership.roleOf(team.getId(), creator.getId()))
            .thenReturn(Optional.of(TeamRole.OWNER));
        TeamMembership added = useCase.addMember("ux", "m", "MEMBER", creator);
        assertThat(added.role()).isEqualTo(TeamRole.MEMBER);
    }

    @Test
    void unknownUserIsNotFound() {
        Team team = useCase.create("ux", "UX", creator);
        when(membership.roleOf(team.getId(), creator.getId()))
            .thenReturn(Optional.of(TeamRole.OWNER));
        when(users.findBySsoSubject("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.addMember("ux", "ghost", "MEMBER", creator))
            .isInstanceOf(NotFoundException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=TeamUseCaseTest`
Expected: FAIL — класс не существует.

- [ ] **Step 3: Write implementation**

`application/service/TeamUseCase.java`:

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

@Service
public class TeamUseCase {

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

    @Transactional
    public TeamMembership addMember(String teamSlug, String ssoSubject, String role, User actor) {
        Team team = teams.findBySlug(teamSlug)
            .orElseThrow(() -> new NotFoundException("Team not found: " + teamSlug));
        if (!actor.isAdmin()) {
            membership.roleOf(team.getId(), actor.getId())
                .filter(r -> r == TeamRole.OWNER)
                .orElseThrow(() -> new ForbiddenException("Only team OWNER can add members"));
        }
        User newMember = users.findBySsoSubject(ssoSubject)
            .orElseThrow(() -> new NotFoundException("User not found: " + ssoSubject));
        TeamRole teamRole = TeamRole.valueOf(role);
        membership.save(TeamMembership.builder()
            .teamId(team.getId()).userId(newMember.getId()).role(teamRole).build());
        return new TeamMembership(team.getId(), newMember.getId(), teamRole);
    }
}
```

Методы отсутствуют в портах — добавьте:

`TeamMembershipPort` (Task 3):

```java
    TeamMembership save(TeamMembership membership);
```

`TeamRepositoryPort` (Task 3):

```java
    java.util.List<Team> findAll();
```

Реализации в `JpaTeamMembershipAdapter` (Task 4):

```java
    @Override
    @Transactional
    public com.skillhub.domain.model.TeamMembership save(com.skillhub.domain.model.TeamMembership m) {
        var teamRef = teamRepository.getReferenceById(m.teamId());
        var userRef = userRepository.getReferenceById(m.userId());
        jpa.save(JpaTeamMember.builder().team(teamRef).user(userRef).role(m.role().name()).build());
        return m;
    }
```

(в конструктор адаптера добавьте `com.skillhub.adapters.out.jpa.repository.JpaTeamRepository teamRepository`, `com.skillhub.adapters.out.jpa.repository.JpaUserRepository userRepository` — Lombok `@RequiredArgsConstructor`)

и в `JpaTeamRepositoryAdapter` (Task 4):

```java
    @Override
    @Transactional(readOnly = true)
    public java.util.List<Team> findAll() {
        return jpa.findAll().stream().map(TeamJpaMapper::toDomain).toList();
    }
```

`application/service/CategoryUseCase.java`:

```java
package com.skillhub.application.service;

import com.skillhub.core.exception.ConflictException;
import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.core.exception.NotFoundException;
import com.skillhub.domain.model.Category;
import com.skillhub.domain.model.User;
import com.skillhub.domain.port.CategoryRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryUseCase {

    private final CategoryRepositoryPort categories;

    public CategoryUseCase(CategoryRepositoryPort categories) {
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<Category> list() {
        return categories.findAllOrderedByName();
    }

    @Transactional
    public Category create(String slug, String name, String parentSlug, String icon, User actor) {
        if (!actor.isAdmin()) {
            throw new ForbiddenException("Only admin can manage categories");
        }
        if (categories.existsBySlug(slug)) {
            throw new ConflictException("Category already exists: " + slug);
        }
        Category parent = parentSlug == null ? null
            : categories.findBySlug(parentSlug)
                .orElseThrow(() -> new NotFoundException("Parent category not found: " + parentSlug));
        return categories.save(Category.builder()
            .slug(slug).name(name).parent(parent).icon(icon).build());
    }
}
```

`adapters/in/rest/CategoryController.java`:

```java
package com.skillhub.adapters.in.rest;

import com.skillhub.adapters.in.rest.dto.CategoryResponse;
import com.skillhub.adapters.in.rest.dto.CreateCategoryRequest;
import com.skillhub.adapters.in.security.CurrentUserResolver;
import com.skillhub.application.service.CategoryUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryUseCase categoryUseCase;
    private final CurrentUserResolver currentUser;

    @GetMapping
    public List<CategoryResponse> list() {
        return categoryUseCase.list().stream().map(CategoryResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest req,
                                                   Authentication auth) {
        CategoryResponse created = CategoryResponse.from(categoryUseCase.create(
            req.slug(), req.name(), req.parentSlug(), req.icon(), currentUser.resolve(auth)));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
```

`adapters/in/rest/TeamController.java`:

```java
package com.skillhub.adapters.in.rest;

import com.skillhub.adapters.in.rest.dto.AddMemberRequest;
import com.skillhub.adapters.in.rest.dto.CreateTeamRequest;
import com.skillhub.adapters.in.rest.dto.TeamResponse;
import com.skillhub.adapters.in.security.CurrentUserResolver;
import com.skillhub.application.service.TeamUseCase;
import com.skillhub.domain.model.TeamMembership;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamUseCase teamUseCase;
    private final CurrentUserResolver currentUser;

    @GetMapping
    public List<TeamResponse> list() {
        return teamUseCase.list().stream().map(TeamResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<TeamResponse> create(@Valid @RequestBody CreateTeamRequest req,
                                               Authentication auth) {
        TeamResponse created = TeamResponse.from(
            teamUseCase.create(req.slug(), req.name(), currentUser.resolve(auth)));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{slug}/members")
    public ResponseEntity<Map<String, String>> addMember(@PathVariable String slug,
                                                         @Valid @RequestBody AddMemberRequest req,
                                                         Authentication auth) {
        TeamMembership member = teamUseCase.addMember(
            slug, req.ssoSubject(), req.role(), currentUser.resolve(auth));
        return ResponseEntity.ok(Map.of(
            "ssoSubject", member.userId().toString(),
            "role", member.role().name()));
    }
}
```

- [ ] **Step 4: Write the failing IT and run**

`src/test/java/com/skillhub/adapters/in/rest/CategoryTeamApiIT.java`:

```java
package com.skillhub.adapters.in.rest;

import com.skillhub.application.service.ApiTokenService;
import com.skillhub.application.service.UserSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CategoryTeamApiIT {

    @Autowired TestRestTemplate rest;
    @Autowired UserSyncService users;
    @Autowired ApiTokenService tokens;

    String adminHeader;
    String memberHeader;
    String adminSubject = "cat-admin";

    @BeforeEach
    void setUp() {
        var admin = users.syncFromSso(adminSubject, "admin@skillhub.io", "Admin");
        admin.setAdmin(true);
        adminHeader = "Bearer " + tokens.createToken(admin, "admin").rawToken();

        var member = users.syncFromSso("cat-member", "member@skillhub.io", "Member");
        memberHeader = "Bearer " + tokens.createToken(member, "member").rawToken();
    }

    HttpHeaders headers(String token) {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    void adminCreatesCategoryAnyUserLists() {
        ResponseEntity<String> created = rest.exchange("/api/categories", HttpMethod.POST,
            new HttpEntity<>(Map.of("slug", "docs", "name", "Работа с документами"),
                headers(adminHeader)), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> list = rest.exchange("/api/categories", HttpMethod.GET,
            new HttpEntity<>(headers(memberHeader)), String.class);
        assertThat(list.getBody()).contains("Работа с документами");
    }

    @Test
    void nonAdminCannotCreateCategory() {
        ResponseEntity<String> r = rest.exchange("/api/categories", HttpMethod.POST,
            new HttpEntity<>(Map.of("slug", "forbidden-cat", "name", "X"),
                headers(memberHeader)), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createTeamCreatorBecomesOwner() {
        ResponseEntity<String> created = rest.exchange("/api/teams", HttpMethod.POST,
            new HttpEntity<>(Map.of("slug", "design-team", "name", "Design"),
                headers(memberHeader)), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).contains("design-team");
    }

    @Test
    void ownerAddsMember() {
        rest.exchange("/api/teams", HttpMethod.POST,
            new HttpEntity<>(Map.of("slug", "ux-team", "name", "UX"),
                headers(memberHeader)), String.class);

        ResponseEntity<String> added = rest.exchange("/api/teams/ux-team/members",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("ssoSubject", adminSubject, "role", "MEMBER"),
                headers(memberHeader)), String.class);
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
```

Run: `mvn test -Dtest='TeamUseCaseTest,CategoryTeamApiIT'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat: add team and category use cases with REST endpoints"
```

---

### Task 13: SocialUseCase — рейтинги, отзывы, избранное

**Files:**
- Create: `src/main/java/com/skillhub/application/service/SocialUseCase.java`
- Create: `src/main/java/com/skillhub/adapters/in/rest/dto/RateRequest.java`, `ReviewRequest.java`, `ReviewResponse.java`
- Create: `src/main/java/com/skillhub/adapters/in/rest/SocialController.java`
- Test: `src/test/java/com/skillhub/application/service/SocialUseCaseTest.java` (unit, Mockito)
- Test: `src/test/java/com/skillhub/adapters/in/rest/SocialApiIT.java` (IT)

**Interfaces:**
- Consumes: `ElementUseCase.getBySlug` (Task 8), `RatingRepositoryPort`, `ReviewRepositoryPort`, `FavoriteRepositoryPort` (Task 3), `ClockPort` (Task 6).
- Produces:
  - `SocialUseCase` (`application/service/`, `@Service`): `void rate(slug, user, rating)` (1–5, иначе 422); `void review(slug, user, rating, text)`; `List<Review> reviews(slug, viewer)`; `boolean setFavorite(slug, user, add)`; `record SocialInfo(double avgRating, long ratingCount, boolean favorited) info(slug, viewer)`.
  - REST: `PUT /api/elements/{slug}/rating`, `PUT /api/elements/{slug}/review`, `GET /api/elements/{slug}/reviews`, `POST/DELETE /api/elements/{slug}/favorite`, `GET /api/elements/{slug}/social`.

`adapters/in/rest/dto/RateRequest.java`:

```java
package com.skillhub.adapters.in.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RateRequest(@Min(1) @Max(5) int rating) {}
```

`adapters/in/rest/dto/ReviewRequest.java`:

```java
package com.skillhub.adapters.in.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ReviewRequest(@Min(1) @Max(5) int rating, @NotBlank String text) {}
```

`adapters/in/rest/dto/ReviewResponse.java`:

```java
package com.skillhub.adapters.in.rest.dto;

import com.skillhub.domain.model.Review;

public record ReviewResponse(String author, int rating, String text, String createdAt) {
    public static ReviewResponse from(Review r) {
        return new ReviewResponse(r.getUser().getDisplayName(), r.getRating(),
            r.getText(), r.getCreatedAt().toString());
    }
}
```

- [ ] **Step 1: Write the failing unit test**

`src/test/java/com/skillhub/application/service/SocialUseCaseTest.java`:

```java
package com.skillhub.application.service;

import com.skillhub.core.exception.UnprocessableException;
import com.skillhub.domain.model.*;
import com.skillhub.domain.port.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SocialUseCaseTest {

    ElementUseCase elementUseCase;
    RatingRepositoryPort ratings;
    ReviewRepositoryPort reviews;
    FavoriteRepositoryPort favorites;
    ClockPort clock;
    SocialUseCase useCase;

    User user = User.builder().id(UUID.randomUUID()).ssoSubject("s").email("e")
        .displayName("U").admin(false).createdAt(Instant.now()).build();
    Element element = Element.builder().id(UUID.randomUUID()).slug("el")
        .type(ElementType.SKILL).name("el").description("")
        .team(Team.builder().id(UUID.randomUUID()).slug("t").name("T")
            .createdAt(Instant.now()).build())
        .tags(new String[0]).visibility(Visibility.PUBLIC).author(user)
        .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();

    @BeforeEach
    void setUp() {
        elementUseCase = mock(ElementUseCase.class);
        ratings = mock(RatingRepositoryPort.class);
        reviews = mock(ReviewRepositoryPort.class);
        favorites = mock(FavoriteRepositoryPort.class);
        clock = mock(ClockPort.class);
        when(clock.now()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(elementUseCase.getBySlug("el", user)).thenReturn(element);
        when(ratings.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ratings.avgRating(element.getId())).thenReturn(4.0);
        when(ratings.countByElementId(element.getId())).thenReturn(1L);
        useCase = new SocialUseCase(elementUseCase, ratings, reviews, favorites, clock);
    }

    @Test
    void rateSavesRating() {
        useCase.rate("el", user, 5);
        org.mockito.Mockito.verify(ratings).save(new Rating(element.getId(), user.getId(), 5));
    }

    @Test
    void invalidRatingIsUnprocessable() {
        assertThatThrownBy(() -> useCase.rate("el", user, 9))
            .isInstanceOf(UnprocessableException.class);
    }

    @Test
    void reviewUpserts() {
        when(reviews.findByElementIdAndUserId(element.getId(), user.getId()))
            .thenReturn(Optional.empty());
        useCase.review("el", user, 4, "good");
        org.mockito.Mockito.verify(reviews).save(any(Review.class));

        Review existing = Review.builder().id(UUID.randomUUID())
            .element(element).user(user).rating(3).text("old")
            .createdAt(Instant.now()).build();
        when(reviews.findByElementIdAndUserId(element.getId(), user.getId()))
            .thenReturn(Optional.of(existing));
        useCase.review("el", user, 5, "updated");
        assertThat(existing.getRating()).isEqualTo(5);
        assertThat(existing.getText()).isEqualTo("updated");
    }

    @Test
    void favoriteToggle() {
        when(favorites.findByUserIdAndElementId(user.getId(), element.getId()))
            .thenReturn(Optional.empty());
        assertThat(useCase.setFavorite("el", user, true)).isTrue();
        assertThat(useCase.setFavorite("el", user, false)).isFalse();
    }

    @Test
    void socialInfoReturnsAverageAndCount() {
        when(favorites.findByUserIdAndElementId(user.getId(), element.getId()))
            .thenReturn(Optional.empty());
        SocialUseCase.SocialInfo info = useCase.info("el", user);
        assertThat(info.avgRating()).isEqualTo(4.0);
        assertThat(info.ratingCount()).isEqualTo(1L);
        assertThat(info.favorited()).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=SocialUseCaseTest`
Expected: FAIL — класс не существует.

- [ ] **Step 3: Write implementation**

`application/service/SocialUseCase.java`:

```java
package com.skillhub.application.service;

import com.skillhub.core.exception.UnprocessableException;
import com.skillhub.domain.model.*;
import com.skillhub.domain.port.ClockPort;
import com.skillhub.domain.port.FavoriteRepositoryPort;
import com.skillhub.domain.port.RatingRepositoryPort;
import com.skillhub.domain.port.ReviewRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SocialUseCase {

    public record SocialInfo(double avgRating, long ratingCount, boolean favorited) {}

    private final ElementUseCase elementUseCase;
    private final RatingRepositoryPort ratings;
    private final ReviewRepositoryPort reviews;
    private final FavoriteRepositoryPort favorites;
    private final ClockPort clock;

    public SocialUseCase(ElementUseCase elementUseCase, RatingRepositoryPort ratings,
                         ReviewRepositoryPort reviews, FavoriteRepositoryPort favorites,
                         ClockPort clock) {
        this.elementUseCase = elementUseCase;
        this.ratings = ratings;
        this.reviews = reviews;
        this.favorites = favorites;
        this.clock = clock;
    }

    @Transactional
    public void rate(String slug, User user, int rating) {
        validateRating(rating);
        Element element = elementUseCase.getBySlug(slug, user);
        ratings.save(new Rating(element.getId(), user.getId(), rating));
    }

    @Transactional
    public void review(String slug, User user, int rating, String text) {
        validateRating(rating);
        Element element = elementUseCase.getBySlug(slug, user);
        reviews.findByElementIdAndUserId(element.getId(), user.getId())
            .ifPresentOrElse(existing -> {
                existing.setRating(rating);
                existing.setText(text);
                reviews.save(existing);
            }, () -> reviews.save(Review.builder()
                .element(element).user(user).rating(rating).text(text)
                .createdAt(clock.now()).build()));
    }

    @Transactional(readOnly = true)
    public List<Review> reviews(String slug, User viewer) {
        Element element = elementUseCase.getBySlug(slug, viewer);
        return reviews.findAllByElementIdOrderByCreatedAtDesc(element.getId());
    }

    @Transactional
    public boolean setFavorite(String slug, User user, boolean add) {
        Element element = elementUseCase.getBySlug(slug, user);
        if (add) {
            favorites.save(new Favorite(user.getId(), element.getId(), clock.now()));
        } else {
            favorites.delete(new Favorite(user.getId(), element.getId(), clock.now()));
        }
        return add;
    }

    @Transactional(readOnly = true)
    public SocialInfo info(String slug, User viewer) {
        Element element = elementUseCase.getBySlug(slug, viewer);
        double avg = ratings.avgRating(element.getId());
        long count = ratings.countByElementId(element.getId());
        boolean favorited = viewer != null
            && favorites.findByUserIdAndElementId(viewer.getId(), element.getId()).isPresent();
        return new SocialInfo(avg, count, favorited);
    }

    private void validateRating(int rating) {
        if (rating < 1 || rating > 5) {
            throw new UnprocessableException("Rating must be between 1 and 5", rating);
        }
    }
}
```

`adapters/in/rest/SocialController.java`:

```java
package com.skillhub.adapters.in.rest;

import com.skillhub.adapters.in.rest.dto.RateRequest;
import com.skillhub.adapters.in.rest.dto.ReviewRequest;
import com.skillhub.adapters.in.rest.dto.ReviewResponse;
import com.skillhub.adapters.in.security.CurrentUserResolver;
import com.skillhub.application.service.SocialUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/elements/{slug}")
@RequiredArgsConstructor
public class SocialController {

    private final SocialUseCase socialUseCase;
    private final CurrentUserResolver currentUser;

    @PutMapping("/rating")
    public Map<String, Integer> rate(@PathVariable String slug,
                                     @Valid @RequestBody RateRequest request,
                                     Authentication auth) {
        socialUseCase.rate(slug, currentUser.resolve(auth), request.rating());
        return Map.of("rating", request.rating());
    }

    @PutMapping("/review")
    public Map<String, Integer> review(@PathVariable String slug,
                                       @Valid @RequestBody ReviewRequest request,
                                       Authentication auth) {
        socialUseCase.review(slug, currentUser.resolve(auth),
            request.rating(), request.text());
        return Map.of("rating", request.rating());
    }

    @GetMapping("/reviews")
    public List<ReviewResponse> reviews(@PathVariable String slug, Authentication auth) {
        return socialUseCase.reviews(slug, currentUser.resolve(auth)).stream()
            .map(ReviewResponse::from)
            .toList();
    }

    @PostMapping("/favorite")
    public Map<String, Boolean> favorite(@PathVariable String slug, Authentication auth) {
        return Map.of("favorited",
            socialUseCase.setFavorite(slug, currentUser.resolve(auth), true));
    }

    @DeleteMapping("/favorite")
    public Map<String, Boolean> unfavorite(@PathVariable String slug, Authentication auth) {
        return Map.of("favorited",
            socialUseCase.setFavorite(slug, currentUser.resolve(auth), false));
    }

    @GetMapping("/social")
    public Map<String, Object> social(@PathVariable String slug, Authentication auth) {
        SocialUseCase.SocialInfo info = socialUseCase.info(slug, currentUser.resolve(auth));
        return Map.of("avgRating", info.avgRating(),
            "ratingCount", info.ratingCount(), "favorited", info.favorited());
    }
}
```

- [ ] **Step 4: Write the failing IT and run**

`src/test/java/com/skillhub/adapters/in/rest/SocialApiIT.java`:

```java
package com.skillhub.adapters.in.rest;

import com.skillhub.application.service.ApiTokenService;
import com.skillhub.application.service.UserSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SocialApiIT {

    @Autowired TestRestTemplate rest;
    @Autowired UserSyncService users;
    @Autowired ApiTokenService tokens;
    @Autowired JdbcTemplate jdbc;

    String authHeader;

    @BeforeEach
    void setUp() {
        var user = users.syncFromSso("soc-user", "soc@skillhub.io", "Social User");
        authHeader = "Bearer " + tokens.createToken(user, "soc").rawToken();

        jdbc.update("""
            INSERT INTO teams (slug, name) VALUES ('soc-team', 'Soc')
            ON CONFLICT (slug) DO NOTHING
            """);
        UUID teamId = jdbc.queryForObject("SELECT id FROM teams WHERE slug='soc-team'", UUID.class);
        jdbc.update("""
            INSERT INTO team_members (team_id, user_id, role)
            SELECT ?, ?, 'OWNER'
            WHERE NOT EXISTS (SELECT 1 FROM team_members WHERE team_id = ? AND user_id = ?)
            """, teamId, user.getId(), teamId, user.getId());

        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM elements WHERE slug='soc-skill'", Integer.class);
        if (count == null || count == 0) {
            rest.exchange("/api/elements", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                    "slug", "soc-skill", "type", "SKILL", "name", "Soc Skill",
                    "description", "d", "team", "soc-team",
                    "tags", new String[]{}, "visibility", "PUBLIC"),
                    jsonHeaders()), String.class);
        }
    }

    HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, authHeader);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    void rateAndReviewElement() {
        ResponseEntity<String> rated = rest.exchange("/api/elements/soc-skill/rating",
            HttpMethod.PUT, new HttpEntity<>(Map.of("rating", 5), jsonHeaders()), String.class);
        assertThat(rated.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> reviewed = rest.exchange("/api/elements/soc-skill/review",
            HttpMethod.PUT,
            new HttpEntity<>(Map.of("rating", 4, "text", "good"), jsonHeaders()), String.class);
        assertThat(reviewed.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> reviews = rest.exchange("/api/elements/soc-skill/reviews",
            HttpMethod.GET, new HttpEntity<>(jsonHeaders()), String.class);
        assertThat(reviews.getBody()).contains("good");
    }

    @Test
    void invalidRatingIsUnprocessable() {
        ResponseEntity<String> r = rest.exchange("/api/elements/soc-skill/rating",
            HttpMethod.PUT, new HttpEntity<>(Map.of("rating", 9), jsonHeaders()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void favoriteToggle() {
        ResponseEntity<String> fav = rest.exchange("/api/elements/soc-skill/favorite",
            HttpMethod.POST, new HttpEntity<>(jsonHeaders()), String.class);
        assertThat(fav.getBody()).contains("true");

        ResponseEntity<String> unfav = rest.exchange("/api/elements/soc-skill/favorite",
            HttpMethod.DELETE, new HttpEntity<>(jsonHeaders()), String.class);
        assertThat(unfav.getBody()).contains("false");
    }

    @Test
    void socialInfoReturnsAverage() {
        if (!elementExists("soc-skill-2")) {
            rest.exchange("/api/elements", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                    "slug", "soc-skill-2", "type", "SKILL", "name", "Soc Skill 2",
                    "description", "d", "team", "soc-team",
                    "tags", new String[]{}, "visibility", "PUBLIC"),
                    jsonHeaders()), String.class);
        }
        rest.exchange("/api/elements/soc-skill-2/rating", HttpMethod.PUT,
            new HttpEntity<>(Map.of("rating", 4), jsonHeaders()), String.class);
        ResponseEntity<String> info = rest.exchange("/api/elements/soc-skill-2/social",
            HttpMethod.GET, new HttpEntity<>(jsonHeaders()), String.class);
        assertThat(info.getBody()).contains("\"avgRating\":4.0").contains("\"ratingCount\":1");
    }
}
```

Run: `mvn test -Dtest='SocialUseCaseTest,SocialApiIT'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat: add social use case with ratings, reviews and favorites"
```

---

### Task 14: SearchPort + SearchUseCase + SearchController

**Files:**
- Create: `src/main/java/com/skillhub/application/dto/SearchQuery.java`, `SearchQueryResult.java`
- Create: `src/main/java/com/skillhub/adapters/out/jpa/SearchAdapter.java` (реализация `SearchPort` — native SQL)
- Create: `src/main/java/com/skillhub/application/service/SearchUseCase.java`
- Create: `src/main/java/com/skillhub/adapters/in/rest/dto/SearchResultResponse.java`
- Create: `src/main/java/com/skillhub/adapters/in/rest/SearchController.java`
- Test: `src/test/java/com/skillhub/adapters/out/jpa/SearchAdapterIT.java`

**Interfaces:**
- Consumes: порт `SearchPort` (Task 3), колонка `search_vector` (Task 2), `ElementJpaMapper` (Task 4), `AccessService`.
- Produces:
  - `application/dto/SearchQuery`: `record SearchQuery(String q, String type, String category, UUID userId, int limit, int offset)`; `SearchQueryResult`: `record SearchQueryResult(List<Element> items, long total, Map<String, Long> facetsByType)`.
  - `SearchAdapter` (`@Repository`, реализует `SearchPort`) — native SQL с visibility-фильтром (PUBLIC или членство в команде).
  - REST: `GET /api/search?q&type&category&limit&offset` → `{items, total, facetsByType}`.

`application/dto/SearchQuery.java`:

```java
package com.skillhub.application.dto;

import java.util.UUID;

public record SearchQuery(String q, String type, String category, UUID userId,
                          int limit, int offset) {}
```

`application/dto/SearchQueryResult.java`:

```java
package com.skillhub.application.dto;

import com.skillhub.domain.model.Element;

import java.util.List;
import java.util.Map;

public record SearchQueryResult(List<Element> items, long total,
                                Map<String, Long> facetsByType) {}
```

- [ ] **Step 1: Write the failing test**

`src/test/java/com/skillhub/adapters/out/jpa/SearchAdapterIT.java`:

```java
package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.SearchAdapter;
import com.skillhub.application.dto.SearchQuery;
import com.skillhub.application.dto.SearchQueryResult;
import com.skillhub.domain.model.*;
import com.skillhub.domain.port.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SearchAdapterIT {

    @Autowired SearchAdapter searchAdapter;
    @Autowired ElementRepositoryPort elements;
    @Autowired TeamRepositoryPort teams;
    @Autowired UserRepositoryPort users;
    @Autowired JdbcTemplate jdbc;

    @Test
    void searchFindsByNameAndRespectsVisibility() {
        User author = users.save(User.builder()
            .ssoSubject("search-sub").email("s@b.c").displayName("S")
            .admin(false).createdAt(Instant.now()).build());
        Team team = teams.save(Team.builder()
            .slug("search-t").name("SearchT").createdAt(Instant.now()).build());

        jdbc.update("INSERT INTO categories (slug, name) VALUES ('dev', 'Разработка') " +
            "ON CONFLICT (slug) DO NOTHING");

        elements.save(Element.builder()
            .slug("pdf-docs-skill").type(ElementType.SKILL).name("PDF Skill")
            .description("Работа с PDF документами").team(team)
            .tags(new String[]{"pdf", "docs"}).visibility(Visibility.PUBLIC)
            .author(author).downloadsCount(0)
            .createdAt(Instant.now()).updatedAt(Instant.now()).build());
        elements.save(Element.builder()
            .slug("hidden-item").type(ElementType.SCRIPT).name("Hidden")
            .description("secret").team(team)
            .tags(new String[]{}).visibility(Visibility.TEAM)
            .author(author).downloadsCount(0)
            .createdAt(Instant.now()).updatedAt(Instant.now()).build());

        UUID outsider = UUID.randomUUID();
        SearchQueryResult publicOnly = searchAdapter.search(new SearchQuery(
            "PDF", null, null, outsider, 20, 0));
        assertThat(publicOnly.items()).extracting(Element::getSlug)
            .contains("pdf-docs-skill").doesNotContain("hidden-item");

        UUID memberId = author.getId();
        SearchQueryResult memberView = searchAdapter.search(new SearchQuery(
            "secret", null, null, memberId, 20, 0));
        assertThat(memberView.items()).extracting(Element::getSlug)
            .contains("hidden-item");

        SearchQueryResult byType = searchAdapter.search(new SearchQuery(
            "", "SKILL", null, outsider, 20, 0));
        assertThat(byType.facetsByType()).containsEntry("SKILL", 1L);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=SearchAdapterIT`
Expected: FAIL — SearchAdapter не существует.

- [ ] **Step 3: Write implementation**

`adapters/out/jpa/SearchAdapter.java`:

```java
package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.entity.JpaElement;
import com.skillhub.adapters.out.jpa.mapper.ElementJpaMapper;
import com.skillhub.application.dto.SearchQuery;
import com.skillhub.application.dto.SearchQueryResult;
import com.skillhub.domain.model.Element;
import com.skillhub.domain.port.SearchPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class SearchAdapter implements SearchPort {

    @PersistenceContext
    private EntityManager em;

    private static final String WHERE = """
        (e.visibility = 'PUBLIC'
         OR e.team_id IN (SELECT tm.team_id FROM team_members tm WHERE tm.user_id = :userId))
        AND (:type IS NULL OR e.type = :type)
        AND (:category IS NULL OR e.category_id = (SELECT c.id FROM categories c WHERE c.slug = :category))
        AND (:q = '' OR e.search_vector @@ plainto_tsquery('simple', :q)
             OR e.name ILIKE ('%' || :q || '%'))
        """;

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public SearchQueryResult search(SearchQuery q) {
        String query = q.q() == null ? "" : q.q();

        List<JpaElement> items = em.createNativeQuery(
                "SELECT e.* FROM elements e WHERE " + WHERE +
                " ORDER BY CASE WHEN :q = '' THEN 0 " +
                "ELSE ts_rank(e.search_vector, plainto_tsquery('simple', :q)) END DESC, " +
                "e.downloads_count DESC LIMIT :limit OFFSET :offset", JpaElement.class)
            .setParameter("userId", q.userId())
            .setParameter("q", query)
            .setParameter("type", q.type())
            .setParameter("category", q.category())
            .setParameter("limit", q.limit())
            .setParameter("offset", q.offset())
            .getResultList();

        long total = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM elements e WHERE " + WHERE)
            .setParameter("userId", q.userId())
            .setParameter("q", query)
            .setParameter("type", q.type())
            .setParameter("category", q.category())
            .getSingleResult()).longValue();

        Map<String, Long> facets = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT e.type AS type, COUNT(*) AS cnt FROM elements e WHERE " + WHERE +
                " GROUP BY e.type")
            .setParameter("userId", q.userId())
            .setParameter("q", query)
            .setParameter("type", q.type())
            .setParameter("category", q.category())
            .getResultList();
        for (Object[] row : rows) {
            facets.put((String) row[0], ((Number) row[1]).longValue());
        }

        return new SearchQueryResult(
            items.stream().map(ElementJpaMapper::toDomain).toList(),
            total, facets);
    }
}
```

`application/service/SearchUseCase.java`:

```java
package com.skillhub.application.service;

import com.skillhub.application.dto.SearchQuery;
import com.skillhub.application.dto.SearchQueryResult;
import com.skillhub.domain.model.User;
import com.skillhub.domain.port.SearchPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SearchUseCase {

    private final SearchPort searchPort;

    public SearchUseCase(SearchPort searchPort) {
        this.searchPort = searchPort;
    }

    @Transactional(readOnly = true)
    public SearchQueryResult search(String q, String type, String category,
                                    User viewer, int limit, int offset) {
        UUID userId = viewer == null
            ? UUID.nameUUIDFromBytes("anonymous".getBytes())
            : viewer.getId();
        return searchPort.search(new SearchQuery(q, type, category, userId, limit, offset));
    }
}
```

`adapters/in/rest/dto/SearchResultResponse.java`:

```java
package com.skillhub.adapters.in.rest.dto;

import java.util.List;
import java.util.Map;

public record SearchResultResponse(List<ElementResponse> items, long total,
                                   Map<String, Long> facetsByType) {}
```

`adapters/in/rest/SearchController.java`:

```java
package com.skillhub.adapters.in.rest;

import com.skillhub.adapters.in.rest.dto.ElementResponse;
import com.skillhub.adapters.in.rest.dto.SearchResultResponse;
import com.skillhub.adapters.in.security.CurrentUserResolver;
import com.skillhub.application.service.SearchUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchUseCase searchUseCase;
    private final CurrentUserResolver currentUser;

    @GetMapping
    public SearchResultResponse search(@RequestParam(value = "q", defaultValue = "") String q,
                                       @RequestParam(value = "type", required = false) String type,
                                       @RequestParam(value = "category", required = false) String category,
                                       @RequestParam(value = "limit", defaultValue = "20") int limit,
                                       @RequestParam(value = "offset", defaultValue = "0") int offset,
                                       Authentication auth) {
        var result = searchUseCase.search(q, type, category, currentUser.resolve(auth), limit, offset);
        List<ElementResponse> items = result.items().stream()
            .map(ElementResponse::from)
            .toList();
        return new SearchResultResponse(items, result.total(), result.facetsByType());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=SearchAdapterIT`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat: add search port adapter with visibility filter and facets"
```

---

### Task 15: Финальная проверка — полный прогон тестов

**Files:**
- Modify: фиксы по результатам прогона

**Interfaces:**
- Consumes: все предыдущие задачи.
- Produces: зелёная сборка `mvn test`, `mvn package`.

- [ ] **Step 1: Полный прогон**

Run: `mvn clean test`
Expected: все тесты PASS.

- [ ] **Step 2: Проверка правила зависимостей**

Run: `grep -rl "org.springframework" src/main/java/com/skillhub/domain/ | wc -l`
Expected: `0` — в домене нет Spring.

- [ ] **Step 3: Сборка jar**

Run: `mvn package -DskipTests`
Expected: `target/skillhub-api-0.1.0-SNAPSHOT.jar` создан.

- [ ] **Step 4: Ручная smoke-проверка с dev-инфраструктурой**

```bash
docker compose up -d
java -jar target/skillhub-api-0.1.0-SNAPSHOT.jar
curl -s http://localhost:8080/actuator/health
```
Expected: `{"status":"UP"}`.

- [ ] **Step 5: Commit (если были фиксы)**

```bash
git add -A src
git commit -m "fix: stabilize integration tests"
```

---

## Отложено (следующие планы)

- **Web UI (React)** — план готов: `docs/superpowers/plans/2026-09-30-skillhub-ui.md`.
- **CLI** — отдельный план: `install/publish`, чтение `manifest.json`, Bearer-токен.
- Orphan-cleanup job для S3 (фоновая чистка при сбое PG после S3-записи).
- Депрекация версий (endpoint `POST .../versions/{v}/deprecate`).
- Драфт-версии (публикация со статусом DRAFT + перевод в PUBLISHED).
