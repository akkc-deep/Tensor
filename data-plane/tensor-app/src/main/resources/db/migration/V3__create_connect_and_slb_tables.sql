-- 创建沪深港通和转融资、转融券数据表。
-- 字段说明采用对应 Tushare 接口口径；数值按来源单位入库，日期以 DATE 存储。
-- 将本迁移会话的时区设为 UTC。
SET time_zone = '+00:00';

CREATE TABLE `tushare_pro__moneyflow_hsgt` (
    `trade_date` DATE NOT NULL COMMENT '交易日期',
    `ggt_ss` DECIMAL(38,18) NULL COMMENT '港股通（沪）资金净流入额（百万元）',
    `ggt_sz` DECIMAL(38,18) NULL COMMENT '港股通（深）资金净流入额（百万元）',
    `hgt` DECIMAL(38,18) NULL COMMENT '沪股通资金净流入额（百万元）',
    `sgt` DECIMAL(38,18) NULL COMMENT '深股通资金净流入额（百万元）',
    `north_money` DECIMAL(38,18) NULL COMMENT '北向资金净流入额（百万元）',
    `south_money` DECIMAL(38,18) NULL COMMENT '南向资金净流入额（百万元）',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`trade_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='沪深港通资金流向';

CREATE TABLE `tushare_pro__hsgt_top10` (
    `trade_date` DATE NOT NULL COMMENT '交易日期',
    `ts_code` VARCHAR(64) NOT NULL COMMENT '股票代码',
    `name` VARCHAR(128) NULL COMMENT '股票名称',
    `close` DECIMAL(38,18) NULL COMMENT '收盘价',
    `change` DECIMAL(38,18) NULL COMMENT '涨跌额',
    `rank` BIGINT NULL COMMENT '资金排名',
    `market_type` BIGINT NOT NULL COMMENT '市场类型（1：沪市 3：深市）',
    `amount` DECIMAL(38,18) NULL COMMENT '成交金额（元）',
    `net_amount` DECIMAL(38,18) NULL COMMENT '净成交金额（元）',
    `buy` DECIMAL(38,18) NULL COMMENT '买入金额（元）',
    `sell` DECIMAL(38,18) NULL COMMENT '卖出金额（元）',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`trade_date`, `ts_code`, `market_type`),
    -- 支持按股票代码查询。
    KEY `idx_hsgt_top10_ts_code` (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='沪深股通十大成交股';

CREATE TABLE `tushare_pro__hk_hold` (
    `code` VARCHAR(64) NOT NULL COMMENT '原始股票代码',
    `trade_date` DATE NOT NULL COMMENT '交易日期',
    `ts_code` VARCHAR(64) NULL COMMENT 'TS 股票代码',
    `name` VARCHAR(128) NULL COMMENT '股票名称',
    `vol` DECIMAL(38,18) NULL COMMENT '持股数量（股）',
    `ratio` DECIMAL(38,18) NULL COMMENT '持股占比（%）',
    `exchange` VARCHAR(64) NOT NULL COMMENT '交易所标识',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`trade_date`, `code`, `exchange`),
    -- 支持按股票代码查询。
    KEY `idx_hk_hold_ts_code` (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='沪深港通持股明细';

CREATE TABLE `tushare_pro__slb_len` (
    `trade_date` DATE NOT NULL COMMENT '交易日期',
    `ob` DECIMAL(38,18) NOT NULL COMMENT '期初余额(亿元)',
    `auc_amount` DECIMAL(38,18) NULL COMMENT '竞价成交金额(亿元)',
    `repo_amount` DECIMAL(38,18) NULL COMMENT '再借成交金额(亿元)',
    `repay_amount` DECIMAL(38,18) NULL COMMENT '偿还金额(亿元)',
    `cb` DECIMAL(38,18) NULL COMMENT '期末余额(亿元)',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`trade_date`, `ob`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='转融通期限与规模';

CREATE TABLE `tushare_pro__slb_sec` (
    `trade_date` DATE NOT NULL COMMENT '交易日期',
    `ts_code` VARCHAR(64) NOT NULL COMMENT '股票代码',
    `name` VARCHAR(128) NULL COMMENT '股票名称',
    `ope_inv` DECIMAL(38,18) NULL COMMENT '期初余量(万股)',
    `lent_qnt` DECIMAL(38,18) NULL COMMENT '转融券融出数量(万股)',
    `cls_inv` DECIMAL(38,18) NULL COMMENT '期末余量(万股)',
    `end_bal` DECIMAL(38,18) NULL COMMENT '期末余额(万元)',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`trade_date`, `ts_code`),
    -- 支持按股票代码查询。
    KEY `idx_slb_sec_ts_code` (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='转融通证券汇总';

CREATE TABLE `tushare_pro__slb_sec_detail` (
    `trade_date` DATE NOT NULL COMMENT '交易日期',
    `ts_code` VARCHAR(64) NOT NULL COMMENT '股票代码',
    `name` VARCHAR(128) NULL COMMENT '股票名称',
    `tenor` BIGINT NOT NULL COMMENT '期限（天）',
    `fee_rate` DECIMAL(38,18) NOT NULL COMMENT '融出费率(%)',
    `lent_qnt` DECIMAL(38,18) NULL COMMENT '转融券融出数量(万股)',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`trade_date`, `ts_code`, `tenor`, `fee_rate`),
    -- 支持按股票代码查询。
    KEY `idx_slb_sec_detail_ts_code` (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='转融通证券明细';
