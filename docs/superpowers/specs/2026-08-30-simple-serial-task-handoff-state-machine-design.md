# Simple Serial Task Handoff State Machine Design

## Status

The user approved the written design in conversation on 2026-08-30. The design
is ready for implementation planning; implementation remains a separate step.

## Goal

Expand `managing-task-handoffs` so it can:

1. split a large project into an explicitly ordered list of manageable tasks;
2. record and update the lifecycle state of each task with a small state machine;
3. preserve the existing `pause` and `next-task` handoff behavior; and
4. prepare the next task only after the current task is completed.

The workflow is serial by project convention. The user guarantees that work is
not performed on multiple tasks in parallel and that a blocked task is resolved
before other work begins. The skill records task state but does not enforce
cross-task mutual exclusion.

## Scope

Included:

- Create one project task board from established project facts.
- Assign every task a stable ID, unique positive order, title, goal, scope,
  acceptance criteria, direct dependencies, sources, first action, and status.
- Manage the six statuses `NOT_STARTED`, `READY`, `IN_PROGRESS`, `PAUSED`,
  `BLOCKED`, and `COMPLETED`.
- Select the next task by the task board's predefined order after the current
  task completes.
- Create a `pause` handoff when unfinished work pauses or becomes blocked.
- Create a `next-task` handoff after the current task completes and before the
  selected next task is prepared for a later session.
- Keep the project task board as the only authoritative source of task status.
- Keep handoffs as contextual transfer artifacts rather than status stores.

Excluded:

- Any dependency on `designing-task-contracts`, `docs/task-designs/`, or task
  design artifacts.
- Implementation planning or implementation of the project tasks themselves.
- Cross-task concurrency checks, locks, leases, roles, permissions, approval
  models, hashes, receipts, event logs, replay, recovery, or background workers.
- Automatic verification commands while writing a handoff.
- Automatic replanning, task insertion, task cancellation, or completed-task
  reopening.
- Reading, migrating, or modifying the legacy `.task-handoff/` runtime.

## Approach

### 1. Artifacts and authority

The skill creates and updates two kinds of Markdown artifacts:

```text
docs/task-handoffs/<project-id>-task-board.md
docs/task-handoffs/<task-id>-handoff.md
```

The task board is authoritative for task identity, order, task definition, and
current status. A handoff names its task board and task ID, but its status text
is only a snapshot. If a handoff conflicts with the task board, the task board
wins and the skill stops until the handoff is refreshed.

The task board has these sections in order:

1. `Project` — project ID, goal, scope, and completion condition.
2. `Workflow` — ordered execution rule and the allowed state transitions.
3. `Tasks` — the authoritative task table.
4. `Task Details` — goal, scope, acceptance criteria, direct dependencies,
   sources, first action, and completion or blocker evidence for each task.
5. `Risks` — unresolved project-level risks without invented requirements.

The authoritative task table uses this shape:

```markdown
| Order | Task ID | Title | Status | Dependencies | Handoff |
|---:|---|---|---|---|---|
| 1 | T01 | Example task | READY | None | None |
```

`Order` values are unique positive integers. `Dependencies` contains only
direct task IDs. `Handoff` is `None` or the exact path of the current handoff
for that task.

### 2. Project decomposition

When asked to split a large project, the skill reads the available project
context and produces tasks that are independently understandable and ordered.
It does not create detailed task designs. Each task entry contains only the
facts the state machine and handoff workflow require:

- a stable task ID and unique order;
- a concrete goal and bounded scope;
- observable acceptance criteria;
- direct dependencies, if any;
- established sources in reading order; and
- one concrete first action.

If a missing fact would materially change the task breakdown, the skill asks
one short question and creates no board yet. It does not invent missing scope,
dependencies, acceptance criteria, sources, or task order.

On successful initialization, the task with the smallest order is `READY` and
all remaining tasks are `NOT_STARTED`. The first task has no predecessor
handoff. Creating the board does not start implementation.

### 3. Per-task state machine

