package com.skillhub.application.service;

import com.skillhub.domain.model.*;
import com.skillhub.domain.port.ApiTokenRepositoryPort;
import com.skillhub.domain.port.ClockPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
        ApiTokenService.CreatedToken created = service.createToken(user, "cli", null);
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
        ApiTokenService.CreatedToken created = service.createToken(user, "cli", null);
        when(tokens.findByTokenHash(created.token().getTokenHash()))
            .thenReturn(Optional.of(created.token()));
        assertThat(service.authenticate(created.rawToken())).contains(user);
    }

    @Test
    void expiredTokenIsRejected() {
        ApiTokenService.CreatedToken created = service.createToken(user, "cli", null);
        created.token().setExpiresAt(Instant.parse("2020-01-01T00:00:00Z"));
        when(tokens.findByTokenHash(created.token().getTokenHash()))
            .thenReturn(Optional.of(created.token()));
        assertThat(service.authenticate(created.rawToken())).isEmpty();
    }

    @Test
    void createTokenSetsExpiryFromLifetimeDays() {
        service.createToken(user, "cli", 7);
        ArgumentCaptor<ApiToken> captor = ArgumentCaptor.forClass(ApiToken.class);
        org.mockito.Mockito.verify(tokens).save(captor.capture());
        assertThat(captor.getValue().getExpiresAt())
            .isEqualTo(Instant.parse("2026-01-08T00:00:00Z"));
    }

    @Test
    void createTokenWithoutLifetimeIsNeverExpiring() {
        service.createToken(user, "cli", null);
        ArgumentCaptor<ApiToken> captor = ArgumentCaptor.forClass(ApiToken.class);
        org.mockito.Mockito.verify(tokens).save(captor.capture());
        assertThat(captor.getValue().getExpiresAt()).isNull();
    }

    @Test
    void createTokenLifetime30And90Days() {
        service.createToken(user, "a", 30);
        service.createToken(user, "b", 90);
        ArgumentCaptor<ApiToken> captor = ArgumentCaptor.forClass(ApiToken.class);
        org.mockito.Mockito.verify(tokens, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getExpiresAt())
            .isEqualTo(Instant.parse("2026-01-31T00:00:00Z"));
        assertThat(captor.getAllValues().get(1).getExpiresAt())
            .isEqualTo(Instant.parse("2026-04-01T00:00:00Z"));
    }

    @Test
    void revokedTokenIsRejected() {
        ApiTokenService.CreatedToken created = service.createToken(user, "cli", null);
        created.token().setRevokedAt(Instant.parse("2025-12-01T00:00:00Z"));
        when(tokens.findByTokenHash(created.token().getTokenHash()))
            .thenReturn(Optional.of(created.token()));
        assertThat(service.authenticate(created.rawToken())).isEmpty();
    }

    @Test
    void revokeTokenSetsRevokedAt() {
        ApiTokenService.CreatedToken created = service.createToken(user, "cli", null);
        when(tokens.findByIdAndUserId(created.token().getId(), user.getId()))
            .thenReturn(Optional.of(created.token()));
        org.mockito.Mockito.clearInvocations(tokens);
        service.revokeToken(user, created.token().getId());
        ArgumentCaptor<ApiToken> captor = ArgumentCaptor.forClass(ApiToken.class);
        org.mockito.Mockito.verify(tokens).save(captor.capture());
        assertThat(captor.getValue().getRevokedAt())
            .isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void revokeTokenIsIdempotent() {
        ApiTokenService.CreatedToken created = service.createToken(user, "cli", null);
        created.token().setRevokedAt(Instant.parse("2025-06-01T00:00:00Z"));
        when(tokens.findByIdAndUserId(created.token().getId(), user.getId()))
            .thenReturn(Optional.of(created.token()));
        org.mockito.Mockito.clearInvocations(tokens);
        service.revokeToken(user, created.token().getId());
        org.mockito.Mockito.verify(tokens, org.mockito.Mockito.never()).save(any());
        assertThat(created.token().getRevokedAt())
            .isEqualTo(Instant.parse("2025-06-01T00:00:00Z"));
    }

    @Test
    void revokeForeignTokenThrows() {
        when(tokens.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());
        org.junit.jupiter.api.Assertions.assertThrows(
            java.util.NoSuchElementException.class,
            () -> service.revokeToken(user, UUID.randomUUID()));
    }
}
