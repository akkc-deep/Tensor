-- 完整性检查历史：固定受理范围、定义与规则，原子保存单元报告和问题。
-- 索引时间统一使用 UTC 毫秒；完整 JSON 保留原始精度。
CREATE TABLE tensor_integrity_check_task (
    check_id CHAR(36) NOT NULL PRIMARY KEY COMMENT '检查任务 ID（UUID）',
    submission_id CHAR(36) NOT NULL COMMENT '客户端提交 ID（UUID）',
    plugin_id VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    request_hash CHAR(64) NOT NULL COMMENT '原始请求摘要',
    capability_hash CHAR(64) NOT NULL COMMENT '受理能力摘要',
    original_request JSON NOT NULL COMMENT '原始请求文档',
    normalized_scope JSON NOT NULL COMMENT '固定检查范围文档',
    definition_snapshot JSON NOT NULL COMMENT '受理时定义及规则文档',
    status VARCHAR(24) NOT NULL COMMENT '检查任务状态',
    planned_units INT NOT NULL COMMENT '完整计划单元数',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间（UTC）',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间（UTC）',
    started_at DATETIME(3) NULL COMMENT '开始时间（UTC）',
    finished_at DATETIME(3) NULL COMMENT '结束时间（UTC）',
    error_code VARCHAR(64) NULL COMMENT '安全错误码',
    error_message TEXT NULL COMMENT '安全错误说明',
    UNIQUE KEY uk_integrity_submission (submission_id),
    KEY idx_integrity_task_created (created_at, check_id),
    KEY idx_integrity_task_filter (plugin_id, status, created_at, check_id),
    CONSTRAINT ck_integrity_task_status CHECK (status IN ('QUEUED','RUNNING','COMPLETED','FAILED','INTERRUPTED')),
    CONSTRAINT ck_integrity_task_count CHECK (planned_units >= 0),
    CONSTRAINT ck_integrity_task_json CHECK (JSON_TYPE(original_request)='OBJECT'
        AND JSON_TYPE(normalized_scope)='OBJECT' AND JSON_TYPE(definition_snapshot)='OBJECT'),
    CONSTRAINT ck_integrity_task_error CHECK ((error_code IS NULL AND error_message IS NULL)
        OR (error_code IS NOT NULL AND error_message IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='完整性检查任务与受理快照';

CREATE TABLE tensor_integrity_check_result (
    result_id CHAR(36) NOT NULL PRIMARY KEY COMMENT '检查单元 ID（UUID）',
    check_id CHAR(36) NOT NULL COMMENT '所属任务 ID',
    unit_key CHAR(64) NOT NULL COMMENT '接口与股票规范 JSON 的 SHA-256',
    plugin_id VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    api_name VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    symbol VARCHAR(255) NULL COMMENT '原规范股票代码；非股票为空',
    start_date DATE NOT NULL COMMENT '检查起始日期（含）',
    end_date DATE NOT NULL COMMENT '检查结束日期（含）',
    date_field VARCHAR(64) NULL COMMENT '检查日期轴字段',
    definition_hash CHAR(64) NOT NULL COMMENT '受理定义摘要',
    definition_snapshot JSON NOT NULL COMMENT '单元定义及规则文档',
    report JSON NOT NULL COMMENT '完整检查报告文档',
    unit_status VARCHAR(24) NOT NULL COMMENT '单元执行状态',
    coverage_status VARCHAR(24) NOT NULL COMMENT '覆盖维度结论',
    key_status VARCHAR(24) NOT NULL COMMENT '业务键维度结论',
    field_status VARCHAR(24) NOT NULL COMMENT '必需字段维度结论',
    overall_status VARCHAR(24) NOT NULL COMMENT '已保存整体结论',
    actual_count BIGINT NULL COMMENT '实际数量；未知为空',
    expected_count BIGINT NULL COMMENT '正式预期数量；未知为空',
    matched_count BIGINT NULL COMMENT '正式匹配数量；未知为空',
    missing_count BIGINT NULL COMMENT '已知缺失数量',
    suspected_missing_count BIGINT NULL COMMENT '疑似缺失数量',
    extra_count BIGINT NULL COMMENT '额外数量',
    required_field_issue_count BIGINT NULL COMMENT '必需字段问题数量',
    snapshot_started_at DATETIME(3) NULL COMMENT '读取快照时间（UTC）',
    finished_at DATETIME(3) NULL COMMENT '单元结束时间（UTC）',
    incomplete BOOLEAN NOT NULL COMMENT '报告是否未完成',
    issues_complete BOOLEAN NOT NULL COMMENT '问题明细是否完整',
    UNIQUE KEY uk_integrity_unit (check_id, unit_key),
    KEY idx_integrity_result_scope (check_id, api_name, symbol, result_id),
    KEY idx_integrity_result_status (check_id, overall_status, result_id),
    CONSTRAINT fk_integrity_result_task FOREIGN KEY (check_id) REFERENCES tensor_integrity_check_task(check_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_integrity_unit_status CHECK (unit_status IN ('PENDING','RUNNING','COMPLETED','ERROR','NOT_RUN')),
    CONSTRAINT ck_integrity_result_status CHECK (
        coverage_status IN ('PASS','FAIL','WARN','UNKNOWN','NOT_APPLICABLE')
        AND key_status IN ('PASS','FAIL','WARN','UNKNOWN','NOT_APPLICABLE')
        AND field_status IN ('PASS','FAIL','WARN','UNKNOWN','NOT_APPLICABLE')
        AND overall_status IN ('PASS','FAIL','WARN','UNKNOWN','NOT_APPLICABLE')),
    CONSTRAINT ck_integrity_result_range CHECK (start_date <= end_date),
    CONSTRAINT ck_integrity_result_json CHECK (JSON_TYPE(definition_snapshot)='OBJECT' AND JSON_TYPE(report)='OBJECT'),
    CONSTRAINT ck_integrity_result_counts CHECK (
        (actual_count IS NULL OR actual_count >= 0) AND (expected_count IS NULL OR expected_count >= 0)
        AND (matched_count IS NULL OR matched_count >= 0) AND (missing_count IS NULL OR missing_count >= 0)
        AND (suspected_missing_count IS NULL OR suspected_missing_count >= 0)
        AND (extra_count IS NULL OR extra_count >= 0)
        AND (required_field_issue_count IS NULL OR required_field_issue_count >= 0)
        AND (matched_count IS NULL OR actual_count IS NULL OR matched_count <= actual_count)
        AND (matched_count IS NULL OR expected_count IS NULL OR matched_count <= expected_count)
        AND (expected_count IS NULL OR matched_count IS NULL OR missing_count IS NULL OR expected_count-matched_count=missing_count)
        AND (actual_count IS NULL OR matched_count IS NULL OR extra_count IS NULL OR actual_count-matched_count=extra_count)),
    CONSTRAINT ck_integrity_result_flags CHECK (incomplete IN (0,1) AND issues_complete IN (0,1)
        AND (unit_status <> 'ERROR' OR incomplete=1) AND (issues_complete=1 OR incomplete=1)
        AND ((incomplete=0 AND unit_status='COMPLETED') OR (expected_count IS NULL AND matched_count IS NULL)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='完整性检查计划与单元报告';

CREATE TABLE tensor_integrity_check_issue (
    issue_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '问题自增 ID',
    result_id CHAR(36) NOT NULL COMMENT '所属检查单元 ID',
    rule_id LONGTEXT NOT NULL COMMENT '受理规则标识',
    rule_version LONGTEXT NOT NULL COMMENT '受理规则版本',
    type VARCHAR(32) NOT NULL COMMENT '问题类型',
    status VARCHAR(32) NOT NULL COMMENT '问题结论',
    date_field VARCHAR(64) NULL COMMENT '日期轴字段',
    issue_date DATE NULL COMMENT '问题日期；未解析为空',
    business_key JSON NOT NULL COMMENT '完整业务键原值',
    field VARCHAR(64) NULL COMMENT '问题字段',
    related_dates JSON NOT NULL COMMENT '关联日期原值',
    reason_code LONGTEXT NOT NULL COMMENT '原因代码',
    message LONGTEXT NOT NULL COMMENT '安全说明',
    evidence JSON NOT NULL COMMENT '最小安全证据数组',
    incomplete BOOLEAN NOT NULL COMMENT '未完成报告中的已知问题',
    KEY idx_integrity_issue_date (result_id, issue_date, issue_id),
    KEY idx_integrity_issue_type (result_id, type, issue_id),
    CONSTRAINT fk_integrity_issue_result FOREIGN KEY (result_id) REFERENCES tensor_integrity_check_result(result_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_integrity_issue_type CHECK (type IN ('MISSING','SUSPECTED_MISSING','EXTRA','REQUIRED_FIELD_MISSING',
        'BUSINESS_KEY_INVALID','SOURCE_IDENTITY_MISMATCH','REFERENCE_INCOMPLETE','DATE_SCOPE_UNRESOLVED','RULE_EXECUTION_FAILED')),
    CONSTRAINT ck_integrity_issue_status CHECK (status IN ('PASS','FAIL','WARN','UNKNOWN','NOT_APPLICABLE')),
    CONSTRAINT ck_integrity_issue_date CHECK (issue_date IS NULL OR date_field IS NOT NULL),
    CONSTRAINT ck_integrity_issue_json CHECK (JSON_TYPE(business_key)='OBJECT'
        AND JSON_TYPE(related_dates)='OBJECT' AND JSON_TYPE(evidence)='ARRAY'),
    CONSTRAINT ck_integrity_issue_flag CHECK (incomplete IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='完整性检查问题与原始安全证据';
