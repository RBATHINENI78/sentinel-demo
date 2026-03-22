-- =================================================================
-- V1__initial_schema.sql
-- work_tasks    : business table — never locked by our mechanism
-- request_locks : atomic dedup mutex — INSERT is the lock itself
-- =================================================================

CREATE TABLE IF NOT EXISTS work_tasks (
    id            UUID         NOT NULL DEFAULT RANDOM_UUID(),
    recipient_id  UUID         NOT NULL,
    title         VARCHAR(255) NOT NULL,
    description   VARCHAR(1000),
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by    VARCHAR(100) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_work_tasks
        PRIMARY KEY (id),

    -- Passive safety net: if somehow the lock table is bypassed,
    -- the DB still prevents two ACTIVE tasks for the same recipient.
    CONSTRAINT uq_work_tasks_recipient_active
        UNIQUE (recipient_id, status)
);

CREATE INDEX IF NOT EXISTS idx_wt_status
    ON work_tasks (status);

CREATE INDEX IF NOT EXISTS idx_wt_recipient
    ON work_tasks (recipient_id);

CREATE INDEX IF NOT EXISTS idx_wt_created_at
    ON work_tasks (created_at DESC);

-- =================================================================

CREATE TABLE IF NOT EXISTS request_locks (
    -- business_key IS the PK. The INSERT is the distributed mutex.
    -- PK violation = duplicate caught atomically, no extra lookup.
    business_key  VARCHAR(512) NOT NULL,
    acquired_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    expires_at    TIMESTAMP    NOT NULL,

    CONSTRAINT pk_request_locks
        PRIMARY KEY (business_key)
);

-- Cleanup scheduler uses this to purge expired rows efficiently
CREATE INDEX IF NOT EXISTS idx_rl_expires
    ON request_locks (expires_at);
