package com.skillhub.application.service;

import com.skillhub.core.exception.ConflictException;
import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.core.exception.NotFoundException;
import com.skillhub.core.exception.UnprocessableException;
import com.skillhub.domain.model.*;
import com.skillhub.domain.port.CategoryRepositoryPort;
import com.skillhub.domain.port.ClockPort;
import com.skillhub.domain.port.ElementRepositoryPort;
import com.skillhub.domain.port.PackContentRepositoryPort;
import com.skillhub.domain.port.TeamRepositoryPort;
import com.skillhub.domain.service.AccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final PackContentRepositoryPort packContents;
    private final AuditService audit;

    public ElementUseCase(ElementRepositoryPort elements, TeamRepositoryPort teams,
                          CategoryRepositoryPort categories, AccessService access,
                          ClockPort clock, PackContentRepositoryPort packContents,
                          AuditService audit) {
        this.elements = elements;
        this.teams = teams;
        this.categories = categories;
        this.access = access;
        this.clock = clock;
        this.packContents = packContents;
        this.audit = audit;
    }

    @Transactional
    public Element create(CreateCommand cmd, User author) {
        if (cmd.teamSlug() == null && cmd.visibility() == Visibility.TEAM) {
            throw new UnprocessableException("TEAM visibility requires a team", cmd.teamSlug());
        }
        Team team = null;
        if (cmd.teamSlug() != null) {
            team = teams.findBySlug(cmd.teamSlug())
                .orElseThrow(() -> new NotFoundException("Team not found: " + cmd.teamSlug()));
            if (!access.canPublish(team, author)) {
                throw new ForbiddenException(
                    "Only OWNER/MAINTAINER can publish to team " + team.getSlug());
            }
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
        if (element.getDeletedAt() != null) {
            throw new NotFoundException("Element not found: " + slug);
        }
        if (!access.canRead(element, viewer)) {
            throw new ForbiddenException("Element is not visible to you: " + slug);
        }
        return element;
    }

    @Transactional
    public void delete(String slug, User user) {
        Element element = elements.findBySlug(slug)
            .orElseThrow(() -> new NotFoundException("Element not found: " + slug));
        if (element.getDeletedAt() != null) {
            throw new NotFoundException("Element not found: " + slug);
        }
        if (!access.canDelete(element, user)) {
            throw new ForbiddenException("Not allowed to delete element: " + slug);
        }
        var refs = packContents.findAllByElementId(element.getId());
        if (!refs.isEmpty()) {
            String packs = refs.stream()
                .map(rc -> rc.getPackElement().getSlug())
                .distinct().sorted()
                .collect(Collectors.joining(", "));
            throw new ConflictException("Element is used in packs: " + packs);
        }
        Instant now = clock.now();
        element.setDeletedAt(now);
        element.setUpdatedAt(now);
        elements.save(element);
        audit.log(user, "DELETE_ELEMENT", element.getId(), Map.of("slug", slug));
    }

    @Transactional(readOnly = true)
    public List<Element> listVisible(User viewer) {
        return elements.findAll().stream()
            .filter(e -> e.getDeletedAt() == null)
            .filter(e -> access.canRead(e, viewer))
            .toList();
    }
}
