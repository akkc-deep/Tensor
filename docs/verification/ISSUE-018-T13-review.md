# ISSUE-018-T13 whole-work independent review

Review date: 2026-09-13. **Spec verdict: FAIL — two Important findings. Quality verdict: FAIL — the task-query failure path loses classified evidence. Outcome acceptance: NOT MET; T13 must remain unfinished.** The proposed BLOCKED handoff is substantively justified, after the two local repairs and their limited independent re-review.

## Scope and method

Read `.superpowers/sdd/2026-09-12-issue-018-t13/final-review-brief.md` first and the binding `docs/task-designs/ISSUE-018-T13-design.md` in full. Used the requesting-code-review template. Reviewed the complete owned work in passes: evidence schema/validator and tests, Java SOURCE orchestration/projection and tests, task/browser/SQL/log integration, official/candidate assessments, canonical results, ISSUE-017 supplement, issue/task status changes and `final-handoff-draft.md`.

The review baseline is `5dd840d71509398afde4f14d2b2b501a0c882e40`; the supplied current HEAD is `35299da39f67f64a75d787c8c1f3a7d4de3e499e`. The implementation is in the supplied `final-review.diff` and current owned files, not a committed base-to-HEAD implementation range. Scope follows `owned-files.json`; concurrent demo/vite and .gitignore changes are excluded. The existing board is correctly still IN_PROGRESS during this gate. The proposed handoff, rather than the historical next-task text still at its destination, is the pause artifact reviewed here.

Read the supplied implementation and limited review reports, including Task 1/2/3 repairs and the Task 4 index, bootstrap, SQL encoding and future projection reports. Performed read-only JSON aggregation and inspected existing Surefire suite attributes. No tests, discovery, browser, application, SOURCE, SQL, account, network, credential lookup, Git mutation or subagents were run. Only this report was written. Root separately owns link/hash/secret/diff checks, all fixes, staging and the eventual state transition.

## Strengths

- The independent matrix preserves all 40 interfaces, 34 RANGE targets, 31 native / 2 natural-day / 1 trading-day planners and 6 SINGLE-only interfaces. SINGLE selection remains 74 samples with separate 000001.SZ and 600000.SH tasks; stock_company changes the second exchange consistently. Production registration, policies, business keys and application code were not changed by this work.
- `tushare-range-evidence.js:257–389` checks batch structure, split coverage, root continuity, leaf totals and SOURCE-versus-SQL null boundaries. Complete native and natural-day ranges cannot be replaced by a successful partial tree. Zero-leaf top_list evidence requires the closed-calendar form and a planning request; trading-day truth remains dependent on the cited calendar rather than guessed weekdays.
- Candidate selection at `tushare-range-evidence.js:515–552` binds a specific referenced SOURCE case from a clean run, exact API/mode/parameters/date axis and completeness citations. Runtime capability checks at `tushare-live.spec.js:1048–1050` then compare availability, version, planning mode and rule before submission. There is no production verification bypass or attempt to use the present failed SOURCE run for RANGE tasks.
- The Java Probe is an explicitly selected test-side entry. It checks private inputs before requests, uses existing parameter/envelope/calendar logic, keeps one budget context, stops after a failure and leaves subsequent cases NOT_RUN. Its persistence fields remain null. The new income/fina_indicator/repurchase observations contain fixed counts only, are limited to the specified future RANGE cases, and were not backfilled into historical runs.
- The live harness validates the task receipt, retains actual task observations separately from acceptance status, retrieves all batch pages including split parents, compares independent SQL snapshots and other-stock history, and checks visible records and task/log counts. The navigation and utf8mb4 repairs address observed defects with bounded offline behavior tests. It does not infer inserted/updated counts by subtracting SQL totals.
- The evidence and handoff distinguish successful task execution from failed observation and invalid run identity. In particular, fina_mainbz remains EVIDENCE_MISSING after a SUCCEEDED task because the SQL observation failed; the second SINGLE run retains cleanup FAILED after source-input changes. None of the historical PASS labels is promoted to final acceptance.

## Issues

### Critical — none

No Critical finding was identified in the owned work.

### Important

#### I1 — The canonical completeness index still discards the official-contract findings

**Locations:** `docs/verification/ISSUE-018-range-acceptance.json:457–460` and `:478–481` are one concrete example (weekly); the same initial UNKNOWN/null/empty-reference state remains for every interface. Compare `.superpowers/sdd/2026-09-12-issue-018-t13/official-review.md:9`, `:71` and `:76`, and `docs/verification/ISSUE-018-range-acceptance.md:50`.

