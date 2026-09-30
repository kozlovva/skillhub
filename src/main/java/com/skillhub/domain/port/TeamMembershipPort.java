package com.skillhub.domain.port;

import com.skillhub.domain.model.TeamRole;

import java.util.Optional;
import java.util.UUID;

public interface TeamMembershipPort {
    Optional<TeamRole> roleOf(UUID teamId, UUID userId);
}
