-- 创建行情、估值、资金流向及融资融券等交易数据表。
-- 字段说明采用对应 Tushare 接口口径；数值按来源单位入库，日期以 DATE 存储。
-- 将本迁移会话的时区设为 UTC。
SET time_zone = '+00:00';

CREATE TABLE `tushare_pro__daily` (
    `ts_code` VARCHAR(64) NOT NULL COMMENT '股票代码',
    `trade_date` DATE NOT NULL COMMENT '交易日期',
    `open` DECIMAL(38,18) NULL COMMENT '开盘价',
    `high` DECIMAL(38,18) NULL COMMENT '最高价',
    `low` DECIMAL(38,18) NULL COMMENT '最低价',
    `close` DECIMAL(38,18) NULL COMMENT '收盘价',
    `pre_close` DECIMAL(38,18) NULL COMMENT '昨收价【除权价】',
    `change` DECIMAL(38,18) NULL COMMENT '涨跌额',
    `pct_chg` DECIMAL(38,18) NULL COMMENT '涨跌幅（%，基于除权后的昨收价计算）',
    `vol` DECIMAL(38,18) NULL COMMENT '成交量 （手）',
    `amount` DECIMAL(38,18) NULL COMMENT '成交额 （千元）',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`ts_code`, `trade_date`),
    -- 支持按交易日期查询。
    KEY `idx_daily_trade_date` (`trade_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='日线行情';

CREATE TABLE `tushare_pro__weekly` (
    `ts_code` VARCHAR(64) NOT NULL COMMENT '股票代码',
    `trade_date` DATE NOT NULL COMMENT '交易日期',
    `close` DECIMAL(38,18) NULL COMMENT '周收盘价',
    `open` DECIMAL(38,18) NULL COMMENT '周开盘价',
    `high` DECIMAL(38,18) NULL COMMENT '周最高价',
    `low` DECIMAL(38,18) NULL COMMENT '周最低价',
    `pre_close` DECIMAL(38,18) NULL COMMENT '上一周收盘价',
    `change` DECIMAL(38,18) NULL COMMENT '周涨跌额',
    `pct_chg` DECIMAL(38,18) NULL COMMENT '周涨跌幅（未复权，保留来源口径）',
    `vol` DECIMAL(38,18) NULL COMMENT '周成交量',
    `amount` DECIMAL(38,18) NULL COMMENT '周成交额',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`ts_code`, `trade_date`),
    -- 支持按交易日期查询。
    KEY `idx_weekly_trade_date` (`trade_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='周线行情';

CREATE TABLE `tushare_pro__monthly` (
    `ts_code` VARCHAR(64) NOT NULL COMMENT '股票代码',
    `trade_date` DATE NOT NULL COMMENT '交易日期',
    `close` DECIMAL(38,18) NULL COMMENT '月收盘价',
    `open` DECIMAL(38,18) NULL COMMENT '月开盘价',
    `high` DECIMAL(38,18) NULL COMMENT '月最高价',
    `low` DECIMAL(38,18) NULL COMMENT '月最低价',
    `pre_close` DECIMAL(38,18) NULL COMMENT '上月收盘价',
    `change` DECIMAL(38,18) NULL COMMENT '月涨跌额',
    `pct_chg` DECIMAL(38,18) NULL COMMENT '月涨跌幅（未复权，保留来源口径）',
    `vol` DECIMAL(38,18) NULL COMMENT '月成交量',
    `amount` DECIMAL(38,18) NULL COMMENT '月成交额',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`ts_code`, `trade_date`),
    -- 支持按交易日期查询。
    KEY `idx_monthly_trade_date` (`trade_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='月线行情';

CREATE TABLE `tushare_pro__adj_factor` (
    `ts_code` VARCHAR(64) NOT NULL COMMENT '股票代码',
    `trade_date` DATE NOT NULL COMMENT '交易日期',
    `adj_factor` DECIMAL(38,18) NULL COMMENT '复权因子',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`ts_code`, `trade_date`),
    -- 支持按交易日期查询。
    KEY `idx_adj_factor_trade_date` (`trade_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='复权因子';

CREATE TABLE `tushare_pro__suspend_d` (
    `ts_code` VARCHAR(64) NOT NULL COMMENT 'TS代码',
    `trade_date` DATE NOT NULL COMMENT '停复牌日期',
    `suspend_timing` VARCHAR(255) NULL COMMENT '日内停牌时间段（日内停牌才有值，否则为空值）',
    `suspend_type` VARCHAR(64) NULL COMMENT '停复牌类型：S-停牌，R-复牌',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`ts_code`, `trade_date`),
    -- 支持按交易日期查询。
    KEY `idx_suspend_d_trade_date` (`trade_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='每日停复牌信息';

CREATE TABLE `tushare_pro__daily_basic` (
    `ts_code` VARCHAR(64) NOT NULL COMMENT 'TS股票代码',
    `trade_date` DATE NOT NULL COMMENT '交易日期',
    `close` DECIMAL(38,18) NULL COMMENT '当日收盘价',
    `turnover_rate` DECIMAL(38,18) NULL COMMENT '换手率 (成交量/无限售流通股数)',
    `turnover_rate_f` DECIMAL(38,18) NULL COMMENT '换手率（自由流通股）(成交量/自由流通股数)',
    `volume_ratio` DECIMAL(38,18) NULL COMMENT '量比 VOL/MA',
    `pe` DECIMAL(38,18) NULL COMMENT '市盈率（总市值/净利润， 亏损的PE为空）',
    `pe_ttm` DECIMAL(38,18) NULL COMMENT '市盈率（ 总市值/净利润TTM，亏损的PE为空）',
    `pb` DECIMAL(38,18) NULL COMMENT '市净率（总市值/(净资产-其他权益工具)）',
    `ps` DECIMAL(38,18) NULL COMMENT '市销率 (总市值/营业收入(最新年报))',
    `ps_ttm` DECIMAL(38,18) NULL COMMENT '市销率（TTM）(总市值/营业收入TTM)',
    `dv_ratio` DECIMAL(38,18) NULL COMMENT '股息率 （%），除息日发生在去年期间的派现',
    `dv_ttm` DECIMAL(38,18) NULL COMMENT '股息率（TTM）（%），除息日在近12个月且分红报告期在12个月以内的派现',
    `total_share` DECIMAL(38,18) NULL COMMENT '总股本 （万股）',
    `float_share` DECIMAL(38,18) NULL COMMENT '流通股本 （万股）',
    `free_share` DECIMAL(38,18) NULL COMMENT '自由流通股本 （万）',
    `total_mv` DECIMAL(38,18) NULL COMMENT '总市值 （万元）',
    `circ_mv` DECIMAL(38,18) NULL COMMENT '流通市值（万元）',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`ts_code`, `trade_date`),
    -- 支持按交易日期查询。
    KEY `idx_daily_basic_trade_date` (`trade_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='每日估值与市场指标';

CREATE TABLE `tushare_pro__stk_limit` (
    `trade_date` DATE NOT NULL COMMENT '交易日期',
    `ts_code` VARCHAR(64) NOT NULL COMMENT 'TS股票代码',
    `up_limit` DECIMAL(38,18) NULL COMMENT '涨停价',
    `down_limit` DECIMAL(38,18) NULL COMMENT '跌停价',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`trade_date`, `ts_code`),
    -- 支持按股票代码查询。
    KEY `idx_stk_limit_ts_code` (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='每日涨跌停价格';

CREATE TABLE `tushare_pro__moneyflow` (
    `ts_code` VARCHAR(64) NOT NULL COMMENT 'TS代码',
    `trade_date` DATE NOT NULL COMMENT '交易日期',
    `buy_sm_vol` DECIMAL(38,18) NULL COMMENT '小单买入量（手）',
    `buy_sm_amount` DECIMAL(38,18) NULL COMMENT '小单买入金额（万元）',
    `sell_sm_vol` DECIMAL(38,18) NULL COMMENT '小单卖出量（手）',
    `sell_sm_amount` DECIMAL(38,18) NULL COMMENT '小单卖出金额（万元）',
    `buy_md_vol` DECIMAL(38,18) NULL COMMENT '中单买入量（手）',
    `buy_md_amount` DECIMAL(38,18) NULL COMMENT '中单买入金额（万元）',
    `sell_md_vol` DECIMAL(38,18) NULL COMMENT '中单卖出量（手）',
    `sell_md_amount` DECIMAL(38,18) NULL COMMENT '中单卖出金额（万元）',
    `buy_lg_vol` DECIMAL(38,18) NULL COMMENT '大单买入量（手）',
    `buy_lg_amount` DECIMAL(38,18) NULL COMMENT '大单买入金额（万元）',
    `sell_lg_vol` DECIMAL(38,18) NULL COMMENT '大单卖出量（手）',
    `sell_lg_amount` DECIMAL(38,18) NULL COMMENT '大单卖出金额（万元）',
    `buy_elg_vol` DECIMAL(38,18) NULL COMMENT '特大单买入量（手）',
    `buy_elg_amount` DECIMAL(38,18) NULL COMMENT '特大单买入金额（万元）',
    `sell_elg_vol` DECIMAL(38,18) NULL COMMENT '特大单卖出量（手）',
    `sell_elg_amount` DECIMAL(38,18) NULL COMMENT '特大单卖出金额（万元）',
    `net_mf_vol` DECIMAL(38,18) NULL COMMENT '净流入量（手）',
    `net_mf_amount` DECIMAL(38,18) NULL COMMENT '净流入额（万元）',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`ts_code`, `trade_date`),
    -- 支持按交易日期查询。
    KEY `idx_moneyflow_trade_date` (`trade_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='个股资金流向';

CREATE TABLE `tushare_pro__margin` (
    `trade_date` DATE NOT NULL COMMENT '交易日期',
    `exchange_id` VARCHAR(64) NOT NULL COMMENT '交易所代码（SSE上交所SZSE深交所BSE北交所）',
    `rzye` DECIMAL(38,18) NULL COMMENT '融资余额(元)',
    `rzmre` DECIMAL(38,18) NULL COMMENT '融资买入额(元)',
    `rzche` DECIMAL(38,18) NULL COMMENT '融资偿还额(元)',
    `rqye` DECIMAL(38,18) NULL COMMENT '融券余额(元)',
    `rqmcl` DECIMAL(38,18) NULL COMMENT '融券卖出量(股,份,手)',
    `rzrqye` DECIMAL(38,18) NULL COMMENT '融资融券余额(元)',
    `rqyl` DECIMAL(38,18) NULL COMMENT '融券余量(股,份,手)',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`trade_date`, `exchange_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='融资融券汇总';

CREATE TABLE `tushare_pro__margin_detail` (
    `trade_date` DATE NOT NULL COMMENT '交易日期',
    `ts_code` VARCHAR(64) NOT NULL COMMENT 'TS股票代码',
    `rzye` DECIMAL(38,18) NULL COMMENT '融资余额(元)',
    `rqye` DECIMAL(38,18) NULL COMMENT '融券余额(元)',
    `rzmre` DECIMAL(38,18) NULL COMMENT '融资买入额(元)',
    `rqyl` DECIMAL(38,18) NULL COMMENT '融券余量（股）',
    `rzche` DECIMAL(38,18) NULL COMMENT '融资偿还额(元)',
    `rqchl` DECIMAL(38,18) NULL COMMENT '融券偿还量(股)',
    `rqmcl` DECIMAL(38,18) NULL COMMENT '融券卖出量(股,份,手)',
    `rzrqye` DECIMAL(38,18) NULL COMMENT '融资融券余额(元)',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`trade_date`, `ts_code`),
    -- 支持按股票代码查询。
    KEY `idx_margin_detail_ts_code` (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='融资融券交易明细';

CREATE TABLE `tushare_pro__top_list` (
    `trade_date` DATE NOT NULL COMMENT '交易日期',
    `ts_code` VARCHAR(64) NOT NULL COMMENT 'TS代码',
    `name` VARCHAR(128) NULL COMMENT '名称',
    `close` DECIMAL(38,18) NULL COMMENT '收盘价',
    `pct_change` DECIMAL(38,18) NULL COMMENT '涨跌幅',
    `turnover_rate` DECIMAL(38,18) NULL COMMENT '换手率',
    `amount` DECIMAL(38,18) NULL COMMENT '总成交额',
    `l_sell` DECIMAL(38,18) NULL COMMENT '龙虎榜卖出额',
    `l_buy` DECIMAL(38,18) NULL COMMENT '龙虎榜买入额',
    `l_amount` DECIMAL(38,18) NULL COMMENT '龙虎榜成交额',
    `net_amount` DECIMAL(38,18) NULL COMMENT '龙虎榜净买入额',
    `net_rate` DECIMAL(38,18) NULL COMMENT '龙虎榜净买额占比',
    `amount_rate` DECIMAL(38,18) NULL COMMENT '龙虎榜成交额占比',
    `float_values` DECIMAL(38,18) NULL COMMENT '当日流通市值',
    `reason` VARCHAR(255) NOT NULL COMMENT '上榜理由',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`trade_date`, `ts_code`, `reason`),
    -- 支持按股票代码查询。
    KEY `idx_top_list_ts_code` (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='龙虎榜每日明细';

CREATE TABLE `tushare_pro__top_inst` (
    `trade_date` DATE NOT NULL COMMENT '交易日期',
    `ts_code` VARCHAR(64) NOT NULL COMMENT 'TS代码',
    `exalter` VARCHAR(255) NOT NULL COMMENT '营业部名称',
    `buy` DECIMAL(38,18) NULL COMMENT '买入额（元）',
    `buy_rate` DECIMAL(38,18) NULL COMMENT '买入占总成交比例',
    `sell` DECIMAL(38,18) NULL COMMENT '卖出额（元）',
    `sell_rate` DECIMAL(38,18) NULL COMMENT '卖出占总成交比例',
    `net_buy` DECIMAL(38,18) NOT NULL COMMENT '净成交额（元）',
    `side` VARCHAR(64) NOT NULL COMMENT '买卖类型0：买入金额最大的前5名， 1：卖出金额最大的前5名',
    `reason` VARCHAR(255) NOT NULL COMMENT '上榜理由',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`trade_date`, `ts_code`, `exalter`, `side`, `reason`, `net_buy`),
    -- 支持按股票代码查询。
    KEY `idx_top_inst_ts_code` (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='龙虎榜机构交易明细';

CREATE TABLE `tushare_pro__block_trade` (
    `ts_code` VARCHAR(64) NOT NULL COMMENT 'TS代码',
    `trade_date` DATE NOT NULL COMMENT '交易日历',
    `price` DECIMAL(38,18) NOT NULL COMMENT '成交价',
    `vol` DECIMAL(38,18) NOT NULL COMMENT '成交量（万股）',
    `amount` DECIMAL(38,18) NULL COMMENT '成交金额',
    `buyer` VARCHAR(255) NOT NULL COMMENT '买方营业部',
    `seller` VARCHAR(255) NOT NULL COMMENT '卖方营业部',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`trade_date`, `ts_code`, `buyer`, `seller`, `price`, `vol`),
    -- 支持按股票代码查询。
    KEY `idx_block_trade_ts_code` (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='大宗交易';
