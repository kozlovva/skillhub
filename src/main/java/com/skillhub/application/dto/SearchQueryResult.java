package com.skillhub.application.dto;

import com.skillhub.domain.model.Element;

import java.util.List;
import java.util.Map;

public record SearchQueryResult(List<Element> items, long total,
                                Map<String, Long> facetsByType) {}
