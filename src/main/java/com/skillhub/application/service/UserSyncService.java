package com.skillhub.application.service;

import com.skillhub.domain.model.User;
import com.skillhub.domain.port.ClockPort;
import com.skillhub.domain.port.UserRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSyncService {

    private final UserRepositoryPort users;
    private final ClockPort clock;

    public UserSyncService(UserRepositoryPort users, ClockPort clock) {
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public User syncFromSso(String subject, String email, String displayName) {
        return users.findBySsoSubject(subject)
            .map(u -> {
                u.setEmail(email);
                u.setDisplayName(displayName);
                return users.save(u);
            })
            .orElseGet(() -> users.save(User.builder()
                .ssoSubject(subject)
                .email(email)
                .displayName(displayName)
                .admin(false)
                .createdAt(clock.now())
                .build()));
    }
}
