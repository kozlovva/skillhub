package com.skillhub.application.dto;

import java.util.UUID;

public record SearchQuery(String q, String type, String category, UUID userId,
                          int limit, int offset) {}
