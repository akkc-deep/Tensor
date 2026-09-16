# STUDIO-T01 验收记录

- 日期：2026-09-16。
- 任务板：`docs/task-handoffs/studio-frontend-task-board.md`。
- 设计：`docs/task-designs/STUDIO-T01-design.md`。
- 环境：Node 24.15.0，Chromium，Vite 本地服务 `http://127.0.0.1:4174`。
- 视觉来源：提交 `2ae5963` 的原版 Studio Demo；此次未修改原 Demo 或 live 版。

## 已完成

正式入口已采用 Studio 顶部导航、路径、页面标题、配色、三栏下载容器和外观设置。下载页三栏继续运行现有数据源/接口选择、参数提交/结果和近期任务；数据查看的业务内容保持原实现。路由、KeepAlive、业务 composable 与存储键继续复用。

外观设置提供 Demo 四色预设、默认恢复和折叠的自定义 HEX。旧偏好可以恢复，存储不可用时主题仍可应用并显示“仅本次生效”。固定背景与文字沿用 Studio，主题色仅改变强调色；过亮色仍作可读性修正。

## 验证结果

以下命令均在 `control-plane` 下运行：

| 检查 | 命令 | 结果 |
|---|---|---|
| 新预期 RED | `npm test -- src/App.spec.js src/layouts/AppLayout.spec.js src/views/SettingsView.spec.js src/utils/theme.spec.js src/composables/useTheme.spec.js src/router/index.spec.js` | 实现前 21 项预期失败：旧布局、旧配色与缺少预设控件 |
| 全量单元/集成 | `npm test` | 37 个文件，571 项通过 |
| 最终受影响复验 | 与 RED 相同的六个测试文件 | 52 项通过 |
| 构建 | `npm run build` | 通过；正式入口、原 Demo、live 版均生成 |
| 外壳对照 | `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 npm run test:e2e -- studio-shell.spec.js` | 3 项通过：1280/1440 外壳与设置几何/颜色对照、历史/缓存/主题持久化，以及 1024 大页码 |
| 业务与桌面回归 | `TENSOR_UI_BASE_URL=http://127.0.0.1:4174 npm run test:e2e -- ui-redesign.spec.js` | 49 项通过，包括 40 接口矩阵、提交/查询、错误恢复、缓存、宽表、主题存储降级、键盘及 1024/1280/1440 桌面布局 |
| 样式机械检查 | impeccable detector 对此次改动的 Vue/CSS 文件 | 无发现 |
| 差异格式 | `git diff --check` | 通过 |

浏览器验证拦截 API 并使用受控契约响应，不调用真实下载账户。浏览器对照测试未发现页面 JavaScript 错误。构建保留已有的主包大于 500kB 提示，包拆分不属于 T01。

## 视觉对照

1440×1000 截图已随文档保存：

| 页面 | Demo | 正式入口 |
|---|---|---|
| 下载 | [基准](studio-t01/demo-downloads-1440.png) | [T01](studio-t01/downloads-1440.png) |
| 设置 | [基准](studio-t01/demo-settings-1440.png) | [T01](studio-t01/settings-1440.png) |

对照时扣除 Demo 专用横幅的实际高度。1280/1440 下顶栏、工作区路径和标题的 x/y/宽高，以及三栏的宽度/分列值由浏览器断言比较；设置介绍、主题色行与色板组的位置/尺寸同样比较。顶栏 66px、路径 48px、主标题 25px、水平留白 30px 与基准一致。默认强调色为 #3565b6，背景为 #f7f9fb。

下载截图两侧均选择 daily；正式侧展示真实契约对应的原业务控件，任务列表使用空结果，Demo 使用自己的示例任务。因此本轮只对照外壳和容器，不声称内部内容或容器整体高度已经一致。正式版去除了 Demo 横幅、预览提示和设计页脚；设置保留实际保存状态和自定义色入口。

## 审查与边界

独立只读代码审查未发现严重或阻断问题，发现一处窄右栏大页码裁切。补充 1024px / 100000000 页的测试后先观察失败（按钮超出右边界约 19px），允许 `.el-pager` 换行后通过，末页点击请求及状态显示均验证成功。

T02–T09 的目录、表单、任务卡片、详情和数据视图内部仍待迁移。此次仅验收 PC，未将原移动端断言继续作为产品要求；原 UI 回归中的业务断言保留，布局与截图场景调整为 PC。
