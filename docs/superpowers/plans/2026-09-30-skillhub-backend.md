# SkillHub Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Backend API корпоративного хранилища скиллов: элементы с semver-версиями (zip в S3), паки, категории, команды, права, полнотекстовый поиск, рейтинги/отзывы/избранное, SSO + API-токены.

**Architecture:** Модульный монолит Spring Boot. Метаданные в PostgreSQL (Flyway), архивы версий в S3/MinIO (immutable). Единая модель `Element` с полем `type` (SKILL/SCRIPT/AGENT/HOOK/PACK/OTHER). Версия публикуется как zip: сервер валидирует архив, строит `file_index` (jsonb), кладёт архив в S3, пишет метаданные в PG. Скачивание — прямая отдача через API после проверки прав.

**Tech Stack:** Java 21, Spring Boot 3.3.x (Maven), PostgreSQL 16, Flyway, Spring Data JPA, Spring Security (OAuth2 Resource Server + кастомный API-токен фильтр), AWS SDK v2 (S3), commons-compress (zip), Lombok, springdoc-openapi, Testcontainers (PostgreSQL + MinIO через GenericContainer).

**Спека:** `docs/superpowers/specs/2026-09-30-skillhub-design.md`

## Global Constraints

- Java 21, Spring Boot 3.3.x, Maven. Группа `com.skillhub`, артефакт `skillhub-api`, базовый пакет `com.skillhub`.
- Ошибки — единый формат JSON: `{"code": "...", "message": "...", "details": ...}`.
- HTTP-коды: 401/403 (auth), 404, 409 (конфликт версии/слага), 422 (невалидный manifest/semver/архив).
- Версии: semver, regex `^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$`; версии immutable; статус DRAFT → PUBLISHED → DEPRECATED.
- Лимиты архива: загрузка ≤ 50MB, распакованное содержимое ≤ 200MB (209715200 байт), ≤ 5000 файлов, запрет path traversal.
- API-токены: префикс `skh_`, в БД хранится SHA-256 hex-хеш.
- Slug элемента — глобально уникальный (уточнение к спеке: упрощает URL `/api/elements/{slug}` без team в пути).
- Поиск: PG tsvector, конфигурация `simple` (ru+en), generated column, GIN-индекс.
- Транзакционность публикации: сначала S3, потом PG; orphan-объекты S3 чистятся фоновым job'ом.
- Скачивание: любая версия по `version`; `latest` = последняя PUBLISHED по `published_at`.
- Visibility: PUBLIC — чтение всеми, TEAM — только участникам команды; публикация — OWNER/MAINTAINER (или админ).
- TDD: тест до реализации. Unit — JUnit 5 + Mockito, интеграционные — Testcontainers (нужен Docker).
- Коммит после каждой задачи, conventional commits (`feat:`, `test:`, `chore:`).

---

### Task 1: Скелет проекта, docker-compose, smoke-тест

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/skillhub/SkillHubApplication.java`
- Create: `src/main/resources/application.yml`
- Create: `docker-compose.yml`
- Create: `.gitignore`
- Test: `src/test/java/com/skillhub/SmokeTest.java`

**Interfaces:**
- Produces: запускаемое приложение `com.skillhub.SkillHubApplication`; конфиг-ключи `skillhub.storage.*`, `skillhub.api-token-prefix`, `skillhub.upload.*` — используются задачами 5–11.

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
Expected: FAIL — компиляция невозможна, зависимостей нет.

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
- Create: `src/main/resources/db/migration/V1__init.sql`
- Create: `src/main/resources/db/migration/V2__search_vector.sql`
- Test: `src/test/java/com/skillhub/migration/FlywayMigrationIT.java`

**Interfaces:**
- Produces: таблицы `users, api_tokens, teams, team_members, categories, elements, element_versions, pack_contents, ratings, reviews, favorites, audit_log`; колонка `elements.search_vector` (tsvector). Используются Task 3+.

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
Expected: PASS (Testcontainers поднимет PostgreSQL; нужен Docker).

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration src/test
git commit -m "feat: add Flyway schema V1 (all tables) and V2 (tsvector search)"
```

---

### Task 3: JPA-сущности и репозитории

**Files:**
- Create: `src/main/java/com/skillhub/core/model/` — `User.java`, `ApiToken.java`, `Team.java`, `TeamMember.java`, `TeamRole.java`, `Category.java`, `Element.java`, `ElementType.java`, `Visibility.java`, `ElementVersion.java`, `VersionStatus.java`, `PackContent.java`, `PackContentId.java`, `Rating.java`, `RatingId.java`, `Review.java`, `Favorite.java`, `FavoriteId.java`, `AuditLog.java`
- Create: `src/main/java/com/skillhub/core/repo/` — `UserRepository.java`, `ApiTokenRepository.java`, `TeamRepository.java`, `TeamMemberRepository.java`, `CategoryRepository.java`, `ElementRepository.java`, `ElementVersionRepository.java`, `PackContentRepository.java`, `RatingRepository.java`, `ReviewRepository.java`, `FavoriteRepository.java`, `AuditLogRepository.java`
- Test: `src/test/java/com/skillhub/core/repo/ElementRepositoryIT.java`

**Interfaces:**
- Produces (сигнатуры, используются Task 5–14):

```java
// UserRepository
Optional<User> findBySsoSubject(String ssoSubject);
// ApiTokenRepository
Optional<ApiToken> findByTokenHash(String tokenHash);
// TeamRepository
Optional<Team> findBySlug(String slug);
// TeamMemberRepository
Optional<TeamMember> findByTeamIdAndUserId(UUID teamId, UUID userId);
// CategoryRepository
Optional<Category> findBySlug(String slug);
boolean existsBySlug(String slug);
// ElementRepository
Optional<Element> findBySlug(String slug);
boolean existsBySlug(String slug);
// ElementVersionRepository
Optional<ElementVersion> findByElementIdAndVersion(UUID elementId, String version);
Optional<ElementVersion> findFirstByElementIdAndStatusOrderByPublishedAtDesc(UUID elementId, VersionStatus status);
List<ElementVersion> findAllByElementIdOrderByCreatedAtDesc(UUID elementId);
// PackContentRepository
List<PackContent> findAllByPackElementId(UUID packElementId);
// RatingRepository / ReviewRepository / FavoriteRepository
Optional<Rating> findByElementIdAndUserId(UUID elementId, UUID userId);
Optional<Review> findByElementIdAndUserId(UUID elementId, UUID userId);
List<Review> findAllByElementIdOrderByCreatedAtDesc(UUID elementId);
Optional<Favorite> findByUserIdAndElementId(UUID userId, UUID elementId);
```

- [ ] **Step 1: Write the failing test**

`src/test/java/com/skillhub/core/repo/ElementRepositoryIT.java`:

```java
package com.skillhub.core.repo;

import com.skillhub.core.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ElementRepositoryIT {

    @Autowired UserRepository users;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;
    @Autowired CategoryRepository categories;
    @Autowired ElementRepository elements;
    @Autowired ElementVersionRepository versions;

    @Test
    void saveAndFindElementWithVersion() {
        User author = users.save(User.builder()
            .ssoSubject("sub-1").email("a@b.c").displayName("Alice")
            .isAdmin(true).createdAt(Instant.now()).build());
        Team team = teams.save(Team.builder().slug("platform")
            .name("Platform").createdAt(Instant.now()).build());
        members.save(TeamMember.builder().team(team).user(author).role(TeamRole.OWNER).build());
        Category cat = categories.save(Category.builder().slug("dev").name("Разработка").build());

        Element el = elements.save(Element.builder()
            .slug("pdf-skill").type(ElementType.SKILL).name("PDF Skill").description("d")
            .team(team).category(cat).tags(new String[]{"pdf", "docs"})
            .visibility(Visibility.PUBLIC).author(author).build());

        versions.save(ElementVersion.builder()
            .element(el).version("1.0.0").status(VersionStatus.PUBLISHED)
            .changelog("initial").s3_key("platform/pdf-skill/1.0.0.zip")
            .sizeBytes(10).fileIndex("{\"files\":[]}")
            .publishedBy(author).createdAt(Instant.now()).publishedAt(Instant.now()).build());

        Element found = elements.findBySlug("pdf-skill").orElseThrow();
        assertThat(found.getTeam().getSlug()).isEqualTo("platform");
        assertThat(found.getTags()).containsExactly("pdf", "docs");
        assertThat(versions.findFirstByElementIdAndStatusOrderByPublishedAtDesc(
            found.getId(), VersionStatus.PUBLISHED)).isPresent();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ElementRepositoryIT`
Expected: FAIL — классы model/repo не существуют.

- [ ] **Step 3: Write implementation**

Общие приёмы: Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` на всех сущностях; enum'ы — `@Enumerated(EnumType.STRING)`.

`core/model/User.java`:

```java
package com.skillhub.core.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "sso_subject", nullable = false, unique = true)
    private String ssoSubject;
    @Column(nullable = false)
    private String email;
    @Column(name = "display_name", nullable = false)
    private String displayName;
    @Column(name = "avatar_url")
    private String avatarUrl;
    @Column(name = "is_admin", nullable = false)
    private boolean admin;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
```

`core/model/ApiToken.java`:

```java
package com.skillhub.core.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_tokens")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApiToken {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false)
    private String name;
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "last_used_at")
    private Instant lastUsedAt;
    @Column(name = "expires_at")
    private Instant expiresAt;
}
```

`core/model/Team.java`:

```java
package com.skillhub.core.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "teams")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Team {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true)
    private String slug;
    @Column(nullable = false)
    private String name;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
```

`core/model/TeamRole.java`:

```java
package com.skillhub.core.model;

public enum TeamRole { OWNER, MAINTAINER, MEMBER }
```

`core/model/TeamMember.java`:

```java
package com.skillhub.core.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "team_members")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TeamMember {
    @Id @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "team_id")
    private Team team;
    @Id @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id")
    private User user;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private TeamRole role;
}
```

`core/model/Category.java`:

```java
package com.skillhub.core.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "categories")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Category {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true)
    private String slug;
    @Column(nullable = false)
    private String name;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "parent_id")
    private Category parent;
    private String icon;
}
```

`core/model/ElementType.java`:

```java
package com.skillhub.core.model;

public enum ElementType { SKILL, SCRIPT, AGENT, HOOK, PACK, OTHER }
```

`core/model/Visibility.java`:

```java
package com.skillhub.core.model;

public enum Visibility { PUBLIC, TEAM }
```

`core/model/Element.java`:

```java
package com.skillhub.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "elements")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Element {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true)
    private String slug;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private ElementType type;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String description;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "team_id", nullable = false)
    private Team team;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "category_id")
    private Category category;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] tags;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Visibility visibility;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "author_id", nullable = false)
    private User author;
    @Column(name = "latest_version")
    private String latestVersion;
    @Column(name = "downloads_count", nullable = false)
    private long downloadsCount;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
```

`core/model/VersionStatus.java`:

```java
package com.skillhub.core.model;

