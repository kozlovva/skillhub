package com.skillhub.adapters.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AddMemberRequest(
    @NotBlank String ssoSubject,
    @NotBlank @Pattern(regexp = "OWNER|MAINTAINER|MEMBER") String role
) {}
