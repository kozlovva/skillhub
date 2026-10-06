package com.skillhub.domain.port;

import com.skillhub.domain.model.Element;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ElementRepositoryPort {
    Element save(Element element);
    Optional<Element> findBySlug(String slug);
    Optional<Element> findById(UUID id);
    boolean existsBySlug(String slug);
    List<Element> findAll();
    void incrementDownloads(UUID elementId);
}
