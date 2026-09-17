# Data Integrity T09 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Track completion below.

**Goal:** Deliver the six integrity HTTP endpoints and exact public contracts.

**Architecture:** Thin controller delegates admission and all reads to existing integrity services. A local query parser enforces strict filters; response records expose only the specified fields and retain saved report JSON.

**Tech Stack:** Java 21, Spring MVC, Jackson, JUnit/MockMvc, MySQL 8.4.6/Testcontainers.

**Spec:** `docs/task-designs/DATA-INTEGRITY-T09-design.md` (complete, binding requirements).

## Global Constraints

- Work only in `.worktrees/data-integrity` on `feat/data-integrity`; preserve the existing staged Studio/T01–T08 baseline. Stage new files; do not commit the mixed baseline or merge it.
- Use minimal code. Do not change rules, SQL, migration, runner or download API behavior.
- Preserve the raw submission object and saved historical JSON; Long and BigDecimal remain strings, unknown remains null.
- Complete T09 only with passing HTTP tests, schema contracts, real application/MySQL evidence and full unit regression. The clean-main release gate remains T13.

### Task 1: HTTP controller, queries and public DTOs

**Files:** Create `web/IntegrityCheckController.java`, `web/integrity/IntegrityCheckQuery.java`, `web/dto/IntegrityCheckResponses.java`, and `web/IntegrityCheckControllerTest.java` in tensor-app. Modify IntegrityCheckService (thin report read entry points), ErrorCode, GlobalExceptionHandler and directly affected error enum tests. Preserve the existing no-controller-to-repository architecture gate.

**Interfaces:** Consume `IntegrityCheckService.capability/submit/tasks/progress/results/issues` (query methods delegate to the existing repository behind the architecture boundary), `IntegrityCheckJson.readValue`, and `DownloadTaskService.capabilities`. Produce the six routes and exact DTOs specified in sections 1–4 of the spec, including nested `Receipt`, `Capability`, `TaskSummary`, `Detail`, `Result`, `Issue`, `Page` records with static mapping methods.

- [x] Write the spec's first MockMvc RED using the existing controller surface (no integrity route), legal POST and stubbed service result; assert status 202, Location, requestId and unchanged request. Run and record the 404 failure.
- [x] Implement the minimal controller/response/query files. Strict JSON parsing catches parsing errors only. Reject unknown/duplicate query keys, validate UUID/identifier/date/enum/page values and preserve historical symbols.
- [x] Cover all seven groups in the spec's Tests section applicable to MockMvc; keep real serializer/filter/advice in the test. Confirm exact strings, nulls, saved report JSON and no current capability calls on history.
- [x] Add INTEGRITY_CHECK_NOT_FOUND and 404 mapping; run affected enum and global error tests.
- [x] Run `mvn -o -f data-plane/pom.xml '-Dtest=IntegrityCheckControllerTest,GlobalExceptionHandlerTest,DownloadTaskContractTest' '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' -Dsurefire.failIfNoSpecifiedTests=false test` and stage only owned paths.

### Task 2: Public schema and contract verification

**Files:** Create `docs/contracts/integrity-check.schema.json`, `integrity-check-examples.json`, `web/IntegrityCheckContractTest.java`; update OpenAPI and error-codes.md.

**Interfaces:** Consume the DTOs/paths from Task 1 and the exact fixed shapes in spec section 3. Independent JSON Schema and OpenAPI express the same property sets/enums/precision. Test both production outputs and hand-authored examples.

- [x] Add a failing contract test for the absent schema and routes, run it, then add schemas/examples.
- [x] Cover capability, first/replay receipt, ongoing detail, FAIL/UNKNOWN/N/A/incomplete result, null-date issue and empty page; reject unknown fields and invalid numeric precision.
- [x] Verify OpenAPI routes, parameters, responses and DTO parity with independent schema; preserve existing download definitions.
- [x] Run IntegrityCheckContractTest and DownloadTaskContractTest, then stage owned paths.

### Task 3: Real application verification and completion

**Files:** Modify `observability/ProductionApplicationContextIT.java`; create `docs/verification/DATA-INTEGRITY-T09.md`; update task board and prepare T10 design/handoff after T09 completion.

**Interfaces:** Use production Spring HTTP/runner/MySQL with the existing controlled three-market-data setup. Exercise capability -> submit -> replay/history -> detail/results/issues/date filters. Compare securities before/after.

- [x] Extend the real application test to call all six HTTP routes without a Tushare token, retaining real runner and MySQL.
- [x] Run `DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock mvn -o -f data-plane/pom.xml '-Dtest=ProductionApplicationContextIT' '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' -Dsurefire.failIfNoSpecifiedTests=false test`.
- [x] Run full `mvn -o -f data-plane/pom.xml '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' test`; inspect fresh reports for failures/errors/skips.
- [x] Review only the T09 diff against its starting index; fix material findings and rerun affected tests.
- [x] Record acceptance evidence and mark T09 COMPLETED; finish/link T10 design and handoff, prepare READY without implementing T10; stage exact changed paths.
