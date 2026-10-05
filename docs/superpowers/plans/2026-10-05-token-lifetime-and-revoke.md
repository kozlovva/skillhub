# Срок жизни и отзыв API-токенов — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Добавить токенам выбираемый при создании срок жизни (7/30/90 дней или бессрочно) и мягкий отзыв (revoked_at + кнопка в UI).

**Architecture:** Миграция добавляет колонку `revoked_at`; `expires_at` уже есть в схеме, но никогда не заполнялся. Сервис принимает `lifetimeDays` при создании, проверяет `revokedAt == null` при аутентификации и добавляет `revokeToken(user, tokenId)` с проверкой владельца. Контроллер отдаёт `id`/`expiresAt`/`revokedAt` в списке и получает `DELETE /api/tokens/{id}`. UI добавляет селект срока жизни, колонки таблицы и confirm-диалог отзыва.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Spring Data JPA, Flyway, PostgreSQL 16 (Testcontainers в тестах), React 18 + TypeScript + MUI + TanStack Query.

**Спецификация:** `docs/superpowers/specs/2026-10-05-token-lifetime-and-revoke-design.md`

## Global Constraints

- Существующий стиль кода: гексагональная архитектура (`domain/model`, `domain/port`, `adapters/in|out`, `application/service`); не ломать слои.
- Сырой токен нигде не хранится — только SHA-256 хеш.
- Ревок мягкий: колонка `revoked_at`, токен остаётся в списке.
- Отзыв чужого токена неотличим от несуществующего: HTTP 404 в обоих случаях.
- Ревок идемпотентен: повторный вызов не перезаписывает `revoked_at`.
- UI-тексты на русском, стиль существующей страницы `TokensPage.tsx` (MUI, `size="small"` таблицы).
- Тесты: `mvn test` в корне (Testcontainers PostgreSQL, Flyway, `ddl-auto: validate`); UI: `npm run build` и `npm run test` в `ui/`.
- Не коммитить ничего, кроме файлов задачи.

---

### Task 1: Миграция V6 — колонка `revoked_at`

**Files:**
- Create: `src/main/resources/db/migration/V6__add_api_tokens_revoked_at.sql`

**Interfaces:**
- Consumes: схема `api_tokens` из `V1__init.sql`.
- Produces: колонка `api_tokens.revoked_at TIMESTAMPTZ NULL`, на которую полагаются Task 2 и Task 3.

- [ ] **Step 1: Создать файл миграции**

```sql
ALTER TABLE api_tokens ADD COLUMN revoked_at TIMESTAMPTZ NULL;
```

- [ ] **Step 2: Запустить тесты для проверки миграции**

Run: `mvn test`
Expected: BUILD SUCCESS. Тесты поднимают PostgreSQL в Testcontainers, применяют Flyway и валидируют схему (`ddl-auto: validate`); новая колонка nullable, поэтому существующие проверки не ломаются.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V6__add_api_tokens_revoked_at.sql
git commit -m "feat: add revoked_at column to api_tokens"
```

---

### Task 2: Домен, порт и JPA-адаптер — поле `revokedAt` и поиск по id+владельцу

**Files:**
- Modify: `src/main/java/com/skillhub/domain/model/ApiToken.java`
- Modify: `src/main/java/com/skillhub/domain/port/ApiTokenRepositoryPort.java`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/entity/JpaApiToken.java`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/repository/JpaApiTokenRepository.java`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/JpaApiTokenRepositoryAdapter.java`

**Interfaces:**
- Consumes: колонка `revoked_at` из Task 1.
- Produces (используются Task 3):
  - `ApiToken.getRevokedAt() : java.time.Instant` / `setRevokedAt(Instant)` (Lombok).
  - `ApiTokenRepositoryPort.findByIdAndUserId(UUID tokenId, UUID userId) : Optional<ApiToken>`.

- [ ] **Step 1: Добавить поле в доменную модель**

В `src/main/java/com/skillhub/domain/model/ApiToken.java` добавить поле последним в классе:

```java
    private Instant revokedAt;
```

Файл после правки:

```java
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
    private Instant revokedAt;
}
```

- [ ] **Step 2: Добавить метод в порт**

В `src/main/java/com/skillhub/domain/port/ApiTokenRepositoryPort.java` добавить в интерфейс:

```java
    Optional<ApiToken> findByIdAndUserId(UUID tokenId, UUID userId);
```

