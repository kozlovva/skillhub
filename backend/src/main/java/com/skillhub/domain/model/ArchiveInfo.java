package com.skillhub.domain.model;

import java.util.List;

public record ArchiveInfo(String manifestName, String manifestVersion,
                          String manifestDescription, String manifestType,
                          List<FileEntry> files, long totalSize) {}
