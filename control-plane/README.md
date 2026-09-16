# Tensor Control Plane

Tensor Control Plane 是面向 PC 桌面浏览器的 Vue 3 Studio，用于创建和跟踪下载任务、查询已落库数据以及设置界面主题。

## 本地运行

需要 Node 24.15+（24.x）。在本目录执行：

```bash
npm install
npm run dev
```

开发服务器将 `/api` 转发到 `TENSOR_BACKEND_URL`；未设置时使用 `http://127.0.0.1:8080`。

```bash
TENSOR_BACKEND_URL=http://127.0.0.1:8080 npm run dev
```

常用命令：

```bash
npm run build       # 生产构建
npm test            # 单元测试（单次运行）
npm run preview     # 预览构建产物
```

构建后的正式 UI 回归会自行启动 4173 预览服务，可直接执行：

```bash
npm run build
env -u TENSOR_UI_BASE_URL npm run test:e2e -- --config=playwright.ui.config.js
```

默认 `npm run test:e2e` 使用 `playwright.config.js`，目标是 `PLAYWRIGHT_BASE_URL`（默认 `http://127.0.0.1:8080`）；其中包含需要已配置服务和集成环境的场景，不等同于上述自包含的预览 UI 回归。

STUDIO-T10 的 HTTP 夹具开发回归使用以下精确命令，完整边界见 [STUDIO-T10 设计说明](../docs/task-designs/STUDIO-T10-design.md#tests)：

```bash
npm run dev -- --host 127.0.0.1 --port 4174 --strictPort
PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 TENSOR_UI_BASE_URL=http://127.0.0.1:4174 npm run test:e2e -- studio-acceptance.spec.js studio-shell.spec.js studio-catalog.spec.js studio-form.spec.js studio-submission.spec.js studio-tasks.spec.js studio-detail.spec.js studio-recovery.spec.js studio-datasets.spec.js studio-table.spec.js ui-redesign.spec.js download-tasks.spec.js stock-download-parameters.spec.js
```

## 正式 Studio

正式入口提供三个主路由：

- `/downloads`：创建单次或按日期范围的批量下载任务，并查看最近任务；`/downloads/tasks/:taskId` 展示任务与批次详情。
- `/datasets`：选择数据集、编辑筛选草稿、提交查询，并对已提交结果分页。修改草稿不会悄悄改变当前结果。
- `/settings`：选择主题色和显示比例。设置会持久化到浏览器存储；存储不可用时仅在本次页面生效。

任务创建只表示服务端已受理，不代表下载成功。请求结果不确定时，页面会保存提交快照，并允许按原参数查找或重新确认。批量任务的进度、失败批次、重试和恢复以任务详情为准。

下载页和数据页的路由缓存只覆盖应用内导航往返，不保证浏览器完整刷新后保留未持久化页面状态；主题和显示比例会单独持久化到浏览器存储。

数据表获得焦点后用 `Left`／`Right` 横向滚动；再聚焦表格内部滚动区，用 `PageUp`／`PageDown` 纵向滚动。聚焦或悬停截断长文本可打开提示；提示支持 `ArrowUp`／`ArrowDown`、`PageUp`／`PageDown`、`Home`／`End` 滚动，按 `Esc` 关闭。支持 1024px 起的 PC 桌面窗口，并适配 1920×1080、2K（2560×1440）及 4K（3840×2160）。

### 2K / 4K 显示设置

默认“自动适配”按浏览器实际可用宽度调整文字、控件、表格与弹窗：

| 浏览器窗口宽度（CSS px） | 显示比例 |
| --- | --- |
| 小于 2240 | 100% |
| 2240–2879 | 125% |
| 2880–3519 | 150% |
| 3520 起 | 200% |

系统缩放和浏览器缩放会改变可用宽度，因此不会再按物理像素密度重复放大。例如 4K 屏幕在系统 200% 缩放下通常约为 1920 CSS px，此时自动模式采用 100%。

在“外观设置 → 显示与布局 → 显示比例”可选择自动或 100%、125%、150%、175%、200%。手动比例遇到较窄窗口会暂时下调，重新扩大窗口后恢复所选比例。内容宽度上限随比例变化：100% 为 1490px，125% 为 1862.5px，200% 为 2980px，居中显示。

## 独立 Demo 与验证

- [本地模拟 Demo](src/demos/README.md)：`/ui-demos.html`，使用固定快照和页面内模拟状态。
- [后端接入 Demo](src/demos/live/README.md)：`/studio-live.html`，通过真实 API 工作。
- [STUDIO-T10 验证记录](../docs/verification/STUDIO-T10.md)：正式入口、回归、构建与清理证据。
- [2K / 4K 适配与控件对齐](../docs/verification/STUDIO-display-adaptation-2026-09-16.md)：显示比例、Demo 控件对照和大屏验证证据。

两个 Demo 是独立调试入口，不接入正式 Studio 导航，也不属于正式应用依赖。
