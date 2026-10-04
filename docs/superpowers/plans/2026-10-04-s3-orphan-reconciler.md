# S3 Orphan Reconciler Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Фоновая джоба раз в час удаляет из S3 объекты-сироты (загружены, но не зафиксированы в `element_versions`), старше грейс-периода 24 ч.

**Architecture:** Полная сверка множеств: новый `StoragePort.list()` (пагинированный `listObjectsV2Paginator`) + `ElementVersionRepositoryPort.findAllS3Keys()` (JPQL `select v.s3Key`). Новый `StorageReconciliationService` с `@Scheduled`-методом удаляет ключи, которых нет в БД и чей `lastModified` старше грейса; каждая ошибка изолирована. `VersionUseCase.publish` и схема БД не меняются.

**Tech Stack:** Spring Boot 3 (hexagonal: domain/port/adapters, Lombok), AWS SDK v2 S3, JUnit 5 + Mockito (unit), Testcontainers (MinIO IT, Postgres IT, failsafe для `*IT`).

## Global Constraints

- Изменения только в Java-коде и `application.yml`; схема БД и `VersionUseCase.publish` не трогаются.
- Свойства конфига (verbatim): `skillhub.storage.reconcile-enabled` (default `true`, `matchIfMissing = true`), `skillhub.storage.reconcile-cron` (default `0 0 * * * *`), `skillhub.storage.reconcile-grace` (default `24h`, тип `Duration`).
- Грейс-правило: удаляется только объект, у которого `!known.contains(key)` И `lastModified <= clock.now() - grace` (грейс защищает незакоммиченные публикации).
- Каждый `delete` — в собственном try/catch (WARN, продолжаем); всё тело `reconcile` — в общем try/catch (ERROR, шедулер не падает).
- Логи: INFO при удалении сироты (`Deleted orphaned storage object: {key}`), WARN при ошибке delete, ERROR при падении сверки; сообщение WARN при неудаче delete: `Failed to delete orphaned storage object: {key}`.
- Unit-тесты — Mockito в стиле существующих (`VersionUseCaseTest`); IT — `@SpringBootTest @Transactional` (JPA) или Testcontainer MinIO (S3).
- Команды: unit — `mvn test -Dtest=<Class>`; IT — `mvn verify -Dit.test=<Class>`; полный прогон — `mvn verify` (Docker должен быть запущен для Testcontainers).

---

### Task 1: `StoragePort.list()` и реализация в `S3StorageAdapter`

**Files:**
- Create: `src/main/java/com/skillhub/domain/model/StorageObjectInfo.java`
- Modify: `src/main/java/com/skillhub/domain/port/StoragePort.java`
- Modify: `src/main/java/com/skillhub/adapters/out/s3/S3StorageAdapter.java`
- Test: `src/test/java/com/skillhub/adapters/out/s3/S3StorageAdapterIT.java`

**Interfaces:**
- Consumes: существующий `S3StorageAdapter` (поле `bucket`, `S3Client client`).
- Produces (используется Task 3):
  ```java
  // domain/model
  public record StorageObjectInfo(String key, Instant lastModified) {}
  // domain/port StoragePort — новый метод
  List<StorageObjectInfo> list();
  ```

- [ ] **Step 1: Написать падающие тесты (добавить в `S3StorageAdapterIT`)**

В `src/test/java/com/skillhub/adapters/out/s3/S3StorageAdapterIT.java` добавить импорты и два теста:

```java
import com.skillhub.domain.model.StorageObjectInfo;
import java.util.List;
import java.util.stream.Collectors;
```

```java
@Test
void listReturnsAllObjectsWithKeysAndTimestamps() {
    storage.upload("team/list-el/1.0.0.zip", "a".getBytes());
    storage.upload("personal/list-el/1.0.0.zip", "b".getBytes());

    List<StorageObjectInfo> objects = storage.list();

    assertThat(objects).extracting(StorageObjectInfo::key)
        .contains("team/list-el/1.0.0.zip", "personal/list-el/1.0.0.zip");
    assertThat(objects).allSatisfy(o -> assertThat(o.lastModified()).isNotNull());
}

@Test
void listPaginatesAcrossMultiplePages() {
    for (int i = 0; i < 1005; i++) {
        storage.upload("team/paginate/" + i + ".zip", new byte[] {1});
    }
    long count = storage.list().stream()
        .map(StorageObjectInfo::key)
        .filter(k -> k.startsWith("team/paginate/"))
        .distinct()
        .count();
    assertThat(count).isEqualTo(1005);
}
```

- [ ] **Step 2: Запустить тесты, убедиться что падают**

Run: `mvn verify -Dit.test=S3StorageAdapterIT`
Expected: FAIL — компиляция: метод `list()` не существует у `StoragePort` (и класса `StorageObjectInfo` нет). Это ожидаемый RED.

