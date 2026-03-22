package com.demo.sentinel.config;

import com.demo.sentinel.dto.ErrorResponse;
import com.demo.sentinel.exception.DuplicateRequestException;
import com.demo.sentinel.exception.LockAcquisitionException;
import com.demo.sentinel.exception.WorkTaskNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised exception handling for all API errors.
 * Maps every exception type to the correct HTTP status and a consistent
 * ErrorResponse payload.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ─── 409 Conflict ────────────────────────────────────────────────────────

    /**
     * Primary duplicate guard — thrown by DuplicateRequestAspect when the
     * request_locks INSERT fails due to PK violation (duplicate in-flight).
     */
    @ExceptionHandler(DuplicateRequestException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateRequestException ex) {
        log.warn("[ExceptionHandler] Duplicate request blocked: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of("DUPLICATE_REQUEST", ex.getMessage()));
    }

    /**
     * Passive safety net — fires only if request_locks is bypassed and the
     * DB unique constraint on work_tasks catches the duplicate instead.
     * Should never happen in normal flow.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDbConstraint(DataIntegrityViolationException ex) {
        log.error("[ExceptionHandler] DB integrity violation (safety net triggered): {}",
                  ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of("DUPLICATE_REQUEST",
                "This operation has already been completed for the given recipient."));
    }

    // ─── 404 Not Found ────────────────────────────────────────────────────────

    @ExceptionHandler(WorkTaskNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(WorkTaskNotFoundException ex) {
        log.warn("[ExceptionHandler] Resource not found: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of("NOT_FOUND", ex.getMessage()));
    }

    // ─── 400 Bad Request ──────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                fe -> fe.getField(),
                fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                (a, b) -> a
            ));

        log.warn("[ExceptionHandler] Validation failed: {}", fieldErrors);
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.validation(fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("[ExceptionHandler] Unreadable request body: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of("BAD_REQUEST", "Request body is missing or malformed."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = String.format("Parameter '%s' has invalid value: '%s'",
                                       ex.getName(), ex.getValue());
        log.warn("[ExceptionHandler] Type mismatch: {}", message);
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of("BAD_REQUEST", message));
    }

    // ─── 503 Service Unavailable ──────────────────────────────────────────────

    @ExceptionHandler(LockAcquisitionException.class)
    public ResponseEntity<ErrorResponse> handleLockFailure(LockAcquisitionException ex) {
        log.error("[ExceptionHandler] Lock acquisition failed: {}", ex.getMessage(), ex);
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse.of("LOCK_UNAVAILABLE",
                "Service is temporarily unavailable. Please retry."));
    }

    // ─── 500 Internal Server Error ────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("[ExceptionHandler] Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of("INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later."));
    }
}
