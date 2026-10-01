package com.skillhub.adapters.out.jpa.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reviews")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JpaReview {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "element_id", nullable = false)
    private JpaElement element;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private JpaUser user;
    @Column(nullable = false) private int rating;
    @Column(nullable = false) private String text;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}
