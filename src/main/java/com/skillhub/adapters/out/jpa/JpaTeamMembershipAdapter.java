package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.repository.JpaTeamMemberRepository;
import com.skillhub.domain.model.TeamRole;
import com.skillhub.domain.port.TeamMembershipPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaTeamMembershipAdapter implements TeamMembershipPort {

    private final JpaTeamMemberRepository jpa;

    @Override
    public Optional<TeamRole> roleOf(UUID teamId, UUID userId) {
        return jpa.findRole(teamId, userId).map(TeamRole::valueOf);
    }
}
