package com.skillhub.adapters.in.rest.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillhub.domain.model.ElementVersion;

import java.util.ArrayList;
import java.util.List;

public record VersionResponse(String version, String status, String changelog,
                              long sizeBytes, List<FileDto> files) {

    public record FileDto(String path, long size) {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static VersionResponse from(ElementVersion v) {
        List<FileDto> files = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(v.getFileIndex());
            for (JsonNode node : root.path("files")) {
                files.add(new FileDto(node.path("path").asText(), node.path("size").asLong()));
            }
        } catch (Exception ignored) {
        }
        return new VersionResponse(v.getVersion(), v.getStatus().name(),
            v.getChangelog(), v.getSizeBytes(), files);
    }
}
