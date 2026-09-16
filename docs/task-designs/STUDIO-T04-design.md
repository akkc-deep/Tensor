# STUDIO-T04：下载提交与结果确认

## Goal

Studio 表单可靠创建真实下载任务，用户能区分正在提交、已接收、明确拒绝和结果不确定，并在刷新后确认原任务。任务身份和状态以 `docs/task-handoffs/studio-frontend-task-board.md` 为准。

## Scope

- 迁移提交按钮、锁定、受理反馈、提交错误、原提交恢复和本地存储异常；仅验收 PC 桌面。
- 复用 T03 参数校验和规范化快照、现有任务 API、sessionStorage 提交记录及列表受理回调。
- 本轮完成 T04；最近任务迁移、已创建任务重试／恢复、详情弹窗和后继任务准备不在本轮范围。

## Approach

- `DownloadAction.vue` 改用原生按钮和现有 Download 图标，增加 `mode`、`recovering` 属性。空闲按 SINGLE／RANGE 显示“开始下载”／“开始批量下载”，请求中显示“正在创建…”或“正在查找…”。禁用条件仍为 disabled 或 submitting，保持原 submit 事件及 button 类型。
- `DownloadView.vue` 底部对齐 Demo：分隔线上间距 29px、上内边距 23px、全宽 40px 主按钮、按钮下 13px 放“接收后可继续提交其他任务”；替换演示说明，不引入 Demo 代码。
- 移除独立“任务接收”WorkbenchPanel 和空闲大占位。在按钮下用现有 AsyncStatePanel 呈现紧凑反馈：图标与 14px 标题同排、12px 说明、原生次按钮自动换行，保留 status／alert 和请求 ID。反馈无固定最小高度；1024／1280／1440 下长 ID、参数、错误可换行。
- SUBMITTING／RECOVERING 时禁用来源、目录、模式、参数、提交与恢复入口，并展示 `pendingSubmission` 的原来源、接口、模式、提交标识和排序参数。UNCERTAIN 时也展示同一快照，允许编辑草稿但禁止新提交，明确“请先确认原任务；重新确认始终使用以下原参数”。只提供“重新查找”和“使用原参数重新确认”，不自动补发 POST。
- ACCEPTED 保留真实 taskId 和“查看任务”路由，说明接收仅确认任务身份，进度以近期任务或详情为准；通过现有 onAccepted 刷新列表。冲突恢复保留核对参数提示。清理本地记录失败不遮盖已受理事实，可重新清除。
- REJECTED 保留字段错误、请求 ID 和清理失败重试；提供下一步说明：参数错误修改后重交、队列满稍后重交、来源／能力／定义错误重新选择接口获取最新配置。
- READ 失败禁止新提交并提供重新读取；WRITE 失败不发送 POST，说明允许存储后从主按钮重试；CORRUPT 提醒先核对近期任务并明确清除只作用于当前标签页；REMOVE 失败提供重新清除，不掩盖原任务状态。
- `useDownloadFlow` 和 `downloadTaskSubmission` 保持现有单一提交状态机：持久化白名单冻结请求后 POST；明确首次拒绝才清理；网络错误、异常响应、空查询或重放拒绝保留原提交标识；刷新先 GET 查询原任务，无须元数据；卸载后的旧响应不能变更存储或刷新列表。除验证发现的范围内缺陷外不改业务机制。

## Files

- 修改 `control-plane/src/components/download/DownloadAction.vue`、`control-plane/src/views/DownloadView.vue` 和对应测试；移除 `src/style.css` 中仅服务旧结果面板的样式。
- 更新受影响布局测试、旧 DownloadResult 测试及 E2E 提交按钮／反馈定位，保留业务断言。
- 新增 `control-plane/e2e/studio-submission.spec.js`：HTTP 受控提交、恢复、存储异常与桌面对照。
- 新增本文、`docs/verification/STUDIO-T04.md` 和桌面截图；回填任务板并将新增文件加入 Git。

## Tests

在 `control-plane` 使用 Node 24.15.0：

1. `npm test -- src/components/download/DownloadAction.spec.js src/views/DownloadView.spec.js src/composables/useDownloadFlow.spec.js src/composables/downloadTaskFlow.integration.spec.js`：先验证新增等待快照及恢复／异常行为测试失败，再以最小实现通过。覆盖双击锁定、原请求重放、刷新只查原任务、受理刷新列表和存储失败不丢原状态。
2. `npm test`、`npm run build`：全部单元／集成和正式／Demo 构建通过。
3. 本地 Vite 4174，设置 `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 TENSOR_UI_BASE_URL=http://127.0.0.1:4174`，运行 `npm run test:e2e -- studio-submission.spec.js studio-form.spec.js studio-catalog.spec.js studio-shell.spec.js ui-redesign.spec.js download-tasks.spec.js stock-download-parameters.spec.js`。验证单次／批量 POST 内容、慢请求锁定、重复点击、断网、刷新、空查询后原参数重放、拒绝和存储异常。
4. 1024／1280／1440 同视口同模式对照 Demo 按钮宽高／颜色／字体／间距，检查异常反馈无页面横向溢出和键盘可操作，保存并查看代表性截图。
5. `node --test e2e/tushare-range-evidence.test.js`，修改 E2E 的 Node 语法检查，impeccable detector、`git diff --check`。

## Acceptance

- 单次／批量任务均经现有真实 API 边界提交；规范化参数不变，等待期间不能重复创建或改动原快照。
- 接收反馈显示任务身份并刷新列表；错误可理解且有操作指引，不把接收误报为下载完成。
- 网络不确定、刷新、空查询后重放都沿用原 submissionId 和参数；损坏或不可读记录不会被新请求覆盖。
- PC Studio 提交操作与 Demo 对照完成，扩展状态采用相同视觉语言，相关回归通过，新增文件 Git 跟踪。

## Risks

- Demo 没有持久化受理或异常反馈，新增反馈按现有 Studio 字体、间距和颜色设计，不模拟任务结果。
- 浏览器测试使用受控 HTTP 响应验证真实前端 API 接线，不代表真实上游账号采集验收；在线后端套件仅同步受影响定位并注明边界。
- 当前工作区已有 T01–T03 暂存改动，保留这些改动，本轮不创建包含它们的提交。
