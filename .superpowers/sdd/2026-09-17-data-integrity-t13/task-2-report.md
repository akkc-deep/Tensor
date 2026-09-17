# T13 Task 2 report

## Status

Implementation, review fix round 1, and the final unwrapped five-suite run are complete. The launcher owns MySQL 8.4.6, five isolated schemas, one restricted random application account, private 0600 credential/configuration files, and all JVM/browser processes. It runs the new T13 suite followed by the four packaged regression suites serially and aggregates every failure.

The acceptance JAR is `data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`, SHA-256 `7b4963f4719c56a7e6e2bdbd28de3971969bd2ca3ab06aa537ed080c08e72f60`. The controller separately recorded that its 19 packaged static files match the current `control-plane/dist` in `docs/verification/data-integrity-t13/packaged-static-assets.json`.

## Delivered behavior

- A real Chromium flow creates a fixture version 2 report with actual 20, expected 20, matched 19, missing Jan 20, extra Jan 21, coverage 95%, and FAIL.
- Recursive MySQL queries independently recompute full keys and counts. The suite changes Jan 21 to Jan 20 between checks, then proves a new ID reports 100%/PASS while the original report remains 95%/FAIL.
- PROVEN, PROVEN_EMPTY, and UNCONFIRMED retain their distinct reliable-missing, reliable-empty, and unknown meanings.
- The same database restarts from fixture version 2 to 3. Old evidence remains at version 2; a new report uses version 3 and displays the generic extension rule and evidence.
- Every check records the complete seven-column securities table before and after, then compares ordered rows and SHA-256 values.
- The version 2 JVM has no Tushare token. Both versions use an owned loopback receiver, whose request counter must remain zero.
- The report is captured at 1440, 1024, and 390 pixels with overflow and keyboard issue-focus assertions.
- The launcher grants only `CREATE, SELECT, INSERT, UPDATE, ALTER, INDEX, REFERENCES, TRIGGER`; it does not grant DROP, DELETE, or global privileges.

## Real-run history

The initial scaffolding probe failed before product execution because the launcher did not yet exist. Task 1 already supplied the product behavioral RED, so this was recorded as launcher scaffolding rather than another product RED.

The first launcher preparation attempt stopped before any suite because Colima cannot bind the macOS `/var/folders` temporary root. The launcher now creates its 0700 private directory inside the worktree. The first complete five-suite run then exposed the stripped suites' missing `$JAVA_HOME/bin` PATH entry and current Studio headings/navigation. The launcher now prepends the validated Java 21 runtime; the approved display locators use the packaged UI's current accessible names.

The second complete real run used Node 24.15.0, Java 21.0.11, MySQL client 8.4.11, owned MySQL server 8.4.6, and the exact JAR above. Its results were:

- `integrity-fixture`: failed after 8.477 seconds because independent SQL rendered `0.9500` instead of fixed-scale `0.950000`; cleanup also rejected the username/JDBC URL that Flyway legitimately logs.
- `download-outcomes`: failed after 13.481 seconds on a visibility assertion for the selected option of a native select.
- `dataset-query`: 11/11 passed in 415.682 seconds.
- `tushare-metadata`: failed after 11.407 seconds because a global closed-popup assertion counted hidden native options elsewhere on the page.
- `fixture-flow`: failed after 12.349 seconds on the same native selected-option visibility assumption.

The SQL now casts directly to `DECIMAL(7,6)`. JVM cleanup checks secret password/token values, always clears the application handle, removes its owned log directory, and verifies port 8080 even when a safety assertion fails. Native selected values use `toHaveValue`, and the Element Plus popup check counts visible custom options. Existing HTTP, SQL, business, and network assertions were not weakened.

The packaged regressions also received only the approved Studio presentation synchronization: migrations 1..9, 55 business tables, download H1 `下载工作台`, and exact `数据下载` / `数据查看` navigation labels without the removed numeric suffixes.

The third complete run proved `integrity-fixture` 1/1, `dataset-query` 11/11, and `tushare-metadata` 40/40. It exposed two further current-Studio locator assumptions before their serial successors could run: task-level and batch-level record counts now repeat the same visible text, and the modal task drawer must be closed before sidebar navigation. `fixture-flow` reached 1/3 before the same repeated-text ambiguity. The exact suite results were 26.112 seconds PASS, 9.665 seconds FAIL, 415.554 seconds PASS, 141.048 seconds PASS, and 14.060 seconds FAIL.

## Review fix round 1

The bounded review from tree `fd672d4001f653c744ba95bfb8f4fe0a5ab54bbf` found three important evidence gaps and one minor durable-evidence gap. All four are addressed:

- Each npm/Playwright/JVM suite now starts in its own process group. SIGINT and SIGTERM become controlled launcher interruptions; the launcher sends SIGTERM to the owned group, waits with a bound, falls back to SIGKILL plus a final wait, and only then removes its owned container and credentials.
- The browser locates the individual MISSING and EXTRA table rows and asserts each row's exact type, primary date, `ts_code=PROVEN_EXTRA`, and `trade_date`. The Jan 20 filter must leave exactly the complete MISSING row.
- A separate recursive SQL call for PROVEN independently records expected 20, actual 0, matched 0, missing 20, extra 0, rate `0.000000`, and the ordered complete keys `PROVEN|2026-01-01` through `PROVEN|2026-01-20`.
- Lifecycle collection occurs after cleanup in a `finally`, so both the explicit version 2 stop and cleanup-owned version 3 stop are written before safe evidence is persisted.

