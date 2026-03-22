package com.demo.sentinel.dto;

import com.demo.sentinel.entity.WorkTask.TaskStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for updating a task's status.
 */
public record UpdateWorkTaskRequest(

    @NotNull(message = "status is required")
    TaskStatus status
) {}
