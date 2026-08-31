# Tensor Duplicate Task Index Retirement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删除重复的静态任务索引，使 Tensor v1 task board 成为唯一活跃任务索引，同时完整保留任务事实与历史迁移记录。

**Architecture:** 以 Git 中未修改的 task board 任务表作为删除前基线，用整行 SHA-256 固定 77 个任务的全部权威字段；随后只修改看板治理说明和任务详情来源、路线图入口与两份历史文档的取代标记。删除后通过哈希、结构、引用白名单和 Git 路径集合验证没有任务状态或范围漂移。

**Tech Stack:** Markdown、`apply_patch`、Git、`rg`、`shasum`、POSIX shell。

**Spec:** `docs/superpowers/specs/2026-08-31-task-index-retirement-design.md`

## Global Constraints

- `docs/task-handoffs/tensor-v1-task-board.md` 是任务身份、顺序、定义、依赖、状态、设计和交接的唯一权威来源。
- 只删除 `docs/planning/task-index.md`；不创建替代静态索引。
- 实施提交相对其父提交的路径范围必须恰为一个删除和四个修改。
- 不改变任务状态、顺序、依赖、设计路径、交接路径、验收或状态证据。
- `M02-T05` 必须保持 `NOT_STARTED`，其 Handoff 必须保持 `None`。
- 两份 2026-08-26 历史文档只添加取代标记，不改写历史正文。
- 实施提交消息固定为 `docs: retire duplicate task index`。

---

### Task 1: 固定任务事实基线并退役重复来源

**Files:**

- Delete: `docs/planning/task-index.md`
- Modify: `docs/task-handoffs/tensor-v1-task-board.md`

**Interfaces:**

- Consumes: Git `HEAD` 中 77 行 task board 任务表；当前整行 SHA-256 为 `7002cd2a97b41bf18d102104e7b80e4b870d1f41728a4abf518a28166578bf49`。
- Produces: 唯一权威职责明确的 task board，以及每项只引用一个现存模块任务卡的 77 个 `Sources`。

- [ ] **Step 1: 验证删除前基线**

Run:

```bash
test "$(git show HEAD:docs/task-handoffs/tensor-v1-task-board.md | rg -c '^\| [0-9]+ \| M[0-9]{2}-T[0-9]{2} \|')" -eq 77
test "$(git show HEAD:docs/task-handoffs/tensor-v1-task-board.md | rg '^\| [0-9]+ \| M[0-9]{2}-T[0-9]{2} \|' | shasum -a 256 | awk '{print $1}')" = 7002cd2a97b41bf18d102104e7b80e4b870d1f41728a4abf518a28166578bf49
test "$(rg -c '^- \*\*Sources:\*\* 1\.' docs/task-handoffs/tensor-v1-task-board.md)" -eq 77
test "$(rg -c '；2\. `docs/planning/task-index\.md`' docs/task-handoffs/tensor-v1-task-board.md)" -eq 77
rg '^\| 12 \| M02-T05 \|.*\| `NOT_STARTED` \|.*\| None \|$' docs/task-handoffs/tensor-v1-task-board.md
```

Expected: all commands exit `0`; both counts are `77`, the hash equals the fixed baseline, and M02-T05 is `NOT_STARTED` with Handoff `None`.

- [ ] **Step 2: 删除静态索引并更新 task board**

Use `apply_patch` to:

1. delete `docs/planning/task-index.md`;
2. add this Workflow item without changing the existing four items:

```markdown
- **Authority:** This board is the sole authoritative source for task identity, order, definition, dependencies, status, design documents, and handoffs.
```

3. transform every task-detail source from this exact shape:

```markdown
- **Sources:** 1. `<module-plan>` 的 `Task <task-id>` 任务卡；2. `docs/planning/task-index.md` 的 `<task-id>` 行。
```

to this exact shape:

