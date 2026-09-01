# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M04-T04`
- **Next task:** `M04-T05`
- **Design document:** `docs/task-designs/M04-T05-design.md`
- **Expected next status:** `READY`；本交接写入并链接后从 `NOT_STARTED` 转换。

## Next Task

- **ID and title:** `M04-T05`，V5 公司行动、股东与治理表。
- **Goal:** 创建唯一生产迁移 `data-plane/tensor-app/src/main/resources/db/migration/V5__create_corporate_and_governance_tables.sql`，以固定 MySQL 8.4.6 SQL 建立 M03-T07/T08 冻结的 10 张来源表，并与 V1～V4 组成可重复迁移和校验的 49 表 schema。
- **Scope:** 只创建上述 V5 SQL；按 10 份运行时 YAML 原序转换 91 个业务列，追加 30 个来源列，仅为 `pledge_detail` 增加一个内部 `business_key CHAR(64) NOT NULL`；声明 9 个 COMPOSITE 主键、1 个 FINGERPRINT 主键和 10 个最小二级索引。不得修改 POM、Java、YAML、schema、模板、V1～V4 或其他模块，不得实现 fixture、指纹编码、Upsert、查询、下载、REST 或前端职责。
- **Acceptance:** V5 在官方 `mysql:8.4.6` 的全新 schema 中经 Flyway 首次 migrate、validate 和零项二次 migrate 后，实际得到 10 表、91 个业务列、122 个总列、10 个 PRIMARY 和 10 个二级索引；V1～V5 合计 49 表、1000 列、49 个 PRIMARY 和 40 个二级索引。`pledge_detail` 保留 14 个 nullable 业务列，以第 15 列内部 `business_key` 为唯一主键并建立 `ts_code`/`ann_date` 两个索引；完整 harness 只输出 `M04-T05_OK:49:10:91:122:10`，reactor `test`/`verify` 均为 150/150，JAR、范围、格式和清理门禁全部通过，最终实现提交只含唯一 V5 SQL。

## Dependencies

### `M03-T07`

- **Artifact:** `docs/task-designs/M03-T07-design.md` 与实现提交 `7cc724e` 中的 `dividend.yaml`、`repurchase.yaml`、`share_float.yaml`，当前文件位于 `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/`。
- **Decision:** 三个 API 合计 30 个业务列，保持模板字段原序和批准的 DATE/STRING/DECIMAL 映射；全部使用固定顺序的 COMPOSITE 业务键，filters 为 `[ts_code, ann_date]`。
- **Rationale:** 这些类型、长度、可空性、业务键和 filters 已由项目所有者批准，用于在空 `dividend` 样例下仍形成唯一、可持久化的运行时契约。
- **Constraint:** V5 必须机械保持 YAML 的列名、顺序、类型参数和可空性；主键顺序不得改变，主键最左前缀已覆盖 `ts_code` 时不得重复建索引，三个表不得增加 `business_key`。
- **Usage:** V5 从这三份 YAML 建立 `dividend`、`repurchase`、`share_float` 的业务列、复合主键和 `ann_date` 二级索引。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；提交 `7cc724e` 精确交付三份 YAML，记录的完整 loader harness 输出 `M03-T07_OK:3:30`，reactor `test`/`verify` 为 87/87，独立审查无发现；当前三份文件相对该提交无差异。

### `M03-T08`

- **Artifact:** `docs/task-designs/M03-T08-design.md` 与实现提交 `cedb21b` 中的 `stk_rewards.yaml`、`stk_holdernumber.yaml`、`stk_holdertrade.yaml`、`top10_holders.yaml`、`top10_floatholders.yaml`、`pledge_stat.yaml`、`pledge_detail.yaml`，当前文件位于 `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/`。
- **Decision:** 七个 API 合计 61 个业务列，保持模板字段原序和批准的 DATE/STRING/LONG/DECIMAL 映射；六表使用固定顺序的 COMPOSITE 业务键，`pledge_detail` 以全部 14 个 nullable 字段原序构成 FINGERPRINT。
- **Rationale:** 这些类型、长度、可空性和业务键已由项目所有者批准，使两个空 top-10 样例仍有完整契约，并允许 `pledge_detail` 的合法空值参与确定性指纹。
- **Constraint:** V5 必须机械保持 YAML 契约；六个 COMPOSITE 主键顺序不得改变，`pledge_detail` 的 14 个业务列不得收紧可空性，其数据库主键只能是内部 `business_key`，并因该主键不覆盖 filters 而分别建立 `ts_code` 和 `ann_date` 索引。
- **Usage:** V5 从这七份 YAML 建立股东与治理表的业务列、六个复合主键、一个内部指纹主键及相应最小索引。
- **Readiness evidence:** 权威看板状态为 `COMPLETED`；提交 `cedb21b` 精确交付七份 YAML，记录的完整 loader harness 输出 `M03-T08_OK:7:61`，reactor `test`/`verify` 为 87/87，独立审查无发现；当前七份文件相对该提交无差异。

两项输入覆盖互不重叠的 3+7 个 API，采用同一表名公式、字段顺序、逻辑类型参数和“COMPOSITE 键列不可空、其余列按批准契约”规则；M03-T08 的 FINGERPRINT 特例由 M04-T05 设计明确映射为内部 `business_key`，决策和约束无冲突。

## Start Here

按以下顺序读取：

1. `docs/task-designs/M04-T05-design.md`。
2. `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 的 `Global Constraints`、`Task M04-T05` 和 `Module Gate`。
3. `docs/task-designs/M03-T07-design.md`。
4. `docs/task-designs/M03-T08-design.md`。
5. `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/` 下的 `dividend.yaml`、`repurchase.yaml`、`share_float.yaml`、`stk_rewards.yaml`、`stk_holdernumber.yaml`、`stk_holdertrade.yaml`、`top10_holders.yaml`、`top10_floatholders.yaml`、`pledge_stat.yaml`、`pledge_detail.yaml`。
6. `data-plane/tensor-app/src/main/resources/db/migration/` 下现有 V1～V4 SQL。
7. `data-plane/tensor-app/pom.xml` 与 `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/metadata/DatasetDefinitionLoader.java`。

首个实施动作：在 V5 仍不存在时，先运行设计中的 reactor 基线和三个空模板 `jq` 断言；随后创建完整 `/private/tmp/M04T05SchemaCheck.java`，安装既有模块并生成 `/private/tmp/M04-T05-classpath.txt`，再以不可用数据库地址运行同一 harness，取得只因 `migration file missing: data-plane/tensor-app/src/main/resources/db/migration/V5__create_corporate_and_governance_tables.sql` 而失败的精确 RED，之后才创建 V5 SQL。

## Risks

- `dividend`、`top10_holders` 和 `top10_floatholders` 共 32 个业务列没有样例行；必须以冻结 YAML 和实际 MySQL 8.4.6 schema 门禁为准，不得根据缺失样例改型或删列。
- 五张表包含较长复合主键；若实际 MySQL 报索引长度错误，只能修正 V5 的可实现错误，不得缩窄业务类型或改变业务键。
- `pledge_detail` 的 14 个 FINGERPRINT 输入全部允许空值；本任务只存储内部 `business_key`，不实现或改变后续指纹编码规范。
- 现有 Flyway 可能提示其最高已测试 MySQL 为 8.1；验收仍固定使用官方 `mysql:8.4.6` 的 migrate、validate、零项二次 migrate 和结果级 schema 断言。
