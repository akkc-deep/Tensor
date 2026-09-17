# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/data-integrity-task-board.md`
- **Completed task:** `DATA-INTEGRITY-T09`
- **Next task:** `DATA-INTEGRITY-T10`
- **Design document:** `docs/task-designs/DATA-INTEGRITY-T10-design.md`
- **Expected next status:** `READY`；本交接写入、链接后执行NOT_STARTED→READY，不表示开始实施。

## Next Task

`DATA-INTEGRITY-T10`：前端请求、精确 DTO 与检查状态。提供六个API封装、不可变提交/恢复及详情与可见结果页轮询，供后续页面使用；本项不添加页面布局、路由或后端功能。

验收要求：精确计数/null及所有状态正确解析；能力变更须刷新并确认；重复点击不重复提交，响应不确定先按submissionId查回，重发保持原ID和载荷，明确新检查才新ID。运行中2秒轮询不重叠，终态/路由切换/卸载停止或取消，checkId/generation屏蔽旧响应；临时查询失败保留数据/requestId并停轮询，显式重连只GET。专项、完整前端测试及构建通过，新文件加入Git。

## Dependencies

### DATA-INTEGRITY-T09

- **Artifact:** `docs/contracts/openapi-v1.yaml`、`docs/contracts/integrity-check.schema.json`、`docs/contracts/integrity-check-examples.json`、`docs/contracts/error-codes.md`；行为解释见 `docs/task-designs/DATA-INTEGRITY-T09-design.md`，验收见 `docs/verification/DATA-INTEGRITY-T09.md`。
- **Decision:** 六端点分开本地检查能力与下载可用性；POST原请求重放先于当前能力检查；历史直接读取保存报告；计数为字符串、未知为null、比例为六位字符串；任务执行状态与数据结论独立。
- **Rationale:** 无Token仍可本地检查；旧规则、原范围与证据可追溯；响应丢失后能找回同一检查，不丢失Long精度或把未知当0。
- **Constraint:** 三列表全部筛选及1..100任意pageSize必须保持。TaskSummary无overallStatus，不能伪造结论或自动N+1查询。downloadAvailability.rowLimit仍遵循原下载整数合同。apiNames省略和数组顺序参与重放身份，不做前端自动规范化。INTEGRITY_CHECK_NOT_FOUND为404，其他四种完整性错误的状态/retryable以错误文档为准。
- **Usage:** 从schema和15个示例实现纯DTO解析，以HTTP合同实现六封装和错误识别；用真实Axios adapter、假计时器和deferred Promise验证提交恢复/轮询，不向后端增加新接口。实际Controller通过Service查询已存报告，前端无需依赖其内部架构。
- **Readiness evidence:** 看板已记录T09 COMPLETED。最终HTTP/合同专项113项通过，包含真实MySQL8.4.6的ProductionApplicationContextIT；完整Java1457项、前端609项及构建通过，0失败/错误/跳过；架构修复后独立复审无Critical/Important遗留项。clean-main发布合同门禁未执行，由T13承担，不影响本项消费已验证HTTP合同。

### 既有前端HTTP、下载精度与恢复基础

- **Artifact:** `control-plane/src/api/http.js`、`errors.js`、`downloadTasks.js`、`downloadTaskDtos.js`、`utils/downloadTaskSubmission.js`、`composables/useDownloadTask.js` 及对应spec。
- **Decision:** 复用现有Axios实例、X-Request-Id、安全错误、`parseTaskJson`和嵌入下载能力解析；提交缓存使用独立integrity key，方法/状态固定在T10设计中。
- **Rationale:** 现有无损JSON解析可保护下载rowLimit与业务键整数，统一HTTP边界避免重复维护网络错误和泄露原始响应。
- **Constraint:** 不复制下载的SUCCEEDED/PARTIAL_FAILED枚举、20/50/100白名单或自动退避轮询；检查查询失败明确停止，GET重连不POST。`errors.js`尚缺五种INTEGRITY错误，本项需补充并回归既有映射。
- **Usage:** 参考现有adapter测试、sessionStorage快照和generation隔离模式；只新增三个完整性模块及测试，直接扩展errors映射，不重构下载流程。
- **Readiness evidence:** T09最终完整回归已执行当前前端37文件/609项测试及生产构建，全部通过。

上述直接输入与共享设计的精度、原请求、轮询约束一致，无未解决冲突。

## Start Here

1. 完整阅读 `docs/task-designs/DATA-INTEGRITY-T10-design.md`，再核对本看板T10状态；仅收到用户显式启动后转IN_PROGRESS。
2. 依次读共享设计 `docs/task-designs/DATA-INTEGRITY-design.md` 第2、5、8、9节及上述T09公开schema/examples/OpenAPI/error-codes和验收记录。
3. 依次读 `control-plane/src/api/http.js`、`downloadTasks.js`、`downloadTaskDtos.js`、`utils/downloadTaskSubmission.js`、`composables/useDownloadTask.js` 及其测试。
4. 首个实施动作：在 `control-plane/src/api/integrityDtos.spec.js` 用T09示例编写Long.MAX_VALUE字符串转BigInt、null保留及数字token拒绝的测试，建立最小可调用导出并确认断言RED，然后实现DTO解析；不把模块导入错误当成行为失败证据。

工作区固定为 `.worktrees/data-integrity`，分支 `feat/data-integrity`。保留当前Studio/T01–T09混合暂存基线；按精确路径加入新增文件，不提交混合基线或合并主分支。T10专属设计已链接，本交接不授权启动实现。

## Risks

- 能力hash更新不能改写未确认提交；旧ID恢复与GET历史不得依赖当前Token/规则。
- AbortSignal取消经HTTP错误规范化后仍可能呈现普通ClientError，必须用signal与generation先剔除过期请求。
- 超过JS安全整数的计数、嵌入下载整数和businessKey各有编码边界，不使用JSON.parse默认Number或格式化字符串代替原值。
- 目前没有完整性页面，本项adapter/状态单测不能作为T13真实浏览器闭环证据；T10完成后再按看板准备T11。
