# Task 1 report: report UI, issue lifecycle, repeat-check copy

## Status

Implemented the complete frontend source/unit-test scope from `task-1-brief.md`. No e2e or docs files were edited. The 16 exact `control-plane/src` paths listed below are staged; no commit was created.

## TDD evidence

- Formatter first RED (2026-09-17 16:00 Asia/Shanghai): `integrityReport.spec.js` executed 3 tests, 3 failed on real empty return values. The first failure expected exact `9223372036854775807`, received `''`; rate expected `95%`, received `''`. GREEN: 1 file / 3 tests passed.
- Issue composable RED: 1 file / 6 tests failed because no GET/defaults, cancellation, snapshot retention, validation, or scope disposal existed. GREEN: 1 file / 6 tests passed.
- Pagination RED: 1 file / 2 tests failed because exact BigInt totals and controls were absent. GREEN: 1 file / 2 tests passed.
- Summary RED: 1 file / 5 tests failed on missing execution/conclusion/progress/scope/time behavior. GREEN: 1 file / 5 tests passed.
- Results RED: 1 file / 5 tests failed on exact report rows, applied filters, saved basis, non-stock/null descriptor, and retained errors. GREEN: 1 file / 5 tests passed.
- Issues RED: 1 file / 5 tests failed on full keys, dates, rule-name fallback, evidence, validation, retry/close, and empty state. GREEN: 1 file / 5 tests passed.
- Detail view RED: 1 file / 7 tests failed on deep-link loading, invalid IDs, filters, issue focus, reconnect/repeat, route cleanup, and retained snapshots. GREEN: 1 file / 7 tests passed.
- Creation view initial RED: 11 tests ran, 4 new copy tests failed. Later two focused regression tests were observed RED for uppercase UUID loading and A-to-B copy failure retaining A. GREEN after normalization and scope/confirmation clearing: 1 file / 15 tests passed.

## Final focused verification

Command:

`PATH=/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin:$PATH npm --prefix control-plane test -- src/utils/integrityReport.spec.js src/composables/useIntegrityIssues.spec.js src/components/integrity src/views/IntegrityCheckView.spec.js src/views/IntegrityView.spec.js`

Result: **11 files / 58 tests passed**, exit 0. `git diff --cached --check` also exited 0. The controller is running the full frontend/build/e2e gates once for the shared worktree, so they are intentionally not duplicated here.

## Delivered behavior

- Exact BigInt/null/decimal-rate/scalar formatters without Number conversion or BigInt JSON serialization.
- Independent issue GET state with validated criteria, generation plus AbortController isolation, same-condition snapshot retention, explicit refresh/reset/dispose, and no polling.
- Summary keeps execution and saved data conclusion separate, shows original request versus fixed scope, exact progress/status counts, IDs, Shanghai timestamps, errors, and Element Plus status icons.
- Results keep one table row per resultId; saved descriptor/rules/evidence remain historical, with NON_STOCK/null-descriptor, ERROR/NOT_RUN, incomplete/truncated, exact statistics, applied filters, BigInt pagination, and selected-result events.
- Issues show all business-key scalars, related dates, primary-date filtering warning/reset, saved rule-name lookup with ID/version fallback, evidence, error retry, truncation warning, focus/close restoration, and exact pagination.
- Detail deep links locally reject invalid IDs, preserve same-task snapshots on GET failure, expose GET-only reconnect, coordinate result/issue filters, clear state across IDs, and navigate repeat checks by `fromCheckId` only.
- Creation view gives pending recovery priority; otherwise copies only the saved scope, refreshes current capability/categories without selecting new APIs or reusing IDs, clears confirmation, blocks missing/disabled sources and failed replacement copies, and rejects late route/user-edit responses.
- UI uses the existing Tensor tokens/layout, accessible region headings/labels, keyboard focus, local horizontal table scrolling, mobile single-column summary, and icon-plus-text states.

## Staged source paths

- `control-plane/src/utils/integrityReport.js` and `.spec.js`
- `control-plane/src/composables/useIntegrityIssues.js` and `.spec.js`
- `control-plane/src/components/integrity/IntegritySummary.vue` and `.spec.js`
- `control-plane/src/components/integrity/IntegrityResultsTable.vue` and `.spec.js`
- `control-plane/src/components/integrity/IntegrityIssuesTable.vue` and `.spec.js`
- `control-plane/src/components/integrity/IntegrityPagination.vue` and `.spec.js`
- `control-plane/src/views/IntegrityCheckView.vue` and `.spec.js`
- `control-plane/src/views/IntegrityView.vue` and `.spec.js`

## Concerns

No known source/unit-test blocker. Full-suite and browser evidence belongs to the controller task and should be appended to the final verification record rather than represented as local Task 1 evidence.

## Independent review fixes: round 1

The four findings in `task-review.md` were reproduced with behavior tests before production changes.

- RED command: `npm --prefix control-plane test -- src/views/IntegrityView.spec.js src/components/integrity/IntegrityIssuesTable.spec.js src/components/integrity/IntegritySummary.spec.js`
- RED result: **3 files failed; 4 tests failed / 25 passed**. Failures proved that copied-source replacement could not submit, rejected-pending copy loading was hidden, the date warning followed an unapplied draft, and QUEUED omitted the active-conclusion note.
- GREEN result after minimal fixes: **3 files / 29 tests passed**, exit 0.
- Full frontend result after round 1: `npm --prefix control-plane test` → **52 files / 739 tests passed**, exit 0, duration 9.13s.

Fixes:

- A deliberate source change now supersedes the `fromCheckId` copy intent, clears saved-copy state, and resumes the ordinary T11 capability/confirmation/submission path.
- Copy attempts from a rejected pending request own an explicit blocking lifecycle. Starting the GET clears the rejected scope and confirmation; loading and request-ID errors render ahead of the pending message; failure cannot submit the old scope.
- The unknown-date exclusion notice now reads only applied criteria, so draft typing/clearing cannot misdescribe the retained issue page.
- QUEUED and RUNNING reports both show “进行中，已有结论” while retaining their distinct execution labels.

Round 1 added four regression tests. No source/unit blocker remains; controller-owned e2e/build verification follows separately.

## Independent review fixes: round 2

The remaining rejected-pending plus invalid `fromCheckId` path was reproduced against the real view flow.

- RED: `IntegrityView.spec.js` ran 18 tests; **1 failed / 17 passed**. After a pending request became definitively rejected and its valid scope was confirmed, clicking “读取这份报告的条件” with an invalid reference retained `600000.SH` and the confirmation because UUID validation returned before the copy lifecycle transition.
- Fix: `loadCopiedScope()` now starts the blocking copy transition before local UUID validation. It cancels prior work, marks the copy attempt active, clears copied/error state, confirmation, draft symbols, and selection, then validates. Local validation failures retain the blocking state and render their message.
- Focused GREEN: `IntegrityView.spec.js` → **1 file / 18 tests passed**, exit 0.
- Complete frontend GREEN: `npm --prefix control-plane test` → **52 files / 740 tests passed**, exit 0, duration 9.63s.
- Complete output: `/tmp/tensor-t12-unit-round2.log`.

Round 2 changed only `IntegrityView.vue` and `IntegrityView.spec.js` in frontend source. No e2e/docs source was edited and no commit was created.
