import {
  PENDING_SUBMISSION_KEY,
  createSubmissionRequest,
  readPendingSubmission,
  removePendingSubmission,
  writePendingSubmission,
} from './downloadTaskSubmission.js'

const submissionId = '33333333-3333-4333-8333-333333333333'

beforeEach(() => sessionStorage.clear())
afterEach(() => sessionStorage.clear())

it('creates a frozen parameter-whitelisted request and stores only its exact envelope', () => {
  const params = { ts_code: '000001.SZ', start_date: '20260901', secret: 'drop' }
  const request = createSubmissionRequest(
    { pluginId: 'contract_fixture', apiName: 'daily', mode: 'RANGE', params },
    ['ts_code', 'start_date'],
    submissionId,
  )

  expect(request).toEqual({
    submissionId,
    pluginId: 'contract_fixture',
    apiName: 'daily',
    mode: 'RANGE',
    params: { ts_code: '000001.SZ', start_date: '20260901' },
  })
  expect(Object.isFrozen(request)).toBe(true)
  expect(Object.isFrozen(request.params)).toBe(true)

  writePendingSubmission(request)
  expect(JSON.parse(sessionStorage.getItem(PENDING_SUBMISSION_KEY))).toEqual({ schemaVersion: 1, request })
  expect(readPendingSubmission()).toEqual(request)
  expect(Object.isFrozen(readPendingSubmission())).toBe(true)
  expect(Object.isFrozen(readPendingSubmission().params)).toBe(true)
})

it('rejects corrupt, unknown-version and oversized records without changing them', () => {
  const invalidRecords = [
    '{',
    JSON.stringify({ schemaVersion: 2, request: {} }),
    JSON.stringify({
      schemaVersion: 1,
      request: {
        submissionId,
        pluginId: 'contract_fixture',
        apiName: 'daily',
        mode: 'SINGLE',
        params: { trade_date: 'x'.repeat(8193) },
      },
    }),
  ]

  for (const record of invalidRecords) {
    sessionStorage.setItem(PENDING_SUBMISSION_KEY, record)
    expect(() => readPendingSubmission()).toThrow(TypeError)
    expect(sessionStorage.getItem(PENDING_SUBMISSION_KEY)).toBe(record)
  }
})

it('enforces the UTF-8 parameter limit before writing a snapshot', () => {
  expect(() =>
    createSubmissionRequest(
      { pluginId: 'contract_fixture', apiName: 'daily', mode: 'SINGLE', params: { note: '中'.repeat(4096) } },
      ['note'],
      submissionId,
    ),
  ).toThrow(TypeError)
  expect(sessionStorage.getItem(PENDING_SUBMISSION_KEY)).toBeNull()
})

it('accepts exactly 8192 UTF-8 parameter bytes and rejects the next byte', () => {
  expect(() =>
    createSubmissionRequest(
      { pluginId: 'contract_fixture', apiName: 'daily', mode: 'SINGLE', params: { note: 'x'.repeat(8181) } },
      ['note'],
      submissionId,
    ),
  ).not.toThrow()
  expect(() =>
    createSubmissionRequest(
      { pluginId: 'contract_fixture', apiName: 'daily', mode: 'SINGLE', params: { note: 'x'.repeat(8182) } },
      ['note'],
      submissionId,
    ),
  ).toThrow(TypeError)
})

it('removes only a snapshot with the same submission identity', () => {
  const first = createSubmissionRequest(
    { pluginId: 'contract_fixture', apiName: 'daily', mode: 'SINGLE', params: {} },
    [],
    submissionId,
  )
  const newer = createSubmissionRequest(
    { pluginId: 'contract_fixture', apiName: 'weekly', mode: 'SINGLE', params: {} },
    [],
    '44444444-4444-4444-8444-444444444444',
  )
  writePendingSubmission(newer)

  expect(removePendingSubmission(first.submissionId)).toBe(false)
  expect(readPendingSubmission()).toEqual(newer)
  expect(removePendingSubmission(newer.submissionId)).toBe(true)
  expect(sessionStorage.getItem(PENDING_SUBMISSION_KEY)).toBeNull()
})

it('rejects array identities and array parameter maps instead of coercing them', () => {
  sessionStorage.setItem(
    PENDING_SUBMISSION_KEY,
    JSON.stringify({
      schemaVersion: 1,
      request: {
        submissionId: [submissionId],
        pluginId: ['contract_fixture'],
        apiName: ['daily'],
        mode: 'SINGLE',
        params: {},
      },
    }),
  )
  expect(() => readPendingSubmission()).toThrow(TypeError)
  expect(() =>
    createSubmissionRequest(
      { pluginId: 'contract_fixture', apiName: 'daily', mode: 'SINGLE', params: [] },
      [],
      submissionId,
    ),
  ).toThrow(TypeError)
})
