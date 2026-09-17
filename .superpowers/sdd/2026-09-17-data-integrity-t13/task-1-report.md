# T13 Task 1 report

## Outcome

Implemented the acceptance-only fixture integrity scenarios and versioned rule registration, then verified them through the real servlet application and MySQL 8.4.6.

- tensor.plugins.fixture.integrity-version defaults to 2; only 2 and 3 start.
- Coverage descriptor, rule, and evidence versions are consistently 2 or 3.
- Version 3 additionally registers fixture.acceptance.extension@1, a generic FIELD PASS rule with FIXTURE_EXTENSION_VERIFIED synthetic acceptance evidence.
- PROVEN_EXTRA over 2026-01-01..21 reports actual=20, expected=20, matched=19, missing=1, extra=1, coverage=0.950000, overall FAIL.
- PROVEN retains its Jan 1..20 reliable behavior; a stock with no rows produces 20 confirmed missing keys.
- PROVEN_EMPTY proves an empty set over Jan 1..21: expected=0, actual=0, coverageRate=null, VERIFIED_EMPTY.
- UNCONFIRMED retains unknown formal counts/rate and candidate-only issues.
- Requests outside each reliable window remain UNCONFIRMED.

## Files

- data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixtureConfiguration.java
- data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixturePlugin.java
- data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/integrity/FixtureIntegrityRules.java
- data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/integrity/FixtureIntegrityExtensionRule.java (new)
- data-plane/tensor-plugin-fixture/src/test/java/com/akkc/tensor/plugin/fixture/FixturePluginTest.java
- data-plane/tensor-plugin-fixture/src/test/java/com/akkc/tensor/plugin/fixture/integrity/FixtureIntegrityComparisonTest.java
- data-plane/tensor-plugin-fixture/src/test/java/com/akkc/tensor/plugin/fixture/integrity/IntegrityPluginContractTest.java
- data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture/IntegrityFixtureFlowIT.java (new)
- .superpowers/sdd/2026-09-17-data-integrity-t13/task-1-report.md (new)

Inherited staged changes, including FixtureFlowIT.java, were preserved. Per controller instruction, this agent did not run git add; the controller will stage the exact files after recording the initial index tree.

## RED

Command:

~~~sh
DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
mvn -o -f data-plane/pom.xml -Dskip.npm=true \
  -Dtest=IntegrityFixtureFlowIT \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false test
~~~

Observed before fixture implementation:

~~~text
Successfully validated 9 migrations
Successfully applied 9 migrations to schema tensor, now at version v9
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
expected: "FAIL"
 but was: "UNKNOWN"
IntegrityFixtureFlowIT.provenExtraReportsIndependentExpectedMatchedMissingAndExtraCounts:71
BUILD FAILURE
Total time: 16.562 s
~~~

This was the intended behavioral RED: a live HTTP request completed against real MySQL, but the old fixture treated PROVEN_EXTRA as unconfirmed and returned UNKNOWN/null.

## GREEN

Final combined command:

~~~sh
DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
mvn -o -f data-plane/pom.xml -Dskip.npm=true \
  '-Dtest=IntegrityReadRepositoryIT,IntegrityComparisonIT,IntegrityCheckRepositoryIT,IntegrityCheckServiceIT,IntegrityCheckRunnerIT,IntegrityCheckCoordinatorTest,FixturePluginTest,FixtureIntegrityComparisonTest,IntegrityPluginContractTest,IntegrityFixtureFlowIT' \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false test
~~~

Result:

~~~text
tensor-core: Tests run: 83, Failures: 0, Errors: 0, Skipped: 0
tensor-plugin-fixture: Tests run: 33, Failures: 0, Errors: 0, Skipped: 0
tensor-app: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
Total: 117 tests, 0 failures, 0 errors, 0 skipped
BUILD SUCCESS
Total time: 01:20 min
~~~

The core total is the six required suites (76 tests) plus the same-name core IntegrityPluginContractTest selected by the Maven pattern (7 tests):

- IntegrityReadRepositoryIT: 12
- IntegrityComparisonIT: 7
- IntegrityCheckRepositoryIT: 27
- IntegrityCheckServiceIT: 8
- IntegrityCheckRunnerIT: 17
- IntegrityCheckCoordinatorTest: 5
- core IntegrityPluginContractTest: 7

Fixture/app details:

- FixturePluginTest: 7
- FixtureIntegrityComparisonTest: 8
- fixture IntegrityPluginContractTest: 18
- IntegrityFixtureFlowIT: 1 (13.05 s)

Surefire summaries:

- data-plane/tensor-core/target/surefire-reports/com.akkc.tensor.core.integrity.*.txt
- data-plane/tensor-plugin-fixture/target/surefire-reports/com.akkc.tensor.plugin.fixture.FixturePluginTest.txt
- data-plane/tensor-plugin-fixture/target/surefire-reports/com.akkc.tensor.plugin.fixture.integrity.FixtureIntegrityComparisonTest.txt
- data-plane/tensor-plugin-fixture/target/surefire-reports/com.akkc.tensor.plugin.fixture.integrity.IntegrityPluginContractTest.txt
- data-plane/tensor-app/target/surefire-reports/com.akkc.tensor.fixture.IntegrityFixtureFlowIT.txt

