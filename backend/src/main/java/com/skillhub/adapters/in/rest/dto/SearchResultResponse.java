package com.skillhub.adapters.in.rest.dto;

import java.util.List;
import java.util.Map;

public record SearchResultResponse(List<ElementResponse> items, long total,
                                   Map<String, Long> facetsByType) {}
