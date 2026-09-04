# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M12-T05`
- **Next task:** `M13-T01`
- **Design document:** `docs/task-designs/M13-T01-design.md`
- **Expected next status:** `READY`; transition from `NOT_STARTED` only after this handoff is written and linked.

## Next Task

- **ID:** `M13-T01`
- **Title:** 前端确定性构建及静态资源复制
- **Goal:** 让 `tensor-app` 的 Maven `generate-resources` 使用固定 Node/npm 依次完成 lockfile 安装、完整前端单测、Vite build 和资源复制，产生可由 M13-T02 消费的 `target/generated-resources/static/index.html` 及其哈希 JS/CSS。
- **Scope:** 只修改 `data-plane/tensor-app/pom.xml` 并创建 `data-plane/tensor-app/src/test/java/com/akkc/tensor/build/FrontendResourceBuildTest.java`；使用 frontend-maven-plugin 1.15.4、Node v24.15.0、npm 11.12.1 与 maven-resources-plugin 3.4.0；不修改前端、根 POM、业务模块、JAR/生产 Web/运行说明，也不提交生成物或读取 Git 元数据。
- **Acceptance criteria:** `generate-resources` 按 `npm ci → 20 files / 120 tests → Vite build → copy-resources` 顺序成功，复制后的 index 引用至少一个哈希 JS 和一个哈希 CSS；批准的聚焦 Maven 命令与完整 reactor 测试退出 0，lockfile 和受保护路径无差异，Maven/Java 无 Git 读取，实现提交精确为一新增、一修改且消息为 `build: integrate frontend assets into app`。

## Dependencies

### `M10-T02`

- **Artifact:** `docs/task-designs/M10-T02-design.md`，以及已完成的 `control-plane/src/main.js`、`control-plane/src/router/index.js`、`control-plane/src/App.vue`、`control-plane/src/layouts/AppLayout.vue` 和稳定生产 Vite 构建表面。
- **Decision:** 生产入口安装 Vue Router 与全量 Element Plus，`/downloads`、`/datasets` 和 404 使用稳定路由；Vite build 输出 `dist/index.html` 与哈希 assets，并允许唯一已批准的 Element Plus 大 chunk 提示。
- **Rationale:** M13-T01 必须构建真实桌面应用壳和可直接刷新的业务入口，而不是示例页或手工资源；既有大 chunk 提示是已审议风险，不应通过修改 Vite 配置掩盖。
- **Constraint:** 不修改入口、router、layout、Vite 配置或 Element Plus 安装策略；生产构建必须退出 0，只允许既有 `Some chunks are larger than 500 kB after minification` 提示。
- **Usage:** frontend-maven-plugin 在 `control-plane` 运行既有 `npm run build`，resources 插件把其完整 `dist` 原样复制到 app 生成资源目录。
- **Readiness evidence:** 权威看板记录 M10-T02 为 `COMPLETED`；最终提交 `d3d4be7`、`993dd3c` 已通过 Node 24.15.0 下 7/7 测试、Vite 8.2.2 build、范围/安全门禁和无 Critical/Important/Minor 的独立复审。

### `M11-T05`

- **Artifact:** `docs/task-designs/M11-T05-design.md`、`control-plane/src/views/DownloadView.vue`、`control-plane/src/views/DownloadView.spec.js` 及真实应用壳回归。
- **Decision:** `/downloads` 已由唯一 `useDownloadFlow()` 和受控组件完成元数据加载、参数校验、同步下载、状态分流、失败重试和选择切换，不再是占位页。
- **Rationale:** Maven 资源构建必须在复制前运行完整单测，确保打包输入包含已完成且行为通过的下载页面，而不是只证明 Vite 能编译。
- **Constraint:** 不修改或 mock 下载页面、API、composable、组件或测试；前端单测必须作为 `generate-resources` 的阻断步骤，失败时不得继续 Vite build 或资源复制。
- **Usage:** `npm run test:unit -- --run` 回归该页面与应用壳，随后的 Vite build 将页面代码纳入哈希 bundle。
- **Readiness evidence:** 权威看板记录 M11-T05 为 `COMPLETED`；实现提交 `dda4f9d` 与修复提交 `e84f21c` 最终通过当时 75/75 全量测试、Vite build、安全/范围门禁和无 Critical/Important 的独立复审。

