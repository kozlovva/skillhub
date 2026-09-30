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

    private boolean elementExists(String slug) {
        Integer c = jdbc.queryForObject(
            "SELECT COUNT(*) FROM elements WHERE slug = ?", Integer.class, slug);
        return c != null && c > 0;
    }
}
