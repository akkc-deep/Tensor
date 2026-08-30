# Simple Serial Task Handoff State Machine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand `managing-task-handoffs` so it can split a large project into an ordered task board, track each task through a small state machine, and create pause or next-task handoffs at the correct lifecycle points.

**Architecture:** Keep the installed skill instruction-only. One Markdown task board is the authoritative task definition and status source; per-task Markdown handoffs carry pause or successor context. The state machine evaluates one named task at a time, does not enforce cross-task serial execution, and creates the normal successor handoff only after the current task is completed.

**Tech Stack:** Markdown skill instructions and templates, YAML UI metadata, Python `unittest` contract tests, Codex skill quick validator, bounded `/tmp` staging and backup.

**Spec:** `docs/superpowers/specs/2026-08-30-simple-serial-task-handoff-state-machine-design.md`

## Global Constraints

- Read and follow the approved spec before changing any skill file.
- At execution start, invoke `superpowers:writing-skills` and `skill-creator` and follow their validation workflows.
- Stage everything under `/tmp/Tensor-simple-task-handoff-state-machine-20260830/`; do not edit the installed skill until the staged copy passes.
- The installed `managing-task-handoffs` skill must contain exactly five files and no scripts or runtime state.
- Do not modify `/Users/qiangzhiwei/.agents/skills/designing-task-contracts/`, any `docs/task-designs/` file, `/Users/qiangzhiwei/code/github/Tensor/.task-handoff/`, or Tensor product source.
- Do not add cross-task status scans to `NOT_STARTED -> READY` or `READY -> IN_PROGRESS`; the user owns serial execution and blocker discipline.
- Preserve only these six states: `NOT_STARTED`, `READY`, `IN_PROGRESS`, `PAUSED`, `BLOCKED`, and `COMPLETED`.
- A normal `next-task` handoff is created after current-task completion and before the successor is updated to `READY`.
- Do not run project verification commands while authoring a handoff; report only supplied results.
- The workspace and installed skill directories are not Git repositories. Do not initialize Git or add commit steps; use the bounded backup and staged/installed verification as the recovery mechanism.

## File Structure

Staging and test files:

- Create: `/tmp/Tensor-simple-task-handoff-state-machine-20260830/tests/test_task_handoff_state_machine.py` — external structural contract suite.
- Create: `/tmp/Tensor-simple-task-handoff-state-machine-20260830/managing-task-handoffs/SKILL.md` — routing, task decomposition, state machine, and handoff sequencing.
- Create: `/tmp/Tensor-simple-task-handoff-state-machine-20260830/managing-task-handoffs/agents/openai.yaml` — updated discovery metadata.
- Create: `/tmp/Tensor-simple-task-handoff-state-machine-20260830/managing-task-handoffs/references/project-task-board-template.md` — authoritative task-board schema.
- Create: `/tmp/Tensor-simple-task-handoff-state-machine-20260830/managing-task-handoffs/references/task-handoff-template.md` — pause and blocked handoff schema.
- Create: `/tmp/Tensor-simple-task-handoff-state-machine-20260830/managing-task-handoffs/references/next-task-handoff-template.md` — completed-to-successor handoff schema.
- Create: `/tmp/Tensor-simple-task-handoff-state-machine-20260830/installed-backup/` — exact pre-install backup of `managing-task-handoffs`.

Installed files:

- Modify: `/Users/qiangzhiwei/.agents/skills/managing-task-handoffs/SKILL.md`
- Modify: `/Users/qiangzhiwei/.agents/skills/managing-task-handoffs/agents/openai.yaml`
- Create: `/Users/qiangzhiwei/.agents/skills/managing-task-handoffs/references/project-task-board-template.md`
- Modify: `/Users/qiangzhiwei/.agents/skills/managing-task-handoffs/references/task-handoff-template.md`
- Modify: `/Users/qiangzhiwei/.agents/skills/managing-task-handoffs/references/next-task-handoff-template.md`

---

### Task 1: Create the external contract suite and prove RED

**Files:**

- Create: `/tmp/Tensor-simple-task-handoff-state-machine-20260830/tests/test_task_handoff_state_machine.py`
- Read only: `/Users/qiangzhiwei/.agents/skills/managing-task-handoffs/`

**Interfaces:**

