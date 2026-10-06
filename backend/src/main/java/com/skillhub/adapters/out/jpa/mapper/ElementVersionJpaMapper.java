package com.skillhub.adapters.out.jpa.mapper;

import com.skillhub.adapters.out.jpa.entity.JpaElementVersion;
import com.skillhub.domain.model.ElementVersion;
import com.skillhub.domain.model.VersionStatus;

public final class ElementVersionJpaMapper {
    private ElementVersionJpaMapper() {}

    public static ElementVersion toDomain(JpaElementVersion e) {
        return ElementVersion.builder()
            .id(e.getId())
            .element(ElementJpaMapper.toDomain(e.getElement()))
            .version(e.getVersion())
            .status(VersionStatus.valueOf(e.getStatus()))
            .changelog(e.getChangelog())
            .s3_key(e.getS3Key())
            .sizeBytes(e.getSizeBytes())
            .fileIndex(e.getFileIndex())
            .publishedBy(UserJpaMapper.toDomain(e.getPublishedBy()))
            .createdAt(e.getCreatedAt()).publishedAt(e.getPublishedAt())
            .deletedAt(e.getDeletedAt())
            .build();
    }

    public static JpaElementVersion toEntity(ElementVersion d) {
        return JpaElementVersion.builder()
            .id(d.getId())
            .element(ElementJpaMapper.toEntity(d.getElement()))
            .version(d.getVersion())
            .status(d.getStatus().name())
            .changelog(d.getChangelog())
            .s3Key(d.getS3_key())
            .sizeBytes(d.getSizeBytes())
            .fileIndex(d.getFileIndex())
            .publishedBy(UserJpaMapper.toEntity(d.getPublishedBy()))
            .createdAt(d.getCreatedAt()).publishedAt(d.getPublishedAt())
            .deletedAt(d.getDeletedAt())
            .build();
    }
}
