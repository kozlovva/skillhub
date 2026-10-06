package com.skillhub.adapters.in.rest;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PackApiIT extends VersionPublishIT {

    void createPack(String slug) {
        rest.exchange("/api/elements", HttpMethod.POST,
            new HttpEntity<>(Map.of(
                "slug", slug, "type", "PACK", "name", slug, "description", "pack",
                "team", "pub-team", "tags", new String[]{}, "visibility", "PUBLIC"),
                jsonHeaders()), String.class);
    }

    void addContent(String packSlug, String elementSlug, String constraint) {
        rest.exchange("/api/packs/" + packSlug + "/contents", HttpMethod.POST,
            new HttpEntity<>(Map.of("element", elementSlug, "versionConstraint", constraint),
                jsonHeaders()), String.class);
    }

    @Test
    void addContentAndDownloadPack() {
        publish("pub-skill", "6.0.0", "six");
        createPack("my-pack");
        addContent("my-pack", "pub-skill", "6.0.0");

        ResponseEntity<byte[]> download = rest.exchange(
            "/api/packs/my-pack/versions/latest/download", HttpMethod.GET,
            new HttpEntity<>(jsonHeaders()), byte[].class);
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);

        boolean foundManifest = false;
        boolean foundSkill = false;
        try (ZipInputStream zin = new ZipInputStream(
                new ByteArrayInputStream(download.getBody()))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (entry.getName().equals("manifest.json")) {
                    foundManifest = true;
                }
                if (entry.getName().equals("pub-skill-6.0.0/SKILL.md")) {
                    foundSkill = true;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertThat(foundManifest).isTrue();
        assertThat(foundSkill).isTrue();
    }

    @Test
    void getPackShowsContents() {
        publish("pub-skill", "7.0.0", "seven");
        createPack("listed-pack");
        addContent("listed-pack", "pub-skill", "latest");
        ResponseEntity<String> r = rest.exchange("/api/packs/listed-pack", HttpMethod.GET,
            new HttpEntity<>(jsonHeaders()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("pub-skill").contains("latest");
    }

    @Test
    void nonPackElementIsUnprocessable() {
        publish("pub-skill", "8.0.0", "eight");
        ResponseEntity<String> r = rest.exchange("/api/packs/pub-skill/contents",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("element", "pub-skill", "versionConstraint", "latest"),
                jsonHeaders()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
