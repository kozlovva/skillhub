package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.mapper.ElementJpaMapper;
import com.skillhub.adapters.out.jpa.repository.JpaElementRepository;
import com.skillhub.domain.model.Element;
import com.skillhub.domain.port.ElementRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JpaElementRepositoryAdapter implements ElementRepositoryPort {

    private final JpaElementRepository jpa;

    @Override
    @Transactional
    public Element save(Element element) {
        return ElementJpaMapper.toDomain(jpa.save(ElementJpaMapper.toEntity(element)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Element> findBySlug(String slug) {
        return jpa.findBySlugAndDeletedAtIsNull(slug).map(ElementJpaMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Element> findById(java.util.UUID id) {
        return jpa.findById(id).map(ElementJpaMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySlug(String slug) {
        return jpa.existsBySlug(slug);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Element> findAll() {
        return jpa.findAll().stream().map(ElementJpaMapper::toDomain).toList();
    }

    @Override
    @Transactional
    public void incrementDownloads(java.util.UUID elementId) {
        jpa.incrementDownloads(elementId);
    }
}
