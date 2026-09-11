# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/ISSUE-018/ISSUE-018-task-board.md`。
- **Completed task:** `ISSUE-018-T06`，已先记录 COMPLETED、四条 Maven 门禁、浏览器回归和独立审查证据。
- **Next task:** `ISSUE-018-T07`，按预定义 Order 选择的后继。
- **Design document:** `docs/task-designs/ISSUE-018-T07-design.md`，已全文复核、通过独立就绪评审并回填看板，可直接实施。
- **Expected next status:** `READY`；本交接写入并链接后执行 `NOT_STARTED -> READY`，本次仅完成后继准备。

## Next Task

`ISSUE-018-T07`：通用批次执行、拆分与资源预算。

使已接收任务通过同步串行执行器完成领取、计划核对、持久参数取数、完整性判断、二分、原子提交与终态汇总。新增 `DateRangePlanner`、`DownloadTaskRunner`，补充 T03 包内执行定义入口及 T04 兼容的停止检查重载。SINGLE 生成一条无日期范围的根批，旧插件仍可执行。生产协调器、retry/resume、启动恢复和关闭装配属于 T08，HTTP / 配置绑定属于 T09。

验收要求：闭区间二分无漏无重，三类计划严格核对；已保存计划和片段参数重用，满额父响应不适配或入库，UNKNOWN 不冒充成功。单批网络 / 适配 / 可记录写入错误后继续兄弟批；鉴权、权限、限流、定义、预算错误停止并保留成功与 PENDING，存储结果不明立即停止新请求。范围 36600 天、节点 10000、每轮预约 5000、运行 30 分钟、累计成功 sourceRows 1000000 的等号 / 超限均有证据。空批经共同事务提交，停止时拒绝迟到结果，实际调用退出前不释放执行所有权。来源无关测试、旧 SINGLE fixture、真实 MySQL 专项与三条完整 Maven 门禁均按设计取得实际结果；生产 34 项 RANGE 不因这些测试开放。

## Dependencies

### ISSUE-018-T03

- **Artifact:** `docs/task-designs/ISSUE-018-T03-design.md`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskService.java`、`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTask.java`、`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskRepository.java`、`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskJson.java`；`data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/task/DownloadTaskServiceTest.java`、`data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/task/DownloadTaskServiceIT.java`、`data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/task/DownloadTaskRepositoryIT.java`。仓储和 JSON 是 T03 已接收任务链路消费的既有输入，不新增看板依赖。
- **Decision:** 接收只做本地能力 / 参数检查，任务、规范化参数、策略与定义摘要已持久。`validateReplay` 保持 current → definitionHash → normalize 顺序；幂等找回优先于当前开关 / 插件 / 容量。执行必须使用持久 sourceParams，而非重新构造历史片段。
- **Rationale:** 排队等待或重执行时，当前定义变化必须先被识别；用户范围的纯预检与实际批次映射分离，避免新策略覆盖已保存的请求事实。数据库状态是任务查询与执行恢复依据。
- **Constraint:** 不改 public 接收 / 查询合同、摘要 schema、仓储公开表面或迁移。包内 `executionDefinition` 是 T07 待新增入口，复用既有校验并返回真实 plugin/adapter/dataset/range；不得将其描述为 T03 已有方法。重复 normalize 可调用全用户范围 sourceParameters 探针，结果丢弃，不覆盖片段快照。无外层事务执行来源或适配。
- **Usage:** 用 `queuedTasks/claimTask` 得到真实 generation 与 permit，复用 `savePlan/claimBatch/reserveRequest/split/failBatch/snapshot/finishTask`。从保存策略取日期参数名，保持 core 不含来源字段分支；新增包内入口沿用适配器唯一索引和定义校验。
- **Readiness evidence:** T03 看板 COMPLETED，专项 JSON20 + Service17 + Query5 + MySQL RepositoryIT17 + ServiceIT11 共70项通过；真实 MySQL 证明提交后可见 QUEUED、零批次及幂等 / 容量 / 插入失败行为。四条门禁与独立审查均通过，失败 / 错误 / 跳过为0。T06 最终后端867项回归及双构建通过，未修改此链路生产代码；这里引用已记录证据，不声称 T07 已实施。

### ISSUE-018-T04

- **Artifact:** `docs/task-designs/ISSUE-018-T04-design.md`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/BatchCommitService.java`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/PersistenceService.java`、`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/PersistenceParticipant.java`；`data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/task/BatchCommitServiceIT.java`、`data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence/PersistenceServiceIT.java`。
- **Decision:** 证券 Upsert、实际计数与批次 SUCCEEDED 同事务；空批使用真实参与者也提交成功状态。锁序固定为数据集锁 → 任务行 → 批次行，锁内复验运行许可和截止，已获准事务可在任务截止后完成。失败标记由事务外调用者另行保存。
- **Rationale:** 数据与成功状态必须共同裁决，旧许可不能污染新轮次；来源、适配和失败处理不能延长证券事务。提交异常可能是回执不明，不能直接推断没有写入。
- **Constraint:** 保留四参数 `commit` 与旧 persist 行为、60秒事务上限、外层事务拒绝和锁序。T07 新增五参数 `BooleanSupplier` 重载，在预检查及 beforeWrite 锁内读取停止；不在 afterWrite 中断已获准事务，不在持锁时检查插件定义。不得再独立写 succeedBatch 或重复累计计数。
- **Usage:** 完整响应适配后，包括零行，统一调用 T07 新重载；持久错误只尝试一次 failBatch，其失败或冲突进入 NEEDS_RECOVERY。沿用真实 DataSource / 事务管理器、MySQL8.4.6、触发器与受控代理验证锁等待停止、整批回滚和提交回执不明。
- **Readiness evidence:** T04 看板 COMPLETED；真实 MySQL 专项 PersistenceServiceIT16 + BatchCommitServiceIT15 + RepositoryIT17 + 旧 DownloadServiceTest3 共51项通过，独立审查无开放问题。空批共同事务、第二 JDBC 批 / 成功状态失败回滚、旧 RR 快照与许可隔离、等待锁到期和获准后跨截止提交已有证据。四条门禁退出0，失败 / 错误 / 跳过为0；本地停止重载及其窗口测试仍是 T07 新工作。

### ISSUE-018-T06

- **Artifact:** `docs/task-designs/ISSUE-018-T06-design.md`；`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TushareProPlugin.java`；`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchPolicies.java`、`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/batch/TushareTradeCalendar.java`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/BatchDownloadSupport.java`、`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/batch/BatchCallContext.java`；`data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchDownloadTest.java`；`data-plane/tensor-app/src/test/java/com/akkc/tensor/web/TushareBatchAvailabilityTest.java`。
- **Decision:** 可选插件提供描述、纯参数转换、计划、取数和完整性判断；真实请求由现有客户端读取同一个 context 并预约一次。31原生、3逐日策略及6 SINGLE-only 已实现，但34生产 RANGE 描述全部 NEEDS_VERIFICATION / UNKNOWN；旧40项 SINGLE 保留，fina_mainbz 已为 ts_code + snapshot。
- **Rationale:** 来源日期轴与完整性属于插件，core 只根据规划模式、范围和 assessment 执行通用二分。算法可被受控验证，不等于上游语义和账户已真实验证；重复预约会错误耗尽预算。
- **Constraint:** core 不导入 Tushare 实现或增加插件依赖。BatchDownloadSupport 的 SINGLE / RANGE 均走 downloadBatch，runner 不额外预约；普通插件 SINGLE 由 runner 预约一次后调用 download。SINGLE 不 plan/assess，不按 RANGE 完整性拆分。不改生产策略验证标记、40项 YAML、T05传输实现或公共插件接口。
- **Usage:** 以 T06 为可选合同实现参考；T07 验收采用 `runner_test/prices`、`symbol/from/to` 和真实旧 FixturePlugin，证明来源无关。plan、downloadBatch 透传同一轮 context；根 / 新子批才保存映射，后续使用持久参数，满额父及未知结果均不直接提交。
- **Readiness evidence:** T06 看板 COMPLETED；专项449项（新增策略 / 日历 / 批量130项），后端全单元867项，生产867+4、验收867+4+3均退出0且无失败 / 错误 / 跳过；前端170项及构建通过，Chromium3项通过。真实插件与T03组合证明配置凭证仍拒绝全部34 RANGE且零上游 / 插入，全部40 SINGLE可本地接收。独立设计符合性及代码质量审查通过，无开放问题；未进行真实 API 验证。

