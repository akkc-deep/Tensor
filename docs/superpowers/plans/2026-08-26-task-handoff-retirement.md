# Tensor Task Handoff Retirement Implementation Plan

> **历史记录（已被取代，2026-08-31）：** 本文关于保留 `docs/planning/task-index.md` 的决定已被项目所有者正式取代；正文仅用于说明 2026-08-26 的迁移，不再定义当前任务工作流。当前唯一权威任务索引为 [`docs/task-handoffs/tensor-v1-task-board.md`](../../task-handoffs/tensor-v1-task-board.md)。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 退役 Tensor 项目内重复维护的旧任务交接流程，保留并迁移项目独有的任务、设计和验收资产。

**Architecture:** 当前状态、权限、生命周期、事件、记录和恢复不再存放于项目 Markdown；项目文档只保留 Tensor 的任务清单、依赖、候选输入、设计内容和验收要求。迁移先创建限定范围的临时归档，再按“索引与设计规范 → M00 → 总规范与路线图 → M01–M14 → 删除旧载体”的顺序执行，最后用残留扫描、链接检查和受保护内容哈希验证。

**Tech Stack:** Markdown、`apply_patch`、`rg`、`tar`、`shasum`、Python 3 只读链接检查。

## Global Constraints

- 不初始化 `.task-handoff`，不准备、授权、选择或启动任何项目任务。
- 不修改 BRD、PRD、TRD、`docs/data-template/` 或 `control-plane/`；其当前聚合 SHA-256 必须保持 `ee4a6c87c6ba247358e7b5123b2dc64a6c58c00a7b8f641521e655d8ca8738ee`。
- 保留 M00–M14、77 个任务、206 AI 小时和原有依赖；只把 M00-T04 的旧交接治理交付物改为项目任务设计与验收证据模板。
- 删除目标仅为 `docs/task-handoffs/task-handoff.md` 与 `docs/task-handoffs/records/README.md`；任务索引必须先迁移并验证，再删除旧路径。
- 项目不是 Git 仓库，不初始化新仓库，不执行提交；每个任务以命令输出和 `/tmp/Tensor-task-handoff-retirement-20260826-v1.tar.gz` 作为恢复与复核依据。
- 项目文档不得复制 `managing-task-handoffs` Skill 的通用状态机、授权矩阵、审计、归档、恢复或存储规则。

---

### Task 1: 建立迁移基线和可恢复归档

**Files:**
- Archive: `/tmp/Tensor-task-handoff-retirement-20260826-v1.tar.gz`
- Read: `docs/task-handoffs/`
- Read: `docs/task-designs/README.md`
- Read: `docs/superpowers/specs/2026-08-25-tensor-module-roadmap-design.md`
- Read: `docs/superpowers/plans/2026-08-25-tensor-implementation-roadmap.md`
- Read: `docs/superpowers/plans/tensor-modules/`

**Interfaces:**
- Produces: one immutable-by-convention archive containing every legacy or reference file changed by Tasks 2–7.
- Consumes: no project mutation and no handoff initialization.

- [ ] **Step 1: Verify the migration is needed and no handoff state exists**

Run:

```bash
test ! -e .task-handoff
test -f docs/task-handoffs/task-handoff.md
test -f docs/task-handoffs/task-index.md
test -f docs/task-handoffs/records/README.md
```

Expected: every command exits `0`.

- [ ] **Step 2: Refuse to overwrite an earlier recovery archive**

Run:

```bash
test ! -e /tmp/Tensor-task-handoff-retirement-20260826-v1.tar.gz
```

Expected: exit `0`. If it exits `1`, stop and inspect the existing archive; do not overwrite it.

- [ ] **Step 3: Create the bounded recovery archive**

Run:

```bash
tar -czf /tmp/Tensor-task-handoff-retirement-20260826-v1.tar.gz docs/task-handoffs docs/task-designs/README.md docs/superpowers/specs/2026-08-25-tensor-module-roadmap-design.md docs/superpowers/plans/2026-08-25-tensor-implementation-roadmap.md docs/superpowers/plans/tensor-modules
```

Expected: exit `0`; no project file changes.

- [ ] **Step 4: Verify archive integrity and scope**

Run:

