import { test as base } from '@playwright/test'

export const TASK_ID = '22222222-2222-4222-8222-222222222222'

const MAX_INT64 = 9223372036854775807n
const BATCH_IDS = [
  '44444444-4444-4444-8444-444444444441',
  '44444444-4444-4444-8444-444444444442',
  '44444444-4444-4444-8444-444444444443',
]

const SOURCE = {
  pluginId: 'tushare_pro',
  displayName: 'Tushare Pro',
  description: '受控任务测试数据源',
  enabled: true,
  credentialConfigured: true,
  downloadAvailable: true,
  unavailableReason: null,
}

const API = {
  apiName: 'daily',
  displayName: '日线行情',
  category: '行情与估值',
  queryMode: 'trade_date',
  parameters: [],
}

const PARAMETERS = [
  { name: 'ts_code', label: '股票代码', type: 'TS_CODE', required: true },
  { name: 'start_date', label: '开始日期', type: 'DATE_RANGE_MEMBER', required: true, relatedParameter: 'end_date' },
  { name: 'end_date', label: '结束日期', type: 'DATE_RANGE_MEMBER', required: true, relatedParameter: 'start_date' },
]

const CAPABILITIES = {
  single: {
    available: true,
    parameters: [
      { name: 'ts_code', label: '股票代码', type: 'TS_CODE', required: true },
      { name: 'trade_date', label: '交易日期', type: 'DATE', required: true },
    ],
  },
  range: {
    availability: 'AVAILABLE',
    unavailableReason: null,
    dateAxis: 'TRADE_DATE',
    dateLabel: '交易日期',
    startParameter: 'start_date',
    endParameter: 'end_date',
    parameters: PARAMETERS,
    planningMode: 'TRADING_DAYS',
    splittable: false,
    policyVersion: 'controlled-browser-v1',
    completenessRule: {
      kind: 'VERIFIED_RULE',
      rowLimit: null,
      evidence: '受控浏览器夹具验证规则',
    },
  },
}

function requireFixture(condition, message) {
  if (!condition) throw new Error(`Download-task fixture failed: ${message}`)
}

export function rawJson(value) {
  if (value === null) return 'null'
  if (typeof value === 'bigint') {
    requireFixture(value >= 0n && value <= MAX_INT64, 'bigint is in the int64 response range')
    return value.toString()
  }
  if (typeof value === 'number') {
    requireFixture(Number.isSafeInteger(value), 'number response tokens are safe integers')
    return String(value)
  }
  if (typeof value === 'string' || typeof value === 'boolean') return JSON.stringify(value)
  if (Array.isArray(value)) return `[${value.map(rawJson).join(',')}]`
  requireFixture(value && typeof value === 'object', 'response value is JSON compatible')
  return `{${Object.entries(value).map(([key, item]) => `${JSON.stringify(key)}:${rawJson(item)}`).join(',')}}`
}

function counts(overrides = {}) {
  return {
    totalBatches: 3n,
    pendingBatches: 0n,
    runningBatches: 0n,
    succeededBatches: 3n,
    failedBatches: 0n,
    splitBatches: 0n,
    sourceRows: 15n,
    insertedRows: 14n,
    updatedRows: 1n,
    ...overrides,
  }
}

export function task(overrides = {}) {
  return {
    taskId: TASK_ID,
    submissionId: '33333333-3333-4333-8333-333333333333',
    pluginId: 'tushare_pro',
    apiName: 'daily',
    mode: 'RANGE',
    params: {
      ts_code: '000001.SZ',
      start_date: '20260901',
      end_date: '20260903',
    },
    status: 'SUCCEEDED',
    version: 9007199254740993n,
    planReady: true,
    counts: counts(),
    lastError: null,
    canRetry: false,
    canResume: false,
    requestCount: 3n,
    runRequestCount: 3n,
    createdAt: '2026-09-12T00:00:00Z',
    updatedAt: '2026-09-12T00:03:00Z',
    queuedAt: '2026-09-12T00:00:01Z',
    startedAt: '2026-09-12T00:00:02Z',
    finishedAt: '2026-09-12T00:03:00Z',
    deadlineAt: '2026-09-12T00:30:02Z',
    ...overrides,
  }
}

