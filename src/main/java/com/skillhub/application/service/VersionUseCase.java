package com.skillhub.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillhub.core.exception.ConflictException;
import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.core.exception.NotFoundException;
import com.skillhub.domain.model.*;
import com.skillhub.domain.port.ElementRepositoryPort;
import com.skillhub.domain.port.ElementVersionRepositoryPort;
import com.skillhub.domain.port.ClockPort;
import com.skillhub.domain.port.StoragePort;
import com.skillhub.domain.service.AccessService;
import com.skillhub.domain.service.ArchiveService;
import com.skillhub.core.exception.UnprocessableException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class VersionUseCase {

    private final ElementRepositoryPort elements;
    private final ElementVersionRepositoryPort versions;
    private final StoragePort storage;
    private final AuditService audit;
    private final AccessService access;
    private final ArchiveService archiveService;
    private final ClockPort clock;
    private final Duration presignTtl;
    private final ObjectMapper mapper = new ObjectMapper();

    public VersionUseCase(ElementRepositoryPort elements,
                          ElementVersionRepositoryPort versions,
                          StoragePort storage, AuditService audit,
                          AccessService access, ArchiveService archiveService,
                          ClockPort clock,
                          @Value("${skillhub.storage.presign-ttl:10m}") Duration presignTtl) {
        this.elements = elements;
        this.versions = versions;
        this.storage = storage;
        this.audit = audit;
        this.access = access;
        this.archiveService = archiveService;
        this.clock = clock;
        this.presignTtl = presignTtl;
    }

    @Transactional
    public ElementVersion publish(String slug, byte[] zipBytes, String changelog, User publisher) {
        Element element = elements.findBySlug(slug)
            .orElseThrow(() -> new NotFoundException("Element not found: " + slug));
        if (!access.canPublish(element.getTeam(), publisher)) {
            throw new ForbiddenException("Only OWNER/MAINTAINER can publish to this team");
        }

        ArchiveInfo info = archiveService.inspect(zipBytes);

        if (versions.findByElementIdAndVersion(element.getId(), info.manifestVersion()).isPresent()) {
            throw new ConflictException(
                "Version " + info.manifestVersion() + " already exists for " + slug);
        }

        String s3Key = element.getTeam().getSlug() + "/" + element.getSlug()
            + "/" + info.manifestVersion() + ".zip";
        storage.upload(s3Key, zipBytes);

        ElementVersion version = versions.save(ElementVersion.builder()
            .element(element)
            .version(info.manifestVersion())
            .status(VersionStatus.PUBLISHED)
            .changelog(changelog == null ? "" : changelog)
            .s3_key(s3Key)
            .sizeBytes(zipBytes.length)
            .fileIndex(buildFileIndex(info))
            .publishedBy(publisher)
            .createdAt(clock.now())
            .publishedAt(clock.now())
            .build());

        element.setLatestVersion(info.manifestVersion());
        element.setUpdatedAt(clock.now());
        elements.save(element);

        audit.log(publisher, "PUBLISH_VERSION", element.getId(),
            Map.of("version", info.manifestVersion(), "s3Key", s3Key));
        return version;
    }

    private String buildFileIndex(ArchiveInfo info) {
        ObjectNode root = mapper.createObjectNode();
        root.put("totalSize", info.totalSize());
        ArrayNode files = root.putArray("files");
        for (FileEntry f : info.files()) {
            ObjectNode node = files.addObject();
            node.put("path", f.path());
            node.put("size", f.size());
        }
        return root.toString();
    }

    @Transactional(readOnly = true)
    public ElementVersion getVersion(String slug, String version, User viewer) {
        Element element = elements.findBySlug(slug)
            .orElseThrow(() -> new NotFoundException("Element not found: " + slug));
        if (!access.canRead(element, viewer)) {
            throw new ForbiddenException("Element is not visible to you: " + slug);
        }
        return "latest".equals(version)
            ? versions.findLatestPublished(element.getId())
                .orElseThrow(() -> new NotFoundException("No published versions for: " + slug))
            : versions.findByElementIdAndVersion(element.getId(), version)
                .orElseThrow(() -> new NotFoundException(
                    "Version not found: " + slug + "@" + version));
    }

    @Transactional(readOnly = true)
    public List<ElementVersion> listVersions(String slug, User viewer) {
        Element element = elements.findBySlug(slug)
            .orElseThrow(() -> new NotFoundException("Element not found: " + slug));
        if (!access.canRead(element, viewer)) {
            throw new ForbiddenException("Element is not visible to you: " + slug);
        }
        return versions.findAllByElementIdOrderByCreatedAtDesc(element.getId());
    }

    @Transactional
    public String getArchive(ElementVersion version) {
        elements.incrementDownloads(version.getElement().getId());
        return storage.presignedGetUrl(version.getS3_key(), presignTtl);
    }

    @Transactional(readOnly = true)
    public byte[] getFile(ElementVersion version, String path) {
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.equals("..")
                || normalized.contains("../") || normalized.contains("/..")) {
            throw new UnprocessableException("Illegal file path", path);
        }
        byte[] archive = storage.download(version.getS3_key());
        try (ZipArchiveInputStream zin = new ZipArchiveInputStream(
                new ByteArrayInputStream(archive), "UTF-8", true, true)) {
            ZipArchiveEntry entry;
            while ((entry = zin.getNextZipEntry()) != null) {
                if (!entry.isDirectory()
                        && entry.getName().replace('\\', '/').equals(normalized)) {
                    return zin.readAllBytes();
                }
            }
        } catch (IOException e) {
            throw new UnprocessableException("Stored archive is corrupted", e.getMessage());
        }
        throw new NotFoundException("File not found in version: " + path);
    }
}
