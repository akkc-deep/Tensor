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

2026-09-13 按用户要求，将 ISSUE-018-T14 的剩余缺口拆为 ISSUE-019～026，按编号串行处理。主责接口分组不重叠，最终任务验收统一归 ISSUE-026；[后续 issue 看板](../task-handoffs/ISSUE-018/ISSUE-018-followups-task-board.md)管理新 issue 状态，原 T14 仍由原看板管理，拆分不改变其验收结论。

2026-09-14 按用户明确要求，将ISSUE-026继续拆为ISSUE-027～032六个子issue；[子issue看板](../task-handoffs/ISSUE-026/ISSUE-026-task-board.md)管理它们，ISSUE-026保留总任务与最终关闭条件。

| ID | 问题 | 状态 | 当前阶段 | 文档 |
| --- | --- | --- | --- | --- |
| ISSUE-001 | Controller 方法入口参数不够聚合 | 已解决 | 请求聚合与13种下载类型完成；624项后端、170项前端及7项包合同通过 | [详情](problems/ISSUE-001-method-input-aggregation.md) · [方案](proposals/ISSUE-001-download-request-aggregation.md) · [设计](../task-designs/ISSUE-001-designs.md) · [验收](../verification/ISSUE-001-controller-inputs.md) |
| ISSUE-002 | Controller 承担过多业务逻辑 | 已解决 | Core 分层及显式成功日志完成；638 项后端、170 项前端及 7 项包契约通过 | [详情](problems/ISSUE-002-controller-business-logic-layering.md) · [方案](proposals/ISSUE-002-controller-service-layering.md) · [实施与验收](../superpowers/plans/2026-09-07-issue-002-controller-service-layering.md) |
| ISSUE-003 | 数据库交互逻辑较复杂 | 新增 | 数据库层重构设计已确认，待文档复核 | [详情](problems/ISSUE-003-database-access-complexity.md) · [方案](proposals/ISSUE-003-spring-jdbc-complexity-reduction.md) |
| ISSUE-004 | 前端 UI 不美观，缺乏科技感 | 已解决 | 六任务完成，170单测及60项浏览器验收通过 | [详情](problems/ISSUE-004-ui-visual-redesign.md) · [最终方案](proposals/ISSUE-004-ui-visual-concepts.md) · [技术设计](../task-designs/ISSUE-004-design.md) · [任务看板](../task-handoffs/ISSUE-004/ISSUE-004-task-board.md) · [HTML 预览](proposals/ISSUE-004-ui-visual-concepts.html) · [验收记录](../verification/ISSUE-004-ui-redesign.md) |
| ISSUE-005 | Tushare 小数解析导致适配失败 | 已解决 | 已解决，stock_company 真实三样例通过 | [详情](problems/ISSUE-005-tushare-decimal-decoding.md) · [设计与计划](proposals/ISSUE-005-tushare-decimal-decoding.md) |
| ISSUE-006 | 股东户数公告日期混入日期时间格式 | 已解决 | 已解决，真实150行适配/入库/查看通过 | [详情与设计](problems/ISSUE-006-holdernumber-announcement-date.md) |
| ISSUE-007 | dividend 业务键未区分实施进度 | 已关闭 | 已关闭：修复及真实分红38行闭环通过（M14-T09） | [诊断详情](problems/ISSUE-007-dividend-adapter-diagnosis.md) · [修复设计](proposals/ISSUE-007-dividend-business-key.md) |
| ISSUE-008 | 真实 Tushare 验收尚缺 9 个接口 | 已关闭 | 2026-09-11 永久移除这 9 个接口，取消后续支持与验收 | [详情](problems/ISSUE-008-tushare-live-coverage-gap.md) |
| ISSUE-009 | daily 与 balancesheet 性能尚未验证 | 新增 | 不依赖：移出 M14-T08/本轮发布前置条件；未实测，用户后续单独处理 | [详情](problems/ISSUE-009-query-performance-verification.md) |
| ISSUE-010 | 根 health 公开组件与分组结构 | 已解决 | 三端点仅公开状态；数据库故障回归及新生产包 S01 11/11 通过 | [详情](problems/ISSUE-010-health-component-exposure.md) · [验收记录](../verification/ISSUE-010-health.md) |
| ISSUE-011 | 数据集非只读方法返回 500 | 已解决 | 专用 405 处理；新生产包 S05 12/12 通过，数据与上游调用数不变 | [详情](problems/ISSUE-011-dataset-method-status.md) · [验收记录](../verification/ISSUE-011-dataset-method-status.md) |
| ISSUE-012 | 查询接口未拒绝任意字段参数 | 已解决 | 参数名白名单；新生产包 S06 6/6 通过，合法查询及数据与上游调用数不变 | [详情](problems/ISSUE-012-query-extra-parameters.md) · [验收记录](../verification/ISSUE-012-query-extra-parameters.md) |
| ISSUE-013 | 参数校验失败的查询缺少完成事件 | 已解决 | 查询转换/校验纳入日志；新生产包 S01～S08 与日志门禁通过，15 请求各 1 事件 | [详情](problems/ISSUE-013-query-completion-events.md) · [验收记录](../verification/ISSUE-013-query-completion-events.md) |
| ISSUE-014 | 安全 Maven 门禁未完成 | 已解决 | 原命令重跑：HEAD 七类 81 项、当前源码七类 118 项通过；六模块 Enforcer 通过 | [详情](problems/ISSUE-014-security-maven-verification.md) · [验收记录](../verification/ISSUE-014-security-maven-verification.md) |
| ISSUE-015 | 后端漏洞扫描未形成有效报告 | 已完成 | 扫描脚本已修复；27 个高阈值 CVE 与四类分析缺口按用户决定标记为不需要处理，接受剩余风险并关闭 | [详情](problems/ISSUE-015-backend-dependency-audit.md) · [验证记录](../verification/ISSUE-015-backend-dependency-audit.md) |
| ISSUE-016 | UI 测试 YAML 解析器缺少严格字段校验 | 新增 | 从 ISSUE-004 延期项拆出，已登记，待解决方案 | [详情](problems/ISSUE-016-ui-fixture-yaml-validation.md) |
| ISSUE-017 | 支持股票参数的接口必填股票，其余保留原下载方式 | 处理中 | T14完整74任务/148查询及SQL归属通过；fina_mainbz按方案A采用默认分类及工程阈值，RANGE验收交ISSUE-026 | [详情](problems/ISSUE-017-stock-scoped-downloads.md) · [方案](proposals/ISSUE-017-required-stock-parameters.md) · [实施与验证](../verification/ISSUE-017-stock-scoped-downloads.md) |
| ISSUE-018 | 补齐按日期区间批量下载能力 | 处理中 | T01–T12 已完成；T14完整SINGLE74任务/148查询及四接口RANGE25任务通过，4 AVAILABLE/v2；G1–G6通过；其余30项证据未闭环，T14 BLOCKED，母issue未关闭 | [详情](problems/ISSUE-018-date-range-batch-downloads.md) · [方案草稿](proposals/ISSUE-018-date-range-batch-downloads.md) · [详细设计](../task-designs/ISSUE-018-design.md) · [任务看板](../task-handoffs/ISSUE-018/ISSUE-018-task-board.md) · [T14交接](../task-handoffs/ISSUE-018/ISSUE-018-T14-handoff.md) |
| ISSUE-019 | 确认三接口限量规则并补齐来源证据 | 已解决 | 方案A已确认；两轮SOURCE及78/160项回归通过，28项任务输入交ISSUE-026，生产准入保持 | [详情](problems/ISSUE-019-documented-range-limits.md) · [已确认方案](proposals/ISSUE-019-documented-range-limits.md) · [设计](../task-designs/ISSUE-019-design.md) · [执行看板](../task-handoffs/ISSUE-018/ISSUE-018-followups-task-board.md) |
| ISSUE-020 | 厘清主营业务构成默认分类与数量限制 | 已解决 | 方案A已确认；两股票来源与12项任务输入交ISSUE-026，78项Node/163项Maven通过 | [详情](problems/ISSUE-020-fina-mainbz-default-type.md) · [执行看板](../task-handoffs/ISSUE-018/ISSUE-018-followups-task-board.md) |
| ISSUE-021 | 验证交易所日历与交易日下载 | 已解决 | 三轮39项来源已验证；沪深/BJ与直接BSE结论明确，38项任务输入交ISSUE-026 | [详情](problems/ISSUE-021-exchange-calendar-semantics.md) · [执行看板](../task-handoffs/ISSUE-018/ISSUE-018-followups-task-board.md) |
| ISSUE-022 | 补齐日期口径与区间边界证据 | 已解决 | 五接口日期/边界来源补齐；两轮39请求、35项候选交ISSUE-026，回归与复审通过 | [详情](problems/ISSUE-022-range-date-axis-evidence.md) · [执行看板](../task-handoffs/ISSUE-018/ISSUE-018-followups-task-board.md) |
| ISSUE-023 | 补齐稀疏事件与记录修订证据 | 已解决 | 三轮39项来源/131请求，四接口事件与边界完成；35项候选交ISSUE-026，复审通过 | [详情](problems/ISSUE-023-sparse-event-range-evidence.md) · [执行看板](../task-handoffs/ISSUE-018/ISSUE-018-followups-task-board.md) |
| ISSUE-024 | 验证转融资与转融券历史范围 | 已完成 | 用户已批准方案A；39项来源及33项任务输入交付，TASK/SQL由ISSUE-026验收 | [详情](problems/ISSUE-024-slb-historical-range-evidence.md) · [执行看板](../task-handoffs/ISSUE-018/ISSUE-018-followups-task-board.md) |
| ISSUE-025 | 明确十一接口响应采集合同与来源 | 已完成 | 用户接受不完整；响应采集合同、135项来源及87组输入交付，78项回归通过；生产实现归ISSUE-026 | [详情](problems/ISSUE-025-unknown-extraction-contracts.md) · [执行看板](../task-handoffs/ISSUE-018/ISSUE-018-followups-task-board.md) |
| ISSUE-026 | 完成剩余区间任务验收与 T14 收尾 | 处理中 | 总任务；已拆为027～032，278项真实验证及原关闭条件保持 | [详情](problems/ISSUE-026-range-task-final-acceptance.md) · [执行看板](../task-handoffs/ISSUE-018/ISSUE-018-followups-task-board.md) |
| ISSUE-027 | 实现允许不完整的后端响应采集 | 已完成 | RESPONSE_ONLY、runner及历史摘要交付；287项后端/包与468项前端、独立审查通过 | [详情](problems/ISSUE-027-response-only-backend.md) · [子看板](../task-handoffs/ISSUE-026/ISSUE-026-task-board.md) |
| ISSUE-028 | 贯通响应采集接口合同与页面说明 | 已完成 | HTTP/页面合同交付，后端1144、前端524、受控浏览器66项及独立审查通过 | [详情](problems/ISSUE-028-response-only-contract-ui.md) · [子看板](../task-handoffs/ISSUE-026/ISSUE-026-task-board.md) |
| ISSUE-029 | 升级验收工具并补齐主营业务拆分来源 | 已完成 | schema2/严格身份及四SOURCE交付；42请求、完整拆分树与独立验收通过，生产及旧证据保持 | [详情](problems/ISSUE-029-range-evidence-and-split-source.md) · [子看板](../task-handoffs/ISSUE-026/ISSUE-026-task-board.md) |
| ISSUE-030 | 配置剩余接口候选策略与日历映射 | 已完成 | 30候选/BJ映射、272绑定与新两包交付；1133后端、524前端、104证据、106浏览器检查通过，正式证据保持4/30/6 | [详情](problems/ISSUE-030-range-candidate-policies.md) · [设计](../task-designs/ISSUE-030-design.md) · [交接](../task-handoffs/ISSUE-030-handoff.md) · [子看板](../task-handoffs/ISSUE-026/ISSUE-026-task-board.md) |
| ISSUE-031 | 完成278项真实区间任务与SQL验收 | 阻塞 | 82通过、1失败、195未运行；fina_indicator适配失败后撤回v3，待安全诊断与恢复合同 | [详情](problems/ISSUE-031-range-live-task-verification.md) · [设计](../task-designs/ISSUE-031-design.md) · [交接](../task-handoffs/ISSUE-031-handoff.md) · [子看板](../task-handoffs/ISSUE-026/ISSUE-026-task-board.md) |
| ISSUE-032 | 完成最终回归与母任务收尾 | 未开始 | 依赖ISSUE-031，待专属设计与实施 | [详情](problems/ISSUE-032-range-final-closure.md) · [子看板](../task-handoffs/ISSUE-026/ISSUE-026-task-board.md) |
