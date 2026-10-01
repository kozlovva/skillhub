package com.skillhub.domain.model;

import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ElementVersion {
    private UUID id;
    private Element element;
    private String version;
    private VersionStatus status;
    private String changelog;
    private String s3_key;
    private long sizeBytes;
    private String fileIndex;
    private User publishedBy;
    private Instant createdAt;
    private Instant publishedAt;
}
