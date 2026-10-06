package com.skillhub.adapters.out.jpa.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "team_members")
@IdClass(JpaTeamMember.JpaTeamMemberId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JpaTeamMember {
    @Id @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "team_id")
    private JpaTeam team;
    @Id @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id")
    private JpaUser user;
    @Column(nullable = false) private String role;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class JpaTeamMemberId implements java.io.Serializable {
        private java.util.UUID team;
        private java.util.UUID user;
    }
}