The controller-supplied pre-task selected Java baseline was 52 passing tests. The final command independently exercised the broader design-mandated integrity suites.

## Real HTTP/MySQL evidence

IntegrityFixtureFlowIT owns one MySQL 8.4.6 container, applies and validates all 9 migrations, starts the full acceptance servlet application under version 2, closes it, then restarts version 3 against the same database.

It verifies:

- public capability GET without a token;
- POST 202 and Location, identical replay 200/same checkId, changed payload 409 SUBMISSION_CONFLICT;
- task list, detail, results and issues endpoints and their filters;
- completed task with data FAIL;
- saved scope, descriptor, versions, string counts, evidence, complete business keys, Jan 20 MISSING and Jan 21 EXTRA;
- PROVEN no-record stock with 20 confirmed missing issues;
- PROVEN_EMPTY and UNCONFIRMED semantics;
- independent recursive CTE: expected 20, matched 19, missing 1, coverage 0.950000; separate actual=20; missing Jan 20; extra Jan 21;
- explicit seven-column fixture rows sorted by key, row-for-row equality and SHA-256 equality before/after checks and restart;
- download task and batch counts remain zero;
- same-database 2 to 3 restart changes the capability hash;
- task A GET/results/issues bodies and persisted report JSON remain byte-for-byte unchanged;
- old submission replays task A after restart;
- old hash with a new submission returns 409 INTEGRITY_DEFINITION_CHANGED;
- refreshed hash creates task B with coverage@3 and extension@1;
- task A remains coverage@2 and contains no extension result.

## Concerns and boundaries

- Flyway logs its existing compatibility warning that MySQL 8.4 is newer than its latest tested 8.1; validation and all 9 migrations pass.
- Expected failure-path tests log coordinator/runner errors and invalid-version startup warnings; their summaries are green.
- -Dskip.npm=true avoided concurrent Maven frontend steps deleting the controller's node_modules; it skipped only frontend executions. The controller independently reported 740 frontend unit tests, build, and 39 stub browser tests passing.
- Full clean/acceptance packaging is left to the controller's serialized final gate.
- No production upstream baseline, core rule, HTTP special case, migration, or securities-table write path was added.

## Review fix round 1

The review findings were addressed only in `IntegrityFixtureFlowIT`. The final focused verification command was:

~~~sh
DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
mvn -o -f data-plane/pom.xml -Dskip.npm=true \
  -Dtest=IntegrityFixtureFlowIT \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false test
~~~

Result:

~~~text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 17.447 s
~~~

The passing test establishes the requested review evidence:

- An owned loopback HTTP server counts attempted Tushare requests while both application versions use a synthetic token; the counter remains exactly 0 after both the version 2 and version 3 application runs. Download task and batch tables are asserted separately to remain empty.
- Independent recursive SQL for PROVEN establishes 20 expected full `(ts_code, trade_date)` keys, 0 matched, 20 missing, and 0 actual rows. The ordered SQL missing keys equal all Jan 1..20 keys and exactly equal the ordered API issues; every API issue is MISSING/FAIL with the complete business key.
- Public page totals, progress/count fields, and non-null rates are asserted to be JSON strings before exact value comparison, including PROVEN coverage `"0.000000"`; required null cases remain JSON null.
- The saved report B coverage result descriptor, every saved coverage evidence item, the report descriptor coverage rule, and both persisted/API issue rows are asserted at rule version `3`.
- The PROVEN_EXTRA recursive expected, missing, and extra SQL uses and asserts both business-key fields. Generated CTE symbols explicitly use the fixture table's case-sensitive MySQL collation.

Surefire report: `data-plane/tensor-app/target/surefire-reports/com.akkc.tensor.fixture.IntegrityFixtureFlowIT.txt`.

## Review fix round 2

The follow-up correction separates the two upstream preconditions while retaining the same controlled loopback receiver and cumulative counter:

- Version 2 sets the application property `TENSOR_TUSHARE_TOKEN=` explicitly empty. Under that configuration it exercises the complete fixture scenario set and the capability, create, history, detail, results, and issues endpoint families. The receiver count is asserted as 0 after the version 2 phase.
- Version 3 sets `TENSOR_TUSHARE_TOKEN=integrity-fixture-token`, proving that the fixture integrity flow also makes zero upstream requests when a Tushare download client has a configured credential. The same receiver's cumulative count is asserted as 0 again after the version 3 phase.
- The version 2 no-token evidence comes from the explicit application property. The absence of an `Authorization` header on the IT's public API requests is not used as evidence about the upstream Tushare token.

Final focused verification command:

~~~sh
DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
mvn -o -f data-plane/pom.xml -Dskip.npm=true \
  -Dtest=IntegrityFixtureFlowIT \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false test
~~~

~~~text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 17.432 s
~~~

Surefire elapsed time for `IntegrityFixtureFlowIT`: 12.98 s.
