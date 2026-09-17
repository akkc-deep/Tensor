# Task 2 spec and quality review

## Verdict

- **Spec: FAIL — 3 Important findings.**
- **Quality: FAIL — 1 Minor finding.**

The third real T13 suite run passed 1/1 and the controller inspected all three responsive screenshots. Those normal-path results do not exercise the findings below. The still-running or subsequently repaired legacy-suite result is not treated as a code defect in this review.

## Spec findings

### Important — the launcher does not terminate and wait for its owned suite process tree on interruption

`scripts/verify-integrity-fixture.py:143-153` runs npm through blocking `subprocess.run` and retains no process or process-group handle. `scripts/verify-integrity-fixture.py:265-287` only cleans the container and credentials if Python reaches its exception/finally path, while the launcher installs no `SIGTERM` handler. A `SIGTERM` sent to the launcher can therefore terminate Python without running cleanup. On `KeyboardInterrupt`, `subprocess.run` can kill/reap its direct npm child, but the launcher still has no bounded termination/wait contract for npm's Playwright and JVM descendants. It may remove the container and credential files while owned descendants remain alive. This violates design §4's explicit requirement to terminate and wait for owned child processes before removing owned resources.

Minimal fix: start each suite in its own process group, keep its `Popen` handle, convert `SIGINT`/`SIGTERM` into a controlled interruption, then send `SIGTERM` to that owned group and wait with a bounded timeout; use `SIGKILL` plus a final wait only after the timeout. Remove the container and private credential directory only after that wait. Verify the behavior with a small fake-npm fixture that writes parent/child/grandchild PIDs, signal a separate launcher process, and assert every owned PID is gone and cleanup sentinels were removed; this does not require Docker or a browser.

### Important — the browser test does not prove the exact missing/extra issue mapping

`control-plane/e2e/integrity-fixture.spec.js:143-160` checks that the issue region contains Jan 20, Jan 21, `PROVEN_EXTRA`, and `trade_date`, but it never scopes those values to exact issue rows or asserts which row is `MISSING` and which is `EXTRA`. The test would still pass if the UI swapped the two issue types or rendered the complete business-key values in the wrong issue. The independent SQL assertion at `integrity-fixture.spec.js:222-226` proves the database sets, not that the browser renders the required Jan 20 missing key accurately.

Minimal fix: locate the individual missing and extra issue rows/cards and assert their exact type, primary date, and complete business key (`ts_code=PROVEN_EXTRA`, `trade_date=2026-01-20` for missing; Jan 21 for extra). After applying the Jan 20 date filter, assert that the remaining row is specifically the missing row with that full key.

### Important — the browser suite does not independently recompute the empty-local PROVEN key set

Design §3 requires the real browser and Java tests to calculate sets independently and explicitly says that local `PROVEN` with no rows must likewise calculate all 20 missing keys. `control-plane/e2e/integrity-fixture.helpers.js:312-335` hard-codes `coverageSummary()` to `PROVEN_EXTRA`, while `control-plane/e2e/integrity-fixture.spec.js:256-260` verifies the `PROVEN` case only through UI totals (`actual 0`, `expected 20`, `missing 20`, `coverage 0%`). Those values therefore repeat the application report and do not supply the required independent browser-side SQL set evidence.

Add an independent SQL query for `PROVEN` over Jan 1 through Jan 20 and assert exact values: expected `20`, actual `0`, matched `0`, missing `20`, extra `0`, rate `0.000000`, plus the ordered complete missing-key set `PROVEN|2026-01-01` through `PROVEN|2026-01-20`. Persist that result in the safe browser evidence and compare it with the displayed report without deriving either expectation from the report response.

## Quality finding

### Minor — durable lifecycle evidence omits the version 3 JVM shutdown

`control-plane/e2e/integrity-fixture.spec.js:202-214` copies `environment.lifecycle` into evidence before calling `environment.cleanup()`. Normal test execution stops v2 explicitly, but v3 is stopped by that later cleanup, so `browser-evidence.json` records only the v2 lifecycle entry. The controller confirmed this exact result in the passing third run. The boolean cleanup summary does not preserve v3's signal, exit code, or log-safety record.

Move lifecycle collection after cleanup, using `finally` so the entries are copied even when cleanup reports a failure. Persist both v2 and v3 records before writing the evidence file.

## Other reviewed requirements

No additional issue was found in the scoped changes for recursive SQL counts and exact decimal rates, seven-column fixture snapshots before and after every check, the empty-token/owned-receiver zero-call boundary, version 2 to 3 history, three responsive widths, restricted schema grants, or the V9/55-table and Studio-label synchronization in the four legacy suites. Their existing HTTP, SQL, business, and network assertions remain present.

No Maven, npm, browser, or signal command was run during this review. No implementation or resource file was changed.
