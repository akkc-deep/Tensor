# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/data-integrity-task-board.md`
- **Completed task:** `DATA-INTEGRITY-T11`
- **Next task:** `DATA-INTEGRITY-T12`
- **Design document:** `docs/task-designs/DATA-INTEGRITY-T12-design.md`
- **Expected next status:** `READY`；本交接写入并链接后执行 `NOT_STARTED -> READY`，不表示开始实现。

## Next Task

`DATA-INTEGRITY-T12`：报告详情、问题定位与再次检查。扩展既有 `/integrity/checks/:checkId` 入口，呈现保存的范围、版本、结论、结果单元和完整问题键，连接 T10 轮询/GET 重连；从旧报告复制条件，在创建页刷新当前能力并经明确确认生成新检查，旧报告保持不变。

验收包括独立刷新/深链接，执行状态与数据结论分开，精确计数及 null/0，部分执行和截断明确，问题日期/规则名版本/完整业务键/relatedDates/依据可查。日期筛选提示排除未知日期、重置可找回；切任务/切页/卸载拒绝旧响应，查询失败保留同条件快照，重连仅 GET。1440/1024/390 与键盘可操作且无页面横溢；专项、完整前端、构建及 API stub 浏览器通过，实际结果回填。真实后端闭环属于 T13。

## Dependencies

### DATA-INTEGRITY-T10

- **Artifact:** `control-plane/src/api/integrityChecks.js`、`control-plane/src/api/integrityDtos.js`、`control-plane/src/composables/useIntegrityCheck.js` 及同目录 spec、`control-plane/src/api/errors.js`；`docs/task-designs/DATA-INTEGRITY-T10-design.md`、`docs/verification/DATA-INTEGRITY-T10.md`。
- **Decision:** 六端点使用严格 DTO；Long 计数解析为 BigInt，比例保持十进制字符串，null 保持未知。详情/当前可见结果页由同一 flow 管理，活动任务完整轮次结束后 2 秒 GET 轮询，任一错误停轮询；重连只 GET。保存的 descriptor、规则和证据是旧报告权威。
- **Rationale:** 大数与未知统计不能舍入或伪造；迟到响应和网络故障不能覆盖当前任务或触发重复创建；当前规则不能改写历史口径。
- **Constraint:** 只读 refs 通过现有方法更新；`load/changeResults/reconnect/setActive/dispose` 保持原语义。历史摘要无 overallStatus，不添加 N+1 详情请求。问题查询使用现有 `listIntegrityIssues` 与 `validateIntegrityCriteria`，日期只按 issue.date 过滤；数量不转 Number。pendingSubmission 的原 ID/载荷不能被再次检查入口覆盖。
- **Usage:** 详情页创建一个 flow，合法路由 ID 调用 load，非法 ID 停止活动请求并隐藏旧报告，卸载 dispose；筛选分页使用 changeResults。新增独立 useIntegrityIssues 按需查询所选 resultId，无问题轮询。展示保存的报告字段，不 GET 当前 capability 补旧规则。
- **Readiness evidence:** 看板 T10 为 COMPLETED；Node24.15.0 专项 4 文件/66 项、完整前端 40 文件/663 项、生产构建与原生 ESM 导入通过；最终独立 Spec/Quality PASS。T11 完整前端 696 项也覆盖现有 T10 模块。

### DATA-INTEGRITY-T11

- **Artifact:** `control-plane/src/views/IntegrityView.vue`、`control-plane/src/views/IntegrityCheckView.vue`、`control-plane/src/components/integrity/IntegrityCheckForm.vue`、`IntegrityScopePreview.vue`、`IntegrityCheckHistory.vue`、`control-plane/src/utils/integrityForm.js` 及对应 spec；`control-plane/src/router/index.js`、`control-plane/src/layouts/AppLayout.vue`；`control-plane/e2e/integrity-checks.spec.js`、`control-plane/e2e/integrity-checks.fixtures.js`；`docs/task-designs/DATA-INTEGRITY-T11-design.md`、`docs/verification/DATA-INTEGRITY-T11.md`。
- **Decision:** 已提供创建/口径确认/历史与最小详情入口。能力 API 为检查全集，分类仅附加元数据；无 Token 不禁止本地检查。表单保留完整范围与失效 API，已知 API 按能力原顺序提交。响应不确定先查回原 ID，prepared 存储失败冻结原快照后重试。
- **Rationale:** 用户原选择与已发送身份必须稳定；来源/分类失败不影响恢复原请求，不能用默认全选或缩减范围替代用户意图。
- **Constraint:** 保留现有路由名、checkId prop、导航和视觉 token。T12 扩展合法详情的真实 GET 行为，需更新 T11 浏览器中最小入口零详情 GET 的断言。复制旧条件不能触发默认全选、复用旧 ID 或覆盖 pending；能力刷新后明确确认才提交。保留 choiceGeneration 和分类响应隔离、BigInt 历史分页。
- **Usage:** 详情“再次检查”跳转 `/integrity?fromCheckId=<uuid>`；创建页 GET 原 Detail 并复制保存 scope，再加载当前能力。pending 优先原请求恢复，复制错误显示具体 requestId，不回退到另一范围。扩展既有专用 fixtures/stub 套件，沿用真实 T10 DTO 校验和三宽度验证。
- **Readiness evidence:** 看板 T11 已 COMPLETED；Node24.15.0 完整前端 46 文件/696 项、Chromium API stub 14 项及生产构建通过，0 失败/跳过；1440/1024/390 无页面横溢且截图已检查。独立审查两项 P2 均 RED/GREEN 修复，最终 Spec/Quality PASS，无遗留可执行问题。

以上直接依赖的决策与约束无未解决冲突。详情独立使用 flow，不改变创建页恢复状态；再次检查只在明确入口复制范围并重新确认。

## Start Here

1. 完整阅读 `docs/task-designs/DATA-INTEGRITY-T12-design.md`，核对本板 T12；收到用户明确启动后才转 IN_PROGRESS。
2. 依次读取共享设计 `docs/task-designs/DATA-INTEGRITY-design.md` 第 5、6、8、9 节，`docs/contracts/integrity-check.schema.json`、`docs/contracts/integrity-check-examples.json`、`docs/contracts/openapi-v1.yaml`，再读上述 T10/T11 设计、模块、测试和验收。
3. 首个实现动作：在 `control-plane/src/utils/integrityReport.spec.js` 为最小未实现骨架编写行为 RED，断言 `9223372036854775807n` 精确显示、null 为“无法计算”、0n 为“0”、`0.999999` 为 `99.9999%`，观察断言失败后实现格式化；随后按完整设计连接报告页面。

继续使用 `.worktrees/data-integrity`、分支 `feat/data-integrity`。保留原 Studio/T01–T11 混合暂存基线，新增文件按精确路径加入 Git，不提交混合基线或合并主分支。

## Risks

Issue 的规则显示名须从同一保存 Result 的 ruleResults 按 ID/版本匹配，缺存档时显示准确 ID/版本，不查当前注册表。BigInt 不能直接传 Number 分页组件或 JSON.stringify；报告未知、不适用、未完成和已有 FAIL 必须分别保留。pending 未确认时阻止复制新范围。现有构建大 chunk 提示不在本项优化范围；stub 不能代替 T13 的真实 MySQL/fixture 验收。本次只准备 T12，不开始实现。
