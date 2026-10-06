package com.skillhub.application.dto;

public enum SortOrder {
    ASC, DESC;

    public static SortOrder fromString(String value) {
        return switch (value == null ? "" : value.toLowerCase()) {
            case "", "desc" -> DESC;
            case "asc" -> ASC;
            default -> throw new IllegalArgumentException("Unknown order: " + value);
        };
    }

    public String sql() {
        return this == ASC ? "ASC" : "DESC";
    }
}
