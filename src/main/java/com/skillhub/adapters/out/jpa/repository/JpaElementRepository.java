package com.skillhub.adapters.out.jpa.repository;

import com.skillhub.adapters.out.jpa.entity.JpaElement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface JpaElementRepository extends JpaRepository<JpaElement, UUID> {
    Optional<JpaElement> findBySlug(String slug);
    boolean existsBySlug(String slug);
}
