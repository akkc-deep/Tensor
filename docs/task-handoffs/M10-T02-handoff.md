# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M10-T01`
- **Next task:** `M10-T02`
- **Design document:** `docs/task-designs/M10-T02-design.md`
- **Expected next status:** `READY`

## Next Task

- **Task ID:** `M10-T02`
- **Title:** `/downloads`、`/datasets` 路由和桌面布局
- **Goal:** 把 Vue/Vite 示例页替换为 Tensor 控制面的稳定桌面应用壳：`/` 重定向到 `/downloads`，固定 `/downloads`、`/datasets` 和可恢复的 catch-all 404，并以语义化顶部导航承载两个最终业务入口。
- **Scope:** 创建 router factory、语义化 `AppLayout`、三个无状态 view 和两份新测试，修改 App、生产入口、全局样式与既有 smoke test，并删除五个无引用示例文件。保持 M10-T01 的 package、lockfile、Vite/Vitest/Playwright 配置和测试 setup 不变；不实现 API、业务表单、Pinia、E2E、服务端 SPA fallback 或其他后续任务内容。
- **Acceptance:** `/`、`/downloads`、`/datasets` 与未知路径按固定路由合同工作；每页只有一个最终标题，两个业务页显示批准的未完成引导，404 可返回下载页；顶部 `nav` 恰有两个可聚焦 RouterLink，当前项同时具有 `aria-current="page"` 和可见样式；`body` 最小宽度 1280px 且窄视口使用原生横向滚动；3 files/7 tests、生产构建、示例清理和精确 16 文件范围门禁通过且不访问网络。

## Dependencies

### M10-T01

- **Artifact:** M10-T01 实现提交 `90c2029` 中 `control-plane/package.json`、`control-plane/package-lock.json`、`control-plane/vite.config.js`、`control-plane/vitest.config.js`、`control-plane/playwright.config.js`、`control-plane/src/test/setup.js` 与 `control-plane/src/App.spec.js` 的前端依赖、构建和测试基线。
- **Decision:** 保留 Node `>=24.15.0 <25`、十个精确顶层依赖、官方 registry lockfile v3、Vue Router 4.6.4、Element Plus 2.14.5、Vitest/jsdom/VTU setup，以及既定 `test`、`test:unit`、`build` 等脚本；生产入口安装 router 与 Element Plus，路由测试通过 memory history 隔离状态。
- **Rationale:** M10-T01 已统一可重复安装、Vue 组件测试、桌面 Chrome E2E 配置和生产构建边界，使 M10-T02 能只实现路由与应用壳，无需重新选择依赖、工具链或测试环境。
- **Constraint:** 不修改 package、lockfile、Vite/Vitest/Playwright 配置或测试 setup；所有 npm/Vitest/Vite 命令使用 Node 24.15.0；不调用网络 API、不安装或运行浏览器、不创建 E2E，并保持后端地址与凭证不进入浏览器代码。
- **Usage:** 直接消费已安装的 Vue Router 和 Element Plus；用 `createMemoryHistory()`、现有 jsdom/VTU setup 与 `npm run test:unit -- --run` 验证真实 RouterLink/RouterView，再用 `npm test` 和 `npm run build` 完成回归与生产 bundle 验证。
- **Readiness evidence:** M10-T01 在权威看板中为 `COMPLETED`，实现提交为 `90c2029`；当前 `control-plane` 相对该提交无差异。已记录的官方 registry `npm ci` 安装 172 packages/审计 173 packages 且 0 vulnerabilities，`test:unit` 与 `npm test` 各为 1 file/1 test，Vite 8.2.2 build 16 modules 成功，独立审查为 `Ready to merge: Yes` 且无 Critical/Important/Minor。

## Start Here

1. `docs/task-designs/M10-T02-design.md` 全文。
2. `docs/superpowers/plans/2026-09-04-m10-t02-routes-desktop-layout.md` 全文。
3. `docs/task-handoffs/tensor-v1-task-board.md` 的 M10-T02 行与任务详情。
4. `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md` 的 M10-T02 任务卡、Global Constraints 和 Module Gate。
5. `docs/task-designs/M10-T01-design.md` 与当前 `control-plane/package.json`、测试配置、setup、App/入口/样式和示例基线。
6. **First action:** 确认工作树为空，切换到 Node 24.15.0，以官方 registry 运行 `npm ci`、现有 1 file/1 test 基线和生产构建；随后只写设计规定的三份测试并运行聚焦命令，取得仅因 router/layout/view 目标模块不存在而失败的严格 RED。

## Risks

- 生产 router 使用 `createWebHistory()`，但打包 JAR 对 history 路由直接刷新的 SPA fallback 属于 M13-T03；本任务只验证客户端导航与生产 bundle。
- 测试 setup 已全局安装 Element Plus，可能掩盖生产入口遗漏 plugin；因此必须同时按设计审查 `main.js` 的生产安装，不能只依赖组件测试。
- jsdom 可验证路由、语义导航、DOM focus 和 active 属性，但桌面像素布局、横向滚动与焦点可见性的最终浏览器验收属于 M14-T03。
