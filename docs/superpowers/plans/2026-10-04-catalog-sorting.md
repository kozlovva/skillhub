# Сортировка каталога (рейтинг, дата публикации) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Добавить в каталог выбор сортировки (популярность / рейтинг / дата публикации последней версии) с переключением направления, применяемой к уже отфильтрованному набору (тип/категория/поиск).

**Architecture:** Бэкенд — новые параметры `sort`/`order` в `GET /api/search`; `SearchAdapter` выбирает ORDER BY из фиксированного набора строк по enum'у (без интерполяции пользовательского ввода). Рейтинг — `LEFT JOIN LATERAL` по `ratings`; дата — подзапрос `MAX(published_at)` среди `PUBLISHED`-версий. Фронтенд — `ToggleButtonGroup` + кнопка направления в `CatalogPage`.

**Tech Stack:** Spring Boot 3 (нативный SQL через EntityManager, JUnit 5 + Testcontainers Postgres, failsafe для *IT), React 18 + MUI v6 + TanStack Query v5 + Vitest.

## Global Constraints

- Спека: `docs/superpowers/specs/2026-10-04-catalog-sorting-design.md`
- Неверное значение `sort`/`order` → HTTP 400.
- Элементы без рейтинга/без опубликованных версий — всегда в конце (`NULLS LAST`), независимо от направления.
- Сортировка по умолчанию — прежнее поведение (релевантность при непустом `q`, иначе скачивания).
- ORDER BY формируется только из enum-значений; строки пользовательского ввода в SQL не попадают.
- Детерминированный порядок страниц: вторичный ключ `e.id` во всех вариантах ORDER BY.
- Комментарии в коде не писать (правило репозитория).
- Бэкенд-тесты: `mvn verify "-Dit.test=<ClassName>"` (PowerShell — флаг в кавычках); UI-тесты: `npm test` в `ui/`.

---

### Task 1: Бэкенд — enum'ы сортировки, SearchQuery, SearchUseCase, SearchController, SearchAdapter

**Files:**
- Create: `src/main/java/com/skillhub/application/dto/SortBy.java`
- Create: `src/main/java/com/skillhub/application/dto/SortOrder.java`
- Modify: `src/main/java/com/skillhub/application/dto/SearchQuery.java`
- Modify: `src/main/java/com/skillhub/application/service/SearchUseCase.java`
- Modify: `src/main/java/com/skillhub/adapters/in/rest/SearchController.java`
- Modify: `src/main/java/com/skillhub/adapters/out/jpa/SearchAdapter.java`
- Test: `src/test/java/com/skillhub/adapters/in/rest/SearchApiIT.java` (create)
- Test: `src/test/java/com/skillhub/adapters/out/jpa/SearchAdapterIT.java` (modify)

**Interfaces:**
- Consumes: существующие `SearchPort.search(SearchQuery)`, `SearchQueryResult`, `CurrentUserResolver.resolve(auth)`.
- Produces:
  - `enum SortBy { RELEVANCE, DOWNLOADS, RATING, PUBLISHED }` со статикой `SortBy.fromString(String)` ("" и "relevance" → RELEVANCE; неизвестное → `IllegalArgumentException`).
  - `enum SortOrder { ASC, DESC }` со статикой `SortOrder.fromString(String)` ("" и "desc" → DESC; неизвестное → `IllegalArgumentException`) и `String sql()` → `"ASC"`/`"DESC"`.
  - `SearchQuery(String q, String type, String category, UUID userId, boolean admin, int limit, int offset, SortBy sortBy, SortOrder sortOrder)`.
  - `SearchUseCase.search(String q, String type, String category, SortBy sortBy, SortOrder sortOrder, User viewer, int limit, int offset)`.
  - REST: `GET /api/search?sort=relevance|downloads|rating|published&order=asc|desc` (defaults: `relevance`, `desc`).

- [ ] **Step 1: Создать enum'ы `SortBy` и `SortOrder`**

`src/main/java/com/skillhub/application/dto/SortBy.java`:

