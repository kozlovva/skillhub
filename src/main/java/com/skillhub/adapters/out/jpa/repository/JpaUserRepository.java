package com.skillhub.adapters.out.jpa.repository;

import com.skillhub.adapters.out.jpa.entity.JpaUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface JpaUserRepository extends JpaRepository<JpaUser, UUID> {
    Optional<JpaUser> findBySsoSubject(String ssoSubject);
}
