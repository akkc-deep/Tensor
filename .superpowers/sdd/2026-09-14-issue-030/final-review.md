# ISSUE-030 final independent review

## Scope and review basis

Reviewed `docs/task-designs/ISSUE-030-design.md` in full and the exact 21-file ISSUE-030-only delta in `/private/tmp/issue030-control/final-review.diff` (SHA-256 `f4f9ecdbed797109933129a78ea310ef24fc4a53ae08f49eec9716dd6fc8109d`). The base is the saved pre-ISSUE-030 dirty working tree, not Git HEAD. Earlier uncommitted implementation is not attributed to this issue. Read the scoped Task 1, Task 2 and UI-fix reviews and the updated ISSUE-030 verification report.

Performed the requesting-code-review rubric directly, with no subagents. No tests were rerun, no live SOURCE/TASK/SQL was executed, and no working-tree, index or Git state was mutated. The only output written by this reviewer is this private review report. Outside-diff inspection was limited to the concrete policy admission/planning/assessment boundaries, strict SOURCE-identity consumer, saved baseline/evidence, and frozen build identities.

## Strengths

- `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchPolicies.java:318` uses an explicit 30-entry evidence map and an explicit eleven-API RESPONSE_ONLY set. All 30 new candidates match the design's rule, limit, mode, axis, parameter shape and v2 requirements. The four original policies continue through their existing evidence branch with unchanged rules, note, date and evidence. The independent policy, application and frontend matrices check 40 registrations, 34 RANGE / 6 SINGLE_ONLY, 31 native / 3 daily and 22 ROW_LIMIT / 11 RESPONSE_ONLY / 1 calendar rules.
- `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchPolicies.java:259` maps BJ to SSE only at the top_list calendar-planning boundary. Subsequent requests retain the original BJ stock. Direct trade_cal BSE still rejects locally; margin retains all three exchange_id values. Existing envelope, date, stock, context-budget, strict threshold and full-calendar checks remain intact.
- `control-plane/e2e/issue030-range-candidates.js:237` preserves runs while rebuilding only the 30 candidate interface records; `:286` copies original params/dateAxis into plans with one full SOURCE reference and all current rule references. Independent review of the actual persisted objects confirms that only `interfaces` changed at the JSON root, all 26 runs / 826 cases / 928 historical requests are preserved, and all four old AVAILABLE and six SINGLE_ONLY interface objects are exactly unchanged.
- `control-plane/e2e/tushare-range-evidence.test.js:147` and the subsequent new tests exercise the formal candidate boundary, clean-SINGLE reuse, full identities, wrong run, ambiguous mainbz, old full rows, unreferenced sources, missing RESPONSE_ONLY identity and refusal to promote SOURCE-only evidence to AVAILABLE. The strict consumer was inspected for the concrete risk of same-parameter fallback: supplied full references match both runId and caseId, and response-only inputs require one full identity.
- The updated report distinguishes local candidate AVAILABLE from formal NEEDS_VERIFICATION, frozen build documentation from later closure documentation, and unsuccessful browser attempts from the clean successful run. It records the one-time nature of the candidate reset tool so a successor does not overwrite later TASK references.

## Findings

### Critical

None.

### Important

None.

### Minor

None.

## Independently checked evidence

- Recomputed the complete 893-file isolated snapshot hash: `c05f7a306c73cea3e526120b165672878f3aba2bab4052bee96b1dffd68c10db`. Compared all 422 current build/test source files byte-for-byte against the frozen isolated copy; all match.
- Recomputed both JAR hashes and the manifest/request-examples hashes against `frozen-identity.json`: production `de8130e205f20493a01c566d74390a14483bb1296bcf955df5c2e4f389ff3ac1`, acceptance `518add550be578917ef064ca626982f4293f2d493acb1f919358af0f34c5ce2a`; all match. These are new candidate artifacts, not the ISSUE-029 packages.
- Parsed the actual isolated Surefire/Failsafe XML files: 66 suites, 1133 tests, 0 failures, 0 errors, 0 skipped. `build.log` records BUILD SUCCESS; the supplied build evidence also records 524 frontend tests passing.
- Inspected `candidate-node-final.log`: 104 passed, 0 failed/cancelled/skipped. Inspected the clean `ui-browser.log`: 66 passed in 2.8 minutes. The earlier interrupted/failed attempts are retained and are not counted as GREEN.
- Inspected `metadata-browser.log` and `metadata-summary.json`: 40 metadata cases passed in 3.4 minutes; all 40 API and dataset contracts passed, 39 required-parameter submissions were blocked and one parameterless contract checked. There were zero task POSTs, synchronous-download POSTs, records GETs or upstream calls; 11 screenshots were recorded. JVM, sentinel and private-log cleanup checks passed; the acceptance JAR hash remained `518add550be578917ef064ca626982f4293f2d493acb1f919358af0f34c5ce2a` before/after. The final source audit also remains stable.
- Independently traversed all 272 plan cases through source-bindings to actual clean PASS SOURCE/RANGE records. Every run/case identity, API, original params/dateAxis, current index membership and exact single full SOURCE reference matches; every case includes the interface's rule references. The actual set equals the independently delivered 28/12/38/35/35/33/87/4 inventory, including separate old 12/new 4 mainbz identities.
- Checked private directory 0700 and input/binding/summary files 0600. Confirmed exact historical run equality and unchanged original AVAILABLE/SINGLE_ONLY objects against the saved dirty baseline.

