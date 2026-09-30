package com.skillhub.adapters.out.jpa;

import com.skillhub.adapters.out.jpa.entity.JpaAuditLog;
import com.skillhub.adapters.out.jpa.entity.JpaUser;
import com.skillhub.adapters.out.jpa.repository.JpaAuditLogRepository;
import com.skillhub.domain.model.User;
import com.skillhub.domain.port.AuditPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class JpaAuditAdapter implements AuditPort {

    private final JpaAuditLogRepository jpa;

    @Override
    @Transactional
    public void log(User user, String action, java.util.UUID elementId, String detailsJson) {
        try {
            JpaUser ref = user == null ? null
                : JpaUser.builder().id(user.getId()).build();
            jpa.save(JpaAuditLog.builder()
                .user(ref)
                .action(action)
                .elementId(elementId)
                .details(detailsJson == null ? "{}" : detailsJson)
                .createdAt(Instant.now())
                .build());
        } catch (Exception e) {
            log.warn("Failed to write audit log", e);
        }
    }
}
