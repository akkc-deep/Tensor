# Roleless Task Skills Minimalization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the two installed roleless task skills with static Markdown authoring skills for high-quality task designs and handoffs.

**Architecture:** Each installed skill contains only `SKILL.md`, `agents/openai.yaml`, and one Markdown template reference. A test harness outside the skill directories validates staged and installed copies; deployment first creates bounded backups, then replaces each installed tree exactly so no runtime files survive.

**Tech Stack:** Markdown, Python `unittest`, Codex skill quick validator, `apply_patch`, `rsync`, SHA-256 comparison.

## Global Constraints

- Follow `docs/superpowers/specs/2026-08-29-roleless-task-skills-minimal-design.md` exactly.
- Do not modify any file under `/Users/qiangzhiwei/code/github/Tensor/.task-handoff/`.
- Do not reuse `/tmp/Tensor-task-skill-split-20260827`.
- Stage all replacement artifacts under `/tmp/Tensor-roleless-task-skills-20260829`.
- Installed skill directories contain exactly three files each and no runtime code.
- Do not add role, identity, permission, approval, authority, delegation, lifecycle, package, hash, receipt, manifest, event, record, lock, provenance, recovery, replay, access-control, or execution-gate models.
- The verification harness and deployment backups remain outside installed skill directories.
- The workspace and installed skill directories are not Git repositories. Do not initialize Git or claim commits.

## File Structure

- Create: `/tmp/Tensor-roleless-task-skills-20260829/tests/test_minimal_skills.py` — external staged/installed contract suite.
- Create: `/tmp/Tensor-roleless-task-skills-20260829/designing-task-contracts/SKILL.md` — design authoring instructions.
- Create: `/tmp/Tensor-roleless-task-skills-20260829/designing-task-contracts/agents/openai.yaml` — design skill UI metadata.
- Create: `/tmp/Tensor-roleless-task-skills-20260829/designing-task-contracts/references/task-design-template.md` — seven-section design contract.
- Create: `/tmp/Tensor-roleless-task-skills-20260829/managing-task-handoffs/SKILL.md` — handoff authoring instructions.
- Create: `/tmp/Tensor-roleless-task-skills-20260829/managing-task-handoffs/agents/openai.yaml` — handoff skill UI metadata.
- Create: `/tmp/Tensor-roleless-task-skills-20260829/managing-task-handoffs/references/task-handoff-template.md` — six-section handoff contract.
- Backup: `/tmp/Tensor-roleless-task-skills-20260829/installed-backups/` — complete pre-deployment installed trees.
- Replace: `/Users/qiangzhiwei/.agents/skills/designing-task-contracts/` — exact staged design tree.
- Replace: `/Users/qiangzhiwei/.agents/skills/managing-task-handoffs/` — exact staged handoff tree.

---

### Task 1: Create the external contract suite and prove RED

**Files:**

- Create: `/tmp/Tensor-roleless-task-skills-20260829/tests/test_minimal_skills.py`
- Read only: `/Users/qiangzhiwei/.agents/skills/designing-task-contracts/`
- Read only: `/Users/qiangzhiwei/.agents/skills/managing-task-handoffs/`

**Interfaces:**

- Consumes: two current installed skill roots supplied through environment variables.
- Produces: separate design and handoff contract test classes that can run against staged or installed roots.

- [ ] **Step 1: Verify the new staging root is absent**

Run:

```bash
test ! -e /tmp/Tensor-roleless-task-skills-20260829
```

Expected: exit `0`. If the path exists, inspect it and choose a new bounded path rather than overwriting unknown data.

- [ ] **Step 2: Create the failing external suite**

Use `apply_patch` to create the test file with this content:

