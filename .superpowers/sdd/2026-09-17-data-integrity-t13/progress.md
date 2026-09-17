# SDD ledger — plan: docs/superpowers/plans/2026-09-17-data-integrity-t13.md

Base HEAD 211094822aa061c2f9217d367cc06f8ddcae3b4e; inherited staged baseline preserved. No commits authorized for mixed baseline.

| Scope | Shared interface / consistency | Finding |
| --- | --- | --- |
| Task 1 | Fixture contracts / Java IT expected values | Matches spec sections 1–3 |
| Task 2 | UI/launcher isolation and cleanup | Matches spec section 4 |
| Task 3 | Verification results / docs / board | Requires actual clean-main gate; no fabricated completion |
| Task 1 ↔ 2 | Version2/3 and synthetic symbols | Same fixed values from spec |
| Task 1 ↔ 3 | Surefire current results | Controller runs full final suite after implementation |
| Task 2 ↔ 3 | Playwright evidence and JAR SHA | Preserve exact run provenance |

Task 1: in progress; backend implementer assigned, controller reads contracts and validates environment.

Baseline: Java52 passed; UI first concurrent Maven npm-ci run failed missing dependencies, serial retry740/52 passed; Vite build passed. Never run Maven npm-ci concurrently with UI tests.

Contract audit: verify-contracts.sh still named removed Flyway method productionMigrationInventoryCreates51TablesWithoutFixture and persisted pre-V9 schema totals. Minimal V9 method/evidence synchronization added; main/clean committed input/archive gates unchanged. Internal helper probes will be exercised, distinct from the unexecuted final gate.

Initial staged baseline tree (before any T13 staging): `1728e704532dca83032c35aaf4cc576307e37ca5`. Review diffs use this tree, not HEAD, to exclude inherited Studio/T01–T12 changes. Final staging is controller-owned.
Stub screenshot inspection: report390/report1440, UNKNOWN390, N/A1440, interrupted1024/interrupted390 opened; correct separate execution/data states, internal table scroll, no page overflow.

Read-only coverage audit complete: coverage-audit.md maps all10 outcomes/15 boundaries; no new preexisting core/UI/Tushare test gap, only planned Task1/2 real integration remains. Task2 agent t13_browser is read-only preparing selectors/resources; implementation not yet started while Task1 active.

Task1 review: 0critical/2important/2minor; fix round1 assigned to t13_backend. Actual source-call sentinel and independently enumerated PROVEN full keys required; additionally strengthen textual counts and saved version3 coverage result/evidence. Current full controller run:441/28 reports all pass0skip, actualFlyway47 validated; final amendedIT must rerun.

Task1 fix1 review: original four addressed; new Important evidence gap because synthetic token displaced no-token scenario. Ruling: v2 uses empty upstream token, v3 uses synthetic token; both keep owned receiver and assert zero calls. This proves both no-token capability and no-call behavior with an available download client. Backend assigned focused fix2/IT only.

Full acceptance clean verify passed:1469 tests/84 reports,0failure/error/skip,56.090s; frontend740/52 also passed. Configured verify patterns do not select real *IT; prior441 suite plus focused amended IT provide real MySQL evidence separately. JAR SHA256 recorded acceptance-build.json.

Task 1: complete — fixture behavior/real HTTP SQL implemented,117 initial combined GREEN,441 controller suite,final focusedIT1pass0fail/error/skip17.432s. Review fix1 closed original4,fix2 closed no-token regression; final spec/quality APPROVED,no open findings. Task2 browser implementation dispatched to t13_browser with actual built JAR,controller keeps it until browser finishes. No commits.

Task2 real run found inherited Studio selector drift: four legacy suites still expected download H1 数据下载, actual packaged current UI H1 下载工作台. Controller independently verified all19 JAR static files byte-identical to current dist and read actual failure DOM; no product/package defect. Ruling: minimally synchronize stale display/navigation selectors in the four required legacy suites (add tushare-metadata.spec.js and fixture-flow.spec.js to permitted test edits), preserve all business/SQL/HTTP/network assertions and production sources. Cost if wrong: test drift could hide UI regression, so task/final reviews inspect exact deltas and actual suite results. Separately prepend discovered JAVA_HOME/bin to launcher PATH to preserve minimal-env Java probes.

