# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M05-T04`
- **Next task:** `M05-T05`
- **Design document:** `docs/task-designs/M05-T05-design.md`
- **Expected next status:** `READY`；本交接写入并链接后执行 `NOT_STARTED -> READY`。

## Next Task

- **ID / title:** `M05-T05`——`GenericDatasetAdapter`、重复键和指纹键。
- **Goal:** 在 `tensor-core` 中把身份和字段均匹配已验证定义的成功 `DownloadEnvelope`，严格适配为不可变 `AdaptedBatch`；转换后校验不可空列和业务键，稳定处理批次内重复键，并为 FINGERPRINT 数据集生成批准格式的 SHA-256 `business_key`，任何失败均不产生部分批次。
- **Scope:** 精确创建 `GenericDatasetAdapter.java`、`FingerprintKeyCodec.java` 和 `GenericDatasetAdapterTest.java`；实现成功包络准入、一次字段索引、逐值转换、缺失校验、COMPOSITE/FINGERPRINT 键、完全相同重复行去重、冲突重复键整批失败、空行批次和单一 `ingestedAt`。不修改 POM、plugin-api、既有 M05 Java、YAML、SQL 或其他模块，不重复参数/目录/schema 校验，不实现插件调用、持久化、事务、查询、REST 或前端。
- **Acceptance criteria:** 两个生产类的公开表面与设计精确一致；身份/字段/值/缺失错误使用批准的安全边界；指纹固定采用字段原序、`0x00|0x01` tag、4 字节大端 UTF-8 长度、五类规范文本和 64 位小写 SHA-256，固定向量通过；COMPOSITE/FINGERPRINT 完全相同行只保留首项，同键不同内容整批失败；业务列、可选 `business_key`、空 rows 和唯一 `ingestedAt` 正确；取得缺两个生产类的可归因 RED 后 11/11 GREEN，reactor `test`/`verify` 132/132、三层 Enforcer、静态/范围/格式/清理和精确三文件提交门禁全部通过。

## Dependencies

### `M02-T04`

- **Artifact:** `docs/task-designs/M02-T04-design.md`；提交 `075d1d4` 中的 `DownloadEnvelope.java`、`DownloadStatus.java` 与 `AdaptedBatch.java`。
- **Decision:** 成功包络冻结不可变 params/fields/data、非空字段、`rowCount == data.size()` 和行宽合同；失败包络无部分载荷。`AdaptedBatch` 冻结 dataset/table、columns、完整行 map、原业务键定义和单一 `Instant`，并允许空 rows。
- **Rationale:** 适配器应只补跨对象的定义匹配、值转换和业务键语义，不复制已由公共 records 保证的局部形状和容器不变量。
- **Constraint:** 不修改或重建 plugin-api records；只适配 `SUCCESS` 包络；批次每行 key 集必须精确等于 columns，业务键字段仍引用批次 columns，输入容器不得被修改。
- **Usage:** `GenericDatasetAdapter.adapt` 读取包络身份、字段和二维数据，返回业务列原序加可选 `business_key` 的 `AdaptedBatch`，并把调用方传入的同一 `ingestedAt` 放入 batch component。
- **Readiness evidence:** 权威看板为 `COMPLETED`；当前三个公共类型仍由提交 `075d1d4` 提供，设计与当前声明表面一致。

### `M02-T05`

- **Artifact:** `docs/task-designs/M02-T05-design.md`；提交 `445b941` 与后续契约修复中的 `DatasetAdapter.java`、`AdapterException.java`、`ErrorCode.java` 和 `TensorException.java`。
- **Decision:** `DatasetAdapter` 精确公开 `datasetKey()`、`definition()`、`adapt(DownloadEnvelope,Instant)`；适配失败只能使用 retryable=false 的 `ADAPTER_FIELD_MISSING|ADAPTER_TYPE_INVALID`，异常只携带安全 message/code 且无 cause 重载。
- **Rationale:** core 的通用适配器必须实现已发布 SPI，并复用统一领域错误，不能创建第二套接口、错误码或诊断 DTO。
- **Constraint:** 不修改 plugin-api；缺失/不可映射字段使用 `ADAPTER_FIELD_MISSING`，类型或冲突键使用 `ADAPTER_TYPE_INVALID`；消息不得包含原始值、完整行、业务键、Token、来源响应、内部路径或 cause。
- **Usage:** `GenericDatasetAdapter` 实现现有 SPI；身份/字段/必填/冲突失败按完成设计中的精确消息构造 `AdapterException`，M05-T04 的类型异常原样传播。
- **Readiness evidence:** 权威看板为 `COMPLETED`；当前 SPI 和错误类型可编译且由既有 plugin-api 回归覆盖。

### `M05-T02`

- **Artifact:** `docs/task-designs/M05-T02-design.md`；提交 `57771b0` 中的 `DatasetCatalog.java`、`DatasetStartupValidator.java` 和 `SchemaInspector.java`。
- **Decision:** `DatasetCatalog` 只暴露元数据关系和实际表结构均验证通过、DatasetKey 唯一的不可变定义；FINGERPRINT 物理表在业务列后有 `business_key`，三个来源技术列另行存在。
- **Rationale:** Generic adapter 可以信任构造时定义的字段/键/表关系，专注运行时包络和值，不维护第二套 schema 准入逻辑。
- **Constraint:** 装配方必须从已验证目录取得定义；本任务不自行查询 catalog、不读取 JDBC/YAML/SQL、不验证物理表，也不把 `source_plugin`、`source_api`、`ingested_at` 放入行 map。
- **Usage:** 构造 `GenericDatasetAdapter` 时传入目录选出的 `DatasetDefinition`；业务列和可选 `business_key` 进入 rows，datasetKey/ingestedAt 留给 M06 绑定来源技术列。
- **Readiness evidence:** 权威看板为 `COMPLETED`；目录公开表面和 FINGERPRINT schema 关系已由该任务的完成证据确认。

