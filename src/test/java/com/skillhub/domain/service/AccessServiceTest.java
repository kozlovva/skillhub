package com.skillhub.domain.service;

import com.skillhub.domain.model.*;
import com.skillhub.domain.port.TeamMembershipPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessServiceTest {

    TeamMembershipPort membership;
    AccessService access;

    Team team = Team.builder().id(UUID.randomUUID()).slug("t").name("T")
        .createdAt(Instant.now()).build();
    User user = User.builder().id(UUID.randomUUID()).ssoSubject("s").email("e")
        .displayName("n").admin(false).createdAt(Instant.now()).build();
    Element element = Element.builder().id(UUID.randomUUID()).slug("el")
        .type(ElementType.SKILL).name("n").description("").team(team)
        .tags(new String[0]).visibility(Visibility.TEAM).author(user)
        .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();

    @BeforeEach
    void setUp() {
        membership = mock(TeamMembershipPort.class);
        access = new AccessService(membership);
    }

    @Test
    void publicElementReadableByAnyone() {
        element.setVisibility(Visibility.PUBLIC);
        assertThat(access.canRead(element, null)).isTrue();
    }

    @Test
    void teamElementReadableByMember() {
        when(membership.roleOf(team.getId(), user.getId()))
            .thenReturn(Optional.of(TeamRole.MEMBER));
        assertThat(access.canRead(element, user)).isTrue();
    }

    @Test
    void teamElementNotReadableByOutsider() {
        when(membership.roleOf(team.getId(), user.getId())).thenReturn(Optional.empty());
        assertThat(access.canRead(element, user)).isFalse();
    }

    @Test
    void adminReadsEverything() {
        when(membership.roleOf(team.getId(), user.getId())).thenReturn(Optional.empty());
        user.setAdmin(true);
        assertThat(access.canRead(element, user)).isTrue();
    }

    @Test
    void onlyOwnerMaintainerOrAdminPublish() {
        when(membership.roleOf(team.getId(), user.getId()))
            .thenReturn(Optional.of(TeamRole.MEMBER));
        assertThat(access.canPublish(team, user)).isFalse();

        when(membership.roleOf(team.getId(), user.getId()))
            .thenReturn(Optional.of(TeamRole.MAINTAINER));
        assertThat(access.canPublish(team, user)).isTrue();

        when(membership.roleOf(team.getId(), user.getId())).thenReturn(Optional.empty());
        user.setAdmin(true);
        assertThat(access.canPublish(team, user)).isTrue();
    }
}
