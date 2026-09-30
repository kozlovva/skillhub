package com.skillhub.adapters.in.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class VersionDownloadIT extends VersionPublishIT {

    @Autowired protected TestRestTemplate rest;
    @LocalServerPort int port;

    HttpClient noRedirectClient() {
        return HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    HttpResponse<String> getNoRedirect(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + path))
            .header(HttpHeaders.AUTHORIZATION, authHeader)
            .GET()
            .build();
        return noRedirectClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void downloadSpecificAndLatestVersion() throws Exception {
        publish("pub-skill", "3.0.0", "three");
        publish("pub-skill", "3.1.0", "three-one");

        HttpResponse<String> latest = getNoRedirect(
            "/api/elements/pub-skill/versions/latest/download");
        assertThat(latest.statusCode()).isEqualTo(302);
        String latestUrl = latest.headers().firstValue("Location").orElseThrow();
        assertThat(latestUrl).contains("pub-team/pub-skill/3.1.0.zip")
            .contains("X-Amz-Signature");
        assertThat(fetchViaPresignedUrl(latestUrl)).contains("3.1.0");

        HttpResponse<String> specific = getNoRedirect(
            "/api/elements/pub-skill/versions/3.0.0/download");
        assertThat(specific.statusCode()).isEqualTo(302);
        String specificUrl = specific.headers().firstValue("Location").orElseThrow();
        assertThat(specificUrl).contains("pub-team/pub-skill/3.0.0.zip")
            .contains("X-Amz-Signature");
        assertThat(fetchViaPresignedUrl(specificUrl)).contains("3.0.0");
    }

    String fetchViaPresignedUrl(String url) throws Exception {
        HttpResponse<byte[]> response = noRedirectClient().send(
            HttpRequest.newBuilder(URI.create(url)).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        return new String(response.body(), StandardCharsets.ISO_8859_1);
    }

    @Test
    void listVersionsReturnsAll() {
        publish("pub-skill", "3.2.0", null);
        publish("pub-skill", "3.2.1", null);
        ResponseEntity<String> list = rest.exchange(
            "/api/elements/pub-skill/versions", HttpMethod.GET,
            new HttpEntity<>(jsonHeaders()), String.class);
        assertThat(list.getBody()).contains("3.2.0").contains("3.2.1");
    }

    @Test
    void downloadSingleFile() {
        publish("pub-skill", "4.0.0", "four");
        ResponseEntity<byte[]> file = rest.exchange(
            "/api/elements/pub-skill/versions/4.0.0/files?path=SKILL.md", HttpMethod.GET,
            new HttpEntity<>(jsonHeaders()), byte[].class);
        assertThat(file.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(file.getBody(), StandardCharsets.UTF_8)).isEqualTo("# skill");
    }

    @Test
    void unknownVersionIsNotFound() throws Exception {
        HttpResponse<String> r = getNoRedirect(
            "/api/elements/pub-skill/versions/9.9.9/download");
        assertThat(r.statusCode()).isEqualTo(404);
    }

    @Test
    void downloadsCounterIncrements() throws Exception {
        publish("pub-skill", "5.0.0", "five");
        HttpResponse<String> download = getNoRedirect(
            "/api/elements/pub-skill/versions/5.0.0/download");
        assertThat(download.statusCode()).isEqualTo(302);
        Long count = jdbc.queryForObject(
            "SELECT downloads_count FROM elements WHERE slug='pub-skill'", Long.class);
        assertThat(count).isGreaterThanOrEqualTo(1);
    }
}
