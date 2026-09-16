# STUDIO-T06 — 任务详情弹窗与批次明细

## Goal

在正式下载工作台中以 Studio 右侧弹窗查看真实任务和批次，保留任务地址直达、刷新和浏览器前进／后退。

## Scope

- 迁移任务详情、批次明细、分页、加载／失败／空态及弹窗生命周期；仅验收 PC 1024／1280／1440px。
- 复用任务 API、DTO 的 BigInt 解析、历史完整性文案和 useDownloadTask 的轮询／竞态保护。
- 保留现有重试／恢复入口和行为；列表快捷操作及操作联动设计属于 T07。本任务不修改后端、Demo 或元数据契约。

## Approach

### 路由和生命周期

采用 Vue Router 命名视图：`/downloads/tasks/:taskId` 的默认视图仍是 DownloadView，`task` 视图挂载 DownloadTaskView。AppLayout 将下载和详情默认视图使用相同 KeepAlive key；弹窗放在 KeepAlive 外。这样无需复制表单状态，也不引入独立于 URL 的任务状态。相较在 DownloadView 内嵌子路由，此方式避免缓存详情导致离页轮询未清理。

原生 dialog 使用 showModal，沿用 Demo 430px、全高、31px 内边距和遮罩。首次打开聚焦关闭按钮，Esc／关闭按钮／点击遮罩回到下载地址；若历史上一项是 `/downloads`，使用 back 保留前进重开能力，否则 replace 至下载页。详情切换 ID 复用现有 flow.load。卸载 dispose、关闭 dialog、恢复原触发元素；元素已移除时聚焦相同任务链接或工作区。直接地址和刷新均挂载真实下载工作台与弹窗，详情请求不依赖元数据成功。

### 内容和数据

标题使用真实 apiName；状态、来源、模式、规范化参数、返回行数优先展示。任务 ID／submissionId、版本、采集策略、请求计数与所有时间可在“任务记录”展开查看。原有写入次数及其非去重总量语义保留。

计划展示 `succeededBatches + failedBatches` 的已结束数、当前计划总数及全部状态分项。进度条仅在 planReady 且总数大于零时出现，使用 BigInt 计算比例后转换小范围 Number；说明已结束包含失败、计划随拆分变化。未生成计划不显示虚假百分比。RESPONSE_ONLY／UNKNOWN 沿用保存快照说明，不查询当前能力推断历史完整性。

DownloadBatchTable 保留组件接口，改为 Demo 纵向列表；批次区间、状态、尝试次数、来源行数、写入次数和错误直接展示，批次／父批次 ID、来源参数、时间放入逐项原生 details。分页采用原生上一页／下一页和 20／50／100 下拉，全部页数运算使用 BigInt；越尾保留当前页并提供返回第一页，空页不出现 1 / 0。默认仍查询叶子批次，拆分数量及子批次父标识解释动态拆分，不新增筛选范围。

详情与批次分别显示其错误／上次成功结果和重新加载入口；沿用现有共享刷新周期同时查询两端，单端失败不清除另一端成功数据。初次详情失败时仍可查看已加载批次；TASK_NOT_FOUND／非法 ID 不显示批次。运行中每 2 秒刷新、失败退避、隐藏暂停、终态停止及迟到响应保护沿用原逻辑。

### Implementation sequence

1. 补充命名视图、表单保留、关闭卸载、独立错误、精确进度与纵向批次测试，运行定向 Vitest 确认新增行为失败。
2. 修改路由／外壳和 DownloadTaskView；实现原生弹窗、内容层次与焦点恢复，保留 flow 和操作约束。
3. 迁移 DownloadBatchTable，更新原表格选择器相关测试，运行定向及全量单元测试。
4. 增加 Studio 详情浏览器场景，覆盖前进后退／刷新／焦点／键盘／轮询／错误／分页及三个桌面宽度；与 Demo 同视口截图对照。
5. 构建、审查、记录验收证据，新增文件加入 Git，按证据更新本任务状态。

## Files

- 修改 `control-plane/src/router/index.js`、`control-plane/src/layouts/AppLayout.vue`：工作台背板及路由弹窗。
- 修改 `control-plane/src/views/DownloadTaskView.vue`、`control-plane/src/components/download/DownloadBatchTable.vue`：详情／批次 Studio 展示及生命周期。
- 更新相应 `.spec.js`、`control-plane/e2e/download-tasks.spec.js`、`control-plane/e2e/download-task-lifecycle.spec.js`；新增 `control-plane/e2e/studio-detail.spec.js`。
- 修改 `control-plane/src/style.css`：增加 Demo 琥珀警告色 token，仅用于详情及批次。适配受影响的 UI／在线采集脚本选择器，保留原断言。
- `docs/verification/STUDIO-T06.md`、`docs/verification/studio-t06/`：结果和视觉证据；任务板记录设计路径及状态。

## Tests

在 `control-plane` 下使用 Node 24.15.0：

- `npm test -- src/views/DownloadTaskView.spec.js src/components/download/DownloadBatchTable.spec.js src/layouts/AppLayout.spec.js src/composables/useDownloadTask.spec.js`：新增行为先失败，实现后通过；保留历史精度／请求竞态／操作保护检查。
- `npm test`、`npm run build`：全部前端单元／集成通过，构建成功。
- `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 TENSOR_UI_BASE_URL=http://127.0.0.1:4174 npm run test:e2e -- studio-detail.spec.js download-tasks.spec.js studio-tasks.spec.js studio-submission.spec.js studio-shell.spec.js`：受控 HTTP 场景通过，详情和工作台无水平溢出。
- `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 TENSOR_UI_BASE_URL=http://127.0.0.1:4174 npm run test:e2e -- studio-detail.spec.js ui-redesign.spec.js`；`node --test e2e/tushare-range-evidence.test.js`：UI 和离线证据回归通过。
- `git diff --check`；对修改 UI 运行 Impeccable detector 并检查截图。在线 Tushare 采集及真实后端操作不属于本轮受控浏览器验收。

## Acceptance

- 列表／提交受理链接打开同一路由弹窗，刷新地址可重开；关闭及浏览器后退清理详情活动并可返回原入口，前进重开。
- 所有详情／批次事实来自真实 API 响应；大整数原值保留；失败批次、未生成计划、动态拆分及完整性语义不误导。
- 详情和批次单端失败可恢复，分页与每页数量真实发起查询；旧请求不覆盖新任务／新页。
- 原生模态焦点约束和 Esc 可用，三种桌面宽度与 Demo 对照且长字段不溢出；单元、浏览器及构建检查通过，新增文件被 Git 跟踪。

## Risks

- 弹窗宽度仅 430px，历史字段全部展开会很长；采用纵向滚动及按需展开，禁止横向宽表。
- 直达详情新增工作台背板请求；详情使用独立 API，元数据失败不能阻塞弹窗。原工作台列表轮询在背景继续，详情关闭只停止自身轮询。
- 原生 dialog 由真实 Chromium 验证焦点和 top layer；jsdom 只补充最低限度 showModal/close 测试实现。