Task2 full runtime iteration: dataset-query11/11 passed in415.682s. Remaining harness/legacy failures: SQL result scale4 vs required6; helper safety-log rejection skipped temp cleanup; native selected-option visibility in outcomes/fixture-flow; global option locator counted native options in metadata. Implementer fixing exact sources, then runs all5 suites. No increased timeout or production UI edits authorized/needed. Static task review may overlap final runtime after code freezes; completion remains gated by both.

Task2 review:2Important (owned process-group signal cleanup; row-bound browser missing/extra full-key assertions),1Minor(v3 shutdown evidence copied before cleanup). Fix round1 queued to browser implementer after current real run; preserve runtime source freeze. New T13 browser1/1 passed current run and three widths were visually inspected, SQL20/19/1/1→20/20/0/0 exact. Current legacyoutcomes new strict locator duplicate count text will be scoped to task record, then targeted old suites before final unwrapped5-suite run.

Controller evidence consistency check: all41 Java method references in ten-outcome/fifteen-boundary tables resolve to this run’s Surefire summary after stripping JUnit parameter signatures/indices; coverage-method-check.json saved. Third-run real screenshots1440/1024/390 show95% and correct Jan20 missing/Jan21 extra, internal table scrolling and keyboard focus. Final unwrapped run will refresh these artifacts after review fixes.

Task2 third full run ended: real integrity1/1 pass26.112s,dataset11/11 pass415.554s,metadata40/40 pass141.048s. Download-outcomes stops atcase3 after9.665s andfixture-flow stops atcase2 after14.060s due exact same count string in task summary and expanded batch record. Correct by scoping record assertion, not dropping count assertions. Implementer enteredfix1; PROVEN browser independent SQL design clause under targeted reviewer clarification.

Task2 fix1 scope amended after exact design§3 check: thirdImportant requires browser independent PROVEN SQL20expected/0actual/0matched/20missing/0extra/rate0.000000 and orderedJan1..20 full keys, independent of app report. Reviewer confirmed and implementer acknowledged. Current fix1 total3Important+1Minor plus real-run legacy duplicate-text locators; no new product scope.

Task2 fix1 probes: SIGTERM and SIGINT fake-npm real process-tree tests each verified3 owned PIDs gone and cleanup sentinels deleted. Focusedfixture-flow3/3 passed. Download-outcomes actual failure atcase7 exposed MySQL binary-log trust for TRIGGER-only user. Ruling: configure only owned mysql:8.4.6 container with --log-bin-trust-function-creators=ON and verify server setting; retain exact schema-restricted grants, noSUPER/DROP/DELETE/global grant. This is required for existing trigger fault injection, not a business assertion change.

Task2 fix1 ready for scoped review: focusedfixture-flow3/3 passed; focuseddownload-outcomes15/15 passed159.452s including120-second fault timeout andtriggerrollback/upstream-error cases. Signal probesSIGTERM/SIGINT passed; staticchecks passed. Final unwrapped5-suite launcher starting with source frozen. Fix diff recorded againstfd672d4001f653c744ba95bfb8f4fe0a5ab54bbf; noMaven/npm parallelism.

Task2 fix1 source APPROVED,all3Important+1Minor addressed,no new findings. Controller staged code treeefc412d043d37d8ded12c914abcbd61313674d9f. Final unwrappedrun:integrity1/1 anddownload15/15 passed;dataset large seed/query ongoing. Current finalbrowser evidence includesPROVEN20 ordered keys,v2/v3 lifecycle,upstream0,allcleanuptrue. No code edits during this run.

Final unwrapped run progress:integrity1/1 passed26.176s,download15/15 passed161.394s,dataset11/11 passed415.625s,all0fail/0skip. metadata15/40 ongoing;fixture next. Productioncleanverify waits for allownedbrowser/JVMgroups andMySQL cleanup.

Task 2: complete — final unwrapped five-suite launcher70passed/0failed/0skipped (1+15+11+40+3); all owned JVM/browser groups, MySQL container and temporary credentials cleaned. Final1440/1024/390 screenshots visually reviewed; accurate95%, Jan20MISSING/Jan21EXTRA, internal table scroll and keyboard focus. Source scoped review APPROVED.

Production clean verify passed after browser cleanup:1466 Java tests/83reports,0failure/error/skip,54.109s; UI740/52 andbuildpassed. Production archive has8migrations and no fixture module/dataset/V6/acceptance config; SHA and nested archive scan recorded in production-build.json. This build intentionally removes the prior acceptance target; provenance was saved first.

