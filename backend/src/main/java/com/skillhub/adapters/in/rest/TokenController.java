package com.skillhub.adapters.in.rest;

import com.skillhub.adapters.in.security.CurrentUserResolver;
import com.skillhub.application.service.ApiTokenService;
import com.skillhub.domain.model.ApiToken;
import com.skillhub.domain.model.User;
import com.skillhub.domain.port.ApiTokenRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/tokens")
@RequiredArgsConstructor
public class TokenController {

    public record CreateTokenRequest(String name, Integer lifetimeDays) {}
    public record TokenItem(UUID id, String name, String createdAt,
                            String lastUsedAt, String expiresAt, String revokedAt) {}

    private final ApiTokenService tokenService;
    private final ApiTokenRepositoryPort tokens;
    private final CurrentUserResolver currentUser;

    @PostMapping
    public ResponseEntity<Map<String, String>> create(@RequestBody CreateTokenRequest req,
                                                      Authentication auth) {
        User user = currentUser.resolve(auth);
        ApiTokenService.CreatedToken created = tokenService.createToken(user, req.name(), req.lifetimeDays());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "token", created.rawToken(),
            "name", created.token().getName()));
    }

    @GetMapping
    public List<TokenItem> list(Authentication auth) {
        User user = currentUser.resolve(auth);
        return tokens.findByUserId(user.getId()).stream()
            .map(t -> new TokenItem(t.getId(), t.getName(), t.getCreatedAt().toString(),
                t.getLastUsedAt() == null ? null : t.getLastUsedAt().toString(),
                t.getExpiresAt() == null ? null : t.getExpiresAt().toString(),
                t.getRevokedAt() == null ? null : t.getRevokedAt().toString()))
            .toList();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable UUID id, Authentication auth) {
        User user = currentUser.resolve(auth);
        try {
            tokenService.revokeToken(user, id);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Token not found");
        }
        return ResponseEntity.noContent().build();
    }
}
