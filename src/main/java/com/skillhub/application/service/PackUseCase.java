package com.skillhub.application.service;

import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.core.exception.UnprocessableException;
import com.skillhub.domain.model.*;
import com.skillhub.domain.port.PackContentRepositoryPort;
import com.skillhub.domain.port.StoragePort;
import com.skillhub.domain.service.AccessService;
import com.skillhub.domain.service.ArchiveService;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class PackUseCase {

    private final PackContentRepositoryPort packContents;
    private final ElementUseCase elementUseCase;
    private final VersionUseCase versionUseCase;
    private final AccessService access;
    private final StoragePort storage;

    public PackUseCase(PackContentRepositoryPort packContents,
                       ElementUseCase elementUseCase,
                       VersionUseCase versionUseCase,
                       AccessService access,
                       StoragePort storage) {
        this.packContents = packContents;
        this.elementUseCase = elementUseCase;
        this.versionUseCase = versionUseCase;
        this.access = access;
        this.storage = storage;
    }

    @Transactional
    public PackContent addContent(String packSlug, String elementSlug,
                                  String versionConstraint, User user) {
        Element pack = elementUseCase.getBySlug(packSlug, user);
        if (pack.getType() != ElementType.PACK) {
            throw new UnprocessableException("Element is not a PACK: " + packSlug, null);
        }
        if (elementSlug.equals(packSlug)) {
            throw new UnprocessableException("Pack cannot contain itself: " + packSlug, null);
        }
        if (pack.getTeam() != null
                ? !access.canPublish(pack.getTeam(), user)
                : !access.canPublishPersonal(pack, user)) {
            throw new ForbiddenException("Only OWNER/MAINTAINER can modify this pack");
        }
        Element element = elementUseCase.getBySlug(elementSlug, user);
        if (element.getType() == ElementType.PACK) {
            throw new UnprocessableException(
                "Nested packs are not allowed: " + elementSlug, null);
        }
        validateConstraint(versionConstraint);
        return packContents.save(PackContent.builder()
            .packElement(pack)
            .element(element)
            .versionConstraint(versionConstraint)
            .build());
    }

    @Transactional(readOnly = true)
    public List<PackContent> getContents(String packSlug, User viewer) {
        Element pack = elementUseCase.getBySlug(packSlug, viewer);
        return packContents.findAllByPackId(pack.getId());
    }

    @Transactional(readOnly = true)
    public byte[] downloadPack(String packSlug, User viewer) {
        Element pack = elementUseCase.getBySlug(packSlug, viewer);
        List<PackContent> contents = packContents.findAllByPackId(pack.getId());

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipArchiveOutputStream zos = new ZipArchiveOutputStream(bos)) {

            StringBuilder manifest = new StringBuilder("{\"pack\":\"")
                .append(pack.getSlug()).append("\",\"contents\":[");
            boolean first = true;
            for (PackContent content : contents) {
                Element element = content.getElement();
                if (!access.canRead(element, viewer)) {
                    continue;
                }
                ElementVersion version = versionUseCase.getVersion(
                    element.getSlug(), content.getVersionConstraint(), viewer);
                if (!first) {
                    manifest.append(",");
                }
                first = false;
                manifest.append("{\"element\":\"").append(element.getSlug())
                    .append("\",\"version\":\"").append(version.getVersion()).append("\"}");
                copyElementArchive(zos, version,
                    element.getSlug() + "-" + version.getVersion() + "/");
            }
            manifest.append("]}");

            zos.putArchiveEntry(new ZipArchiveEntry("manifest.json"));
            zos.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeArchiveEntry();
            zos.finish();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UnprocessableException("Failed to build pack archive", e.getMessage());
        }
    }

    private void copyElementArchive(ZipArchiveOutputStream zos, ElementVersion version,
                                    String prefix) throws IOException {
        byte[] archive = storage.download(version.getS3_key());
        try (ZipArchiveInputStream zin = new ZipArchiveInputStream(
                new ByteArrayInputStream(archive), "UTF-8", true, true)) {
            ZipArchiveEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zin.getNextZipEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                zos.putArchiveEntry(new ZipArchiveEntry(prefix + entry.getName()));
                int read;
                while ((read = zin.read(buffer)) != -1) {
                    zos.write(buffer, 0, read);
                }
                zos.closeArchiveEntry();
            }
        }
    }

    private void validateConstraint(String constraint) {
        if (!"latest".equals(constraint)
                && !ArchiveService.SEMVER.matcher(constraint).matches()) {
            throw new UnprocessableException(
                "versionConstraint must be 'latest' or exact semver", constraint);
        }
    }
}
