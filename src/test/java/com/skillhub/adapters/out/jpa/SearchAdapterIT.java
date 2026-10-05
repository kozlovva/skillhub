package com.skillhub.adapters.out.jpa;

import com.skillhub.application.dto.SearchQuery;
import com.skillhub.application.dto.SearchQueryResult;
import com.skillhub.application.dto.SortBy;
import com.skillhub.application.dto.SortOrder;
import com.skillhub.domain.model.*;
import com.skillhub.domain.port.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
    @PersistenceContext EntityManager em;

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
            "PDF", null, null, outsider, false, 20, 0, SortBy.RELEVANCE, SortOrder.DESC));
        assertThat(publicOnly.items()).extracting(Element::getSlug)
            .contains("pdf-docs-skill").doesNotContain("hidden-item");

        UUID memberId = author.getId();
        SearchQueryResult memberView = searchAdapter.search(new SearchQuery(
            "secret", null, null, memberId, false, 20, 0, SortBy.RELEVANCE, SortOrder.DESC));
        assertThat(memberView.items()).extracting(Element::getSlug)
            .contains("hidden-item");

        SearchQueryResult byType = searchAdapter.search(new SearchQuery(
            "", "SKILL", null, outsider, false, 20, 0, SortBy.RELEVANCE, SortOrder.DESC));
        assertThat(byType.items()).extracting(Element::getSlug).contains("pdf-docs-skill");
        assertThat(byType.facetsByType()).containsKey("SKILL");
    }

    @Test
    void adminSeesTeamElementsFromOtherTeams() {
        User author = users.save(User.builder()
            .ssoSubject("adm-author").email("a@b.c").displayName("A")
            .admin(false).createdAt(Instant.now()).build());
        User admin = users.save(User.builder()
            .ssoSubject("adm-admin").email("admin@b.c").displayName("Admin")
            .admin(true).createdAt(Instant.now()).build());
        Team team = teams.save(Team.builder()
            .slug("adm-team").name("AdmTeam").createdAt(Instant.now()).build());

        elements.save(Element.builder()
            .slug("adm-hidden").type(ElementType.SKILL).name("AdmHiddenUnique")
            .description("team only").team(team)
            .tags(new String[]{}).visibility(Visibility.TEAM)
            .author(author).downloadsCount(0)
            .createdAt(Instant.now()).updatedAt(Instant.now()).build());

        SearchQueryResult nonAdmin = searchAdapter.search(new SearchQuery(
            "AdmHiddenUnique", null, null, UUID.randomUUID(), false, 20, 0,
            SortBy.RELEVANCE, SortOrder.DESC));
        assertThat(nonAdmin.items()).extracting(Element::getSlug)
            .doesNotContain("adm-hidden");

        SearchQueryResult adminView = searchAdapter.search(new SearchQuery(
            "AdmHiddenUnique", null, null, admin.getId(), true, 20, 0,
            SortBy.RELEVANCE, SortOrder.DESC));
        assertThat(adminView.items()).extracting(Element::getSlug)
            .contains("adm-hidden");
    }

    @Test
    void russianStemmingMatchesInflectedForms() {
        User author = users.save(User.builder()
            .ssoSubject("ru-author").email("ru@b.c").displayName("RU")
            .admin(false).createdAt(Instant.now()).build());
        Team team = teams.save(Team.builder()
            .slug("ru-team").name("RuTeam").createdAt(Instant.now()).build());

        elements.save(Element.builder()
            .slug("ru-doc-skill").type(ElementType.SKILL).name("Русский документ")
            .description("Полезный навык").team(team)
            .tags(new String[]{}).visibility(Visibility.PUBLIC)
            .author(author).downloadsCount(0)
            .createdAt(Instant.now()).updatedAt(Instant.now()).build());

        SearchQueryResult result = searchAdapter.search(new SearchQuery(
            "документы", null, null, author.getId(), false, 20, 0, SortBy.RELEVANCE, SortOrder.DESC));
        assertThat(result.items()).extracting(Element::getSlug).contains("ru-doc-skill");
    }

    @Test
    void changelogIsSearchable() {
        User author = users.save(User.builder()
            .ssoSubject("cl-author").email("cl@b.c").displayName("CL")
            .admin(false).createdAt(Instant.now()).build());
        Team team = teams.save(Team.builder()
            .slug("cl-team").name("ClTeam").createdAt(Instant.now()).build());

        elements.save(Element.builder()
            .slug("cl-skill").type(ElementType.SKILL).name("Сервер")
            .description("Описание").team(team)
            .tags(new String[]{}).visibility(Visibility.PUBLIC)
            .author(author).downloadsCount(0)
            .latestChangelog("Настройка библиотека")
            .createdAt(Instant.now()).updatedAt(Instant.now()).build());

        SearchQueryResult result = searchAdapter.search(new SearchQuery(
            "библиотеки", null, null, author.getId(), false, 20, 0, SortBy.RELEVANCE, SortOrder.DESC));
        assertThat(result.items()).extracting(Element::getSlug).contains("cl-skill");
    }

    private User newAuthor(String subject) {
        User user = users.save(User.builder()
            .ssoSubject(subject).email(subject + "@b.c").displayName(subject)
            .admin(false).createdAt(Instant.now()).build());
        em.flush();
        return user;
    }

    private Team newTeam(String slug) {
        Team team = teams.save(Team.builder()
            .slug(slug).name(slug).createdAt(Instant.now()).build());
        em.flush();
        return team;
    }

    private Element newElement(String slug, String name, ElementType type, Team team, User author) {
        Element element = elements.save(Element.builder()
            .slug(slug).type(type).name(name)
            .description("Описание " + slug).team(team)
            .tags(new String[]{}).visibility(Visibility.PUBLIC)
            .author(author).downloadsCount(0)
            .createdAt(Instant.now()).updatedAt(Instant.now()).build());
        em.flush();
        return element;
    }

    private void cleanCatalog() {
        jdbc.update("DELETE FROM elements");
    }

    @Test
    void sortByRatingPutsUnratedLastAndRespectsType() {
        cleanCatalog();
        User author = newAuthor("sort-r-author");
        Team team = newTeam("sort-r-team");

        Element top = newElement("sort-top", "Топ элемент", ElementType.SKILL, team, author);
        Element mid = newElement("sort-mid", "Средний элемент", ElementType.SKILL, team, author);
        Element unrated = newElement("sort-unrated", "Без оценок", ElementType.SKILL, team, author);
        Element script = newElement("sort-script", "Скрипт элемент", ElementType.SCRIPT, team, author);

        jdbc.update("INSERT INTO ratings (element_id, user_id, rating) VALUES (?, ?, 5)",
            top.getId(), author.getId());
        jdbc.update("INSERT INTO ratings (element_id, user_id, rating) VALUES (?, ?, 3)",
            mid.getId(), author.getId());

        SearchQueryResult desc = searchAdapter.search(new SearchQuery(
            "", "SKILL", null, author.getId(), false, 20, 0, SortBy.RATING, SortOrder.DESC));
        assertThat(desc.items()).extracting(Element::getSlug)
            .containsExactly("sort-top", "sort-mid", "sort-unrated");

        SearchQueryResult asc = searchAdapter.search(new SearchQuery(
            "", "SKILL", null, author.getId(), false, 20, 0, SortBy.RATING, SortOrder.ASC));
        assertThat(asc.items()).extracting(Element::getSlug)
            .containsExactly("sort-mid", "sort-top", "sort-unrated");

        SearchQueryResult onlyScripts = searchAdapter.search(new SearchQuery(
            "", "SCRIPT", null, author.getId(), false, 20, 0, SortBy.RATING, SortOrder.DESC));
        assertThat(onlyScripts.items()).extracting(Element::getSlug)
            .containsExactly("sort-script");
    }

    @Test
    void sortByPublishedUsesLatestPublishedVersionOnly() throws Exception {
        cleanCatalog();
        User author = newAuthor("sort-p-author");
        Team team = newTeam("sort-p-team");

        Element older = newElement("sort-p-older", "Старее", ElementType.SKILL, team, author);
        Element newer = newElement("sort-p-newer", "Новее", ElementType.SKILL, team, author);
        Element neverPublished = newElement("sort-p-none", "Не публиковался", ElementType.SKILL, team, author);

        jdbc.update("""
            INSERT INTO element_versions (element_id, version, status, s3_key, size_bytes, file_index, published_by, published_at)
            VALUES (?, '1.0.0', 'PUBLISHED', 'k', 1, '{}'::jsonb, ?, now() - interval '2 days')
            """, older.getId(), author.getId());
        jdbc.update("""
            INSERT INTO element_versions (element_id, version, status, s3_key, size_bytes, file_index, published_by, published_at)
            VALUES (?, '1.0.0', 'PUBLISHED', 'k', 1, '{}'::jsonb, ?, now() - interval '1 day')
            """, newer.getId(), author.getId());
        jdbc.update("""
            INSERT INTO element_versions (element_id, version, status, s3_key, size_bytes, file_index, published_by, published_at)
            VALUES (?, '2.0.0', 'DRAFT', 'k', 1, '{}'::jsonb, ?, now())
            """, older.getId(), author.getId());

        SearchQueryResult desc = searchAdapter.search(new SearchQuery(
            "", "SKILL", null, author.getId(), false, 20, 0, SortBy.PUBLISHED, SortOrder.DESC));
        assertThat(desc.items()).extracting(Element::getSlug)
            .containsExactly("sort-p-newer", "sort-p-older", "sort-p-none");
    }

    @Test
    void sortByPublishedIgnoresDeletedVersions() {
        cleanCatalog();
        User author = newAuthor("sort-pd-author");
        Team team = newTeam("sort-pd-team");

        Element withDeletedNewest = newElement(
            "sort-pd-a", "A", ElementType.SKILL, team, author);
        Element other = newElement("sort-pd-b", "B", ElementType.SKILL, team, author);

        jdbc.update("""
            INSERT INTO element_versions (element_id, version, status, s3_key, size_bytes, file_index, published_by, published_at)
            VALUES (?, '1.0.0', 'PUBLISHED', 'k', 1, '{}'::jsonb, ?, now() - interval '3 days')
            """, withDeletedNewest.getId(), author.getId());
        jdbc.update("""
            INSERT INTO element_versions (element_id, version, status, s3_key, size_bytes, file_index, published_by, published_at, deleted_at)
            VALUES (?, '2.0.0', 'PUBLISHED', 'k', 1, '{}'::jsonb, ?, now() - interval '1 day', now() - interval '1 day')
            """, withDeletedNewest.getId(), author.getId());
        jdbc.update("""
            INSERT INTO element_versions (element_id, version, status, s3_key, size_bytes, file_index, published_by, published_at)
            VALUES (?, '1.0.0', 'PUBLISHED', 'k', 1, '{}'::jsonb, ?, now() - interval '2 days')
            """, other.getId(), author.getId());

        SearchQueryResult desc = searchAdapter.search(new SearchQuery(
            "", "SKILL", null, author.getId(), false, 20, 0, SortBy.PUBLISHED, SortOrder.DESC));
        assertThat(desc.items()).extracting(Element::getSlug)
            .containsExactly("sort-pd-b", "sort-pd-a");
    }
}
