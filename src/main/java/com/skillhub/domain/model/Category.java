package com.skillhub.domain.model;

import lombok.*;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Category {
    private UUID id;
    private String slug;
    private String name;
    private Category parent;
    private String icon;
}
