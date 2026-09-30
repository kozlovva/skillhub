package com.skillhub.adapters.in.rest.dto;

import com.skillhub.domain.model.PackContent;

import java.util.List;

public record PackResponse(String slug, List<ContentDto> contents) {

    public record ContentDto(String element, String version, String versionConstraint) {}

    public static PackResponse from(String slug, List<PackContent> contents) {
        return new PackResponse(slug, contents.stream()
            .map(c -> new ContentDto(c.getElement().getSlug(),
                c.getElement().getLatestVersion(),
                c.getVersionConstraint()))
            .toList());
    }
}
