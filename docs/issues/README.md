# 待修复问题

用于记录已发现、计划后续修复的问题。README 只维护索引，问题详情分别记录在独立文档中。

## 处理流程

1. 记录问题及已知事实。
2. 由 AI 给出解决方案，确认后写入设计文档。
3. 根据已确认的设计制定实施计划。
4. 将计划拆分为可独立验收的任务。
5. 按任务实施、验证并记录结果。
6. 满足关闭条件后将问题标记为已解决。

## 问题索引

| ID | 问题 | 当前阶段 | 文档 |
| --- | --- | --- | --- |
| ISSUE-001 | Controller 方法入口参数不够聚合 | Download 方案已确认，待正式设计 | [详情](problems/ISSUE-001-method-input-aggregation.md) · [方案](proposals/ISSUE-001-download-request-aggregation.md) |
| ISSUE-002 | Controller 承担过多业务逻辑 | 分层方案已确认，待实施计划 | [详情](problems/ISSUE-002-controller-business-logic-layering.md) · [方案](proposals/ISSUE-002-controller-service-layering.md) |
| ISSUE-003 | 数据库交互逻辑较复杂 | 数据库层重构设计已确认，待文档复核 | [详情](problems/ISSUE-003-database-access-complexity.md) · [方案](proposals/ISSUE-003-spring-jdbc-complexity-reduction.md) |
| ISSUE-004 | 前端 UI 不美观，缺乏科技感 | 最终设计已确认，待实施计划 | [详情](problems/ISSUE-004-ui-visual-redesign.md) · [最终方案](proposals/ISSUE-004-ui-visual-concepts.md) · [HTML 预览](proposals/ISSUE-004-ui-visual-concepts.html) |
