package com.skillhub.domain.port;

import com.skillhub.domain.model.ApiToken;

import java.util.Optional;

public interface ApiTokenRepositoryPort {
    ApiToken save(ApiToken token);
    Optional<ApiToken> findByTokenHash(String tokenHash);
}