```bash
gzip -t /tmp/Tensor-task-handoff-retirement-20260826-v1.tar.gz
tar -tzf /tmp/Tensor-task-handoff-retirement-20260826-v1.tar.gz
```

Expected: `gzip -t` exits `0`; the listing contains the three files under `docs/task-handoffs/`, `docs/task-designs/README.md`, the roadmap design, the implementation roadmap, and M00–M14 module plans.

---

### Task 2: 迁移任务索引并精简任务设计规范

**Files:**
- Move: `docs/task-handoffs/task-index.md` → `docs/planning/task-index.md`
- Modify: `docs/task-designs/README.md:1-37`

**Interfaces:**
- Produces: the project-only task inventory at `docs/planning/task-index.md`; the project-only task design convention at `docs/task-designs/README.md`.
- Consumes: M00–M14 task rows and dependency values from the legacy index without changing their count or hours.

- [ ] **Step 1: Establish failing path and legacy-content checks**

Run:

```bash
test -f docs/planning/task-index.md
rg -n 'task-handoff\.md|docs/task-handoffs|DESIGN_REVIEW|WAITING_FOR_DESIGN|IN_PROGRESS|DH-' docs/task-handoffs/task-index.md docs/task-designs/README.md
```

Expected: the first command exits `1`; the second exits `0` and reports legacy workflow wording.

- [ ] **Step 2: Move the index and replace its governance preface**

Use `apply_patch` with `*** Move to: docs/planning/task-index.md`. Replace the opening block with exactly:

```markdown
# Tensor 任务索引

> 本文件是 M00–M14、77 个预定义任务的项目计划索引，只维护任务、交付物、工时、依赖和模块计划链接，不记录当前执行状态或运行时授权。

## 使用说明

- 总计 15 个模块、77 个任务、206 AI 小时；单任务不超过 4 AI 小时。
- 每个任务的项目设计文档使用 `docs/task-designs/<任务编号>-designs.md`，并与对应任务卡双向链接。
- 每个模块的详细目标、候选输入、文件边界、接口、步骤和验收保存在模块标题后的计划链接中。
- 本索引中的候选任务和依赖信息不表示任何任务已经被选择或启动。
```

Replace the M00-T04 row with exactly:

```markdown
| M00-T04 | Tensor 任务设计与验收证据模板 | 1.0 | M00-T01 |
```

Keep every other task row, hour value, dependency and module link unchanged.

- [ ] **Step 3: Replace the task-design README with project-only responsibilities**

Use `apply_patch` to keep the title and example paths, define the five stable headings `做什么`、`怎么做`、`如何测试`、`如何验证`、`依赖什么信息`, then replace the usage section with exactly:

```markdown
## 使用说明

- 任务设计文档只描述做什么、怎么做、如何测试、如何验证和依赖什么信息。
- 设计文档顶部链接对应任务，对应任务卡通过 `Design` 字段链接回设计文档。
- 模块计划中的候选输入用于编写任务设计，不表示当前任务状态或运行时访问授权。
- 当前状态、执行授权、实际验证结果、事件、归档和恢复不写入本目录。
```

Remove references to `docs/task-handoffs/`, `task-handoff.md`, `records/`, `DH-*`, and hand-written lifecycle status names.

- [ ] **Step 4: Verify inventory preservation and responsibility separation**

Run:

```bash
test -f docs/planning/task-index.md
test ! -f docs/task-handoffs/task-index.md
rg -c '^\| M[0-9]{2}-T[0-9]{2} \|' docs/planning/task-index.md
rg -n '77 个任务|206 AI 小时' docs/planning/task-index.md
rg -n 'task-handoff\.md|docs/task-handoffs|DESIGN_REVIEW|WAITING_FOR_DESIGN|IN_PROGRESS|DH-' docs/planning/task-index.md docs/task-designs/README.md
```

Expected: the count is `77`; both totals are present; the last command exits `1` with no output.

---

### Task 3: 将 M00-T04 改为项目模板任务

**Files:**
- Modify: `docs/superpowers/plans/tensor-modules/M00-contracts.md:13-186`

**Interfaces:**
- Produces: M00-T04 project task-design and acceptance-evidence template requirements used by M01–M14.
- Consumes: the five stable headings from `docs/task-designs/README.md`; no current-state or authorization format.

- [ ] **Step 1: Confirm the legacy M00 task is still present**

