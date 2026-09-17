# T10 composable review

Reviewed the full `docs/task-designs/DATA-INTEGRITY-T10-design.md`, `control-plane/src/composables/useIntegrityCheck.js`, and its spec. This is a static review against the fixed design interfaces; API/DTO implementation was excluded and tests were not rerun, as requested.

## Findings

### P2 — A transient storage failure hides the later POST error and request ID

Location: `control-plane/src/composables/useIntegrityCheck.js:157–172`.

Reproduction: prepare a request, make `storage.setItem` fail, and call `submit()`. Restore storage, then call `submit()` again with a POST that fails with `ClientError('TIMEOUT', requestId)` and an empty recovery response. The state correctly becomes `uncertain`, but `submissionError` still contains the first storage TypeError because line 172 only assigns when null. The same issue leaves a stale “尚未发送” error after a successful retry. On an explicit resend, a new storage failure also replaces an existing original POST error at line 159.

Impact: callers cannot show or correlate the original uncertain POST failure/requestId, and can tell the user that a request was never sent after it actually was. This violates the requirement to retain the initial uncertain submission error including requestId.

Minimal recommendation: distinguish a preflight storage error from the original POST error. Clear a resolved preflight error before the first actual POST, preserve the original POST error through recovery/resend, and avoid overwriting it if storage fails during resend. Add regression coverage for write-failure → successful write → timeout and timeout → empty recovery → resend write-failure.

### P2 — Default session storage access can throw before the composable creates its error state

Location: `control-plane/src/composables/useIntegrityCheck.js:13`.

Reproduction: use a browser/storage policy where the `globalThis.sessionStorage` getter throws `SecurityError` (or install an equivalent throwing getter in a unit test), then invoke `useIntegrityCheck()` without an injected storage object. Default parameter evaluation throws before the guarded `readPending()` block runs.

Impact: creation of the composable fails, so the page cannot expose the required storage error state or even use the read-only detail/result functionality. The existing storage-write test injects an object and does not exercise this case.

Minimal recommendation: resolve the default storage inside a guarded initialization path rather than the parameter initializer. Return a usable composable with the safe storage error populated, and keep POST disabled while storage is inaccessible. Test the throwing default getter.

## Other review observations

The polling request-slot implementation retains aborted same-key requests until settlement, and its generation/controller checks reject stale task/page responses and stale finally handlers. Submission recovery remains GET-only and the visible happy-path state machine preserves the frozen request. No additional concrete race/recovery defect was established by this static review.

## Follow-up fixes and verification

Both P2 findings were fixed in the composable, with three focused regression tests in its existing spec. The only public-state addition is readonly `storageError`: initial read/write errors remain available in `submissionError` for compatibility, but a resolved preflight error is cleared before POST. Once a POST error exists, storage failures during explicit resend update `storageError` without overwriting the original POST error or requestId. Successful writes clear `storageError`. Default storage is resolved lazily inside the existing guarded read/write paths, so a throwing getter leaves a usable composable, exposes a safe storage error, blocks POST, and still permits detail/results GETs.

Tests used `/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin` first on PATH and ran from the isolated worktree root.

RED command:

```sh
PATH=/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin:$PATH npm --prefix control-plane test -- src/composables/useIntegrityCheck.spec.js -t 'transient storage|storage fails during|throwing default storage'
```

Result before implementation: exit 1, 3 failed / 38 excluded by filter. The failures demonstrated the stale preflight TypeError replacing NETWORK/requestId, resend storage TypeError replacing TIMEOUT/requestId, and the uncaught default getter SecurityError. No missing-export or import failure was counted as RED.

GREEN: the same command returned exit 0, 3 passed / 38 excluded by filter. An intervening attempt while the API agent replaced `integrityChecks.js` encountered a missing-file import error and was not counted as verification.

Composable regression command:

```sh
PATH=/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin:$PATH npm --prefix control-plane test -- src/composables/useIntegrityCheck.spec.js
```

Result after API implementation was restored: exit 0, all 41 tests passed, no skips. This includes the real HTTP-adapter lifecycle integration test. No full frontend suite or build was run by this reviewer.

Scoped self-review: confirmed default storage resolution is inside caught operations; repeated preflight failures replace only prior storage errors; successful writes remove stale preflight errors; original POST errors survive later POST and storage failures; GET lifecycle code is unchanged. Only the composable and its spec are staged for handoff; no commit was made.
