package com.skillhub.domain.port;

import com.skillhub.domain.model.Element;

import java.util.List;
import java.util.Optional;

public interface ElementRepositoryPort {
    Element save(Element element);
    Optional<Element> findBySlug(String slug);
    boolean existsBySlug(String slug);
    List<Element> findAll();
}
