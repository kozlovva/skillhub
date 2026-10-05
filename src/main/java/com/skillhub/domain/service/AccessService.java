package com.skillhub.domain.service;

import com.skillhub.domain.model.*;
import com.skillhub.domain.port.TeamMembershipPort;

import java.util.Optional;

public class AccessService {

    private final TeamMembershipPort membership;

    public AccessService(TeamMembershipPort membership) {
        this.membership = membership;
    }

    public boolean canRead(Element element, User user) {
        if (element.getVisibility() == Visibility.PUBLIC) {
            return true;
        }
        if (element.getTeam() == null) {
            return user != null && user.isAdmin();
        }
        return user != null && (user.isAdmin() || isTeamMember(element.getTeam(), user));
    }

    public boolean canPublish(Team team, User user) {
        if (user == null) {
            return false;
        }
        if (user.isAdmin()) {
            return true;
        }
        return membership.roleOf(team.getId(), user.getId())
            .map(r -> r == TeamRole.OWNER || r == TeamRole.MAINTAINER)
            .orElse(false);
    }

    public boolean canPublishPersonal(Element element, User user) {
        if (user == null) {
            return false;
        }
        return user.isAdmin() || (element.getAuthor() != null
            && user.getId().equals(element.getAuthor().getId()));
    }

    public boolean canDelete(Element element, User user) {
        if (user == null) {
            return false;
        }
        if (user.isAdmin()) {
            return true;
        }
        if (element.getTeam() == null) {
            return element.getAuthor() != null
                && user.getId().equals(element.getAuthor().getId());
        }
        return membership.roleOf(element.getTeam().getId(), user.getId())
            .map(r -> r == TeamRole.OWNER)
            .orElse(false);
    }

    public boolean isTeamMember(Team team, User user) {
        return membership.roleOf(team.getId(), user.getId()).isPresent();
    }
}
