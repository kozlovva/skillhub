package com.skillhub.adapters.out.jpa.repository;

import com.skillhub.adapters.out.jpa.entity.JpaCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaCategoryRepository extends JpaRepository<JpaCategory, UUID> {
    Optional<JpaCategory> findBySlug(String slug);
    boolean existsBySlug(String slug);
    List<JpaCategory> findAllByOrderByNameAsc();
}
