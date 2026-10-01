package com.skillhub.application.service;

import com.skillhub.core.exception.ConflictException;
import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.core.exception.NotFoundException;
import com.skillhub.domain.model.*;
import com.skillhub.domain.port.CategoryRepositoryPort;
import com.skillhub.domain.port.ClockPort;
import com.skillhub.domain.port.ElementRepositoryPort;
import com.skillhub.domain.port.TeamRepositoryPort;
import com.skillhub.domain.service.AccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ElementUseCase {

    public record CreateCommand(String slug, ElementType type, String name,
                                String description, String teamSlug, String categorySlug,
                                String[] tags, Visibility visibility) {}

    private final ElementRepositoryPort elements;
    private final TeamRepositoryPort teams;
    private final CategoryRepositoryPort categories;
    private final AccessService access;
    private final ClockPort clock;

    public ElementUseCase(ElementRepositoryPort elements, TeamRepositoryPort teams,
                          CategoryRepositoryPort categories, AccessService access,
                          ClockPort clock) {
        this.elements = elements;
        this.teams = teams;
        this.categories = categories;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public Element create(CreateCommand cmd, User author) {
        Team team = teams.findBySlug(cmd.teamSlug())
            .orElseThrow(() -> new NotFoundException("Team not found: " + cmd.teamSlug()));
        if (!access.canPublish(team, author)) {
            throw new ForbiddenException(
                "Only OWNER/MAINTAINER can publish to team " + team.getSlug());
        }
        if (elements.existsBySlug(cmd.slug())) {
            throw new ConflictException("Element already exists: " + cmd.slug());
        }
        Category category = cmd.categorySlug() == null ? null
            : categories.findBySlug(cmd.categorySlug())
                .orElseThrow(() -> new NotFoundException(
                    "Category not found: " + cmd.categorySlug()));
        return elements.save(Element.builder()
            .slug(cmd.slug())
            .type(cmd.type())
            .name(cmd.name())
            .description(cmd.description() == null ? "" : cmd.description())
            .team(team)
            .category(category)
            .tags(cmd.tags() == null ? new String[0] : cmd.tags())
            .visibility(cmd.visibility())
            .author(author)
            .createdAt(clock.now())
            .updatedAt(clock.now())
            .build());
    }

    @Transactional(readOnly = true)
    public Element getBySlug(String slug, User viewer) {
        Element element = elements.findBySlug(slug)
            .orElseThrow(() -> new NotFoundException("Element not found: " + slug));
        if (!access.canRead(element, viewer)) {
            throw new ForbiddenException("Element is not visible to you: " + slug);
        }
        return element;
    }

    @Transactional(readOnly = true)
    public List<Element> listVisible(User viewer) {
        return elements.findAll().stream()
            .filter(e -> access.canRead(e, viewer))
            .toList();
    }
}
