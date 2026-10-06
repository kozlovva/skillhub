package com.skillhub.adapters.in.security;

import com.skillhub.application.service.ApiTokenService;
import com.skillhub.application.service.UserSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityApiIT {

    @Autowired TestRestTemplate rest;
    @Autowired ApiTokenService tokens;
    @Autowired UserSyncService users;

    String authHeader;

    @BeforeEach
    void setUp() {
        var user = users.syncFromSso("sec-user", "sec@skillhub.io", "sec-user", "Sec User");
        authHeader = "Bearer " + tokens.createToken(user, "sec").rawToken();
    }

    @Test
    void tokenManagementEndpointRequiresAuth() {
        ResponseEntity<String> noAuth = rest.getForEntity("/api/tokens", String.class);
        assertThat(noAuth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authHeader.substring(7));
        ResponseEntity<String> ok = rest.exchange("/api/tokens", HttpMethod.GET,
            new HttpEntity<>(headers), String.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void invalidTokenIsUnauthorized() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("skh_invalid");
        ResponseEntity<String> r = rest.exchange("/api/tokens", HttpMethod.GET,
            new HttpEntity<>(headers), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createTokenEndpointReturnsRawToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authHeader.substring(7));
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> created = rest.exchange("/api/tokens", HttpMethod.POST,
            new HttpEntity<>("{\"name\":\"cli-new\"}", headers), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).contains("skh_");
    }
}
