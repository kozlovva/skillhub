package com.skillhub.adapters.in.rest;

import com.skillhub.application.service.ApiTokenService;
import com.skillhub.application.service.UserSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VersionPublishIT {

    static final String MINIO_USER = "minio" + "admin";
    static final String MINIO_PASSWORD = "minio" + "admin";
    static final String BUCKET = "skillhub";

    static GenericContainer<?> minio = new GenericContainer<>(
        DockerImageName.parse("quay.io/minio/minio:RELEASE.2024-08-03T04-33-23Z"))
        .withCommand("server /data")
        .withEnv("MINIO_ROOT_USER", MINIO_USER)
        .withEnv("MINIO_ROOT_PASSWORD", MINIO_PASSWORD)
        .withExposedPorts(9000)
        .waitingFor(Wait.forHttp("/minio/health/live").forStatusCode(200));

    @DynamicPropertySource
    static void s3Props(DynamicPropertyRegistry registry) {
        minio.start();
        registry.add("skillhub.storage.endpoint",
            () -> "http://" + minio.getHost() + ":" + minio.getMappedPort(9000));
        registry.add("skillhub.storage.access-key", () -> MINIO_USER);
        registry.add("skillhub.storage.secret-key", () -> MINIO_PASSWORD);
        registry.add("skillhub.storage.bucket", () -> BUCKET);
    }

    @Autowired protected TestRestTemplate rest;
    @Autowired protected UserSyncService users;
    @Autowired protected ApiTokenService tokens;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected com.skillhub.adapters.out.s3.S3StorageAdapter storage;

    protected String authHeader;

    @BeforeEach
    void setUp() {
        storage.ensureBucket();
        var user = users.syncFromSso("pub-user", "pub@skillhub.io", "Publisher");
        authHeader = "Bearer " + tokens.createToken(user, "pub").rawToken();

        jdbc.update("""
            INSERT INTO teams (slug, name) VALUES ('pub-team', 'Pub')
            ON CONFLICT (slug) DO NOTHING
            """);
        UUID teamId = jdbc.queryForObject("SELECT id FROM teams WHERE slug='pub-team'", UUID.class);
        jdbc.update("""
            INSERT INTO team_members (team_id, user_id, role)
            SELECT ?, ?, 'OWNER'
            WHERE NOT EXISTS (SELECT 1 FROM team_members WHERE team_id = ? AND user_id = ?)
            """, teamId, user.getId(), teamId, user.getId());

        if (!elementExists("pub-skill")) {
            rest.exchange("/api/elements", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                    "slug", "pub-skill", "type", "SKILL", "name", "pub-skill",
                    "description", "d", "team", "pub-team",
                    "tags", new String[]{}, "visibility", "PUBLIC"),
                    jsonHeaders()), String.class);
        }
    }

    boolean elementExists(String slug) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM elements WHERE slug = ?", Integer.class, slug);
        return count != null && count > 0;
    }

    static byte[] zip(String version) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.setLevel(Deflater.NO_COMPRESSION);
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(("{\"name\":\"pub-skill\",\"version\":\"" + version
                + "\",\"description\":\"d\",\"type\":\"SKILL\"}")
                .getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("SKILL.md"));
            zos.write("# skill".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.finish();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    ResponseEntity<String> publish(String slug, String version, String changelog) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(zip(version)) {
            @Override
            public String getFilename() {
                return slug + "-" + version + ".zip";
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authHeader);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        String url = "/api/elements/" + slug + "/versions"
            + (changelog == null ? "" : "?changelog=" + changelog);
        return rest.postForEntity(url, new HttpEntity<>(body, headers), String.class);
    }

    HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, authHeader);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    void publishVersionCreatesMetadata() {
        ResponseEntity<String> r = publish("pub-skill", "1.0.0", "initial release");
        assertThat(r.getStatusCode().value()).isEqualTo(201);
        assertThat(r.getBody()).contains("\"version\":\"1.0.0\"").contains("SKILL.md");

        String latest = jdbc.queryForObject(
            "SELECT latest_version FROM elements WHERE slug='pub-skill'", String.class);
        assertThat(latest).isEqualTo("1.0.0");
    }

    @Test
    void duplicateVersionConflicts() {
        publish("pub-skill", "2.0.0", null);
        ResponseEntity<String> r = publish("pub-skill", "2.0.0", null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void invalidSemverIsUnprocessable() {
        ResponseEntity<String> r = publish("pub-skill", "not-semver", null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
