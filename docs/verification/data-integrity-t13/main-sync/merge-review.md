# T13 main-sync independent review

## Verdict and scope

**APPROVED for the reviewed merge resolution. Critical: 0; Important: 0; Minor: 0 remaining.**

Reviewed the integration of remote main `5aaf6ad` with the inherited stash `ee0c3d3b52f576e5bb52459caff1fc3ae6ca1707` (original HEAD `2110948`) in `.worktrees/data-integrity`. This is a bounded merge-correctness review, not a new T01–T13 implementation audit. Read the T13 task design and prior final review; compared both sides of the merge with the current files. The initial reviewed staged tree was `8fd3b7dc3f4ede645ef152c298cb8990749d7d6b`; review also includes the subsequent mobile-navigation CSS restoration and duplicate-comment cleanup.

No source, index, HEAD, or repository evidence was changed by this reviewer. No test suite was run. The controller is independently running the required regression checks; this report does not claim those results.

## Preservation evidence

NUL-delimited Git filename comparisons against original HEAD, remote main, stash, and the current worktree established:

- Remote changed 293 files. All 147 remote-only changed files match remote main exactly.
- Of 146 overlapping remote/local changed files, 133 match remote main exactly. The remaining 13 are the four legacy E2E suites, AppLayout and its test, router, global CSS, FixtureConfiguration and FixturePluginTest, OpenAPI, Integrity shared design, and Integrity task board.
- All 263 local-only stash files match the stash exactly. No inherited Integrity implementation or evidence was lost.
- Existing Studio download/data-table components, display controls, download backend/controllers, and all Tushare dataset metadata match remote main. Remote deletions remain deleted. The existing named task-modal route, download channel, KeepAlive behavior, and workspace structure are retained.
- `git diff --check`, `git diff --cached --check`, and `git ls-files -u` produced no findings.

## Focused review

1. **Fixture auto-merge is correct.** `FixtureConfiguration.java:41` retains acceptance-only version injection/default 2 and the compatible no-argument factory. Lines 75–78 retain upstream Chinese column labels. `FixturePluginTest.java:154` retains default/version-3/invalid-version tests, while expected metadata/YAML labels match the remote definitions. The merge does not change field identifiers, types, precision, nullability, or business keys.

2. **Navigation integrates without dropping Studio behavior.** `AppLayout.vue` adds Integrity navigation, detail-route active state and breadcrumb label, while retaining the remote named task outlet, channel provider, and download/dataset cache. Router changes are additive Integrity routes. `style.css:468` adds only a <=680px navigation/workspace adaptation, in rem units compatible with the remote display-scale system. This restores the local mobile handling required after adding the fourth navigation entry; desktop Studio CSS remains intact. Actual viewport evidence remains the controller's verification responsibility.

3. **Legacy E2E merging preserves both sets of contracts.** Relative to the stash, dataset-query retains upstream metadata-driven Chinese+field headers and Chinese pagination; fixture-flow retains upstream localized fixture headers. Relative to remote main, the existing T13 V9 inventory (9 migrations/55 business tables), current Studio headings/navigation, scoped duplicate count assertions, task-dialog close-before-navigation, and native select handling remain. Download outcomes and Tushare metadata are otherwise exactly the inherited T13 versions. SQL row counts, HTTP/network checks, and download/error assertions were not removed by this merge.

4. **OpenAPI and task state are preserved.** The three overlapping Integrity contract/design/board files match the inherited stash exactly. Relative to remote main, OpenAPI removes no existing endpoint or schema contract; changes add Integrity paths/schemas/error codes plus introductory documentation. Existing task-list title filtering remains preserved with remote backend/frontend files. The inherited documented contract-gate blocker is not silently marked completed.

## Findings

Critical: none.

Important: none.

Minor: none remaining. The duplicate jsdom dialog comment introduced during integration was reported and the controller removed it; current `control-plane/src/test/setup.js` matches remote main exactly.

## Readiness limits

