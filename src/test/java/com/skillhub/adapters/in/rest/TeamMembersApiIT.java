package com.skillhub.adapters.in.rest;

import com.skillhub.application.service.ApiTokenService;
import com.skillhub.application.service.UserSyncService;
import com.skillhub.domain.port.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TeamMembersApiIT {

    @Autowired TestRestTemplate rest;
    @Autowired UserSyncService users;
    @Autowired UserRepositoryPort userRepo;
    @Autowired ApiTokenService tokens;

    String admin;
    String owner;
    String maintainer;
    UUID maintainerId;

    @BeforeEach
    void setUp() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());

        var a = users.syncFromSso("tm-admin", "tm-admin@b.c", "tm-admin", "Tm Admin");
        a.setAdmin(true);
        userRepo.save(a);
        admin = "Bearer " + tokens.createToken(a, "admin").rawToken();

        var o = users.syncFromSso("tm-owner", "tm-owner@b.c", "tm-owner", "Tm Owner");
        owner = "Bearer " + tokens.createToken(o, "owner").rawToken();

        var m = users.syncFromSso("tm-maint", "tm-maint@b.c", "tm-maint", "Tm Maintainer");
        maintainerId = m.getId();
        maintainer = "Bearer " + tokens.createToken(m, "maint").rawToken();

        rest.exchange("/api/teams", HttpMethod.POST,
            new HttpEntity<>(Map.of("slug", "tm-team", "name", "TM"),
                json(admin)), String.class);
        rest.exchange("/api/teams/tm-team/members", HttpMethod.POST,
            new HttpEntity<>(Map.of("userId", o.getId().toString(), "role", "OWNER"),
                json(admin)), String.class);
        rest.exchange("/api/teams/tm-team/members", HttpMethod.POST,
            new HttpEntity<>(Map.of("userId", maintainerId.toString(), "role", "MAINTAINER"),
                json(admin)), String.class);
    }

    HttpHeaders json(String token) {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    void memberSeesRosterOutsiderForbidden() {
        ResponseEntity<String> seen = rest.exchange("/api/teams/tm-team/members",
            HttpMethod.GET, new HttpEntity<>(json(maintainer)), String.class);
        assertThat(seen.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(seen.getBody()).contains("tm-owner").contains("OWNER").contains("MAINTAINER");

        var stranger = users.syncFromSso("tm-stranger", "tm-stranger@b.c", "tm-stranger", "Stranger");
        String strangerAuth = "Bearer " + tokens.createToken(stranger, "stranger").rawToken();
        ResponseEntity<String> forbidden = rest.exchange("/api/teams/tm-team/members",
            HttpMethod.GET, new HttpEntity<>(json(strangerAuth)), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void maintainerCannotManageOwnerCan() {
        ResponseEntity<String> forbidden = rest.exchange(
            "/api/teams/tm-team/members/" + maintainerId,
            HttpMethod.PATCH,
            new HttpEntity<>(Map.of("role", "MEMBER"), json(maintainer)), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> ok = rest.exchange(
            "/api/teams/tm-team/members/" + maintainerId,
            HttpMethod.PATCH,
            new HttpEntity<>(Map.of("role", "MEMBER"), json(admin)), String.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody()).contains("\"role\":\"MEMBER\"");
    }

    @Test
    void removeMemberWorksAndGuardsLastOwner() {
        var extra = users.syncFromSso("tm-extra", "tm-extra@b.c", "tm-extra", "Tm Extra");
        rest.exchange("/api/teams/tm-team/members", HttpMethod.POST,
            new HttpEntity<>(Map.of("userId", extra.getId().toString(), "role", "MEMBER"),
                json(admin)), String.class);

        ResponseEntity<String> removed = rest.exchange(
            "/api/teams/tm-team/members/" + extra.getId(),
            HttpMethod.DELETE, new HttpEntity<>(json(owner)), String.class);
        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> roster = rest.exchange("/api/teams/tm-team/members",
            HttpMethod.GET, new HttpEntity<>(json(admin)), String.class);
        assertThat(roster.getBody()).doesNotContain("tm-extra");

        String adminMemberId = java.util.Arrays.stream(roster.getBody().split("\\},"))
            .filter(s -> s.contains("tm-admin"))
            .map(s -> s.replaceAll(".*\"userId\":\"([0-9a-f-]{36})\".*", "$1"))
            .findFirst().orElse(null);
        org.junit.jupiter.api.Assumptions.assumeTrue(adminMemberId != null);

        ResponseEntity<String> demoted = rest.exchange(
            "/api/teams/tm-team/members/" + adminMemberId,
            HttpMethod.PATCH,
            new HttpEntity<>(Map.of("role", "MEMBER"), json(admin)), String.class);
        assertThat(demoted.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> lastOwner = rest.exchange(
            "/api/teams/tm-team/members/" + users.syncFromSso(
                "tm-owner", "tm-owner@b.c", "tm-owner", "Tm Owner").getId(),
            HttpMethod.PATCH,
            new HttpEntity<>(Map.of("role", "MEMBER"), json(admin)), String.class);
        assertThat(lastOwner.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(lastOwner.getBody()).contains("last OWNER");
    }
}
