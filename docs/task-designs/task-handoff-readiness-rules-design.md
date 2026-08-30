# Task Handoff Dependency Readiness Rules Design

## Goal

Add a small dependency-readiness gate to `managing-task-handoffs` so each
identified next task has a `READY` or `BLOCKED` conclusion, and implementation
starts only when dependencies are ready and compatible. The handoff remains a
static Markdown artifact rather than a runtime state machine.

## Scope

Included:

- Validate only the direct dependencies consumed by the next task.
- Confirm required artifacts are identified and available for read-only use.
- Confirm each dependency's decision, rationale, compatibility constraint, and
  usage are established.
- Compare dependency constraints and block unresolved conflicts.
- Produce a visible `READY` or `BLOCKED` conclusion; only `READY` permits work.
- Preserve the existing `pause` workflow.

Excluded:

- Lifecycle engines, databases, event logs, locks, roles, permissions, hashes,
  background automation, or new scripts.
- Running predecessor tests or modifying artifacts during handoff creation.
- Expanding indirect dependency history or summarizing predecessor tasks.
- Treating readiness as authorization for external or production mutations.

## Approach

### Readiness contract

Add `## Readiness` to the `next-task` template. It has exactly two values:

```text
READY
BLOCKED
```

Once the next task is identified, create or update its handoff with the current
readiness result. `BLOCKED` is persisted so an older `READY` conclusion cannot
remain authoritative after validation fails. Revalidation updates the same
document; no event history or separate state file is created. If the next task
itself is unknown, no target handoff can be created, so ask one short question.

Each direct dependency keeps the existing fields and adds an explicit check:

```markdown
### `<task-id-or-title>`

- **Artifact:** Exact source of truth consumed by the next task.
- **Decision:** Decision the next task must preserve.
- **Rationale:** Established reason for the decision.
- **Constraint:** Compatibility rule the next task must not violate.
- **Usage:** How the next task consumes the artifact.
- **Readiness evidence:** Existing evidence required to trust the input, when applicable.
- **Check:** PASS or FAIL
```

Do not include a predecessor merely because it appears earlier in the plan.
Include it only when the next task directly consumes its artifact or decision.

### Validation order

Validate in this order:

1. **Task definition:** next-task ID or title, goal, scope, acceptance criteria,
   ordered sources, and first action are established.
2. **Dependency identity:** every direct dependency is listed once and names the
   exact artifact the next task consumes.
3. **Artifact availability:** each declared artifact is available for exact,
   read-only inspection. Do not search for substitute artifacts.
4. **Decision completeness:** decision, rationale, constraint, and usage are
   established by the artifact, cited task design, or supplied context.
5. **Input readiness:** when usability depends on completion or verification
   evidence, that evidence is already supplied and supports the claim. Do not
   run verification commands during handoff creation.
6. **Compatibility:** compare all direct-dependency decisions and constraints.
   No two requirements may demand incompatible behavior from the next task.
7. **Start entry:** when all checks pass, the reading order and first work action
   use only dependencies that passed the preceding checks.

A task with no direct dependencies skips checks 2 through 6. It can be ready
when the task definition and start entry are complete.

Record every dependency that can be evaluated as `PASS` or `FAIL`. Missing,
unreadable, unverified, or conflicting inputs are `FAIL`. Do not infer missing
values to make a dependency pass.

### Decision rule

```text
all required checks pass  -> Readiness: READY; normal Start Here action
any required check fails  -> Readiness: BLOCKED; blocker-resolution action only
```

A `BLOCKED` handoff adds `## Blockers`, lists the exact failed dependency and
reason, and asks one short resolution question. It must not provide or authorize
an implementation-start action.

Examples of blocking conditions:

- A required artifact is missing, unreadable, or not identified exactly.
- A dependency decision or compatibility constraint is unknown.
- Required readiness evidence is absent or explicitly unverified.
- Two dependencies impose incompatible constraints, such as JWT versus Session
  for the same authentication contract.

The blocking question names the highest-priority failure. It must not ask the
next worker to choose implicitly or continue around the conflict.

### Start rule

A new session may start the task only when all of these are true:

1. The expected next-task handoff exists.
2. `Handoff Type` is `next-task`.
3. `Readiness` is `READY`.
4. Every listed direct dependency has `Check: PASS`.
5. `Start Here` provides an ordered source list and one concrete first action.

Otherwise the new session stops and reports the first missing or conflicting
fact. Before implementation, it performs a read-only freshness check that the
listed artifacts are still available and still express the recorded decisions
and constraints. Drift changes `Readiness` to `BLOCKED`. Reading a `pause`
handoff authorizes only resuming that paused task; it does not make a different
task ready.

## Files

- `/Users/qiangzhiwei/.agents/skills/managing-task-handoffs/SKILL.md`:
  add the validation order, `READY/BLOCKED` decision, and start rule.
- `/Users/qiangzhiwei/.agents/skills/managing-task-handoffs/references/next-task-handoff-template.md`:
  add `Readiness`, per-dependency `Check`, and `Blockers` fields.
- `/Users/qiangzhiwei/.agents/skills/managing-task-handoffs/references/task-handoff-template.md`:
  no functional change; verify the pause contract still works.

No scripts, runtime files, or Tensor product source files are added or changed.

## Tests

Run the installed skill validator:

```bash
python3 /Users/qiangzhiwei/.codex/skills/.system/skill-creator/scripts/quick_validate.py /Users/qiangzhiwei/.agents/skills/managing-task-handoffs
```

Expected result: exit code `0` and `Skill is valid!`.

Check that the readiness contract is discoverable:

```bash
rg -n "Readiness|READY|BLOCKED|Check: PASS|Start rule" /Users/qiangzhiwei/.agents/skills/managing-task-handoffs
```

Expected result: matches occur in `SKILL.md` and the next-task template, and no
matches add readiness state to the pause template.

Run these behavior scenarios against a staged skill before installation and
against the installed copy afterward:

1. No dependencies and complete task/start facts -> `READY` handoff.
2. One complete direct dependency -> one dependency block with `Check: PASS`.
3. Multiple compatible dependencies -> all blocks pass and the handoff is
   created without predecessor summaries.
4. Missing artifact or decision -> `BLOCKED`; one precise blocking question.
5. Required evidence marked unverified -> `BLOCKED`.
6. JWT versus Session constraints -> `BLOCKED`; conflict is named explicitly.
7. `pause` request -> pause template output remains unchanged and contains no
   readiness gate.

## Acceptance

- Every identified next task has one current handoff whose readiness is
  `READY` or `BLOCKED`; revalidation updates that same document.
- `READY` requires every direct dependency to contain `Check: PASS`.
- Multiple predecessors are validated independently and then checked for
  compatibility.
- Missing, unverified, or conflicting inputs produce `Readiness: BLOCKED`, no
  implementation-start action, and one precise blocking question.
- The document contains only the next task and its directly consumed inputs;
  it contains no predecessor summary or indirect history.
- A new session can determine from the document alone whether it may start and
  which sources to read first.
- `pause` behavior remains unchanged.
- The skill remains instruction-only with no scripts or runtime state.

## Risks

- `READY` is a point-in-time document conclusion. The new session therefore
  performs a read-only freshness check before implementation and marks drift as
  `BLOCKED`.
- Read-only inspection can establish availability and declared compatibility,
  but it cannot prove semantic correctness beyond the evidence already
  available. Required verification evidence must therefore be explicit.
- This design intentionally changes the earlier roleless-minimalization decision
  that removed readiness gates, but keeps the gate document-only and does not
  restore lifecycle or authorization machinery.
