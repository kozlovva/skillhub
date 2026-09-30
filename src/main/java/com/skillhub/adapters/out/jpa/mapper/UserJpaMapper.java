package com.skillhub.adapters.out.jpa.mapper;

import com.skillhub.adapters.out.jpa.entity.JpaUser;
import com.skillhub.domain.model.User;

public final class UserJpaMapper {
    private UserJpaMapper() {}

    public static User toDomain(JpaUser e) {
        return User.builder()
            .id(e.getId()).ssoSubject(e.getSsoSubject()).email(e.getEmail())
            .displayName(e.getDisplayName()).avatarUrl(e.getAvatarUrl())
            .admin(e.isAdmin()).createdAt(e.getCreatedAt())
            .build();
    }

    public static JpaUser toEntity(User d) {
        return JpaUser.builder()
            .id(d.getId()).ssoSubject(d.getSsoSubject()).email(d.getEmail())
            .displayName(d.getDisplayName()).avatarUrl(d.getAvatarUrl())
            .admin(d.isAdmin()).createdAt(d.getCreatedAt())
            .build();
    }
}