No additional code defect or lost-change blocker was found in the reviewed merge. The resolved source is technically ready for the controller to finish its regression and Git workflow.

This is not full T13 completion. Per T13 design section 6, acceptance still requires all required inputs on a genuine clean committed main, the unchanged official `scripts/verify-contracts.sh` gate to succeed, and recorded commit/code consistency. A feature worktree, branch rename, dirty main, or earlier archived test evidence cannot substitute for that gate. Test outcomes after the main sync must be recorded as new evidence and distinguished from the older saved acceptance run.

## Scoped addendum: fixture headers in download outcomes

**APPROVED. Critical: 0; Important: 0; Minor: 0.**

The controller's first real rerun exposed an additional compatibility gap not identified in the initial static review: `download-outcomes.spec.js` still used raw field identifiers for fixture table display headers after remote main introduced Chinese labels. This addendum reviews only the subsequent four-line assertion adjustment at `control-plane/e2e/download-outcomes.spec.js:1075`.

The new branch is limited to `pluginId === 'fixture'` and `apiName === 'fixture_daily'`. It expects the exact seven independent literal Chinese-label-plus-field headers already asserted by `fixture-flow.spec.js`; the daily-specific branch and fallback are unchanged. This tests the actual established display contract and does not derive expected strings from the rendered DOM or relax matching.

Raw response columns remain checked against `expectedColumns` at lines 1062–1068; one-item count and exact body row equality remain at lines 1069–1070. The specific DOM row count remains at line 1083, and `assertFixtureBody` still checks all seven cell values, exact decimal precision, null rendering, source fields, and timestamp at lines 1112–1119. The upsert test still checks first/second row content and a strictly later ingestion time; failure scenarios still call `assertFixtureUnchanged`. The diff contains no changes to SQL, migration inventory, database row assertions, HTTP, or request/network checks.

`git diff --check -- control-plane/e2e/download-outcomes.spec.js` is clean. No tests were run by this reviewer. The controller's planned complete five-suite rerun is still required; this static approval does not claim the rerun passed. The full clean-committed-main contract gate remains required for T13 completion.

## Scoped addendum: wait for terminal task and control availability

**APPROVED. Critical: 0; Important: 0; Minor: 0.**

Reviewed only the two-line polling change in `control-plane/e2e/download-outcomes.spec.js:846` and the concrete service/coordinator contract. The inspected rerun log records 14 download tests passed and `showsSourcePayloadFailure` failed because the first terminal response had `canRetry: false` rather than the expected `true`; the failure was not a mismatch in the expected terminal status, error, or counts.

`DownloadTaskService.controls` (lines 226–234) computes availability from the coordinator in addition to the stored task state. `DownloadTaskCoordinator.controlAllowed` (lines 111–114) requires RUNNING with no active lease. Its `run` finally block releases that lease after `runner.runNext` returns (lines 144–163), so a committed terminal task can correctly precede retry availability. Idle polling also acquires a lease. The documented GET contract calls these fields current hints, not an atomic guarantee implied by terminal status.

The helper has one caller, which requests only SUCCEEDED or FAILED for these SINGLE-task scenarios. Service `retryable` accepts FAILED/PARTIAL_FAILED; therefore expecting `canRetry: status === 'FAILED'` and `canResume: false` is correct within this helper's actual usage. Polling the three fields together retains the exact task DTO that satisfied those existing expectations. A permanent unavailable control, wrong terminal status, missing field, or wrong resume flag still fails within the original timeout.

The final task assertion still requires the original status, exact counts/error, `canRetry: Boolean(error)`, `canResume: false`, requestCount 1, and runRequestCount 1. Exact batch state/attemptCount remains unchanged. The change adds only read-only GET polling until the existing contract assertions can hold; it adds no POST, retry action, explicit sleep, timeout extension, production change, or weaker assertion.

`git diff --check -- control-plane/e2e/download-outcomes.spec.js` is clean. No tests were run by this reviewer. Approval covers this bounded synchronization correction; the complete five-suite rerun and final genuine clean-committed-main contract gate remain required.
