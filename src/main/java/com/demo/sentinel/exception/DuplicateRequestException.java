package com.demo.sentinel.exception;

/**
 * Thrown when a duplicate request is detected by the AOP aspect.
 * Maps to HTTP 409 Conflict.
 */
public class DuplicateRequestException extends RuntimeException {

    public DuplicateRequestException(String message) {
        super(message);
    }

    public DuplicateRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
