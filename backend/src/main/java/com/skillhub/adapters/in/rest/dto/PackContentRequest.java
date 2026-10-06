package com.skillhub.adapters.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record PackContentRequest(
    @NotBlank String element,
    @NotBlank String versionConstraint
) {}
