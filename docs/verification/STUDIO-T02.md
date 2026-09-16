# STUDIO-T02 验收记录

- 日期：2026-09-16。
- 任务板：`docs/task-handoffs/studio-frontend-task-board.md`。
- 设计：`docs/task-designs/STUDIO-T02-design.md`。
- 环境：Node 24.15.0，Chromium，本机 Vite `http://127.0.0.1:4174`。
- 视觉来源：原版 Studio Demo（任务板锁定的提交 `2ae5963`）；本次未修改 Demo。

## 已完成

正式下载页左栏改为 Studio 内嵌接口目录。来源、接口名称、编码、分类和数量均使用既有元数据 HTTP API。搜索忽略大小写及首尾空格，和分类组合过滤；标题显示完整目录数，页脚显示结果数。保留多来源选择与单来源自动选择，切换来源清空旧目录筛选。

点击目录项立即更新中栏接口标题、编码及分类／查询方式；能力请求期间清空旧参数、禁用提交，目录仍可切换。过滤隐藏当前接口或重复点击选中项不会丢失输入。来源／目录加载及失败留在左栏，能力加载及失败放在中栏；空来源、空目录、无匹配和不可用来源分别呈现，错误保留请求 ID 和重试入口。恢复原提交期间尚未加载的目录不再误报为空。

复用 `useDownloadFlow` 的请求代次保护，迟到的目录／能力成功或失败均不能覆盖新选择。不可用来源在 UI 和 composable 两处禁止选择接口，提交继续受既有可用性约束。参数校验、提交快照、结果恢复和任务轮询保留。

## 验证结果

下列命令在 `control-plane` 下，PATH 优先使用 Node 24.15.0。浏览器均设置 `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174` 和 `TENSOR_UI_BASE_URL=http://127.0.0.1:4174`。

| 检查 | 命令 | 结果 |
|---|---|---|
| 目录行为 RED → GREEN | `npm test -- src/components/download/ApiSelect.spec.js src/views/DownloadView.spec.js src/composables/useDownloadFlow.spec.js` | 新目录、分阶段状态和不可用来源防护在实现前失败；实施后 43 项通过 |
| 恢复期初始状态 RED → GREEN | `npm test -- src/views/DownloadView.spec.js -t 'recovers first'` | 复现尚未请求来源时误报“暂无数据源”；修复后纳入全量通过结果 |
| 最终全量单元／集成 | `npm test` | 37 个文件、576 项通过；含来源和能力的迟到成功／失败、参数、提交与恢复 |
| 构建 | `npm run build` | 通过，正式入口与两个 Demo 均生成 |
| 业务 UI 与股票参数回归 | `npm run test:e2e -- studio-catalog.spec.js studio-shell.spec.js ui-redesign.spec.js download-tasks.spec.js stock-download-parameters.spec.js` | 首轮 67 项通过；其中 UI 49 项、股票参数 3 项全部通过。8 项失败为目录行高差异与旧手机视口，见下方修复说明 |
| 修复后受影响浏览器复验 | `npm run test:e2e -- studio-catalog.spec.js download-tasks.spec.js studio-shell.spec.js` | 24 项全部通过：目录 6、任务 15、外壳 3；结合上行已通过的 52 项，共覆盖 76 个不同浏览器场景 |
| 最终截图导出 | `npm run test:e2e -- studio-catalog.spec.js -g 'Demo 对照'` | 1024／1280／1440 三项通过；导出前滚动使选中项可见 |
| 既有测试辅助函数兼容 | `npm run test:e2e -- studio-catalog.spec.js -g '辅助函数'` | 先复现正则名称导致 TypeError，修复后纳入上述 24 项通过结果 |
| 静态检查 | Node `--check` 检查 12 个受影响 E2E 文件；impeccable detector 检查两个改动 Vue 文件；`git diff --check` | 全部通过，detector 无发现 |

浏览器使用受控 API 响应，检查真实前端请求路径、请求 ID、返回契约和业务交互；没有连接真实下载账号或执行真实采集。依赖打包后端、数据库或账号的 fixture-flow、dataset-query、download-outcomes、tushare-metadata、tushare-live、download-task-lifecycle 只同步目录选择交互并完成语法检查，未运行完整套件。构建仍有既有主包超过 500kB 的提示。

## 视觉与交互证据

- [1440px 正式目录](studio-t02/catalog-1440.png)
- [1440px Demo 基准](studio-t02/demo-catalog-1440.png)
- [1024px 正式目录](studio-t02/catalog-1024.png)

三个 PC 视口均验证：名称／编码搜索、分类交集、总数／结果数、键盘 Enter 选择、末项聚焦后内部滚动、无页面横向溢出；同时对照搜索框、分类控件、选中行、中栏标题与图标的宽高、内边距、字体大小、正文色和背景色。页面无 JavaScript 错误或未预期请求。

目录列表最大高度 417px。对照发现编码继承普通正文行高导致行高多出约 5.2px，已按 Demo 使用 12px 等宽字体的正常行高修复；中栏标题也采用 Demo 的 600 字重与 1.5 行高。桌面对照断言复验通过。

正式元数据保持服务端排序，daily 的位置与 Demo 不同；正式版多出的来源选择控件使搜索和列表下移。截图两侧选择 daily，但正式版沿用能力默认 RANGE、Demo 为 SINGLE；本项对照范围为目录、选中态和中栏标题，未将表单、任务内容或整个容器高度判定为等价。

## 审查与边界

独立只读审查发现测试辅助函数仅接受字符串，旧业务 E2E 仍有正则名称调用；已先用浏览器测试复现，再支持正则按按钮文本定位，并通过复验。生产实现未发现其他明确缺陷。

既有任务 E2E 的五项失败来自 390px 手机视口下外壳溢出。按任务板已确定的仅 PC 范围，将这些场景改为 1024px，保留任务状态、版本冲突、恢复及完整性断言；15 项任务回归均通过。

T03 及后续任务未实施或准备；表单、任务列表和接收结果仍为既有内部 UI。新增测试辅助文件、目录 E2E、设计、本文和截图均加入 Git，未创建提交。
