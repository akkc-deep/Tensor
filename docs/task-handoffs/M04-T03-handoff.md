# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M04-T02`
- **Next task:** `M04-T03`
- **Design document:** `docs/task-designs/M04-T03-design.md`
- **Expected next status:** `READY`

## Next Task

- **ID:** `M04-T03`
- **Title:** V3 互联互通与转融通表
- **Goal:** 创建唯一生产 Flyway V3 迁移，在固定的官方 MySQL 8.4.6 中精确建立 M03-T05 的 6 张来源表、44 个业务列、统一来源字段、6 个复合主键和 4 个最小二级索引，并与 V1、V2 组成可重复迁移和校验的 30 表 schema。
- **Scope:** 只创建设计 Files 节指定的 `V3__create_connect_and_slb_tables.sql`；按 6 份冻结 YAML 原序机械转换类型和可空性，追加三个来源字段并声明设计冻结的键/索引。不得修改 POM、Java、YAML、schema、template、V1、V2、其他迁移或模块，不得创建永久测试或使用浮动 MySQL 标签。
- **Acceptance criteria:** 临时完整 harness 经历缺 V3 文件的可归因 RED 后，在本机 Colima 的官方 `mysql:8.4.6` 全新 schema 中只输出 `M04-T03_OK:30:6:44:62:4`；Flyway 首次执行 V1/V2/V3 三项、validate 和零项二次 migrate 通过；6 表/44 业务列/62 V3 总列/6 主键/4 二级索引及全局 30 表/361 列得到实际 `information_schema` 证据；`hk_hold` 保留 `code` 与 `ts_code` 的不同职责，三个 SLB 表在空样例基线下仍建立 19 个业务列；reactor `test`/`verify` 150/150、六层 Enforcer、JAR V1/V2/V3、范围/格式/清理门禁与独立审查通过；实现提交精确包含唯一 V3 SQL。

## Dependencies

### `M03-T05`

- **Artifact:** `docs/task-designs/M03-T05-design.md`，以及提交 `09967d4` 中 `moneyflow_hsgt`、`hsgt_top10`、`hk_hold`、`slb_len`、`slb_sec`、`slb_sec_detail` 共 6 份运行时 YAML。
- **Decision:** 冻结 6 API、44 个原序业务列、`STRING(64/128)`、`DATE`、`LONG`、`DECIMAL(38,18)` 类型与可空性，六个 COMPOSITE 键及精确 filters；三个 SLB 空样例仍保留完整 19 列，`hk_hold` 的业务键使用 `code`、筛选与固定列使用 `ts_code`。
- **Rationale:** 互联互通金额、转融通期限/规模及 `code/ts_code` 职责已由项目所有者批准并经公开 loader 固化，V3 只做机械 DDL 转换，不能根据空样例或相似列名重新推断。
- **Constraint:** 44 列名称、顺序、长度、精度和可空性必须与设计/YAML 同时一致；只有 `rank/market_type/tenor` 映射为 `BIGINT`，其他普通数值保持 `DECIMAL(38,18)`；业务键字段不可空且顺序不得改变，`hk_hold.code` 与 `hk_hold.ts_code` 不得合并或互换。
- **Usage:** 为 V3 六张表提供逐列 DDL、六个精确复合主键和四个未被主键最左前缀覆盖字段的单列二级索引基线；`moneyflow_hsgt` 与 `slb_len` 不创建二级索引。
- **Readiness evidence:** 权威看板为 `COMPLETED`；提交 `09967d4` 精确包含 6 个 YAML，临时 loader harness 输出 `M03-T05_OK:6`，提交后 reactor 87/87、三层 Enforcer、JAR/范围/格式/清理和两层无发现审查均已记录通过；当前 6 个 YAML 与该提交无差异。

- **Dependency comparison:** 只有一项直接依赖；其设计、提交与当前 6 份运行时 YAML一致，不存在跨依赖或内部约束冲突。已发布 V1/V2 使用同一表名公式、来源字段、MySQL 类型映射、引擎、排序规则和最小索引规则，与 M03-T05 输入兼容。

## Start Here

1. 完整读取 `docs/task-designs/M04-T03-design.md`，以其中 6 表顺序、类型映射、主键/4 索引矩阵、MySQL 8.4.6 harness、TCP readiness、失败边界和验收作为唯一实施合同。
2. 核对 `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md` 的 Global Constraints、Task M04-T03 和 Module Gate，保持 M04-T02～T06 固定使用官方 `mysql:8.4.6`。
3. 完整读取 `docs/task-designs/M03-T05-design.md`，再逐一核对提交 `09967d4` 中 6 份运行时 YAML；设计与 YAML 冲突时停止。
4. 核对已发布 V1、V2 迁移、`data-plane/tensor-app/pom.xml` 和公开 `DatasetDefinitionLoader`；不得修改它们。
5. 首个实施动作：运行设计 Tests 节的基线 reactor 与三个 SLB 空模板命令；在 150/150 和三个 `true` 通过后创建完整 `/private/tmp/M04T03SchemaCheck.java` 与 classpath，不创建 V3 SQL，确认 harness 在数据库连接前只因精确 V3 路径缺失而非 0，取得可归因 RED。

## Risks

- 三个 SLB 模板当前没有样例行，但其 19 个业务列已经冻结；不得据此删列、改型或放宽业务键。
- `hk_hold.code` 与 `hk_hold.ts_code` 同时存在但职责不同；建表、主键和索引必须保留两列。
- 官方镜像初始化阶段的 Unix socket 会先于 TCP 就绪；必须按设计轮询容器内 `127.0.0.1` 的真实 MySQL 协议，不能把临时 socket 或宿主端口接受连接误判为服务就绪。
- 现有 Flyway 会提示其最高已测试 MySQL 为 8.1；M04-T01、M04-T02 已在同一 8.4.6 镜像实际通过，M04-T03 仍必须以 migrate/validate/二次 migrate 和结果级 schema 断言为准。
- 临时 harness 不替代 M04-T06 的永久 49 表总契约，完成后必须与 classpath、容器和 Maven 生成物一并清理。
