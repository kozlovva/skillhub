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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
            .revokedAt(token.getRevokedAt())
            .build());
        return toDomain(saved);
    }

    @Override
    public Optional<ApiToken> findByTokenHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApiToken> findByUserId(UUID userId) {
        return jpa.findAllByUserId(userId).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ApiToken> findByIdAndUserId(UUID tokenId, UUID userId) {
        return jpa.findByIdAndUserId(tokenId, userId).map(this::toDomain);
    }

    private ApiToken toDomain(JpaApiToken t) {
        return ApiToken.builder()
            .id(t.getId())
            .user(UserJpaMapper.toDomain(t.getUser()))
            .name(t.getName())
            .tokenHash(t.getTokenHash())
            .createdAt(t.getCreatedAt())
            .lastUsedAt(t.getLastUsedAt())
            .expiresAt(t.getExpiresAt())
            .revokedAt(t.getRevokedAt())
            .build();
    }
}
