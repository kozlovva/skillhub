package com.skillhub.domain.port;

import com.skillhub.domain.model.User;

import java.util.Optional;

public interface UserRepositoryPort {
    User save(User user);
    Optional<User> findBySsoSubject(String ssoSubject);
}
