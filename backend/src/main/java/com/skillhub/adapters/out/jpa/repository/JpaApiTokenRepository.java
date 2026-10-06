package com.skillhub.adapters.out.jpa.repository;

import com.skillhub.adapters.out.jpa.entity.JpaApiToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaApiTokenRepository extends JpaRepository<JpaApiToken, UUID> {
    Optional<JpaApiToken> findByTokenHash(String tokenHash);
    List<JpaApiToken> findAllByUserId(UUID userId);
    Optional<JpaApiToken> findByIdAndUserId(UUID id, UUID userId);
}