```python
import os
import re
import unittest
from pathlib import Path


DESIGN_ROOT = Path(os.environ["DESIGN_SKILL_ROOT"])
HANDOFF_ROOT = Path(os.environ["HANDOFF_SKILL_ROOT"])

FORBIDDEN_TEXT = (
    ".task-handoff",
    "package_version",
    "compile-package",
    "transition_state",
    "ready_receipt",
    "manifest_entries",
    "source_records",
    "identity",
    "permission",
    "approval",
    "authority",
    "delegat",
    "lifecycle",
    "recovery",
    "replay",
)

FORBIDDEN_WORDS = (
    "role",
    "package",
    "hash",
    "receipt",
    "manifest",
    "event",
    "record",
    "lock",
    "provenance",
    "access",
    "gate",
)


class MinimalSkillContract:
    root: Path
    expected_reference: str
    expected_name: str
    expected_description: str
    expected_yaml: str
    required_headings: tuple[str, ...]
    default_path: str

    def test_exact_three_file_inventory(self):
        files = {
            path.relative_to(self.root).as_posix()
            for path in self.root.rglob("*")
            if path.is_file()
        }
        self.assertEqual(
            files,
            {
                "SKILL.md",
                "agents/openai.yaml",
                f"references/{self.expected_reference}",
            },
        )

    def test_frontmatter_is_exact_and_discoverable(self):
        text = (self.root / "SKILL.md").read_text(encoding="utf-8")
        prefix = (
            "---\n"
            f"name: {self.expected_name}\n"
            f"description: {self.expected_description}\n"
            "---\n"
        )
        self.assertTrue(text.startswith(prefix))
        self.assertIn(f"references/{self.expected_reference}", text)

    def test_ui_metadata_is_exact(self):
        observed = (self.root / "agents/openai.yaml").read_text(encoding="utf-8")
        self.assertEqual(observed, self.expected_yaml)

    def test_template_has_exact_required_sections(self):
        text = (
            self.root / "references" / self.expected_reference
        ).read_text(encoding="utf-8")
        headings = tuple(
            line.removeprefix("## ")
            for line in text.splitlines()
            if line.startswith("## ")
        )
        self.assertEqual(headings, self.required_headings)

    def test_default_path_is_documented(self):
        skill = (self.root / "SKILL.md").read_text(encoding="utf-8")
        self.assertIn(self.default_path, skill)

    def test_removed_models_are_absent(self):
        combined = "\n".join(
            path.read_text(encoding="utf-8")
            for path in self.root.rglob("*")
            if path.is_file()
        ).casefold()
        for term in FORBIDDEN_TEXT:
            with self.subTest(term=term):
                self.assertNotIn(term.casefold(), combined)
        for word in FORBIDDEN_WORDS:
            with self.subTest(word=word):
                self.assertIsNone(
                    re.search(rf"\b{re.escape(word.casefold())}\b", combined)
                )


class MinimalDesignSkillTests(MinimalSkillContract, unittest.TestCase):
    root = DESIGN_ROOT
    expected_reference = "task-design-template.md"
    expected_name = "designing-task-contracts"
    expected_description = (
        "Use when a user asks to create, revise, or review a task design "
        "before implementation."
    )
    expected_yaml = (
        'interface:\n'
        '  display_name: "Task Design"\n'
        '  short_description: "Write focused implementation-ready task designs"\n'
        '  default_prompt: "Use $designing-task-contracts to write a focused task design."\n'
        'policy:\n'
        '  allow_implicit_invocation: true\n'
    )
    required_headings = (
        "Goal",
        "Scope",
        "Approach",
        "Files",
        "Tests",
        "Acceptance",
        "Risks",
    )
    default_path = "docs/task-designs/<task-id>-design.md"


class MinimalHandoffSkillTests(MinimalSkillContract, unittest.TestCase):
    root = HANDOFF_ROOT
    expected_reference = "task-handoff-template.md"
    expected_name = "managing-task-handoffs"
    expected_description = (
        "Use when a user asks to pause, transfer, or summarize task work "
        "for another worker."
    )
    expected_yaml = (
        'interface:\n'
        '  display_name: "Task Handoff"\n'
        '  short_description: "Write concise evidence-based task handoffs"\n'
        '  default_prompt: "Use $managing-task-handoffs to write an evidence-based task handoff."\n'
        'policy:\n'
        '  allow_implicit_invocation: true\n'
    )
    required_headings = (
        "Completed",
        "Changed Files",
        "Verification",
        "Remaining Work",
        "Next Step",
        "Risks",
    )
    default_path = "docs/task-handoffs/<task-id>-handoff.md"


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 3: Run the suite against current installed skills and verify RED**

Run:

```bash
DESIGN_SKILL_ROOT=/Users/qiangzhiwei/.agents/skills/designing-task-contracts HANDOFF_SKILL_ROOT=/Users/qiangzhiwei/.agents/skills/managing-task-handoffs PYTHONPYCACHEPREFIX=/tmp/Tensor-roleless-task-skills-20260829/pycache python3 -m unittest -v test_minimal_skills.py
```

Run from `/tmp/Tensor-roleless-task-skills-20260829/tests`.

Expected: `FAILED`; both `test_exact_three_file_inventory` cases fail because runtime files are installed, and the static output-contract tests fail because the old skills describe packages and lifecycle operations.

- [ ] **Step 4: Run pre-rewrite behavioral baselines**

Use fresh isolated worker contexts. Give one worker only the current installed design skill and this request:

```text
Create a task design for adding CSV export to a small web application. Do not implement it. Report the artifacts you would create.
```

Give another worker only the current installed handoff skill and this request:

```text
Create a concise handoff from these facts: src/export.js changed; tests/export.test.js changed; `npm test` passed 12 tests; pagination remains. Do not mutate a project. Report the artifact you would create.
```

Expected RED: the design worker introduces a v3 JSON package/finalization workflow; the handoff worker introduces lifecycle or `.task-handoff` machinery instead of only the approved Markdown artifact. Record their exact behavior in the execution notes.

---

### Task 2: Build and validate the minimal design skill

**Files:**

- Create: `/tmp/Tensor-roleless-task-skills-20260829/designing-task-contracts/SKILL.md`
- Create: `/tmp/Tensor-roleless-task-skills-20260829/designing-task-contracts/agents/openai.yaml`
- Create: `/tmp/Tensor-roleless-task-skills-20260829/designing-task-contracts/references/task-design-template.md`
- Test: `/tmp/Tensor-roleless-task-skills-20260829/tests/test_minimal_skills.py`

**Interfaces:**

- Consumes: an explicit task request and available project context.
- Produces: one seven-section Markdown task design and no other artifact.

- [ ] **Step 1: Confirm the design contract remains RED**

Run the `MinimalDesignSkillTests` class against the installed design skill.

Expected: `FAILED`, including `test_exact_three_file_inventory`.

- [ ] **Step 2: Write the minimal design `SKILL.md`**

Use `apply_patch` with this exact content:

```markdown
---
name: designing-task-contracts
description: Use when a user asks to create, revise, or review a task design before implementation.
---

