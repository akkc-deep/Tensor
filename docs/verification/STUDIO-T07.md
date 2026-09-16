# STUDIO-T07 验收记录

- 日期：2026-09-16。
- 任务板：`docs/task-handoffs/studio-frontend-task-board.md`。
- 设计：`docs/task-designs/STUDIO-T07-design.md`。
- 环境：Node 24.15.0、Chromium、本机 Vite `http://127.0.0.1:4174`。
- 视觉基准：任务板锁定的 Studio Demo（提交 `2ae5963`），Demo 未修改。

## 实现结果

最近任务新增 Studio 风格的重试／恢复快捷按钮，独立使用真实响应的 canRetry／canResume。列表读取失败时按钮禁用；快捷操作先读取任务详情，使用该详情的精确 BigInt 版本发送请求。预查询失败或新详情撤销许可时不发送 POST。详情继续复用既有操作规则，并显示提交中的文字和禁用原因。

AppLayout 提供应用实例内的任务操作通道。相同任务跨列表和详情共用在途锁；操作开始使其他详情实例的旧许可和旧请求失效，结束后刷新列表和已打开的详情。已发送请求在详情关闭后仍持有锁，响应到达时通知现有页面刷新。通道不修改任务事实、批次和行数，不使用受理回执推断执行已完成。

快捷流程只读取详情，不读取批次或开启运行态轮询；失败后的查询退避仍保留。工作台离开／返回会停用／激活该流程，卸载清理订阅。修复页面隐藏再恢复时旧许可提前启用的问题：必须取得新的成功详情后才能使用原操作能力。

快捷反馈保留在筛选栏下方，任务移出“需处理”分组后仍可通过反馈打开详情。提示区包含接口、操作结果、查询或操作错误、请求 ID、重查入口和详情链接。每次快捷操作清空上次操作反馈，区分预查询与 POST 在途；冲突／结果不确定提示引导核对最新状态，不错误宣称查询已经完成或仍在进行。异常后自动查询而不自动重放操作。

## 验证

以下 npm 命令在 control-plane 目录使用 Node 24.15.0 执行。浏览器命令均设置 `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 TENSOR_UI_BASE_URL=http://127.0.0.1:4174`。

| 检查 | 命令 | 结果 |
|---|---|---|
| 新增行为 RED → GREEN | `npm test -- src/composables/useDownloadTask.spec.js src/components/download/DownloadTaskList.spec.js` | 初次 4 项失败，覆盖缺少跨入口协调、隐藏后旧权限、轻量查询和快捷按钮；实施后连同视图共 111 项定向测试通过 |
| 反馈修正 RED → GREEN | `npm test -- src/views/DownloadView.spec.js` | 同任务预查询失败残留旧反馈、POST 在途文案各自失败后修复，25 项通过 |
| 全量单元／集成 | `npm test` | 最终 37 文件、607 项通过 |
| 工作台浏览器回归 | `npm run test:e2e -- studio-recovery.spec.js download-tasks.spec.js studio-detail.spec.js studio-tasks.spec.js studio-submission.spec.js studio-shell.spec.js` | 56 项通过 |
| 最终操作及 UI 回归 | `npm run test:e2e -- studio-recovery.spec.js download-tasks.spec.js ui-redesign.spec.js` | 73 项通过；与上行重叠 24 项，两组合计 105 个不同场景 |
| 构建 | `npm run build` | 最终构建成功，保留既有产物大于 500kB 的提示 |
| 静态检查 | `git diff --check`；Impeccable detector（列表、下载视图、详情和外壳） | 无空白错误；detector `[]` |

## 关键场景

- 列表仍显示旧版本时，点击后读取新版本 `9007199254740997`，POST 原始 JSON 为 `{"expectedVersion":9007199254740997}`；双击只产生一次操作。
- 在“需处理”分组操作成功后，服务端列表变为零条，反馈仍然保留；详情显示服务端成功状态及来源行数 `9007199254740998`。已成功批次保持来源行数 `9007199254740993`、尝试次数 1；重新执行的批次为尝试次数 2。
- 中断任务恢复遇到 409 和后续详情查询 500，保留冲突提示、查询错误和请求 ID；手动重查后再次恢复使用更新版本 `9007199254740994`。
- 操作响应断网但服务端已经成功：详情和列表通过 GET 显示最新成功状态，保留结果不确定提示；没有自动重放 POST。明确 TASK_DEFINITION_CHANGED 拒绝也展示错误并重新查询。
- 列表重试在途时打开详情，详情按钮禁用；详情关闭、重开期间请求未结束时两处均不能重复发送；迟到受理仍刷新列表及新的详情实例。
- 预查询失败、详情撤销 canRetry 时均不发操作。单元测试保留 canRetry／canResume 独立于错误 retryable 的规则、批次过滤／分页、大整数、失败退避、隐藏／卸载及旧响应隔离检查。

## 桌面对照与审查

三个桌面宽度均验证快捷按钮字号／颜色与 Demo 一致、键盘聚焦可用，页面／列表／详情无水平溢出。正式页面显示真实 API 编码、错误、分页和操作反馈；Demo 的示例记录与模拟任务逻辑没有进入正式应用。

- [1024px 快捷操作](studio-t07/list-1024.png)／[Demo](studio-t07/demo-list-1024.png)
- [1280px 快捷操作](studio-t07/list-1280.png)／[Demo](studio-t07/demo-list-1280.png)
- [1440px 快捷操作](studio-t07/list-1440.png)／[Demo](studio-t07/demo-list-1440.png)
- [1024px 冲突和查询失败](studio-t07/conflict-1024.png)
- [1280px 结果不确定及最新详情](studio-t07/uncertain-detail.png)

浏览器对照首次发现快捷按钮 12px 小于 Demo 的 14px，已修正并通过三种宽度复验。初次新冲突夹具将 retryable 错设为 true，被现有错误契约校验识别为无效响应；修正夹具为契约要求的 false 后冲突场景通过。

独立代码审查确认共享锁、旧 GET 隔离及关闭后的通知正确；额外内存验证覆盖其他实例旧 GET、隐藏期间 POST 结束和回到页面后新鲜权限。审查发现的两项反馈问题已通过失败测试修正，查询时态文案也已修正。限定复核三项均关闭，无未解决的 Critical／Important／Minor 问题。八张截图已实际查看或通过同条件自动对照。

## 验收边界

本轮没有修改后端，也没有执行真实 Tushare 上游、数据库集成或 packaged download-task-lifecycle 套件。成功数据保留由服务端契约和受控 HTTP 响应验证前端展示，不将这些结果表述为本轮真实采集或数据库验证。浏览器初次启动受沙箱限制，获准在本机执行后完成测试。未部署或推送；本任务不实施 T08。
