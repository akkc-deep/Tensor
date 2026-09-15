# ISSUE-030 Task 1 UI fix scoped re-review

## Verdict

- Warning selector ambiguity: **ADDRESSED** in both specs.
- Stale SINGLE setup assumptions: **ADDRESSED** at all 7 reported setup locations.
- Fix-introduced breakage: **NONE FOUND** in the scoped diff.
- Open static findings: **NONE**.

## Finding 1 — warning selector ambiguity

**ADDRESSED.** `ui-redesign.spec.js` and `tushare-metadata.spec.js` both replace the broad `.form-footer__help` assertion with a locator filtered by the complete warning text `数据完整性未确认，可能存在上游截断`. Each then separately asserts visibility and content. This removes the observed two-element strict-mode ambiguity while continuing to require the intended RESPONSE_ONLY warning. The surrounding rule branches, default RANGE radio checks, row-limit/calendar assertions and enabled-state assertions remain unchanged.

## Finding 2 — stale SINGLE defaults

**ADDRESSED.** The new `chooseSingleMode` helper scopes the click to the `下载模式` radiogroup, selects the exact `单次请求` label and verifies that the SINGLE radio is checked. It is invoked at exactly 7 explicit setup locations identified by the follow-up report:

1. configuration failure/reload and validation flow;
2. `new_share` final submission;
3. the five-viewport submission loop;
4. business-theme submission flow;
5. keyboard-only submission flow;
6. normal screenshot submission flow;
7. error screenshot submission flow.

The helper is not added to `chooseDownload`, so selecting an AVAILABLE candidate continues to default to RANGE. The 40-interface matrix loop and its RANGE checked assertions are untouched. At each repaired SINGLE site, the existing API choice, field input, keyboard interaction, viewport setup, submit action, request-count checks, screenshots and success/error assertions are preserved.

## Fix-introduced breakage

None found in the 155-line scoped diff. The changes are test-only and do not alter product code, HTTP fixtures, capability data, request bodies or saved extraction summaries. No original assertion or scenario was removed.

This review read only `ui-fix-brief.md`, the appended Browser fix round in `task-1-report.md`, and `ui-fix-review.diff`. It did not run tests or assess runtime results; the controller owns a clean serial browser rerun after the interrupted/colliding attempts.
