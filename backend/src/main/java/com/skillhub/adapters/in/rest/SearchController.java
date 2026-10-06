package com.skillhub.adapters.in.rest;

import com.skillhub.adapters.in.rest.dto.ElementResponse;
import com.skillhub.adapters.in.rest.dto.SearchResultResponse;
import com.skillhub.adapters.in.security.CurrentUserResolver;
import com.skillhub.application.dto.SortBy;
import com.skillhub.application.dto.SortOrder;
import com.skillhub.application.service.SearchUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
                                       @RequestParam(value = "sort", defaultValue = "relevance") String sort,
                                       @RequestParam(value = "order", defaultValue = "desc") String order,
                                       @RequestParam(value = "limit", defaultValue = "20") int limit,
                                       @RequestParam(value = "offset", defaultValue = "0") int offset,
                                       Authentication auth) {
        SortBy sortBy;
        SortOrder sortOrder;
        try {
            sortBy = SortBy.fromString(sort);
            sortOrder = SortOrder.fromString(order);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        var result = searchUseCase.search(q, type, category, sortBy, sortOrder,
            currentUser.resolve(auth), limit, offset);
        List<ElementResponse> items = result.items().stream()
            .map(e -> ElementResponse.from(e, result.ratings().get(e.getId())))
            .toList();
        return new SearchResultResponse(items, result.total(), result.facetsByType());
    }
}
