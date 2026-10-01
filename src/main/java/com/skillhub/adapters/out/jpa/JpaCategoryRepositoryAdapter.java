package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.mapper.CategoryJpaMapper;
import com.skillhub.adapters.out.jpa.repository.JpaCategoryRepository;
import com.skillhub.domain.model.Category;
import com.skillhub.domain.port.CategoryRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JpaCategoryRepositoryAdapter implements CategoryRepositoryPort {

    private final JpaCategoryRepository jpa;

    @Override
    public Category save(Category category) {
        return CategoryJpaMapper.toDomain(jpa.save(CategoryJpaMapper.toEntity(category)));
    }

    @Override
    public Optional<Category> findBySlug(String slug) {
        return jpa.findBySlug(slug).map(CategoryJpaMapper::toDomain);
    }

    @Override
    public boolean existsBySlug(String slug) {
        return jpa.existsBySlug(slug);
    }

    @Override
    public List<Category> findAllOrderedByName() {
        return jpa.findAllByOrderByNameAsc().stream()
            .map(CategoryJpaMapper::toDomain)
            .toList();
    }
}