A temporary fake-npm probe sent SIGTERM and SIGINT to separate launcher processes. In both runs the npm parent, child, and grandchild PIDs were gone and the owned container/credential cleanup sentinels were removed. This probe did not use Docker or a browser.

Safe probe summary: `docs/verification/data-integrity-t13/real/signal-probe-summary.json`. Reproduction commands are `python3 /tmp/t13-signal-probe/run-probe.py` and `PROBE_SIGNAL=SIGINT python3 /tmp/t13-signal-probe/run-probe.py`; the temporary harness creates no durable credentials or PID log.

The legacy failures were then checked with a temporary launcher wrapper that still created the official five-schema restricted environment but marked unselected suites as exit 125 and wrote its launcher summary under `/tmp`. It is not counted as a complete run. `fixture-flow` passed 3/3. `download-outcomes` subsequently passed 15/15 in 159.452 seconds, including its real 120-second timeout case, trigger rollback, upstream failures, database assertions, and cleanup.

The focused run also proved that MySQL 8.4's binary-log policy rejects trigger creation by the deliberately restricted account unless trusted trigger creators are enabled. The owned acceptance server now starts with `log_bin_trust_function_creators=ON` and verifies the value is `1` before any suite. Grants remain exactly `CREATE, SELECT, INSERT, UPDATE, ALTER, INDEX, REFERENCES, TRIGGER`; no global, DROP, DELETE, or SUPER grant was added.

Current Studio-only compatibility changes scope record counts to the expanded task-record panel, close the modal task drawer before clicking sidebar navigation, assert the named terminal progress bar on detail pages, and retain exact no-cancel button/link assertions. HTTP, SQL, error, timing, rollback, and network expectations remain unchanged.

## Static verification

`node --check` passed for all changed JavaScript suites/helpers, Python AST parsing passed for the launcher, and `git diff --check` passed. No Maven command was run. The controller owns staging and commits.

## Final real-browser verification

The unwrapped launcher ran from 2026-09-17T10:45:07.610559Z through 2026-09-17T10:57:57.113808Z with the exact JAR and tools listed above. It passed **70 tests, 0 failures, 0 skipped**:

- `integrity-fixture`: 1 passed, 0 failed, 0 skipped; 26.176 seconds.
- `download-outcomes`: 15 passed, 0 failed, 0 skipped; 161.394 seconds.
- `dataset-query`: 11 passed, 0 failed, 0 skipped; 415.625 seconds.
- `tushare-metadata`: 40 passed, 0 failed, 0 skipped; 140.744 seconds.
- `fixture-flow`: 3 passed, 0 failed, 0 skipped; 18.761 seconds.

`browser-evidence.json` records six real checks and six securities snapshots. Every ordered seven-column `before` value equals its `after` value, including its SHA-256. Independent SQL records:

- PROVEN_EXTRA before update: expected 20, actual 20, matched 19, missing 1, extra 1, rate `0.950000`, missing `PROVEN_EXTRA|2026-01-20`, extra `PROVEN_EXTRA|2026-01-21`.
- PROVEN_EXTRA after update: expected 20, actual 20, matched 20, missing 0, extra 0, rate `1.000000`.
- PROVEN: expected 20, actual 0, matched 0, missing 20, extra 0, rate `0.000000`, with ordered complete keys from `PROVEN|2026-01-01` through `PROVEN|2026-01-20`.

Fresh responsive screenshots and SHA-256 values are:

- `report-1440.png`: `1f49b5809cfd4d51aeb4230a192564f18b4be447e5e3942e6653b9e8de5e709c`.
- `report-1024.png`: `264e8b86fc5061e27fc295688386715a90e3146a7378f2258538226ffe8f00a4`.
- `report-390.png`: `a7c99a479204a4463284237a0fb0da64c4dbaf2ae2d2738a460fdb8ccdcc1c5d`.

Both version 2 and version 3 lifecycle records have `logSafety=true` and an owned termination exit code of 143. The owned upstream receiver saw zero calls. JVM, receiver, and port cleanup are true; the launcher removed its MySQL container and private credential/configuration directory, no worktree launcher temp directory remains, and port 8080 is free.

Durable evidence:

- `docs/verification/data-integrity-t13/real/browser-evidence.json`
- `docs/verification/data-integrity-t13/real/launcher-summary.json`
- `docs/verification/data-integrity-t13/real/signal-probe-summary.json`
- `docs/verification/data-integrity-t13/real/report-1440.png`
- `docs/verification/data-integrity-t13/real/report-1024.png`
- `docs/verification/data-integrity-t13/real/report-390.png`

The bounded fix review approved all three important findings and the minor finding. The controller recorded the reviewed staged code tree as `efc412d043d37d8ded12c914abcbd61313674d9f`.
