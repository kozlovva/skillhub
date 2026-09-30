package com.skillhub.domain.model;

import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Element {
    private UUID id;
    private String slug;
    private ElementType type;
    private String name;
    private String description;
    private Team team;
    private Category category;
    private String[] tags;
    private Visibility visibility;
    private User author;
    private String latestVersion;
    private long downloadsCount;
    private Instant createdAt;
    private Instant updatedAt;
}
