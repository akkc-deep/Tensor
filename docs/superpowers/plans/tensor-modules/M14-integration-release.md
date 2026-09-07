# M14 Integration and Release Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在所有模块独立通过后，以页面黑盒测试完成 fixture、真实 Tushare 49 接口、性能、安全、全新环境和发布准入验证。

**Architecture:** 原预定义集成任务只编写/执行 Playwright、shell 验证和证据文档，不直接修改 M00–M13 生产实现。用户2026-09-06明确新增M14-T09承接剩余工作，允许其按确认后的ISSUE-007设计分阶段完成必要修复和验收接入；此例外不扩展其他集成任务。其他缺陷仍独立定位并按明确设计处理。

**Tech Stack:** Playwright、shell、curl、jq、MySQL 8.4 client、Maven、npm、Actuator/Micrometer。

**Spec:** `docs/superpowers/specs/2026-08-25-tensor-module-roadmap-design.md`

## Global Constraints

- M00–M13 模块门禁全部通过后才可进入本模块。
- 所有用户验收从已打包 JAR 的页面入口完成；除故障准备和验证证据外，不用直接 API/SQL 替代页面操作。
- 集成任务不得顺手修改 Java、YAML、SQL 或 Vue 生产文件。
- 发现缺陷时建立独立修复任务，并在其任务设计中明确根因、文件边界、失败测试和回归范围。M14-T09按用户此次要求合并承接ISSUE-007修复和剩余验收，修复与验收仍分阶段验证/提交；未决业务规则不因任务创建而获批准。
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
- [ ] Consume the completed M13-T05 acceptance JAR and `docs/runbook/acceptance.md`; start it with acceptance profile, clean MySQL schema and fixture enabled, then wait for health readiness. Do not assemble or modify the JAR in this E2E task.
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

**Transfer (2026-09-06):** 用户要求新增任务移交剩余工作；当前实际28通过/1失败/11未运行及诊断保留，后续执行入口为Order76/M14-T09。以下既有任务卡是历史技术输入，不代表原49已完成，不复用已用启动器。

**Files:**
- Create: `control-plane/e2e/tushare-live.spec.js`
- Create: `docs/verification/M14-T05-tushare-live.md`

**Current authorized phase (2026-09-06):** 用户要求排除top_inst与broker_recommend，只验证2000积分档可满足的接口。按修订设计执行固定40接口/48原样例/80页面查询、28ok/12empty；另7项权限文档未确认的接口暂不覆盖。原49项manifest、JAR、生产表与元数据合同保持不变，9项范围排除必须逐项记录，不能计作通过或skip。该阶段成功后记录子集完成并暂停原任务，不宣称49全量验收或发布准入完成。

**Interfaces:** Uses unchanged manifest parameters and a credential supplied only through `TENSOR_TUSHARE_TOKEN`; every business call originates from the page. Frozen scope/eligibility and controller-selected minimum interval are defined in `docs/task-designs/M14-T05-design.md`.

- [ ] Consume the revised design and user's narrowed scope; preserve the original public manifest validation (49 APIs/58 samples), then select the exact 40 APIs/48 samples and publish all 9 exclusions with fixed safe reasons.
- [ ] Add same-function scope/counterexample checks before implementation; preserve existing safety, result/count validation and lifecycle behavior. Register only the selected 40 cases, no runtime skip or arbitrary selection flag.
- [ ] Preserve the per-API ok/empty rules and all unchanged sample values; redo fixture SUCCESS/EMPTY independently (2 POST/3 queries).
- [ ] Use one worker, zero retries and `M14_T05_CALL_INTERVAL_MS=2000`; record actual auth/permission/quota/network/product failures without additional upstream probes or altered parameters.
- [ ] Verify Node24 syntax, 40-case discovery, focused pure probes and missing-environment summary (registered40/unexecuted40, manifestSamples58/selectedSamples48); do not repeat unrelated Maven/unit/old synthetic Chromium gates.
- [ ] Prepare a new MySQL8.4.6 schema/least-privilege account and original JAR. Finish local prerequisites before asking the user to launch once in their Token-configured terminal; no long confirmation-wait loop.
- [ ] Run `npx playwright test e2e/tushare-live.spec.js --workers=1`; successful phase requires 40 completed cases/48 live POSTs/80 live queries, plus separate fixture checks, unchanged 6 migrations/50 business tables, selected40 row counts matching pages and excluded9 tables still empty.
- [ ] Run the unchanged post-CLI scanner after all workers exit, preserve failure codes, scan evidence and clean exact owned resources. Commit only the spec/evidence implementation files; control documents are separate commits.
- [ ] Report the phase's actual results and exclusions; full49 acceptance remains incomplete. On success pause M14-T05 with a valid handoff; on real failure record BLOCKED. Do not automatically prepare the successor.

