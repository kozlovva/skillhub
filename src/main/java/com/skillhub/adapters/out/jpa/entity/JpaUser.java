package com.skillhub.adapters.out.jpa.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JpaUser {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "sso_subject", nullable = false, unique = true)
    private String ssoSubject;
    @Column private String username;
    @Column(nullable = false) private String email;
    @Column(name = "display_name", nullable = false) private String displayName;
    @Column(name = "avatar_url") private String avatarUrl;
    @Column(name = "is_admin", nullable = false) private boolean admin;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}
