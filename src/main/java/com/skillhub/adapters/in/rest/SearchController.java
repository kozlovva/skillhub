package com.skillhub.adapters.in.rest;

import com.skillhub.adapters.in.rest.dto.ElementResponse;
import com.skillhub.adapters.in.rest.dto.SearchResultResponse;
import com.skillhub.adapters.in.security.CurrentUserResolver;
import com.skillhub.application.service.SearchUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchUseCase searchUseCase;
    private final CurrentUserResolver currentUser;

    @GetMapping
    public SearchResultResponse search(@RequestParam(value = "q", defaultValue = "") String q,
                                       @RequestParam(value = "type", required = false) String type,
                                       @RequestParam(value = "category", required = false) String category,
                                       @RequestParam(value = "limit", defaultValue = "20") int limit,
                                       @RequestParam(value = "offset", defaultValue = "0") int offset,
                                       Authentication auth) {
        var result = searchUseCase.search(q, type, category, currentUser.resolve(auth), limit, offset);
        List<ElementResponse> items = result.items().stream()
            .map(e -> ElementResponse.from(e, result.ratings().get(e.getId())))
            .toList();
        return new SearchResultResponse(items, result.total(), result.facetsByType());
    }
}
