# STUDIO-T07 — 任务重试与中断恢复

## Goal

在 Studio 最近任务与详情弹窗中安全重试失败任务、恢复中断任务，并让两处展示最终一致。

## Scope

- 列表快捷操作、详情操作、共享操作锁、状态反馈和操作后刷新；仅 PC 1024／1280／1440px。
- 复用任务 API、BigInt 版本与既有失败后重查；不修改后端批次策略，不涉及创建任务的提交恢复。

## Approach

列表沿用 Demo 的状态下方文字按钮与 RefreshRight 图标，分别由响应中的 canRetry／canResume 决定是否显示，不由失败状态或 error.retryable 推断。旧列表查询失败时禁用操作。详情保留现有按钮与成功结果保留说明，补充操作中的文字与禁止操作原因。

AppLayout 提供一个应用实例内的任务操作通道，维护按任务 ID 的在途锁并通知操作开始／结束。列表与详情复用 useDownloadTask 的 control：捕获已查询详情中的精确版本，在途 GET 完成后发送一次 POST；相同任务跨入口互斥。操作开始使其他详情实例的旧许可与请求失效；结束使列表和打开的详情重新查询。通知在已发送 POST 结束时仍发生，即使发起详情已关闭。通道不保存业务结果或修改成功批次／行数。

DownloadView 维护一个轻量 useDownloadTask 实例处理快捷操作，关闭批次读取与运行态轮询。点击时锁定快捷入口，先 load(taskId)，只有新详情允许对应动作才调用 retry／resume。预查询失败不发送 POST；许可已改变则提示查看最新状态并刷新列表。操作反馈位于筛选栏下方，包含接口、结果、错误／请求 ID、重新查询和详情链接，即使任务离开当前筛选页也能看到结果。

保留 useDownloadTask 原有单请求槽、冲突和结果不确定后的自动重查、失败退避与旧响应隔离；重查成功前保持禁用。隐藏或离开页面使许可失效，返回并取得新详情后才允许操作。快捷实例随工作台激活／停用，卸载时清理。详情关闭不取消已送达服务端的操作。

## Files

- `control-plane/src/composables/useDownloadTask.js`：共享通道、跨入口锁、轻量查询选项、激活／停用及状态失效。
- `control-plane/src/layouts/AppLayout.vue`：提供应用实例内通道。
- `control-plane/src/views/DownloadView.vue`：快捷查询／操作与列表刷新订阅。
- `control-plane/src/components/download/DownloadTaskList.vue`：快捷按钮、禁用状态及持久反馈。
- `control-plane/src/views/DownloadTaskView.vue`：消费通道和操作状态说明。
- 对应单元测试；`control-plane/e2e/studio-recovery.spec.js`：HTTP 夹具下的联动、异常和桌面对照。
- `docs/verification/STUDIO-T07.md`、`docs/verification/studio-t07/`：验收结果与截图；任务板回填设计和状态。

## Tests

在 control-plane 使用 Node 24.15.0：

- `npm test -- src/composables/useDownloadTask.spec.js src/views/DownloadView.spec.js src/views/DownloadTaskView.spec.js src/components/download/DownloadTaskList.spec.js`：先验证新增行为失败，再实现；覆盖跨实例互斥、关闭后联动、隐藏后许可失效、预查询和轻量模式。
- `npm test`、`npm run build`：全量前端单元／集成及构建通过。
- `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 TENSOR_UI_BASE_URL=http://127.0.0.1:4174 npm run test:e2e -- studio-recovery.spec.js download-tasks.spec.js studio-detail.spec.js studio-tasks.spec.js studio-submission.spec.js studio-shell.spec.js`：部分失败／中断、精确版本、冲突、网络异常、预查询拒绝、跨入口点击、列表／详情最终一致与桌面对照。
- `git diff --check`；Impeccable detector 检查修改的 UI；截图检查三个 PC 宽度的按钮、反馈、键盘焦点和溢出。

## Acceptance

- 列表快捷操作先取得详情；权限只来自 canRetry／canResume，POST 使用精确 BigInt expectedVersion。
- 快速点击及同任务跨入口不会重复发送；失败详情／隐藏页面／未确认状态不能使用旧许可。
- 受理、冲突、明确拒绝和结果不确定均有可读反馈；冲突／不确定后只查询，不自动重放 POST。
- 操作触发列表与详情刷新，包括详情关闭后 POST 才结束；保留筛选／分页，服务端成功批次与行数原样展示。
- 受影响测试、构建和 PC 对照通过，新增文件加入 Git，任务板记录可追溯证据。

## Risks

- 不同浏览器／客户端间的竞争仍由服务端版本冲突保护；前端通道只协调当前应用实例。
- 操作结束后的详情或列表查询可能失败，保留旧数据并显示错误、禁用旧许可，用户可重查；不将请求受理描述为任务执行完成。
- 受控 HTTP 浏览器场景不代表真实上游或数据库集成，本轮记录此验证边界。
