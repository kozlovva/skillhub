package com.skillhub.adapters.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateElementRequest(
    @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]*$", message = "slug must be kebab-case")
    String slug,
    @NotBlank @Pattern(regexp = "SKILL|SCRIPT|AGENT|HOOK|PACK|OTHER", message = "type must be one of: SKILL, SCRIPT, AGENT, HOOK, PACK, OTHER")
    String type,
    @NotBlank String name,
    String description,
    @NotBlank String team,
    String category,
    String[] tags,
    @NotBlank @Pattern(regexp = "PUBLIC|TEAM", message = "visibility must be one of: PUBLIC, TEAM")
    String visibility
) {}
