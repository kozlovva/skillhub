package com.skillhub.domain.port;

import com.skillhub.domain.model.Favorite;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FavoriteRepositoryPort {
    Favorite save(Favorite favorite);
    void delete(Favorite favorite);
    Optional<Favorite> findByUserIdAndElementId(UUID userId, UUID elementId);
    List<Favorite> findAllByUserId(UUID userId);
}
