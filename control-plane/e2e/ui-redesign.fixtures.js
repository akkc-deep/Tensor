import { randomUUID } from 'node:crypto'
import { rawJson, task, batch } from './download-tasks.fixtures.js'
import { readFileSync } from 'node:fs'

// Contract copied from tushare-metadata.spec.js. It intentionally stays
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
  ['stock_basic', '股票基础信息', 'basic_organization', 'snapshot', ['ts_code', 'list_status'], 10],
  ['stock_company', '上市公司基本信息', 'basic_organization', 'snapshot', ['ts_code', 'exchange'], 18],
  ['income', '利润表', '财务与披露', 'ann_date', ['ts_code', 'ann_date'], 85],
  ['balancesheet', '资产负债表', '财务与披露', 'ann_date', ['ts_code', 'ann_date'], 152],
  ['cashflow', '现金流量表', '财务与披露', 'ann_date', ['ts_code', 'ann_date'], 97],
  ['fina_indicator', '财务指标', '财务与披露', 'ann_date', ['ts_code', 'ann_date'], 108],
  ['fina_audit', '财务审计意见', '财务与披露', 'ann_date', ['ts_code', 'ann_date'], 7],
  ['fina_mainbz', '主营业务构成', '财务与披露', 'snapshot', ['ts_code'], 8],
  ['stk_rewards', '管理层薪酬与持股', '股东与治理', 'snapshot', ['ts_code'], 7],
  ['stk_holdernumber', '股东户数', '股东与治理', 'snapshot', ['ts_code'], 4],
  ['trade_cal', '交易日历', 'basic_organization', 'date_range', ['exchange', 'start_date', 'end_date'], 4],
  ['margin', '融资融券汇总', '交易与资金', 'trade_date', ['exchange_id', 'trade_date'], 9],
  ['daily', '日线行情', '行情与估值', 'trade_date', ['ts_code', 'trade_date'], 11],
  ['weekly', '周线行情', '行情与估值', 'trade_date', ['ts_code', 'trade_date'], 11],
  ['monthly', '月线行情', '行情与估值', 'trade_date', ['ts_code', 'trade_date'], 11],
  ['adj_factor', '复权因子', '行情与估值', 'trade_date', ['ts_code', 'trade_date'], 3],
  ['suspend_d', '每日停复牌信息', '行情与估值', 'trade_date', ['ts_code', 'trade_date'], 4],
  ['daily_basic', '每日估值与市场指标', '行情与估值', 'trade_date', ['ts_code', 'trade_date'], 18],
  ['moneyflow', '个股资金流向', '交易与资金', 'trade_date', ['ts_code', 'trade_date'], 20],
  ['stk_limit', '每日涨跌停价格', '行情与估值', 'trade_date', ['ts_code', 'trade_date'], 4],
  ['top_list', '龙虎榜每日明细', '交易与资金', 'trade_date', ['ts_code', 'trade_date'], 15],
  ['margin_detail', '融资融券交易明细', '交易与资金', 'trade_date', ['ts_code', 'trade_date'], 10],
  ['block_trade', '大宗交易', '交易与资金', 'trade_date', ['ts_code', 'trade_date'], 7],
  ['slb_len', '转融通期限与规模', '互联互通与转融通', 'trade_date', ['trade_date'], 6],
  ['slb_sec', '转融通证券汇总', '互联互通与转融通', 'trade_date', ['ts_code', 'trade_date'], 7],
  ['slb_sec_detail', '转融通证券明细', '互联互通与转融通', 'trade_date', ['ts_code', 'trade_date'], 6],
  ['forecast', '业绩预告', '财务与披露', 'ann_date', ['ts_code', 'ann_date'], 13],
  ['express', '业绩快报', '财务与披露', 'ann_date', ['ts_code', 'ann_date'], 15],
  ['dividend', '分红送股', '公司行动', 'ann_date', ['ts_code', 'ann_date'], 14],
  ['disclosure_date', '财报披露计划', '财务与披露', 'ann_date', ['ts_code', 'ann_date'], 5],
  ['repurchase', '股票回购', '公司行动', 'ann_date', ['ann_date'], 9],
  ['stk_holdertrade', '股东增减持', '股东与治理', 'ann_date', ['ts_code', 'ann_date'], 11],
  ['top10_holders', '前十大股东', '股东与治理', 'ann_date', ['ts_code', 'ann_date'], 9],
  ['top10_floatholders', '前十大流通股东', '股东与治理', 'ann_date', ['ts_code', 'ann_date'], 9],
  ['new_share', 'IPO 新股发行信息', 'basic_organization', 'date_range', ['start_date', 'end_date'], 12],
  ['stk_managers', '上市公司管理层信息', 'basic_organization', 'snapshot', ['ts_code'], 11],
  ['pledge_stat', '股权质押统计', '股东与治理', 'snapshot', ['ts_code'], 7],
  ['pledge_detail', '股权质押明细', '股东与治理', 'snapshot', ['ts_code'], 14],
  ['index_classify', '行业指数分类', 'basic_organization', 'snapshot', [], 7],
  ['index_member_all', '行业分级与完整成分', 'basic_organization', 'snapshot', ['ts_code'], 11],
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
      row.change = ['0.0100', '-0.0200', '-0.0000', '0', '1.25', '-2.5', null, ''][rowIndex % 8]
      row.pct_chg = ['1.2500', '-2.5000', '-0.0000', '0', '3.75', '-4.25', null, ''][rowIndex % 8]
      row.pre_close = '11.2700'
    }
    if (apiName === 'weekly') {
      row.change = ['0.0100', '-0.0200', '-0.0000', '0', '1.25', '-2.5', null, ''][rowIndex % 8]
      row.pct_chg = ['-0.0378', '0.0250', '-0.0000', '0', '0.01', '-0.02', null, ''][rowIndex % 8]
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

export function successDownload(request, outcome = 'SUCCESS') {
  const empty = outcome === 'EMPTY'
  const saved = task({
    taskId: randomUUID(), submissionId: request.body.submissionId,
    apiName: request.body.apiName, mode: request.body.mode, params: request.body.params,
    extraction: request.body.mode === 'SINGLE' ? null : {
      policyVersion: rangeCapability(request.body.apiName).policyVersion,
      ruleKind: rangeCapability(request.body.apiName).completenessRule.kind,
    },
    version: 1n, requestCount: 1n, runRequestCount: 1n,
    counts: { totalBatches: 1n, pendingBatches: 0n, runningBatches: 0n, succeededBatches: 1n,
      failedBatches: 0n, splitBatches: 0n, sourceRows: empty ? 0n : 12n,
      insertedRows: empty ? 0n : 10n, updatedRows: empty ? 0n : 2n },
  })
  return { status: 202, task: saved,
    body: { requestId: request.requestId, taskId: saved.taskId, status: 'QUEUED', version: 1n, createdAt: saved.createdAt },
    headers: { Location: `/api/v1/download-tasks/${saved.taskId}` } }
}

// Independent candidate matrix: API, scope, mode, axis, rule, row limit, official document.
export const RANGE_EXPECTATIONS = new Map(`
daily ts_code N TRADE_DATE ROW 6000 27
weekly ts_code N TRADE_DATE ROW 6000 144
monthly ts_code N TRADE_DATE ROW 4500 145
adj_factor ts_code N TRADE_DATE RESPONSE - 28
daily_basic ts_code N TRADE_DATE ROW 6000 32
stk_limit ts_code N TRADE_DATE ROW 5800 183
suspend_d ts_code N TRADE_DATE RESPONSE - 214
moneyflow ts_code N TRADE_DATE ROW 6000 170
margin exchange_id N TRADE_DATE ROW 4000 58
margin_detail ts_code N TRADE_DATE ROW 6000 59
block_trade ts_code N TRADE_DATE ROW 1000 161
slb_len - N TRADE_DATE ROW 5000 331
slb_sec ts_code N TRADE_DATE ROW 5000 332
slb_sec_detail ts_code N TRADE_DATE ROW 5000 333
trade_cal exchange N CALENDAR_DATE CALENDAR - 26
new_share - N ISSUE_DATE ROW 2000 123
income ts_code N ANNOUNCEMENT_DATE RESPONSE - 33
balancesheet ts_code N ANNOUNCEMENT_DATE RESPONSE - 36
cashflow ts_code N ANNOUNCEMENT_DATE RESPONSE - 44
fina_audit ts_code N ANNOUNCEMENT_DATE RESPONSE - 80
forecast ts_code N ANNOUNCEMENT_DATE ROW 3500 45
express ts_code N ANNOUNCEMENT_DATE RESPONSE - 46
repurchase - N ANNOUNCEMENT_DATE RESPONSE - 124
stk_managers ts_code N ANNOUNCEMENT_DATE RESPONSE - 193
stk_holdernumber ts_code N ANNOUNCEMENT_DATE ROW 3000 166
stk_holdertrade ts_code N ANNOUNCEMENT_DATE ROW 3000 175
pledge_detail ts_code N ANNOUNCEMENT_DATE ROW 1000 111
fina_indicator ts_code N REPORT_PERIOD ROW 100 79
fina_mainbz ts_code N REPORT_PERIOD ROW 100 81
top10_holders ts_code N REPORT_PERIOD RESPONSE - 61
top10_floatholders ts_code N REPORT_PERIOD RESPONSE - 62
top_list ts_code T TRADE_DATE ROW 10000 106
dividend ts_code C ANNOUNCEMENT_DATE ROW 2000 103
disclosure_date ts_code C ANNOUNCEMENT_DATE ROW 6000 162
`.trim().split('\n').map((line) => {
  const [apiName, rawScope, mode, dateAxis, rule, rawLimit, document] = line.split(' ')
  const dateLabel = apiName === 'disclosure_date' ? '最新披露公告日' : {
    TRADE_DATE: '交易日期', CALENDAR_DATE: '日历日期', ISSUE_DATE: '上网发行日期',
    ANNOUNCEMENT_DATE: '公告日期', REPORT_PERIOD: '报告期',
  }[dateAxis]
  return [apiName, { scope: rawScope === '-' ? null : rawScope,
    planningMode: { N: 'NATIVE_RANGE', T: 'TRADING_DAYS', C: 'CALENDAR_DAYS' }[mode],
    dateAxis, dateLabel, rule, rowLimit: rawLimit === '-' ? null : Number(rawLimit), document: Number(document),
    splittable: mode === 'N' && rule === 'ROW' }]
}))
requireFixture(RANGE_EXPECTATIONS.size === 34, 'exactly 34 RANGE candidates')
const LEGACY_SOURCE = new Map([
  ['daily_basic', [6000, 32]], ['stk_limit', [5800, 183]],
  ['moneyflow', [6000, 170]], ['margin_detail', [6000, 59]],
])
const CANDIDATE_SOURCE = new Map(`
daily issue019-source-20260913T122126Z issue019-source-20260913T122126Z-daily-000001-whole ISSUE019
forecast issue019-source-20260913T122126Z issue019-source-20260913T122126Z-forecast-000001-whole ISSUE019
dividend issue019-source-20260913T122126Z issue019-source-20260913T122126Z-dividend-000001-whole ISSUE019
fina_mainbz issue026-mainbz-split-source-20260914T013736Z issue026-mainbz-split-source-20260914T013736Z-000001-annual ISSUE020
trade_cal issue021-source-20260913T135006Z issue021-source-20260913T135006Z-trade_cal-sse-whole ISSUE021
margin issue021-source-20260913T135006Z issue021-source-20260913T135006Z-margin-sse-whole ISSUE021
top_list issue021-bj-source-20260913T135704Z issue021-bj-source-20260913T135704Z-920008-whole ISSUE021
weekly issue022-source-20260913T141544Z issue022-source-20260913T141544Z-weekly-000001-whole ISSUE022
monthly issue022-source-20260913T141544Z issue022-source-20260913T141544Z-monthly-000001-whole ISSUE022
fina_indicator issue022-source-20260913T141544Z issue022-source-20260913T141544Z-indicator-000001-whole ISSUE022
stk_holdernumber issue022-source-20260913T141544Z issue022-source-20260913T141544Z-holder-000001-whole ISSUE022
new_share issue022-source-20260913T141544Z issue022-source-20260913T141544Z-ipo-whole ISSUE022
block_trade issue023-source-20260913T144308Z issue023-source-20260913T144308Z-block-official ISSUE023
disclosure_date issue023-source-20260913T144308Z issue023-source-20260913T144308Z-disclosure-official ISSUE023
stk_holdertrade issue023-source-20260913T144308Z issue023-source-20260913T144308Z-holder-official ISSUE023
pledge_detail issue023-source-20260913T144308Z issue023-source-20260913T144308Z-pledge-official PLEDGE
slb_len issue024-source-20260913T153146Z issue024-source-20260913T153146Z-len-whole ISSUE024
slb_sec issue024-source-20260913T153146Z issue024-source-20260913T153146Z-slb_sec-000001-whole ISSUE024
slb_sec_detail issue024-source-20260913T153146Z issue024-source-20260913T153146Z-slb_sec_detail-000001-whole ISSUE024
adj_factor issue025-source-20260913T162759Z issue025-source-20260913T162759Z-adj_factor-000001-whole ISSUE025
suspend_d issue025-source-20260913T162759Z issue025-source-20260913T162759Z-suspend_d-000001-whole ISSUE025
income issue025-source-20260913T162759Z issue025-source-20260913T162759Z-income-000001-whole ISSUE025
balancesheet issue025-source-20260913T162759Z issue025-source-20260913T162759Z-balancesheet-000001-whole ISSUE025
cashflow issue025-source-20260913T162759Z issue025-source-20260913T162759Z-cashflow-000001-whole ISSUE025
fina_audit issue025-source-20260913T162759Z issue025-source-20260913T162759Z-fina_audit-000001-whole ISSUE025
express issue025-source-20260913T162759Z issue025-source-20260913T162759Z-express-600000-whole ISSUE025
repurchase issue025-source-20260913T162759Z issue025-source-20260913T162759Z-repurchase-all-whole ISSUE025
stk_managers issue025-source-20260913T162759Z issue025-source-20260913T162759Z-stk_managers-000001-whole ISSUE025
top10_holders issue025-source-20260913T162759Z issue025-source-20260913T162759Z-top10_holders-000001-whole ISSUE025
top10_floatholders issue025-source-20260913T162759Z issue025-source-20260913T162759Z-top10_floatholders-000001-whole ISSUE025
`.trim().split('\n').map((line) => {
  const [apiName, runId, caseId, decision] = line.split(' ')
  return [apiName, { runId, caseId, decision }]
}))
const DECISION = {
  ISSUE019: 'docs/issues/proposals/ISSUE-019-documented-range-limits.md#决策记录',
  ISSUE020: 'docs/issues/proposals/ISSUE-020-fina-mainbz-default-type.md#决策记录',
  ISSUE021: 'docs/task-designs/ISSUE-021-design.md',
  ISSUE022: 'docs/task-designs/ISSUE-022-design.md',
  ISSUE023: 'docs/task-designs/ISSUE-023-design.md',
  PLEDGE: 'docs/task-designs/ISSUE-023-design.md#质押非空样本决定2026-09-13',
  ISSUE024: 'docs/issues/proposals/ISSUE-024-historical-support.md#决策记录',
  ISSUE025: 'docs/issues/proposals/ISSUE-025-extraction-contracts.md#决策记录',
}

export function rangeCapability(apiName) {
  const policy = RANGE_EXPECTATIONS.get(apiName)
  const common = { completenessRule: { kind: 'UNKNOWN', rowLimit: null, evidence: null } }
  if (!policy) return { ...common, availability: 'UNSUPPORTED', unavailableReason: 'Range download is not supported',
    dateAxis: null, dateLabel: null, startParameter: null, endParameter: null, parameters: [],
    planningMode: null, splittable: false, policyVersion: 'unsupported-v1' }
  const { scope, rule, rowLimit, document, ...metadata } = policy
  const label = policy.dateLabel.replace(/日期$/, '')
  const legacy = LEGACY_SOURCE.get(apiName)
  if (legacy) {
    const runId = 'issue018-t14-priority-source-20260913T092938Z'
    const suffixes = ['single-000001', 'single-600000', 'range', 'lower-bound', 'upper-bound',
      'range-600000', 'lower-bound-600000', 'upper-bound-600000', ...(apiName === 'daily_basic' ? ['cross-year'] : [])]
    common.completenessRule = { kind: 'CONFIRMED_ROW_LIMIT', rowLimit,
      evidence: `docs/verification/ISSUE-018-range-acceptance.md#${apiName}；SOURCE ${runId}；`
        + suffixes.map((suffix) => `${runId}-${apiName}-${suffix}`).join(',')
        + `；https://tushare.pro/document/2?doc_id=${document}` }
  } else {
    const source = CANDIDATE_SOURCE.get(apiName)
    requireFixture(source && DECISION[source.decision], `${apiName} candidate evidence`)
    common.completenessRule = {
      kind: { ROW: 'CONFIRMED_ROW_LIMIT', CALENDAR: 'VERIFIED_RULE', RESPONSE: 'RESPONSE_ONLY' }[rule],
      rowLimit,
      evidence: `${DECISION[source.decision]}；docs/verification/ISSUE-018-range-acceptance.md#${apiName}`
        + `；docs/verification/ISSUE-018-range-acceptance.json#${source.runId}/${source.caseId}`
        + `；https://tushare.pro/document/2?doc_id=${document}`,
    }
  }
  const withdrawn = ['fina_indicator', 'balancesheet', 'cashflow', 'repurchase'].includes(apiName)
  if (withdrawn) common.completenessRule = { kind: 'UNKNOWN', rowLimit: null, evidence: null }
  return { ...common, ...metadata, availability: withdrawn ? 'NEEDS_VERIFICATION' : 'AVAILABLE',
    policyVersion: withdrawn ? 'tushare-range-v3' : 'tushare-range-v2',
    unavailableReason: withdrawn ? '区间参数语义与完整性尚待真实接口验证' : null,
    startParameter: 'start_date', endParameter: 'end_date', parameters: [
      ...(scope ? [structuredClone(PARAMETER[scope])] : []),
      { ...PARAMETER.start_date, label: `${label}开始日期` },
      { ...PARAMETER.end_date, label: `${label}结束日期` },
    ],
  }
}

function capabilities(apiName) {
  return { single: { available: true, parameters: EXPECTED.get(apiName).parameters }, range: rangeCapability(apiName) }
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
  const tasks = new Map()

  await page.route('**/api/v1/**', async (route) => {
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
      const capabilityMatch = path.match(/^\/api\/v1\/data-sources\/tushare_pro\/apis\/([^/]+)\/download-capabilities$/)
      const taskMatch = path.match(/^\/api\/v1\/download-tasks\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})(?:\/(batches|retry|resume))?$/)
      const definitionMatch = path.match(/^\/api\/v1\/data-sources\/tushare_pro\/datasets\/([^/]+)$/)
      const recordsMatch = path.match(/^\/api\/v1\/data-sources\/tushare_pro\/datasets\/([^/]+)\/records$/)
      if (method === 'GET' && capabilityMatch && EXPECTED.has(capabilityMatch[1])) {
        if (url.search) return reject('unexpected capabilities query')
        response = { status: 200, body: capabilities(capabilityMatch[1]) }
      } else if (key === 'GET /api/v1/download-tasks') {
        const allowed = ['page', 'pageSize', 'submissionId', 'pluginId', 'apiName', 'status']
        if ([...url.searchParams.keys()].some((key) => !allowed.includes(key)) || new Set(url.searchParams.keys()).size !== [...url.searchParams.keys()].length) return reject('invalid list query')
        const items = [...tasks.values()].reverse().filter((item) => ['submissionId','pluginId','apiName','status'].every((key) => !url.searchParams.has(key) || item[key] === url.searchParams.get(key)))
        const pageNumber = Number(url.searchParams.get('page') || 1)
        const pageSize = Number(url.searchParams.get('pageSize') || 20)
        response = { status: 200, body: { page: pageNumber, pageSize, total: BigInt(items.length), items: items.slice((pageNumber - 1) * pageSize, pageNumber * pageSize) } }
      } else if (taskMatch && tasks.has(taskMatch[1])) {
        const saved = tasks.get(taskMatch[1])
        if (method === 'GET' && !taskMatch[2] && !url.search) response = { status: 200, body: saved }
        else if (method === 'GET' && taskMatch[2] === 'batches' && url.searchParams.toString() === 'page=1&pageSize=20&includeSplit=false') {
          response = { status: 200, body: { page: 1, pageSize: 20, total: 1n, items: [batch(0, { rangeStart: null, rangeEnd: null, sourceParams: saved.params, sourceRows: saved.counts.sourceRows, insertedRows: saved.counts.insertedRows, updatedRows: saved.counts.updatedRows })] } }
        } else if (method === 'POST' && ['retry', 'resume'].includes(taskMatch[2]) && !url.search && request.postData() === `{"expectedVersion":${saved.version}}`) {
          saved.version += 1n
          response = { status: 202, headers: { Location: `/api/v1/download-tasks/${saved.taskId}` }, body: { requestId, taskId: saved.taskId, status: saved.status, version: saved.version, createdAt: saved.createdAt } }
        } else return reject('invalid task request')
      } else if (method === 'GET' && definitionMatch && EXPECTED.has(decodeURIComponent(definitionMatch[1]))) {
        response = { status: 200, body: definitionResponse(decodeURIComponent(definitionMatch[1])) }
      } else if (method === 'GET' && recordsMatch && EXPECTED.has(decodeURIComponent(recordsMatch[1]))) {
        const apiName = decodeURIComponent(recordsMatch[1])
        response = { status: 200, body: pageResponse(apiName, url, requestId) }
      } else if (key === 'POST /api/v1/download-tasks') {
        if (body?.pluginId !== 'tushare_pro' || !EXPECTED.has(body?.apiName) || body?.mode !== 'SINGLE' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(body?.submissionId ?? '') || body?.params === null || typeof body?.params !== 'object') {
          return reject('invalid download identity')
        }
        const existing = [...tasks.values()].find((item) => item.submissionId === body.submissionId)
        response = existing
          ? { status: 200, headers: { Location: `/api/v1/download-tasks/${existing.taskId}` }, body: { requestId, taskId: existing.taskId, status: existing.status, version: existing.version, createdAt: existing.createdAt } }
          : successDownload(recorded)
      } else {
        return reject('undeclared API route')
      }
    }

    if (!response || !Number.isInteger(response.status) || response.body === undefined) return reject('invalid override response')
    if (response.task) tasks.set(response.task.taskId, response.task)
    const responseBody = typeof response.body === 'function' ? await response.body(recorded) : response.body
    if (responseBody?.requestId !== undefined && responseBody.requestId !== requestId) return reject('response requestId mismatch')
    await route.fulfill({
      status: response.status,
      contentType: 'application/json',
      headers: { 'X-Request-Id': requestId, ...response.headers },
      body: rawJson(responseBody),
    })
  })

  return { requests, unexpected }
}