export function batch(index, overrides = {}) {
  const day = String(index + 1).padStart(2, '0')
  return {
    batchId: BATCH_IDS[index],
    parentBatchId: null,
    batchKey: `00000${index + 1}`,
    rangeStart: `2026-09-${day}`,
    rangeEnd: `2026-09-${day}`,
    sourceParams: {
      ts_code: '000001.SZ',
      start_date: `202609${day}`,
      end_date: `202609${day}`,
    },
    status: 'SUCCEEDED',
    attemptCount: 1,
    sourceRows: 5n,
    insertedRows: 5n,
    updatedRows: 0n,
    error: null,
    startedAt: `2026-09-12T00:0${index}:00Z`,
    finishedAt: `2026-09-12T00:0${index}:30Z`,
    createdAt: '2026-09-12T00:00:00Z',
    updatedAt: `2026-09-12T00:0${index}:30Z`,
    ...overrides,
  }
}

function succeededBatches() {
  return [batch(0), batch(1), batch(2)]
}

function partialTask() {
  return task({
    status: 'PARTIAL_FAILED',
    counts: counts({
      succeededBatches: 2n,
      failedBatches: 1n,
      sourceRows: 9007199254740995n,
      insertedRows: 9007199254740995n,
      updatedRows: 0n,
    }),
    lastError: { code: 'SOURCE_TIMEOUT', message: 'Source request timed out' },
    canRetry: true,
    requestCount: 9223372036854775806n,
    runRequestCount: 3n,
    finishedAt: '2026-09-12T00:03:00Z',
  })
}

function partialBatches() {
  return [
    batch(0, { sourceRows: 9007199254740993n, insertedRows: 9007199254740993n }),
    batch(1, { sourceRows: 2n, insertedRows: 2n }),
    batch(2, {
      status: 'FAILED',
      sourceRows: 0n,
      insertedRows: 0n,
      error: { code: 'SOURCE_TIMEOUT', message: 'Source request timed out' },
    }),
  ]
}

function interruptedTask(version = 9007199254740993n) {
  return task({
    status: 'INTERRUPTED',
    version,
    counts: counts({
      pendingBatches: 1n,
      succeededBatches: 2n,
      sourceRows: 10n,
      insertedRows: 10n,
      updatedRows: 0n,
    }),
    lastError: { code: 'EXECUTION_INTERRUPTED', message: 'Download task execution was interrupted' },
    canResume: true,
    finishedAt: null,
  })
}

function interruptedBatches() {
  return [
    batch(0),
    batch(1),
    batch(2, {
      status: 'PENDING',
      attemptCount: 0,
      sourceRows: 0n,
      insertedRows: 0n,
      startedAt: null,
      finishedAt: null,
      updatedAt: '2026-09-12T00:00:00Z',
    }),
  ]
}

function errorBody(requestId, code, message, retryable, fieldErrors = []) {
  return { requestId, code, message, retryable, fieldErrors }
}

function exactQuery(url, expected) {
  const entries = [...url.searchParams.entries()]
  return entries.length === Object.keys(expected).length &&
    entries.every(([key, value]) => expected[key] === value)
}

