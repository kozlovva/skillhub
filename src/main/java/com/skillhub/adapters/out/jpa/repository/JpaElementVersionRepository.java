package com.skillhub.adapters.out.jpa.repository;

import com.skillhub.adapters.out.jpa.entity.JpaElementVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaElementVersionRepository extends JpaRepository<JpaElementVersion, UUID> {
    Optional<JpaElementVersion> findByElementIdAndVersion(UUID elementId, String version);
    Optional<JpaElementVersion> findFirstByElementIdAndStatusOrderByPublishedAtDesc(
        UUID elementId, String status);
    List<JpaElementVersion> findAllByElementIdOrderByCreatedAtDesc(UUID elementId);
}
