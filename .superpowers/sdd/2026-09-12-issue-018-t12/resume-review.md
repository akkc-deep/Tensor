# T12 resume review

Date: 2026-09-12. Scope: T12 design, verification document and pause handoff; `/tmp/issue018-t12-resume-review.diff` against HEAD `885d559b`, with surrounding source; followed by `/tmp/issue018-t12-resume-fixes.diff`, `/tmp/issue018-t12-resume-final-fixes.diff` and current query/outcomes/lifecycle code. Read-only review except this report; no tests, app starts, external calls, staging or subagents.

## Findings

### Resolved in code — live lifecycle assertions did not fully prove the specified batch presentation

`control-plane/e2e/download-task-lifecycle.spec.js:130` and `:153`.

The reconnect case asserts a successful task, three table rows and three committed SQL keys, but never asserts that the three displayed leaves are successful. The partial-failure case asserts the page's task-level status, then checks the middle batch's failure and attempt count using the API only. It does not assert that the live page displays the middle date interval, fixed `SOURCE_TIMEOUT` message and attempt count 1 before retry, as explicitly required by the lifecycle design. A broken/missing batch rendering can therefore pass these live scenarios even though the underlying database/API facts are correct.

Add narrow row-scoped DOM assertions for all three successful leaves after reconnect, and for the middle interval, fixed error and attempt before retry. Preserve the existing API/SQL/call/version assertions. This is an acceptance-test coverage gap, not a demonstrated production UI defect. Verify the changed spec through the real LifecycleIT (flow2/resume1), with existing report rejection and no retries.

Scoped update: the reconnect case now finds each exact one-day interval in a unique row and asserts its visible success status. Before retry the partial case finds the exact middle interval and asserts visible failed status, attempt 1, SOURCE_TIMEOUT and fixed message. Existing API/SQL/call/version assertions remain. The finding is resolved in code and affected real execution, as independently checked below.

No open critical, important or minor code finding identified after the scoped update.

## Spec verdict

**PASS for specification compliance and T12 runtime acceptance.** The lifecycle code gap is resolved and final G6 closes the remaining runtime evidence gate. Reviewed implementation follows the design: real Servlet/MySQL8.4.6 source, owned application/process lifecycle, same-database manual recovery, strict flow/resume report counts, independent-schema environment selection, suite separation, task receipt/result migration, CORS Location, exact V8/package inventories, publication prerequisites and runbook boundaries.

## Code-quality verdict

**PASS for reviewed code quality.** No concrete critical/important production-code defect found. The production changes are limited to Location exposure and restoring the runtime JSON-schema dependency; test-only source/control types remain excluded from packaged artifacts. No broad API allowance, new dependency for the environment helper, production RANGE opening or removal of ordinary test cases was found in the reviewed changes.

The scoped query change tracks API requests from their request event until requestfinished/requestfailed, drains those plus every unsettled response-body scan before the seed workflow's full navigations, and rechecks newly arrived work before returning. Existing intentional query-abort exemption remains one explicit expected request; unexpected request failures and body-read/safety errors still fail assertClean. New synthetic probes cover a request pending before its response and a rejected response body. Diagnostic output contains counts, fixed phases and allowed paths with task UUIDs removed.

The tooltip helper now distinguishes automatic clipped precision text (hover its owning cell) from the explicit wrapped longText tooltip (hover its text), matching DatasetTable's two existing mechanisms. Clipped text still requires measured overflow; wrapped text requires multiple rendered lines. Exact table values, high-precision decimal, complete plain-text tooltips and no-markup assertions remain. The empty/null/zero screenshot checks now use IntersectionObserver bounds, including ancestor clipping, and permit at most one CSS pixel lost per dimension plus one CSS pixel at the viewport edge. This replaces a fractional intersection ratio1 failure without allowing substantially clipped screenshot cells. No screenshot or tooltip check was removed.

The query-only acceptance-process argv sets min-request-interval=0ms for its enforced owned local upstream; production/default1500ms, real-account suite and 375-request/SQL/event contracts are unchanged. This is consistent with the recorded local-fixture ruling and leaves throttle semantics to the existing G1 coverage. Outcomes now rejects null/non-object/array params and requires exactly trade_date/ts_code with their exact string values; JSON object member order no longer causes a false failure.

## Runtime evidence

Root reports independently rechecked G1 82 classes/1282 tests with no failures/errors/skips, G2 468, G3 1025, G4 1028, G5 3, stable current JAR hashes, and synthetic preflight manifest40/rejections11. Those are existing executed evidence, not tests executed by this reviewer.

