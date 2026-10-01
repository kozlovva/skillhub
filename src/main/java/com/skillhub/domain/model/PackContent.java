package com.skillhub.domain.model;

import lombok.*;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PackContent {
    private Element packElement;
    private Element element;
    private String versionConstraint;
}
