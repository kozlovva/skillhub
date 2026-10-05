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
        var user = users.syncFromSso("elem-user", "el@skillhub.io", "elem-user", "Element User");
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
        return jsonHeaders(authHeader);
    }

    HttpHeaders jsonHeaders(String bearer) {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, bearer);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    String otherAuthHeader() {
        var other = users.syncFromSso(
            "other-user", "other@skillhub.io", "other-user", "Other User");
        return "Bearer " + tokens.createToken(other, "other").rawToken();
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
    void invalidTypeIsUnprocessable() {
        createCategory();
        Map<String, Object> body = new java.util.HashMap<>(skillRequest("bad-type-skill"));
        body.put("type", "WIDGET");
        ResponseEntity<String> r = rest.exchange("/api/elements", HttpMethod.POST,
            new HttpEntity<>(body, jsonHeaders()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(r.getBody()).contains("type");
    }

    @Test
    void invalidVisibilityIsUnprocessable() {
        createCategory();
        Map<String, Object> body = new java.util.HashMap<>(skillRequest("bad-vis-skill"));
        body.put("visibility", "SECRET");
        ResponseEntity<String> r = rest.exchange("/api/elements", HttpMethod.POST,
            new HttpEntity<>(body, jsonHeaders()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(r.getBody()).contains("visibility");
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

    @Test
    void deleteElementInPackConflicts() {
        createCategory();
        rest.exchange("/api/elements", HttpMethod.POST,
            new HttpEntity<>(skillRequest("packed-skill"), jsonHeaders()), String.class);
        Map<String, Object> packReq = new java.util.HashMap<>(skillRequest("the-pack"));
        packReq.put("type", "PACK");
        rest.exchange("/api/elements", HttpMethod.POST,
            new HttpEntity<>(packReq, jsonHeaders()), String.class);
        rest.exchange("/api/packs/the-pack/contents", HttpMethod.POST,
            new HttpEntity<>(Map.of("element", "packed-skill", "versionConstraint", "latest"),
                jsonHeaders()), String.class);

        ResponseEntity<String> del = rest.exchange("/api/elements/packed-skill",
            HttpMethod.DELETE, new HttpEntity<>(jsonHeaders()), String.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(del.getBody()).contains("the-pack");
    }

    @Test
    void deleteElementTwiceIsNotFound() {
        createCategory();
        rest.exchange("/api/elements", HttpMethod.POST,
            new HttpEntity<>(skillRequest("twice-skill"), jsonHeaders()), String.class);

        ResponseEntity<String> first = rest.exchange("/api/elements/twice-skill",
            HttpMethod.DELETE, new HttpEntity<>(jsonHeaders()), String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> second = rest.exchange("/api/elements/twice-skill",
            HttpMethod.DELETE, new HttpEntity<>(jsonHeaders()), String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void nonOwnerCannotDeleteElement() {
        createCategory();
        rest.exchange("/api/elements", HttpMethod.POST,
            new HttpEntity<>(skillRequest("owned-skill"), jsonHeaders()), String.class);

        ResponseEntity<String> del = rest.exchange("/api/elements/owned-skill",
            HttpMethod.DELETE, new HttpEntity<>(jsonHeaders(otherAuthHeader())), String.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deletedElementVersionsAreGone() {
        createCategory();
        rest.exchange("/api/elements", HttpMethod.POST,
            new HttpEntity<>(skillRequest("gone-skill"), jsonHeaders()), String.class);
        rest.exchange("/api/elements/gone-skill", HttpMethod.DELETE,
            new HttpEntity<>(jsonHeaders()), String.class);

        ResponseEntity<String> versions = rest.exchange("/api/elements/gone-skill/versions",
            HttpMethod.GET, new HttpEntity<>(jsonHeaders()), String.class);
        assertThat(versions.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void nonAuthorizedCannotDeleteVersion() {
        createCategory();
        rest.exchange("/api/elements", HttpMethod.POST,
            new HttpEntity<>(skillRequest("ver-skill"), jsonHeaders()), String.class);

        ResponseEntity<String> del = rest.exchange("/api/elements/ver-skill/versions/1.0.0",
            HttpMethod.DELETE, new HttpEntity<>(jsonHeaders(otherAuthHeader())), String.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