- [ ] **Step 3: Добавить поле в JPA-сущность**

В `src/main/java/com/skillhub/adapters/out/jpa/entity/JpaApiToken.java` добавить после `expiresAt`:

```java
    @Column(name = "revoked_at") private Instant revokedAt;
```

- [ ] **Step 4: Добавить метод в Spring Data репозиторий**

В `src/main/java/com/skillhub/adapters/out/jpa/repository/JpaApiTokenRepository.java` добавить:

```java
    Optional<JpaApiToken> findByIdAndUserId(UUID id, UUID userId);
```

- [ ] **Step 5: Обновить адаптер — выделить общий маппер, добавить revokedAt и findByIdAndUserId**

Полная замена содержимого `src/main/java/com/skillhub/adapters/out/jpa/JpaApiTokenRepositoryAdapter.java` (заодно устраняется дублирование маппинга в трёх местах):

```java
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
            .revokedAt(token.getRevokedAt())
            .build());
        return toDomain(saved);
    }

    @Override
    public Optional<ApiToken> findByTokenHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApiToken> findByUserId(UUID userId) {
        return jpa.findAllByUserId(userId).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ApiToken> findByIdAndUserId(UUID tokenId, UUID userId) {
        return jpa.findByIdAndUserId(tokenId, userId).map(this::toDomain);
    }

    private ApiToken toDomain(JpaApiToken t) {
        return ApiToken.builder()
            .id(t.getId())
            .user(UserJpaMapper.toDomain(t.getUser()))
            .name(t.getName())
            .tokenHash(t.getTokenHash())
            .createdAt(t.getCreatedAt())
            .lastUsedAt(t.getLastUsedAt())
            .expiresAt(t.getExpiresAt())
            .revokedAt(t.getRevokedAt())
            .build();
    }
}
```

- [ ] **Step 6: Запустить тесты**

