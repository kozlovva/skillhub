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
class JpaElementRepositoryAdapterIT {

    @Autowired ElementRepositoryPort elements;
    @Autowired TeamRepositoryPort teams;
    @Autowired CategoryRepositoryPort categories;
    @Autowired UserRepositoryPort users;
    @Autowired ElementVersionRepositoryPort versions;
    @Autowired TeamMembershipPort membership;

    @Test
    void saveAndLoadElementGraph() {
        User author = users.save(User.builder()
            .ssoSubject("sub-1").email("a@b.c").displayName("Alice")
            .admin(true).createdAt(Instant.now()).build());
        Team team = teams.save(Team.builder()
            .slug("platform").name("Platform").createdAt(Instant.now()).build());
        Category cat = categories.findBySlug("dev")
            .orElseGet(() -> categories.save(Category.builder()
                .slug("dev").name("Разработка").build()));

        Element saved = elements.save(Element.builder()
            .slug("pdf-skill").type(ElementType.SKILL).name("PDF Skill").description("d")
            .team(team).category(cat).tags(new String[]{"pdf", "docs"})
            .visibility(Visibility.PUBLIC).author(author)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build());

        versions.save(ElementVersion.builder()
            .element(saved).version("1.0.0").status(VersionStatus.PUBLISHED)
            .changelog("initial").s3_key("platform/pdf-skill/1.0.0.zip")
            .sizeBytes(10).fileIndex("{\"files\":[]}")
            .publishedBy(author).createdAt(Instant.now()).publishedAt(Instant.now()).build());

        Element loaded = elements.findBySlug("pdf-skill").orElseThrow();
        assertThat(loaded.getTeam().getSlug()).isEqualTo("platform");
        assertThat(loaded.getTags()).containsExactly("pdf", "docs");
        assertThat(loaded.getAuthor().getDisplayName()).isEqualTo("Alice");

        assertThat(versions.findLatestPublished(loaded.getId()))
            .as("latest published version").isPresent();

        assertThat(membership.roleOf(team.getId(), author.getId()))
            .as("membership from raw SQL is empty until seeded").isEmpty();
    }
}
