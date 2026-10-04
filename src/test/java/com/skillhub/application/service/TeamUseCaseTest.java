package com.skillhub.application.service;

import com.skillhub.core.exception.ConflictException;
import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.core.exception.NotFoundException;
import com.skillhub.domain.model.*;
import com.skillhub.domain.port.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
        when(users.findBySsoSubject("m")).thenReturn(Optional.of(newMember));
        when(membership.roleOf(team.getId(), plainUser.getId()))
            .thenReturn(Optional.of(TeamRole.MEMBER));

        assertThatThrownBy(() -> useCase.addMember("ux", "m", "MEMBER", plainUser))
            .isInstanceOf(ForbiddenException.class);

        when(membership.roleOf(team.getId(), plainUser.getId()))
            .thenReturn(Optional.of(TeamRole.OWNER));
        TeamMembership added = useCase.addMember("ux", "m", "MEMBER", plainUser);
        assertThat(added.role()).isEqualTo(TeamRole.MEMBER);
    }

    @Test
    void unknownUserIsNotFound() {
        Team team = useCase.create("ux", "UX", creator);
        when(teams.findBySlug("ux")).thenReturn(Optional.of(team));
        when(membership.roleOf(team.getId(), plainUser.getId()))
            .thenReturn(Optional.of(TeamRole.OWNER));
        when(users.findBySsoSubject("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.addMember("ux", "ghost", "MEMBER", plainUser))
            .isInstanceOf(NotFoundException.class);
    }
}