- Consumes: `HANDOFF_SKILL_ROOT`, the skill root to validate.
- Produces: three `unittest` classes covering board/state rules, handoff templates, and isolation/inventory.

- [ ] **Step 1: Confirm the staging root is unused**

Run:

```bash
test ! -e /tmp/Tensor-simple-task-handoff-state-machine-20260830
```

Expected: exit code `0`. If the directory exists, inspect it and stop rather than overwriting unknown files.

- [ ] **Step 2: Write the failing external contract test**

Use `apply_patch` to create the test file with this exact content:

```python
import os
import unittest
from pathlib import Path


ROOT = Path(os.environ["HANDOFF_SKILL_ROOT"])


def headings(path: Path) -> tuple[str, ...]:
    return tuple(
        line.removeprefix("## ")
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.startswith("## ")
    )


class BoardAndStateContractTests(unittest.TestCase):
    def setUp(self):
        self.skill = (ROOT / "SKILL.md").read_text(encoding="utf-8")
        self.board = ROOT / "references" / "project-task-board-template.md"

    def test_description_and_board_route(self):
        self.assertIn(
            "split a project into tasks, track task states, pause work, or hand off a completed task",
            self.skill,
        )
        self.assertIn("references/project-task-board-template.md", self.skill)

    def test_board_template_sections(self):
        self.assertEqual(
            headings(self.board),
            ("Project", "Workflow", "Tasks", "Task Details", "Risks"),
        )

    def test_six_states_are_present(self):
        for state in (
            "NOT_STARTED",
            "READY",
            "IN_PROGRESS",
            "PAUSED",
            "BLOCKED",
            "COMPLETED",
        ):
            with self.subTest(state=state):
                self.assertIn(state, self.skill)

    def test_only_expected_transitions_are_declared(self):
        for transition in (
            "NOT_STARTED -> READY",
            "READY -> IN_PROGRESS",
            "IN_PROGRESS -> PAUSED",
            "PAUSED -> IN_PROGRESS",
            "READY -> BLOCKED",
            "IN_PROGRESS -> BLOCKED",
            "BLOCKED -> READY",
            "IN_PROGRESS -> COMPLETED",
        ):
            with self.subTest(transition=transition):
                self.assertIn(transition, self.skill)
        self.assertIn("All other transitions are invalid", self.skill)

    def test_ready_has_no_cross_task_guard(self):
        self.assertIn("Do not inspect other task statuses", self.skill)
        self.assertIn("The user owns serial execution", self.skill)

    def test_completion_handoff_order(self):
        markers = (
            "update the current task to `COMPLETED`",
            "write the `next-task` handoff",
            "update the successor to `READY`",
        )
        positions = tuple(self.skill.index(marker) for marker in markers)
        self.assertEqual(positions, tuple(sorted(positions)))


class HandoffTemplateContractTests(unittest.TestCase):
    def test_pause_template_sections(self):
        self.assertEqual(
            headings(ROOT / "references" / "task-handoff-template.md"),
            (
                "Handoff Type",
                "Task Link",
                "Current State",
                "Changed Files",
                "Verification",
                "Remaining Work",
                "Resume Task",
                "Start Here",
                "Blocker",
                "Risks",
            ),
        )

    def test_pause_template_links_transition(self):
        text = (ROOT / "references" / "task-handoff-template.md").read_text(
            encoding="utf-8"
        )
        for field in ("Task board", "Task ID", "Observed status", "Target status"):
            with self.subTest(field=field):
                self.assertIn(field, text)
        self.assertIn("PAUSED or BLOCKED", text)
        self.assertIn("Resolution condition", text)

    def test_next_template_sections(self):
        self.assertEqual(
            headings(ROOT / "references" / "next-task-handoff-template.md"),
            (
                "Handoff Type",
                "Task Link",
                "Next Task",
                "Dependencies",
                "Start Here",
                "Risks",
            ),
        )

    def test_next_template_links_completed_task_and_successor(self):
        text = (
            ROOT / "references" / "next-task-handoff-template.md"
        ).read_text(encoding="utf-8")
        for field in (
            "Task board",
            "Completed task",
            "Next task",
            "Expected next status",
        ):
            with self.subTest(field=field):
                self.assertIn(field, text)
        self.assertIn("READY", text)


class InventoryAndIsolationContractTests(unittest.TestCase):
    def test_exact_inventory(self):
        files = {
            path.relative_to(ROOT).as_posix()
            for path in ROOT.rglob("*")
            if path.is_file()
        }
        self.assertEqual(
            files,
            {
                "SKILL.md",
                "agents/openai.yaml",
                "references/project-task-board-template.md",
                "references/task-handoff-template.md",
                "references/next-task-handoff-template.md",
            },
        )

    def test_ui_metadata(self):
        text = (ROOT / "agents" / "openai.yaml").read_text(encoding="utf-8")
        self.assertIn('display_name: "Task Handoff"', text)
        self.assertIn(
            'short_description: "Split projects, track task states, and hand off work"',
            text,
        )

    def test_forbidden_coupling_is_absent(self):
        combined = "\n".join(
            path.read_text(encoding="utf-8")
            for path in ROOT.rglob("*")
            if path.is_file()
        )
        for forbidden in (
            "designing-task-contracts",
            "docs/task-designs",
            ".task-handoff",
        ):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, combined)

    def test_no_runtime_files(self):
        self.assertFalse(any(path.suffix == ".py" for path in ROOT.rglob("*")))
        self.assertFalse((ROOT / "scripts").exists())


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 3: Run the suite against the current installed skill**

Run:

```bash
HANDOFF_SKILL_ROOT=/Users/qiangzhiwei/.agents/skills/managing-task-handoffs python3 -m unittest -v /tmp/Tensor-simple-task-handoff-state-machine-20260830/tests/test_task_handoff_state_machine.py
```

Expected: `FAILED`. The current skill has no project task board template, state-transition contract, completion-to-successor sequence, or five-file inventory.

---

### Task 2: Build the staged project board and state-machine contract

**Files:**

- Create: `/tmp/Tensor-simple-task-handoff-state-machine-20260830/managing-task-handoffs/SKILL.md`
- Create: `/tmp/Tensor-simple-task-handoff-state-machine-20260830/managing-task-handoffs/agents/openai.yaml`
- Create: `/tmp/Tensor-simple-task-handoff-state-machine-20260830/managing-task-handoffs/references/project-task-board-template.md`

**Interfaces:**

- Consumes: established project goal, scope, task facts, and explicit transition request.
- Produces: `docs/task-handoffs/<project-id>-task-board.md` with one authoritative row and detail block per task.

- [ ] **Step 1: Create the staged skill directories**

Run:

```bash
mkdir -p /tmp/Tensor-simple-task-handoff-state-machine-20260830/managing-task-handoffs/agents /tmp/Tensor-simple-task-handoff-state-machine-20260830/managing-task-handoffs/references
```

Expected: exit code `0` and only the bounded staging directories are created.

- [ ] **Step 2: Write the staged `SKILL.md`**

Use `apply_patch` to create this exact content:

```markdown
---
name: managing-task-handoffs
description: Use when a user asks to split a project into tasks, track task states, pause work, or hand off a completed task.
---

