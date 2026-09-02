# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M06-T06`
- **Next task:** `M07-T01`
- **Design document:** `docs/task-designs/M07-T01-design.md`
- **Expected next status:** `READY`；本交接写入并链接后从 `NOT_STARTED` 转换。

## Next Task

- **Task ID:** `M07-T01`
- **Title:** 配置属性和同步 `RestClient`
- **Goal:** 在 Java 21 `tensor-plugin-tushare` 中提供由 Spring Boot 以 `tensor.plugins.tushare-pro` 前缀构造器绑定的安全不可变配置，以及具有权威 base URL、连接/读取超时、`Tensor/1.0` User-Agent 和零应用自动重试的同步 `RestClient`，为后续唯一出站协议实现提供稳定输入。
- **Scope:** 用户已批准注解方式；只修改模块 POM，并创建 `TushareProperties.java`、`TushareRestClientFactory.java`、`TushareRestClientFactoryTest.java`。新增 BOM 管理的最小 `spring-boot` 核心依赖，完成默认/覆盖绑定、URI/时长/大小验证、脱敏 `Credential`、缺 Token readiness、JDK 同步传输与 Binder/WireMock 测试。不得注册配置 Bean，不实现请求/响应 DTO、响应体限制执行、JSON 校验、错误分类、插件描述符、49 接口下载或其他 M07-T02～T04 职责。
- **Acceptance criteria:** 精确注解前缀、六组件 record、嵌套凭证值对象、默认/覆盖/验证/readiness 与唯一 factory 公开表面符合设计；Token 不进入字符串、日志、异常、URI/header 或 factory 请求内容；client 的 base URL、connect/read timeout、User-Agent、无构造网络副作用和 503 恰一次请求均可观察；严格 RED/GREEN 后聚焦 9/9、两项 mutation、reactor `test`/`verify` 146/146、三层 Enforcer、依赖树、秘密/静态/范围/格式/清理和精确四文件提交门禁得到设计指定结果。

## Dependencies

### `M02-T05`

- **Artifact:** `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/DataSourcePlugin.java`、`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/descriptor/PluginReadiness.java` 与 `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/error/`；实现提交 `445b941`，契约修复提交 `dd495ee`。
- **Decision:** 数据源 SPI 的 readiness 返回已发布的四字段不可变 `PluginReadiness`；公共 plugin-api 保持无 Spring/HTTP/具体插件依赖，安全原因不携带凭证值、配置路径、原始响应或其他敏感诊断。
- **Rationale:** 具体插件需要把 enabled/凭证配置状态投影到统一注册与下载可用性模型，同时不能把具体来源配置或秘密扩散到共享边界。
- **Constraint:** M07-T01 不修改 plugin-api，不创建平行 readiness DTO，不把 `Credential` 或 Token 放入公共类型；只在具体插件配置中保存脱敏值对象，并以 `Disabled`、`Credentials missing` 或 null 原因构造合法 `PluginReadiness`。来源异常及 retryable 分类留给 M07-T03。
- **Usage:** `TushareProperties.readiness()` 根据不可变 enabled/credential 状态创建 `PluginReadiness`；后续 `TushareProPlugin` 可直接返回该投影，当前 factory 不读取 readiness 或任何错误类型。
- **Readiness evidence:** M02-T05 在权威看板中为 `COMPLETED`；最终聚焦 8/8、模块 `verify` 79/79、父级/模块 Enforcer、`jdeps`、禁用依赖与范围门禁已记录通过，最终范围化复审无 Critical/Important/Minor。当前 `DataSourcePlugin` 和 `PluginReadiness` 相对最终修复提交无差异。

唯一直接依赖无内部冲突：M02-T05 约束 framework-free 公共 SPI/readiness，用户批准的 Spring Boot 注解和同步 HTTP 只存在于具体 Tushare 模块；`Credential` 只向公共 readiness 投影布尔状态，不跨越模块边界。

## Start Here

1. 完整读取 `docs/task-designs/M07-T01-design.md`，以其中四文件范围、精确公开表面、固定验证/安全消息、九项测试、两项 mutation 和门禁为唯一实施合同。
2. 核对 `docs/task-handoffs/tensor-v1-task-board.md` 的 M07-T01 行与任务详情。
3. 核对 `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md` 的 Global Constraints、Task M07-T01 和 Module Gate。
4. 核对 `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 7.1～7.2、14.1～14.2、16 与附录 B，以及 PRD 3.1～3.3、10.1～10.3。
5. 核对 M02-T05 设计、上述 `DataSourcePlugin`/`PluginReadiness` 当前产物、`data-plane/pom.xml` 的 Boot 3.5.16 BOM 和 `data-plane/tensor-plugin-tushare/pom.xml` 的现有 Spring Web/WireMock 依赖。
6. **First action:** 先运行设计给出的 reactor 基线并确认 plugin-api 79、tushare 58（137/137）；随后只在模块 POM 增加 BOM 管理的 `spring-boot` 依赖并完整创建 `TushareRestClientFactoryTest.java`，不创建两个生产类，运行聚焦命令并确认仅因 `TushareProperties`/`TushareRestClientFactory` 缺失在 `testCompile` 非零。

## Risks

- `Credential.value()` 是后续出站 body 构造所需的明文访问器；`toString()` 脱敏不能阻止主动读取，M07-T02 必须把生产调用限制在唯一 JSON 构造点。
- `@ConfigurationProperties` 声明绑定合同但不自动注册 Bean；M07-T04 必须显式启用该类型，不得创建平行配置模型。
- WireMock 使用本地 HTTP 覆盖，因此 URI 合同允许绝对 HTTP(S)；生产配置仍须遵守 TRD 的 HTTPS 地址要求。
- 本任务只绑定并验证 `maxResponseBytes`；解析前真正拒绝超过 64 MiB 的响应属于 M07-T02，不得在 M07-T01 完成报告中误报为已执行。
- 503 恰一次请求证明没有应用级自动重试；JDK 传输层内部协议行为不等同于产品自动重试。
