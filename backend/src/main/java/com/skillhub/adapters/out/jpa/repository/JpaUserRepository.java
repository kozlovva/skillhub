package com.skillhub.adapters.out.jpa.repository;

import com.skillhub.adapters.out.jpa.entity.JpaUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaUserRepository extends JpaRepository<JpaUser, UUID> {
    Optional<JpaUser> findBySsoSubject(String ssoSubject);

    @Query(value = """
        SELECT * FROM users u
        WHERE (u.username ILIKE :pattern OR u.display_name ILIKE :pattern)
          AND NOT EXISTS (
            SELECT 1 FROM team_members tm
            WHERE tm.team_id = :excludeTeamId AND tm.user_id = u.id
          )
        ORDER BY u.display_name
        LIMIT :limit
        """, nativeQuery = true)
    List<JpaUser> searchCandidates(String pattern, UUID excludeTeamId, int limit);
}
