SET time_zone = '+00:00';

CREATE TABLE `tushare_pro__stock_basic` (
    `ts_code` VARCHAR(64) NOT NULL,
    `symbol` VARCHAR(64) NULL,
    `name` VARCHAR(128) NULL,
    `area` VARCHAR(128) NULL,
    `industry` VARCHAR(128) NULL,
    `cnspell` VARCHAR(64) NULL,
    `market` VARCHAR(64) NULL,
    `list_date` DATE NULL,
    `act_name` VARCHAR(128) NULL,
    `act_ent_type` VARCHAR(128) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__stock_company` (
    `ts_code` VARCHAR(64) NOT NULL,
    `com_name` VARCHAR(128) NULL,
    `com_id` VARCHAR(64) NULL,
    `chairman` VARCHAR(128) NULL,
    `manager` VARCHAR(128) NULL,
    `secretary` VARCHAR(128) NULL,
    `reg_capital` DECIMAL(38,18) NULL,
    `setup_date` DATE NULL,
    `province` VARCHAR(128) NULL,
    `city` VARCHAR(128) NULL,
    `introduction` TEXT NULL,
    `website` VARCHAR(255) NULL,
    `email` VARCHAR(255) NULL,
    `office` VARCHAR(255) NULL,
    `business_scope` TEXT NULL,
    `employees` BIGINT NULL,
    `main_business` TEXT NULL,
    `exchange` VARCHAR(64) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__hs_const` (
    `ts_code` VARCHAR(64) NOT NULL,
    `hs_type` VARCHAR(64) NOT NULL,
    `in_date` DATE NOT NULL,
    `out_date` DATE NULL,
    `is_new` VARCHAR(64) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`hs_type`, `ts_code`, `in_date`),
    KEY `idx_hs_const_ts_code` (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__trade_cal` (
    `exchange` VARCHAR(64) NOT NULL,
    `cal_date` DATE NOT NULL,
    `is_open` BIGINT NULL,
    `pretrade_date` DATE NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`exchange`, `cal_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__new_share` (
    `ts_code` VARCHAR(64) NOT NULL,
    `sub_code` VARCHAR(64) NULL,
    `name` VARCHAR(128) NULL,
    `ipo_date` DATE NULL,
    `issue_date` DATE NULL,
    `amount` DECIMAL(38,18) NULL,
    `market_amount` DECIMAL(38,18) NULL,
    `price` DECIMAL(38,18) NULL,
    `pe` DECIMAL(38,18) NULL,
    `limit_amount` DECIMAL(38,18) NULL,
    `funds` DECIMAL(38,18) NULL,
    `ballot` DECIMAL(38,18) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__namechange` (
    `ts_code` VARCHAR(64) NOT NULL,
    `name` VARCHAR(128) NOT NULL,
    `start_date` DATE NOT NULL,
    `end_date` DATE NULL,
    `ann_date` DATE NULL,
    `change_reason` VARCHAR(255) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`ts_code`, `start_date`, `name`),
    KEY `idx_namechange_ann_date` (`ann_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__stk_managers` (
    `ts_code` VARCHAR(64) NULL,
    `ann_date` DATE NULL,
    `name` VARCHAR(128) NULL,
    `gender` VARCHAR(64) NULL,
    `lev` VARCHAR(64) NULL,
    `title` VARCHAR(128) NULL,
    `edu` VARCHAR(128) NULL,
    `national` VARCHAR(128) NULL,
    `birthday` DATE NULL,
    `begin_date` DATE NULL,
    `end_date` DATE NULL,
    `business_key` CHAR(64) NOT NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`business_key`),
    KEY `idx_stk_managers_ts_code` (`ts_code`),
    KEY `idx_stk_managers_ann_date` (`ann_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__broker_recommend` (
    `month` CHAR(6) NOT NULL,
    `broker` VARCHAR(128) NOT NULL,
    `ts_code` VARCHAR(64) NOT NULL,
    `name` VARCHAR(128) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`month`, `broker`, `ts_code`),
    KEY `idx_broker_recommend_ts_code` (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__index_classify` (
    `index_code` VARCHAR(64) NOT NULL,
    `industry_name` VARCHAR(128) NULL,
    `level` VARCHAR(64) NULL,
    `industry_code` VARCHAR(64) NULL,
    `is_pub` VARCHAR(64) NULL,
    `parent_code` VARCHAR(64) NULL,
    `src` VARCHAR(64) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`index_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__index_member` (
    `index_code` VARCHAR(64) NOT NULL,
    `con_code` VARCHAR(64) NOT NULL,
    `in_date` DATE NOT NULL,
    `out_date` DATE NULL,
    `is_new` VARCHAR(64) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`index_code`, `con_code`, `in_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__index_member_all` (
    `l1_code` VARCHAR(64) NOT NULL,
    `l1_name` VARCHAR(128) NULL,
    `l2_code` VARCHAR(64) NOT NULL,
    `l2_name` VARCHAR(128) NULL,
    `l3_code` VARCHAR(64) NOT NULL,
    `l3_name` VARCHAR(128) NULL,
    `ts_code` VARCHAR(64) NOT NULL,
    `name` VARCHAR(128) NULL,
    `in_date` DATE NOT NULL,
    `out_date` DATE NULL,
    `is_new` VARCHAR(64) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`l1_code`, `l2_code`, `l3_code`, `ts_code`, `in_date`),
    KEY `idx_index_member_all_ts_code` (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
