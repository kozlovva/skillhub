package com.skillhub.application.service;

import com.skillhub.core.exception.ConflictException;
import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.core.exception.NotFoundException;
import com.skillhub.core.exception.UnprocessableException;
import com.skillhub.domain.model.*;
import com.skillhub.domain.port.*;
import com.skillhub.domain.service.AccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ElementUseCaseTest {

    ElementRepositoryPort elements;
    TeamRepositoryPort teams;
    CategoryRepositoryPort categories;
    ClockPort clock;
    TeamMembershipPort membership;
    PackContentRepositoryPort packContents;
    ElementUseCase useCase;

    User owner = User.builder().id(UUID.randomUUID()).ssoSubject("s").email("e")
        .displayName("Owner").admin(false).createdAt(Instant.now()).build();
    Team team = Team.builder().id(UUID.randomUUID()).slug("platform").name("Platform")
        .createdAt(Instant.now()).build();

    @BeforeEach
    void setUp() {
        elements = mock(ElementRepositoryPort.class);
        teams = mock(TeamRepositoryPort.class);
        categories = mock(CategoryRepositoryPort.class);
        clock = mock(ClockPort.class);
        membership = mock(TeamMembershipPort.class);
        packContents = mock(PackContentRepositoryPort.class);
        when(packContents.findAllByElementId(any())).thenReturn(java.util.List.of());
        when(clock.now()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(teams.findBySlug("platform")).thenReturn(Optional.of(team));
        when(membership.roleOf(team.getId(), owner.getId()))
            .thenReturn(Optional.of(TeamRole.OWNER));
        when(elements.save(any())).thenAnswer(inv -> {
            Element e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });
        useCase = new ElementUseCase(elements, teams, categories,
            new AccessService(membership), clock, packContents, mock(AuditService.class));
    }

    ElementUseCase.CreateCommand cmd(String slug) {
        return new ElementUseCase.CreateCommand(slug, ElementType.SKILL, slug, "d",
            "platform", null, new String[0], Visibility.PUBLIC);
    }

    @Test
    void createByOwnerSucceeds() {
        Element created = useCase.create(cmd("my-skill"), owner);
        assertThat(created.getSlug()).isEqualTo("my-skill");
        assertThat(created.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void duplicateSlugConflicts() {
        when(elements.existsBySlug("my-skill")).thenReturn(true);
        assertThatThrownBy(() -> useCase.create(cmd("my-skill"), owner))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void memberCannotCreate() {
        when(membership.roleOf(team.getId(), owner.getId()))
            .thenReturn(Optional.of(TeamRole.MEMBER));
        assertThatThrownBy(() -> useCase.create(cmd("my-skill"), owner))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void unknownTeamIsNotFound() {
        when(teams.findBySlug("platform")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.create(cmd("my-skill"), owner))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createWithoutTeamSucceeds() {
        ElementUseCase.CreateCommand solo = new ElementUseCase.CreateCommand(
            "solo", ElementType.SCRIPT, "Solo", "d", null, null, new String[0], Visibility.PUBLIC);
        Element created = useCase.create(solo, owner);
        assertThat(created.getTeam()).isNull();
        verify(teams, never()).findBySlug(any());
    }

    @Test
    void teamVisibilityWithoutTeamIsUnprocessable() {
        ElementUseCase.CreateCommand solo = new ElementUseCase.CreateCommand(
            "solo", ElementType.SCRIPT, "Solo", "d", null, null, new String[0], Visibility.TEAM);
        assertThatThrownBy(() -> useCase.create(solo, owner))
            .isInstanceOf(UnprocessableException.class);
    }

    @Test
    void getHiddenElementForbiddenForOutsider() {
        Element hidden = Element.builder().id(UUID.randomUUID()).slug("h")
            .type(ElementType.SKILL).name("h").description("").team(team)
            .tags(new String[0]).visibility(Visibility.TEAM).author(owner)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(elements.findBySlug("h")).thenReturn(Optional.of(hidden));
        assertThatThrownBy(() -> useCase.getBySlug("h", null))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deletedElementIsNotFound() {
        Element deleted = Element.builder().id(UUID.randomUUID()).slug("gone")
            .type(ElementType.SKILL).name("gone").description("").team(team)
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now())
            .deletedAt(Instant.parse("2026-02-01T00:00:00Z")).build();
        when(elements.findBySlug("gone")).thenReturn(Optional.of(deleted));
        assertThatThrownBy(() -> useCase.getBySlug("gone", owner))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void ownerDeletesElement() {
        Element e = Element.builder().id(UUID.randomUUID()).slug("my-skill")
            .type(ElementType.SKILL).name("my-skill").description("").team(team)
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(elements.findBySlug("my-skill")).thenReturn(Optional.of(e));
        useCase.delete("my-skill", owner);
        assertThat(e.getDeletedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        verify(elements).save(e);
    }

    @Test
    void maintainerCannotDeleteElement() {
        Element e = Element.builder().id(UUID.randomUUID()).slug("my-skill")
            .type(ElementType.SKILL).name("my-skill").description("").team(team)
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(elements.findBySlug("my-skill")).thenReturn(Optional.of(e));
        when(membership.roleOf(team.getId(), owner.getId()))
            .thenReturn(Optional.of(TeamRole.MAINTAINER));
        assertThatThrownBy(() -> useCase.delete("my-skill", owner))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void elementInPackCannotBeDeleted() {
        Element e = Element.builder().id(UUID.randomUUID()).slug("my-skill")
            .type(ElementType.SKILL).name("my-skill").description("").team(team)
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(elements.findBySlug("my-skill")).thenReturn(Optional.of(e));
        Element pack = Element.builder().id(UUID.randomUUID()).slug("the-pack")
            .type(ElementType.PACK).name("The Pack").description("")
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(packContents.findAllByElementId(e.getId())).thenReturn(java.util.List.of(
            PackContent.builder().packElement(pack).element(e).versionConstraint("latest").build()));
        assertThatThrownBy(() -> useCase.delete("my-skill", owner))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("the-pack");
    }

    @Test
    void alreadyDeletedElementIsNotFound() {
        Element e = Element.builder().id(UUID.randomUUID()).slug("my-skill")
            .type(ElementType.SKILL).name("my-skill").description("").team(team)
            .tags(new String[0]).visibility(Visibility.PUBLIC).author(owner)
            .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now())
            .deletedAt(Instant.parse("2026-02-01T00:00:00Z")).build();
        when(elements.findBySlug("my-skill")).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> useCase.delete("my-skill", owner))
            .isInstanceOf(NotFoundException.class);
    }
}
