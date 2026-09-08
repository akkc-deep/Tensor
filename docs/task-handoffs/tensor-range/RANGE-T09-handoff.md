# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-range/tensor-range-task-board.md`。
- **Completed task:** `RANGE-T08`，已先记录 COMPLETED 及本轮验证证据。
- **Next task:** `RANGE-T09`，按预定义 Order 选择的后继（Order 9）。
- **Design document:** `docs/task-designs/RANGE-T09-design.md`，详细设计已完成、完整读取、独立就绪评审 READY，并链接本项看板行。
- **Expected next status:** `READY`；写本交接时为 NOT_STARTED，先链接本交接，再执行 NOT_STARTED -> READY。READY 仅表示实施准备完成。

## Next Task

`RANGE-T09`：恢复单元划分、适配与同轮冲突。

在可靠归属和完整性基础上隔离恢复单元，并准确处理重复与冲突。按[专属设计](../../task-designs/RANGE-T09-design.md)实施 RecoveryUnitProcessor：取数前冻结精确 scope、FetchBatch 和公共条件，一次性 Session 接收完整结果或来源失败，先完成整体包络／行宽／对象时间／业务键归属检查，再暴露不可变单元。REQUEST 不推断行映射；可靠 STOCK_TIME 下按规范 DATE／MONTH／完整 RANGE 划分，整体错误必须在任何单元提交前拦截。

扩展注册的 GenericDatasetAdapter，提供保留重复的 adaptRows 和只转换键列的 adaptKeyRow，保留原 adapt、业务唯一键及旧指纹编码。新增 BusinessContentCodec，以固定版本、列顺序、类型、空值和 UTF-8 长度编码全部持久化业务内容；单元内精确字节比较，同键异值拒绝当前整个单元。CommittedKeyIndex 每次执行独立创建，仅保存确认提交后的键、版本及摘要，不保留完整行或持久成功指纹。

验收覆盖 A／B／C 中 B 普通字段错误只拒绝 B、最后一行归属错误整批拒绝、无依据时保持 REQUEST、缺行不造空或失败、完整合法空、保存 REQUEST／STOCK RANGE 不变更边界、单元内去重与同轮冲突，以及未确认提交不污染索引。KnownMembers 与独立 STOCK failure 必须使用同一规范单元规则，拒绝子 RANGE、错误 DATE 和同股重叠；全局失败只返回精确冻结 scope，已经暴露单元的 Session 不再产生整批回退。

完成设计规定验证、独立规格／质量评审、`docs/verification/RANGE-T09-recovery-units.md` 及 AC-PRD-RANGE-07／08／09／11／14／29 的本项机制追踪。不实施业务事务、失败表／仓储、S／F／N 与 I／U 编排、DownloadService／HTTP 接入或后续任务。测试中的提交替身不能证明 MySQL 事务通过。

## Dependencies

### RANGE-T04

- **Artifact:** `docs/task-designs/RANGE-T04-design.md`、`docs/verification/RANGE-T04-plugin-contracts.md`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/RecoveryPolicy.java`、`RecoverySelector.java`、`FetchBatch.java`、`FetchResult.java`、`DownloadContext.java`、`DownloadEnvelope.java`（后五项与 RecoveryPolicy 同目录）；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/descriptor/ApiDescriptor.java` 及对应合同测试。
- **Decision:** 恢复策略、对象／时间选择器、完整批次及明确来源单元失败使用不可变共享合同。REQUEST 的 targetField／timeField／unitTimeType 均为空；STOCK_TIME 必须有明确映射、独立恢复 verified 及证据。49 项生产仍为 REQUEST，完整性未确认。
- **Rationale:** 可表达恢复粒度不等于具备来源许可；普通列名、股票清单或成功 HTTP 响应不能证明独立单元完整性。统一合同保留来源与 Core 职责，避免在失败时猜测范围。
- **Constraint:** 不改 SPI、业务键、49 份 Dataset 或生产策略／schema，不新增生产 STOCK 能力。FetchResult.UnitFailure 只容纳来源码，ADAPTER／DATA_CONFLICT 使用 Core 内部 Failure。DownloadEnvelope 构造不变量直接测试构造拒绝，不通过反射制造正常路径不可能的成功包络。
- **Usage:** openInitial 直接接收 RecoverySelector scope 与 FetchBatch，openRetry 原样保留保存的 selector；检查 api／definition／参数／有序列和策略。整体预检查结束前不暴露 UnitInput；context 异常原样传播，局部错误不扩大为已成功范围的 REQUEST。
- **Readiness evidence:** T04 已 COMPLETED；共享合同与同版调用迁移、49 项模式及证据映射、原调用回归通过。记录全量 521 项 Java 单测、170 项前端测试、verify 及 acceptance 打包合同，合同 8 组＋4 个变异检查、独立规格／质量／跨模块评审 PASS。该证据支持消费类型及拒绝规则，不证明区间执行或真实来源可用。

### RANGE-T07

