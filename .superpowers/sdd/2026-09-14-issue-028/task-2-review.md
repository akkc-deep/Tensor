# ISSUE-028 frontend independent review

Scope: frontend DTOs, download pages/components, frontend fixtures, and controlled browser E2E only. Backend Java and published contract ownership were excluded except where the frontend consumes their shapes.

## Verdicts

- Spec compliance: **APPROVE**
- Code quality: **APPROVE**

No actionable findings remain in the reviewed frontend scope.

## Evidence

- `control-plane/src/api/downloadTaskDtos.js:202` adds `extraction` to the exact task whitelist. `taskExtraction` at line 209 requires an exact two-field frozen object for RANGE, accepts only the four contract rule kinds with a nonblank saved version, and requires explicit `null` for SINGLE. Invalid responses continue through the existing request-ID-safe `INVALID_RESPONSE` path.
- `control-plane/src/api/downloadTaskDtos.js:442` preserves the completeness-rule row-limit/evidence invariants. The range cross-check at line 519 requires RESPONSE_ONLY to use `NATIVE_RANGE` with `splittable=false`; the same parser covers AVAILABLE and NEEDS_VERIFICATION responses, while UNSUPPORTED retains its UNKNOWN-only shape.
- Formal examples are losslessly loaded and passed through the strict frontend parsers in `control-plane/src/api/downloadTaskDtos.spec.js:18`. The focused invalid extraction matrix starts at line 245, and the RESPONSE_ONLY capability matrix at line 662 covers both allowed availability states plus invalid planning, splitting, row-limit, and evidence combinations.
- All affected frontend task fixtures now carry the saved extraction fact. In particular, the recovery/conflict composable fixtures use a complete formally parsed task in `control-plane/src/composables/useDownloadFlow.spec.js:27`; the browser fixtures preserve the task-time rule/version independently from later capability changes.
- `control-plane/src/utils/downloadTaskText.js:6` centralizes the shared list/detail semantics. RESPONSE_ONLY success compares the parsed BigInt count directly with `0n`; non-success states keep their failure/interruption/running labels even when rows exist. The notice helper distinguishes RESPONSE_ONLY from historical UNKNOWN.
- `control-plane/src/components/download/DownloadTaskList.vue:175` and `control-plane/src/views/DownloadTaskView.vue:64` render saved rule/version and persistent uncertainty beside mode/status using ordinary visible text. They do not consult API names or current capabilities. Strict and SINGLE success wording remains unchanged.
- `control-plane/src/views/DownloadView.vue:72` derives the pre-submit notice only from the selected RESPONSE_ONLY RANGE capability and renders it beside the existing submit action at line 263 without introducing a dialog, checkbox, or state-management layer.
- Existing responsive and accessibility structure is preserved: the list remains a keyboard-focusable labelled scroll region, detail content retains mobile single-column behavior and wrapping, and status/uncertainty meaning is available as text rather than color or hover content.

## Verification

- `npm --prefix control-plane test`: **34 files, 524 tests passed**.
- `npm --prefix control-plane run test:e2e -- e2e/download-tasks.spec.js e2e/ui-redesign.spec.js`: **66 tests passed** in the controlled fixture environment, including RESPONSE_ONLY success/empty/failure/interruption persistence, saved-vs-current policy changes, historical strict/UNKNOWN/SINGLE cases, five viewport layouts, keyboard/focus, and reduced motion.
- `git diff --check`: passed.
- `git diff --cached --check`: passed.
- No real SOURCE or TASK calls were made.
