package com.fos.workspace.config;

import com.fos.sdk.core.ErrorResponse;
import com.fos.sdk.events.RequestContext;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(EntityNotFoundException ex) {
        return ErrorResponse.of("NOT_FOUND", ex.getMessage(), RequestContext.get());
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied(AccessDeniedException ex) {
        return ErrorResponse.of("FORBIDDEN", ex.getMessage(), RequestContext.get());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return ErrorResponse.of("VALIDATION_FAILED", "Request validation failed", details, RequestContext.get());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(IllegalStateException ex) {
        log.warn("Returning 409 Conflict for IllegalStateException: {}", ex.getMessage(), ex);
        return ErrorResponse.of("CONFLICT", ex.getMessage(), RequestContext.get());
    }

    @ExceptionHandler(HttpClientErrorException.Unauthorized.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ErrorResponse handleUpstreamUnauthorized(HttpClientErrorException.Unauthorized ex) {
        log.warn("Downstream service unauthorized request: {}", ex.getMessage());
        return ErrorResponse.of(
                "UPSTREAM_UNAUTHORIZED",
                "Workspace dependency rejected the request with 401 Unauthorized",
                RequestContext.get());
    }
}