The state machine is applied to one named task at a time:

```text
NOT_STARTED -> READY -> IN_PROGRESS -> COMPLETED
                     |             |
                     | pause       | blocked
                     v             v
                   PAUSED        BLOCKED
                     |             |
                     v             v
                 IN_PROGRESS     READY
```

Allowed transitions and guards are:

| From | To | Required facts and action |
|---|---|---|
| `NOT_STARTED` | `READY` | The task exists and its own required task-board facts are established. Do not inspect or validate any other task's status. |
| `READY` | `IN_PROGRESS` | The user or worker explicitly starts this task. A `next-task` handoff is read when one exists; the first task has none. |
| `IN_PROGRESS` | `PAUSED` | Write a valid `pause` handoff first, then update the task status and handoff path. |
| `PAUSED` | `IN_PROGRESS` | Read the same task's `pause` handoff and confirm it identifies the resume goal, sources, remaining work, and first action. |
| `READY` or `IN_PROGRESS` | `BLOCKED` | Write or update a `pause` handoff that records the exact blocker and its resolution condition, then change status. |
| `BLOCKED` | `READY` | Record established evidence that the blocker is resolved; never infer resolution. |
| `IN_PROGRESS` | `COMPLETED` | Acceptance and verification results are established. Changed files or a passing command alone do not prove the task outcome. |

All other transitions are invalid. In particular, a task cannot move directly
from `NOT_STARTED` to `IN_PROGRESS`, from `PAUSED` to `COMPLETED`, or away from
`COMPLETED`. Invalid requests produce no file changes and report the allowed
next transitions.

The skill does not scan other task rows before `NOT_STARTED -> READY` or before
starting a task. Multiple `READY` rows can therefore exist. Serial execution
and the rule that a `BLOCKED` task must be resolved before other work begins are
user-owned operating constraints, not machine-enforced transition guards.

### 4. Completion and normal handoff flow

The normal successful flow is ordered as follows:

1. Validate the current task's acceptance and supplied verification results.
2. Update the current task from `IN_PROGRESS` to `COMPLETED` and record its
   established completion evidence in `Task Details`.
3. Select the non-completed task with the smallest greater `Order` value.
4. Create `docs/task-handoffs/<next-task-id>-handoff.md` in `next-task` mode.
5. After the handoff is valid and written, update that task from
   `NOT_STARTED` to `READY` and record the handoff path in the task table.

Steps 2 and 4-5 are deliberately separable. If the next-task context is
missing or direct dependencies conflict, the completed task remains
`COMPLETED`, the next task remains unchanged, no partial handoff is written,
and the skill asks one short question about the first material gap.

If no later non-completed task exists, the project is complete and no
`next-task` handoff is generated. The skill does not invent another task.

The predefined `Order` is the only automatic next-task selection rule. The
skill does not scan filenames, infer priority, or reorder tasks from dependency
shape. Dependencies are validated only as inputs to the selected next-task
handoff, using the existing direct-dependency rules.

### 5. Pause and blocked handoffs

`pause` remains the handoff type for unfinished work. It records:

- what is complete, partial, blocked, and unverified;
- relevant changed files;
- exact supplied verification commands and results;
- supplied remaining work without expanding scope;
- the task to resume, established sources, and first action; and
- risks and blockers.

When a task becomes `BLOCKED`, its `pause` handoff must identify the blocker and
the concrete condition that would resolve it. Its first action is blocker
resolution, not implementation continuation. Resolving the blocker returns the
task to `READY`; restarting work is a separate `READY -> IN_PROGRESS`
transition.

### 6. Next-task handoffs

`next-task` remains the handoff type for a completed current task with an
established successor. The file is named for the successor because it is the
successor session's entry point. It contains only:

- the next task's ID, goal, scope, and acceptance criteria;
- its directly consumed dependency artifacts, decisions, rationales,
  constraints, usage, and supplied readiness evidence;
- ordered sources and one concrete first action; and
- non-conflicting risks.

