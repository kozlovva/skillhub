package com.skillhub.adapters.out.jpa.repository;

import com.skillhub.adapters.out.jpa.entity.JpaReview;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaReviewRepository extends JpaRepository<JpaReview, UUID> {
    Optional<JpaReview> findByElementIdAndUserId(UUID elementId, UUID userId);
    List<JpaReview> findAllByElementIdOrderByCreatedAtDesc(UUID elementId);
}
