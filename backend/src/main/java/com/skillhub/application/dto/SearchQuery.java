package com.skillhub.application.dto;

import java.util.UUID;

public record SearchQuery(String q, String type, String category, UUID userId,
                          boolean admin, int limit, int offset,
                          SortBy sortBy, SortOrder sortOrder) {}
