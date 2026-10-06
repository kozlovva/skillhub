package com.skillhub.adapters.out.jpa.mapper;

import com.skillhub.adapters.out.jpa.entity.JpaReview;
import com.skillhub.domain.model.Review;

public final class ReviewJpaMapper {
    private ReviewJpaMapper() {}

    public static Review toDomain(JpaReview e) {
        return Review.builder()
            .id(e.getId())
            .element(ElementJpaMapper.toDomain(e.getElement()))
            .user(UserJpaMapper.toDomain(e.getUser()))
            .rating(e.getRating()).text(e.getText()).createdAt(e.getCreatedAt())
            .build();
    }

    public static JpaReview toEntity(Review d) {
        return JpaReview.builder()
            .id(d.getId())
            .element(ElementJpaMapper.toEntity(d.getElement()))
            .user(UserJpaMapper.toEntity(d.getUser()))
            .rating(d.getRating()).text(d.getText()).createdAt(d.getCreatedAt())
            .build();
    }
}
