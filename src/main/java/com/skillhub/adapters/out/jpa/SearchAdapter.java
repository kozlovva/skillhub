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
        AND (CAST(:type AS text) IS NULL OR e.type = :type)
        AND (CAST(:category AS text) IS NULL OR e.category_id = (SELECT c.id FROM categories c WHERE c.slug = :category))
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
