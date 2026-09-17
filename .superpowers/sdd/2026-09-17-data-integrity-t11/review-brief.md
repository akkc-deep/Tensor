# T11 Review Package

Workspace: /Users/qiangzhiwei/code/github/Tensor/.worktrees/data-integrity
Spec: docs/task-designs/DATA-INTEGRITY-T11-design.md
Plan: docs/superpowers/plans/2026-09-17-data-integrity-t11.md
Review ONLY T11 new production files utils/integrityForm.js, components/integrity/*.vue, views/Integrity{,Check}View.vue and matching unit specs; existing router/index.js + router/index.spec.js, layouts/AppLayout.vue + spec, style.css T11 deltas; e2e/integrity-checks.{fixtures,spec}.js. T10 API/DTO/composable are dependencies, not new diff.
Existing-file before-T11 staged copies: /tmp/tensor-t11-baseline/control-plane/src/... (use diff -u). Other staged files are preexisting unrelated work; do not review whole HEAD diff. Base HEAD 2110948 has mixed uncommitted baseline.

Evidence so far: baseline40/663 pass; core utility/component22 tests pass; initial real browser10/12 pass including1440/1024/390 no overflow. Failing browser cases history total display (implementer fixing) and test uncheck disappeared checkbox (controller changed to click+assert removed). Additional2 recovery boundary browser tests pass after fixes. Full final runs pending.

Known fixes already requested/being completed: prepared storage failure freeze form; recovery independent metadata with original ID/scope visible; newly selected source defaults APIs; same-source hash preserves removed APIs; draft symbol normalization on plugin switch; errors associated with controls; visible exact history total, ElementPlus status icons, remove ornamental step labels; nonwrapping Add button; normal code formatting. Verify current state rather than re-reporting obsolete code.

Check spec and real behavior, race conditions/async generations, disabled/source checks, pending recovery and hash retry, strict limits/dates, no hidden API selection changes, no fabricated conclusions, BigInt/history lifecycle, a11y and meaningful tests. Read-only review, no file changes except your report path; no subagents. Report actionable defects with path/line and reproduction; don't expand T12 scope. T11 detail deliberately minimal and no GET. Do not demand backend/MySQL verification for T11.