# Managing Task Handoffs

Manage one evidence-based project task board and the handoffs associated with
its tasks. This skill records workflow state and transfer context. It does not
start or continue implementation merely because a board or handoff is created.

## Choose the operation

- **Split project:** create `docs/task-handoffs/<project-id>-task-board.md` from
  established project facts. Read
  [the project task board template](references/project-task-board-template.md).
- **Transition task:** update one named task through an allowed state change.
- **Pause or block:** read
  [the pause handoff template](references/task-handoff-template.md), write the
  handoff, then update the board.
- **Complete and hand off:** complete the current task, then read
  [the next-task handoff template](references/next-task-handoff-template.md),
  write the successor handoff, and prepare the successor.

Use a user-specified path instead of a default path. Use only established facts.
Ask one short question and stop when a missing fact materially changes the task
breakdown, transition, or handoff. Never invent tasks, scope, dependencies,
acceptance criteria, results, remaining work, sources, or next actions.

## Project task board

The task board is the only authority for task identity, order, definition, and
status. Handoffs contain snapshots and entry context; they do not replace board
state. If a handoff conflicts with its named board and task ID, stop and refresh
the handoff from established facts before continuing.

When splitting a project, give each task a stable ID, unique positive order,
title, goal, bounded scope, observable acceptance criteria, direct dependencies,
ordered sources, one concrete first action, and one allowed status. If these
facts cannot be established, ask one short question and create no board yet.

