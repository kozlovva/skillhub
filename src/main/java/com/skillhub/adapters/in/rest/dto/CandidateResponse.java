package com.skillhub.adapters.in.rest.dto;

import com.skillhub.domain.model.User;

import java.util.UUID;

public record CandidateResponse(UUID userId, String username, String displayName) {
    public static CandidateResponse from(User u) {
        return new CandidateResponse(u.getId(), u.getUsername(), u.getDisplayName());
    }
}
