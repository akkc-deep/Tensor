### Task 1: 报告组件、查询生命周期与复制条件

**Files:** 新增 `control-plane/src/utils/integrityReport.js` 及 spec、`composables/useIntegrityIssues.js` 及 spec；新增 `components/integrity/IntegritySummary.vue`、`IntegrityResultsTable.vue`、`IntegrityIssuesTable.vue`、`IntegrityPagination.vue` 及各自 spec；修改 `views/IntegrityCheckView.vue`/spec、`views/IntegrityView.vue`/spec。确有复用需要可抽取小型状态/证据展示组件，但不做无关重构。

**Interfaces:** 消费现有 `useIntegrityCheck()` 的 readonly refs 和 `load/changeResults/reconnect/setActive/dispose`；消费 `listIntegrityIssues`、`validateIntegrityCriteria`；输出设计第3节三个纯格式化函数、第4节 `useIntegrityIssues()`，四组件props/events完全依设计；路由名与 checkId prop保持不变。

- [ ] 阅读完整专属设计、共享设计5/6/8/9节，现有精确DTO/API/composable、创建页、合同示例与T11视觉。
- [ ] 首RED：最小空骨架下断言 `formatIntegrityCount(9223372036854775807n)==='9223372036854775807'`、null为“无法计算”、0n为“0”、`formatIntegrityRate('0.999999')==='99.9999%'`；记录行为失败，再实现十进制位移/直接toString。
- [ ] 按设计逐组先写行为测试，再实现问题取消/generation与同条件保留、精确分页、报告渲染、保存规则名回退、主日期过滤提示及重置。对完整键测试 BigInt/精确DECIMAL/null/布尔和 relatedDates。
- [ ] 完成详情load/错误/GET重连/结果筛选分页/聚焦问题区域；同页刷新保留，不同checkId清理，非法ID不GET，卸载取消。覆盖COMPLETED+FAIL、全UNKNOWN/N/A、ERROR/NOT_RUN/截断、迟到成功和错误、终态停止与2秒轮询。
- [ ] 完成创建页 fromCheckId 复制 scope，能力重新获取但不默认全选、不复用ID；pending优先、查询失败/来源消失/失效API/超限明确，修改路由或用户编辑后迟到查询不得覆盖。通过真实flow与API边界测试新ID和原报告不变。
- [ ] 运行上述文件专项，再运行完整前端，保存准确RED/GREEN证据并自查。按精确文件纳入Git，不提交。控制器负责浏览器测试、截图与文档，勿修改 e2e 或 docs。
