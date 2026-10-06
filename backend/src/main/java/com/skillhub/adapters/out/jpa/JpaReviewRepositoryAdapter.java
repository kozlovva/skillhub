package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.mapper.ReviewJpaMapper;
import com.skillhub.adapters.out.jpa.repository.JpaReviewRepository;
import com.skillhub.domain.model.Review;
import com.skillhub.domain.port.ReviewRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaReviewRepositoryAdapter implements ReviewRepositoryPort {

    private final JpaReviewRepository jpa;

    @Override
    @Transactional
    public Review save(Review review) {
        return ReviewJpaMapper.toDomain(jpa.save(ReviewJpaMapper.toEntity(review)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Review> findByElementIdAndUserId(UUID elementId, UUID userId) {
        return jpa.findByElementIdAndUserId(elementId, userId).map(ReviewJpaMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Review> findAllByElementIdOrderByCreatedAtDesc(UUID elementId) {
        return jpa.findAllByElementIdOrderByCreatedAtDesc(elementId).stream()
            .map(ReviewJpaMapper::toDomain)
            .toList();
    }
}
