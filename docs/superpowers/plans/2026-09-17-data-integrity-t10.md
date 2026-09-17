# DATA-INTEGRITY-T10 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 实现完整性检查六API、精确DTO、能力确认、幂等恢复和可停止GET轮询。
**Architecture:** DTO纯解析；API复用HTTP及无损JSON；composable管理不可变快照和生命周期。
**Tech Stack:** Node 24.15.0、Vue 3、Axios、Vitest；不新增依赖。
**Spec:** `docs/task-designs/DATA-INTEGRITY-T10-design.md`

## Global Constraints

- 仅在 `.worktrees/data-integrity` 实施，保留既有暂存基线；新增文件按精确路径加入Git，不提交混合基线或合并。
- 不实现T11/T12页面。字段、状态、错误及原请求以T09公开合同为准。
- 计数字符串转BigInt，null保持null；原数组顺序及apiNames省略必须保留。
- 所有POST来自显式submit/resend；响应不确定先GET原submissionId，重连只GET。
- 当前有效轮次完成后2000ms轮询，终态/离开/卸载停止，错误保留数据和requestId。

### Task 1: DTO与六API

**Files:** 新增 `control-plane/src/api/integrityDtos.js`、`integrityChecks.js` 及对应spec；修改 `errors.js`、`api.spec.js`。
**Interfaces:** 完整遵循Spec第2–4节函数签名；额外导出 `validateIntegrityCriteria(kind, criteria)`（kind为tasks/results/issues）、`validateIntegrityCheckId(value)` 供composable同步验证，均返回规范化值或TypeError。

- [x] 从Spec Tests首个RED开始：先建立可调用 `parseIntegrityResult(value) { return value }`，用T09示例断言 `actualCount === 9223372036854775807n`、null保留及数字token拒绝，确认行为失败。
- [x] 实现Spec第3节所有固定DTO，递归复制冻结；以15个公开例子与非法字段/枚举/日期/精度变异测试覆盖。
- [x] 写真实Axios adapter测试，确认六路径、筛选、状态码、requestId/Location/身份错误、分页及错误映射失败，再实现API及5种错误。
- [x] 执行 `npm --prefix control-plane test -- src/api/integrityDtos.spec.js src/api/integrityChecks.spec.js src/api/api.spec.js`；记录RED/GREEN证据，自查并加入Git。

### Task 2: 创建恢复与轮询状态

**Files:** 新增 `control-plane/src/composables/useIntegrityCheck.js` 及对应spec。
**Interfaces:** 消费Task1的六API、createIntegritySubmission及两个参数验证器；产出Spec第4–5节全部refs与方法。状态暴露只读refs。

- [x] 写双击、冻结请求、超时查回、空页同ID重发、存储失败和能力变化测试；空实现先断言失败。
- [x] 实现独立缓存key、generation和AbortController，按Spec的状态转换保留最初提交错误并额外暴露recoveryError区分GET失败。
- [x] 写fake timers/deferred测试：立即GET、settle后2000ms、无重叠、隐藏结果、切任务/页、旧错误/finally、失败保留、GET重连、终态/卸载。
- [x] 实现按请求槽复用的轮询；执行 `npm --prefix control-plane test -- src/composables/useIntegrityCheck.spec.js`，记录证据并加入Git。

### Task 3: 集成验收与交接

**Files:** `docs/verification/DATA-INTEGRITY-T10.md`、任务看板，T11专属设计及交接。
**Interfaces:** 消费前两项实际测试结果；向T11提供既定公开方法和限制。

- [x] 独立审查任务实现是否满足Spec与代码质量，修复实质问题并执行对应测试。
- [x] 在Node24执行专项、完整前端测试和构建；记录数量与实际结果。
- [x] 记录T10 COMPLETED后，按Order选择T11；使用designing-task-contracts完成专属设计，回填后写next-task交接并置READY。
- [x] 精确路径git add，git diff --check，保留隔离工作区和混合基线。