Run:

```bash
rg -n '固化单一交接|task-handoff\.md|docs/task-handoffs|DESIGN_REVIEW|WAITING_FOR_DESIGN|IN_PROGRESS|DH-' docs/superpowers/plans/tensor-modules/M00-contracts.md
```

Expected: exit `0`, including the M00-T04 legacy section.

- [ ] **Step 2: Replace generic lifecycle constraints with project planning boundaries**

Use `apply_patch` to replace `## Context Budget Rule` with `## Project Planning Inputs` and this content:

```markdown
本计划中的 `Context boundary`、`Files` 和 `Interfaces` 是编写 Tensor 任务设计时的候选输入和预期变更，不记录当前执行状态或运行时授权。每项实现前，设计必须明确做什么、怎么做、如何测试、如何验证以及依赖什么信息。
```

For M00-T01 through M00-T03, replace each checklist label `Complete DESIGN_REVIEW` with `Confirm task design inputs`. Replace every instruction to create `DH-*` with an instruction to record the missing decision in the corresponding task design and stop implementation until the decision is fixed.

- [ ] **Step 3: Replace the complete M00-T04 task block**

Replace the block from `### Task M00-T04` through its commit step with this structure and wording:

```markdown
### Task M00-T04: 建立 Tensor 任务设计与验收证据模板（1.0h）

**Context boundary:** Read roadmap sections 1 and 7, approved design-spec sections 3 and 9, and `docs/task-designs/README.md`. Do not read production source code.

**Files:**
- Create: `docs/superpowers/task-templates/task-design.md`
- Create: `docs/superpowers/task-templates/acceptance-evidence.md`

**Interfaces:**
- Produces: project-specific task-design and acceptance-evidence templates used by M01–M14; neither template stores current status, authority, events, records or recovery data.

- [ ] **Step 1: Write the task-design template**

Include only the five stable headings from `docs/task-designs/README.md`: `做什么`、`怎么做`、`如何测试`、`如何验证`、`依赖什么信息`. Include the task ID and corresponding-task link above them.

- [ ] **Step 2: Write the acceptance-evidence template**

Include requirement IDs, changed files, test command IDs, commands, timestamps, exit codes, pass/fail counts, bounded summaries and secret-scan result. Do not duplicate current status, actor authority, event history, archival or recovery fields.

- [ ] **Step 3: Verify project-only template responsibilities**

Verify that `docs/superpowers/task-templates/task-design.md` contains the five exact headings and a corresponding-task link. Run `rg -q 'test command|exit code|requirement' docs/superpowers/task-templates/acceptance-evidence.md` and expect exit `0`. Inspect both templates and confirm they contain only project design and acceptance evidence fields, without current execution or recovery data.

- [ ] **Step 4: Record the checkpoint when Git is unavailable**

Run the verification commands above and retain their output in the active execution summary; do not initialize a Git repository.
```

Update the M00 module gate so it requires the tracking matrix, schema, OpenAPI, task-design template and acceptance-evidence template; remove the “exactly one current handoff” requirement.

- [ ] **Step 4: Verify M00 no longer recreates the retired flow**

Run:

```bash
rg -n 'task-handoff\.md|docs/task-handoffs|DESIGN_REVIEW|WAITING_FOR_DESIGN|IN_PROGRESS|DH-|固化单一交接' docs/superpowers/plans/tensor-modules/M00-contracts.md
rg -n 'Task M00-T04: 建立 Tensor 任务设计与验收证据模板|task-design\.md|acceptance-evidence\.md' docs/superpowers/plans/tensor-modules/M00-contracts.md
```

Expected: the first exits `1` with no output; the second exits `0` with all three matches.

---

### Task 4: 精简路线图设计规范

**Files:**
- Modify: `docs/superpowers/specs/2026-08-25-tensor-module-roadmap-design.md:20-307`

**Interfaces:**
- Produces: architecture and task-decomposition guidance without a second handoff lifecycle.
- Consumes: project task-design requirements, module boundaries and the four-hour maximum task size.

- [ ] **Step 1: Capture the legacy sections that must disappear**

Run:

```bash
rg -n 'docs/task-handoffs|task-handoff\.md|DESIGN_REVIEW|WAITING_FOR_DESIGN|IN_PROGRESS|BLOCKED|DH-|FIX-|精确读取白名单|交接任务' docs/superpowers/specs/2026-08-25-tensor-module-roadmap-design.md
```

