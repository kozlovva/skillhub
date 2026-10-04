package com.skillhub.domain.port;

import com.skillhub.domain.model.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {
    User save(User user);
    Optional<User> findById(UUID id);
    Optional<User> findBySsoSubject(String ssoSubject);
    List<User> searchCandidates(String query, UUID excludeTeamId, int limit);
}
