# ISSUE-018-T05：Tushare 请求上下文与共享节流

任务：[ISSUE-018 看板 T05](../task-handoffs/ISSUE-018/ISSUE-018-task-board.md#issue-018-t05)。直接输入：[T01 合同设计](ISSUE-018-T01-design.md)及 `BatchCallContext`。共享来源：[总体设计](ISSUE-018-design.md) §3.2、§3.13、§5.1。

## Goal

同一 TushareProClient 的旧同步与带上下文调用统一预约请求、等待来源节流，并以本轮剩余时间限制网络请求。停止或到期不发送后续请求、不返回可继续入库的迟到结果；调用返回前实际 I/O 必须已经退出。

## Scope

扩展客户端上下文入口、新增共享 TushareRequestGate、配置最小请求间隔；在既有 RestClient / JDK HttpClient 传输中落实每请求独立超时，覆盖请求头等待、响应体和关闭阶段。新增可控时钟 / 受控上游测试，更新受影响的精确 public-surface 与配置断言。

本项不实现 BatchDownloadSupport 的 Tushare 规划 / downloadBatch / 日期策略、不开放 RANGE、不接线任务 worker、不持久化预算、不改 40 项 YAML、迁移、HTTP 或前端。T06 后续所有规划、批量和后台 SINGLE 请求只能使用本项的带上下文入口；旧 TushareProPlugin.download 继续通过旧 execute 入口。仅约束同客户端实例，不增加跨进程账户协调或按接口猜测限流值。

## Approach

### 配置与兼容接口

`TushareProperties` 最后追加 `@DefaultValue("1500ms") Duration minRequestInterval`，默认 1500 毫秒。必须非 null、非负，且可安全转换为 long 纳秒；非法值固定 `minRequestInterval must be non-negative and fit in nanoseconds`，不拼接原始值。0 是明确配置的无额外间隔，不改变预算 / 停止 / 截止检查；生产缺省值仍是 1500ms。保留原六参数构造器，委托七参数规范构造器并填默认间隔；多构造器下用 Spring Boot `@ConstructorBinding` 明确绑定七参数规范构造器，防止默认绑定失效。

配置入口为 `tensor.plugins.tushare-pro.min-request-interval`。沿用其他配置的自动绑定，不必修改 app YAML。操作者可按更慢的账户 / 接口约束增大此统一间隔；更慢约束优先，不能在某个 execute 重载中重置为 1500ms。现有仓库没有可复用的逐 API 限流表，本项不虚构一个。保留 connectTimeout、readTimeout 的现有校验与 120 秒上限、maxResponseBytes 的 64 MiB 默认及上界。

`TushareProClient` 保留唯一公开构造器 `(RestClient restClient, TushareProperties properties)` 及旧公开方法，增加：

```java
DownloadEnvelope execute(DatasetDefinition definition, Map<String,Object> params,
                         BatchCallContext context);
```

公开构造器创建一个实例级 gate，使用系统 UTC Clock、System.nanoTime 和 Thread.sleep；增加包内构造器 `(RestClient, TushareProperties, TushareRequestGate)` 供同包受控时钟测试。两个 execute 共享同一个 gate 与原有 JSON / 响应校验路径；definition、params、context 用具名 NPE 拒绝，编码失败在预约之前处理，不发 HTTP。

旧 execute 委托带上下文入口，使用私有兼容 context：deadline=Instant.MAX、stopRequested=false、beforeRequest 无持久预算副作用。它仍遵守客户端节流、配置网络超时和线程中断；不伪造后台任务 ID 或预算记录。client 内不缓存最近任务 context、参数、响应或凭证副本。

### TushareRequestGate 的确定顺序

新增 client 包内 final `TushareRequestGate`，不增加公共插件 API。构造器：`(Duration minInterval, Clock clock, LongSupplier nanoTime, Sleeper sleeper)`；Sleeper 为包内嵌套函数接口 `void sleep(Duration duration) throws InterruptedException`。生产使用 UTC / 单调时间，测试注入二者；gate 提供包内 `Clock clock()` 供相同调用链传给每请求超时工厂，以及 `<T> T execute(BatchCallContext context, Supplier<T> operation)`。共享包内静态 `check(BatchCallContext context, Clock clock)` 统一停止 / 线程中断 / 截止检查，供 gate、I/O 调用及请求包装器使用。

每实例用一个 ReentrantLock 串行保护完整 operation 与实际 I/O 退出。为给来源保守、可证明的最小间隔，明确采用**上一个实际调用结束至下一个调用开始**的间隔：比按开始时间计算更保守，响应慢时吞吐会降低；本任务没有并发吞吐承诺，不优化此间隔。上一调用出错但已尝试传输，同样计入间隔；只预约后在节流中失败且未开始 operation，则不更新最后完成时间。

顺序固定：

1. 校验 context 与 deadline 非空；调用 `context.beforeRequest()` 恰好一次，且在任何等待、节流和实际请求之前。其已分类 TensorException 原样传播。不在客户端实现或重置 T07 的数据库预算；预约成功后再等待失败，预算仍保留。
2. 检查 context.stopRequested / 当前线程中断，再检查 UTC now < deadline；停止优先。以 `tryLock()` 加可中断短等待取得 gate，不能用无法检查停止的 synchronized 入口或无界 lock 等待。每次等待不超过 min(100ms, 剩余时限)，每轮重查控制条件。
3. 取得 gate 后，以单调纳秒计算距上一完成的间隔，循环短等待至满足 minInterval；同时用 UTC Clock 检查任务截止。第一次无需等待，minInterval=0 无额外等待。等待上限为 min(100ms, 尚缺间隔, 剩余时限)，不向上舍入突破截止；使用 System.nanoTime 差值语义避免加法溢出。测试单调时钟和 UTC 时钟一起受控推进。
4. 最后再查停止 / 截止，然后调用 operation。此时只调用一次，不自动 retry，不因为失败退还预算。gate 保持持有至 operation 的整个请求、响应关闭以及 I/O 线程退出。
5. operation 开始后无论成功失败，在 finally 记录最后完成的单调时间并 unlock；operation 前失败只 unlock。不将异常文字、token 或 params 写到日志或异常。

停止 / 等待 InterruptedException：恢复当前线程的中断标志，抛固定 `EXECUTION_INTERRUPTED`；当前时间等于或晚于截止时间抛固定 `TASK_LIMIT_EXCEEDED`。可在 gate 内使用私有 TensorException 子类及包内 `check(BatchCallContext, Clock)` / `failure(ErrorCode)` 辅助方法，固定消息分别为 `Download task execution was interrupted`、`Download task limit exceeded`。插件不依赖 core.StoredError 或 app。

### 每请求超时，保留现有 RestClient 注入

当前 Spring 6.2.19 的 JdkClientHttpRequestFactory 在创建请求时固定 Spring TimeoutHandler 超时，但不设置原生 HttpRequest.timeout；RestClient 支持本地 request attributes。保留 `TushareRestClientFactory.create(TushareProperties)` 公开接口与共享 JDK HttpClient（已有 connectTimeout / 不跟随重定向 / User-Agent）。不在并发调用中对共享 requestFactory.setReadTimeout，也不通过 reflection 修改 JDK 请求。

工厂内部改为返回一个私有 `AbstractClientHttpRequest` 包装请求。包装类实现 method / URI、ByteArrayOutputStream 请求体及 executeInternal(HttpHeaders)；使用已有 public AbstractClientHttpRequest，不引用包不可见的 AbstractBufferingClientHttpRequest / AbstractStreamingClientHttpRequest。

在工厂中定义包内常量 `CONTROL_ATTRIBUTE = "tensor.tushare.requestControl"` 和包内嵌套 record `RequestControl(BatchCallContext context, Clock clock)`。带上下文客户端给 RestClient 请求添加该本地 attribute，context 为本次调用对象、clock 来自 gate；attribute 不进入 URL、headers、JSON body 或持久参数。工厂直接使用的其他请求没有 attribute 时保留配置 readTimeout。

包装请求在 executeInternal 内先执行 `check(context, clock)`，再计算 `remaining = Duration.between(clock.instant(), context.deadline())`。remaining<1ms（含非正数）保守拒绝为 TASK_LIMIT_EXCEEDED，不发送请求；Spring 6.2.19 将 Duration 截为整数毫秒，不把不足 1ms 的任务预算传成 0，也不向上舍入。readTimeout 取 min(properties.readTimeout, remaining) 向下截取整数毫秒；若是配置本身不足 1ms，则固定 SOURCE_TIMEOUT 且不发送，保留其立即超时分类。创建一次局部 JdkClientHttpRequestFactory，使用私有委托 HttpClient 包装器以 immutable request builder 设置同一正数原生 timeout，底层仍复用工厂持有的 HttpClient；同时设置 Spring readTimeout，然后创建 delegate，复制 headers，仅在已编码 body 非空时复制 body。空 GET / POST 不强制开启 streaming body，以保留旧传输行为。紧贴 `delegate.execute()` 前再次执行 `check(context, clock)`，避免线程排程或构建请求期间的停止被忽略；若此时 remaining<1ms 也拒绝。请求局部工厂没有独立线程池或需要关闭的 HttpClient；后续请求仍复用共享客户端连接设施。无 attribute 的路径仍有原 readTimeout，原工厂的 GET / 状态码 / 无自动重试行为保持。

RestClient 本地 attributes 在调用 ClientHttpRequest.execute 之前填入，故可在包装请求 executeInternal 读取。remaining 的最终计算在 I/O 线程实际创建 delegate 时进行，覆盖预约、节流和线程调度所消耗的预算。connectTimeout 仍为共享 HttpClient 原值，但覆盖整个交换的更短请求 timeout 同时限制连接阶段；不能让两个超时串行叠加超过本轮剩余时间。

### 等待上游、停止与真正退出

仅在 gate 内的 operation 中启动一个本次调用专用的 Java 21 虚拟 I/O 线程；FutureTask 的 callable 先执行 `check(context, gate.clock())`，再执行**整个既有 RestClient.exchange**，包含框架 finally 的 response.close。保留当前有界响应读取与 JSON / 身份校验，不把 body 读到 gate 之外；Spring JDK response.close 可能读取未消费 body，必须同样处于超时和停止控制内。

调用线程用 `FutureTask.get` 分段等待，每段 <= min(100ms, 剩余时限)，每轮检查停止 / 截止；FutureTask 完成后仍先 join 实际 I/O 线程；join 返回后、接受 envelope 或传播普通 I/O 分类之前，再执行一次控制检查。网络返回后才检测到到期的结果不能交给后续适配 / 入库。

任一停止、截止或调用线程 InterruptedException 时，调用 `future.cancel(true)` 中断 I/O；随后**在所有结果路径** join 实际线程直到其退出（包括成功、ExecutionException、cancel 返回 false 的竞态）。FutureTask 已完成 / 已取消不等于线程已退出，不能只等 Future 状态，不启动替代请求或先释放 gate。join 期间若调用线程被打断，继续清理，最终恢复调用线程中断标志并返回 EXECUTION_INTERRUPTED。正常停止 / 到期只中断 I/O 子线程，不给调用者注入虚假中断。

先记住控制失败，再取消和 join；外部中断仍优先，随后先返回已保存的控制失败，普通完成路径才重新检查上下文，不让取消产生的 IOException 把 TASK_LIMIT_EXCEEDED / EXECUTION_INTERRUPTED 改成 SOURCE_NETWORK_ERROR。如果没有控制失败，按既有 TushareErrorClassifier 分类 HTTP、业务、ResourceAccessException、IOException；配置 readTimeout 先耗尽而任务仍未截止时继续是 SOURCE_TIMEOUT。已有 TensorException 原样传播；错误不含原 cause、suppressed、地址、凭证或响应正文。异常解包不能将 ExecutionException / CancellationException / 原始 RuntimeException 直接暴露；意外非分类运行时错误固定 SOURCE_UNAVAILABLE（扩展 classifier 的包内固定失败入口即可），不要捕获 Error 并冒充业务失败。

Spring 6.2.19 的 TimeoutHandler 在响应头后到期会关闭原始 InputStream，读取错误未必含 HttpTimeoutException；JdkClientHttpResponse.close 还会吞掉 drain 的 IOException。带 RequestControl 的请求保存实际选择的超时及其来源（任务预算或配置），在 delegate.execute 前记录 System.nanoTime，并用私有响应 / 输入流包装器在读取、I/O 异常及实际 close 完成后核对经过时间；超出所选正数超时分别抛 TASK_LIMIT_EXCEEDED 或 SOURCE_TIMEOUT，覆盖亚毫秒下取整早于 UTC deadline 的情况。依靠已有 JDK / Spring 定时取消与关闭使 I/O 退出，不新增定时器或共享执行器；超时分类不会替代客户端的 cancel + join。无 attribute 的直接工厂调用保持原标准 HTTP / ResourceAccessException 行为。测试通过 package-private HttpClient 注入记录真实 native request timeout，不增加公开 API。

生产 JDK 21 传输在等待响应头时收到 interrupt 会取消 HTTP future，响应 InputStream 在中断时关闭订阅；本项必须用受控响应头 / 慢 body 测试证明退出。若注入不响应中断的任意自定义传输，仍等实际退出，不提前返回；该传输不在生产超时承诺内。此限制保留原公开 RestClient 注入用于测试，同时不声称可以约束任意第三方无限阻塞 transport。

## Files

- 新增 `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareRequestGate.java`：实例共享节流、可控时间与固定控制错误。
- 修改同目录 `TushareProClient.java`：上下文重载、兼容 context、共享 gate、局部 deadline attribute、受控虚拟 I/O 线程及异常收束。私有帮助方法留同文件，不创建任务框架。
- 修改同目录 `TushareRestClientFactory.java`：私有请求包装、每请求超时、复用 HttpClient。公开 create 及 createHttpClient 的既有合同保留。
- 修改 `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/config/TushareProperties.java`：第七项绑定及六参数兼容构造器。
- 修改同 client 目录 `TushareErrorClassifier.java`：仅增加控制路径实际需要的固定安全错误访问，不变更旧来源分类。
- 新增对应测试目录 `TushareRequestGateTest.java`；扩展 `TushareProClientTest.java`、`TushareRestClientFactoryTest.java`；独立 `TushareProClientControlTest.java` 承载受控传输与线程退出测试，避免挤入已有协议测试。TushareProPluginTest 与 app 既有六参数构造调用因兼容重载继续编译，同步 TushareErrorClassifierTest 中受新增包内方法影响的精确断言；StockScopedDownloadTest 的受控上游显式配置零间隔，避免股票归属测试执行真实节流等待。
- 不改 T01 的 BatchCallContext、生产插件接口、TusharePluginConfiguration bean 签名或新增依赖；不新增公共 request timeout API、全局 ThreadLocal、共享执行器或 Spring 生命周期组件。

## Tests

第一个实现动作：写 TushareRequestGateTest，用计数 context + 受控 Clock / nanoTime / Sleeper 断言三次 operation 的顺序为 reservation→wait→operation，默认间隔的虚拟时间为 0、1500、3000 毫秒，真实等待为零。先观察缺少 Gate 类型的失败，再实现最小 gate。

必须覆盖：

1. 默认 / 显式 0 / 更慢 3000ms 绑定、负数 / null / 纳秒溢出拒绝、六参数兼容默认、凭证 toString 脱敏；原 read / connect / 64 MiB 边界保留。精确 record / public method 断言按新增合同更新，不仅删除旧断言。
2. 同一 gate 混合旧 / 带 context 请求共享间隔、同客户端不同 definition 也共享；另一个客户端独立。并发两请求使用 latch 和 fake sleeper，第二次 beforeRequest 已执行，但 operation 不得重叠；第一结束后再等完整间隔。失败请求也形成间隔；在节流中失败不改最后完成时间。
3. 预算拒绝零 operation / 零 HTTP；预约后节流中停止 / 到期仍保留一次预算。等待 gate、等待间隔、I/O 线程启动后和 delegate.execute 前都检查 stop / deadline；用请求包装器的 latch 在已预约且 caller 已通过 gate 后置 stop=true，释放时必须零 HTTP；=deadline 拒绝，外部 interrupt 恢复标志。所有 latch / future 等待有界，不用数秒 sleep 猜间隔。
4. 受控 request factory 记录请求 attribute 及真实 delegate timeout，模拟节流和 I/O 排程后推进时钟，断言 min(config, remaining) 向下取整及正数精度，0<remaining<1ms 固定 TASK_LIMIT_EXCEEDED、配置本身<1ms 固定 SOURCE_TIMEOUT，均零 HTTP；没有 attribute 的 create 用例保留配置超时。并发不同 deadline 不互相污染，不通过检查一个被 mock 返回的数字冒充实际 timeout。
5. WireMock（或本地 JDK HttpServer latch）在请求已收到后阻塞响应头：带 context 调用先预约一次，停止或截止使一次请求退出，无重发；caller 返回前 I/O 线程 finally 已运行。正常响应同样 join，再检查 envelope 与原 source 参数一致。
6. 单独受控慢 body：先发响应头，再阻塞 body；停止 / 截止必须关闭并退出，覆盖 header timeout 之后的读取。HTTP 非 2xx 的大 / 慢响应体也要验证 response.close 阶段受控；失败不能因自动 drain 超出原 read / 任务期限而无限阻塞。latch 释放和服务器清理放 finally。
7. 配置 readTimeout 小于任务预算时 SOURCE_TIMEOUT；任务截止先到时 TASK_LIMIT_EXCEEDED；stop 优先 EXECUTION_INTERRUPTED。到期同时收到成功、IOException 或 FutureTask 已完成的竞态都不得返回 envelope；自定义受控传输忽略一次中断后等测试释放，证明 Future.cancel 不使调用提前结束或 gate 提前开放。
8. 按两个 execute 重载覆盖已有固定 HTTP / 网络 / 业务错误、无 cause / suppressed / secret，严格 JSON / body 大小 / 股票归属及无自动 retry 回归；出错后同客户端下一次调用能获得 gate，正常 caller 没有残留内部中断。新增控制对象只作为本地 attribute，实际上游 body 仍只含 api_name/token/params/fields，headers 无预算或凭证。

从根目录运行（Java 21，构建内 Node 24.15.0 / npm 11.12.1）：

```sh
mvn -f data-plane/pom.xml -Dtest=TushareRequestGateTest,TushareProClientTest,TushareProClientControlTest,TushareRestClientFactoryTest,TushareProPluginTest,StockScopedDownloadTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml -Dtest='*Test,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f data-plane/pom.xml clean verify
mvn -f data-plane/pom.xml -Pacceptance clean verify
```

四条退出 0，选定类实际执行，失败 / 错误 / 跳过为 0；前端内嵌测试与构建通过。不需要真实 Tushare token，受控上游只证明传输合同，不替代 T13。T05 不新增数据库测试要求，不运行依赖 main / 干净 HEAD 的发布脚本。

## Acceptance

同客户端所有实际请求统一经历一次预算预约和同一节流 gate，默认至少 1500ms 且可保留更慢的统一约束。整个交换（含响应关闭）受剩余时限和停止检查保护，迟到结果被拒绝；退出和 gate 释放发生在真实 I/O 已退出之后。新旧入口保持现有身份、JSON、64 MiB、安全错误与同步插件行为。T06 可直接调用新重载，不需选择超时 / 停止协议；实现与实际测试证据先写 T05 完成事实，再准备 T06 的完整设计和交接。

## Risks

- 保守的完成到下次开始节流降低吞吐，但不放宽最小间隔；多个实例或其他程序共用账户仍可能上游限流，按实际错误停止处理，不承诺统一账户调度。
- 时间注入仅为 package-private 生产依赖，不增加任意测试开关。单调时间控制节流、UTC 控制任务截止，两者不能混用。
- Spring/JDK 的中断和 response.close 行为是传输边界；升级依赖后须重跑响应头、慢 body 和关闭测试。任意注入且无限阻塞的第三方 transport 不在超时保证内，不能用取消 Future 冒充它已经退出。
- 新配置不进入任务参数或定义摘要；本项没有范围完整性 / 日期规划判断，也不说明任何真实 RANGE 接口已开放。
