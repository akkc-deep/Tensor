import { AxiosError } from 'axios'

import {
  getDownloadCapabilities,
  getDownloadTask,
  listDownloadTaskBatches,
  listDownloadTasks,
  resumeDownloadTask,
  retryDownloadTask,
  submitDownloadTask,
} from './downloadTasks.js'
import { ApiError, ClientError } from './errors.js'
import { configureHttp, http } from './http.js'

const DEFAULT_ADAPTER = http.defaults.adapter
const DEFAULT_BASE_URL = '/api/v1'
const DEFAULT_TIMEOUT = 130000
const TASK_ID = '22222222-2222-4222-8222-222222222222'

function rawResponse(config, data, { status = 200, headers = {} } = {}) {
  const requestId = config.headers.get('X-Request-Id')
  return {
    data: typeof data === 'string' ? data : JSON.stringify(data),
    status,
    statusText: status === 202 ? 'Accepted' : 'OK',
    headers: { 'X-Request-Id': requestId, ...headers },
    config,
    request: {},
  }
}

function validTask(overrides = {}) {
  return {
    taskId: TASK_ID,
    submissionId: '33333333-3333-4333-8333-333333333333',
    pluginId: 'contract_fixture',
    apiName: 'daily',
    mode: 'RANGE',
    params: { start_date: '20260803', end_date: '20260805' },
    status: 'QUEUED',
    version: 1,
    planReady: false,
    counts: {
      totalBatches: 0,
      pendingBatches: 0,
      runningBatches: 0,
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
    requestCount: 0,
    runRequestCount: 0,
    createdAt: '2026-09-12T00:00:00Z',
    updatedAt: '2026-09-12T00:00:00Z',
    queuedAt: '2026-09-12T00:00:00Z',
    startedAt: null,
    finishedAt: null,
    deadlineAt: null,
    ...overrides,
  }
}

function validBatch(overrides = {}) {
  return {
    batchId: '44444444-4444-4444-8444-444444444444',
    parentBatchId: null,
    batchKey: '000001',
    rangeStart: '2026-09-12',
    rangeEnd: '2026-09-12',
    sourceParams: { trade_date: '20260912' },
    status: 'SUCCEEDED',
    attemptCount: 1,
    sourceRows: 1,
    insertedRows: 1,
    updatedRows: 0,
    error: null,
    startedAt: '2026-09-12T00:00:00Z',
    finishedAt: '2026-09-12T00:00:01Z',
    createdAt: '2026-09-12T00:00:00Z',
    updatedAt: '2026-09-12T00:00:01Z',
    ...overrides,
  }
}

function validCapabilities() {
  return {
    single: { available: true, parameters: [] },
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
}

afterEach(() => {
  http.defaults.adapter = DEFAULT_ADAPTER
  configureHttp({ baseURL: DEFAULT_BASE_URL, timeout: DEFAULT_TIMEOUT })
})

it('sends an exact bigint retry version through the real Axios adapter', async () => {
  const requests = []
  http.defaults.adapter = async (config) => {
    requests.push(config)
    const requestId = config.headers.get('X-Request-Id')
    return rawResponse(
      config,
      `{"requestId":"${requestId}","taskId":"${TASK_ID}","status":"RUNNING","version":9007199254740993,"createdAt":"2026-09-12T00:00:00Z"}`,
      {
        status: 202,
        headers: { Location: `/api/v1/download-tasks/${TASK_ID}` },
      },
    )
  }

  const receipt = await retryDownloadTask(TASK_ID, 9007199254740993n)

  expect(requests).toHaveLength(1)
  expect(requests[0].method).toBe('post')
  expect(requests[0].url).toBe(`/download-tasks/${TASK_ID}/retry`)
  expect(requests[0].data).toBe('{"expectedVersion":9007199254740993}')
  expect(requests[0].headers.get('Content-Type')).toBe('application/json')
  expect(receipt).toMatchObject({
    taskId: TASK_ID,
    status: 'RUNNING',
    version: 9007199254740993n,
  })
})

it('submits one complete task request and parses a durable 202 receipt', async () => {
  const requests = []
  const taskId = '22222222-2222-4222-8222-222222222222'
  http.defaults.adapter = async (config) => {
    requests.push(config)
    const requestId = config.headers.get('X-Request-Id')
    return {
      data: JSON.stringify({
        requestId,
        taskId,
        status: 'QUEUED',
        version: 1,
        createdAt: '2026-09-12T00:00:00Z',
      }),
      status: 202,
      statusText: 'Accepted',
      headers: {
        'X-Request-Id': requestId,
        Location: `/api/v1/download-tasks/${taskId}`,
      },
      config,
      request: {},
    }
  }
  const request = {
    submissionId: '33333333-3333-4333-8333-333333333333',
    pluginId: 'contract_fixture',
    apiName: 'daily',
    mode: 'RANGE',
    params: {
      ts_code: '000001.SZ',
      start_date: '20260803',
      end_date: '20260805',
    },
  }

  const receipt = await submitDownloadTask(request)

  expect(requests).toHaveLength(1)
  expect(requests[0].method).toBe('post')
  expect(requests[0].url).toBe('/download-tasks')
  expect(JSON.parse(requests[0].data)).toEqual(request)
  expect(receipt).toEqual({
    requestId: requests[0].headers.get('X-Request-Id'),
    taskId,
    status: 'QUEUED',
    version: 1n,
    createdAt: '2026-09-12T00:00:00Z',
  })
  expect(receipt.status).not.toBe('SUCCESS')
  expect(receipt.status).not.toBe('EMPTY')
})

describe('download task transport', () => {
  it('gets a normalized task path through raw JSON and forwards cancellation', async () => {
    const requests = []
    const controller = new AbortController()
    http.defaults.adapter = async (config) => {
      requests.push(config)
      return rawResponse(config, validTask())
    }

    const task = await getDownloadTask(TASK_ID.toUpperCase(), { signal: controller.signal })

    expect(requests).toHaveLength(1)
    expect(requests[0]).toMatchObject({
      method: 'get',
      url: `/download-tasks/${TASK_ID}`,
      responseType: 'text',
      signal: controller.signal,
    })
    expect(task.taskId).toBe(TASK_ID)
    expect(task.version).toBe(1n)
  })

  it('lists the requested leaf batch page with defaults and exact query values', async () => {
    const requests = []
    http.defaults.adapter = async (config) => {
      requests.push(config)
      return rawResponse(config, {
        page: 1,
        pageSize: 20,
        total: 1,
        items: [validBatch()],
      })
    }

    const page = await listDownloadTaskBatches(TASK_ID)

    expect(page.total).toBe(1n)
    expect(page.items[0].sourceRows).toBe(1n)
    expect(requests).toHaveLength(1)
    expect(requests[0].url).toBe(`/download-tasks/${TASK_ID}/batches`)
    expect(requests[0].params).toBeInstanceOf(URLSearchParams)
    expect([...requests[0].params]).toEqual([
      ['page', '1'],
      ['pageSize', '20'],
      ['includeSplit', 'false'],
    ])
  })

  it('sends explicit batch pagination without mutating criteria', async () => {
    const requests = []
    http.defaults.adapter = async (config) => {
      requests.push(config)
      return rawResponse(config, { page: 7, pageSize: 50, total: 0, items: [] })
    }
    const criteria = { page: 7, pageSize: 50 }

    await listDownloadTaskBatches(TASK_ID, criteria)

    expect([...requests[0].params]).toEqual([
      ['page', '7'],
      ['pageSize', '50'],
      ['includeSplit', 'false'],
    ])
    expect(criteria).toEqual({ page: 7, pageSize: 50 })
  })

  it.each([
    ['invalid detail task ID', () => getDownloadTask('task')],
    ['null batch task ID', () => listDownloadTaskBatches(null)],
    ['null batch criteria', () => listDownloadTaskBatches(TASK_ID, null)],
    ['array batch criteria', () => listDownloadTaskBatches(TASK_ID, [])],
    ['unknown batch criterion', () => listDownloadTaskBatches(TASK_ID, { status: 'FAILED' })],
    ['batch page zero', () => listDownloadTaskBatches(TASK_ID, { page: 0 })],
    ['batch page overflow', () => listDownloadTaskBatches(TASK_ID, { page: 2147483648 })],
    ['fractional batch page', () => listDownloadTaskBatches(TASK_ID, { page: 1.5 })],
    ['invalid batch pageSize', () => listDownloadTaskBatches(TASK_ID, { pageSize: 10 })],
  ])('rejects %s before HTTP', async (name, run) => {
    let requests = 0
    http.defaults.adapter = async (config) => {
      requests += 1
      return rawResponse(config, {})
    }

    await expect(run()).rejects.toBeInstanceOf(TypeError)
    expect(requests).toBe(0)
  })

  it('rejects detail identity, batch pagination drift and SPLIT rows', async () => {
    http.defaults.adapter = async (config) => rawResponse(config, validTask({
      taskId: '55555555-5555-4555-8555-555555555555',
    }))
    await expect(getDownloadTask(TASK_ID)).rejects.toMatchObject({ kind: 'INVALID_RESPONSE' })

    http.defaults.adapter = async (config) => rawResponse(config, {
      page: 2,
      pageSize: 20,
      total: 0,
      items: [],
    })
    await expect(listDownloadTaskBatches(TASK_ID)).rejects.toMatchObject({ kind: 'INVALID_RESPONSE' })

    http.defaults.adapter = async (config) => rawResponse(config, {
      page: 1,
      pageSize: 20,
      total: 1,
      items: [validBatch({ status: 'SPLIT' })],
    })
    await expect(listDownloadTaskBatches(TASK_ID)).rejects.toMatchObject({ kind: 'INVALID_RESPONSE' })
  })

  it('resumes with the exact maximum int64 version and accepts a non-QUEUED 202 receipt', async () => {
    const requests = []
    http.defaults.adapter = async (config) => {
      requests.push(config)
      const requestId = config.headers.get('X-Request-Id')
      return rawResponse(
        config,
        `{"requestId":"${requestId}","taskId":"${TASK_ID}","status":"SUCCEEDED","version":9223372036854775807,"createdAt":"2026-09-12T00:00:00Z"}`,
        { status: 202, headers: { Location: `/api/v1/download-tasks/${TASK_ID}` } },
      )
    }

    const receipt = await resumeDownloadTask(TASK_ID.toUpperCase(), 9223372036854775807n)

    expect(requests[0].url).toBe(`/download-tasks/${TASK_ID}/resume`)
    expect(requests[0].data).toBe('{"expectedVersion":9223372036854775807}')
    expect(requests[0].headers.get('Content-Type')).toBe('application/json')
    expect(receipt).toMatchObject({ taskId: TASK_ID, status: 'SUCCEEDED', version: 9223372036854775807n })
  })

  it.each([
    ['9007199254740991', 9007199254740991n],
    ['9007199254740992', 9007199254740992n],
    ['9007199254740993', 9007199254740993n],
    ['9223372036854775807', 9223372036854775807n],
  ])('sends and receives exact control version token %s', async (token, version) => {
    const controller = new AbortController()
    let request
    http.defaults.adapter = async (config) => {
      request = config
      const requestId = config.headers.get('X-Request-Id')
      return rawResponse(
        config,
        `{"requestId":"${requestId}","taskId":"${TASK_ID}","status":"RUNNING","version":${token},"createdAt":"2026-09-12T00:00:00Z"}`,
        { status: 202, headers: { Location: `/api/v1/download-tasks/${TASK_ID}` } },
      )
    }

    const receipt = await retryDownloadTask(TASK_ID, version, { signal: controller.signal })

    expect(request.data).toBe(`{"expectedVersion":${token}}`)
    expect(request.signal).toBe(controller.signal)
    expect(receipt.version).toBe(version)
  })

  it.each([
    ['invalid task ID', 'task', 1n],
    ['number version', TASK_ID, 1],
    ['string version', TASK_ID, '1'],
    ['null version', TASK_ID, null],
    ['zero version', TASK_ID, 0n],
    ['negative version', TASK_ID, -1n],
    ['overflow version', TASK_ID, 9223372036854775808n],
  ])('rejects control %s before HTTP', async (name, taskId, version) => {
    let requests = 0
    http.defaults.adapter = async (config) => {
      requests += 1
      return rawResponse(config, {})
    }

    await expect(retryDownloadTask(taskId, version)).rejects.toBeInstanceOf(TypeError)
    expect(requests).toBe(0)
  })

  it.each([
    ['non-202 status', { status: 200, taskId: TASK_ID, location: `/api/v1/download-tasks/${TASK_ID}` }],
    ['wrong task receipt', { status: 202, taskId: '55555555-5555-4555-8555-555555555555', location: `/api/v1/download-tasks/${TASK_ID}` }],
    ['wrong Location', { status: 202, taskId: TASK_ID, location: '/api/v1/download-tasks/55555555-5555-4555-8555-555555555555' }],
  ])('rejects control %s', async (name, response) => {
    http.defaults.adapter = async (config) => {
      const requestId = config.headers.get('X-Request-Id')
      return rawResponse(
        config,
        { requestId, taskId: response.taskId, status: 'RUNNING', version: 2, createdAt: '2026-09-12T00:00:00Z' },
        { status: response.status, headers: { Location: response.location } },
      )
    }

    await expect(retryDownloadTask(TASK_ID, 1n)).rejects.toMatchObject({ kind: 'INVALID_RESPONSE' })
  })

  it('gets capabilities through raw task JSON and forwards cancellation', async () => {
    const requests = []
    const controller = new AbortController()
    http.defaults.adapter = async (config) => {
      requests.push(config)
      return rawResponse(config, validCapabilities())
    }

    const capabilities = await getDownloadCapabilities(
      'contract_fixture',
      'daily',
      { signal: controller.signal },
    )

    expect(requests).toHaveLength(1)
    expect(requests[0]).toMatchObject({
      method: 'get',
      url: '/data-sources/contract_fixture/apis/daily/download-capabilities',
      responseType: 'text',
      signal: controller.signal,
    })
    expect(capabilities.range.availability).toBe('UNSUPPORTED')
    expect(Object.isFrozen(capabilities)).toBe(true)
  })

  it('serializes only validated list filters as one value each with defaults', async () => {
    const requests = []
    http.defaults.adapter = async (config) => {
      requests.push(config)
      return rawResponse(config, { page: 7, pageSize: 50, total: 0, items: [] })
    }
    const criteria = {
      page: 7,
      pageSize: 50,
      pluginId: 'contract_fixture',
      apiName: undefined,
      status: 'PARTIAL_FAILED',
      submissionId: undefined,
    }
    const snapshot = { ...criteria }

    const page = await listDownloadTasks(criteria)

    expect(page).toEqual({ page: 7, pageSize: 50, total: 0n, items: [] })
    expect(requests).toHaveLength(1)
    expect(requests[0].params).toBeInstanceOf(URLSearchParams)
    expect([...requests[0].params]).toEqual([
      ['page', '7'],
      ['pageSize', '50'],
      ['pluginId', 'contract_fixture'],
      ['status', 'PARTIAL_FAILED'],
    ])
    expect(criteria).toEqual(snapshot)
  })

  it('uses page 1 and pageSize 20 when list criteria are omitted', async () => {
    let request
    http.defaults.adapter = async (config) => {
      request = config
      return rawResponse(config, { page: 1, pageSize: 20, total: 0, items: [] })
    }

    await listDownloadTasks()

    expect([...request.params]).toEqual([['page', '1'], ['pageSize', '20']])
  })

  it.each([
    ['null criteria', null],
    ['array criteria', []],
    ['unknown key', { sort: 'createdAt' }],
    ['null filter', { pluginId: null }],
    ['blank filter', { pluginId: ' ' }],
    ['array filter', { status: ['QUEUED'] }],
    ['invalid identifier', { apiName: 'Daily' }],
    ['invalid status', { status: 'queued' }],
    ['page zero', { page: 0 }],
    ['page overflow', { page: 2147483648 }],
    ['fractional page', { page: 1.5 }],
    ['invalid page size', { pageSize: 10 }],
    ['invalid submission ID', { submissionId: 'submission' }],
  ])('rejects %s before sending a list request', async (name, criteria) => {
    let requests = 0
    http.defaults.adapter = async (config) => {
      requests += 1
      return rawResponse(config, { page: 1, pageSize: 20, total: 0, items: [] })
    }

    await expect(listDownloadTasks(criteria)).rejects.toBeInstanceOf(TypeError)
    expect(requests).toBe(0)
  })

  it('validates the page against the requested pagination', async () => {
    http.defaults.adapter = async (config) =>
      rawResponse(config, { page: 2, pageSize: 20, total: 0, items: [] })

    await expect(listDownloadTasks()).rejects.toMatchObject({
      kind: 'INVALID_RESPONSE',
    })
  })

  it('requires an exact unique submission result when restoring by submissionId', async () => {
    const submissionId = '33333333-3333-4333-8333-333333333333'
    const invalidPages = [
      { page: 1, pageSize: 20, total: 1, items: [] },
      { page: 1, pageSize: 20, total: 2, items: [validTask()] },
      { page: 1, pageSize: 20, total: 1, items: [validTask({ submissionId: '44444444-4444-4444-8444-444444444444' })] },
    ]
    for (const data of invalidPages) {
      http.defaults.adapter = async (config) => rawResponse(config, data)
      await expect(listDownloadTasks({ submissionId })).rejects.toMatchObject({
        kind: 'INVALID_RESPONSE',
      })
    }
  })

  it('matches a normalized server submission ID after an uppercase UUID query', async () => {
    const submissionId = '33333333-3333-4333-8333-33333333333A'
    http.defaults.adapter = async (config) =>
      rawResponse(config, {
        page: 1,
        pageSize: 20,
        total: 1,
        items: [validTask({ submissionId: submissionId.toLowerCase() })],
      })

    await expect(listDownloadTasks({ submissionId })).resolves.toMatchObject({ total: 1n })
  })

  it.each([
    [202, 'QUEUED'],
    [200, 'SUCCEEDED'],
    [200, 'PARTIAL_FAILED'],
  ])('accepts HTTP %i receipts with shared task statuses', async (statusCode, status) => {
    http.defaults.adapter = async (config) => {
      const requestId = config.headers.get('X-Request-Id')
      return rawResponse(
        config,
        { requestId, taskId: TASK_ID, status, version: 2, createdAt: '2026-09-12T00:00:00Z' },
        { status: statusCode, headers: { Location: `/api/v1/download-tasks/${TASK_ID}` } },
      )
    }

    await expect(submitDownloadTask({ params: {} })).resolves.toMatchObject({ status, version: 2n })
  })

  it('requires a newly accepted 202 receipt to still be QUEUED', async () => {
    http.defaults.adapter = async (config) => {
      const requestId = config.headers.get('X-Request-Id')
      return rawResponse(
        config,
        { requestId, taskId: TASK_ID, status: 'RUNNING', version: 2, createdAt: '2026-09-12T00:00:00Z' },
        { status: 202, headers: { Location: `/api/v1/download-tasks/${TASK_ID}` } },
      )
    }

    await expect(submitDownloadTask({ params: {} })).rejects.toMatchObject({
      kind: 'INVALID_RESPONSE',
    })
  })

  it.each([
    ['9007199254740991', 9007199254740991n],
    ['9007199254740992', 9007199254740992n],
    ['9007199254740993', 9007199254740993n],
    ['9223372036854775807', 9223372036854775807n],
  ])('preserves the raw receipt version token %s exactly', async (token, expected) => {
    http.defaults.adapter = async (config) => {
      const requestId = config.headers.get('X-Request-Id')
      return rawResponse(
        config,
        `{"requestId":"${requestId}","taskId":"${TASK_ID}","status":"SUCCEEDED","version":${token},"createdAt":"2026-09-12T00:00:00Z"}`,
        { status: 200, headers: { Location: `/api/v1/download-tasks/${TASK_ID}` } },
      )
    }

    await expect(submitDownloadTask({ params: {} })).resolves.toMatchObject({
      version: expected,
    })
  })

  it.each([
    '9223372036854775808',
    '9007199254740990.5',
    '1.0000000000000001',
    '1e3',
    '"1"',
  ])('rejects invalid raw receipt version token %s', async (token) => {
    http.defaults.adapter = async (config) => {
      const requestId = config.headers.get('X-Request-Id')
      return rawResponse(
        config,
        `{"requestId":"${requestId}","taskId":"${TASK_ID}","status":"SUCCEEDED","version":${token},"createdAt":"2026-09-12T00:00:00Z"}`,
        { status: 200, headers: { Location: `/api/v1/download-tasks/${TASK_ID}` } },
      )
    }

    await expect(submitDownloadTask({ params: {} })).rejects.toMatchObject({
      kind: 'INVALID_RESPONSE',
    })
  })

  it.each([
    ['unexpected submit status', async (config) => rawResponse(config, {}, { status: 201 })],
    ['wrong response request ID', async (config) => rawResponse(config, validCapabilities(), { headers: { 'X-Request-Id': '44444444-4444-4444-8444-444444444444' } })],
    ['missing Location', async (config) => {
      const id = config.headers.get('X-Request-Id')
      return rawResponse(config, { requestId: id, taskId: TASK_ID, status: 'QUEUED', version: 1, createdAt: '2026-09-12T00:00:00Z' }, { status: 202 })
    }],
  ])('rejects %s as an invalid response', async (name, adapter) => {
    http.defaults.adapter = adapter
    const promise = name === 'wrong response request ID'
      ? getDownloadCapabilities('contract_fixture', 'daily')
      : submitDownloadTask({ params: {} })
    await expect(promise).rejects.toBeInstanceOf(ClientError)
    await expect(promise).rejects.toMatchObject({ kind: 'INVALID_RESPONSE' })
  })

  it('keeps a valid API error on the existing normalization path', async () => {
    http.defaults.adapter = async (config) => {
      const id = config.headers.get('X-Request-Id')
      throw new AxiosError(
        'raw error',
        'ERR_BAD_RESPONSE',
        config,
        {},
        rawResponse(
          config,
          { requestId: id, code: 'QUERY_FAILED', message: 'Query failed', retryable: true, fieldErrors: [] },
          { status: 500 },
        ),
      )
    }

    await expect(listDownloadTasks()).rejects.toBeInstanceOf(ApiError)
    await expect(listDownloadTasks()).rejects.toMatchObject({
      code: 'QUERY_FAILED',
      retryable: true,
    })
  })

  it('rejects malformed JSON and numeric responses without source as INVALID_RESPONSE', async () => {
    http.defaults.adapter = async (config) => rawResponse(config, '{"page":')
    await expect(listDownloadTasks()).rejects.toMatchObject({ kind: 'INVALID_RESPONSE' })

    const nativeParse = JSON.parse
    const spy = vi.spyOn(JSON, 'parse').mockImplementation((text, reviver) =>
      nativeParse(text, (key, value) => reviver(key, value)),
    )
    try {
      http.defaults.adapter = async (config) =>
        rawResponse(config, '{"page":1,"pageSize":20,"total":0,"items":[]}')
      await expect(listDownloadTasks()).rejects.toMatchObject({ kind: 'INVALID_RESPONSE' })
    } finally {
      spy.mockRestore()
    }
  })
})
