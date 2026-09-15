# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/ISSUE-018/ISSUE-018-task-board.md`。
- **Completed task:** `ISSUE-018-T10`，已先记录 COMPLETED、结果、最终验证及审查关闭证据。
- **Next task:** `ISSUE-018-T11`，按预定义 Order 选中的下一项。
- **Design document:** `docs/task-designs/ISSUE-018-T11-design.md`，全文复核及独立就绪审查通过，已先回填看板 Design document。
- **Expected next status:** `READY`；本交接写入、核对并链接后执行 `NOT_STARTED -> READY`，尚未启动 T11 实现。

## Next Task

`ISSUE-018-T11`：任务详情、轮询与手动操作。

实现 `/downloads/tasks/:taskId`、useDownloadTask、DownloadTaskView 和叶子批次表；扩展任务 API/DTO，接通列表/接收面板与返回导航。详情不依赖元数据、sessionStorage或T10的内存receipt。保留当前视觉、下载配置缓存和T10提交恢复。

验收：同URL可刷新查询、关闭重开后可从列表进入同任务；任务/批次日期、动态批数、失败与待执行区间、尝试次数和完整整数准确。运行时2秒轮询，终态停止，隐藏暂停/恢复补查，失败5/10/30秒退避，旧ID/页/卸载响应隔离且请求不重叠。retry/resume分别依服务端许可，发送点击时原bigint版本的JSON数值，重复操作互斥；202、409和不确定结果后查事实、不自动重发POST。组件/状态、受控浏览器和build通过并记录证据。

## Dependencies

### ISSUE-018-T09

- **Artifact:** `docs/task-designs/ISSUE-018-T09-design.md`；`docs/contracts/download-task.schema.json`、`docs/contracts/download-task-examples.json`、`docs/contracts/openapi-v1.yaml`、`docs/contracts/error-codes.md`；HTTP `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadTaskController.java`；DTO `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadTaskResponse.java`、`DownloadBatchResponse.java`、`DownloadTaskReceipt.java`、`DownloadTaskControlRequest.java`、`DownloadTaskPage.java`；真实合同测试 `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadTaskControllerIT.java`、`DownloadTaskContractTest.java`、`DownloadTaskRequestBindingTest.java`。
- **Decision:** 详情/批次GET为200，后台失败仍以数据返回；任务查询不依赖插件可用性。批次默认叶子、按batchKey排序、超尾页不折回。retry/resume只收`{expectedVersion}`整数token，成功202+同任务Location+Receipt；回执允许已推进的状态，createdAt仍为原创建时间。controls仅为瞬时提示，操作完整复验。
- **Rationale:** 数据库是任务事实来源；查询故障不是后台任务失败，202也不是下载成功。原版本用于发现其他页面/worker已推进，不能换成新查版本掩盖冲突。跨GET的任务与批次不是一个快照，不能用当前页重算总任务计数。
- **Constraint:** int64为JSON数值且最大9223372036854775807；证券LONG/DECIMAL仍string。控制不能使用字符串或指数版本；不按ErrorCode.retryable决定按钮。定义/容量/插件等可能在提示后变化，409须保留固定原因并查询。保持生产34项RANGE NEEDS_VERIFICATION及6项SINGLE-only，不修改后端合同或生产能力。
- **Usage:** 复用T10原始JSON解析，新增详情/批次/两控制API及Batch DTO；只取page/pageSize的叶子列表，显式includeSplit=false。bigint正int64经校验后以十进制文本构造JSON number token并指定application/json。Task/Batch错误作为固定数据文案，HTTP错误仍走现有ApiError/ClientError；ApiError只有code，无status字段，按T09固定code映射分支。
- **Readiness evidence:** 看板T09记录2026-09-12四条最终门禁退出0：专项602项/33类，全单元1019项/63类，生产1019+4，验收1019+4+3，失败/错误/跳过0；各生命周期前端170项与build通过。真实MySQL HTTP证明三批retry、并发一202一409、resume保留普通失败、丢回执后查询及旧版本冲突。完整命令/日志见看板T09 Verification evidence；独立审查已关闭。这是HTTP输入证据，不是T11页面已完成。

### ISSUE-018-T10

