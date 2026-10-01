package com.skillhub.adapters.out.jpa.entity;

import lombok.*;
import java.io.Serializable;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class JpaPackContentId implements Serializable {
    private UUID packElement;
    private UUID element;
}
