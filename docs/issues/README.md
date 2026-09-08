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

| ID | 问题 | 状态 | 当前阶段 | 文档 |
| --- | --- | --- | --- | --- |
| ISSUE-001 | Controller 方法入口参数不够聚合 | 已解决 | 请求聚合与13种下载类型完成；624项后端、170项前端及7项包合同通过 | [详情](problems/ISSUE-001-method-input-aggregation.md) · [方案](proposals/ISSUE-001-download-request-aggregation.md) · [设计](../task-designs/ISSUE-001-designs.md) · [验收](../verification/ISSUE-001-controller-inputs.md) |
| ISSUE-002 | Controller 承担过多业务逻辑 | 已解决 | Core 分层及显式成功日志完成；638 项后端、170 项前端及 7 项包契约通过 | [详情](problems/ISSUE-002-controller-business-logic-layering.md) · [方案](proposals/ISSUE-002-controller-service-layering.md) · [实施与验收](../superpowers/plans/2026-09-07-issue-002-controller-service-layering.md) |
| ISSUE-003 | 数据库交互逻辑较复杂 | 新增 | 数据库层重构设计已确认，待文档复核 | [详情](problems/ISSUE-003-database-access-complexity.md) · [方案](proposals/ISSUE-003-spring-jdbc-complexity-reduction.md) |
| ISSUE-004 | 前端 UI 不美观，缺乏科技感 | 已解决 | 六任务完成，170单测及60项浏览器验收通过 | [详情](problems/ISSUE-004-ui-visual-redesign.md) · [最终方案](proposals/ISSUE-004-ui-visual-concepts.md) · [技术设计](../task-designs/ISSUE-004-design.md) · [任务看板](../task-handoffs/ISSUE-004/ISSUE-004-task-board.md) · [HTML 预览](proposals/ISSUE-004-ui-visual-concepts.html) · [验收记录](../verification/ISSUE-004-ui-redesign.md) |
| ISSUE-005 | Tushare 小数解析导致适配失败 | 已解决 | 已解决，stock_company 真实三样例通过 | [详情](problems/ISSUE-005-tushare-decimal-decoding.md) · [设计与计划](proposals/ISSUE-005-tushare-decimal-decoding.md) |
| ISSUE-006 | 股东户数公告日期混入日期时间格式 | 已解决 | 已解决，真实150行适配/入库/查看通过 | [详情与设计](problems/ISSUE-006-holdernumber-announcement-date.md) |
| ISSUE-007 | dividend 业务键未区分实施进度 | 已关闭 | 已关闭：修复及真实分红38行闭环通过（M14-T09） | [诊断详情](problems/ISSUE-007-dividend-adapter-diagnosis.md) · [修复设计](proposals/ISSUE-007-dividend-business-key.md) |
| ISSUE-008 | 真实 Tushare 验收尚缺 9 个接口 | 新增 | 不依赖：移出 M14-T08/本轮发布前置条件；未解决，用户后续单独处理 | [详情](problems/ISSUE-008-tushare-live-coverage-gap.md) |
| ISSUE-009 | daily 与 balancesheet 性能尚未验证 | 新增 | 不依赖：移出 M14-T08/本轮发布前置条件；未实测，用户后续单独处理 | [详情](problems/ISSUE-009-query-performance-verification.md) |
| ISSUE-010 | 根 health 公开组件与分组结构 | 已解决 | 三端点仅公开状态；数据库故障回归及新生产包 S01 11/11 通过 | [详情](problems/ISSUE-010-health-component-exposure.md) · [验收记录](../verification/ISSUE-010-health.md) |
| ISSUE-011 | 数据集非只读方法返回 500 | 已解决 | 专用 405 处理；新生产包 S05 12/12 通过，数据与上游调用数不变 | [详情](problems/ISSUE-011-dataset-method-status.md) · [验收记录](../verification/ISSUE-011-dataset-method-status.md) |
| ISSUE-012 | 查询接口未拒绝任意字段参数 | 已解决 | 参数名白名单；新生产包 S06 6/6 通过，合法查询及数据与上游调用数不变 | [详情](problems/ISSUE-012-query-extra-parameters.md) · [验收记录](../verification/ISSUE-012-query-extra-parameters.md) |
| ISSUE-013 | 参数校验失败的查询缺少完成事件 | 已解决 | 查询转换/校验纳入日志；新生产包 S01～S08 与日志门禁通过，15 请求各 1 事件 | [详情](problems/ISSUE-013-query-completion-events.md) · [验收记录](../verification/ISSUE-013-query-completion-events.md) |
| ISSUE-014 | 安全 Maven 门禁未完成 | 已解决 | 原命令重跑：HEAD 七类 81 项、当前源码七类 118 项通过；六模块 Enforcer 通过 | [详情](problems/ISSUE-014-security-maven-verification.md) · [验收记录](../verification/ISSUE-014-security-maven-verification.md) |
| ISSUE-015 | 后端漏洞扫描未形成有效报告 | 已完成 | 扫描脚本已修复；27 个高阈值 CVE 与四类分析缺口按用户决定标记为不需要处理，接受剩余风险并关闭 | [详情](problems/ISSUE-015-backend-dependency-audit.md) · [验证记录](../verification/ISSUE-015-backend-dependency-audit.md) |
| ISSUE-016 | UI 测试 YAML 解析器缺少严格字段校验 | 新增 | 从 ISSUE-004 延期项拆出，已登记，待解决方案 | [详情](problems/ISSUE-016-ui-fixture-yaml-validation.md) |
| ISSUE-017 | 前端 UI 第二版：重做视觉、字体、动效与表格排版 | 新增 | 布局、字体、动效及表格尺寸／对齐提案已记录，待视觉与交互评审 | [详情](problems/ISSUE-017-ui-redesign-v2.md) · [第二版提案](proposals/ISSUE-017-ui-redesign-v2.md) |