On initialization, set the smallest-order task to `READY` and every other task
to `NOT_STARTED`. The first task has no predecessor handoff. Board creation does
not start the first task.

## State machine

The only states are `NOT_STARTED`, `READY`, `IN_PROGRESS`, `PAUSED`, `BLOCKED`,
and `COMPLETED`. Apply transitions to one exact task ID:

- `NOT_STARTED -> READY`
- `READY -> IN_PROGRESS`
- `IN_PROGRESS -> PAUSED`
- `PAUSED -> IN_PROGRESS`
- `READY -> BLOCKED`
- `IN_PROGRESS -> BLOCKED`
- `BLOCKED -> READY`
- `IN_PROGRESS -> COMPLETED`

All other transitions are invalid. Reject an invalid transition without writing
any file and report the task's allowed next transitions.

For `NOT_STARTED -> READY`, require only that the named task exists and its own
board facts are established. Do not inspect other task statuses. Apply the same
local rule when starting a `READY` task. The user owns serial execution and the
rule that a blocker is resolved before other work begins; this skill does not
enforce cross-task mutual exclusion.

For `READY -> IN_PROGRESS`, start only on an explicit request. Read the task's
current handoff when one exists and use its ordered sources and first action.

For `IN_PROGRESS -> PAUSED`, write a valid `pause` handoff before updating the
board. For `PAUSED -> IN_PROGRESS`, read that handoff and confirm it establishes
the resume task, remaining work, ordered sources, and first action.

For `READY -> BLOCKED` or `IN_PROGRESS -> BLOCKED`, write a `pause` handoff that
names the blocker and its resolution condition before updating the board. For
`BLOCKED -> READY`, require supplied evidence that the blocker is resolved.
Never infer resolution. Restarting work is a separate `READY -> IN_PROGRESS`
transition.

For `IN_PROGRESS -> COMPLETED`, require established outcome-level acceptance
and supplied verification results. A changed file or passing command is not by
itself a completed outcome. Record the evidence in the task detail.

## Complete and prepare the successor

After validating completion, perform these operations in order:

1. update the current task to `COMPLETED` and record its evidence;
2. select the non-completed task with the smallest greater order;
3. write the `next-task` handoff for that successor; and
4. update the successor to `READY` and record the handoff path.

The completion transition and successor preparation are separate. If successor
facts are missing or dependency inputs conflict, retain the completed state,
leave the successor unchanged, write no partial handoff, and ask one short
question about the first material gap.

If no later non-completed task exists, the project is complete. Do not create a
handoff or invent another task. Never select a successor from filenames,
repository scans, inferred priority, or dependency shape; predefined task order
is the only automatic selection rule.

## Handoff rules

For `pause`, confirm the task to resume, unfinished scope, at least one source or
artifact, and a concrete first action. Report supplied verification commands and
results without running them. Copy supplied remaining work without expanding it.

For `next-task`, include only the successor and its directly consumed inputs.
Each dependency must identify its exact artifact, decision, rationale,
constraint, usage, and supplied readiness evidence when usability depends on
that evidence. Compare direct-dependency decisions and constraints. An
unresolved conflict prevents handoff creation and successor preparation.

## Safe mutation order

For each individual transition:

1. read the named board and locate the exact task ID;
2. confirm the observed source status;
3. validate only the facts required by the transition;
4. write any handoff required before that transition; and
5. update the board row and matching task detail.

Stop without mutation when the board is missing, the task is unknown, the
source status is stale, the transition is invalid, required facts are missing,
completion evidence is insufficient, or dependencies conflict.

## Common mistakes

- Creating the normal successor handoff before the current task is completed.
- Treating `READY` as implementation already started.
- Scanning other task statuses to enforce serial execution.
- Treating a handoff snapshot as the authoritative task status.
- Guessing a next task instead of following the predefined order.
- Summarizing predecessor history that the successor does not consume.
```

- [ ] **Step 3: Write staged UI metadata**

Use `apply_patch` with:

```yaml
interface:
  display_name: "Task Handoff"
  short_description: "Split projects, track task states, and hand off work"
  default_prompt: "Use $managing-task-handoffs to split a project, update task state, or create a handoff."
policy:
  allow_implicit_invocation: true
