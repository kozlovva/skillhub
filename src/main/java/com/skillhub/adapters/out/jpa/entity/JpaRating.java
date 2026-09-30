package com.skillhub.adapters.out.jpa.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "ratings")
@IdClass(JpaRatingId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JpaRating {
    @Id @Column(name = "element_id") private UUID elementId;
    @Id @Column(name = "user_id") private UUID userId;
    @Column(nullable = false) private int rating;
}
