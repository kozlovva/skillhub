package com.skillhub.adapters.in.rest;

import com.skillhub.core.exception.ConflictException;
import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.core.exception.NotFoundException;
import com.skillhub.core.exception.UnprocessableException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void notFoundMapsTo404() {
        ResponseEntity<ErrorResponse> r = handler.handleNotFound(new NotFoundException("no element"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(r.getBody().code()).isEqualTo("NOT_FOUND");
        assertThat(r.getBody().message()).isEqualTo("no element");
    }

    @Test
    void conflictMapsTo409() {
        ResponseEntity<ErrorResponse> r = handler.handleConflict(new ConflictException("dup"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody().code()).isEqualTo("CONFLICT");
    }

    @Test
    void unprocessableMapsTo422() {
        ResponseEntity<ErrorResponse> r = handler.handleUnprocessable(
            new UnprocessableException("bad manifest", "field=version"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(r.getBody().code()).isEqualTo("UNPROCESSABLE");
        assertThat(r.getBody().details()).isEqualTo("field=version");
    }

    @Test
    void dataIntegrityViolationMapsTo409() {
        ResponseEntity<ErrorResponse> r = handler.handleDataIntegrityViolation(
            new DataIntegrityViolationException("duplicate key value violates unique constraint"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody().code()).isEqualTo("CONFLICT");
    }

    @Test
    void forbiddenMapsTo403() {
        ResponseEntity<ErrorResponse> r = handler.handleForbidden(new ForbiddenException("denied"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(r.getBody().code()).isEqualTo("FORBIDDEN");
    }

    @Test
    void malformedJsonMapsTo422() {
        ResponseEntity<ErrorResponse> r = handler.handleUnreadable(
            new HttpMessageNotReadableException("bad json"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(r.getBody().code()).isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    void unsupportedMethodMapsTo405() {
        ResponseEntity<ErrorResponse> r = handler.handleMethodNotSupported(
            new HttpRequestMethodNotSupportedException("PUT"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(r.getBody().code()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void missingParameterMapsTo422() {
        ResponseEntity<ErrorResponse> r = handler.handleMissingParameter(
            new MissingServletRequestParameterException("path", "String"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(r.getBody().code()).isEqualTo("MISSING_PARAMETER");
    }
}
