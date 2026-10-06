package com.skillhub.adapters.in.rest.dto;

import com.skillhub.domain.model.TeamMember;

import java.util.UUID;

public record TeamMemberResponse(UUID userId, String username, String displayName, String role) {

    public static TeamMemberResponse from(TeamMember m) {
        return new TeamMemberResponse(m.userId(), m.username(), m.displayName(), m.role().name());
    }
}