The official review establishes 19 explicit public ROW_LIMIT contracts and expressly calls for recording their evidence independently from SOURCE/TASK outcomes. Nevertheless, the final canonical index has **40 UNKNOWN entries, zero completeness references**, and generic `OFFICIAL_COMPLETENESS_EVIDENCE_MISSING` for all 34 RANGE targets. For example, weekly's documented maximum 6000 and retrieval citation are absent. This is the initial fixture state carried forward, not an accurate index of the completed official review. The root confirmed there is no recorded decision explaining that discrepancy.

This matters because the index is the machine-consumed handoff and candidate-selection input: it cannot distinguish a missing public rule from a known public rule whose real applicability or task acceptance remains unresolved. The prose's 19/3/11 distinction is lost. Conservative production availability is appropriate, but it does not require omitting known official evidence from a separate evidence index.

**Repair:** Reconcile the 19 public-contract entries with their exact official references, retrieval times/summaries and documented limits, and replace the generic unresolved labels with the actual remaining limitations. Keep source/task statuses, all historical cases/runs, policy versions and dispositions unchanged. In particular, retain fina_mainbz's unresolved default type and observed SINGLE 150 versus documented 100 applicability conflict explicitly; recording the published limit must not represent that conflict as resolved. If that conflict warrants keeping its machine completeness kind UNKNOWN, record the specific decision and preserve the published-contract evidence in the documented index contract rather than silently treating the page as missing. Keep the 11 original unknown rules and daily/forecast/dividend's ambiguous limits unresolved; evaluate calendar evidence separately. Add a focused persisted-index expectation for the intended public-evidence state and retained closed gates. No real request, policy change or old-run rewrite is needed.

#### I2 — Task-query failures lose task-native error codes and lack response identity validation

**Locations:** `control-plane/e2e/tushare-live.spec.js:30–36`, `:225–235`, and `:1289–1295`.

`readTaskApi` parses a non-200 task response with `validateApiError`, whose legacy `API_ERROR_CODES` set omits task-native codes such as `TASK_NOT_FOUND`. A valid task error envelope therefore throws inside the swallowed catch, and the saved `TASK_QUERY` observation contains `errorCode: null`. The branch also saves the locally generated request ID without checking that both the response header and error body carry it; the subsequent `safeCheck` is success-only because it first requires HTTP 200. Consequently, even an accepted legacy error code can be attributed to a query without checking its returned identity.

The run still fails safely, so this is not a false-success or retry issue. It is a concrete loss of the classified, attributable failure evidence required for diagnosing a stopped real run. The records-query branch now has a stricter safe projection, while the task-query branch retains this gap. The existing tests cover records failures but do not exercise this task-native non-200 path.

**Repair:** Give the task-query failure path a bounded whitelist projection that recognizes the actual task HTTP error contract, requires matching sent/header/body request identity and retains only fixed safe facts. Associate it with the queried owned task/route and current case where available, without copying message/body/cause or changing the observed task status. Unknown/malformed or mismatched responses must stop safely without being accepted as correlated classified evidence. Add an imperative `readTaskApi` regression for a valid `TASK_NOT_FOUND` envelope, identity mismatch and unsafe/unknown error input; verify that the prior SUCCEEDED/RUNNING observation is not rewritten. This can be fixed and checked entirely offline; do not rerun any retained task or rewrite historical results.

### Minor — none

No separate Minor finding is raised. The two findings above are the complete actionable list from this whole-work review.

## Verification evidence and test quality

The test design contains independent expected interface/parameter matrices, rejection cases for malformed or misleading evidence, successful and incomplete split trees, complete calendars, budget/stop behavior and secret projection. Imperative harness tests execute the tracked functions against inert boundaries, including input replacement, deferred PASS publication, navigation and SQL invocation. Those tests verify the corresponding local behavior; they are not evidence that a real browser, source or database round now passes.

Existing reports record final Node **61/61**, zero failures/skips, after navigation and SQL encoding RED/GREEN sequences. This reviewer inspected current Surefire attributes: Probe **30/30**, Policies **109/109**, BatchDownload **17/17**, TradeCalendar **4/4**, Availability **2/2**, all with zero failures/errors/skips. These files do not establish one new combined 162-test run: the recorded five-class gate was **158/158 with Probe26**, followed by a focused Probe30 run. The five-class lifecycle's frontend **468** and build success remain the reported historical local checks. Discovery of 40 SINGLE interfaces and synthetic RANGE selection is kept separate from execution.

No redundant verification command was run here. The two findings are established by the actual persisted values and direct error-path control flow. Their repairs need focused offline regressions and a limited independent re-review. No production change was present to trigger the design's six full source gates; neither those six gates nor `scripts/verify-contracts.sh` is claimed as having run for this work.

