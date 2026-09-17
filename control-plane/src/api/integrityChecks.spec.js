import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

import { ClientError } from './errors.js'
import { configureHttp, http } from './http.js'
import {
  getIntegrityCapabilities,
  getIntegrityCheck,
  listIntegrityChecks,
  listIntegrityIssues,
  listIntegrityResults,
  submitIntegrityCheck,
} from './integrityChecks.js'

const DEFAULT_ADAPTER = http.defaults.adapter
const DEFAULT_BASE_URL = '/api/v1'
const DEFAULT_TIMEOUT = 130000
const CHECK_ID = '11111111-1111-4111-8111-111111111111'
const SUBMISSION_ID = '22222222-2222-4222-8222-222222222222'
const RESULT_ID = '33333333-3333-4333-8333-333333333333'
const examples = JSON.parse(readFileSync(
  resolve(process.cwd(), '../docs/contracts/integrity-check-examples.json'),
  'utf8',
)).examples

function example(name) {
  return structuredClone(examples.find((item) => item.name === name).value)
}

function response(config, data, { status = 200, headers = {} } = {}) {
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

function request() {
  return example('submission')
}

afterEach(() => {
  http.defaults.adapter = DEFAULT_ADAPTER
  configureHttp({ baseURL: DEFAULT_BASE_URL, timeout: DEFAULT_TIMEOUT })
})

it('uses all six paths and preserves submission and filter values', async () => {
  const requests = []
  http.defaults.adapter = async (config) => {
    requests.push(config)
    const requestId = config.headers.get('X-Request-Id')
    if (config.url.includes('integrity-capabilities')) return response(config, example('availableCapability'))
    if (config.method === 'post') {
      const receipt = example('createdReceipt')
      receipt.requestId = requestId
      return response(config, receipt, {
        status: 202,
        headers: { Location: `/api/v1/integrity-checks/${CHECK_ID}` },
      })
    }
    if (config.url === '/integrity-checks') return response(config, example('historicalTaskPage'))
    if (config.url.endsWith('/results')) {
      return response(config, { page: 1, pageSize: 37, total: '1', items: [example('failedDataResult')] })
    }
    if (config.url.endsWith('/issues')) {
      const page = { page: 2, pageSize: 100, total: '1', items: [example('undatedIssue')] }
      return response(config, JSON.stringify(page)
        .replace('"event_id":"9223372036854775807"', '"event_id":9223372036854775807'))
    }
    return response(config, example('runningDetail'))
  }

  await getIntegrityCapabilities('fixture')
  const submission = request()
  await submitIntegrityCheck(submission)
  await listIntegrityChecks({ pluginId: 'fixture', status: 'RUNNING' })
  await getIntegrityCheck(CHECK_ID.toUpperCase())
  await listIntegrityResults(CHECK_ID, {
    pageSize: 37, symbol: ' 历史 值 ', apiName: 'fixture_daily', overallStatus: 'FAIL',
  })
  const issuePage = await listIntegrityIssues(CHECK_ID, {
    page: 2, pageSize: 100, resultId: RESULT_ID, symbol: '000001.SZ', apiName: 'fixture_daily',
    type: 'DATE_SCOPE_UNRESOLVED', status: 'UNKNOWN', dateFrom: '2024-02-01', dateTo: '2024-02-29',
  })
  expect(issuePage.items[0].issue.businessKey.event_id).toBe(9223372036854775807n)

  expect(requests.map(({ method, url }) => [method, url])).toEqual([
    ['get', '/data-sources/fixture/integrity-capabilities'],
    ['post', '/integrity-checks'],
    ['get', '/integrity-checks'],
    ['get', `/integrity-checks/${CHECK_ID}`],
    ['get', `/integrity-checks/${CHECK_ID}/results`],
    ['get', `/integrity-checks/${CHECK_ID}/issues`],
  ])
  expect(JSON.parse(requests[1].data)).toEqual(submission)
  expect(http.getUri(requests[2])).toBe(
    '/api/v1/integrity-checks?page=1&pageSize=20&pluginId=fixture&status=RUNNING',
  )
  expect(http.getUri(requests[4])).toBe(
    `/api/v1/integrity-checks/${CHECK_ID}/results?page=1&pageSize=37&symbol=+%E5%8E%86%E5%8F%B2+%E5%80%BC+&apiName=fixture_daily&overallStatus=FAIL`,
  )
  expect(http.getUri(requests[5])).toContain(
    `/api/v1/integrity-checks/${CHECK_ID}/issues?page=2&pageSize=100&resultId=${RESULT_ID}`,
  )
})

it('accepts replay receipts and default and out-of-range empty pages', async () => {
  const requests = []
  http.defaults.adapter = async (config) => {
    requests.push(config)
    if (config.method === 'post') {
      const body = example('replayedReceipt')
      body.requestId = config.headers.get('X-Request-Id')
      return response(config, body, { headers: { Location: `/api/v1/integrity-checks/${CHECK_ID}` } })
    }
    if (config.url.endsWith('/results')) {
      return response(config, { page: 1, pageSize: 1, total: '0', items: [] })
    }
    return response(config, { page: 999, pageSize: 17, total: '0', items: [] })
  }

  await expect(submitIntegrityCheck(request())).resolves.toMatchObject({ status: 'COMPLETED' })
  await expect(listIntegrityChecks({ page: 999, pageSize: 17 })).resolves.toMatchObject({ total: 0n, items: [] })
  await expect(listIntegrityResults(CHECK_ID, { pageSize: 1 })).resolves.toMatchObject({ total: 0n, items: [] })
  expect(http.getUri(requests[1])).toBe('/api/v1/integrity-checks?page=999&pageSize=17')
})

it('rejects bad paths and criteria before sending', async () => {
  const requests = []
  http.defaults.adapter = async (config) => {
    requests.push(config)
    return response(config, {})
  }

  const calls = [
    () => getIntegrityCapabilities('Fixture'),
    () => getIntegrityCheck('bad'),
    () => listIntegrityChecks({ ignored: true }),
    () => listIntegrityResults(CHECK_ID, { overallStatus: 'COMPLETED' }),
    () => listIntegrityIssues(CHECK_ID, { dateFrom: '2024-03-01', dateTo: '2024-02-01' }),
    () => listIntegrityIssues(CHECK_ID, { pageSize: 101 }),
    () => submitIntegrityCheck({ ...request(), ignored: true }),
  ]
  for (const call of calls) await expect(call()).rejects.toBeInstanceOf(TypeError)
  expect(requests).toHaveLength(0)
})

it('rejects status, header, location and response identity mismatches', async () => {
  const cases = [
    async (config) => response(config, example('availableCapability'), { status: 201 }),
    async (config) => response(config, example('availableCapability'), { headers: { 'X-Request-Id': 'mismatch' } }),
    async (config) => {
      const body = example('availableCapability')
      body.pluginId = 'other_plugin'
      return response(config, body)
    },
  ]
  for (const adapter of cases) {
    http.defaults.adapter = adapter
    await expect(getIntegrityCapabilities('fixture')).rejects.toMatchObject({
      kind: 'INVALID_RESPONSE',
    })
  }

  http.defaults.adapter = async (config) => {
    const body = example('createdReceipt')
    body.requestId = config.headers.get('X-Request-Id')
    return response(config, body, { status: 202, headers: { Location: '/wrong' } })
  }
  await expect(submitIntegrityCheck(request())).rejects.toBeInstanceOf(ClientError)

  http.defaults.adapter = async (config) => response(config, { page: 2, pageSize: 20, total: '0', items: [] })
  await expect(listIntegrityChecks()).rejects.toBeInstanceOf(ClientError)

  http.defaults.adapter = async (config) => {
    const body = example('runningDetail')
    body.checkId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
    return response(config, body)
  }
  await expect(getIntegrityCheck(CHECK_ID)).rejects.toBeInstanceOf(ClientError)

  http.defaults.adapter = async (config) => response(config, {
    page: 1, pageSize: 20, total: '1', items: [{
      ...example('failedDataResult'), checkId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    }],
  })
  await expect(listIntegrityResults(CHECK_ID)).rejects.toBeInstanceOf(ClientError)
})

it('requires an exact zero-or-one submission recovery page', async () => {
  for (const page of [
    { page: 1, pageSize: 20, total: '1', items: [] },
    { page: 1, pageSize: 20, total: '0', items: [example('historicalTaskPage').items[0]] },
    { page: 1, pageSize: 20, total: '2', items: [example('historicalTaskPage').items[0]] },
  ]) {
    http.defaults.adapter = async (config) => response(config, page)
    await expect(listIntegrityChecks({ submissionId: SUBMISSION_ID }))
      .rejects.toBeInstanceOf(ClientError)
  }
})
