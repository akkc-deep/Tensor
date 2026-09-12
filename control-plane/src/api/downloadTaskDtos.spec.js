import { ClientError } from './errors.js'
import {
  parseDownloadBatch,
  parseDownloadBatchPage,
  parseDownloadCapabilities,
  parseDownloadTask,
  parseDownloadTaskPage,
  parseDownloadTaskReceipt,
  parseTaskJson,
} from './downloadTaskDtos.js'

const REQUEST_ID = '11111111-1111-4111-8111-111111111111'
const TASK_ID = '22222222-2222-4222-8222-222222222222'
const SUBMISSION_ID = '33333333-3333-4333-8333-333333333333'
const BATCH_ID = '44444444-4444-4444-8444-444444444444'

function batch(overrides = {}) {
  return {
    batchId: BATCH_ID,
    parentBatchId: null,
    batchKey: '000001/0',
    rangeStart: '2026-09-12',
    rangeEnd: '2026-09-13',
    sourceParams: { start_date: '20260912', end_date: '20260913' },
    status: 'SUCCEEDED',
    attemptCount: 1,
    sourceRows: 9007199254740993n,
    insertedRows: 9223372036854775807n,
    updatedRows: 0,
    error: null,
    startedAt: '2026-09-12T00:00:00Z',
    finishedAt: '2026-09-12T00:00:01Z',
    createdAt: '2026-09-12T00:00:00Z',
    updatedAt: '2026-09-12T00:00:01Z',
    ...overrides,
  }
}

function receipt() {
  return {
    requestId: REQUEST_ID,
    taskId: TASK_ID,
    status: 'QUEUED',
    version: 1,
    createdAt: '2026-09-12T00:00:00Z',
  }
}

function task(overrides = {}) {
  return {
    taskId: TASK_ID,
    submissionId: SUBMISSION_ID,
    pluginId: 'contract_fixture',
    apiName: 'daily',
    mode: 'RANGE',
    params: {
      ts_code: '000001.SZ',
      start_date: '20260803',
      end_date: '20260805',
    },
    status: 'RUNNING',
    version: 3,
    planReady: true,
    counts: {
      totalBatches: 3,
      pendingBatches: 2,
      runningBatches: 1,
      succeededBatches: 0,
      failedBatches: 0,
      splitBatches: 0,
      sourceRows: 0,
      insertedRows: 0,
      updatedRows: 0,
    },
    lastError: null,
    canRetry: false,
    canResume: false,
    requestCount: 1,
    runRequestCount: 1,
    createdAt: '2026-09-12T00:00:00Z',
    updatedAt: '2026-09-12T00:00:01Z',
    queuedAt: '2026-09-12T00:00:00Z',
    startedAt: '2026-09-12T00:00:01Z',
    finishedAt: null,
    deadlineAt: '2026-09-12T00:30:01Z',
    ...overrides,
  }
}

function availableCapabilities() {
  return {
    single: {
      available: true,
      parameters: [
        { name: 'ts_code', label: 'Stock code', type: 'TS_CODE', required: true },
        { name: 'trade_date', label: 'Trade date', type: 'DATE', required: true },
      ],
    },
    range: {
      availability: 'AVAILABLE',
      unavailableReason: null,
      dateAxis: 'TRADE_DATE',
      dateLabel: 'Trade date',
      startParameter: 'start_date',
      endParameter: 'end_date',
      parameters: [
        { name: 'ts_code', label: 'Stock code', type: 'TS_CODE', required: true },
        {
          name: 'start_date',
          label: 'Start date',
          type: 'DATE_RANGE_MEMBER',
          required: true,
          relatedParameter: 'end_date',
        },
        {
          name: 'end_date',
          label: 'End date',
          type: 'DATE_RANGE_MEMBER',
          required: true,
          relatedParameter: 'start_date',
        },
      ],
      planningMode: 'TRADING_DAYS',
      splittable: false,
      policyVersion: 'contract-fixture-v1',
      completenessRule: {
        kind: 'VERIFIED_RULE',
        rowLimit: null,
        evidence: 'Verified fixture rule',
      },
    },
  }
}

