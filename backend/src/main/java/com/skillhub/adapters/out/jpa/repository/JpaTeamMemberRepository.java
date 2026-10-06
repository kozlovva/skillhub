package com.skillhub.adapters.out.jpa.repository;

import com.skillhub.adapters.out.jpa.entity.JpaTeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaTeamMemberRepository extends JpaRepository<JpaTeamMember, JpaTeamMember.JpaTeamMemberId> {
    @Query("SELECT m.role FROM JpaTeamMember m WHERE m.team.id = :teamId AND m.user.id = :userId")
    Optional<String> findRole(@Param("teamId") UUID teamId, @Param("userId") UUID userId);

    @Query("SELECT COUNT(m) > 0 FROM JpaTeamMember m WHERE m.team.id = :teamId AND m.user.id = :userId")
    boolean existsMember(@Param("teamId") UUID teamId, @Param("userId") UUID userId);

    @Query("SELECT m FROM JpaTeamMember m JOIN FETCH m.team WHERE m.user.id = :userId ORDER BY m.team.name")
    List<JpaTeamMember> findByUserId(@Param("userId") UUID userId);

    @Query("SELECT m FROM JpaTeamMember m JOIN FETCH m.user WHERE m.team.id = :teamId ORDER BY m.user.displayName")
    List<JpaTeamMember> findByTeamId(@Param("teamId") UUID teamId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM JpaTeamMember m WHERE m.team.id = :teamId AND m.user.id = :userId")
    void deleteByTeamIdAndUserId(@Param("teamId") UUID teamId, @Param("userId") UUID userId);
}
