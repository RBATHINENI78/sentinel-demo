package com.demo.sentinel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Represents an in-flight operation lock.
 *
 * The business_key column is the PRIMARY KEY — the INSERT itself is the
 * distributed mutex. A duplicate key exception means the operation is
 * already in progress. No separate existence check is needed.
 *
 * This table is intentionally tiny: rows are inserted on request arrival
 * and deleted on completion (success or failure). The scheduler purges
 * any rows that survive beyond their TTL (e.g. after a pod crash).
 */
@Entity
@Table(name = "request_locks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class RequestLock {

    /**
     * Composite business key: "{operation}:{keyPart1}|{keyPart2}|..."
     * Acts as the primary key — uniqueness is enforced at DB level.
     */
    @Id
    @Column(name = "business_key", length = 512, nullable = false)
    private String businessKey;

    @Column(name = "acquired_at", nullable = false)
    private LocalDateTime acquiredAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
