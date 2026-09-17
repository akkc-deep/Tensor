import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

import { ClientError } from './errors.js'
import { parseTaskJson } from './downloadTaskDtos.js'
import {
  createIntegritySubmission,
  parseIntegrityCapabilities,
  parseIntegrityDetail,
  parseIntegrityIssue,
  parseIntegrityIssuePage,
  parseIntegrityReceipt,
  parseIntegrityResult,
  parseIntegrityResultPage,
  parseIntegrityTaskPage,
  parseIntegrityTaskSummary,
  validateIntegrityCheckId,
  validateIntegrityCriteria,
} from './integrityDtos.js'

const examples = JSON.parse(readFileSync(
  resolve(process.cwd(), '../docs/contracts/integrity-check-examples.json'),
  'utf8',
)).examples

function example(name) {
  return structuredClone(examples.find((item) => item.name === name).value)
}

const REQUEST_ID = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'

function expectInvalid(parser, value) {
  try {
    parser(value, REQUEST_ID)
    throw new Error('expected rejection')
  } catch (error) {
    expect(error).toBeInstanceOf(ClientError)
    expect(error).toMatchObject({ kind: 'INVALID_RESPONSE', requestId: REQUEST_ID })
  }
}

function expectDeepFrozen(value) {
  if (value === null || typeof value !== 'object') return
  expect(Object.isFrozen(value)).toBe(true)
  for (const item of Object.values(value)) expectDeepFrozen(item)
}

it('preserves exact nullable result counts and rejects numeric wire tokens', () => {
  const value = example('failedDataResult')
  value.report.statistics.actualCount = '9223372036854775807'
  value.report.statistics.expectedCount = null
  value.report.statistics.coverageRate = null

  const parsed = parseIntegrityResult(value, REQUEST_ID)

  expect(parsed.report.statistics.actualCount).toBe(9223372036854775807n)
  expect(parsed.report.statistics.expectedCount).toBeNull()
  expect(parsed.report.statistics.coverageRate).toBeNull()

  value.report.statistics.actualCount = 20
  expect(() => parseIntegrityResult(value, REQUEST_ID))
    .toThrow('服务返回了无法识别的响应。')
})

it('parses and recursively freezes every T09 public example', () => {
  const parsers = {
    Capability: parseIntegrityCapabilities,
    Receipt: parseIntegrityReceipt,
    Detail: parseIntegrityDetail,
    Result: parseIntegrityResult,
    Issue: parseIntegrityIssue,
    TaskPage: parseIntegrityTaskPage,
    ResultPage: parseIntegrityResultPage,
    IssuePage: parseIntegrityIssuePage,
  }

  for (const fixture of examples) {
    if (fixture.schema === 'Request') continue
    const parsed = parsers[fixture.schema](
      structuredClone(fixture.value),
      fixture.value.requestId ?? REQUEST_ID,
    )
    expectDeepFrozen(parsed)
  }

  const detail = parseIntegrityDetail(example('runningDetail'), REQUEST_ID)
  expect(detail.completedUnits).toBe(0n)
  expect(detail.statusCounts).toEqual({
    PASS: 0n, FAIL: 0n, WARN: 0n, UNKNOWN: 1n, NOT_APPLICABLE: 0n,
  })
  expect(detail).not.toHaveProperty('report')

  const issue = parseIntegrityIssue(example('undatedIssue'), REQUEST_ID)
  expect(issue.issueId).toBe(9223372036854775807n)
  expect(issue.issue.date).toBeNull()
  expect(issue.issue.businessKey).toEqual({
    ts_code: '000001.SZ',
    trade_date: null,
    event_id: '9223372036854775807',
    amount: '1.230000000000000000',
  })

  const historical = parseIntegrityTaskPage(example('historicalTaskPage'), REQUEST_ID)
  expect(historical.items[0]).not.toHaveProperty('overallStatus')
})

