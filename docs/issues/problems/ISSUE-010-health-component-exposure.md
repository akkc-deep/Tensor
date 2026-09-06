# ISSUE-010：根 health 公开组件与分组结构

## 当前状态

新增，未解决。M14-T07 黑盒验证发现生产包不满足已批准的“health 只公开状态”合同。本轮只记录问题，未修改生产实现。

## 实际证据

[安全证据](../../verification/M14-T07-security.md)的 `S01.health`：GET `/actuator/health` 返回 200，`statusUp=true`，同时存在 `components`、`groups`，额外字段数为 2；未出现顶层 `details`。未保存组件名称、配置值或原始响应。

两个探针端点通过；其余八个被禁止的 Actuator 路径均返回 404。此问题不代表已发现凭证泄漏。

## 合同与关闭条件

[批准设计](../../task-designs/M14-T07-design.md) S01 要求根 health、liveness、readiness 均只返回状态。既有公开测试 `ObservabilityTest` 仍期望 `show-components=always`，这是需要在修复设计中统一的合同差异；不能用旧测试通过覆盖本次失败。

按问题流程确认修复设计，保留根数据库健康判断，使三个端点满足批准的状态公开边界，并取得新生产包的完整安全验证证据。若需要更换冻结 JAR，先按任务设计流程明确新身份；不暗换输入或放宽本轮断言。
