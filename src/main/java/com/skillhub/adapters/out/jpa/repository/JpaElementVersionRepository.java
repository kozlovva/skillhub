package com.skillhub.adapters.out.jpa.repository;

import com.skillhub.adapters.out.jpa.entity.JpaElementVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaElementVersionRepository extends JpaRepository<JpaElementVersion, UUID> {
    Optional<JpaElementVersion> findByElementIdAndVersion(UUID elementId, String version);
    Optional<JpaElementVersion> findByElementIdAndVersionAndDeletedAtIsNull(
        UUID elementId, String version);
    Optional<JpaElementVersion> findFirstByElementIdAndStatusAndDeletedAtIsNullOrderByPublishedAtDesc(
        UUID elementId, String status);
    List<JpaElementVersion> findAllByElementIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID elementId);

    @Query("select v.s3Key from JpaElementVersion v")
    List<String> findAllS3Keys();
}