Run: `mvn test`
Expected: BUILD SUCCESS — схема `revoked_at` совпадает с миграцией из Task 1 (`ddl-auto: validate`).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/skillhub/domain/model/ApiToken.java src/main/java/com/skillhub/domain/port/ApiTokenRepositoryPort.java src/main/java/com/skillhub/adapters/out/jpa/entity/JpaApiToken.java src/main/java/com/skillhub/adapters/out/jpa/repository/JpaApiTokenRepository.java src/main/java/com/skillhub/adapters/out/jpa/JpaApiTokenRepositoryAdapter.java
git commit -m "feat: revokedAt field and findByIdAndUserId in token persistence layer"
```

---

### Task 3: Сервис — срок жизни при создании, проверка ревока, revokeToken

**Files:**
- Modify: `src/main/java/com/skillhub/application/service/ApiTokenService.java`
- Test: `src/test/java/com/skillhub/application/service/ApiTokenServiceTest.java`

**Interfaces:**
- Consumes: `ApiToken.revokedAt` и `ApiTokenRepositoryPort.findByIdAndUserId(UUID, UUID)` из Task 2.
- Produces (используются Task 4):
  - `ApiTokenService.createToken(User user, String name, Integer lifetimeDays) : CreatedToken` — `lifetimeDays == null` означает бессрочно; иначе `expiresAt = now() + lifetimeDays` дней.
  - `ApiTokenService.revokeToken(User user, UUID tokenId) : void` — бросает `NoSuchElementException`, если токен не найден или чужой; идемпотентен.

- [ ] **Step 1: Написать падающие тесты**

В `src/test/java/com/skillhub/application/service/ApiTokenServiceTest.java` добавить импорты и тесты. Новые импорты:

```java
import org.mockito.ArgumentCaptor;
import java.time.temporal.ChronoUnit;
```

Новые тесты в конец класса (существующие тесты не менять; обратите внимание: `service.createToken(user, "cli")` в старых тестах нужно заменить на `service.createToken(user, "cli", null)`):

```java
    @Test
    void createTokenSetsExpiryFromLifetimeDays() {
        service.createToken(user, "cli", 7);
        ArgumentCaptor<ApiToken> captor = ArgumentCaptor.forClass(ApiToken.class);
        org.mockito.Mockito.verify(tokens).save(captor.capture());
        assertThat(captor.getValue().getExpiresAt())
            .isEqualTo(Instant.parse("2026-01-08T00:00:00Z"));
    }

    @Test
    void createTokenWithoutLifetimeIsNeverExpiring() {
        service.createToken(user, "cli", null);
        ArgumentCaptor<ApiToken> captor = ArgumentCaptor.forClass(ApiToken.class);
        org.mockito.Mockito.verify(tokens).save(captor.capture());
        assertThat(captor.getValue().getExpiresAt()).isNull();
    }

    @Test
    void createTokenLifetime30And90Days() {
        service.createToken(user, "a", 30);
        service.createToken(user, "b", 90);
        ArgumentCaptor<ApiToken> captor = ArgumentCaptor.forClass(ApiToken.class);
        org.mockito.Mockito.verify(tokens, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getExpiresAt())
            .isEqualTo(Instant.parse("2026-01-31T00:00:00Z"));
        assertThat(captor.getAllValues().get(1).getExpiresAt())
            .isEqualTo(Instant.parse("2026-04-01T00:00:00Z"));
    }

    @Test
    void revokedTokenIsRejected() {
        ApiTokenService.CreatedToken created = service.createToken(user, "cli", null);
        created.token().setRevokedAt(Instant.parse("2025-12-01T00:00:00Z"));
        when(tokens.findByTokenHash(created.token().getTokenHash()))
            .thenReturn(Optional.of(created.token()));
        assertThat(service.authenticate(created.rawToken())).isEmpty();
    }

    @Test
    void revokeTokenSetsRevokedAt() {
        ApiTokenService.CreatedToken created = service.createToken(user, "cli", null);
        when(tokens.findByIdAndUserId(created.token().getId(), user.getId()))
            .thenReturn(Optional.of(created.token()));
        service.revokeToken(user, created.token().getId());
        ArgumentCaptor<ApiToken> captor = ArgumentCaptor.forClass(ApiToken.class);
        org.mockito.Mockito.verify(tokens).save(captor.capture());
        assertThat(captor.getValue().getRevokedAt())
            .isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void revokeTokenIsIdempotent() {
        ApiTokenService.CreatedToken created = service.createToken(user, "cli", null);
        created.token().setRevokedAt(Instant.parse("2025-06-01T00:00:00Z"));
        when(tokens.findByIdAndUserId(created.token().getId(), user.getId()))
            .thenReturn(Optional.of(created.token()));
        service.revokeToken(user, created.token().getId());
        org.mockito.Mockito.verify(tokens, org.mockito.Mockito.never()).save(any());
        assertThat(created.token().getRevokedAt())
            .isEqualTo(Instant.parse("2025-06-01T00:00:00Z"));
    }

    @Test
    void revokeForeignTokenThrows() {
        when(tokens.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());
        org.junit.jupiter.api.Assertions.assertThrows(
            java.util.NoSuchElementException.class,
            () -> service.revokeToken(user, UUID.randomUUID()));
    }
```

Обновить все вызовы `createToken` в существующих тестах на трёхаргументную форму:

```java
        ApiTokenService.CreatedToken created = service.createToken(user, "cli", null);
```

(в тестах `rawTokenHasPrefixAndHashDiffers`, `authenticateResolvesUserByHash`, `expiredTokenIsRejected`).

- [ ] **Step 2: Запустить тесты и убедиться, что они падают**

Run: `mvn test -Dtest=ApiTokenServiceTest`
Expected: COMPILATION ERROR — `createToken(User, String)` не существует и `revokeToken` не определён.

- [ ] **Step 3: Реализовать изменения в сервисе**

В `src/main/java/com/skillhub/application/service/ApiTokenService.java`:

1. Добавить импорт:

```java
import java.time.temporal.ChronoUnit;
```

2. Заменить `createToken`:

```java
    @Transactional
    public CreatedToken createToken(User user, String name, Integer lifetimeDays) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ApiToken saved = tokens.save(ApiToken.builder()
            .user(user)
            .name(name)
            .tokenHash(hash(raw))
            .createdAt(clock.now())
            .expiresAt(lifetimeDays == null ? null : clock.now().plus(lifetimeDays, ChronoUnit.DAYS))
            .build());
        return new CreatedToken(saved, raw);
    }
```

3. В `authenticate` добавить фильтр ревока — заменить `.filter(t -> t.getExpiresAt() == null || t.getExpiresAt().isAfter(clock.now()))` на:

```java
            .filter(t -> t.getRevokedAt() == null)
            .filter(t -> t.getExpiresAt() == null || t.getExpiresAt().isAfter(clock.now()))
