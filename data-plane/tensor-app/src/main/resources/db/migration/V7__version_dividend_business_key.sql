-- 将分红业务键扩展为 ts_code、end_date、ann_date、div_proc 的 SHA-256 指纹，保留不同实施阶段。
-- 先新增可空列，以便为已有记录回填业务键；来源追踪列仍保持在表尾。
ALTER TABLE `tushare_pro__dividend`
    ADD COLUMN `business_key` CHAR(64) NULL COMMENT '股票代码、报告期、公告日及实施进度的 SHA-256 业务键指纹' AFTER `imp_ann_date`;

-- 按 FingerprintKeyCodec 的字段顺序和编码规则回填，日期统一为 YYYY-MM-DD。
-- 非空值编码为 0x01 + 4 字节大端 UTF-8 字节长度 + 内容；空值编码为 0x00。
-- 长度前缀避免拼接歧义，空标记区分 NULL 与文本；回填仅更新 business_key。
UPDATE `tushare_pro__dividend`
SET `business_key` = SHA2(CONCAT(
    UNHEX('01'), UNHEX(LPAD(HEX(OCTET_LENGTH(CONVERT(`ts_code` USING utf8mb4))), 8, '0')),
    CONVERT(`ts_code` USING utf8mb4),
    UNHEX('01'), UNHEX(LPAD(HEX(OCTET_LENGTH(CONVERT(DATE_FORMAT(`end_date`, '%Y-%m-%d') USING utf8mb4))), 8, '0')),
    CONVERT(DATE_FORMAT(`end_date`, '%Y-%m-%d') USING utf8mb4),
    UNHEX('01'), UNHEX(LPAD(HEX(OCTET_LENGTH(CONVERT(DATE_FORMAT(`ann_date`, '%Y-%m-%d') USING utf8mb4))), 8, '0')),
    CONVERT(DATE_FORMAT(`ann_date`, '%Y-%m-%d') USING utf8mb4),
    IF(`div_proc` IS NULL,
        UNHEX('00'),
        CONCAT(
            UNHEX('01'), UNHEX(LPAD(HEX(OCTET_LENGTH(CONVERT(`div_proc` USING utf8mb4))), 8, '0')),
            CONVERT(`div_proc` USING utf8mb4)))
), 256);

-- 在同一条 ALTER 中收紧非空约束并切换主键；股票代码索引补偿原复合主键的查询能力。
-- MODIFY 显式保留字段注释；本语句原子执行，但整个迁移不构成可回滚事务。
ALTER TABLE `tushare_pro__dividend`
    MODIFY COLUMN `business_key` CHAR(64) NOT NULL COMMENT '股票代码、报告期、公告日及实施进度的 SHA-256 业务键指纹',
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (`business_key`),
    ADD KEY `idx_dividend_ts_code` (`ts_code`);
