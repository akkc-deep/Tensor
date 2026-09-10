import { readFileSync } from 'node:fs'

// Contract matches the current catalog in tushare-metadata.spec.js. It stays
// independent from the production metadata files consumed below.
export const PARAMETER = {
  list_status: { name: 'list_status', label: '上市状态', type: 'ENUM', required: true, allowedValues: ['L', 'P', 'D'] },
  exchange: { name: 'exchange', label: '交易所', type: 'ENUM', required: true, allowedValues: ['SSE', 'SZSE', 'BSE'] },
  exchange_id: { name: 'exchange_id', label: '交易所', type: 'ENUM', required: true, allowedValues: ['SSE', 'SZSE', 'BSE'] },
  start_date: { name: 'start_date', label: '开始日期', type: 'DATE_RANGE_MEMBER', required: true, relatedParameter: 'end_date' },
  end_date: { name: 'end_date', label: '结束日期', type: 'DATE_RANGE_MEMBER', required: true, relatedParameter: 'start_date' },
  trade_date: { name: 'trade_date', label: '交易日期', type: 'DATE', required: true },
  ann_date: { name: 'ann_date', label: '公告日期', type: 'DATE', required: true },
  ts_code: { name: 'ts_code', label: '股票代码', type: 'TS_CODE', required: true },
}

export const EXPECTED_ROWS = [
  ['stock_basic', '股票基础信息', 'basic_organization', 'snapshot', ['list_status'], 10],
  ['stock_company', '上市公司基本信息', 'basic_organization', 'snapshot', ['exchange'], 18],
  ['income', '利润表', '财务与披露', 'ann_date', ['ts_code', 'ann_date'], 85],
  ['balancesheet', '资产负债表', '财务与披露', 'ann_date', ['ts_code', 'ann_date'], 152],
  ['cashflow', '现金流量表', '财务与披露', 'ann_date', ['ts_code', 'ann_date'], 97],
  ['fina_indicator', '财务指标', '财务与披露', 'ann_date', ['ts_code', 'ann_date'], 108],
  ['fina_audit', '财务审计意见', '财务与披露', 'ann_date', ['ts_code', 'ann_date'], 7],
  ['fina_mainbz', '主营业务构成', '财务与披露', 'ann_date', ['ts_code', 'ann_date'], 8],
  ['stk_rewards', '管理层薪酬与持股', '股东与治理', 'snapshot', ['ts_code'], 7],
  ['stk_holdernumber', '股东户数', '股东与治理', 'snapshot', ['ts_code'], 4],
  ['trade_cal', '交易日历', 'basic_organization', 'date_range', ['exchange', 'start_date', 'end_date'], 4],
  ['margin', '融资融券汇总', '交易与资金', 'trade_date', ['exchange_id', 'trade_date'], 9],
  ['daily', '日线行情', '行情与估值', 'trade_date', ['trade_date'], 11],
  ['weekly', '周线行情', '行情与估值', 'trade_date', ['trade_date'], 11],
  ['monthly', '月线行情', '行情与估值', 'trade_date', ['trade_date'], 11],
  ['adj_factor', '复权因子', '行情与估值', 'trade_date', ['trade_date'], 3],
  ['suspend_d', '每日停复牌信息', '行情与估值', 'trade_date', ['trade_date'], 4],
  ['daily_basic', '每日估值与市场指标', '行情与估值', 'trade_date', ['trade_date'], 18],
  ['moneyflow', '个股资金流向', '交易与资金', 'trade_date', ['trade_date'], 20],
  ['stk_limit', '每日涨跌停价格', '行情与估值', 'trade_date', ['trade_date'], 4],
  ['top_list', '龙虎榜每日明细', '交易与资金', 'trade_date', ['trade_date'], 15],
  ['margin_detail', '融资融券交易明细', '交易与资金', 'trade_date', ['trade_date'], 10],
  ['block_trade', '大宗交易', '交易与资金', 'trade_date', ['trade_date'], 7],
  ['slb_len', '转融通期限与规模', '互联互通与转融通', 'trade_date', ['trade_date'], 6],
  ['slb_sec', '转融通证券汇总', '互联互通与转融通', 'trade_date', ['trade_date'], 7],
  ['slb_sec_detail', '转融通证券明细', '互联互通与转融通', 'trade_date', ['trade_date'], 6],
  ['forecast', '业绩预告', '财务与披露', 'ann_date', ['ann_date'], 13],
  ['express', '业绩快报', '财务与披露', 'ann_date', ['ann_date'], 15],
  ['dividend', '分红送股', '公司行动', 'ann_date', ['ann_date'], 14],
  ['disclosure_date', '财报披露计划', '财务与披露', 'ann_date', ['ann_date'], 5],
  ['repurchase', '股票回购', '公司行动', 'ann_date', ['ann_date'], 9],
  ['stk_holdertrade', '股东增减持', '股东与治理', 'ann_date', ['ann_date'], 11],
  ['top10_holders', '前十大股东', '股东与治理', 'ann_date', ['ann_date'], 9],
  ['top10_floatholders', '前十大流通股东', '股东与治理', 'ann_date', ['ann_date'], 9],
  ['new_share', 'IPO 新股发行信息', 'basic_organization', 'date_range', ['start_date', 'end_date'], 12],
  ['stk_managers', '上市公司管理层信息', 'basic_organization', 'snapshot', [], 11],
  ['pledge_stat', '股权质押统计', '股东与治理', 'snapshot', [], 7],
  ['pledge_detail', '股权质押明细', '股东与治理', 'snapshot', [], 14],
  ['index_classify', '行业指数分类', 'basic_organization', 'snapshot', [], 7],
  ['index_member_all', '行业分级与完整成分', 'basic_organization', 'snapshot', [], 11],
]

