package com.skillhub.domain.service;

import com.skillhub.core.exception.UnprocessableException;
import com.skillhub.domain.model.ArchiveInfo;
import com.skillhub.domain.service.ArchiveService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchiveServiceTest {

    ArchiveService service = new ArchiveService(200, 10);

    static byte[] zip(Map<String, String> entries) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(bos)) {
            entries.forEach((name, content) -> {
                try {
                    zos.putNextEntry(new ZipEntry(name));
                    zos.write(content.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            zos.finish();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static Map<String, String> validEntries() {
        Map<String, String> entries = new HashMap<>();
        entries.put("manifest.json", "{\"name\":\"pdf-skill\",\"version\":\"1.2.3\","
            + "\"description\":\"PDF tools\",\"type\":\"SKILL\"}");
        entries.put("SKILL.md", "# skill");
        entries.put("scripts/run.sh", "echo hi");
        return entries;
    }

    @Test
    void inspectValidArchive() {
        ArchiveInfo info = service.inspect(zip(validEntries()));
        assertThat(info.manifestName()).isEqualTo("pdf-skill");
        assertThat(info.manifestVersion()).isEqualTo("1.2.3");
        assertThat(info.manifestType()).isEqualTo("SKILL");
        assertThat(info.files()).extracting(com.skillhub.domain.model.FileEntry::path)
            .containsExactlyInAnyOrder("manifest.json", "SKILL.md", "scripts/run.sh");
        assertThat(info.totalSize()).isPositive();
    }

    @Test
    void rejectsMissingManifest() {
        Map<String, String> entries = new HashMap<>();
        entries.put("SKILL.md", "# skill");
        assertThatThrownBy(() -> service.inspect(zip(entries)))
            .isInstanceOf(UnprocessableException.class)
            .hasMessageContaining("manifest.json");
    }

    @Test
    void rejectsInvalidSemverInManifest() {
        Map<String, String> entries = new HashMap<>(validEntries());
        entries.put("manifest.json",
            "{\"name\":\"x\",\"version\":\"1.2\",\"description\":\"\",\"type\":\"SKILL\"}");
        assertThatThrownBy(() -> service.inspect(zip(entries)))
            .isInstanceOf(UnprocessableException.class)
            .hasMessageContaining("version");
    }

    @Test
    void rejectsPathTraversal() {
        Map<String, String> entries = new HashMap<>(validEntries());
        entries.put("../evil.sh", "rm -rf");
        assertThatThrownBy(() -> service.inspect(zip(entries)))
            .isInstanceOf(UnprocessableException.class)
            .hasMessageContaining("traversal");
    }

    @Test
    void rejectsTooManyFiles() {
        Map<String, String> entries = validEntries();
        for (int i = 0; i < 11; i++) {
            entries.put("f" + i + ".txt", "x");
        }
        assertThatThrownBy(() -> service.inspect(zip(entries)))
            .isInstanceOf(UnprocessableException.class)
            .hasMessageContaining("files");
    }

    @Test
    void rejectsNotAZip() {
        assertThatThrownBy(() -> service.inspect("not a zip".getBytes()))
            .isInstanceOf(UnprocessableException.class);
    }
}
