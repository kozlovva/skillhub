package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.mapper.UserJpaMapper;
import com.skillhub.adapters.out.jpa.repository.JpaUserRepository;
import com.skillhub.domain.model.User;
import com.skillhub.domain.port.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaUserRepositoryAdapter implements UserRepositoryPort {

    private final JpaUserRepository jpa;

    @Override
    public User save(User user) {
        return UserJpaMapper.toDomain(jpa.save(UserJpaMapper.toEntity(user)));
    }

    @Override
    public Optional<User> findBySsoSubject(String ssoSubject) {
        return jpa.findBySsoSubject(ssoSubject).map(UserJpaMapper::toDomain);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpa.findById(id).map(UserJpaMapper::toDomain);
    }

    @Override
    public List<User> searchCandidates(String query, UUID excludeTeamId, int limit) {
        return jpa.searchCandidates("%" + query + "%", excludeTeamId, limit)
            .stream().map(UserJpaMapper::toDomain).toList();
    }
}
