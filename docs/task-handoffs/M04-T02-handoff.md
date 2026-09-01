# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M04-T01`
- **Next task:** `M04-T02`
- **Design document:** `docs/task-designs/M04-T02-design.md`
- **Expected next status:** `READY`

## Next Task

- **ID:** `M04-T02`
- **Title:** V2 行情、交易与资金表
- **Goal:** 创建唯一生产 Flyway V2 迁移，在固定的官方 MySQL 8.4.6 中精确建立 M03-T03/M03-T04 的 13 张来源表、133 个业务列、统一来源字段、13 个复合主键和 12 个最小二级索引，并与 V1 组成可重复迁移和校验的 24 表 schema。
- **Scope:** 只创建设计 Files 节指定的 `V2__create_market_and_trading_tables.sql`；按 13 份冻结 YAML 原序机械转换类型和可空性，追加三个来源字段并声明设计冻结的键/索引。不得修改 POM、Java、YAML、schema、模板、V1、其他迁移或模块，不得创建永久测试或使用浮动 MySQL 标签。
- **Acceptance criteria:** 临时完整 harness 经历缺 V2 文件的可归因 RED 后，在本机 Colima 的官方 `mysql:8.4.6` 全新 schema 中只输出 `M04-T02_OK:24:13:133:172:12`；Flyway 首次执行 V1/V2 两项、validate 和零项二次 migrate 通过；13 表/133 业务列/172 V2 总列/13 主键/12 二级索引及全局 24 表/299 列得到实际 `information_schema` 证据；`daily` 恰有 14 列和正确主键；reactor `test`/`verify` 150/150、六层 Enforcer、JAR V1/V2、范围/格式/清理门禁与独立审查通过；实现提交精确包含唯一 V2 SQL。

## Dependencies

### `M03-T03`

- **Artifact:** `docs/task-designs/M03-T03-design.md`，以及提交 `3c2e977` 中 `daily`、`weekly`、`monthly`、`adj_factor`、`suspend_d`、`daily_basic`、`stk_limit` 共 7 份运行时 YAML。
- **Decision:** 冻结 7 API、62 个原序业务列、`STRING(64/255)`、`DATE`、`DECIMAL(38,18)` 类型与可空性，七个 COMPOSITE 键及精确 filters；其中 `stk_limit` 主键顺序为 `(trade_date, ts_code)`，其余六表为 `(ts_code, trade_date)`。
- **Rationale:** 行情与估值字段、精度、空样例和筛选规则已由项目所有者批准并经公开 loader 固化，V2 只做机械 DDL 转换，不能重新推断。
- **Constraint:** 62 列名称、顺序、长度、精度和可空性必须与设计/YAML 同时一致；全部数值保持 `DECIMAL(38,18)`，不得缩窄或改为浮点；主键和 filters 顺序彼此独立，不得重排。
- **Usage:** 为 V2 前七张表提供逐列 DDL、六个 `(ts_code, trade_date)` 主键、一个 `(trade_date, ts_code)` 主键，以及七个未被主键最左前缀覆盖字段的单列二级索引基线。
- **Readiness evidence:** 权威看板为 `COMPLETED`；提交 `3c2e977` 精确包含 7 个 YAML，临时 loader harness 输出 `M03-T03_OK:7`，提交后 reactor `test`/`verify` 87/87、三层 Enforcer、JAR/范围/格式/清理和两层无发现审查均已记录通过。

### `M03-T04`

- **Artifact:** `docs/task-designs/M03-T04-design.md`，以及提交 `c00ea0d` 中 `moneyflow`、`margin`、`margin_detail`、`top_list`、`top_inst`、`block_trade` 共 6 份运行时 YAML。
- **Decision:** 冻结 6 API、71 个原序业务列、批准的字符串长度、`DATE`、`DECIMAL(38,18)` 类型与可空性，六个 COMPOSITE 键及精确 filters；`margin` 唯一 filter `trade_date` 已由主键最左前缀覆盖。
- **Rationale:** 交易金额、融资融券指标、龙虎榜和大宗交易身份字段已由项目所有者批准并经公开 loader 固化，V2 必须保存完整身份而不能用更短字段或哈希键替代。
- **Constraint:** `top_list.reason`、`top_inst.exalter/side/reason/net_buy` 和 `block_trade.buyer/seller/price/vol` 必须保持不可空并按冻结顺序进入主键；所有数值列保持 `DECIMAL(38,18)`，`buyer/seller/reason/exalter` 保持 `VARCHAR(255)`。
- **Usage:** 为 V2 后六张表提供逐列 DDL、六个精确复合主键和五个未被主键最左前缀覆盖字段的单列二级索引基线；`margin` 不创建二级索引。
- **Readiness evidence:** 权威看板为 `COMPLETED`；提交 `c00ea0d` 精确包含 6 个 YAML，临时 loader harness 输出 `M03-T04_OK:6`，提交后独立 loader 检查、reactor `test`/`verify` 87/87、三层 Enforcer、JAR/范围/格式/清理和两层无发现审查均已记录通过。

- **Dependency comparison:** 两项直接依赖的 API 集互不重叠，按 7+6 合计 13 表、62+71 合计 133 个业务列；全部使用 `tushare_pro`、同一公开 loader、`tushare_pro__<api>` 表名公式、COMPOSITE 键、来源字段规则和同一机械 MySQL 类型映射。差异只在各自冻结的主键顺序、字符串长度和 filters，已由 M04-T02 设计逐表列明，不存在未解决冲突。

## Start Here

1. 完整读取 `docs/task-designs/M04-T02-design.md`，以其中 13 表顺序、类型映射、主键/12 索引矩阵、MySQL 8.4.6 harness、失败边界和验收作为唯一实施合同。
2. 核对 `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 的 Global Constraints、Task M04-T02 和 Module Gate，保持 M04-T02～T06 固定使用官方 `mysql:8.4.6`。
3. 完整读取 `docs/task-designs/M03-T03-design.md`、`docs/task-designs/M03-T04-design.md`，再逐一核对其提交 `3c2e977`、`c00ea0d` 中 13 份运行时 YAML；设计与 YAML 冲突时停止。
4. 核对已发布 `data-plane/tensor-app/src/main/resources/db/migration/V1__create_basic_and_organization_tables.sql`、`data-plane/tensor-app/pom.xml` 和公开 `DatasetDefinitionLoader`；不得修改它们。
5. 首个实施动作：运行设计 Tests 节的基线 reactor 命令；在 150/150 通过后创建完整 `/private/tmp/M04T02SchemaCheck.java` 和 classpath，不创建 V2 SQL，确认 harness 在数据库连接前只因精确 V2 路径缺失而非 0，取得可归因 RED。

## Risks

- M04-T02～T06 的实际数据库验证固定使用本机 Colima 中官方 `mysql:8.4.6`；镜像或 daemon 不可用时必须报告环境阻塞，不得改用浮动标签、其他补丁版本或非 MySQL 数据库。
- 现有 Flyway 会提示其最高已测试 MySQL 为 8.1；M04-T01 已在同一 8.4.6 镜像实际通过，M04-T02 仍必须以 migrate/validate/二次 migrate 和结果级 schema 断言为准。
- `top_inst` 与 `block_trade` 的长字符串复合主键可能接近 InnoDB 索引长度边界；必须用 MySQL 8.4.6 实际执行验证，不得缩短字段或改变业务键规避失败。
- 临时 harness 不替代 M04-T06 的永久 49 表总契约，完成后必须与 classpath、容器和 Maven 生成物一并清理。
