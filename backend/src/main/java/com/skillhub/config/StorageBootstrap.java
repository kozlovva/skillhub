package com.skillhub.config;

import com.skillhub.adapters.out.s3.S3StorageAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "skillhub.storage", name = "ensure-bucket",
    havingValue = "true", matchIfMissing = true)
@Slf4j
public class StorageBootstrap implements ApplicationRunner {

    private final S3StorageAdapter storage;

    public StorageBootstrap(S3StorageAdapter storage) {
        this.storage = storage;
    }

    @Override
    public void run(ApplicationArguments args) {
        storage.ensureBucket();
        log.info("Object storage bucket is present");
    }
}
