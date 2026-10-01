package com.skillhub.application.service;

import com.skillhub.domain.model.*;
import com.skillhub.domain.port.ApiTokenRepositoryPort;
import com.skillhub.domain.port.ClockPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiTokenServiceTest {

    ApiTokenRepositoryPort tokens;
    ClockPort clock;
    ApiTokenService service;

    User user = User.builder().id(UUID.randomUUID()).ssoSubject("s").email("e")
        .displayName("n").admin(false).createdAt(Instant.now()).build();

    @BeforeEach
    void setUp() {
        tokens = mock(ApiTokenRepositoryPort.class);
        clock = mock(ClockPort.class);
        when(clock.now()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(tokens.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new ApiTokenService(tokens, clock, "skh_");
    }

    @Test
    void rawTokenHasPrefixAndHashDiffers() {
        ApiTokenService.CreatedToken created = service.createToken(user, "cli");
        assertThat(created.rawToken()).startsWith("skh_");
        assertThat(created.token().getTokenHash()).hasSize(64);
        assertThat(created.token().getTokenHash()).isNotEqualTo(created.rawToken());
    }

    @Test
    void hashIsDeterministic() {
        assertThat(ApiTokenService.hash("abc")).isEqualTo(ApiTokenService.hash("abc")).hasSize(64);
    }

    @Test
    void authenticateResolvesUserByHash() {
        ApiTokenService.CreatedToken created = service.createToken(user, "cli");
        when(tokens.findByTokenHash(created.token().getTokenHash()))
            .thenReturn(Optional.of(created.token()));
        assertThat(service.authenticate(created.rawToken())).contains(user);
    }

    @Test
    void expiredTokenIsRejected() {
        ApiTokenService.CreatedToken created = service.createToken(user, "cli");
        created.token().setExpiresAt(Instant.parse("2020-01-01T00:00:00Z"));
        when(tokens.findByTokenHash(created.token().getTokenHash()))
            .thenReturn(Optional.of(created.token()));
        assertThat(service.authenticate(created.rawToken())).isEmpty();
    }
}
