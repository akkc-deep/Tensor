# ISSUE-028 independent final review

Review scope: `docs/task-designs/ISSUE-028-design.md`, the scoped `/private/tmp/issue028-control/issue028-review.patch` (including the newly tracked text utility), and relevant current dependency/test files. Preexisting staged work was treated as baseline, not as ISSUE-028 changes. Read-only review except this report; no Maven, external sources, task execution, or production changes performed by reviewer.

## Current verdict after bounded incremental review

**APPROVE — specification and implementation quality.** Both initial P2 findings below are resolved. No remaining actionable code or contract findings were found. Final gate logs have now been independently inspected: full `-Pacceptance verify`, the final complete MySQL HTTP controller suite, and the controlled browser suite all pass. ISSUE-028 review and verification are approved.

## Initial findings and resolutions

### 1. Resolved P2 — Cover response-only terminal facts through actual HTTP

Location: `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadTaskControllerIT.java:179`.

The new response-only integration scenario creates `Flow(..., true)` and never dispatches its manual poller. It therefore verifies the new rule only while the task remains QUEUED. Existing actual HTTP success/failure/interruption scenarios use strict rules or SINGLE; the new browser terminal scenarios are controlled HTTP fixtures. This leaves the design's explicit requirement that response-only nonempty success, empty success, failure and interruption each have HTTP observations unmet.

Add bounded controlled HTTP coverage for those four states, checking status, exact saved extraction, counts and list/detail agreement. Existing `Flow.json` already validates the published schema, so reuse it. Preserve production source/evidence isolation; controlled stored state setup is sufficient for the response mapping layer if lifecycle execution is already established in core tests. This is an acceptance coverage finding, not a claim of an observed production failure.

### 2. Resolved P2 — Remove contradictory production admission claims from published contracts

Locations: `docs/contracts/download-task.schema.json:1114`, `docs/contracts/download-task-examples.json:24`, and `docs/contracts/download-task-examples.json:1191`.

The updated OpenAPI correctly documents four AVAILABLE, 30 NEEDS_VERIFICATION and six SINGLE-only APIs. The schema description still states 34 NEEDS_VERIFICATION and the range submission example says all 34 cannot accept RANGE. The needs-verification example also generalizes its unavailable state to production until T13. These statements contradict the preserved production baseline and the newly published OpenAPI. Update the prose to the current counts and describe the unavailable example as one controlled scenario, without changing fixtures or admission.

## Incremental resolution review

- Reviewed `preservesResponseOnlyExtractionAcrossExecutedOutcomes` and its four MethodSource arguments in `DownloadTaskControllerIT`. Each creates a controlled native, unsplittable RESPONSE_ONLY task and actually executes through the source, coordinator, runner and MySQL-backed repository. Nonempty and empty responses reach SUCCEEDED with the expected source-row counts; source failure reaches FAILED; synchronized coordinator shutdown during a blocked request reaches INTERRUPTED. The tests compare exact saved extraction, status and all returned counts between HTTP list/detail; `Flow.json` validates those responses against the formal schema. The source fixture now returns `BatchAssessment.RESPONSE_ONLY` for the new rule while retaining the existing assessment logic for strict rules.
- Inspected `/private/tmp/issue028-control/response-only-outcomes-green.log`: 4 tests run, zero failures/errors/skips, BUILD SUCCESS (2026-09-14 02:47:40 +08:00). This resolves the missing HTTP result-state coverage without production SOURCE/TASK calls.
- Re-read schema CapabilitiesResponse description and both changed example descriptions. All now preserve four AVAILABLE / 30 NEEDS_VERIFICATION / six SINGLE-only; the NEEDS_VERIFICATION example is explicitly one such production shape and its daily endpoint remains one of the unverified APIs in unchanged production policies. This resolves the contradictory admission claims.
- The incremental changes are bounded test-fixture and documentation updates. No production implementation, candidate-policy or evidence-index change was introduced to address review findings.

## Confirmed implementation behavior