public enum VersionStatus { DRAFT, PUBLISHED, DEPRECATED }
```

`core/model/ElementVersion.java`:

```java
package com.skillhub.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "element_versions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ElementVersion {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "element_id", nullable = false)
    private Element element;
    @Column(nullable = false)
    private String version;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private VersionStatus status;
    @Column(nullable = false)
    private String changelog;
    @Column(name = "s3_key", nullable = false)
    private String s3_key;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "file_index", nullable = false, columnDefinition = "jsonb")
    private String fileIndex;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "published_by", nullable = false)
    private User publishedBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "published_at")
    private Instant publishedAt;
}
```

`core/model/PackContentId.java`:

```java
package com.skillhub.core.model;

import lombok.*;
import java.io.Serializable;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class PackContentId implements Serializable {
    private UUID packElementId;
    private UUID elementId;
}
```

`core/model/PackContent.java`:

```java
package com.skillhub.core.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pack_contents")
@IdClass(PackContentId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PackContent {
    @Id @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "pack_element_id")
    private Element packElement;
    @Id @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "element_id")
    private Element element;
    @Column(name = "version_constraint", nullable = false)
    private String versionConstraint;
}
```

`core/model/RatingId.java`:

```java
package com.skillhub.core.model;

import lombok.*;
import java.io.Serializable;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class RatingId implements Serializable {
    private UUID elementId;
    private UUID userId;
}
```

`core/model/Rating.java`:

```java
package com.skillhub.core.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ratings")
@IdClass(RatingId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Rating {
    @Id @Column(name = "element_id") private UUID elementId;
    @Id @Column(name = "user_id") private UUID userId;
    @Column(nullable = false) private int rating;
}
```

`core/model/Review.java`:

```java
package com.skillhub.core.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reviews")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Review {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "element_id", nullable = false)
    private Element element;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false) private int rating;
    @Column(nullable = false) private String text;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
```

`core/model/FavoriteId.java`:

```java
package com.skillhub.core.model;

import lombok.*;
import java.io.Serializable;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class FavoriteId implements Serializable {
    private UUID userId;
    private UUID elementId;
}
```

`core/model/Favorite.java`:

```java
package com.skillhub.core.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "favorites")
@IdClass(FavoriteId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Favorite {
    @Id @Column(name = "user_id") private UUID userId;
    @Id @Column(name = "element_id") private UUID elementId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
```

`core/model/AuditLog.java`:

```java
package com.skillhub.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id")
    private User user;
    @Column(nullable = false) private String action;
    private UUID elementId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String details;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
```

Репозитории (все в пакете `com.skillhub.core.repo`):

```java
// UserRepository.java
package com.skillhub.core.repo;

import com.skillhub.core.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findBySsoSubject(String ssoSubject);
}
```

```java
// ApiTokenRepository.java
package com.skillhub.core.repo;

import com.skillhub.core.model.ApiToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ApiTokenRepository extends JpaRepository<ApiToken, UUID> {
    Optional<ApiToken> findByTokenHash(String tokenHash);
}
```

```java
// TeamRepository.java
package com.skillhub.core.repo;

import com.skillhub.core.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {
    Optional<Team> findBySlug(String slug);
}
```

```java
// TeamMemberRepository.java
package com.skillhub.core.repo;

import com.skillhub.core.model.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {
    Optional<TeamMember> findByTeamIdAndUserId(UUID teamId, UUID userId);
}
```

```java
// CategoryRepository.java
package com.skillhub.core.repo;

import com.skillhub.core.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    Optional<Category> findBySlug(String slug);
    boolean existsBySlug(String slug);
    List<Category> findAllByOrderByNameAsc();
}
```

```java
// ElementRepository.java
package com.skillhub.core.repo;

import com.skillhub.core.model.Element;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ElementRepository extends JpaRepository<Element, UUID> {
    Optional<Element> findBySlug(String slug);
    boolean existsBySlug(String slug);
}
```

```java
// ElementVersionRepository.java
package com.skillhub.core.repo;

import com.skillhub.core.model.ElementVersion;
import com.skillhub.core.model.VersionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ElementVersionRepository extends JpaRepository<ElementVersion, UUID> {
    Optional<ElementVersion> findByElementIdAndVersion(UUID elementId, String version);
    Optional<ElementVersion> findFirstByElementIdAndStatusOrderByPublishedAtDesc(
        UUID elementId, VersionStatus status);
    List<ElementVersion> findAllByElementIdOrderByCreatedAtDesc(UUID elementId);
}
```

```java
// PackContentRepository.java
package com.skillhub.core.repo;

import com.skillhub.core.model.PackContent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PackContentRepository extends JpaRepository<PackContent, PackContentId> {
    List<PackContent> findAllByPackElementId(UUID packElementId);
}
```

```java
// RatingRepository.java
package com.skillhub.core.repo;

import com.skillhub.core.model.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface RatingRepository extends JpaRepository<Rating, RatingId> {
    Optional<Rating> findByElementIdAndUserId(UUID elementId, UUID userId);
}
```

```java
// ReviewRepository.java
package com.skillhub.core.repo;

import com.skillhub.core.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    Optional<Review> findByElementIdAndUserId(UUID elementId, UUID userId);
    List<Review> findAllByElementIdOrderByCreatedAtDesc(UUID elementId);
}
```

```java
// FavoriteRepository.java
package com.skillhub.core.repo;

import com.skillhub.core.model.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface FavoriteRepository extends JpaRepository<Favorite, FavoriteId> {
    Optional<Favorite> findByUserIdAndElementId(UUID userId, UUID elementId);
}
```

```java
// AuditLogRepository.java
package com.skillhub.core.repo;

import com.skillhub.core.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ElementRepositoryIT`
Expected: PASS. (Если `ddl-auto: validate` ругается на маппинг — исправить маппинг, НЕ схему.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skillhub/core src/test
git commit -m "feat: add JPA entities and Spring Data repositories"
```

---

### Task 4: Единая обработка ошибок

**Files:**
- Create: `src/main/java/com/skillhub/api/ErrorResponse.java`
- Create: `src/main/java/com/skillhub/api/GlobalExceptionHandler.java`
- Create: `src/main/java/com/skillhub/core/exception/NotFoundException.java`
- Create: `src/main/java/com/skillhub/core/exception/ConflictException.java`
- Create: `src/main/java/com/skillhub/core/exception/UnprocessableException.java`
- Create: `src/main/java/com/skillhub/core/exception/ForbiddenException.java`
- Test: `src/test/java/com/skillhub/api/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Produces: `ErrorResponse(String code, String message, Object details)`; исключения `NotFoundException(msg)`, `ConflictException(msg)`, `UnprocessableException(msg, details)`, `ForbiddenException(msg)` → HTTP 404/409/422/403 с телом `{"code","message","details"}`. Используются Task 6+.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/skillhub/api/GlobalExceptionHandlerTest.java`:

```java
package com.skillhub.api;

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

`api/ErrorResponse.java`:

```java
package com.skillhub.api;

public record ErrorResponse(String code, String message, Object details) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null);
    }
}
```

Исключения (`core/exception/`, по одному файлу):

```java
// NotFoundException.java
package com.skillhub.core.exception;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}

// ConflictException.java
package com.skillhub.core.exception;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}

// UnprocessableException.java
package com.skillhub.core.exception;

public class UnprocessableException extends RuntimeException {
    private final Object details;
    public UnprocessableException(String message, Object details) {
        super(message);
        this.details = details;
    }
    public Object getDetails() { return details; }
}