```

4. Добавить метод `revokeToken` после `authenticate`:

```java
    @Transactional
    public void revokeToken(User user, UUID tokenId) {
        ApiToken token = tokens.findByIdAndUserId(tokenId, user.getId())
            .orElseThrow(() -> new NoSuchElementException("Token not found"));
        if (token.getRevokedAt() == null) {
            token.setRevokedAt(clock.now());
            tokens.save(token);
        }
    }
```

5. Добавить импорт:

```java
import java.util.NoSuchElementException;
import java.util.UUID;
```

- [ ] **Step 4: Запустить тесты и убедиться, что они проходят**

Run: `mvn test -Dtest=ApiTokenServiceTest`
Expected: PASS, все тесты зелёные.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skillhub/application/service/ApiTokenService.java src/test/java/com/skillhub/application/service/ApiTokenServiceTest.java
git commit -m "feat: token lifetime at creation, revoke check and revokeToken"
```

---

### Task 4: REST — lifetimeDays в создании, id/expiry в списке, DELETE-эндпоинт

**Files:**
- Modify: `src/main/java/com/skillhub/adapters/in/rest/TokenController.java`

**Interfaces:**
- Consumes: `tokenService.createToken(User, String, Integer)` и `tokenService.revokeToken(User, UUID)` из Task 3; `ApiToken.getId()/getExpiresAt()/getRevokedAt()` из Task 2.
- Produces (используются Task 5):
  - `POST /api/tokens` — тело `{ "name": string, "lifetimeDays": number | null }`, ответ `201 { "token", "name" }`.
  - `GET /api/tokens` — элементы `TokenItem(UUID id, String name, String createdAt, String lastUsedAt, String expiresAt, String revokedAt)`.
  - `DELETE /api/tokens/{id}` — 204; 404 если не найден/чужой.

- [ ] **Step 1: Обновить контроллер**

Полная замена содержимого `src/main/java/com/skillhub/adapters/in/rest/TokenController.java`:

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
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/tokens")
@RequiredArgsConstructor
public class TokenController {

    public record CreateTokenRequest(String name, Integer lifetimeDays) {}
    public record TokenItem(UUID id, String name, String createdAt,
                            String lastUsedAt, String expiresAt, String revokedAt) {}

    private final ApiTokenService tokenService;
    private final ApiTokenRepositoryPort tokens;
    private final CurrentUserResolver currentUser;

    @PostMapping
    public ResponseEntity<Map<String, String>> create(@RequestBody CreateTokenRequest req,
                                                      Authentication auth) {
        User user = currentUser.resolve(auth);
        ApiTokenService.CreatedToken created = tokenService.createToken(user, req.name(), req.lifetimeDays());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "token", created.rawToken(),
            "name", created.token().getName()));
    }

    @GetMapping
    public List<TokenItem> list(Authentication auth) {
        User user = currentUser.resolve(auth);
        return tokens.findByUserId(user.getId()).stream()
            .map(t -> new TokenItem(t.getId(), t.getName(), t.getCreatedAt().toString(),
                t.getLastUsedAt() == null ? null : t.getLastUsedAt().toString(),
                t.getExpiresAt() == null ? null : t.getExpiresAt().toString(),
                t.getRevokedAt() == null ? null : t.getRevokedAt().toString()))
            .toList();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable UUID id, Authentication auth) {
        User user = currentUser.resolve(auth);
        try {
            tokenService.revokeToken(user, id);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Token not found");
        }
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 2: Запустить все тесты**

Run: `mvn test`
Expected: BUILD SUCCESS. Ошибку 404 обрабатывает существующий `GlobalExceptionHandler.handleResponseStatus`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/skillhub/adapters/in/rest/TokenController.java
git commit -m "feat: token lifetime param, id/expiry in list, DELETE /api/tokens/{id}"
```

---

### Task 5: UI — селект срока жизни, колонки таблицы, кнопка отзыва

**Files:**
- Modify: `ui/src/pages/TokensPage.tsx`

**Interfaces:**
- Consumes: `GET /api/tokens` → `TokenItem` с полями `id, name, createdAt, lastUsedAt, expiresAt, revokedAt`; `POST /api/tokens` с `lifetimeDays`; `DELETE /api/tokens/{id}` — из Task 4.
- Produces: готовая страница «API-токены» (никто не потребляет дальше).

- [ ] **Step 1: Обновить TokensPage.tsx**

Полная замена содержимого `ui/src/pages/TokensPage.tsx`:

```tsx
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Typography, Paper, Table, TableHead, TableRow, TableCell, TableBody,
  Button, TextField, Dialog, DialogTitle, DialogContent, DialogActions, Stack, Box,
  MenuItem, Select, FormControl, InputLabel,
} from '@mui/material';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import { api, toApiError } from '../api/client';
import { useSnackbar } from '../layout/SnackbarContext';
import PageHeader from '../components/PageHeader';

