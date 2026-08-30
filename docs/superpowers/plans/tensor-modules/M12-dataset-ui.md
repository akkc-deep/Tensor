# M12 Dataset Query Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现按实际字段动态筛选、全字段宽表展示和服务端分页的数据查看页面。

**Architecture:** 数据集定义驱动筛选和列展示；`useDatasetQuery` 隔离查询生命周期与竞态。表格只渲染当前页，不裁剪宽表，不在前端计算或排序高精度字符串。

**Tech Stack:** Vue 3、Element Plus、Axios、Vitest、Vue Test Utils。

**Spec:** `docs/superpowers/specs/2026-08-25-tensor-module-roadmap-design.md`

## Global Constraints

- 选择数据集后不自动查询；无条件查询允许但必须服务端分页。
- 只展示实际存在的 `ts_code`、`trade_date`、`ann_date` 筛选，条件关系为 AND。
- 所有业务字段加三个来源字段完整展示；152 列必须横向滚动。
- 不提供排序、列配置、行选择、编辑、删除或导出。

## Project Inputs

候选输入为 M10 API、格式化接口和数据查看文件。数据查看页面不依赖 Java 或 SQL 实现。

---

### Task M12-T01: 数据集选择与动态筛选（3.0h）

**Files:**
- Create: `control-plane/src/components/dataset/DatasetSelect.vue`, `DynamicFilterForm.vue`
- Create: `control-plane/src/composables/useDatasetFilters.js`
- Test: `control-plane/src/components/dataset/DatasetSelect.spec.js`, `control-plane/src/components/dataset/DynamicFilterForm.spec.js`, `control-plane/src/composables/useDatasetFilters.spec.js`

**Interfaces:** Filters emit `tsCode`, `tradeDateFrom/To`, `annDateFrom/To`; `reset()` clears filters/results/page but caller retains source/dataset.

- [ ] Confirm task design; freeze field-to-filter mapping and date range semantics.
- [ ] Test dataset switch reset, ts-code only, date only, both dates, no filters, AND output, invalid code/range and reset behavior.
- [ ] Run tests and confirm missing components fail.
- [ ] Implement only from dataset `filters`; never inspect concrete API name or infer from columns beyond supplied metadata.
- [ ] Run tests and commit as `feat(ui): add dataset selection and dynamic filters` when Git exists.

### Task M12-T02: 全字段宽表（3.5h）

**Files:**
- Create: `control-plane/src/components/dataset/DatasetTable.vue`
- Test: `control-plane/src/components/dataset/DatasetTable.spec.js`

**Interfaces:** Props `columns`, `items`, `loading`; fixes `ts_code` or first business column; displays source fields last.

- [ ] Confirm task design; freeze column keys/labels/logical types and tooltip truncation.
- [ ] Test exact column order, 152 columns, horizontal scroll, fixed column choice, null/zero/empty/high precision/date/ingested time and long-text tooltip.
- [ ] Run tests and confirm missing table fails.
- [ ] Implement dynamic `el-table-column` rendering and format via M10 utilities without `v-html` or front-end sorting.
- [ ] Run tests and build; inspect DOM to ensure no field is silently dropped.
- [ ] Commit as `feat(ui): render complete dataset tables` when Git exists.

### Task M12-T03: 服务端分页组件（2.0h）

**Files:**
- Create: `control-plane/src/components/dataset/DatasetPagination.vue`
- Test: `control-plane/src/components/dataset/DatasetPagination.spec.js`

**Interfaces:** Props page/pageSize/totalElements/totalPages/disabled; emits page and page-size changes; sizes exactly `[20,50,100]`.

- [ ] Confirm task design; freeze empty and zero-page rendering.
- [ ] Test default 50, all sizes, page change, disabled loading, total display and empty total.
- [ ] Run tests and confirm missing component fails.
- [ ] Implement controlled pagination with visible labels and keyboard support.
- [ ] Run tests and commit as `feat(ui): add server dataset pagination` when Git exists.

### Task M12-T04: 查询生命周期与竞态（3.0h）

**Files:**
- Create: `control-plane/src/composables/useDatasetQuery.js`
- Test: `control-plane/src/composables/useDatasetQuery.spec.js`

**Interfaces:** States `UNQUERIED|LOADING|SUCCESS|EMPTY|FAILURE`; actions query, retry, changePage, changePageSize, reset; stale generation ignored.

- [ ] Confirm task design; freeze old-result hiding, page reset and server normalized page acceptance.
- [ ] Test no auto query, loading clears old table, success/empty/failure, retry, stale response, page preservation, filter/page-size reset to page 1 and out-of-range normalized page.
- [ ] Run tests and confirm missing composable fails.
- [ ] Implement with M10 API client and generation checks; preserve current source/dataset on reset.
- [ ] Run tests and commit as `feat(ui): manage dataset query lifecycle` when Git exists.

### Task M12-T05: DatasetView 集成（2.5h）

**Files:**
- Modify: `control-plane/src/views/DatasetView.vue`
- Test: `control-plane/src/views/DatasetView.spec.js`
- Modify: `control-plane/src/router/index.js`

**Interfaces:** Route `/datasets`; layout selection → filters/actions → state/total → table → pagination.

- [ ] Confirm task design; confirm child/component contracts and OpenAPI mocks are stable.
- [ ] Test unqueried guidance, query/reset, empty, failure/retry, page/size, switch reset, balancesheet width, no mutating controls and keyboard operation.
- [ ] Run view test and confirm the initial M10 view fails the full dataset-query assertions.
- [ ] Replace the initial view body with assembled components/composables while keeping the stable route and page heading.
- [ ] Run full frontend suite/build and commit as `feat(ui): complete dataset query page` when Git exists.

## Module Gate

Run `cd control-plane && npm run test:unit -- --run && npm run build`. M12 is complete only when dynamic filters, full-width table, paging, race handling, readonly limitations and accessibility tests pass.
