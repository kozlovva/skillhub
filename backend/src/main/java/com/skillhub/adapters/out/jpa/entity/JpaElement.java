package com.skillhub.adapters.out.jpa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "elements")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JpaElement {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true) private String slug;
    @Column(nullable = false) private String type;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String description;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "team_id")
    private JpaTeam team;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "category_id")
    private JpaCategory category;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] tags;
    @Column(nullable = false) private String visibility;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "author_id", nullable = false)
    private JpaUser author;
    @Column(name = "latest_version") private String latestVersion;
    @Column(name = "latest_changelog", nullable = false) private String latestChangelog = "";
    @Column(name = "downloads_count", nullable = false) private long downloadsCount;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;
}
