# T13 Task 1 review

Verdict: **CHANGES REQUESTED** — 0 critical, 2 important, 2 minor.

Scope reviewed: `task-1-brief.md`, T13 design sections 1–3, `task-1.diff`, current Task1 source, and `task-1-report.md`. This was a read-only review; no tests were rerun. The supplied Task1 evidence reports 117 selected tests green (core 83, fixture 33, app IT 1; 0 failures/errors/skips), including the intended real-MySQL behavioral RED. The controller additionally reports the broader 441-test integrity/Flyway group green with 0 failures/errors/skips. Those results do not close the specification gaps below.

## Findings

### Important — “zero upstream calls” is not measured

`data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture/IntegrityFixtureFlowIT.java:56`, `:120`, and `:175` call `assertNoUpstreamDownloads`, but its implementation at `:345-347` only counts rows in `tensor_download_task` and `tensor_download_batch`. A direct JVM source-client/network call can occur without creating either row, so this does not prove the brief's “上游计数0” requirement. The T13 design explicitly requires a controlled upstream receiver for the real Java HTTP test and says not to infer JVM network silence from other evidence. The report accurately calls these “download task and batch counts” at `task-1-report.md:124`, but Task1 remains incomplete against its brief.

Remedy: start a loopback controlled HTTP receiver owned by this IT (for example `HttpServer` plus `AtomicInteger`), point the configured upstream base URL at it with a synthetic credential, keep the fixture integrity request unchanged, and assert the receiver count is exactly zero after the complete v2 checks and again after the v3 restart/check. Keep the download-table assertions as a separate invariant and rename them so they are not presented as network evidence.

### Important — the no-record stock is not independently recomputed or shown as the exact 20 missing keys

The PROVEN path at `IntegrityFixtureFlowIT.java:112-113` reaches `assertScenario`, but `:251-255` only checks the issues page total and the report's `missingCount`. The only recursive SQL at `:267-305` is for PROVEN_EXTRA. Neither the real IT nor `FixtureIntegrityComparisonTest.java:80-84` enumerates Jan 1..20 and checks every PROVEN issue's date/type/full business key. This falls short of design section 2 (“列出20个缺失日”) and section 3 (“本地PROVEN无行同样计算20个缺失键”); report-derived count and issue total are not the required independent computation.

Remedy: add a PROVEN recursive-CTE query that selects the full key (`ts_code`, `trade_date`) for Jan 1..20, asserts actual/matched=0 and all 20 exact missing keys, then fetch the PROVEN issue page and assert the ordered set is exactly Jan 1..20 with `MISSING`/`FAIL` and `{ts_code: PROVEN, trade_date: ...}`. For the existing PROVEN_EXTRA missing/extra SQL, return/assert both key fields as well, rather than only `trade_date` with the symbol implied by a literal predicate.

### Minor — `asText()` does not prove Long/count fields are JSON strings

The IT claims string counts in `task-1-report.md:119`, but assertions such as `IntegrityFixtureFlowIT.java:81`, `:89`, `:96`, `:206-211`, and `:248-263` call `asText()` directly. Jackson returns the same text for numeric JSON nodes, so a regression from quoted decimal strings to JavaScript-unsafe JSON numbers would still pass. Null handling is asserted correctly with `isNull()`.

Remedy: assert `isTextual()` before each public count/rate equality that this IT uses as contract evidence (task progress, page totals, statistics and coverage rate). Retain the existing exact string comparisons.

### Minor — report B does not directly prove coverage rule/evidence version 3

`IntegrityFixtureFlowIT.java:131-133` proves the live capability descriptor advertises coverage@3, and `:160-169` proves report B has capability version 3 plus extension@1. It does not inspect report B's saved coverage `ruleResults` descriptor or its coverage evidence version. A persistence/execution regression that saved coverage@2 while capability@3 could pass this IT, despite the design and `task-1-report.md:8,129` claiming descriptor/rule/evidence consistency and B coverage@3.

Remedy: locate `fixture.coverage.fixture_daily` in report B and assert its descriptor version is `3` and every corresponding evidence `ruleVersion` is `3`; also assert the saved report descriptor's coverage rule is version `3`. Retain the existing A@2 and no-extension checks; checking B issue `ruleVersion=3` would further close the real HTTP path.

## What is sound

- `FixtureIntegrityRules.java:40-53` implements the requested PROVEN, PROVEN_EXTRA, PROVEN_EMPTY and UNCONFIRMED windows without widening PROVEN; out-of-window requests remain UNCONFIRMED.
- `FixtureIntegrityRules.java:21-30` keeps descriptor and coverage-rule versions aligned at 2/3; `FixtureIntegrityExtensionRule.java:16-31` is the requested independent FIELD@1 synthetic PASS rule.
- `FixtureConfiguration.java` defaults the acceptance-only property to 2 and the constructor rejects values outside 2/3; the old two-argument constructor delegates to 2.
- The Task1 diff contains only fixture production files, fixture tests, and the new app IT. No core, HTTP-controller or frontend rule/source branch was added.
- The real IT uses MySQL 8.4.6, all six public endpoint families, independent PROVEN_EXTRA CTE counts, seven-column fixture snapshots, application restart on the same database, byte-stable A responses/report JSON, replay/conflict/stale-hash behavior, and a distinct B report with extension@1.

No commit or baseline mutation was performed by this review.
