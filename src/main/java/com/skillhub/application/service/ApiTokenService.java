package com.skillhub.application.service;

import com.skillhub.domain.model.ApiToken;
import com.skillhub.domain.model.User;
import com.skillhub.domain.port.ApiTokenRepositoryPort;
import com.skillhub.domain.port.ClockPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class ApiTokenService {

    public record CreatedToken(ApiToken token, String rawToken) {}

    private final ApiTokenRepositoryPort tokens;
    private final ClockPort clock;
    private final String prefix;
    private final SecureRandom random = new SecureRandom();

    public ApiTokenService(ApiTokenRepositoryPort tokens,
                           ClockPort clock,
                           @Value("${skillhub.api-token-prefix:skh_}") String prefix) {
        this.tokens = tokens;
        this.clock = clock;
        this.prefix = prefix;
    }

    @Transactional
    public CreatedToken createToken(User user, String name) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ApiToken saved = tokens.save(ApiToken.builder()
            .user(user)
            .name(name)
            .tokenHash(hash(raw))
            .createdAt(clock.now())
            .build());
        return new CreatedToken(saved, raw);
    }

    @Transactional
    public Optional<User> authenticate(String rawToken) {
        if (rawToken == null || !rawToken.startsWith(prefix)) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(hash(rawToken))
            .filter(t -> t.getExpiresAt() == null || t.getExpiresAt().isAfter(clock.now()))
            .map(t -> {
                t.setLastUsedAt(clock.now());
                return tokens.save(t).getUser();
            });
    }

    public static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
