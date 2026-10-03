package com.skillhub.adapters.in.rest;

import com.skillhub.application.service.ApiTokenService;
import com.skillhub.application.service.UserSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TokenApiIT {

    @Autowired TestRestTemplate rest;
    @Autowired UserSyncService users;
    @Autowired ApiTokenService tokens;

    String authHeader;

    @BeforeEach
    void setUp() {
        var user = users.syncFromSso("token-user", "tok@skillhub.io", "Token User");
        authHeader = "Bearer " + tokens.createToken(user, "setup").rawToken();
    }

    HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, authHeader);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    void createTokenReturnsRawOnce() {
        ResponseEntity<String> created = rest.exchange("/api/tokens", HttpMethod.POST,
            new HttpEntity<>(Map.of("name", "cli-token"), jsonHeaders()), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).contains("skh_");
    }

    @Test
    void listTokensWithoutHashes() {
        rest.exchange("/api/tokens", HttpMethod.POST,
            new HttpEntity<>(Map.of("name", "list-token"), jsonHeaders()), String.class);
        ResponseEntity<String> list = rest.exchange("/api/tokens", HttpMethod.GET,
            new HttpEntity<>(jsonHeaders()), String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains("list-token").doesNotContain("tokenHash");
    }
}
