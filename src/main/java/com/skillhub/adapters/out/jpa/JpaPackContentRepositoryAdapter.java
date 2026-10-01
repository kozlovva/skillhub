package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.entity.JpaElement;
import com.skillhub.adapters.out.jpa.entity.JpaPackContent;
import com.skillhub.adapters.out.jpa.repository.JpaElementRepository;
import com.skillhub.adapters.out.jpa.repository.JpaPackContentRepository;
import com.skillhub.domain.model.Element;
import com.skillhub.domain.model.PackContent;
import com.skillhub.domain.port.PackContentRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaPackContentRepositoryAdapter implements PackContentRepositoryPort {

    private final JpaPackContentRepository jpa;
    private final JpaElementRepository elementRepository;

    @Override
    @Transactional
    public PackContent save(PackContent content) {
        JpaElement pack = elementRepository.getReferenceById(content.getPackElement().getId());
        JpaElement element = elementRepository.getReferenceById(content.getElement().getId());
        jpa.save(JpaPackContent.builder()
            .packElement(pack)
            .element(element)
            .versionConstraint(content.getVersionConstraint())
            .build());
        return content;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PackContent> findAllByPackId(UUID packId) {
        return jpa.findAllByPackElementId(packId).stream()
            .map(this::toDomain)
            .toList();
    }

    private PackContent toDomain(JpaPackContent e) {
        return PackContent.builder()
            .packElement(com.skillhub.adapters.out.jpa.mapper.ElementJpaMapper.toDomain(e.getPackElement()))
            .element(com.skillhub.adapters.out.jpa.mapper.ElementJpaMapper.toDomain(e.getElement()))
            .versionConstraint(e.getVersionConstraint())
            .build();
    }
}