# Designing Task Contracts

Create one focused Markdown design. Use the path named by the user; otherwise
write `docs/task-designs/<task-id>-design.md`.

Read [the task design template](references/task-design-template.md). Inspect the
available project context before writing. Before drafting, ask one short
question and stop when a missing fact materially changes the design. Otherwise
mark the unknown as unresolved. Never guess, assume, or choose missing
requirements to make a design appear implementation-ready. Do not start
implementation.

The design is ready when another capable worker can implement it without
guessing. Use concrete file paths, interfaces, test commands, expected results,
and observable acceptance criteria. Separate confirmed decisions from genuine
risks. Preserve the user's terminology, scope, and prior decisions when revising
an existing design.

## Common mistakes

- Describing aspirations instead of a concrete approach.
- Listing tests without commands or expected outcomes.
- Hiding unresolved decisions inside implementation steps.
- Expanding scope while revising an existing design.
```

- [ ] **Step 3: Write exact UI metadata**

Use `apply_patch` with:

```yaml
interface:
  display_name: "Task Design"
  short_description: "Write focused implementation-ready task designs"
  default_prompt: "Use $designing-task-contracts to write a focused task design."
policy:
  allow_implicit_invocation: true
```

- [ ] **Step 4: Write the design template reference**

Use `apply_patch` with:

```markdown
# Task Design Template

Use these sections in this order. Replace the guidance with task-specific facts.

## Goal

State the concrete outcome and why it matters.

## Scope

List included work and explicit exclusions.

## Approach

Describe the chosen solution, important interfaces, data flow, and failure handling.

## Files

List files to create, modify, or remove and the responsibility of each.

## Tests

List exact commands, scenarios, and expected results.

## Acceptance

