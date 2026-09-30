package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.entity.JpaTeamMember;
import com.skillhub.adapters.out.jpa.repository.JpaTeamMemberRepository;
import com.skillhub.adapters.out.jpa.repository.JpaTeamRepository;
import com.skillhub.adapters.out.jpa.repository.JpaUserRepository;
import com.skillhub.domain.model.TeamMembership;
import com.skillhub.domain.model.TeamRole;
import com.skillhub.domain.port.TeamMembershipPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
    public TeamMembership save(TeamMembership m) {
        var teamRef = teamRepository.getReferenceById(m.teamId());
        var userRef = userRepository.getReferenceById(m.userId());
        jpa.save(JpaTeamMember.builder().team(teamRef).user(userRef).role(m.role().name()).build());
        return m;
    }
}
