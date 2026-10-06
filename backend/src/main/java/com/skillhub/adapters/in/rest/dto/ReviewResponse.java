package com.skillhub.adapters.in.rest.dto;

import com.skillhub.domain.model.Review;

public record ReviewResponse(String author, int rating, String text, String createdAt) {
    public static ReviewResponse from(Review r) {
        return new ReviewResponse(r.getUser().getDisplayName(), r.getRating(),
            r.getText(), r.getCreatedAt().toString());
    }
}
