package com.demo.sentinel.dto;

import com.demo.sentinel.entity.WorkTask;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO returned after task operations.
 */
public record WorkTaskResponse(
    UUID          id,
    UUID          recipientId,
    String        title,
    String        description,
    String        status,
    String        createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static WorkTaskResponse from(WorkTask task) {
        return new WorkTaskResponse(
            task.getId(),
            task.getRecipientId(),
            task.getTitle(),
            task.getDescription(),
            task.getStatus().name(),
            task.getCreatedBy(),
            task.getCreatedAt(),
            task.getUpdatedAt()
        );
    }
}
