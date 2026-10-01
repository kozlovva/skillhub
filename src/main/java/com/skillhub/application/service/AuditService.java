package com.skillhub.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillhub.domain.model.User;
import com.skillhub.domain.port.AuditPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class AuditService {

    private final AuditPort auditPort;
    private final ObjectMapper mapper = new ObjectMapper();

    public AuditService(AuditPort auditPort) {
        this.auditPort = auditPort;
    }

    public void log(User user, String action, UUID elementId, Map<String, Object> details) {
        try {
            auditPort.log(user, action, elementId,
                mapper.writeValueAsString(details == null ? Map.of() : details));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit details", e);
        }
    }
}