```java
package com.skillhub.application.dto;

public enum SortBy {
    RELEVANCE, DOWNLOADS, RATING, PUBLISHED;

    public static SortBy fromString(String value) {
        return switch (value == null ? "" : value.toLowerCase()) {
            case "", "relevance" -> RELEVANCE;
            case "downloads" -> DOWNLOADS;
            case "rating" -> RATING;
            case "published" -> PUBLISHED;
            default -> throw new IllegalArgumentException("Unknown sort: " + value);
        };
    }
}
```

`src/main/java/com/skillhub/application/dto/SortOrder.java`:

```java
package com.skillhub.application.dto;

public enum SortOrder {
    ASC, DESC;

    public static SortOrder fromString(String value) {
        return switch (value == null ? "" : value.toLowerCase()) {
            case "", "desc" -> DESC;
            case "asc" -> ASC;
            default -> throw new IllegalArgumentException("Unknown order: " + value);
        };
    }

    public String sql() {
        return this == ASC ? "ASC" : "DESC";
    }
}
```

- [ ] **Step 2: Обновить `SearchQuery`**

`src/main/java/com/skillhub/application/dto/SearchQuery.java` — заменить целиком:

```java
package com.skillhub.application.dto;

import java.util.UUID;

public record SearchQuery(String q, String type, String category, UUID userId,
                          boolean admin, int limit, int offset,
                          SortBy sortBy, SortOrder sortOrder) {}
```

- [ ] **Step 3: Обновить `SearchUseCase`**

`src/main/java/com/skillhub/application/service/SearchUseCase.java` — заменить метод `search`:

```java
    @Transactional(readOnly = true)
    public SearchQueryResult search(String q, String type, String category,
                                    SortBy sortBy, SortOrder sortOrder,
                                    User viewer, int limit, int offset) {
        UUID userId = viewer == null
            ? UUID.nameUUIDFromBytes("anonymous".getBytes())
            : viewer.getId();
        boolean admin = viewer != null && viewer.isAdmin();
        return searchPort.search(new SearchQuery(q, type, category, userId, admin, limit, offset,
            sortBy, sortOrder));
    }
```

(импорты `com.skillhub.application.dto.SortBy`, `SortOrder` добавить).

- [ ] **Step 4: Обновить `SearchController`**

`src/main/java/com/skillhub/adapters/in/rest/SearchController.java` — заменить метод `search` и добавить импорты (`com.skillhub.application.dto.SortBy`, `SortOrder`, `org.springframework.http.HttpStatus`, `org.springframework.web.server.ResponseStatusException`):

```java
    @GetMapping
    public SearchResultResponse search(@RequestParam(value = "q", defaultValue = "") String q,
                                       @RequestParam(value = "type", required = false) String type,
                                       @RequestParam(value = "category", required = false) String category,
                                       @RequestParam(value = "sort", defaultValue = "relevance") String sort,
                                       @RequestParam(value = "order", defaultValue = "desc") String order,
                                       @RequestParam(value = "limit", defaultValue = "20") int limit,
                                       @RequestParam(value = "offset", defaultValue = "0") int offset,
                                       Authentication auth) {
        SortBy sortBy;
        SortOrder sortOrder;
        try {
            sortBy = SortBy.fromString(sort);
            sortOrder = SortOrder.fromString(order);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        var result = searchUseCase.search(q, type, category, sortBy, sortOrder,
            currentUser.resolve(auth), limit, offset);
        List<ElementResponse> items = result.items().stream()
            .map(e -> ElementResponse.from(e, result.ratings().get(e.getId())))
            .toList();
        return new SearchResultResponse(items, result.total(), result.facetsByType());
    }
```

- [ ] **Step 5: Обновить `SearchAdapter` — варианты ORDER BY**

В `src/main/java/com/skillhub/adapters/out/jpa/SearchAdapter.java`:

Заменить блок с `"SELECT e.* FROM elements e WHERE " + WHERE + ...` и добавить helper-методы:

