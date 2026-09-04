import { AxiosError } from 'axios'

import { listApis, listDataSources } from './dataSources.js'
import { getDataset, listDatasets, queryDataset } from './datasets.js'
import { downloadDataset } from './downloads.js'
import { ApiError, ClientError } from './errors.js'
import { configureHttp, http } from './http.js'

const DEFAULT_ADAPTER = http.defaults.adapter
const DEFAULT_BASE_URL = '/api/v1'
const DEFAULT_TIMEOUT = 130000

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
}

let requests

function requestId(config) {
  return config.headers.get('X-Request-Id')
}

function response(config, data) {
  return {
    data,
    status: 200,
    statusText: 'OK',
    headers: { 'X-Request-Id': requestId(config) },
    config,
    request: {},
  }
}

function respondWith(data) {
  http.defaults.adapter = async (config) => {
    requests.push(config)
    return response(config, data)
  }
}

function rejectWith(createFailure) {
  http.defaults.adapter = async (config) => {
    requests.push(config)
    const failure = createFailure(requestId(config))
    config.data = 'raw-request-secret'
    const rejectedResponse = failure.status
      ? {
          data: failure.body,
          status: failure.status,
          statusText: 'Error',
          headers: failure.headers ?? {
            'X-Request-Id': requestId(config),
          },
          config,
          request: { detail: 'raw-response-secret' },
        }
      : undefined

    throw new AxiosError(
      'raw-axios-secret',
      failure.code,
      config,
      { detail: 'raw-request-secret' },
      rejectedResponse,
    )
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

beforeEach(() => {
  requests = []
  respondWith({ ok: true })
})

afterEach(() => {
  http.defaults.adapter = DEFAULT_ADAPTER
  configureHttp({ baseURL: DEFAULT_BASE_URL, timeout: DEFAULT_TIMEOUT })
})

describe('API boundary', () => {
  it('keeps one atomically configurable Axios instance', () => {
    expect(http.defaults.baseURL).toBe(DEFAULT_BASE_URL)
    expect(http.defaults.timeout).toBe(DEFAULT_TIMEOUT)

    configureHttp({ baseURL: '/custom' })
    expect(http.defaults.baseURL).toBe('/custom')
    expect(http.defaults.timeout).toBe(DEFAULT_TIMEOUT)

    configureHttp({ timeout: 1000 })
    expect(http.defaults.timeout).toBe(1000)

    for (const options of [
      null,
      [],
      { baseURL: ' ' },
      { timeout: 0 },
      { timeout: 1.5 },
      { baseURL: '/must-not-apply', timeout: 0 },
    ]) {
      expect(() => configureHttp(options)).toThrow(TypeError)
      expect(http.defaults.baseURL).toBe('/custom')
      expect(http.defaults.timeout).toBe(1000)
    }
  })

  it('lists data sources and returns response data unchanged', async () => {
    const data = [{ pluginId: 'fixture', displayName: 'Fixture' }]
    respondWith(data)

    await expect(listDataSources()).resolves.toBe(data)
    expect(requests[0].method).toBe('get')
    expect(requests[0].url).toBe('/data-sources')
  })

  it('lists APIs with one encoded plugin path segment', async () => {
    await listApis('fixture/source')

    expect(requests[0].method).toBe('get')
    expect(requests[0].url).toBe('/data-sources/fixture%2Fsource/apis')
  })

  it('posts only the three download fields without changing dynamic params', async () => {
    const request = {
      pluginId: 'tushare_pro',
      apiName: 'daily',
      params: { trade_date: '20260904', ts_code: '000001.SZ' },
      ignored: 'page-state',
    }
    const snapshot = structuredClone(request)

    await downloadDataset(request)

    expect(requests[0].method).toBe('post')
    expect(requests[0].url).toBe('/downloads')
    expect(JSON.parse(requests[0].data)).toEqual({
      pluginId: 'tushare_pro',
      apiName: 'daily',
      params: { trade_date: '20260904', ts_code: '000001.SZ' },
    })
    expect(request).toEqual(snapshot)
  })

  it('lists datasets with one encoded plugin path segment', async () => {
    await listDatasets('fixture/source')

    expect(requests[0].method).toBe('get')
    expect(requests[0].url).toBe('/data-sources/fixture%2Fsource/datasets')
  })

  it('gets one dataset with independently encoded path segments', async () => {
    await getDataset('fixture/source', 'daily/detail')

    expect(requests[0].method).toBe('get')
    expect(requests[0].url).toBe(
      '/data-sources/fixture%2Fsource/datasets/daily%2Fdetail',
    )
  })

  it('queries records with only the seven camelCase parameters', async () => {
    const criteria = {
      tsCode: '',
      tradeDateFrom: '2026-09-01',
      tradeDateTo: undefined,
      annDateFrom: null,
      annDateTo: '2026-09-04',
      page: 2,
      pageSize: 100,
      ignored: 'page-state',
    }
    const snapshot = structuredClone(criteria)

    await queryDataset('fixture/source', 'daily/detail', criteria)

    expect(requests[0].method).toBe('get')
    expect(requests[0].url).toBe(
      '/data-sources/fixture%2Fsource/datasets/daily%2Fdetail/records',
    )
    expect(requests[0].params).toEqual({
      tsCode: '',
      tradeDateFrom: '2026-09-01',
      annDateFrom: null,
      annDateTo: '2026-09-04',
      page: 2,
      pageSize: 100,
    })
    expect(criteria).toEqual(snapshot)
  })

  it('generates a distinct UUID header and passes the configured timeout', async () => {
    await listDataSources()
    await listDataSources()
    configureHttp({ timeout: 125000 })
    await listDataSources()

    const ids = requests.map(requestId)
    const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
    expect(ids).toHaveLength(3)
    expect(new Set(ids).size).toBe(3)
    expect(ids.every((id) => uuid.test(id))).toBe(true)
    expect(requests.map((request) => request.timeout)).toEqual([
      DEFAULT_TIMEOUT,
      DEFAULT_TIMEOUT,
      125000,
    ])
  })

  it('normalizes every frozen server code and freezes field errors', async () => {
    let sourceFields

    for (const [code, [status, retryable]] of Object.entries(API_RULES)) {
      sourceFields = [{ field: 'trade_date', message: 'required' }]
      rejectWith((id) => ({
        code: 'ERR_BAD_RESPONSE',
        status,
        body: {
          requestId: id,
          code,
          message: 'safe server message',
          retryable,
          fieldErrors: sourceFields,
        },
      }))

      const error = await capture(listDataSources())
      expect(error).toBeInstanceOf(ApiError)
      expect(error).toMatchObject({
        requestId: requestId(requests.at(-1)),
        code,
        message: 'safe server message',
        retryable,
        fieldErrors: [{ field: 'trade_date', message: 'required' }],
      })
      expect(Object.isFrozen(error.fieldErrors)).toBe(true)
      expect(Object.isFrozen(error.fieldErrors[0])).toBe(true)
      sourceFields[0].message = 'changed'
      expect(error.fieldErrors[0].message).toBe('required')
      expect(error.cause).toBeUndefined()
      expect(error.config).toBeUndefined()
      expect(error.response).toBeUndefined()
    }
  })

  it('rejects malformed server envelopes without leaking response data', async () => {
    const valid = (id) => ({
      requestId: id,
      code: 'PARAM_REQUIRED',
      message: 'raw-server-secret',
      retryable: false,
      fieldErrors: [],
    })
    const failures = [
      (id) => ({ status: 400, body: { ...valid(id), code: 'UNKNOWN' } }),
      (id) => ({ status: 500, body: valid(id) }),
      (id) => ({ status: 400, body: { ...valid(id), retryable: true } }),
      (id) => {
        const { code, ...body } = valid(id)
        return { status: 400, body }
      },
      (id) => ({ status: 400, body: { ...valid(id), extra: true } }),
      (id) => ({
        status: 400,
        body: { ...valid(id), fieldErrors: [{ field: '', message: 'bad' }] },
      }),
      (id) => ({
        status: 400,
        body: valid(id),
        headers: { 'X-Request-Id': `${id}-mismatch` },
      }),
    ]

    for (const failure of failures) {
      rejectWith((id) => ({ code: 'ERR_BAD_RESPONSE', ...failure(id) }))
      const error = await capture(listDataSources())

      expect(error).toBeInstanceOf(ClientError)
      expect(error).toMatchObject({
        kind: 'INVALID_RESPONSE',
        message: '服务返回了无法识别的响应。',
        retryable: false,
        requestId: requestId(requests.at(-1)),
      })
      expect(error.message).not.toContain('raw-server-secret')
      expect(error.cause).toBeUndefined()
      expect(error.config).toBeUndefined()
      expect(error.response).toBeUndefined()
    }
  })

  it('normalizes both Axios timeout codes without retaining the cause', async () => {
    for (const code of ['ECONNABORTED', 'ETIMEDOUT']) {
      rejectWith(() => ({ code }))
      const error = await capture(listDataSources())

      expect(error).toBeInstanceOf(ClientError)
      expect(error).toMatchObject({
        kind: 'TIMEOUT',
        message: '请求超时，请稍后重试。',
        retryable: true,
        requestId: requestId(requests.at(-1)),
      })
      expect(error.stack).not.toContain('raw-axios-secret')
      expect(error.cause).toBeUndefined()
      expect(error.config).toBeUndefined()
    }
  })

  it('separates network and unexpected failures with fixed safe messages', async () => {
    const failures = [
      ['ERR_NETWORK', 'NETWORK', '无法连接服务，请检查网络后重试。', true],
      ['ERR_CANCELED', 'UNEXPECTED', '请求未能发送。', false],
    ]

    for (const [code, kind, message, retryable] of failures) {
      rejectWith(() => ({ code }))
      const error = await capture(listDataSources())

      expect(error).toBeInstanceOf(ClientError)
      expect(error).toMatchObject({
        kind,
        message,
        retryable,
        requestId: requestId(requests.at(-1)),
      })
      expect(error.stack).not.toContain('raw-axios-secret')
      expect(error.cause).toBeUndefined()
      expect(error.config).toBeUndefined()
      expect(error.request).toBeUndefined()
      expect(error.response).toBeUndefined()
    }
  })
})
