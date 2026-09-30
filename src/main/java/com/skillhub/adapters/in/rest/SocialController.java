package com.skillhub.adapters.in.rest;

import com.skillhub.adapters.in.rest.dto.RateRequest;
import com.skillhub.adapters.in.rest.dto.ReviewRequest;
import com.skillhub.adapters.in.rest.dto.ReviewResponse;
import com.skillhub.adapters.in.security.CurrentUserResolver;
import com.skillhub.application.service.SocialUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/elements/{slug}")
@RequiredArgsConstructor
public class SocialController {

    private final SocialUseCase socialUseCase;
    private final CurrentUserResolver currentUser;

    @PutMapping("/rating")
    public Map<String, Integer> rate(@PathVariable String slug,
                                     @Valid @RequestBody RateRequest request,
                                     Authentication auth) {
        socialUseCase.rate(slug, currentUser.resolve(auth), request.rating());
        return Map.of("rating", request.rating());
    }

    @PutMapping("/review")
    public Map<String, Integer> review(@PathVariable String slug,
                                       @Valid @RequestBody ReviewRequest request,
                                       Authentication auth) {
        socialUseCase.review(slug, currentUser.resolve(auth),
            request.rating(), request.text());
        return Map.of("rating", request.rating());
    }

    @GetMapping("/reviews")
    public List<ReviewResponse> reviews(@PathVariable String slug, Authentication auth) {
        return socialUseCase.reviews(slug, currentUser.resolve(auth)).stream()
            .map(ReviewResponse::from)
            .toList();
    }

    @PostMapping("/favorite")
    public Map<String, Boolean> favorite(@PathVariable String slug, Authentication auth) {
        return Map.of("favorited",
            socialUseCase.setFavorite(slug, currentUser.resolve(auth), true));
    }

    @DeleteMapping("/favorite")
    public Map<String, Boolean> unfavorite(@PathVariable String slug, Authentication auth) {
        return Map.of("favorited",
            socialUseCase.setFavorite(slug, currentUser.resolve(auth), false));
    }

    @GetMapping("/social")
    public Map<String, Object> social(@PathVariable String slug, Authentication auth) {
        SocialUseCase.SocialInfo info = socialUseCase.info(slug, currentUser.resolve(auth));
        return Map.of("avgRating", info.avgRating(),
            "ratingCount", info.ratingCount(), "favorited", info.favorited());
    }
}