function expectInvalid(run, requestId = REQUEST_ID) {
  expect(run).toThrowError(
    expect.objectContaining({
      name: 'ClientError',
      kind: 'INVALID_RESPONSE',
      requestId,
    }),
  )
}

describe('lossless task JSON', () => {
  it('preserves safe numbers and converts every unsafe integer boundary from its source token', () => {
    const parsed = parseTaskJson(
      '{"safe":9007199254740991,"next":9007199254740992,"odd":9007199254740993,"max":9223372036854775807,"code":"9007199254740993"}',
    )

    expect(parsed).toEqual({
      safe: 9007199254740991,
      next: 9007199254740992n,
      odd: 9007199254740993n,
      max: 9223372036854775807n,
      code: '9007199254740993',
    })
  })

  it.each([
    '{"value":9007199254740990.5}',
    '{"value":1.0000000000000001}',
    '{"value":1e3}',
    '{"value":9223372036854775808.0}',
    '{"value":',
  ])('leaves invalid or non-integer JSON unusable for DTO validation: %s', (text) => {
    expect(parseTaskJson(text)).toBe(text)
  })

  it('rejects every numeric response when JSON.parse cannot expose source tokens', () => {
    const nativeParse = JSON.parse
    const spy = vi.spyOn(JSON, 'parse').mockImplementation((text, reviver) =>
      nativeParse(text, (key, value) => reviver(key, value)),
    )
    try {
      expect(parseTaskJson('{"version":1}')).toBe('{"version":1}')
      expect(parseTaskJson('{"status":"QUEUED"}')).toEqual({ status: 'QUEUED' })
    } finally {
      spy.mockRestore()
    }
  })

  it('does not accept an adapter-preparsed value as raw task JSON', () => {
    expect(parseTaskJson({ version: 1 })).toBeUndefined()
  })
})

describe('download task receipt DTO', () => {
  it('returns a frozen whitelist and normalizes int64 to bigint', () => {
    const source = receipt()
    const parsed = parseDownloadTaskReceipt(source, REQUEST_ID)
    source.status = 'FAILED'

    expect(parsed).toEqual({ ...receipt(), version: 1n })
    expect(Object.isFrozen(parsed)).toBe(true)
    expect(parsed.status).toBe('QUEUED')
  })

  it.each([
    ['missing field', ({ createdAt, ...value }) => value],
    ['extra field', (value) => ({ ...value, outcome: 'SUCCESS' })],
    ['request ID mismatch', (value) => ({ ...value, requestId: SUBMISSION_ID })],
    ['invalid task ID', (value) => ({ ...value, taskId: 'task' })],
    ['non-string task ID', (value) => ({ ...value, taskId: [TASK_ID] })],
    ['unknown status', (value) => ({ ...value, status: 'SUCCESS' })],
    ['invalid instant', (value) => ({ ...value, createdAt: '2026-02-30T00:00:00Z' })],
    ['wire string version', (value) => ({ ...value, version: '1' })],
    ['zero version', (value) => ({ ...value, version: 0 })],
    ['fractional version', (value) => ({ ...value, version: 1.5 })],
    ['overflow version', (value) => ({ ...value, version: 9223372036854775808n })],
  ])('rejects %s', (name, mutate) => {
    expectInvalid(() => parseDownloadTaskReceipt(mutate(receipt()), REQUEST_ID))
  })

  it('requires every receipt field and accepts every persisted task status', () => {
    for (const key of Object.keys(receipt())) {
      const value = receipt()
      delete value[key]
      expectInvalid(() => parseDownloadTaskReceipt(value, REQUEST_ID))
    }
    for (const status of ['QUEUED', 'RUNNING', 'SUCCEEDED', 'PARTIAL_FAILED', 'FAILED', 'INTERRUPTED']) {
      expect(parseDownloadTaskReceipt({ ...receipt(), status }, REQUEST_ID).status).toBe(status)
    }
  })
})

