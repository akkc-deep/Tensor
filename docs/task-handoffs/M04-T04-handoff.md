# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M04-T03`
- **Next task:** `M04-T04`
- **Design document:** `docs/task-designs/M04-T04-design.md`
- **Expected next status:** `READY`

## Next Task

- **ID:** `M04-T04`
- **Title:** V4 财务与披露宽表
- **Goal:** 创建唯一生产 Flyway V4 迁移，在固定的官方 MySQL 8.4.6 中精确建立 M03-T06 的 9 张财务与披露来源表、490 个业务列、统一来源字段、9 个复合主键和 8 个最小二级索引，并与 V1～V3 组成可重复迁移和校验的 39 表 schema。
- **Scope:** 只创建设计 Files 节指定的 `V4__create_financial_tables.sql`；按 9 份冻结 YAML 原序机械转换类型和可空性，追加三个来源字段并声明设计冻结的键/索引。不得修改 POM、Java、YAML、schema、template、V1～V3、其他迁移或模块，不得创建永久测试或使用浮动 MySQL 标签。
- **Acceptance criteria:** 临时完整 harness 经历缺 V4 文件的可归因 RED 后，在本机 Colima 的官方 `mysql:8.4.6` 全新 schema 中只输出 `M04-T04_OK:39:9:490:517:8`；Flyway 首次执行 V1～V4 四项、validate 和零项二次 migrate 通过；9 表/490 业务列/517 V4 总列/9 主键/8 二级索引及全局 39 表/878 列/39 主键/30 二级索引得到实际 `information_schema` 证据；`balancesheet` 保留全部 152 个业务列，`fina_mainbz` 不发明 `ann_date`，三个长文本字段保持可空 `TEXT`，五个空样例数据集仍建立 449 个业务列；reactor `test`/`verify` 150/150、六层 Enforcer、JAR V1～V4、范围/格式/清理门禁与独立审查通过；实现提交精确包含唯一 V4 SQL。

## Dependencies

### `M03-T06`

- **Artifact:** `docs/task-designs/M03-T06-design.md`，以及提交 `73f9278` 中 `income`、`balancesheet`、`cashflow`、`fina_indicator`、`fina_audit`、`fina_mainbz`、`express`、`forecast`、`disclosure_date` 共 9 份运行时 YAML。
- **Decision:** 冻结 9 API、490 个原序业务列、DATE 23、STRING 30、TEXT 3、DECIMAL 434 的机械类型分布、“业务键列不可空、其余列可空”、九个 COMPOSITE 键及精确 filters；五个空样例数据集仍保留完整 449 列，`fina_mainbz` 保留 `ann_date` 参数但没有同名业务列。
- **Rationale:** 财务宽表字段、类型、长文本、业务键、筛选和参数/列差异已由项目所有者批准并经公开 loader 固化，V4 只做机械 DDL 转换，不能根据字段名语义、空样例或查询参数重新推断。
- **Constraint:** 490 列名称、顺序、长度、精度、`longText` 和可空性必须与设计/YAML 同时一致；STRING 只转为相同长度的 `VARCHAR`，DATE 转为 `DATE`，三个长文本转为 `TEXT`，434 个数值保持 `DECIMAL(38,18)`；业务键字段不可空且顺序不得改变，`fina_mainbz` 不得新增 `ann_date` 列。
- **Usage:** 为 V4 九张表提供逐列 DDL、九个精确复合主键和八个未被主键最左前缀覆盖的 `ann_date` 单列二级索引基线；`fina_mainbz` 不创建二级索引。
- **Readiness evidence:** 权威看板为 `COMPLETED`；提交 `73f9278` 精确包含 9 个 YAML，临时 loader harness 输出 `M03-T06_OK:9:490`，提交后 reactor 87/87、三层 Enforcer、JAR/范围/格式/清理和无发现任务审查均已记录通过；当前 9 个 YAML 与该提交无差异。

- **Dependency comparison:** 只有一项直接依赖；其设计、提交与当前 9 份运行时 YAML 一致，不存在跨依赖或内部约束冲突。已发布 V1～V3 使用同一表名公式、来源字段、MySQL 类型映射、引擎、排序规则和最小索引规则，与 M03-T06 输入兼容。

## Start Here

1. 完整读取 `docs/task-designs/M04-T04-design.md`，以其中 9 表顺序、类型映射、主键/8 索引矩阵、MySQL 8.4.6 harness、TCP readiness、失败边界和验收作为唯一实施合同。
2. 核对 `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 的 Global Constraints、Task M04-T04 和 Module Gate，保持 M04-T02～T06 固定使用官方 `mysql:8.4.6`。
3. 完整读取 `docs/task-designs/M03-T06-design.md`，再逐一核对提交 `73f9278` 中 9 份运行时 YAML；设计与 YAML 冲突时停止。
4. 核对已发布 V1～V3 迁移、`data-plane/tensor-app/pom.xml` 和公开 `DatasetDefinitionLoader`；不得修改它们。
5. 首个实施动作：运行设计 Tests 节的基线 reactor 与五个空模板命令；在 150/150 和五个 `true` 通过后创建完整 `/private/tmp/M04T04SchemaCheck.java` 与 classpath，不创建 V4 SQL，确认 harness 在数据库连接前只因精确 V4 路径缺失而非 0，取得可归因 RED。

## Risks

- 五个空样例数据集共 449 个业务列已经冻结；不得据此删列、改型或放宽业务键。
- `balancesheet`、`cashflow` 和 `fina_indicator` 是宽表，`fina_mainbz` 的复合主键包含 `VARCHAR(255)`；必须由官方 MySQL 8.4.6 实际门禁确认行大小和索引长度，不能通过缩窄业务类型绕过失败。
- `fina_mainbz` 接受 `ann_date` 查询参数但没有同名业务列；建表不得发明该列。
- `perf_summary`、`summary` 和 `change_reason` 必须保持可空 `TEXT`，不得降为 `VARCHAR`。
- 官方镜像初始化阶段的 Unix socket 会先于 TCP 就绪；必须轮询容器内 `127.0.0.1` 的真实 MySQL 协议。
- 现有 Flyway 会提示其最高已测试 MySQL 为 8.1；M04-T01～T03 已在同一 8.4.6 镜像实际通过，M04-T04 仍必须以 migrate/validate/二次 migrate 和结果级 schema 断言为准。