## Final documentation and successor preparation review

Completed a scoped final read of `docs/verification/ISSUE-030-range-candidate-policies.md`, the new ISSUE-030 build-registration section in `docs/verification/ISSUE-018-T14-runs.md`, ISSUE-030/031 problem records, issue/handoff indexes, the authoritative ISSUE-026 board rows and details, and the complete new `docs/task-designs/ISSUE-031-design.md` and `docs/task-handoffs/ISSUE-031-handoff.md`. No implementation was re-reviewed or changed in this follow-up.

- The final ISSUE-030 report and build registration match the actual 1133 backend/package, 524 frontend, 104 evidence, 66 UI and 40 metadata results and the already independently checked hashes. Failed/interrupted attempts remain explicitly unsuccessful. Formal 4/30/6, 26/826/928 history, no new real SOURCE/TASK/SQL, and the distinction between frozen build documents and later closure documents are consistent.
- Current authoritative status and all reviewed status mirrors agree: ISSUE-030 COMPLETED, ISSUE-031 READY, ISSUE-032 NOT_STARTED, parent ISSUE-026 IN_PROGRESS. The recorded sequence is current completion first, successor detailed design/backlink second, successor handoff/link third, then NOT_STARTED to READY. The predecessor handoff and initialization evidence are explicitly historical. Nothing claims that ISSUE-031 has executed or the parent has closed.
- The successor plan preserves shared design section 5's exact ten rounds, counts `28/16/38/35/37/33/24/42/21/4` (278 TASK), 276 unique full SOURCE identities, two specified disclosure repetitions, and four specified regression sources. Independently checked all six extension SOURCE identities and their exact params/dateAxis against the actual formal index. Mainbz old 12/new 4 identities, promised TASK names, stock grouping, whole/annual/wide before endpoint checks, and immediate disclosure rechecks remain explicit.
- The successor receives exact new source/package/input identities and recovery instructions that do not reset future TASK references. It retains per-round new schemas, source identity binding, initial/run-time shared bindings, clean snapshot and budget gates, strict/response-only SQL and page semantics, mainbz real split evidence, unexpected-empty representativeness handling, failure stop/withdraw/version rules, preserved successful data and historical results, and per-interface proof before formal promotion. Phase-specific index tests must follow actual new TASK evidence while retaining SOURCE-only rejection tests; the plan does not call for resetting the index to satisfy stale assertions.
- The handoff's first action is a concrete offline input/identity check and 278-case expansion from completed design. Runtime environment readiness and later live results are not inferred from READY. ISSUE-032 retains the final six gates and parent closure.

**Material documentation gaps: none.** The reported final whitespace/link checks and source/package/input stability are consistent with these documents; no further implementation or validation rerun is justified by this scoped review.

## Assessment

**Code quality / specification alignment: APPROVED, with zero findings.**

**Ready to complete ISSUE-030: YES. Final specification/code/evidence/documentation assessment: APPROVED.** All four ISSUE-030 acceptance conditions have concrete passing evidence, both final browser gates passed against the frozen candidate source/package, and the final closure/build-registration/successor-preparation documents are consistent. There are no Critical, Important or Minor findings and no remaining ISSUE-030 review gate. ISSUE-031 is correctly prepared as READY and has not been executed.

This assessment is only for ISSUE-030. It is not approval to release, execute the successor's live tasks, close ISSUE-026, or close T13/T14. It does not prove upstream completeness for RESPONSE_ONLY or any of the future 278 TASK/SQL results.
