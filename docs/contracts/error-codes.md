# Tensor v1 error codes

All error responses use `ApiError`. The `requestId` body value equals the `X-Request-Id` response header. Messages are short, actionable summaries and never include upstream raw responses or internal diagnostics.

| Code | HTTP | Retryable | Meaning |
|---|---:|---|---|
| `PARAM_REQUIRED` | 400 | `false` | A required input is missing; provide the named field and submit again. |
| `PARAM_INVALID` | 400 | `false` | An input has an invalid format, value, enum, or range; correct the field error and retry. |
| `PLUGIN_DISABLED` | 409 | `false` | The selected data source is disabled; select an available source or contact a maintainer. |
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
| `PERSISTENCE_FAILED` | 500 | `true` | Persistence failed or its commit receipt was lost. The database may already have committed; rollback and absence of a task or write are not guaranteed. Query by taskId/submissionId before deciding whether to retry. |
| `QUERY_FAILED` | 500 | `true` | The persisted data could not be queried; retry later. |
| `INTERNAL_ERROR` | 500 | `false` | An unexpected server error occurred; retry only if advised by support. |

## Download task and batch errors

Task and batch errors are stored with task results; a successful query of those results returns HTTP 200, including stored `SOURCE_TIMEOUT`, `PERSISTENCE_FAILED` and `EXECUTION_INTERRUPTED`. GET returns 5xx only when the query itself fails; it does not convert the stored failure into a new HTTP error. The statuses below are fallbacks if a batch error reaches the HTTP exception boundary. `retryable` is an ErrorCode classification property, while `canRetry` / `canResume` are current hints derived from state and lifecycle. A later control request still revalidates its exact client version, active lease, source readiness, definition and capacity.

| Code | HTTP | Retryable | Meaning |
|---|---:|---|---|
| `TASK_NOT_FOUND` | 404 | `false` | Download task was not found. |
| `SUBMISSION_CONFLICT` | 409 | `false` | Submission ID belongs to a different request. |
| `TASK_STATE_CONFLICT` | 409 | `false` | Download task state has changed. |
| `TASK_DEFINITION_CHANGED` | 409 | `false` | Download task definition has changed. |
| `BATCH_DOWNLOAD_UNAVAILABLE` | 409 | `false` | Batch download is unavailable. |
| `TASK_QUEUE_FULL` | 429 | `true` | Download task queue is full. |
| `BATCH_COMPLETENESS_UNCONFIRMED` | 409 | `false` | Batch completeness is unconfirmed. |
| `SOURCE_RANGE_MISMATCH` | 502 | `false` | Source data is outside the requested range. |
| `TASK_LIMIT_EXCEEDED` | 409 | `false` | Download task limit exceeded. |
| `EXECUTION_INTERRUPTED` | 409 | `false` | Download task execution was interrupted. |

The task routes use the same `ApiErrorResponse` fields: `requestId`, `code`, `message`, `retryable`, and `fieldErrors`. Messages and field names are fixed safe summaries; no input values, raw upstream error, stack trace or credentials are returned. Stored errors expose only `code` and their fixed classification `message`.

`POST /api/v1/download-tasks` returns 202 after a new task's acceptance commits. It does not wait for downloading and does not claim download success. An equivalent submissionId replay returns 200 for the original task, with its original createdAt and current stored version/status. Both responses include the task Location. A receipt may already be stale when it reaches the client; GET is the current persisted result.

If a submission or retry/resume response is lost or reports `PERSISTENCE_FAILED`, first query the original taskId, or filter tasks by the original submissionId. Never blindly generate a new submissionId, assume no write committed, or requeue using a freshly substituted version. Replaying the same equivalent submissionId can find the original task even when new admission is disabled; retry/resume are explicit state/version operations and have no automatic replay guarantee. Database records remain authoritative: afterCommit logs are best-effort observations and may be absent after a commit or process failure.

Requests reject unknown/repeated query parameters and duplicate JSON members at runtime. Structural errors are 400; invalid present top-level fields take precedence over missing required ones. A syntactically valid request that changes a previously used submissionId is 409 `SUBMISSION_CONFLICT`. A well-formed unknown taskId is 404; an unknown submissionId filter is an empty 200 page.

Task versions, request counts, batch counts, row counts, page totals and nullable completeness `rowLimit` are JSON integer numbers bounded by signed int64 (maximum `9223372036854775807`). JavaScript Number is exact only through `9007199254740991`; clients must parse larger values losslessly, display their exact decimal digits, and preserve the exact version when sending `expectedVersion` as an integer token. Do not round or truncate. This contract does not change historical securities LONG/DECIMAL string serialization.

## Local integrity HTTP errors

The six local integrity endpoints use the same safe `ApiError` and request ID contract. A successful query returns 200 even when data is UNKNOWN/FAIL or execution is FAILED/INTERRUPTED. GET errors describe failure to query, not a stored data conclusion.

| Code | HTTP | Retryable | Meaning |
|---|---:|---|---|
| `INTEGRITY_CHECK_NOT_FOUND` | 404 | `false` | Integrity check was not found. Results and issues also validate their parent check; a nonmatching resultId filter returns an empty page. |
| `INTEGRITY_UNAVAILABLE` | 409 | `false` | Local integrity checking is unavailable for the plugin. |
| `INTEGRITY_DEFINITION_CHANGED` | 409 | `false` | Refresh the capability snapshot before creating a new check. |
| `INTEGRITY_QUEUE_FULL` | 429 | `true` | No queue slot is available; this attempt did not create a check. |
| `INTEGRITY_LIMIT_EXCEEDED` | 400 | `false` | The requested symbols, inclusive date span, or planned units exceed the configured limit. |

First admission returns 202; an identical original submission returns 200 with the same checkId and Location. Replay occurs before checking the current capability hash; a changed payload with the same submissionId returns 409/SUBMISSION_CONFLICT. On an uncertain response, query history by submissionId or replay the unchanged request with the same ID. Never assume a persistence error proves no commit.

All six endpoints reject unknown/repeated query parameters; submission JSON rejects duplicate members, trailing content and unknown fields. Invalid query values use PARAM_INVALID; missing required submission fields use PARAM_REQUIRED. Lists accept page >= 1 and any pageSize from 1 to 100 (defaults 1/20). Historical symbols are exact filters and do not require current plugin availability. Issue dates filter the saved issue date; filtering excludes undated issues.

Integrity counts, page totals, issueId and long-valued limits are decimal strings. Unknown statistics stay null; a computed zero stays "0". coverageRate is null or a six-place decimal string from 0.000000 to 1.000000, rounded HALF_UP for display only. plannedUnits, page/pageSize and int-valued limits remain JSON integers. Download availability reuses the existing download contract (including integer rowLimit). See `integrity-check.schema.json` and `integrity-check-examples.json`; saved reports do not depend on current rules or credentials.