- **Artifact:** `docs/task-designs/RANGE-T07-design.md`、`docs/verification/RANGE-T07-complete-batch-fetch.md`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/DataSourcePlugin.java`；`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareCompleteBatchFetcher.java`、`TushareBatchSource.java`（同目录）及对应测试；`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TushareProPlugin.java`。
- **Decision:** fetchBatch 是默认拒绝的独立完整取数入口；所有页和整体范围检查通过才返回，失败不返回部分包络。保留精确原批次参数，不从缺行编造逐股状态。生产来源注册表为空，真实完整来源未启用。
- **Rationale:** 参数可重建、规划预检肯定建议和实际取全是分别验证的前提；后页失败、截断或未知结束不能把之前正常的 A 行交给单元提交。
- **Constraint:** T09 只接受严格 fetchBatch 或等义受控结果；REQUEST 也不豁免完整来源合同。不得回退旧 download、恢复部分页、依据来源异常自动拆小或升级生产证据。SOURCE_REQUEST_UNCONFIRMED 原样抛出，不登记为已经发生的业务失败；新摘要不复制来源消息或异常链。
- **Usage:** 未来编排先 open Session，再调用来源，将完整 FetchResult 交给 accept，业务 SourceException 交给 sourceFailed。成员未知的整体失败使用精确 scope；已核实完整 KnownMembers 只通过设计限定的包可见受控入口验证共同失败。列表不能授权取全或改变规范单位；accept 全局错误仍整 scope 拒绝。
- **Readiness evidence:** T07 已 COMPLETED；受控分页、精确参数／数值、后页失败、游标／请求／总数／全集及安全边界通过。记录最终模块 244 项，verify 644、acceptance 647 项 Java 检查，两者均含 170 项前端测试及构建；51 资源摘要、合同 8＋4、独立规格／质量／集成评审 PASS。生产零业务调用，数据库 IT 仅编译，真实来源和浏览器未执行。

两个直接输入一致：T04 定义可消费的恢复及包络合同，T07 提供先完整后处理的边界。T09 不把合同可表达性当作来源真实性；没有未解决的依赖冲突。T05 转换器作为已有公共辅助随下述阅读输入复用，看板直接依赖仍为 T04／T07。

## Start Here

按顺序读取：

1. 完整读取 `docs/task-designs/RANGE-T09-design.md`，核对本交接与看板本项；收到明确启动请求后执行 READY -> IN_PROGRESS，保留本交接为入口上下文。
2. `docs/design/Tensor_区间下载_TRD_v1.0.md` §4.2、§5.2、§6；实际 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/GenericDatasetAdapter.java`、同目录 `ValueConverter.java`／`FingerprintKeyCodec.java`，以及 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/BusinessKeyExtractor.java`／`BusinessKey.java` 和对应测试。
3. 上述 T04／T07 专属设计、验证记录、共享合同及完整来源入口。按 T09 设计核对 BRD §3～§6、PRD §5～§6及§9.1。
4. `docs/task-designs/RANGE-T05-design.md`、`docs/verification/RANGE-T05-parameter-conversion.md`，实际 `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/DownloadParameterConverter.java` 与 `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/SourceParameterMapper.java` 及测试。REQUEST 保留原 ts_code，STOCK 公共 map 不能带 ts_code；首次两 map 不能相等时取数前冻结 REQUEST，重试只消费原 selector 和冻结公共 map。T12 后续可从 T08 PlannedBatch 提取 scope／fetchBatch，T09 不依赖该类型。
5. 按设计 Tests 保存 49 份 Dataset 和 2 份生产策略资源摘要，保留当前分支与前项暂存成果；验证命令和结果只记录 T09 实际执行证据。

**First action:** 在待新增的 `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/RecoveryUnitProcessorTest.java` 写入设计的受控 A／B／C 场景：B 非键 amount 非法只拒绝 B，以及最后一行股票／日期归属非法导致整 scope SOURCE_PAYLOAD_INVALID 且 adaptRows 调用为零。随后补同键异内容 DATA_CONFLICT 与 Ready 尚未确认时索引仍空的断言，执行设计中的定向 Maven 命令并保存先行结果，再实施最小接口和算法。缺类型／方法造成的编译失败单独记录，不冒充运行行为反例；后续按设计矩阵验证规范 RANGE 成员与 failure 的拒绝规则。

## Risks

- 本次仅完成 T09 设计和交接，T09 代码及测试尚未实施。READY 不表示实现、事务或最终功能 AC 已通过。
- 生产日历与完整来源注册表仍为空；受控 STOCK_TIME、KnownMembers 或预检通过不能提升真实可用性。ISSUE-008 九项真实调用保持“不依赖，未解决”，本项不执行。
- FetchResult 未声明逐成员成功空，缺股／缺日不补状态；完整全空仅确认原 scope。KnownMembers 必须对应精确完整独立单元集合，不能由股票清单乘日期推导。
- 适配实现限定当前注册 GenericDatasetAdapter；不能另造适配器绕过扩展注册。T12 接入非 Generic 时须在业务前明确拒绝不兼容路径。
- 索引仅确认真实提交后更新；SQL 返回、适配成功和内存替身均不够证明数据库提交。真实业务写入与失败明细删除的原子性由 T11／T12 验证，提交未知不能继续确认索引。
- 同轮索引随已提交键数增长，只保留键、版本与摘要；不引入数量上限或跨次成功账本。SHA-256 的理论碰撞边界保留，单元内仍精确比较完整规范字节。