```

- [ ] **Step 4: Write the project task-board template**

Use `apply_patch` with:

```markdown
# Project Task Board Template

Use these sections in order. Replace guidance with established project facts.

## Project

- **Project ID:** Stable identifier.
- **Goal:** Concrete project outcome.
- **Scope:** Included work and explicit exclusions.
- **Completion condition:** Observable condition for all project work to finish.

## Workflow

- **Execution:** Serial execution is owned by the user; the board does not enforce cross-task exclusion.
- **Next-task selection:** Choose the non-completed task with the smallest greater `Order` after the current task completes.
- **Allowed transitions:** `NOT_STARTED -> READY`, `READY -> IN_PROGRESS`, `IN_PROGRESS -> PAUSED`, `PAUSED -> IN_PROGRESS`, `READY -> BLOCKED`, `IN_PROGRESS -> BLOCKED`, `BLOCKED -> READY`, `IN_PROGRESS -> COMPLETED`.

## Tasks

| Order | Task ID | Title | Status | Dependencies | Handoff |
|---:|---|---|---|---|---|
| 1 | `<task-id>` | `<title>` | `READY` | `None` | `None` |

Repeat one row per task. Orders are unique positive integers. Dependencies name
only direct task IDs. Status is exactly one allowed state. Handoff is `None` or
the exact current handoff path.

## Task Details

Repeat this block for every task in order.

### `<task-id>`

- **Goal:** Concrete task outcome.
- **Scope:** Included work and explicit exclusions.
- **Acceptance:** Observable completion conditions.
- **Dependencies:** Direct task IDs or `None`.
- **Sources:** Established paths or artifacts in reading order.
- **First action:** One concrete starting action.
- **State evidence:** Established blocker, resolution, or completion evidence; otherwise `None`.

## Risks

List established project-level risks. Write `None identified` when there are none.
```

- [ ] **Step 5: Run the board/state subset and verify GREEN**

Run:

```bash
HANDOFF_SKILL_ROOT=/tmp/Tensor-simple-task-handoff-state-machine-20260830/managing-task-handoffs python3 -m unittest -v test_task_handoff_state_machine.BoardAndStateContractTests
```

Run from `/tmp/Tensor-simple-task-handoff-state-machine-20260830/tests`.

Expected: all `BoardAndStateContractTests` pass. The complete suite still fails because the two staged handoff templates do not exist yet.

---

### Task 3: Integrate pause, blocked, and completed-task handoffs

**Files:**

- Create: `/tmp/Tensor-simple-task-handoff-state-machine-20260830/managing-task-handoffs/references/task-handoff-template.md`
- Create: `/tmp/Tensor-simple-task-handoff-state-machine-20260830/managing-task-handoffs/references/next-task-handoff-template.md`
- Test: `/tmp/Tensor-simple-task-handoff-state-machine-20260830/tests/test_task_handoff_state_machine.py`

**Interfaces:**

- Consumes: exact task-board path, task IDs, observed/target state, and established handoff facts.
- Produces: one pause/blocker handoff or one successor handoff tied back to the authoritative board.

- [ ] **Step 1: Run the handoff tests and verify RED**

Run:

```bash
HANDOFF_SKILL_ROOT=/tmp/Tensor-simple-task-handoff-state-machine-20260830/managing-task-handoffs python3 -m unittest -v test_task_handoff_state_machine.HandoffTemplateContractTests
```

Run from `/tmp/Tensor-simple-task-handoff-state-machine-20260830/tests`.

Expected: errors because both staged handoff template files are absent.

- [ ] **Step 2: Write the pause/blocker handoff template**

Use `apply_patch` with:

```markdown
# Pause Handoff Template

Use these sections in order. Replace guidance with observed task facts.

## Handoff Type

Write exactly `pause`.

## Task Link

- **Task board:** Exact project task-board path.
- **Task ID:** Exact task ID from the board.
- **Observed status:** `READY` or `IN_PROGRESS` before this handoff.
- **Target status:** `PAUSED` or `BLOCKED` after the board update.

## Current State

State what is complete, partial, blocked, and unverified.

## Changed Files

List each relevant changed file and summarize its change. Write `None` when
there are none.

## Verification

List exact supplied commands and results. Write `Not run` when no verification
was supplied. Do not run commands while creating this handoff.

