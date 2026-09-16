# STUDIO-T04 验收记录

- 日期：2026-09-16。
- 任务板：`docs/task-handoffs/studio-frontend-task-board.md`。
- 设计：`docs/task-designs/STUDIO-T04-design.md`。
- 环境：Node 24.15.0、Chromium、本机 Vite `http://127.0.0.1:4174`。
- 视觉基准：任务板锁定的 Studio Demo（提交 `2ae5963`），Demo 未修改。

## 实现范围

正式表单底部改为 Studio 全宽原生按钮：单次“开始下载”、批量“开始批量下载”，请求中显示“正在创建…”或“正在查找…”。空闲时不再展示“任务接收”大面板及等待占位；受理、错误和恢复反馈位于操作区下方，保留状态播报、任务详情链接、请求 ID 和字段错误。

继续使用原有 `useDownloadFlow`、任务 API 与 sessionStorage 记录。来源、目录、模式、参数在等待期间锁定；等待、恢复和结果不确定时展示同一冻结的原提交参数。网络失败、刷新及空查询后明确重放均沿用原 submissionId 和参数，编辑后的表单草稿不覆盖待确认请求。受理后触发已有任务列表刷新，提示仅确认任务身份，不把受理当作下载完成。

本地记录读取失败可重读，写入失败明确请求未发送并允许主按钮重试，损坏记录说明先核对近期任务及清理的标签页范围，清理失败可重试且保留服务端受理事实。参数拒绝、队列满及配置变化分别提供后续操作指引。

## 验证结果

npm 命令均在 `control-plane` 下使用 Node 24.15.0；浏览器命令设置 `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 TENSOR_UI_BASE_URL=http://127.0.0.1:4174`。

| 检查 | 命令 | 结果 |
|---|---|---|
| 提交 RED → GREEN | `npm test -- src/components/download/DownloadAction.spec.js src/views/DownloadView.spec.js src/composables/useDownloadFlow.spec.js src/composables/downloadTaskFlow.integration.spec.js` | 先复现新按钮状态、等待快照及移除空闲面板的 7 项失败；实施后 55 项通过 |
| 单元／集成 | `npm test` | 37 文件、584 项通过；移除旧 DownloadResult 文件中重复且依赖 ElButton 内部结构的两项按钮测试，独立 DownloadAction 测试继续覆盖禁用与提交 |
| 长标识 RED → GREEN | `npm run test:e2e -- studio-submission.spec.js -g '长来源'` | 合法 64 字符来源／接口和长参数先复现反馈容器溢出，增加快照整体换行后通过 |
| 提交专项浏览器 | `npm run test:e2e -- studio-submission.spec.js --max-failures=3` | 13 项通过，覆盖三视口对照、SINGLE／RANGE 慢提交与双击、断网刷新找回、空查询后原请求重放、四种存储故障、明确拒绝后重交及局部溢出 |
| 离线采集回归 | `node --test e2e/tushare-range-evidence.test.js` | 111 项通过；测试替身同步新提交按钮名称 |
| 构建 | `npm run build` | 正式入口与两个 Demo 构建通过；保留既有主包超过 500kB 提示 |
| 静态检查 | 13 个受影响 E2E 文件 Node `--check`、impeccable detector 检查两个修改 Vue 文件、`git diff --check` | 通过，detector 输出 `[]` |

最终样式修正后另行执行 `npm test -- src/views/DownloadView.spec.js src/components/download/DownloadAction.spec.js src/components/download/DownloadResult.spec.js`：33 项通过；`npm run test:e2e -- studio-submission.spec.js --output=node_modules/.cache/tensor-playwright-t04-final --max-failures=1`：13 项再次通过，包含受理链接真实键盘焦点检查，导出七张最终截图。最终构建通过。

受影响整套浏览器命令：`npm run test:e2e -- studio-submission.spec.js studio-form.spec.js studio-catalog.spec.js studio-shell.spec.js ui-redesign.spec.js download-tasks.spec.js stock-download-parameters.spec.js --max-failures=3`。首次 96 项通过、主题测试 1 项超时。Trace 显示导航后 `openCustomTheme` 在设置页挂载前执行 `queryCount` 得到 0，跳过了展开动作；单独复跑该主题用例通过。辅助函数现先等待折叠区可见，再展开并确认 HEX 输入可见。修复后 UI 套件 48 项通过，截图场景暴露出在下载页误调用主题展开的旧空操作；移除该调用后，`npm run test:e2e -- ui-redesign.spec.js -g '设置零 API|生成八张' --output=node_modules/.cache/tensor-playwright-t04-theme-final --max-failures=1` 两项通过。最终分批覆盖 **97 个不同浏览器场景**：提交 13、表单 8、目录 6、外壳 3、业务 UI 49、任务 15、股票参数 3。未留失败项。

## 桌面视觉证据

按钮在 1024／1280／1440 下均与 Demo 逐项比较宽高、字号、字重、文字色、背景色、图标间距和圆角。对照图使用同一 RANGE 模式及有效参数：000001.SZ、2026-09-01 至 2026-09-02。正式版保留真实完整性说明，Demo 保留演示条和示例任务，因此页面绝对纵向位置不同。

- [1440px 提交区域](studio-t04/submit-1440.png)／[Demo](studio-t04/demo-submit-1440.png)
- [1024px 提交区域](studio-t04/submit-1024.png)／[Demo](studio-t04/demo-submit-1024.png)
- [1440px 已受理](studio-t04/accepted-1440.png)／[详情链接键盘焦点](studio-t04/accepted-focus-1440.png)
- [1024px 结果不确定与原参数](studio-t04/uncertain-1024.png)

不确定态截图特意将当前草稿改成 SINGLE／000002.SZ，反馈仍显示原 RANGE／000001.SZ 和原日期，确认草稿与原提交身份分离。

## 审查与边界

独立代码审查指出旧 ElButton 测试未迁移以及长来源／接口在快照中越界；均已复现并修复。复核相关单元测试 10 项、长标识浏览器测试通过。独立视觉审查发现受理态详情链接未使用主题色，已补主题链接颜色、下划线间距和键盘焦点；更新默认态／焦点态截图后，审查者单项复核为 resolved，disposition 为 ship。

本轮只完成 T04；T05 的最近任务视觉迁移及后续任务准备不在本轮范围。截图右栏仍沿用现有任务列表。

浏览器测试采用受控 HTTP 响应，验证正式前端 API 路径、请求参数、受理回调、恢复和异常状态，没有执行真实账号采集。依赖在线后端／数据库／凭证的 fixture-flow、dataset-query、download-outcomes、tushare-metadata、tushare-live、download-task-lifecycle 仅同步受影响定位并完成语法检查。

首次本机服务监听和 Chromium 启动因沙箱权限失败；使用已授权的本机运行权限后正常执行。首轮视觉测试的程序化 focus 未切换到键盘输入方式，已改为实际 Tab 后验证焦点。

设计、提交 E2E、本文及七张最终截图已加入 Git。保留工作区既有 T01–T03 变更，本轮不创建提交。