```markdown
- **Sources:** `<module-plan>` 的 `Task <task-id>` 任务卡。
```

Only the 77 `Sources` lines may receive that mechanical replacement; retain all State evidence verbatim.

- [ ] **Step 3: 验证 task board 结构与任务事实不变**

Run:

```bash
test ! -e docs/planning/task-index.md
test "$(rg -c '^\| [0-9]+ \| M[0-9]{2}-T[0-9]{2} \|' docs/task-handoffs/tensor-v1-task-board.md)" -eq 77
test "$(rg '^\| [0-9]+ \| M[0-9]{2}-T[0-9]{2} \|' docs/task-handoffs/tensor-v1-task-board.md | shasum -a 256 | awk '{print $1}')" = 7002cd2a97b41bf18d102104e7b80e4b870d1f41728a4abf518a28166578bf49
test "$(rg -c '^### `M[0-9]{2}-T[0-9]{2}`$' docs/task-handoffs/tensor-v1-task-board.md)" -eq 77
test "$(rg -c '^- \*\*Sources:\*\* `docs/superpowers/plans/tensor-modules/M[0-9]{2}-[^`]+\.md` 的 `Task M[0-9]{2}-T[0-9]{2}` 任务卡。$' docs/task-handoffs/tensor-v1-task-board.md)" -eq 77
test "$(rg -c 'docs/planning/task-index\.md' docs/task-handoffs/tensor-v1-task-board.md)" -eq 1
rg '^- \*\*State evidence:\*\* .*docs/planning/task-index\.md' docs/task-handoffs/tensor-v1-task-board.md
rg '^- \*\*Authority:\*\* This board is the sole authoritative source' docs/task-handoffs/tensor-v1-task-board.md
rg '^\| 12 \| M02-T05 \|.*\| `NOT_STARTED` \|.*\| None \|$' docs/task-handoffs/tensor-v1-task-board.md
```

Expected: all commands exit `0`; the one remaining old-path mention is existing State evidence, not an active source; M02-T05 remains unchanged.

- [ ] **Step 4: 验证全部模块任务卡文件存在**

Run:

```bash
rg '^- \*\*Sources:\*\* `([^`]+)`' --replace '$1' docs/task-handoffs/tensor-v1-task-board.md | sort -u | while IFS= read -r source_path; do test -f "$source_path" || exit 1; done
```

Expected: command exits `0` with no missing path.

### Task 2: 切换活跃入口并标记历史文档

**Files:**

- Modify: `docs/superpowers/plans/2026-08-25-tensor-implementation-roadmap.md`
- Modify: `docs/superpowers/specs/2026-08-26-task-handoff-retirement-design.md`
- Modify: `docs/superpowers/plans/2026-08-26-task-handoff-retirement.md`

**Interfaces:**

- Consumes: Task 1 中成为唯一权威任务索引的 `docs/task-handoffs/tensor-v1-task-board.md`。
- Produces: 指向 task board 的路线图入口，以及明确不再约束当前工作流的两份历史记录。

- [ ] **Step 1: 更新路线图第 5 节**

Replace only the paragraph under `## 5. 任务索引` with:

```markdown
M00–M14 的 77 个任务统一维护在 [`docs/task-handoffs/tensor-v1-task-board.md`](../../task-handoffs/tensor-v1-task-board.md)。该看板是任务身份、顺序、定义、依赖、状态、设计和交接的唯一权威来源；AI 工时、模块交付物和实施细节继续由本路线图及其链接的 15 个模块任务计划提供。
```

Do not change the effort summary or module-plan links elsewhere in the roadmap.

- [ ] **Step 2: 给两份历史文档添加相同的取代标记**

Immediately after each document title, insert:

```markdown
> **历史记录（已被取代，2026-08-31）：** 本文关于保留 `docs/planning/task-index.md` 的决定已被项目所有者正式取代；正文仅用于说明 2026-08-26 的迁移，不再定义当前任务工作流。当前唯一权威任务索引为 [`docs/task-handoffs/tensor-v1-task-board.md`](../../task-handoffs/tensor-v1-task-board.md)。
```

Do not modify any later historical body text.

- [ ] **Step 3: 验证入口与历史边界**

Run:

```bash
rg -n -A2 '^## 5\. 任务索引$' docs/superpowers/plans/2026-08-25-tensor-implementation-roadmap.md
test "$(rg -c '^> \*\*历史记录（已被取代，2026-08-31）：\*\*' docs/superpowers/specs/2026-08-26-task-handoff-retirement-design.md)" -eq 1
test "$(rg -c '^> \*\*历史记录（已被取代，2026-08-31）：\*\*' docs/superpowers/plans/2026-08-26-task-handoff-retirement.md)" -eq 1
test "$(rg -l 'docs/planning/task-index\.md' docs --glob '*.md' | sort | tr '\n' ' ')" = "docs/superpowers/plans/2026-08-26-task-handoff-retirement.md docs/superpowers/plans/2026-08-31-task-index-retirement.md docs/superpowers/specs/2026-08-26-task-handoff-retirement-design.md docs/superpowers/specs/2026-08-31-task-index-retirement-design.md docs/task-handoffs/tensor-v1-task-board.md "
```

Expected: the roadmap points to the task board; both banners occur exactly once; old-path references remain only in this retirement design/plan, bannered historical documents, and existing board State evidence.

### Task 3: 审核精确范围并提交

**Files:**

- Verify: all files changed by Tasks 1–2

**Interfaces:**

- Consumes: Tasks 1–2 的已验证文档变更。
- Produces: 一个范围精确、格式无误且可由 Git 恢复的退役提交。

- [ ] **Step 1: 审阅内容差异和路径集合**

Run:

```bash
git diff -- docs/task-handoffs/tensor-v1-task-board.md docs/superpowers/plans/2026-08-25-tensor-implementation-roadmap.md docs/superpowers/specs/2026-08-26-task-handoff-retirement-design.md docs/superpowers/plans/2026-08-26-task-handoff-retirement.md docs/planning/task-index.md
git diff --name-status
```

Expected: exactly four `M` entries and one `D` entry, with no task-table, State evidence, effort-summary, module-link, or historical-body changes.

- [ ] **Step 2: 运行最终验证**

Repeat every verification command from Task 1 Steps 3–4 and Task 2 Step 3, then run:

```bash
git diff --check
test "$(git diff --name-status | rg -c '^M\s')" -eq 4
test "$(git diff --name-status | rg -c '^D\s+docs/planning/task-index\.md$')" -eq 1
test "$(git diff --name-status | wc -l | tr -d ' ')" -eq 5
```

Expected: every command exits `0` and the diff scope is exactly one deletion plus four modifications.

- [ ] **Step 3: 暂存精确路径并提交**

Run:

```bash
git add docs/task-handoffs/tensor-v1-task-board.md docs/superpowers/plans/2026-08-25-tensor-implementation-roadmap.md docs/superpowers/specs/2026-08-26-task-handoff-retirement-design.md docs/superpowers/plans/2026-08-26-task-handoff-retirement.md docs/planning/task-index.md
git diff --cached --check
git diff --cached --name-status
git commit -m "docs: retire duplicate task index"
```

Expected: staged scope is the same four modifications and one deletion; commit succeeds with the fixed message.

- [ ] **Step 4: 复核提交结果**

Run:

```bash
git status --short
git show --stat --oneline --summary HEAD
git show HEAD:docs/task-handoffs/tensor-v1-task-board.md | rg '^\| 12 \| M02-T05 \|.*\| `NOT_STARTED` \|.*\| None \|$'
```

Expected: worktree is clean, the commit summary contains only the five intended paths, and committed M02-T05 remains `NOT_STARTED` with Handoff `None`.
