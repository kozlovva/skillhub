package com.skillhub.application.service;

import com.skillhub.core.exception.ConflictException;
import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.core.exception.NotFoundException;
import com.skillhub.domain.model.Category;
import com.skillhub.domain.model.User;
import com.skillhub.domain.port.CategoryRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryUseCase {

    private final CategoryRepositoryPort categories;

    public CategoryUseCase(CategoryRepositoryPort categories) {
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<Category> list() {
        return categories.findAllOrderedByName();
    }

    @Transactional
    public Category create(String slug, String name, String parentSlug, String icon, User actor) {
        if (!actor.isAdmin()) {
            throw new ForbiddenException("Only admin can manage categories");
        }
        if (categories.existsBySlug(slug)) {
            throw new ConflictException("Category already exists: " + slug);
        }
        Category parent = parentSlug == null ? null
            : categories.findBySlug(parentSlug)
                .orElseThrow(() -> new NotFoundException("Parent category not found: " + parentSlug));
        return categories.save(Category.builder()
            .slug(slug).name(name).parent(parent).icon(icon).build());
    }
}
