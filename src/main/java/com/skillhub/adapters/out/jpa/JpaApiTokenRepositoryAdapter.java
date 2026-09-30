package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.entity.JpaApiToken;
import com.skillhub.adapters.out.jpa.entity.JpaUser;
import com.skillhub.adapters.out.jpa.mapper.UserJpaMapper;
import com.skillhub.adapters.out.jpa.repository.JpaApiTokenRepository;
import com.skillhub.domain.model.ApiToken;
import com.skillhub.domain.model.User;
import com.skillhub.domain.port.ApiTokenRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JpaApiTokenRepositoryAdapter implements ApiTokenRepositoryPort {

    private final JpaApiTokenRepository jpa;

    @Override
    public ApiToken save(ApiToken token) {
        JpaUser user = JpaUser.builder().id(token.getUser().getId()).build();
        JpaApiToken saved = jpa.save(JpaApiToken.builder()
            .id(token.getId())
            .user(user)
            .name(token.getName())
            .tokenHash(token.getTokenHash())
            .createdAt(token.getCreatedAt())
            .lastUsedAt(token.getLastUsedAt())
            .expiresAt(token.getExpiresAt())
            .build());
        return ApiToken.builder()
            .id(saved.getId())
            .user(UserJpaMapper.toDomain(saved.getUser()))
            .name(saved.getName())
            .tokenHash(saved.getTokenHash())
            .createdAt(saved.getCreatedAt())
            .lastUsedAt(saved.getLastUsedAt())
            .expiresAt(saved.getExpiresAt())
            .build();
    }

    @Override
    public Optional<ApiToken> findByTokenHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash).map(t -> ApiToken.builder()
            .id(t.getId())
            .user(UserJpaMapper.toDomain(t.getUser()))
            .name(t.getName())
            .tokenHash(t.getTokenHash())
            .createdAt(t.getCreatedAt())
            .lastUsedAt(t.getLastUsedAt())
            .expiresAt(t.getExpiresAt())
            .build());
    }
}
