package com.demo.sentinel.service;

import com.demo.sentinel.annotation.PreventDuplicate;
import com.demo.sentinel.dto.CreateWorkTaskRequest;
import com.demo.sentinel.dto.UpdateWorkTaskRequest;
import com.demo.sentinel.dto.WorkTaskResponse;
import com.demo.sentinel.entity.WorkTask;
import com.demo.sentinel.entity.WorkTask.TaskStatus;
import com.demo.sentinel.exception.WorkTaskNotFoundException;
import com.demo.sentinel.repository.WorkTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core business service for WorkTask operations.
 *
 * DESIGN NOTE — why createTask has no existsBy check:
 * The @PreventDuplicate aspect already serialises concurrent attempts
 * via an atomic INSERT into request_locks. By the time execution reaches
 * this method, we are guaranteed to be the only thread processing this
 * business key. The DB unique constraint on (recipient_id, status) acts
 * as a passive last-resort safety net only — it will never fire in normal flow.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WorkTaskService {

    private final WorkTaskRepository workTaskRepository;

    // ─── CREATE ──────────────────────────────────────────────────────────────

    /**
     * Creates a new task for the given recipient.
     *
     * Protected by @PreventDuplicate — concurrent or duplicate calls with
     * the same recipientId will be rejected with DuplicateRequestException
     * before this method body is ever reached.
     *
     * The work_tasks table is NEVER locked here. Only a plain INSERT happens.
     */
    @PreventDuplicate(
        operation  = "CREATE_TASK",
        keyParts   = {"#req.recipientId()"},
        ttlSeconds = 30
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WorkTaskResponse createTask(CreateWorkTaskRequest req) {
        log.info("[WorkTaskService] Creating task for recipient: [{}], title: [{}]",
                 req.recipientId(), req.title());

        WorkTask task = WorkTask.builder()
            .recipientId(req.recipientId())
            .title(req.title())
            .description(req.description())
            .status(TaskStatus.ACTIVE)
            .createdBy(req.createdBy())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        WorkTask saved = workTaskRepository.save(task);

        log.info("[WorkTaskService] Task created successfully. id: [{}], recipient: [{}]",
                 saved.getId(), saved.getRecipientId());

        return WorkTaskResponse.from(saved);
    }

    // ─── READ ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public WorkTaskResponse getTaskById(UUID id) {
        log.debug("[WorkTaskService] Fetching task by id: [{}]", id);
        return workTaskRepository.findById(id)
            .map(WorkTaskResponse::from)
            .orElseThrow(() -> new WorkTaskNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<WorkTaskResponse> getTasksByStatus(TaskStatus status) {
        log.debug("[WorkTaskService] Fetching tasks by status: [{}]", status);
        return workTaskRepository.findByStatus(status)
            .stream()
            .map(WorkTaskResponse::from)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<WorkTaskResponse> getTasksByStatusPaged(TaskStatus status, Pageable pageable) {
        log.debug("[WorkTaskService] Fetching tasks by status: [{}], page: [{}]",
                  status, pageable.getPageNumber());
        return workTaskRepository.findByStatus(status, pageable)
            .map(WorkTaskResponse::from);
    }

    @Transactional(readOnly = true)
    public List<WorkTaskResponse> getTasksByRecipient(UUID recipientId) {
        log.debug("[WorkTaskService] Fetching tasks for recipient: [{}]", recipientId);
        return workTaskRepository.findAllByRecipientId(recipientId)
            .stream()
            .map(WorkTaskResponse::from)
            .collect(Collectors.toList());
    }

    // ─── UPDATE ───────────────────────────────────────────────────────────────

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WorkTaskResponse updateTaskStatus(UUID id, UpdateWorkTaskRequest req) {
        log.info("[WorkTaskService] Updating task [{}] status to [{}]", id, req.status());

        WorkTask task = workTaskRepository.findById(id)
            .orElseThrow(() -> new WorkTaskNotFoundException(id));

        TaskStatus oldStatus = task.getStatus();
        task.setStatus(req.status());
        task.setUpdatedAt(LocalDateTime.now());

        WorkTask updated = workTaskRepository.save(task);

        log.info("[WorkTaskService] Task [{}] status updated: [{}] -> [{}]",
                 id, oldStatus, updated.getStatus());

        return WorkTaskResponse.from(updated);
    }

    // ─── DELETE ───────────────────────────────────────────────────────────────

    @Transactional
    public void deleteTask(UUID id) {
        log.info("[WorkTaskService] Deleting task: [{}]", id);

        if (!workTaskRepository.existsById(id)) {
            throw new WorkTaskNotFoundException(id);
        }

        workTaskRepository.deleteById(id);
        log.info("[WorkTaskService] Task [{}] deleted successfully", id);
    }
}
