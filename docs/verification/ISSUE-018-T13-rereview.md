# ISSUE-018-T13 final repair — limited independent re-review

2026-09-13. **Spec: PASS. Quality: PASS. I1: ADDRESSED. I2: ADDRESSED.** No new Critical, Important or Minor finding was identified in the repair diff. **Outcome acceptance remains NOT MET; the reviewed BLOCKED pause handoff is suitable for root to finalize.**

## Scope and evidence

Read the complete I1/I2 findings in `ISSUE-018-T13-review.md`, `.superpowers/sdd/2026-09-12-issue-018-t13/final-fix-brief.md` and `final-fix-report.md`. Reviewed `final-fix-review.diff` against its baseline, including root's handoff-draft synchronization, and the directly relevant current code and tests. This is the one limited independent re-review of those two findings and regressions introduced by their repairs; it does not reopen unchanged areas of the whole-work review.

The diff contains the three authorized JavaScript files, the acceptance JSON/Markdown and the proposed final handoff. Read-only JSON inspection and comparison against `final-fix-baseline.json` confirmed that the complete historical `runs` value is unchanged, every interface field other than `completeness`/`unresolved` is unchanged, and all six SINGLE-only interface records are unchanged in full. The original whole-work report remains a separate historical review and was not edited by this reviewer.

## Finding disposition

### I1 — Canonical completeness evidence: ADDRESSED

The index now records the exact 19 published limits required by the repair ruling: weekly6000, monthly4500, daily_basic6000, stk_limit5800, moneyflow6000, margin4000, margin_detail6000, block_trade1000, slb_len5000, slb_sec5000, slb_sec_detail5000, new_share2000, stk_holdernumber3000, stk_holdertrade3000, pledge_detail1000, fina_indicator100, fina_mainbz100, top_list10000 and disclosure_date6000. Their references point to their individual official sections, which retain the acquisition identity and quotation context. For example, weekly is now recorded at `docs/verification/ISSUE-018-range-acceptance.json:471` rather than retaining the initial missing-rule placeholder.

trade_cal is separately CALENDAR_COVERAGE with a null row limit (`:356`), citing the binding natural-day contract, official section and the existing SSE/SZSE observations. Its unresolved facts explicitly retain the failed SOURCE run, BSE coverage failure, no actual BJ planning/determined mapping and no RANGE TASK. It does not claim that the public page alone guarantees complete natural-day responses.

The resulting kinds are **19 ROW_LIMIT / 1 CALENDAR_COVERAGE / 20 UNKNOWN**. The original 11 unknown rules, daily/forecast/dividend's ambiguous limits and six SINGLE-only entries keep the existing strict UNKNOWN/null/empty-reference form. The validator's UNKNOWN contract was not relaxed. Generic missing-official-rule labels are removed from the known-rule entries and replaced with actual per-interface gaps, including the failed SOURCE identity and unexecuted RANGE TASK.

The fina_mainbz ruling is implemented explicitly (`json:250`, `md:126`): ROW_LIMIT100 records the published contract, while default type and two SINGLE150 observations versus that limit remain unresolved. The Markdown overview (`:54`) and revised individual conclusions distinguish published evidence from actual request applicability and availability. No production rule, version or gate was changed by recording these facts.

The new persisted-index test at `control-plane/e2e/tushare-range-evidence.test.js:141` independently enumerates the 19 limits and exact remaining unknown set, checks the separate calendar and citations, verifies the current closed dispositions/versions and confirms that no RANGE TASK was introduced. It does not derive expectations from production policy. This addresses the original evidence-indexing gap without changing historical SOURCE/TASK facts.

### I2 — Classified, attributable task-query failures: ADDRESSED

`control-plane/e2e/tushare-range-evidence.js:656` adds `safeTaskQueryFailure`, reusing the helper's independent fixed code set including TASK_NOT_FOUND. It validates the exact error envelope, nonblank message/field-error shapes, boolean retryable, failure HTTP status and matching sent/header/body request UUID. It validates the task UUID, allowed task route and bounded plugin/API/case context; fixture's absent acceptance case is explicitly null. Existing recursive safety checks apply before the projection leaves the helper. Only fixed identity/status/code facts are returned; message, body and cause are omitted.

