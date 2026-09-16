# STUDIO-T10 整体对照、回归与旧代码清理

## Goal

完成 Studio 正式入口的跨页面验收，移除迁移后没有生产引用的旧组件和样式，提供实际使用与验证说明。

## Scope

消费 T07 的下载／任务完整链路和 T09 的数据查看链路，覆盖 1024／1280／1440px PC、三主页面和任务详情。保留所有真实业务能力、API／存储契约、主题与路由缓存；不修改后端采集策略、不部署、不移除 Demo 基准。

## Approach

### 跨页面验收

在 `control-plane/e2e/studio-acceptance.spec.js` 增加以下两条连续旅程，复用 `ui-redesign.fixtures.js` 的 `installApi`、`syntheticRecords` 及任务／批次夹具，不在生产代码中添加测试开关。请求拦截记录 method、path、参数和 requestId；遇到未定义路径失败。

1. 单次日线：选择 daily／SINGLE，提交 `000001.SZ`、`20260807`，核对一次 POST 及规范化参数；受理后列表显示返回 taskId；打开同 ID 详情，关闭和浏览器前进能重开，工作台参数保留。随后进入数据查看，选择同一真实数据集定义，查询该代码／日期，展示夹具提供的已写入记录及来源。编辑未提交筛选草稿，分页仍发原条件；跳设置改变预设强调色，再返回数据页验证草稿、结果、页码与精确值保留。刷新验证主题持久化，返回下载页仍能从服务端列表打开任务。不能把前端夹具的记录变化描述为真实数据库写入证明。
2. 批量与恢复：创建 RANGE 日线任务，核对起止日期和证券代码；服务端夹具推进至 PARTIAL_FAILED，详情展示成功／失败批次，列表和详情对同一任务同时重试最多发送一次带精确 expectedVersion 的 POST。接收后只由 GET 更新状态；成功批次的尝试次数和行数保持不变。再从独立 INTERRUPTED 任务执行 resume，服务端进入成功态后查询对应记录，并切换主题返回任务详情，确认来源、进度、完整性和最终状态仍来自响应。复用 T07 已覆盖的冲突／不确定专项，不重新实现操作状态机。

在上述旅程中收集 pageerror；三种宽度均检查页面无横向溢出、表格内滚动和 dialog 内纵向滚动。截图前等待查询／过渡稳定，关闭动画；生成下载、数据、设置、详情各 3 张正式截图，与固定基准 `2ae5963` 的同视口 Demo 对照。对照采用等价接口／模式／记录／任务状态，单独记录数据源控件、真实状态、完整字段、分页和历史元信息的必要差异，不要求 Demo 的假数据和截断行为。已有 T01–T09 截图保留，最终记录链接到新一轮截图。

### 清理范围

已扫描 `control-plane/src`，以下组件仅有自身文件及测试引用，没有生产导入：

- 删除 `src/components/common/WorkbenchPanel.vue` 和 `WorkbenchPanel.spec.js`。
- 删除 `src/components/download/ApiDescription.vue` 和 `ApiDescription.spec.js`。
- 删除 `src/components/download/DownloadResult.vue` 和 `DownloadResult.spec.js`；从 `src/views/DownloadView.spec.js`、`src/layouts/AppLayout.spec.js` 删除其导入及“组件不存在”的旧断言，保留任务受理和路由业务断言。
- 删除没有引用的 `src/components/common/MetadataField.vue`；保留仍被原生表单、设置和 AsyncStatePanel 测试使用的 `FieldError.vue`。

实施前重新执行引用检查，若出现新生产引用，保留该文件并在验证记录说明。不得因为只搜索一个入口没有命中就删除组件。`style.css` 仅清理这些已删除组件对应的 `.workbench-panel`、`.workbench-panel__meta`、`.panel-heading` 及其父选择器规则；先搜索 selector 在全部 Vue／JS／测试中的出现。混合规则保留仍使用的 `.theme-status` 等分支。其他旧样式只有在记录“选择器、完整搜索结果、删除后布局验证”时才允许删除；不整理主题 token、Element Plus 全局覆盖或执行无关重构。

### 入口和文档

