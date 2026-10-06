package com.skillhub.domain.model;

import java.util.UUID;

public record TeamMember(UUID userId, String username, String displayName, TeamRole role) {
}
