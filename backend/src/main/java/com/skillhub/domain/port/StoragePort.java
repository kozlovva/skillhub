package com.skillhub.domain.port;

import com.skillhub.domain.model.StorageObjectInfo;

import java.time.Duration;
import java.util.List;

public interface StoragePort {
    void upload(String key, byte[] content);
    byte[] download(String key);
    void delete(String key);
    String presignedGetUrl(String key, Duration ttl);
    List<StorageObjectInfo> list();
}
