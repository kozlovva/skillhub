package com.skillhub.domain.port;

import com.skillhub.domain.model.ApiToken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiTokenRepositoryPort {
    ApiToken save(ApiToken token);
    Optional<ApiToken> findByTokenHash(String tokenHash);
    List<ApiToken> findByUserId(UUID userId);
}