## Remaining Work

List supplied unfinished work and known failures without expanding scope.

## Resume Task

Identify the exact task to resume and its established goal.

## Start Here

List established sources in reading order, then give one concrete first action.
For `BLOCKED`, the first action resolves the blocker rather than continuing
implementation.

## Blocker

For `PAUSED`, write `None`. For `BLOCKED`, state:

- **Reason:** Exact established blocker.
- **Resolution condition:** Observable condition that must be established before `BLOCKED -> READY`.

## Risks

List material risks and blockers. Write `None identified` when there are none.
```

- [ ] **Step 3: Write the completed-to-successor handoff template**

Use `apply_patch` with:

```markdown
# Next Task Handoff Template

Use these sections in order. Include only facts the successor needs.

## Handoff Type

Write exactly `next-task`.

## Task Link

- **Task board:** Exact project task-board path.
- **Completed task:** Exact predecessor task ID whose state is `COMPLETED`.
- **Next task:** Exact successor task ID selected by predefined order.
- **Expected next status:** `READY` after this handoff is written and the board is updated.

## Next Task

State the successor's ID, title, goal, scope, and observable acceptance criteria.

## Dependencies

Write `None` when there are no directly consumed dependencies. Otherwise repeat
this block for each direct input:

### `<task-id-or-title>`

- **Artifact:** Exact source of truth consumed by the successor.
- **Decision:** Decision the successor must preserve.
- **Rationale:** Established reason for that decision.
- **Constraint:** Compatibility rule the successor must not violate.
- **Usage:** How the successor consumes the artifact.
- **Readiness evidence:** Include only when it determines whether the input is usable.

Include only directly consumed inputs. Compare all listed decisions and
constraints. Stop without writing the handoff when any conflict is unresolved.

## Start Here

List established sources in reading order, then give one concrete first action.

## Risks

