import { readFileSync } from 'node:fs'

const examples = JSON.parse(readFileSync(new URL('../../docs/contracts/integrity-check-examples.json', import.meta.url), 'utf8')).examples
export const example = name => structuredClone(examples.find(item => item.name === name).value)
export const CHECK_ID = '11111111-1111-4111-8111-111111111111'
export const PENDING_KEY = 'tensor.integrityChecks.pending.v1'

export function capability() {
  const value = example('availableCapability')
  value.pluginId = 'tushare_pro'
  value.apis = [
    ['daily', '日线行情', 'STOCK_DATE', '交易日期'],
    ['stock_basic', '股票基本信息', 'STOCK_SNAPSHOT', '当前快照'],
    ['trade_cal', '交易日历', 'NON_STOCK', '不适用'],
    ['unimplemented', '尚无覆盖规则的接口', null, null],
  ].map(([apiName, displayName, scopeKind, dateLabel]) => {
    const api = example('availableCapability').apis[0]
    api.apiName = apiName
    api.displayName = displayName
    api.descriptor = scopeKind ? {
      ...api.descriptor, datasetKey: { pluginId: value.pluginId, apiName }, scopeKind, dateLabel,
      symbolField: scopeKind === 'NON_STOCK' ? null : 'ts_code',
      dateField: scopeKind === 'STOCK_DATE' ? 'trade_date' : null,
      dependencies: scopeKind === 'STOCK_DATE' ? [{ datasetKey: { pluginId: value.pluginId, apiName: 'trade_cal' }, columns: ['cal_date', 'is_open'], purpose: '核对参考交易日历' }] : [],
      rules: scopeKind === 'NON_STOCK' ? [] : [{ ...api.descriptor.rules[0], ruleId: `tushare.coverage.${apiName}` }],
      limitations: scopeKind === 'STOCK_DATE' ? ['生产预期全集尚未证实，覆盖结论可能无法判定。'] : [],
    } : null
    return api
  })
  return value
}

export function submission() {
  return { ...example('submission'), pluginId: 'tushare_pro', apiNames: ['daily', 'stock_basic', 'trade_cal', 'unimplemented'] }
}

export function summary(request = submission()) {
  const task = example('historicalTaskPage').items[0]
  return { ...task, pluginId: request.pluginId, submissionId: request.submissionId,
    originalRequest: request, capabilityHash: request.capabilityHash,
    scope: { ...task.scope, pluginId: request.pluginId, symbols: request.symbols, startDate: request.startDate, endDate: request.endDate, apiNames: request.apiNames },
    status: 'COMPLETED', plannedUnits: request.symbols.length * 3 + 1, finishedAt: task.updatedAt,
  }
}

export const SECOND_CHECK_ID = '55555555-5555-4555-8555-555555555555'
export const RESULT_ID = '33333333-3333-4333-8333-333333333333'

export function savedReport(request = { ...submission(), apiNames: ['daily'] }, checkId = CHECK_ID) {
  const detail = { ...example('runningDetail'), ...summary(request), checkId,
    plannedUnits: 1, completedUnits: '1', errorUnits: '0', notRunUnits: '0', overallStatus: 'FAIL',
    statusCounts: { PASS: '0', FAIL: '1', WARN: '0', UNKNOWN: '0', NOT_APPLICABLE: '0' },
  }
  const result = example('failedDataResult')
  result.checkId = checkId
  const report = result.report
  report.scope = { ...report.scope, datasetKey: { pluginId: request.pluginId, apiName: request.apiNames[0] }, symbol: request.symbols[0], startDate: request.startDate, endDate: request.endDate }
  report.descriptor.datasetKey = { ...report.scope.datasetKey }
  report.descriptor.rules[0].displayName = '保存的覆盖规则'
  report.descriptor.rules[0].version = '7'
  report.ruleResults[0].descriptor = structuredClone(report.descriptor.rules[0])
  report.evidence[0].summary = '保存时有20个预期键，命中19个；额外记录不能抵消缺失。'
  report.evidence[0].ruleVersion = '7'
  report.ruleResults[0].evidence = structuredClone(report.evidence)
  const unknown = example('undatedIssue')
  unknown.issue.apiName = request.apiNames[0]
  unknown.issue.symbol = request.symbols[0]
  unknown.issue.businessKey.is_final = false
  unknown.issue.message = '无法定位主日期，请保留整个检查范围。'
  const missing = structuredClone(unknown)
  missing.issueId = '1'
  missing.ruleId = report.descriptor.rules[0].ruleId
  missing.ruleVersion = '7'
  missing.issue = { ...missing.issue, type: 'MISSING', status: 'FAIL', date: '2024-02-20', field: null,
    reasonCode: 'MISSING_KEYS', message: '确认缺失2024-02-20的数据',
    businessKey: { ts_code: request.symbols[0], trade_date: '2024-02-20', event_id: '9223372036854775807', amount: '1.230000000000000000', is_final: false, revision: 2, optional: null },
  }
  missing.issue.evidence = structuredClone(report.evidence)
  return { detail, results: [result], issues: [missing, unknown] }
}

