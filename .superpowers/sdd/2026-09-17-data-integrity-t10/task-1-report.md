# Task 1 report: DTO 与六 API

## 实现

- 新增完整性 Capability、Receipt、TaskSummary、Detail、Result、Issue 与三种分页 DTO 的严格解析、递归复制冻结和精确 `BigInt` 计数。
- 新增不可变提交快照、检查 ID 与 tasks/results/issues 查询条件同步校验；恢复查询固定 `page=1&pageSize=20` 且不允许附带执行状态。
- 新增六个真实 Axios API 封装，校验状态码、请求 ID、Location、请求/响应身份、分页和结果所属检查。
- 新增五种完整性错误映射，并保持既有错误行为。

## RED 证据

1. `npm --prefix control-plane test -- src/api/integrityDtos.spec.js`
   - 首个可调用 stub 后测试按预期失败：`actualCount` 收到字符串 `"9223372036854775807"`，期望 `9223372036854775807n`。
2. `npm --prefix control-plane test -- src/api/integrityDtos.spec.js`
   - 扩展 DTO stub 后 6 项中 5 项按预期失败：精度、递归冻结、非法响应、不可变提交和 ID 规范化均尚未实现。
3. `npm --prefix control-plane test -- src/api/integrityChecks.spec.js`
   - 六 API stub 后 5/5 按预期失败，均为 `Integrity API is not implemented`。
4. `npm --prefix control-plane test -- src/api/api.spec.js`
   - 新增错误表后 1 项按预期失败：完整性错误被规范化为 `ClientError(INVALID_RESPONSE)`，而非 `ApiError`。
5. `npm --prefix control-plane test -- src/api/integrityDtos.spec.js`
   - 恢复筛选约束回归按预期失败：带 `submissionId` 的 page/status 组合尚未拒绝。

以上命令使用 `PATH=/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin:$PATH`。

## GREEN 证据

- 指定 API/DTO/错误命令：
  `npm --prefix control-plane test -- src/api/integrityDtos.spec.js src/api/integrityChecks.spec.js src/api/api.spec.js`
  - 3 个测试文件通过，25/25 测试通过。
- 与 composable 的集成命令：
  `npm --prefix control-plane test -- src/api/integrityDtos.spec.js src/api/integrityChecks.spec.js src/composables/useIntegrityCheck.spec.js src/api/api.spec.js`
  - 4 个测试文件通过，66/66 测试通过。
- `git diff --check` 通过，无空白错误。
- 生产模块 Node 动态导入通过，确认六 API 及两个同步 validator 均已导出。

## 关注点

- 本子任务未运行完整前端回归和构建；由主任务统一执行并记录最终结果。
- 未提交 commit；按任务要求仅暂存本子任务拥有的文件。