It does not summarize all predecessor history. An unresolved dependency
conflict or missing required fact prevents handoff creation and prevents the
automatic `NOT_STARTED -> READY` update for the selected next task.

### 7. Failure handling

Every individual task transition follows a read-check-write order:

1. Read the named task board and locate the exact task ID.
2. Confirm the observed source status matches the requested transition.
3. Validate only the facts required for that transition.
4. Write any required handoff.
5. Update the task-board row and corresponding task detail.

The skill stops without mutation when the task board is missing, the task ID is
unknown, the source status is stale, the transition is invalid, required
handoff facts are missing, completion evidence is insufficient, or dependency
inputs conflict. Because the design is instruction-only and single-user, it
does not provide atomic file locking or concurrent-write recovery.

Completing the current task and preparing its successor are two consecutive
transitions, not one atomic mutation. Completion is committed first; a failure
while preparing the successor cannot erase or roll back established completion.

## Files

Implementation changes only the installed `managing-task-handoffs` skill:

- `/Users/qiangzhiwei/.agents/skills/managing-task-handoffs/SKILL.md` — add
  project decomposition, state transitions, sequencing, authority rules, and
  routing to the appropriate reference template.
- `/Users/qiangzhiwei/.agents/skills/managing-task-handoffs/agents/openai.yaml`
  — expand discoverability text from handoff writing to project task splitting,
  state updates, and handoffs.
- `/Users/qiangzhiwei/.agents/skills/managing-task-handoffs/references/project-task-board-template.md`
  — add the fixed project task board contract.
- `/Users/qiangzhiwei/.agents/skills/managing-task-handoffs/references/task-handoff-template.md`
  — add the task-board path, task ID, expected source state, and pause/blocker
  linkage while preserving the current pause evidence sections.
- `/Users/qiangzhiwei/.agents/skills/managing-task-handoffs/references/next-task-handoff-template.md`
  — add the task-board path, completed predecessor ID, selected successor ID,
  and expected successor state while preserving current direct-dependency
  validation.

Implementation must not modify:

- `/Users/qiangzhiwei/.agents/skills/designing-task-contracts/`;
- any `docs/task-designs/` file;
- `/Users/qiangzhiwei/code/github/Tensor/.task-handoff/`; or
- Tensor product source files.

A fresh bounded staging directory under
`/tmp/Tensor-simple-task-handoff-state-machine-20260830/` will hold the staged
skill, a backup of the installed skill, and external contract tests. The staged
copy must pass before installation, and the installed copy must pass again.

## Tests

Run the skill validator against the staged and installed copies:

```bash
python3 /Users/qiangzhiwei/.codex/skills/.system/skill-creator/scripts/quick_validate.py /tmp/Tensor-simple-task-handoff-state-machine-20260830/managing-task-handoffs
python3 /Users/qiangzhiwei/.codex/skills/.system/skill-creator/scripts/quick_validate.py /Users/qiangzhiwei/.agents/skills/managing-task-handoffs
```

Expected result for each command: exit code `0` and `Skill is valid!`.

Run the external contract suite against staged and installed copies:

```bash
HANDOFF_SKILL_ROOT=/tmp/Tensor-simple-task-handoff-state-machine-20260830/managing-task-handoffs python3 -m unittest -v /tmp/Tensor-simple-task-handoff-state-machine-20260830/tests/test_task_handoff_state_machine.py
HANDOFF_SKILL_ROOT=/Users/qiangzhiwei/.agents/skills/managing-task-handoffs python3 -m unittest -v /tmp/Tensor-simple-task-handoff-state-machine-20260830/tests/test_task_handoff_state_machine.py
```

Expected result: all tests pass for both roots. The structural suite checks
that the following contracts are present and internally consistent:

1. Project breakdown creates the required board sections, unique orders, one
   initial `READY` task, and remaining `NOT_STARTED` tasks.
2. The six statuses and only the allowed transition pairs are documented.
3. `NOT_STARTED -> READY` contains no cross-task status guard.
4. `NOT_STARTED -> IN_PROGRESS`, `PAUSED -> COMPLETED`, and transitions away
   from `COMPLETED` are declared invalid and require zero mutation.
