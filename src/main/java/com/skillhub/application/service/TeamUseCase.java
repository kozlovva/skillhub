package com.skillhub.application.service;

import com.skillhub.core.exception.ConflictException;
import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.core.exception.NotFoundException;
import com.skillhub.domain.model.*;
import com.skillhub.domain.port.ClockPort;
import com.skillhub.domain.port.TeamMembershipPort;
import com.skillhub.domain.port.TeamRepositoryPort;
import com.skillhub.domain.port.UserRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TeamUseCase {

    private static final int CANDIDATES_LIMIT = 10;

    private final TeamRepositoryPort teams;
    private final TeamMembershipPort membership;
    private final UserRepositoryPort users;
    private final ClockPort clock;

    public TeamUseCase(TeamRepositoryPort teams, TeamMembershipPort membership,
                       UserRepositoryPort users, ClockPort clock) {
        this.teams = teams;
        this.membership = membership;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public Team create(String slug, String name, User creator) {
        if (!creator.isAdmin()) {
            throw new ForbiddenException("Only admin can create teams");
        }
        if (teams.findBySlug(slug).isPresent()) {
            throw new ConflictException("Team already exists: " + slug);
        }
        Team team = teams.save(Team.builder()
            .slug(slug).name(name).createdAt(clock.now()).build());
        membership.save(TeamMembership.builder()
            .teamId(team.getId()).userId(creator.getId()).role(TeamRole.OWNER).build());
        return team;
    }

    @Transactional(readOnly = true)
    public List<Team> list() {
        return teams.findAll();
    }

    @Transactional(readOnly = true)
    public List<User> searchCandidates(String teamSlug, String query, User actor) {
        Team team = teams.findBySlug(teamSlug)
            .orElseThrow(() -> new NotFoundException("Team not found: " + teamSlug));
        requireOwnerOrAdmin(team, actor);
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }
        return users.searchCandidates(query.trim(), team.getId(), CANDIDATES_LIMIT);
    }

    @Transactional
    public TeamMembership addMember(String teamSlug, UUID userId, String role, User actor) {
        Team team = teams.findBySlug(teamSlug)
            .orElseThrow(() -> new NotFoundException("Team not found: " + teamSlug));
        requireOwnerOrAdmin(team, actor);
        User newMember = users.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found: " + userId));
        TeamRole teamRole = TeamRole.valueOf(role);
        membership.save(TeamMembership.builder()
            .teamId(team.getId()).userId(newMember.getId()).role(teamRole).build());
        return new TeamMembership(team.getId(), newMember.getId(), teamRole);
    }

    private void requireOwnerOrAdmin(Team team, User actor) {
        if (actor.isAdmin()) {
            return;
        }
        membership.roleOf(team.getId(), actor.getId())
            .filter(r -> r == TeamRole.OWNER)
            .orElseThrow(() -> new ForbiddenException("Only team OWNER can add members"));
    }
}