### `M12-T05`

- **Artifact:** `docs/task-designs/M12-T05-design.md`、`control-plane/src/views/DatasetView.vue`、`control-plane/src/views/DatasetView.spec.js` 和 `control-plane/src/layouts/AppLayout.spec.js`。
- **Decision:** `/datasets` 已完成元数据 generation、动态筛选、五态查询、全字段表格、服务端分页、重试与 reset；当前控制面完整基线为 20 files / 120 tests。
- **Rationale:** M13-T01 的最终前端输入必须同时包含已完成的数据查询页面，并以全量测试而非单一资源存在性证明其可用。
- **Constraint:** 不修改数据查询页面、router、API、composable、组件、布局或测试；必须使用 Node v24.15.0 和提交的 lockfile 运行全部 120 项测试，构建只允许既有大 chunk 提示。
- **Usage:** Maven 内的前端单测验证最终控制面，Vite build 生成同时包含下载页和数据查看页的 index、JS、CSS，随后统一复制。
- **Readiness evidence:** 权威看板记录 M12-T05 为 `COMPLETED`；实现提交 `25b4fae`、测试强化 `60e4a42`、选择器修复 `95d04c8` 最终通过聚焦 11/11、完整 120/120、router 3/3、Vite build、范围/安全门禁及无 Critical/Important/Minor 的独立复审。

三个直接依赖无冲突：M10-T02 固定生产入口、路由与 Vite 输出边界，M11-T05 和 M12-T05 分别完成该入口下的两个业务页面；M13-T01 不改变这些行为，只用固定工具链先回归其共同的 120 项测试，再构建并复制同一份生产输出。三项均接受同一个既有 Element Plus 大 chunk 提示，且都要求 Node 24.15.0、稳定 router 和前端受保护路径不变。

## Start Here

按以下顺序读取：

1. `docs/task-designs/M13-T01-design.md`
2. `docs/superpowers/plans/2026-09-05-m13-t01-frontend-resource-build.md`
3. `docs/task-handoffs/tensor-v1-task-board.md` 的 M13-T01 行与详情
4. `docs/superpowers/plans/tensor-modules/M13-packaging-runbook.md` 的 Global Constraints 与 Task M13-T01
5. `data-plane/tensor-app/pom.xml`
6. `control-plane/package.json`、`control-plane/package-lock.json`、`control-plane/vite.config.js` 和 `control-plane/vitest.config.js`
7. 三项直接依赖的设计及本交接 Dependencies 中列出的当前前端产物

首个实施动作：确认两个实施文件没有重叠改动且 `data-plane/tensor-app/target/generated-resources/static` 仍不存在，保留当前范围外 `.idea/misc.xml` 与 `data-plane/**/target/`；只创建计划给出的完整 `FrontendResourceBuildTest.java`，保持 POM 不变，运行带 `-Dsurefire.failIfNoSpecifiedTests=false` 的聚焦命令，取得只因 app 生成资源不存在而失败的严格 RED。

## Risks

- 首次 Maven 构建需要下载固定 Node/npm 和 lockfile 依赖；下载不可用时必须明确失败并请求原命令的网络授权，不得回退系统工具链或改变版本。
- 所有到达 `tensor-app:generate-resources` 的 Maven 生命周期都会运行完整前端安装、120 项单测和 build，时间成本是批准的阻断门禁，不增加 skip profile。
- 资源测试冻结哈希 JS/CSS 的存在及 index 引用，不证明跨机器字节 checksum；确定性由固定工具链、lockfile、`npm ci` 和固定 Vite 配置提供。
- 当前工作树存在范围外 `.idea/misc.xml` 与 `data-plane/**/target/`；`control-plane` 无未提交变化且 app 的 `target/generated-resources/static` 已确认不存在。实施必须精确暂存两个目标文件并保留其他内容。
