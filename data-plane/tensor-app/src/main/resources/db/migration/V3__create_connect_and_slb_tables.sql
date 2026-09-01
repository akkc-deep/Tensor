SET time_zone = '+00:00';

CREATE TABLE `tushare_pro__moneyflow_hsgt` (
    `trade_date` DATE NOT NULL,
    `ggt_ss` DECIMAL(38,18) NULL,
    `ggt_sz` DECIMAL(38,18) NULL,
    `hgt` DECIMAL(38,18) NULL,
    `sgt` DECIMAL(38,18) NULL,
    `north_money` DECIMAL(38,18) NULL,
    `south_money` DECIMAL(38,18) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`trade_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__hsgt_top10` (
    `trade_date` DATE NOT NULL,
    `ts_code` VARCHAR(64) NOT NULL,
    `name` VARCHAR(128) NULL,
    `close` DECIMAL(38,18) NULL,
    `change` DECIMAL(38,18) NULL,
    `rank` BIGINT NULL,
    `market_type` BIGINT NOT NULL,
    `amount` DECIMAL(38,18) NULL,
    `net_amount` DECIMAL(38,18) NULL,
    `buy` DECIMAL(38,18) NULL,
    `sell` DECIMAL(38,18) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`trade_date`, `ts_code`, `market_type`),
    KEY `idx_hsgt_top10_ts_code` (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__hk_hold` (
    `code` VARCHAR(64) NOT NULL,
    `trade_date` DATE NOT NULL,
    `ts_code` VARCHAR(64) NULL,
    `name` VARCHAR(128) NULL,
    `vol` DECIMAL(38,18) NULL,
    `ratio` DECIMAL(38,18) NULL,
    `exchange` VARCHAR(64) NOT NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`trade_date`, `code`, `exchange`),
    KEY `idx_hk_hold_ts_code` (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__slb_len` (
    `trade_date` DATE NOT NULL,
    `ob` DECIMAL(38,18) NOT NULL,
    `auc_amount` DECIMAL(38,18) NULL,
    `repo_amount` DECIMAL(38,18) NULL,
    `repay_amount` DECIMAL(38,18) NULL,
    `cb` DECIMAL(38,18) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`trade_date`, `ob`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__slb_sec` (
    `trade_date` DATE NOT NULL,
    `ts_code` VARCHAR(64) NOT NULL,
    `name` VARCHAR(128) NULL,
    `ope_inv` DECIMAL(38,18) NULL,
    `lent_qnt` DECIMAL(38,18) NULL,
    `cls_inv` DECIMAL(38,18) NULL,
    `end_bal` DECIMAL(38,18) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`trade_date`, `ts_code`),
    KEY `idx_slb_sec_ts_code` (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__slb_sec_detail` (
    `trade_date` DATE NOT NULL,
    `ts_code` VARCHAR(64) NOT NULL,
    `name` VARCHAR(128) NULL,
    `tenor` BIGINT NOT NULL,
    `fee_rate` DECIMAL(38,18) NOT NULL,
    `lent_qnt` DECIMAL(38,18) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`trade_date`, `ts_code`, `tenor`, `fee_rate`),
    KEY `idx_slb_sec_detail_ts_code` (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