- [ ] **Step 3: Реализовать**

`src/main/java/com/skillhub/domain/model/StorageObjectInfo.java` (новый файл):

```java
package com.skillhub.domain.model;

import java.time.Instant;

public record StorageObjectInfo(String key, Instant lastModified) {}
```

В `src/main/java/com/skillhub/domain/port/StoragePort.java` добавить импорт и метод (интерфейс станет):

```java
package com.skillhub.domain.port;

import com.skillhub.domain.model.StorageObjectInfo;

import java.time.Duration;
import java.util.List;

public interface StoragePort {
    void upload(String key, byte[] content);
    byte[] download(String key);
    void delete(String key);
    String presignedGetUrl(String key, Duration ttl);
    List<StorageObjectInfo> list();
}
```

В `src/main/java/com/skillhub/adapters/out/s3/S3StorageAdapter.java` добавить импорты и метод (класс уже импортирует `software.amazon.awssdk.services.s3.model.*`, нужен только доменный импорт):

```java
import com.skillhub.domain.model.StorageObjectInfo;
import java.util.ArrayList;
import java.util.List;
```

```java
@Override
public List<StorageObjectInfo> list() {
    List<StorageObjectInfo> result = new ArrayList<>();
    ListObjectsV2Paginator paginator = client.listObjectsV2Paginator(
        ListObjectsV2Request.builder().bucket(bucket).build());
    paginator.contents().forEach(o ->
        result.add(new StorageObjectInfo(o.key(), o.lastModified())));
    return result;
}
```

- [ ] **Step 4: Запустить тесты, убедиться что проходят**

Run: `mvn verify -Dit.test=S3StorageAdapterIT`
Expected: PASS — все тесты класса, включая новые два (1005 PUT в локальный MinIO занимает секунды).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skillhub/domain/model/StorageObjectInfo.java src/main/java/com/skillhub/domain/port/StoragePort.java src/main/java/com/skillhub/adapters/out/s3/S3StorageAdapter.java src/test/java/com/skillhub/adapters/out/s3/S3StorageAdapterIT.java
git commit -m "feat: list storage objects via StoragePort with S3 pagination"
```

---

### Task 2: `ElementVersionRepositoryPort.findAllS3Keys()`

**Files:**
- Modify: `src/main/java/com/skillhub/domain/port/ElementVersionRepositoryPort.java`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/repository/JpaElementVersionRepository.java`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/JpaElementVersionRepositoryAdapter.java`
- Test: `src/test/java/com/skillhub/adapters/out/jpa/JpaElementVersionRepositoryAdapterIT.java` (новый)

**Interfaces:**
- Consumes: сущность `JpaElementVersion` с полем `s3Key` (JpaElementVersion.java:22).
- Produces (используется Task 3):
  ```java
  // domain/port ElementVersionRepositoryPort — новый метод
  Set<String> findAllS3Keys();
  ```

- [ ] **Step 1: Написать падающий тест**

`src/test/java/com/skillhub/adapters/out/jpa/JpaElementVersionRepositoryAdapterIT.java` (новый файл; стиль — как `JpaUserRepositoryAdapterIT`: `@SpringBootTest @Transactional`, откат транзакции изолирует тест):

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
class JpaElementVersionRepositoryAdapterIT {

    @Autowired UserRepositoryPort users;
    @Autowired ElementRepositoryPort elements;
    @Autowired ElementVersionRepositoryPort versions;

    @Test
    void findAllS3KeysReturnsSavedKeys() {
        User author = users.save(User.builder()
            .ssoSubject("s3keys-sub").email("s3keys@b.c").username("s3keys")
            .displayName("S3 Keys").admin(false).createdAt(Instant.now()).build());
        Element element = elements.save(Element.builder()
            .slug("s3keys-element").type(ElementType.SKILL).name("S3 Keys Element")
            .description("").tags(new String[0]).visibility(Visibility.PUBLIC)
            .author(author).createdAt(Instant.now()).updatedAt(Instant.now()).build());
        versions.save(ElementVersion.builder()
            .element(element).version("1.0.0").status(VersionStatus.PUBLISHED)
            .changelog("").s3_key("team/s3keys-element/1.0.0.zip").sizeBytes(10)
            .fileIndex("{}").publishedBy(author)
            .createdAt(Instant.now()).publishedAt(Instant.now()).build());

        assertThat(versions.findAllS3Keys()).contains("team/s3keys-element/1.0.0.zip");
    }
}
```

- [ ] **Step 2: Запустить тест, убедиться что падает**

Run: `mvn verify -Dit.test=JpaElementVersionRepositoryAdapterIT`
Expected: FAIL — компиляция: метода `findAllS3Keys()` нет у `ElementVersionRepositoryPort`. Ожидаемый RED.

