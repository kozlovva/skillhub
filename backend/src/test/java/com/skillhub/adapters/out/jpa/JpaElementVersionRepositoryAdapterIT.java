package com.skillhub.adapters.out.jpa;

import com.skillhub.domain.model.*;
import com.skillhub.domain.port.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class JpaElementVersionRepositoryAdapterIT {

    @Autowired UserRepositoryPort users;
    @Autowired ElementRepositoryPort elements;
    @Autowired ElementVersionRepositoryPort versions;

    @Test
    void findAllS3KeysReturnsSavedKeys() {
        User author = users.save(User.builder()
            .ssoSubject("s3keys-sub").email("s3keys@b.c").username("s3keys")
            .displayName("S3 Keys").admin(false).createdAt(Instant.now()).build());
        Element element = elements.save(Element.builder()
            .slug("s3keys-element").type(ElementType.SKILL).name("S3 Keys Element")
            .description("").tags(new String[0]).visibility(Visibility.PUBLIC)
            .author(author).createdAt(Instant.now()).updatedAt(Instant.now()).build());
        versions.save(ElementVersion.builder()
            .element(element).version("1.0.0").status(VersionStatus.PUBLISHED)
            .changelog("").s3_key("team/s3keys-element/1.0.0.zip").sizeBytes(10)
            .fileIndex("{}").publishedBy(author)
            .createdAt(Instant.now()).publishedAt(Instant.now()).build());

        assertThat(versions.findAllS3Keys()).contains("team/s3keys-element/1.0.0.zip");
    }

    @Test
    void findAllByElementIdExcludesSoftDeletedVersions() {
        User author = users.save(User.builder()
            .ssoSubject("sdv-sub").email("sdv@b.c").username("sdv")
            .displayName("SDV").admin(false).createdAt(Instant.now()).build());
        Element element = elements.save(Element.builder()
            .slug("sdv-element").type(ElementType.SKILL).name("SDV Element")
            .description("").tags(new String[0]).visibility(Visibility.PUBLIC)
            .author(author).createdAt(Instant.now()).updatedAt(Instant.now()).build());
        ElementVersion kept = versions.save(ElementVersion.builder()
            .element(element).version("1.0.0").status(VersionStatus.PUBLISHED)
            .changelog("").s3_key("team/sdv-element/1.0.0.zip").sizeBytes(10)
            .fileIndex("{}").publishedBy(author)
            .createdAt(Instant.now().minusSeconds(60)).publishedAt(Instant.now()).build());
        ElementVersion removed = versions.save(ElementVersion.builder()
            .element(element).version("2.0.0").status(VersionStatus.PUBLISHED)
            .changelog("").s3_key("team/sdv-element/2.0.0.zip").sizeBytes(10)
            .fileIndex("{}").publishedBy(author)
            .createdAt(Instant.now()).publishedAt(Instant.now()).build());

        ElementVersion softDeleted = versions.findByElementIdAndVersion(
            element.getId(), "2.0.0").orElseThrow();
        softDeleted.setDeletedAt(Instant.now());
        versions.save(softDeleted);

        assertThat(versions.findAllByElementIdOrderByCreatedAtDesc(element.getId()))
            .extracting(ElementVersion::getVersion)
            .containsExactly("1.0.0");
        assertThat(versions.findAllS3Keys())
            .as("S3 keys stay unfiltered for reversibility")
            .contains("team/sdv-element/2.0.0.zip");
        assertThat(kept.getVersion()).isEqualTo("1.0.0");
        assertThat(removed.getVersion()).isEqualTo("2.0.0");
    }
}