### Task M14-T09: 分红修复与2000档剩余验收（新增承接任务）

**Authority:** 用户2026-09-06要求新增任务；权威看板Order76，初始登记NOT_STARTED；本轮D-01已确认，当前状态以看板为准。原M14-T06/T07/T08的ID和依赖不变，Order顺延。

**Design:** `docs/task-designs/M14-T09-design.md`；**Handoff:** `docs/task-handoffs/tensor-v1/M14-T09-handoff.md`（pause；原transfer保留于Git，前驱未完成）。**Dependencies:** M14-T04、M14-T05；后者仅提供已完成的实现/证据输入，不要求其原49目标先完成。

**Goal / scope:** 接手dividend保存规则确认及必要修复、本地适配/迁移/幂等/schema/打包/复审/health验证、新冻结包和完整40接口复验/新证据归档。2026-09-06用户已明确确认四字段指纹/V7方案，按其生产文件边界实施；此次批准与此前任务创建分别记录。

**Files:** 以新任务设计及ISSUE-007设计中的精确范围为准；复用 `control-plane/e2e/tushare-live.spec.js`，新结果写 `docs/verification/M14-T09-tushare-live.md`；原M14-T05已扫描证据不改写。

- [x] D-01已确认并写入设计，批准c72a823，单独启动fbd594a。
- [x] 修复963ea17；合成RED/GREEN76/73、迁移/幂等/schema/7打包检查、独立复审、冻结新包及7迁移/50空表health通过。
- [x] 当前任务归属与新证据路径已接管，原manifest/参数和40/48/80与fixture2/3、9项排除及安全边界保留，离线反例/发现40通过。
- [x] 工具已执行一次完整范围复验nuy4jdhx，实际32通过/1失败/7未运行；dividend38通过，top10_holders当前SUCCESS320与EMPTY预期冲突而停止。结果不与此前28项拼接；D-02及新轮完整验收仍待完成。
- [x] D-02明确确认，d6e462f限定修改和离线/接入复审通过；cf64556解阻、f22c1f2单独启动。yx5keenc实际33通过/1失败/6未运行，top10_holders320闭环通过；top10_floatholders SUCCESS280与EMPTY断言冲突，末查未执行。安全证据6ae877f先独立提交，清理通过。
- [x] D-03获明确确认，ff3cfdc限定修改、RED/GREEN/发现40及18启动探针和独立复审通过；6b22d36解阻、07ec797单独启动。9mkzsd_0最终173秒，40通过/0失败/0未运行，48/80与fixture2/3、133完成事件、全40页面/DB与扫描清理全部通过，真实证据先独立提交14e038e。
- [x] 本任务完成条件全部满足，按看板记录IN_PROGRESS→COMPLETED；保留历史pause入口和原49的9项未覆盖事实，已回写M14-T05，不自动准备M14-T06。

### Task M14-T06: Daily 与 balancesheet 性能验证（4.0h）

