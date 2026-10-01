package com.skillhub.adapters.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateTeamRequest(
    @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]*$", message = "slug must be kebab-case")
    String slug,
    @NotBlank String name
) {}
