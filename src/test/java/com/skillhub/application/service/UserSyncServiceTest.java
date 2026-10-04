package com.skillhub.application.service;

import com.skillhub.domain.model.User;
import com.skillhub.domain.port.ClockPort;
import com.skillhub.domain.port.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserSyncServiceTest {

    UserRepositoryPort users;
    ClockPort clock;
    UserSyncService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepositoryPort.class);
        clock = mock(ClockPort.class);
        when(clock.now()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        service = new UserSyncService(users, clock);
    }

    @Test
    void createsUserWithUsername() {
        when(users.findBySsoSubject("sub-1")).thenReturn(Optional.empty());
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = service.syncFromSso("sub-1", "v@b.c", "vpetrov", "Владимир Петров");

        assertThat(result.getUsername()).isEqualTo("vpetrov");
        assertThat(result.getDisplayName()).isEqualTo("Владимир Петров");
    }

    @Test
    void updatesUsernameOnResync() {
        User existing = User.builder().id(UUID.randomUUID()).ssoSubject("sub-1")
            .username("old").email("old@b.c").displayName("Old").admin(false)
            .createdAt(Instant.now()).build();
        when(users.findBySsoSubject("sub-1")).thenReturn(Optional.of(existing));
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = service.syncFromSso("sub-1", "new@b.c", "vpetrov", "Владимир Петров");

        assertThat(result.getUsername()).isEqualTo("vpetrov");
        assertThat(result.getEmail()).isEqualTo("new@b.c");
        verify(users).save(existing);
    }
}
