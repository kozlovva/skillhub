package com.skillhub.domain.port;

import com.skillhub.domain.model.Team;

import java.util.List;
import java.util.Optional;

public interface TeamRepositoryPort {
    Team save(Team team);
    Optional<Team> findBySlug(String slug);
    List<Team> findAll();
}
