package com.demo.sentinel.repository;

import com.demo.sentinel.entity.RequestLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * Repository for the request_locks table.
 *
 * Key design decisions:
 * - business_key is the PK, so save() = atomic INSERT with PK uniqueness check
 * - deleteById() releases the lock on success or failure
 * - deleteExpired() is called by the cleanup scheduler every 60s
 */
@Repository
public interface RequestLockRepository extends JpaRepository<RequestLock, String> {

    /**
     * Bulk-deletes all expired lock rows.
     * Called by LockCleanupScheduler to keep the table lean.
     * Returns the count of deleted rows for observability.
     */
    @Modifying
    @Query("DELETE FROM RequestLock r WHERE r.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