// ForbiddenException.java
package com.skillhub.core.exception;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) { super(message); }
}
```

`api/GlobalExceptionHandler.java`:

```java
package com.skillhub.api;

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
git add src/main/java/com/skillhub/api src/main/java/com/skillhub/core/exception src/test
git commit -m "feat: add unified error handling with ErrorResponse format"
```

---

### Task 5: Аутентификация — SSO (OIDC) + API-токены

**Files:**
- Create: `src/main/java/com/skillhub/core/service/UserService.java`
- Create: `src/main/java/com/skillhub/core/service/ApiTokenService.java`
- Create: `src/main/java/com/skillhub/security/ApiTokenAuthFilter.java`
- Create: `src/main/java/com/skillhub/security/SecurityConfig.java`
- Test: `src/test/java/com/skillhub/security/ApiTokenServiceTest.java`
- Test: `src/test/java/com/skillhub/security/ApiTokenAuthIT.java`

**Interfaces:**
- Consumes: `UserRepository`, `ApiTokenRepository` (Task 3).
- Produces:
  - `UserService.syncFromSso(String subject, String email, String displayName)` → `User` (создаёт или обновляет).
  - `ApiTokenService.createToken(User user, String name)` → `CreatedToken` (record с полями `ApiToken token`, `String rawToken`; raw = `skh_` + base64url(32 байта)); `authenticate(String rawToken)` → `Optional<User>`; `sha256Hex(String)` → String.
  - `ApiTokenAuthFilter` — principal в SecurityContext = `User` (используется Task 8+ через `@AuthenticationPrincipal User`).
  - `SecurityConfig` — `/api/**` требуют аутентификации; swagger/actuator health — permitAll; JWT (OIDC) и `skh_`-токены оба валидны.

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/skillhub/security/ApiTokenServiceTest.java`:

```java
package com.skillhub.security;

import com.skillhub.core.service.ApiTokenService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiTokenServiceTest {

    @Test
    void rawTokenHasPrefixAndHashDiffers() {
        ApiTokenService service = new ApiTokenService(null, "skh_");
        ApiTokenService.CreatedToken created = service.newRawToken();
        assertThat(created.rawToken()).startsWith("skh_");
        String hash = service.sha256Hex(created.rawToken());
        assertThat(hash).hasSize(64).isNotEqualTo(created.rawToken());
    }

    @Test
    void sha256IsDeterministic() {
        assertThat(ApiTokenService.staticHash("abc"))
            .isEqualTo(ApiTokenService.staticHash("abc"));
    }
}
```

`src/test/java/com/skillhub/security/ApiTokenAuthIT.java`:

```java
package com.skillhub.security;

import com.skillhub.core.service.ApiTokenService;
import com.skillhub.core.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiTokenAuthIT {

    @Autowired TestRestTemplate rest;
    @Autowired ApiTokenService tokens;
    @Autowired UserService userService;

    @Test
    void apiTokenGrantsAccessToProtectedEndpoint() {
        var user = userService.syncFromSso("sub-it-1", "it@skillhub.io", "IT User");
        String raw = tokens.createToken(user, "test").rawToken();

        ResponseEntity<String> noAuth = rest.getForEntity("/api/test-protected", String.class);
        assertThat(noAuth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(raw);
        ResponseEntity<String> ok = rest.exchange("/api/test-protected", HttpMethod.GET,
            new HttpEntity<>(headers), String.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void invalidTokenIsUnauthorized() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("skh_invalid");
        ResponseEntity<String> r = rest.exchange("/api/test-protected", HttpMethod.GET,
            new HttpEntity<>(headers), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
```

Вместе с тестом создайте временный защищённый эндпоинт-заглушку (удаляется в Task 8):

`src/main/java/com/skillhub/api/TestProtectedController.java` (временный):

```java
package com.skillhub.api;

import com.skillhub.core.model.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestProtectedController {

    @GetMapping("/api/test-protected")
    public String me(@AuthenticationPrincipal User user) {
        return user == null ? "anonymous" : user.getDisplayName();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest='ApiTokenServiceTest,ApiTokenAuthIT'`
Expected: FAIL — классы не существуют.

- [ ] **Step 3: Write implementation**

`core/service/UserService.java`:

```java
package com.skillhub.core.service;

import com.skillhub.core.model.User;
import com.skillhub.core.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;

    @Transactional
    public User syncFromSso(String subject, String email, String displayName) {
        return users.findBySsoSubject(subject).map(u -> {
            u.setEmail(email);
            u.setDisplayName(displayName);
            return u;
        }).orElseGet(() -> users.save(User.builder()
            .ssoSubject(subject)
            .email(email)
            .displayName(displayName)
            .admin(false)
            .createdAt(Instant.now())
            .build()));
    }
}
```

`core/service/ApiTokenService.java`:

```java
package com.skillhub.core.service;

import com.skillhub.core.model.ApiToken;
import com.skillhub.core.model.User;
import com.skillhub.core.repo.ApiTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class ApiTokenService {

    public record CreatedToken(ApiToken token, String rawToken) {}

    private final ApiTokenRepository tokens;
    private final String prefix;
    private final SecureRandom random = new SecureRandom();

    public ApiTokenService(ApiTokenRepository tokens,
                           @Value("${skillhub.api-token-prefix:skh_}") String prefix) {
        this.tokens = tokens;
        this.prefix = prefix;
    }

    public CreatedToken newRawToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return new CreatedToken(null,
            prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    @Transactional
    public CreatedToken createToken(User user, String name) {
        CreatedToken raw = newRawToken();
        ApiToken token = tokens.save(ApiToken.builder()
            .user(user)
            .name(name)
            .tokenHash(sha256Hex(raw.rawToken()))
            .createdAt(Instant.now())
            .build());
        return new CreatedToken(token, raw.rawToken());
    }

    @Transactional
    public Optional<User> authenticate(String rawToken) {
        if (rawToken == null || !rawToken.startsWith(prefix)) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(sha256Hex(rawToken))
            .filter(t -> t.getExpiresAt() == null || t.getExpiresAt().isAfter(Instant.now()))
            .map(t -> {
                t.setLastUsedAt(Instant.now());
                return t.getUser();
            });
    }

    public String sha256Hex(String value) {
        return staticHash(value);
    }

    public static String staticHash(String value) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of()
                .formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
```

`security/ApiTokenAuthFilter.java`:

```java
package com.skillhub.security;

import com.skillhub.core.model.User;
import com.skillhub.core.service.ApiTokenService;
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

`security/SecurityConfig.java`:

```java
package com.skillhub.security;

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

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest='ApiTokenServiceTest,ApiTokenAuthIT'`
Expected: PASS. (JWT-валидация настроена лениво — issuer не вызывается, т.к. тесты используют только API-токены.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skillhub src/test
git commit -m "feat: add OIDC resource server + API token authentication"
```

---

### Task 6: S3-хранилище (MinIO)

**Files:**
- Create: `src/main/java/com/skillhub/storage/StorageProperties.java`
- Create: `src/main/java/com/skillhub/storage/S3StorageService.java`
- Test: `src/test/java/com/skillhub/storage/S3StorageServiceIT.java`

**Interfaces:**
- Consumes: конфиг `skillhub.storage.*` (Task 1).
- Produces:
  - `StorageProperties(String endpoint, String accessKey, String secretKey, String bucket, Duration presignTtl)` — record.
  - `S3StorageService`: `void ensureBucket()`, `void upload(String key, byte[] content)`, `byte[] download(String key)`, `void delete(String key)`, `String presignedGetUrl(String key, Duration ttl)`. Используется Task 9–11.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/skillhub/storage/S3StorageServiceIT.java`:

```java
package com.skillhub.storage;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class S3StorageServiceIT {

    static GenericContainer<?> minio = new GenericContainer<>(DockerImageName.parse("minio/minio"))
        .withCommand("server /data")
        .withEnv("MINIO_ROOT_USER", "minioadmin")
        .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
        .withExposedPorts(9000)
        .waitingFor(Wait.forHttp("/minio/health/live").forStatusCode(200));

    static S3StorageService storage;

    @BeforeAll
    static void setUp() {
        minio.start();
        storage = new S3StorageService(
            "http://" + minio.getHost() + ":" + minio.getMappedPort(9000),
            "minioadmin", "minioadmin", "test-bucket", "PT10M");
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

Run: `mvn test -Dtest=S3StorageServiceIT`
Expected: FAIL — классы не существуют.

- [ ] **Step 3: Write implementation**

`storage/StorageProperties.java`:

```java
package com.skillhub.storage;

import java.time.Duration;

public record StorageProperties(
    String endpoint,
    String accessKey,
    String secretKey,
    String bucket,
    Duration presignTtl
) {
    public static StorageProperties of(String endpoint, String accessKey, String secretKey,
                                       String bucket, String presignTtl) {
        return new StorageProperties(endpoint, accessKey, secretKey, bucket,
            Duration.parse(presignTtl));
    }
}
```

`storage/S3StorageService.java`:

```java
package com.skillhub.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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

@Service
public class S3StorageService {

    private final StorageProperties props;
    private final S3Client client;
    private final S3Presigner presigner;

    public S3StorageService(@Value("${skillhub.storage.endpoint}") String endpoint,
                            @Value("${skillhub.storage.access-key}") String accessKey,
                            @Value("${skillhub.storage.secret-key}") String secretKey,
                            @Value("${skillhub.storage.bucket}") String bucket,
                            @Value("${skillhub.storage.presign-ttl}") String presignTtl) {
        this.props = StorageProperties.of(endpoint, accessKey, secretKey, bucket, presignTtl);
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
            client.headBucket(HeadBucketRequest.builder().bucket(props.bucket()).build());
        } catch (NoSuchBucketException e) {
            client.createBucket(CreateBucketRequest.builder().bucket(props.bucket()).build());
        }
    }

    public void upload(String key, byte[] content) {
        client.putObject(PutObjectRequest.builder()
            .bucket(props.bucket()).key(key).build(), RequestBody.fromBytes(content));
    }

    public byte[] download(String key) {
        return client.getObjectAsBytes(GetObjectRequest.builder()
            .bucket(props.bucket()).key(key).build()).asByteArray();
    }

    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder()
            .bucket(props.bucket()).key(key).build());
    }

    public String presignedGetUrl(String key, Duration ttl) {
        PresignedGetObjectRequest request = presigner.presignGetObject(
            b -> b.getObjectRequest(GetObjectRequest.builder()
                    .bucket(props.bucket()).key(key).build())
                .signatureDuration(ttl));
        return request.url().toString();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=S3StorageServiceIT`
Expected: PASS (нужен Docker).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skillhub/storage src/test
git commit -m "feat: add S3/MinIO storage service with presigned URLs"
```

---

### Task 7: Сервис архивов — валидация и file_index

**Files:**
- Create: `src/main/java/com/skillhub/core/service/ArchiveService.java`
- Test: `src/test/java/com/skillhub/core/service/ArchiveServiceTest.java`

**Interfaces:**
- Consumes: исключение `UnprocessableException` (Task 4); лимиты `skillhub.upload.*`.
- Produces:
  - `record FileEntry(String path, long size)`, `record ArchiveInfo(String manifestName, String manifestVersion, String manifestDescription, String manifestType, List<FileEntry> files, long totalSize)`
  - `ArchiveService.SEMVER` — `public static final Pattern`
  - `ArchiveInfo inspect(byte[] zipBytes)`; бросает `UnprocessableException` если: не zip; нет `manifest.json` в корне; нет name/version в manifest; version не semver; path traversal; превышены лимиты. Используется Task 9, Task 11.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/skillhub/core/service/ArchiveServiceTest.java`:

```java
package com.skillhub.core.service;

import com.skillhub.core.exception.UnprocessableException;
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
        assertThat(info.files()).extracting(ArchiveService.FileEntry::path)
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

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ArchiveServiceTest`
Expected: FAIL — класс не существует.

- [ ] **Step 3: Write implementation**

`core/service/ArchiveService.java`:

```java
package com.skillhub.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillhub.core.exception.UnprocessableException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class ArchiveService {

    public record FileEntry(String path, long size) {}
    public record ArchiveInfo(String manifestName, String manifestVersion,
                              String manifestDescription, String manifestType,
                              List<FileEntry> files, long totalSize) {}

    public static final Pattern SEMVER =
        Pattern.compile("^\\d+\\.\\d+\\.\\d+(-[0-9A-Za-z.-]+)?$");
    private static final String MANIFEST = "manifest.json";

    private final long maxUncompressedBytes;
    private final int maxFiles;
    private final ObjectMapper mapper = new ObjectMapper();

    public ArchiveService(@Value("${skillhub.upload.max-uncompressed-bytes}") long maxUncompressedBytes,
                          @Value("${skillhub.upload.max-files}") int maxFiles) {
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
                    manifestBytes = readEntryBytes(zin);
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

        JsonNode manifest = parseManifest(
            new String(manifestBytes, StandardCharsets.UTF_8));
        String name = requiredText(manifest, "name");
        String version = requiredText(manifest, "version");
        if (!SEMVER.matcher(version).matches()) {
            throw new UnprocessableException(
                "manifest version must be semver (e.g. 1.2.3)", version);
        }
        String description = manifest.path("description").asText("");
        String type = manifest.path("type").asText("OTHER");

        return new ArchiveInfo(name, version, description, type, files, total);
    }

    private byte[] readEntryBytes(ZipArchiveInputStream zin) throws IOException {
        return zin.readAllBytes();
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

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ArchiveServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skillhub/core/service/ArchiveService.java src/test
git commit -m "feat: add archive validation service with manifest parsing and zip protection"
```

---

### Task 8: Element API — создание, просмотр, список

**Files:**
- Create: `src/main/java/com/skillhub/core/service/AccessService.java`
- Create: `src/main/java/com/skillhub/core/service/ElementService.java`
- Create: `src/main/java/com/skillhub/api/dto/CreateElementRequest.java`
- Create: `src/main/java/com/skillhub/api/dto/ElementResponse.java`
- Create: `src/main/java/com/skillhub/api/ElementController.java`
- Delete: `src/main/java/com/skillhub/api/TestProtectedController.java` (заглушка из Task 5)
- Test: `src/test/java/com/skillhub/api/ElementApiIT.java`

**Interfaces:**
- Consumes: репозитории (Task 3), исключения (Task 4), `UserService` (Task 5); principal SecurityContext = `User`.
- Produces:
  - `AccessService`: `boolean canRead(Element, User)` (PUBLIC — всем; TEAM — участникам или админу), `boolean canPublish(Team, User)` (OWNER/MAINTAINER или админ), `boolean isTeamMember(Team, User)`.
  - `ElementService`: `Element create(CreateElementRequest, User)`, `Element getBySlug(String slug, User)` (404/403), `List<Element> listVisible(User)`.
  - `ElementResponse.from(Element)` — статическая фабрика.
  - REST: `POST /api/elements` → 201; `GET /api/elements/{slug}` → 200; `GET /api/elements` → 200 список.

`api/dto/CreateElementRequest.java`:

```java
package com.skillhub.api.dto;

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

`api/dto/ElementResponse.java`:

```java
package com.skillhub.api.dto;

import com.skillhub.core.model.Element;

public record ElementResponse(
    String slug,
    String type,
    String name,
    String description,
    String team,
    String category,
    String[] tags,
    String visibility,
    String latestVersion,
    long downloadsCount
) {
    public static ElementResponse from(Element e) {
        return new ElementResponse(
            e.getSlug(),
            e.getType().name(),
            e.getName(),
            e.getDescription(),
            e.getTeam().getSlug(),
            e.getCategory() == null ? null : e.getCategory().getSlug(),
            e.getTags(),
            e.getVisibility().name(),
            e.getLatestVersion(),
            e.getDownloadsCount());
    }
}
```

- [ ] **Step 1: Write the failing test**

`src/test/java/com/skillhub/api/ElementApiIT.java`:

```java
package com.skillhub.api;

import com.skillhub.api.dto.ElementResponse;
import com.skillhub.core.model.User;
import com.skillhub.core.repo.CategoryRepository;
import com.skillhub.core.repo.TeamRepository;
import com.skillhub.core.service.ApiTokenService;
import com.skillhub.core.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ElementApiIT {

    @Autowired TestRestTemplate rest;
    @Autowired UserService userService;
    @Autowired ApiTokenService tokens;
    @Autowired TeamRepository teams;
    @Autowired CategoryRepository categories;

    String authHeader;

    @BeforeEach
    void setUp() {
        User user = userService.syncFromSso("elem-user", "el@skillhub.io", "Element User");
        authHeader = "Bearer " + tokens.createToken(user, "elem").rawToken();

        teams.findBySlug("platform-team").orElseGet(() ->
            teams.save(Team.builder().slug("platform-team").name("Platform")
                .createdAt(Instant.now()).build()));
        categories.findBySlug("dev").orElseGet(() ->
            categories.save(Category.builder().slug("dev").name("Разработка").build()));
    }

    Map<String, Object> skillRequest(String slug) {
        return Map.of(
            "slug", slug, "type", "SKILL", "name", slug,
            "description", "d", "team", "platform-team",
            "category", "dev", "tags", new String[]{}, "visibility", "PUBLIC");
    }

    void createSkill(String slug) {
        rest.exchange("/api/elements", HttpMethod.POST,
            new HttpEntity<>(skillRequest(slug), jsonHeaders()), ElementResponse.class);
    }

    HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, authHeader);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    void createAndGetElement() {
        ResponseEntity<ElementResponse> created = rest.exchange("/api/elements", HttpMethod.POST,
            new HttpEntity<>(skillRequest("my-skill"), jsonHeaders()), ElementResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().slug()).isEqualTo("my-skill");

        ResponseEntity<ElementResponse> got = rest.exchange("/api/elements/my-skill",
            HttpMethod.GET, new HttpEntity<>(jsonHeaders()), ElementResponse.class);
        assertThat(got.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(got.getBody().team()).isEqualTo("platform-team");
    }

    @Test
    void duplicateSlugConflicts() {
        createSkill("dup-skill");
        ResponseEntity<String> second = rest.exchange("/api/elements", HttpMethod.POST,
            new HttpEntity<>(skillRequest("dup-skill"), jsonHeaders()), String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void unknownSlugIsNotFound() {
        ResponseEntity<String> r = rest.exchange("/api/elements/nope-404", HttpMethod.GET,
            new HttpEntity<>(jsonHeaders()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void invalidSlugIsUnprocessable() {
        ResponseEntity<String> r = rest.exchange("/api/elements", HttpMethod.POST,
            new HttpEntity<>(skillRequest("Bad Slug!"), jsonHeaders()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void listReturnsVisibleElements() {
        createSkill("public-skill");
        ResponseEntity<ElementResponse[]> list = rest.exchange("/api/elements", HttpMethod.GET,
            new HttpEntity<>(jsonHeaders()), ElementResponse[].class);
        assertThat(list.getBody()).extracting(ElementResponse::slug).contains("public-skill");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ElementApiIT`
Expected: FAIL — сервис/контроллер не существуют.

- [ ] **Step 3: Write implementation**

`core/service/AccessService.java`:

```java
package com.skillhub.core.service;

import com.skillhub.core.model.Element;
import com.skillhub.core.model.Team;
import com.skillhub.core.model.TeamRole;
import com.skillhub.core.model.User;
import com.skillhub.core.model.Visibility;
import com.skillhub.core.repo.TeamMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccessService {

    private final TeamMemberRepository members;

    public boolean canRead(Element element, User user) {
        if (element.getVisibility() == Visibility.PUBLIC) {
            return true;
        }
        return user.isAdmin() || isTeamMember(element.getTeam(), user);
    }

    public boolean canPublish(Team team, User user) {
        if (user.isAdmin()) {
            return true;
        }
        return members.findByTeamIdAndUserId(team.getId(), user.getId())
            .map(m -> m.getRole() == TeamRole.OWNER || m.getRole() == TeamRole.MAINTAINER)
            .orElse(false);
    }

    public boolean isTeamMember(Team team, User user) {
        return members.findByTeamIdAndUserId(team.getId(), user.getId()).isPresent();
    }
}
```

`core/service/ElementService.java`:

```java
package com.skillhub.core.service;

import com.skillhub.api.dto.CreateElementRequest;
import com.skillhub.core.exception.ConflictException;
import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.core.exception.NotFoundException;
import com.skillhub.core.model.*;
import com.skillhub.core.repo.CategoryRepository;
import com.skillhub.core.repo.ElementRepository;
import com.skillhub.core.repo.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ElementService {

    private final ElementRepository elements;
    private final TeamRepository teams;
    private final CategoryRepository categories;
    private final AccessService access;

    @Transactional
    public Element create(CreateElementRequest req, User author) {
        Team team = teams.findBySlug(req.team())
            .orElseThrow(() -> new NotFoundException("Team not found: " + req.team()));
        if (!access.canPublish(team, author)) {
            throw new ForbiddenException(
                "Only OWNER/MAINTAINER can publish to team " + team.getSlug());
        }
        if (elements.existsBySlug(req.slug())) {
            throw new ConflictException("Element already exists: " + req.slug());
        }
        Category category = req.category() == null ? null
            : categories.findBySlug(req.category())
                .orElseThrow(() -> new NotFoundException("Category not found: " + req.category()));
        Instant now = Instant.now();
        return elements.save(Element.builder()
            .slug(req.slug())
            .type(ElementType.valueOf(req.type()))
            .name(req.name())
            .description(req.description() == null ? "" : req.description())
            .team(team)
            .category(category)
            .tags(req.tags() == null ? new String[0] : req.tags())
            .visibility(Visibility.valueOf(req.visibility()))
            .author(author)
            .createdAt(now)
            .updatedAt(now)
            .build());
    }

    @Transactional(readOnly = true)
    public Element getBySlug(String slug, User viewer) {
        Element element = elements.findBySlug(slug)
            .orElseThrow(() -> new NotFoundException("Element not found: " + slug));
        if (viewer != null && !access.canRead(element, viewer)) {
            throw new ForbiddenException("Element is not visible to you: " + slug);
        }
        return element;
    }

    @Transactional(readOnly = true)
    public List<Element> listVisible(User viewer) {
        return elements.findAll().stream()
            .filter(e -> viewer == null || access.canRead(e, viewer))
            .toList();
    }
}
```

`api/ElementController.java`:

```java
package com.skillhub.api;

import com.skillhub.api.dto.CreateElementRequest;
import com.skillhub.api.dto.ElementResponse;
import com.skillhub.core.model.User;
import com.skillhub.core.service.ElementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/elements")
@RequiredArgsConstructor
public class ElementController {

    private final ElementService elementService;

    @PostMapping
    public ResponseEntity<ElementResponse> create(@Valid @RequestBody CreateElementRequest request,
                                                  @AuthenticationPrincipal User user) {
        ElementResponse created = ElementResponse.from(elementService.create(request, user));
        return ResponseEntity.status(HttpStatus.CREATED)
            .location(URI.create("/api/elements/" + created.slug()))
            .body(created);
    }

    @GetMapping("/{slug}")
    public ElementResponse get(@PathVariable String slug,
                               @AuthenticationPrincipal User user) {
        return ElementResponse.from(elementService.getBySlug(slug, user));
    }

    @GetMapping
    public List<ElementResponse> list(@AuthenticationPrincipal User user) {
        return elementService.listVisible(user).stream()
            .map(ElementResponse::from)
            .toList();
    }
}
```

Удалите `TestProtectedController.java` и обновите `ApiTokenAuthIT` — замените `/api/test-protected` на `/api/elements` (список видимых): тест `apiTokenGrantsAccessToProtectedEndpoint` проверяет `GET /api/elements` (200 с токеном, 401 без).

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest='ElementApiIT,ApiTokenAuthIT'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add -A src
git commit -m "feat: add element CRUD API with visibility-based access control"
```

---

### Task 9: Публикация версии (multipart + S3 + file_index + аудит)

**Files:**
- Create: `src/main/java/com/skillhub/core/service/AuditService.java`
- Create: `src/main/java/com/skillhub/core/service/VersionService.java`
- Create: `src/main/java/com/skillhub/api/dto/VersionResponse.java`
- Create: `src/main/java/com/skillhub/api/VersionController.java`
- Test: `src/test/java/com/skillhub/api/VersionPublishIT.java`

**Interfaces:**
- Consumes: `ArchiveService.inspect(byte[])` (Task 7), `S3StorageService` (Task 6), `ElementRepository` (Task 3), `AccessService` (Task 8).
- Produces:
  - `AuditService.log(User user, String action, UUID elementId, Object details)` — никогда не бросает исключений (логирует сбой).
  - `VersionService.publish(String slug, byte[] zipBytes, String changelog, User publisher)` → `ElementVersion`:
    1. элемент по slug (404 если нет);
    2. `canPublish` → иначе 403;
    3. `archiveService.inspect(zipBytes)` (422 при невалидном архиве);
    4. версия уже есть → 409;
    5. `s3Key = "{teamSlug}/{elementSlug}/{version}.zip"`, `storage.upload(s3Key, zipBytes)` — сначала S3;
    6. сохранить `ElementVersion` (status=PUBLISHED, file_index=JSON, size_bytes=zipBytes.length, published_at=now); обновить `element.latestVersion`;
    7. `audit.log(publisher, "PUBLISH_VERSION", elementId, {...})`.
  - `VersionResponse.from(ElementVersion)` — парсит file_index.
  - REST: `POST /api/elements/{slug}/versions?changelog=...` (multipart field `file`) → 201 `VersionResponse`.

`api/dto/VersionResponse.java`:

```java
package com.skillhub.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillhub.core.model.ElementVersion;

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

- [ ] **Step 1: Write the failing test**

`src/test/java/com/skillhub/api/VersionPublishIT.java`:

```java
package com.skillhub.api;

import com.skillhub.core.model.*;
import com.skillhub.core.repo.ElementRepository;
import com.skillhub.core.repo.TeamMemberRepository;
import com.skillhub.core.repo.TeamRepository;
import com.skillhub.core.service.ApiTokenService;
import com.skillhub.core.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VersionPublishIT {

    @Autowired protected TestRestTemplate rest;
    @Autowired UserService userService;
    @Autowired ApiTokenService tokens;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;
    @Autowired ElementRepository elements;

    protected User publisher;
    protected String authHeader;

    @BeforeEach
    void setUp() {
        publisher = userService.syncFromSso("pub-user", "pub@skillhub.io", "Publisher");
        authHeader = "Bearer " + tokens.createToken(publisher, "pub").rawToken();
        Team team = teams.findBySlug("pub-team").orElseGet(() ->
            teams.save(Team.builder().slug("pub-team").name("Pub")
                .createdAt(Instant.now()).build()));
        members.findByTeamIdAndUserId(team.getId(), publisher.getId()).orElseGet(() ->
            members.save(TeamMember.builder().team(team).user(publisher)
                .role(TeamRole.OWNER).build()));
        createElement("pub-skill");
    }

    void createElement(String slug) {
        if (elements.existsBySlug(slug)) {
            return;
        }
        Map<String, Object> request = Map.of(
            "slug", slug, "type", "SKILL", "name", slug, "description", "d",
            "team", "pub-team", "tags", new String[]{}, "visibility", "PUBLIC");
        rest.exchange("/api/elements", HttpMethod.POST,
            new HttpEntity<>(request, jsonHeaders()), String.class);
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

        Element el = elements.findBySlug("pub-skill").orElseThrow();
        assertThat(el.getLatestVersion()).isEqualTo("1.0.0");
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

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=VersionPublishIT`
Expected: FAIL — VersionService/VersionController не существуют (404).

- [ ] **Step 3: Write implementation**

`core/service/AuditService.java`:

```java
package com.skillhub.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillhub.core.model.AuditLog;
import com.skillhub.core.model.User;
import com.skillhub.core.repo.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLog;
    private final ObjectMapper mapper = new ObjectMapper();

    public void log(User user, String action, UUID elementId, Object details) {
        try {
            auditLog.save(AuditLog.builder()
                .user(user)
                .action(action)
                .elementId(elementId)
                .details(mapper.writeValueAsString(details == null ? "{}" : details))
                .createdAt(Instant.now())
                .build());
        } catch (Exception e) {
            log.warn("Failed to write audit log", e);
        }
    }
}
```

`core/service/VersionService.java` (в этой задаче — только `publish`; методы скачивания добавляются в Task 10):

```java
package com.skillhub.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillhub.core.exception.ConflictException;
import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.core.exception.NotFoundException;
import com.skillhub.core.model.*;
import com.skillhub.core.repo.ElementRepository;
import com.skillhub.core.repo.ElementVersionRepository;
import com.skillhub.storage.S3StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VersionService {

    private final ElementRepository elements;
    private final ElementVersionRepository versions;
    private final ArchiveService archiveService;
    private final S3StorageService storage;
    private final AccessService access;
    private final AuditService audit;
    private final ObjectMapper mapper = new ObjectMapper();

    @Transactional
    public ElementVersion publish(String slug, byte[] zipBytes, String changelog, User publisher) {
        Element element = elements.findBySlug(slug)
            .orElseThrow(() -> new NotFoundException("Element not found: " + slug));
        if (!access.canPublish(element.getTeam(), publisher)) {
            throw new ForbiddenException("Only OWNER/MAINTAINER can publish to this team");
        }

        ArchiveService.ArchiveInfo info = archiveService.inspect(zipBytes);

        if (versions.findByElementIdAndVersion(element.getId(), info.manifestVersion()).isPresent()) {
            throw new ConflictException(
                "Version " + info.manifestVersion() + " already exists for " + slug);
        }

        String s3Key = element.getTeam().getSlug() + "/" + element.getSlug()
            + "/" + info.manifestVersion() + ".zip";
        storage.upload(s3Key, zipBytes);

        Instant now = Instant.now();
        ElementVersion version = versions.save(ElementVersion.builder()
            .element(element)
            .version(info.manifestVersion())
            .status(VersionStatus.PUBLISHED)
            .changelog(changelog == null ? "" : changelog)
            .s3_key(s3Key)
            .sizeBytes(zipBytes.length)
            .fileIndex(buildFileIndex(info))
            .publishedBy(publisher)
            .createdAt(now)
            .publishedAt(now)
            .build());

        element.setLatestVersion(info.manifestVersion());
        element.setUpdatedAt(now);
        elements.save(element);

        audit.log(publisher, "PUBLISH_VERSION", element.getId(),
            Map.of("version", info.manifestVersion(), "s3Key", s3Key));
        return version;
    }

    private String buildFileIndex(ArchiveService.ArchiveInfo info) {
        ObjectNode root = mapper.createObjectNode();
        root.put("totalSize", info.totalSize());
        ArrayNode files = root.putArray("files");
        for (ArchiveService.FileEntry f : info.files()) {
            ObjectNode node = files.addObject();
            node.put("path", f.path());
            node.put("size", f.size());
        }
        return root.toString();
    }
}
```

`api/VersionController.java` (в этой задаче — только publish):

```java
package com.skillhub.api;

import com.skillhub.api.dto.VersionResponse;
import com.skillhub.core.model.User;
import com.skillhub.core.service.VersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/elements/{slug}/versions")
@RequiredArgsConstructor
public class VersionController {

    private final VersionService versionService;

    @PostMapping
    public ResponseEntity<VersionResponse> publish(@PathVariable String slug,
                                                   @RequestParam("file") MultipartFile file,
                                                   @RequestParam(value = "changelog", required = false) String changelog,
                                                   @AuthenticationPrincipal User user) throws IOException {
        VersionResponse published = VersionResponse.from(
            versionService.publish(slug, file.getBytes(), changelog, user));
        return ResponseEntity.status(HttpStatus.CREATED).body(published);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=VersionPublishIT`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add -A src
git commit -m "feat: add version publishing with archive validation, S3 upload and audit"
```

---

### Task 10: Скачивание — архив любой версии, latest, отдельный файл

**Files:**
- Modify: `src/main/java/com/skillhub/core/service/VersionService.java` — добавить `getVersion`, `listVersions`, `getArchive`, `getFile`, `incrementDownloads`
- Modify: `src/main/java/com/skillhub/api/VersionController.java` — добавить download endpoints
- Test: `src/test/java/com/skillhub/api/VersionDownloadIT.java`

**Interfaces:**
- Consumes: `VersionService.publish` (Task 9), `S3StorageService.download`, `AccessService.canRead`.
- Produces:
  - `ElementVersion getVersion(String slug, String version, User viewer)` — `version == "latest"` → последняя PUBLISHED по `published_at`; viewer может быть null (проверка прав пропускается — для внутренних вызовов паков). 404/403.
  - `List<ElementVersion> listVersions(String slug, User viewer)`.
  - `byte[] getArchive(ElementVersion)` — из S3 + инкремент `downloads_count`.
  - `byte[] getFile(ElementVersion, String path)` — извлечение файла из архива; 404 если пути нет.
  - REST: `GET /api/elements/{slug}/versions` → список; `GET /api/elements/{slug}/versions/{version}/download` → zip; `GET /api/elements/{slug}/versions/{version}/files?path=...` → байты файла.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/skillhub/api/VersionDownloadIT.java`:

```java
package com.skillhub.api;

import com.skillhub.api.dto.ElementResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class VersionDownloadIT extends VersionPublishIT {

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
        ResponseEntity<ElementResponse> el = rest.exchange(
            "/api/elements/pub-skill", HttpMethod.GET,
            new HttpEntity<>(jsonHeaders()), ElementResponse.class);
        assertThat(el.getBody().downloadsCount()).isGreaterThanOrEqualTo(1);
    }

    org.springframework.http.HttpHeaders authHeaders() {
        org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
        h.set(org.springframework.http.HttpHeaders.AUTHORIZATION, authHeader);
        return h;
    }
}
```

Замечание: тест наследует `VersionPublishIT` — поля `rest`, `authHeader` и методы `publish`, `jsonHeaders` уже объявлены там как `protected`/package; убедитесь, что доступ есть (поменяйте модификаторы `rest`/`authHeader`/`publisher` на `protected` в `VersionPublishIT`).

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=VersionDownloadIT`
Expected: FAIL — download endpoints не существуют (404).

- [ ] **Step 3: Write implementation**

Добавьте в `VersionService` методы (и импорты `org.apache.commons.compress.archivers.zip.ZipArchiveInputStream`, `org.apache.commons.compress.archivers.zip.ZipArchiveEntry`, `java.io.ByteArrayInputStream`, `java.io.IOException`, `java.util.List`, `com.skillhub.core.exception.UnprocessableException`):

```java
    @Transactional(readOnly = true)
    public ElementVersion getVersion(String slug, String version, User viewer) {
        Element element = elements.findBySlug(slug)
            .orElseThrow(() -> new NotFoundException("Element not found: " + slug));
        if (viewer != null && !access.canRead(element, viewer)) {
            throw new ForbiddenException("Element is not visible to you: " + slug);
        }
        return "latest".equals(version)
            ? versions.findFirstByElementIdAndStatusOrderByPublishedAtDesc(
                    element.getId(), VersionStatus.PUBLISHED)
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

Добавьте в `VersionController` (и импорты `com.skillhub.core.model.ElementVersion`, `org.springframework.http.HttpHeaders`, `org.springframework.http.MediaType`, `java.util.List`):

```java
    @GetMapping
    public List<VersionResponse> list(@PathVariable String slug,
                                      @AuthenticationPrincipal User user) {
        return versionService.listVersions(slug, user).stream()
            .map(VersionResponse::from)
            .toList();
    }

    @GetMapping("/{version}/download")
    public ResponseEntity<byte[]> download(@PathVariable String slug,
                                           @PathVariable String version,
                                           @AuthenticationPrincipal User user) {
        ElementVersion v = versionService.getVersion(slug, version, user);
        byte[] data = versionService.getArchive(v);
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
                                       @AuthenticationPrincipal User user) {
        ElementVersion v = versionService.getVersion(slug, version, user);
        byte[] data = versionService.getFile(v, path);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(data);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=VersionDownloadIT`
Expected: PASS (включая родительские тесты публикации).

- [ ] **Step 5: Commit**

```bash
git add -A src
git commit -m "feat: add version download, single-file access and downloads counter"
```

---

### Task 11: Паки — состав и скачивание

**Files:**
- Create: `src/main/java/com/skillhub/core/service/PackService.java`
- Create: `src/main/java/com/skillhub/api/dto/PackContentRequest.java`
- Create: `src/main/java/com/skillhub/api/dto/PackResponse.java`
- Create: `src/main/java/com/skillhub/api/PackController.java`
- Test: `src/test/java/com/skillhub/api/PackApiIT.java`

**Interfaces:**
- Consumes: `ElementService.getBySlug` (Task 8), `VersionService.getVersion` (Task 10, viewer=null допускается), `AccessService.canPublish`.
- Produces:
  - `PackService.addContent(String packSlug, PackContentRequest, User)` → `PackContent`; `versionConstraint` — `"latest"` или точная semver-версия (иначе 422).
  - `PackService.getContents(String packSlug, User)` → `List<PackContent>`.
  - `PackService.downloadPack(String packSlug, User)` → `byte[]` zip: `manifest.json` пака (`{"pack":"slug","contents":[{"element":"...","version":"..."}]}`) + файлы каждого элемента в подпапке `{element-slug}-{version}/`.
  - `PackResponse.from(String slug, List<PackContent>)`.
  - REST: `POST /api/packs/{slug}/contents` → 201; `GET /api/packs/{slug}` → 200; `GET /api/packs/{slug}/versions/latest/download` → 200 zip.

`api/dto/PackContentRequest.java`:

```java
package com.skillhub.api.dto;

import jakarta.validation.constraints.NotBlank;

public record PackContentRequest(
    @NotBlank String element,
    @NotBlank String versionConstraint
) {}
```

`api/dto/PackResponse.java`:

```java
package com.skillhub.api.dto;

import com.skillhub.core.model.PackContent;

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

- [ ] **Step 1: Write the failing test**

`src/test/java/com/skillhub/api/PackApiIT.java`:

```java
package com.skillhub.api;

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
                String name = entry.getName();
                if (name.equals("manifest.json")) {
                    foundManifest = true;
                }
                if (name.equals("pub-skill-6.0.0/SKILL.md")) {
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
        addContent("pub-skill", "pub-skill", "latest");
        ResponseEntity<String> r = rest.exchange("/api/packs/pub-skill/contents",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("element", "pub-skill", "versionConstraint", "latest"),
                jsonHeaders()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=PackApiIT`
Expected: FAIL — pack endpoints не существуют (404).

- [ ] **Step 3: Write implementation**

`core/service/PackService.java`:

```java
package com.skillhub.core.service;

import com.skillhub.api.dto.PackContentRequest;
import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.core.exception.UnprocessableException;
import com.skillhub.core.model.Element;
import com.skillhub.core.model.ElementType;
import com.skillhub.core.model.ElementVersion;
import com.skillhub.core.model.PackContent;
import com.skillhub.core.model.User;
import com.skillhub.core.repo.PackContentRepository;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class PackService {

    private final PackContentRepository packContents;
    private final ElementService elementService;
    private final VersionService versionService;
    private final AccessService access;

    @Transactional
    public PackContent addContent(String packSlug, PackContentRequest req, User user) {
        Element pack = elementService.getBySlug(packSlug, user);
        if (pack.getType() != ElementType.PACK) {
            throw new UnprocessableException("Element is not a PACK: " + packSlug, null);
        }
        if (!access.canPublish(pack.getTeam(), user)) {
            throw new ForbiddenException("Only OWNER/MAINTAINER can modify this pack");
        }
        Element element = elementService.getBySlug(req.element(), user);
        validateConstraint(req.versionConstraint());
        return packContents.save(PackContent.builder()
            .packElement(pack)
            .element(element)
            .versionConstraint(req.versionConstraint())
            .build());
    }

    @Transactional(readOnly = true)
    public List<PackContent> getContents(String packSlug, User viewer) {
        Element pack = elementService.getBySlug(packSlug, viewer);
        return packContents.findAllByPackElementId(pack.getId());
    }

    @Transactional(readOnly = true)
    public byte[] downloadPack(String packSlug, User viewer) {
        Element pack = elementService.getBySlug(packSlug, viewer);
        List<PackContent> contents = packContents.findAllByPackElementId(pack.getId());

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipArchiveOutputStream zos = new ZipArchiveOutputStream(bos)) {

            StringBuilder manifest = new StringBuilder("{\"pack\":\"")
                .append(pack.getSlug()).append("\",\"contents\":[");
            boolean first = true;
            for (PackContent content : contents) {
                ElementVersion version = resolveVersion(content);
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

    private ElementVersion resolveVersion(PackContent content) {
        return versionService.getVersion(
            content.getElement().getSlug(), content.getVersionConstraint(), null);
    }

    private void copyElementArchive(ZipArchiveOutputStream zos, ElementVersion version,
                                    String prefix) throws IOException {
        byte[] archive = versionService.getArchive(version);
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

`api/PackController.java`:

```java
package com.skillhub.api;

import com.skillhub.api.dto.PackContentRequest;
import com.skillhub.api.dto.PackResponse;
import com.skillhub.core.model.User;
import com.skillhub.core.service.PackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/packs/{slug}")
@RequiredArgsConstructor
public class PackController {

    private final PackService packService;

    @PostMapping("/contents")
    public ResponseEntity<PackResponse> addContent(@PathVariable String slug,
                                                   @Valid @RequestBody PackContentRequest request,
                                                   @AuthenticationPrincipal User user) {
        packService.addContent(slug, request, user);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(PackResponse.from(slug, packService.getContents(slug, user)));
    }

    @GetMapping
    public PackResponse get(@PathVariable String slug,
                            @AuthenticationPrincipal User user) {
        return PackResponse.from(slug, packService.getContents(slug, user));
    }

    @GetMapping("/versions/{version}/download")
    public ResponseEntity<byte[]> download(@PathVariable String slug,
                                           @PathVariable String version,
                                           @AuthenticationPrincipal User user) {
        byte[] data = packService.downloadPack(slug, user);
        String filename = slug + "-" + version + ".zip";
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(data);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=PackApiIT`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add -A src
git commit -m "feat: add pack contents and pack download endpoints"
```

---

### Task 12: Категории и команды (CRUD + участники)

**Files:**
- Create: `src/main/java/com/skillhub/api/dto/CreateCategoryRequest.java`
- Create: `src/main/java/com/skillhub/api/dto/CreateTeamRequest.java`
- Create: `src/main/java/com/skillhub/api/dto/AddMemberRequest.java`
- Create: `src/main/java/com/skillhub/api/dto/CategoryResponse.java`
- Create: `src/main/java/com/skillhub/api/dto/TeamResponse.java`
- Create: `src/main/java/com/skillhub/api/CategoryController.java`
- Create: `src/main/java/com/skillhub/api/TeamController.java`
- Create: `src/main/java/com/skillhub/core/service/TeamService.java`
- Test: `src/test/java/com/skillhub/api/CategoryTeamApiIT.java`

**Interfaces:**
- Consumes: `CategoryRepository`, `TeamRepository`, `TeamMemberRepository` (Task 3), `UserService` (Task 5).
- Produces:
  - `CategoryController`: `GET /api/categories` → список; `POST /api/categories` (админ) → 201.
  - `TeamService.create(name, slug, User)` → Team (создатель = OWNER); `addMember(teamSlug, username-подход не используется — по sso_subject роли не хватает, поэтому: пользователь должен уже существовать в системе)`.
  - `TeamController`: `GET /api/teams` → список; `POST /api/teams` → 201 (создатель = OWNER); `POST /api/teams/{slug}/members` body `{"ssoSubject": "...", "role": "MEMBER"}` → 200 (только OWNER команды или админ).
  - `CategoryResponse.from(Category)`, `TeamResponse.from(Team)`.

`api/dto/CreateCategoryRequest.java`:

```java
package com.skillhub.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCategoryRequest(
    @NotBlank String slug,
    @NotBlank String name,
    String parentSlug,
    String icon
) {}
```

`api/dto/CreateTeamRequest.java`:

```java
package com.skillhub.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateTeamRequest(
    @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]*$", message = "slug must be kebab-case")
    String slug,
    @NotBlank String name
) {}
```

`api/dto/AddMemberRequest.java`:

```java
package com.skillhub.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AddMemberRequest(
    @NotBlank String ssoSubject,
    @NotBlank @Pattern(regexp = "OWNER|MAINTAINER|MEMBER") String role
) {}
```

`api/dto/CategoryResponse.java`:

```java
package com.skillhub.api.dto;

import com.skillhub.core.model.Category;

public record CategoryResponse(String slug, String name, String parent, String icon) {
    public static CategoryResponse from(Category c) {
        return new CategoryResponse(c.getSlug(), c.getName(),
            c.getParent() == null ? null : c.getParent().getSlug(), c.getIcon());
    }
}
```

`api/dto/TeamResponse.java`:

```java
package com.skillhub.api.dto;

import com.skillhub.core.model.Team;

public record TeamResponse(String slug, String name) {
    public static TeamResponse from(Team t) {
        return new TeamResponse(t.getSlug(), t.getName());
    }
}
```

- [ ] **Step 1: Write the failing test**

`src/test/java/com/skillhub/api/CategoryTeamApiIT.java`:

```java
package com.skillhub.api;

import com.skillhub.core.model.User;
import com.skillhub.core.service.ApiTokenService;
import com.skillhub.core.service.UserService;
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
    @Autowired UserService userService;
    @Autowired ApiTokenService tokens;

    String adminHeader;
    String memberHeader;

    @BeforeEach
    void setUp() {
        User admin = userService.syncFromSso("cat-admin", "admin@skillhub.io", "Admin");
        admin.setAdmin(true);
        adminHeader = "Bearer " + tokens.createToken(admin, "admin").rawToken();

        User member = userService.syncFromSso("cat-member", "member@skillhub.io", "Member");
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
            new HttpEntity<>(Map.of("ssoSubject", "cat-admin", "role", "MEMBER"),
                headers(memberHeader)), String.class);
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=CategoryTeamApiIT`
Expected: FAIL — endpoints не существуют (404).

- [ ] **Step 3: Write implementation**

`api/CategoryController.java`:

```java
package com.skillhub.api;

import com.skillhub.api.dto.CategoryResponse;
import com.skillhub.api.dto.CreateCategoryRequest;
import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.core.exception.ConflictException;
import com.skillhub.core.exception.NotFoundException;
import com.skillhub.core.model.Category;
import com.skillhub.core.model.User;
import com.skillhub.core.repo.CategoryRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categories;

    @GetMapping
    public List<CategoryResponse> list() {
        return categories.findAllByOrderByNameAsc().stream()
            .map(CategoryResponse::from)
            .toList();
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest req,
                                                   @AuthenticationPrincipal User user) {
        if (!user.isAdmin()) {
            throw new ForbiddenException("Only admin can manage categories");
        }
        if (categories.existsBySlug(req.slug())) {
            throw new ConflictException("Category already exists: " + req.slug());
        }
        Category parent = req.parentSlug() == null ? null
            : categories.findBySlug(req.parentSlug())
                .orElseThrow(() -> new NotFoundException("Parent category not found: " + req.parentSlug()));
        Category saved = categories.save(Category.builder()
            .slug(req.slug()).name(req.name()).parent(parent).icon(req.icon()).build());
        return ResponseEntity.status(HttpStatus.CREATED).body(CategoryResponse.from(saved));
    }
}
```

`core/service/TeamService.java`:

```java
package com.skillhub.core.service;

import com.skillhub.core.exception.ConflictException;
import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.core.exception.NotFoundException;
import com.skillhub.core.model.*;
import com.skillhub.core.repo.TeamMemberRepository;
import com.skillhub.core.repo.TeamRepository;
import com.skillhub.core.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final UserRepository users;

    @Transactional
    public Team create(String slug, String name, User creator) {
        if (teams.findBySlug(slug).isPresent()) {
            throw new ConflictException("Team already exists: " + slug);
        }
        Team team = teams.save(Team.builder()
            .slug(slug).name(name).createdAt(Instant.now()).build());
        members.save(TeamMember.builder()
            .team(team).user(creator).role(TeamRole.OWNER).build());
        return team;
    }

    @Transactional(readOnly = true)
    public List<Team> list() {
        return teams.findAll();
    }

    @Transactional
    public TeamMember addMember(String teamSlug, String ssoSubject, String role, User actor) {
        Team team = teams.findBySlug(teamSlug)
            .orElseThrow(() -> new NotFoundException("Team not found: " + teamSlug));
        if (!actor.isAdmin()) {
            members.findByTeamIdAndUserId(team.getId(), actor.getId())
                .filter(m -> m.getRole() == TeamRole.OWNER)
                .orElseThrow(() -> new ForbiddenException("Only team OWNER can add members"));
        }
        User newMember = users.findBySsoSubject(ssoSubject)
            .orElseThrow(() -> new NotFoundException("User not found: " + ssoSubject));
        return members.save(TeamMember.builder()
            .team(team).user(newMember).role(TeamRole.valueOf(role)).build());
    }
}
```

`api/TeamController.java`:

```java
package com.skillhub.api;

import com.skillhub.api.dto.AddMemberRequest;
import com.skillhub.api.dto.CreateTeamRequest;
import com.skillhub.api.dto.TeamResponse;
import com.skillhub.core.model.TeamMember;
import com.skillhub.core.model.User;
import com.skillhub.core.service.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @GetMapping
    public List<TeamResponse> list() {
        return teamService.list().stream().map(TeamResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<TeamResponse> create(@Valid @RequestBody CreateTeamRequest req,
                                               @AuthenticationPrincipal User user) {
        TeamResponse created = TeamResponse.from(
            teamService.create(req.slug(), req.name(), user));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{slug}/members")
    public ResponseEntity<Map<String, String>> addMember(@PathVariable String slug,
                                                         @Valid @RequestBody AddMemberRequest req,
                                                         @AuthenticationPrincipal User user) {
        TeamMember member = teamService.addMember(slug, req.ssoSubject(), req.role(), user);
        return ResponseEntity.ok(Map.of(
            "ssoSubject", member.getUser().getSsoSubject(),
            "role", member.getRole().name()));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=CategoryTeamApiIT`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add -A src
git commit -m "feat: add category and team management APIs"
```

---

### Task 13: Рейтинги, отзывы, избранное

**Files:**
- Create: `src/main/java/com/skillhub/core/service/SocialService.java`
- Create: `src/main/java/com/skillhub/api/dto/RateRequest.java`
- Create: `src/main/java/com/skillhub/api/dto/ReviewRequest.java`
- Create: `src/main/java/com/skillhub/api/dto/ReviewResponse.java`
- Create: `src/main/java/com/skillhub/api/SocialController.java`
- Modify: `src/main/java/com/skillhub/core/repo/RatingRepository.java` — добавить `avgRating`, `countByElementId`
- Test: `src/test/java/com/skillhub/api/SocialApiIT.java`

**Interfaces:**
- Consumes: `ElementService.getBySlug` (Task 8), репозитории (Task 3).
- Produces:
  - `SocialService.rate(slug, user, rating)` — upsert 1–5 (иначе 422); `review(slug, user, rating, text)` — upsert; `favorite(slug, user)` / `unfavorite(slug, user)` — добавление/удаление; `getReviews(slug)` → список; `getSocialInfo(slug, user)` → `record SocialInfo(double avgRating, long ratingCount, boolean favorited)`.
  - REST:
    - `PUT /api/elements/{slug}/rating` body `{"rating": 5}` → 200
    - `PUT /api/elements/{slug}/review` body `{"rating": 5, "text": "..."}` → 200
    - `GET /api/elements/{slug}/reviews` → список
    - `POST /api/elements/{slug}/favorite` → 200 `{favorited: true}`; `DELETE` → `{favorited: false}`
    - `GET /api/elements/{slug}/social` → `{avgRating, ratingCount, favorited}`

`api/dto/RateRequest.java`:

```java
package com.skillhub.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RateRequest(@Min(1) @Max(5) int rating) {}
```

`api/dto/ReviewRequest.java`:

```java
package com.skillhub.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ReviewRequest(@Min(1) @Max(5) int rating, @NotBlank String text) {}
```

`api/dto/ReviewResponse.java`:

```java
package com.skillhub.api.dto;

import com.skillhub.core.model.Review;

public record ReviewResponse(String author, int rating, String text, String createdAt) {
    public static ReviewResponse from(Review r) {
        return new ReviewResponse(r.getUser().getDisplayName(), r.getRating(),
            r.getText(), r.getCreatedAt().toString());
    }
}
```

- [ ] **Step 1: Write the failing test**

`src/test/java/com/skillhub/api/SocialApiIT.java`:

```java
package com.skillhub.api;

import com.skillhub.core.model.User;
import com.skillhub.core.service.ApiTokenService;
import com.skillhub.core.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SocialApiIT {

    @Autowired TestRestTemplate rest;
    @Autowired UserService userService;
    @Autowired ApiTokenService tokens;
    @Autowired com.skillhub.core.repo.TeamRepository teams;
    @Autowired com.skillhub.core.repo.TeamMemberRepository members;
    @Autowired com.skillhub.core.repo.ElementRepository elements;

    String authHeader;

    @BeforeEach
    void setUp() {
        User user = userService.syncFromSso("soc-user", "soc@skillhub.io", "Social User");
        authHeader = "Bearer " + tokens.createToken(user, "soc").rawToken();

        com.skillhub.core.model.Team team = teams.findBySlug("soc-team").orElseGet(() ->
            teams.save(com.skillhub.core.model.Team.builder()
                .slug("soc-team").name("Soc").createdAt(Instant.now()).build()));
        members.save(com.skillhub.core.model.TeamMember.builder()
            .team(team).user(user)
            .role(com.skillhub.core.model.TeamRole.OWNER).build());

        if (!elements.existsBySlug("soc-skill")) {
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
        rest.exchange("/api/elements/soc-skill/rating", HttpMethod.PUT,
            new HttpEntity<>(Map.of("rating", 4), jsonHeaders()), String.class);
        ResponseEntity<String> info = rest.exchange("/api/elements/soc-skill/social",
            HttpMethod.GET, new HttpEntity<>(jsonHeaders()), String.class);
        assertThat(info.getBody()).contains("\"avgRating\":4.0").contains("\"ratingCount\":1");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=SocialApiIT`
Expected: FAIL — endpoints не существуют (404).

- [ ] **Step 3: Write implementation**

`core/service/SocialService.java`:

```java
package com.skillhub.core.service;

import com.skillhub.core.exception.UnprocessableException;
import com.skillhub.core.model.*;
import com.skillhub.core.repo.FavoriteRepository;
import com.skillhub.core.repo.RatingRepository;
import com.skillhub.core.repo.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SocialService {

    public record SocialInfo(double avgRating, long ratingCount, boolean favorited) {}

    private final RatingRepository ratings;
    private final ReviewRepository reviews;
    private final FavoriteRepository favorites;
    private final ElementService elementService;

    @Transactional
    public void rate(String slug, User user, int rating) {
        validateRating(rating);
        Element element = elementService.getBySlug(slug, user);
        RatingId id = new RatingId(element.getId(), user.getId());
        ratings.save(Rating.builder()
            .elementId(id.getElementId()).userId(id.getUserId()).rating(rating).build());
    }

    @Transactional
    public void review(String slug, User user, int rating, String text) {
        validateRating(rating);
        Element element = elementService.getBySlug(slug, user);
        reviews.findByElementIdAndUserId(element.getId(), user.getId())
            .ifPresentOrElse(existing -> {
                existing.setRating(rating);
                existing.setText(text);
                reviews.save(existing);
            }, () -> reviews.save(Review.builder()
                .element(element).user(user).rating(rating).text(text)
                .createdAt(Instant.now()).build()));
    }

    @Transactional(readOnly = true)
    public List<Review> getReviews(String slug, User viewer) {
        Element element = elementService.getBySlug(slug, viewer);
        return reviews.findAllByElementIdOrderByCreatedAtDesc(element.getId());
    }

    @Transactional
    public boolean toggleFavorite(String slug, User user, boolean add) {
        Element element = elementService.getBySlug(slug, user);
        if (add) {
            favorites.save(Favorite.builder()
                .userId(user.getId()).elementId(element.getId())
                .createdAt(Instant.now()).build());
        } else {
            favorites.findByUserIdAndElementId(user.getId(), element.getId())
                .ifPresent(favorites::delete);
        }
        return add;
    }

    @Transactional(readOnly = true)
    public SocialInfo getSocialInfo(String slug, User viewer) {
        Element element = elementService.getBySlug(slug, viewer);
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

Дополните `core/repo/RatingRepository.java` двумя методами:

```java
    @org.springframework.data.jpa.repository.Query(
        "SELECT COALESCE(AVG(r.rating), 0.0) FROM Rating r WHERE r.elementId = :elementId")
    double avgRating(@org.springframework.data.repository.query.Param("elementId") java.util.UUID elementId);

    @org.springframework.data.jpa.repository.Query(
        "SELECT COUNT(r) FROM Rating r WHERE r.elementId = :elementId")
    long countByElementId(@org.springframework.data.repository.query.Param("elementId") java.util.UUID elementId);
```

`api/SocialController.java`:

```java
package com.skillhub.api;

import com.skillhub.api.dto.RateRequest;
import com.skillhub.api.dto.ReviewRequest;
import com.skillhub.api.dto.ReviewResponse;
import com.skillhub.core.model.User;
import com.skillhub.core.service.SocialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/elements/{slug}")
@RequiredArgsConstructor
public class SocialController {

    private final SocialService socialService;

    @PutMapping("/rating")
    public Map<String, Object> rate(@PathVariable String slug,
                                    @Valid @RequestBody RateRequest request,
                                    @AuthenticationPrincipal User user) {
        socialService.rate(slug, user, request.rating());
        return Map.of("rating", request.rating());
    }

    @PutMapping("/review")
    public Map<String, Object> review(@PathVariable String slug,
                                      @Valid @RequestBody ReviewRequest request,
                                      @AuthenticationPrincipal User user) {
        socialService.review(slug, user, request.rating(), request.text());
        return Map.of("rating", request.rating());
    }

    @GetMapping("/reviews")
    public List<ReviewResponse> reviews(@PathVariable String slug,
                                        @AuthenticationPrincipal User user) {
        return socialService.getReviews(slug, user).stream()
            .map(ReviewResponse::from)
            .toList();
    }

    @PostMapping("/favorite")
    public Map<String, Boolean> favorite(@PathVariable String slug,
                                         @AuthenticationPrincipal User user) {
        return Map.of("favorited", socialService.toggleFavorite(slug, user, true));
    }

    @DeleteMapping("/favorite")
    public Map<String, Boolean> unfavorite(@PathVariable String slug,
                                           @AuthenticationPrincipal User user) {
        return Map.of("favorited", socialService.toggleFavorite(slug, user, false));
    }

    @GetMapping("/social")
    public Map<String, Object> social(@PathVariable String slug,
                                      @AuthenticationPrincipal User user) {
        SocialService.SocialInfo info = socialService.getSocialInfo(slug, user);
        return Map.of("avgRating", info.avgRating(),
            "ratingCount", info.ratingCount(), "favorited", info.favorited());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=SocialApiIT`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add -A src
git commit -m "feat: add ratings, reviews and favorites APIs"
```

---

### Task 14: Полнотекстовый поиск с фасетами

**Files:**
- Modify: `src/main/java/com/skillhub/core/repo/ElementRepository.java` — native search queries
- Create: `src/main/java/com/skillhub/core/service/SearchService.java`
- Create: `src/main/java/com/skillhub/api/dto/SearchResultResponse.java`
- Create: `src/main/java/com/skillhub/api/dto/SearchFacets.java`
- Create: `src/main/java/com/skillhub/api/SearchController.java`
- Test: `src/test/java/com/skillhub/api/SearchApiIT.java`

**Interfaces:**
- Consumes: колонка `elements.search_vector` (Task 2), `AccessService` (Task 8).
- Produces:
  - `ElementRepository` — native queries `searchElements` (с учётом видимости: PUBLIC или членство в команде) и `countByTypeForSearch` (фасеты).
  - `SearchService.search(q, category, type, user, limit, offset)` → `record SearchResult(List<Element> items, long total)`; `facets(q, user)` → `record Facets(Map<String, Long> byType)`.
  - REST: `GET /api/search?q=...&type=...&category=...&limit=20&offset=0` → 200 `{items: [ElementResponse], total, facets: {byType: {SKILL: 2, ...}}}`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/skillhub/api/SearchApiIT.java`:

```java
package com.skillhub.api;

import com.skillhub.core.model.TeamRole;
import com.skillhub.core.model.User;
import com.skillhub.core.repo.ElementRepository;
import com.skillhub.core.repo.TeamMemberRepository;
import com.skillhub.core.repo.TeamRepository;
import com.skillhub.core.service.ApiTokenService;
import com.skillhub.core.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SearchApiIT {

    @Autowired TestRestTemplate rest;
    @Autowired UserService userService;
    @Autowired ApiTokenService tokens;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;
    @Autowired ElementRepository elements;

    String authHeader;

    @BeforeEach
    void setUp() {
        User user = userService.syncFromSso("search-user", "search@skillhub.io", "Search User");
        authHeader = "Bearer " + tokens.createToken(user, "search").rawToken();

        var team = teams.findBySlug("search-team").orElseGet(() ->
            teams.save(com.skillhub.core.model.Team.builder()
                .slug("search-team").name("Search").createdAt(Instant.now()).build()));
        members.save(com.skillhub.core.model.TeamMember.builder()
            .team(team).user(user).role(TeamRole.OWNER).build());

        createElement("pdf-docs-skill", "SKILL", "PDF Documentation Skill",
            "Работа с PDF документами", "PUBLIC", new String[]{"pdf", "docs"});
        createElement("logo-design", "SKILL", "Logo Design Skill",
            "Design logos quickly", "PUBLIC", new String[]{"design"});
        createElement("private-search-item", "SCRIPT", "Private Script",
            "secret stuff", "TEAM", new String[]{});
    }

    void createElement(String slug, String type, String name, String description,
                       String visibility, String[] tags) {
        if (elements.existsBySlug(slug)) {
            return;
        }
        rest.exchange("/api/elements", HttpMethod.POST,
            new HttpEntity<>(Map.of(
                "slug", slug, "type", type, "name", name,
                "description", description, "team", "search-team",
                "tags", tags, "visibility", visibility),
                jsonHeaders()), String.class);
    }

    HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, authHeader);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    void searchFindsByDescription() {
        ResponseEntity<String> r = rest.exchange(
            "/api/search?q=PDF", HttpMethod.GET, new HttpEntity<>(jsonHeaders()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("pdf-docs-skill");
    }

    @Test
    void searchByRussianText() {
        ResponseEntity<String> r = rest.exchange(
            "/api/search?q=" + java.net.URLEncoder.encode("документами",
                java.nio.charset.StandardCharsets.UTF_8),
            HttpMethod.GET, new HttpEntity<>(jsonHeaders()), String.class);
        assertThat(r.getBody()).contains("pdf-docs-skill");
    }

    @Test
    void searchRespectsVisibility() {
        ResponseEntity<String> r = rest.exchange(
            "/api/search?q=secret", HttpMethod.GET,
            new HttpEntity<>(jsonHeaders()), String.class);
        // участник команды видит TEAM-элемент
        assertThat(r.getBody()).contains("private-search-item");
    }

    @Test
    void searchFiltersByType() {
        ResponseEntity<String> r = rest.exchange(
            "/api/search?q=skill&type=SKILL", HttpMethod.GET,
            new HttpEntity<>(jsonHeaders()), String.class);
        assertThat(r.getBody()).contains("pdf-docs-skill");
    }

    @Test
    void searchReturnsFacets() {
        ResponseEntity<String> r = rest.exchange(
            "/api/search?q=skill", HttpMethod.GET,
            new HttpEntity<>(jsonHeaders()), String.class);
        assertThat(r.getBody()).contains("byType");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=SearchApiIT`
Expected: FAIL — search endpoint не существует (404).

- [ ] **Step 3: Write implementation**

Дополните `core/repo/ElementRepository.java`:

```java
    @Query(value = """
        SELECT * FROM elements e
        WHERE (e.visibility = 'PUBLIC'
               OR e.team_id IN (SELECT tm.team_id FROM team_members tm WHERE tm.user_id = :userId))
          AND (:type IS NULL OR e.type = :type)
          AND (:category IS NULL OR e.category_id = (SELECT c.id FROM categories c WHERE c.slug = :category))
          AND (:q = ''
               OR e.search_vector @@ plainto_tsquery('simple', :q)
               OR e.name ILIKE ('%' || :q || '%'))
        ORDER BY CASE WHEN :q = '' THEN 0
                      ELSE ts_rank(e.search_vector, plainto_tsquery('simple', :q)) END DESC,
                 e.downloads_count DESC
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<Element> searchElements(@Param("q") String q,
                                 @Param("type") String type,
                                 @Param("category") String category,
                                 @Param("userId") UUID userId,
                                 @Param("limit") int limit,
                                 @Param("offset") int offset);

    @Query(value = """
        SELECT e.type AS type, COUNT(*) AS cnt FROM elements e
        WHERE (e.visibility = 'PUBLIC'
               OR e.team_id IN (SELECT tm.team_id FROM team_members tm WHERE tm.user_id = :userId))
          AND (:type IS NULL OR e.type = :type)
          AND (:category IS NULL OR e.category_id = (SELECT c.id FROM categories c WHERE c.slug = :category))
          AND (:q = ''
               OR e.search_vector @@ plainto_tsquery('simple', :q)
               OR e.name ILIKE ('%' || :q || '%'))
        GROUP BY e.type
        """, nativeQuery = true)
    List<Object[]> countByTypeForSearch(@Param("q") String q,
                                        @Param("type") String type,
                                        @Param("category") String category,
                                        @Param("userId") UUID userId);

    @Query(value = """
        SELECT COUNT(*) FROM elements e
        WHERE (e.visibility = 'PUBLIC'
               OR e.team_id IN (SELECT tm.team_id FROM team_members tm WHERE tm.user_id = :userId))
          AND (:type IS NULL OR e.type = :type)
          AND (:category IS NULL OR e.category_id = (SELECT c.id FROM categories c WHERE c.slug = :category))
          AND (:q = ''
               OR e.search_vector @@ plainto_tsquery('simple', :q)
               OR e.name ILIKE ('%' || :q || '%'))
        """, nativeQuery = true)
    long countForSearch(@Param("q") String q,
                        @Param("type") String type,
                        @Param("category") String category,
                        @Param("userId") UUID userId);
```

Импорты в `ElementRepository`: `org.springframework.data.repository.query.Param`, `org.springframework.data.jpa.repository.Query`.

`core/service/SearchService.java`:

```java
package com.skillhub.core.service;

import com.skillhub.core.model.Element;
import com.skillhub.core.model.User;
import com.skillhub.core.repo.ElementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SearchService {

    public record SearchResult(List<Element> items, long total, Map<String, Long> facetsByType) {}

    private final ElementRepository elements;

    @Transactional(readOnly = true)
    public SearchResult search(String q, String type, String category, User user,
                               int limit, int offset) {
        UUID userId = user == null ? UUID.nameUUIDFromBytes("anonymous".getBytes()) : user.getId();
        List<Element> items = elements.searchElements(
            q == null ? "" : q, type, category, userId, limit, offset);
        long total = elements.countForSearch(q == null ? "" : q, type, category, userId);
        Map<String, Long> facets = new LinkedHashMap<>();
        for (Object[] row : elements.countByTypeForSearch(q == null ? "" : q, type, category, userId)) {
            facets.put((String) row[0], ((Number) row[1]).longValue());
        }
        return new SearchResult(items, total, facets);
    }
}
```

`api/dto/SearchResultResponse.java`:

```java
package com.skillhub.api.dto;

import java.util.List;
import java.util.Map;

public record SearchResultResponse(List<ElementResponse> items, long total,
                                   Map<String, Long> facetsByType) {}
```

`api/SearchController.java`:

```java
package com.skillhub.api;

import com.skillhub.api.dto.ElementResponse;
import com.skillhub.api.dto.SearchResultResponse;
import com.skillhub.core.model.User;
import com.skillhub.core.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public SearchResultResponse search(@RequestParam(value = "q", defaultValue = "") String q,
                                       @RequestParam(value = "type", required = false) String type,
                                       @RequestParam(value = "category", required = false) String category,
                                       @RequestParam(value = "limit", defaultValue = "20") int limit,
                                       @RequestParam(value = "offset", defaultValue = "0") int offset,
                                       @AuthenticationPrincipal User user) {
        SearchService.SearchResult result = searchService.search(q, type, category, user, limit, offset);
        List<ElementResponse> items = result.items().stream()
            .map(ElementResponse::from)
            .toList();
        return new SearchResultResponse(items, result.total(), result.facetsByType());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=SearchApiIT`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add -A src
git commit -m "feat: add full-text search with visibility filtering and facets"
```

---

### Task 15: Финальная проверка — полный прогон тестов

**Files:**
- Modify: при необходимости фиксы по результатам прогона

**Interfaces:**
- Consumes: все предыдущие задачи.
- Produces: зелёная сборка `mvn test` и `mvn package`.

- [ ] **Step 1: Полный прогон**

Run: `mvn clean test`
Expected: все тесты PASS.

- [ ] **Step 2: Сборка jar**

Run: `mvn package -DskipTests`
Expected: `target/skillhub-api-0.1.0-SNAPSHOT.jar` создан.

- [ ] **Step 3: Ручная smoke-проверка с dev-инфраструктурой**

```bash
docker compose up -d
java -jar target/skillhub-api-0.1.0-SNAPSHOT.jar
# в другом терминале:
curl -s http://localhost:8080/actuator/health
```
Expected: `{"status":"UP"}`. (Flyway применит миграции, MinIO bucket будет создан при первой публикации.)

- [ ] **Step 4: Commit (если были фиксы)**

```bash
git add -A src
git commit -m "fix: stabilize integration tests"
```

---

## Отложено (следующие планы)

- **Web UI (React)** — отдельный план: каталог, карточки, browse файлов, админка, создание API-токенов.
- **CLI** — отдельный план: `install`/`publish`, чтение `manifest.json`, Bearer-токен.
- Orphan-cleanup job для S3 (фоновая чистка при сбое PG после S3-записи) — можно добавить в план UI/полировки.
- Депрекация версий (endpoint `POST .../versions/{v}/deprecate`) — малая задача, добавить при необходимости.
