# T10 final spec and quality review

## Verdict

**Spec review: PASS for the reviewed implementation. Quality review: PASS.** No new actionable correctness finding was established. The two storage P2 findings in `task-2-review.md` are addressed in the current code and have focused regression coverage.

This is a scoped static review, not the final execution/acceptance gate. The main agent owns fresh focused/full frontend verification, build, verification documentation, and task handoff. No passing suite was rerun by this reviewer.

## Scope and authority

Reviewed the complete `docs/task-designs/DATA-INTEGRITY-T10-design.md`; cross-checked the T09 design sections 2–4, `docs/contracts/integrity-check.schema.json`, the contract examples and integrity sections of `openapi-v1.yaml`.

Implementation scope: `control-plane/src/api/integrityDtos.js`, `integrityChecks.js`, `composables/useIntegrityCheck.js`, their specs, and the five-error additions in `api/errors.js` / `api/api.spec.js`. Shared HTTP and lossless JSON helpers were read only to trace their integration. Unrelated staged branch baseline was excluded. No implementation edits or subagents were used.

## Review evidence

- DTOs copy/freeze closed nested shapes, preserve explicit nulls and all report/evidence/business-key fields, convert canonical wire count strings directly to BigInt, and retain safe numeric page/planned-unit fields. Nested download capabilities keep their independent JSON-integer parsing path. Historical summaries do not manufacture an overall data conclusion; task, unit and data-status enums remain separate.
- All six API methods use the shared HTTP instance and lossless text transform. Responses validate status, response/request correlation, endpoint identity and requested pagination. POST checks receipt submission/source identity, body requestId and Location while preserving original request array order and optional API omission. Historical symbols retain their original text. Submission lookup requires the exact empty or unique matching result and fixed recovery pagination.
- The submission state machine persists the frozen request before POST, merges duplicate submit calls, retains the original uncertain POST error/requestId, performs GET recovery first, and enables explicit same-ID resend only after a successful empty lookup. Definition changes retain the old payload, clear confirmation and reload capability; preparing a new request requires explicit confirmation. No mount/reconnect/recovery path initiates POST.
- Capability and submission generations discard responses from switched sources or inactive/disposed scopes. Polling generations reject stale task/page success, error and finally writes. Request slots remain occupied through abort settlement, preventing overlap for the same GET key. Timers start only after the entire current round settles, stop on terminal tasks or query failure, and only query detail plus a visible result page.
- The reviewed specs exercise all contract examples, precision/null/status cases, real Axios adapter paths and lossless business keys, frozen submission identity, storage failures, recovery failure, definition changes, real adapter lost-response recovery, slow requests, stale task/page responses, stale finally handlers, terminal states and scope disposal. The API suite was being finalized concurrently; final execution results belong in the acceptance evidence rather than being inferred from this review.

## Findings

None. There is no severity/reproduction/location entry because this review did not establish an additional defect requiring a change.

## Limits

This review does not claim a real backend/browser workflow, T13 release gate, or final full-regression/build pass. It does not review implementation outside the stated T10 scope.
