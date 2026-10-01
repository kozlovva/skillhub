package com.skillhub.adapters.in.rest;

import com.skillhub.adapters.in.rest.dto.CreateElementRequest;
import com.skillhub.adapters.in.rest.dto.ElementResponse;
import com.skillhub.adapters.in.security.CurrentUserResolver;
import com.skillhub.application.service.ElementUseCase;
import com.skillhub.domain.model.ElementType;
import com.skillhub.domain.model.Visibility;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/elements")
@RequiredArgsConstructor
public class ElementController {

    private final ElementUseCase elementUseCase;
    private final CurrentUserResolver currentUser;

    @PostMapping
    public ResponseEntity<ElementResponse> create(@Valid @RequestBody CreateElementRequest req,
                                                  Authentication auth) {
        var cmd = new ElementUseCase.CreateCommand(
            req.slug(), ElementType.valueOf(req.type()), req.name(),
            req.description(), req.team(), req.category(), req.tags(),
            Visibility.valueOf(req.visibility()));
        ElementResponse created = ElementResponse.from(
            elementUseCase.create(cmd, currentUser.resolve(auth)));
        return ResponseEntity.status(HttpStatus.CREATED)
            .location(URI.create("/api/elements/" + created.slug()))
            .body(created);
    }

    @GetMapping("/{slug}")
    public ElementResponse get(@PathVariable String slug, Authentication auth) {
        return ElementResponse.from(elementUseCase.getBySlug(slug, currentUser.resolve(auth)));
    }

    @GetMapping
    public List<ElementResponse> list(Authentication auth) {
        return elementUseCase.listVisible(currentUser.resolve(auth)).stream()
            .map(ElementResponse::from)
            .toList();
    }
}
