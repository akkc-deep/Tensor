SET time_zone = '+00:00';

CREATE TABLE `tushare_pro__daily` (
    `ts_code` VARCHAR(64) NOT NULL,
    `trade_date` DATE NOT NULL,
    `open` DECIMAL(38,18) NULL,
    `high` DECIMAL(38,18) NULL,
    `low` DECIMAL(38,18) NULL,
    `close` DECIMAL(38,18) NULL,
    `pre_close` DECIMAL(38,18) NULL,
    `change` DECIMAL(38,18) NULL,
    `pct_chg` DECIMAL(38,18) NULL,
    `vol` DECIMAL(38,18) NULL,
    `amount` DECIMAL(38,18) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`ts_code`, `trade_date`),
    KEY `idx_daily_trade_date` (`trade_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__weekly` (
    `ts_code` VARCHAR(64) NOT NULL,
    `trade_date` DATE NOT NULL,
    `close` DECIMAL(38,18) NULL,
    `open` DECIMAL(38,18) NULL,
    `high` DECIMAL(38,18) NULL,
    `low` DECIMAL(38,18) NULL,
    `pre_close` DECIMAL(38,18) NULL,
    `change` DECIMAL(38,18) NULL,
    `pct_chg` DECIMAL(38,18) NULL,
    `vol` DECIMAL(38,18) NULL,
    `amount` DECIMAL(38,18) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`ts_code`, `trade_date`),
    KEY `idx_weekly_trade_date` (`trade_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__monthly` (
    `ts_code` VARCHAR(64) NOT NULL,
    `trade_date` DATE NOT NULL,
    `close` DECIMAL(38,18) NULL,
    `open` DECIMAL(38,18) NULL,
    `high` DECIMAL(38,18) NULL,
    `low` DECIMAL(38,18) NULL,
    `pre_close` DECIMAL(38,18) NULL,
    `change` DECIMAL(38,18) NULL,
    `pct_chg` DECIMAL(38,18) NULL,
    `vol` DECIMAL(38,18) NULL,
    `amount` DECIMAL(38,18) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`ts_code`, `trade_date`),
    KEY `idx_monthly_trade_date` (`trade_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__adj_factor` (
    `ts_code` VARCHAR(64) NOT NULL,
    `trade_date` DATE NOT NULL,
    `adj_factor` DECIMAL(38,18) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`ts_code`, `trade_date`),
    KEY `idx_adj_factor_trade_date` (`trade_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__suspend_d` (
    `ts_code` VARCHAR(64) NOT NULL,
    `trade_date` DATE NOT NULL,
    `suspend_timing` VARCHAR(255) NULL,
    `suspend_type` VARCHAR(64) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`ts_code`, `trade_date`),
    KEY `idx_suspend_d_trade_date` (`trade_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__daily_basic` (
    `ts_code` VARCHAR(64) NOT NULL,
    `trade_date` DATE NOT NULL,
    `close` DECIMAL(38,18) NULL,
    `turnover_rate` DECIMAL(38,18) NULL,
    `turnover_rate_f` DECIMAL(38,18) NULL,
    `volume_ratio` DECIMAL(38,18) NULL,
    `pe` DECIMAL(38,18) NULL,
    `pe_ttm` DECIMAL(38,18) NULL,
    `pb` DECIMAL(38,18) NULL,
    `ps` DECIMAL(38,18) NULL,
    `ps_ttm` DECIMAL(38,18) NULL,
    `dv_ratio` DECIMAL(38,18) NULL,
    `dv_ttm` DECIMAL(38,18) NULL,
    `total_share` DECIMAL(38,18) NULL,
    `float_share` DECIMAL(38,18) NULL,
    `free_share` DECIMAL(38,18) NULL,
    `total_mv` DECIMAL(38,18) NULL,
    `circ_mv` DECIMAL(38,18) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`ts_code`, `trade_date`),
    KEY `idx_daily_basic_trade_date` (`trade_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__stk_limit` (
    `trade_date` DATE NOT NULL,
    `ts_code` VARCHAR(64) NOT NULL,
    `up_limit` DECIMAL(38,18) NULL,
    `down_limit` DECIMAL(38,18) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`trade_date`, `ts_code`),
    KEY `idx_stk_limit_ts_code` (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__moneyflow` (
    `ts_code` VARCHAR(64) NOT NULL,
    `trade_date` DATE NOT NULL,
    `buy_sm_vol` DECIMAL(38,18) NULL,
    `buy_sm_amount` DECIMAL(38,18) NULL,
    `sell_sm_vol` DECIMAL(38,18) NULL,
    `sell_sm_amount` DECIMAL(38,18) NULL,
    `buy_md_vol` DECIMAL(38,18) NULL,
    `buy_md_amount` DECIMAL(38,18) NULL,
    `sell_md_vol` DECIMAL(38,18) NULL,
    `sell_md_amount` DECIMAL(38,18) NULL,
    `buy_lg_vol` DECIMAL(38,18) NULL,
    `buy_lg_amount` DECIMAL(38,18) NULL,
    `sell_lg_vol` DECIMAL(38,18) NULL,
    `sell_lg_amount` DECIMAL(38,18) NULL,
    `buy_elg_vol` DECIMAL(38,18) NULL,
    `buy_elg_amount` DECIMAL(38,18) NULL,
    `sell_elg_vol` DECIMAL(38,18) NULL,
    `sell_elg_amount` DECIMAL(38,18) NULL,
    `net_mf_vol` DECIMAL(38,18) NULL,
    `net_mf_amount` DECIMAL(38,18) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`ts_code`, `trade_date`),
    KEY `idx_moneyflow_trade_date` (`trade_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__margin` (
    `trade_date` DATE NOT NULL,
    `exchange_id` VARCHAR(64) NOT NULL,
    `rzye` DECIMAL(38,18) NULL,
    `rzmre` DECIMAL(38,18) NULL,
    `rzche` DECIMAL(38,18) NULL,
    `rqye` DECIMAL(38,18) NULL,
    `rqmcl` DECIMAL(38,18) NULL,
    `rzrqye` DECIMAL(38,18) NULL,
    `rqyl` DECIMAL(38,18) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`trade_date`, `exchange_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__margin_detail` (
    `trade_date` DATE NOT NULL,
    `ts_code` VARCHAR(64) NOT NULL,
    `rzye` DECIMAL(38,18) NULL,
    `rqye` DECIMAL(38,18) NULL,
    `rzmre` DECIMAL(38,18) NULL,
    `rqyl` DECIMAL(38,18) NULL,
    `rzche` DECIMAL(38,18) NULL,
    `rqchl` DECIMAL(38,18) NULL,
    `rqmcl` DECIMAL(38,18) NULL,
    `rzrqye` DECIMAL(38,18) NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`trade_date`, `ts_code`),
    KEY `idx_margin_detail_ts_code` (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__top_list` (
    `trade_date` DATE NOT NULL,
    `ts_code` VARCHAR(64) NOT NULL,
    `name` VARCHAR(128) NULL,
    `close` DECIMAL(38,18) NULL,
    `pct_change` DECIMAL(38,18) NULL,
    `turnover_rate` DECIMAL(38,18) NULL,
    `amount` DECIMAL(38,18) NULL,
    `l_sell` DECIMAL(38,18) NULL,
    `l_buy` DECIMAL(38,18) NULL,
    `l_amount` DECIMAL(38,18) NULL,
    `net_amount` DECIMAL(38,18) NULL,
    `net_rate` DECIMAL(38,18) NULL,
    `amount_rate` DECIMAL(38,18) NULL,
    `float_values` DECIMAL(38,18) NULL,
    `reason` VARCHAR(255) NOT NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`trade_date`, `ts_code`, `reason`),
    KEY `idx_top_list_ts_code` (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__top_inst` (
    `trade_date` DATE NOT NULL,
    `ts_code` VARCHAR(64) NOT NULL,
    `exalter` VARCHAR(255) NOT NULL,
    `buy` DECIMAL(38,18) NULL,
    `buy_rate` DECIMAL(38,18) NULL,
    `sell` DECIMAL(38,18) NULL,
    `sell_rate` DECIMAL(38,18) NULL,
    `net_buy` DECIMAL(38,18) NOT NULL,
    `side` VARCHAR(64) NOT NULL,
    `reason` VARCHAR(255) NOT NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`trade_date`, `ts_code`, `exalter`, `side`, `reason`, `net_buy`),
    KEY `idx_top_inst_ts_code` (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE `tushare_pro__block_trade` (
    `ts_code` VARCHAR(64) NOT NULL,
    `trade_date` DATE NOT NULL,
    `price` DECIMAL(38,18) NOT NULL,
    `vol` DECIMAL(38,18) NOT NULL,
    `amount` DECIMAL(38,18) NULL,
    `buyer` VARCHAR(255) NOT NULL,
    `seller` VARCHAR(255) NOT NULL,
    `source_plugin` VARCHAR(64) NOT NULL,
    `source_api` VARCHAR(64) NOT NULL,
    `ingested_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`trade_date`, `ts_code`, `buyer`, `seller`, `price`, `vol`),
    KEY `idx_block_trade_ts_code` (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
