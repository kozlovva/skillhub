package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.mapper.TeamJpaMapper;
import com.skillhub.adapters.out.jpa.repository.JpaTeamRepository;
import com.skillhub.domain.model.Team;
import com.skillhub.domain.port.TeamRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JpaTeamRepositoryAdapter implements TeamRepositoryPort {

    private final JpaTeamRepository jpa;

    @Override
    public Team save(Team team) {
        return TeamJpaMapper.toDomain(jpa.save(TeamJpaMapper.toEntity(team)));
    }

    @Override
    public Optional<Team> findBySlug(String slug) {
        return jpa.findBySlug(slug).map(TeamJpaMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Team> findAll() {
        return jpa.findAll().stream().map(TeamJpaMapper::toDomain).toList();
    }
}
