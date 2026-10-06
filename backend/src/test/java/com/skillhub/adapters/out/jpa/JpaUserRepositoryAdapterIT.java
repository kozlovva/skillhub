package com.skillhub.adapters.out.jpa;

import com.skillhub.domain.model.*;
import com.skillhub.domain.port.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class JpaUserRepositoryAdapterIT {

    @Autowired UserRepositoryPort users;
    @Autowired TeamRepositoryPort teams;
    @Autowired TeamMembershipPort membership;

    private User user(String subject, String username, String displayName) {
        return users.save(User.builder()
            .ssoSubject(subject).email(subject + "@b.c").username(username)
            .displayName(displayName).admin(false).createdAt(Instant.now()).build());
    }

    private Team team(String slug) {
        return teams.save(Team.builder().slug(slug).name(slug)
            .createdAt(Instant.now()).build());
    }

    @Test
    void findByIdReturnsSavedUser() {
        User saved = user("find-sub", "finduser", "Find User");
        assertThat(users.findById(saved.getId())).hasValueSatisfying(
            u -> assertThat(u.getUsername()).isEqualTo("finduser"));
        assertThat(users.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void searchFindsByUsernameAndDisplayNameCaseInsensitive() {
        user("s1", "vpetrov", "Иванов Пётр");
        user("s2", "sidorov", "Vasily Petrov");

        List<User> byUsername = users.searchCandidates("PET", null, 10);
        List<User> byName = users.searchCandidates("petrov", null, 10);

        assertThat(byUsername).extracting(User::getUsername)
            .containsExactlyInAnyOrder("vpetrov", "sidorov");
        assertThat(byName).extracting(User::getUsername)
            .containsExactlyInAnyOrder("vpetrov", "sidorov");
    }

    @Test
    void searchExcludesTeamMembers() {
        String marker = UUID.randomUUID().toString();
        User member = user("m1", "member1-" + marker, "Member One");
        User outsider = user("o1", "outsider1-" + marker, "Outsider One");
        Team t = team("search-team");
        membership.save(TeamMembership.builder()
            .teamId(t.getId()).userId(member.getId()).role(TeamRole.MEMBER).build());

        List<User> result = users.searchCandidates("1-" + marker, t.getId(), 10);

        assertThat(result).extracting(User::getUsername).containsExactly(outsider.getUsername());
    }

    @Test
    void searchRespectsLimit() {
        user("l1", "aa1", "L1");
        user("l2", "aa2", "L2");
        user("l3", "aa3", "L3");

        List<User> result = users.searchCandidates("aa", null, 2);

        assertThat(result).hasSize(2);
    }
}
