# M10 Frontend Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Vue 示例工程改造成具有路由、Element Plus、Axios、组件测试和 E2E 基线的 Tensor 控制面。

**Architecture:** 前端只依赖 M00 OpenAPI 契约，不读取 Java 实现。页面状态保留在 composable 中，不引入 Pinia；通用 API、格式化、错误和可访问状态组件由两个页面复用。

**Tech Stack:** Vue 3.5.x、Vite 8.x、Vue Router 4.x、Element Plus 2.x、Axios 1.x、Vitest、Vue Test Utils、Playwright、Node.js 24 LTS。

**Spec:** `docs/superpowers/specs/2026-08-25-tensor-module-roadmap-design.md`

## Global Constraints

- 桌面 Chrome 为验收基准，内容最小宽度 1280px；较窄视口横向滚动但不隐藏主操作。
- 不引入 Pinia；不按 `apiName` 硬编码参数或筛选逻辑。
- 所有表单有可见标签；加载/成功/失败使用 `aria-live`；错误不只依赖颜色。
- 前端不保存、显示或发送 Token。

## Project Inputs

候选输入为 M00 OpenAPI 和前端工程文件。前端工程基线不依赖 Java、SQL 或完整产品设计文档。

---

### Task M10-T01: 依赖与测试基线（2.0h）

**Files:**
- Modify: `control-plane/package.json`
- Create/update by package manager: `control-plane/package-lock.json`
- Modify: `control-plane/vite.config.js`
- Create: `control-plane/vitest.config.js`
- Create: `control-plane/playwright.config.js`
- Create: `control-plane/src/test/setup.js`
- Test: `control-plane/src/App.spec.js`

**Interfaces:** Scripts `dev`, `build`, `test`, `test:unit`, `test:e2e`; Vite dev proxy `/api` to configurable backend.

- [ ] Confirm task design; pin exact compatible patch versions and Node 24 engine.
- [ ] Write an App smoke test that mounts the current root component; run `npm run test:unit -- --run` and confirm failure because the script and Vitest configuration do not yet exist.
- [ ] Add runtime dependencies Vue Router, Element Plus and Axios; add Vitest, VTU, jsdom and Playwright dev dependencies.
- [ ] Configure test setup to reset DOM/mocks and install Element Plus test behavior without global network calls.
- [ ] Run `npm ci`, `npm run test:unit -- --run` and `npm run build`; expect the smoke test and production build to exit 0.
- [ ] Commit package/config/test files as `build(ui): establish frontend test foundation` when Git exists.

### Task M10-T02: 路由与桌面布局（2.0h）

**Files:**
- Create: `control-plane/src/router/index.js`
- Create: `control-plane/src/layouts/AppLayout.vue`
- Create: `control-plane/src/views/NotFoundView.vue`
- Create: `control-plane/src/views/DownloadView.vue`
- Create: `control-plane/src/views/DatasetView.vue`
- Modify: `control-plane/src/App.vue`, `control-plane/src/main.js`, `control-plane/src/style.css`
- Test: `control-plane/src/router/index.spec.js`, `control-plane/src/layouts/AppLayout.spec.js`

**Interfaces:** `/` redirects `/downloads`; routes `/downloads`, `/datasets`, catch-all 404. The two initial views render final page titles and accessible “模块尚未完成” guidance, then M11/M12 replace their bodies without changing route contracts.

- [ ] Confirm task design; freeze route names `downloads`, `datasets`, `not-found` and active-nav semantics.
- [ ] Write router tests for redirect/routes/404 and layout tests for visible two-item navigation plus keyboard focus.
- [ ] Run targeted unit tests and confirm missing router/layout failure.
- [ ] Implement router, layout, the two accessible initial views and global styles with `min-width:1280px` content and visible focus styles.
- [ ] Run unit suite and build; remove Vue/Vite example assets/components no longer referenced.
- [ ] Commit as `feat(ui): add Tensor routes and desktop layout` when Git exists.

### Task M10-T03: API 客户端与 DTO 映射（2.0h）

**Files:**
- Create: `control-plane/src/api/http.js`
- Create: `control-plane/src/api/dataSources.js`
- Create: `control-plane/src/api/downloads.js`
- Create: `control-plane/src/api/datasets.js`
- Create: `control-plane/src/api/errors.js`
- Test: `control-plane/src/api/api.spec.js`

**Interfaces:** Functions `listDataSources()`, `listApis(pluginId)`, `downloadDataset(request)`, `listDatasets(pluginId)`, `getDataset(pluginId,apiName)`, `queryDataset(pluginId,apiName,criteria)`.

- [ ] Confirm task design; compare every request/response field with `docs/contracts/openapi-v1.yaml`.
- [ ] Mock Axios adapter tests for paths, snake_case dynamic params, camelCase query params, `X-Request-Id`, timeout and `ApiError` normalization.
- [ ] Run targeted tests and confirm missing clients fail.
- [ ] Implement one Axios instance with configurable base URL, timeout and safe interceptor that never logs request bodies/credentials.
- [ ] Run unit tests and ensure errors preserve code/message/retryable/fieldErrors/requestId.
- [ ] Commit as `feat(ui): add typed API client boundaries` when Git exists.

### Task M10-T04: 通用格式化与状态组件（2.0h）

**Files:**
- Create: `control-plane/src/utils/date.js`, `format.js`, `validation.js`
- Create: `control-plane/src/components/common/AsyncStatePanel.vue`
- Create: `control-plane/src/components/common/FieldError.vue`
- Test: `control-plane/src/utils/format.spec.js`, `control-plane/src/components/common/AsyncStatePanel.spec.js`

**Interfaces:** `toApiDate`, `toApiMonth`, `formatDate`, `formatIngestedAt`, `formatCell`; null maps `--`, zero and empty string remain distinct.

- [ ] Confirm task design; freeze Asia/Shanghai display fallback and no numeric parsing of decimal strings.
- [ ] Write tests for valid/invalid date/month, null/zero/empty/high-precision string and state accessibility.
- [ ] Run targeted tests and confirm missing utilities/components fail.
- [ ] Implement pure formatting/validation and reusable initial/loading/empty/failure panel with `aria-live`.
- [ ] Run unit suite and build.
- [ ] Commit as `feat(ui): add shared display and accessibility utilities` when Git exists.

## Module Gate

Run `cd control-plane && npm ci && npm run test:unit -- --run && npm run build`. M10 is complete only when routes, API boundary, formatting and accessibility primitives pass without reading backend Java implementation.
