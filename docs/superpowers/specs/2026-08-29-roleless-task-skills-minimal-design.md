# Roleless Task Skills Minimalization Design

## Status

The user approved this design on 2026-08-29. It replaces the earlier plan to
repair the lifecycle engine. The two installed skills will retain only task
design and task handoff authoring.

## Goal

Replace the current roleless task skills with two small, static Markdown
authoring skills:

- `designing-task-contracts` creates or revises a high-quality task design.
- `managing-task-handoffs` creates or revises a high-quality task handoff.

Neither skill manages execution. They do not create runtime state, enforce a
lifecycle, authorize work, or recover prior state.

## Installed Structure

Each installed skill has exactly three files:

```text
designing-task-contracts/
├── SKILL.md
├── agents/openai.yaml
└── references/task-design-template.md

managing-task-handoffs/
├── SKILL.md
├── agents/openai.yaml
└── references/task-handoff-template.md
```

The installed directories contain no scripts, assets, test harnesses, runtime
modules, package compilers, or additional references.

## Task Design Contract

When the user asks to create, revise, or review a task design, the design skill
uses a user-specified path. If no path is specified, it writes
`docs/task-designs/<task-id>-design.md`.

The document contains these sections:

1. Goal
2. Scope
3. Approach
4. Files
5. Tests
6. Acceptance
7. Risks

The design must be specific enough for another capable worker to implement
without guessing. It distinguishes confirmed facts from unresolved questions,
preserves explicit user decisions, and does not invent missing requirements.

## Task Handoff Contract

When the user asks to pause, transfer, or summarize task work, the handoff skill
uses a user-specified path. If no path is specified, it writes
`docs/task-handoffs/<task-id>-handoff.md`.

The document contains these sections:

1. Completed
2. Changed Files
3. Verification
4. Remaining Work
5. Next Step
6. Risks

The handoff reports only observed work. It includes exact commands and results
when available, labels unverified claims, and never turns plans or intentions
into completed work. `Verification` is a reporting section only. Creating the
handoff does not execute any command, create or manage verification state, or
start or continue implementation.

A changed file or passing test is not by itself a completed outcome. The
handoff does not infer what remaining work changes or which file it belongs in.
It does not infer that code, tests, documentation, or any other artifact must
change from an unfinished outcome. It copies supplied unfinished scope without
expanding it. When a needed fact is missing, it says the fact is not established
and makes inspection or clarification the next step.

## Interaction Rules

- Before drafting, ask one short question and stop when a missing fact
  materially changes the design. Otherwise mark the unknown as unresolved.
- Never guess, assume, or choose missing requirements to make a design appear
  implementation-ready.
- Preserve user terminology and decisions.
- Prefer concrete file paths, commands, results, and acceptance statements over
  process narration.
- Do not start or continue implementation merely because a design or handoff
  was requested.

## Removed Capabilities

The replacement skills remove all of the following:

- command-line interfaces and Python runtimes;
- JSON design packages and compilation;
- lifecycle states and transitions;
- role, identity, permission, approval, authority, and delegation models;
- hashes, receipts, manifests, events, records, locks, and provenance models;
- recovery, replay, migration, adoption, initialization, and archival;
- access scopes, secret scanners, path enforcement, and execution gates;
- readiness, start, checkpoint, verification, completion, suspension, task
  insertion, and plan revision operations.

These removals apply to skill functionality. External deployment verification
may still compare files and run tests; those tools are not installed as skill
capabilities.

## Staging and Deployment

Build a new staged pair at
`/tmp/Tensor-roleless-task-skills-20260829`. Do not reuse the stale
`/tmp/Tensor-task-skill-split-20260827` tree.

Before replacing either installed directory, copy its complete current contents
to a bounded backup under the new staging root. Validate both staged skills
before installation. Replace the installed directories from the staged trees so
obsolete files cannot survive deployment. Validate the installed copies again.

The deployment changes only:

- `/Users/qiangzhiwei/.agents/skills/designing-task-contracts/`
- `/Users/qiangzhiwei/.agents/skills/managing-task-handoffs/`

It does not modify Tensor product source or `.task-handoff`.

## Testing

The verification harness lives outside both skills. It runs against staged and
installed roots and checks:

- exact three-file inventories;
- valid skill frontmatter and UI metadata;
- the required design and handoff sections;
- correct default output paths;
- absence of removed runtime capabilities and models;
- design behavior that does not invent missing requirements;
- handoff behavior that does not fabricate work or verification.

Behavioral scenarios are run before and after the rewrite. The pre-rewrite run
must demonstrate the unwanted lifecycle behavior. A post-rewrite design run
with materially incomplete input asks one short question and creates nothing
yet; otherwise post-rewrite runs produce only the approved static artifacts.

## Completion Evidence

Completion requires all staged and installed validation suites to pass. It also
requires an exact before-and-after comparison of these five Tensor files:

- `.task-handoff/config.yaml`
- `.task-handoff/current.yaml`
- `.task-handoff/events/00000001-init.yaml`
- `.task-handoff/events/00000002-prepare.yaml`
- `.task-handoff/events/00000003-mark-ready.yaml`

Their SHA-256 digests, sizes, modes, and modification times must remain
unchanged.
