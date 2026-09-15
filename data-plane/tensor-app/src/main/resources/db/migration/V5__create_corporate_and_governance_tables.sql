-- 创建分红回购、股东持股及股权质押等公司治理数据表。
-- 字段说明采用对应 Tushare 接口口径；数值按来源单位入库，日期以 DATE 存储。
-- 将本迁移会话的时区设为 UTC。
SET time_zone = '+00:00';

CREATE TABLE `tushare_pro__dividend` (
    `ts_code` VARCHAR(64) NOT NULL COMMENT 'TS代码',
    `end_date` DATE NOT NULL COMMENT '分红年度',
    `ann_date` DATE NOT NULL COMMENT '公告日(预案，决案)',
    `div_proc` VARCHAR(64) NULL COMMENT '实施进度',
    `stk_div` DECIMAL(38,18) NULL COMMENT '每股送转',
    `stk_bo_rate` DECIMAL(38,18) NULL COMMENT '每股送股比例',
    `stk_co_rate` DECIMAL(38,18) NULL COMMENT '每股转增比例',
    `cash_div` DECIMAL(38,18) NULL COMMENT '每股分红（税后）',
    `cash_div_tax` DECIMAL(38,18) NULL COMMENT '每股分红（税前）',
    `record_date` DATE NULL COMMENT '股权登记日',
    `ex_date` DATE NULL COMMENT '除权除息日',
    `pay_date` DATE NULL COMMENT '派息日',
    `div_listdate` DATE NULL COMMENT '红股上市日',
    `imp_ann_date` DATE NULL COMMENT '实施公告日',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`ts_code`, `end_date`, `ann_date`),
    -- 支持按公告日期查询。
    KEY `idx_dividend_ann_date` (`ann_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='分红送股';

CREATE TABLE `tushare_pro__repurchase` (
    `ts_code` VARCHAR(64) NOT NULL COMMENT 'TS代码',
    `ann_date` DATE NOT NULL COMMENT '公告日期',
    `end_date` DATE NULL COMMENT '截止日期',
    `proc` VARCHAR(64) NOT NULL COMMENT '进度',
    `exp_date` DATE NULL COMMENT '过期日期',
    `vol` DECIMAL(38,18) NULL COMMENT '回购数量',
    `amount` DECIMAL(38,18) NULL COMMENT '回购金额',
    `high_limit` DECIMAL(38,18) NULL COMMENT '回购最高价',
    `low_limit` DECIMAL(38,18) NULL COMMENT '回购最低价',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`ts_code`, `ann_date`, `proc`),
    -- 支持按公告日期查询。
    KEY `idx_repurchase_ann_date` (`ann_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='股票回购';

CREATE TABLE `tushare_pro__share_float` (
    `ts_code` VARCHAR(64) NOT NULL COMMENT 'TS代码',
    `ann_date` DATE NULL COMMENT '公告日期',
    `float_date` DATE NOT NULL COMMENT '解禁日期',
    `float_share` DECIMAL(38,18) NULL COMMENT '流通股份(股)',
    `float_ratio` DECIMAL(38,18) NULL COMMENT '流通股份占总股本比率',
    `holder_name` VARCHAR(128) NOT NULL COMMENT '股东名称',
    `share_type` VARCHAR(64) NOT NULL COMMENT '股份类型',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`ts_code`, `float_date`, `holder_name`, `share_type`),
    -- 支持按公告日期查询。
    KEY `idx_share_float_ann_date` (`ann_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='限售股解禁';

CREATE TABLE `tushare_pro__stk_rewards` (
    `ts_code` VARCHAR(64) NOT NULL COMMENT 'TS股票代码',
    `ann_date` DATE NOT NULL COMMENT '公告日期',
    `end_date` DATE NOT NULL COMMENT '截止日期',
    `name` VARCHAR(128) NOT NULL COMMENT '姓名',
    `title` VARCHAR(128) NULL COMMENT '职务',
    `reward` DECIMAL(38,18) NULL COMMENT '报酬（元）',
    `hold_vol` DECIMAL(38,18) NULL COMMENT '持股数（股）',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`ts_code`, `ann_date`, `end_date`, `name`),
    -- 支持按公告日期查询。
    KEY `idx_stk_rewards_ann_date` (`ann_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='管理层薪酬与持股';

CREATE TABLE `tushare_pro__stk_holdernumber` (
    `ts_code` VARCHAR(64) NOT NULL COMMENT 'TS股票代码',
    `ann_date` DATE NOT NULL COMMENT '公告日期',
    `end_date` DATE NOT NULL COMMENT '截止日期',
    `holder_num` BIGINT NULL COMMENT '股东户数',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`ts_code`, `end_date`, `ann_date`),
    -- 支持按公告日期查询。
    KEY `idx_stk_holdernumber_ann_date` (`ann_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='股东户数';

CREATE TABLE `tushare_pro__stk_holdertrade` (
    `ts_code` VARCHAR(64) NOT NULL COMMENT 'TS代码',
    `ann_date` DATE NOT NULL COMMENT '公告日期',
    `holder_name` VARCHAR(128) NOT NULL COMMENT '股东名称',
    `holder_type` VARCHAR(64) NULL COMMENT '股东类型G高管P个人C公司',
    `in_de` VARCHAR(64) NOT NULL COMMENT '类型IN增持DE减持',
    `change_vol` DECIMAL(38,18) NOT NULL COMMENT '变动数量',
    `change_ratio` DECIMAL(38,18) NULL COMMENT '占流通比例（%）',
    `after_share` DECIMAL(38,18) NULL COMMENT '变动后持股',
    `after_ratio` DECIMAL(38,18) NULL COMMENT '变动后占流通比例（%）',
    `avg_price` DECIMAL(38,18) NULL COMMENT '平均价格',
    `total_share` DECIMAL(38,18) NULL COMMENT '持股总数',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`ts_code`, `ann_date`, `holder_name`, `in_de`, `change_vol`),
    -- 支持按公告日期查询。
    KEY `idx_stk_holdertrade_ann_date` (`ann_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='股东增减持';

CREATE TABLE `tushare_pro__top10_holders` (
    `ts_code` VARCHAR(64) NOT NULL COMMENT 'TS股票代码',
    `ann_date` DATE NOT NULL COMMENT '公告日期',
    `end_date` DATE NOT NULL COMMENT '报告期',
    `holder_name` VARCHAR(128) NOT NULL COMMENT '股东名称',
    `hold_amount` DECIMAL(38,18) NULL COMMENT '持有数量（股）',
    `hold_ratio` DECIMAL(38,18) NULL COMMENT '占总股本比例(%)',
    `hold_float_ratio` DECIMAL(38,18) NULL COMMENT '占流通股本比例(%)',
    `hold_change` DECIMAL(38,18) NULL COMMENT '持股变动',
    `holder_type` VARCHAR(64) NULL COMMENT '股东类型',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`ts_code`, `end_date`, `holder_name`, `ann_date`),
    -- 支持按公告日期查询。
    KEY `idx_top10_holders_ann_date` (`ann_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='前十大股东';

CREATE TABLE `tushare_pro__top10_floatholders` (
    `ts_code` VARCHAR(64) NOT NULL COMMENT 'TS股票代码',
    `ann_date` DATE NOT NULL COMMENT '公告日期',
    `end_date` DATE NOT NULL COMMENT '报告期',
    `holder_name` VARCHAR(128) NOT NULL COMMENT '股东名称',
    `hold_amount` DECIMAL(38,18) NULL COMMENT '持有数量（股）',
    `hold_ratio` DECIMAL(38,18) NULL COMMENT '占总股本比例(%)',
    `hold_float_ratio` DECIMAL(38,18) NULL COMMENT '占流通股本比例(%)',
    `hold_change` DECIMAL(38,18) NULL COMMENT '持股变动（0表示不变，空值表示 新进）',
    `holder_type` VARCHAR(64) NULL COMMENT '股东类型',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`ts_code`, `end_date`, `holder_name`, `ann_date`),
    -- 支持按公告日期查询。
    KEY `idx_top10_floatholders_ann_date` (`ann_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='前十大流通股东';

CREATE TABLE `tushare_pro__pledge_stat` (
    `ts_code` VARCHAR(64) NOT NULL COMMENT 'TS代码',
    `end_date` DATE NOT NULL COMMENT '截止日期',
    `pledge_count` BIGINT NULL COMMENT '质押次数',
    `unrest_pledge` DECIMAL(38,18) NULL COMMENT '无限售股质押数量（万）',
    `rest_pledge` DECIMAL(38,18) NULL COMMENT '限售股份质押数量（万）',
    `total_share` DECIMAL(38,18) NULL COMMENT '总股本',
    `pledge_ratio` DECIMAL(38,18) NULL COMMENT '质押比例',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`ts_code`, `end_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='股权质押统计';

CREATE TABLE `tushare_pro__pledge_detail` (
    `ts_code` VARCHAR(64) NULL COMMENT 'TS股票代码',
    `ann_date` DATE NULL COMMENT '公告日期',
    `holder_name` VARCHAR(128) NULL COMMENT '股东名称',
    `pledge_amount` DECIMAL(38,18) NULL COMMENT '质押数量（万股）',
    `start_date` DATE NULL COMMENT '质押开始日期',
    `end_date` DATE NULL COMMENT '质押结束日期',
    `is_release` VARCHAR(64) NULL COMMENT '是否已解押',
    `release_date` DATE NULL COMMENT '解押日期',
    `pledgor` VARCHAR(128) NULL COMMENT '质押方',
    `holding_amount` DECIMAL(38,18) NULL COMMENT '持股总数（万股）',
    `pledged_amount` DECIMAL(38,18) NULL COMMENT '质押总数（万股）',
    `p_total_ratio` DECIMAL(38,18) NULL COMMENT '本次质押占总股本比例',
    `h_total_ratio` DECIMAL(38,18) NULL COMMENT '持股总数占总股本比例',
    `is_buyback` VARCHAR(64) NULL COMMENT '是否回购（0否 1是）',
    `business_key` CHAR(64) NOT NULL COMMENT '业务键字段按规范编码后生成的 SHA-256 指纹',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 业务键指纹唯一标识记录，支持业务键中允许为空的字段。
    PRIMARY KEY (`business_key`),
    -- 支持按股票代码查询。
    KEY `idx_pledge_detail_ts_code` (`ts_code`),
    -- 支持按公告日期查询。
    KEY `idx_pledge_detail_ann_date` (`ann_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='股权质押明细';
