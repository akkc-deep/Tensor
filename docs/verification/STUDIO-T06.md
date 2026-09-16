# STUDIO-T06 验收记录

- 日期：2026-09-16。
- 任务板：`docs/task-handoffs/studio-frontend-task-board.md`。
- 设计：`docs/task-designs/STUDIO-T06-design.md`。
- 环境：Node 24.15.0、Chromium、本机 Vite `http://127.0.0.1:4174`。
- 视觉基准：任务板锁定的 Studio Demo（提交 `2ae5963`）；Demo 未修改。

## 实现结果

任务地址 `/downloads/tasks/:taskId` 使用命名视图打开右侧原生弹窗。背后的 DownloadView 保留同一实例及草稿、接口和列表状态；详情位于 KeepAlive 外，关闭或离页卸载并 dispose。关闭按钮、Esc、遮罩和返回链接恢复入口焦点；直达／刷新可重开，浏览器前进后退可用。无可返回的下载记录时 replace 到下载页，避免离开应用。

弹窗宽 430px、全高、内边距 31px；采用 Studio 状态圆点、信息分隔线、批次列表和数量标记。名称使用真实 apiName；来源、模式、规范化参数、行数、状态与计划直接可见，任务／提交标识、版本、策略、请求计数、写入次数及所有历史时间保留在“任务记录”中。写入次数并非去重总量的说明保留。

计划未就绪不显示百分比；进度表示成功加失败的“已结束”，并明确包含失败以及动态拆分会改变当前计划。计算和原始展示保持 BigInt 精度；历史 RESPONSE_ONLY／UNKNOWN 使用任务保存快照，不使用当前接口能力替代。

原批次横向宽表改为纵向列表。区间、状态、尝试次数、来源行数、写入次数及错误直接可见；批次／父批次标识、来源参数和时间可展开。20／50／100 每页、上一页／下一页、准确总数／页数及越尾返回可用，零结果不再显示“1 / 0 页”。

详情和批次各自保留成功数据、错误及重载入口；初次详情失败也可查看已取得的批次。沿用同一刷新周期同时查询两端，运行中每 2 秒轮询、终态停止、隐藏暂停及旧请求隔离逻辑保持。旧重试／恢复能力保留，T07 的列表快捷操作和进一步联动未实施。

## 验证

以下 npm 命令在 `control-plane` 目录使用 Node 24.15.0 执行。浏览器命令设置 `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 TENSOR_UI_BASE_URL=http://127.0.0.1:4174`。

| 检查 | 命令 | 结果 |
|---|---|---|
| 新行为 RED → GREEN | `npm test -- src/views/DownloadTaskView.spec.js src/components/download/DownloadBatchTable.spec.js src/layouts/AppLayout.spec.js src/composables/useDownloadTask.spec.js` | 实施前 9 项因缺少弹窗／纵向批次／零结果分页等失败；实施后 71 项通过，补充独立错误及大整数进度后 73 项通过 |
| 全量单元／集成 | `npm test` | 最终 37 文件、601 项通过 |
| 离线采集证据回归 | `node --test e2e/tushare-range-evidence.test.js` | 111 项通过 |
| 浏览器工作台回归 | `npm run test:e2e -- studio-detail.spec.js download-tasks.spec.js studio-tasks.spec.js studio-submission.spec.js studio-shell.spec.js` | 47 项通过 |
| 浏览器 UI 回归和最终详情复验 | `npm run test:e2e -- studio-detail.spec.js ui-redesign.spec.js` | 57 项通过，其中详情 8 项与上行重复；两组合计 96 个不同场景 |
| 视觉修正复验 | `npm run test:e2e -- studio-detail.spec.js`；分页专项 | 警告色修正后 8 项通过，最终 1024px 分页另行复验通过 |
| 返回链接修复 RED → GREEN | `npm run test:e2e -- studio-detail.spec.js --grep '返回下载页只导航一次'` | 旧实现前进重开后 `history.state.forward` 错为 `/downloads`；改普通链接后最终详情专项第 8 项通过 |
| 构建 | `npm run build` | 最终构建成功；保留既有产物大于 500kB 的提示 |
| 静态检查 | `git diff --check`；Impeccable detector（详情、批次和外壳） | 无空白错误；detector `[]` |

## 关键场景

- 列表及受理链接打开详情，关闭后仍为原表单草稿；深链接、新页面重开和刷新均从 API 查询任务。
- 关闭按钮自动聚焦、Shift+Tab 保持模态焦点、Esc／遮罩／返回链接关闭并恢复原任务链接；前进重开不增加多余历史记录。
- 23 批次分两页，切到每页 50 后显示 23 条；返回 20 后再翻页，服务端缩为零条时保持越尾提示并可返回第一页。
- 大整数 `9007199254740993`／`9223372036854775807` 不丢精度；`4503599627370496 / 9007199254740993` 进度为 49.99%，不会经 Number 四舍五入冒充 50%。
- 初次详情失败但批次成功、详情恢复但批次失败、保留旧批次并刷新恢复；长接口／参数／错误无水平溢出。
- 虚拟时钟验证 2 秒轮询、隐藏暂停及恢复立即查询、终态停止、关闭后不再请求详情；已有单元测试覆盖失败退避、快速切换任务／分页、迟到响应和卸载。
- 原重试／恢复浏览器场景继续验证 expectedVersion 大整数、重复点击防护、状态冲突及成功数据保留。
- 更新受影响的历史浏览器选择器和在线采集证据脚本，状态断言限定弹窗，防止与背板任务卡片冲突；写入计数断言先展开任务记录。

## 桌面对照

截图使用受控 HTTP 夹具，展示等价的部分失败批量任务和额外错误／历史字段，不作为真实下载结果。1024／1280／1440px 自动对照弹窗宽度、位置、内边距、背景，并验证无页面或弹窗水平溢出。正式内容较 Demo 多，按真实数据滚动；标题使用服务端接口编码，演示标识和模拟进度不进入正式界面。

- [1024px 详情](studio-t06/detail-1024.png)／[Demo](studio-t06/demo-detail-1024.png)／[批次与展开记录](studio-t06/batches-1024.png)
- [1280px 详情](studio-t06/detail-1280.png)／[Demo](studio-t06/demo-detail-1280.png)／[批次与展开记录](studio-t06/batches-1280.png)
- [1440px 详情](studio-t06/detail-1440.png)／[Demo](studio-t06/demo-detail-1440.png)／[批次与展开记录](studio-t06/batches-1440.png)
- [1024px 长字段](studio-t06/long-detail-1024.png)／[独立批次错误](studio-t06/batch-error-1024.png)／[批次分页](studio-t06/pagination-1024.png)

独立代码审查发现返回链接同时触发 RouterLink 默认导航和 close 导航，真实浏览器表现为额外历史记录；通过失败测试复现后修复为单次导航，复核 resolved。另恢复重试前后来源行数的精确断言，复核 resolved。

独立视觉审查提出的三项收尾已完成：恢复 Demo 琥珀警告色、补充 1024px 完整分页证据、补充完整独立批次错误证据。审查者实际复看新截图，F1／F2／F3 均为 resolved，最终限定复核 disposition 为 ship。共 12 张截图已加入 Git。

## 验收边界

本轮没有修改后端；未运行真实 Tushare 上游采集、带数据库的 packaged fixture／download-outcomes／task-lifecycle 集成套件或部署。相关脚本的界面选择器已适配；离线证据脚本和受控 HTTP 浏览器检查通过不等于这些在线套件已执行。T07 保持 NOT_STARTED。
