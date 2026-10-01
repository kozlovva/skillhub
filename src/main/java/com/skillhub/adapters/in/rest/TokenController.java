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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tokens")
@RequiredArgsConstructor
public class TokenController {

    public record CreateTokenRequest(String name) {}
    public record TokenItem(String name, String createdAt, String lastUsedAt, String expiresAt) {}

    private final ApiTokenService tokenService;
    private final ApiTokenRepositoryPort tokens;
    private final CurrentUserResolver currentUser;

    @PostMapping
    public ResponseEntity<Map<String, String>> create(@RequestBody CreateTokenRequest req,
                                                      Authentication auth) {
        User user = currentUser.resolve(auth);
        ApiTokenService.CreatedToken created = tokenService.createToken(user, req.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "token", created.rawToken(),
            "name", created.token().getName()));
    }

    @GetMapping
    public List<TokenItem> list(Authentication auth) {
        User user = currentUser.resolve(auth);
        return tokens.findByUserId(user.getId()).stream()
            .map(t -> new TokenItem(t.getName(), t.getCreatedAt().toString(),
                t.getLastUsedAt() == null ? null : t.getLastUsedAt().toString(),
                t.getExpiresAt() == null ? null : t.getExpiresAt().toString()))
            .toList();
    }
}
