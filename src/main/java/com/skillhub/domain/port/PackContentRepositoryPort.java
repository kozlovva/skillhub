package com.skillhub.domain.port;

import com.skillhub.domain.model.PackContent;

import java.util.List;
import java.util.UUID;

public interface PackContentRepositoryPort {
    PackContent save(PackContent content);
    List<PackContent> findAllByPackId(UUID packId);
}
