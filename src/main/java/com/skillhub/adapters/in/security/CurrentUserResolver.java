package com.skillhub.adapters.in.security;

import com.skillhub.application.service.UserSyncService;
import com.skillhub.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final UserSyncService userSyncService;

    public User resolve(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        if (auth.getPrincipal() instanceof User user) {
            return user;
        }
        if (auth.getPrincipal() instanceof Jwt jwt) {
            String subject = jwt.getSubject();
            String username = jwt.getClaimAsString("preferred_username");
            String email = jwt.getClaimAsString("email");
            return userSyncService.syncFromSso(subject, email != null ? email : subject,
                username != null ? username : subject);
        }
        return null;
    }
}
