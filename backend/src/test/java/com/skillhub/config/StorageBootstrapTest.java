package com.skillhub.config;

import com.skillhub.adapters.out.s3.S3StorageAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StorageBootstrapTest {

    @Test
    void runEnsuresBucketExists() {
        S3StorageAdapter storage = mock(S3StorageAdapter.class);

        new StorageBootstrap(storage).run(mock(ApplicationArguments.class));

        verify(storage).ensureBucket();
    }
}
