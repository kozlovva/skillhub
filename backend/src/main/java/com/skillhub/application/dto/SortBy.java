package com.skillhub.application.dto;

public enum SortBy {
    RELEVANCE, DOWNLOADS, RATING, PUBLISHED;

    public static SortBy fromString(String value) {
        return switch (value == null ? "" : value.toLowerCase()) {
            case "", "relevance" -> RELEVANCE;
            case "downloads" -> DOWNLOADS;
            case "rating" -> RATING;
            case "published" -> PUBLISHED;
            default -> throw new IllegalArgumentException("Unknown sort: " + value);
        };
    }
}
