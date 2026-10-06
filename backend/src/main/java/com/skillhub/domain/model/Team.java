package com.skillhub.domain.model;

import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Team {
    private UUID id;
    private String slug;
    private String name;
    private Instant createdAt;
}
