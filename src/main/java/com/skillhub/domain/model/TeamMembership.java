package com.skillhub.domain.model;

import lombok.Builder;

import java.util.UUID;

@Builder
public record TeamMembership(UUID teamId, UUID userId, TeamRole role) {}
