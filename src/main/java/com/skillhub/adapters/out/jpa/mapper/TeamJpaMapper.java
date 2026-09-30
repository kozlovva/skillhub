package com.skillhub.adapters.out.jpa.mapper;

import com.skillhub.adapters.out.jpa.entity.JpaTeam;
import com.skillhub.domain.model.Team;

public final class TeamJpaMapper {
    private TeamJpaMapper() {}

    public static Team toDomain(JpaTeam e) {
        return Team.builder()
            .id(e.getId()).slug(e.getSlug()).name(e.getName()).createdAt(e.getCreatedAt())
            .build();
    }

    public static JpaTeam toEntity(Team d) {
        return JpaTeam.builder()
            .id(d.getId()).slug(d.getSlug()).name(d.getName()).createdAt(d.getCreatedAt())
            .build();
    }
}
