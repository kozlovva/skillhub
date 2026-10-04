package com.skillhub.application.service;

import com.skillhub.core.exception.UnprocessableException;
import com.skillhub.domain.model.*;
import com.skillhub.domain.port.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SocialUseCaseTest {

    ElementUseCase elementUseCase;
    RatingRepositoryPort ratings;
    ReviewRepositoryPort reviews;
    FavoriteRepositoryPort favorites;
    ClockPort clock;
    SocialUseCase useCase;

    User user = User.builder().id(UUID.randomUUID()).ssoSubject("s").email("e")
        .displayName("U").admin(false).createdAt(Instant.now()).build();
    Element element = Element.builder().id(UUID.randomUUID()).slug("el")
        .type(ElementType.SKILL).name("el").description("")
        .team(Team.builder().id(UUID.randomUUID()).slug("t").name("T")
            .createdAt(Instant.now()).build())
        .tags(new String[0]).visibility(Visibility.PUBLIC).author(user)
        .downloadsCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();

    @BeforeEach
    void setUp() {
        elementUseCase = mock(ElementUseCase.class);
        ratings = mock(RatingRepositoryPort.class);
        reviews = mock(ReviewRepositoryPort.class);
        favorites = mock(FavoriteRepositoryPort.class);
        clock = mock(ClockPort.class);
        when(clock.now()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(elementUseCase.getBySlug("el", user)).thenReturn(element);
        when(ratings.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ratings.avgRating(element.getId())).thenReturn(4.0);
        when(ratings.countByElementId(element.getId())).thenReturn(1L);
        useCase = new SocialUseCase(elementUseCase, ratings, reviews, favorites, clock);
    }

    @Test
    void rateSavesRating() {
        useCase.rate("el", user, 5);
        org.mockito.Mockito.verify(ratings).save(new Rating(element.getId(), user.getId(), 5));
    }

    @Test
    void invalidRatingIsUnprocessable() {
        assertThatThrownBy(() -> useCase.rate("el", user, 9))
            .isInstanceOf(UnprocessableException.class);
    }

    @Test
    void reviewUpserts() {
        when(reviews.findByElementIdAndUserId(element.getId(), user.getId()))
            .thenReturn(Optional.empty());
        useCase.review("el", user, 4, "good");
        org.mockito.Mockito.verify(reviews).save(any(Review.class));
        org.mockito.Mockito.verify(ratings).save(new Rating(element.getId(), user.getId(), 4));

        Review existing = Review.builder().id(UUID.randomUUID())
            .element(element).user(user).rating(3).text("old")
            .createdAt(Instant.now()).build();
        when(reviews.findByElementIdAndUserId(element.getId(), user.getId()))
            .thenReturn(Optional.of(existing));
        useCase.review("el", user, 5, "updated");
        assertThat(existing.getRating()).isEqualTo(5);
        assertThat(existing.getText()).isEqualTo("updated");
        org.mockito.Mockito.verify(ratings).save(new Rating(element.getId(), user.getId(), 5));
    }

    @Test
    void favoriteToggle() {
        when(favorites.findByUserIdAndElementId(user.getId(), element.getId()))
            .thenReturn(Optional.empty());
        assertThat(useCase.setFavorite("el", user, true)).isTrue();
        assertThat(useCase.setFavorite("el", user, false)).isFalse();
    }

    @Test
    void socialInfoReturnsAverageAndCount() {
        when(favorites.findByUserIdAndElementId(user.getId(), element.getId()))
            .thenReturn(Optional.empty());
        SocialUseCase.SocialInfo info = useCase.info("el", user);
        assertThat(info.avgRating()).isEqualTo(4.0);
        assertThat(info.ratingCount()).isEqualTo(1L);
        assertThat(info.favorited()).isFalse();
    }
}