```java
    private static final String RATING_JOIN =
        " LEFT JOIN LATERAL (SELECT AVG(r.rating) AS avg_rating FROM ratings r " +
        "WHERE r.element_id = e.id) rt ON true ";

    private String orderBy(SortBy sortBy, SortOrder order) {
        String dir = order.sql();
        return switch (sortBy) {
            case RELEVANCE -> "ORDER BY CASE WHEN :q = '' THEN 0 " +
                "ELSE ts_rank(e.search_vector, plainto_tsquery('russian', :q)) END DESC, " +
                "e.downloads_count DESC, e.id";
            case DOWNLOADS -> "ORDER BY e.downloads_count " + dir + ", e.id";
            case RATING -> "ORDER BY rt.avg_rating " + dir + " NULLS LAST, e.id";
            case PUBLISHED -> "ORDER BY (SELECT MAX(v.published_at) FROM element_versions v " +
                "WHERE v.element_id = e.id AND v.status = 'PUBLISHED') " + dir + " NULLS LAST, e.id";
        };
    }
```

Метод `search` — заменить первый запрос (список items):

```java
        boolean joinRating = q.sortBy() == SortBy.RATING;
        List<JpaElement> items = em.createNativeQuery(
                "SELECT e.* FROM elements e" + (joinRating ? RATING_JOIN : "") +
                " WHERE " + WHERE + " " + orderBy(q.sortBy(), q.sortOrder()) +
                " LIMIT :limit OFFSET :offset", JpaElement.class)
            .setParameter("userId", q.userId())
            .setParameter("admin", q.admin())
            .setParameter("q", query)
            .setParameter("type", q.type())
            .setParameter("category", q.category())
            .setParameter("limit", q.limit())
            .setParameter("offset", q.offset())
            .getResultList();
```

Запросы `total` и `facets` не меняются. Импорты: `com.skillhub.application.dto.SortBy`, `SortOrder` добавить.

- [ ] **Step 6: Исправить существующие вызовы `SearchQuery` в `SearchAdapterIT`**

В `src/test/java/com/skillhub/adapters/out/jpa/SearchAdapterIT.java` добавить импорт `com.skillhub.application.dto.SortBy`/`SortOrder` и во всех четырёх вызовах `new SearchQuery(...)` добавить последние два аргумента `SortBy.RELEVANCE, SortOrder.DESC`:

```java
        SearchQueryResult publicOnly = searchAdapter.search(new SearchQuery(
            "PDF", null, null, outsider, false, 20, 0, SortBy.RELEVANCE, SortOrder.DESC));
```

(аналогично `memberView`, `byType`, и вызовы в тестах `adminSeesTeamElementsFromOtherTeams`, `russianStemmingMatchesInflectedForms`, `changelogIsSearchable`).

- [ ] **Step 7: Компиляция**

Run: `mvn -q compile test-compile`
Expected: BUILD SUCCESS

- [ ] **Step 8: Написать падающие IT-тесты сортировки**

В конец `SearchAdapterIT.java` добавить (внутри класса; хелперы — приватные методы):

