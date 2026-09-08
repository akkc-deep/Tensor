# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-range/tensor-range-task-board.md`。
- **Completed task:** `RANGE-T09`，已先记录 COMPLETED 及本轮验证证据。
- **Next task:** `RANGE-T10`，按预定义 Order 选择的后继（Order 10）。
- **Design document:** `docs/task-designs/RANGE-T10-design.md`，详细设计已完成、完整读取、独立就绪评审 READY，并已链接本项看板行。
- **Expected next status:** `READY`；写本交接时为 NOT_STARTED，先链接本交接，再执行 NOT_STARTED -> READY。READY 仅表示实施准备完成。

## Next Task

`RANGE-T10`：两张失败记录表与存储访问。

用两张 MySQL 表精确保存当前尚未解决的明确失败，供查询和重试使用。按[专属设计](../../task-designs/RANGE-T10-design.md)新增唯一 V8 迁移，以及 Core 的 `TaskParametersJson`、`RetryTaskRepository`、`RetryTaskStorageService` 和三个 App bean；实现首次主表／首明细原子创建、后续追加、重复原因更新、完整选择器键定位及分页读取。

验收须证明：首次和追加事务失败不留下半条结构；同日两股和不连续日期各自独立；JSON 对象保存公共条件及首次原始日期，后续原因更新不覆盖参数；列表按 updated_at DESC、task_id DESC 排序，页大小20／50／100、筛选和空／超尾页处理符合设计。查询只依赖保存标识，插件下线不隐藏记录。创建时在同一事务读回实际 JSON 比较键值含义，数值语义损失须回滚；SavedFailure 只在提交正常返回后发布。

保留全部旧迁移、49张业务表与数据、Dataset YAML及生产策略；V6仍仅用于测试／acceptance。仅新增TRD两张管理表，不保存状态、版本、计划、凭证或响应。仓储提供加入调用者事务的锁定、完整键删除及主表收尾原语；业务写入与成功明细删除的组合由T11实现。本项不接管下载流程、HTTP或页面。

完成设计规定的实际MySQL验证、两次完整构建、独立规格／质量评审及 `docs/verification/RANGE-T10-failure-storage.md`，更新AC-PRD-RANGE-21／22／26的存储部分追踪。默认Maven不执行数据库IT，必须运行设计中的两条显式IT命令并保存六类实际结果；数据库未执行不能标COMPLETED。

## Dependencies

### RANGE-T04

