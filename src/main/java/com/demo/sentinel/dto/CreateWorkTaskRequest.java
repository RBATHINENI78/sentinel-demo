package com.demo.sentinel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request DTO for creating a task.
 * recipientId + the operation name together form the business dedup key.
 */
public record CreateWorkTaskRequest(

    @NotNull(message = "recipientId is required")
    UUID recipientId,

    @NotBlank(message = "title is required")
    @Size(max = 255, message = "title must not exceed 255 characters")
    String title,

    @Size(max = 1000, message = "description must not exceed 1000 characters")
    String description,

    @NotBlank(message = "createdBy is required")
    @Size(max = 100, message = "createdBy must not exceed 100 characters")
    String createdBy
) {}
