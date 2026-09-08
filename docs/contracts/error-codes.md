# Tensor v1 error codes

All error responses use `ApiError`. The `requestId` body value equals the `X-Request-Id` response header and any nested `downloadResult.requestId`. `fieldErrors` is always present (an empty array when no field is involved). Messages are short, actionable summaries and never include Token, Authorization, upstream raw responses, stack traces, credentials, or database diagnostics. `retryable` only advises a later **manual** attempt after the condition is addressed; it never triggers automatic replay.

The range download additions below are the target contract of [RANGE-T03](../task-designs/RANGE-T03-design.md); running endpoints have not yet migrated. Existing non-download HTTP mappings are retained. The HTTP column classifies an error; download execution uses the stage rules below to choose between an `ApiError` and a recorded unit failure in a normal result.

| Code | HTTP | Retryable | Meaning |
|---|---:|---|---|
| `PARAM_REQUIRED` | 400 | `false` | A required input is missing; provide the named field and submit again. |
| `PARAM_INVALID` | 400 | `false` | An input has an invalid format, value, enum, or range; correct the field error and retry. |
| `PLUGIN_DISABLED` | 409 | `false` | The original data source is disabled; check its configuration or contact a maintainer. A saved task must keep its original plugin and scope. |
| `DATASET_MISCONFIGURED` | 409 | `false` | The registered dataset metadata is incomplete or inconsistent; contact a maintainer. |
| `SOURCE_AUTH_FAILED` | 502 | `false` | The upstream source rejected its configured credentials; contact a maintainer. |
| `SOURCE_PERMISSION_DENIED` | 502 | `false` | The upstream account lacks permission for this API; contact a maintainer. |
| `SOURCE_RATE_LIMITED` | 502 | `true` | The upstream source limited the request; wait and retry. |
| `SOURCE_UNAVAILABLE` | 502 | `true` | The upstream source is temporarily unavailable; retry later. |
| `SOURCE_NETWORK_ERROR` | 502 | `true` | The upstream source could not be reached; check connectivity and retry. |
| `SOURCE_TIMEOUT` | 504 | `true` | The upstream source timed out; retry later. |
| `SOURCE_PAYLOAD_INVALID` | 502 | `true` | The upstream source returned an unusable payload; retry later or contact a maintainer. |
| `ADAPTER_FIELD_MISSING` | 422 | `false` | A required source field is missing or cannot be mapped; contact a maintainer. |
| `ADAPTER_TYPE_INVALID` | 422 | `false` | A mapped source value violates a declared type constraint; contact a maintainer. |
| `PERSISTENCE_FAILED` | 500 | `true` | The current recovery unit transaction is confirmed failed or rolled back; previously committed units remain. If the database is healthy and rollback is confirmed, save this failure and continue. Otherwise stop and return any confirmed subtotals. Retry manually after recovery. |
| `QUERY_FAILED` | 500 | `true` | The persisted data could not be queried; retry later. |
| `INTERNAL_ERROR` | 500 | `false` | An unexpected server error occurred; retry only if advised by support. |
| `SOURCE_REQUEST_UNCONFIRMED` | 409 | `false` | The original API's legal request conditions or date semantics are unconfirmed. Make zero business requests and create no first-download task. Ask a maintainer to establish the original API evidence; do not switch to a VIP API or invent a required stock input. |
| `CALENDAR_UNCONFIRMED` | 502 | `true` | Any necessary calendar's applicability, date coverage, or freshness is unconfirmed. Make zero business requests; create no first-download task and retain existing retry items. Check the source and retry manually. |
| `SOURCE_TRUNCATED` | 502 | `false` | Source truncation is evidenced; no part of the current batch response may be committed. Establish a legal way to obtain the complete response. |
| `SOURCE_COMPLETENESS_UNCONFIRMED` | 502 | `false` | The current response cannot be proved complete. A short response does not prove completeness; ask a maintainer to establish the completion criterion. |
| `DATA_CONFLICT` | 422 | `false` | The same business key has different content within this unit or against data committed earlier in this execution. Fail only the current complete recovery unit; previously committed units remain. |
| `RETRY_TASK_NOT_FOUND` | 404 | `false` | The current failed task does not exist. Refresh the actual list; absence does not prove an unacknowledged download succeeded. |
| `DOWNLOAD_BUSY` | 409 | `true` | The single-instance execution slot is occupied. Wait until actual execution ends, then retry manually; do not queue or release the existing execution. |
| `RETRY_TASK_INVALID` | 409 | `false` | Saved public conditions, target, or time cannot be parsed or are incompatible with the current contract. Preserve the record and contact a maintainer; do not edit its source or expand its scope. |
| `TASK_RECORD_SAVE_UNCONFIRMED` | 500 | `false` | Creation, append, or reason update of an explicit failure is not confirmed saved. This code also covers a confirmed record rollback, with a message stating that it was not saved. Stop; inspect actual records without promising recovery of missing items. |
| `COMMIT_UNCONFIRMED` | 500 | `false` | Commit of business writes or successful retry-item deletion is unknown. Stop and report the unconfirmed scope; do not invent a failure or automatically replay it. |

