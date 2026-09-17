# Task 2 fix round 1 review

## Verdict

**APPROVED for the bounded source fix.** All three Important findings and the Minor finding from `task-2-review.md` are **ADDRESSED**. No new finding was found in the fix-round diff.

The final unwrapped five-suite run is still a controller gate. It must finish successfully before Task 2 is reported green; its current execution is not a source defect.

## Original findings

### Important — interrupted launcher process-tree cleanup: ADDRESSED

`scripts/verify-integrity-fixture.py:43-53` converts `SIGINT` and `SIGTERM` into controlled interruptions. `scripts/verify-integrity-fixture.py:165-223` starts each npm suite in its own process group, sends `SIGTERM`, waits with a bound, escalates to `SIGKILL`, and performs a final process/group wait. The exception then reaches the launcher's existing container and credential cleanup. Controller-provided SIGTERM and SIGINT probes confirmed that the fake npm parent, child, and grandchild exited and both cleanup sentinels were removed.

### Important — exact browser missing/extra row mapping: ADDRESSED

`control-plane/e2e/integrity-fixture.spec.js:143-172` now requires exactly two issue rows, binds `MISSING` to Jan 20 and `EXTRA` to Jan 21, and checks `ts_code=PROVEN_EXTRA` plus the exact `trade_date` inside each row. The filtered result must contain exactly the Jan 20 missing row. This closes the previous token-only assertion gap.

### Important — independent SQL for empty-local PROVEN: ADDRESSED

`control-plane/e2e/integrity-fixture.helpers.js:312-337` restricts the generalized recursive SQL helper to the controlled `PROVEN_EXTRA` and `PROVEN` cases. `control-plane/e2e/integrity-fixture.spec.js:270-281` independently asserts expected 20, actual 0, matched 0, missing 20, extra 0, rate `0.000000`, and the ordered complete keys `PROVEN|2026-01-01` through `PROVEN|2026-01-20`, then persists the SQL result in browser evidence.

### Minor — version 3 lifecycle evidence ordering: ADDRESSED

`control-plane/e2e/integrity-fixture.spec.js:214-229` collects lifecycle entries in a `finally` after `environment.cleanup()`, so the cleanup-owned version 3 stop is included even when cleanup reports a failure. Both version 2 and version 3 lifecycle records are available before safe evidence is written.

## Regression review

The legacy-suite changes remain narrowly scoped. `download-outcomes.spec.js:815-824`, `:941-966`, and `:980-988` preserve the no-cancel, HTTP, task, count, and navigation assertions while targeting the current terminal progress bar, expanded record panel, and modal close action. `fixture-flow.spec.js:563-568` scopes the repeated count text to the record panel. Controller evidence reports `fixture-flow` 3/3 and `download-outcomes` 15/15 in 159.452 seconds, including the real 120-second timeout case.

`scripts/verify-integrity-fixture.py:127-152` verifies the owned MySQL server exposes `log_bin_trust_function_creators=1`, and `:281-305` enables it only as an owned-server option. The application account grants at `:268-278` remain exactly `CREATE, SELECT, INSERT, UPDATE, ALTER, INDEX, REFERENCES, TRIGGER` on the five owned schemas; no global, `DROP`, `DELETE`, or `SUPER` privilege was added.

No Maven, npm, browser, or signal command was run during this review. No implementation or resource file was changed.