```java
    private User newAuthor(String subject) {
        return users.save(User.builder()
            .ssoSubject(subject).email(subject + "@b.c").displayName(subject)
            .admin(false).createdAt(Instant.now()).build());
    }

    private Team newTeam(String slug) {
        return teams.save(Team.builder()
            .slug(slug).name(slug).createdAt(Instant.now()).build());
    }

    private Element newElement(String slug, String name, ElementType type, Team team, User author) {
        return elements.save(Element.builder()
            .slug(slug).type(type).name(name)
            .description("Описание " + slug).team(team)
            .tags(new String[]{}).visibility(Visibility.PUBLIC)
            .author(author).downloadsCount(0)
            .createdAt(Instant.now()).updatedAt(Instant.now()).build());
    }

    @Test
    void sortByRatingPutsUnratedLastAndRespectsType() {
        User author = newAuthor("sort-r-author");
        Team team = newTeam("sort-r-team");

        Element top = newElement("sort-top", "Топ элемент", ElementType.SKILL, team, author);
        Element mid = newElement("sort-mid", "Средний элемент", ElementType.SKILL, team, author);
        Element unrated = newElement("sort-unrated", "Без оценок", ElementType.SKILL, team, author);
        Element script = newElement("sort-script", "Скрипт элемент", ElementType.SCRIPT, team, author);

        jdbc.update("INSERT INTO ratings (element_id, user_id, rating) VALUES (?, ?, 5)",
            top.getId(), author.getId());
        jdbc.update("INSERT INTO ratings (element_id, user_id, rating) VALUES (?, ?, 3)",
            mid.getId(), author.getId());

        SearchQueryResult desc = searchAdapter.search(new SearchQuery(
            "", "SKILL", null, author.getId(), false, 20, 0, SortBy.RATING, SortOrder.DESC));
        assertThat(desc.items()).extracting(Element::getSlug)
            .containsExactly("sort-top", "sort-mid", "sort-unrated");

        SearchQueryResult asc = searchAdapter.search(new SearchQuery(
            "", "SKILL", null, author.getId(), false, 20, 0, SortBy.RATING, SortOrder.ASC));
        assertThat(asc.items()).extracting(Element::getSlug)
            .containsExactly("sort-mid", "sort-top", "sort-unrated");

        SearchQueryResult onlyScripts = searchAdapter.search(new SearchQuery(
            "", "SCRIPT", null, author.getId(), false, 20, 0, SortBy.RATING, SortOrder.DESC));
        assertThat(onlyScripts.items()).extracting(Element::getSlug)
            .containsExactly("sort-script");
    }

    @Test
    void sortByPublishedUsesLatestPublishedVersionOnly() throws Exception {
        User author = newAuthor("sort-p-author");
        Team team = newTeam("sort-p-team");

        Element older = newElement("sort-p-older", "Старее", ElementType.SKILL, team, author);
        Element newer = newElement("sort-p-newer", "Новее", ElementType.SKILL, team, author);
        Element neverPublished = newElement("sort-p-none", "Не публиковался", ElementType.SKILL, team, author);

        jdbc.update("""
            INSERT INTO element_versions (element_id, version, status, s3_key, size_bytes, file_index, published_by, published_at)
            VALUES (?, '1.0.0', 'PUBLISHED', 'k', 1, '{}'::jsonb, ?, now() - interval '2 days')
            """, older.getId(), author.getId());
        jdbc.update("""
            INSERT INTO element_versions (element_id, version, status, s3_key, size_bytes, file_index, published_by, published_at)
            VALUES (?, '1.0.0', 'PUBLISHED', 'k', 1, '{}'::jsonb, ?, now() - interval '1 day')
            """, newer.getId(), author.getId());
        jdbc.update("""
            INSERT INTO element_versions (element_id, version, status, s3_key, size_bytes, file_index, published_by, published_at)
            VALUES (?, '2.0.0', 'DRAFT', 'k', 1, '{}'::jsonb, ?, now())
            """, older.getId(), author.getId());

        SearchQueryResult desc = searchAdapter.search(new SearchQuery(
            "", "SKILL", null, author.getId(), false, 20, 0, SortBy.PUBLISHED, SortOrder.DESC));
        assertThat(desc.items()).extracting(Element::getSlug)
            .containsExactly("sort-p-newer", "sort-p-older", "sort-p-none");
    }
```

Импорты, которые уже есть (`ElementType`, `Visibility`, `Instant`, `Element`, `Team`, `User` — через `com.skillhub.domain.model.*`), плюс понадобятся `com.skillhub.application.dto.SortBy`, `SortOrder` (добавлены в Step 6).

- [ ] **Step 9: Создать падающий `SearchApiIT` для валидации параметров**

`src/test/java/com/skillhub/adapters/in/rest/SearchApiIT.java`:

```java
package com.skillhub.adapters.in.rest;

import com.skillhub.application.service.ApiTokenService;
import com.skillhub.application.service.UserSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SearchApiIT {

    @Autowired TestRestTemplate rest;
    @Autowired UserSyncService users;
    @Autowired ApiTokenService tokens;

    String authHeader;

    @BeforeEach
    void setUp() {
        var user = users.syncFromSso("search-api-user", "search-api@skillhub.io", "Search Api");
        authHeader = "Bearer " + tokens.createToken(user, "search-api").rawToken();
    }

    private ResponseEntity<String> get(String query) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authHeader);
        return rest.exchange("/api/search" + query, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    void invalidSortReturns400() {
        assertThat(get("?sort=bogus").getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void invalidOrderReturns400() {
        assertThat(get("?sort=rating&order=sideways").getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void validSortParamsAreAccepted() {
        ResponseEntity<String> response = get("?sort=rating&order=asc");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }
}
```

