package com.demo.sentinel.exception;

import java.util.UUID;

/**
 * Thrown when a requested task cannot be found.
 * Maps to HTTP 404 Not Found.
 */
public class WorkTaskNotFoundException extends RuntimeException {

    public WorkTaskNotFoundException(UUID id) {
        super("Task not found with id: " + id);
    }

    public WorkTaskNotFoundException(String message) {
        super(message);
    }
}
