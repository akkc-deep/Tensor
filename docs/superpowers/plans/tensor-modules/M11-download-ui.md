# M11 Download Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现元数据驱动的单接口下载页面，完整区分成功、合法空数据和失败。

**Architecture:** 选择组件和动态表单只消费 API 描述符；`useDownloadFlow` 管理加载、选择、校验、提交和请求世代。切换来源/接口清空旧状态，较早响应不能覆盖当前选择。

**Tech Stack:** Vue 3 Composition API、Element Plus、Axios、Vitest、Vue Test Utils。

**Spec:** `docs/superpowers/specs/2026-08-25-tensor-module-roadmap-design.md`

## Global Constraints

- 首期仅手动单接口同步下载，不显示阶段进度、百分比、取消或历史。
- 参数控件完全由元数据类型生成，不按 Tushare API 名写分支。
- 提交期间锁定来源、接口、参数和按钮；失败后保留参数供重试。

## Project Inputs

候选输入为 M10 API、通用组件接口和下载页文件。下载页面不依赖 Java 或 SQL 实现。

---

### Task M11-T01: 数据源与接口选择（2.5h）

**Files:**
- Create: `control-plane/src/components/download/DataSourceSelect.vue`, `ApiSelect.vue`, `ApiDescription.vue`
- Test: `control-plane/src/components/download/DataSourceSelect.spec.js`, `control-plane/src/components/download/ApiSelect.spec.js`, `control-plane/src/components/download/ApiDescription.spec.js`

**Interfaces:** Props use M10 normalized descriptors; emits `update:modelValue`; API options group by eight categories and search apiName/displayName.

- [ ] Confirm task design; freeze selection/reset and unavailable-reason presentation.
- [ ] Test single-source default, unavailable source disabled, 49 grouped options, search and change emits.
- [ ] Run component tests and confirm missing components fail.
- [ ] Implement visible labels and keyboard-operable Element Plus controls without embedding the 49 API list.
- [ ] Run tests and commit as `feat(ui): add download source and API selectors` when Git exists.

### Task M11-T02: 动态参数表单（3.0h）

**Files:**
- Create: `control-plane/src/components/download/DynamicParameterForm.vue`
- Create: `control-plane/src/composables/useParameterForm.js`
- Test: `control-plane/src/components/download/DynamicParameterForm.spec.js`

**Interfaces:** Accepts `ParameterDescriptor[]`; exposes `validate(): Promise<boolean>`, `normalizedValues()` and `reset()`.

- [ ] Confirm task design; map every parameter type to the exact Element Plus control and submit format.
- [ ] Test DATE, DATE_RANGE_MEMBER, MONTH, TS_CODE, ENUM, TEXT, no-parameter API, required, bad code and reversed range with first-error focus.
- [ ] Run targeted tests and confirm missing form fails.
- [ ] Implement descriptor-driven rendering/validation with visible labels and `aria-describedby`; normalize dates to `YYYYMMDD`, month to `YYYYMM`, code to trimmed uppercase.
- [ ] Run tests and confirm no component branch compares a concrete `apiName`.
- [ ] Commit as `feat(ui): render download parameters from metadata` when Git exists.

### Task M11-T03: 下载状态与竞态控制（3.0h）

**Files:**
- Create: `control-plane/src/composables/useDownloadFlow.js`
- Test: `control-plane/src/composables/useDownloadFlow.spec.js`

**Interfaces:** State `INITIAL|METADATA_LOADING|READY|SUBMITTING|SUCCESS|EMPTY|FAILURE`; actions load/select/submit/retry; monotonically increasing generation ignores stale responses.

- [ ] Confirm task design; freeze state transitions and which values reset/persist.
- [ ] Test metadata loading/failure, source/API switch reset, duplicate submit prevention, success/empty/failure, retry parameter retention and stale response suppression.
- [ ] Run tests and confirm missing composable fails.
- [ ] Implement reactive state and generation checks around M10 API calls; do not persist tasks or progress.
- [ ] Run tests and commit as `feat(ui): manage download request lifecycle` when Git exists.

### Task M11-T04: 下载结果反馈（1.5h）

**Files:**
- Create: `control-plane/src/components/download/DownloadAction.vue`, `DownloadResult.vue`
- Test: `control-plane/src/components/download/DownloadResult.spec.js`

**Interfaces:** SUCCESS shows source/insert/update counts; EMPTY exact text `下载成功，0 条数据`; FAILURE shows safe summary and request ID, with retry affordance when appropriate.

- [ ] Confirm task design; align count labels and error wording with PRD 5.7/9.
- [ ] Test three outcomes, disabled/submitting button, no stage progress and no Token/raw details.
- [ ] Run tests and confirm missing components fail.
- [ ] Implement accessible `aria-live` feedback and locked action.
- [ ] Run tests and commit as `feat(ui): show final download outcomes` when Git exists.

### Task M11-T05: DownloadView 集成（2.0h）

**Files:**
- Modify: `control-plane/src/views/DownloadView.vue`
- Test: `control-plane/src/views/DownloadView.spec.js`
- Modify: `control-plane/src/router/index.js`

**Interfaces:** Route `/downloads`; composition order source → API → description → params → action → result.

- [ ] Confirm task design; confirm all child contracts and M10 API mocks are stable.
- [ ] Write integration tests for initial guidance, 49 metadata flow, invalid submit block, success, empty, failure, switch reset and keyboard sequence.
- [ ] Run view tests and confirm the initial M10 view fails the full download-flow assertions.
- [ ] Replace the initial view body with assembled components/composable while keeping the stable route and page heading; keep result local to page lifecycle.
- [ ] Run full frontend unit suite/build and commit as `feat(ui): complete data download page` when Git exists.

## Module Gate

Run `cd control-plane && npm run test:unit -- --run && npm run build`. M11 is complete only when all UI states, parameter types, stale-response protection and outcome distinctions pass with mocked OpenAPI responses.
