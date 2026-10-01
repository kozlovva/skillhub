package com.skillhub.adapters.out.jpa.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pack_contents")
@IdClass(JpaPackContentId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JpaPackContent {
    @Id @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "pack_element_id")
    private JpaElement packElement;
    @Id @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "element_id")
    private JpaElement element;
    @Column(name = "version_constraint", nullable = false) private String versionConstraint;
}
