package com.skillhub.adapters.out.jpa.mapper;

import com.skillhub.adapters.out.jpa.entity.JpaElement;
import com.skillhub.domain.model.Element;
import com.skillhub.domain.model.ElementType;
import com.skillhub.domain.model.Visibility;

public final class ElementJpaMapper {
    private ElementJpaMapper() {}

    public static Element toDomain(JpaElement e) {
        return Element.builder()
            .id(e.getId()).slug(e.getSlug())
            .type(ElementType.valueOf(e.getType()))
            .name(e.getName()).description(e.getDescription())
            .team(TeamJpaMapper.toDomain(e.getTeam()))
            .category(e.getCategory() == null ? null : CategoryJpaMapper.toDomain(e.getCategory()))
            .tags(e.getTags())
            .visibility(Visibility.valueOf(e.getVisibility()))
            .author(UserJpaMapper.toDomain(e.getAuthor()))
            .latestVersion(e.getLatestVersion())
            .latestChangelog(e.getLatestChangelog() == null ? "" : e.getLatestChangelog())
            .downloadsCount(e.getDownloadsCount())
            .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt())
            .deletedAt(e.getDeletedAt())
            .build();
    }

    public static JpaElement toEntity(Element d) {
        return JpaElement.builder()
            .id(d.getId()).slug(d.getSlug()).type(d.getType().name())
            .name(d.getName()).description(d.getDescription())
            .team(TeamJpaMapper.toEntity(d.getTeam()))
            .category(d.getCategory() == null ? null : CategoryJpaMapper.toEntity(d.getCategory()))
            .tags(d.getTags()).visibility(d.getVisibility().name())
            .author(UserJpaMapper.toEntity(d.getAuthor()))
            .latestVersion(d.getLatestVersion())
            .latestChangelog(d.getLatestChangelog() == null ? "" : d.getLatestChangelog())
            .downloadsCount(d.getDownloadsCount())
            .createdAt(d.getCreatedAt()).updatedAt(d.getUpdatedAt())
            .deletedAt(d.getDeletedAt())
            .build();
    }
}
