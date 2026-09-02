# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M06-T01`
- **Next task:** `M06-T02`
- **Design document:** `docs/task-designs/M06-T02-design.md`
- **Expected next status:** `READY`; transition from `NOT_STARTED` only after this handoff is written.

## Next Task

- **ID:** `M06-T02`
- **Title:** 复合键与指纹键编码/绑定
- **Goal:** 在 `tensor-core` 中把 M05 已适配行转换为具有有序结构相等语义的不可变 `BusinessKey`，并通过明确 JDBC setter 绑定 `String`、`LocalDate`、`Long`、`BigDecimal`、`Instant` 和 typed null，供后继键预查与 Upsert 共用。
- **Scope:** 精确创建任务卡规定的 `BusinessKey.java`、`BusinessKeyExtractor.java`、`JdbcValueBinder.java` 和 `BusinessKeyExtractorTest.java`；COMPOSITE 按定义字段原序提取，FINGERPRINT 直接消费 M05 已生成的 `business_key`，Instant 显式使用 UTC，null 使用调用方提供的 JDBC type。排除 codec 重算、SQL、已有键预查、锁、计数、Upsert、事务、查询及其他任务交付物。
- **Acceptance criteria:** 必须取得只缺三个生产类型的可归因 TDD RED 和 8/8 聚焦 GREEN；COMPOSITE/FINGERPRINT、不可变结构相等、M05 固定指纹、缺失/非法键、五类明确 setter、UTC、BigDecimal 精度、typed null、未知类型和 SQLException 合同均通过；模块 `test`/`verify` 各达到 146/146，三层 Enforcer、静态、范围、格式、清理与精确四文件提交门禁全部符合设计。

## Dependencies

### `M05-T05`

- **Artifact:** `docs/task-designs/M05-T05-design.md`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/FingerprintKeyCodec.java`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/GenericDatasetAdapter.java`。
- **Decision:** M05 适配结果的 COMPOSITE 业务键值已按定义完成类型转换且非 null；FINGERPRINT 行在业务列后包含由唯一 `FingerprintKeyCodec` 生成的 64 位小写 `business_key`；允许的持久化业务值为 `String`、`LocalDate`、`Long`、`BigDecimal` 或 null。
- **Rationale:** 保持指纹规范序列化、SHA-256 和行适配只有一个实现，避免持久化层重复计算后产生不同的物理键；复用已转换 Java 类型可以让 JDBC 层只负责无损、明确的 setter 分派。
- **Constraint:** M06-T02 不得修改 M05 文件，不得重新编码、哈希或比较 identity fields；FINGERPRINT 必须直接消费并只做 `business_key` 格式复验。COMPOSITE 必须按 `BusinessKeyDefinition.fields()` 原序提取，所有输入 map/list 保持不变；任何错误不得回显键或行值。
- **Usage:** `BusinessKeyExtractor` 从每个已适配行产生后继 M06-T03 集合预查使用的结构键；`JdbcValueBinder` 绑定这些业务值及后继传入的批次来源/时间值。
- **Readiness evidence:** 看板已记录 M05-T05 为 `COMPLETED`；实现提交 `d7ec551` 与合同修复 `8ca49d0` 通过聚焦 11/11、reactor `test`/`verify` 132/132、三层 Enforcer、静态/范围/格式/清理门禁及最终审查，无剩余 Critical/Important/Minor。

该依赖的决定与 M06-T02 设计无冲突：M05 负责生成和承载稳定适配值及指纹，M06-T02 只提取物理键并绑定这些既有值。

## Start Here

按以下顺序读取：

1. `docs/task-designs/M06-T02-design.md`
2. `docs/superpowers/plans/2026-09-02-m06-t02-business-key-binding.md`
3. `docs/task-designs/M05-T05-design.md`
4. `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/FingerprintKeyCodec.java`
5. `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/GenericDatasetAdapter.java`
6. `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md` 的 Task M06-T02

首个实施动作：在允许 Mockito/Byte Buddy JVM attach 的环境运行设计中的基线命令，确认 plugin-api 79/79 加 core 59/59、合计 138/138；随后只创建完整 `BusinessKeyExtractorTest.java`，运行聚焦命令并取得仅因三个生产类型缺失的 `tensor-core:testCompile` RED。

## Risks

- extractor 只验证 `business_key` 的格式而不重新证明其与 identity fields 内容一致；调用链必须只把 M05 `GenericDatasetAdapter` 产生的已验证 `AdaptedBatch` 交给持久化层。
- null 的 `jdbcType` 由后继调用方从已验证定义/schema 提供；M06-T03/M06-T04 设计必须冻结该映射及占位符顺序，不能让 binder 猜测。
- MySQL `DATETIME` 不携带时区；显式 UTC Calendar 之外，运行连接仍须遵守 TRD 的 UTC 会话约束。
- 受限沙箱的既有 Mockito/Byte Buddy attach 错误不是代码回归；基线与最终 reactor 结论必须来自允许 JVM attach 的环境。
