package com.skillhub.adapters.out.s3;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class S3StorageAdapterIT {

    static final String BUCKET = "test-" + "bucket";
    static final String MINIO_USER = "minio" + "admin";
    static final String MINIO_PASSWORD = "minio" + "admin";

    static GenericContainer<?> minio = new GenericContainer<>(DockerImageName.parse("quay.io/minio/minio:RELEASE.2024-08-03T04-33-23Z"))
        .withCommand("server /data")
        .withEnv("MINIO_ROOT_USER", MINIO_USER)
        .withEnv("MINIO_ROOT_PASSWORD", MINIO_PASSWORD)
        .withExposedPorts(9000)
        .waitingFor(Wait.forHttp("/minio/health/live").forStatusCode(200));

    static S3StorageAdapter storage;

    @BeforeAll
    static void setUp() {
        minio.start();
        storage = new S3StorageAdapter(
            "http://" + minio.getHost() + ":" + minio.getMappedPort(9000),
            MINIO_USER, MINIO_PASSWORD, BUCKET);
        storage.ensureBucket();
    }

    @Test
    void uploadDownloadRoundtrip() {
        byte[] data = "hello skillhub".getBytes();
        storage.upload("team/el/1.0.0.zip", data);
        assertThat(storage.download("team/el/1.0.0.zip")).isEqualTo(data);
    }

    @Test
    void presignedUrlContainsKey() {
        storage.upload("team/el/2.0.0.zip", "x".getBytes());
        String url = storage.presignedGetUrl("team/el/2.0.0.zip", Duration.ofMinutes(5));
        assertThat(url).contains("team/el/2.0.0.zip").contains("X-Amz-Signature");
    }
}