Expected: exit `0` with matches in goals, Section 4, Section 7, Section 9, completion criteria and rejected approaches.

- [ ] **Step 2: Replace Section 4 with project design completeness rules**

Replace `## 4. 任务上下文预算` through the paragraph immediately before `## 5. AI 工时口径` with:

```markdown
## 4. 任务设计完整性

每个预定义任务在实施前必须拥有 `docs/task-designs/<任务编号>-designs.md`。设计只包含 `做什么`、`怎么做`、`如何测试`、`如何验证`、`依赖什么信息` 五个固定部分，并与对应任务卡双向链接。

模块计划列出的文件、章节和接口是任务设计的候选输入，不是当前状态或运行时授权。设计无法唯一说明实现方式、测试方法、验证标准或必要依赖时，先修订任务设计，再开始实现。

单个任务保持 0.5～4 AI 小时，并在设计中列出完成实现与验证所需的最小输入。任务目标、交付物或验收发生实质变化时修改任务设计和路线图，不通过编辑路线图记录运行时状态。
```

- [ ] **Step 3: Remove lifecycle duplication from remaining sections**

Apply these exact semantic changes:

- Section 1 goals: replace status and design-handoff wording with “每个任务实施前具有完整、唯一、可验证的任务设计；实现过程中不临时拆分或猜测未决设计”。
- Section 7: keep module decomposition and four-hour limits; remove status names and generated `DH-*` instructions.
- Section 9: keep task ID, module, hours, requirement links, prerequisites, candidate inputs, files, interfaces, steps, commands and acceptance; remove current-state, authorization, whitelist, status and handoff-record fields.
- Section 10: keep BRD→PRD→TRD→module→task→evidence traceability; replace the current-handoff completion criterion with “项目任务索引、任务设计和模块计划职责清晰且链接有效”。
- Section 11: keep language isolation, module boundaries and no-guessing rules; remove wording that prescribes generic lifecycle states or legacy handoff records.

- [ ] **Step 4: Verify the design spec owns architecture, not runtime handoff state**

Run:

```bash
rg -n 'docs/task-handoffs|task-handoff\.md|DESIGN_REVIEW|WAITING_FOR_DESIGN|IN_PROGRESS|BLOCKED|DH-|FIX-|精确读取白名单|交接任务' docs/superpowers/specs/2026-08-25-tensor-module-roadmap-design.md
rg -n '^## 4\. 任务设计完整性|0\.5～4 AI 小时|BRD FR-01～FR-09' docs/superpowers/specs/2026-08-25-tensor-module-roadmap-design.md
```

Expected: the first exits `1` with no output; the second exits `0` and confirms the retained project constraints.

---

### Task 5: 精简总实施路线图并更新索引链接

**Files:**
- Modify: `docs/superpowers/plans/2026-08-25-tensor-implementation-roadmap.md:1-369`

**Interfaces:**
- Produces: implementation roadmap linking to `docs/planning/task-index.md` and retaining module interfaces, milestones, effort, traceability and completion conditions.
- Consumes: the migrated index and the revised roadmap design spec.

- [ ] **Step 1: Confirm legacy protocol and old index link**

Run:

```bash
rg -n 'docs/task-handoffs|task-handoff\.md|DESIGN_REVIEW|WAITING_FOR_DESIGN|IN_PROGRESS|BLOCKED|DH-|FIX-|精确读取白名单|设计交接' docs/superpowers/plans/2026-08-25-tensor-implementation-roadmap.md
```

Expected: exit `0`, including Sections 1, 5, 7 and 8.

- [ ] **Step 2: Replace architecture and global handoff constraints**

Replace the architecture paragraph with:

```markdown
**Architecture:** 采用契约优先的模块化单体：Java 后端按 Maven 模块隔离，YAML 元数据、Flyway SQL 和 Vue 控制面分别实施。每个任务使用独立设计文档说明做什么、怎么做、如何测试、如何验证以及依赖什么信息；项目路线图不记录当前执行状态或运行时授权。
```

In Global Constraints, keep technology, security, language, four-hour task size and no-Git constraints. Replace all legacy handoff bullets with:

