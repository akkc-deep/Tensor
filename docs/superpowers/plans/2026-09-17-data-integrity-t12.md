# DATA-INTEGRITY-T12 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 展示持久报告和完整问题定位，复制原范围并重新确认当前能力后生成新报告。

**Architecture:** 复用 T10 精确 API/生命周期和 T11 创建/历史。详情协调 Summary、Results、Issues、BigInt Pagination，问题通过独立按需 GET composable；创建页处理 fromCheckId 并优先恢复 pending。

**Tech Stack:** Vue 3、Element Plus icons、Vitest、Playwright、Node24.15.0。

**Spec:** `docs/task-designs/DATA-INTEGRITY-T12-design.md`（全部章节作为本计划的完整行为合同）。

## Global Constraints

- 只在 `.worktrees/data-integrity` 工作，保留 Studio/T01–T11 混合暂存基线；按精确路径 git add，不创建混合提交，不合并。
- 最小实现；不改后端/API/来源规则，真实 fixture 闭环留给 T13。
- Long 计数使用 BigInt，比例使用十进制字符串，null 不等于0；不产生跨接口百分比。
- 历史只用存档 descriptor/ruleResults/证据，不请求当前能力补旧报告。COMPLETED不意味着PASS。
- 当前视觉沿用 Tensor tokens、顶部导航、T11 布局，状态图标加文字；1440/1024/390无文档横溢。
- 必须测试驱动。首个RED遵循设计的可调用空骨架，而非导入错误。Node命令前设置 PATH=/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin:$PATH。

### Task 1: 报告组件、查询生命周期与复制条件

**Files:** 新增 `control-plane/src/utils/integrityReport.js` 及 spec、`composables/useIntegrityIssues.js` 及 spec；新增 `components/integrity/IntegritySummary.vue`、`IntegrityResultsTable.vue`、`IntegrityIssuesTable.vue`、`IntegrityPagination.vue` 及各自 spec；修改 `views/IntegrityCheckView.vue`/spec、`views/IntegrityView.vue`/spec。确有复用需要可抽取小型状态/证据展示组件，但不做无关重构。

**Interfaces:** 消费现有 `useIntegrityCheck()` 的 readonly refs 和 `load/changeResults/reconnect/setActive/dispose`；消费 `listIntegrityIssues`、`validateIntegrityCriteria`；输出设计第3节三个纯格式化函数、第4节 `useIntegrityIssues()`，四组件props/events完全依设计；路由名与 checkId prop保持不变。

- [x] 阅读完整专属设计、共享设计5/6/8/9节，现有精确DTO/API/composable、创建页、合同示例与T11视觉。
- [x] 首RED：最小空骨架下断言 `formatIntegrityCount(9223372036854775807n)==='9223372036854775807'`、null为“无法计算”、0n为“0”、`formatIntegrityRate('0.999999')==='99.9999%'`；记录行为失败，再实现十进制位移/直接toString。
- [x] 按设计逐组先写行为测试，再实现问题取消/generation与同条件保留、精确分页、报告渲染、保存规则名回退、主日期过滤提示及重置。对完整键测试 BigInt/精确DECIMAL/null/布尔和 relatedDates。
- [x] 完成详情load/错误/GET重连/结果筛选分页/聚焦问题区域；同页刷新保留，不同checkId清理，非法ID不GET，卸载取消。覆盖COMPLETED+FAIL、全UNKNOWN/N/A、ERROR/NOT_RUN/截断、迟到成功和错误、终态停止与2秒轮询。
- [x] 完成创建页 fromCheckId 复制 scope，能力重新获取但不默认全选、不复用ID；pending优先、查询失败/来源消失/失效API/超限明确，修改路由或用户编辑后迟到查询不得覆盖。通过真实flow与API边界测试新ID和原报告不变。
- [x] 运行上述文件专项，再运行完整前端，保存准确RED/GREEN证据并自查。按精确文件纳入Git，不提交。控制器负责浏览器测试、截图与文档，勿修改 e2e 或 docs。

### Task 2: 浏览器集成与交付（控制器执行）

**Files:** 扩展 `control-plane/e2e/integrity-checks.fixtures.js`、`integrity-checks.spec.js`；新增 `docs/verification/DATA-INTEGRITY-T12.md`、截图；回填本计划/看板，准备T13设计与交接。

**Interfaces:** 使用现有六HTTP端点和完整DTO；fixture保留T11的请求ID/Location和创建行为，补充真实合同形状的报告、结果和问题。Task1页面名、筛选label、按钮文案依完整设计。

- [x] 先以深链接报告断言观察浏览器RED，再扩展stub：保存规则/大数/95%/null/完整键/未知日期，支持服务端结果与问题过滤分页并记录请求。
- [x] 验证报告刷新、保存口径、问题主日期过滤/重置、结果筛选/翻页、错误保留和GET重连、轮询停止、重新检查新ID/旧报告不变、pending优先、空/UNKNOWN/N/A/中断与长值。
- [x] 对1440/1024/390检查键盘操作与document宽度，批量截图并实际打开检查；只在发现缺陷后做一批修复并确认。
- [x] 运行完整前端、生产构建、全部integrity浏览器stub；独立规格/质量复审，必要修复后复查覆盖测试。
- [x] 写T12实际验收，记录COMPLETED；完整设计并链接T13后才准备其next-task交接/READY，不开始T13。新增文件纳入Git，保留隔离工作区。

## Verification commands

```sh
npm --prefix control-plane test
npm --prefix control-plane run build
PLAYWRIGHT_BASE_URL=http://127.0.0.1:4178 npm --prefix control-plane run test:e2e -- e2e/integrity-checks.spec.js
```

基线46文件696项通过。浏览器stub不冒充T13真实MySQL闭环。完整设计已由前任务完成，本次用户明确启动T12，无需重复设计确认。
