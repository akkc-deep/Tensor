# ISSUE-013：参数校验失败的查询缺少完成事件

## 当前状态

新增，未解决。M14-T07 日志语义门禁失败；本轮凭证扫描通过，未修改日志策略或生产实现。

## 实际证据

[安全证据](../../verification/M14-T07-security.md)共记录 15 个下载/查询 requestId，只找到 11 个完成事件。S07 的 `tsCode`、`page`、`pageSize`、`tradeDateFrom` 四个反例均返回 400 / `PARAM_INVALID`，但其 requestId 对应完成事件数均为 0。其余 11 个请求各有一个事件。

HTTP 输入拒绝检查 S07 通过，不能替代日志门禁；`log_scan` 实际失败原因为 `completion-events-not-unique`。最终报告中的 `log_scan.details.requests` 与 S07 探针可逐 requestId 对照。

## 关闭条件

按问题流程确认修复设计，使校验失败的查询也满足[批准设计](../../task-designs/M14-T07-design.md)的每个本轮下载/查询 requestId 恰有一个完成事件要求。保留既有事件字段合同，不记录完整参数、SQL、上游原文或凭证；以新冻结生产包取得 HTTP 与日志门禁共同通过的证据。