- **Artifact:** `docs/task-designs/RANGE-T04-design.md`、`docs/verification/RANGE-T04-plugin-contracts.md`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/RecoverySelector.java`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/model/DatasetKey.java`、同目录 `PluginId.java`／`ApiName.java`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/error/ErrorCode.java` 及对应合同测试。
- **Decision:** 复用不可变共享对象／时间选择器与已有标识和错误码。REQUEST的target_value为空串，NONE的time_value为空串；STOCK为规范单股代码，DATE／MONTH／RANGE使用严格规范字符串，RANGE保留完整范围。
- **Rationale:** 完整对象＋时间共同标识一个失败恢复单元，同日不同股票不能合并。类型可表达性不等于来源已核实或允许独立恢复。
- **Constraint:** 不新增选择器枚举、unit_key或业务数据集定义，不改SPI／49份Dataset／生产策略。只有设计允许的明确失败码可入表；参数、日历和来源请求未确认、存储保存或提交未知不能预登记为明确失败。存储不得凭类型启用生产STOCK能力。
- **Usage:** Repository的ItemKey组合UUID与原RecoverySelector，所有存在检查、原因更新及删除均使用五列完整主键。读取复用共享类型的结构规范；当前插件／接口策略兼容性由后续执行层判断，存储查询不访问插件注册表。
- **Readiness evidence:** T04已COMPLETED，共享合同、同版调用迁移和49项登记已验证；记录521项Java单测、verify525／acceptance528项Java检查、170项前端测试及构建，合同8组＋4个变异和独立评审PASS。该证据支持消费类型及拒绝规则，不证明真实来源、两表或业务事务已通过。

### RANGE-T05

- **Artifact:** `docs/task-designs/RANGE-T05-design.md`、`docs/verification/RANGE-T05-parameter-conversion.md`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/DownloadParameterConverter.java`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/SourceParameterMapper.java` 及对应测试。
- **Decision:** 使用taskParameters生成的冻结公共Map保存首次输入。38项保留日历过滤／月份展开前的原始start_date/end_date，11项不增加日期；REQUEST保留原股票／市场条件，STOCK将股票放在selector而不重复保存ts_code。重试先从副本提取展示日期，再按保存明细重建精确请求。
- **Rationale:** 原始1～10日用于展示，剩余3／7日或完整RANGE才决定实际重试范围；不能用展示区间补造明细、扩大请求或覆盖冻结条件。
- **Constraint:** T10仅存通用JSON对象，不复制参数白名单、元数据必填校验或日期映射。原始两端都缺失的旧记录可读；单边／非法日期也保留读取，T05在执行重建时拒绝。不得静默补日期、改参数或把task_params整包发给来源。当前T05值为String，通用JSON支持不增加合法接口参数。
- **Usage:** 创建入口接收T05的taskParameters结果并深冻结，真实MySQL测试用受控ApiDescriptor调用实际converter生成REQUEST／STOCK参数，再验证create、append、updateReason及read前后原始日期和公共值不变。后续写接口不接收参数Map；查询只还原键值，originalDateRange与canExecute由后续层派生。
- **Readiness evidence:** T05已COMPLETED；绑定、规范化、精确重建和旧调用回归已验证。记录定向194项、593项Java单测、verify597／acceptance600项Java检查、170项前端测试及构建；51资源摘要不变、合同8＋4及独立评审PASS。证据支持输入Map和拒绝行为，不证明T10数据库或后续重试流程已实施。

两个直接输入一致：T04提供失败单元的结构身份，T05提供与该身份匹配的冻结公共条件。JSON存储不重新解释元数据；独立恢复依据仍由策略及执行层控制，没有未解决的依赖冲突。

## Start Here

按顺序读取：

1. 完整读取 `docs/task-designs/RANGE-T10-design.md`，核对本交接与看板本项；收到明确启动请求后执行 READY -> IN_PROGRESS，保留本交接为入口上下文。
2. `docs/design/Tensor_区间下载_TRD_v1.0.md` §7.1～§7.2、§8.1、§9.3；按设计补读§7.3事务边界及 `docs/contracts/openapi-v1.yaml` 的失败任务分页合同。
3. `data-plane/tensor-app/src/main/resources/db/migration/`、`data-plane/tensor-app/src/test/resources/db/migration/V6__create_fixture_tables.sql`；实际 `FlywaySchemaContractIT.java`、`DividendBusinessKeyMigrationIT.java`、`PackagedJarContractTest.java` 和 `AcceptancePackagedJarContractTest.java`，完整路径见设计Files。
4. `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/` 中的PersistenceService、GenericUpsertRepository、JdbcValueBinder及对应MySQL测试；`data-plane/tensor-app/src/main/java/com/akkc/tensor/config/ApplicationConfiguration.java` 和两个模块POM。
5. 上述T04／T05设计、验证记录和实际共享类型／转换器。按设计记录当前分支、暂存清单及旧迁移／49份YAML／两份策略摘要，保留前项成果；验证只报告T10实际执行结果。

**First action:** 在待新增的 `data-plane/tensor-app/src/test/java/com/akkc/tensor/db/RetryTaskStorageIT.java` 写入首次创建回滚用例：用真实Flyway迁移和隔离MySQL，在主表插入后让首明细INSERT触发SQL故障，再由独立JDBC连接核对本次主／明细均不存在且旧任务不变。执行设计规定的显式App IT命令并保存先行结果，随后实施已固定的V8及最小存储链路。缺类型／迁移导致的编译或启动失败单独记录，不冒充运行行为反例；按设计继续完成追加后半段回滚、JSON实际落库语义及完整测试矩阵。

## Risks

- 本次仅完成T10设计及交接，代码、V8和数据库测试尚未实施；READY不表示存储已部署或功能验收通过。
- 默认verify仅执行单测及打包合同。必须实际执行六类指定MySQL IT、核对tests>0且无失败／错误／跳过，并在acceptance clean前保存证据；不可用H2或自动跳过替代。
- MySQL原生JSON可能规范化数字。按设计在首次事务内回读比较，拒绝有效数字损失；外部SQL历史上已丢失的重复键或精度不能事后恢复，不能用Java codec测试声称数据库任意精度通过。
- 提交异常可能已落库；保存服务统一返回未确认，调用者不得自动重试、补造记录或发布未确认taskId。T11业务提交分类及业务＋明细删除原子性仍需单独实施和验证。
- V8更新schema及打包精确清单；V7专项升级仍固定target7，V6保持测试专用。生产迁移验证须使用设计中的App filesystem location，不能误用含V6的测试classpath。
- 生产日历与完整来源注册仍未启用，49项仍为REQUEST；受控股票／MySQL验证不提高真实来源可用性。ISSUE-008九项真实调用保持“不依赖，未解决”，本项不执行。
