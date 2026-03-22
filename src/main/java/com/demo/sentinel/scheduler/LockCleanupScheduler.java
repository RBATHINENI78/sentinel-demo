package com.demo.sentinel.scheduler;

import com.demo.sentinel.repository.RequestLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Scheduled job that purges expired rows from request_locks.
 *
 * WHY THIS EXISTS:
 * Lock rows are normally deleted by the AOP aspect after method completion
 * (success retains the lock as a sentinel; failure deletes it).
 * However, if a pod crashes mid-flight, the lock row is left behind.
 * This scheduler ensures those orphaned rows are cleaned up so the
 * request_locks table never grows unbounded.
 *
 * FREQUENCY:
 * Runs every 60 seconds (configurable via app.lock.cleanup-interval-ms).
 * At that frequency, the table will at most accumulate ~60s worth of
 * orphaned rows — negligible for the table's size and performance.
 *
 * MULTI-POD SAFETY:
 * All pods run this scheduler. Concurrent cleanup runs are harmless —
 * DELETE WHERE expires_at < NOW() is idempotent. The second pod simply
 * deletes zero rows.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LockCleanupScheduler {

    private final RequestLockRepository lockRepository;

    @Value("${app.lock.cleanup-interval-ms:60000}")
    private long cleanupIntervalMs;

    @Scheduled(fixedDelayString = "${app.lock.cleanup-interval-ms:60000}")
    @Transactional
    public void purgeExpiredLocks() {
        LocalDateTime now = LocalDateTime.now();
        log.debug("[LockCleanup] Starting purge of expired locks at: {}", now);

        try {
            int deleted = lockRepository.deleteExpired(now);

            if (deleted > 0) {
                log.info("[LockCleanup] Purged {} expired request lock(s).", deleted);
            } else {
                log.debug("[LockCleanup] No expired locks found.");
            }
        } catch (Exception ex) {
            // Log but do not re-throw — a cleanup failure is non-fatal.
            // The scheduler will retry on the next cycle.
            log.error("[LockCleanup] Failed to purge expired locks: {}", ex.getMessage(), ex);
        }
    }
}
