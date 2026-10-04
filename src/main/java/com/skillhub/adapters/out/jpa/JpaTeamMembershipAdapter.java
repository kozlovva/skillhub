package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.entity.JpaTeamMember;
import com.skillhub.adapters.out.jpa.repository.JpaTeamMemberRepository;
import com.skillhub.adapters.out.jpa.repository.JpaTeamRepository;
import com.skillhub.adapters.out.jpa.repository.JpaUserRepository;
import com.skillhub.domain.model.TeamMember;
import com.skillhub.domain.model.TeamMembership;
import com.skillhub.domain.model.TeamRole;
import com.skillhub.domain.model.UserTeamRole;
import com.skillhub.domain.port.TeamMembershipPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaTeamMembershipAdapter implements TeamMembershipPort {

    private final JpaTeamMemberRepository jpa;
    private final JpaTeamRepository teamRepository;
    private final JpaUserRepository userRepository;

    @Override
    public Optional<TeamRole> roleOf(UUID teamId, UUID userId) {
        return jpa.findRole(teamId, userId).map(TeamRole::valueOf);
    }

    @Override
    public List<UserTeamRole> teamsOfUser(UUID userId) {
        return jpa.findByUserId(userId).stream()
            .map(m -> new UserTeamRole(m.getTeam().getSlug(), m.getTeam().getName(),
                TeamRole.valueOf(m.getRole())))
            .toList();
    }

    @Override
    @Transactional
    public TeamMembership save(TeamMembership m) {
        var teamRef = teamRepository.getReferenceById(m.teamId());
        var userRef = userRepository.getReferenceById(m.userId());
        jpa.save(JpaTeamMember.builder().team(teamRef).user(userRef).role(m.role().name()).build());
        return m;
    }

    @Override
    public List<TeamMember> membersOf(UUID teamId) {
        return jpa.findByTeamId(teamId).stream()
            .map(m -> new TeamMember(m.getUser().getId(), m.getUser().getUsername(),
                m.getUser().getDisplayName(), TeamRole.valueOf(m.getRole())))
            .toList();
    }

    @Override
    @Transactional
    public void delete(UUID teamId, UUID userId) {
        jpa.deleteByTeamIdAndUserId(teamId, userId);
    }
}
