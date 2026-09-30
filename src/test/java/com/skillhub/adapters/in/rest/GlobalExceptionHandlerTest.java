package com.skillhub.adapters.in.rest;

import com.skillhub.core.exception.ConflictException;
import com.skillhub.core.exception.ForbiddenException;
import com.skillhub.core.exception.NotFoundException;
import com.skillhub.core.exception.UnprocessableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
    void forbiddenMapsTo403() {
        ResponseEntity<ErrorResponse> r = handler.handleForbidden(new ForbiddenException("denied"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(r.getBody().code()).isEqualTo("FORBIDDEN");
    }
}
