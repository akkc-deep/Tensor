import { AxiosError } from 'axios'

import retryTasks from '../test/fixtures/retry-tasks.json'
import rangeResults from '../test/fixtures/range-results.json'
import { ApiError, ClientError } from './errors.js'
import { configureHttp, http } from './http.js'
import { executeRetryTask, getRetryTask, listRetryTasks } from './retryTasks.js'

const DEFAULT_ADAPTER = http.defaults.adapter
const DEFAULT_BASE_URL = '/api/v1'
const DEFAULT_TIMEOUT = 130000
const TASK_ID = retryTasks.detail.taskId
let requests

function requestId(config) {
  return config.headers.get('X-Request-Id')
}

function ok(config, data, headers = { 'X-Request-Id': data.requestId }) {
  return { data, status: 200, statusText: 'OK', headers, config, request: {} }
}

function respondWith(data, headers) {
  http.defaults.adapter = async (config) => {
    requests.push(config)
    const body = typeof data === 'function' ? data(config) : structuredClone(data)
    const responseHeaders = typeof headers === 'function' ? headers(config, body) : headers
    return ok(config, body, responseHeaders ?? { 'X-Request-Id': body.requestId })
  }
}

function rejectWith(createFailure) {
  http.defaults.adapter = async (config) => {
    requests.push(config)
    const failure = createFailure(requestId(config))
    throw new AxiosError('raw-secret', failure.code ?? 'ERR_BAD_RESPONSE', config, {}, {
      data: failure.body,
      status: failure.status,
      statusText: 'Error',
      headers: failure.headers ?? { 'X-Request-Id': failure.body.requestId },
      config,
      request: {},
    })
  }
}

async function capture(promise) {
  try {
    await promise
    throw new Error('expected rejection')
  } catch (error) {
    return error
  }
}

function page(overrides = {}) {
  return { ...structuredClone(retryTasks.page), ...overrides }
}

function detail(overrides = {}) {
  return { ...structuredClone(retryTasks.detail), ...overrides }
}

beforeEach(() => {
  requests = []
  respondWith(retryTasks.empty)
})

afterEach(() => {
  http.defaults.adapter = DEFAULT_ADAPTER
  configureHttp({ baseURL: DEFAULT_BASE_URL, timeout: DEFAULT_TIMEOUT })
})

