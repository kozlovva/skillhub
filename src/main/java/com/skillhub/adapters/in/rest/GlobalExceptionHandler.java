package com.skillhub.adapters.in.rest;

import com.skillhub.core.exception.ConflictException;
import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.core.exception.NotFoundException;
import com.skillhub.core.exception.UnprocessableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of("CONFLICT", e.getMessage()));
    }

    @ExceptionHandler(UnprocessableException.class)
    public ResponseEntity<ErrorResponse> handleUnprocessable(UnprocessableException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(new ErrorResponse("UNPROCESSABLE", e.getMessage(), e.getDetails()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse.of("FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse.of("ARCHIVE_TOO_LARGE", "Upload exceeds 50MB limit"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOther(Exception e) {
        log.error("Unhandled error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of("INTERNAL", "Unexpected error"));
    }
}
