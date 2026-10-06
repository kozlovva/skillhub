package com.skillhub.application.service;

import com.skillhub.core.exception.UnprocessableException;
import com.skillhub.domain.model.*;
import com.skillhub.domain.port.ClockPort;
import com.skillhub.domain.port.ElementRepositoryPort;
import com.skillhub.domain.port.FavoriteRepositoryPort;
import com.skillhub.domain.port.RatingRepositoryPort;
import com.skillhub.domain.port.ReviewRepositoryPort;
import com.skillhub.domain.service.AccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class SocialUseCase {

    public record SocialInfo(double avgRating, long ratingCount, boolean favorited) {}

    private final ElementUseCase elementUseCase;
    private final RatingRepositoryPort ratings;
    private final ReviewRepositoryPort reviews;
    private final FavoriteRepositoryPort favorites;
    private final ClockPort clock;
    private final ElementRepositoryPort elements;
    private final AccessService access;

    public SocialUseCase(ElementUseCase elementUseCase, RatingRepositoryPort ratings,
                         ReviewRepositoryPort reviews, FavoriteRepositoryPort favorites,
                         ClockPort clock, ElementRepositoryPort elements, AccessService access) {
        this.elementUseCase = elementUseCase;
        this.ratings = ratings;
        this.reviews = reviews;
        this.favorites = favorites;
        this.clock = clock;
        this.elements = elements;
        this.access = access;
    }

    @Transactional
    public void rate(String slug, User user, int rating) {
        validateRating(rating);
        Element element = elementUseCase.getBySlug(slug, user);
        ratings.save(new Rating(element.getId(), user.getId(), rating));
    }

    @Transactional
    public void review(String slug, User user, int rating, String text) {
        validateRating(rating);
        Element element = elementUseCase.getBySlug(slug, user);
        reviews.findByElementIdAndUserId(element.getId(), user.getId())
            .ifPresentOrElse(existing -> {
                existing.setRating(rating);
                existing.setText(text);
                reviews.save(existing);
            }, () -> reviews.save(Review.builder()
                .element(element).user(user).rating(rating).text(text)
                .createdAt(clock.now()).build()));
        ratings.save(new Rating(element.getId(), user.getId(), rating));
    }

    @Transactional(readOnly = true)
    public List<Review> reviews(String slug, User viewer) {
        Element element = elementUseCase.getBySlug(slug, viewer);
        return reviews.findAllByElementIdOrderByCreatedAtDesc(element.getId());
    }

    @Transactional
    public boolean setFavorite(String slug, User user, boolean add) {
        Element element = elementUseCase.getBySlug(slug, user);
        if (add) {
            favorites.save(new Favorite(user.getId(), element.getId(), clock.now()));
        } else {
            favorites.delete(new Favorite(user.getId(), element.getId(), clock.now()));
        }
        return add;
    }

    @Transactional(readOnly = true)
    public SocialInfo info(String slug, User viewer) {
        Element element = elementUseCase.getBySlug(slug, viewer);
        double avg = ratings.avgRating(element.getId());
        long count = ratings.countByElementId(element.getId());
        boolean favorited = viewer != null
            && favorites.findByUserIdAndElementId(viewer.getId(), element.getId()).isPresent();
        return new SocialInfo(avg, count, favorited);
    }

    @Transactional(readOnly = true)
    public List<Element> favorites(User viewer) {
        return favorites.findAllByUserId(viewer.getId()).stream()
            .map(f -> elements.findById(f.elementId()))
            .flatMap(Optional::stream)
            .filter(e -> e.getDeletedAt() == null)
            .filter(e -> access.canRead(e, viewer))
            .toList();
    }

    private void validateRating(int rating) {
        if (rating < 1 || rating > 5) {
            throw new UnprocessableException("Rating must be between 1 and 5", rating);
        }
    }
}