### `M05-T03`

- **Artifact:** `docs/task-designs/M05-T03-design.md`；提交 `6e86d46` 与严格日期修复 `be26e31` 中的 `ParameterValidator.java` 和 `ValidatedParameters.java`。
- **Decision:** 下载参数在调用插件前仅由元数据驱动准入，并以有序不可变字符串 map 传给下游；未知、缺失、类型和范围错误在上游调用前结束。
- **Rationale:** 适配器处理的是插件成功返回的数据行，重复校验 `DownloadEnvelope.params()` 会产生第二套参数规则并混淆职责。
- **Constraint:** M05-T05 不读取、改写或重新验证 params，不按 API/参数名分支；后继下载编排必须先完成 M05-T03 校验，再调用插件并只把成功包络交给 adapter。
- **Usage:** 作为调用顺序和排除边界；`GenericDatasetAdapter` 的实现与测试不依赖 `ValidatedParameters` 类型，也不复制参数校验器逻辑。
- **Readiness evidence:** 权威看板为 `COMPLETED`；严格参数、日期/月和安全错误的完成证据已记录。

### `M05-T04`

- **Artifact:** `docs/task-designs/M05-T04-design.md`；提交 `e609f50` 中的 `ValueConverter.java`、`ConversionContext.java` 与 `ValueConverterTest.java`。
- **Decision:** 七项逻辑类型严格转换为 `String|LocalDate|Long|BigDecimal|null`，DECIMAL 固化目标 scale，失败使用精确安全 `ADAPTER_TYPE_INVALID`；nullable 和业务键缺失明确留给 M05-T05 在转换后校验。
- **Rationale:** 单值转换与批次编排分层，避免日期、数字、TEXT/ENUM 和错误消息规则被通用适配器重复实现。
- **Constraint:** 每个来源单元恰调用一次 converter；类型失败不捕获、不包装；null 仅在转换后依据 `nullable == false` 或业务键成员判为 `ADAPTER_FIELD_MISSING`；不得接受 Float/Double、任意 `toString()`、猜测、取整或舍入。
- **Usage:** 每行以从 0 开始的 `ConversionContext` 按定义列序转换；转换结果构成完整行、COMPOSITE 结构键和 FINGERPRINT 规范文本。
- **Readiness evidence:** 权威看板为 `COMPLETED`；提交 `e609f50` 经主控新鲜 `test`/`verify` 121/121、三层 Enforcer、静态/范围/清理门禁和两层无发现审查确认。

五项直接输入没有冲突：M02-T04/T05 冻结数据形状、SPI 和错误，M05-T02 保证定义准入，M05-T03 保证插件调用前参数准入，M05-T04 保证单值严格转换；M05-T05 只聚合这些合同形成完整批次、指纹和批次内键语义。FINGERPRINT 的 `business_key` 与原身份字段同时存在，适配行不承担 M06 的来源技术列绑定。

## Start Here

按以下顺序读取：

1. `docs/task-designs/M05-T05-design.md`；
2. `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 的 Global Constraints、Task M05-T05 与 Module Gate；
3. `docs/task-designs/M02-T04-design.md` 与当前 `DownloadEnvelope`/`AdaptedBatch`；
4. `docs/task-designs/M02-T05-design.md` 与当前 `DatasetAdapter`/领域错误；
5. `docs/task-designs/M05-T02-design.md` 与当前 `DatasetCatalog`；
6. `docs/task-designs/M05-T03-design.md` 与当前参数准入边界；
7. `docs/task-designs/M05-T04-design.md` 与当前 `ValueConverter`/`ConversionContext`；
8. `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 的 8.4 适配校验顺序与 9.4 首期业务键合同。

首个实施动作：在允许 Mockito/Byte Buddy 本地 JVM attach 的环境运行 `mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml -pl tensor-core -am test`，确认 plugin-api 79 项、core 42 项、合计 121/121 与三层 Enforcer 通过；随后只完整创建 `GenericDatasetAdapterTest.java`，在两个生产类型不存在时运行设计规定的聚焦命令，取得只因 `GenericDatasetAdapter`/`FingerprintKeyCodec` 缺失产生的可归因 `testCompile` RED。

## Risks

- 指纹 tag、长度、字符集、字段顺序和规范文本已成为持久化幂等合同；未来变化必须与版本化元数据、Flyway 键迁移和历史重算一起发布。
- M07-T02 必须保留小数为 `BigDecimal`；Float/Double 已由 M05-T04 拒绝，codec 不能恢复丢失精度。
- 参数准入不在 adapter 内重复；后继下载服务必须维持“参数校验→插件成功包络→适配”的调用顺序。
- `source_plugin`、`source_api`、`ingested_at` 由 M06 从 batch components 绑定，不得因为行 map 未包含它们而扩展本任务范围。
- 完整 reactor 门禁必须允许 Mockito/Byte Buddy 本地 JVM attach；受限沙箱的既有 `MockMaker` 错误不是代码 RED 或回归结论。
