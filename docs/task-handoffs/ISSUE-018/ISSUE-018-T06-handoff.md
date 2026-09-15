# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/ISSUE-018/ISSUE-018-task-board.md`。
- **Completed task:** `ISSUE-018-T05`，已先记录 COMPLETED、四条验证门禁和独立审查证据。
- **Next task:** `ISSUE-018-T06`，按预定义 Order 选择的后继。
- **Design document:** `docs/task-designs/ISSUE-018-T06-design.md`，已全文复核并回填看板，可直接实施。
- **Expected next status:** `READY`；本交接写入并链接后执行 `NOT_STARTED -> READY`，本次仅完成后继准备。

## Next Task

`ISSUE-018-T06`：Tushare 区间策略、日期规划与参数纠正。

为 34 项 RANGE 目标实现显式策略、参数转换、日期规划和响应范围 / 完整性判断，保留 40 项 SINGLE。新增 TushareBatchPolicies 门面与 TushareTradeCalendar 校验器，TushareProPlugin 实现可选 BatchDownloadSupport；仅纠正 fina_mainbz 的 SINGLE 参数为 ts_code + snapshot，并同步示例及相关精确断言。本项不实现 worker、任务 HTTP / 前端、拆分、入库或真实接口验收。

验收要求：31 原生 + top_list 交易日 + dividend / disclosure_date 自然日，6 项仅 SINGLE；RANGE 29 股票 / 5 非股票和全部 34 / 6 规则保留。日期轴按专属设计逐项检查，完整日历先验证再取开市日；未验证 BJ/BSE 条件拒绝且不替换交易所。候选数值的 `<L / =L / >L`、UNKNOWN、空响应和错误分类均有受控测试。所有 34 项生产描述保持 NEEDS_VERIFICATION / UNKNOWN，真实可用数为 0。fina_mainbz 旧 ann_date 请求为 400、零上游 / 写入，合法单股票请求保留；四条 Maven 门禁及受影响受控浏览器回归取得本项实际证据。

## Dependencies

### ISSUE-018-T01

- **Artifact:** `docs/task-designs/ISSUE-018-T01-design.md`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/BatchDownloadSupport.java`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/batch/BatchDownloadDescriptor.java`、`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/batch/DateRange.java`、`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/batch/BatchAssessment.java`、`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/batch/BatchCallContext.java`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/error/ErrorCode.java`；`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/download/ParameterCodec.java`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/download/DownloadParameterResolver.java`。
- **Decision:** BatchDownloadSupport 为可选扩展，旧 DataSourcePlugin.download 保留。描述 / sourceParameters 纯本地，plan 可通过上下文请求日历；assess 先检查范围再判断完整性，UNKNOWN 包括缺依据的空响应，AVAILABLE 不允许 UNKNOWN。四类 RANGE 形状分别为股票、无股票、exchange、exchange_id 加起止日期。
- **Rationale:** 接收阶段必须零上游；日期和完整性规则属于来源插件，通用执行器只消费合同。未知依据不能变为完整成功或误开放能力。
- **Constraint:** 不改变公共接口、九字段 ParameterDescriptor 和既有唯一 Codec。exchange / exchange_id 描述枚举保留 SSE,SZSE,BSE，具体 BSE 不可用条件在策略中拒绝。插件不依赖 core / Web / 仓储，凭证不进入业务参数或 envelope。
- **Usage:** 用既定四类参数形状生成 34 项描述；实现五项批量方法，按严格闭区间和输出日期列校验。包内受控已验证策略副本用于算法测试，生产注册表仍未验证；SINGLE 不调用 RANGE assess。
- **Readiness evidence:** T01 看板 COMPLETED；T05 最终全单元及双构建均再次执行 plugin-api 87 项和 app 359 项，失败 / 错误 / 跳过为 0。T01 的合同和绑定可直接消费；不以这些结果代替 T06 生产描述绑定测试。

### ISSUE-018-T05