it('rejects unknown and missing fields, bad enums, dates, ratios and count tokens', () => {
  const mutations = [
    (value) => { value.extra = true },
    (value) => { delete value.report.finishedAt },
    (value) => { value.report.unitStatus = 'SUCCEEDED' },
    (value) => { value.report.scope.startDate = '2024-02-30' },
    (value) => { value.report.statistics.coverageRate = '0.95' },
    (value) => { value.report.statistics.missingCount = '-1' },
    (value) => { value.report.statistics.extraCount = '01' },
  ]
  for (const mutate of mutations) {
    const value = example('failedDataResult')
    mutate(value)
    expectInvalid(parseIntegrityResult, value)
  }

  const summary = example('historicalTaskPage').items[0]
  summary.status = 'PARTIAL_FAILED'
  expectInvalid(parseIntegrityTaskSummary, summary)

  const capability = example('availableCapability')
  capability.limits.maxScannedRowsPerUnit = 500000
  expectInvalid(parseIntegrityCapabilities, capability)
})

it('preserves nullable descriptor, report states, null dates and large download rowLimit', () => {
  for (const name of ['unknownResult', 'notApplicableResult', 'incompleteResult']) {
    const parsed = parseIntegrityResult(example(name), REQUEST_ID)
    expect(['UNKNOWN', 'NOT_APPLICABLE']).toContain(parsed.report.overallStatus)
  }

  const capability = example('availableCapability')
  capability.apis[0].descriptor = null
  capability.apis[0].downloadAvailability.range = {
    availability: 'AVAILABLE',
    unavailableReason: null,
    dateAxis: 'TRADE_DATE',
    dateLabel: '交易日期',
    startParameter: 'start_date',
    endParameter: 'end_date',
    parameters: [
      { name: 'start_date', label: '开始', type: 'DATE_RANGE_MEMBER', required: true, relatedParameter: 'end_date' },
      { name: 'end_date', label: '结束', type: 'DATE_RANGE_MEMBER', required: true, relatedParameter: 'start_date' },
    ],
    planningMode: 'CALENDAR_DAYS',
    splittable: false,
    policyVersion: 'v1',
    completenessRule: {
      kind: 'CONFIRMED_ROW_LIMIT',
      rowLimit: 9223372036854775807n,
      evidence: 'saved limit',
    },
  }
  const parsed = parseIntegrityCapabilities(capability, REQUEST_ID)
  expect(parsed.apis[0].descriptor).toBeNull()
  expect(parsed.apis[0].downloadAvailability.range.completenessRule.rowLimit)
    .toBe(9223372036854775807n)
})

it('creates immutable fresh and restored submissions without rewriting arrays', () => {
  const selection = {
    pluginId: 'fixture',
    capabilityHash: 'a'.repeat(64),
    symbols: [' z ', '000001.SZ'],
    startDate: '2024-02-01',
    endDate: '2024-02-29',
    apiNames: ['second_api', 'first_api'],
  }
  const id = '22222222-2222-4222-8222-222222222222'
  const created = createIntegritySubmission(selection, id)
  expect(created).toEqual({ submissionId: id, ...selection })
  expectDeepFrozen(created)
  selection.symbols[0] = 'changed'
  expect(created.symbols[0]).toBe(' z ')

  const restored = createIntegritySubmission(structuredClone(created), id)
  expect(restored).toEqual(created)

  const uppercaseId = 'AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA'
  expect(createIntegritySubmission({ ...created, submissionId: uppercaseId }, uppercaseId).submissionId)
    .toBe(uppercaseId)

  const omitted = createIntegritySubmission({ ...selection, symbols: ['000001.SZ'], apiNames: undefined }, id)
  expect(omitted).not.toHaveProperty('apiNames')

  for (const invalid of [
    { ...selection, ignored: true },
    { ...selection, startDate: '2024-02-30' },
    { ...selection, startDate: '2024-03-01', endDate: '2024-02-01' },
    { ...selection, symbols: [] },
    { ...selection, apiNames: [] },
    { ...created, submissionId: '33333333-3333-4333-8333-333333333333' },
  ]) {
    expect(() => createIntegritySubmission(invalid, id)).toThrow(TypeError)
  }
})

