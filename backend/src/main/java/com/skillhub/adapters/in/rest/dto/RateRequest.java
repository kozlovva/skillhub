package com.skillhub.adapters.in.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RateRequest(@Min(1) @Max(5) int rating) {}
