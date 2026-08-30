# M14 Integration and Release Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在所有模块独立通过后，以页面黑盒测试完成 fixture、真实 Tushare 49 接口、性能、安全、全新环境和发布准入验证。

**Architecture:** 本模块只编写/执行 Playwright、shell 验证和证据文档，不直接修改 M00–M13 生产实现。发现缺陷时建立单语言缺陷修复任务；修复完成后重跑精确失败用例和整个任务矩阵。

**Tech Stack:** Playwright、shell、curl、jq、MySQL 8.4 client、Maven、npm、Actuator/Micrometer。

**Spec:** `docs/superpowers/specs/2026-08-25-tensor-module-roadmap-design.md`

## Global Constraints

- M00–M13 模块门禁全部通过后才可进入本模块。
- 所有用户验收从已打包 JAR 的页面入口完成；除故障准备和验证证据外，不用直接 API/SQL 替代页面操作。
- 集成任务不得顺手修改 Java、YAML、SQL 或 Vue 生产文件。
- 发现缺陷时建立独立的单语言修复任务，并在其任务设计中明确根因、文件边界、失败测试和回归范围。
- 真实 Token、数据库密码和完整上游响应不得写入测试截图、trace、日志或证据包。

## Project Inputs

候选输入为公开契约、运行说明、测试文件和指定既有验收证据。集成验证不读取 M00–M13 生产实现。

---

### Task M14-T01: Fixture 页面主闭环（4.0h）

**Context boundary:** Read M00 OpenAPI, M13 runbook, UI accessible labels and fixture public descriptor only. Do not read backend implementation.

**Files:**
- Create: `control-plane/e2e/fixture-flow.spec.js`
- Create: `docs/verification/M14-T01-fixture-flow.md`

**Interfaces:** Browser flows cover app start, both routes, fixture selection, valid download, dataset query and plugin disable behavior.

- [ ] Confirm task design; freeze test selectors by role/label rather than CSS internals.
- [ ] Start packaged JAR with acceptance profile, clean MySQL schema and fixture enabled; wait for health readiness.
- [ ] Write Playwright tests that select fixture, submit SUCCESS, assert source/insert/update counts, open datasets, filter by `000001.SZ`, query and verify the row/source fields.
- [ ] Add EMPTY flow and fixture-disable restart; assert empty feedback and absence of fixture while Tushare descriptor remains unaffected.
- [ ] Run `cd control-plane && npx playwright test e2e/fixture-flow.spec.js`; expect all tests pass.
- [ ] Record command, versions, pass count and redacted screenshots in the evidence file; create single-language defect correction tasks for failures without editing production code.
- [ ] Commit E2E/evidence as `test(e2e): verify fixture user flow` when Git exists.

### Task M14-T02: 下载失败、幂等和回滚矩阵（4.0h）

**Files:**
- Create: `control-plane/e2e/download-outcomes.spec.js`
- Create: `docs/verification/M14-T02-download-outcomes.md`

**Interfaces:** Covers PRD AC-004～011 with fixture/upstream stubs through the page.

- [ ] Confirm task design; list exact setup for success, empty, validation, auth, permission, timeout, payload, adapter and persistence failures.
- [ ] Write UI tests for missing required value/reversed range blocked client-side and safe error summaries for each server failure class.
- [ ] Test duplicate SUCCESS twice, then query the page and assert one business row with updated `ingested_at` and correct second-run update count.
- [ ] Trigger TYPE_FAILURE and PERSISTENCE_FAILURE, then query the page and prove no partial row became visible.
- [ ] Run `npx playwright test e2e/download-outcomes.spec.js`; expect all matrix rows pass and no progress/cancel/history UI appears.
- [ ] Inspect app logs by request ID for one final event per request and no Token/raw SQL; record redacted evidence.
- [ ] Commit as `test(e2e): verify download outcome matrix` when Git exists.

### Task M14-T03: 查询、分页、宽表与无障碍（4.0h）

