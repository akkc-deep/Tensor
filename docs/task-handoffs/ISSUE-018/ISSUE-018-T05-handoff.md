# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/ISSUE-018/ISSUE-018-task-board.md`。
- **Completed task:** `ISSUE-018-T04`，已先记录 COMPLETED 和四条验证命令通过证据。
- **Next task:** `ISSUE-018-T05`，按预定义 Order 选择的后继。
- **Design document:** `docs/task-designs/ISSUE-018-T05-design.md`，专属设计已完成、独立复核通过并回填看板。
- **Expected next status:** `READY`；本交接写入并链接后执行 `NOT_STARTED -> READY`，本次不启动实现。

## Next Task

`ISSUE-018-T05`：Tushare 请求上下文与共享节流。

使同一客户端的新旧执行入口统一预约预算、节流，并以剩余时限和停止信号约束完整网络交换。范围为 TushareRequestGate、TushareProClient 上下文重载、每请求独立超时、TushareProperties 最小间隔及相关测试；不实现日期策略、插件 RANGE 开放、任务 worker 或持久化预算。

验收包括默认 1500ms 共享间隔、更慢配置不被重载绕过、预约拒绝零 HTTP、等待中停止 / 截止、实际发送前复验、配置超时与任务期限分别分类、64 MiB 与既有错误 / 身份校验回归。取消后必须等实际 I/O 线程退出才返回并释放 gate，响应头、慢 body 及 response.close 均受控；迟到 envelope 不得被接受。

## Dependencies

### ISSUE-018-T01

- **Artifact:** `docs/task-designs/ISSUE-018-T01-design.md`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/batch/BatchCallContext.java`；同模块 `BatchDownloadSupport.java`、`error/ErrorCode.java`、`error/TensorException.java`。
- **Decision:** BatchCallContext 固定 deadline / stopRequested / beforeRequest；每次实际请求在节流之前预约一次。预约后等待或传输失败仍占预算，拒绝用 TASK_LIMIT_EXCEEDED / EXECUTION_INTERRUPTED 分类。旧 DataSourcePlugin.download 保留。
- **Rationale:** 规划、拆分重发及后台 SINGLE 共同消耗每轮请求预算，不能仅在任务入口计一次或在失败时退还；上下文必须跨全部来源请求路径生效。
- **Constraint:** plugin-tushare 不依赖 core、Web、任务仓储；上下文只携带调用许可，不把凭证混入参数 / 快照 / 日志。T05 不实现真实数据库预算，T06 / T07 后续将上下文接入插件与 worker。
- **Usage:** 新 execute 重载接收该接口，gate 在等待前调用 beforeRequest，等待及发送边界使用共享 check；局部请求 attribute 携带 context / clock 以重新计算剩余超时。旧 execute 使用无持久预算的兼容 context，仍通过同一 gate。真实 HTTP 次数与预约次数一一对应，取消的已预约调用不扣回。
- **Readiness evidence:** T01 看板 COMPLETED，plugin-api 87 项合同测试已通过。T04 最终全单元与双构建再次执行 plugin-api 87、Tushare 91 和 app 359 项，全部失败 / 错误 / 跳过为 0；该证据确认现有合同与客户端输入可用，不证明 T05 上下文传输已实现。

### 既有 Tushare 客户端与传输基线

- **Artifact:** `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareProClient.java`、`TushareRestClientFactory.java`、`TushareErrorClassifier.java`；`config/TushareProperties.java`；对应 `client/TushareProClientTest.java`、`TushareRestClientFactoryTest.java`。
- **Decision:** 保留公开 RestClient 注入、现有 JDK HttpClient、严格 JSON / 响应身份校验、固定无原始 cause 的错误、64 MiB 上界及无自动重试。保留旧六参数属性构造调用，新增七参数配置绑定。
- **Rationale:** 新旧入口复用同一经过验证的协议路径；按每请求动态超时，避免改共享 requestFactory 的超时而污染并发请求。
- **Constraint:** Spring 6.2.19 的 request attributes 在 execute 前传入，请求 timeout 在创建 delegate 时固定；response.close 可能继续读 body，控制范围必须覆盖整个 exchange。生产承诺限于本项目工厂与 JDK 21 传输，任意注入且忽略中断的 transport 不能靠 Future.cancel 宣称已经退出。
- **Usage:** 按专属设计的包内 RequestControl attribute、私有 AbstractClientHttpRequest 包装器和局部 JdkClientHttpRequestFactory 实现；每次请求的虚拟 I/O 线程由调用者等待真实退出。响应 / 错误解析沿用既有实现；更新确受新增 API / 属性影响的精确断言。
- **Readiness evidence:** T04 四条门禁均退出 0；全单元为 679，生产包 679+4，验收包 679+4+3，前端 170 项与构建通过。已有 Tushare 91 项随全单元 / 双构建执行。此基线不包含尚未编写的 T05 Gate / 上下文测试。

上述直接输入与总体设计一致，无未解决的依赖冲突。完成到下次开始的保守节流、亚毫秒剩余时间拒绝及发送前 / join 后控制复验已在专属设计固定并复核关闭。

## Start Here

按顺序读取：

1. `docs/task-designs/ISSUE-018-T05-design.md` 全文。
2. `docs/task-designs/ISSUE-018-design.md` §3.2、§3.13、§5.1。
3. T01 专属设计、BatchCallContext 与错误合同。
4. 上述 Tushare 客户端、工厂、配置及对应测试。

第一个实施动作：写 `TushareRequestGateTest`，以计数 context + 受控 UTC / 单调时钟 / Sleeper 证明预约→等待→operation 顺序，以及默认三次调用虚拟时间 0/1500/3000ms；观察缺少 Gate 的失败后再实现最小 gate，不重新设计本任务。

用户明确启动后再记录 `READY -> IN_PROGRESS`，保留本交接为入口上下文。按专属设计四条 Maven 命令记录实际测试数量和结果；不以已有 T04 成绩替代本项验证。

## Risks

- 节流按前次完成到下次开始计算，吞吐更保守；其他实例 / 程序共享账户仍可能触发上游限流，不增加账户协调或猜测按接口限流表。
- FutureTask 取消或完成不等于实际 I/O 退出，所有路径 join 实际线程后才释放 gate。正常控制失败不把内部中断注入调用线程；外部中断保留。
- 实际发送前仍需停止检查；0<remaining<1ms 保守拒绝为 TASK_LIMIT_EXCEEDED，配置本身不足 1ms 则 SOURCE_TIMEOUT。控制结果在 join 后再复验，不能返回迟到成功。
- 不同请求必须各自保存超时，不能修改共享工厂配置。受控响应头、慢 body 与关闭行为的证据不能省略。
- 工作区保留 `feat/download-by-date-range` 分支的 T01–T04 暂存成果，未创建提交；本项仅完成设计和 READY 准备，不包含 T05 代码。
