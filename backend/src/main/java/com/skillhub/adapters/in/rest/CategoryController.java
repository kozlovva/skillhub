package com.skillhub.adapters.in.rest;

import com.skillhub.adapters.in.rest.dto.CategoryResponse;
import com.skillhub.adapters.in.rest.dto.CreateCategoryRequest;
import com.skillhub.adapters.in.security.CurrentUserResolver;
import com.skillhub.application.service.CategoryUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryUseCase categoryUseCase;
    private final CurrentUserResolver currentUser;

    @GetMapping
    public List<CategoryResponse> list() {
        return categoryUseCase.list().stream().map(CategoryResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest req,
                                                   Authentication auth) {
        CategoryResponse created = CategoryResponse.from(categoryUseCase.create(
            req.slug(), req.name(), req.parentSlug(), req.icon(), currentUser.resolve(auth)));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
