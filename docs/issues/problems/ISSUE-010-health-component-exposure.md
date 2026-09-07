# ISSUE-010：根 health 公开组件与分组结构

## 当前状态

已解决（2026-09-07）。三个 health 端点只公开 status，保留数据库健康判断；相关回归与新生产包 S01 的 11 项黑盒检查全部通过。[验收记录](../../verification/ISSUE-010-health.md)包含新构建身份及完整安全运行结果。

## 实际证据

[安全证据](../../verification/M14-T07-security.md)的 `S01.health`：GET `/actuator/health` 返回 200，`statusUp=true`，同时存在 `components`、`groups`，额外字段数为 2；未出现顶层 `details`。未保存组件名称、配置值或原始响应。

两个探针端点通过；其余八个被禁止的 Actuator 路径均返回 404。此问题不代表已发现凭证泄漏。

## 合同与关闭条件

[批准设计](../../task-designs/M14-T07-design.md) S01 要求根 health、liveness、readiness 均只返回状态。既有公开测试 `ObservabilityTest` 仍期望 `show-components=always`，这是需要在修复设计中统一的合同差异；不能用旧测试通过覆盖本次失败。

按问题流程确认修复设计，保留根数据库健康判断，使三个端点满足批准的状态公开边界，并取得新生产包的完整安全验证证据。若需要更换冻结 JAR，先按任务设计流程明确新身份；不暗换输入或放宽本轮断言。

## 修复设计与实施顺序（2026-09-07）

本次采用 M14-T07 的状态公开合同，取代 M09-T06 中 components 始终显示的旧约定。修复范围只涉及 health 输出、相关测试和验证记录。

1. 先修改 `ProductionApplicationContextIT`：根 health、liveness、readiness 必须精确返回 `{"status":"UP"}`；停止自有 MySQL 后根 health 必须为 HTTP 503、`{"status":"DOWN"}`，两探针仍为 UP。覆盖有/无 Token、八个禁止的 Actuator 路径及组件子路径的 404、安全头和 no-store。同步 `ObservabilityTest` 的旧配置断言。
2. 将 `show-components` 改为 `never`，保留 `show-details=never`、probes、db contributor。Boot 3.5.16 的 `HealthEndpointSupport` 无条件把主组名称集合交给 `SystemHealth`，只改配置仍会暴露 `groups`；新增一个继承原生 `HealthEndpointWebExtension` 的 servlet 扩展，在聚合时不传组名称，继续使用原生 contributor、聚合器和 HTTP 状态映射。无需新增 Controller、健康检查或通用响应过滤器。
3. 执行回归和生产打包；以本次工作区源码的独立临时 Git 快照建立新构建身份，不修改原 M14-T07 的冻结 JAR、历史脚本和历史证据。新 JAR 构建后、黑盒验证开始前，将 SHA-256 及快照提交写入本问题的验证记录。
4. 在该快照中复用完整 `verify-release.sh`，把固定旧 JAR SHA 替换成登记的新 SHA，保留全部安全断言；本机 8080 已有服务，验证副本统一使用独立回环端口 18080（启动、浏览器、请求及清理一起替换），不停止现有服务。记录验证副本 SHA 和完整结果。既有其他 ISSUE 的失败继续如实记录，不能据此宣称整个发布安全门禁通过。本问题验收要求 S01 全通过、数据库故障回归通过，并记录完整安全运行结果。

任务按上述顺序串行执行，每一步的结果记入 `docs/verification/ISSUE-010-health.md`。旧合同和旧失败证据保留为历史记录。

## 完成证据

- 47 项定向回归全部通过，验证无/有 Token、根 health 与两个探针的精确 JSON、组件路径 404、安全头，以及数据库中断时根 HTTP 503/DOWN。
- 完整生产构建通过：385 项后端单测、170 项前端单测、4 项生产 JAR 合同检查。
- 验证脚本 309 项自测通过；新 JAR 的 S01 11/11 通过，三个 health 响应的额外字段均为 0。
- 完整安全运行 20 通过、4 失败、0 未执行；失败归属 ISSUE-011/012/013/015。自有资源清理与身份复核通过。本问题关闭不代表全部安全门禁或发布准入通过。
