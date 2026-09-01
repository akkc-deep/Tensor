SET time_zone = '+00:00';

CREATE TABLE `tushare_pro__dividend` (
    `ts_code` VARCHAR(64) NOT NULL,
    `end_date` DATE NOT NULL,
    `ann_date` DATE NOT NULL,
    `div_proc` VARCHAR(64) NULL,
    `stk_div` DECIMAL(38,18) NULL,
    `stk_bo_rate` DECIMAL(38,18) NULL,
    `stk_co_rate` DECIMAL(38,18) NULL,
    `cash_div` DECIMAL(38,18) NULL,
    `cash_div_tax` DECIMAL(38,18) NULL,
    `record_date` DATE NULL,
    `ex_date` DATE NULL,
    `pay_date` DATE NULL,
    `div_listdate` DATE NULL,
    `imp_ann_date` DATE NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`ts_code`, `end_date`, `ann_date`),
    KEY `idx_dividend_ann_date` (`ann_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__repurchase` (
    `ts_code` VARCHAR(64) NOT NULL,
    `ann_date` DATE NOT NULL,
    `end_date` DATE NULL,
    `proc` VARCHAR(64) NOT NULL,
    `exp_date` DATE NULL,
    `vol` DECIMAL(38,18) NULL,
    `amount` DECIMAL(38,18) NULL,
    `high_limit` DECIMAL(38,18) NULL,
    `low_limit` DECIMAL(38,18) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`ts_code`, `ann_date`, `proc`),
    KEY `idx_repurchase_ann_date` (`ann_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__share_float` (
    `ts_code` VARCHAR(64) NOT NULL,
    `ann_date` DATE NULL,
    `float_date` DATE NOT NULL,
    `float_share` DECIMAL(38,18) NULL,
    `float_ratio` DECIMAL(38,18) NULL,
    `holder_name` VARCHAR(128) NOT NULL,
    `share_type` VARCHAR(64) NOT NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`ts_code`, `float_date`, `holder_name`, `share_type`),
    KEY `idx_share_float_ann_date` (`ann_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__stk_rewards` (
    `ts_code` VARCHAR(64) NOT NULL,
    `ann_date` DATE NOT NULL,
    `end_date` DATE NOT NULL,
    `name` VARCHAR(128) NOT NULL,
    `title` VARCHAR(128) NULL,
    `reward` DECIMAL(38,18) NULL,
    `hold_vol` DECIMAL(38,18) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`ts_code`, `ann_date`, `end_date`, `name`),
    KEY `idx_stk_rewards_ann_date` (`ann_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__stk_holdernumber` (
    `ts_code` VARCHAR(64) NOT NULL,
    `ann_date` DATE NOT NULL,
    `end_date` DATE NOT NULL,
    `holder_num` BIGINT NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`ts_code`, `end_date`, `ann_date`),
    KEY `idx_stk_holdernumber_ann_date` (`ann_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__stk_holdertrade` (
    `ts_code` VARCHAR(64) NOT NULL,
    `ann_date` DATE NOT NULL,
    `holder_name` VARCHAR(128) NOT NULL,
    `holder_type` VARCHAR(64) NULL,
    `in_de` VARCHAR(64) NOT NULL,
    `change_vol` DECIMAL(38,18) NOT NULL,
    `change_ratio` DECIMAL(38,18) NULL,
    `after_share` DECIMAL(38,18) NULL,
    `after_ratio` DECIMAL(38,18) NULL,
    `avg_price` DECIMAL(38,18) NULL,
    `total_share` DECIMAL(38,18) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`ts_code`, `ann_date`, `holder_name`, `in_de`, `change_vol`),
    KEY `idx_stk_holdertrade_ann_date` (`ann_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__top10_holders` (
    `ts_code` VARCHAR(64) NOT NULL,
    `ann_date` DATE NOT NULL,
    `end_date` DATE NOT NULL,
    `holder_name` VARCHAR(128) NOT NULL,
    `hold_amount` DECIMAL(38,18) NULL,
    `hold_ratio` DECIMAL(38,18) NULL,
    `hold_float_ratio` DECIMAL(38,18) NULL,
    `hold_change` DECIMAL(38,18) NULL,
    `holder_type` VARCHAR(64) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`ts_code`, `end_date`, `holder_name`, `ann_date`),
    KEY `idx_top10_holders_ann_date` (`ann_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__top10_floatholders` (
    `ts_code` VARCHAR(64) NOT NULL,
    `ann_date` DATE NOT NULL,
    `end_date` DATE NOT NULL,
    `holder_name` VARCHAR(128) NOT NULL,
    `hold_amount` DECIMAL(38,18) NULL,
    `hold_ratio` DECIMAL(38,18) NULL,
    `hold_float_ratio` DECIMAL(38,18) NULL,
    `hold_change` DECIMAL(38,18) NULL,
    `holder_type` VARCHAR(64) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`ts_code`, `end_date`, `holder_name`, `ann_date`),
    KEY `idx_top10_floatholders_ann_date` (`ann_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__pledge_stat` (
    `ts_code` VARCHAR(64) NOT NULL,
    `end_date` DATE NOT NULL,
    `pledge_count` BIGINT NULL,
    `unrest_pledge` DECIMAL(38,18) NULL,
    `rest_pledge` DECIMAL(38,18) NULL,
    `total_share` DECIMAL(38,18) NULL,
    `pledge_ratio` DECIMAL(38,18) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`ts_code`, `end_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__pledge_detail` (
    `ts_code` VARCHAR(64) NULL,
    `ann_date` DATE NULL,
    `holder_name` VARCHAR(128) NULL,
    `pledge_amount` DECIMAL(38,18) NULL,
    `start_date` DATE NULL,
    `end_date` DATE NULL,
    `is_release` VARCHAR(64) NULL,
    `release_date` DATE NULL,
    `pledgor` VARCHAR(128) NULL,
    `holding_amount` DECIMAL(38,18) NULL,
    `pledged_amount` DECIMAL(38,18) NULL,
    `p_total_ratio` DECIMAL(38,18) NULL,
    `h_total_ratio` DECIMAL(38,18) NULL,
    `is_buyback` VARCHAR(64) NULL,
    `business_key` CHAR(64) NOT NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`business_key`),
    KEY `idx_pledge_detail_ts_code` (`ts_code`),
    KEY `idx_pledge_detail_ann_date` (`ann_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
