package com.skillhub.adapters.out.jpa.mapper;

import com.skillhub.adapters.out.jpa.entity.JpaCategory;
import com.skillhub.domain.model.Category;

public final class CategoryJpaMapper {
    private CategoryJpaMapper() {}

    public static Category toDomain(JpaCategory e) {
        return Category.builder()
            .id(e.getId()).slug(e.getSlug()).name(e.getName())
            .parent(e.getParent() == null ? null : toDomain(e.getParent()))
            .icon(e.getIcon())
            .build();
    }

    public static JpaCategory toEntity(Category d) {
        return JpaCategory.builder()
            .id(d.getId()).slug(d.getSlug()).name(d.getName())
            .parent(d.getParent() == null ? null : toEntity(d.getParent()))
            .icon(d.getIcon())
            .build();
    }
}
