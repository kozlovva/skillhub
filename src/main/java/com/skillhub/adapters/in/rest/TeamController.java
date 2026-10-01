package com.skillhub.adapters.in.rest;

import com.skillhub.adapters.in.rest.dto.AddMemberRequest;
import com.skillhub.adapters.in.rest.dto.CreateTeamRequest;
import com.skillhub.adapters.in.rest.dto.TeamResponse;
import com.skillhub.adapters.in.security.CurrentUserResolver;
import com.skillhub.application.service.TeamUseCase;
import com.skillhub.domain.model.TeamMembership;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamUseCase teamUseCase;
    private final CurrentUserResolver currentUser;

    @GetMapping
    public List<TeamResponse> list() {
        return teamUseCase.list().stream().map(TeamResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<TeamResponse> create(@Valid @RequestBody CreateTeamRequest req,
                                               Authentication auth) {
        TeamResponse created = TeamResponse.from(
            teamUseCase.create(req.slug(), req.name(), currentUser.resolve(auth)));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{slug}/members")
    public ResponseEntity<Map<String, String>> addMember(@PathVariable String slug,
                                                         @Valid @RequestBody AddMemberRequest req,
                                                         Authentication auth) {
        TeamMembership member = teamUseCase.addMember(
            slug, req.ssoSubject(), req.role(), currentUser.resolve(auth));
        return ResponseEntity.ok(Map.of(
            "ssoSubject", member.userId().toString(),
            "role", member.role().name()));
    }
}
