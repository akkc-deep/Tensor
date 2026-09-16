# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/studio-frontend-task-board.md`。
- **Completed task:** `STUDIO-T09`，已为 `COMPLETED`。
- **Next task:** `STUDIO-T10`，按任务板 Order 10 选择。
- **Design document:** `docs/task-designs/STUDIO-T10-design.md`。
- **Expected next status:** `READY`；本交接写入并关联后执行 `NOT_STARTED -> READY`，尚未开始实施。

## Next Task

STUDIO-T10「整体对照、回归与旧代码清理」：完成正式 Studio 的跨页面视觉和业务验收，移除设计中明确列出的无生产引用组件及样式，更新使用说明。验收覆盖单次／批量创建、进度、重试／恢复、数据查询分页、主题与缓存，三个主页面及详情在 1024／1280／1440px 的最终对照。检查正式入口无 Demo 数据依赖、删除引用证据、单元／集成／浏览器及构建结果；新文件加入 Git并记录真实集成未执行边界。

## Dependencies

### STUDIO-T07

- **Artifact:** `docs/task-designs/STUDIO-T07-design.md`、`docs/verification/STUDIO-T07.md`；实现入口 `control-plane/src/views/DownloadView.vue`、`control-plane/src/views/DownloadTaskView.vue`、`control-plane/src/composables/useDownloadTask.js`、`control-plane/src/layouts/AppLayout.vue`，回归 `control-plane/e2e/studio-recovery.spec.js`。
- **Decision:** 列表快捷操作先读取详情，依照 canRetry／canResume 和精确 expectedVersion 请求；同应用实例共享任务锁；POST 结束通知列表与详情刷新；不确定结果仅重查，不自动重放。
- **Rationale:** 避免陈旧权限、跨入口重复操作及关闭弹窗后的状态不一致，保持服务端成功批次事实。
- **Constraint:** 保留 T01–T06 的路由缓存、原提交标识恢复、任务状态分组与服务端计数、命名视图详情、BigInt 和完整性语义；T05 前后端 statusGroup 契约一起发布。
- **Usage:** 构建批量失败／重试／中断恢复的跨页面旅程，消费现有真实 API 边界；清理不得改任务状态机。
- **Readiness evidence:** 任务板为 COMPLETED；T07 记录 607 项单元／集成、105 个不同浏览器场景和构建通过，操作共享锁及异常反馈已复核。

### STUDIO-T09

- **Artifact:** `docs/task-designs/STUDIO-T09-design.md`、`docs/verification/STUDIO-T09.md`、`docs/verification/studio-t09/`；`control-plane/src/views/DatasetView.vue`、`control-plane/src/components/dataset/DatasetTable.vue`、`control-plane/src/components/dataset/DatasetPagination.vue`，回归 `control-plane/e2e/studio-datasets.spec.js`、`control-plane/e2e/studio-table.spec.js`。
- **Decision:** 数据集与筛选来自真实元数据；分页复用已提交条件；完整定义列加来源列，不截断服务端页；表格内部双向滚动并保持精确数值／日期／单位。所有非空长文本可聚焦查看全文及键盘滚动，全局 Esc 关闭提示。
- **Rationale:** 使真实宽表和大量记录可用，避免草稿污染查询或丢失数值／来源信息。
- **Constraint:** 不复制 Demo 的 24 行／8 列限制，不改格式化和查询契约；保持 T08 的重置、竞态、失败重试与页面缓存，保留三种 PC 宽度和主题可读性。
- **Usage:** 在跨页面旅程验证下载后数据读取、分页、草稿与主题往返；引用三种宽度及宽表／长文本基线。
- **Readiness evidence:** T09 已记录 COMPLETED；最终 37 文件 609 项单元／集成、71 项浏览器回归及构建通过，10 张截图已跟踪。独立审查三项长文本问题经真实 Chromium 复核全部 resolved。

两组输入均以真实 API 为事实来源，保留存储／查询快照和 PC 范围；未发现相互冲突的决策或约束。

## Start Here

1. `docs/task-designs/STUDIO-T10-design.md`。
2. `docs/task-handoffs/studio-frontend-task-board.md`，及已关联的 T01–T09 设计、验证记录。
3. `control-plane/e2e/ui-redesign.spec.js`、`control-plane/e2e/ui-redesign.fixtures.js`、`control-plane/e2e/download-tasks.spec.js`、`control-plane/e2e/dataset-query.spec.js`。
4. `control-plane/package.json`、`control-plane/playwright.ui.config.js`、`control-plane/vite.config.js`、`control-plane/src/demos/README.md`。

收到 T10 启动请求后，按设计在 `control-plane/e2e/studio-acceptance.spec.js` 实现第一条连续旅程（单次日线提交→列表／详情→数据查询分页→主题往返），运行该场景并检查真实请求记录。随后完成第二条旅程与有引用证据的清理；无需重新选择设计方案。

## Risks

- 当前分支 `feat/studio-frontend` 已暂存 T01–T09 改动；保留全部既有工作，不擅自提交、回滚或推送。
- 设计中列出的死组件删除前仍需复查引用，混合 CSS 规则保留活跃分支。`FieldError` 有生产调用，不能随旧 MetadataField 删除。
- Demo 和 live Demo 保留为独立入口；它们被构建不等于正式应用依赖模拟数据。
- HTTP 夹具不能替代真实 MySQL 或 Tushare 验收；T09 未运行这些外部集成。既有 bundle 提示和开发 ResizeObserver 日志需按实际证据记录，不伪称消除。
