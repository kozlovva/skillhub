package com.skillhub.domain.model;

import java.util.UUID;

public record TeamMembership(UUID teamId, UUID userId, TeamRole role) {}
