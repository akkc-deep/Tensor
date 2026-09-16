-- 创建股票基础信息、上市公司及行业组织相关数据表。
-- 字段说明采用对应 Tushare 接口口径；数值按来源单位入库，日期以 DATE 存储。
-- 将本迁移会话的时区设为 UTC。
SET time_zone = '+00:00';

CREATE TABLE `tushare_pro__stock_basic` (
    `ts_code` VARCHAR(64) NOT NULL COMMENT 'TS代码',
    `symbol` VARCHAR(64) NULL COMMENT '股票代码',
    `name` VARCHAR(128) NULL COMMENT '股票名称',
    `area` VARCHAR(128) NULL COMMENT '地域',
    `industry` VARCHAR(128) NULL COMMENT '所属行业',
    `cnspell` VARCHAR(64) NULL COMMENT '拼音缩写',
    `market` VARCHAR(64) NULL COMMENT '市场类型（主板/创业板/科创板/CDR）',
    `list_date` DATE NULL COMMENT '上市日期',
    `act_name` VARCHAR(128) NULL COMMENT '实控人名称',
    `act_ent_type` VARCHAR(128) NULL COMMENT '实控人企业性质',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='股票基础信息';

CREATE TABLE `tushare_pro__stock_company` (
    `ts_code` VARCHAR(64) NOT NULL COMMENT '股票代码',
    `com_name` VARCHAR(128) NULL COMMENT '公司全称',
    `com_id` VARCHAR(64) NULL COMMENT '统一社会信用代码',
    `chairman` VARCHAR(128) NULL COMMENT '法人代表',
    `manager` VARCHAR(128) NULL COMMENT '总经理',
    `secretary` VARCHAR(128) NULL COMMENT '董秘',
    `reg_capital` DECIMAL(38,18) NULL COMMENT '注册资本(万元)',
    `setup_date` DATE NULL COMMENT '注册日期',
    `province` VARCHAR(128) NULL COMMENT '所在省份',
    `city` VARCHAR(128) NULL COMMENT '所在城市',
    `introduction` TEXT NULL COMMENT '公司介绍',
    `website` VARCHAR(255) NULL COMMENT '公司主页',
    `email` VARCHAR(255) NULL COMMENT '电子邮件',
    `office` VARCHAR(255) NULL COMMENT '办公室',
    `business_scope` TEXT NULL COMMENT '经营范围',
    `employees` BIGINT NULL COMMENT '员工人数',
    `main_business` TEXT NULL COMMENT '主要业务及产品',
    `exchange` VARCHAR(64) NULL COMMENT '交易所代码',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='上市公司基本信息';

CREATE TABLE `tushare_pro__hs_const` (
    `ts_code` VARCHAR(64) NOT NULL COMMENT 'TS 股票代码',
    `hs_type` VARCHAR(64) NOT NULL COMMENT '沪深港通类型：SH 沪股通，SZ 深股通',
    `in_date` DATE NOT NULL COMMENT '纳入日期',
    `out_date` DATE NULL COMMENT '剔除日期',
    `is_new` VARCHAR(64) NULL COMMENT '是否为最新成分：1 是，0 否',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`hs_type`, `ts_code`, `in_date`),
    -- 支持按股票代码查询。
    KEY `idx_hs_const_ts_code` (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='沪深港通成分股';

CREATE TABLE `tushare_pro__trade_cal` (
    `exchange` VARCHAR(64) NOT NULL COMMENT '交易所 SSE上交所 SZSE深交所',
    `cal_date` DATE NOT NULL COMMENT '日历日期',
    `is_open` BIGINT NULL COMMENT '是否交易 0休市 1交易',
    `pretrade_date` DATE NULL COMMENT '上一个交易日',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`exchange`, `cal_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='交易日历';

CREATE TABLE `tushare_pro__new_share` (
    `ts_code` VARCHAR(64) NOT NULL COMMENT 'TS股票代码',
    `sub_code` VARCHAR(64) NULL COMMENT '申购代码',
    `name` VARCHAR(128) NULL COMMENT '名称',
    `ipo_date` DATE NULL COMMENT '上网发行日期',
    `issue_date` DATE NULL COMMENT '上市日期',
    `amount` DECIMAL(38,18) NULL COMMENT '发行总量（万股）',
    `market_amount` DECIMAL(38,18) NULL COMMENT '上网发行总量（万股）',
    `price` DECIMAL(38,18) NULL COMMENT '发行价格',
    `pe` DECIMAL(38,18) NULL COMMENT '市盈率',
    `limit_amount` DECIMAL(38,18) NULL COMMENT '个人申购上限（万股）',
    `funds` DECIMAL(38,18) NULL COMMENT '募集资金（亿元）',
    `ballot` DECIMAL(38,18) NULL COMMENT '中签率',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='IPO 新股发行信息';

CREATE TABLE `tushare_pro__namechange` (
    `ts_code` VARCHAR(64) NOT NULL COMMENT 'TS代码',
    `name` VARCHAR(128) NOT NULL COMMENT '证券名称',
    `start_date` DATE NOT NULL COMMENT '开始日期',
    `end_date` DATE NULL COMMENT '结束日期',
    `ann_date` DATE NULL COMMENT '公告日期',
    `change_reason` VARCHAR(255) NULL COMMENT '变更原因',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`ts_code`, `start_date`, `name`),
    -- 支持按公告日期查询。
    KEY `idx_namechange_ann_date` (`ann_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='股票曾用名';

CREATE TABLE `tushare_pro__stk_managers` (
    `ts_code` VARCHAR(64) NULL COMMENT 'TS股票代码',
    `ann_date` DATE NULL COMMENT '公告日期',
    `name` VARCHAR(128) NULL COMMENT '姓名',
    `gender` VARCHAR(64) NULL COMMENT '性别',
    `lev` VARCHAR(64) NULL COMMENT '岗位类别',
    `title` VARCHAR(128) NULL COMMENT '岗位',
    `edu` VARCHAR(128) NULL COMMENT '学历',
    `national` VARCHAR(128) NULL COMMENT '国籍',
    `birthday` DATE NULL COMMENT '出生年月',
    `begin_date` DATE NULL COMMENT '上任日期',
    `end_date` DATE NULL COMMENT '离任日期',
    `business_key` CHAR(64) NOT NULL COMMENT '业务键字段按规范编码后生成的 SHA-256 指纹',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 业务键指纹唯一标识记录，支持业务键中允许为空的字段。
    PRIMARY KEY (`business_key`),
    -- 支持按股票代码查询。
    KEY `idx_stk_managers_ts_code` (`ts_code`),
    -- 支持按公告日期查询。
    KEY `idx_stk_managers_ann_date` (`ann_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='上市公司管理层信息';

CREATE TABLE `tushare_pro__broker_recommend` (
    `month` CHAR(6) NOT NULL COMMENT '月度',
    `broker` VARCHAR(128) NOT NULL COMMENT '券商',
    `ts_code` VARCHAR(64) NOT NULL COMMENT '股票代码',
    `name` VARCHAR(128) NULL COMMENT '股票简称',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`month`, `broker`, `ts_code`),
    -- 支持按股票代码查询。
    KEY `idx_broker_recommend_ts_code` (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='券商月度金股推荐';

CREATE TABLE `tushare_pro__index_classify` (
    `index_code` VARCHAR(64) NOT NULL COMMENT '指数代码',
    `industry_name` VARCHAR(128) NULL COMMENT '行业名称',
    `level` VARCHAR(64) NULL COMMENT '行业层级：L1 一级，L2 二级，L3 三级',
    `industry_code` VARCHAR(64) NULL COMMENT '行业代码',
    `is_pub` VARCHAR(64) NULL COMMENT '是否发布了指数',
    `parent_code` VARCHAR(64) NULL COMMENT '父级代码',
    `src` VARCHAR(64) NULL COMMENT '行业分类（SW申万）',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`index_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='行业指数分类';

CREATE TABLE `tushare_pro__index_member` (
    `index_code` VARCHAR(64) NOT NULL COMMENT '申万行业指数代码',
    `con_code` VARCHAR(64) NOT NULL COMMENT '成分股票代码',
    `in_date` DATE NOT NULL COMMENT '纳入日期',
    `out_date` DATE NULL COMMENT '剔除日期',
    `is_new` VARCHAR(64) NULL COMMENT '是否为最新成分：Y 是，N 否',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`index_code`, `con_code`, `in_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='申万行业成分股';

CREATE TABLE `tushare_pro__index_member_all` (
    `l1_code` VARCHAR(64) NOT NULL COMMENT '一级行业代码',
    `l1_name` VARCHAR(128) NULL COMMENT '一级行业名称',
    `l2_code` VARCHAR(64) NOT NULL COMMENT '二级行业代码',
    `l2_name` VARCHAR(128) NULL COMMENT '二级行业名称',
    `l3_code` VARCHAR(64) NOT NULL COMMENT '三级行业代码',
    `l3_name` VARCHAR(128) NULL COMMENT '三级行业名称',
    `ts_code` VARCHAR(64) NOT NULL COMMENT '成分股票代码',
    `name` VARCHAR(128) NULL COMMENT '成分股票名称',
    `in_date` DATE NOT NULL COMMENT '纳入日期',
    `out_date` DATE NULL COMMENT '剔除日期',
    `is_new` VARCHAR(64) NULL COMMENT '是否最新Y是N否',
    `source_plugin` VARCHAR(64) NOT NULL COMMENT '来源插件标识',
    `source_api` VARCHAR(64) NOT NULL COMMENT '来源接口名称',
    `ingested_at` DATETIME(3) NOT NULL COMMENT '数据入库时间（UTC，毫秒精度）',
    -- 以来源业务键唯一标识记录，支持重复下载时更新同一条数据。
    PRIMARY KEY (`l1_code`, `l2_code`, `l3_code`, `ts_code`, `in_date`),
    -- 支持按股票代码查询。
    KEY `idx_index_member_all_ts_code` (`ts_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='行业分级与完整成分';