**Files:**
- Create: `control-plane/e2e/dataset-query.spec.js`
- Create: `docs/verification/M14-T03-dataset-query.md`

**Interfaces:** Covers AC-012～016, PRD 6 and TRD 13.5–13.7.

- [ ] Confirm task design; prepare deterministic datasets with no filters, ts-code, both date fields, more than 100 rows and 152 columns.
- [ ] Write tests for no auto-query, unfiltered query, AND filters, dynamic filter absence, reset semantics, 20/50/100 paging, total/page retention and server normalized last page.
- [ ] Verify old table hides during a new query and a delayed stale response cannot replace the current dataset.
- [ ] Verify balancesheet renders 152 business columns plus three source columns, fixed first column, horizontal scroll, null/zero/empty/high-precision distinctions and tooltip text.
- [ ] Navigate/select/input/query/page with keyboard; assert labels, focus, aria-live and non-color error text; assert no edit/delete/export controls.
- [ ] Run `npx playwright test e2e/dataset-query.spec.js`; record results/screenshots and create single-language defect correction tasks for failures.
- [ ] Commit as `test(e2e): verify read-only dataset UX` when Git exists.

### Task M14-T04: 49 数据集自动契约与页面驱动（4.0h）

**Files:**
- Create: `control-plane/e2e/tushare-metadata.spec.js`
- Create: `scripts/verify-49-contracts.sh`
- Create: `docs/verification/M14-T04-49-contracts.md`

**Interfaces:** Script runs Maven M03/M04 contracts and Playwright asserts page descriptors for exactly the manifest 49 APIs/datasets.

- [ ] Confirm task design; freeze independent expected API list from `manifest.json` and PRD categories/parameters.
- [ ] Write shell script with `set -eu` to run 49 metadata tests, Flyway schema contract, package JAR content contract and report exact pass counts.
- [ ] Write Playwright loop selecting all 49 download APIs and asserting category, description, parameter controls/required state; select all 49 datasets and assert filter definitions.
- [ ] Run `scripts/verify-49-contracts.sh`; expect 49 YAML, 49 production tables and 49 packaged YAML with no extras.
- [ ] Run `npx playwright test e2e/tushare-metadata.spec.js`; expect 49/49 API and dataset cases pass.
- [ ] Record versioned manifest hash, counts and commands without including source data rows.
- [ ] Commit as `test(release): verify all 49 dataset contracts` when Git exists.

### Task M14-T05: 真实 Tushare 49 接口页面验收（4.0h）

**Files:**
- Create: `control-plane/e2e/tushare-live.spec.js`
- Create: `docs/verification/M14-T05-tushare-live.md`

**Interfaces:** Uses manifest sample parameters and a credential supplied only through `TENSOR_TUSHARE_TOKEN`; every call originates from the page.

- [ ] Confirm task design; verify controlled environment, API permissions, rate limits and redaction controls before using the live Token.
- [ ] Generate test cases at runtime from public manifest names/parameters without serializing the Token; page-select each API and submit its legal sample.
- [ ] For sample status `ok`, assert nonzero source count then query the same dataset through the page and verify a matching record; for `empty`, assert legal zero result and separately run fixture adaptation coverage already proven by M14-T02.
- [ ] Capture auth/permission failures as environment blockers, not product success; after credentials/permissions are corrected rerun the same unchanged task.
- [ ] Run `npx playwright test e2e/tushare-live.spec.js --workers=1`; expect 49 completed cases with no automatic retry.
- [ ] Search Playwright artifacts, app logs and evidence for the actual Token and remove any artifact containing it; evidence records only API, outcome, counts, request ID and duration.
- [ ] Commit test/evidence metadata without credentials as `test(release): verify live Tushare interfaces` when Git exists.

### Task M14-T06: Daily 与 balancesheet 性能验证（4.0h）

