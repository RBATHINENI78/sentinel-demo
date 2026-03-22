package com.demo.sentinel.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Prevents duplicate execution of the annotated method within the TTL window.
 *
 * <p>The business key is derived from the method arguments using SpEL expressions
 * defined in {@code keyParts}. The {@code operation} field scopes the key so that
 * two different endpoints using the same field values (e.g. recipientId) never
 * collide with each other.
 *
 * <p>Usage example:
 * <pre>
 *   {@literal @}PreventDuplicate(
 *       operation  = "CREATE_TASK",
 *       keyParts   = {"#req.recipientId()"},
 *       ttlSeconds = 30
 *   )
 *   public WorkTaskResponse createTask(CreateWorkTaskRequest req) { ... }
 * </pre>
 *
 * <p>Works correctly across multiple pods sharing a single database.
 * The INSERT into request_locks is atomic at the DB level — only one
 * pod wins the race regardless of how many pods attempt it simultaneously.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PreventDuplicate {

    /**
     * SpEL expressions evaluated against the method parameters.
     * Results are joined with "|" to form the business key.
     * Example: {"#req.recipientId()", "#req.taskType()"}
     */
    String[] keyParts();

    /**
     * Logical name for this operation. Prevents key collisions across
     * different endpoints that share the same field values.
     */
    String operation();

    /**
     * How long the lock is held after success. Protects against crashes
     * between lock acquisition and work completion. Default: 30 seconds.
     */
    long ttlSeconds() default 30;
}
