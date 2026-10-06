package com.skillhub.domain.model;

import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class User {
    private UUID id;
    private String ssoSubject;
    private String username;
    private String email;
    private String displayName;
    private String avatarUrl;
    private boolean admin;
    private Instant createdAt;
}
