package com.skillhub.adapters.out.jpa.repository;

import com.skillhub.adapters.out.jpa.entity.JpaTeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface JpaTeamMemberRepository extends JpaRepository<JpaTeamMember, JpaTeamMember.JpaTeamMemberId> {
    @Query("SELECT m.role FROM JpaTeamMember m WHERE m.team.id = :teamId AND m.user.id = :userId")
    Optional<String> findRole(@Param("teamId") UUID teamId, @Param("userId") UUID userId);

    @Query("SELECT COUNT(m) > 0 FROM JpaTeamMember m WHERE m.team.id = :teamId AND m.user.id = :userId")
    boolean existsMember(@Param("teamId") UUID teamId, @Param("userId") UUID userId);
}