export function filterDescriptor(field) {
  return field === 'ts_code'
    ? { field, operator: 'EQ', controlType: 'TEXT' }
    : { field, operator: 'BETWEEN', controlType: 'DATE_RANGE' }
}

export function expectedFilters() {
  const groups = [
    [[], 'trade_cal index_classify'],
    [['ts_code'], 'stock_basic stock_company new_share index_member_all fina_mainbz pledge_stat'],
    [['trade_date'], 'margin slb_len'],
    [['ts_code', 'trade_date'], 'daily weekly monthly adj_factor suspend_d daily_basic stk_limit moneyflow margin_detail top_list block_trade slb_sec slb_sec_detail'],
    [['ts_code', 'ann_date'], 'stk_managers income balancesheet cashflow fina_indicator fina_audit express forecast disclosure_date dividend repurchase stk_rewards stk_holdernumber stk_holdertrade top10_holders top10_floatholders pledge_detail'],
  ]
  const result = new Map()
  for (const [fields, names] of groups) {
    for (const name of names.split(' ')) {
      if (result.has(name)) throw new Error(`Duplicate filter fixture: ${name}`)
      result.set(name, fields.map(filterDescriptor))
    }
  }
  return result
}

function requireFixture(condition, message) {
  if (!condition) throw new Error(`UI fixture check failed: ${message}`)
}

function parseScalar(value) {
  if (value === 'true') return true
  if (value === 'false') return false
  if (/^\d+$/.test(value)) return Number(value)
  if (value.startsWith('[') && value.endsWith(']')) {
    const contents = value.slice(1, -1).trim()
    return contents ? contents.split(',').map((item) => item.trim()) : []
  }
  return value
}

const COLUMN_KEYS = ['name', 'label', 'logicalType', 'nullable', 'displayOrder', 'length', 'precision', 'scale', 'longText', 'allowedValues']

function parseColumn(line, apiName) {
  const match = line.match(/^\s*- \{ (.*) \}\s*$/)
  requireFixture(match, `${apiName} column uses the supported inline-map form`)
  const column = {}
  const keyPattern = COLUMN_KEYS.join('|')
  const pairPattern = new RegExp(`(?:^|, )(${keyPattern}): (.*?)(?=, (?:${keyPattern}): |$)`, 'g')
  for (const pair of match[1].matchAll(pairPattern)) column[pair[1]] = parseScalar(pair[2])
  requireFixture(Object.keys(column).length >= 5, `${apiName} column contains required properties`)
  requireFixture(typeof column.name === 'string', `${apiName} column name`)
  requireFixture(typeof column.label === 'string', `${apiName}.${column.name} label`)
  requireFixture(typeof column.logicalType === 'string', `${apiName}.${column.name} logicalType`)
  requireFixture(typeof column.nullable === 'boolean', `${apiName}.${column.name} nullable`)
  requireFixture(Number.isInteger(column.displayOrder), `${apiName}.${column.name} displayOrder`)
  return column
}

function loadColumns(apiName, filename, expectedCount) {
  const sample = JSON.parse(readFileSync(new URL(`../../docs/data-template/${filename}`, import.meta.url), 'utf8'))
  requireFixture(sample.api_name === apiName, `${apiName} sample identity`)
  requireFixture(Array.isArray(sample.fields), `${apiName} sample fields`)
  requireFixture(sample.fields.length === expectedCount, `${apiName} expected sample field count`)

  const yaml = readFileSync(new URL(`../../data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/${apiName}.yaml`, import.meta.url), 'utf8')
  const lines = yaml.split('\n')
  const start = lines.findIndex((line) => line === 'columns:')
  const end = lines.findIndex((line, index) => index > start && !line.startsWith('  '))
  requireFixture(start >= 0 && end > start, `${apiName} columns block`)
  const columns = lines.slice(start + 1, end).filter((line) => line.trim()).map((line) => parseColumn(line, apiName))
  requireFixture(columns.length === expectedCount, `${apiName} YAML column count`)
  requireFixture(columns.map(({ name }) => name).join('\0') === sample.fields.join('\0'), `${apiName} sample/YAML field order`)
  columns.forEach((column, index) => requireFixture(column.displayOrder === index, `${apiName}.${column.name} displayOrder`))
  return columns
}

