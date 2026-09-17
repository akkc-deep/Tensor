# Task1 independent review

Spec: FAIL. Quality: Needs fixes. No Critical findings.

## Important

1. IntegrityView.vue:66,112: after successful copy, explicit different source selection clears copiedDetail, but copyBlocked requires it while fromCheckId remains. Required unavailable-source replacement becomes permanently blocked. Distinguish superseded copy intent and test replacement source confirmation/preparation.
2. IntegrityView.vue:66,133,273: rejected pending disables copyBlocked and masks copy loading/error. After clicking read referenced report, old pending scope can remain confirmable during GET or after failure, and requestId/error is hidden. Copy attempt must have visible blocking state and no wrong-scope fallback.
3. IntegrityIssuesTable.vue:52: date-exclusion warning follows draft values, making retained page misleading while typing or clearing without applying. Drive warning from applied criteria; test both directions.

## Minor

4. IntegritySummary.vue:27: queued state lacks required “进行中，已有结论” note; both QUEUED and RUNNING must show it.

## Confirmed strengths

Exact BigInt/null/rate formatting; issue generation/abort/same-query snapshot lifecycle; saved report/rule evidence independent of current capability; valid deep-link and focus behavior; removed APIs preserved and blocked by unchanged form validator.

## External checks resolved by controller

T10 polling/reconnect/error-stop is covered by unchanged useIntegrityCheck specs included in final735unit tests and browser test at integrity-checks.spec.js:401. Final36Chromium tests and actual screenshot inspection cover1440/1024/390 document geometry. No reruns requested from reviewer.
