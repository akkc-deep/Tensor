# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/data-integrity-task-board.md`。
- **Completed task:** `DATA-INTEGRITY-T07`，已记录COMPLETED。
- **Next task:** `DATA-INTEGRITY-T08`，按Order=8选择。
- **Design document:** `docs/task-designs/DATA-INTEGRITY-T08-design.md`，已完成、回填并经过可实施性复查。
- **Expected next status:** READY；链接本交接后NOT_STARTED→READY，不启动实现。

## Next Task

**DATA-INTEGRITY-T08 — Tushare 日周月候选缺口规则。** 在本地读取许可内，对 daily/weekly/monthly 推导候选完整业务键，区分日历不足、未结束周期和疑似行情缺口。来源覆盖始终保持UNKNOWN，无正式预期数/覆盖率、无生产PROVEN集合、无上游调用。

SH/SZ使用对应日历；BJ没有已验证适用依据时保持CALENDAR_BASIS_UNPROVEN。日历每日唯一且is_open=0/1，周/月按完整周期最后开市日，最终标记必须落入原范围。仅排除明确上市前候选，停牌作线索，不能删除原股票/日期范围。三条规则及能力由已保存的1升级为2，其余37接口、日期轴和下载能力不改。专项规则/无网络/真实应用MySQL验证和既有后端回归全部通过才可完成。

## Dependencies

### DATA-INTEGRITY-T02

- **Artifact:** `docs/task-designs/DATA-INTEGRITY-T02-design.md`；`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/integrity/TushareIntegrityPolicies.java`、`TushareIntegrityCoverageRule.java`；同模块 `TushareProPlugin.java`、`datasets/tushare_pro/` YAML。
- **Decision:** 恰好40接口、固定股票/日期轴与参考依赖；三个行情覆盖目前为@1占位UNKNOWN。无Token仍可发现本地能力。
- **Rationale:** 来源检查口径独立于SINGLE/RANGE下载限制，不通过下载能力推导完整性。
- **Constraint:** 其他接口不套用daily语义；三规则行为变化必须升级版本；BJ下载日历策略不改。stock_basic只保存当前list_date等字段，不包含历史list_status/delist_date。
- **Usage:** 注册新的行情规则和纯本地referenceReads委托，复用既有依赖投影/purpose；仅三项规则/能力升2，完整hash变化由既有核心捕获。
- **Readiness evidence:** T02已COMPLETED；`docs/verification/DATA-INTEGRITY-T02.md`记录100项策略、7项注册和1265项当时后端回归；T07最终回归继续覆盖Tushare462项。

### DATA-INTEGRITY-T03

- **Artifact:** `docs/task-designs/DATA-INTEGRITY-T03-design.md`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/integrity/IntegrityReadRepository.java`、`IntegrityReadPlan.java`、`IntegrityReadBudget.java`。
- **Decision:** 同单元目标/参考共享只读REPEATABLE_READ快照、稳定游标和累计预算；参考仅同来源/已声明用途。
- **Rationale:** 多页/参考比较使用同一时点，来源不能借参考读取扩大全市场或任意SQL。
- **Constraint:** 股票日期参考只能原范围，快照参考无日期；NON_STOCK交易日历须固定exchange等其余业务键，范围只允许原范围/完整ISO周/完整自然月。参考不能跨股票。
- **Usage:** hook生成trade_cal的完整周期窗口、stock_basic当前股票快照与suspend_d原范围请求；规则context.scan使用相同请求。T03继续独立校验许可。
- **Readiness evidence:** T03已COMPLETED；T07最终MySQL组再次通过ReadRepositoryIT12项，同快照及跨页/授权证据保留。

### DATA-INTEGRITY-T04

- **Artifact:** `docs/task-designs/DATA-INTEGRITY-T04-design.md`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/integrity/IntegrityUnitEvaluator.java`、`DefaultIntegrityContext.java`、`IntegrityIssueCollector.java`；plugin-api `integrity/` 合同。
- **Decision:** `context.compare`接受完整键、PROVEN/UNCONFIRMED和同快照证据；核心决定统计和缺口类型，按ruleId执行并聚合FAIL>UNKNOWN>WARN>PASS。
- **Rationale:** 可靠性属于基线证据，不能由日历数量或本地无缺口推断。
- **Constraint:** 本任务一律UNCONFIRMED；只生成SUSPECTED_MISSING/WARN，不生成MISSING/EXTRA；正式统计为空，无候选也不PASS。源规则不吞扫描异常或重置预算。
- **Usage:** 交给compare一次惰性候选键流；范围/参考问题用REFERENCE_INCOMPLETE、目标归属及真实evidence/relatedDates，不伪造目标业务键；复用现有core通用规则。
- **Readiness evidence:** T04已COMPLETED；T07最终再次运行ComparisonIT7项及整体后端回归，已知FAIL和不完整问题保留仍成立。

