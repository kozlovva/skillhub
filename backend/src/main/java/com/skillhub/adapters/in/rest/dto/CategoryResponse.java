package com.skillhub.adapters.in.rest.dto;

import com.skillhub.domain.model.Category;

public record CategoryResponse(String slug, String name, String parent, String icon) {
    public static CategoryResponse from(Category c) {
        return new CategoryResponse(c.getSlug(), c.getName(),
            c.getParent() == null ? null : c.getParent().getSlug(), c.getIcon());
    }
}
