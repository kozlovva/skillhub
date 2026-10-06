package com.skillhub.domain.port;

import com.skillhub.domain.model.Rating;

import java.util.Optional;
import java.util.UUID;

public interface RatingRepositoryPort {
    Rating save(Rating rating);
    Optional<Rating> findByElementIdAndUserId(UUID elementId, UUID userId);
    double avgRating(UUID elementId);
    long countByElementId(UUID elementId);
}
