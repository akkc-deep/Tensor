# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M07-T03`
- **Next task:** `M07-T04`
- **Design document:** `docs/task-designs/M07-T04-design.md`
- **Expected next status:** `READY`；本交接写入并链接后从 `NOT_STARTED` 转换。

## Next Task

- **Task ID:** `M07-T04`
- **Title:** `TushareProPlugin` 描述符、readiness 和 49 接口下载
- **Goal:** 在 Java 21 `tensor-plugin-tushare` 中交付元数据驱动的 `TushareProPlugin` 和单 Bean Spring 装配：固定 `tushare_pro` 描述符并暴露 M03 全部 49 个 API/dataset，以 M07-T01 本地配置反映 readiness，在 client 前安全拒绝不可用/未知下载，并把 ready API 恰一次委托给 M07-T02/M07-T03 当前 `TushareProClient`。
- **Scope:** 只创建 `TushareProPlugin.java`、`TusharePluginConfiguration.java` 和 `TushareProPluginTest.java`。实现批准的插件构造器、固定描述符文案、49 定义投影/不可变查找、readiness、本地失败、一次委托及 Spring 本地装配；不得修改 POM、既有源码/测试/YAML/合同或其他模块，不实现参数校验、适配、持久化、REST、健康、前端、日志或重试。
- **Acceptance criteria:** descriptor 固定 ID/名称/说明并精确暴露 49 个同序 API/dataset；disabled、缺 Token、ready 状态与 M07-T01 一致且所有状态均可创建 Bean；构造/readiness 不联网或泄漏 Token；不可用与未知 API 分别按用户批准的固定失败在 client 前拒绝；ready `daily` 保持 definition、params、包络和 M07-T03 异常身份并只委托一次；严格 RED/GREEN 后聚焦 8/8、插件+M03 契约 58/58、三类 mutation、reactor `test`/`verify` 172/172、三层 Enforcer、秘密/静态/范围/格式/清理及精确三文件提交门禁得到设计规定结果。

## Dependencies

### `M07-T02`

- **Artifact:** `docs/task-designs/M07-T02-design.md`；`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareProClient.java`、三个协议 DTO 与 `TushareResponseValidator.java`；`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/metadata/DatasetDefinitionLoader.java` 和 49 份已消费的 M03 definitions；实现提交 `3244d92`。
- **Decision:** 唯一公开 client 接收一份已验证 `DatasetDefinition` 与 params，同步执行精确请求/严格响应校验，并只返回合法成功或合法空 `DownloadEnvelope`；client/factory 构造不联网。M03 loader 提供按 `apiName` 排序、不可变且无重复的 49 定义列表。
- **Rationale:** M07-T04 只需维护 API 名到 definition 的不可变索引并把现有统一 client 暴露为插件 SPI，不应复制 Tushare 协议、字段清单、响应校验或包络构造。
- **Constraint:** 必须使用公开 `execute(DatasetDefinition, Map)` 与公开 loader/pattern；不得读取模板、重排/修正 definition/params/fields/data、构造第二套包络、直接接触 Token 或在插件/配置构造阶段触发网络。M07-T03 已按授权修改 client/validator 的失败实现，当前接口与成功/空语义仍保持 M07-T02 合同。
- **Usage:** 配置 Bean 方法创建既有 factory/client、加载 classpath definitions，再注入批准的插件构造器；插件从 definitions 投影 49 描述符并按 `ApiName` 查找后调用 client。
- **Readiness evidence:** M07-T02 在权威看板中为 `COMPLETED`；实现提交 `3244d92` 精确六文件，完成证据记录聚焦 10/10、三类 mutation、reactor `test`/`verify` 156/156、三层 Enforcer、秘密/静态/范围/格式/清理门禁。其 public client 签名、DTO、成功/空包络和元数据消费边界在 M07-T03 分类改造后未漂移。

### `M07-T03`

