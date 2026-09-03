# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M07-T01`
- **Next task:** `M07-T02`
- **Design document:** `docs/task-designs/M07-T02-design.md`
- **Expected next status:** `READY`；本交接写入并链接后从 `NOT_STARTED` 转换。

## Next Task

- **Task ID:** `M07-T02`
- **Title:** Tushare 请求、响应 DTO 和严格返回校验
- **Goal:** 在 Java 21 `tensor-plugin-tushare` 中交付唯一接触 Token 和上游 JSON 协议的同步 `TushareProClient`，从已验证 `DatasetDefinition` 生成精确请求，经 M07-T01 `RestClient` POST，并在解析前限长、严格校验业务码/字段顺序/行宽后，只为合法成功或合法空结果构造 `DownloadEnvelope`。
- **Scope:** 只创建 `TushareRequest.java`、`TushareResponse.java`、`TushareData.java`、`TushareResponseValidator.java`、`TushareProClient.java` 和 `TushareProClientTest.java` 六个文件。实现精确 `api_name/token/params/fields` 请求、固定脱敏 DTO 字符串、`RestClient.exchange` HTTP 门禁、`readNBytes(max + 1)` 响应限长、严格重复键/尾随根值/标量类型解析、有序结构校验和 WireMock 测试；不得修改 POM、既有代码/YAML/合同，不实现 M07-T03 错误分类、M07-T04 插件装配或其他模块职责。
- **Acceptance criteria:** 唯一公开客户端 API 与四个包内类型符合设计；Token 只在方法局部出站 body 构造点使用；合法成功/空包络保留定义同序字段并以 `items.size()` 计数；HTTP、大小、JSON、业务和结构失败按固定顺序安全拒绝且不产生半包络；严格 RED/GREEN 后聚焦 10/10、三类 mutation、reactor `test`/`verify` 156/156、三层 Enforcer、秘密/静态/范围/格式/清理及精确六文件提交门禁取得设计规定结果。

## Dependencies

### `M03-T09`

- **Artifact:** `docs/task-designs/M03-T09-design.md`、`data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/metadata/TushareMetadataContractTest.java` 与 `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/*.yaml`；实现提交 `36230d8`。
- **Decision:** 49 个 Tushare API、851 列的字段名称与顺序、参数、业务键、filters、表名公式和默认 batchSize 已由永久独立契约门禁冻结；运行时通过 `DatasetDefinition` 暴露 API 名和 columns 原序。
- **Rationale:** 上游请求字段与返回校验必须服从已验证元数据，才能避免插件自建第二套字段合同或静默接受字段漂移。
- **Constraint:** M07-T02 只能从传入 `DatasetDefinition` 读取 API 名和 columns 顺序；不得在生产代码读取 manifest/模板、排序或去重字段、修改 loader/YAML/永久测试，也不得把集合相等替代设计批准的完整同序比较。
- **Usage:** `TushareProClient` 以 definition 生成 `api_name` 和逗号分隔 fields；`TushareResponseValidator` 将返回 fields 与同一 columns 列表完整同序比较；测试通过现有 loader 读取真实 `daily` 定义。
- **Readiness evidence:** M03-T09 在权威看板中为 `COMPLETED`；提交 `36230d8` 的定向契约为 50/50，提交态 reactor `test`/`verify` 为 137/137，三层 Enforcer、JAR/范围/格式/清理门禁及独立审查均已记录通过；当前永久测试和 49 份 YAML 相对该提交无差异。

### `M07-T01`

- **Artifact:** `docs/task-designs/M07-T01-design.md`、`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/config/TushareProperties.java`、`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareRestClientFactory.java`、模块 POM 与 `TushareRestClientFactoryTest.java`；实现提交 `06682a8`，测试安全修复 `6e09e3a`、`e936287`、`9c49eb6`。
- **Decision:** `TushareProperties` 冻结脱敏 `Credential`、`maxResponseBytes` 和配置边界；factory 冻结 JDK 同步 `RestClient`、base URL、connect/read timeout、唯一 `Tensor/1.0` User-Agent 与零应用自动重试，且自身不读取 Token。
- **Rationale:** 配置、凭证状态和传输策略集中在已验证前置任务，使协议客户端只承担一次出站 body 构造、实际响应限长和返回校验。
- **Constraint:** M07-T02 不修改 properties/factory/POM/readiness；只在 `execute` 方法局部调用唯一明文 `Credential.value()`，不得把 Token 放入 URI/header/cookie/日志/异常；必须使用注入的 `RestClient` 和配置上限，不创建平行配置或 HTTP 客户端。
- **Usage:** 公开构造器注入 M07-T01 的 `RestClient` 与 `TushareProperties`；`execute` 从 properties 取得方法局部 Token 和 `maxResponseBytes`，沿用 factory 的 base URL、超时、User-Agent 与零重试。
- **Readiness evidence:** M07-T01 在权威看板中为 `COMPLETED`；聚焦 9/9、提交态 reactor `verify` 146/146、三层 Enforcer、依赖树、秘密/禁用 API/范围/格式/清理门禁和最终范围化复审均已记录通过；当前 M07-T01 四文件集合相对最终修复提交无差异。

- **Dependency comparison:** M03-T09 冻结协议请求/返回必须遵守的 API 与字段顺序，M07-T01 冻结安全配置和同步传输；二者职责互补且无冲突。M07-T02 只在局部连接这些输入并构造既有 M02 `DownloadEnvelope`，同时保留用户批准的 M07-T03 局部错误分类接缝。

## Start Here

1. 完整读取 `docs/task-designs/M07-T02-design.md`，以其中六文件范围、精确类型表面、固定失败消息、十项测试、三类 mutation 和门禁作为唯一实施合同。
2. 核对 `docs/task-handoffs/tensor-v1-task-board.md` 的 M07-T02 行与任务详情。
3. 核对 `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md` 的 Global Constraints、Task M07-T02 和 Module Gate。
4. 核对 `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 7.1～7.3、14.1～14.2、16 与附录 A/B。
5. 核对 M03-T09 设计、永久测试、真实 `daily` YAML/loader，以及 M07-T01 设计、properties、factory、POM 和测试；不得修改这些直接依赖产物。
6. **First action:** 运行设计给出的 reactor 基线并确认 plugin-api 79、tushare 67（146/146）；随后只完整创建 `TushareProClientTest.java`，不创建五个生产类型，运行聚焦命令并确认仅因五个目标生产类型缺失在 `testCompile` 非零。

## Risks

- `TushareRequest` 必须短暂持有明文 Token；固定 `toString()` 不能阻止主动读取，生产 accessor 使用必须限制在唯一方法局部序列化路径。
- 固定通用失败是用户批准的 M07-T02 阶段边界；M07-T03 必须在 HTTP status 或 code/msg 仍为局部值时映射安全 `SourceException`，不得保存原始上游消息/body/cause。
- `readNBytes(max + 1)` 最多保留 64 MiB 加一字节，符合首期限制但仍占用显著堆内存；不得改成无界读取或擅自提高上限。
- 完整同序字段校验有意比 TRD 的集合表述更严格，以保持 M03/M05 合同；真实上游若不遵守请求顺序，必须另行设计裁决，不能静默排序。
- WireMock 需要本地回环端口；受限沙箱若禁止监听，应在允许本地监听的测试环境重跑，不得删除集成断言。