describe('download task DTO', () => {
  it('copies, deeply freezes and converts all task int64 fields', () => {
    const source = task({
      version: 9007199254740993n,
      requestCount: 9007199254740992n,
      counts: {
        totalBatches: 3,
        pendingBatches: 0,
        runningBatches: 0,
        succeededBatches: 2,
        failedBatches: 1,
        splitBatches: 9007199254740993n,
        sourceRows: 9223372036854775807n,
        insertedRows: 2,
        updatedRows: 0,
      },
      lastError: { code: 'SOURCE_TIMEOUT', message: 'Source request timed out' },
    })
    const parsed = parseDownloadTask(source, REQUEST_ID)
    source.params.ts_code = 'changed'
    source.counts.sourceRows = 0
    source.lastError.message = 'changed'

    expect(parsed.version).toBe(9007199254740993n)
    expect(parsed.requestCount).toBe(9007199254740992n)
    expect(parsed.runRequestCount).toBe(1n)
    expect(parsed.counts.sourceRows).toBe(9223372036854775807n)
    expect(parsed.counts.splitBatches).toBe(9007199254740993n)
    expect(parsed.params.ts_code).toBe('000001.SZ')
    expect(parsed.lastError.message).toBe('Source request timed out')
    expect([
      parsed,
      parsed.params,
      parsed.counts,
      parsed.lastError,
    ].every(Object.isFrozen)).toBe(true)
  })

  it.each([
    ['extra top-level field', (value) => ({ ...value, definitionHash: 'secret' })],
    ['missing explicit nullable field', ({ deadlineAt, ...value }) => value],
    ['bad identifier', (value) => ({ ...value, pluginId: 'A' })],
    ['non-string identifier', (value) => ({ ...value, apiName: ['daily'] })],
    ['unknown mode', (value) => ({ ...value, mode: 'ALL' })],
    ['non-string parameter', (value) => ({ ...value, params: { trade_date: 20260912 } })],
    ['bad parameter name', (value) => ({ ...value, params: { TradeDate: '20260912' } })],
    ['unknown status', (value) => ({ ...value, status: 'EMPTY' })],
    ['non-boolean planReady', (value) => ({ ...value, planReady: 1 })],
    ['invalid stored error', (value) => ({ ...value, lastError: { code: 'RAW', message: 'x' } })],
    ['empty stored error message', (value) => ({ ...value, lastError: { code: 'SOURCE_TIMEOUT', message: '' } })],
    ['counts that do not add up', (value) => ({ ...value, counts: { ...value.counts, totalBatches: 4 } })],
    ['negative count', (value) => ({ ...value, counts: { ...value.counts, sourceRows: -1 } })],
    ['wire string count', (value) => ({ ...value, counts: { ...value.counts, sourceRows: '0' } })],
    ['unsafe rounded Number count', (value) => ({ ...value, counts: { ...value.counts, sourceRows: 9007199254740992 } })],
    ['non-boolean control hint', (value) => ({ ...value, canRetry: 'false' })],
    ['invalid calendar instant', (value) => ({ ...value, updatedAt: '2026-02-30T00:00:00Z' })],
    ['non-nullable queuedAt', (value) => ({ ...value, queuedAt: null })],
    ['invalid nullable instant', (value) => ({ ...value, finishedAt: '2026-09-12T00:00:00+08:00' })],
  ])('rejects %s', (name, mutate) => {
    expectInvalid(() => parseDownloadTask(mutate(task()), REQUEST_ID))
  })

  it('requires every task and count field and accepts both modes', () => {
    for (const key of Object.keys(task())) {
      const value = task()
      delete value[key]
      expectInvalid(() => parseDownloadTask(value, REQUEST_ID))
    }
    for (const key of Object.keys(task().counts)) {
      const value = task()
      delete value.counts[key]
      expectInvalid(() => parseDownloadTask(value, REQUEST_ID))
    }
    for (const mode of ['SINGLE', 'RANGE']) {
      expect(parseDownloadTask(task({ mode }), REQUEST_ID).mode).toBe(mode)
    }
  })

  it('normalizes all count fields to bigint', () => {
    const parsed = parseDownloadTask(task(), REQUEST_ID)
    expect(Object.values(parsed.counts).every((value) => typeof value === 'bigint')).toBe(true)
    expect(typeof parsed.version).toBe('bigint')
    expect(typeof parsed.requestCount).toBe('bigint')
    expect(typeof parsed.runRequestCount).toBe('bigint')
  })
})

