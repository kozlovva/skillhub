package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.mapper.UserJpaMapper;
import com.skillhub.adapters.out.jpa.repository.JpaUserRepository;
import com.skillhub.domain.model.User;
import com.skillhub.domain.port.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

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
}
