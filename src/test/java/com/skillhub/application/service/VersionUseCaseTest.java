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
            new AccessService(membership), new ArchiveService(200, 10), clock);
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
}
