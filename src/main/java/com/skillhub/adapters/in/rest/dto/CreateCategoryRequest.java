package com.skillhub.adapters.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCategoryRequest(
    @NotBlank String slug,
    @NotBlank String name,
    String parentSlug,
    String icon
) {}
