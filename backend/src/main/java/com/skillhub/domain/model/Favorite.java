package com.skillhub.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Favorite(UUID userId, UUID elementId, Instant createdAt) {}
