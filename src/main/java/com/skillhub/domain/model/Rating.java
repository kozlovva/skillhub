package com.skillhub.domain.model;

import java.util.UUID;

public record Rating(UUID elementId, UUID userId, int rating) {}