List observable conditions that prove the task is complete.

## Risks

List material risks, assumptions, and unresolved decisions. Write `None identified`
when there are none.
```

- [ ] **Step 5: Verify GREEN for the design skill**

Run:

```bash
python3 /Users/qiangzhiwei/.codex/skills/.system/skill-creator/scripts/quick_validate.py /tmp/Tensor-roleless-task-skills-20260829/designing-task-contracts
DESIGN_SKILL_ROOT=/tmp/Tensor-roleless-task-skills-20260829/designing-task-contracts HANDOFF_SKILL_ROOT=/Users/qiangzhiwei/.agents/skills/managing-task-handoffs PYTHONPYCACHEPREFIX=/tmp/Tensor-roleless-task-skills-20260829/pycache python3 -m unittest -v test_minimal_skills.MinimalDesignSkillTests
```

Run the unittest command from `/tmp/Tensor-roleless-task-skills-20260829/tests`.

Expected: quick validation exits `0`; all six design contract tests pass.

- [ ] **Step 6: Run a design behavioral scenario**

Use a fresh isolated worker with only the staged design skill and the CSV-export request from Task 1.

Expected: because the request omits facts that materially change the design, it
asks one short material question and creates nothing yet. It does not invent
requirements, create a JSON package, or start implementation. Inspect the
actual response and workspace rather than trusting the worker's summary.

---

### Task 3: Build and validate the minimal handoff skill

**Files:**

- Create: `/tmp/Tensor-roleless-task-skills-20260829/managing-task-handoffs/SKILL.md`
- Create: `/tmp/Tensor-roleless-task-skills-20260829/managing-task-handoffs/agents/openai.yaml`
- Create: `/tmp/Tensor-roleless-task-skills-20260829/managing-task-handoffs/references/task-handoff-template.md`
- Test: `/tmp/Tensor-roleless-task-skills-20260829/tests/test_minimal_skills.py`

**Interfaces:**

- Consumes: observed work, changed files, verification facts, remaining work, and risks.
- Produces: one six-section Markdown handoff and no other artifact.

- [ ] **Step 1: Confirm the handoff contract remains RED**

Run the `MinimalHandoffSkillTests` class against the installed handoff skill.

Expected: `FAILED`, including `test_exact_three_file_inventory`.

- [ ] **Step 2: Write the minimal handoff `SKILL.md`**

Use `apply_patch` with this exact content:

```markdown
---
name: managing-task-handoffs
description: Use when a user asks to pause, transfer, or summarize task work for another worker.
---

# Managing Task Handoffs

Create one evidence-based Markdown handoff. Use the path named by the user;
otherwise write `docs/task-handoffs/<task-id>-handoff.md`.

Read [the task handoff template](references/task-handoff-template.md). Use only
facts and material already available in the request or current context. Report
only observed facts. Distinguish work that is complete, partial, planned,
blocked, or unverified. Never convert an intention into completed work.

A changed file or passing test is not by itself a completed outcome. Do not
infer what remaining work changes or which file it belongs in. Do not infer
that code, tests, documentation, or any other artifact must change from an
unfinished outcome. Copy the supplied unfinished scope into Remaining Work
without expanding it. When a needed fact is missing, say it is not established
and make inspection or clarification the next step.

Verification is reporting-only. Include concrete file paths and exact commands
and results only when they were already supplied. Do not run any command while
creating the handoff, including a command listed as verification evidence, and
do not create or manage verification state. If verification was not run or its
result is unknown, say so.

Creating a handoff must not start or continue implementation. Make the next
step a specific inspection or clarification that another capable worker can
use to identify the next change.

## Common mistakes

- Claiming success from code changes without verification evidence.
- Omitting partial work or known breakage.
- Inventing required code, test, documentation, or file changes.
- Writing a vague next step such as "continue implementation."
- Repeating history that does not help the next worker.
```

- [ ] **Step 3: Write exact UI metadata**

Use `apply_patch` with:

```yaml
interface:
  display_name: "Task Handoff"
  short_description: "Write concise evidence-based task handoffs"
  default_prompt: "Use $managing-task-handoffs to write an evidence-based task handoff."
policy:
  allow_implicit_invocation: true
