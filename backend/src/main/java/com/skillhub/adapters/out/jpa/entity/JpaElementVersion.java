package com.skillhub.adapters.out.jpa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "element_versions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JpaElementVersion {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "element_id", nullable = false)
    private JpaElement element;
    @Column(nullable = false) private String version;
    @Column(nullable = false) private String status;
    @Column(nullable = false) private String changelog;
    @Column(name = "s3_key", nullable = false) private String s3Key;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "file_index", nullable = false, columnDefinition = "jsonb")
    private String fileIndex;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "published_by", nullable = false)
    private JpaUser publishedBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "deleted_at") private Instant deletedAt;
}
