package com.skillhub.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillhub.core.exception.UnprocessableException;
import com.skillhub.domain.model.ArchiveInfo;
import com.skillhub.domain.model.FileEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ArchiveService {

    public static final Pattern SEMVER =
        Pattern.compile("^\\d+\\.\\d+\\.\\d+(-[0-9A-Za-z.-]+)?$");
    private static final String MANIFEST = "manifest.json";

    private final long maxUncompressedBytes;
    private final int maxFiles;
    private final ObjectMapper mapper = new ObjectMapper();

    public ArchiveService(long maxUncompressedBytes, int maxFiles) {
        this.maxUncompressedBytes = maxUncompressedBytes;
        this.maxFiles = maxFiles;
    }

    public ArchiveInfo inspect(byte[] zipBytes) {
        List<FileEntry> files = new ArrayList<>();
        byte[] manifestBytes = null;
        long total = 0;
        int count = 0;

        try (ZipArchiveInputStream zin = new ZipArchiveInputStream(
                new ByteArrayInputStream(zipBytes), "UTF-8", true, true)) {
            ZipArchiveEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zin.getNextZipEntry()) != null) {
                String path = normalize(entry.getName());
                if (path == null) {
                    throw new UnprocessableException(
                        "Path traversal detected in archive", entry.getName());
                }
                if (entry.isDirectory()) {
                    continue;
                }
                count++;
                if (count > maxFiles) {
                    throw new UnprocessableException(
                        "Archive contains more than " + maxFiles + " files", null);
                }
                ByteArrayOutputStream manifestBuf =
                    path.equals(MANIFEST) ? new ByteArrayOutputStream() : null;
                long size = 0;
                int read;
                while ((read = zin.read(buffer)) != -1) {
                    size += read;
                    total += read;
                    if (manifestBuf != null) {
                        manifestBuf.write(buffer, 0, read);
                    }
                    if (total > maxUncompressedBytes) {
                        throw new UnprocessableException(
                            "Uncompressed archive exceeds " + maxUncompressedBytes + " bytes", null);
                    }
                }
                if (manifestBuf != null) {
                    manifestBytes = manifestBuf.toByteArray();
                }
                files.add(new FileEntry(path, size));
            }
        } catch (IOException e) {
            throw new UnprocessableException("Not a valid zip archive", e.getMessage());
        }

        if (manifestBytes == null) {
            throw new UnprocessableException(
                "Archive must contain " + MANIFEST + " in its root", null);
        }

        JsonNode manifest = parseManifest(new String(manifestBytes, StandardCharsets.UTF_8));
        String name = requiredText(manifest, "name");
        String version = requiredText(manifest, "version");
        if (!SEMVER.matcher(version).matches()) {
            throw new UnprocessableException(
                "manifest version must be semver (e.g. 1.2.3)", version);
        }
        return new ArchiveInfo(name, version,
            manifest.path("description").asText(""),
            manifest.path("type").asText("OTHER"),
            files, total);
    }

    private String normalize(String name) {
        String cleaned = name.replace('\\', '/');
        if (cleaned.startsWith("/") || cleaned.equals("..")
                || cleaned.contains("../") || cleaned.contains("/..")) {
            return null;
        }
        return cleaned;
    }

    private JsonNode parseManifest(String json) {
        try {
            return mapper.readTree(json);
        } catch (IOException e) {
            throw new UnprocessableException("manifest.json is not valid JSON", e.getMessage());
        }
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            throw new UnprocessableException("manifest." + field + " is required", null);
        }
        return value;
    }
}