**Current disposition:** 用户明确要求本任务先跳过、登记issue并直接标记完成。看板据此将M14-T06标为COMPLETED（任务管理收尾）；性能尚未实施或实测，以下原清单仍未执行，由 `docs/issues/problems/ISSUE-009-query-performance-verification.md` 跟踪。未确认的数据规模不作为合同；本次不关闭ISSUE-008/009、不视为性能或发布门禁通过。

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

**Design:** `docs/task-designs/M14-T07-design.md`（用户明确回复“确认设计”；启动与结果以权威看板为准）。

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

### Task M14-T08: AC 映射与发布证据收尾

**Design:** [M14-T08 专属设计](../../../task-designs/M14-T08-designs.md)。

**Scope adjustment (2026-09-07):** 用户明确要求“把M14-T08收窄为 AC 映射与发布证据收尾”。本任务仅整理已有验收文档并核对映射，不执行候选包冻结/构建、工具接入、新环境首跑、自动回归、安全复扫或 40 接口真实复验；这五类工作不再作为本任务完成前提，不新建相应子任务。产品验证缺口须披露，但不阻止文档交付完成。

**Dependencies:** M14-T01、T02、T03、T04、T09、T07，仅消费已有证据。49 自动契约与 T09 固定 40 接口的结果按各自原版本记录；ISSUE-008 九接口与 ISSUE-009 性能验证仍为“不依赖，用户后续单独处理”。安全原失败、ISSUE-010～014 专项验证及 ISSUE-015 原风险接受分别归档，不改写历史结果或将不同版本合并为当前候选整体验收通过。

**Context boundary:** Read the linked design, PRD/traceability, existing verification documents, runbooks, task handoffs and issue decisions. Do not run their historical commands or inspect production implementation/credentials to manufacture new acceptance evidence.

**Files:**
- Create: `docs/verification/release-checklist.md`
- Create: `docs/verification/ac-001-018.md`
- Create: `docs/verification/release-summary.md`

**Interfaces:** Each AC/requirement maps to existing evidence with its original version, date, result and limitations; document checks are the only new verification in this task.

- [ ] Read the narrowed design and ordered sources; inventory existing commands, timestamps, exit codes, counts, source/JAR identities and coverage limitations without rerunning commands.
- [ ] Write `release-checklist.md`, keeping original execution results separate from current dispositions; record missing evidence, different versions, ISSUE-008/009 deferrals and ISSUE-015 accepted risks explicitly.
- [ ] Map all 18 ACs, PRD-F-001～031 with original P0/P1, and PRD 10.1～10.6 in `ac-001-018.md`. Preserve direct/partial coverage semantics and mark every AC as not reverified in this task.
- [ ] Write `release-summary.md`: existing evidence has been archived; the current candidate has not completed a new end-to-end acceptance run. Do not claim release readiness, full-49 live coverage, performance success or a new overall security pass.
- [ ] Check document structure, exact requirement IDs, source links, original counts/versions, safe summaries and `git diff --check`; add new deliverables to Git and record these document checks.
- [ ] Complete M14-T08 only when the three documents are accurate, complete and traceable. Disclosed product verification gaps do not prevent this documentation task from completing; leave other tasks/issues and historical outcomes unchanged.
- [ ] Commit the redacted evidence package as `docs: record Tensor v1 release verification` when Git exists.

## Module Gate

M14-T08 now completes on AC mapping and accurate archival of existing release evidence, per the owner's 2026-09-07 scope decision. Fresh builds, test-tool adaptation, clean-environment runs, automated/security reruns and live reruns are not gates for this documentation task. Document completion does not establish the current candidate's technical acceptance or release readiness.

Technical release conclusions must remain supported by evidence for the stated version: applicable AC-001～018 and PRD-F-001～031, 49 automated contracts, the authorized 40 live cases, security and clean-environment operation. Existing gaps and different artifact versions remain visible rather than being resolved by this task. ISSUE-008's nine live interfaces and ISSUE-009 performance verification remain owner-deferred, non-required items; ISSUE-015 retains its original accepted-risk boundary. This task neither declares those original goals passed nor changes other task/issue statuses.
