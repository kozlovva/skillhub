package com.skillhub.adapters.in.security;

import com.skillhub.application.service.ApiTokenService;
import com.skillhub.application.service.UserSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublicReadAccessIT {

    @Autowired TestRestTemplate rest;
    @Autowired UserSyncService users;
    @Autowired ApiTokenService tokens;
    @Autowired JdbcTemplate jdbc;
    @LocalServerPort int port;

    String authHeader;
    String publicSlug;
    String teamSlug;

    @BeforeEach
    void setUp() {
        publicSlug = "anon-public-" + UUID.randomUUID();
        teamSlug = "anon-team-" + UUID.randomUUID();

        var user = users.syncFromSso("anon-user-" + UUID.randomUUID(), "anon@skillhub.io", "Anon User");
        authHeader = "Bearer " + tokens.createToken(user, "anon").rawToken();

        UUID teamId = UUID.randomUUID();
        jdbc.update("INSERT INTO teams (id, slug, name) VALUES (?, ?, ?) ON CONFLICT (slug) DO NOTHING",
            teamId, "anon-team", "Anon Team");
        UUID resolvedTeamId = jdbc.queryForObject(
            "SELECT id FROM teams WHERE slug = 'anon-team'", UUID.class);
        jdbc.update("""
            INSERT INTO team_members (team_id, user_id, role)
            SELECT ?, ?, 'OWNER'
            WHERE NOT EXISTS (SELECT 1 FROM team_members WHERE team_id = ? AND user_id = ?)
            """, resolvedTeamId, user.getId(), resolvedTeamId, user.getId());

        jdbc.update("""
            INSERT INTO categories (slug, name) VALUES ('anon-dev', 'Dev')
            ON CONFLICT (slug) DO NOTHING
            """);

        createElement(publicSlug, "PUBLIC");
        createElement(teamSlug, "TEAM");
    }

    void createElement(String slug, String visibility) {
        Map<String, Object> body = Map.of(
            "slug", slug, "type", "SKILL", "name", slug,
            "description", "d", "team", "anon-team",
            "category", "anon-dev", "tags", new String[]{}, "visibility", visibility);
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, authHeader);
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> r = rest.exchange("/api/elements", HttpMethod.POST,
            new HttpEntity<>(body, h), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void anonymousSearchIsPermitted() {
        ResponseEntity<String> r = rest.getForEntity("/api/search?q=" + publicSlug, String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains(publicSlug);
    }

    @Test
    void anonymousGetOfPublicElementIsPermitted() {
        ResponseEntity<String> r = rest.getForEntity("/api/elements/" + publicSlug, String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void anonymousGetOfTeamElementIsForbidden() {
        ResponseEntity<String> r = rest.getForEntity("/api/elements/" + teamSlug, String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void anonymousVersionListOfTeamElementIsForbidden() {
        ResponseEntity<String> r = rest.getForEntity(
            "/api/elements/" + teamSlug + "/versions", String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void anonymousVersionListOfPublicElementIsPermitted() {
        ResponseEntity<String> r = rest.getForEntity(
            "/api/elements/" + publicSlug + "/versions", String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void anonymousCreateElementIsUnauthorized() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/elements"))
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .POST(HttpRequest.BodyPublishers.ofString(
                "{\"slug\":\"anon-new-skill\",\"type\":\"SKILL\"}"))
            .build();
        HttpResponse<String> r = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(r.body()).contains("\"code\":\"UNAUTHORIZED\"");
    }

    @Test
    void authenticatedNonMemberCreateIsForbiddenWithBody() {
        var outsider = users.syncFromSso(
            "anon-outsider-" + UUID.randomUUID(), "outsider@skillhub.io", "Outsider");
        String outsiderAuth = "Bearer " + tokens.createToken(outsider, "outsider").rawToken();

        Map<String, Object> body = Map.of(
            "slug", "anon-forbidden-" + UUID.randomUUID(), "type", "SKILL", "name", "x",
            "description", "d", "team", "anon-team",
            "category", "anon-dev", "tags", new String[]{}, "visibility", "PUBLIC");
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, outsiderAuth);
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> r = rest.exchange("/api/elements", HttpMethod.POST,
            new HttpEntity<>(body, h), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(r.getBody()).contains("\"code\":\"FORBIDDEN\"");
    }

    @Test
    void anonymousTokenListIsUnauthorized() {
        ResponseEntity<String> r = rest.getForEntity("/api/tokens", String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
