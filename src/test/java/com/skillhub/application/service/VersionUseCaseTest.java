package com.skillhub.application.service;

import com.skillhub.core.exception.ConflictException;
import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.domain.model.*;
import com.skillhub.domain.port.*;
import com.skillhub.domain.service.AccessService;
import com.skillhub.domain.service.ArchiveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VersionUseCaseTest {

    ElementRepositoryPort elements;
    ElementVersionRepositoryPort versions;
    StoragePort storage;
    AuditService audit;
    ClockPort clock;
    TeamMembershipPort membership;
    PackContentRepositoryPort packContents;
    VersionUseCase useCase;

    User owner = User.builder().id(UUID.randomUUID()).ssoSubject("s").email("e")
        .displayName("Owner").admin(false).createdAt(Instant.now()).build();
    Team team = Team.builder().id(UUID.randomUUID()).slug("platform").name("Platform")
        .createdAt(Instant.now()).build();
    Element element = Element.builder().id(UUID.randomUUID()).slug("my-skill")
        .type(ElementType.SKILL).name("my-skill").description("").team(team)
        .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
        .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();

    static byte[] zip(String version) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(("{\"name\":\"my-skill\",\"version\":\"" + version
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

    @BeforeEach
    void setUp() {
        elements = mock(ElementRepositoryPort.class);
        versions = mock(ElementVersionRepositoryPort.class);
        storage = mock(StoragePort.class);
        audit = mock(AuditService.class);
        clock = mock(ClockPort.class);
        membership = mock(TeamMembershipPort.class);
        packContents = mock(PackContentRepositoryPort.class);
        when(packContents.findAllByElementId(any())).thenReturn(java.util.List.of());
        when(clock.now()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(elements.findBySlug("my-skill")).thenReturn(Optional.of(element));
        when(membership.roleOf(team.getId(), owner.getId()))
            .thenReturn(Optional.of(TeamRole.OWNER));
        when(versions.findByElementIdAndVersion(any(), anyString()))
            .thenReturn(Optional.empty());
        when(versions.save(any())).thenAnswer(inv -> {
            ElementVersion v = inv.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });
        useCase = new VersionUseCase(elements, versions, storage, audit,
            new AccessService(membership), new ArchiveService(200, 10), clock,
            java.time.Duration.ofMinutes(10), packContents);
    }

    @Test
    void ownerDeletesVersionAndRecomputesLatest() {
        ElementVersion v2 = ElementVersion.builder().id(UUID.randomUUID()).element(element)
            .version("2.0.0").status(VersionStatus.PUBLISHED).changelog("two")
            .s3_key("platform/my-skill/2.0.0.zip").sizeBytes(1).fileIndex("{}")
            .publishedBy(owner).createdAt(Instant.now()).publishedAt(Instant.now()).build();
        ElementVersion v1 = ElementVersion.builder().id(UUID.randomUUID()).element(element)
            .version("1.0.0").status(VersionStatus.PUBLISHED).changelog("one")
            .s3_key("platform/my-skill/1.0.0.zip").sizeBytes(1).fileIndex("{}")
            .publishedBy(owner).createdAt(Instant.now()).publishedAt(Instant.now()).build();
        element.setLatestVersion("2.0.0");
        when(versions.findActiveByElementIdAndVersion(element.getId(), "2.0.0"))
            .thenReturn(Optional.of(v2));
        when(versions.findAllByElementIdOrderByCreatedAtDesc(element.getId()))
            .thenReturn(java.util.List.of(v1));
        useCase.deleteVersion("my-skill", "2.0.0", owner);
        assertThat(v2.getDeletedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(element.getLatestVersion()).isEqualTo("1.0.0");
        assertThat(element.getLatestChangelog()).isEqualTo("one");
        verify(elements).save(element);
    }

    @Test
    void deletingOnlyVersionClearsLatest() {
        ElementVersion v1 = ElementVersion.builder().id(UUID.randomUUID()).element(element)
            .version("1.0.0").status(VersionStatus.PUBLISHED).changelog("one")
            .s3_key("platform/my-skill/1.0.0.zip").sizeBytes(1).fileIndex("{}")
            .publishedBy(owner).createdAt(Instant.now()).publishedAt(Instant.now()).build();
        element.setLatestVersion("1.0.0");
        when(versions.findActiveByElementIdAndVersion(element.getId(), "1.0.0"))
            .thenReturn(Optional.of(v1));
        when(versions.findAllByElementIdOrderByCreatedAtDesc(element.getId()))
            .thenReturn(java.util.List.of());
        useCase.deleteVersion("my-skill", "1.0.0", owner);
        assertThat(element.getLatestVersion()).isNull();
        assertThat(element.getLatestChangelog()).isEqualTo("");
        verify(elements).save(element);
    }

    @Test
    void versionPinnedByPackCannotBeDeleted() {
        ElementVersion v1 = ElementVersion.builder().id(UUID.randomUUID()).element(element)
            .version("1.0.0").status(VersionStatus.PUBLISHED).changelog("one")
            .s3_key("platform/my-skill/1.0.0.zip").sizeBytes(1).fileIndex("{}")
            .publishedBy(owner).createdAt(Instant.now()).publishedAt(Instant.now()).build();
        when(versions.findActiveByElementIdAndVersion(element.getId(), "1.0.0"))
            .thenReturn(Optional.of(v1));
        Element pack = Element.builder().id(UUID.randomUUID()).slug("the-pack")
            .type(ElementType.PACK).name("The Pack").description("")
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(packContents.findAllByElementId(element.getId())).thenReturn(java.util.List.of(
            PackContent.builder().packElement(pack).element(element)
                .versionConstraint("1.0.0").build()));
        assertThatThrownBy(() -> useCase.deleteVersion("my-skill", "1.0.0", owner))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void latestConstraintDoesNotBlockVersionDelete() {
        ElementVersion v1 = ElementVersion.builder().id(UUID.randomUUID()).element(element)
            .version("1.0.0").status(VersionStatus.PUBLISHED).changelog("one")
            .s3_key("platform/my-skill/1.0.0.zip").sizeBytes(1).fileIndex("{}")
            .publishedBy(owner).createdAt(Instant.now()).publishedAt(Instant.now()).build();
        element.setLatestVersion("2.0.0");
        when(versions.findActiveByElementIdAndVersion(element.getId(), "1.0.0"))
            .thenReturn(Optional.of(v1));
        Element pack = Element.builder().id(UUID.randomUUID()).slug("the-pack")
            .type(ElementType.PACK).name("The Pack").description("")
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(packContents.findAllByElementId(element.getId())).thenReturn(java.util.List.of(
            PackContent.builder().packElement(pack).element(element)
                .versionConstraint("latest").build()));
        useCase.deleteVersion("my-skill", "1.0.0", owner);
        assertThat(v1.getDeletedAt()).isNotNull();
    }

    @Test
    void maintainerCannotDeleteVersion() {
        when(membership.roleOf(team.getId(), owner.getId()))
            .thenReturn(Optional.of(TeamRole.MAINTAINER));
        assertThatThrownBy(() -> useCase.deleteVersion("my-skill", "1.0.0", owner))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void publishUploadsToS3ThenSavesVersion() {
        ElementVersion published = useCase.publish("my-skill", zip("1.0.0"), "init", owner);
        assertThat(published.getVersion()).isEqualTo("1.0.0");
        assertThat(published.getS3_key()).isEqualTo("platform/my-skill/1.0.0.zip");
        verify(storage).upload(
            org.mockito.ArgumentMatchers.eq("platform/my-skill/1.0.0.zip"),
            any(byte[].class));
        assertThat(element.getLatestVersion()).isEqualTo("1.0.0");
        assertThat(element.getLatestChangelog()).isEqualTo("init");
        verify(audit).log(any(), anyString(), any(), any());
    }

    @Test
    void duplicateVersionConflicts() {
        when(versions.findByElementIdAndVersion(element.getId(), "1.0.0"))
            .thenReturn(Optional.of(ElementVersion.builder().build()));
        assertThatThrownBy(() -> useCase.publish("my-skill", zip("1.0.0"), null, owner))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void nonPublisherForbidden() {
        when(membership.roleOf(team.getId(), owner.getId()))
            .thenReturn(Optional.of(TeamRole.MEMBER));
        assertThatThrownBy(() -> useCase.publish("my-skill", zip("1.0.0"), null, owner))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void authorPublishesTeamLessElement() {
        Element personal = Element.builder().id(UUID.randomUUID()).slug("my-skill")
            .type(ElementType.SKILL).name("my-skill").description("").team(null)
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(elements.findBySlug("my-skill")).thenReturn(Optional.of(personal));
        ElementVersion published = useCase.publish("my-skill", zip("1.0.0"), "init", owner);
        assertThat(published.getS3_key()).isEqualTo("personal/my-skill/1.0.0.zip");
    }

    @Test
    void nonAuthorCannotPublishTeamLessElement() {
        User stranger = User.builder().id(UUID.randomUUID()).ssoSubject("s2").email("e2")
            .displayName("Stranger").admin(false).createdAt(Instant.now()).build();
        Element personal = Element.builder().id(UUID.randomUUID()).slug("my-skill")
            .type(ElementType.SKILL).name("my-skill").description("").team(null)
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(elements.findBySlug("my-skill")).thenReturn(Optional.of(personal));
        assertThatThrownBy(() -> useCase.publish("my-skill", zip("1.0.0"), "init", stranger))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deletedVersionIsNotReturnedByGetVersion() {
        when(versions.findActiveByElementIdAndVersion(element.getId(), "1.0.0"))
            .thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.getVersion("my-skill", "1.0.0", owner))
            .isInstanceOf(com.skillhub.core.exception.NotFoundException.class);
    }

    @Test
    void getArchiveReturnsPresignedUrlAndIncrementsDownloads() {
        ElementVersion version = ElementVersion.builder().id(UUID.randomUUID())
            .element(element).version("1.0.0").s3_key("platform/my-skill/1.0.0.zip").build();
        when(storage.presignedGetUrl(org.mockito.ArgumentMatchers.eq(
                "platform/my-skill/1.0.0.zip"), any(java.time.Duration.class)))
            .thenReturn("http://s3/platform/my-skill/1.0.0.zip?X-Amz-Signature=abc");

        String url = useCase.getArchive(version);

        assertThat(url).contains("X-Amz-Signature");
        verify(storage).presignedGetUrl(
            org.mockito.ArgumentMatchers.eq("platform/my-skill/1.0.0.zip"),
            any(java.time.Duration.class));
        verify(elements).incrementDownloads(element.getId());
    }
}