**Files:**
- Create: `scripts/performance/verify-query-p95.sh`
- Create: `control-plane/e2e/loading-feedback.spec.js`
- Create: `docs/verification/M14-T06-performance.md`

**Interfaces:** 50-row API P95 ≤2s; UI loading feedback ≤300ms; 100-row maximum; performance evidence includes data volume, indexes, P50/P95/max, EXPLAIN and resource use.

- [ ] Confirm task design; freeze dataset sizes, warmup count, measured request count and machine/environment description.
- [ ] Write shell script that performs 10 warmups and 100 measured bound-filter requests, collects curl `time_total`, calculates P50/P95/max with deterministic sort/awk and fails when P95 exceeds 2.000 seconds.
- [ ] Collect MySQL `EXPLAIN` for daily ts-code/date and balancesheet ts-code/ann-date queries; assert selected key is not null and rows are bounded by test data.
- [ ] Write Playwright timing test that clicks query and observes accessible loading state within 300ms for both datasets; verify only current page rows render and 152-column view remains operable.
- [ ] Run the performance script and Playwright test on cold/warm cases; capture CPU, heap and connection-pool metrics from approved endpoints.
- [ ] Record exact measurements and environment; create module-specific defect correction tasks for failures, then rerun unchanged benchmarks.
- [ ] Commit as `test(perf): verify query and wide-table targets` when Git exists.

### Task M14-T07: 安全与运行控制验证（3.0h）

**Files:**
- Create: `scripts/security/verify-release.sh`
- Create: `docs/verification/M14-T07-security.md`

**Interfaces:** Verifies credentials, read-only APIs, SQL binding, headers, dependencies, Git capability ban, actuator exposure and network assumptions.

- [ ] Confirm task design; define exact secret canaries and allowed actuator endpoints.
- [ ] Write script scanning source, JAR strings, HTTP responses, logs and Playwright artifacts for canary Token/password; fail on any match.
- [ ] Add requests proving no mutating dataset route, arbitrary table/column/sort/SQL inputs are rejected, HTML is escaped and security headers exist.
- [ ] Run Maven Enforcer/ArchUnit/Git capability tests and dependency vulnerability scan; fail on unaccepted high severity findings.
- [ ] Verify production CORS disabled, config/env actuator unavailable, health safe, fixture absent and external access control requirement documented.
- [ ] Run `scripts/security/verify-release.sh`; expect exit 0 and write a redacted evidence summary.
- [ ] Commit as `test(security): verify release controls` when Git exists.

### Task M14-T08: 全新环境验收与发布证据包（3.0h）

**Files:**
- Create: `docs/verification/release-checklist.md`
- Create: `docs/verification/ac-001-018.md`
- Create: `docs/verification/release-summary.md`

**Interfaces:** Final evidence links every requirement/AC to a fresh command, test result or controlled manual observation.

- [ ] Confirm task design; require M14-T01～T07 evidence and zero unresolved defects or design gaps.
- [ ] On a clean environment, follow M13 runbook with only Java 21, MySQL 8.4, DB config and live Token; do not read implementation source while operating.
- [ ] Run packaged smoke, Maven verify, frontend tests, Playwright suite, 49 contract script, live test, performance and security scripts; record commands, timestamps and exit codes.
- [ ] Fill AC-001 through AC-018 individually with evidence links and record P0/P1 status, 49/49 counts, performance numbers and secret-scan result.
- [ ] Verify scope-excluded features have no half-finished routes or controls and production JAR contains no fixture/test resources.
- [ ] Mark release ready only if every gate passes; otherwise list exact blocking defect task IDs and do not accept M14-T08.
- [ ] Commit the redacted evidence package as `docs: record Tensor v1 release verification` when Git exists.

## Module Gate

M14 is complete only when M14-T01～T08 have passed, every defect correction task and design gap is closed, AC-001～018 and PRD-F-001～031 have fresh evidence, 49 live cases have controlled outcomes, performance/security gates pass and the clean-environment runbook succeeds.
