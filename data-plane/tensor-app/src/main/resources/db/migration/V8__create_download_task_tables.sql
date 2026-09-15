-- 创建下载任务与批次表，记录幂等提交、执行轮次及批次拆分关系。
-- 应用以 UTC 写入时间字段；外键保留任务与批次之间的引用完整性。
CREATE TABLE tensor_download_task (
    task_id CHAR(36) NOT NULL PRIMARY KEY COMMENT '下载任务 ID（UUID）',
    submission_id CHAR(36) NOT NULL COMMENT '客户端提交 ID（UUID），用于幂等去重',
    request_hash CHAR(64) NOT NULL COMMENT '数据集、下载模式及规范化参数的 SHA-256 摘要',
    plugin_id VARCHAR(64) NOT NULL COMMENT '数据来源插件标识',
    api_name VARCHAR(64) NOT NULL COMMENT '数据来源接口名称',
    mode VARCHAR(8) NOT NULL COMMENT '下载模式：SINGLE 单次，RANGE 日期区间',
    params JSON NOT NULL COMMENT '规范化后的任务请求参数（JSON 对象）',
    definition_hash CHAR(64) NOT NULL COMMENT '接口、数据集与下载策略定义的 SHA-256 摘要',
    policy_snapshot JSON NOT NULL COMMENT '提交时的下载策略快照（JSON 对象）',
    status VARCHAR(24) NOT NULL COMMENT '任务状态：QUEUED 排队、RUNNING 执行中、SUCCEEDED 成功、PARTIAL_FAILED 部分失败、FAILED 失败、INTERRUPTED 中断',
    plan_ready BOOLEAN NOT NULL DEFAULT FALSE COMMENT '批次计划是否已完整持久化',
    active_run_id CHAR(36) NOT NULL COMMENT '当前所属应用运行实例 ID（UUID）',
    run_generation INT NOT NULL DEFAULT 0 COMMENT '任务执行轮次，每次开始执行时递增',
    version BIGINT NOT NULL DEFAULT 1 COMMENT '乐观锁版本号，用于并发更新校验',
    request_count BIGINT NOT NULL DEFAULT 0 COMMENT '任务累计向来源接口发起的请求次数',
    run_request_count BIGINT NOT NULL DEFAULT 0 COMMENT '当前执行轮次向来源接口发起的请求次数',
    last_error_code VARCHAR(64) NULL COMMENT '最近一次任务错误码',
    last_error_message VARCHAR(512) NULL COMMENT '最近一次任务错误说明',
    created_at DATETIME(3) NOT NULL COMMENT '任务创建时间（UTC，毫秒精度）',
    updated_at DATETIME(3) NOT NULL COMMENT '任务最近更新时间（UTC，毫秒精度）',
    queued_at DATETIME(3) NOT NULL COMMENT '本次入队时间（UTC，毫秒精度）',
    started_at DATETIME(3) NULL COMMENT '本轮开始执行时间（UTC，毫秒精度）',
    finished_at DATETIME(3) NULL COMMENT '本轮执行结束时间（UTC，毫秒精度）',
    deadline_at DATETIME(3) NULL COMMENT '本轮执行截止时间（UTC，毫秒精度）',
    -- 同一提交 ID 只对应一个任务，保证重复提交幂等。
    UNIQUE KEY uk_download_task_submission (submission_id),
    -- 按状态和应用实例查找队列，再按入队时间及任务 ID 稳定排序。
    KEY idx_download_task_queue (status, active_run_id, queued_at, task_id),
    -- 支持按创建时间及任务 ID 分页查询任务。
    KEY idx_download_task_created (created_at, task_id),
    -- 支持按插件、接口筛选并按创建时间及任务 ID 分页。
    KEY idx_download_task_source (plugin_id, api_name, created_at, task_id),
    -- 限定单次下载和日期区间下载两种模式。
    CONSTRAINT ck_download_task_mode CHECK (mode IN ('SINGLE', 'RANGE')),
    -- 限制任务状态为约定的状态机取值。
    CONSTRAINT ck_download_task_status CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'PARTIAL_FAILED', 'FAILED', 'INTERRUPTED')),
    -- 计划就绪标记只能取 0 或 1。
    CONSTRAINT ck_download_task_plan_ready CHECK (plan_ready IN (0, 1)),
    -- 计数及版本保持合法，当前轮次请求数不得超过累计请求数。
    CONSTRAINT ck_download_task_counters CHECK (run_generation >= 0 AND version >= 1 AND request_count >= 0
        AND run_request_count >= 0 AND run_request_count <= request_count),
    -- 任务参数必须为 JSON 对象。
    CONSTRAINT ck_download_task_params CHECK (JSON_TYPE(params) = 'OBJECT'),
    -- 策略快照必须为 JSON 对象。
    CONSTRAINT ck_download_task_policy CHECK (JSON_TYPE(policy_snapshot) = 'OBJECT'),
    -- 错误码与错误说明必须同时为空或同时有值。
    CONSTRAINT ck_download_task_error CHECK ((last_error_code IS NULL AND last_error_message IS NULL)
        OR (last_error_code IS NOT NULL AND last_error_message IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='下载任务及执行状态';

CREATE TABLE tensor_download_batch (
    batch_id CHAR(36) NOT NULL PRIMARY KEY COMMENT '下载批次 ID（UUID）',
    task_id CHAR(36) NOT NULL COMMENT '所属下载任务 ID（UUID）',
    parent_batch_id CHAR(36) NULL COMMENT '拆分前的父批次 ID；根批次为空',
    batch_key VARCHAR(128) NOT NULL COMMENT '任务内唯一的稳定批次键，用于去重和排序',
    range_start DATE NULL COMMENT '批次日期区间起始日（含）；无日期区间时为空',
    range_end DATE NULL COMMENT '批次日期区间结束日（含）；无日期区间时为空',
    source_params JSON NOT NULL COMMENT '本批次实际传给来源接口的参数（JSON 对象）',
    status VARCHAR(16) NOT NULL COMMENT '批次状态：PENDING 待执行、RUNNING 执行中、SUCCEEDED 成功、FAILED 失败、SPLIT 已拆分',
    attempt_count INT NOT NULL DEFAULT 0 COMMENT '批次累计执行尝试次数',
    run_generation INT NULL COMMENT '最近认领本批次的任务执行轮次；未认领时为空',
    source_rows BIGINT NOT NULL DEFAULT 0 COMMENT '成功提交批次的来源记录数',
    inserted_rows BIGINT NOT NULL DEFAULT 0 COMMENT '成功提交批次的新增记录数',
    updated_rows BIGINT NOT NULL DEFAULT 0 COMMENT '成功提交批次的更新记录数',
    error_code VARCHAR(64) NULL COMMENT '本批次错误码',
    error_message VARCHAR(512) NULL COMMENT '本批次错误说明',
    created_at DATETIME(3) NOT NULL COMMENT '批次创建时间（UTC，毫秒精度）',
    updated_at DATETIME(3) NOT NULL COMMENT '批次最近更新时间（UTC，毫秒精度）',
    started_at DATETIME(3) NULL COMMENT '最近一次尝试开始时间（UTC，毫秒精度）',
    finished_at DATETIME(3) NULL COMMENT '最近一次尝试结束时间（UTC，毫秒精度）',
    -- 同一任务内批次键唯一，避免重复规划同一批次。
    UNIQUE KEY uk_download_batch_key (task_id, batch_key),
    -- 按任务与批次状态筛选，再按批次键稳定排序。
    KEY idx_download_batch_status (task_id, status, batch_key),
    -- 支持通过父批次查找拆分后的子批次。
    KEY idx_download_batch_parent (parent_batch_id),
    -- 批次必须关联已存在的任务；禁止删除或改写仍被引用的任务 ID。
    CONSTRAINT fk_download_batch_task FOREIGN KEY (task_id)
        REFERENCES tensor_download_task(task_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    -- 父批次必须存在；禁止删除或改写仍被子批次引用的批次 ID。
    CONSTRAINT fk_download_batch_parent FOREIGN KEY (parent_batch_id)
        REFERENCES tensor_download_batch(batch_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    -- 限制批次状态为约定的状态机取值。
    CONSTRAINT ck_download_batch_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'SPLIT')),
    -- 日期区间两端同时为空或同时有值，且起始日不得晚于结束日。
    CONSTRAINT ck_download_batch_range CHECK ((range_start IS NULL AND range_end IS NULL)
        OR (range_start IS NOT NULL AND range_end IS NOT NULL AND range_start <= range_end)),
    -- 尝试次数和行数不得为负；有执行轮次时必须从 1 开始。
    CONSTRAINT ck_download_batch_counters CHECK (attempt_count >= 0 AND source_rows >= 0
        AND inserted_rows >= 0 AND updated_rows >= 0 AND (run_generation IS NULL OR run_generation >= 1)),
    -- 来源接口参数必须为 JSON 对象。
    CONSTRAINT ck_download_batch_params CHECK (JSON_TYPE(source_params) = 'OBJECT'),
    -- 错误码与错误说明必须同时为空或同时有值。
    CONSTRAINT ck_download_batch_error CHECK ((error_code IS NULL AND error_message IS NULL)
        OR (error_code IS NOT NULL AND error_message IS NOT NULL)),
    -- 批次不能将自身作为父批次。
    CONSTRAINT ck_download_batch_parent CHECK (parent_batch_id IS NULL OR parent_batch_id <> batch_id),
    -- 仅成功批次可记录已提交行数，其他状态的行数必须为零。
    CONSTRAINT ck_download_batch_success_counts CHECK (status = 'SUCCEEDED'
        OR (source_rows = 0 AND inserted_rows = 0 AND updated_rows = 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='下载批次及拆分关系';
