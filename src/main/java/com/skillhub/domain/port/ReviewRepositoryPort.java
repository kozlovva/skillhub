package com.skillhub.domain.port;

import com.skillhub.domain.model.Review;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepositoryPort {
    Review save(Review review);
    Optional<Review> findByElementIdAndUserId(UUID elementId, UUID userId);
    List<Review> findAllByElementIdOrderByCreatedAtDesc(UUID elementId);
}
