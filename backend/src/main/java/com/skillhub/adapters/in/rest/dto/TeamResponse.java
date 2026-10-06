package com.skillhub.adapters.in.rest.dto;

import com.skillhub.domain.model.Team;

public record TeamResponse(String slug, String name) {
    public static TeamResponse from(Team t) {
        return new TeamResponse(t.getSlug(), t.getName());
    }
}
