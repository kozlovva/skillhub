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
