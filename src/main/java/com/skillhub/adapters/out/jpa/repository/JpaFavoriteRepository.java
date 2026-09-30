package com.skillhub.adapters.out.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface JpaFavoriteRepository extends JpaRepository<com.skillhub.adapters.out.jpa.entity.JpaFavorite, com.skillhub.adapters.out.jpa.entity.JpaFavoriteId> {
    Optional<com.skillhub.adapters.out.jpa.entity.JpaFavorite> findByUserIdAndElementId(UUID userId, UUID elementId);
}
