package com.skillhub.adapters.in.security;

import com.skillhub.application.service.UserSyncService;
import com.skillhub.domain.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CurrentUserResolverTest {

    @Test
    void usesEmailClaimAndPreferredUsername() {
        UserSyncService sync = mock(UserSyncService.class);
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("sub-1")
            .claim("email", "real@skillhub.io").claim("preferred_username", "vlad")
            .build();
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(jwt);
        User synced = User.builder().ssoSubject("sub-1")
            .email("real@skillhub.io").displayName("vlad").build();
        when(sync.syncFromSso("sub-1", "real@skillhub.io", "vlad")).thenReturn(synced);

        User result = new CurrentUserResolver(sync).resolve(auth);

        assertThat(result).isSameAs(synced);
        verify(sync).syncFromSso("sub-1", "real@skillhub.io", "vlad");
    }

    @Test
    void fallsBackToSubjectWhenEmailClaimMissing() {
        UserSyncService sync = mock(UserSyncService.class);
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("sub-2").build();
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(jwt);
        when(sync.syncFromSso("sub-2", "sub-2", "sub-2"))
            .thenReturn(User.builder().build());

        new CurrentUserResolver(sync).resolve(auth);

        verify(sync).syncFromSso("sub-2", "sub-2", "sub-2");
    }

    @Test
    void anonymousReturnsNull() {
        UserSyncService sync = mock(UserSyncService.class);
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);

        CurrentUserResolver resolver = new CurrentUserResolver(sync);

        assertThat(resolver.resolve(auth)).isNull();
        assertThat(resolver.resolve(null)).isNull();
    }
}