```markdown
- 每个预定义任务使用 `docs/task-designs/<任务编号>-designs.md` 保存 Tensor 项目设计；任务卡和设计文档必须双向链接。
- 模块计划中的候选输入、文件和接口用于准备任务设计，不记录当前状态或运行时授权。
- 任务执行中的状态、权限、上下文、验证、事件、记录和恢复不写入本路线图。
```

- [ ] **Step 3: Replace Section 1 execution protocol**

Replace `## 1. 执行协议` through the paragraph immediately before `## 2. 文件与模块地图` with:

```markdown
## 1. 路线图使用方式

本路线图用于维护模块依赖、稳定接口、项目任务设计要求、实施顺序和验收标准。它不作为当前任务入口，也不保存当前执行状态、授权证据、事件、归档或恢复信息。

实施某个预定义任务前，先完成对应 `docs/task-designs/<任务编号>-designs.md`，明确做什么、怎么做、如何测试、如何验证和依赖什么信息。设计缺少必要结论时先修订设计；已经产生的任务进度和验证结果不回写到路线图结构中。
```

- [ ] **Step 4: Update task index, defect and completion sections**

Apply these changes:

- Section 5 uses link label `docs/planning/task-index.md` with link target `../../planning/task-index.md`, and describes it as the 77-task project plan rather than a coordinator-only handoff source.
- Section 7 replaces `FIX-*`/`DH-*` flow with a project statement that integration defects become separately planned, single-language correction tasks; no runtime naming, status or archival rules are defined here.
- Section 8 keeps module gates, traceability and zero open defects; removes requirements for generated handoff records or lifecycle statuses.
- Replace “模块交接基线” with “模块接口基线”.

- [ ] **Step 5: Verify preserved roadmap data and removed runtime governance**

Run:

```bash
rg -n 'docs/task-handoffs|task-handoff\.md|DESIGN_REVIEW|WAITING_FOR_DESIGN|IN_PROGRESS|BLOCKED|DH-|FIX-|精确读取白名单|设计交接' docs/superpowers/plans/2026-08-25-tensor-implementation-roadmap.md
rg -n '\[.*task-index\.md\]\(\.\./\.\./planning/task-index\.md\)|77 个任务|206' docs/superpowers/plans/2026-08-25-tensor-implementation-roadmap.md
```

Expected: the first exits `1` with no output; the second exits `0` and confirms the new link and preserved totals.

---

### Task 6: 清理 M01–M14 模块计划中的旧流程

**Files:**
- Modify: `docs/superpowers/plans/tensor-modules/M01-backend-foundation.md`
- Modify: `docs/superpowers/plans/tensor-modules/M02-plugin-api.md`
- Modify: `docs/superpowers/plans/tensor-modules/M03-tushare-metadata.md`
- Modify: `docs/superpowers/plans/tensor-modules/M04-flyway-schema.md`
- Modify: `docs/superpowers/plans/tensor-modules/M05-core-registry-adapter.md`
- Modify: `docs/superpowers/plans/tensor-modules/M06-core-persistence-query.md`
- Modify: `docs/superpowers/plans/tensor-modules/M07-tushare-plugin.md`
- Modify: `docs/superpowers/plans/tensor-modules/M08-fixture-plugin.md`
- Modify: `docs/superpowers/plans/tensor-modules/M09-app-api.md`
- Modify: `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md`
- Modify: `docs/superpowers/plans/tensor-modules/M11-download-ui.md`
- Modify: `docs/superpowers/plans/tensor-modules/M12-dataset-ui.md`
- Modify: `docs/superpowers/plans/tensor-modules/M13-packaging-runbook.md`
- Modify: `docs/superpowers/plans/tensor-modules/M14-integration-release.md`

**Interfaces:**
- Produces: project module plans containing goals, architecture boundaries, candidate inputs, files, interfaces, steps and acceptance without a duplicate runtime workflow.
- Consumes: unchanged task IDs, hours, dependencies and deliverables.

- [ ] **Step 1: Replace every Context Budget Rule with Project Inputs**

For each file, rename `## Context Budget Rule` to `## Project Inputs` and replace the paragraph with the corresponding exact content:

