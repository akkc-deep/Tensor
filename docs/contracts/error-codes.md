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
| `PERSISTENCE_FAILED` | 500 | `true` | The persistence transaction failed and was rolled back; no committed download was formed. Retry later. |
| `QUERY_FAILED` | 500 | `true` | The persisted data could not be queried; retry later. |
| `INTERNAL_ERROR` | 500 | `false` | An unexpected server error occurred; retry only if advised by support. |
