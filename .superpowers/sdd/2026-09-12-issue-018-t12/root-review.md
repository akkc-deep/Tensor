# Root scoped review — T12

## Findings

No remaining findings.

The initial review found that `ui-redesign.fixtures.js` derived inaccurate RANGE metadata from the old query mode. The supplied fix replaces that derivation with an independent 34-policy matrix. Its axes, labels, planning modes, splittability, scope parameter shapes, and `tushare-range-v1` version match `TushareBatchPolicies`; it retains 34 `NEEDS_VERIFICATION`, six `UNSUPPORTED`, and the intentionally closed `UNKNOWN/null/null` completeness rule. The ordinary UI matrix now also asserts the SINGLE selection and disabled RANGE gate/reason for every API. This resolves the finding at source level.

## Verdict

Changes to CORS exposure, Playwright suite selection, per-file base URLs, and the packaged environment helper conform to the scoped T12 design. The helper validates the exact four roots and five fields before assignment, uses an absolute `O_NOFOLLOW` file owned by the current user with exact mode `0600`, and emits a fixed secret-free error. The dedicated suite flags, lifecycle URL/scenario checks, ordinary exclusions, one worker, task reporter/retry settings, and real-account trace/screenshot restrictions are present. CORS adds only `Location` to the existing exposed headers and has a focused 202 regression assertion.

No tests were rerun by this reviewer. The initial verdict used the supplied report/diff and source assertions; the fix verdict uses `root-fix.diff`. The final ordinary browser rerun remains part of the root coordinator's combined gate evidence.
