# M00 Requirements and Shared Contracts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 冻结 Tensor v1 的需求追踪、数据集定义、REST API、任务设计和验收证据契约。

**Architecture:** 本模块只创建可审阅的 Markdown、JSON Schema、OpenAPI YAML 和任务模板，不创建生产实现。后续模块按这些契约开发，不再通过读取其他模块实现推断接口。

**Tech Stack:** Markdown、JSON Schema 2020-12、OpenAPI 3.1、YAML。

**Spec:** `docs/superpowers/specs/2026-08-25-tensor-module-roadmap-design.md`

## Global Constraints

- 每个预定义任务使用 `docs/task-designs/<任务编号>-designs.md` 保存 Tensor 项目的唯一设计结论，任务卡通过 `Design` 字段链接该文件。
- 每项 FR、PRD-F、AC 和 TRD 质量门槛必须有稳定标识。
- REST 基础路径固定为 `/api/v1`，动态插件参数保持 snake_case。
- 任务设计和验收证据模板只保存项目设计与测试证据，不保存当前状态、权限、事件、归档或恢复信息。

## Project Planning Inputs

本计划中的 `Context boundary`、`Files` 和 `Interfaces` 是编写 Tensor 任务设计时的候选输入和预期变更，不记录当前执行状态或运行时授权。每项实现前，设计必须明确做什么、怎么做、如何测试、如何验证以及依赖什么信息。

---

### Task M00-T01: 建立需求追踪索引（1.0h）

**Design:** [M00-T01-designs.md](../../../task-designs/M00-T01-designs.md)

**Context boundary:** Candidate inputs are BRD sections 4–7, PRD sections 7、10、12、13 and TRD sections 21–23; use their existing matrices instead of loading all appendices. Do not read source code.

**Files:**
- Create: `docs/traceability/tensor-v1-requirements.md`
- Test: shell checks against the three design documents

**Interfaces:**
- Consumes: BRD `FR-01..09`, PRD `PRD-F-001..031` and `AC-001..018`, TRD sections 1–23.
- Produces: rows keyed by `BRD-ID`, `PRD-ID`, `TRD-section`, `acceptance-ID`, `module`, `evidence`.

- [ ] **Step 1: Confirm task design inputs**

Confirm that the listed requirement ranges are sufficient to build the traceability table. If a mapping decision is missing, record it in the M00-T01 task design and stop implementation until the decision is fixed.

- [ ] **Step 2: Create the traceability table with one row per PRD requirement**

Use the exact header:

```markdown
| BRD | PRD | Priority | TRD | Acceptance | Module | Evidence |
|---|---|---|---|---|---|---|
```

Map PRD-F-001 through PRD-F-031 and add separate rows for performance, reliability, security, extensibility, usability and observability.

- [ ] **Step 3: Verify identifiers are complete**

Run:

```bash
for n in $(seq -w 1 31); do rg -q "PRD-F-0$n" docs/traceability/tensor-v1-requirements.md || exit 1; done
for n in $(seq -w 1 18); do rg -q "AC-0$n" docs/traceability/tensor-v1-requirements.md || exit 1; done
```

Expected: exit 0.

- [ ] **Step 4: Verify no requirement row is unassigned**

Run `rg -n '\| *\| *$|T[B]D|T[O]DO' docs/traceability/tensor-v1-requirements.md`.
Expected: no output.

- [ ] **Step 5: Record evidence and commit when Git exists**

Run `git add docs/traceability/tensor-v1-requirements.md && git commit -m "docs: add Tensor v1 requirement traceability"` only when `git rev-parse --is-inside-work-tree` succeeds.

### Task M00-T02: 冻结数据集定义 schema（1.5h）

**Design:** [M00-T02-designs.md](../../../task-designs/M00-T02-designs.md)

**Context boundary:** Candidate inputs are `docs/data-template/manifest.json`, TRD sections 5 and 8, and the required portion of M00-T01 output. Do not read Java or Vue.

**Files:**
- Create: `docs/contracts/dataset-definition.schema.json`
- Create: `docs/contracts/dataset-definition.example.yaml`
- Test: `docs/contracts/dataset-definition.schema.json`

**Interfaces:**
- Consumes: `DatasetDefinition` semantics from TRD 5.3 and YAML fields from TRD 8.1.
- Produces: schema requiring `pluginId`, `apiName`, `tableName`, `category`, `displayName`, `queryMode`, `parameters`, `columns`, `businessKey`, `filters`.

- [ ] **Step 1: Confirm task design inputs**

Confirm the schema determines identifier patterns, column logical types, key modes `COMPOSITE|FINGERPRINT`, filter fields and display metadata.

- [ ] **Step 2: Write the schema root and identifier rules**

Use JSON Schema draft `https://json-schema.org/draft/2020-12/schema`; set `additionalProperties: false`; apply `^[a-z][a-z0-9_]{1,63}$` to plugin/API identifiers and `^tushare_pro__[a-z][a-z0-9_]{1,63}$` to first-phase table names.

- [ ] **Step 3: Define exact enums**

Parameter types: `DATE`, `DATE_RANGE_MEMBER`, `MONTH`, `TS_CODE`, `ENUM`, `TEXT`. Column logical types: `STRING`, `TEXT`, `DATE`, `MONTH`, `LONG`, `DECIMAL`, `ENUM`. Key modes: `COMPOSITE`, `FINGERPRINT`. Query modes: `trade_date`, `ann_date`, `snapshot`, `date_range`.

