CREATE TABLE tensor_download_task (
    task_id CHAR(36) NOT NULL PRIMARY KEY,
    submission_id CHAR(36) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    plugin_id VARCHAR(64) NOT NULL,
    api_name VARCHAR(64) NOT NULL,
    mode VARCHAR(8) NOT NULL,
    params JSON NOT NULL,
    definition_hash CHAR(64) NOT NULL,
    policy_snapshot JSON NOT NULL,
    status VARCHAR(24) NOT NULL,
    plan_ready BOOLEAN NOT NULL DEFAULT FALSE,
    active_run_id CHAR(36) NOT NULL,
    run_generation INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 1,
    request_count BIGINT NOT NULL DEFAULT 0,
    run_request_count BIGINT NOT NULL DEFAULT 0,
    last_error_code VARCHAR(64) NULL,
    last_error_message VARCHAR(512) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    queued_at DATETIME(3) NOT NULL,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    deadline_at DATETIME(3) NULL,
    UNIQUE KEY uk_download_task_submission (submission_id),
    KEY idx_download_task_queue (status, active_run_id, queued_at, task_id),
    KEY idx_download_task_created (created_at, task_id),
    KEY idx_download_task_source (plugin_id, api_name, created_at, task_id),
    CONSTRAINT ck_download_task_mode CHECK (mode IN ('SINGLE', 'RANGE')),
    CONSTRAINT ck_download_task_status CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'PARTIAL_FAILED', 'FAILED', 'INTERRUPTED')),
    CONSTRAINT ck_download_task_plan_ready CHECK (plan_ready IN (0, 1)),
    CONSTRAINT ck_download_task_counters CHECK (run_generation >= 0 AND version >= 1 AND request_count >= 0
        AND run_request_count >= 0 AND run_request_count <= request_count),
    CONSTRAINT ck_download_task_params CHECK (JSON_TYPE(params) = 'OBJECT'),
    CONSTRAINT ck_download_task_policy CHECK (JSON_TYPE(policy_snapshot) = 'OBJECT'),
    CONSTRAINT ck_download_task_error CHECK ((last_error_code IS NULL AND last_error_message IS NULL)
        OR (last_error_code IS NOT NULL AND last_error_message IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE tensor_download_batch (
    batch_id CHAR(36) NOT NULL PRIMARY KEY,
    task_id CHAR(36) NOT NULL,
    parent_batch_id CHAR(36) NULL,
    batch_key VARCHAR(128) NOT NULL,
    range_start DATE NULL,
    range_end DATE NULL,
    source_params JSON NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    run_generation INT NULL,
    source_rows BIGINT NOT NULL DEFAULT 0,
    inserted_rows BIGINT NOT NULL DEFAULT 0,
    updated_rows BIGINT NOT NULL DEFAULT 0,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(512) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    UNIQUE KEY uk_download_batch_key (task_id, batch_key),
    KEY idx_download_batch_status (task_id, status, batch_key),
    KEY idx_download_batch_parent (parent_batch_id),
    CONSTRAINT fk_download_batch_task FOREIGN KEY (task_id)
        REFERENCES tensor_download_task(task_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_download_batch_parent FOREIGN KEY (parent_batch_id)
        REFERENCES tensor_download_batch(batch_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_download_batch_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'SPLIT')),
    CONSTRAINT ck_download_batch_range CHECK ((range_start IS NULL AND range_end IS NULL)
        OR (range_start IS NOT NULL AND range_end IS NOT NULL AND range_start <= range_end)),
    CONSTRAINT ck_download_batch_counters CHECK (attempt_count >= 0 AND source_rows >= 0
        AND inserted_rows >= 0 AND updated_rows >= 0 AND (run_generation IS NULL OR run_generation >= 1)),
    CONSTRAINT ck_download_batch_params CHECK (JSON_TYPE(source_params) = 'OBJECT'),
    CONSTRAINT ck_download_batch_error CHECK ((error_code IS NULL AND error_message IS NULL)
        OR (error_code IS NOT NULL AND error_message IS NOT NULL)),
    CONSTRAINT ck_download_batch_parent CHECK (parent_batch_id IS NULL OR parent_batch_id <> batch_id),
    CONSTRAINT ck_download_batch_success_counts CHECK (status = 'SUCCEEDED'
        OR (source_rows = 0 AND inserted_rows = 0 AND updated_rows = 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