List non-conflicting risks relevant to the successor. Write `None identified`
when there are none.
```

- [ ] **Step 4: Run the complete structural suite and verify GREEN**

Run:

```bash
HANDOFF_SKILL_ROOT=/tmp/Tensor-simple-task-handoff-state-machine-20260830/managing-task-handoffs python3 -m unittest -v /tmp/Tensor-simple-task-handoff-state-machine-20260830/tests/test_task_handoff_state_machine.py
```

Expected: all tests pass.

- [ ] **Step 5: Run the quick validator against the staged skill**

Run:

```bash
python3 /Users/qiangzhiwei/.codex/skills/.system/skill-creator/scripts/quick_validate.py /tmp/Tensor-simple-task-handoff-state-machine-20260830/managing-task-handoffs
```

Expected: exit code `0` and `Skill is valid!`.

---

### Task 4: Run staged behavioral evaluations and refactor minimally

**Files:**

- Modify only if a scenario fails: staged `SKILL.md` or one staged reference template.
- Test: all files under `/tmp/Tensor-simple-task-handoff-state-machine-20260830/managing-task-handoffs/`.

**Interfaces:**

- Consumes: exact scenario facts and the staged skill.
- Produces: one task board or handoff artifact per scenario, with no implementation work.

- [ ] **Step 1: Evaluate project splitting**

Use a fresh isolated worker with only the staged skill and this prompt:

```text
Create a task board for project DEMO from these established facts. Goal: publish a static product page. Scope: HTML, CSS, and deployment documentation; no backend. Tasks in order: DEMO-01 create semantic HTML, DEMO-02 add responsive CSS depending on DEMO-01, DEMO-03 write deployment documentation depending on DEMO-02. Each task is accepted when its named artifact exists and its supplied check passes. Sources are requirements.md first, then README.md. Do not implement any task.
```

Expected: one `docs/task-handoffs/DEMO-task-board.md`; `DEMO-01` is `READY`, the other tasks are `NOT_STARTED`, orders are unique, and no handoff or task-design artifact is created.

- [ ] **Step 2: Evaluate local-only readiness**

Use a fresh copy of the scenario board in which `DEMO-01` is still `READY`, then request:

```text
Move DEMO-02 from NOT_STARTED to READY. Its own board facts are complete. Do not start implementation.
```

Expected: `DEMO-02` becomes `READY`; the worker does not reject the request because `DEMO-01` is also `READY` and does not scan unrelated status rows.

- [ ] **Step 3: Evaluate invalid transition and pause ordering**

Run two fresh scenarios:

```text
Move DEMO-03 directly from NOT_STARTED to IN_PROGRESS.
```

Expected: no file changes; report that `READY` is the allowed next state.

```text
Pause DEMO-01 from IN_PROGRESS. Established facts: index.html is partial; no verification was run; remaining work is to add the footer; resume by reading requirements.md and inspecting the existing index.html, then add the footer. No blocker is present.
```

Expected: write a `pause` handoff first, then update the board to `PAUSED` with the exact handoff path.

- [ ] **Step 4: Evaluate blocked-state evidence**

Prompt a fresh worker:

```text
Move DEMO-01 from IN_PROGRESS to BLOCKED because deployment credentials are unavailable. No resolution condition has been established.
```

Expected: ask one short question for the resolution condition and make no file changes. After supplying `Credentials are available and readable by the deployment owner`, expected: write the blocker handoff first, then update the board to `BLOCKED`.

- [ ] **Step 5: Evaluate completion and successor ordering**

Run a failing-evidence scenario first:

```text
Complete DEMO-01. index.html changed and an HTML check passed, but no acceptance result was supplied.
```

Expected: no completion claim, no successor handoff, and no successor state change.

Then supply complete facts:

```text
Complete DEMO-01. Acceptance is established: index.html contains the required header, main, and footer. Verification `html5validator index.html` exited 0. DEMO-02 directly consumes index.html, preserves its semantic structure, adds only responsive presentation, reads index.html then requirements.md, and first inspects the existing class structure.
```

Expected order: update `DEMO-01` to `COMPLETED`; write `docs/task-handoffs/DEMO-02-handoff.md`; update `DEMO-02` to `READY`. The handoff contains only DEMO-02 and its directly consumed DEMO-01 input.

- [ ] **Step 6: Evaluate final-task completion**

Prompt a fresh worker with DEMO-03 as the last `IN_PROGRESS` task and complete acceptance and verification facts.

Expected: DEMO-03 becomes `COMPLETED`; no invented successor or successor handoff appears.

- [ ] **Step 7: Refactor only observed failures and rerun validation**

If a staged behavior differs from an expected result, make the smallest instruction or template correction addressing that exact failure. Then rerun all behavioral scenarios plus:

```bash
HANDOFF_SKILL_ROOT=/tmp/Tensor-simple-task-handoff-state-machine-20260830/managing-task-handoffs python3 -m unittest -v /tmp/Tensor-simple-task-handoff-state-machine-20260830/tests/test_task_handoff_state_machine.py
python3 /Users/qiangzhiwei/.codex/skills/.system/skill-creator/scripts/quick_validate.py /tmp/Tensor-simple-task-handoff-state-machine-20260830/managing-task-handoffs
```

Expected: all structural tests pass, quick validation succeeds, and all behavioral scenarios match their expected outcomes.

---

### Task 5: Back up, install, and verify isolation

**Files:**

- Create: `/tmp/Tensor-simple-task-handoff-state-machine-20260830/installed-backup/`
- Modify: `/Users/qiangzhiwei/.agents/skills/managing-task-handoffs/`
- Verify unchanged: `/Users/qiangzhiwei/.agents/skills/designing-task-contracts/`
- Verify unchanged: `/Users/qiangzhiwei/code/github/Tensor/.task-handoff/`

**Interfaces:**

- Consumes: fully validated staged five-file skill.
- Produces: validated installed five-file skill plus a recoverable pre-install backup.

- [ ] **Step 1: Reconfirm the installed inventory has not drifted**

Run:

```bash
find /Users/qiangzhiwei/.agents/skills/managing-task-handoffs -type f -print | sort
```

Expected exactly:

```text
/Users/qiangzhiwei/.agents/skills/managing-task-handoffs/SKILL.md
/Users/qiangzhiwei/.agents/skills/managing-task-handoffs/agents/openai.yaml
/Users/qiangzhiwei/.agents/skills/managing-task-handoffs/references/next-task-handoff-template.md
/Users/qiangzhiwei/.agents/skills/managing-task-handoffs/references/task-handoff-template.md
```

If any file differs, stop and inspect the drift before installing.

- [ ] **Step 2: Capture isolation manifests**

Run:

```bash
find /Users/qiangzhiwei/.agents/skills/designing-task-contracts -type f -exec shasum -a 256 {} \; | LC_ALL=C sort > /tmp/Tensor-simple-task-handoff-state-machine-20260830/design-skill-before.sha256
find /Users/qiangzhiwei/code/github/Tensor/.task-handoff -type f -exec shasum -a 256 {} \; | LC_ALL=C sort > /tmp/Tensor-simple-task-handoff-state-machine-20260830/legacy-runtime-before.sha256
```

Expected: exit code `0`; both manifest files are non-empty.

- [ ] **Step 3: Create the exact installed-skill backup**

Run:

```bash
cp -R /Users/qiangzhiwei/.agents/skills/managing-task-handoffs /tmp/Tensor-simple-task-handoff-state-machine-20260830/installed-backup/
```

Expected: the backup contains the exact four-file pre-install inventory.

- [ ] **Step 4: Install the staged skill**

Request the required filesystem approval, then run:

```bash
rsync -a /tmp/Tensor-simple-task-handoff-state-machine-20260830/managing-task-handoffs/ /Users/qiangzhiwei/.agents/skills/managing-task-handoffs/
```

Expected: exit code `0`. Because the pre-install inventory was checked and the new structure only adds one file, no deletion is required.

- [ ] **Step 5: Run installed structural and skill validation**

Run:

```bash
HANDOFF_SKILL_ROOT=/Users/qiangzhiwei/.agents/skills/managing-task-handoffs python3 -m unittest -v /tmp/Tensor-simple-task-handoff-state-machine-20260830/tests/test_task_handoff_state_machine.py
python3 /Users/qiangzhiwei/.codex/skills/.system/skill-creator/scripts/quick_validate.py /Users/qiangzhiwei/.agents/skills/managing-task-handoffs
```

Expected: all tests pass; quick validator exits `0` with `Skill is valid!`.

- [ ] **Step 6: Repeat the behavioral evaluations against the installed skill**

Repeat Task 4 with fresh isolated workspaces and the installed skill.

Expected: every installed-skill result matches its staged result. Do not reuse staged artifacts as evidence.

- [ ] **Step 7: Verify isolation and exact installed inventory**

Run:

```bash
find /Users/qiangzhiwei/.agents/skills/designing-task-contracts -type f -exec shasum -a 256 {} \; | LC_ALL=C sort > /tmp/Tensor-simple-task-handoff-state-machine-20260830/design-skill-after.sha256
find /Users/qiangzhiwei/code/github/Tensor/.task-handoff -type f -exec shasum -a 256 {} \; | LC_ALL=C sort > /tmp/Tensor-simple-task-handoff-state-machine-20260830/legacy-runtime-after.sha256
diff -u /tmp/Tensor-simple-task-handoff-state-machine-20260830/design-skill-before.sha256 /tmp/Tensor-simple-task-handoff-state-machine-20260830/design-skill-after.sha256
diff -u /tmp/Tensor-simple-task-handoff-state-machine-20260830/legacy-runtime-before.sha256 /tmp/Tensor-simple-task-handoff-state-machine-20260830/legacy-runtime-after.sha256
find /Users/qiangzhiwei/.agents/skills/managing-task-handoffs -type f -print | sort
```

Expected: both `diff` commands produce no output. The installed inventory is exactly:

```text
/Users/qiangzhiwei/.agents/skills/managing-task-handoffs/SKILL.md
/Users/qiangzhiwei/.agents/skills/managing-task-handoffs/agents/openai.yaml
/Users/qiangzhiwei/.agents/skills/managing-task-handoffs/references/next-task-handoff-template.md
/Users/qiangzhiwei/.agents/skills/managing-task-handoffs/references/project-task-board-template.md
/Users/qiangzhiwei/.agents/skills/managing-task-handoffs/references/task-handoff-template.md
```

- [ ] **Step 8: Run the final forbidden-coupling scan**

Run:

```bash
rg -n "designing-task-contracts|docs/task-designs|\.task-handoff" /Users/qiangzhiwei/.agents/skills/managing-task-handoffs
```

Expected: no matches and `rg` exit code `1`.

Record the staged and installed test counts, quick-validator results, behavioral scenario results, isolation diffs, backup path, and final inventory in the completion response.
