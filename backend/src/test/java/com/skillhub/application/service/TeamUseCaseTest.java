package com.skillhub.application.service;

import com.skillhub.core.exception.ConflictException;
import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.core.exception.NotFoundException;
import com.skillhub.domain.model.*;
import com.skillhub.domain.port.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TeamUseCaseTest {

    TeamRepositoryPort teams;
    TeamMembershipPort membership;
    UserRepositoryPort users;
    ClockPort clock;
    TeamUseCase useCase;

    User creator = User.builder().id(UUID.randomUUID()).ssoSubject("s").email("e")
        .displayName("Creator").admin(true).createdAt(Instant.now()).build();

    User plainUser = User.builder().id(UUID.randomUUID()).ssoSubject("plain").email("p")
        .displayName("Plain").admin(false).createdAt(Instant.now()).build();

    @BeforeEach
    void setUp() {
        teams = mock(TeamRepositoryPort.class);
        membership = mock(TeamMembershipPort.class);
        users = mock(UserRepositoryPort.class);
        clock = mock(ClockPort.class);
        when(clock.now()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(teams.save(any())).thenAnswer(inv -> {
            Team t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        useCase = new TeamUseCase(teams, membership, users, clock);
    }

    @Test
    void createTeamMakesCreatorOwner() {
        Team team = useCase.create("design", "Design", creator);
        assertThat(team.getSlug()).isEqualTo("design");
        org.mockito.Mockito.verify(membership).save(TeamMembership.builder()
            .teamId(team.getId()).userId(creator.getId()).role(TeamRole.OWNER).build());
    }

    @Test
    void onlyAdminCreatesTeam() {
        assertThatThrownBy(() -> useCase.create("design", "Design", plainUser))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void duplicateTeamConflicts() {
        when(teams.findBySlug("design")).thenReturn(Optional.of(Team.builder().build()));
        assertThatThrownBy(() -> useCase.create("design", "Design", creator))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void onlyOwnerAddsMember() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));
        User newMember = User.builder().id(UUID.randomUUID()).ssoSubject("m")
            .email("m").displayName("M").admin(false).createdAt(Instant.now()).build();
        when(users.findById(newMember.getId())).thenReturn(Optional.of(newMember));
        when(membership.roleOf(team.getId(), plainUser.getId()))
            .thenReturn(Optional.of(TeamRole.MEMBER));

        assertThatThrownBy(() -> useCase.addMember("ux", newMember.getId(), "MEMBER", plainUser))
            .isInstanceOf(ForbiddenException.class);

        when(membership.roleOf(team.getId(), plainUser.getId()))
            .thenReturn(Optional.of(TeamRole.OWNER));
        TeamMembership added = useCase.addMember("ux", newMember.getId(), "MEMBER", plainUser);
        assertThat(added.role()).isEqualTo(TeamRole.MEMBER);
    }

    @Test
    void maintainerCannotAddMember() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));
        when(membership.roleOf(team.getId(), plainUser.getId()))
            .thenReturn(Optional.of(TeamRole.MAINTAINER));
        assertThatThrownBy(() -> useCase.addMember("ux", UUID.randomUUID(), "MEMBER", plainUser))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void maintainerCannotSearchCandidates() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));
        when(membership.roleOf(team.getId(), plainUser.getId()))
            .thenReturn(Optional.of(TeamRole.MAINTAINER));
        assertThatThrownBy(() -> useCase.searchCandidates("ux", "pet", plainUser))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void unknownUserIsNotFound() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));
        when(membership.roleOf(team.getId(), plainUser.getId()))
            .thenReturn(Optional.of(TeamRole.OWNER));
        UUID ghost = UUID.randomUUID();
        when(users.findById(ghost)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.addMember("ux", ghost, "MEMBER", plainUser))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void candidatesRequireOwner() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));
        when(membership.roleOf(team.getId(), plainUser.getId()))
            .thenReturn(Optional.of(TeamRole.MEMBER));

        assertThatThrownBy(() -> useCase.searchCandidates("ux", "pet", plainUser))
            .isInstanceOf(ForbiddenException.class);

        when(membership.roleOf(team.getId(), plainUser.getId()))
            .thenReturn(Optional.of(TeamRole.OWNER));
        when(users.searchCandidates("pet", team.getId(), 10)).thenReturn(List.of());
        assertThat(useCase.searchCandidates("ux", "pet", plainUser)).isEmpty();
    }

    @Test
    void candidatesShortQueryReturnsEmptyWithoutSearch() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));

        assertThat(useCase.searchCandidates("ux", "p", creator)).isEmpty();
        assertThat(useCase.searchCandidates("ux", "   ", creator)).isEmpty();
        verifyNoInteractions(users);
    }

    @Test
    void candidatesTrimQueryAndSearch() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));
        User found = User.builder().id(UUID.randomUUID()).ssoSubject("p")
            .username("vpetrov").email("p").displayName("Petrov").admin(false)
            .createdAt(Instant.now()).build();
        when(users.searchCandidates("pet", team.getId(), 10)).thenReturn(List.of(found));

        List<User> result = useCase.searchCandidates("ux", "  pet  ", creator);

        assertThat(result).containsExactly(found);
        verify(users).searchCandidates("pet", team.getId(), 10);
    }

    @Test
    void candidatesTeamNotFound() {
        when(teams.findBySlug("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.searchCandidates("ghost", "pet", creator))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void membersVisibleToTeamMemberAndAdmin() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));
        when(membership.roleOf(team.getId(), plainUser.getId()))
            .thenReturn(Optional.of(TeamRole.MEMBER));
        List<TeamMember> roster = List.of(
            new TeamMember(creator.getId(), "admin", "Admin", TeamRole.OWNER));
        when(membership.membersOf(team.getId())).thenReturn(roster);

        assertThat(useCase.members("ux", creator)).isEqualTo(roster);
        assertThat(useCase.members("ux", plainUser)).isEqualTo(roster);
    }

    @Test
    void membersHiddenFromOutsider() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));
        when(membership.roleOf(team.getId(), plainUser.getId()))
            .thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.members("ux", plainUser))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void membersTeamNotFound() {
        when(teams.findBySlug("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.members("ghost", creator))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void ownerChangesRole() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));
        User member = User.builder().id(UUID.randomUUID()).ssoSubject("m")
            .username("member").email("m").displayName("Member").admin(false)
            .createdAt(Instant.now()).build();
        when(membership.membersOf(team.getId())).thenReturn(List.of(
            new TeamMember(creator.getId(), "admin", "Admin", TeamRole.OWNER),
            new TeamMember(member.getId(), "member", "Member", TeamRole.MEMBER)));

        TeamMember updated = useCase.changeRole("ux", member.getId(), "MAINTAINER", creator);

        assertThat(updated.role()).isEqualTo(TeamRole.MAINTAINER);
        assertThat(updated.username()).isEqualTo("member");
        org.mockito.Mockito.verify(membership).save(TeamMembership.builder()
            .teamId(team.getId()).userId(member.getId()).role(TeamRole.MAINTAINER).build());
    }

    @Test
    void maintainerCannotChangeRole() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));
        when(membership.roleOf(team.getId(), plainUser.getId()))
            .thenReturn(Optional.of(TeamRole.MAINTAINER));
        assertThatThrownBy(() -> useCase.changeRole("ux", creator.getId(), "MEMBER", plainUser))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void cannotDemoteLastOwner() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));
        when(membership.membersOf(team.getId())).thenReturn(List.of(
            new TeamMember(creator.getId(), "admin", "Admin", TeamRole.OWNER)));
        assertThatThrownBy(() -> useCase.changeRole("ux", creator.getId(), "MEMBER", creator))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void changeRoleUnknownMemberIsNotFound() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));
        when(membership.membersOf(team.getId())).thenReturn(List.of(
            new TeamMember(creator.getId(), "admin", "Admin", TeamRole.OWNER)));
        assertThatThrownBy(() -> useCase.changeRole("ux", UUID.randomUUID(), "MEMBER", creator))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void ownerRemovesMember() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));
        User member = User.builder().id(UUID.randomUUID()).ssoSubject("m")
            .username("member").email("m").displayName("Member").admin(false)
            .createdAt(Instant.now()).build();
        when(membership.membersOf(team.getId())).thenReturn(List.of(
            new TeamMember(creator.getId(), "admin", "Admin", TeamRole.OWNER),
            new TeamMember(member.getId(), "member", "Member", TeamRole.MEMBER)));

        useCase.removeMember("ux", member.getId(), creator);

        org.mockito.Mockito.verify(membership).delete(team.getId(), member.getId());
    }

    @Test
    void cannotRemoveLastOwner() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));
        when(membership.membersOf(team.getId())).thenReturn(List.of(
            new TeamMember(creator.getId(), "admin", "Admin", TeamRole.OWNER)));
        assertThatThrownBy(() -> useCase.removeMember("ux", creator.getId(), creator))
            .isInstanceOf(ConflictException.class);
        org.mockito.Mockito.verify(membership, org.mockito.Mockito.never())
            .delete(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
