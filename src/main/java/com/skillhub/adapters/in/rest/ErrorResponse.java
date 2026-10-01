package com.skillhub.adapters.in.rest;

public record ErrorResponse(String code, String message, Object details) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null);
    }
}