describe('retry task Axios boundary', () => {
  it('sends independent optional filters and explicit pagination without mutating input', async () => {
    const criteria = { pluginId: 'tushare_pro', apiName: ' ', page: 2, pageSize: 50, ignored: 'x' }
    const snapshot = structuredClone(criteria)
    const body = page({ page: 2, pageSize: 50, totalElements: 51, totalPages: 2, items: [retryTasks.page.items[0]] })
    respondWith(body)

    await expect(listRetryTasks(criteria)).resolves.toEqual(body)
    expect(requests).toHaveLength(1)
    expect(requests[0].method).toBe('get')
    expect(requests[0].url).toBe('/retry-tasks')
    expect(requests[0].params).toEqual({ pluginId: 'tushare_pro', page: 2, pageSize: 50 })
    expect(criteria).toEqual(snapshot)

    respondWith(retryTasks.empty)
    await listRetryTasks({ apiName: 'daily' })
    await listRetryTasks()
    expect(requests[1].params).toEqual({ apiName: 'daily', page: 1, pageSize: 20 })
    expect(requests[2].params).toEqual({ page: 1, pageSize: 20 })
  })

  it('normalizes an uppercase UUID and executes with an undefined zero-byte Axios body', async () => {
    respondWith((config) => ({ ...structuredClone(retryTasks.detail), requestId: requestId(config) }))
    await getRetryTask(TASK_ID.toUpperCase())
    expect(requests[0]).toMatchObject({ method: 'get', url: `/retry-tasks/${TASK_ID}` })

    respondWith((config) => ({
      ...structuredClone(rangeResults.PARTIAL),
      requestId: requestId(config),
      taskId: TASK_ID,
    }))
    await executeRetryTask(TASK_ID.toUpperCase())
    expect(requests[1]).toMatchObject({ method: 'post', url: `/retry-tasks/${TASK_ID}/execute` })
    expect(requests[1].data).toBeUndefined()
    expect(requests[1].params).toBeUndefined()
  })

  it('accepts exact summary/detail shapes, original range statuses and complete selector identities', async () => {
    for (const valid of [
      retryTasks.detail,
      retryTasks.remaining,
      retryTasks.stocks,
      retryTasks.original,
      retryTasks.legacy,
      retryTasks.blocked,
    ]) {
      respondWith(valid)
      await expect(getRetryTask(valid.taskId)).resolves.toEqual(valid)
    }
  })

  it('rejects malformed task wire values with the outgoing request id and no body echo', async () => {
    const invalid = [
      detail({ extra: 'response-secret' }),
      detail({ taskId: ['7a5d2e8e-7bc2-4d16-8942-cb9e45d5d1f8'] }),
      detail({ pluginId: ['tushare_pro'] }),
      detail({ apiName: ['daily'] }),
      detail({ failedItemCount: '2' }),
      detail({ createdAt: '2026-02-29T01:00:00.000Z' }),
      detail({ originalDateRange: { startDate: '20260229', endDate: '20260301' } }),
      detail({ originalDateRange: null }),
      detail({ taskParams: { page_size: '20' } }),
      detail({ taskParams: { access_token: 'response-secret' } }),
      detail({ taskParams: { nested: { value: 'x' } } }),
      detail({ canExecute: false, executionBlocker: null }),
      detail({ retrying: true, canExecute: false, executionBlocker: { code: 'RETRY_TASK_INVALID', message: 'x' } }),
      detail({ failedScopes: [retryTasks.detail.failedScopes[1], retryTasks.detail.failedScopes[0]] }),
      detail({ items: [retryTasks.detail.items[1], retryTasks.detail.items[0]] }),
    ]

    for (const body of invalid) {
      respondWith((config) => ({ ...body, requestId: requestId(config) }))
      const error = await capture(getRetryTask(TASK_ID))
      expect(error).toBeInstanceOf(ClientError)
      expect(error).toMatchObject({ kind: 'INVALID_RESPONSE', requestId: requestId(requests.at(-1)) })
      expect(error.message).not.toContain('response-secret')
    }
  })

  it('enforces page totals, returned-page size, uniqueness and server sort order', async () => {
    const first = structuredClone(retryTasks.page.items[0])
    const second = { ...structuredClone(first), taskId: '6a5d2e8e-7bc2-4d16-8942-cb9e45d5d1f8', updatedAt: '2026-09-09T01:59:59.999Z' }
    const validLastPage = page({ page: 2, pageSize: 20, totalElements: 21, totalPages: 2, items: [second] })
    respondWith(validLastPage)
    await expect(listRetryTasks({ page: 99 })).resolves.toEqual(validLastPage)

    const invalid = [
      page({ totalPages: 2 }),
      page({ page: 2 }),
      page({ pageSize: 50 }),
      page({ totalElements: '1' }),
      page({ totalElements: 2, totalPages: 1, items: [first, first] }),
      page({ totalElements: 2, totalPages: 1, items: [second, first] }),
    ]
    for (const body of invalid) {
      respondWith(body)
      await expect(listRetryTasks()).rejects.toMatchObject({ kind: 'INVALID_RESPONSE' })
    }
    respondWith(retryTasks.empty)
    await expect(listRetryTasks()).resolves.toEqual(retryTasks.empty)
  })

  it('checks response request ids and task identity for every normal response', async () => {
    const cases = [
      () => listRetryTasks(),
      () => getRetryTask(TASK_ID),
      () => executeRetryTask(TASK_ID),
    ]
    const bodies = [retryTasks.empty, retryTasks.detail, { ...rangeResults.SUCCESS, taskId: null }]
    for (let index = 0; index < cases.length; index += 1) {
      respondWith((config) => ({ ...structuredClone(bodies[index]), requestId: `${requestId(config)}-body` }),
        (config) => ({ 'X-Request-Id': requestId(config) }))
      const error = await capture(cases[index]())
      expect(error).toMatchObject({
        kind: 'INVALID_RESPONSE',
        requestId: requestId(requests.at(-1)),
      })
    }

    respondWith((config) => ({ ...detail(), requestId: requestId(config), taskId: '8a5d2e8e-7bc2-4d16-8942-cb9e45d5d1f8' }))
    await expect(getRetryTask(TASK_ID)).rejects.toMatchObject({ kind: 'INVALID_RESPONSE' })

    respondWith((config) => ({ ...detail(), requestId: requestId(config), taskId: TASK_ID.toUpperCase() }))
    await expect(getRetryTask(TASK_ID)).rejects.toMatchObject({ kind: 'INVALID_RESPONSE' })

    respondWith((config) => ({ ...rangeResults.PARTIAL, requestId: requestId(config), taskId: '8a5d2e8e-7bc2-4d16-8942-cb9e45d5d1f8' }))
    await expect(executeRetryTask(TASK_ID)).rejects.toMatchObject({ kind: 'INVALID_RESPONSE' })

    respondWith((config) => ({ ...rangeResults.PARTIAL, requestId: requestId(config), taskId: TASK_ID.toUpperCase() }))
    await expect(executeRetryTask(TASK_ID)).rejects.toMatchObject({ kind: 'INVALID_RESPONSE' })
  })

  it('rejects a mismatched task id in an Axios ApiError snapshot without replaying', async () => {
    rejectWith((id) => ({
      status: 500,
      body: {
        requestId: id,
        code: 'COMMIT_UNCONFIRMED',
        message: '安全说明',
        retryable: false,
        fieldErrors: [],
        downloadResult: {
          ...structuredClone(rangeResults.UNCONFIRMED),
          requestId: id,
          taskId: '8a5d2e8e-7bc2-4d16-8942-cb9e45d5d1f8',
        },
      },
    }))
    await expect(executeRetryTask(TASK_ID)).rejects.toMatchObject({ kind: 'INVALID_RESPONSE' })
    expect(requests).toHaveLength(1)
  })

  it('preserves no-snapshot busy and not-found ApiErrors from the real Axios interceptor', async () => {
    for (const [code, status, retryable] of [
      ['DOWNLOAD_BUSY', 409, true],
      ['RETRY_TASK_NOT_FOUND', 404, false],
    ]) {
      rejectWith((id) => ({
        status,
        body: { requestId: id, code, message: '安全说明', retryable, fieldErrors: [] },
      }))
      const error = await capture(executeRetryTask(TASK_ID))
      expect(error).toBeInstanceOf(ApiError)
      expect(error).toMatchObject({ code, retryable, downloadResult: null })
    }
    expect(requests).toHaveLength(2)
  })

  it('preserves all four real Axios client error boundaries without replaying', async () => {
    const cases = [
      ['ECONNABORTED', 'TIMEOUT'],
      ['ERR_NETWORK', 'NETWORK'],
      ['ERR_BAD_RESPONSE', 'INVALID_RESPONSE'],
      ['ERR_OTHER', 'UNEXPECTED'],
    ]
    for (const [code, kind] of cases) {
      http.defaults.adapter = async (config) => {
        requests.push(config)
        if (kind === 'INVALID_RESPONSE') {
          throw new AxiosError('secret', code, config, {}, {
            data: { unsafe: 'response-secret' }, status: 500, statusText: 'Error',
            headers: { 'X-Request-Id': requestId(config) }, config, request: {},
          })
        }
        throw new AxiosError('secret', code, config)
      }
      const error = await capture(executeRetryTask(TASK_ID))
      expect(error).toBeInstanceOf(ClientError)
      expect(error).toMatchObject({ kind, requestId: requestId(requests.at(-1)) })
      expect(error.message).not.toContain('secret')
    }
    expect(requests).toHaveLength(4)
  })
})