interface TokenItem {
  id: string;
  name: string;
  createdAt: string;
  lastUsedAt: string | null;
  expiresAt: string | null;
  revokedAt: string | null;
}

const LIFETIME_OPTIONS = [
  { value: 7, label: '7 дней' },
  { value: 30, label: '30 дней' },
  { value: 90, label: '90 дней' },
  { value: 0, label: 'Бессрочно' },
];

function formatExpiry(expiresAt: string | null): string {
  return expiresAt ? new Date(expiresAt).toLocaleString() : '—';
}

export default function TokensPage() {
  const qc = useQueryClient();
  const { showError, showSuccess } = useSnackbar();
  const [name, setName] = useState('');
  const [lifetimeDays, setLifetimeDays] = useState<number>(0);
  const [rawToken, setRawToken] = useState<string | null>(null);
  const [rawTokenExpiresAt, setRawTokenExpiresAt] = useState<string | null>(null);
  const [tokenToRevoke, setTokenToRevoke] = useState<TokenItem | null>(null);

  const { data: tokens } = useQuery({
    queryKey: ['tokens'],
    queryFn: async () => (await api.get<TokenItem[]>('/api/tokens')).data,
  });

  const createMutation = useMutation({
    mutationFn: async () => {
      const res = await api.post<{ token: string; name: string }>('/api/tokens', {
        name,
        lifetimeDays: lifetimeDays === 0 ? null : lifetimeDays,
      });
      return res.data;
    },
    onSuccess: (data) => {
      setRawToken(data.token);
      setRawTokenExpiresAt(
        lifetimeDays === 0
          ? null
          : new Date(Date.now() + lifetimeDays * 24 * 60 * 60 * 1000).toISOString(),
      );
      setName('');
      setLifetimeDays(0);
      showSuccess('Токен создан');
      qc.invalidateQueries({ queryKey: ['tokens'] });
    },
    onError: (e) => showError(toApiError(e).message),
  });

  const revokeMutation = useMutation({
    mutationFn: async (id: string) => {
      await api.delete(`/api/tokens/${id}`);
    },
    onSuccess: () => {
      setTokenToRevoke(null);
      showSuccess('Токен отозван');
      qc.invalidateQueries({ queryKey: ['tokens'] });
    },
    onError: (e) => showError(toApiError(e).message),
  });

  return (
    <Stack spacing={3}>
      <PageHeader
        title="API-токены"
        subtitle="Токены используются CLI и AI-агентами (заголовок Authorization: Bearer)."
      />
      <Paper sx={{ p: 3 }}>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Имя</TableCell>
            <TableCell>Создан</TableCell>
            <TableCell>Истекает</TableCell>
            <TableCell>Последнее использование</TableCell>
            <TableCell>Действия</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {(tokens ?? []).map((t) => {
            const expired =
              t.expiresAt !== null && new Date(t.expiresAt).getTime() < Date.now();
            return (
              <TableRow key={t.id}>
                <TableCell sx={{ fontWeight: 500 }}>{t.name}</TableCell>
                <TableCell>{new Date(t.createdAt).toLocaleString()}</TableCell>
                <TableCell sx={expired ? { color: 'text.disabled' } : undefined}>
                  {formatExpiry(t.expiresAt)}
                </TableCell>
                <TableCell>{t.lastUsedAt ? new Date(t.lastUsedAt).toLocaleString() : '—'}</TableCell>
                <TableCell>
                  {t.revokedAt ? (
                    <Typography variant="body2" color="text.disabled">
                      Отозван {new Date(t.revokedAt).toLocaleString()}
                    </Typography>
                  ) : (
                    <Button size="small" color="error" onClick={() => setTokenToRevoke(t)}>
                      Отозвать
                    </Button>
                  )}
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
      <Stack direction="row" spacing={1} sx={{ mt: 2 }} flexWrap="wrap" useFlexGap alignItems="center">
        <TextField
          label="Имя токена"
          value={name}
          onChange={(e) => setName(e.target.value)}
          sx={{ width: 220 }}
        />
        <FormControl sx={{ width: 160 }}>
          <InputLabel id="token-lifetime-label">Срок жизни</InputLabel>
          <Select
            labelId="token-lifetime-label"
            label="Срок жизни"
            value={lifetimeDays}
            onChange={(e) => setLifetimeDays(Number(e.target.value))}
          >
            {LIFETIME_OPTIONS.map((o) => (
              <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>
            ))}
          </Select>
        </FormControl>
        <Box sx={{ flexGrow: 1 }} />
        <Button
          variant="contained"
          onClick={() => createMutation.mutate()}
          disabled={!name.trim()}
        >
          Создать токен
        </Button>
      </Stack>

      <Dialog open={rawToken !== null} onClose={() => setRawToken(null)} fullWidth maxWidth="sm">
        <DialogTitle>Токен создан</DialogTitle>
        <DialogContent>
          <Typography variant="body2" sx={{ mb: 1 }}>
            Скопируйте токен сейчас — он больше не будет показан:
          </Typography>
          <Stack
            direction="row"
            spacing={1}
            alignItems="center"
            sx={{
              bgcolor: 'background.paper',
              border: '1px solid',
              borderColor: 'divider',
              borderRadius: 1,
              p: 1,
            }}
          >
            <Box
              component="code"
              sx={{
                fontFamily: '"JetBrains Mono", ui-monospace, monospace',
                fontSize: 13,
                lineHeight: '20px',
                px: 1,
                wordBreak: 'break-all',
                flexGrow: 1,
                userSelect: 'all',
              }}
            >
              {rawToken}
            </Box>
            <Button
              size="small"
              startIcon={<ContentCopyIcon />}
              onClick={() => {
                void navigator.clipboard?.writeText(rawToken ?? '');
                showSuccess('Скопировано');
              }}
            >
              Копировать
            </Button>
          </Stack>
          <Typography variant="body2" sx={{ mt: 2 }}>
            Срок действия: {rawTokenExpiresAt ? new Date(rawTokenExpiresAt).toLocaleString() : 'бессрочно'}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRawToken(null)}>Закрыть</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={tokenToRevoke !== null} onClose={() => setTokenToRevoke(null)} maxWidth="xs" fullWidth>
        <DialogTitle>Отозвать токен?</DialogTitle>
        <DialogContent>
          <Typography variant="body2">
            Токен «{tokenToRevoke?.name}» перестанет работать немедленно. Действие необратимо.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setTokenToRevoke(null)}>Отмена</Button>
          <Button
            color="error"
            variant="contained"
            disabled={revokeMutation.isPending}
            onClick={() => tokenToRevoke && revokeMutation.mutate(tokenToRevoke.id)}
          >
            Отозвать
          </Button>
        </DialogActions>
      </Dialog>
      </Paper>
    </Stack>
  );
}
```

- [ ] **Step 2: Проверить типы и сборку**

Run (в `ui/`): `npm run build`
Expected: сборка `tsc && vite build` завершается без ошибок типов.

- [ ] **Step 3: Запустить тесты UI**

Run (в `ui/`): `npm run test`
Expected: все существующие vitest-тесты проходят (страница токенов ранее не имела тестов; новые тесты для неё не требуются спеком).

- [ ] **Step 4: Commit**

```bash
git add ui/src/pages/TokensPage.tsx
git commit -m "feat: token lifetime selector and revoke button in tokens page"
```

---

### Task 5: Финальная верификация

**Files:**
- Нет новых файлов.

**Interfaces:**
- Consumes: всё выше.
- Produces: подтверждение работоспособности всей фичи.

- [ ] **Step 1: Полный прогон бэкенда**

Run: `mvn test`
Expected: BUILD SUCCESS.

- [ ] **Step 2: Ручная проверка локального стенда (опционально, если docker-compose запущен)**

1. Открыть `/tokens`, создать токен со сроком 7 дней — в модалке «Срок действия: <дата + 7 дней>», в таблице колонка «Истекает» заполнена.
2. Создать бессрочный токен — «Истекает» показывает «—».
3. Нажать «Отозвать», подтвердить — в строке появляется «Отозван <дата>», кнопка исчезает.
4. Запрос с отозванным токеном (CLI: `skillhub whoami`) возвращает 401.
