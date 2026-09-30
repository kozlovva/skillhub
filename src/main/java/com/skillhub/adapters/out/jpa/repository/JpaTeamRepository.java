package com.skillhub.adapters.out.jpa.repository;

import com.skillhub.adapters.out.jpa.entity.JpaTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface JpaTeamRepository extends JpaRepository<JpaTeam, UUID> {
    Optional<JpaTeam> findBySlug(String slug);
}
