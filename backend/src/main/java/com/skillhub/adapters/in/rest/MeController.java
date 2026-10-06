package com.skillhub.adapters.in.rest;

import com.skillhub.adapters.in.rest.dto.ElementResponse;
import com.skillhub.adapters.in.security.CurrentUserResolver;
import com.skillhub.application.service.SocialUseCase;
import com.skillhub.domain.model.Element;
import com.skillhub.domain.model.User;
import com.skillhub.domain.model.UserTeamRole;
import com.skillhub.domain.port.TeamMembershipPort;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.util.List;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    public record TeamRoleItem(String slug, String name, String role) {}
    public record MeResponse(String username, String userId, boolean admin,
                             List<TeamRoleItem> teams) {}

    private final CurrentUserResolver currentUser;
    private final TeamMembershipPort memberships;
    private final SocialUseCase socialUseCase;

    @GetMapping
    public MeResponse me(Authentication auth) {
        User user = currentUser.resolve(auth);
        List<TeamRoleItem> teams = memberships.teamsOfUser(user.getId()).stream()
            .map(MeController::toItem)
            .toList();
        return new MeResponse(user.getDisplayName(), user.getId().toString(),
            user.isAdmin(), teams);
    }

    @GetMapping("/favorites")
    public List<ElementResponse> favorites(Authentication auth) {
        return socialUseCase.favorites(currentUser.resolve(auth)).stream()
            .map(ElementResponse::from)
            .toList();
    }

    private static TeamRoleItem toItem(UserTeamRole t) {
        return new TeamRoleItem(t.teamSlug(), t.teamName(), t.role().name());
    }
}