export async function installIntegrityApi(page) {
  const state = {
    capability: capability(), sources: [{ pluginId: 'tushare_pro', displayName: 'Tushare Pro', description: '本地数据', enabled: true, credentialConfigured: false, downloadAvailable: false, unavailableReason: '未配置下载 Token' }],
    sourceFailure: false, capabilityFailure: false, categoryFailure: false, historyFailure: false,
    recoveryFailure: false, outcome: 'success', accepted: null, history: [], total: null,
    posts: [], requests: [], postGate: null, categoryGate: null,
    reports: { [CHECK_ID]: savedReport() }, nextCheckId: CHECK_ID,
    detailFailure: false, resultsFailure: false, issuesFailure: false, detailGate: null, resultsGate: null, issuesGate: null,
  }
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), url = new URL(request.url()), method = request.method()
    const requestId = request.headers()['x-request-id']
    const query = Object.fromEntries(url.searchParams)
    state.requests.push({ method, path: url.pathname, query, requestId })
    const reply = (body, status = 200, headers = {}) => route.fulfill({ status, contentType: 'application/json', headers: { 'X-Request-Id': requestId, ...headers }, body: JSON.stringify(body) })
    const failure = (code = 'QUERY_FAILED', status = 500) => reply({ requestId, code, message: '暂时无法读取，请重试。', retryable: status >= 500, fieldErrors: [] }, status)
    if (url.pathname === '/api/v1/data-sources') return state.sourceFailure ? failure() : reply(state.sources)
    if (url.pathname.endsWith('/integrity-capabilities')) return state.capabilityFailure ? failure() : reply(state.capability)
    if (url.pathname.endsWith('/apis')) {
      if (state.categoryGate) await state.categoryGate
      return state.categoryFailure ? failure() : reply(state.capability.apis.map((api, i) => ({ apiName: api.apiName, category: i === 0 ? '行情数据' : '基础数据' })))
    }
    if (url.pathname === '/api/v1/integrity-checks' && method === 'POST') {
      const body = request.postDataJSON()
      state.posts.push(body)
      if (state.postGate) await state.postGate
      if (state.outcome === 'rejected') return failure('INTEGRITY_LIMIT_EXCEEDED', 400)
      if (state.outcome === 'definition-changed') return failure('INTEGRITY_DEFINITION_CHANGED', 409)
      if (state.outcome === 'lost-empty') return route.abort('failed')
      state.accepted = { ...summary(body), checkId: state.nextCheckId }
      state.reports[state.nextCheckId] = savedReport(body, state.nextCheckId)
      if (state.outcome === 'lost-accepted') return route.abort('failed')
      return reply({ ...example('createdReceipt'), checkId: state.nextCheckId, requestId, submissionId: body.submissionId, pluginId: body.pluginId, plannedUnits: state.accepted.plannedUnits }, 202, { Location: `/api/v1/integrity-checks/${state.nextCheckId}` })
    }
    if (url.pathname === '/api/v1/integrity-checks' && method === 'GET') {
      if (query.submissionId) {
        if (state.recoveryFailure) return failure()
        const items = state.accepted?.submissionId === query.submissionId ? [state.accepted] : []
        return reply({ page: Number(query.page), pageSize: Number(query.pageSize), total: String(items.length), items })
      }
      if (state.historyFailure) return failure()
      const matches = state.history.filter(task => (!query.status || task.status === query.status) && (!query.pluginId || task.pluginId === query.pluginId))
      return reply({ page: Number(query.page), pageSize: Number(query.pageSize), total: state.total ?? String(matches.length), items: matches.slice((Number(query.page) - 1) * Number(query.pageSize), Number(query.page) * Number(query.pageSize)) })
    }
    const match = url.pathname.match(/^\/api\/v1\/integrity-checks\/([^/]+)(?:\/(results|issues))?$/)
    if (match && method === 'GET' && state.reports[match[1]]) {
      const data = structuredClone(state.reports[match[1]])
      const kind = match[2] || 'detail'
      const fails = state[`${kind}Failure`]
      if (state[`${kind}Gate`]) await state[`${kind}Gate`]
      if (fails) return failure()
      if (kind === 'detail') return reply(data.detail)
      let items = data[kind]
      if (kind === 'results') items = items.filter(({ report }) =>
        (!query.symbol || report.scope.symbol === query.symbol) &&
        (!query.apiName || report.scope.datasetKey.apiName === query.apiName) &&
        (!query.overallStatus || report.overallStatus === query.overallStatus))
      else items = items.filter(({ resultId, issue }) =>
        (!query.resultId || resultId === query.resultId) &&
        (!query.type || issue.type === query.type) && (!query.status || issue.status === query.status) &&
        (!query.dateFrom || issue.date !== null && issue.date >= query.dateFrom) &&
        (!query.dateTo || issue.date !== null && issue.date <= query.dateTo))
      const pageNumber = Number(query.page), pageSize = Number(query.pageSize)
      return reply({ page: pageNumber, pageSize, total: data[`${kind}Total`] ?? String(items.length), items: items.slice((pageNumber - 1) * pageSize, pageNumber * pageSize) })
    }
    return reply({ requestId, code: 'INTEGRITY_CHECK_NOT_FOUND', message: '检查不存在。', retryable: false, fieldErrors: [] }, 404)
  })
  return state
}
