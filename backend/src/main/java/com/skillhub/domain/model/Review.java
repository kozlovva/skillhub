package com.skillhub.domain.model;

import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Review {
    private UUID id;
    private Element element;
    private User user;
    private int rating;
    private String text;
    private Instant createdAt;
}
