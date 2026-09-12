# ISSUE-018-T12 query resume report

Date: 2026-09-12

Scope: `control-plane/e2e/dataset-query.spec.js`, `control-plane/e2e/download-outcomes.spec.js`, and `control-plane/e2e/download-task-lifecycle.spec.js`. No product source was changed and no real Tushare request was made.

## Query timeout root cause and fix

The first diagnostic run completed all 252 initial downloads, then timed out while waiting for API response-body scans. The mandated resumed diagnostic (`/tmp/issue018-t12-query-resume-diagnostic.log`) isolated two distinct states around each full navigation:

- A snapshot of `responseScans` can be empty while an API request has started but has not emitted its `response` event. This is the request-before-response window, so waiting only the current response-scan array is insufficient.
- Once the response event exists, a pending scan can still be seen explicitly. The last concrete failure was `API response body resource unavailable: /api/v1/download-tasks`, caused by navigation disposing the recent-task-list response before its body read settled.

The monitor now tracks same-origin API requests from `request` until `requestfinished` or `requestfailed`. Before each full navigation and at every clean boundary it drains both pending requests and response-body scans until the counts remain quiescent across an event-loop turn. Safe diagnostics contain only phase, counters, and allowlisted paths; UUIDs become `:taskId`, and query strings, headers, bodies, credentials, and database details are excluded.

No failure was suppressed: response-body read rejection remains fatal, `requestfailed` remains fatal except the existing one-shot explicitly matched query abort, HTTP and public-surface checks remain strict, and neither timeouts nor `networkidle` behavior changed. Synthetic probes prove that a request existing before a response blocks the drain and that a rejected `response.text()` increments `bodyReadFailed` and the failure count and is rejected.

The acceptance JVM also receives `--tensor.plugins.tushare-pro.min-request-interval=0ms`. This boundary applies only to the test-owned localhost stub, where all 375 deterministic fixture calls remain real HTTP calls. It does not change product configuration, source/request assertions, polling, or timeout behavior.

## Tooltip and geometry correction

The balance precision assertion now exercises the Element Plus automatic overflow-tooltip behavior through the owning table cell, consistent with Element Plus binding its mouse-enter handler to the `td`. This is a behavior correction; the evidence does not assign the earlier child-hover failure to one exact pointer-target mechanism. Concrete ancestor-clipping evidence and exact plain-text tooltip content remain required.

The stock-company introduction uses a separate explicit tooltip and is intentionally allowed to wrap. Its assertion now requires more than one `Range.getClientRects()` line and hovers the exact text target. The exact long value and the absence of `strong`, `em`, `script`, and `style` descendants remain checked.

The former 100% viewport assertion failed at ratio `0.999641478061676` because the 523-pixel-tall row ended at `1000.1875`. The final helper retains Playwright intersection visibility and compares `IntersectionObserver.boundingClientRect` with `intersectionRect`, allowing at most one CSS pixel of lost width or height. This preserves overflow-ancestor clipping coverage with a bounded subpixel tolerance.

A temporary preview probe at `/tmp/issue018-t12-tooltip-probe.mjs` passed the final contracts against `127.0.0.1:4173`:

- balance precision: `clippedByAncestor=true`; owning-cell hover produced the exact plain-text value;
- company introduction: three rendered lines; exact target-hover tooltip containing literal `<strong>` text and no markup;
- company cells: `business_scope` box `(325, 700.1875, 240, 86)`, `main_business` `(705, 700.1875, 240, 86)`, and `employees` `(565, 700.1875, 140, 86)`; each intersection rectangle exactly matched its bounding rectangle and the values were exactly empty string, `--`, and `0`;
- index snapshot: horizontal scroll changed from `0` to `284` with `scrollWidth=1440` and `clientWidth=1156`; fixed/non-fixed geometry and styles passed;
- unexpected fixture API requests: `0`.

## Verification evidence

### Dataset query

Final log: `/tmp/issue018-t12-query-final-5.log`, exit `0`, 11/11 passed in 7.1 minutes. Acceptance JAR SHA-256 was `31ade90bf11c948c712de657446f12c0adb819b8b27e96373cbadde986bc2ba8`; MySQL was 8.4.6.

- Download submissions: 375; strict upstream calls: 375; upstream failures: 0.
- Events: accepted 375, batch-finished 375, task-finished 375.
- Flyway rows: exact `1:1,2:1,3:1,4:1,5:1,6:1,7:1,8:1`; business tables: 52.
- Final rows: daily 126, stock company 1, index classify 1, balance sheet 1, disclosure date 123.
- Disclosure dates: 1 row on `2026-08-07`, 122 rows on `2026-08-08`.
- The exact 14 bilingual daily headers, 155-column balance table, exact decimals, long/plain text, stale-response release order, reset race, network failure recovery, and keyboard paging/focus all passed.
- Final cleanup evidence: owned JVM stopped and owned upstream stopped. The runner also reported its owned MySQL container and temporary secret files removed.

The successful process loaded the one-pixel viewport-bound version that preceded the final `IntersectionObserver` ancestor-clipping refinement. The refined final helper passed the short preview probe above and remains for the root-owned final G6 run.

Superseded evidence is preserved separately. `/tmp/issue018-t12-query-final-3.log` passed cases 1–6 and both tooltip sections before the 100% subpixel viewport failure. `/tmp/issue018-t12-query-final-4.log` was interrupted with exit 130 after the reviewer requested the explicit one-pixel contract; its runner reported owned container and temporary-secret cleanup. Neither is counted as final success.

### Download outcomes

Final log: `/tmp/issue018-t12-outcomes-fixed.log`, exit `0`, 15/15 passed in 3.0 minutes.

- Download posts: 14; queries: 17; upstream calls: 8.
- Events: accepted 14, batches 14, finished 14.
- Cleanup: cleanup trigger absent, JVM stopped, and stub stopped.

The upstream parameter assertion now checks a non-null, non-array object with exact sorted keys `trade_date` and `ts_code`, and exact values `20260807` and `000001.SZ`. This matches the Java `Map.equals` contract without depending on JSON property order. Task submission and persisted task detail continue to enforce the full exact parameter object.

### Lifecycle DOM additions

The reopened completed task now requires exactly three batch rows, exact ranges `2026-09-01` through `2026-09-03`, and exact `已成功` status in each row. Before retry, the failed middle batch requires exact range `2026-09-11 至 2026-09-11`, exact `失败`, `尝试次数 1`, `SOURCE_TIMEOUT`, and `Source request timed out`.

The first attempt (`/tmp/issue018-t12-lifecycle-dom.log`) exited 1 before lifecycle execution because `DOCKER_HOST` was absent and Testcontainers tried `/var/run/docker.sock`; it is retained as environment-failure evidence. It still completed the frontend 34 files/468 tests and production build.

The corrected run used the documented Colima endpoint and passed: `/tmp/issue018-t12-lifecycle-dom-final.log`, exit `0`, Maven build success, `DownloadTaskLifecycleIT` 5/5 with zero failures/errors/skips, frontend 34 files/468 tests, and production build. Its generated Playwright JSON reports contain exactly two successful flow tests (`liveDisconnectReloadAndReopen`, `livePartialFailureRetriesOnlyTheMiddleBatch`) and one successful resume test (`liveResumeAfterRestart`). Testcontainers used MySQL 8.4.6 and performed its owned lifecycle cleanup.

## Remaining root-owned verification

The root agent owns final G6 on the ultimate source tree, including the final `IntersectionObserver` helper, and the final ordinary 126-test browser suite. This report does not claim those pending root-owned results.
