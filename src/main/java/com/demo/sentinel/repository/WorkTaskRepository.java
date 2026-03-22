package com.demo.sentinel.repository;

import com.demo.sentinel.entity.WorkTask;
import com.demo.sentinel.entity.WorkTask.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for work_tasks.
 *
 * IMPORTANT: None of these queries use SELECT FOR UPDATE or any lock hint.
 * This table is never involved in the duplicate-prevention locking — that
 * lives entirely in request_locks. Reads here are always free from contention.
 */
@Repository
public interface WorkTaskRepository extends JpaRepository<WorkTask, UUID> {

    /** Used by other features — pure SELECT, zero locks, never blocked. */
    List<WorkTask> findByStatus(TaskStatus status);

    /** Paginated version for high-volume read scenarios. */
    Page<WorkTask> findByStatus(TaskStatus status, Pageable pageable);

    /** Existence check — plain SELECT, no FOR UPDATE. */
    boolean existsByRecipientIdAndStatus(UUID recipientId, TaskStatus status);

    Optional<WorkTask> findByRecipientIdAndStatus(UUID recipientId, TaskStatus status);

    /** All tasks for a given recipient, most recent first. */
    @Query("SELECT w FROM WorkTask w WHERE w.recipientId = :recipientId ORDER BY w.createdAt DESC")
    List<WorkTask> findAllByRecipientId(UUID recipientId);
}