- [ ] **Step 10: Запустить тесты, убедиться что падают/зелёные**

Run: `mvn verify "-Dit.test=SearchAdapterIT+SearchApiIT" -q`
Expected: тесты сортировки PASS (реализация уже написана в Steps 1–5). Если FAIL — исправить реализацию, не ослабляя ассерты. Проверить, что старые тесты SearchAdapterIT не сломались.

- [ ] **Step 11: Запустить весь бэкенд**

Run: `mvn verify -q`
Expected: BUILD SUCCESS

- [ ] **Step 12: Commit**

```bash
git add src/main/java/com/skillhub/application/dto/SortBy.java src/main/java/com/skillhub/application/dto/SortOrder.java src/main/java/com/skillhub/application/dto/SearchQuery.java src/main/java/com/skillhub/application/service/SearchUseCase.java src/main/java/com/skillhub/adapters/in/rest/SearchController.java src/main/java/com/skillhub/adapters/out/jpa/SearchAdapter.java src/test/java/com/skillhub/adapters/out/jpa/SearchAdapterIT.java src/test/java/com/skillhub/adapters/in/rest/SearchApiIT.java
git commit -m "feat: add sort and order params to search API (rating, published date)"
```

---

### Task 2: Фронтенд — параметры запроса и контролы сортировки в CatalogPage

**Files:**
- Modify: `ui/src/api/search.ts`
- Modify: `ui/src/pages/CatalogPage.tsx`
- Test: `ui/src/pages/CatalogPage.test.tsx`

**Interfaces:**
- Consumes: REST `GET /api/search?sort=...&order=...` из Task 1 (400 при невалидных значениях).
- Produces: `search.search({ q, type, category, sort, order, limit?, offset? })`; в `CatalogPage` — состояние `sort: 'relevance' | 'rating' | 'published'` (по умолчанию `'relevance'`) и `order: 'asc' | 'desc'` (по умолчанию `'desc'`). При `sort === 'relevance'` параметры `sort`/`order` в запрос не передаются.

UI-решения (по ui-ux-pro-max): MUI `ToggleButtonGroup` с `exclusive`, `aria-label="Сортировка"` на группе; кнопка направления — `IconButton` с `aria-label`, отражающим текущее состояние («По убыванию»/«По возрастанию»), иконки MUI (`ArrowDownwardIcon`/`ArrowUpwardIcon`) с `aria-hidden="true"`; для «Популярность» направление скрыто (направление не имеет смысла). Стили — в духе существующей страницы: `variant="overline"` подпись, `size="small"`, границы `theme.palette.divider`.

- [ ] **Step 1: Написать падающие тесты**

В конец `ui/src/pages/CatalogPage.test.tsx` добавить:

```tsx
test('sorting control changes query params', async () => {
  const { search } = await import('../api/search');
  renderPage();
  await screen.findByText('PDF Skill');
  await userEvent.click(screen.getByRole('button', { name: 'Рейтинг' }));
  await waitFor(() => expect(search.search).toHaveBeenCalledWith(
    expect.objectContaining({ sort: 'rating', order: 'desc' })
  ));
  await userEvent.click(screen.getByRole('button', { name: 'По убыванию' }));
  await waitFor(() => expect(search.search).toHaveBeenCalledWith(
    expect.objectContaining({ sort: 'rating', order: 'asc' })
  ));
});

test('direction toggle hidden for popularity sort', async () => {
  renderPage();
  await screen.findByText('PDF Skill');
  expect(screen.queryByRole('button', { name: 'По убыванию' })).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: 'Дата' }));
  expect(screen.getByRole('button', { name: 'По убыванию' })).toBeInTheDocument();
});
```

- [ ] **Step 2: Запустить тесты, убедиться что падают**

Run (в `ui/`): `npm test`
Expected: новые тесты FAIL (кнопок «Рейтинг»/«Дата» нет).

- [ ] **Step 3: Обновить `ui/src/api/search.ts`**

Заменить целиком:

```ts
import { api } from './client';
import type { SearchResultResponse } from '../types';

export const search = {
  async search(params: {
    q?: string; type?: string; category?: string;
    sort?: string; order?: string; limit?: number; offset?: number;
  }): Promise<SearchResultResponse> {
    return (await api.get<SearchResultResponse>('/api/search', { params })).data;
  },
};
```

