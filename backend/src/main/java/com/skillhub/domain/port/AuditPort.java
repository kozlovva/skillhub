package com.skillhub.domain.port;

import com.skillhub.domain.model.User;

import java.util.UUID;

public interface AuditPort {
    void log(User user, String action, UUID elementId, String detailsJson);
}
