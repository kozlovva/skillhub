package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.entity.JpaElement;
import com.skillhub.adapters.out.jpa.mapper.ElementJpaMapper;
import com.skillhub.application.dto.RatingSummary;
import com.skillhub.application.dto.SearchQuery;
import com.skillhub.application.dto.SearchQueryResult;
import com.skillhub.domain.model.Element;
import com.skillhub.domain.port.SearchPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class SearchAdapter implements SearchPort {

    @PersistenceContext
    private EntityManager em;

    private static final String BASE_WHERE = """
        (e.visibility = 'PUBLIC'
         OR :admin = true
         OR e.team_id IN (SELECT tm.team_id FROM team_members tm WHERE tm.user_id = :userId))
        AND (CAST(:category AS text) IS NULL OR e.category_id = (SELECT c.id FROM categories c WHERE c.slug = :category))
        AND (:q = '' OR e.search_vector @@ plainto_tsquery('russian', :q)
             OR e.name ILIKE ('%' || :q || '%'))
        """;

    private static final String WHERE = BASE_WHERE + """
        AND (CAST(:type AS text) IS NULL OR e.type = :type)
        """;

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public SearchQueryResult search(SearchQuery q) {
        String query = q.q() == null ? "" : q.q();

        List<JpaElement> items = em.createNativeQuery(
                "SELECT e.* FROM elements e WHERE " + WHERE +
                " ORDER BY CASE WHEN :q = '' THEN 0 " +
                "ELSE ts_rank(e.search_vector, plainto_tsquery('russian', :q)) END DESC, " +
                "e.downloads_count DESC LIMIT :limit OFFSET :offset", JpaElement.class)
            .setParameter("userId", q.userId())
            .setParameter("admin", q.admin())
            .setParameter("q", query)
            .setParameter("type", q.type())
            .setParameter("category", q.category())
            .setParameter("limit", q.limit())
            .setParameter("offset", q.offset())
            .getResultList();

        long total = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM elements e WHERE " + WHERE)
            .setParameter("userId", q.userId())
            .setParameter("admin", q.admin())
            .setParameter("q", query)
            .setParameter("type", q.type())
            .setParameter("category", q.category())
            .getSingleResult()).longValue();

        Map<String, Long> facets = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT e.type AS type, COUNT(*) AS cnt FROM elements e WHERE " + BASE_WHERE +
                " GROUP BY e.type")
            .setParameter("userId", q.userId())
            .setParameter("admin", q.admin())
            .setParameter("q", query)
            .setParameter("category", q.category())
            .getResultList();
        for (Object[] row : rows) {
            facets.put((String) row[0], ((Number) row[1]).longValue());
        }

        Map<UUID, RatingSummary> ratings = new HashMap<>();
        List<UUID> ids = items.stream().map(JpaElement::getId).toList();
        if (!ids.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<Object[]> ratingRows = em.createNativeQuery(
                    "SELECT r.element_id, AVG(r.rating) AS avg, COUNT(*) AS cnt " +
                    "FROM ratings r WHERE r.element_id IN (:ids) GROUP BY r.element_id")
                .setParameter("ids", ids)
                .getResultList();
            for (Object[] row : ratingRows) {
                ratings.put((UUID) row[0], new RatingSummary(
                    ((Number) row[1]).doubleValue(), ((Number) row[2]).longValue()));
            }
        }

        return new SearchQueryResult(
            items.stream().map(ElementJpaMapper::toDomain).toList(),
            total, facets, ratings);
    }
}
