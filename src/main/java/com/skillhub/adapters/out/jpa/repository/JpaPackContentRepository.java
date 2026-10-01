package com.skillhub.adapters.out.jpa.repository;

import com.skillhub.adapters.out.jpa.entity.JpaPackContent;
import com.skillhub.adapters.out.jpa.entity.JpaPackContentId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface JpaPackContentRepository extends JpaRepository<JpaPackContent, JpaPackContentId> {
    List<JpaPackContent> findAllByPackElementId(UUID packElementId);
}