const FILTERS = expectedFilters()
const manifest = JSON.parse(readFileSync(new URL('../../docs/data-template/manifest.json', import.meta.url), 'utf8'))
requireFixture(Array.isArray(manifest.interfaces) && manifest.interfaces.length === 40, 'manifest has 40 interfaces')
const manifestByName = new Map(manifest.interfaces.map((entry) => [entry.api_name, entry]))
requireFixture(manifestByName.size === 40, 'manifest API names are unique')
requireFixture(EXPECTED_ROWS.length === 40, 'literal matrix has 40 interfaces')

export const EXPECTED = new Map(EXPECTED_ROWS.map(([apiName, displayName, category, queryMode, parameterNames, columnCount]) => {
  const entry = manifestByName.get(apiName)
  requireFixture(entry, `${apiName} exists in manifest`)
  const columns = loadColumns(apiName, entry.filename, columnCount)
  if (apiName === 'balancesheet') requireFixture(columns.length === 152, 'balancesheet has 152 business columns')
  const filters = FILTERS.get(apiName)
  requireFixture(filters, `${apiName} filter contract`)
  return [apiName, {
    apiName,
    displayName,
    category,
    queryMode,
    parameters: parameterNames.map((name) => structuredClone(PARAMETER[name])),
    filters,
    fixedColumn: columns.some(({ name }) => name === 'ts_code') ? 'ts_code' : columns[0].name,
    columns,
  }]
}))
requireFixture([...manifestByName.keys()].every((name) => EXPECTED.has(name)), 'manifest exactly matches the literal matrix')

export const DATA_SOURCE = {
  pluginId: 'tushare_pro',
  displayName: 'Tushare Pro',
  description: 'Tushare Pro 市场数据',
  enabled: true,
  credentialConfigured: true,
  downloadAvailable: true,
  unavailableReason: null,
}

export const APIS = [...EXPECTED.values()].map(({ columns, filters, fixedColumn, ...api }) => api)
export const DATASETS = [...EXPECTED.values()].map(({ parameters, columns, ...dataset }) => dataset)

function syntheticValue(apiName, column, rowIndex) {
  if (rowIndex === 6) return null
  if (rowIndex === 7) return ''
  if (column.logicalType === 'DATE') return '2026-08-07'
  if (column.logicalType === 'MONTH') return '202608'
  if (column.logicalType === 'LONG') return String(1000 + rowIndex)
  if (column.logicalType === 'DECIMAL') {
    return apiName === 'balancesheet' && rowIndex === 0
      ? '12345678901234567890.123456789012345678'
      : `${123 + rowIndex}.4500`
  }
  if (column.name === 'ts_code') return `00000${rowIndex + 1}.SZ`
  return `${column.name}-${rowIndex + 1}`
}

export function syntheticRecords(apiName, count = 8) {
  const definition = EXPECTED.get(apiName)
  requireFixture(definition, `record API ${apiName}`)
  return Array.from({ length: count }, (_, rowIndex) => {
    const row = Object.fromEntries(definition.columns.map((column) => [column.name, syntheticValue(apiName, column, rowIndex)]))
    row.source_plugin = 'tushare_pro'
    row.source_api = apiName
    row.ingested_at = '2026-08-07T12:34:56Z'
    if (apiName === 'daily') {
      row.change = ['0.0100', '-0.0200', '-0.0000', '0', '1.25', '-2.5', null, ''][rowIndex]
      row.pct_chg = ['1.2500', '-2.5000', '-0.0000', '0', '3.75', '-4.25', null, ''][rowIndex]
      row.pre_close = '11.2700'
    }
    if (apiName === 'weekly') {
      row.change = ['0.0100', '-0.0200', '-0.0000', '0', '1.25', '-2.5', null, ''][rowIndex]
      row.pct_chg = ['-0.0378', '0.0250', '-0.0000', '0', '0.01', '-0.02', null, ''][rowIndex]
      row.pre_close = '11.2700'
    }
    if (apiName === 'stk_holdernumber') row.holder_num = rowIndex === 0 ? '9223372036854775807' : String(1000 + rowIndex)
    if (apiName === 'stock_company') row.introduction = rowIndex === 0
      ? '这是一段用于验证长文本提示的公司介绍，包含字面量 <strong>，不得被浏览器解释为 HTML 节点。'
      : row.introduction
    return row
  })
}

