# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M07-T02`
- **Next task:** `M07-T03`
- **Design document:** `docs/task-designs/M07-T03-design.md`
- **Expected next status:** `READY`；本交接写入并链接后从 `NOT_STARTED` 转换。

## Next Task

- **Task ID:** `M07-T03`
- **Title:** 鉴权、权限、限流、网络、超时和格式错误分类
- **Goal:** 在 Java 21 `tensor-plugin-tushare` 中把 M07-T02 的 HTTP、非零业务码、网络、读取超时、JSON 和结构失败归约为 M02 七项安全 `SourceException`，固定 code/message 并沿用既有 retryable，同时保持 Token、raw status/code/msg/body、URI 和底层 cause 不进入异常、包络或日志。
- **Scope:** 只创建 `TushareErrorClassifier.java` 与 `TushareErrorClassifierTest.java`，修改 `TushareProClient.java`、`TushareResponseValidator.java` 和 `TushareProClientTest.java`。实现设计冻结的四个包内静态分类入口、HTTP/业务词表/cause/payload 映射和五文件测试门禁；不得修改 POM、plugin-api、协议 DTO、M07-T01、metadata/YAML、合同或其他模块，不实现重试、日志、失败包络、M07-T04 插件装配或后续职责。
- **Acceptance criteria:** HTTP 401/403/429/5xx/其他非 2xx、业务 auth/rate/permission/unknown、DNS/connect/read timeout/其他 transport、invalid JSON/size/structure 得到设计精确 code/message/retryable；auth→rate→permission→unknown 和 timeout/network cause 顺序可观察；所有异常无 raw 输入/cause/suppressed；M07-T02 十项回归保持；严格 RED/GREEN 后聚焦 18/18、三类 mutation、reactor `test`/`verify` 164/164、三层 Enforcer、秘密/静态/范围/格式/清理和精确五文件提交门禁得到设计规定结果。

## Dependencies

### `M07-T02`

- **Artifact:** `docs/task-designs/M07-T02-design.md`，`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareProClient.java`、`TushareResponseValidator.java`、三个协议 DTO，`data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/client/TushareProClientTest.java`；实现提交 `3244d92`。
- **Decision:** 唯一公开 client 已冻结精确请求、方法局部 Token、HTTP-before-body、`readNBytes(max + 1)`、严格 JSON、有序业务/结构校验、成功/合法空 `DownloadEnvelope` 和固定脱敏 DTO；M07-T02 明确保留并授权 M07-T03 在 status、业务 code/msg、transport/parse failure 仍为局部值时修改 client/validator，立即构造 M02 安全 `SourceException`。
- **Rationale:** 分类必须发生在原始上游信息仍可判断但尚未逃逸的唯一接缝，才能同时保留 M07-T02 协议/校验行为和 M02 无 cause、固定 retryable 的公共失败边界。
- **Constraint:** 只在现有局部接缝消费 status/msg/cause；不得读取非 2xx body、保存业务 code/msg 或底层 Throwable、改变 Token/请求/响应上限/严格解析/校验顺序/字段同序/成功包络、增加重试日志或修改协议 DTO。七项 code 与 retryable 必须直接复用现有 `ErrorCode`/`SourceException`，不得复制错误矩阵或把 raw 输入拼入 message。
- **Usage:** 新 classifier 接收 client callback 的 status、validator 非零分支的 msg、client/reader 的 transport failure，并为解析/限长/结构失败提供统一 payload exception；client/validator 立即抛出返回的 `SourceException`，既有 client test 原十项场景升级为 code/message/retryable/脱敏回归。
- **Readiness evidence:** M07-T02 在权威看板中为 `COMPLETED`；实现提交 `3244d92` 精确六文件，聚焦 10/10、响应上限/字段顺序/秘密路径 mutation、提交态 reactor `test`/`verify` 156/156、三层 Enforcer、秘密/静态/范围/格式/清理门禁和范围化复审均已记录通过；当前 client、validator、client test 相对该实现提交无差异。M02 `SourceException` 当前仍只接受 `(ErrorCode,String)`，七项 retryable 真值无漂移。

- **Dependency comparison:** M07-T02 同时提供要保持的协议/校验结果和已批准的错误分类局部接缝；其既有 M02 来源异常合同提供公共 code/retryable 表面。二者职责互补且无冲突。M07-T03 只把不可信局部信息归约为设计固定的七类安全异常，不更改前置成功语义或后继插件职责。

## Start Here

1. 完整读取 `docs/task-designs/M07-T03-design.md`，以其中五文件范围、四个 classifier 操作、固定消息表、HTTP/业务/cause 顺序、十八项聚焦测试、三类 mutation 和门禁作为唯一实施合同。
2. 核对 `docs/task-handoffs/tensor-v1-task-board.md` 的 M07-T03 行与任务详情，并确认本交接仍是其当前入口上下文。
3. 核对 `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md` 的 Global Constraints、Task M07-T03 和 Module Gate。
4. 核对 `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 7.1～7.4、14.1～14.2、15、20.1 和附录 A/B，以及 `docs/contracts/error-codes.md` 的七项 source code/message 安全边界。
5. 核对 M07-T02 设计、提交 `3244d92` 的 client/validator/协议 DTO/client test，以及 M02 `ErrorCode.java`、`SourceException.java`、`TensorException.java`；只修改设计授权的三个既有文件。
6. **First action:** 运行设计给出的 reactor 基线并确认 plugin-api 79、tushare 77（156/156）；随后只完整创建八项 `TushareErrorClassifierTest.java` 并修改既有 client test 的失败断言，不创建/修改生产类型，运行聚焦命令并确认仅因 `TushareErrorClassifier` 缺失在 `testCompile` 非零。

## Risks

- 业务分类只使用用户批准的最小词表；上游文案变化会安全回退 payload invalid。不得擅自加入数字码、同义词或把原消息用于诊断。
- `HttpConnectTimeoutException` 继承 `HttpTimeoutException` 但必须归 network；一般 network cause 嵌套读取 timeout 时 timeout 必须胜出，需保留显式两遍/有界 cause 顺序。
- 其余非 2xx 保守归 unavailable；更细 3xx/4xx 语义需后续独立裁决，不得泄露 status/body。
- WireMock 需要本地回环端口；受限沙箱若禁止监听，应在允许本地监听的测试环境重跑，不得删除集成断言。