| Module | Project Inputs content |
|---|---|
| M01 | `候选输入为 M00 契约、父 POM、模块 POM、构建规则和架构测试。后端工程基线不得依赖前端实现或业务模块内部代码。` |
| M02 | `候选输入为 M00 契约、本模块接口文件和指定 TRD 契约。Plugin API 不依赖具体插件、数据库或 Vue 实现。` |
| M03 | `候选输入为对应 API 模板投影、M00 schema、PRD 附录和 TRD 9.4。每个字段的类型、长度、可空性和业务键必须先在任务设计中唯一确定。` |
| M04 | `候选输入为对应 M03 YAML、迁移文件和 schema 测试。Flyway 任务不依赖 Java 或 Vue 实现。` |
| M05 | `候选输入为本模块 Java 边界以及直接消费的 M02、M03、M04 稳定契约。Core 注册与适配不依赖具体插件或前端实现。` |
| M06 | `候选输入为 persistence/query 文件和稳定 DatasetDefinition 契约。持久化与查询不依赖 Tushare 或 Vue 实现。` |
| M07 | `候选输入为插件文件、M02 SPI 和 M03 数据集定义。Tushare 插件不依赖 core 内部实现、数据库或 Vue。` |
| M08 | `候选输入为 fixture 文件及其消费的公共 SPI 和服务接口。Fixture 不依赖 Tushare 或 Vue 实现。` |
| M09 | `候选输入为 OpenAPI、app/core 边界文件和对应测试。应用 API 不依赖 Vue 或具体插件内部实现。` |
| M10 | `候选输入为 M00 OpenAPI 和前端工程文件。前端工程基线不依赖 Java、SQL 或完整产品设计文档。` |
| M11 | `候选输入为 M10 API、通用组件接口和下载页文件。下载页面不依赖 Java 或 SQL 实现。` |
| M12 | `候选输入为 M10 API、格式化接口和数据查看文件。数据查看页面不依赖 Java 或 SQL 实现。` |
| M13 | `候选输入为构建、配置、运行说明和已稳定公开产物接口。打包任务不依赖业务模块内部实现。` |
| M14 | `候选输入为公开契约、运行说明、测试文件和指定既有验收证据。集成验证不读取 M00–M13 生产实现。` |

- [ ] **Step 2: Convert task-level lifecycle wording to design checks**

Across M01–M14, replace every checklist prefix `Complete DESIGN_REVIEW` with `Confirm task design`. Keep the project-specific technical condition after the semicolon.

Apply these additional exact semantic rewrites:

- “before `IN_PROGRESS`” becomes “before implementation”.
- “keep the task in `WAITING_FOR_DESIGN`” becomes “stop implementation until the task design contains the missing decision”.
- “create/generate/require a `DH-*` file” becomes “record the missing decision in the task design and stop implementation until it is resolved”.
- M03 architecture says unresolved column types must be fixed in task design and never guessed during implementation.
- M14 replaces `FIX-*` with “single-language defect correction task” and replaces “zero open FIX/DH tasks” with “zero unresolved defects or design gaps”.
- Keep business SQL/API allowlists; only remove “precise read whitelist” workflow wording.

- [ ] **Step 3: Verify all 14 module plans are free of legacy workflow names**

Run:

```bash
rg -n 'docs/task-handoffs|task-handoff\.md|DESIGN_REVIEW|WAITING_FOR_DESIGN|IN_PROGRESS|BLOCKED|DH-|FIX-|精确读取白名单|交接任务' docs/superpowers/plans/tensor-modules/M{01,02,03,04,05,06,07,08,09,10,11,12,13,14}-*.md
rg -l '^## Project Inputs$' docs/superpowers/plans/tensor-modules/M{01,02,03,04,05,06,07,08,09,10,11,12,13,14}-*.md
```

Expected: the first exits `1` with no output; the second lists exactly 14 files.

- [ ] **Step 4: Verify task identity and hours were not changed**

Run:

```bash
rg -c '^### Task M[0-9]{2}-T[0-9]{2}:' docs/superpowers/plans/tensor-modules/*.md
```

Expected: per-file counts sum to `77`; M00 remains `4` and M01–M14 retain their original counts.

---

### Task 7: 删除旧载体并完成全量验证

**Files:**
- Delete: `docs/task-handoffs/task-handoff.md`
- Delete: `docs/task-handoffs/records/README.md`
- Verify: all Markdown under `docs/`, excluding the approved retirement design and this implementation plan when scanning historical path names.