- [ ] **Step 4: Обновить `CatalogPage.tsx`**

Добавить в импорты из `@mui/material`: `ToggleButton`, `ToggleButtonGroup`. Добавить импорты иконок:

```tsx
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
```

Добавить тип и состояние рядом с остальными state:

```tsx
type SortOption = 'relevance' | 'rating' | 'published';

const [sort, setSort] = useState<SortOption>('relevance');
const [order, setOrder] = useState<'asc' | 'desc'>('desc');
```

Обновить useQuery поиска:

```tsx
  const { data, isPending } = useQuery({
    queryKey: ['search', debouncedQ, type, category, sort, order],
    queryFn: () => search.search({
      q: debouncedQ,
      type: type ?? undefined,
      category: category ?? undefined,
      sort: sort === 'relevance' ? undefined : sort,
      order: sort === 'relevance' ? undefined : order,
    }),
    placeholderData: keepPreviousData,
  });
```

В sticky-строке фильтров (после `<ChipGroup title="Категория" chips={categoryChips} />`) добавить:

```tsx
          <Divider orientation="vertical" flexItem sx={{ mx: 1, alignSelf: 'stretch', my: 0.5 }} />
          <Stack direction="row" spacing={1} alignItems="center">
            <Typography variant="overline" sx={{ color: 'text.secondary' }}>Сортировка</Typography>
            <ToggleButtonGroup
              exclusive
              size="small"
              aria-label="Сортировка"
              value={sort}
              onChange={(_, v) => { if (v !== null) setSort(v); }}
            >
              <ToggleButton value="relevance">Популярность</ToggleButton>
              <ToggleButton value="rating">Рейтинг</ToggleButton>
              <ToggleButton value="published">Дата</ToggleButton>
            </ToggleButtonGroup>
            {sort !== 'relevance' && (
              <IconButton
                size="small"
                aria-label={order === 'desc' ? 'По убыванию' : 'По возрастанию'}
                onClick={() => setOrder(order === 'desc' ? 'asc' : 'desc')}
                sx={{
                  border: `1px solid ${theme.palette.divider}`,
                  borderRadius: 1,
                  bgcolor: 'background.paper',
                }}
              >
                {order === 'desc'
                  ? <ArrowDownwardIcon fontSize="small" aria-hidden="true" />
                  : <ArrowUpwardIcon fontSize="small" aria-hidden="true" />}
              </IconButton>
            )}
          </Stack>
```

- [ ] **Step 5: Запустить тесты UI**

Run (в `ui/`): `npm test`
Expected: все тесты PASS, включая два новых.

- [ ] **Step 6: Сборка UI (typecheck)**

Run (в `ui/`): `npm run build`
Expected: tsc + vite build без ошибок.

- [ ] **Step 7: Commit**

```bash
git add ui/src/api/search.ts ui/src/pages/CatalogPage.tsx ui/src/pages/CatalogPage.test.tsx
git commit -m "feat: add sorting controls (rating, publish date) to catalog page"
```

---

## Self-Review

1. **Spec coverage:** `sort`/`order` REST-параметры + 400 (Task 1, Steps 1–4, SearchApiIT), ORDER BY варианты с NULLS LAST и стабильным `e.id` (Task 1, Step 5, тесты Step 8), фильтр по типу при сортировке (тест `sortByRatingPutsUnratedLastAndRespectsType`), выбор пользователя приоритетнее поиска (сортировка не зависит от `q`; параметр передаётся всегда, кроме дефолтного relevance), UI-контролы + react-query key (Task 2), «Популярность» без направления (Task 2, тест Step 1). Миграции не требуются — совпадает со спекой.
2. **Placeholder scan:** нет TBD/TODO; все шаги содержат полный код.
3. **Type consistency:** `SearchQuery(..., SortBy, SortOrder)` единообразно во всех задачах; `SortOrder.sql()` → `"ASC"`/`"DESC"` используется в `orderBy`; фронтовый `sort: 'relevance' | 'rating' | 'published'` совпадает со значениями `ToggleButton`.
