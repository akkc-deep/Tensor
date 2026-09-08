# RANGE-T06 日历确认验证

2026-09-08执行，任务为[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的RANGE-T06，验收依据为[专属设计](../task-designs/RANGE-T06-design.md)。本记录只证明插件提供器层及受控来源行为，不表示生产日历已可用。

## 交付行为

- 新增插件内`CalendarSource`和`TushareCalendarProvider`。先按接口、证据状态及类别确定完整必要身份，再检查所有来源存在；任一来源缺失时fetch为零。margin只选原exchange_id，CSF和北向互联互通使用各自身份，不替换为普通交易所日历。
- 每次确认按来源键排序获取，同源身份合并为一次fetch；下一次确认重新获取。来源收到不可变身份及实际日期集合，原1～10日展示条件不会扩张失败3／7日范围。
- 来源身份、生效范围、本轮修订声明、逐行开闭值、重复冲突及每个身份的精确日期覆盖全部通过后才返回不可变Decision。合法任务外日期不进入结果，任务外坏协议仍拒绝；全闭市与空来源明确区分。
- 仅在fetch边界将SourceException转换为无cause、无原消息的固定CALENDAR_UNCONFIRMED；已有日历异常和服务端context异常原样传播，其他RuntimeException／Error不吞掉。不自动重试，不访问任务存储或数据库。
- TushareProPlugin覆盖confirmCalendar，保持四参构造器和原download逻辑；空值、context、readiness及未知API的顺序通过测试。生产来源注册表固定为空，不提供来源配置或注入开关。当前19项交易日期仍未确认，30项非交易日期误调用也拒绝且不取辅助来源。

## 本轮实际验证

所有Maven命令在沙箱外执行，沿用已确认的Mockito JVM附加环境。以下命令均从仓库根运行，退出码均为0：

| 命令 | 实际结果 |
|---|---|
| `mvn -f data-plane/pom.xml -pl tensor-plugin-tushare -am test` | 216个Java测试（API97／Tushare119）；5.747秒 |
| `mvn -f data-plane/pom.xml test` | 612个Java单测（97／97／119／13／286）、170个前端测试及构建；26.548秒 |
| `mvn -f data-plane/pom.xml verify` | 612个Java单测＋4个生产JAR合同，共616；170个前端测试及构建；28.300秒 |
| `mvn -f data-plane/pom.xml -Pacceptance clean verify` | 612个Java单测＋4个生产JAR＋3个acceptance JAR合同，共619；170个前端测试及构建；34.564秒 |
| `PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py` | 8组合同检查及4个内存变异反例PASS；49目标／38迁移／11保留／29项AC保持 |
| `PYTHONDONTWRITEBYTECODE=1 python3 /tmp/tensor-range-t06/audit-resources.py` | 资源摘要、19项逐行分类及证据状态独立核对PASS，见下节 |

实际调度的测试零失败、零错误、零跳过。保留原有Maven编码及Mockito动态代理／JVM附加警告。日志临时保存在`/tmp/tensor-range-t06/`，本报告保存持久结果。

当前POM默认Surefire不调度`*IT.java`，Failsafe只执行上述两类JAR合同。数据库／容器IT仅编译、未执行：`PersistenceServiceIT`、`ExistingKeyRepositoryIT`、`DatasetQueryServiceIT`、`DownloadControllerIT`、`DatasetControllerIT`、`FixtureFlowIT`、`FlywaySchemaContractIT`、`DividendBusinessKeyMigrationIT`、`ProductionApplicationContextIT`。浏览器E2E、真实日历及真实业务API均未运行，未读取凭证。

## 测试先行及可观察证据

新增`TushareCalendarProviderTest`有16个测试方法，其中多组逐项覆盖无效输入；`TushareProPluginTest`增加3个行为测试，原业务回归保留。

| 检查 | 直接结果 |
|---|---|
| moneyflow并集／全闭市 | SSE两日闭、SZSE仅3日开，结果只开放3日；两身份完整全闭时合法空openDates |
| 必要身份／来源 | 缺SZSE或BSE时全部fetch为零；margin三分支各只取所选源，缺失／小写／未知／非字符串拒绝；CSF只用专属源 |
| 单轮复用／重新确认 | HKEX两北向身份同源一次fetch；相同scope和context第二次确认仍重新fetch |
| 覆盖／来源观察 | null返回、null行／字段、空列表、错误来源／身份、非法0／1字符串、缺日、仅额外日期、重复掩盖缺日、逆序／不足生效范围及未确认修订均拒绝；一致重复去重、冲突整次失败；受控跨年通过 |
| 精确范围／不可变性 | 上游较宽合法日期只消费3／7日；任务外坏协议仍拒绝；原条件、来源注册表、rows、fetch集合和Decision均防御复制或不可变 |
| 证据门槛 | UNCONFIRMED／CONFLICT及非交易模式零fetch；未知完整市场或方向即使改成DOCUMENTED也拒绝，错误profile不启用 |
| 异常边界 | SourceException变固定日历错误；既有日历异常及来源前／后context故障实例保持；非来源运行异常不吞掉，无自动重试或剩余来源调用 |
| 生产SPI／原业务 | 49项真实描述符均安全拒绝日历，业务client零调用；readiness／未知API／context顺序通过；原download依旧精确调用原client并保留包络和来源异常 |

提供器首个moneyflow测试先于实现写入。首次定向命令因测试误引入其他模块的测试工具而编译失败；删除该误导入后、实施之前再运行`mvn -f data-plane/pom.xml -pl tensor-plugin-tushare -am -Dtest=TushareCalendarProviderTest -Dsurefire.failIfNoSpecifiedTests=false test`，退出1，仅因CalendarSource／TushareCalendarProvider缺失。实现后曾修正一个测试泛型推断编译问题；最终相同定向命令16项全通过。没有把测试设置错误计为业务反例。

SPI测试也先于实现写入。`mvn -f data-plane/pom.xml -pl tensor-plugin-tushare -am -Dtest=TushareProPluginTest -Dsurefire.failIfNoSpecifiedTests=false test`退出1：14项中3项失败，对应尚未覆盖confirmCalendar、未知API错误类型及缺少提供器入口context检查。补齐接入后，上述完整模块／全量／两次verify均通过。

## 独立资源核对与评审

实施前保存49份Dataset及策略YAML／schema的SHA-256，最终51个文件逐项一致。独立脚本直接使用T02逐项JSON作为期望，核对生产19项名称、类别及状态完全相同：C-A／C-M／C-S／C-N／C-X为12／1／3／2／1，DOCUMENTED／UNCONFIRMED／CONFLICT为1／16／2。相关4项真实排除为hk_hold、hsgt_top10、moneyflow_hsgt、top_inst，19项liveStatus均保持NOT_RUN；其他30项不带日历状态。

独立实现评审结论为规格符合、代码质量Approved，无Critical／Important／Minor遗留。评审核对了完整设计及本任务5个Java文件的增量，特别检查完整来源预检、精确日期、同源复用及异常捕获边界。完整构建、合同、资源审计与本报告由主执行者核对，不将评审阶段尚未运行的命令写成评审已验证。

最终集成复核同样PASS，无Critical／Important／Minor问题；独立重核四份Maven日志的计数及耗时均与上表一致。`git diff --check`、`git diff --cached --check`及本轮文档相对链接检查均退出0；新文件已加入Git暂存，未提交或发布。

## 验收范围

AC-PRD-RANGE-02／15／16／17／18的提供器层证据已记录于[增量追踪](../traceability/tensor-range-requirements.md#range-t06-日历提供器层增量证据)。生产无完整运行证据的日历适配器，唯一静态DOCUMENTED项hsgt_top10仍因缺来源而拒绝；受控修订声明不证明真实权威修订已核查。

首次不建失败任务、重试保留明细及非交易类别绕过屏障的完整编排／数据库效果仍由T08／T12／T13／T18验证。T06没有这些层的副作用，也不能代替它们的验收。ISSUE-008九项继续“不依赖，未解决”，不以拒绝边界关闭最终功能AC。
