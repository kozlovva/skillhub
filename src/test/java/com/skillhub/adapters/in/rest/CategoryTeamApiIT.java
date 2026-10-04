package com.skillhub.adapters.in.rest;

import com.skillhub.application.service.ApiTokenService;
import com.skillhub.application.service.UserSyncService;
import com.skillhub.domain.model.User;
import com.skillhub.domain.port.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CategoryTeamApiIT {

    @Autowired TestRestTemplate rest;
    @Autowired UserSyncService users;
    @Autowired UserRepositoryPort userRepo;
    @Autowired ApiTokenService tokens;

    String adminHeader;
    String memberHeader;
    String adminSubject = "cat-admin";
    UUID adminId;
    User memberUser;

    @BeforeEach
    void setUp() {
        var admin = users.syncFromSso(adminSubject, "admin@skillhub.io", "cat-admin", "Admin");
        admin.setAdmin(true);
        userRepo.save(admin);
        adminId = admin.getId();
        adminHeader = "Bearer " + tokens.createToken(admin, "admin").rawToken();

        memberUser = users.syncFromSso("cat-member", "member@skillhub.io", "cat-member", "Member");
        memberHeader = "Bearer " + tokens.createToken(memberUser, "member").rawToken();
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
    void adminCreatesTeamNonAdminForbidden() {
        ResponseEntity<String> created = rest.exchange("/api/teams", HttpMethod.POST,
            new HttpEntity<>(Map.of("slug", "design-team", "name", "Design"),
                headers(adminHeader)), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).contains("design-team");

        ResponseEntity<String> forbidden = rest.exchange("/api/teams", HttpMethod.POST,
            new HttpEntity<>(Map.of("slug", "other-team", "name", "Other"),
                headers(memberHeader)), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void ownerAddsMember() {
        rest.exchange("/api/teams", HttpMethod.POST,
            new HttpEntity<>(Map.of("slug", "ux-team", "name", "UX"),
                headers(adminHeader)), String.class);

        ResponseEntity<String> promote = rest.exchange("/api/teams/ux-team/members",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("userId", memberUser.getId().toString(), "role", "OWNER"),
                headers(adminHeader)), String.class);
        assertThat(promote.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(promote.getBody()).contains(memberUser.getId().toString());

        ResponseEntity<String> added = rest.exchange("/api/teams/ux-team/members",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("userId", adminId.toString(), "role", "MEMBER"),
                headers(memberHeader)), String.class);
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void meReturnsAdminFlagAndTeamRoles() {
        rest.exchange("/api/teams", HttpMethod.POST,
            new HttpEntity<>(Map.of("slug", "me-team", "name", "Me"),
                headers(adminHeader)), String.class);
        rest.exchange("/api/teams/me-team/members", HttpMethod.POST,
            new HttpEntity<>(Map.of("userId", memberUser.getId().toString(), "role", "MAINTAINER"),
                headers(adminHeader)), String.class);

        ResponseEntity<String> adminMe = rest.exchange("/api/me", HttpMethod.GET,
            new HttpEntity<>(headers(adminHeader)), String.class);
        assertThat(adminMe.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(adminMe.getBody()).contains("\"admin\":true");

        ResponseEntity<String> memberMe = rest.exchange("/api/me", HttpMethod.GET,
            new HttpEntity<>(headers(memberHeader)), String.class);
        assertThat(memberMe.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(memberMe.getBody()).contains("\"admin\":false");
        assertThat(memberMe.getBody()).contains("me-team");
        assertThat(memberMe.getBody()).contains("MAINTAINER");
    }

    @Test
    void memberCandidatesRequireOwnerAndReturnMatches() {
        rest.exchange("/api/teams", HttpMethod.POST,
            new HttpEntity<>(Map.of("slug", "cand-team", "name", "Cand"),
                headers(adminHeader)), String.class);

        ResponseEntity<String> forbidden = rest.exchange(
            "/api/teams/cand-team/member-candidates?q=cat-m", HttpMethod.GET,
            new HttpEntity<>(headers(memberHeader)), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> found = rest.exchange(
            "/api/teams/cand-team/member-candidates?q=cat-m", HttpMethod.GET,
            new HttpEntity<>(headers(adminHeader)), String.class);
        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody()).contains("cat-member").contains("Member");
    }
}