Root additionally reports final outcomes 15/15 in 3.0 minutes including the 120-second timeout, 14 task POSTs/17 queries/8 source requests and all cleanup checks true; ENUM target 2/2. Earlier query runs that failed old geometry/tooltip premises remain failed historical evidence.

Root reports query-final-5 passed all11 in7.1 minutes:375 task submissions/source calls,39 query responses,8 migrations/52 tables, rows126/1/1/1/123 with disclosure dates1+122, JVM/upstream cleanup true. Short probes also passed clipped precision, wrapped longText, scroll/sticky and empty/null/zero presentation with zero unexpected requests. The final IntersectionObserver refinement was made after the query process loaded its source, so that11-case run does not establish execution of that last refinement.

Reviewer read-only parsing of the existing `data-plane/tensor-app/target/surefire-reports/TEST-com.akkc.tensor.web.DownloadTaskLifecycleIT.xml` confirms the changed-spec LifecycleIT5/5, failures/errors/skips0. Existing `target/download-task-lifecycle-flow-f160f92e-fdf0-426c-ac4b-261f0c42c0aa.json` contains exactly the two expected flow names and `target/download-task-lifecycle-resume-79538138-84d1-49e4-b5c7-eaf42b6cab97.json` exactly the resume name; all passed with one result/attempt, retry0, and skipped/unexpected/flaky0. The target paths are relative to tensor-app. Root records `/tmp/issue018-t12-lifecycle-dom-final.log` exit0 and frontend468/build success. The preceding `/tmp/issue018-t12-lifecycle-dom.log` failed from missing DOCKER_HOST and remains failed environmental evidence.

Final G6 is now passed. Reviewer read-only parsing of `/tmp/issue018-t12-resume-gate6.log` confirms126 successful test lines and `126 passed (15.8m)`, exactly query11/outcomes15/metadata40/tasks3/fixture3/stock3/ui51, with no failed/skipped/did-not-run/retry lines. Root records exit0, final spec hashes unchanged, four newly created independent schemas, and complete owned-container/private-environment cleanup. Root also rechecked zero task-labelled containers/private environment files and stopped its identity-verified preview82694. No tests were run by this reviewer.

Reviewer independently parsed the final artifacts below under `/var/folders/s5/h3vynqy544lc7vwtz0zjy39m0000gn/T/`:

- `tensor-m14-t03-ohZM8T/evidence.json`:375 downloads/upstream calls,39 query responses, task events375 accepted/375 batch-finished/375 task-finished; initially empty MySQL8.4.6 schema,8 migrations/52 tables, final rows126/1/1/1/123 and disclosure dates1+122. JVM/upstream cleanup true. The final IntersectionObserver source actually executed: all three screenshot cells have523px height and522.8125px visible intersection, a0.1875px loss within the explicit1px bound.
- `tensor-m14-t02-OKPjdy/evidence.json`:14 task POSTs,17 queries,8 source calls, task events14/14/14, triggerAbsent/jvmStopped/stubStopped all true.
- `tensor-m14-t04-BTIkQE/metadata-evidence.json`:40 cases/API contracts/dataset contracts passed, both task and synchronous POSTs0, records GETs0, upstream calls0,11 screenshots, JVM/sentinel/private-log cleanup all true.

Also parsed `control-plane/node_modules/.cache/tensor-playwright/fixture-flow-fixture-page--ac687-tureOnBothPagesAfterRestart-chromium/flow-evidence.json`: saved fixture row contains the expected exact decimal/string/null values; disabled source inventory contains only Tushare with credentialConfigured/downloadAvailable false. The three successful fixture cases retain the empty-result/no-change and restart assertions previously reviewed.

Together with rechecked G1–G5 and the changed-spec LifecycleIT, this final G6 closes the T12 runtime-evidence gate. All review findings are closed; no additional code correction or runtime gate is requested by this review. Root may complete the T12 state/evidence bookkeeping under the task workflow. The full publication script remains explicitly unrun because main/clean-input/HEAD prerequisites are unmet; this is the design's recorded publication boundary, not an outstanding T12 test requirement. T13 has not started, production RANGE remains closed and this review does not authorize publication or real-account calls.

## Final review status

Specification PASS; code quality PASS; T12 runtime-evidence gate CLOSED. No open findings. Further code changes would need their affected review/verification; unchanged source needs no repeated static review or test run for this report.
