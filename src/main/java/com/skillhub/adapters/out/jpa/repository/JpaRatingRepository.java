package com.skillhub.adapters.out.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface JpaRatingRepository extends JpaRepository<com.skillhub.adapters.out.jpa.entity.JpaRating, com.skillhub.adapters.out.jpa.entity.JpaRatingId> {
    Optional<com.skillhub.adapters.out.jpa.entity.JpaRating> findByElementIdAndUserId(UUID elementId, UUID userId);

    @Query("SELECT COALESCE(AVG(r.rating), 0.0) FROM JpaRating r WHERE r.elementId = :elementId")
    double avgRating(@Param("elementId") UUID elementId);

    long countByElementId(UUID elementId);
}