- **Artifact:** `docs/task-designs/ISSUE-018-T10-design.md`；`control-plane/src/api/downloadTasks.js`、`downloadTaskDtos.js`及测试；`control-plane/src/composables/useDownloadFlow.js`、`useDownloadTaskList.js`及测试；`control-plane/src/utils/downloadTaskSubmission.js`及测试；`control-plane/src/views/DownloadView.vue`、`control-plane/src/components/download/DownloadTaskList.vue`及测试；`control-plane/src/composables/downloadTaskFlow.integration.spec.js`、`control-plane/src/layouts/AppLayout.spec.js`。实际路由/缓存输入为 `control-plane/src/router/index.js`、`control-plane/src/layouts/AppLayout.vue`。
- **Decision:** 已接收任务只显示身份，详情入口为`/downloads/tasks/{taskId}`。Task/Receipt为冻结白名单DTO，version/counts/total为bigint，原始JSON每个number token都检查source整数词法。未确认提交用sessionStorage保存原submissionId/非敏感请求；详情不接管这份提交状态。下载列表独立轮询，setActive由DownloadView的onActivated/onDeactivated接线。
- **Rationale:** 接收与实际执行分离；失去响应不能产生第二个任务。大整数不允许先舍入再转换，版本和写入次数必须保真。AppLayout缓存下载页以保留配置，离开时必须暂停列表请求但保留在途提交。
- **Constraint:** 不修改共享http.js、旧同步downloadDataset/DownloadResult或证券字符串。缺JSON.parse source时明确拒绝任务数值。新详情不加入KeepAlive；同name不同taskId复用详情组件时必须watch参数。ElPagination会在page-count下降时自动发折页事件，需保留服务端超尾页（含total=0）并允许显式用户翻页。
- **Usage:** 扩展现有两个任务API/DTO文件，不复制Task解析；新增useDownloadTask管理详情与控制。列表/接收面板的a改RouterLink，URL保持，返回下载页沿既有缓存激活后立即补查。批次表复用T10 bigint分页与真实控件防自动折页规则，不抽取全站框架。sessionStorage、初始提交和模式表单逻辑保留。
- **Readiness evidence:** 2026-09-12最终Node24.15.0/npm11.12.1下，T10完整专项10文件/206项、全前端31文件/340项通过；生产build、两个diff检查通过。真实Axios→DTO→flow三项包含在全量中；独立API/flow/UI审查问题已关闭。生产预览受控Chromium在1440×1080及390×844：各一次RANGE POST、202仅接收、9007199254740993精确展示、零pageerror、无页面横向溢出。日志`/tmp/issue018-t10-focused-final.log`、`/tmp/issue018-t10-all-final.log`、`/tmp/issue018-t10-build-final.log`、`/tmp/issue018-t10-visual-production-final.log`；实际命令与持久结果在看板T10。构建仍有大bundle提示，未阻止构建；本次预览已停止。这些不证明T12真实后台生命周期或T13完整性。

直接输入无未解决冲突。T11专属设计已对照真实Controller/schema、ApiError形状、T10导出与KeepAlive复核；独立审查确认实施就绪。控制版本、查询/控制互斥、隐藏和同名路由切换、终态与批次失败轮询规则均固定。

## Start Here

按顺序读取：

1. `docs/task-designs/ISSUE-018-T11-design.md`全文，使用其API、Batch DTO、查询/控制状态机、页面与测试合同。
2. `docs/task-handoffs/ISSUE-018/ISSUE-018-task-board.md` T11范围和验收；`docs/task-designs/ISSUE-018-design.md` §3.7、§3.10–3.12、§5.1–5.2。
3. T09专属设计、schema/examples、DownloadTaskController与上述DTO；核对200/202/409、nullable批次字段及原版本数值合同。
4. T10专属设计、downloadTasks.js/downloadTaskDtos.js及真实Axios测试、useDownloadTaskList与DownloadView/AppLayout生命周期测试；再读router/index.js、AppLayout.vue及现有UI组件。
5. `control-plane/e2e/ui-redesign.fixtures.js`、`control-plane/e2e/stock-download-parameters.spec.js`、`control-plane/e2e/download-outcomes.spec.js`和playwright.config.js，了解普通测试仍待T12迁移的边界；本项新增独立受控download-tasks fixture/spec。

第一项实施动作：扩展 `control-plane/src/api/downloadTasks.spec.js`，真实http adapter返回原始text回执，调用尚不存在的`retryDownloadTask(taskId, 9007199254740993n)`；断言最终wire body恰为`{"expectedVersion":9007199254740993}`、Content-Type为application/json、202/Location/同taskId及bigint回执。观察缺入口RED后实现最小控制传输，再补详情/批次和调度。不要重新设计或先改全局HTTP。

明确启动后记录`READY -> IN_PROGRESS`，保留本交接。使用设计中的Node24前端专项/全量/build与专用Chromium命令，实际启动生产preview并记录URL，完成后停止自己启动的服务。新增交付文件加入Git，保持`feat/download-by-date-range`及当前暂存成果，不自动提交。完成后先记录T11证据，再按Order准备T12设计/交接；本交接不启动实现。

## Risks

- controls不是长期承诺；版本/定义/容量/插件变化仍可能拒绝，必须以原version请求并保留固定错误提示。
- 详情与批次分别读取，动态拆分会改变当前计划；禁止固定百分比、跨页重算或把写入次数当整段去重总量。
- 不支持JSON.parse source的浏览器继续明确拒绝任务数值；不能有损降级或把bigint作为quoted string发送。
- DownloadView保留缓存而详情同name可能复用组件，两个生命周期都需真实router验证；隐藏/离开不等于取消后台任务。
- 本项受控浏览器与生产AVAILABLE不是同一事实；34项生产RANGE仍待验证。普通e2e全面迁移、后台断开/重建/发布门禁由T12执行，真实接口完整性由T13承担，母issue尚未完成。