**Interfaces:**
- Produces: no legacy `docs/task-handoffs/` files, valid project links, preserved task inventory and unchanged protected content.
- Consumes: successful checks from Tasks 1–6.

- [ ] **Step 1: Prove no operational document still depends on deletion targets**

Run:

```bash
rg -n 'docs/task-handoffs|task-handoff\.md|task-handoffs/records|\.\./\.\./task-handoffs/task-index\.md' docs --glob '*.md' --glob '!docs/task-handoffs/task-handoff.md' --glob '!docs/task-handoffs/records/README.md' --glob '!docs/superpowers/specs/2026-08-26-task-handoff-retirement-design.md' --glob '!docs/superpowers/plans/2026-08-26-task-handoff-retirement.md'
```

Expected: exit `1` with no output. Do not delete anything if matches remain.

- [ ] **Step 2: Delete the two retired files with apply_patch**

Use `apply_patch` with:

```text
*** Delete File: docs/task-handoffs/task-handoff.md
*** Delete File: docs/task-handoffs/records/README.md
```

Do not delete `docs/planning/task-index.md` or `docs/task-designs/README.md`. Empty directories need no filesystem entry and may disappear automatically.

- [ ] **Step 3: Verify storage and task inventory**

Run:

```bash
test ! -e .task-handoff
test ! -e docs/task-handoffs/task-handoff.md
test ! -e docs/task-handoffs/records/README.md
test ! -e docs/task-handoffs/task-index.md
test -f docs/planning/task-index.md
rg -c '^\| M[0-9]{2}-T[0-9]{2} \|' docs/planning/task-index.md
```

Expected: every `test` exits `0`; the task-row count is `77`.

- [ ] **Step 4: Run the full legacy-workflow residual scan**

Run:

```bash
rg -n 'docs/task-handoffs|task-handoff\.md|DESIGN_REVIEW|WAITING_FOR_DESIGN|CONTEXT_AMENDMENT|IN_PROGRESS|BLOCKED|DH-|FIX-|精确读取白名单|交接任务' docs --glob '*.md' --glob '!docs/superpowers/specs/2026-08-26-task-handoff-retirement-design.md' --glob '!docs/superpowers/plans/2026-08-26-task-handoff-retirement.md'
```

Expected: exit `1` with no output. Business allowlists such as SQL identifiers and query filters are outside this pattern and remain unchanged.

- [ ] **Step 5: Check all local Markdown links**

Run:

```bash
python3 -c 'import pathlib,re,sys; root=pathlib.Path(".").resolve(); broken=[]; pattern=re.compile(r"\[[^]]*\]\(([^)]+)\)"); files=list((root/"docs").rglob("*.md")); [(broken.append((str(f.relative_to(root)),t)) if not (f.parent/t.split("#",1)[0]).resolve().exists() else None) for f in files for t in pattern.findall(f.read_text(encoding="utf-8")) if t and not t.startswith(("http://","https://","#","mailto:"))]; print(f"BROKEN_LINKS={len(broken)}"); [print(f"{f}: {t}") for f,t in broken]; sys.exit(1 if broken else 0)'
```

Expected: output `BROKEN_LINKS=0`, exit `0`.

- [ ] **Step 6: Verify protected content is unchanged**

Run:

```bash
find docs/design docs/data-template control-plane -type f -print0 | sort -z | xargs -0 shasum -a 256 | shasum -a 256
```

Expected:

```text
ee4a6c87c6ba247358e7b5123b2dc64a6c58c00a7b8f641521e655d8ca8738ee  -
```

- [ ] **Step 7: Verify the recovery archive remains readable**

Run:

```bash
gzip -t /tmp/Tensor-task-handoff-retirement-20260826-v1.tar.gz
shasum -a 256 /tmp/Tensor-task-handoff-retirement-20260826-v1.tar.gz
```

Expected: both commands exit `0`; report the archive path and its SHA-256 in the completion summary.

If any final check fails, stop. Restore only the files in the bounded archive with `tar -xzf /tmp/Tensor-task-handoff-retirement-20260826-v1.tar.gz -C /Users/qiangzhiwei/code/github/Tensor`, then re-run all checks from Task 1. Do not initialize `.task-handoff` as part of recovery.
