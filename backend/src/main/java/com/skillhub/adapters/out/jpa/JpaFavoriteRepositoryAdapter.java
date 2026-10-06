package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.entity.JpaFavorite;
import com.skillhub.adapters.out.jpa.repository.JpaFavoriteRepository;
import com.skillhub.domain.model.Favorite;
import com.skillhub.domain.port.FavoriteRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaFavoriteRepositoryAdapter implements FavoriteRepositoryPort {

    private final JpaFavoriteRepository jpa;

    @Override
    @Transactional
    public Favorite save(Favorite favorite) {
        jpa.save(JpaFavorite.builder()
            .userId(favorite.userId())
            .elementId(favorite.elementId())
            .createdAt(favorite.createdAt())
            .build());
        return favorite;
    }

    @Override
    @Transactional
    public void delete(Favorite favorite) {
        jpa.findByUserIdAndElementId(favorite.userId(), favorite.elementId())
            .ifPresent(jpa::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Favorite> findByUserIdAndElementId(UUID userId, UUID elementId) {
        return jpa.findByUserIdAndElementId(userId, elementId)
            .map(f -> new Favorite(f.getUserId(), f.getElementId(), f.getCreatedAt()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Favorite> findAllByUserId(UUID userId) {
        return jpa.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(f -> new Favorite(f.getUserId(), f.getElementId(), f.getCreatedAt()))
            .toList();
    }
}
