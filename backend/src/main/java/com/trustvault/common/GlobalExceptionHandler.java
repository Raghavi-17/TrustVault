package com.trustvault.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ErrorResponse err = ErrorResponse.ofValidation(
            HttpStatus.BAD_REQUEST.value(),
            "Validation Failed",
            "One or more request parameters failed validation",
            request.getRequestURI(),
            errors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", ex.getMessage(), request.getRequestURI())
        );
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse.of(HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage(), request.getRequestURI())
        );
    }

    @ExceptionHandler({AccessForbiddenException.class, AccessDeniedException.class})
    public ResponseEntity<ErrorResponse> handleForbidden(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse.of(HttpStatus.FORBIDDEN.value(), "Access Denied", ex.getMessage(), request.getRequestURI())
        );
    }

    @ExceptionHandler(AccountDeactivatedException.class)
    public ResponseEntity<ErrorResponse> handleDeactivated(AccountDeactivatedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse.of(HttpStatus.FORBIDDEN.value(), "Account Deactivated", ex.getMessage(), request.getRequestURI())
        );
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
            ErrorResponse.of(HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many Requests", ex.getMessage(), request.getRequestURI())
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
            ErrorResponse.of(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Payload Too Large", "Uploaded file exceeds maximum allowed size of 15MB", request.getRequestURI())
        );
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ErrorResponse> handleStorage(StorageException ex, HttpServletRequest request) {
        log.error("Storage error on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Storage Error", "Failed to process document storage operation", request.getRequestURI())
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error processing request to {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error", "An unexpected error occurred. Please contact an administrator.", request.getRequestURI())
        );
    }
}
