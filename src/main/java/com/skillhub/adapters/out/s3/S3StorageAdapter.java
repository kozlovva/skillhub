package com.skillhub.adapters.out.s3;

import com.skillhub.domain.port.StoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.time.Duration;

@Component
public class S3StorageAdapter implements StoragePort {

    private final String bucket;
    private final S3Client client;
    private final S3Presigner presigner;

    public S3StorageAdapter(@Value("${skillhub.storage.endpoint}") String endpoint,
                            @Value("${skillhub.storage.presign-endpoint:}") String presignEndpoint,
                            @Value("${skillhub.storage.access-key}") String accessKey,
                            @Value("${skillhub.storage.secret-key}") String secretKey,
                            @Value("${skillhub.storage.bucket}") String bucket) {
        this.bucket = bucket;
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKey, secretKey));
        this.client = S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.US_EAST_1)
            .credentialsProvider(credentials)
            .forcePathStyle(true)
            .build();
        this.presigner = S3Presigner.builder()
            .endpointOverride(URI.create(presignEndpoint.isBlank() ? endpoint : presignEndpoint))
            .region(Region.US_EAST_1)
            .credentialsProvider(credentials)
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build())
            .build();
    }

    public void ensureBucket() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            } else {
                throw e;
            }
        }
    }

    @Override
    public void upload(String key, byte[] content) {
        client.putObject(PutObjectRequest.builder()
            .bucket(bucket).key(key).build(), RequestBody.fromBytes(content));
    }

    @Override
    public byte[] download(String key) {
        return client.getObjectAsBytes(GetObjectRequest.builder()
            .bucket(bucket).key(key).build()).asByteArray();
    }

    @Override
    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder()
            .bucket(bucket).key(key).build());
    }

    @Override
    public String presignedGetUrl(String key, Duration ttl) {
        PresignedGetObjectRequest request = presigner.presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder()
                    .bucket(bucket).key(key).build())
                .build());
        return request.url().toString();
    }
}
