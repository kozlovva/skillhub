package com.skillhub.adapters.out.s3;

import com.skillhub.domain.model.StorageObjectInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.time.Duration;
import java.util.List;

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
            "", MINIO_USER, MINIO_PASSWORD, BUCKET);
        storage.ensureBucket();
    }

    @Test
    void uploadDownloadRoundtrip() {
        byte[] data = "hello skillhub".getBytes();
        storage.upload("team/el/1.0.0.zip", data);
        assertThat(storage.download("team/el/1.0.0.zip")).isEqualTo(data);
    }

    @Test
    void presignedUrlDownloadsContent() throws Exception {
        byte[] data = "presigned-content".getBytes();
        storage.upload("team/el/2.0.0.zip", data);
        String url = storage.presignedGetUrl("team/el/2.0.0.zip", Duration.ofMinutes(5));
        assertThat(url).contains("X-Amz-Signature");
        assertThat(url).doesNotContain(BUCKET + ".");

        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpResponse<byte[]> response = httpClient.send(
            java.net.http.HttpRequest.newBuilder(URI.create(url)).GET().build(),
            java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(data);
    }

    @Test
    void listReturnsAllObjectsWithKeysAndTimestamps() {
        storage.upload("team/list-el/1.0.0.zip", "a".getBytes());
        storage.upload("personal/list-el/1.0.0.zip", "b".getBytes());

        List<StorageObjectInfo> objects = storage.list();

        assertThat(objects).extracting(StorageObjectInfo::key)
            .contains("team/list-el/1.0.0.zip", "personal/list-el/1.0.0.zip");
        assertThat(objects).allSatisfy(o -> assertThat(o.lastModified()).isNotNull());
    }

    @Test
    void listPaginatesAcrossMultiplePages() {
        for (int i = 0; i < 1005; i++) {
            storage.upload("team/paginate/" + i + ".zip", new byte[] {1});
        }
        long count = storage.list().stream()
            .map(StorageObjectInfo::key)
            .filter(k -> k.startsWith("team/paginate/"))
            .distinct()
            .count();
        assertThat(count).isEqualTo(1005);
    }
}