```

- [ ] **Step 4: Write the handoff template reference**

Use `apply_patch` with:

```markdown
# Task Handoff Template

Use these sections in this order. Replace the guidance with observed task facts.

## Completed

List completed outcomes only. Identify partial or unverified work explicitly.

## Changed Files

List each changed file and summarize its relevant change.

## Verification

List exact commands and results. Write `Not run` when no verification was run.

## Remaining Work

List unfinished work, known failures, and open decisions.

## Next Step

Give the next worker one concrete starting action.

## Risks

List material risks and blockers. Write `None identified` when there are none.
```

- [ ] **Step 5: Verify GREEN for the handoff skill**

Run:

```bash
python3 /Users/qiangzhiwei/.codex/skills/.system/skill-creator/scripts/quick_validate.py /tmp/Tensor-roleless-task-skills-20260829/managing-task-handoffs
DESIGN_SKILL_ROOT=/tmp/Tensor-roleless-task-skills-20260829/designing-task-contracts HANDOFF_SKILL_ROOT=/tmp/Tensor-roleless-task-skills-20260829/managing-task-handoffs PYTHONPYCACHEPREFIX=/tmp/Tensor-roleless-task-skills-20260829/pycache python3 -m unittest -v test_minimal_skills.MinimalHandoffSkillTests
```

Run the unittest command from `/tmp/Tensor-roleless-task-skills-20260829/tests`.

Expected: quick validation exits `0`; all six handoff contract tests pass.

- [ ] **Step 6: Run a handoff behavioral scenario**

Use a fresh isolated worker with only the staged handoff skill and the evidence-based request from Task 1.

Expected: it proposes one Markdown handoff using exactly the six sections,
reports no completed outcome as established, records `npm test` as 12 passing
tests, keeps pagination in Remaining Work, and makes inspection or clarification
the Next Step without assigning pagination to a file or inferring code, test,
documentation, or other artifact changes. It does not run commands, start or
continue implementation, introduce lifecycle files, or create verification
state. Inspect the actual artifact.

---

### Task 4: Validate the staged pair and deploy recoverably

**Files:**

- Verify: `/tmp/Tensor-roleless-task-skills-20260829/designing-task-contracts/`
- Verify: `/tmp/Tensor-roleless-task-skills-20260829/managing-task-handoffs/`
- Create backup: `/tmp/Tensor-roleless-task-skills-20260829/installed-backups/`
- Replace: `/Users/qiangzhiwei/.agents/skills/designing-task-contracts/`
- Replace: `/Users/qiangzhiwei/.agents/skills/managing-task-handoffs/`

**Interfaces:**

- Consumes: both independently GREEN staged skills.
- Produces: exact minimal installed copies plus complete bounded backups of the prior installed trees.

- [ ] **Step 1: Run the complete staged suite**

Run both quick validators, then:

```bash
DESIGN_SKILL_ROOT=/tmp/Tensor-roleless-task-skills-20260829/designing-task-contracts HANDOFF_SKILL_ROOT=/tmp/Tensor-roleless-task-skills-20260829/managing-task-handoffs PYTHONPYCACHEPREFIX=/tmp/Tensor-roleless-task-skills-20260829/staged-pycache python3 -m unittest -v test_minimal_skills.py
```

Run from `/tmp/Tensor-roleless-task-skills-20260829/tests`.

Expected: both quick validators exit `0`; all 12 contract tests pass.

- [ ] **Step 2: Back up both installed trees**

Create `/tmp/Tensor-roleless-task-skills-20260829/installed-backups` and copy the complete current installed directories into it. Refuse to overwrite an existing backup.

Expected: the backup contains the current design package compiler and the complete current handoff runtime and tests.

- [ ] **Step 3: Replace installed trees exactly**

After confirming the backup, run these exact scoped replacements:

```bash
rsync -a --delete /tmp/Tensor-roleless-task-skills-20260829/designing-task-contracts/ /Users/qiangzhiwei/.agents/skills/designing-task-contracts/
rsync -a --delete /tmp/Tensor-roleless-task-skills-20260829/managing-task-handoffs/ /Users/qiangzhiwei/.agents/skills/managing-task-handoffs/
```

Expected: both commands exit `0`. These commands require sandbox approval because they replace installed user-level skills. Do not broaden either source or destination.

- [ ] **Step 4: Run the complete installed suite from `/tmp`**

Run both quick validators against installed paths, then:

```bash
DESIGN_SKILL_ROOT=/Users/qiangzhiwei/.agents/skills/designing-task-contracts HANDOFF_SKILL_ROOT=/Users/qiangzhiwei/.agents/skills/managing-task-handoffs PYTHONPYCACHEPREFIX=/tmp/Tensor-roleless-task-skills-20260829/installed-pycache python3 -m unittest -v test_minimal_skills.py
```

Run from `/tmp/Tensor-roleless-task-skills-20260829/tests`.

Expected: both quick validators exit `0`; all 12 installed contract tests pass.

- [ ] **Step 5: Recover on installed verification failure**

If any installed check fails, stop and restore only the two exact installed targets from `/tmp/Tensor-roleless-task-skills-20260829/installed-backups` using scoped `rsync -a --delete`, then rerun the pre-deployment installed checks. Do not touch any project file while recovering the skills.

---

### Task 5: Run final behavioral and zero-change verification

**Files:**

- Verify: both staged and installed skill roots.
- Read only: the five Tensor `.task-handoff` files listed in the design.

**Interfaces:**

- Consumes: installed pair passing static validation.
- Produces: fresh staged/installed evidence, behavioral evidence, and exact `.task-handoff` zero-change evidence.

- [ ] **Step 1: Run post-install behavioral scenarios**

Repeat the two Task 1 scenarios with fresh workers loading only the corresponding installed skill.

Expected: the underspecified design request produces one short material question
and no artifact yet. The handoff request produces only its approved Markdown
artifact with six exact sections. Neither invents missing facts or introduces
removed machinery. Handoff verification remains reporting-only: no command is
run, no verification state is created, and no implementation is started or
continued. The handoff copies `pagination remains` without inferring code,
test, documentation, or other artifact changes.

- [ ] **Step 2: Re-run all staged and installed suites fresh**

Run both quick validators and the full 12-test external suite once against staged roots and once against installed roots.

Expected: four quick validations exit `0`; staged reports 12 passed; installed reports 12 passed.

- [ ] **Step 3: Verify exact installed inventories**

Run `find` against each installed skill and sort the relative file list.

Expected for design:

```text
SKILL.md
agents/openai.yaml
references/task-design-template.md
```

Expected for handoff:

```text
SKILL.md
agents/openai.yaml
references/task-handoff-template.md
```

- [ ] **Step 4: Recompute the five `.task-handoff` baselines**

Run `shasum -a 256` and `stat -f '%N|%z|%Lp|%m'` on the five exact paths.

Expected SHA-256 values:

```text
d745924f5104c28ec873cee8f961545307dceb01804f3af2ea2910899391ceeb  .task-handoff/config.yaml
b8f761ef0cd55ff72e38ab84b98154e405caf7d94c3035c65cb7da62d9e5cfe9  .task-handoff/current.yaml
29d3f0da0b19b007e11cde46c2a09898c20cf6980b2f1d8ad266355ec613a61c  .task-handoff/events/00000001-init.yaml
f727c5787936dffb4230ae5f8b070d179440f6f2e788ea5e8527a7a769aa7256  .task-handoff/events/00000002-prepare.yaml
5835e7e8a0f508d061c7687e9bd466446146d42364d8374260548e4f1899b152  .task-handoff/events/00000003-mark-ready.yaml
```

Expected size, mode, and modification time values:

```text
.task-handoff/config.yaml|180|644|1787817095
.task-handoff/current.yaml|12224|600|1787817194
.task-handoff/events/00000001-init.yaml|1458|644|1787817095
.task-handoff/events/00000002-prepare.yaml|13373|600|1787817130
.task-handoff/events/00000003-mark-ready.yaml|13384|600|1787817194
```

- [ ] **Step 5: Report completion evidence**

Report the staged and installed validation counts, behavioral scenario outcomes, exact installed inventories, backup path, and the five unchanged handoff file comparisons. State that Git commits were unavailable because neither target is a Git repository.
