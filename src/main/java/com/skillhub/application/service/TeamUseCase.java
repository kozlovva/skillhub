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

@Service
public class TeamUseCase {

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

    @Transactional
    public TeamMembership addMember(String teamSlug, String ssoSubject, String role, User actor) {
        Team team = teams.findBySlug(teamSlug)
            .orElseThrow(() -> new NotFoundException("Team not found: " + teamSlug));
        if (!actor.isAdmin()) {
            membership.roleOf(team.getId(), actor.getId())
                .filter(r -> r == TeamRole.OWNER)
                .orElseThrow(() -> new ForbiddenException("Only team OWNER can add members"));
        }
        User newMember = users.findBySsoSubject(ssoSubject)
            .orElseThrow(() -> new NotFoundException("User not found: " + ssoSubject));
        TeamRole teamRole = TeamRole.valueOf(role);
        membership.save(TeamMembership.builder()
            .teamId(team.getId()).userId(newMember.getId()).role(teamRole).build());
        return new TeamMembership(team.getId(), newMember.getId(), teamRole);
    }
}
