package com.skillhub.domain.model;

import java.time.Instant;

public record StorageObjectInfo(String key, Instant lastModified) {}
