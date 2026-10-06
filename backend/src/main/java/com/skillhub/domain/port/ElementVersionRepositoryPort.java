package com.skillhub.domain.port;

import com.skillhub.domain.model.ElementVersion;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ElementVersionRepositoryPort {
    ElementVersion save(ElementVersion version);
    Optional<ElementVersion> findByElementIdAndVersion(UUID elementId, String version);
    Optional<ElementVersion> findActiveByElementIdAndVersion(UUID elementId, String version);
    Optional<ElementVersion> findLatestPublished(UUID elementId);
    List<ElementVersion> findAllByElementIdOrderByCreatedAtDesc(UUID elementId);
    Set<String> findAllS3Keys();
}
