### Task 1: DTO与六API

**Files:** 新增 `control-plane/src/api/integrityDtos.js`、`integrityChecks.js` 及对应spec；修改 `errors.js`、`api.spec.js`。
**Interfaces:** 完整遵循Spec第2–4节函数签名；额外导出 `validateIntegrityCriteria(kind, criteria)`（kind为tasks/results/issues）、`validateIntegrityCheckId(value)` 供composable同步验证，均返回规范化值或TypeError。

- [ ] 从Spec Tests首个RED开始：先建立可调用 `parseIntegrityResult(value) { return value }`，用T09示例断言 `actualCount === 9223372036854775807n`、null保留及数字token拒绝，确认行为失败。
- [ ] 实现Spec第3节所有固定DTO，递归复制冻结；以15个公开例子与非法字段/枚举/日期/精度变异测试覆盖。
- [ ] 写真实Axios adapter测试，确认六路径、筛选、状态码、requestId/Location/身份错误、分页及错误映射失败，再实现API及5种错误。
- [ ] 执行 `npm --prefix control-plane test -- src/api/integrityDtos.spec.js src/api/integrityChecks.spec.js src/api/api.spec.js`；记录RED/GREEN证据，自查并加入Git。