- **Artifact:** `docs/task-designs/ISSUE-018-T05-design.md`；`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareProClient.java`、`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareRequestGate.java`、`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareRestClientFactory.java`；`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/config/TushareProperties.java`；`data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/client/TushareProClientControlTest.java`。
- **Decision:** `execute(DatasetDefinition, Map<String,Object>, BatchCallContext)` 与旧入口共享同一实例 gate；每次调用先预约一次，再等待 / 节流 / 传输。默认上次 operation 完成到下次开始间隔 1500ms；每请求独立超时依据实际发送前剩余时限计算。全部完成 / 取消路径等待实际 I/O 线程退出后才释放 gate。
- **Rationale:** 日历、原生、逐日和后台 SINGLE 都消耗同一轮预算，不能绕过共享节流、双预约或在取消未退出时续发。Spring body / close 的实际行为要求覆盖整个 exchange 并保留超时来源。
- **Constraint:** T06 不直接调用 beforeRequest、不新建客户端、不自动 retry。规划和 downloadBatch 必须传同一个本轮 context；后台 SINGLE 的全部 40 项也用三参数 execute。旧同步 download 继续兼容入口。保持 64 MiB、严格 JSON / 股票身份校验、固定安全错误及控制失败分类；不要包装原始 cause 或把失败转空计划。
- **Usage:** 门面持有插件已有 client，top_list 规划经它请求一次未过滤的完整 trade_cal；实际片段由 downloadBatch 经它取数。纯规划循环检查停止 / 截止但不预约。T06 传输测试显式设置零间隔，共享节流证据沿用 T05 Gate / Control 测试。
- **Readiness evidence:** T05 看板 COMPLETED，独立设计符合性 / 代码质量审查通过。专项 82 项（Gate 12、Client 17、Control 23、Factory 17、Plugin 9、Stock 4）；全单元 723 项；生产构建 723 + 4；验收构建 723 + 4 + 3，四条命令均退出 0、无失败 / 错误 / 跳过；各生命周期前端 170 项和构建通过。受控传输证明慢头 / body / close、取消后真实退出和 1500ms 节流 + 2250ms 调度后 native timeout 为 6250ms。上述为已记录的 T05 验证结果，本交接没有重新执行或扩展其证明范围。

两项直接依赖的决定与约束一致：T01 定义调用合同，T05 拥有请求预约和传输控制，T06 只接入策略及规划。未发现需要更改依赖合同的冲突。

## Start Here

按顺序读取：

1. `docs/task-designs/ISSUE-018-T06-design.md` 全文。
2. `docs/task-designs/ISSUE-018-design.md` §2.2、§3.2–3.5、§3.13、§5.1、§7，以及 `docs/issues/problems/ISSUE-018-date-range-batch-downloads.md` 的 40 项官网依据。
3. T01 / T05 上述专属设计、合同、客户端及测试。
4. `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TushareProPlugin.java`、`data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/fina_mainbz.yaml`、`docs/contracts/download-request-examples.json` 及设计列出的现有精确断言。
5. `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskService.java` 的 normalize：它以整个用户区间调用 sourceParameters。逐日转换必须兼容这个纯首日预检探针，实际 plan 仍生成完整单日序列，assess 拒绝非单点逐日片段。

第一个实施动作：新增 `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchPoliciesTest.java`，独立列出设计中的 34 项策略与 6 项 SINGLE-only 预期，断言 31 原生 / 3 逐日、29 / 5 参数形状、官方来源及全部生产 NEEDS_VERIFICATION / UNKNOWN；先观察缺失策略类失败，再实现最小注册表。

按明确启动请求记录 `READY -> IN_PROGRESS`，保留本交接为入口上下文。遵循设计中的四条 Maven 和 preview / 浏览器命令记录本项证据；专用 MySQL 验收 harness 留 T12，真实 Tushare 验收留 T13。保持现有分支与 T01–T05 暂存成果，新文件加入 Git，不自动提交。

## Risks

- 11 项原生策略缺完整提取依据；其余 22 项候选行数规则及 trade_cal 覆盖规则也尚无真实接口验证。所有 34 项生产 RANGE 保持待验证，不用测试副本开放生产。
- BJ 日历映射 / 直接 BSE、slb_sec / slb_sec_detail 标停历史范围、fina_mainbz 默认 type 和 disclosure_date 最新版本语义仍待验；不替换条件、不猜历史范围、不混 P/D/I。
- 未验证 trade_cal 的 assess 先验证结构 / 范围再返回 UNKNOWN，包括空响应；完整自然日覆盖用于已验证 assess 和 top_list 规划。空日历失败与完整日历全休市的空计划必须区分。
- T03 的多日 sourceParameters 预检不是实际计划；若逐日转换强制单点，会在接收时拒绝合法多日任务，若只执行探针则会漏日。
- fina_mainbz 的示例修改会改变专用 tushare-metadata 测试的 REQUESTS_SHA；按文件实际字节更新固定值，保留其完整枚举和哈希校验。浏览器路由 mock 不证明真实后台执行或账户可用性。
