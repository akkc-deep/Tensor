# ISSUE-009：daily 与 balancesheet 性能尚未验证

## 当前阶段与授权

待后续处理。用户明确要求：“那也先跳过吧，可以记录一个issue，直接标记完成”。据此将 M14-T06 在权威看板上标记为 `COMPLETED`，其含义为本轮跳过并转入 issue 跟踪，不表示性能测试已经执行或通过。

本问题保持未解决。M14-T06 未建立设计文档或交接文件，未创建性能脚本、页面测试和性能报告，也未启动测试 JVM、数据库或合成上游。

## 已知事实与剩余范围

原任务卡与 PRD/TRD 的性能要求仍需验证：

- `daily` 行数场景与 `balancesheet` 的 152 列宽表场景，分别覆盖冷/热查询和重复 Upsert。
- 50 条记录查询 P95 ≤ 2 秒；每组 10 次预热、100 次测量，记录 P50/P95/最大响应时间。
- 用户点击后 300ms 内出现可访问的加载反馈，单次最多返回 100 行，页面只渲染当前页，宽表可操作。
- 核对 COUNT/分页查询的核心字段索引与 MySQL EXPLAIN，记录数据量、机器环境、CPU、堆和连接池资源占用。

此前仅建议 daily 10 万行、balancesheet 1 万行合成数据，用户没有确认；该数字不是冻结合同或实际数据量。现有功能验收和 M14-T09 的真实接口结果不能代替性能证据。

## 后续处理与关闭条件

1. 恢复本问题时先确认测试数据量、数据分布、冷/热定义、运行环境和指标采集方式，完成具体设计与实施计划。
2. 按原任务卡准备 `scripts/performance/verify-query-p95.sh`、`control-plane/e2e/loading-feedback.spec.js` 和 `docs/verification/M14-T06-performance.md`；这些路径当前仅为预定产物，不是已创建的证据。
3. 使用独立测试环境实施和实测，记录可复核的原始计时汇总、页面结果、执行计划及资源占用。失败按问题流程处理；通过全部性能要求并完成资源清理后才能关闭本 issue。

本次看板收尾不满足项目性能或发布门禁，不补造通过记录。M14-T05 的 9 项真实接口缺口继续由 ISSUE-008 独立跟踪。

## 来源

- [权威看板](../../task-handoffs/tensor-v1-task-board.md)：M14-T06 的用户跳过决定与状态依据。
- [M14 任务卡](../../superpowers/plans/tensor-modules/M14-integration-release.md)：Task M14-T06 的文件、测量流程和门槛。
- [PRD](../../design/Tensor_多源证券数据平台_PRD_v1.0.md)：10.1 性能。
- [TRD](../../design/Tensor_多源证券数据平台_TRD_v1.0.md)：18 性能与容量。