`tushare-live.spec.js:1282` checks owned task/route context before the request and uses that projection for non-200 results. A malformed, unsafe, unknown or identity-mismatched response cannot become correlated classified evidence: the fallback contains operation/status with null requestId/errorCode, or the preceding text-safety guard stops before any projection. The unchanged non-200 check then stops execution. No retry or task-state assignment was added. Success still requires a matching response header and the existing DTO parser.

`submitDownload` constructs the context from the accepted task and current case (`:1330`) and passes it through both detail polling (`:1343`) and complete batch paging (`:1363`). This closes the actual caller path, including fixture null context, rather than merely adding an unused helper.

The imperative tests at `tushare-range-evidence.test.js:1144–1256` exercise tracked `readTaskApi` and `submitDownload` bodies against inert I/O. They cover TASK_NOT_FOUND for detail/batch queries, fixture context, independent header/body/both identity mismatches, malformed/unsafe/unknown envelopes, owned-route rejection, retained success-parser checks and actual case-context propagation after RUNNING/SUCCEEDED observations. They check that prior task state remains unchanged and that unsafe error text is not retained. These tests directly cover the original failure and the new integration branches.

## Regression and verification assessment

No new regression was found in the reviewed delta. The helper change is additive; success validation, records-query handling, input binding, budgets, fixed sample selection, SQL/navigation repairs and SOURCE code remain unchanged. Publishing official rules still cannot make this index a candidate for real RANGE tasks: all referenced SOURCE facts retain the failed run identity, and the existing clean-run selection gate is unchanged.

The implementation report records the same sanitized Node24 command before and after the repair:

`env -i PATH=/usr/bin:/bin:/usr/sbin:/sbin LANG=C LC_ALL=C data-plane/tensor-app/target/frontend/node/node --test control-plane/e2e/tushare-range-evidence.test.js`

Reported RED: **67 tests, 62 pass, 5 fail, exit 1**. Reported GREEN: **67/67, zero failures/cancelled/skipped/todo, exit 0**. The failure descriptions correspond to I1/I2: missing public limits, missing task-native classification/context, mismatched identity accepted, malformed response retaining correlation and absent caller context. The additional success-parser guard test passed before the fix; all original 61 tests remained passing. These are supplied execution results, not fresh runs by this reviewer. No concrete unresolved concern required additional execution, so no tests or discovery were repeated.

## Outcome and BLOCKED handoff

The repairs leave **40 interfaces / 3 runs / 329 cases / 34 NEEDS_VERIFICATION / 6 SINGLE_ONLY / 0 AVAILABLE**, with no RANGE TASK. The full failed SOURCE round, all 74 bootstrap NOT_RUN cases, and the second SINGLE round's 14 historical PASS / 1 EVIDENCE_MISSING / 59 NOT_RUN and cleanup FAILED remain intact. The retained 259 Tushare requests and separate fixture calls are historical facts, not new execution in this repair.

The synchronized handoff accurately adds the public-rule distinction and repaired TASK_QUERY evidence path, updates the focused Node result to 67/67, and links the whole review and this re-review. It preserves fina_mainbz's applicability/default-type gap, the real-run failures, remaining source facts, stable-input/new-schema recovery requirements and the prohibition on retrying or rewriting historical work. Its existing blocker and recovery conditions are unchanged and remain justified.

**The local I1/I2 review gate is cleared. Root may copy the reviewed handoff, record IN_PROGRESS -> BLOCKED and reconcile the planned status/summary lines after its final checks.** This is not a finding that T13 or either mother issue is complete. It does not authorize real requests, policy opening, commit, merge or publication, and it does not validate any old run retroactively.

## Limits

This verdict covers I1/I2 and this diff only. No unchanged implementation area was re-audited. No network, SOURCE Probe, account, SQL, application/browser, credential inspection, Git mutation or subagent was used. Only this re-review report was written. Root retains final document/index checks, staging and transition ownership. Fresh real evidence and the remaining acceptance work are still required before completion can be considered.