三项直接输入无未解决冲突：T03 提供持久任务及当前定义校验，T04拥有数据 / 成功共同事务，T06提供来源计划和判断。T07 的两个兼容补充已在专属设计明确，生产生命周期仍留 T08；依赖证据不冒充执行器完成证据。

## Start Here

按顺序读取：

1. `docs/task-designs/ISSUE-018-T07-design.md` 全文，使用其固定接口、错误矩阵、预算位置、测试命令和验收标准。
2. `docs/task-designs/ISSUE-018-design.md` §3.2、§3.5、§3.7–3.9、§3.13–3.14、§5.1，以及看板 T07 的任务边界。
3. 上述 T03 / T04 / T06 专属设计、直接输入实现与测试，特别是仓储已有签名、validateReplay 顺序及 BatchCommitService.beforeWrite 锁序。
4. `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/DownloadService.java`、`data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/DownloadServiceTest.java`；`data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixtureConfiguration.java` 与该模块既有测试，用于旧 SINGLE 兼容，不给 core 增加 fixture 依赖。

第一个实施动作：新增 `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/task/DateRangePlannerTest.java`，写跨闰日 / 跨年闭区间二分与首尾自然日枚举失败用例，观察缺失类红灯，再实现最小算法；之后按设计进入 runner 的空批原子提交和第二批失败后继续第三批用例。

收到明确启动请求后记录 `READY -> IN_PROGRESS`，保留本交接为入口上下文。按设计的显式 MySQL 专项、全单元、生产包和验收包命令验证，不能以 clean verify 替代 IT。保持当前分支和 T06 未提交成果，新文件加入 Git，不自动提交。完成时先记录 T07 outcome，再按 Order 准备 T08 专属设计与交接。

## Risks

- 旧插件未接受 context，只能按一次方法调用计预算；有限 I/O 超时由插件负责。Future.cancel 不证明实际退出，活动租约在提交 runnable 前登记，直到真实同步退出后才释放；T08负责生产装配。
- 计划、拆分或数据提交的回执异常可能发生在实际提交后。停止新请求，返回身份和固定分类，退出后按数据库事实恢复，不自动重发或降级成功叶子。
- T03 的全范围纯预检可以重复；它不能替换持久片段参数。策略语义变化必须更新 policyVersion，执行器不能识别不改变摘要的隐藏变化。
- 等待数据集锁时停止必须在 beforeWrite 拒绝；通过最后许可后允许共同事务完成。全部叶子已成功时，即使随后观察停止 / 截止也应保存 SUCCEEDED，不能将成功事实改为中断。
- 累计行预算依赖单实例、单实际 worker 与 T08 统一协调；执行许可不等于多实例并发预算保证。真实 MySQL 需要现有 Colima/Docker可用，实施时核对连接，不清理用户容器。
- 34生产 RANGE 仍待 T13真实验证；本项不得通过放宽 availability 构造测试，基础设施通过不关闭 ISSUE-018。
