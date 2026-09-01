SET time_zone = '+00:00';

CREATE TABLE `fixture__fixture_daily` (
    `ts_code` VARCHAR(64) NOT NULL,
    `trade_date` DATE NOT NULL,
    `amount` DECIMAL(38,18) NOT NULL,
    `note` VARCHAR(255) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`ts_code`, `trade_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