Final review dispatched on gpt-6-astra to t13_final_review; T13 delta base1728e704532dca83032c35aaf4cc576307e37ca5, staged review tree16949da7bacb7701082f2f562fc015416d66dd44. Post-test evidence checks confirmed6unchanged securities snapshots,3screenshot hashes,allJSON parse,zero owned containers/tempdirs/8080 listeners and1183 unrelated baseline blobs preserved. Actual final contract invocation again exits1 `M14-T04 failed: branch`; contract-gate.json records the unresolved design§6 integration precondition. No commit/merge/push.

Final whole-T13 review: APPROVED for isolated implementation and quality,0Critical/0Important/0Minor; report final-review.md. No fix wave needed. Reviewer independently validated tests/artifact/snapshot hashes. Product/test sources remain frozen since reviewedtree16949da7bacb7701082f2f562fc015416d66dd44; only evidence links, summaries and handoff/status updated after review.

Task 3: isolated verification/docs/review complete; BLOCKED solely on design§6 genuine clean committed main and actual final contract gate. Complete T13 design re-read and boardIN_PROGRESS re-observed; wrote exact Pause Handoff first, then transitioned T13IN_PROGRESS→BLOCKED. T01–T12 remainCOMPLETED. No commits/merge/push. Final task acceptance is intentionally not complete. Retain durable ledger/reviews in Git because no history commit/merge exists; discard only generated review-diff scratch.

## Remote-main synchronization follow-up

User requested synchronization of remote main into the integrity worktree and continuation of T13. Preserved original staged work in stash `ee0c3d3b52f576e5bb52459caff1fc3ae6ca1707`; synchronized `origin/main@5aaf6ad`, resolved 25 conflicts, retained remote Studio and local Integrity behavior. Independent merge and scoped test-fix reviews approved. Reviewed implementation committed as `a7deb7dd2bcac252171d1fd9d4c62fff2e07039e`; 607 source/config/contract hashes match the commit.

Fresh verification: frontend 738/50 files; stub39; Java Integrity441 including125 real IT; production1466; acceptance1469; final real browser70 (1+15+11+40+3), all zero failures/errors/skips. Browser launcher ran 2026-09-17T14:44:53.680712Z to14:57:43.616850Z using acceptance SHA256 `a5ff0e16d960b164702fd08890da9696b41b7c9b8514637f7722683a62a03ddf`. Owned resources cleaned, historical evidence restored, new evidence archived under `docs/verification/data-integrity-t13/main-sync/`. Documentation updates do not alter tested sources.

Automatic approval review rejected the attempted local fast-forward of the 311-file commit into default main, requiring explicit user authorization for this concrete history change. Authorization question remains pending. Actual main is clean at `5aaf6ad`; no push or bypass. Refreshed pause handoff and board evidence without changing BLOCKED status. Only remaining scope: authorized real main integration, source identity confirmation, successful unchanged official contract gate, then recorded BLOCKED→READY→IN_PROGRESS→COMPLETED transitions.

## 2026-09-18 Authorized main integration and completion

User explicitly authorized “授权本地合并并继续完成 T13”, limited to local integration without remote push. Committed pending evidence/screenshots as `1e3b039`, fast-forwarded actual main, retained the isolated worktree and recovery stash. Initial official main gate failed only on outdated metadata count41; upstream5aaf6ad already has the additional mainBusinessSingleIsOnlyAStockSnapshot test. Minimal exact-inventory/self-probe/summary synchronization to42 was independently APPROVED (0Critical/Important/Minor) and committed as `6c3c13f`.

The unchanged main/clean-input/archive-HEAD requirements then passed in the full official gate at `6c3c13f908ca37acddfcf46fe6a76246a4283630`: metadata42/schema47/package4, all0fail/error/skip; frontend738/50 files and build passed;11 rejection probes passed; Maven46.605s. Both owned containers were removed and preexisting containers preserved.607 archived input hashes match integrated main;606 equal the prior tested manifest, with only the gate inventory script differing. Official JSON, run summary, first failure and authorization history are archived under main-sync.

After supplying resolution evidence, recorded BLOCKED→READY, explicit resume READY→IN_PROGRESS, then outcome acceptance IN_PROGRESS→COMPLETED. T13 is the final predefined task; no successor handoff. Final changes only document completion and evidence. No remote push; retain worktree/stash. Production Tushare UNKNOWN limitations remain within the original scope; proposed rule enhancements were not implemented here.
