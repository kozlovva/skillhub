package com.skillhub.application.service;

import com.skillhub.domain.model.StorageObjectInfo;
import com.skillhub.domain.port.ClockPort;
import com.skillhub.domain.port.ElementVersionRepositoryPort;
import com.skillhub.domain.port.StoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Service
@ConditionalOnProperty(name = "skillhub.storage.reconcile-enabled",
    havingValue = "true", matchIfMissing = true)
public class StorageReconciliationService {

    private static final Logger log =
        LoggerFactory.getLogger(StorageReconciliationService.class);

    private final StoragePort storage;
    private final ElementVersionRepositoryPort versions;
    private final ClockPort clock;
    private final Duration grace;

    public StorageReconciliationService(StoragePort storage,
                                        ElementVersionRepositoryPort versions,
                                        ClockPort clock,
                                        @Value("${skillhub.storage.reconcile-grace:24h}")
                                        Duration grace) {
        this.storage = storage;
        this.versions = versions;
        this.clock = clock;
        this.grace = grace;
    }

    @Scheduled(cron = "${skillhub.storage.reconcile-cron:0 0 * * * *}")
    public void reconcile() {
        try {
            Set<String> known = versions.findAllS3Keys();
            Instant threshold = clock.now().minus(grace);
            for (StorageObjectInfo object : storage.list()) {
                if (known.contains(object.key()) || object.lastModified().isAfter(threshold)) {
                    continue;
                }
                try {
                    storage.delete(object.key());
                    log.info("Deleted orphaned storage object: {}", object.key());
                } catch (RuntimeException e) {
                    log.warn("Failed to delete orphaned storage object: {} ({})",
                        object.key(), e.getMessage());
                }
            }
        } catch (RuntimeException e) {
            log.error("S3 orphan reconciliation failed: {}", e.getMessage());
        }
    }
}