- **Artifact:** `docs/task-designs/M07-T03-design.md`；当前 `TushareErrorClassifier.java`、`TushareProClient.java`、`TushareResponseValidator.java`、`TushareErrorClassifierTest.java` 和 `TushareProClientTest.java`；实现提交 `09c48c5` 与审查补强提交 `546f246`。
- **Decision:** HTTP、业务、transport 和 payload 失败只在 client/validator 局部归约为七项固定安全 `SourceException`，code/message/retryable 受控且无 raw status/code/msg/body/Token/URI/cause；成功与合法空路径保持 M07-T02 行为。
- **Rationale:** 插件层只负责本地可用性、API 查找和委托；一旦进入 client，上游失败分类必须保持唯一来源，避免出现第二套异常规则或重试模型。
- **Constraint:** `TushareProPlugin` 不捕获、包装、翻译或重试 client 的 `SourceException`，也不构造失败/半 `DownloadEnvelope`。本地 disabled/缺 Token 必须在 client 前使用批准的 private `TensorException`、`PLUGIN_DISABLED` 和固定 message；ready 的未知 API 使用批准的固定 `IllegalArgumentException`，两者不得接触上游分类器。
- **Usage:** ready API 的 client 成功包络或 M07-T03 `SourceException` 均以同一实例穿过 `download`；测试用 Mockito 同时断言恰一次成功委托和异常身份传播。
- **Readiness evidence:** M07-T03 在权威看板中为 `COMPLETED`；最终聚焦 classifier/client 18/18、reactor `verify` 164/164、三层 Enforcer、六类 mutation、秘密/静态/范围/格式/清理门禁全部通过，独立复审无 Critical/Important/Minor 且结论 `Ready to merge: Yes`。当前分类器/client/test 包含 `09c48c5` 与 `546f246` 的最终结果。

- **Dependency comparison:** M07-T02 提供统一成功/空下载和 metadata/client 接缝，M07-T03 只在同一 client 内补齐安全上游失败；二者接口、数据流和职责互补且无冲突。M07-T04 在 client 外新增的 local readiness/lookup 失败不会改写任何上游结果，并按用户裁决与现有 `PLUGIN_DISABLED` 合同隔离。

## Start Here

1. 完整读取 `docs/task-designs/M07-T04-design.md`，以其中三文件范围、公开构造器/Bean 形状、固定描述符、49 名称清单、拒绝顺序、八项测试、三类 mutation 和门禁作为唯一实施合同。
2. 核对 `docs/task-handoffs/tensor-v1-task-board.md` 的 M07-T04 行与任务详情，并确认本交接仍是其当前入口上下文。
3. 核对 `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md` 的 Global Constraints、Task M07-T04 和 Module Gate。
4. 核对 `docs/task-designs/M07-T02-design.md`、`docs/task-designs/M07-T03-design.md` 与当前 `TushareProClient`/classifier/validator，保持 `execute(DatasetDefinition, Map)`、成功/空包络和固定 `SourceException` 原样传播。
5. 核对 `TushareProperties.java`、`TushareRestClientFactory.java`、`DatasetDefinitionLoader.java`、49 YAML、`TushareMetadataContractTest.java`，以及 M02 `DataSourcePlugin`、`PluginDescriptor`、`PluginReadiness`、`ApiDescriptor`、`TensorException`/`ErrorCode`；不得修改它们。
6. **First action:** 运行设计给出的 reactor 基线并确认 plugin-api 79、tushare 85（164/164）；随后只完整创建八项 `TushareProPluginTest.java`，不创建两个生产类型，运行聚焦命令并确认仅因 `TushareProPlugin` 与 `TusharePluginConfiguration` 缺失在 `testCompile` 非零。

## Risks

- disabled 或缺 Token 时插件 Bean 必须仍注册并暴露完整描述信息；误加条件 Bean 或构造期 readiness 失败会破坏数据源展示与已入库查询。
- 插件构造期恰 49 门禁有意 fail-fast；classpath 元数据缺失、多余或重复不得降级为部分插件或用生产硬编码补齐。
- 生产实现不得出现 API 名特例；显式 49 清单只存在于独立测试基线，运行时始终以 M03 definition map 查找。
- `PluginDescriptor` 使用不可变 properties 的构造期状态；配置变化通过应用重启生效，不得给插件增加可变 refresh 状态。
