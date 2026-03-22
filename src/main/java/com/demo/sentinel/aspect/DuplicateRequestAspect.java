package com.demo.sentinel.aspect;

import com.demo.sentinel.annotation.PreventDuplicate;
import com.demo.sentinel.entity.RequestLock;
import com.demo.sentinel.exception.DuplicateRequestException;
import com.demo.sentinel.exception.LockAcquisitionException;
import com.demo.sentinel.repository.RequestLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * AOP aspect that intercepts methods annotated with {@link PreventDuplicate}
 * and prevents concurrent or repeated execution with the same business key.
 *
 * HOW IT WORKS:
 * 1. Derives a business key from the method arguments using SpEL expressions.
 * 2. Attempts an atomic INSERT into request_locks (PK = business_key).
 * 3. If the INSERT succeeds → this is the first request → let it through.
 * 4. If the INSERT fails with PK violation → duplicate → throw 409.
 * 5. On method success → lock row stays (acts as "already done" sentinel).
 * 6. On method failure → lock row deleted (allows the caller to retry).
 * 7. Scheduler purges expired rows every 60 seconds.
 *
 * MULTI-POD SAFETY:
 * Works correctly across all pods sharing a single database.
 * The DB guarantees atomicity of the INSERT + PK check — only one pod
 * can win the race, regardless of how many pods attempt simultaneously.
 *
 * WORK_TASKS TABLE:
 * Is NEVER locked. All locking is isolated to request_locks only.
 * Readers of work_tasks (unassigned task queries etc.) are never blocked.
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class DuplicateRequestAspect {

    private final RequestLockRepository lockRepository;
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    @Around("@annotation(preventDuplicate)")
    public Object guardAgainstDuplicates(
            ProceedingJoinPoint joinPoint,
            PreventDuplicate preventDuplicate) throws Throwable {

        String businessKey = buildBusinessKey(joinPoint, preventDuplicate);

        log.debug("[DuplicateGuard] Attempting lock acquisition for key: [{}]", businessKey);

        acquireLock(businessKey, preventDuplicate.ttlSeconds());

        log.debug("[DuplicateGuard] Lock acquired for key: [{}]", businessKey);

        try {
            Object result = joinPoint.proceed();
            // On SUCCESS: keep the lock row — it becomes the "already done"
            // sentinel so any retry within TTL gets a 409.
            log.debug("[DuplicateGuard] Method succeeded. Lock [{}] retained as sentinel.", businessKey);
            return result;

        } catch (DuplicateRequestException ex) {
            // Business-level duplicate (e.g. active task already exists).
            // Release lock so the client is not blocked unnecessarily.
            log.warn("[DuplicateGuard] Business duplicate detected for key [{}]. Releasing lock.", businessKey);
            releaseLock(businessKey);
            throw ex;

        } catch (Exception ex) {
            // Unexpected failure — release lock so the client can legitimately retry.
            log.error("[DuplicateGuard] Method failed for key [{}]. Releasing lock to allow retry. Error: {}",
                      businessKey, ex.getMessage());
            releaseLock(businessKey);
            throw ex;
        }
    }

    // ─── private helpers ─────────────────────────────────────────────────────

    /**
     * Inserts a row into request_locks.
     * Uses REQUIRES_NEW so the INSERT is committed immediately and visible
     * to other pods/threads — not held inside the caller's transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void acquireLock(String businessKey, long ttlSeconds) {
        try {
            RequestLock lock = RequestLock.builder()
                .businessKey(businessKey)
                .acquiredAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusSeconds(ttlSeconds))
                .build();

            lockRepository.saveAndFlush(lock);

        } catch (DataIntegrityViolationException ex) {
            log.warn("[DuplicateGuard] Duplicate request blocked. Key: [{}]", businessKey);
            throw new DuplicateRequestException(
                "A request for this operation is already in progress. " +
                "Please wait a moment before retrying.");
        } catch (Exception ex) {
            log.error("[DuplicateGuard] Unexpected error acquiring lock for key [{}]: {}",
                      businessKey, ex.getMessage(), ex);
            throw new LockAcquisitionException(
                "Failed to acquire request lock — please retry", ex);
        }
    }

    /**
     * Deletes the lock row. Called on method failure so the user can retry.
     * Swallows exceptions — a failure to release is not fatal (TTL cleans up).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void releaseLock(String businessKey) {
        try {
            lockRepository.deleteById(businessKey);
            log.debug("[DuplicateGuard] Lock released for key: [{}]", businessKey);
        } catch (Exception ex) {
            // Non-fatal: the TTL-based cleanup scheduler will handle it.
            log.warn("[DuplicateGuard] Could not release lock for key [{}]: {}. " +
                     "Will be cleaned up by scheduler.", businessKey, ex.getMessage());
        }
    }

    /**
     * Builds the scoped business key from the annotation's keyParts
     * by evaluating each SpEL expression against the method arguments.
     *
     * Result format: "{operation}:{part1}|{part2}|..."
     * Example: "CREATE_TASK:550e8400-e29b-41d4-a716-446655440000"
     */
    private String buildBusinessKey(ProceedingJoinPoint joinPoint,
                                    PreventDuplicate annotation) {
        MethodSignature signature  = (MethodSignature) joinPoint.getSignature();
        String[]        paramNames = signature.getParameterNames();
        Object[]        paramValues = joinPoint.getArgs();

        EvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], paramValues[i]);
        }

        String keyBody = Arrays.stream(annotation.keyParts())
            .map(expr -> {
                try {
                    Object value = expressionParser
                        .parseExpression(expr)
                        .getValue(context);
                    return value != null ? value.toString() : "null";
                } catch (Exception ex) {
                    log.error("[DuplicateGuard] Failed to evaluate SpEL expression [{}]: {}",
                              expr, ex.getMessage());
                    throw new LockAcquisitionException(
                        "Invalid keyParts expression: " + expr, ex);
                }
            })
            .collect(Collectors.joining("|"));

        return annotation.operation() + ":" + keyBody;
    }
}
