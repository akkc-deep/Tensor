# T13 final independent review

## Scope and verdict

- Worktree: `/Users/qiangzhiwei/code/github/Tensor/.worktrees/data-integrity`.
- Baseline: inherited staged tree `1728e704532dca83032c35aaf4cc576307e37ca5`.
- Reviewed tree: `16949da7bacb7701082f2f562fc015416d66dd44`. HEAD was not used as the T13 baseline.
- Read `final-review-brief.md`, the complete T13 code/document diff package in bounded passes, the T13 design and verification document, Task 1/2 reports, progress rulings, and the bounded fix reviews. Follow-up reads were limited to named cross-file risks and their evidence.
- **Spec verdict: APPROVED for the implemented isolated-worktree scope. Full T13 acceptance remains BLOCKED on design section 6.**
- **Quality verdict: APPROVED. No new Critical, Important, or Minor findings.**

This review does not authorize a commit of the mixed baseline, a merge, or a COMPLETED task state. No implementation, Git/index/HEAD, or board state was changed. No Maven, frontend, browser, MySQL, signal, or other already-passing test suite was rerun. The only review output written is this file.

## Strengths

1. **Acceptance scope and version semantics are contained.** `FixtureIntegrityRules.java:10` defines separate original Jan 1–20 and extended Jan 1–21 windows; `:40` selects reliability by the declared synthetic symbol and requires the whole requested range to fit. Expected nonempty keys still come only from Jan 1–20 intersected with the request; PROVEN_EMPTY supplies no keys. Unsupported versions fail at construction. Coverage descriptor/rule/evidence versions move together, while `FixtureIntegrityExtensionRule.java:16` registers the independent FIELD extension at version 1 with explicit synthetic evidence. Configuration remains acceptance/profile/enabled gated. The tree-to-tree check independently returned no changes under core, HTTP/app production, plugin-api, Tushare production, or `control-plane/src`.

2. **The new HTTP test proves actual behavior across a database-preserving restart.** `IntegrityFixtureFlowIT.java:62` starts v2 with an empty upstream token; its real loopback receiver is asserted at zero after that phase. The v3 start supplies a synthetic token and reuses the same receiver and MySQL container. SQL constructs independent full `(ts_code, trade_date)` expected/missing/extra sets. Public counts and rates pass an `isTextual()` check before exact string comparison; null cases are explicit. Old detail/results/issues and persisted report JSON are compared after restart, old submissions replay, new submissions with a stale hash fail, and refreshed capabilities produce coverage@3 plus extension@1. No absence-of-download-row proxy is used for the network assertion.

3. **The real browser test verifies the intended user flow and independent SQL.** The Jan 20 MISSING and Jan 21 EXTRA assertions are bound to individual issue rows and complete keys (`integrity-fixture.spec.js:143`). Confirmation starts unchecked, one POST is required per submission, the Jan 21→20 SQL update occurs between checks, and the new report becomes 100% while the old report stays 95%. PROVEN has a separate independent SQL enumeration of all 20 missing keys. Same-database v2→v3 evidence remains readable through the generic report UI. Requests are restricted to the loopback application by the monitor; the owned upstream counter is separately checked.

4. **Securities and resource boundaries are meaningful.** Fixture snapshots include all seven business/source/ingestion columns and compare ordered rows as well as SHA-256. The referenced production HTTP IT independently compares every column of the three market and three reference tables before/after its checks (`ProductionApplicationContextIT.java:148`, `:175`, `:359`). The launcher creates five random isolated schemas in its own MySQL 8.4.6 container, grants only schema-scoped privileges, stores credentials privately, and cleans its owned resources. Signal handling terminates and waits for the owned suite process group before database/credential teardown. The MySQL trigger-trust setting applies only to that owned server; no global application privilege was added. Both JVM shutdown records are collected after cleanup.

5. **Legacy and documentation updates preserve the intended boundaries.** Existing browser changes update V9 inventory and observed Studio locators, scope duplicate visible count text, close the current modal before navigation, and retain business/SQL/HTTP/error/timeout assertions. Documentation separates local checks from unimplemented online reconciliation, synthetic fixture evidence from production UNKNOWN, stub UI evidence from real HTTP/SQL, and default Maven verify from explicitly selected MySQL IT.

## Evidence cross-checks

These are read-only consistency checks against saved artifacts and source, not a fresh execution claim:

- Recomputed suite totals from the per-suite summaries: integrity **441/0/0/0**, including **9 IT classes / 125 IT tests**; acceptance **1469/0/0/0** with zero selected IT classes; production **1466/0/0/0** with zero selected IT classes. The final amended fixture IT is separately recorded as **1/0/0/0**, with its exact method and empty-token/synthetic-token phases.
- The five-suite launcher summary records MySQL 8.4.6, the documented acceptance JAR SHA, five exit codes of zero, and successful container/credential cleanup. The detailed report records the 70-test breakdown; the launcher JSON itself contains suite exit codes, not per-test counts. The separate stub summary records 39 tests and explicitly identifies API stubbing; UI 740 is recorded separately.
- Recomputed all six saved fixture snapshot hashes from their actual UTF-8 row strings. All six have 20 seven-field rows, before equals after, and the recomputed SHA matches. Independent SQL evidence contains 20/20/19/1/1 at `0.950000`, the corrected 20/20/20/0/0 at `1.000000`, and the exact 20 ordered PROVEN missing keys at `0.000000`.
- All three real screenshot SHA values match their current files. This review did not repeat the controller's visual inspection.
- All **83 current production XML hashes** match `production-tests.json`; the current production JAR matches the recorded SHA. Its archive has eight production migrations and no fixture module/dataset or acceptance configuration. A generic substring scan also finds the legitimate production logger class `DownloadTaskOperationLogger$AcceptanceKind`; that class is unrelated to the acceptance profile and is not a packaging violation.
- The added implementation, documentation, and evidence files are present in the reviewed Git tree. The scoped working-tree comparison was clean when performed. The subsequently added `contract-gate.json` link/evidence was read separately and is consistent with the existing blocked gate; later controller pause/handoff changes are outside this frozen source review.

## Issues

### Critical

None.

### Important

None.

### Minor

None.

## Remaining acceptance condition and handoff

**Ready to merge / mark T13 COMPLETED? No.** This is the already-declared integration prerequisite, not a newly discovered code defect.

`docs/task-designs/DATA-INTEGRITY-T13-design.md:116` requires a code-consistent clean committed main and an actual successful `verify-contracts.sh` run; `:118` forbids COMPLETED without it. The script still checks main and protected clean inputs (`scripts/verify-contracts.sh:51`, `:64`) and archives the selected HEAD (`:635`). Its T13 changes only synchronize the V9 method name and inventory evidence.

`docs/verification/DATA-INTEGRITY-T13.md:7` accurately says IN_PROGRESS and names the missing integration prerequisite; `:23` records the actual exit 1 / `M14-T04 failed: branch`. `contract-preflight.json` and the separately read `contract-gate.json` distinguish helper success from the failed final gate. The board's T13 row remains IN_PROGRESS (`docs/task-handoffs/data-integrity-task-board.md:38`), and T12 remains COMPLETED. The old entry handoff is not represented as a finished-task handoff.

The controller should now record this review, write the planned pause/blocker handoff, and name the exact resolution: explicitly authorized integration of all required inputs into a genuine clean committed main, record the integrated SHA and its consistency with the T13 tested code, then run the unchanged final contract gate successfully. Do not rename the feature branch to impersonate main, bypass the guard, or commit the inherited mixed baseline to force a pass. Completion remains conditional on that result.
