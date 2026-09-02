# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M05-T03`
- **Next task:** `M05-T04`
- **Design document:** `docs/task-designs/M05-T04-design.md`
- **Expected next status:** `READY`；本交接写入并链接后执行 `NOT_STARTED -> READY`。

## Next Task

- **ID / title:** `M05-T04`——严格日期、文本、整数和精确数值转换。
- **Goal:** 在 `tensor-core` 中建立唯一的元数据驱动单元值转换边界，严格把来源标量转换为 `String`、`LocalDate`、`Long`、`BigDecimal` 或 null，并以不含原始值的统一 `ADAPTER_TYPE_INVALID` 摘要定位失败。
- **Scope:** 精确创建 `ValueConverter.java`、`ConversionContext.java` 和 `ValueConverterTest.java`；冻结 null/TEXT、短字符串/枚举、DATE/MONTH、LONG/DECIMAL、运行时输入类型和安全错误；不修改 POM、plugin-api、YAML、SQL 或既有类型，不实现通用适配、字段映射、不可空/业务键校验、下载、持久化、REST 或日志。
- **Acceptance criteria:** 两个公开生产类型与设计表面一致；七项逻辑类型只返回批准的五类结果，短字符串不截断、日期/月严格、整数无小数/溢出、十进制无 double/舍入/超精度；失败统一使用只含 API、从 0 开始行号和字段名的安全摘要；取得缺两个生产类型的可归因 RED 后 12/12 GREEN，reactor `test`/`verify` 121/121、三层 Enforcer、静态/范围/格式/清理和精确三文件提交门禁全部通过。

## Dependencies

### `M02-T03`

- **Artifact:** `docs/task-designs/M02-T03-design.md`；实现提交 `551c18f` 中 `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/dataset/LogicalType.java` 与 `ColumnDefinition.java`，以及 Unicode 边界修复/设计同步提交 `0a74740`、`bcd5a91`。
- **Decision:** `LogicalType` 精确为 `STRING|TEXT|DATE|MONTH|LONG|DECIMAL|ENUM`；`ColumnDefinition` 保存安全字段名、逻辑类型、nullable、STRING/ENUM length、DECIMAL precision/scale、allowedValues 和 longText，列表保序不可变，空 allowedValues 允许开放 ENUM。
- **Rationale:** 列转换必须只由稳定、跨模块的元数据合同驱动，不能复制类型枚举、按字段名猜测或读取具体插件实现。
- **Constraint:** 不修改或重建 plugin-api 类型；STRING/ENUM 的 length 非 null，DECIMAL 的 precision/scale 非 null；字段名已满足统一标识正则，allowedValues 保持声明内容和顺序；nullable 只描述后继必填校验，不改变 M05-T04 的 null 保留语义。
- **Usage:** `ValueConverter` 对 `column.logicalType()` 做 exhaustive switch，读取 length、precision、scale、allowedValues 和 name 完成转换与安全定位；`ValueConverterTest` 直接构造真实 `ColumnDefinition` 验证全部七类行为。
- **Readiness evidence:** 权威看板为 `COMPLETED`；最终聚焦测试 9/9、模块 `test`/`verify` 54/54、Enforcer、Unicode 码点边界、范围/格式/清理和无发现复审均已记录通过；当前 M05-T04 新鲜基线再次编译并运行这些 plugin-api 契约为 79/79 全绿。

### `M02-T05`

- **Artifact:** `docs/task-designs/M02-T05-design.md`；实现提交 `445b941` 与契约修复 `dd495ee` 中 `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/error/AdapterException.java`、`ErrorCode.java` 和 `TensorException.java`。
- **Decision:** 类型转换失败使用 retryable=false 的 `ErrorCode.ADAPTER_TYPE_INVALID`；`AdapterException` 只接受两项适配错误码并继承只保存非空安全 message/code 的 `TensorException`，没有 cause 重载或额外诊断字段。
- **Rationale:** core 必须复用跨模块统一错误分类和安全响应边界，不能创建第二套异常、错误码或把底层解析诊断带入公开错误。
- **Constraint:** M05-T04 只能以 `(ErrorCode.ADAPTER_TYPE_INVALID, safeMessage)` 构造异常；不得加入 cause、原始值、来源类名、目标类型、解析异常文本、Token 或内部路径；null/nullable 失败留给 M05-T05 使用 `ADAPTER_FIELD_MISSING`。
- **Usage:** 所有来源转换失败委托单一 helper 创建 `AdapterException`；测试逐类断言 code、`retryable == false` 和精确安全 message。
- **Readiness evidence:** 权威看板为 `COMPLETED`；最终聚焦测试 8/8、模块 `verify` 79/79、Enforcer、`jdeps=java.base`、禁用依赖、范围/格式/清理及无发现复审均已记录通过；当前公开异常实现相对上述提交无后续代码修改。

两项直接依赖的决策与约束一致：M02-T03 唯一定义转换分派和目标列参数，M02-T05 唯一定义失败分类与安全异常；前者的安全字段名可以直接进入后者的安全摘要，nullable 与 `ADAPTER_FIELD_MISSING` 明确保留给 M05-T05，不存在职责重叠、依赖环或消息边界冲突。未发现冲突。

## Start Here

按以下顺序读取：

1. `docs/task-designs/M05-T04-design.md`；
2. `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md` 的 Global Constraints、Task M05-T04 与 Module Gate；
3. `docs/task-designs/M02-T03-design.md`；
4. `docs/task-designs/M02-T05-design.md`；
5. 上述 Dependencies 中列出的当前 `LogicalType`、`ColumnDefinition`、`AdapterException`、`ErrorCode` 和 `TensorException` 实现。

首个实施动作：在允许 Mockito/Byte Buddy 本地 JVM attach 的环境运行 `mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml -pl tensor-core -am test`，确认 plugin-api 79 项、core 30 项、合计 109/109 与三层 Enforcer 通过；随后只完整创建 `ValueConverterTest.java`，在两个生产类型不存在时运行设计规定的聚焦命令，取得只因 `ValueConverter`/`ConversionContext` 缺失产生的可归因 `testCompile` RED。

## Risks

- `DatasetStartupValidatorTest` 使用 Mockito inline mock maker；attach 受限沙箱会产生 10 项 `MockMaker` 初始化错误。同一基线命令已在允许 JVM attach 的环境确认 109/109，全量门禁必须使用该执行条件，不能把环境失败误判为代码 RED。
- M07-T02 后续必须把上游小数 JSON 保留为 BigDecimal；若先生成 Float/Double，本任务按批准设计拒绝，不能恢复可能已经丢失的精度。
- M05-T05 必须在转换后对 `nullable == false` 和业务键 null 使用 `ADAPTER_FIELD_MISSING`；M05-T04 刻意不复制该职责。
- TEXT 保留空字符串和纯空白，STRING/ENUM/DATE/MONTH/数值字符串则 trim 后空转 null；实施不得为了统一代码路径而合并这两种语义。
