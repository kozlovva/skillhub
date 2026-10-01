package com.skillhub.domain.port;

import java.time.Duration;

public interface StoragePort {
    void upload(String key, byte[] content);
    byte[] download(String key);
    void delete(String key);
    String presignedGetUrl(String key, Duration ttl);
}
