# ISSUE-013：参数校验失败的查询缺少完成事件

## 当前状态

已解决（2026-09-07）。查询参数转换/校验拒绝纳入一次性日志包装，新冻结生产包 S01～S08 与日志门禁共同通过：15 个下载/查询 requestId 各恰有一个完成事件，含 S06/S07 的 10 个参数拒绝。完整证据见[验收记录](../../verification/ISSUE-013-query-completion-events.md)。

## 实际证据

[安全证据](../../verification/M14-T07-security.md)共记录 15 个下载/查询 requestId，只找到 11 个完成事件。S07 的 `tsCode`、`page`、`pageSize`、`tradeDateFrom` 四个反例均返回 400 / `PARAM_INVALID`，但其 requestId 对应完成事件数均为 0。其余 11 个请求各有一个事件。

HTTP 输入拒绝检查 S07 通过，不能替代日志门禁；`log_scan` 实际失败原因为 `completion-events-not-unique`。最终报告中的 `log_scan.details.requests` 与 S07 探针可逐 requestId 对照。

## 关闭条件

按问题流程确认修复设计，使校验失败的查询也满足[批准设计](../../task-designs/M14-T07-design.md)的每个本轮下载/查询 requestId 恰有一个完成事件要求。保留既有事件字段合同，不记录完整参数、SQL、上游原文或凭证；以新冻结生产包取得 HTTP 与日志门禁共同通过的证据。

## 修复设计与实施计划

按用户“解决 ISSUE-13”的授权恢复已有完成事件合同。根因是目录/筛选/QueryCriteria 校验在 `OperationLogger.query` 之前，Spring 的日期和整数转换还会在进入 Controller 前失败。

Controller 通过 MultiValueMap 读取日期和分页首值，保留原有重复参数、空值和默认值语义。分页先安全转换为可空整数，其转换失败的拒绝及日期转换、白名单、目录/筛选和 QueryCriteria 校验统一放进现有查询包装器。转换失败沿用 `MethodArgumentTypeMismatchException` 的安全字段错误响应，日志分类为 `PARAM_INVALID/parameter`。分页日志只接受转换后的整数，无法转换时写 `unavailable`；筛选摘要仍只含固定名称，非法路径和未注册 key 继续不生成高基数指标。成功、业务失败、下载的字段与计数语义不变。

实施及验证步骤见[实施计划](../../superpowers/plans/2026-09-07-issue-013-query-completion-events.md)。

## 完成结果

- 新增 27 项 Controller 回归；定向 156 项测试及独立代码复审通过。14 个原始参数错误用例先复现日志缺失；重复参数兼容性问题也经过 RED/GREEN 修正。
- 完整构建 397 项后端单测、4 项生产包合同、170 项前端单测通过。
- 新冻结生产包 21 项限定门禁及验收器 327 项自检通过。15 个请求逐 ID 完成事件唯一、字段安全且与 HTTP 分类一致；业务表指纹和 stub 调用数保持预期，身份复核与清理通过。
- ISSUE-014/015 及整体发布状态仍独立处理，历史失败报告不改判。
