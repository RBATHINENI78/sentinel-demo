package com.demo.sentinel.controller;

import com.demo.sentinel.dto.CreateWorkTaskRequest;
import com.demo.sentinel.dto.UpdateWorkTaskRequest;
import com.demo.sentinel.dto.WorkTaskResponse;
import com.demo.sentinel.entity.WorkTask.TaskStatus;
import com.demo.sentinel.service.WorkTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for WorkTask operations.
 *
 * POST   /api/v1/work-tasks                          — create a task (duplicate protected)
 * GET    /api/v1/work-tasks/{id}                     — fetch by id
 * GET    /api/v1/work-tasks                          — list by status (supports pagination)
 * GET    /api/v1/work-tasks/recipient/{recipientId}  — list for a recipient
 * PATCH  /api/v1/work-tasks/{id}/status              — update status
 * DELETE /api/v1/work-tasks/{id}                     — delete
 */
@RestController
@RequestMapping("/api/v1/work-tasks")
@Slf4j
@RequiredArgsConstructor
public class SentinelController {

    private final WorkTaskService workTaskService;

    /**
     * Creates a task. Protected against duplicate submissions — clicking
     * the button twice will result in HTTP 409 for the second request.
     */
    @PostMapping
    public ResponseEntity<WorkTaskResponse> createTask(
            @Valid @RequestBody CreateWorkTaskRequest request) {

        log.info("[Controller] POST /api/v1/work-tasks — recipientId: [{}]",
                 request.recipientId());

        WorkTaskResponse response = workTaskService.createTask(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkTaskResponse> getTask(@PathVariable UUID id) {
        log.debug("[Controller] GET /api/v1/work-tasks/{}", id);
        return ResponseEntity.ok(workTaskService.getTaskById(id));
    }

    /**
     * List tasks by status with optional pagination.
     * This read path is completely unaffected by the duplicate-prevention locking
     * since work_tasks is never locked by our mechanism.
     *
     * Example: GET /api/v1/work-tasks?status=ACTIVE&page=0&size=20
     */
    @GetMapping
    public ResponseEntity<?> getTasks(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "false") boolean paged,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        if (status == null) {
            status = TaskStatus.ACTIVE;
        }

        if (paged) {
            Page<WorkTaskResponse> page = workTaskService.getTasksByStatusPaged(status, pageable);
            return ResponseEntity.ok(page);
        }

        List<WorkTaskResponse> tasks = workTaskService.getTasksByStatus(status);
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/recipient/{recipientId}")
    public ResponseEntity<List<WorkTaskResponse>> getTasksByRecipient(
            @PathVariable UUID recipientId) {
        log.debug("[Controller] GET /api/v1/work-tasks/recipient/{}", recipientId);
        return ResponseEntity.ok(workTaskService.getTasksByRecipient(recipientId));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<WorkTaskResponse> updateTaskStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWorkTaskRequest request) {

        log.info("[Controller] PATCH /api/v1/work-tasks/{}/status — new status: [{}]",
                 id, request.status());

        return ResponseEntity.ok(workTaskService.updateTaskStatus(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable UUID id) {
        log.info("[Controller] DELETE /api/v1/work-tasks/{}", id);
        workTaskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }
}
