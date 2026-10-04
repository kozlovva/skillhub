package com.skillhub.adapters.in.rest.dto;

import com.skillhub.application.dto.RatingSummary;
import com.skillhub.domain.model.Element;

public record ElementResponse(
    String slug, String type, String name, String description,
    String team, String category, String[] tags, String visibility,
    String latestVersion, long downloadsCount,
    Double avgRating, Long ratingCount
) {
    public static ElementResponse from(Element e) {
        return from(e, null);
    }

    public static ElementResponse from(Element e, RatingSummary rating) {
        return new ElementResponse(
            e.getSlug(), e.getType().name(), e.getName(), e.getDescription(),
            e.getTeam() == null ? null : e.getTeam().getSlug(),
            e.getCategory() == null ? null : e.getCategory().getSlug(),
            e.getTags(), e.getVisibility().name(),
            e.getLatestVersion(), e.getDownloadsCount(),
            rating == null ? null : rating.avg(),
            rating == null ? null : rating.count());
    }
}
