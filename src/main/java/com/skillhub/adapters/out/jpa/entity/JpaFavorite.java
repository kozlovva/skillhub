package com.skillhub.adapters.out.jpa.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "favorites")
@IdClass(JpaFavoriteId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JpaFavorite {
    @Id @Column(name = "user_id") private UUID userId;
    @Id @Column(name = "element_id") private UUID elementId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}