5. `IN_PROGRESS -> PAUSED` requires a valid `pause` handoff.
6. `IN_PROGRESS -> BLOCKED` records a blocker and resolution condition;
   `BLOCKED -> READY` requires established resolution evidence.
7. `IN_PROGRESS -> COMPLETED` requires outcome-level acceptance and supplied
   verification evidence.
8. Completion creates a `next-task` handoff before the selected successor is
   changed from `NOT_STARTED` to `READY`.
9. A missing or conflicting successor input leaves the completed task complete,
   leaves the successor unchanged, and writes no partial handoff.
10. The final task completes without inventing a successor handoff.
11. The skill and templates do not reference `designing-task-contracts`,
    `docs/task-designs/`, or the legacy `.task-handoff/` runtime.

In addition, run fresh-session behavioral evaluations with the staged skill and
repeat them after installation. Use these exact scenarios and expected results:

1. Ask to split a three-task project with complete facts. Expected: one board,
   first task `READY`, remaining tasks `NOT_STARTED`, and no handoff yet.
2. Ask to move a named `NOT_STARTED` task to `READY` while another row is
   `READY`. Expected: the requested task is evaluated only from its own facts;
   no cross-task concurrency rejection is introduced.
3. Ask to start a `NOT_STARTED` task. Expected: no file changes and a report
   that `READY` is the only allowed next state.
4. Ask to pause an `IN_PROGRESS` task with complete pause facts. Expected: the
   pause handoff is written before the board changes to `PAUSED`.
5. Ask to block an `IN_PROGRESS` task without a resolution condition. Expected:
   one short question and no mutation.
6. Ask to complete an `IN_PROGRESS` task with changed files but no outcome-level
   evidence. Expected: no completion claim and no successor handoff.
7. Supply complete completion and successor facts. Expected: current task first
   becomes `COMPLETED`, then the successor handoff is written, then the
   successor becomes `READY`.
8. Complete the final task. Expected: `COMPLETED` with no invented successor or
   handoff.

Verify the isolation boundary explicitly:

```bash
rg -n "designing-task-contracts|docs/task-designs|\.task-handoff" /Users/qiangzhiwei/.agents/skills/managing-task-handoffs
```

Expected result: no matches.

## Acceptance

- The skill can create one ordered task board for a large project without
  creating or requiring task designs.
- Every task has exactly one of the six allowed statuses.
- The skill accepts only the documented per-task state transitions.
- `NOT_STARTED -> READY` does not inspect other task statuses, and the skill
  makes no claim that it enforces serial execution.
- A normal handoff is created only after the current task reaches
  `COMPLETED`; the successor becomes `READY` only after that handoff is valid.
- The first task can be initialized as `READY` without a predecessor handoff.
- A paused or blocked task has a resume-quality `pause` handoff.
- A blocked task cannot return to `READY` without established resolution
  evidence.
- Completion requires outcome-level acceptance and supplied verification
  evidence.
- Automatic successor selection follows only the predefined unique order.
- The final task completes without a fabricated successor.
- The task board is the sole task-status authority; handoffs remain contextual
  snapshots.
- The installed `designing-task-contracts` skill, `docs/task-designs/`, legacy
  `.task-handoff/`, and Tensor product source remain unchanged.

## Risks

- Serial execution and the rule to resolve blockers before other work are
  user-owned conventions. The skill intentionally does not detect violations.
- A single Markdown task board is easy to inspect but has no locking. External
  concurrent edits can overwrite state; this is accepted because the workflow
  is user-controlled and serial.
- A handoff can become stale after task-board edits. The board remains
  authoritative, and a mismatch must stop the workflow until the handoff is
  refreshed.
- The next task can remain `NOT_STARTED` after the previous task completes when
  required handoff facts are missing. This is a safe partial outcome: completed
  evidence is retained, and no readiness claim is invented.
