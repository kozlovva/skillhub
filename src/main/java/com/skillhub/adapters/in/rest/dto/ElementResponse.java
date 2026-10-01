package com.skillhub.adapters.in.rest.dto;

import com.skillhub.domain.model.Element;

public record ElementResponse(
    String slug, String type, String name, String description,
    String team, String category, String[] tags, String visibility,
    String latestVersion, long downloadsCount
) {
    public static ElementResponse from(Element e) {
        return new ElementResponse(
            e.getSlug(), e.getType().name(), e.getName(), e.getDescription(),
            e.getTeam().getSlug(),
            e.getCategory() == null ? null : e.getCategory().getSlug(),
            e.getTags(), e.getVisibility().name(),
            e.getLatestVersion(), e.getDownloadsCount());
    }
}
