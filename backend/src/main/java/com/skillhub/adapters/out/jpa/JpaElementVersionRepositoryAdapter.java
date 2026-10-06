package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.mapper.ElementVersionJpaMapper;
import com.skillhub.adapters.out.jpa.repository.JpaElementVersionRepository;
import com.skillhub.domain.model.ElementVersion;
import com.skillhub.domain.model.VersionStatus;
import com.skillhub.domain.port.ElementVersionRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaElementVersionRepositoryAdapter implements ElementVersionRepositoryPort {

    private final JpaElementVersionRepository jpa;

    @Override
    @Transactional
    public ElementVersion save(ElementVersion version) {
        return ElementVersionJpaMapper.toDomain(jpa.save(ElementVersionJpaMapper.toEntity(version)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ElementVersion> findByElementIdAndVersion(UUID elementId, String version) {
        return jpa.findByElementIdAndVersion(elementId, version)
            .map(ElementVersionJpaMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ElementVersion> findActiveByElementIdAndVersion(UUID elementId, String version) {
        return jpa.findByElementIdAndVersionAndDeletedAtIsNull(elementId, version)
            .map(ElementVersionJpaMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ElementVersion> findLatestPublished(UUID elementId) {
        return jpa.findFirstByElementIdAndStatusAndDeletedAtIsNullOrderByPublishedAtDesc(
                elementId, VersionStatus.PUBLISHED.name())
            .map(ElementVersionJpaMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ElementVersion> findAllByElementIdOrderByCreatedAtDesc(UUID elementId) {
        return jpa.findAllByElementIdAndDeletedAtIsNullOrderByCreatedAtDesc(elementId).stream()
            .map(ElementVersionJpaMapper::toDomain)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> findAllS3Keys() {
        return new HashSet<>(jpa.findAllS3Keys());
    }
}
