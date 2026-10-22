package com.trustvault.common;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ErrorResponse(
    boolean success,
    int status,
    String error,
    String message,
    String path,
    Map<String, String> validationErrors,
    Instant timestamp
) {
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(false, status, error, message, path, null, Instant.now());
    }

    public static ErrorResponse ofValidation(int status, String error, String message, String path, Map<String, String> validationErrors) {
        return new ErrorResponse(false, status, error, message, path, validationErrors, Instant.now());
    }
}
