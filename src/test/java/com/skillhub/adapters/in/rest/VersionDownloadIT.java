package com.skillhub.adapters.in.rest;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class VersionDownloadIT extends VersionPublishIT {

    HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, authHeader);
        return h;
    }

    @Test
    void downloadSpecificAndLatestVersion() {
        publish("pub-skill", "3.0.0", "three");
        publish("pub-skill", "3.1.0", "three-one");

        ResponseEntity<byte[]> latest = rest.exchange(
            "/api/elements/pub-skill/versions/latest/download", HttpMethod.GET,
            new HttpEntity<>(authHeaders()), byte[].class);
        assertThat(latest.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(latest.getHeaders().getContentType().toString()).contains("zip");

        ResponseEntity<byte[]> specific = rest.exchange(
            "/api/elements/pub-skill/versions/3.0.0/download", HttpMethod.GET,
            new HttpEntity<>(authHeaders()), byte[].class);
        assertThat(specific.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(specific.getBody(), StandardCharsets.ISO_8859_1)).contains("3.0.0");
    }

    @Test
    void listVersionsReturnsAll() {
        publish("pub-skill", "3.2.0", null);
        publish("pub-skill", "3.2.1", null);
        ResponseEntity<String> list = rest.exchange(
            "/api/elements/pub-skill/versions", HttpMethod.GET,
            new HttpEntity<>(authHeaders()), String.class);
        assertThat(list.getBody()).contains("3.2.0").contains("3.2.1");
    }

    @Test
    void downloadSingleFile() {
        publish("pub-skill", "4.0.0", "four");
        ResponseEntity<byte[]> file = rest.exchange(
            "/api/elements/pub-skill/versions/4.0.0/files?path=SKILL.md", HttpMethod.GET,
            new HttpEntity<>(authHeaders()), byte[].class);
        assertThat(file.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(file.getBody(), StandardCharsets.UTF_8)).isEqualTo("# skill");
    }

    @Test
    void unknownVersionIsNotFound() {
        ResponseEntity<String> r = rest.exchange(
            "/api/elements/pub-skill/versions/9.9.9/download", HttpMethod.GET,
            new HttpEntity<>(authHeaders()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void downloadsCounterIncrements() {
        publish("pub-skill", "5.0.0", "five");
        rest.exchange("/api/elements/pub-skill/versions/5.0.0/download", HttpMethod.GET,
            new HttpEntity<>(authHeaders()), byte[].class);
        Long count = jdbc.queryForObject(
            "SELECT downloads_count FROM elements WHERE slug='pub-skill'", Long.class);
        assertThat(count).isGreaterThanOrEqualTo(1);
    }
}
