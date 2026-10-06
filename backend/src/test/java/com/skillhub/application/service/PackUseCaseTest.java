package com.skillhub.application.service;

import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.core.exception.UnprocessableException;
import com.skillhub.domain.model.*;
import com.skillhub.domain.port.*;
import com.skillhub.domain.service.AccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PackUseCaseTest {

    ElementUseCase elementUseCase;
    VersionUseCase versionUseCase;
    PackContentRepositoryPort packContents;
    TeamMembershipPort membership;
    StoragePort storage;
    PackUseCase useCase;

    User owner = User.builder().id(UUID.randomUUID()).ssoSubject("s").email("e")
        .displayName("Owner").admin(false).createdAt(Instant.now()).build();
    Team team = Team.builder().id(UUID.randomUUID()).slug("t").name("T")
        .createdAt(Instant.now()).build();

    Element packElement() {
        return Element.builder().id(UUID.randomUUID()).slug("my-pack")
            .type(ElementType.PACK).name("my-pack").description("").team(team)
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    Element skillElement() {
        return Element.builder().id(UUID.randomUUID()).slug("skill-a")
            .type(ElementType.SKILL).name("skill-a").description("").team(team)
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
            .latestVersion("1.0.0").downloadsCount(0)
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    ElementVersion versionOf(Element el) {
        return ElementVersion.builder().id(UUID.randomUUID()).element(el)
            .version("1.0.0").status(VersionStatus.PUBLISHED).changelog("")
            .s3_key("t/skill-a/1.0.0.zip").sizeBytes(1).fileIndex("{}")
            .publishedBy(owner).createdAt(Instant.now()).publishedAt(Instant.now()).build();
    }

    @BeforeEach
    void setUp() {
        elementUseCase = mock(ElementUseCase.class);
        versionUseCase = mock(VersionUseCase.class);
        packContents = mock(PackContentRepositoryPort.class);
        membership = mock(TeamMembershipPort.class);
        storage = mock(StoragePort.class);
        when(membership.roleOf(team.getId(), owner.getId()))
            .thenReturn(Optional.of(TeamRole.OWNER));
        when(packContents.save(any())).thenAnswer(inv -> inv.getArgument(0));
        useCase = new PackUseCase(packContents, elementUseCase, versionUseCase,
            new AccessService(membership), storage);
    }

    @Test
    void addContentToPack() {
        when(elementUseCase.getBySlug("my-pack", owner)).thenReturn(packElement());
        when(elementUseCase.getBySlug("skill-a", owner)).thenReturn(skillElement());

        PackContent content = useCase.addContent("my-pack", "skill-a", "1.0.0", owner);
        assertThat(content.getElement().getSlug()).isEqualTo("skill-a");
        assertThat(content.getVersionConstraint()).isEqualTo("1.0.0");
    }

    @Test
    void nonPackTargetIsUnprocessable() {
        when(elementUseCase.getBySlug("skill-a", owner)).thenReturn(skillElement());
        assertThatThrownBy(() -> useCase.addContent("skill-a", "skill-a", "latest", owner))
            .isInstanceOf(UnprocessableException.class);
    }

    @Test
    void invalidConstraintIsUnprocessable() {
        when(elementUseCase.getBySlug("my-pack", owner)).thenReturn(packElement());
        when(elementUseCase.getBySlug("skill-a", owner)).thenReturn(skillElement());
        assertThatThrownBy(() -> useCase.addContent("my-pack", "skill-a", "1.x", owner))
            .isInstanceOf(UnprocessableException.class);
    }

    @Test
    void nonPublisherForbidden() {
        when(elementUseCase.getBySlug("my-pack", owner)).thenReturn(packElement());
        when(elementUseCase.getBySlug("skill-a", owner)).thenReturn(skillElement());
        when(membership.roleOf(team.getId(), owner.getId()))
            .thenReturn(Optional.of(TeamRole.MEMBER));
        assertThatThrownBy(() -> useCase.addContent("my-pack", "skill-a", "latest", owner))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void personalPackContentManagedByAuthor() {
        Element personalPack = Element.builder().id(UUID.randomUUID()).slug("my-pack")
            .type(ElementType.PACK).name("my-pack").description("").team(null)
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(elementUseCase.getBySlug("my-pack", owner)).thenReturn(personalPack);
        when(elementUseCase.getBySlug("skill-a", owner)).thenReturn(skillElement());

        PackContent content = useCase.addContent("my-pack", "skill-a", "1.0.0", owner);
        assertThat(content.getPackElement().getSlug()).isEqualTo("my-pack");
    }

    @Test
    void personalPackContentForbiddenForNonAuthor() {
        User other = User.builder().id(UUID.randomUUID()).ssoSubject("s2").email("e2")
            .displayName("Other").admin(false).createdAt(Instant.now()).build();
        Element personalPack = Element.builder().id(UUID.randomUUID()).slug("my-pack")
            .type(ElementType.PACK).name("my-pack").description("").team(null)
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(elementUseCase.getBySlug("my-pack", other)).thenReturn(personalPack);

        assertThatThrownBy(() -> useCase.addContent("my-pack", "skill-a", "1.0.0", other))
            .isInstanceOf(ForbiddenException.class)
            .isNotInstanceOf(NullPointerException.class);
    }

    @Test
    void downloadPackBuildsZipWithManifestAndElements() throws Exception {
        Element pack = packElement();
        Element skill = skillElement();
        when(elementUseCase.getBySlug("my-pack", owner)).thenReturn(pack);
        when(packContents.findAllByPackId(pack.getId()))
            .thenReturn(List.of(PackContent.builder()
                .packElement(pack).element(skill).versionConstraint("1.0.0").build()));
        when(versionUseCase.getVersion("skill-a", "1.0.0", owner)).thenReturn(versionOf(skill));
        when(storage.download(any())).thenReturn(zipOf(Map.of("SKILL.md", "# skill")));

        byte[] packZip = useCase.downloadPack("my-pack", owner);

        boolean foundManifest = false;
        boolean foundSkill = false;
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(packZip))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (entry.getName().equals("manifest.json")) {
                    foundManifest = true;
                }
                if (entry.getName().equals("skill-a-1.0.0/SKILL.md")) {
                    foundSkill = true;
                }
            }
        }
        assertThat(foundManifest).isTrue();
        assertThat(foundSkill).isTrue();
    }

    @Test
    void selfReferenceIsUnprocessable() {
        when(elementUseCase.getBySlug("my-pack", owner)).thenReturn(packElement());
        assertThatThrownBy(() -> useCase.addContent("my-pack", "my-pack", "latest", owner))
            .isInstanceOf(UnprocessableException.class);
    }

    @Test
    void packElementAsContentIsUnprocessable() {
        when(elementUseCase.getBySlug("my-pack", owner)).thenReturn(packElement());
        Element otherPack = Element.builder().id(UUID.randomUUID()).slug("other-pack")
            .type(ElementType.PACK).name("other-pack").description("").team(team)
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(elementUseCase.getBySlug("other-pack", owner)).thenReturn(otherPack);
        assertThatThrownBy(() -> useCase.addContent("my-pack", "other-pack", "latest", owner))
            .isInstanceOf(UnprocessableException.class);
    }

    @Test
    void downloadPackSkipsInaccessibleContent() throws Exception {
        User outsider = User.builder().id(UUID.randomUUID()).ssoSubject("s2").email("e2")
            .displayName("Outsider").admin(false).createdAt(Instant.now()).build();
        Team otherTeam = Team.builder().id(UUID.randomUUID()).slug("t2").name("T2")
            .createdAt(Instant.now()).build();
        when(membership.roleOf(otherTeam.getId(), outsider.getId()))
            .thenReturn(Optional.empty());

        Element pack = packElement();
        Element privateSkill = Element.builder().id(UUID.randomUUID()).slug("skill-private")
            .type(ElementType.SKILL).name("skill-private").description("").team(otherTeam)
            .tags(new String[0]).visibility(Visibility.TEAM).author(owner)
            .latestVersion("1.0.0").downloadsCount(0)
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(elementUseCase.getBySlug("my-pack", outsider)).thenReturn(pack);
        when(packContents.findAllByPackId(pack.getId()))
            .thenReturn(List.of(PackContent.builder()
                .packElement(pack).element(privateSkill).versionConstraint("1.0.0").build()));

        byte[] packZip = useCase.downloadPack("my-pack", outsider);

        String manifest = null;
        boolean foundPrivate = false;
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(packZip))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (entry.getName().equals("manifest.json")) {
                    manifest = new String(zin.readAllBytes(), StandardCharsets.UTF_8);
                }
                if (entry.getName().startsWith("skill-private-")) {
                    foundPrivate = true;
                }
            }
        }
        assertThat(foundPrivate).isFalse();
        assertThat(manifest).doesNotContain("skill-private");
    }

    static byte[] zipOf(Map<String, String> entries) {
        try (var bos = new java.io.ByteArrayOutputStream();
             var zos = new java.util.zip.ZipOutputStream(bos)) {
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
}
