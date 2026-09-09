import rangeResults from '../test/fixtures/range-results.json'

import { ApiError, ClientError, normalizeError } from './errors.js'

const API_RULES = {
  PARAM_REQUIRED: [400, false],
  PARAM_INVALID: [400, false],
  PLUGIN_DISABLED: [409, false],
  DATASET_MISCONFIGURED: [409, false],
  SOURCE_AUTH_FAILED: [502, false],
  SOURCE_PERMISSION_DENIED: [502, false],
  SOURCE_RATE_LIMITED: [502, true],
  SOURCE_UNAVAILABLE: [502, true],
  SOURCE_NETWORK_ERROR: [502, true],
  SOURCE_TIMEOUT: [504, true],
  SOURCE_PAYLOAD_INVALID: [502, true],
  ADAPTER_FIELD_MISSING: [422, false],
  ADAPTER_TYPE_INVALID: [422, false],
  PERSISTENCE_FAILED: [500, true],
  QUERY_FAILED: [500, true],
  INTERNAL_ERROR: [500, false],
  SOURCE_REQUEST_UNCONFIRMED: [409, false],
  CALENDAR_UNCONFIRMED: [502, true],
  SOURCE_TRUNCATED: [502, false],
  SOURCE_COMPLETENESS_UNCONFIRMED: [502, false],
  DATA_CONFLICT: [422, false],
  RETRY_TASK_NOT_FOUND: [404, false],
  DOWNLOAD_BUSY: [409, true],
  RETRY_TASK_INVALID: [409, false],
  TASK_RECORD_SAVE_UNCONFIRMED: [500, false],
  COMMIT_UNCONFIRMED: [500, false],
}

function responseError(body, status, responseRequestId = body.requestId, outgoingRequestId = body.requestId) {
  return {
    config: { headers: { 'X-Request-Id': outgoingRequestId } },
    response: {
      data: body,
      status,
      headers: { 'X-Request-Id': responseRequestId },
    },
  }
}

function body(code, status, retryable, downloadResult) {
  const requestId = `request-${code.toLowerCase()}`
  return {
    requestId,
    code,
    message: `safe ${status}`,
    retryable,
    fieldErrors: [],
    ...(downloadResult === undefined ? {} : { downloadResult }),
  }
}

describe('normalizeError response boundary', () => {
  it('accepts exactly the 26 catalogue codes with their status and retryability', () => {
    for (const [code, [status, retryable]] of Object.entries(API_RULES)) {
      const envelope = body(code, status, retryable)
      const error = normalizeError(responseError(envelope, status))
      expect(error).toBeInstanceOf(ApiError)
      expect(error).toMatchObject({
        requestId: envelope.requestId,
        code,
        retryable,
        downloadResult: null,
      })
    }
  })

  it('accepts an unconfirmed snapshot only for the four execution error codes', () => {
    const allowed = [
      ['TASK_RECORD_SAVE_UNCONFIRMED', false],
      ['COMMIT_UNCONFIRMED', false],
      ['PERSISTENCE_FAILED', true],
      ['INTERNAL_ERROR', false],
    ]

    for (const [code, retryable] of allowed) {
      const requestId = `request-${code.toLowerCase()}`
      const snapshot = { ...structuredClone(rangeResults.UNCONFIRMED), requestId }
      const error = normalizeError(responseError(
        body(code, 500, retryable, snapshot),
        500,
      ))
      expect(error).toBeInstanceOf(ApiError)
      expect(error.downloadResult).toMatchObject({
        requestId,
        outcome: 'UNCONFIRMED',
        notStartedUnits: null,
        remainingFailedUnits: null,
      })
    }
  })

  it('rejects null, malformed, mismatched and unauthorized snapshots', () => {
    const requestId = 'request-snapshot'
    const snapshot = { ...structuredClone(rangeResults.UNCONFIRMED), requestId }
    const invalid = [
      body('COMMIT_UNCONFIRMED', 500, false, null),
      body('COMMIT_UNCONFIRMED', 500, false, { ...snapshot, outcome: 'UNKNOWN' }),
      body('COMMIT_UNCONFIRMED', 500, false, { ...snapshot, requestId: 'nested-mismatch' }),
      body('SOURCE_TIMEOUT', 504, true, snapshot),
      { ...body('COMMIT_UNCONFIRMED', 500, false, snapshot), extra: true },
    ]

    for (const envelope of invalid) {
      envelope.requestId = requestId
      const error = normalizeError(responseError(envelope, API_RULES[envelope.code][0], requestId, requestId))
      expect(error).toBeInstanceOf(ClientError)
      expect(error).toMatchObject({ kind: 'INVALID_RESPONSE', requestId })
      expect(error.downloadResult).toBeUndefined()
    }
  })

  it('rejects a valid nested snapshot when the response header does not match it', () => {
    const requestId = 'request-header'
    const snapshot = { ...structuredClone(rangeResults.UNCONFIRMED), requestId }
    const envelope = { ...body('COMMIT_UNCONFIRMED', 500, false, snapshot), requestId }
    const error = normalizeError(responseError(envelope, 500, 'other-header', requestId))

    expect(error).toBeInstanceOf(ClientError)
    expect(error).toMatchObject({ kind: 'INVALID_RESPONSE', requestId })
  })
})