## Download stage rules

- **Before business execution:** invalid input, unavailable plugin or credentials, unconfirmed legal source request, or any unconfirmed necessary calendar returns a non-200 `ApiError`. No first-download failure task is created; existing retry records remain. Omit `downloadResult` when business execution has not started. Calendar reads do not count as business downloads. A legal target request with missing source evidence is `SOURCE_REQUEST_UNCONFIRMED`, not a misspelled parameter.
- **During business execution:** source authentication, permission, rate limit, availability, network, timeout, payload, truncation, and completeness failures, plus adaptation and data conflicts, become explicit recovery failures. After reliable persistence of their actual scope, continue every subsequent planned request or unit; do not retry a failed request automatically. Normal completion returns HTTP 200 `PARTIAL` or `FAILED` with `failures`, confirmed saved `taskId`, and `remainingFailedUnits > 0`. The 422/502/504 classification above does not terminate the download loop. Never commit partial pages or guess failed stocks from an incomplete response.
- **Storage failure or unknown commit:** stop and return non-200 `ApiError` with the applicable `downloadResult`. `TASK_RECORD_SAVE_UNCONFIRMED` retains known failures in `failedUnits`/`failures`, but does not claim they were saved. `COMMIT_UNCONFIRMED` puts the affected scope in `unconfirmedScopes` and increments neither completed nor failed units. Both use `outcome=UNCONFIRMED`; R/I/U retain only earlier confirmed completed-unit subtotals. If the database failure was confirmed rolled back and its failure record confirmed saved, an error may instead carry `PARTIAL`/`FAILED` subtotals and actual `notStartedScopes`; this is not a normal 200 result.

`notStartedUnits` is null when the remaining unit set is unknown; never derive it from the length of range arrays or write unknown as zero. Unstarted and unconfirmed scopes are response-only and are not new failure records. `taskId` is exposed only when an existing saved task with remaining failures is confirmed; a generated UUID is not proof. `remainingFailedUnits` is null if storage or commit uncertainty prevents confirmation. `failureRecordStatus` distinguishes `NOT_REQUIRED`, `CONFIRMED`, and `UNCONFIRMED` independently from business failure facts. Already committed units are not undone by an error response.

For retry execution, validate input first (400), then acquire the slot (409 when busy), then reread the actual task (404 when absent). The execute body must be zero bytes: `{}`, `null`, and any other content are `PARAM_INVALID`. Read-only task GETs do not occupy the slot or contact calendar/business sources; query failures use `QUERY_FAILED`. An offline plugin does not hide saved records. Loss of the HTTP response does not cancel execution or release the slot, and request identifiers do not provide deduplication.

## Parameter migration

For all 38 range targets, require string `start_date` and `end_date` in `YYYYMMDD`, strict Gregorian dates, ordered and inclusive, at most 31 natural days before calendar filtering or month expansion. Missing required fields are `PARAM_REQUIRED`; unknown fields, old `trade_date`/`ann_date`/`month` (alone or mixed with ranges), invalid dates, reversed/overlong ranges, invalid enums, multiple stock codes, and non-string values are `PARAM_INVALID`. A single supplied endpoint still lacks the other required endpoint; do not supply defaults. Preserve original required stock/exchange conditions. The 11 original-parameter APIs reject added date conditions.

See the [OpenAPI contract](openapi-v1.yaml), [range traceability](../traceability/tensor-range-requirements.md), and [T01](../research/RANGE-T01-source-capabilities.md)/[T02](../research/RANGE-T02-calendar-capabilities.md) evidence. Contract examples and controlled validation do not confirm upstream capability or close ISSUE-008.