- Controller obtains one detail snapshot, passes that same task to controls and policySummary, and maps its counts and saved summary into the response. Lists continue to reuse detail conversion for each selected row.
- SINGLE serializes explicit extraction null; RANGE exposes precisely policyVersion/ruleKind. No policy snapshot, definition hash or permit is added to HTTP. Receipt and batch DTOs are unchanged.
- The existing policySummary dependency reads only saved policy JSON. Existing unit tests cover removed plugins/adapters, old policy versions, strict/UNKNOWN rules and corruption; added HTTP tests verify changed-current-capability stability and safe QUERY_FAILED mapping for damaged snapshots.
- The published JSON schema enforces required extraction, SINGLE-null/RANGE-object distinction, exact summary keys, nonblank policyVersion and closed rule enum. RESPONSE_ONLY requires evidence, null rowLimit, native planning and splittable=false under AVAILABLE and NEEDS_VERIFICATION; AVAILABLE UNKNOWN remains rejected.
- OpenAPI references the formal schema. Contract tests validate actual schema/examples; frontend tests additionally run published task/page/capability examples through strict parsing. Existing fixture cases were preserved and populated with saved summaries.
- Frontend extraction parsing uses exactObject/nonBlank and freezes a copied summary. Capability parsing preserves strict field checks and applies the same new response-only cross-field rules.
- Form notice appears adjacent to submission for RESPONSE_ONLY RANGE. List and detail notices depend only on saved extraction and persist in every status. The small shared pure helper selects success labels only for SUCCEEDED and compares sourceRows against 0n; failures, partial failures and interruptions retain their statuses despite nonzero rows. UNKNOWN keeps uncertainty and SINGLE/strict retain their previous descriptions.
- Snapshot refresh tests check explanation updates. Controlled browser cases cover changed current capabilities, terminal reload, both successful result kinds, all statuses, historical rules, SINGLE, desktop/mobile overflow, and no added dialog/checkbox. Existing retry/recovery scenarios remain in the suite; recovery fixtures now use full parsed tasks.
- Production policies and evidence index were byte-compared with pre-task snapshots: both unchanged. No production candidate or evidence upgrade is in the scoped diff. New utility is tracked in Git.

## Verification evidence inspected

- `/private/tmp/issue028-control/frontend-final.log`: 34 files, 524 tests passed.
- `/private/tmp/issue028-control/e2e-final.log`: extended controlled browser suite, 66 passed.
- `/private/tmp/issue028-control/contract-unit-green-escalated.log`: contract/controller unit tests, 11 run, zero failures/errors/skips, BUILD SUCCESS.
- `/private/tmp/issue028-control/controller-it-green.log`: BUILD SUCCESS for the initial controller integration test set.
- `/private/tmp/issue028-control/acceptance-verify.log`: final full `mvn -o -f data-plane/pom.xml -Pacceptance verify` BUILD SUCCESS at 2026-09-14 02:50:24 +08:00. Independently summed the 66 per-suite log results: 1,125 backend tests, zero failures/errors/skips, including four regular packaged-JAR and three acceptance packaged-JAR checks. The sum agrees with `/private/tmp/issue028-control/acceptance-summary.json`. The same log records 34 frontend files / 524 tests passed and a successful Vite production build.
- `/private/tmp/issue028-control/controller-it-final.log`: final complete MySQL-backed DownloadTaskControllerIT suite, 19 tests, zero failures/errors/skips, BUILD SUCCESS at 2026-09-14 02:51:50 +08:00. This separately executed integration suite includes all four new actual RESPONSE_ONLY outcomes.
- Final read of `/private/tmp/issue028-control/e2e-final.log` confirms 66/66 controlled browser tests passed.

The reviewer inspected final existing logs and independently checked the acceptance totals without rerunning tests. No gate remains pending in this review. Root owns closure records and the ISSUE-029 handoff; this approval applies to ISSUE-028 and does not replace later mother-issue production acceptance. No unrelated staged baseline code was included in the review verdict.
