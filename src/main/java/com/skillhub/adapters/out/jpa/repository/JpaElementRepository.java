package com.skillhub.adapters.out.jpa.repository;

import com.skillhub.adapters.out.jpa.entity.JpaElement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JpaElementRepository extends JpaRepository<JpaElement, UUID> {
    Optional<JpaElement> findBySlug(String slug);
    Optional<JpaElement> findBySlugAndDeletedAtIsNull(String slug);
    boolean existsBySlug(String slug);

    @Modifying
    @Query("update JpaElement e set e.downloadsCount = e.downloadsCount + 1 where e.id = :id")
    int incrementDownloads(@Param("id") UUID id);
}