async function installDownloadTaskApi(context) {
  const state = {
    scenario: 'submission',
    task: null,
    batches: [],
    requests: [],
    unexpected: [],
    pageErrors: [],
    retryRunningDetailPending: false,
    failNextDetail: 0,
  }

  const fixture = {
    state,
    requests(method, path) {
      return state.requests.filter((request) => request.method === method && request.path === path)
    },
    seedPartialFailure() {
      state.scenario = 'partial'
      state.task = partialTask()
      state.batches = partialBatches()
    },
    seedInterrupted() {
      state.scenario = 'interrupted'
      state.task = interruptedTask()
      state.batches = interruptedBatches()
    },
    assertClean() {
      requireFixture(state.unexpected.length === 0, `unexpected API calls: ${JSON.stringify(state.unexpected)}`)
      requireFixture(state.pageErrors.length === 0, `page errors: ${state.pageErrors.join('\n')}`)
    },
  }

  const watchPage = (page) => page.on('pageerror', (error) => state.pageErrors.push(error.stack ?? error.message))
  context.pages().forEach(watchPage)
  context.on('page', watchPage)

  await context.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const method = request.method()
    const url = new URL(request.url())
    const path = url.pathname
    const requestId = request.headers()['x-request-id'] ?? ''
    const rawBody = request.postData()
    const recorded = {
      method,
      path,
      query: [...url.searchParams.entries()],
      rawBody,
      requestId,
      contentType: request.headers()['content-type'] ?? '',
    }
    state.requests.push(recorded)

    const reject = async (reason) => {
      state.unexpected.push({ ...recorded, reason })
      await route.abort('failed')
    }
    if (!requestId) return reject('missing X-Request-Id')

    const fulfill = async (status, body, headers = {}) => route.fulfill({
      status,
      contentType: 'application/json',
      headers: { 'X-Request-Id': requestId, ...headers },
      body: rawJson(body),
    })

    if (method === 'GET' && path === '/api/v1/data-sources') {
      return fulfill(200, [SOURCE])
    }
    if (method === 'GET' && path === '/api/v1/data-sources/tushare_pro/apis') {
      return fulfill(200, [API])
    }
    if (method === 'GET' && path === '/api/v1/data-sources/tushare_pro/apis/daily/download-capabilities') {
      return fulfill(200, CAPABILITIES)
    }
    if (method === 'GET' && path === '/api/v1/download-tasks') {
      const query = Object.fromEntries(url.searchParams)
      if (!exactQuery(url, query) || query.page !== '1' || query.pageSize !== '20' ||
          Object.keys(query).some((key) => !['page', 'pageSize', 'submissionId'].includes(key))) {
        return reject('invalid task-list query')
      }
      const matchesSubmission = !query.submissionId ||
        state.task?.submissionId.toLowerCase() === query.submissionId.toLowerCase()
      const items = state.task && matchesSubmission ? [state.task] : []
      return fulfill(200, { page: 1, pageSize: 20, total: BigInt(items.length), items })
    }
    if (method === 'POST' && path === '/api/v1/download-tasks') {
      if (!recorded.contentType.startsWith('application/json')) return reject('submission content type')
      let body
      try { body = JSON.parse(rawBody) } catch { return reject('submission body is not JSON') }
      const keys = Object.keys(body).sort().join(',')
      if (keys !== 'apiName,mode,params,pluginId,submissionId' ||
          body.pluginId !== 'tushare_pro' || body.apiName !== 'daily' || body.mode !== 'RANGE' ||
          body.params?.ts_code !== '000001.SZ' || body.params?.start_date !== '20260901' ||
          body.params?.end_date !== '20260903' ||
          !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(body.submissionId)) {
        return reject('invalid RANGE submission')
      }
      if (state.task) return reject('duplicate fixture submission')
      state.task = task({ submissionId: body.submissionId })
      state.batches = succeededBatches()
      return fulfill(202, {
        requestId,
        taskId: TASK_ID,
        status: 'QUEUED',
        version: 9007199254740993n,
        createdAt: state.task.createdAt,
      }, { Location: `/api/v1/download-tasks/${TASK_ID}` })
    }

    const taskMatch = path.match(/^\/api\/v1\/download-tasks\/([^/]+)$/)
    if (method === 'GET' && taskMatch) {
      if (taskMatch[1] !== TASK_ID || !exactQuery(url, {})) return reject('invalid detail request')
      if (!state.task) return fulfill(404, errorBody(requestId, 'TASK_NOT_FOUND', '任务不存在', false))
      if (state.failNextDetail > 0) {
        state.failNextDetail -= 1
        return fulfill(500, errorBody(requestId, 'QUERY_FAILED', '受控查询暂时失败', true))
      }
      const response = state.task
      if (state.retryRunningDetailPending) {
        state.retryRunningDetailPending = false
        state.task = task({
          status: 'SUCCEEDED',
          version: 9007199254740995n,
          counts: counts({
            sourceRows: 9007199254740998n,
            insertedRows: 9007199254740998n,
            updatedRows: 0n,
          }),
          requestCount: MAX_INT64,
          runRequestCount: 4n,
        })
        state.batches = [
          state.batches[0],
          state.batches[1],
          batch(2, { attemptCount: 2, sourceRows: 3n, insertedRows: 3n }),
        ]
      }
      return fulfill(200, response)
    }

    const batchesMatch = path.match(/^\/api\/v1\/download-tasks\/([^/]+)\/batches$/)
    if (method === 'GET' && batchesMatch) {
      if (batchesMatch[1] !== TASK_ID || !exactQuery(url, { page: '1', pageSize: '20', includeSplit: 'false' })) {
        return reject('invalid leaf-batch request')
      }
      return fulfill(200, { page: 1, pageSize: 20, total: 3n, items: state.batches })
    }

    const controlMatch = path.match(/^\/api\/v1\/download-tasks\/([^/]+)\/(retry|resume)$/)
    if (method === 'POST' && controlMatch) {
      const [, id, action] = controlMatch
      if (id !== TASK_ID || !recorded.contentType.startsWith('application/json') || !exactQuery(url, {})) {
        return reject('invalid control request metadata')
      }
      if (action === 'retry' && state.scenario === 'partial') {
        if (rawBody !== '{"expectedVersion":9007199254740993}') return reject('retry did not preserve original bigint version')
        state.task = task({
          status: 'RUNNING',
          version: 9007199254740994n,
          counts: counts({
            runningBatches: 1n,
            succeededBatches: 2n,
            sourceRows: 9007199254740995n,
            insertedRows: 9007199254740995n,
            updatedRows: 0n,
          }),
          requestCount: MAX_INT64,
          runRequestCount: 4n,
          finishedAt: null,
        })
        state.batches = [
          state.batches[0],
          state.batches[1],
          batch(2, { status: 'RUNNING', attemptCount: 2, sourceRows: 0n, insertedRows: 0n, finishedAt: null }),
        ]
        state.retryRunningDetailPending = true
        return fulfill(202, {
          requestId,
          taskId: TASK_ID,
          status: 'RUNNING',
          version: 9007199254740994n,
          createdAt: state.task.createdAt,
        }, { Location: `/api/v1/download-tasks/${TASK_ID}` })
      }
      if (action === 'resume' && state.scenario === 'interrupted') {
        if (rawBody === '{"expectedVersion":9007199254740993}') {
          state.task = interruptedTask(9007199254740994n)
          state.failNextDetail = 1
          return fulfill(409, errorBody(
            requestId,
            'TASK_STATE_CONFLICT',
            '任务状态或版本已变化',
            false,
          ))
        }
        if (rawBody === '{"expectedVersion":9007199254740994}') {
          state.task = task({ version: 9007199254740995n })
          state.batches = succeededBatches()
          return fulfill(202, {
            requestId,
            taskId: TASK_ID,
            status: 'RUNNING',
            version: 9007199254740995n,
            createdAt: state.task.createdAt,
          }, { Location: `/api/v1/download-tasks/${TASK_ID}` })
        }
        return reject('resume used an unexpected version')
      }
      return reject('control is not permitted by the seeded scenario')
    }

    return reject('undeclared API route')
  })

  return fixture
}

export const test = base.extend({
  downloadTaskApi: async ({ context }, use) => {
    const fixture = await installDownloadTaskApi(context)
    await use(fixture)
    fixture.assertClean()
  },
})