export function definitionResponse(apiName) {
  return structuredClone(EXPECTED.get(apiName))
}

function pageResponse(apiName, url, requestId) {
  const page = Number(url.searchParams.get('page') ?? 1)
  const pageSize = Number(url.searchParams.get('pageSize') ?? 50)
  const totalElements = 8
  return {
    requestId,
    pluginId: 'tushare_pro',
    apiName,
    page,
    pageSize,
    totalElements,
    totalPages: 1,
    columns: [...EXPECTED.get(apiName).columns.map(({ name }) => name), 'source_plugin', 'source_api', 'ingested_at'],
    items: syntheticRecords(apiName),
  }
}

export function successDownload(apiName, requestId, outcome = 'SUCCESS') {
  const empty = outcome === 'EMPTY'
  return {
    requestId,
    outcome,
    pluginId: 'tushare_pro',
    apiName,
    sourceRowCount: empty ? 0 : 12,
    insertedRows: empty ? 0 : 10,
    updatedRows: empty ? 0 : 2,
    message: empty ? '没有可写入的数据' : '下载完成',
  }
}

export function apiFailure(requestId, code = 'SOURCE_TIMEOUT') {
  const failures = {
    SOURCE_TIMEOUT: { status: 504, retryable: true, message: '上游请求超时，请稍后使用原参数重试。' },
    QUERY_FAILED: { status: 500, retryable: true, message: '查询失败，请稍后重试。' },
    PARAM_INVALID: { status: 400, retryable: false, message: '请求参数不合法。' },
  }
  const failure = failures[code]
  requireFixture(failure, `known error code ${code}`)
  return {
    status: failure.status,
    body: { requestId, code, message: failure.message, retryable: failure.retryable, fieldErrors: [] },
  }
}

export async function installApi(page, overrides = {}) {
  const requests = []
  const unexpected = []

  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const method = request.method()
    const url = new URL(request.url())
    const path = url.pathname
    const requestId = request.headers()['x-request-id'] ?? ''
    let body = null
    if (request.postData()) {
      try { body = request.postDataJSON() } catch { body = request.postData() }
    }
    const recorded = { method, path, query: Object.fromEntries(url.searchParams), body, requestId }
    requests.push(recorded)

    const reject = (reason) => {
      unexpected.push({ ...recorded, reason })
      return route.abort('failed')
    }
    if (!requestId) return reject('missing X-Request-Id')

    const key = `${method} ${path}`
    let response
    if (Object.hasOwn(overrides, key)) {
      response = typeof overrides[key] === 'function'
        ? await overrides[key](recorded)
        : overrides[key]
    } else if (key === 'GET /api/v1/data-sources') {
      response = { status: 200, body: [DATA_SOURCE] }
    } else if (key === 'GET /api/v1/data-sources/tushare_pro/apis') {
      response = { status: 200, body: APIS }
    } else if (key === 'GET /api/v1/data-sources/tushare_pro/datasets') {
      response = { status: 200, body: DATASETS }
    } else {
      const definitionMatch = path.match(/^\/api\/v1\/data-sources\/tushare_pro\/datasets\/([^/]+)$/)
      const recordsMatch = path.match(/^\/api\/v1\/data-sources\/tushare_pro\/datasets\/([^/]+)\/records$/)
      if (method === 'GET' && definitionMatch && EXPECTED.has(decodeURIComponent(definitionMatch[1]))) {
        response = { status: 200, body: definitionResponse(decodeURIComponent(definitionMatch[1])) }
      } else if (method === 'GET' && recordsMatch && EXPECTED.has(decodeURIComponent(recordsMatch[1]))) {
        const apiName = decodeURIComponent(recordsMatch[1])
        response = { status: 200, body: pageResponse(apiName, url, requestId) }
      } else if (key === 'POST /api/v1/downloads') {
        if (body?.pluginId !== 'tushare_pro' || !EXPECTED.has(body?.apiName) || body?.params === null || typeof body?.params !== 'object') {
          return reject('invalid download identity')
        }
        response = { status: 200, body: successDownload(body.apiName, requestId) }
      } else {
        return reject('undeclared API route')
      }
    }

    if (!response || !Number.isInteger(response.status) || response.body === undefined) return reject('invalid override response')
    const responseBody = typeof response.body === 'function' ? await response.body(recorded) : response.body
    if (responseBody?.requestId !== undefined && responseBody.requestId !== requestId) return reject('response requestId mismatch')
    await route.fulfill({
      status: response.status,
      contentType: 'application/json',
      headers: { 'X-Request-Id': requestId },
      body: JSON.stringify(responseBody),
    })
  })

  return { requests, unexpected }
}
