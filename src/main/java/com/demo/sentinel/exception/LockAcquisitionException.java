package com.demo.sentinel.exception;

/**
 * Thrown when the request lock cannot be acquired due to an unexpected error
 * (not a duplicate — those throw DuplicateRequestException instead).
 */
public class LockAcquisitionException extends RuntimeException {

    public LockAcquisitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