- 保留 `src/demos`、`src/demos/live`、`ui-demos.html`、`studio-live.html` 以及 Vite 三入口，作为可追溯基准和独立调试工具；不把它们接入正式导航，不移入正式生产依赖。
- 检查 `src/main.js`、`App.vue` 及全部非 Demo、非测试的 JS／Vue 导入，无 `demos`、`catalog.json`、`demoKey`、`createDemo` 依赖；正式浏览器旅程若请求 `/src/demos/` 则失败。构建后正式 `index.html` 不引用 Demo 入口脚本。
- 将 `control-plane/README.md` 的 Vue 模板说明替换为实际说明：Node 24.15+／24.x、安装／开发／构建／测试命令、`TENSOR_BACKEND_URL` 默认 8080、三个正式路由、单次／批量任务及结果不确定恢复、筛选草稿与已提交分页、键盘表格／提示操作、主题持久化、PC 范围。链接两个独立 Demo 文档与验证记录。
- 更新 `src/demos/README.md` 的入口说明，注明正式 Studio 入口已完成迁移且仍通过真实 API 工作；保留快照日期、24 行／8 列限制和模拟执行说明。

## Files

- 新增 `control-plane/e2e/studio-acceptance.spec.js`；按旅程需要最小扩展 `ui-redesign.fixtures.js` 的测试响应，保留已有夹具契约。
- 修改清理范围中列出的两份页面测试、`control-plane/src/style.css`，删除列出的 4 个旧组件及 3 个独立测试。
- 修改 `control-plane/README.md`、`control-plane/src/demos/README.md`。
- 新增 `docs/verification/STUDIO-T10.md`、`docs/verification/studio-t10/`，更新任务板完成证据；不修改前九项历史验证记录来掩盖限制。

## Tests

在仓库根目录先执行 `rg -n 'WorkbenchPanel|ApiDescription|DownloadResult|MetadataField' control-plane/src control-plane/e2e`，记录引用；删除后应只剩非组件用途的测试辅助函数命名等已解释匹配。

在 `control-plane` 使用 Node 24：

1. `npm test`：清理后的全部单元／集成通过；测试总数可因删除仅覆盖死组件的测试减少，记录原因。
2. `node --test e2e/tushare-range-evidence.test.js e2e/packaged-test-environment.test.js`：离线证据与打包测试环境检查通过。
3. 启动 `npm run dev -- --host 127.0.0.1 --port 4174 --strictPort`，运行 `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 TENSOR_UI_BASE_URL=http://127.0.0.1:4174 npm run test:e2e -- studio-acceptance.spec.js studio-shell.spec.js studio-catalog.spec.js studio-form.spec.js studio-submission.spec.js studio-tasks.spec.js studio-detail.spec.js studio-recovery.spec.js studio-datasets.spec.js studio-table.spec.js ui-redesign.spec.js download-tasks.spec.js stock-download-parameters.spec.js`：跨页面、各模块、四主题、桌面和键盘回归通过。
4. `npm run build`；`npm run test:e2e -- --config=playwright.ui.config.js`：正式产物预览上的 UI 回归通过。清空 shell 中 `TENSOR_UI_BASE_URL` 以使用该配置的 4173 预览服务；保留既有 bundle 大小提示并记录，不以本任务名义展开打包优化。
5. 根目录 `git diff --check`；检查全部新增截图、生产引用与 Git 跟踪状态。

真实 MySQL、Tushare 凭据或 packaged 服务集成结果须与上述 HTTP 夹具分开记录。若没有执行，不声称运行过；T05 的后端参数／一致性证据继续按其原记录引用，本任务不重新修改该契约。

## Acceptance

两条跨页面旅程和三种桌面宽度通过；三主页面／任务详情有最终对照证据；正式入口无 Demo 数据依赖；列出的删除项有引用证据且无业务能力损失；更新的使用说明可执行；受影响测试、构建、产物预览通过；限制如实记录，新文件加入 Git。

## Risks

同一工作区包含此前全部任务的暂存改动，必须保留，不得以清理名义回滚。真实后端和前端需一起发布 T05 的 statusGroup 契约，但本任务不发布。既有 ResizeObserver 开发日志和 bundle 提示只在实际复现时记录，不将其混同于新 pageerror。测试夹具验证接线与呈现，不能证明真实上游采集可用。该设计已完成，T10 仅准备为 READY，等待用户另行启动。