- [ ] **Step 3: Реализовать**

В `src/main/java/com/skillhub/domain/port/ElementVersionRepositoryPort.java`:

```java
import java.util.Set;

public interface ElementVersionRepositoryPort {
    ElementVersion save(ElementVersion version);
    Optional<ElementVersion> findByElementIdAndVersion(UUID elementId, String version);
    Optional<ElementVersion> findLatestPublished(UUID elementId);
    List<ElementVersion> findAllByElementIdOrderByCreatedAtDesc(UUID elementId);
    Set<String> findAllS3Keys();
}
```

В `src/main/java/com/skillhub/adapters/out/jpa/repository/JpaElementVersionRepository.java` добавить импорт и метод:

```java
import org.springframework.data.jpa.repository.Query;
```

```java
@Query("select v.s3Key from JpaElementVersion v")
List<String> findAllS3Keys();
```

В `src/main/java/com/skillhub/adapters/out/jpa/JpaElementVersionRepositoryAdapter.java` добавить импорт и метод:

```java
import java.util.HashSet;
import java.util.Set;
```

```java
@Override
@Transactional(readOnly = true)
public Set<String> findAllS3Keys() {
    return new HashSet<>(jpa.findAllS3Keys());
}
```

- [ ] **Step 4: Запустить тест, убедиться что проходит**

Run: `mvn verify -Dit.test=JpaElementVersionRepositoryAdapterIT`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skillhub/domain/port/ElementVersionRepositoryPort.java src/main/java/com/skillhub/adapters/out/jpa/repository/JpaElementVersionRepository.java src/main/java/com/skillhub/adapters/out/jpa/JpaElementVersionRepositoryAdapter.java src/test/java/com/skillhub/adapters/out/jpa/JpaElementVersionRepositoryAdapterIT.java
git commit -m "feat: fetch all version s3 keys for orphan reconciliation"
```

---

### Task 3: `StorageReconciliationService` + шедулинг + конфиг

**Files:**
- Create: `src/main/java/com/skillhub/application/service/StorageReconciliationService.java`
- Create: `src/main/java/com/skillhub/config/SchedulingConfig.java`
- Modify: `src/main/resources/application.yml` (блок `skillhub.storage`)
- Test: `src/test/java/com/skillhub/application/service/StorageReconciliationServiceTest.java` (новый)

**Interfaces:**
- Consumes из Task 1: `StoragePort.list()` → `List<StorageObjectInfo>`; из Task 2: `ElementVersionRepositoryPort.findAllS3Keys()` → `Set<String>`; `ClockPort.now()` → `Instant`.
- Produces: бин `StorageReconciliationService` с публичным методом `reconcile()`; CLI-запуск вручную не предусмотрен.

- [ ] **Step 1: Написать падающие unit-тесты**

`src/test/java/com/skillhub/application/service/StorageReconciliationServiceTest.java` (новый файл; стиль `VersionUseCaseTest` — Mockito):

```java
package com.skillhub.application.service;

import com.skillhub.domain.model.StorageObjectInfo;
import com.skillhub.domain.port.ClockPort;
import com.skillhub.domain.port.ElementVersionRepositoryPort;
import com.skillhub.domain.port.StoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;

class StorageReconciliationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");

    private final StoragePort storage = mock(StoragePort.class);
    private final ElementVersionRepositoryPort versions = mock(ElementVersionRepositoryPort.class);
    private final ClockPort clock = mock(ClockPort.class);
    private final StorageReconciliationService service =
        new StorageReconciliationService(storage, versions, clock, Duration.ofHours(24));

    @BeforeEach
    void setUp() {
        when(clock.now()).thenReturn(NOW);
    }

    @Test
    void deletesOrphansOlderThanGrace() {
        when(versions.findAllS3Keys()).thenReturn(Set.of("team/a/1.0.0.zip"));
        when(storage.list()).thenReturn(List.of(
            new StorageObjectInfo("team/a/1.0.0.zip", NOW.minus(Duration.ofHours(48))),
            new StorageObjectInfo("team/orphan/2.0.0.zip", NOW.minus(Duration.ofHours(25)))
        ));

        service.reconcile();

        verify(storage).delete("team/orphan/2.0.0.zip");
        verify(storage, never()).delete("team/a/1.0.0.zip");
    }

    @Test
    void keepsOrphansWithinGrace() {
        when(versions.findAllS3Keys()).thenReturn(Set.of());
        when(storage.list()).thenReturn(List.of(
            new StorageObjectInfo("team/fresh/1.0.0.zip", NOW.minus(Duration.ofHours(1)))
        ));

        service.reconcile();

        verify(storage, never()).delete(anyString());
    }

    @Test
    void keepsKnownKeysRegardlessOfAge() {
        when(versions.findAllS3Keys()).thenReturn(Set.of("team/known/1.0.0.zip"));
        when(storage.list()).thenReturn(List.of(
            new StorageObjectInfo("team/known/1.0.0.zip", NOW.minus(Duration.ofHours(72)))
        ));

        service.reconcile();

        verify(storage, never()).delete(anyString());
    }

    @Test
    void continuesAfterFailedDelete() {
        when(versions.findAllS3Keys()).thenReturn(Set.of());
        when(storage.list()).thenReturn(List.of(
            new StorageObjectInfo("team/broken/1.0.0.zip", NOW.minus(Duration.ofHours(48))),
            new StorageObjectInfo("team/other/1.0.0.zip", NOW.minus(Duration.ofHours(48)))
        ));
        doThrow(new RuntimeException("s3 down")).when(storage).delete("team/broken/1.0.0.zip");

        service.reconcile();

        verify(storage).delete("team/other/1.0.0.zip");
    }

    @Test
    void emptyBucketIsNoop() {
        when(versions.findAllS3Keys()).thenReturn(Set.of());
        when(storage.list()).thenReturn(List.of());

        service.reconcile();

        verify(storage, never()).delete(anyString());
    }

    @Test
    void listFailureDoesNotPropagate() {
        when(storage.list()).thenThrow(new RuntimeException("s3 down"));

        service.reconcile();

        verify(storage, never()).delete(anyString());
    }
}
```

- [ ] **Step 2: Запустить тесты, убедиться что падают**

Run: `mvn test -Dtest=StorageReconciliationServiceTest`
Expected: FAIL — компиляция: класса `StorageReconciliationService` нет. Ожидаемый RED.

- [ ] **Step 3: Реализовать сервис и конфиг**

`src/main/java/com/skillhub/application/service/StorageReconciliationService.java` (новый файл):

```java
package com.skillhub.application.service;

import com.skillhub.domain.model.StorageObjectInfo;
import com.skillhub.domain.port.ClockPort;
import com.skillhub.domain.port.ElementVersionRepositoryPort;
import com.skillhub.domain.port.StoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Service
@ConditionalOnProperty(name = "skillhub.storage.reconcile-enabled",
    havingValue = "true", matchIfMissing = true)
public class StorageReconciliationService {

    private static final Logger log =
        LoggerFactory.getLogger(StorageReconciliationService.class);

    private final StoragePort storage;
    private final ElementVersionRepositoryPort versions;
    private final ClockPort clock;
    private final Duration grace;

    public StorageReconciliationService(StoragePort storage,
                                        ElementVersionRepositoryPort versions,
                                        ClockPort clock,
                                        @Value("${skillhub.storage.reconcile-grace:24h}")
                                        Duration grace) {
        this.storage = storage;
        this.versions = versions;
        this.clock = clock;
        this.grace = grace;
    }

    @Scheduled(cron = "${skillhub.storage.reconcile-cron:0 0 * * * *}")
    public void reconcile() {
        try {
            Set<String> known = versions.findAllS3Keys();
            Instant threshold = clock.now().minus(grace);
            for (StorageObjectInfo object : storage.list()) {
                if (known.contains(object.key()) || object.lastModified().isAfter(threshold)) {
                    continue;
                }
                try {
                    storage.delete(object.key());
                    log.info("Deleted orphaned storage object: {}", object.key());
                } catch (RuntimeException e) {
                    log.warn("Failed to delete orphaned storage object: {} ({})",
                        object.key(), e.getMessage());
                }
            }
        } catch (RuntimeException e) {
            log.error("S3 orphan reconciliation failed: {}", e.getMessage());
        }
    }
}
```

`src/main/java/com/skillhub/config/SchedulingConfig.java` (новый файл):

```java
package com.skillhub.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
}
```

В `src/main/resources/application.yml` в блок `skillhub.storage` (после строки `presign-ttl: 10m`) добавить:

```yaml
    reconcile-enabled: ${S3_RECONCILE_ENABLED:true}
    reconcile-cron: "0 0 * * * *"
    reconcile-grace: ${S3_RECONCILE_GRACE:24h}
```

- [ ] **Step 4: Запустить тесты, убедиться что проходят**

Run: `mvn test -Dtest=StorageReconciliationServiceTest`
Expected: PASS — все 6 тестов.

- [ ] **Step 5: Полный прогон**

Run: `mvn verify`
Expected: BUILD SUCCESS — все unit-тесты и IT (нужен запущенный Docker для Testcontainers).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/skillhub/application/service/StorageReconciliationService.java src/main/java/com/skillhub/config/SchedulingConfig.java src/main/resources/application.yml src/test/java/com/skillhub/application/service/StorageReconciliationServiceTest.java
git commit -m "feat: hourly S3 orphan reconciliation job"
```
