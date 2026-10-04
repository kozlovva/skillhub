package com.skillhub.domain.port;

import com.skillhub.domain.model.TeamMembership;
import com.skillhub.domain.model.TeamRole;
import com.skillhub.domain.model.UserTeamRole;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamMembershipPort {
    Optional<TeamRole> roleOf(UUID teamId, UUID userId);
    List<UserTeamRole> teamsOfUser(UUID userId);
    TeamMembership save(TeamMembership membership);
}