- [ ] **Step 4: Create a complete `daily` example**

The example declares `tableName: tushare_pro__daily`, parameter `trade_date`, all 11 template columns in template order, business key `[ts_code, trade_date]`, filters `[ts_code, trade_date]`, and fixed column `ts_code`.

- [ ] **Step 5: Parse and inspect the contract files**

Run `jq -e '.required | length == 10 and .additionalProperties == false' docs/contracts/dataset-definition.schema.json`.
Expected: `true` and exit 0.

- [ ] **Step 6: Commit when Git exists**

Run `git add docs/contracts/dataset-definition.schema.json docs/contracts/dataset-definition.example.yaml && git commit -m "docs: define dataset metadata contract"` when Git is available.

### Task M00-T03: 冻结 REST 和错误契约（1.5h）

**Design:** [M00-T03-designs.md](../../../task-designs/M00-T03-designs.md)

**Context boundary:** Candidate inputs are PRD sections 5–9 and TRD section 12. Do not read Java/Vue implementations.

**Files:**
- Create: `docs/contracts/openapi-v1.yaml`
- Create: `docs/contracts/error-codes.md`
- Test: OpenAPI path and error-code shell checks

**Interfaces:**
- Produces: six `/api/v1` business operations, download outcomes `SUCCESS|EMPTY`, `PageResponse`, `ApiError`, `X-Request-Id`.

- [ ] **Step 1: Confirm task design inputs**

Verify the DTO names and field casing match TRD 12.2–12.6 exactly.

- [ ] **Step 2: Declare the six business paths**

Add GET data sources, GET plugin APIs, POST downloads, GET plugin datasets, GET dataset definition and GET dataset records. Define path parameters with the same identifier regex as M00-T02.

- [ ] **Step 3: Declare stable schemas**

Define `DataSourceSummary`, `ApiDescriptor`, `DatasetSummary`, `DatasetDefinitionResponse`, `DownloadRequest`, `DownloadResponse`, `PageResponse`, `ApiError` and `FieldError`. Serialize DECIMAL and BIGINT business values as strings.

- [ ] **Step 4: Create the error catalog**

List the exact codes `PARAM_REQUIRED`, `PARAM_INVALID`, `PLUGIN_DISABLED`, `DATASET_MISCONFIGURED`, `SOURCE_AUTH_FAILED`, `SOURCE_PERMISSION_DENIED`, `SOURCE_RATE_LIMITED`, `SOURCE_UNAVAILABLE`, `SOURCE_NETWORK_ERROR`, `SOURCE_TIMEOUT`, `SOURCE_PAYLOAD_INVALID`, `ADAPTER_FIELD_MISSING`, `ADAPTER_TYPE_INVALID`, `PERSISTENCE_FAILED`, `QUERY_FAILED`, `INTERNAL_ERROR` with HTTP status and retryable flag.

- [ ] **Step 5: Verify paths and sensitive-field absence**

Run `rg -c '^  /api/v1/' docs/contracts/openapi-v1.yaml` and expect `6`. Run `rg -ni 'token|password|authorization' docs/contracts/openapi-v1.yaml` and verify no request/response property exposes a credential.

- [ ] **Step 6: Commit when Git exists**

Run `git add docs/contracts/openapi-v1.yaml docs/contracts/error-codes.md && git commit -m "docs: freeze Tensor v1 REST contract"` when Git is available.

### Task M00-T04: 建立 Tensor 任务设计与验收证据模板（1.0h）

**Context boundary:** Read roadmap sections 1 and 7, approved design-spec sections 3 and 9, and `docs/task-designs/README.md`. Do not read production source code.

**Files:**
- Create: `docs/superpowers/task-templates/task-design.md`
- Create: `docs/superpowers/task-templates/acceptance-evidence.md`

**Interfaces:**
- Produces: project-specific task-design and acceptance-evidence templates used by M01–M14; neither template stores current status, authority, events, records or recovery data.

- [ ] **Step 1: Write the task-design template**

Include only the five stable headings from `docs/task-designs/README.md`: `做什么`、`怎么做`、`如何测试`、`如何验证`、`依赖什么信息`. Include the task ID and a link to the corresponding task card above those headings.

- [ ] **Step 2: Write the acceptance-evidence template**

Include requirement IDs, changed files, test command IDs, commands, timestamps, exit codes, pass/fail counts, bounded summaries and secret-scan result. Do not duplicate current status, actor authority, event history, archival or recovery fields.

- [ ] **Step 3: Verify project-only template responsibilities**

Verify that `docs/superpowers/task-templates/task-design.md` contains the five exact headings and a corresponding-task link. Run `rg -q 'test command|exit code|requirement' docs/superpowers/task-templates/acceptance-evidence.md` and expect exit `0`. Inspect both templates and confirm they contain only project design and acceptance evidence fields, without current execution or recovery data.

- [ ] **Step 4: Record the checkpoint when Git is unavailable**

Run the verification commands above and retain their output in the active execution summary; do not initialize a Git repository.

## Module Gate

Run all M00 verification commands. M00 is complete only when the tracking file contains PRD-F-001～031 and AC-001～018, the dataset schema and OpenAPI parse, and the task-design plus acceptance-evidence templates contain the required project design and verification fields without runtime handoff state.
