package com.skillhub.adapters.out.jpa.entity;

import lombok.*;
import java.io.Serializable;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class JpaRatingId implements Serializable {
    private UUID elementId;
    private UUID userId;
}