### DATA-INTEGRITY-T07

- **Artifact:** `docs/task-designs/DATA-INTEGRITY-T07-design.md`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/IntegrityCheckSupport.java` 的 `integrityReferenceReads(IntegrityScope)`；core `IntegrityReadPlanner.java`、`IntegrityCheckRunner.java`、`IntegrityCheckCoordinator.java`；`docs/verification/DATA-INTEGRITY-T07.md`。
- **Decision:** 默认hook兼容旧插件，来源提议被固定为受限ReferencePermit；Runner每单元校验完整能力hash和API快照，来源规则/许可与保存版本一致后执行。
- **Rationale:** 核心不猜来源交易所/周期；版本变化不能用新规则重算旧计划。检查与下载生命周期独立。
- **Constraint:** 单worker；扫描与生成键/问题/单元/任务预算共享；停止/超时/重启中断，旧报告不重算。hook在快照打开前运行，不能依赖非null snapshotStartedAt来选择范围；执行规则用快照时刻判断当日/周期。
- **Usage:** 仅在Tushare插件实现hook，使用已装配真实Runner验证，不再改核心执行器、仓库或配置。
- **Readiness evidence:** T07已COMPLETED；最终1399项后端、71项真实MySQL和真实生产应用启动均0失败/错误/跳过；两项审查问题修复后终审批准。新文件已加入Git，见T07验收文档。

四项输入一致：固定来源口径→受限读取→未知集合比较→后台执行。已将原看板缺少的T07直接消费依赖补齐，版本升级遵守共享设计第6节，无未解决依赖冲突。

## Start Here

1. 完整阅读 `docs/task-designs/DATA-INTEGRITY-T08-design.md`。
2. 阅读共享 `docs/task-designs/DATA-INTEGRITY-design.md` 第3、4、6节与 `docs/runbook/data-integrity-rules.md`。
3. 阅读Tushare三行情/三参考的实际YAML、`TushareIntegrityPolicies` 和既有策略测试。
4. 阅读T07 hook/Planner与T03许可校验、T04 compare/issue合同；参考T07真实Runner验收。

**First action:** 在 `TushareIntegrityRulesTest` 写入weekly的受控RED用例：完整周历中周五闭市、周四开市，原范围包含周四；断言实际传入context.compare的UNCONFIRMED完整键是周四而不是周五，观察当前@1占位规则不扫描/比较导致失败，再按专属设计实现。用户显式启动后才把T08 READY→IN_PROGRESS。

## Risks

- 本地参考不构成历史证券/停牌/发布全集，不能升级为正式覆盖率。
- 大范围日历或问题数可能触发既有预算，必须保留ERROR/incomplete，不静默缩范围或抽样。
- 周/月参考可能超出原日期范围，参考问题用证据/关联日期定位，不能伪装为目标缺键。
- 隔离区包含Studio和T01–T07混合暂存基线；保留现状，不全量提交或合回原工作区。
