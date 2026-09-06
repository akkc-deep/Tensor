ALTER TABLE `tushare_pro__dividend`
    ADD COLUMN `business_key` CHAR(64) NULL AFTER `imp_ann_date`;

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

ALTER TABLE `tushare_pro__dividend`
    MODIFY COLUMN `business_key` CHAR(64) NOT NULL,
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (`business_key`),
    ADD KEY `idx_dividend_ts_code` (`ts_code`);