## Outcome acceptance

Independent aggregation of the canonical JSON agrees with the verification record:

| Run | Cases and recorded outcomes | Requests | Exit / cleanup |
| --- | --- | --- | --- |
| SOURCE `issue018-t13-source-20260912T123840Z` | 181: 88 PASS, 89 EVIDENCE_MISSING, 1 FAILED, 3 NOT_RUN | 244 | 1 / PASS |
| SINGLE bootstrap `issue018-t13-single-43721811-2900-4e53-9d5f-51c156e1c5de` | 74 NOT_RUN; no task submission | 0 | 1 / PASS |
| SINGLE `issue018-t13-single-5f17f7aa-0b5e-40bd-b6d5-b8d816d94d65` | 74: 14 PASS, 1 EVIDENCE_MISSING, 59 NOT_RUN | 15 | 1 / FAILED |

There are **40 interfaces, 3 runs, 329 cases, 34 NEEDS_VERIFICATION, 6 SINGLE_ONLY, 0 AVAILABLE and no RANGE TASK cases**. The 74 SOURCE SINGLE samples have 36 PASS and 38 EVIDENCE_MISSING. The 259 Tushare requests exclude the separately recorded two fixture requests. Root's separate read-only checks also report agreement between Markdown/JSON, official chapter coverage, bound JAR/manifest/examples hashes and the supplied run identities.

Acceptance 1's scope is preserved, but its final factual index still needs I1. Acceptance 2–5 are not met: there is no valid complete SOURCE/TASK candidate pair, no candidate policy build or real RANGE task closure, and the required representative/special-source facts remain incomplete. The recorded gaps include 11 unknown rules, ambiguous limits, BSE/BJ, stopped-interface historical windows, monthly's trading-day/month-end conflict, fina_mainbz default type and limit applicability, stock_basic's actual listing-state evidence, and meaningful same-row/multiple-security observations. T12 controlled split/fault results do not fill those real-source gaps. The future projection code does not supply past observations.

Acceptance 6 therefore requires an unfinished task and open mother issues. Sound local code after repair will not justify T13 COMPLETED or ISSUE-017/018 closure.

## Pause handoff assessment

The draft correctly records the failed SOURCE round and three unexecuted cases, both distinct SINGLE attempts, the retained 15 successful task executions, the failed SQL observation and source-input instability. It preserves existing database/tasks, failed run identities and the prohibition on automatic retries, repeated sample substitution, success-subset relabeling, new exclusions and automatic publication. It names the existing T13 as the resume task and does not invent T14.

The resolution condition at `final-handoff-draft.md:80` is concrete: registered supplemental evidence or a new fixed evidence round, explicit treatment of prior failures/unexecuted items, stable source inputs and a new empty schema, with a stated execution boundary. It properly requires actual evidence before BLOCKED -> READY and keeps READY -> IN_PROGRESS separate. It does not require every final acceptance gap to be solved before preparatory work can resume: a reviewable new round arrangement is an explicitly allowed condition. A generic continuation instruction or passing tool tests alone cannot retroactively validate the failed rounds.

**Handoff verdict: substantively suitable, pending I1/I2 fixes, limited re-review and root's exact final copy/state reconciliation.** Update its local-implementation review wording and final verification counts to match those repairs; preserve the real-run counts and all outcome limits. The board/issue/README lines that still say IN_PROGRESS are expected at this review point, not an additional defect. Root must perform the planned transition and reconcile them only after the review gate.

## Recommendations and assessment

Repair I1 and I2 in one bounded offline pass, review only those changes and new regressions, then finish root-owned staging, final artifact checks and the BLOCKED pause handoff. Keep the original full review as the reason for that repair, and record the limited re-review separately so the sequence remains auditable.

**Ready to merge? No.** This review does not request a merge, commit or release; two Important defects remain and real outcome acceptance is incomplete. After their correction, the work can be preserved as reviewed partial T13 implementation with a truthful BLOCKED handoff, while final acceptance continues to require new evidence.

## Review limitations

This is static review of the owned implementation and supplied safe artifacts, not a fresh real-run acceptance. No private source rows, raw responses, JDBC/defaults/configuration, live account permissions, retained database contents, process state or external websites were independently inspected. Official quotation/hash verification and retained-data SQL diagnosis are supplied prior evidence; root independently rechecked artifact/hash/link consistency during this review. Temporary evidence may expire as the draft states. String/schema validation cannot itself prove that a citation or human completeness decision is factually correct. Future real behavior after the offline repairs remains unobserved, and no passed old case/run may be upgraded on the strength of this report.
