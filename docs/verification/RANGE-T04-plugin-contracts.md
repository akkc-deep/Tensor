# RANGE-T04 插件合同验证

本记录对应[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的 `RANGE-T04`，验收依据为[详细设计](../task-designs/RANGE-T04-design.md)。2026-09-08 执行。本轮实现、规定验证及独立代码评审均已完成。

## 基线与环境

- 实施前保存全部 49 份 Dataset YAML 的 SHA-256 和完整解析定义，包括参数、查询模式、筛选、列和业务键；与 T01 证据摘要的逐项复核将在实施后执行。
- 初次 `mvn -f data-plane/pom.xml test` 在沙箱内退出 1：Mockito 的 Byte Buddy 无法附加测试 JVM，首个失败为 `DatasetStartupValidatorTest`，不是业务断言失败。此时未修改运行代码。
- 原命令在沙箱外重新执行，退出 0，6 个 reactor 模块全部 SUCCESS（26.722 秒）：493 个 Java 单测、170 个前端单测通过，前端构建通过。确认环境限制后，后续 Maven 验证在相同沙箱外环境执行。
- 实施前运行 `PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py`，退出 0：8 组合同检查及 4 个内存变异反例均 PASS。

## 实施与验证结果

### 交付行为

共享 SPI 只保留三参数下载方法，新增不可变选择器、请求批次、明确单元失败、日历范围／结果与服务端检查上下文；缺省日历检查抛出 `CALENDAR_UNCONFIRMED`。所有内置插件及测试替身已同版迁移。

49 项显式 YAML 策略、固定离线 Schema 和严格加载器已交付。策略与实际数据集联合校验，重复、缺失、错误模式／日历、非法映射／上限、伪启用独立恢复或完整性均拒绝注册。加载错误不回显解析内容。`parameters` 为下载投影，`sourceParameters` 保存原来源定义；原 HTTP 绑定、元数据、执行校验和日志已统一使用来源列表。旧下载服务拒绝明确单元失败，不能丢弃失败后继续适配或持久化。

### 实际命令

从仓库根执行，Maven 均在沙箱外运行：

| 命令 | 退出码 | 实际结果 |
|---|---:|---|
| `mvn -f data-plane/pom.xml -pl tensor-plugin-api,tensor-plugin-tushare,tensor-plugin-fixture -am test` | 0 | 286 个 Java 测试通过（API 88／Core 86／Tushare 99／fixture 13）；随后增加 3 个边界测试纳入全量验证 |
| `mvn -f data-plane/pom.xml test` | 0 | 521 个 Java 测试、170 个前端测试及前端构建通过；28.872 秒 |
| `mvn -f data-plane/pom.xml verify` | 0 | 上述单测及 4 个生产 JAR 合同测试通过，Java 合计 525；28.763 秒 |
| `mvn -f data-plane/pom.xml -Pacceptance clean verify` | 0 | 全量单测、前端构建、4 个生产 JAR 和 3 个 acceptance JAR 合同测试通过，Java 合计 528；33.604 秒 |
| `PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py` | 0 | 更新追踪后 8 组合同及 4 个内存变异检查全部 PASS |
| `git diff --check`、`git diff --cached --check` | 0 | 暂存与未暂存空白检查通过 |
| `PYTHONDONTWRITEBYTECODE=1 python3 /tmp/tensor-range-t04/audit-policies.py` | 0 | 独立核对下述 6 组来源／资源／错误映射，全部 PASS；临时审计程序不属于运行产物 |

测试实际执行项均无失败、无错误、无跳过。保留既有 Mockito 动态代理／JVM 附加和 Maven 编码警告；未通过修改依赖或关闭断言消除警告。

当前 POM 的默认测试不调度 `*IT.java`，Failsafe 只执行打包合同。以下数据库／容器 IT 本轮仅编译、未执行，不能计为数据库通过：`PersistenceServiceIT`、`ExistingKeyRepositoryIT`、`DatasetQueryServiceIT`、`DownloadControllerIT`、`DatasetControllerIT`、`FixtureFlowIT`、`FlywaySchemaContractIT`、`DividendBusinessKeyMigrationIT`、`ProductionApplicationContextIT`。本项不新增数据库行为；浏览器 E2E 和真实业务 API 均未运行。

### 独立资源核对

实施前的完整 Dataset 临时基线与实施后的 49 份文件逐项比较，SHA-256 和完整解析定义均相同，并与已版本控制的 T01 摘要一致。独立程序直接消费 T01／T02 JSON、T03 `x-tensor-range-targets` 和错误目录；期望值不从被测策略计算。

1. 49 份 Dataset YAML 的摘要和完整定义不变，来源参数、queryMode、filters、columns、businessKey 均保留。
2. 策略集合与数据集及 T03 精确相等；mode、dateSemantic、候选方式、日期字段、组批形状、完整性文本、恢复证据、公开文案及引用逐项匹配来源。
3. 模式为 19／15／1／3／11；日历类别为 12／1／3／2／1；请求状态为 40 DOCUMENTED_CANDIDATE／8 UNCONFIRMED／1 CONFLICT；日历证据为 1 DOCUMENTED／16 UNCONFIRMED／2 CONFLICT。
4. 全部 49 项为 REQUEST 且完整性／分页未确认；8 项请求方式为空。forecast 保留 DATE＋请求冲突，monthly 保留 DATE＋日历冲突。
5. Schema 合法、生产资源显式且按 apiName 排序；38 项上限均为 31，11 原条件无上限和日期增补。
6. 26 个运行错误的 HTTP／retryable 元组与 T03 错误目录逐项相等。

### 测试可观察结果与先行失败

| 检查组 | 直接测试证据 |
|---|---|
| SPI 与选择器 | `PluginApiSurfaceTest`、`DownloadContractsTest`：无旧下载签名／五参数构造器；两股同日不同，非法日期、非规范股票、逆序／相等范围拒绝；输入集合变更不影响冻结值 |
| 策略加载与投影 | `DownloadPolicyLoaderTest`、`DownloadParameterProjectionTest`、`TushareProPluginTest`：真实49项加载、九种形状、原参数保留和不合法资源拒绝；重复数据集构造安全失败 |
| 日历与上下文 | `DownloadContractsTest`、两内置插件测试：不连续日期精确覆盖、两来源并集、全休市、缺日／多日／null拒绝；默认日历零来源调用，来源前／后服务端检查异常原样传播 |
| 原执行与 HTTP | `DownloadServiceTest`、`DownloadRequestBindingTest`、`DownloadParameterResolverTest`、`ParameterValidatorTest`：三参调用和来源条件保留；明确失败／null结果先拒绝，adapter／persist零调用；真实Tushare描述符支持原绑定 |
| 元数据、安全与错误 | `DataSourceControllerTest`、`OperationLoggerTest`、`GlobalExceptionHandlerTest`：元数据和日志消费sourceParameters，内部策略不序列化，26码及安全错误映射保留 |

新共享类型／签名和投影测试先于实现写入，首轮分别因新类型或七参数构造器缺失而编译失败，随后局部测试转绿。加载测试也先于实现写入，但该首轮 reactor 在尚未实现的上游投影处停止，未执行到独立加载断言；该日志不作为加载器运行时反例证据。迁移中的导入、null重载及旧反射期望编译问题已修正。

重复 Dataset 构造另有运行时先行失败：原先抛出 `IllegalStateException`，未满足安全 `DATASET_MISCONFIGURED` 合同；修正映射后连同完整单测和两次 verify 均通过。完整本轮日志暂存于 `/tmp/tensor-range-t04/`。

### 独立评审

独立评审已完成：规格符合性 PASS、代码质量 PASS、跨模块集成 PASS；无 Critical／Important／可操作 Minor 遗留问题。评审读取本项完整设计、实施报告和工作区变更包，核查不可变性、严格加载、来源／投影分离、明确失败拦截、三参调用及日历拒绝。评审未重复执行已提供的测试，来源核对和实际命令结果由上述本轮证据支持。

AC-PRD-RANGE-01／14／18／29 的本项合同证据已回填[增量追踪](../traceability/tensor-range-requirements.md#range-t04-合同层增量证据)；29 项最终功能验收保持未执行，未升级任何真实来源或数据库结论。

## 验收边界

本任务验证插件内存合同、49 项策略登记及既有调用回归。31 天运行校验、区间执行、日历提供器、完整取数、恢复单元处理和 HTTP 区间切换由后续任务交付。类型合同与受控验证不证明真实来源支持；本轮不调用业务 API，ISSUE-008 九项继续“不依赖，未解决”。
