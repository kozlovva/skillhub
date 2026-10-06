package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.entity.JpaRating;
import com.skillhub.adapters.out.jpa.repository.JpaRatingRepository;
import com.skillhub.domain.model.Rating;
import com.skillhub.domain.port.RatingRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaRatingRepositoryAdapter implements RatingRepositoryPort {

    private final JpaRatingRepository jpa;

    @Override
    @Transactional
    public Rating save(Rating rating) {
        jpa.save(JpaRating.builder()
            .elementId(rating.elementId())
            .userId(rating.userId())
            .rating(rating.rating())
            .build());
        return rating;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Rating> findByElementIdAndUserId(UUID elementId, UUID userId) {
        return jpa.findByElementIdAndUserId(elementId, userId)
            .map(r -> new Rating(r.getElementId(), r.getUserId(), r.getRating()));
    }

    @Override
    @Transactional(readOnly = true)
    public double avgRating(UUID elementId) {
        return jpa.avgRating(elementId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByElementId(UUID elementId) {
        return jpa.countByElementId(elementId);
    }
}
