package com.skillhub.core.exception;

public class UnprocessableException extends RuntimeException {
    private final Object details;
    public UnprocessableException(String message, Object details) {
        super(message);
        this.details = details;
    }
    public Object getDetails() { return details; }
}