describe('download task page DTO', () => {
  it('keeps server order and total while deeply freezing items', () => {
    const secondId = '55555555-5555-4555-8555-555555555555'
    const parsed = parseDownloadTaskPage(
      { page: 2, pageSize: 20, total: 42, items: [task(), task({ taskId: secondId })] },
      REQUEST_ID,
    )

    expect(parsed).toMatchObject({ page: 2, pageSize: 20, total: 42n })
    expect(parsed.items.map(({ taskId }) => taskId)).toEqual([TASK_ID, secondId])
    expect(Object.isFrozen(parsed)).toBe(true)
    expect(Object.isFrozen(parsed.items)).toBe(true)
  })

  it.each([
    ['extra field', (value) => ({ ...value, totalPages: 1 })],
    ['page zero', (value) => ({ ...value, page: 0 })],
    ['page overflow', (value) => ({ ...value, page: 2147483648 })],
    ['unsupported page size', (value) => ({ ...value, pageSize: 10 })],
    ['wire string total', (value) => ({ ...value, total: '1' })],
    ['fewer total records than returned items', (value) => ({ ...value, total: 0 })],
    ['too many items', (value) => ({ ...value, items: Array.from({ length: 21 }, (_, index) => task({ taskId: `22222222-2222-4222-8222-${String(index).padStart(12, '0')}` })) })],
    ['duplicate task IDs', (value) => ({ ...value, items: [task(), task()] })],
    ['case-only duplicate task IDs', (value) => ({ ...value, items: [task({ taskId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa' }), task({ taskId: 'AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA' })] })],
  ])('rejects %s', (name, mutate) => {
    const value = { page: 1, pageSize: 20, total: 1, items: [task()] }
    expectInvalid(() => parseDownloadTaskPage(mutate(value), REQUEST_ID))
  })

  it('requires every page field', () => {
    for (const key of ['page', 'pageSize', 'total', 'items']) {
      const value = { page: 1, pageSize: 20, total: 0, items: [] }
      delete value[key]
      expectInvalid(() => parseDownloadTaskPage(value, REQUEST_ID))
    }
  })
})

describe('download batch DTO', () => {
  it('returns a deeply frozen whitelist with int64 counts preserved as bigint', () => {
    const source = batch()
    const parsed = parseDownloadBatch(source, REQUEST_ID)
    source.sourceParams.start_date = 'changed'

    expect(parsed).toEqual({ ...batch(), updatedRows: 0n })
    expect(parsed.sourceParams.start_date).toBe('20260912')
    expect(typeof parsed.sourceRows).toBe('bigint')
    expect(typeof parsed.insertedRows).toBe('bigint')
    expect(typeof parsed.updatedRows).toBe('bigint')
    expect([parsed, parsed.sourceParams].every(Object.isFrozen)).toBe(true)
  })

  it('returns a frozen page while preserving server order', () => {
    const secondId = '55555555-5555-4555-8555-555555555555'
    const parsed = parseDownloadBatchPage(
      { page: 2, pageSize: 20, total: 22, items: [batch(), batch({ batchId: secondId })] },
      REQUEST_ID,
    )

    expect(parsed).toMatchObject({ page: 2, pageSize: 20, total: 22n })
    expect(parsed.items.map(({ batchId }) => batchId)).toEqual([BATCH_ID, secondId])
    expect(Object.isFrozen(parsed)).toBe(true)
    expect(Object.isFrozen(parsed.items)).toBe(true)
  })

  it('accepts explicit null range/times, a parent and every batch status', () => {
    const parentBatchId = '66666666-6666-4666-8666-666666666666'
    for (const status of ['PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'SPLIT']) {
      expect(parseDownloadBatch(batch({
        parentBatchId,
        rangeStart: null,
        rangeEnd: null,
        status,
        startedAt: null,
        finishedAt: null,
      }), REQUEST_ID)).toMatchObject({ status, parentBatchId, rangeStart: null })
    }
  })

  it('copies and freezes a stored batch error', () => {
    const source = batch({
      status: 'FAILED',
      error: { code: 'SOURCE_TIMEOUT', message: 'Source request timed out' },
    })
    const parsed = parseDownloadBatch(source, REQUEST_ID)
    source.error.message = 'changed'

    expect(parsed.error).toEqual({
      code: 'SOURCE_TIMEOUT',
      message: 'Source request timed out',
    })
    expect(Object.isFrozen(parsed.error)).toBe(true)
  })

  it.each([
    ['extra field', (value) => ({ ...value, retryable: true })],
    ['invalid batch ID', (value) => ({ ...value, batchId: 'batch' })],
    ['invalid parent ID', (value) => ({ ...value, parentBatchId: 'parent' })],
    ['invalid batch key', (value) => ({ ...value, batchKey: '1/2' })],
    ['overlong batch key', (value) => ({ ...value, batchKey: `000001/${'0'.repeat(122)}` })],
    ['one-sided start range', (value) => ({ ...value, rangeEnd: null })],
    ['one-sided end range', (value) => ({ ...value, rangeStart: null })],
    ['invalid start date', (value) => ({ ...value, rangeStart: '2026-02-30' })],
    ['reversed range', (value) => ({ ...value, rangeStart: '2026-09-14' })],
    ['non-string source parameter', (value) => ({ ...value, sourceParams: { start_date: 20260912 } })],
    ['unknown status', (value) => ({ ...value, status: 'QUEUED' })],
    ['negative attempt count', (value) => ({ ...value, attemptCount: -1 })],
    ['fractional attempt count', (value) => ({ ...value, attemptCount: 1.5 })],
    ['overflow attempt count', (value) => ({ ...value, attemptCount: 2147483648 })],
    ['wire string count', (value) => ({ ...value, sourceRows: '1' })],
    ['negative count', (value) => ({ ...value, insertedRows: -1 })],
    ['overflow count', (value) => ({ ...value, updatedRows: 9223372036854775808n })],
    ['unknown error', (value) => ({ ...value, error: { code: 'RAW', message: 'x' } })],
    ['invalid nullable instant', (value) => ({ ...value, startedAt: '2026-09-12' })],
    ['invalid required instant', (value) => ({ ...value, createdAt: '2026-02-30T00:00:00Z' })],
  ])('rejects %s', (name, mutate) => {
    expectInvalid(() => parseDownloadBatch(mutate(batch()), REQUEST_ID))
  })

  it('requires every batch field', () => {
    for (const key of Object.keys(batch())) {
      const value = batch()
      delete value[key]
      expectInvalid(() => parseDownloadBatch(value, REQUEST_ID))
    }
  })

  it.each([
    ['9007199254740991', 9007199254740991n],
    ['9007199254740992', 9007199254740992n],
    ['9007199254740993', 9007199254740993n],
    ['9223372036854775807', 9223372036854775807n],
  ])('preserves raw batch count token %s', (token, expected) => {
    const text = JSON.stringify(batch({ sourceRows: 0, insertedRows: 0 })).replace('"sourceRows":0', `"sourceRows":${token}`)
    expect(parseDownloadBatch(parseTaskJson(text), REQUEST_ID).sourceRows).toBe(expected)
  })

  it.each(['"1"', '1.5', '1e3', '9223372036854775808']) (
    'rejects invalid raw batch count token %s',
    (token) => {
      const text = JSON.stringify(batch({ sourceRows: 0, insertedRows: 0 })).replace('"sourceRows":0', `"sourceRows":${token}`)
      expectInvalid(() => parseDownloadBatch(parseTaskJson(text), REQUEST_ID))
    },
  )

  it.each(['1.5', '1e3'])('rejects invalid raw attemptCount token %s', (token) => {
    const text = JSON.stringify(batch({ sourceRows: 0, insertedRows: 0 })).replace(
      '"attemptCount":1',
      `"attemptCount":${token}`,
    )
    expectInvalid(() => parseDownloadBatch(parseTaskJson(text), REQUEST_ID))
  })

  it('preserves a raw maximum int64 batch-page total', () => {
    const parsed = parseDownloadBatchPage(
      parseTaskJson('{"page":2147483647,"pageSize":100,"total":9223372036854775807,"items":[]}'),
      REQUEST_ID,
    )
    expect(parsed.total).toBe(9223372036854775807n)
  })

  it.each([
    ['extra field', (value) => ({ ...value, totalPages: 1 })],
    ['page zero', (value) => ({ ...value, page: 0 })],
    ['page overflow', (value) => ({ ...value, page: 2147483648 })],
    ['unsupported page size', (value) => ({ ...value, pageSize: 10 })],
    ['wire string total', (value) => ({ ...value, total: '1' })],
    ['fewer total records than items', (value) => ({ ...value, total: 0 })],
    ['duplicate batch IDs', (value) => ({ ...value, items: [batch(), batch()] })],
    ['case-only duplicate batch IDs', (value) => ({ ...value, items: [batch({ batchId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa' }), batch({ batchId: 'AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA' })] })],
    ['too many items', (value) => ({ ...value, items: Array.from({ length: 21 }, (_, index) => batch({ batchId: `44444444-4444-4444-8444-${String(index).padStart(12, '0')}` })) })],
  ])('rejects batch page %s', (name, mutate) => {
    const value = { page: 1, pageSize: 20, total: 1, items: [batch()] }
    expectInvalid(() => parseDownloadBatchPage(mutate(value), REQUEST_ID))
  })

  it('requires every batch page field and accepts an unchanged out-of-range empty page', () => {
    for (const key of ['page', 'pageSize', 'total', 'items']) {
      const value = { page: 1, pageSize: 20, total: 0, items: [] }
      delete value[key]
      expectInvalid(() => parseDownloadBatchPage(value, REQUEST_ID))
    }
    expect(parseDownloadBatchPage(
      { page: 2147483647, pageSize: 100, total: 3, items: [] },
      REQUEST_ID,
    )).toMatchObject({ page: 2147483647, total: 3n })
  })
})

describe('download capabilities DTO', () => {
  it('accepts and deeply freezes AVAILABLE metadata with mutual endpoints', () => {
    const source = availableCapabilities()
    const parsed = parseDownloadCapabilities(source, REQUEST_ID)
    source.range.parameters[1].label = 'changed'

    expect(parsed.range.parameters[1].label).toBe('Start date')
    expect(parsed.range.completenessRule.rowLimit).toBeNull()
    expect([
      parsed,
      parsed.single,
      parsed.single.parameters,
      parsed.range,
      parsed.range.parameters,
      parsed.range.parameters[1],
      parsed.range.completenessRule,
    ].every(Object.isFrozen)).toBe(true)
  })

  it('accepts unavailable and unsupported schema shapes', () => {
    const needsVerification = availableCapabilities()
    Object.assign(needsVerification.range, {
      availability: 'NEEDS_VERIFICATION',
      unavailableReason: '区间参数语义与完整性尚待真实接口验证',
      planningMode: 'NATIVE_RANGE',
      splittable: true,
      completenessRule: { kind: 'UNKNOWN', rowLimit: null, evidence: null },
    })
    const unsupported = {
      single: { available: false, parameters: [] },
      range: {
        availability: 'UNSUPPORTED',
        unavailableReason: 'Range download is unsupported',
        dateAxis: null,
        dateLabel: null,
        startParameter: null,
        endParameter: null,
        parameters: [],
        planningMode: null,
        splittable: false,
        policyVersion: 'unsupported-v1',
        completenessRule: { kind: 'UNKNOWN', rowLimit: null, evidence: null },
      },
    }

    expect(parseDownloadCapabilities(needsVerification, REQUEST_ID).range.availability).toBe('NEEDS_VERIFICATION')
    expect(parseDownloadCapabilities(unsupported, REQUEST_ID).range.availability).toBe('UNSUPPORTED')
  })

  it('preserves a positive int64 row limit as bigint', () => {
    const value = availableCapabilities()
    value.range.completenessRule = {
      kind: 'CONFIRMED_ROW_LIMIT',
      rowLimit: 9223372036854775807n,
      evidence: 'Documented source limit',
    }

    expect(parseDownloadCapabilities(value, REQUEST_ID).range.completenessRule.rowLimit).toBe(9223372036854775807n)
  })

  it.each([
    ['unknown top-level field', (value) => ({ ...value, requestId: REQUEST_ID })],
    ['duplicate parameter name', (value) => ({ ...value, single: { ...value.single, parameters: [...value.single.parameters, value.single.parameters[0]] } })],
    ['unknown parameter field', (value) => ({ ...value, single: { ...value.single, parameters: [{ ...value.single.parameters[0], secret: true }] } })],
    ['unknown parameter type', (value) => ({ ...value, single: { ...value.single, parameters: [{ ...value.single.parameters[0], type: 'NUMBER' }] } })],
    ['ENUM without allowed values', (value) => ({ ...value, single: { ...value.single, parameters: [{ name: 'market', label: 'Market', type: 'ENUM', required: false }] } })],
    ['duplicate ENUM values', (value) => ({ ...value, single: { ...value.single, parameters: [{ name: 'market', label: 'Market', type: 'ENUM', required: false, allowedValues: ['SSE', 'SSE'] }] } })],
    ['one-way range relation', (value) => ({ ...value, range: { ...value.range, parameters: value.range.parameters.map((parameter) => parameter.name === 'end_date' ? { ...parameter, relatedParameter: 'ts_code' } : parameter) } })],
    ['self-related range member', (value) => ({ ...value, range: { ...value.range, parameters: value.range.parameters.map((parameter) => parameter.name === 'start_date' ? { ...parameter, relatedParameter: 'start_date' } : parameter) } })],
    ['same range endpoints', (value) => ({ ...value, range: { ...value.range, endParameter: 'start_date' } })],
    ['missing start endpoint', (value) => ({ ...value, range: { ...value.range, startParameter: 'missing_date' } })],
    ['non-string start endpoint', (value) => ({ ...value, range: { ...value.range, startParameter: ['start_date'] } })],
    ['AVAILABLE reason', (value) => ({ ...value, range: { ...value.range, unavailableReason: 'not available' } })],
    ['AVAILABLE unknown completeness', (value) => ({ ...value, range: { ...value.range, completenessRule: { kind: 'UNKNOWN', rowLimit: null, evidence: null } } })],
    ['unavailable without reason', (value) => ({ ...value, range: { ...value.range, availability: 'NEEDS_VERIFICATION', unavailableReason: null } })],
    ['splittable non-native range', (value) => ({ ...value, range: { ...value.range, splittable: true, planningMode: 'TRADING_DAYS' } })],
    ['UNSUPPORTED with metadata', (value) => ({ ...value, range: { ...value.range, availability: 'UNSUPPORTED', unavailableReason: 'unsupported' } })],
    ['CONFIRMED_ROW_LIMIT wire string', (value) => ({ ...value, range: { ...value.range, completenessRule: { kind: 'CONFIRMED_ROW_LIMIT', rowLimit: '100', evidence: 'documented' } } })],
    ['VERIFIED_RULE row limit', (value) => ({ ...value, range: { ...value.range, completenessRule: { kind: 'VERIFIED_RULE', rowLimit: 100, evidence: 'verified' } } })],
  ])('rejects %s', (name, mutate) => {
    expectInvalid(() => parseDownloadCapabilities(mutate(availableCapabilities()), REQUEST_ID))
  })

  it('requires every capability, range, completeness and parameter field', () => {
    const paths = [
      ['single'], ['range'], ['single', 'available'], ['single', 'parameters'],
      ...[
        'availability', 'unavailableReason', 'dateAxis', 'dateLabel',
        'startParameter', 'endParameter', 'parameters', 'planningMode',
        'splittable', 'policyVersion', 'completenessRule',
      ].map((key) => ['range', key]),
      ...['kind', 'rowLimit', 'evidence'].map((key) => ['range', 'completenessRule', key]),
      ...['name', 'label', 'type', 'required'].map((key) => ['single', 'parameters', 0, key]),
    ]
    for (const path of paths) {
      const value = structuredClone(availableCapabilities())
      const key = path.at(-1)
      const owner = path.slice(0, -1).reduce((current, part) => current[part], value)
      delete owner[key]
      expectInvalid(() => parseDownloadCapabilities(value, REQUEST_ID))
    }
  })
})

it('uses a safe ClientError without echoing invalid response data', () => {
  const raw = { secret: 'upstream-token' }
  let error
  try {
    parseDownloadTask(raw, REQUEST_ID)
  } catch (caught) {
    error = caught
  }

  expect(error).toBeInstanceOf(ClientError)
  expect(error.message).toBe('服务返回了无法识别的响应。')
  expect(error.message).not.toContain('upstream-token')
  expect(error.cause).toBeUndefined()
})
