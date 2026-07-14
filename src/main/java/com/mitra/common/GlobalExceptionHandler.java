package com.mitra.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Centralized exception handler.
 * Converts all exceptions to structured ApiResponse objects.
 * Eliminates all raw 500 errors and inconsistent error formats.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles @Valid validation failures — returns 400 with field-level errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("VALIDATION_ERROR", message));
    }

    /**
     * Handles resource not found — returns 404.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", ex.getMessage()));
    }

    /**
     * Handles bad business logic requests — returns 400.
     */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("BAD_REQUEST", ex.getMessage()));
    }

    /**
     * Handles access control violations — returns 403.
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("FORBIDDEN", ex.getMessage()));
    }

    /**
     * Handles all unhandled exceptions — returns 500 with a generic message.
     *
     * SEC-07 FIX: Previously called ex.printStackTrace() which outputs the full
     * stack trace to stdout. In cloud/container environments stdout is typically
     * shipped to log aggregators, exposing internal class names, file paths, and
     * line numbers to anyone with log access.
     *
     * Now uses SLF4J log.error() which gives structured, level-filtered logging
     * and never leaks stack traces in the HTTP response body.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        // Log full exception server-side for debugging
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        // Never expose internal details in the response body
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred. Please try again."));
    }
}
