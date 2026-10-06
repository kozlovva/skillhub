package com.skillhub.domain.model;

public record AuditEntry(User user, String action, java.util.UUID elementId,
                         String detailsJson, java.time.Instant createdAt) {}