it('accepts every distinct task, unit and data status without conflating them', () => {
  const summary = example('historicalTaskPage').items[0]
  for (const status of ['QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'INTERRUPTED']) {
    summary.status = status
    expect(parseIntegrityTaskSummary(summary, REQUEST_ID).status).toBe(status)
  }

  const result = example('failedDataResult')
  for (const status of ['PENDING', 'RUNNING', 'COMPLETED', 'ERROR', 'NOT_RUN']) {
    result.report.unitStatus = status
    expect(parseIntegrityResult(result, REQUEST_ID).report.unitStatus).toBe(status)
  }
  for (const status of ['PASS', 'FAIL', 'WARN', 'UNKNOWN', 'NOT_APPLICABLE']) {
    result.report.coverageStatus = status
    result.report.keyStatus = status
    result.report.fieldStatus = status
    result.report.overallStatus = status
    expect(parseIntegrityResult(result, REQUEST_ID).report.overallStatus).toBe(status)
  }
})

it('preserves raw JSON integer tokens in download limits and business keys', () => {
  const capability = example('availableCapability')
  capability.apis[0].downloadAvailability.range = {
    availability: 'AVAILABLE', unavailableReason: null, dateAxis: 'TRADE_DATE', dateLabel: '交易日期',
    startParameter: 'start_date', endParameter: 'end_date',
    parameters: [
      { name: 'start_date', label: '开始', type: 'DATE_RANGE_MEMBER', required: true, relatedParameter: 'end_date' },
      { name: 'end_date', label: '结束', type: 'DATE_RANGE_MEMBER', required: true, relatedParameter: 'start_date' },
    ],
    planningMode: 'CALENDAR_DAYS', splittable: false, policyVersion: 'v1',
    completenessRule: { kind: 'CONFIRMED_ROW_LIMIT', rowLimit: 'BIG', evidence: 'saved limit' },
  }
  const capabilityWire = JSON.stringify(capability).replace('"BIG"', '9223372036854775807')
  const parsedCapability = parseIntegrityCapabilities(parseTaskJson(capabilityWire), REQUEST_ID)
  expect(parsedCapability.apis[0].downloadAvailability.range.completenessRule.rowLimit)
    .toBe(9223372036854775807n)

  const issueWire = JSON.stringify(example('undatedIssue'))
    .replace('"event_id":"9223372036854775807"', '"event_id":9223372036854775807')
  expect(parseIntegrityIssue(parseTaskJson(issueWire), REQUEST_ID).issue.businessKey.event_id)
    .toBe(9223372036854775807n)
})

it('normalizes composable IDs and all three criteria shapes', () => {
  expect(validateIntegrityCheckId('AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA'))
    .toBe('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')
  expect(() => validateIntegrityCheckId('not-an-id')).toThrow(TypeError)

  expect(validateIntegrityCriteria('tasks', {})).toEqual({ page: 1, pageSize: 20 })
  expect(validateIntegrityCriteria('tasks', {
    page: 2,
    pageSize: 37,
    pluginId: 'fixture',
    status: 'INTERRUPTED',
  })).toEqual({
    page: 2,
    pageSize: 37,
    pluginId: 'fixture',
    status: 'INTERRUPTED',
  })
  expect(validateIntegrityCriteria('tasks', {
    submissionId: 'AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA',
  })).toEqual({
    page: 1, pageSize: 20, submissionId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  })
  expect(validateIntegrityCriteria('results', {
    symbol: ' 000001.SZ ', apiName: 'fixture_daily', overallStatus: 'NOT_APPLICABLE',
  })).toEqual({
    page: 1, pageSize: 20, symbol: ' 000001.SZ ', apiName: 'fixture_daily', overallStatus: 'NOT_APPLICABLE',
  })
  expect(validateIntegrityCriteria('issues', {
    resultId: 'BBBBBBBB-BBBB-4BBB-8BBB-BBBBBBBBBBBB',
    symbol: '历史 值',
    apiName: 'fixture_daily',
    type: 'REFERENCE_INCOMPLETE',
    status: 'WARN',
    dateFrom: '2024-02-01',
    dateTo: '2024-02-29',
  })).toMatchObject({ resultId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', symbol: '历史 值' })

  for (const [kind, criteria] of [
    ['unknown', {}],
    ['tasks', { unknown: true }],
    ['tasks', { page: 0 }],
    ['tasks', { pageSize: 101 }],
    ['tasks', { submissionId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', page: 2 }],
    ['tasks', { submissionId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', status: 'RUNNING' }],
    ['results', { symbol: '' }],
    ['results', { symbol: 'x'.repeat(256) }],
    ['issues', { dateFrom: '2024-03-01', dateTo: '2024-02-29' }],
    ['issues', { dateFrom: null }],
  ]) {
    expect(() => validateIntegrityCriteria(kind, criteria)).toThrow(TypeError)
  }
})
