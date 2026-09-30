package com.skillhub.adapters.out.jpa;

import com.skillhub.application.dto.SearchQuery;
import com.skillhub.application.dto.SearchQueryResult;
import com.skillhub.domain.model.*;
import com.skillhub.domain.port.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SearchAdapterIT {

    @Autowired SearchAdapter searchAdapter;
    @Autowired ElementRepositoryPort elements;
    @Autowired TeamRepositoryPort teams;
    @Autowired UserRepositoryPort users;
    @Autowired TeamMembershipPort membership;
    @Autowired JdbcTemplate jdbc;

    @Test
    void searchFindsByNameAndRespectsVisibility() {
        User author = users.save(User.builder()
            .ssoSubject("search-sub").email("s@b.c").displayName("S")
            .admin(false).createdAt(Instant.now()).build());
        Team team = teams.save(Team.builder()
            .slug("search-t").name("SearchT").createdAt(Instant.now()).build());
        membership.save(TeamMembership.builder()
            .teamId(team.getId()).userId(author.getId()).role(TeamRole.OWNER).build());

        jdbc.update("INSERT INTO categories (slug, name) VALUES ('dev', 'Разработка') " +
            "ON CONFLICT (slug) DO NOTHING");

        elements.save(Element.builder()
            .slug("pdf-docs-skill").type(ElementType.SKILL).name("PDF Skill")
            .description("Работа с PDF документами").team(team)
            .tags(new String[]{"pdf", "docs"}).visibility(Visibility.PUBLIC)
            .author(author).downloadsCount(0)
            .createdAt(Instant.now()).updatedAt(Instant.now()).build());
        elements.save(Element.builder()
            .slug("hidden-item").type(ElementType.SCRIPT).name("Hidden")
            .description("secret").team(team)
            .tags(new String[]{}).visibility(Visibility.TEAM)
            .author(author).downloadsCount(0)
            .createdAt(Instant.now()).updatedAt(Instant.now()).build());

        UUID outsider = UUID.randomUUID();
        SearchQueryResult publicOnly = searchAdapter.search(new SearchQuery(
            "PDF", null, null, outsider, 20, 0));
        assertThat(publicOnly.items()).extracting(Element::getSlug)
            .contains("pdf-docs-skill").doesNotContain("hidden-item");

        UUID memberId = author.getId();
        SearchQueryResult memberView = searchAdapter.search(new SearchQuery(
            "secret", null, null, memberId, 20, 0));
        assertThat(memberView.items()).extracting(Element::getSlug)
            .contains("hidden-item");

        SearchQueryResult byType = searchAdapter.search(new SearchQuery(
            "", "SKILL", null, outsider, 20, 0));
        assertThat(byType.facetsByType()).containsEntry("SKILL", 1L);
    }
}
