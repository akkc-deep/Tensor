# ISSUE-001：Controller 请求入参聚合设计

任务与实施步骤：[实施计划](../superpowers/plans/2026-09-07-issue-001-controller-inputs.md)。
依据：[问题](../issues/problems/ISSUE-001-method-input-aggregation.md)与[已确认下载方案](../issues/proposals/ISSUE-001-download-request-aggregation.md)。

## 做什么

仅调整 `tensor-app` 的三个 Controller HTTP 请求边界。下载使用明确的参数子类型；数据集查询按数据集路径、证券代码、交易日期范围、公告日期范围、分页聚合。URL、查询参数、JSON、状态码、字段错误、日志及下游公开接口保持兼容，不升级依赖。

## 怎么做

- `DataSourceController.listDataSources` 保留无参。两个单 `pluginId` 入口保留 `String`：它们无需聚合，直接改成 Spring 类型转换会改变非法标识符的字段错误。数据集详情改为 `DatasetPath`；路径保留原始字符串，使“未知插件 + 非法 API”的检查顺序保持不变。
- `DatasetController` 接收 `DatasetRecordsRequest`，包含路径、证券代码、两个日期范围及原始分页值。专用 MVC resolver 仅支持这两个请求类型，路径只取 URI 模板变量，查询参数不能覆盖路径。重复证券代码保持 Spring 的逗号拼接行为，日期取第一个值，分页保留多值供现有规则处理。转换、默认值和校验仍在现有日志边界执行。
- `DownloadRequest` 包含 `DatasetKey`、sealed `DownloadParameters` 和不可变的已提交字段名集合。13 个参数 record 与显式 Codec 定义放在下载绑定包；无反射、无未知结构 Map 回退。字段名集合只用于区分缺失和显式 `null`，保证转换回 Map 后的日志摘要与原请求一致。
- `ParameterShape` 按字段名排序，比较名称、类型、必填、默认值、允许值集合、正则和关联字段，忽略展示标签和 YAML 顺序。每种结构显式注册一个 Codec，按 shape 读取，按参数类型写回 Map。
- Spring 管理 `DownloadDescriptorResolver`、`DownloadParameterResolver`、`DownloadRequestDeserializer` 与 Jackson Module。反序列化器通过私有临时 `WireValues` record 复用 Jackson 原有结构绑定（包括重复字段行为），再检查顶层必填与标识符。临时 Map 仅用于类型解析，不进入 Controller 请求对象，也不作为未知结构回退。随后按插件可用性、唯一可用描述、API、适配器、参数结构的顺序选择参数类型。
- JSON 类型错误或未知字段交给原 `ParameterValidator` 产生错误，复用其“缺失必填优先”的规则及字段排序；可安全绑定的字符串仍由原 Service 校验和规范化。解析失败通过原 `OperationLogger.download` 记录一次失败事件，成功解析由 Controller 记录操作，避免重复事件。
- 自有 `DownloadBindingException` 保留错误码及字段错误；`GlobalExceptionHandler` 只解包这种绑定异常，其他不可读请求仍返回通用 `request` 字段错误。Service、Validator、插件、持久化与日志类均不修改。

## 如何测试

先建立 HTTP 特征测试：顶层缺失/空白/错误类型、多错误优先级、插件/API/适配器不可用、缺失与非法参数并存、未知字段、非法日期、范围倒序、显式 null、成功与失败日志各一次。新增 JSON 绑定测试覆盖 49 个真实 Tushare 元数据与 fixture 的 13 种类型，顺序无关的 shape 和约束变化拒绝行为。GET 回归覆盖原查询筛选、分页、重复参数、非法参数及路径覆盖。生产上下文使用容器 ObjectMapper 读取 daily 并断言具体参数类型。

## 如何验证

定向运行 Controller、绑定、异常处理、生产上下文与参数验证测试，再执行全后端 `*Test,*IT` 和 `mvn verify`（包含前端打包及生产包合同）。检查失败与跳过数量并记录到 `docs/verification/ISSUE-001-controller-inputs.md`。Git diff 应仅涉及 `tensor-app` 与本 issue 文档；将新增文件加入 Git。

## 依赖什么信息

Java 21、现有 Spring Boot/Jackson 配置、插件注册表与适配器注册表用于保持访问错误优先级；49 份 Tushare YAML 和 fixture 元数据用于固定结构；`ParameterValidator` 用于复用校验；`OperationLogger` 用于保留日志与指标；Colima/MySQL Testcontainers 用于真实数据库和生产上下文验证。现有 ISSUE-012/013 测试是查询白名单及完成日志的回归依据。
