# T13 Task 1 fix round 1 review

Verdict: **CHANGES REQUESTED** — the four original findings are addressed, but the upstream-counter fix introduces one important specification regression.

Scope was limited to `task-1-review.md`, `task-1-fix-1.diff`, the updated Task1 report, the current `IntegrityFixtureFlowIT.java`, and the current Surefire result. No Maven command was run. The current Surefire artifact confirms `IntegrityFixtureFlowIT` 1 test, 0 failures/errors/skips (12.961 s); the focused fix run recorded in `task-1-report.md` was 1/0/0/0 in 17.447 s.

## Original findings

### 1. Controlled upstream request count — ADDRESSED

`IntegrityFixtureFlowIT.java:43-51` creates an owned loopback `HttpServer` with an `AtomicInteger`; `:137-138` and `:199-200` assert zero requests after the complete v2 and v3 runs. `:202-203` always stops the server, and `:442-445` keeps the download-task/table invariant separate under the accurately named `assertNoDownloadRows`. This closes the original false equivalence between empty download tables and zero JVM HTTP requests.

### 2. PROVEN independent SQL and exact 20 missing keys — ADDRESSED

`IntegrityFixtureFlowIT.java:343-393` independently constructs all 20 `(ts_code, trade_date)` keys, proves expected=20/matched=0/missing=20 and actual=0, and compares the ordered SQL keys to the filtered API issues. Each issue is asserted as `MISSING`/`FAIL` with the complete key. The PROVEN_EXTRA missing/extra SQL at `:296-340` now selects and asserts both key fields. No gap remains from the original finding.

### 3. JSON string types — ADDRESSED

`IntegrityFixtureFlowIT.java:455-458` adds `assertText`, which checks `isTextual()` before exact comparison. The public progress count and page totals use it at `:92`, `:100`, `:107`, and `:118`; statistics and non-null coverage rates use it at `:233-238` and `:273-291`. Expected unknown counts/rates remain explicit JSON-null checks at `:275`, `:277`, and `:289-290`. This closes the numeric-node false-positive.

### 4. Saved v3 coverage/evidence versions — ADDRESSED

`IntegrityFixtureFlowIT.java:174-179` asserts the saved coverage rule result is version 3, all its evidence items carry `ruleVersion=3`, and the saved report descriptor lists coverage@3. `:193-197` additionally proves both B issues are stored/exposed at rule version 3. The previous extension@1 and A@2 assertions remain intact.

## Fix regression

### Important — the receiver fix no longer tests the required no-Token condition — OPEN

The original test started the acceptance app without a Tushare token. The fix now passes `TENSOR_TUSHARE_TOKEN=integrity-fixture-token` at `IntegrityFixtureFlowIT.java:410`, and the report explicitly describes Tushare as enabled “with a synthetic token” at `task-1-report.md:163`. Task1 design section 2 requires the public local capability and check to work without a token, and T13's acceptance condition combines “无Token” with a controlled upstream count of zero. A zero receiver count while valid credentials are configured proves the network half, but it removes the no-credential half and makes the report's earlier “public capability GET without a token” claim (`task-1-report.md:115`) inaccurate for the fixed code.

Remedy: keep the loopback receiver and configured base URL, but start both v2 and v3 applications with an explicitly empty token (`TENSOR_TUSHARE_TOKEN=`) instead of the synthetic token. Keep Tushare enabled so the real production graph is present, retain the two receiver-count assertions, and update the fix report to state that the controlled receiver stayed at zero under the no-token configuration.

No other regression was found within the fix diff. No code, Git index, commit, or task baseline was changed by this review.
