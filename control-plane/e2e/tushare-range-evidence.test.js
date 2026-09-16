import * as liveContract from './tushare-range-evidence.js'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import {
  safeCaseEvidence,
  validateCasePlan,
  validateEvidence,
} from './tushare-range-evidence.js'
import {
  ISSUE030_GROUP_COUNTS,
  ISSUE030_SOURCE_GROUPS,
  ISSUE030_SOURCE_INPUTS,
  applyIssue030CandidateIndex,
  buildIssue030CandidatePlan,
} from './issue030-range-candidates.js'

const SAFE_ERROR = 'Invalid Tushare range evidence'
const EVIDENCE_PATH = new URL('../../docs/verification/ISSUE-018-range-acceptance.json', import.meta.url)

// Independent contract: do not derive these expected rows from the helper or evidence index.
const EXPECTED_MATRIX = [
  ['stock_basic', false, null, null, null, null, 25],
  ['stock_company', false, null, null, null, null, 112],
  ['income', true, 'STOCK', 'NATIVE_RANGE', 'ANNOUNCEMENT_DATE', 'ann_date', 33],
  ['balancesheet', true, 'STOCK', 'NATIVE_RANGE', 'ANNOUNCEMENT_DATE', 'ann_date', 36],
  ['cashflow', true, 'STOCK', 'NATIVE_RANGE', 'ANNOUNCEMENT_DATE', 'ann_date', 44],
  ['fina_indicator', true, 'STOCK', 'NATIVE_RANGE', 'REPORT_PERIOD', 'end_date', 79],
  ['fina_audit', true, 'STOCK', 'NATIVE_RANGE', 'ANNOUNCEMENT_DATE', 'ann_date', 80],
  ['fina_mainbz', true, 'STOCK', 'NATIVE_RANGE', 'REPORT_PERIOD', 'end_date', 81],
  ['stk_rewards', false, null, null, null, null, 194],
  ['stk_holdernumber', true, 'STOCK', 'NATIVE_RANGE', 'ANNOUNCEMENT_DATE', 'ann_date', 166],
  ['trade_cal', true, 'EXCHANGE', 'NATIVE_RANGE', 'CALENDAR_DATE', 'cal_date', 26],
  ['margin', true, 'EXCHANGE_ID', 'NATIVE_RANGE', 'TRADE_DATE', 'trade_date', 58],
  ['daily', true, 'STOCK', 'NATIVE_RANGE', 'TRADE_DATE', 'trade_date', 27],
  ['weekly', true, 'STOCK', 'NATIVE_RANGE', 'TRADE_DATE', 'trade_date', 144],
  ['monthly', true, 'STOCK', 'NATIVE_RANGE', 'TRADE_DATE', 'trade_date', 145],
  ['adj_factor', true, 'STOCK', 'NATIVE_RANGE', 'TRADE_DATE', 'trade_date', 28],
  ['suspend_d', true, 'STOCK', 'NATIVE_RANGE', 'TRADE_DATE', 'trade_date', 214],
  ['daily_basic', true, 'STOCK', 'NATIVE_RANGE', 'TRADE_DATE', 'trade_date', 32],
  ['moneyflow', true, 'STOCK', 'NATIVE_RANGE', 'TRADE_DATE', 'trade_date', 170],
  ['stk_limit', true, 'STOCK', 'NATIVE_RANGE', 'TRADE_DATE', 'trade_date', 183],
  ['top_list', true, 'STOCK', 'TRADING_DAYS', 'TRADE_DATE', 'trade_date', 106],
  ['margin_detail', true, 'STOCK', 'NATIVE_RANGE', 'TRADE_DATE', 'trade_date', 59],
  ['block_trade', true, 'STOCK', 'NATIVE_RANGE', 'TRADE_DATE', 'trade_date', 161],
  ['slb_len', true, 'DATES', 'NATIVE_RANGE', 'TRADE_DATE', 'trade_date', 331],
  ['slb_sec', true, 'STOCK', 'NATIVE_RANGE', 'TRADE_DATE', 'trade_date', 332],
  ['slb_sec_detail', true, 'STOCK', 'NATIVE_RANGE', 'TRADE_DATE', 'trade_date', 333],
  ['forecast', true, 'STOCK', 'NATIVE_RANGE', 'ANNOUNCEMENT_DATE', 'ann_date', 45],
  ['express', true, 'STOCK', 'NATIVE_RANGE', 'ANNOUNCEMENT_DATE', 'ann_date', 46],
  ['dividend', true, 'STOCK', 'CALENDAR_DAYS', 'ANNOUNCEMENT_DATE', 'ann_date', 103],
  ['disclosure_date', true, 'STOCK', 'CALENDAR_DAYS', 'ANNOUNCEMENT_DATE', 'ann_date', 162],
  ['repurchase', true, 'DATES', 'NATIVE_RANGE', 'ANNOUNCEMENT_DATE', 'ann_date', 124],
  ['stk_holdertrade', true, 'STOCK', 'NATIVE_RANGE', 'ANNOUNCEMENT_DATE', 'ann_date', 175],
  ['top10_holders', true, 'STOCK', 'NATIVE_RANGE', 'REPORT_PERIOD', 'end_date', 61],
  ['top10_floatholders', true, 'STOCK', 'NATIVE_RANGE', 'REPORT_PERIOD', 'end_date', 62],
  ['new_share', true, 'DATES', 'NATIVE_RANGE', 'ISSUE_DATE', 'ipo_date', 123],
  ['stk_managers', true, 'STOCK', 'NATIVE_RANGE', 'ANNOUNCEMENT_DATE', 'ann_date', 193],
  ['pledge_stat', false, null, null, null, null, 110],
  ['pledge_detail', true, 'STOCK', 'NATIVE_RANGE', 'ANNOUNCEMENT_DATE', 'ann_date', 111],
  ['index_classify', false, null, null, null, null, 181],
  ['index_member_all', false, null, null, null, null, 335],
]

const SINGLE_PARAMS = {
  stock_basic: { ts_code: '000001.SZ', list_status: 'L' },
  stock_company: { ts_code: '000001.SZ', exchange: 'SZSE' },
  income: { ts_code: '000001.SZ', ann_date: '20260807' },
  balancesheet: { ts_code: '000001.SZ', ann_date: '20260807' },
  cashflow: { ts_code: '000001.SZ', ann_date: '20260807' },
  fina_indicator: { ts_code: '000001.SZ', ann_date: '20260807' },
  fina_audit: { ts_code: '000001.SZ', ann_date: '20260807' },
  fina_mainbz: { ts_code: '000001.SZ' },
  stk_rewards: { ts_code: '000001.SZ' },
  stk_holdernumber: { ts_code: '000001.SZ' },
  trade_cal: { exchange: 'SSE', start_date: '20260807', end_date: '20260807' },
  margin: { exchange_id: 'SSE', trade_date: '20260807' },
  daily: { ts_code: '000001.SZ', trade_date: '20260807' },
  weekly: { ts_code: '000001.SZ', trade_date: '20260807' },
  monthly: { ts_code: '000001.SZ', trade_date: '20260807' },
  adj_factor: { ts_code: '000001.SZ', trade_date: '20260807' },
  suspend_d: { ts_code: '000001.SZ', trade_date: '20260807' },
  daily_basic: { ts_code: '000001.SZ', trade_date: '20260807' },
  moneyflow: { ts_code: '000001.SZ', trade_date: '20260807' },
  stk_limit: { ts_code: '000001.SZ', trade_date: '20260807' },
  top_list: { ts_code: '000001.SZ', trade_date: '20260807' },
  margin_detail: { ts_code: '000001.SZ', trade_date: '20260807' },
  block_trade: { ts_code: '000001.SZ', trade_date: '20260807' },
  slb_len: { trade_date: '20260807' },
  slb_sec: { ts_code: '000001.SZ', trade_date: '20260807' },
  slb_sec_detail: { ts_code: '000001.SZ', trade_date: '20260807' },
  forecast: { ts_code: '000001.SZ', ann_date: '20260807' },
  express: { ts_code: '000001.SZ', ann_date: '20260807' },
  dividend: { ts_code: '000001.SZ', ann_date: '20260807' },
  disclosure_date: { ts_code: '000001.SZ', ann_date: '20260807' },
  repurchase: { ann_date: '20260807' },
  stk_holdertrade: { ts_code: '000001.SZ', ann_date: '20260807' },
  top10_holders: { ts_code: '000001.SZ', ann_date: '20260807' },
  top10_floatholders: { ts_code: '000001.SZ', ann_date: '20260807' },
  new_share: { start_date: '20260807', end_date: '20260807' },
  stk_managers: { ts_code: '000001.SZ' },
  pledge_stat: { ts_code: '000001.SZ' },
  pledge_detail: { ts_code: '000001.SZ' },
  index_classify: {},
  index_member_all: { ts_code: '000001.SZ' },
}

function clone(value) {
  return structuredClone(value)
}

function initialIndex() {
  return {
    schemaVersion: 1,
    inputHashes: {
      manifestSha256: '386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984',
      requestExamplesSha256: '6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f',
    },
    interfaces: EXPECTED_MATRIX.map(([
      apiName, rangeTarget, shape, planningMode, dateAxis, outputDateColumn, docId,
    ]) => ({
      apiName,
      rangeTarget,
      shape,
      planningMode,
      dateAxis,
      outputDateColumn,
      officialUrl: `https://tushare.pro/document/2?doc_id=${docId}`,
      completeness: { kind: 'UNKNOWN', rowLimit: null, evidenceRefs: [] },
      sourceStatus: 'NOT_RUN',
      taskStatus: 'NOT_RUN',
      disposition: rangeTarget ? 'NEEDS_VERIFICATION' : 'SINGLE_ONLY',
      policyVersion: rangeTarget ? 'tushare-range-v1' : null,
      cases: [],
      unresolved: rangeTarget
        ? ['OFFICIAL_COMPLETENESS_EVIDENCE_MISSING', 'SOURCE_EVIDENCE_MISSING', 'TASK_EVIDENCE_MISSING']
        : ['RANGE_UNSUPPORTED_BY_DESIGN'],
      decisionRef: rangeTarget ? null : 'docs/task-designs/ISSUE-018-T13-design.md#40项实施矩阵',
    })),
    runs: [],
  }
}

function persistedIndex() {
  return JSON.parse(readFileSync(EVIDENCE_PATH, 'utf8'))
}

test('ISSUE-031 preserves SOURCE contracts and records the partial TASK outcome', () => {
  const index = persistedIndex()
  const accepted = new Set(['daily_basic', 'stk_limit', 'moneyflow', 'margin_detail'])
  const candidates = index.interfaces.filter((entry) => entry.rangeTarget && !accepted.has(entry.apiName))
  const indexed = new Map(index.runs.flatMap((run) =>
    run.cases.map((evidence) => [evidence.caseId, { run, evidence }])))
  const sources = candidates.flatMap((entry) => entry.cases.map((caseId) => indexed.get(caseId))
    .filter(({ evidence }) => evidence.phase === 'SOURCE' && evidence.mode === 'RANGE'))

  assert.equal(index.schemaVersion, 2)
  assert.equal(candidates.length, 30)
  assert.deepEqual(Object.fromEntries(candidates.map((entry) => [entry.apiName, entry.completeness.kind])) , {
    income: 'RESPONSE_ONLY', balancesheet: 'RESPONSE_ONLY', cashflow: 'RESPONSE_ONLY',
    fina_indicator: 'ROW_LIMIT', fina_audit: 'RESPONSE_ONLY', fina_mainbz: 'ROW_LIMIT',
    stk_holdernumber: 'ROW_LIMIT', trade_cal: 'CALENDAR_COVERAGE', margin: 'ROW_LIMIT',
    daily: 'ROW_LIMIT', weekly: 'ROW_LIMIT', monthly: 'ROW_LIMIT', adj_factor: 'RESPONSE_ONLY',
    suspend_d: 'RESPONSE_ONLY', top_list: 'ROW_LIMIT', block_trade: 'ROW_LIMIT',
    slb_len: 'ROW_LIMIT', slb_sec: 'ROW_LIMIT', slb_sec_detail: 'ROW_LIMIT',
    forecast: 'ROW_LIMIT', express: 'RESPONSE_ONLY', dividend: 'ROW_LIMIT',
    disclosure_date: 'ROW_LIMIT', repurchase: 'RESPONSE_ONLY', stk_holdertrade: 'ROW_LIMIT',
    top10_holders: 'RESPONSE_ONLY', top10_floatholders: 'RESPONSE_ONLY',
    new_share: 'ROW_LIMIT', stk_managers: 'RESPONSE_ONLY', pledge_detail: 'ROW_LIMIT',
  })
  assert.equal(sources.length, 272)
  assert.ok(sources.every(({ run, evidence }) => run.exitCode === 0
    && run.cleanup.status === 'PASS' && evidence.status === 'PASS'))
  assert.ok(candidates.every((entry) => entry.sourceStatus === 'PASS'))
  assert.equal(createHash('sha256').update(JSON.stringify(index.runs.slice(0, 26))).digest('hex'),
    '95b2e7e0a4743f982d1818540ea8075f694050b9e0ac51a13617f63529381313')
  const rounds = index.runs.slice(26, 30)
  assert.deepEqual(rounds.map((run) => run.cases.length), [28, 16, 38, 35])
  assert.deepEqual(rounds.map((run) => run.exitCode), [0, 0, 0, 1])
  assert.ok(rounds.every((run) => run.cleanup.status === 'PASS'))
  const tasks = rounds.flatMap((run) => run.cases)
  assert.deepEqual(Object.fromEntries(['PASS', 'FAILED', 'NOT_RUN'].map((status) =>
    [status, tasks.filter((c) => c.status === status).length])), { PASS: 82, FAILED: 1, NOT_RUN: 34 })
  const failed = tasks.find((c) => c.status === 'FAILED')
  assert.equal(failed.caseId, 'issue026-issue022-indicator-000001-whole')
  assert.equal(failed.errorCode, 'ADAPTER_TYPE_INVALID')
  assert.equal(failed.requestCount, 1)
  assert.equal(failed.sqlBeforeKeyCount, 0)
  assert.equal(failed.sqlAfterKeyCount, 0)
  assert.equal(failed.insertedRows, 0)
  assert.equal(failed.updatedRows, 0)
  assert.ok(tasks.filter((c) => c.status === 'NOT_RUN').every((c) => c.taskId === null))
})

test('ISSUE-031 preserves completed continuation rounds and the interrupted response-only evidence', () => {
  const index = persistedIndex()
  const completed = index.runs.slice(30, 33)
  assert.deepEqual(completed.map((run) => run.cases.length), [29, 37, 33])
  assert.deepEqual(completed.map((run) => run.cases.reduce((n, c) => n + c.requestCount, 0)), [29, 129, 33])
  assert.ok(completed.every((run) => run.exitCode === 0 && run.cleanup.status === 'PASS'
    && run.cases.every((c) => c.status === 'PASS' && c.apiName !== 'fina_indicator')))
  const oldDates = index.runs[29].cases
  for (const current of completed[0].cases) {
    const old = oldDates.find((c) => current.caseId === `issue031-resume-${c.caseId}`)
    assert.equal(old.status, 'NOT_RUN')
    assert.deepEqual(current.params, old.params)
    assert.equal(current.dateAxis, old.dateAxis)
    const refs = index.interfaces.find((e) => e.apiName === current.apiName).cases
    assert.ok(refs.includes(current.caseId))
    assert.ok(!refs.includes(old.caseId))
  }
  const interrupted = index.runs[33]
  assert.equal(interrupted.exitCode, 1)
  assert.equal(interrupted.cleanup.status, 'PASS')
  assert.equal(interrupted.cases.length, 24)
  const first = interrupted.cases[0]
  assert.equal(first.apiName, 'adj_factor')
  assert.equal(first.status, 'EVIDENCE_MISSING')
  assert.equal(first.requestCount, 1)
  assert.equal(first.expectedCoverage, 'RESPONSE_ONLY')
  assert.equal(first.errorCode, null)
  assert.equal(first.sourceRowCount, null)
  assert.equal(first.sqlAfterKeyCount, null)
  assert.ok(first.taskId)
  assert.ok(interrupted.cases.slice(1).every((c) => c.status === 'NOT_RUN' && c.taskId === null))
})

test('ISSUE-031 preserves response-only recovery and the actual balancesheet failure', () => {
  const index = persistedIndex()
  const recovered = index.runs[34]
  assert.equal(recovered.runId, 'issue031-statusfix-range-response-trade-20260914T194412Z')
  assert.equal(recovered.exitCode, 0)
  assert.equal(recovered.cleanup.status, 'PASS')
  assert.equal(recovered.cases.length, 24)
  for (const current of recovered.cases) {
    assert.equal(current.status, 'PASS')
    const old = index.runs[33].cases.find((c) => current.caseId === `issue031-statusfix-${c.caseId}`)
    assert.ok(old)
    assert.deepEqual(current.params, old.params)
    assert.equal(current.dateAxis, old.dateAxis)
    const refs = index.interfaces.find((e) => e.apiName === current.apiName).cases
    assert.ok(refs.includes(current.caseId))
    assert.ok(!refs.includes(old.caseId))
  }
  const failedRun = index.runs[35]
  assert.equal(failedRun.runId, 'issue031-statusfix-range-response-announcement-20260914T195234Z')
  assert.equal(failedRun.exitCode, 1)
  assert.equal(failedRun.cleanup.status, 'PASS')
  assert.equal(failedRun.cases.length, 42)
  assert.ok(failedRun.cases.slice(0, 8).every((c) => c.apiName === 'income' && c.status === 'PASS'))
  const income = index.interfaces.find((e) => e.apiName === 'income')
  assert.equal(income.disposition, 'AVAILABLE')
  const failedOnly = structuredClone(index)
  const entry = failedOnly.interfaces.find((e) => e.apiName === 'income')
  const byId = new Map(index.runs.flatMap((r) => r.cases.map((c) => [c.caseId, c])))
  entry.cases = entry.cases.filter((id) => byId.get(id).phase !== 'TASK' || byId.get(id).mode !== 'RANGE')
  entry.cases.push(...failedRun.cases.slice(0, 8).map((c) => c.caseId))
  expectRejected(failedOnly) // Successful observations in an exit1 run cannot replace clean acceptance.
  const failed = failedRun.cases[8]
  assert.equal(failed.apiName, 'balancesheet')
  assert.equal(failed.status, 'FAILED')
  assert.equal(failed.errorCode, 'ADAPTER_TYPE_INVALID')
  assert.equal(failed.requestCount, 1)
  assert.equal(failed.failedLeafCount, 1)
  assert.equal(failed.succeededLeafCount, 0)
  assert.equal(failed.emptyLeafCount, 0)
  assert.equal(failed.sqlBeforeKeyCount, 0)
  assert.equal(failed.sqlAfterKeyCount, 0)
  assert.ok(failedRun.cases.slice(9).every((c) => c.status === 'NOT_RUN' && c.taskId === null))
})

test('ISSUE-031 retains the cashflow failure without inventing AFTER evidence', () => {
  const index = persistedIndex()
  const run = index.runs[36]
  assert.equal(run.runId, 'issue031-keyconflict-range-response-announcement-rest-20260914T202312Z')
  assert.equal(run.exitCode, 1)
  assert.equal(run.cleanup.status, 'PASS')
  assert.equal(run.cases.length, 34)
  assert.ok(run.cases.slice(0, 8).every((c) => c.apiName === 'income' && c.status === 'PASS'))
  const failed = run.cases[8]
  assert.equal(failed.apiName, 'cashflow')
  assert.equal(failed.status, 'FAILED')
  assert.equal(failed.errorCode, 'ADAPTER_TYPE_INVALID')
  assert.equal(failed.requestCount, 1)
  assert.equal(failed.failedLeafCount, 1)
  assert.equal(failed.succeededLeafCount, 0)
  assert.equal(failed.emptyLeafCount, 0)
  assert.equal(failed.sqlBeforeKeyCount, null)
  assert.equal(failed.sqlAfterKeyCount, null)
  assert.ok(run.cases.slice(9).every((c) => c.status === 'NOT_RUN' && c.taskId === null))
  const entry = index.interfaces.find((e) => e.apiName === 'cashflow')
  assert.equal(entry.policyVersion, 'tushare-range-v3')
  assert.equal(entry.disposition, 'EXCLUDED')
})

test('ISSUE-031 accepts independent announcement runs while preserving their failed-run predecessors', () => {
  const index = persistedIndex()
  const runs = index.runs.slice(37, 41)
  assert.deepEqual(runs.map((r) => [r.cases[0].apiName, r.cases.length]),
    [['income', 8], ['fina_audit', 4], ['express', 6], ['stk_managers', 8]])
  for (const run of runs) {
    assert.equal(run.exitCode, 0)
    assert.equal(run.cleanup.status, 'PASS')
    assert.equal(run.acceptanceJarSha256, 'a77d99aca450fe0345869a89392a9889480753f23c41c814bf81ef6f70c732d3')
    for (const current of run.cases) {
      assert.equal(current.status, 'PASS')
      assert.equal(current.requestCount, 1)
      assert.equal(current.expectedCoverage, 'RESPONSE_ONLY')
      const entry = index.interfaces.find((e) => e.apiName === current.apiName)
      assert.equal(entry.disposition, 'AVAILABLE')
      assert.ok(entry.cases.includes(current.caseId))
      for (const [offset, prefix] of [[35, 'issue031-perapi-issue031-keyconflict-'], [36, 'issue031-perapi-']]) {
        const old = index.runs[offset].cases.find((c) => `${prefix}${c.caseId}` === current.caseId)
        assert.ok(old)
        assert.equal(old.status, current.apiName === 'income' ? 'PASS' : 'NOT_RUN')
        assert.deepEqual(current.params, old.params)
        assert.equal(current.dateAxis, old.dateAxis)
        assert.ok(!entry.cases.includes(old.caseId))
      }
    }
  }
})

test('ISSUE-031 preserves the repurchase failure and four unexecuted tasks', () => {
  const index = persistedIndex()
  const run = index.runs[41]
  assert.equal(run.runId, 'issue031-perapi-range-repurchase-20260914T211042Z')
  assert.equal(run.exitCode, 1)
  assert.equal(run.cleanup.status, 'PASS')
  assert.equal(run.cases.length, 5)
  const failed = run.cases[0]
  assert.equal(failed.apiName, 'repurchase')
  assert.deepEqual(failed.params, {start_date: '20260801', end_date: '20260831'})
  assert.equal(failed.status, 'FAILED')
  assert.equal(failed.errorCode, 'ADAPTER_TYPE_INVALID')
  assert.equal(failed.requestCount, 1)
  assert.equal(failed.failedLeafCount, 1)
  assert.equal(failed.succeededLeafCount, 0)
  assert.equal(failed.emptyLeafCount, 0)
  assert.equal(failed.sqlBeforeKeyCount, null)
  assert.equal(failed.sqlAfterKeyCount, null)
  assert.ok(run.cases.slice(1).every((c) => c.status === 'NOT_RUN' && c.taskId === null))
  const entry = index.interfaces.find((e) => e.apiName === 'repurchase')
  assert.equal(entry.policyVersion, 'tushare-range-v3')
  assert.equal(entry.disposition, 'EXCLUDED')
})

test('ISSUE-031 records both holder runs and the four original-range regressions under their new build', () => {
  const index = persistedIndex()
  const runs = index.runs.slice(42, 45)
  assert.deepEqual(runs.map((r) => r.cases.length), [8, 8, 4])
  for (const run of runs) {
    assert.equal(run.exitCode, 0)
    assert.equal(run.cleanup.status, 'PASS')
    assert.equal(run.acceptanceJarSha256, '5ecb993e13d6c1340013eb48ca87ea4a91782906dd06bd51f2108b7b51c1bfd5')
    assert.ok(run.cases.every((c) => c.status === 'PASS' && c.requestCount === 1
      && c.sqlBeforeKeyCount !== null && c.sqlAfterKeyCount !== null))
  }
  for (const [offset, api] of ['top10_holders', 'top10_floatholders'].entries()) {
    const cases = runs[offset].cases
    assert.ok(cases.every((c) => c.apiName === api && c.dateAxis === 'REPORT_PERIOD'
      && c.expectedCoverage === 'RESPONSE_ONLY'))
    assert.equal(cases.at(-1).sqlAfterKeyCount, 100)
    assert.ok(cases.some((c) => c.sourceRowCount > 10))
    assert.equal(index.interfaces.find((e) => e.apiName === api).disposition, 'AVAILABLE')
  }
  assert.deepEqual(runs[2].cases.map((c) => [c.apiName, c.sourceRowCount, c.sqlAfterKeyCount]),
    [['daily_basic', 4, 4], ['moneyflow', 6, 6], ['stk_limit', 6, 6], ['margin_detail', 6, 6]])
})

test('ISSUE-030 independent mother-issue inputs preserve exact group counts and four new mainbz cases', () => {
  assert.deepEqual(Object.fromEntries(Object.entries(ISSUE030_SOURCE_GROUPS)
    .map(([group, inputs]) => [group, inputs.length])), ISSUE030_GROUP_COUNTS)
  assert.equal(ISSUE030_SOURCE_INPUTS.length, 272)
  assert.equal(new Set(ISSUE030_SOURCE_INPUTS
    .map(({ runId, caseId }) => `${runId}/${caseId}`)).size, 272)
  assert.deepEqual(ISSUE030_SOURCE_GROUPS['ISSUE-029'].map(({ runId, caseId }) => [runId, caseId]), [
    ['issue026-mainbz-split-source-20260914T013736Z',
      'issue026-mainbz-split-source-20260914T013736Z-000001-annual'],
    ['issue026-mainbz-split-source-20260914T013736Z',
      'issue026-mainbz-split-source-20260914T013736Z-600000-annual'],
    ['issue026-mainbz-split-source-20260914T013736Z',
      'issue026-mainbz-split-source-20260914T013736Z-000001-wide'],
    ['issue026-mainbz-split-source-20260914T013736Z',
      'issue026-mainbz-split-source-20260914T013736Z-600000-wide'],
  ])
})

test('candidate rebuild preserves all runs, rejects reused TASK IDs and verifies original bindings', () => {
  const before = persistedIndex()
  const index = applyIssue030CandidateIndex(before)
  const plan = buildIssue030CandidatePlan(index)
  const bindings = new Map()
  const byIdentity = new Map(before.runs.flatMap((run) => run.cases.map((evidence) => [
    `${run.runId}/${evidence.caseId}`,
    evidence,
  ])))
  assert.deepEqual(index.runs, before.runs)
  assert.equal(plan.runId, 'issue026-candidate-inputs')
  assert.ok(plan.cases.slice(78, 113).every(({ caseId }) => caseId.startsWith('issue026-issue022-')))
  assert.ok(plan.cases.slice(113, 148).every(({ caseId }) => caseId.startsWith('issue026-issue023-')))
  assert.ok(plan.cases.slice(148, 181).every(({ caseId }) => caseId.startsWith('issue026-issue024-')))
  assert.ok(plan.cases.slice(181, 268).every(({ caseId }) => caseId.startsWith('issue026-issue025-')))
  assert.ok(plan.cases.slice(268).every(({ caseId }) =>
    caseId.startsWith('issue026-task-issue026-mainbz-split-source-')))
  assert.equal(validateEvidence(index), index)
  assert.equal(validateCasePlan(plan), plan)
  assert.throws(() => liveContract.selectTaskCases('range', plan, index, new Map()), { message: SAFE_ERROR })
  const historical = structuredClone(index)
  historical.runs = historical.runs.slice(0, 26)
  const historicalIds = new Set(historical.runs.flatMap((run) => run.cases.map((c) => c.caseId)))
  for (const entry of historical.interfaces) entry.cases = entry.cases.filter((id) => historicalIds.has(id))
  assert.equal(validateEvidence(historical), historical)
  assert.equal(liveContract.selectTaskCases('range', plan, historical, bindings).length, 272)
  assert.equal(bindings.size, 272)
  for (const [position, input] of ISSUE030_SOURCE_INPUTS.entries()) {
    const source = byIdentity.get(`${input.runId}/${input.caseId}`)
    assert.deepEqual(bindings.get(plan.cases[position].caseId), {
      runId: input.runId,
      caseId: input.caseId,
      expectedCoverage: source.expectedCoverage,
      expectedCoverageSource: source.expectedCoverageSource,
    })
  }
})

test('ISSUE-030 retains adopted rule references and only reuses SINGLE tasks from a clean run', () => {
  const index = applyIssue030CandidateIndex(persistedIndex())
  assert.ok(index.interfaces.find(({ apiName }) => apiName === 'fina_mainbz').completeness.evidenceRefs
    .includes('docs/verification/ISSUE-018-T14-runs.md#issue-020-分类与限量独立取证'))
  assert.ok(index.interfaces.find(({ apiName }) => apiName === 'trade_cal').completeness.evidenceRefs
    .includes('docs/task-designs/ISSUE-018-T13-design.md#40项实施矩阵'))

  const dirty = persistedIndex()
  dirty.runs.find(({ runId }) => runId === 'issue018-t14-single-20260913T093339Z').exitCode = 1
  const rebuilt = applyIssue030CandidateIndex(dirty)
  const cases = new Map(rebuilt.runs.flatMap((run) => run.cases.map((evidence) =>
    [evidence.caseId, evidence])))
  for (const entry of rebuilt.interfaces.filter(({ rangeTarget, apiName }) => rangeTarget
    && !['daily_basic', 'stk_limit', 'moneyflow', 'margin_detail'].includes(apiName))) {
    assert.equal(entry.taskStatus, 'NOT_RUN')
    assert.equal(entry.cases.some((caseId) => cases.get(caseId).phase === 'TASK'), false)
  }
})

test('ISSUE-030 actual bindings reject wrong or ambiguous mainbz, old full rows and omitted identities', () => {
  const index = applyIssue030CandidateIndex(persistedIndex())
  const plan = buildIssue030CandidatePlan(index)
  const one = (caseId) => ({ runId: `check-${caseId}`, cases: [
    structuredClone(plan.cases.find((entry) => entry.caseId === caseId)),
  ] })
  const annualId = 'issue026-task-issue026-mainbz-split-source-20260914T013736Z-000001-annual'
  const annual = one(annualId)
  annual.cases[0].evidenceRefs = annual.cases[0].evidenceRefs.filter((ref) =>
    !ref.startsWith('docs/verification/ISSUE-018-range-acceptance.json#'))
  assert.throws(() => liveContract.selectTaskCases('range', annual, index), { message: SAFE_ERROR })

  const wrongRun = one(annualId)
  wrongRun.cases[0].evidenceRefs = wrongRun.cases[0].evidenceRefs.map((ref) =>
    ref.includes('#issue026-mainbz-split-source-20260914T013736Z/')
      ? ref.replace('#issue026-mainbz-split-source-20260914T013736Z/',
        '#issue020-source-20260913T131245Z/') : ref)
  assert.throws(() => liveContract.selectTaskCases('range', wrongRun, index), { message: SAFE_ERROR })

  const full = index.runs.find(({ runId }) => runId === 'issue020-source-20260913T131245Z')
    .cases.find(({ caseId }) => caseId.endsWith('-000001-wide-limit'))
  const entry = index.interfaces.find(({ apiName }) => apiName === 'fina_mainbz')
  const fullPlan = { runId: 'check-old-full-mainbz', cases: [{
    caseId: 'check-old-full-mainbz-case', apiName: full.apiName, mode: full.mode,
    params: full.params, dateAxis: full.dateAxis, start: full.params.start_date,
    end: full.params.end_date, evidenceRefs: [...entry.completeness.evidenceRefs,
      `docs/verification/ISSUE-018-range-acceptance.json#issue020-source-20260913T131245Z/${full.caseId}`],
  }] }
  assert.throws(() => liveContract.selectTaskCases('range', fullPlan, index), { message: SAFE_ERROR })

  const recheckRun = index.runs.find(({ runId }) => runId === 'issue023-boundary-source-20260913T145716Z')
  const recheck = recheckRun.cases.find(({ caseId }) => caseId.endsWith('-disclosure-000001-same-key-recheck'))
  const disclosure = index.interfaces.find(({ apiName }) => apiName === 'disclosure_date')
  const unreferenced = { runId: 'check-unreferenced-source', cases: [{
    caseId: 'check-unreferenced-source-case', apiName: recheck.apiName, mode: recheck.mode,
    params: recheck.params, dateAxis: recheck.dateAxis, start: recheck.params.start_date,
    end: recheck.params.end_date, evidenceRefs: [...disclosure.completeness.evidenceRefs,
      `docs/verification/ISSUE-018-range-acceptance.json#${recheckRun.runId}/${recheck.caseId}`],
  }] }
  assert.throws(() => liveContract.selectTaskCases('range', unreferenced, index), { message: SAFE_ERROR })

  const response = structuredClone(plan.cases.find(({ apiName }) => apiName === 'adj_factor'))
  response.caseId = 'check-response-without-source-identity'
  response.evidenceRefs = response.evidenceRefs.filter((ref) =>
    !ref.startsWith('docs/verification/ISSUE-018-range-acceptance.json#'))
  assert.throws(() => liveContract.selectTaskCases('range',
    { runId: 'check-response-without-source-identity', cases: [response] }, index),
  { message: SAFE_ERROR })
})

test('ISSUE-030 SOURCE-only candidates cannot become formal AVAILABLE interfaces', () => {
  const index = applyIssue030CandidateIndex(persistedIndex())
  const entry = index.interfaces.find(({ apiName }) => apiName === 'adj_factor')
  Object.assign(entry, { disposition: 'AVAILABLE', unresolved: [] })
  assert.throws(() => validateEvidence(index), { message: SAFE_ERROR })
})

test('persisted contracts retain accepted ranges and explicitly exclude four RANGE APIs', () => {
  const index = persistedIndex()
  const limits = { weekly: 6000, monthly: 4500, daily_basic: 6000, stk_limit: 5800,
    moneyflow: 6000, margin: 4000, margin_detail: 6000, block_trade: 1000, slb_len: 5000,
    slb_sec: 5000, slb_sec_detail: 5000, new_share: 2000, stk_holdernumber: 3000,
    stk_holdertrade: 3000, pledge_detail: 1000, fina_indicator: 100, fina_mainbz: 100,
    top_list: 10000, disclosure_date: 6000, daily: 6000, forecast: 3500, dividend: 2000 }
  const responseOnly = ['adj_factor', 'suspend_d', 'income', 'balancesheet', 'cashflow',
    'fina_audit', 'express', 'repurchase', 'stk_managers', 'top10_holders', 'top10_floatholders']
  const unknown = ['pledge_stat', 'stk_rewards', 'stock_basic', 'stock_company',
    'index_classify', 'index_member_all']
  const available = new Set(['daily_basic', 'stk_limit', 'moneyflow', 'margin_detail',
    'daily', 'forecast', 'dividend', 'fina_mainbz', 'margin', 'top_list',
    'stk_holdernumber', 'trade_cal', 'weekly', 'monthly', 'new_share',
    'block_trade', 'disclosure_date', 'stk_holdertrade', 'pledge_detail',
    'slb_len', 'slb_sec', 'slb_sec_detail', 'adj_factor', 'suspend_d',
    'income', 'fina_audit', 'express', 'stk_managers', 'top10_holders', 'top10_floatholders'])

  assert.deepEqual(Object.fromEntries(index.interfaces.filter((i) => i.completeness.kind === 'ROW_LIMIT')
    .map((i) => [i.apiName, i.completeness.rowLimit])), limits)
  assert.deepEqual(index.interfaces.filter((i) => i.completeness.kind === 'RESPONSE_ONLY')
    .map((i) => i.apiName).sort(), responseOnly.sort())
  assert.deepEqual(index.interfaces.filter((i) => i.completeness.kind === 'UNKNOWN')
    .map((i) => i.apiName).sort(), unknown.sort())
  assert.deepEqual(index.interfaces.filter((i) => i.completeness.kind === 'CALENDAR_COVERAGE')
    .map((i) => [i.apiName, i.completeness.rowLimit]), [['trade_cal', null]])

  assert.deepEqual(index.interfaces.filter((entry) => entry.disposition === 'EXCLUDED')
    .map((entry) => entry.apiName).sort(), ['balancesheet', 'cashflow', 'fina_indicator', 'repurchase'])
  for (const entry of index.interfaces.filter((entry) => entry.disposition === 'EXCLUDED')) {
    assert.ok(entry.completeness.evidenceRefs
      .includes('docs/issues/proposals/ISSUE-026-range-scope.md#决策记录'))
  }

  for (const entry of index.interfaces) {
    const accepted = available.has(entry.apiName)
    assert.equal(entry.disposition,
      accepted ? 'AVAILABLE' : entry.rangeTarget ? 'EXCLUDED' : 'SINGLE_ONLY')
    assert.equal(entry.policyVersion, ['fina_indicator', 'balancesheet', 'cashflow', 'repurchase'].includes(entry.apiName) ? 'tushare-range-v3'
      : entry.rangeTarget ? 'tushare-range-v2' : null)
    if (!entry.rangeTarget) continue
    assert.equal(entry.sourceStatus, 'PASS')
    assert.equal(entry.taskStatus, ['fina_indicator', 'balancesheet', 'cashflow', 'repurchase'].includes(entry.apiName) ? 'FAILED' : 'PASS')
    assert.ok(entry.completeness.evidenceRefs
      .includes(`docs/verification/ISSUE-018-range-acceptance.md#${entry.apiName}`))
    if (!accepted) assert.ok(entry.completeness.evidenceRefs.includes(entry.officialUrl))
    if (accepted) assert.deepEqual(entry.unresolved, [])
    else {
      assert.ok(entry.unresolved.includes('RANGE_TASK_NOT_RUN'))
      assert.ok(entry.unresolved.includes('RANGE_SQL_NOT_VERIFIED'))
    }
  }
  assert.equal(index.runs.flatMap((run) => run.cases)
    .filter((evidence) => evidence.phase === 'TASK' && evidence.mode === 'RANGE').length, 416)
  assert.equal(validateEvidence(index), index)
})

test('verified source contracts cannot open ranges without matching RANGE task evidence', () => {
  for (const apiName of ['daily', 'forecast', 'dividend', 'fina_mainbz', 'trade_cal', 'margin', 'top_list',
    'weekly', 'monthly', 'fina_indicator', 'stk_holdernumber', 'new_share',
    'block_trade', 'disclosure_date', 'stk_holdertrade', 'pledge_detail', 'slb_len', 'slb_sec', 'slb_sec_detail']) {
    const index = applyIssue030CandidateIndex(persistedIndex())
    const entry = index.interfaces.find((item) => item.apiName === apiName)
    assert.equal(entry.completeness.kind, apiName === 'trade_cal' ? 'CALENDAR_COVERAGE' : 'ROW_LIMIT')
    // Even with only successful SOURCE samples, SINGLE tasks cannot stand in for RANGE tasks.
    const cases = index.runs.flatMap((run) => run.cases)
    entry.cases = entry.cases.filter((id) => cases.find((item) => item.caseId === id).status === 'PASS')
    entry.sourceStatus = 'PASS'
    assert.equal(validateEvidence(index), index)
    entry.disposition = 'AVAILABLE'
    entry.policyVersion = 'tushare-range-v2'
    entry.unresolved = []
    expectRejected(index)
  }
})

function expectRejected(value, validator = validateEvidence) {
  assert.throws(() => validator(value), { message: SAFE_ERROR })
}

function validRangePlan() {
  return {
    runId: 'issue018-t13-source-01',
    cases: [{
      caseId: 'daily-range-000001-20260803-20260810',
      apiName: 'daily',
      mode: 'RANGE',
      params: { ts_code: '000001.SZ', start_date: '20260803', end_date: '20260810' },
      dateAxis: 'TRADE_DATE',
      start: '20260803',
      end: '20260810',
      evidenceRefs: ['docs/verification/ISSUE-018-range-acceptance.md#daily'],
    }],
  }
}

function validRun(caseEvidence) {
  return {
    runId: 'issue018-t13-source-01',
    startedAt: '2026-09-12T01:02:03.000Z',
    finishedAt: '2026-09-12T01:03:03.000Z',
    sourceDiffSha256: '1'.repeat(64),
    productionJarSha256: '2'.repeat(64),
    acceptanceJarSha256: '3'.repeat(64),
    manifestSha256: '386f46a99b6605e203129836d7a744b96b65304307f52991dd8bba6fd1870984',
    requestExamplesSha256: '6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f',
    commands: ['node --test control-plane/e2e/tushare-range-evidence.test.js'],
    exitCode: 0,
    cleanup: {
      status: 'PASS',
      completedAt: '2026-09-12T01:04:03.000Z',
      evidencePath: 'private/source-evidence.json',
    },
    cases: [caseEvidence],
  }
}

function validTaskEvidence() {
  return safeCaseEvidence({
    caseId: 'daily-task', apiName: 'daily', phase: 'TASK', mode: 'RANGE',
    params: { ts_code: '000001.SZ', start_date: '20260803', end_date: '20260810' },
    dateAxis: 'TRADE_DATE', expectedCoverage: 'closed range',
    expectedCoverageSource: 'official-source', status: 'PASS', errorCode: null,
    taskId: '11111111-1111-4111-8111-111111111111',
    submissionId: '22222222-2222-4222-8222-222222222222', requestCount: 1,
    batchNodes: [{
      batchId: '33333333-3333-4333-8333-333333333333', parentBatchId: null,
      status: 'SUCCEEDED', start: '20260803', end: '20260810', sourceRowCount: 6,
      insertedRows: 6, updatedRows: 0, errorCode: null,
    }],
    leafCount: 1, succeededLeafCount: 1, failedLeafCount: 0, emptyLeafCount: 0,
    sourceRowCount: 6, insertedRows: 6, updatedRows: 0,
    sqlBeforeKeyCount: 0, sqlAfterKeyCount: 6, ownershipSummary: '000001.SZ:6',
    businessKeyDigest: '4'.repeat(64), reviewMethod: 'task API plus read-only SQL',
    evidencePaths: ['private/task-evidence.json'],
  })
}

function availableIndex() {
  const index = initialIndex()
  const source = safeCaseEvidence({
    caseId: 'daily-source', apiName: 'daily', phase: 'SOURCE', mode: 'RANGE',
    params: { ts_code: '000001.SZ', start_date: '20260803', end_date: '20260810' },
    dateAxis: 'TRADE_DATE', expectedCoverage: 'closed range',
    expectedCoverageSource: 'official-source', status: 'PASS', errorCode: null,
    taskId: null, submissionId: null, requestCount: 1,
    batchNodes: [{
      batchId: 'source-1', parentBatchId: null, status: 'SUCCESS',
      start: '20260803', end: '20260810', sourceRowCount: 6,
      insertedRows: null, updatedRows: null, errorCode: null,
    }],
    leafCount: 1, succeededLeafCount: 1, failedLeafCount: 0, emptyLeafCount: 0,
    sourceRowCount: 6, insertedRows: null, updatedRows: null,
    sqlBeforeKeyCount: null, sqlAfterKeyCount: null, ownershipSummary: null,
    businessKeyDigest: null, reviewMethod: 'date-axis comparison',
    evidencePaths: ['private/source-evidence.json'],
  })
  const taskRun = validRun(validTaskEvidence())
  taskRun.runId = 'issue018-t13-task-01'
  taskRun.sourceDiffSha256 = '5'.repeat(64)
  taskRun.productionJarSha256 = '6'.repeat(64)
  taskRun.acceptanceJarSha256 = '7'.repeat(64)
  index.runs.push(validRun(source), taskRun)
  const daily = index.interfaces.find(({ apiName }) => apiName === 'daily')
  Object.assign(daily, {
    completeness: {
      kind: 'ROW_LIMIT', rowLimit: 6000,
      evidenceRefs: ['docs/verification/ISSUE-018-range-acceptance.md#daily'],
    },
    sourceStatus: 'PASS', taskStatus: 'PASS', disposition: 'AVAILABLE',
    policyVersion: 'tushare-range-v2', cases: ['daily-source', 'daily-task'], unresolved: [],
    decisionRef: 'docs/verification/ISSUE-018-range-acceptance.md#daily',
  })
  return index
}

function twoCleanDailySources() {
  const index = availableIndex()
  index.runs.pop()
  const firstRun = index.runs[0]
  const first = firstRun.cases[0]
  const secondRun = clone(firstRun)
  secondRun.runId = 'issue029-source-02'
  secondRun.cases[0].caseId = 'daily-source-02'
  secondRun.cases[0].expectedCoverage = 'second clean coverage'
  secondRun.cases[0].expectedCoverageSource = 'second-official-source'
  index.runs.push(secondRun)
  const entry = index.interfaces.find(({ apiName }) => apiName === 'daily')
  Object.assign(entry, {
    taskStatus: 'NOT_RUN', disposition: 'NEEDS_VERIFICATION',
    cases: [first.caseId, secondRun.cases[0].caseId],
  })
  return index
}

const RESPONSE_ONLY_DECISION = 'docs/issues/proposals/ISSUE-025-extraction-contracts.md#决策记录'
const RESPONSE_SOURCE_REF = 'docs/verification/ISSUE-018-range-acceptance.json#issue025-source-01/adj-factor-source'

function responseOnlyIndex() {
  const index = initialIndex()
  index.schemaVersion = 2
  const params = { ts_code: '000001.SZ', start_date: '20260803', end_date: '20260810' }
  const source = safeCaseEvidence({
    caseId: 'adj-factor-source', apiName: 'adj_factor', phase: 'SOURCE', mode: 'RANGE',
    params, dateAxis: 'TRADE_DATE', expectedCoverage: 'observed source response',
    expectedCoverageSource: 'official-source-observation', status: 'PASS', errorCode: null,
    taskId: null, submissionId: null, requestCount: 1,
    batchNodes: [{ batchId: 'source-1', parentBatchId: null, status: 'SUCCESS',
      start: '20260803', end: '20260810', sourceRowCount: 6,
      insertedRows: null, updatedRows: null, errorCode: null }],
    leafCount: 1, succeededLeafCount: 1, failedLeafCount: 0, emptyLeafCount: 0,
    sourceRowCount: 6, insertedRows: null, updatedRows: null,
    sqlBeforeKeyCount: null, sqlAfterKeyCount: null, ownershipSummary: null,
    businessKeyDigest: null, reviewMethod: 'date-axis source observation',
    evidencePaths: ['private/source-evidence.json'],
  })
  const task = safeCaseEvidence({
    caseId: 'adj-factor-task', apiName: 'adj_factor', phase: 'TASK', mode: 'RANGE',
    params, dateAxis: 'TRADE_DATE', expectedCoverage: 'RESPONSE_ONLY',
    expectedCoverageSource: `${RESPONSE_ONLY_DECISION} ${RESPONSE_SOURCE_REF}`,
    status: 'PASS', errorCode: null,
    taskId: '11111111-1111-4111-8111-111111111111',
    submissionId: '22222222-2222-4222-8222-222222222222', requestCount: 1,
    batchNodes: [{ batchId: '33333333-3333-4333-8333-333333333333', parentBatchId: null,
      status: 'SUCCEEDED', start: '20260803', end: '20260810', sourceRowCount: 6,
      insertedRows: 6, updatedRows: 0, errorCode: null }],
    leafCount: 1, succeededLeafCount: 1, failedLeafCount: 0, emptyLeafCount: 0,
    sourceRowCount: 6, insertedRows: 6, updatedRows: 0,
    sqlBeforeKeyCount: 0, sqlAfterKeyCount: 6, ownershipSummary: '000001.SZ:6',
    businessKeyDigest: '4'.repeat(64),
    reviewMethod: 'persisted summary, full response, SQL and page verified',
    evidencePaths: ['private/task-evidence.json'],
  })
  const sourceRun = validRun(source)
  sourceRun.runId = 'issue025-source-01'
  const taskRun = validRun(task)
  taskRun.runId = 'issue029-task-01'
  taskRun.sourceDiffSha256 = '5'.repeat(64)
  taskRun.productionJarSha256 = '6'.repeat(64)
  taskRun.acceptanceJarSha256 = '7'.repeat(64)
  index.runs.push(sourceRun, taskRun)
  const entry = index.interfaces.find(({ apiName }) => apiName === 'adj_factor')
  Object.assign(entry, {
    completeness: { kind: 'RESPONSE_ONLY', rowLimit: null, evidenceRefs: [
      RESPONSE_ONLY_DECISION, 'docs/verification/ISSUE-018-range-acceptance.md#adj_factor',
      entry.officialUrl,
    ] },
    sourceStatus: 'PASS', taskStatus: 'PASS', disposition: 'AVAILABLE',
    policyVersion: 'tushare-range-v2', cases: [source.caseId, task.caseId], unresolved: [],
    decisionRef: RESPONSE_ONLY_DECISION,
  })
  return index
}

test('schema 1 rejects RESPONSE_ONLY while schema 2 accepts its exact adopted contract', () => {
  const index = initialIndex()
  const entry = index.interfaces.find(({ apiName }) => apiName === 'adj_factor')
  const decision = 'docs/issues/proposals/ISSUE-025-extraction-contracts.md#决策记录'
  Object.assign(entry, {
    completeness: {
      kind: 'RESPONSE_ONLY', rowLimit: null,
      evidenceRefs: [decision, 'docs/verification/ISSUE-018-range-acceptance.md#adj_factor', entry.officialUrl],
    },
    decisionRef: decision,
  })
  expectRejected(index)
  index.schemaVersion = 2
  assert.equal(validateEvidence(index), index)
})

test('schema 1 rejects RESPONSE_ONLY TASK meaning hidden behind an old completeness rule', () => {
  const index = availableIndex()
  index.runs[1].cases[0].expectedCoverage = 'RESPONSE_ONLY'
  expectRejected(index)
})

test('schema 1 rejects RESPONSE_ONLY meaning in an unreferenced run case', () => {
  const index = availableIndex()
  const hidden = clone(index.runs[1].cases[0])
  hidden.caseId = 'hidden-response-only-task'
  hidden.expectedCoverage = 'RESPONSE_ONLY'
  index.runs[1].cases.push(hidden)
  expectRejected(index)
})

test('schema 2 rejects RESPONSE_ONLY outside the eleven adopted APIs or with an incomplete contract', () => {
  const valid = initialIndex()
  valid.schemaVersion = 2
  const entry = valid.interfaces.find(({ apiName }) => apiName === 'adj_factor')
  const decision = 'docs/issues/proposals/ISSUE-025-extraction-contracts.md#决策记录'
  Object.assign(entry, {
    completeness: {
      kind: 'RESPONSE_ONLY', rowLimit: null,
      evidenceRefs: [decision, 'docs/verification/ISSUE-018-range-acceptance.md#adj_factor', entry.officialUrl],
    },
    decisionRef: decision,
  })
  for (const mutate of [
    (index) => { index.interfaces.find(({ apiName }) => apiName === 'daily').completeness = clone(entry.completeness) },
    (index) => { index.interfaces.find(({ apiName }) => apiName === 'adj_factor').completeness.rowLimit = 1 },
    (index) => { index.interfaces.find(({ apiName }) => apiName === 'adj_factor').completeness.evidenceRefs.pop() },
    (index) => { index.interfaces.find(({ apiName }) => apiName === 'adj_factor').completeness.evidenceRefs.splice(1, 1) },
    (index) => { index.interfaces.find(({ apiName }) => apiName === 'adj_factor').decisionRef = 'docs/task-designs/ISSUE-029-design.md' },
  ]) {
    const index = clone(valid)
    mutate(index)
    expectRejected(index)
  }
})

test('schema 2 RESPONSE_ONLY whitelist is exactly the eleven adopted APIs', () => {
  const apis = ['adj_factor', 'suspend_d', 'income', 'balancesheet', 'cashflow', 'fina_audit',
    'express', 'repurchase', 'stk_managers', 'top10_holders', 'top10_floatholders']
  for (const apiName of apis) {
    const index = initialIndex()
    index.schemaVersion = 2
    const entry = index.interfaces.find((value) => value.apiName === apiName)
    Object.assign(entry, {
      completeness: { kind: 'RESPONSE_ONLY', rowLimit: null, evidenceRefs: [
        RESPONSE_ONLY_DECISION, `docs/verification/ISSUE-018-range-acceptance.md#${apiName}`,
        entry.officialUrl,
      ] },
      decisionRef: RESPONSE_ONLY_DECISION,
    })
    assert.equal(validateEvidence(index), index)
  }
})

test('explicit full SOURCE identities select either clean same-parameter source exactly', () => {
  const index = twoCleanDailySources()
  for (const [runId, caseId, expectedCoverage] of [
    ['issue018-t13-source-01', 'daily-source', 'closed range'],
    ['issue029-source-02', 'daily-source-02', 'second clean coverage'],
  ]) {
    const plan = validRangePlan()
    plan.runId = `task-for-${caseId}`
    plan.cases[0].caseId = `task-for-${caseId}`
    plan.cases[0].evidenceRefs.push(
      `docs/verification/ISSUE-018-range-acceptance.json#${runId}/${caseId}`,
    )
    const bindings = new Map()
    assert.equal(liveContract.selectTaskCases('range', plan, index, bindings).length, 1)
    assert.deepEqual(bindings.get(plan.cases[0].caseId), {
      runId, caseId, expectedCoverage,
      expectedCoverageSource: expectedCoverage === 'closed range'
        ? 'official-source' : 'second-official-source',
    })
  }
})

test('two clean same-parameter SOURCE candidates are ambiguous without an identity', () => {
  const index = twoCleanDailySources()
  const plan = validRangePlan()
  plan.runId = 'ambiguous-task-run'
  plan.cases[0].caseId = 'ambiguous-task-case'
  assert.throws(() => liveContract.selectTaskCases('range', plan, index), { message: SAFE_ERROR })
})

test('SOURCE identity supports one globally unique legacy case ref and never falls back from a wrong identity', () => {
  const index = twoCleanDailySources()
  const plan = validRangePlan()
  plan.runId = 'legacy-task-run'
  plan.cases[0].caseId = 'legacy-task-case'
  plan.cases[0].evidenceRefs.push(
    'docs/verification/ISSUE-018-range-acceptance.json#daily-source-02',
  )
  const bindings = new Map()
  liveContract.selectTaskCases('range', plan, index, bindings)
  assert.equal(bindings.get(plan.cases[0].caseId).runId, 'issue029-source-02')
  for (const ref of [
    'docs/verification/ISSUE-018-range-acceptance.json#missing-run/daily-source',
    'docs/verification/ISSUE-018-range-acceptance.json#issue018-t13-source-01/missing-case',
  ]) {
    const wrong = clone(plan)
    wrong.cases[0].evidenceRefs = wrong.cases[0].evidenceRefs.slice(0, -1).concat(ref)
    assert.throws(() => liveContract.selectTaskCases('range', wrong, index), { message: SAFE_ERROR })
  }
  const multiple = clone(plan)
  multiple.cases[0].evidenceRefs.push(
    'docs/verification/ISSUE-018-range-acceptance.json#issue018-t13-source-01/daily-source',
  )
  assert.throws(() => liveContract.selectTaskCases('range', multiple, index), { message: SAFE_ERROR })
  const unclean = clone(index)
  unclean.runs.find(({ runId }) => runId === 'issue029-source-02').exitCode = 1
  assert.throws(() => liveContract.selectTaskCases('range', plan, unclean), { message: SAFE_ERROR })
})

test('RESPONSE_ONLY AVAILABLE requires the adopted TASK meaning and one full-range successful request', () => {
  const valid = responseOnlyIndex()
  assert.equal(validateEvidence(valid), valid)
  for (const mutate of [
    (task) => { task.expectedCoverage = 'COMPLETE' },
    (task) => { task.expectedCoverageSource = RESPONSE_ONLY_DECISION },
    (task) => { task.requestCount = 2 },
    (task) => { task.sqlAfterKeyCount = null },
    (task) => {
      task.batchNodes = [
        { ...task.batchNodes[0], batchId: 'left', end: '20260806', sourceRowCount: 3,
          insertedRows: 3 },
        { ...task.batchNodes[0], batchId: 'right', start: '20260807', sourceRowCount: 3,
          insertedRows: 3 },
      ]
      task.leafCount = task.succeededLeafCount = 2
    },
  ]) {
    const index = clone(valid)
    mutate(index.runs[1].cases[0])
    expectRejected(index)
  }
})

test('schema 2 validates every unreferenced RESPONSE_ONLY TASK API, SOURCE binding and PASS structure', () => {
  const base = responseOnlyIndex()
  const taskRun = base.runs[1]
  const hidden = clone(taskRun.cases[0])
  hidden.caseId = 'hidden-response-task'
  taskRun.cases.push(hidden)
  assert.equal(validateEvidence(base), base)
  for (const mutate of [
    (task) => { task.apiName = 'daily' },
    (task) => { task.expectedCoverageSource = `${RESPONSE_ONLY_DECISION} docs/verification/ISSUE-018-range-acceptance.json#missing-run/adj-factor-source` },
    (task) => { task.expectedCoverageSource = `${RESPONSE_ONLY_DECISION} docs/verification/ISSUE-018-range-acceptance.json#issue025-source-01/missing-case` },
    (task) => { task.requestCount = 2 },
    (task) => { task.batchNodes[0].end = '20260809' },
  ]) {
    const index = clone(base)
    const task = index.runs[1].cases.find(({ caseId }) => caseId === hidden.caseId)
    mutate(task)
    expectRejected(index)
  }
})

test('schema 2 preserves FAILED and NOT_RUN RESPONSE_ONLY facts without inventing SQL', () => {
  for (const status of ['FAILED', 'NOT_RUN']) {
    const index = responseOnlyIndex()
    index.interfaces.find(({ apiName }) => apiName === 'adj_factor').cases = [
      'adj-factor-source', 'adj-factor-task',
    ]
    const task = clone(index.runs[1].cases[0])
    task.caseId = `unreferenced-${status.toLowerCase()}`
    if (status === 'FAILED') {
      Object.assign(task, { status, errorCode: 'SOURCE_TIMEOUT', requestCount: 1,
        sqlBeforeKeyCount: null, sqlAfterKeyCount: null, ownershipSummary: null,
        businessKeyDigest: null })
    } else {
      Object.assign(task, { status, errorCode: null, taskId: null, submissionId: null,
        requestCount: null, batchNodes: [], leafCount: null, succeededLeafCount: null,
        failedLeafCount: null, emptyLeafCount: null, sourceRowCount: null, insertedRows: null,
        updatedRows: null, sqlBeforeKeyCount: null, sqlAfterKeyCount: null,
        ownershipSummary: null, businessKeyDigest: null, reviewMethod: null,
        evidencePaths: [] })
    }
    index.runs[1].cases.push(task)
    assert.equal(validateEvidence(index), index)
  }
})

test('RESPONSE_ONLY initial TASK evidence derives adopted meaning while retaining exact SOURCE identity', () => {
  const index = responseOnlyIndex()
  index.runs.pop()
  const entry = index.interfaces.find(({ apiName }) => apiName === 'adj_factor')
  Object.assign(entry, { taskStatus: 'NOT_RUN', disposition: 'NEEDS_VERIFICATION',
    cases: ['adj-factor-source'], unresolved: ['RANGE_TASK_NOT_RUN'] })
  const plan = {
    runId: 'new-response-task', cases: [{ caseId: 'new-response-task-case',
      apiName: 'adj_factor', mode: 'RANGE',
      params: { ts_code: '000001.SZ', start_date: '20260803', end_date: '20260810' },
      dateAxis: 'TRADE_DATE', start: '20260803', end: '20260810',
      evidenceRefs: [...entry.completeness.evidenceRefs, RESPONSE_SOURCE_REF] }],
  }
  const bindings = new Map()
  liveContract.selectTaskCases('range', plan, index, bindings)
  const initial = harnessFunction('initialTaskEvidence', {
    candidateIndex: index, sourceBindings: bindings, safeCaseEvidence,
    responseOnlyExpectedCoverageSource: liveContract.responseOnlyExpectedCoverageSource,
  })(plan.cases[0])
  assert.equal(initial.expectedCoverage, 'RESPONSE_ONLY')
  assert.equal(initial.expectedCoverageSource, `${RESPONSE_ONLY_DECISION} ${RESPONSE_SOURCE_REF}`)
  assert.deepEqual(bindings.get(plan.cases[0].caseId), {
    runId: 'issue025-source-01', caseId: 'adj-factor-source',
    expectedCoverage: 'observed source response',
    expectedCoverageSource: 'official-source-observation',
  })
})

test('new RESPONSE_ONLY plans require exactly one full run/case SOURCE ref even when unique', () => {
  const index = responseOnlyIndex()
  index.runs.pop()
  const entry = index.interfaces.find(({ apiName }) => apiName === 'adj_factor')
  Object.assign(entry, { taskStatus: 'NOT_RUN', disposition: 'NEEDS_VERIFICATION',
    cases: ['adj-factor-source'], unresolved: ['RANGE_TASK_NOT_RUN'] })
  const plan = { runId: 'response-plan-without-full-source', cases: [{
    caseId: 'response-task-without-full-source', apiName: 'adj_factor', mode: 'RANGE',
    params: { ts_code: '000001.SZ', start_date: '20260803', end_date: '20260810' },
    dateAxis: 'TRADE_DATE', start: '20260803', end: '20260810',
    evidenceRefs: [...entry.completeness.evidenceRefs],
  }] }
  assert.throws(() => liveContract.selectTaskCases('range', plan, index), { message: SAFE_ERROR })
  plan.cases[0].evidenceRefs.push(
    'docs/verification/ISSUE-018-range-acceptance.json#adj-factor-source',
  )
  assert.throws(() => liveContract.selectTaskCases('range', plan, index), { message: SAFE_ERROR })
})

test('accepts the persisted index while preserving the exact 40-row matrix contract', () => {
  const index = persistedIndex()
  assert.equal(validateEvidence(index), index)
  assert.deepEqual(index.interfaces.map((entry) => [
    entry.apiName,
    entry.rangeTarget,
    entry.shape,
    entry.planningMode,
    entry.dateAxis,
    entry.outputDateColumn,
    Number(new URL(entry.officialUrl).searchParams.get('doc_id')),
  ]), EXPECTED_MATRIX)
  assert.equal(index.interfaces.filter(({ shape }) => shape === 'STOCK').length + 5, 34)
  assert.equal(index.interfaces.filter(({ rangeTarget }) => rangeTarget).length, 34)
  assert.equal(index.interfaces.filter(({ planningMode }) => planningMode === 'NATIVE_RANGE').length, 31)
  assert.equal(index.interfaces.filter(({ planningMode }) => planningMode === 'CALENDAR_DAYS').length, 2)
  assert.equal(index.interfaces.filter(({ planningMode }) => planningMode === 'TRADING_DAYS').length, 1)
  assert.equal(index.interfaces.filter(({ rangeTarget }) => !rangeTarget).length, 6)
})

test('initial index fixture starts with no execution runs', () => {
  assert.deepEqual(initialIndex().runs, [])
})

test('rejects an omitted interface instead of accepting a 39-row result', () => {
  const index = initialIndex()
  index.interfaces.splice(12, 1)
  expectRejected(index)
})

test('rejects UNKNOWN completeness marked AVAILABLE', () => {
  const index = initialIndex()
  const daily = index.interfaces.find(({ apiName }) => apiName === 'daily')
  Object.assign(daily, {
    sourceStatus: 'PASS',
    taskStatus: 'PASS',
    disposition: 'AVAILABLE',
    decisionRef: 'docs/verification/ISSUE-018-range-acceptance.md#daily',
    cases: ['daily-source', 'daily-task'],
  })
  expectRejected(index)
})

test('rejects duplicate, extra, reordered and altered matrix rows', () => {
  for (const mutate of [
    (index) => { index.interfaces[1] = clone(index.interfaces[0]) },
    (index) => { index.interfaces.push({ ...clone(index.interfaces[0]), apiName: 'extra_api' }) },
    (index) => { [index.interfaces[0], index.interfaces[1]] = [index.interfaces[1], index.interfaces[0]] },
    (index) => { index.interfaces[12].shape = 'DATES' },
    (index) => { index.interfaces[20].planningMode = 'NATIVE_RANGE' },
    (index) => { index.interfaces[36].rangeTarget = true },
  ]) {
    const index = initialIndex()
    mutate(index)
    expectRejected(index)
  }
})

test('rejects malformed completeness and AVAILABLE without full evidence', () => {
  for (const mutate of [
    (entry) => { entry.completeness = { kind: 'ROW_LIMIT', rowLimit: 0, evidenceRefs: [] } },
    (entry) => { entry.completeness = { kind: 'ROW_LIMIT', rowLimit: 6000, evidenceRefs: [] } },
    (entry) => { entry.disposition = 'EXCLUDED' },
    (entry) => { entry.sourceStatus = 'PASS'; entry.taskStatus = 'PASS'; entry.disposition = 'AVAILABLE' },
  ]) {
    const index = initialIndex()
    mutate(index.interfaces.find(({ apiName }) => apiName === 'daily'))
    expectRejected(index)
  }
})

test('validates exact RANGE and SINGLE case-plan parameters and dates', () => {
  const range = validRangePlan()
  assert.equal(validateCasePlan(range), range)
  const single = {
    runId: 'issue018-t13-single-01',
    cases: [{
      caseId: 'stock-company-single-000001',
      apiName: 'stock_company',
      mode: 'SINGLE',
      params: { ts_code: '000001.SZ', exchange: 'SZSE' },
      dateAxis: null,
      start: null,
      end: null,
      evidenceRefs: [],
    }],
  }
  assert.equal(validateCasePlan(single), single)
})

test('accepts all 40 exact current SINGLE shapes and every RANGE parameter shape', () => {
  const single = {
    runId: 'issue018-t13-single-all',
    cases: EXPECTED_MATRIX.map(([apiName]) => ({
      caseId: `${apiName}-single`, apiName, mode: 'SINGLE', params: SINGLE_PARAMS[apiName],
      dateAxis: null, start: null, end: null, evidenceRefs: [],
    })),
  }
  assert.equal(validateCasePlan(single), single)

  const range = {
    runId: 'issue018-t13-range-shapes',
    cases: [
      ['daily', { ts_code: '000001.SZ', start_date: '20260803', end_date: '20260810' }, 'TRADE_DATE'],
      ['new_share', { start_date: '20260801', end_date: '20260831' }, 'ISSUE_DATE'],
      ['trade_cal', { exchange: 'SSE', start_date: '20260803', end_date: '20260810' }, 'CALENDAR_DATE'],
      ['margin', { exchange_id: 'BSE', start_date: '20260803', end_date: '20260810' }, 'TRADE_DATE'],
      ['top_list', { ts_code: '000001.SZ', start_date: '20260803', end_date: '20260810' }, 'TRADE_DATE'],
      ['dividend', { ts_code: '000001.SZ', start_date: '20260801', end_date: '20260831' }, 'ANNOUNCEMENT_DATE'],
    ].map(([apiName, params, dateAxis]) => ({
      caseId: `${apiName}-range`, apiName, mode: 'RANGE', params, dateAxis,
      start: params.start_date, end: params.end_date,
      evidenceRefs: [`docs/verification/ISSUE-018-range-acceptance.md#${apiName}`],
    })),
  }
  assert.equal(validateCasePlan(range), range)
})

test('rejects missing cases, unknown fields or APIs, duplicate IDs and wrong parameters', () => {
  const mutations = [
    (plan) => { plan.cases = [] },
    (plan) => { plan.secret = 'canary' },
    (plan) => { plan.cases[0].token = 'canary' },
    (plan) => { plan.cases[0].apiName = 'unknown_api' },
    (plan) => { plan.cases.push(clone(plan.cases[0])) },
    (plan) => { plan.cases[0].params.offset = '1' },
    (plan) => { plan.cases[0].params.ts_code = '000001.SZ,600000.SH' },
    (plan) => { delete plan.cases[0].params.ts_code },
    (plan) => { plan.cases[0].params.type = 'P' },
  ]
  for (const mutate of mutations) {
    const plan = validRangePlan()
    mutate(plan)
    expectRejected(plan, validateCasePlan)
  }
})

test('rejects invalid, mismatched, missing and reversed RANGE dates', () => {
  for (const mutate of [
    (c) => { c.start = '20260229'; c.params.start_date = c.start },
    (c) => { c.end = '20260802'; c.params.end_date = c.end },
    (c) => { c.params.start_date = '20260804' },
    (c) => { c.dateAxis = 'ANNOUNCEMENT_DATE' },
    (c) => { c.start = null },
    (c) => { c.end = '2026-08-10'; c.params.end_date = c.end },
  ]) {
    const plan = validRangePlan()
    mutate(plan.cases[0])
    expectRejected(plan, validateCasePlan)
  }
})

test('rejects SINGLE/RANGE shape drift and values outside the current manifest', () => {
  for (const [apiName, mutate] of [
    ['stock_basic', (params) => { params.list_status = 'G' }],
    ['stock_company', (params) => { params.exchange = 'SSE' }],
    ['daily', (params) => { params.trade_date = '20260229' }],
    ['margin', (params) => { params.exchange_id = 'NYSE' }],
  ]) {
    const plan = {
      runId: `bad-${apiName}`,
      cases: [{ caseId: `bad-${apiName}`, apiName, mode: 'SINGLE',
        params: clone(SINGLE_PARAMS[apiName]), dateAxis: null, start: null, end: null,
        evidenceRefs: [] }],
    }
    mutate(plan.cases[0].params)
    expectRejected(plan, validateCasePlan)
  }
  const singleOnlyRange = validRangePlan()
  singleOnlyRange.cases[0].apiName = 'stock_basic'
  expectRejected(singleOnlyRange, validateCasePlan)
})

test('safe projection retains only the browser evidence schema and strips nested secrets', () => {
  const input = {
    caseId: 'daily-source', apiName: 'daily', phase: 'SOURCE', mode: 'RANGE',
    params: {
      ts_code: '000001.SZ', start_date: '20260803', end_date: '20260810',
      token: 'secret-canary', jdbcUrl: 'jdbc:secret-canary',
    },
    dateAxis: 'TRADE_DATE', expectedCoverage: 'closed range',
    expectedCoverageSource: 'official-source', status: 'PASS', errorCode: null,
    taskId: null, submissionId: null, requestCount: 1,
    batchNodes: [{
      batchId: 'source-1', parentBatchId: null, status: 'SUCCESS',
      start: '20260803', end: '20260810', sourceRowCount: 6,
      insertedRows: null, updatedRows: null, errorCode: null,
      rawResponse: 'secret-canary',
    }],
    leafCount: 1, succeededLeafCount: 1, failedLeafCount: 0, emptyLeafCount: 0,
    sourceRowCount: 6, insertedRows: null, updatedRows: null,
    sqlBeforeKeyCount: null, sqlAfterKeyCount: null, ownershipSummary: null,
    businessKeyDigest: null, reviewMethod: 'date-axis comparison',
    evidencePaths: ['private/source-evidence.json'],
    token: 'secret-canary', password: 'secret-canary', rawResponse: 'secret-canary',
    cause: new Error('secret-canary'),
  }
  const projected = safeCaseEvidence(input)
  assert.deepEqual(projected, {
    caseId: 'daily-source', apiName: 'daily', phase: 'SOURCE', mode: 'RANGE',
    params: { ts_code: '000001.SZ', start_date: '20260803', end_date: '20260810' },
    dateAxis: 'TRADE_DATE', expectedCoverage: 'closed range',
    expectedCoverageSource: 'official-source', status: 'PASS', errorCode: null,
    taskId: null, submissionId: null, requestCount: 1,
    batchNodes: [{
      batchId: 'source-1', parentBatchId: null, status: 'SUCCESS',
      start: '20260803', end: '20260810', sourceRowCount: 6,
      insertedRows: null, updatedRows: null, errorCode: null,
    }],
    leafCount: 1, succeededLeafCount: 1, failedLeafCount: 0, emptyLeafCount: 0,
    sourceRowCount: 6, insertedRows: null, updatedRows: null,
    sqlBeforeKeyCount: null, sqlAfterKeyCount: null, ownershipSummary: null,
    businessKeyDigest: null, reviewMethod: 'date-axis comparison',
    evidencePaths: ['private/source-evidence.json'],
  })
  assert.equal(JSON.stringify(projected).includes('secret-canary'), false)
})

test('accepts a complete SOURCE outcome and rejects contradictory outcome counts', () => {
  const index = initialIndex()
  const projected = safeCaseEvidence({
    caseId: 'daily-source', apiName: 'daily', phase: 'SOURCE', mode: 'RANGE',
    params: { ts_code: '000001.SZ', start_date: '20260803', end_date: '20260810' },
    dateAxis: 'TRADE_DATE', expectedCoverage: 'closed range',
    expectedCoverageSource: 'official-source', status: 'PASS', errorCode: null,
    taskId: null, submissionId: null, requestCount: 1,
    batchNodes: [{
      batchId: 'source-1', parentBatchId: null, status: 'SUCCESS',
      start: '20260803', end: '20260810', sourceRowCount: 6,
      insertedRows: null, updatedRows: null, errorCode: null,
    }],
    leafCount: 1, succeededLeafCount: 1, failedLeafCount: 0, emptyLeafCount: 0,
    sourceRowCount: 6, insertedRows: null, updatedRows: null,
    sqlBeforeKeyCount: null, sqlAfterKeyCount: null, ownershipSummary: null,
    businessKeyDigest: null, reviewMethod: 'date-axis comparison',
    evidencePaths: ['private/source-evidence.json'],
  })
  index.runs.push(validRun(projected))
  const daily = index.interfaces.find(({ apiName }) => apiName === 'daily')
  daily.sourceStatus = 'PASS'
  daily.cases = ['daily-source']
  assert.equal(validateEvidence(index), index)

  for (const mutate of [
    (run) => { delete run.productionJarSha256 },
    (run) => { run.cleanup = null },
    (run) => { run.cases[0].taskId = 'invented-for-source' },
    (run) => { run.cases[0].failedLeafCount = 1 },
    (run) => { run.cases[0].status = 'NOT_RUN' },
  ]) {
    const bad = clone(index)
    mutate(bad.runs[0])
    expectRejected(bad)
  }
})

test('rejects interface PASS when referenced outcomes failed or were not run', () => {
  for (const status of ['FAILED', 'NOT_RUN', 'EVIDENCE_MISSING']) {
    const index = initialIndex()
    const caseEvidence = safeCaseEvidence({
      caseId: `daily-source-${status.toLowerCase()}`, apiName: 'daily', phase: 'SOURCE', mode: 'RANGE',
      params: { ts_code: '000001.SZ', start_date: '20260803', end_date: '20260810' },
      dateAxis: 'TRADE_DATE', expectedCoverage: 'closed range', expectedCoverageSource: 'official-source',
      status, errorCode: status === 'FAILED' ? 'SOURCE_UNAVAILABLE' : null,
      taskId: null, submissionId: null,
      requestCount: status === 'NOT_RUN' ? null : 1, batchNodes: [], leafCount: status === 'NOT_RUN' ? null : 0,
      succeededLeafCount: status === 'NOT_RUN' ? null : 0, failedLeafCount: status === 'NOT_RUN' ? null : 0,
      emptyLeafCount: status === 'NOT_RUN' ? null : 0, sourceRowCount: null, insertedRows: null,
      updatedRows: null, sqlBeforeKeyCount: null, sqlAfterKeyCount: null, ownershipSummary: null,
      businessKeyDigest: null, reviewMethod: null, evidencePaths: [],
    })
    index.runs.push(validRun(caseEvidence))
    const daily = index.interfaces.find(({ apiName }) => apiName === 'daily')
    daily.sourceStatus = 'PASS'
    daily.cases = [caseEvidence.caseId]
    expectRejected(index)
  }
})

test('accepts AVAILABLE only with matching RANGE SOURCE and TASK evidence in identified clean runs', () => {
  const index = availableIndex()
  assert.equal(validateEvidence(index), index)

  for (const mutate of [
    (value) => { value.runs[1].cases[0].mode = 'SINGLE' },
    (value) => { value.runs[1].cases[0].params.end_date = '20260811' },
    (value) => { value.runs[1].cases[0].sqlAfterKeyCount = null },
    (value) => { value.runs[1].cases[0].businessKeyDigest = null },
    (value) => { value.runs[1].cleanup.status = 'FAILED' },
    (value) => { value.interfaces[12].policyVersion = 'tushare-range-v1' },
    (value) => { value.interfaces[12].unresolved = ['TASK_EVIDENCE_MISSING'] },
    (value) => { value.interfaces[12].cases = ['daily-source'] },
  ]) {
    const bad = clone(index)
    mutate(bad)
    expectRejected(bad)
  }

  const reopened = clone(index)
  reopened.interfaces[12].policyVersion = 'tushare-range-v3'
  assert.equal(validateEvidence(reopened), reopened)
})

test('rejects unknown fields at every persisted nesting level', () => {
  for (const mutate of [
    (value) => { value.secret = true },
    (value) => { value.inputHashes.secret = true },
    (value) => { value.interfaces[0].secret = true },
    (value) => { value.interfaces[12].completeness.secret = true },
    (value) => { value.runs[0].secret = true },
    (value) => { value.runs[0].cleanup.secret = true },
    (value) => { value.runs[0].cases[0].secret = true },
    (value) => { value.runs[0].cases[0].params.secret = true },
    (value) => { value.runs[0].cases[0].batchNodes[0].secret = true },
  ]) {
    const bad = availableIndex()
    mutate(bad)
    expectRejected(bad)
  }
})

function attachCase(index, evidence, runId = `run-${evidence.caseId}`) {
  const run = validRun(evidence)
  run.runId = runId
  index.runs.push(run)
  index.interfaces.find(({ apiName }) => apiName === evidence.apiName).cases.push(evidence.caseId)
  return run
}

function unrunEvidence(phase = 'TASK') {
  const evidence = validTaskEvidence()
  evidence.caseId = `daily-${phase.toLowerCase()}-unrun`
  evidence.phase = phase
  evidence.status = 'NOT_RUN'
  for (const key of [
    'errorCode', 'taskId', 'submissionId', 'requestCount', 'leafCount', 'succeededLeafCount',
    'failedLeafCount', 'emptyLeafCount', 'sourceRowCount', 'insertedRows', 'updatedRows',
    'sqlBeforeKeyCount', 'sqlAfterKeyCount', 'ownershipSummary', 'businessKeyDigest', 'reviewMethod',
  ]) evidence[key] = null
  evidence.batchNodes = []
  evidence.evidencePaths = []
  return evidence
}

test('phase aggregation rejects a passing case that masks failed, missing or unrun references', () => {
  for (const phase of ['SOURCE', 'TASK']) {
    for (const status of ['FAILED', 'EVIDENCE_MISSING', 'NOT_RUN']) {
      const index = availableIndex()
      const evidence = unrunEvidence(phase)
      evidence.status = status
      if (status === 'FAILED') evidence.errorCode = 'SOURCE_UNAVAILABLE'
      attachCase(index, evidence)
      expectRejected(index)
    }
  }
})

test('run input hashes must equal the index and SOURCE/TASK candidates use distinct runs', () => {
  for (const key of ['manifestSha256', 'requestExamplesSha256']) {
    const index = availableIndex()
    index.runs[0][key] = 'a'.repeat(64)
    expectRejected(index)
  }
  const index = availableIndex()
  index.runs[0].cases.push(...index.runs.pop().cases)
  expectRejected(index)
})

test('every referenced RANGE sample needs a matching opposite-phase sample', () => {
  const index = availableIndex()
  const source = clone(index.runs[0].cases[0])
  source.caseId = 'daily-source-second-stock'
  source.params.ts_code = '600000.SH'
  attachCase(index, source)
  expectRejected(index)
})

test('truthful all-null NOT_RUN cases are accepted and invented execution fields rejected', () => {
  for (const phase of ['SOURCE', 'TASK']) {
    const index = initialIndex()
    attachCase(index, unrunEvidence(phase))
    assert.equal(validateEvidence(index), index)
    for (const [key, value] of Object.entries({
      taskId: 'invented', submissionId: 'invented', requestCount: 0, leafCount: 0,
      sourceRowCount: 0, sqlBeforeKeyCount: 0, ownershipSummary: 'invented',
      reviewMethod: 'invented', errorCode: 'SOURCE_UNAVAILABLE',
    })) {
      const bad = clone(index)
      bad.runs[0].cases[0][key] = value
      expectRejected(bad)
    }
  }
})

test('failed-before-submission and evidence-missing cases remain representable', () => {
  for (const status of ['FAILED', 'EVIDENCE_MISSING']) {
    const index = initialIndex()
    const evidence = unrunEvidence()
    evidence.status = status
    if (status === 'FAILED') evidence.errorCode = 'TASK_QUEUE_FULL'
    attachCase(index, evidence)
    index.interfaces[12].taskStatus = status
    assert.equal(validateEvidence(index), index)
  }
})

function splitTaskEvidence() {
  const evidence = validTaskEvidence()
  const leaf = evidence.batchNodes[0]
  evidence.batchNodes = [
    { ...leaf, batchId: 'parent', status: 'SPLIT', sourceRowCount: 0, insertedRows: 0 },
    { ...leaf, batchId: 'left', parentBatchId: 'parent', end: '20260806' },
    { ...leaf, batchId: 'right', parentBatchId: 'parent', start: '20260807',
      sourceRowCount: 0, insertedRows: 0 },
  ]
  Object.assign(evidence, { requestCount: 3, leafCount: 2, succeededLeafCount: 2, emptyLeafCount: 1 })
  return evidence
}

test('SPLIT parents and successful leaves validate without inflating security/write counts', () => {
  const index = availableIndex()
  index.runs[1].cases[0] = splitTaskEvidence()
  assert.equal(validateEvidence(index), index)
  for (const mutate of [
    (e) => { e.sourceRowCount = 12 },
    (e) => { e.emptyLeafCount = 0 },
    (e) => { e.batchNodes[0].sourceRowCount = 6 },
    (e) => { e.batchNodes[2].parentBatchId = 'absent' },
    (e) => { e.batchNodes[1].batchId = 'right' },
    (e) => { e.batchNodes[0].parentBatchId = 'left' },
    (e) => { e.batchNodes[2].start = '20260806' },
    (e) => { e.batchNodes.pop() },
  ]) {
    const bad = clone(index)
    mutate(bad.runs[1].cases[0])
    expectRejected(bad)
  }
})

test('pending, running and failed leaves are honest incomplete evidence and cannot pass', () => {
  for (const status of ['PENDING', 'RUNNING', 'FAILED']) {
    const index = initialIndex()
    const evidence = splitTaskEvidence()
    Object.assign(evidence, { status: status === 'FAILED' ? 'FAILED' : 'EVIDENCE_MISSING',
      succeededLeafCount: 1, failedLeafCount: status === 'FAILED' ? 1 : 0, emptyLeafCount: 0,
      errorCode: status === 'FAILED' ? 'SOURCE_TIMEOUT' : null })
    Object.assign(evidence.batchNodes[2], { status, errorCode: evidence.errorCode })
    attachCase(index, evidence)
    index.interfaces[12].taskStatus = evidence.status
    assert.equal(validateEvidence(index), index)
    const bad = clone(index)
    bad.runs[0].cases[0].status = 'PASS'
    bad.interfaces[12].taskStatus = 'PASS'
    expectRejected(bad)
  }
})

test('batch statuses and aggregate counts cannot contradict successful case evidence', () => {
  for (const mutate of [
    (e) => { e.batchNodes[0].status = 'FAILED'; e.batchNodes[0].errorCode = 'SOURCE_TIMEOUT' },
    (e) => { e.batchNodes[0].status = 'NOT_RUN' },
    (e) => { e.sourceRowCount = 7 },
    (e) => { e.insertedRows = 7 },
    (e) => { e.emptyLeafCount = 1 },
    (e) => { e.leafCount = 2; e.succeededLeafCount = 2 },
  ]) {
    const index = availableIndex()
    mutate(index.runs[1].cases[0])
    expectRejected(index)
  }
})

test('proven complete closed-calendar zero-leaf SOURCE and TASK successes include planning requests', () => {
  const index = initialIndex()
  for (const phase of ['SOURCE', 'TASK']) {
    const evidence = clone(availableIndex().runs[phase === 'SOURCE' ? 0 : 1].cases[0])
    Object.assign(evidence, { caseId: `top-list-${phase}`, apiName: 'top_list',
      params: { ts_code: '000001.SZ', start_date: '20260808', end_date: '20260809' },
      expectedCoverage: 'COMPLETE_CLOSED_CALENDAR',
      expectedCoverageSource: 'private/calendar-evidence.json#SZSE-20260808-20260809',
      reviewMethod: 'Complete unique daily calendar checked: both days closed',
      requestCount: 1, batchNodes: [], leafCount: 0, succeededLeafCount: 0,
      failedLeafCount: 0, emptyLeafCount: 0, sourceRowCount: 0 })
    if (phase === 'TASK') Object.assign(evidence, {
      insertedRows: 0, updatedRows: 0, sqlAfterKeyCount: evidence.sqlBeforeKeyCount,
      ownershipSummary: 'no security rows',
    })
    attachCase(index, evidence)
  }
  const row = index.interfaces.find(({ apiName }) => apiName === 'top_list')
  row.sourceStatus = row.taskStatus = 'PASS'
  assert.equal(validateEvidence(index), index)
  for (const mutate of [
    (e) => { e.expectedCoverage = 'empty calendar response' },
    (e) => { e.requestCount = 0 },
    (e) => { e.expectedCoverageSource = '' },
    (e) => { e.sourceRowCount = 2 },
    (e) => { e.evidencePaths = [] },
  ]) {
    const bad = clone(index)
    mutate(bad.runs[0].cases[0])
    expectRejected(bad)
  }
})

test('SINGLE allows YAML-valid second stocks and dates with matching stock-company exchange', () => {
  const plan = { runId: 'second-stock', cases: Object.entries(SINGLE_PARAMS).map(([apiName, params]) => ({
    caseId: `${apiName}-second`, apiName, mode: 'SINGLE', params: Object.fromEntries(
      Object.entries(params).map(([key, value]) => [key, key === 'ts_code' ? '600000.SH'
        : key.includes('date') ? '20240229' : apiName === 'stock_company' && key === 'exchange' ? 'SSE' : value])),
    dateAxis: null, start: null, end: null, evidenceRefs: [],
  })) }
  assert.equal(validateCasePlan(plan), plan)
  const bad = clone(plan)
  bad.cases.find(({ apiName }) => apiName === 'stock_company').params.exchange = 'SZSE'
  expectRejected(bad, validateCasePlan)
  bad.cases.find(({ apiName }) => apiName === 'stock_company').params.exchange = 'SSE'
  bad.cases.find(({ apiName }) => apiName === 'daily').params.trade_date = '20230229'
  expectRejected(bad, validateCasePlan)
})

test('all six SINGLE_ONLY rows can record real SINGLE SOURCE and TASK results', () => {
  const index = initialIndex()
  for (const row of index.interfaces.filter(({ rangeTarget }) => !rangeTarget)) {
    for (const phase of ['SOURCE', 'TASK']) {
      const evidence = clone(availableIndex().runs[phase === 'SOURCE' ? 0 : 1].cases[0])
      Object.assign(evidence, { caseId: `${row.apiName}-${phase}`, apiName: row.apiName,
        mode: 'SINGLE', params: SINGLE_PARAMS[row.apiName], dateAxis: null })
      evidence.batchNodes[0].start = null
      evidence.batchNodes[0].end = null
      attachCase(index, evidence)
    }
    row.sourceStatus = row.taskStatus = 'PASS'
  }
  assert.equal(validateEvidence(index), index)
  const bad = clone(index)
  bad.interfaces[0].disposition = 'AVAILABLE'
  expectRejected(bad)
})

test('safe projection and validation reject secrets or raw payloads inside approved string fields', () => {
  const sensitive = ['jdbc:mysql://private/db', 'token=sample-secret', 'password: sample-secret',
    'Authorization: Bearer sample-secret', 'raw response: {"data":[]}', 'Caused by: connection refused',
    'https://user:password@example.com', 'sk-proj-abcdefghijklmnopqrstuvwxyz',
    '{"data":{"items":[[1,2]]}}']
  for (const value of sensitive) {
    for (const key of ['reviewMethod', 'expectedCoverageSource', 'ownershipSummary']) {
      const evidence = validTaskEvidence()
      evidence[key] = value
      expectRejected(evidence, safeCaseEvidence)
      const index = availableIndex()
      index.runs[1].cases[0][key] = value
      expectRejected(index)
    }
    const index = availableIndex()
    index.runs[0].commands = [value]
    expectRejected(index)
    index.runs[0].commands = ['node probe.js']
    index.runs[0].cleanup.evidencePath = value
    expectRejected(index)
  }
  const evidence = validTaskEvidence()
  evidence.errorCode = 'RAW_ERROR_TEXT'
  expectRejected(evidence, safeCaseEvidence)
})

test('invalid RANGE parameters cannot pass through a null date-axis sentinel', () => {
  const plan = validRangePlan()
  plan.cases[0].dateAxis = null
  plan.cases[0].params.end_date = plan.cases[0].end = '20260230'
  expectRejected(plan, validateCasePlan)
  const index = availableIndex()
  index.runs[0].cases[0].dateAxis = null
  index.runs[0].cases[0].params.ts_code = 'invalid'
  expectRejected(index)
})

test('incomplete task nodes can truthfully fail or run before any source request', () => {
  for (const status of ['FAILED', 'RUNNING']) {
    const index = initialIndex()
    const evidence = validTaskEvidence()
    Object.assign(evidence, { status: status === 'FAILED' ? 'FAILED' : 'EVIDENCE_MISSING',
      errorCode: status === 'FAILED' ? 'EXECUTION_INTERRUPTED' : null,
      requestCount: 0, succeededLeafCount: 0, failedLeafCount: status === 'FAILED' ? 1 : 0,
      sourceRowCount: 0, insertedRows: 0, sqlAfterKeyCount: 0 })
    Object.assign(evidence.batchNodes[0], { status, errorCode: evidence.errorCode,
      sourceRowCount: 0, insertedRows: 0 })
    attachCase(index, evidence)
    index.interfaces[12].taskStatus = evidence.status
    assert.equal(validateEvidence(index), index)
  }
})

test('credential assignments and known standalone credential formats are rejected recursively', () => {
  for (const value of ['TENSOR_TUSHARE_TOKEN=secret-value', '--token secret-value',
    'ghp_abcdefghijklmnopqrstuvwxyz1234567890', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signature']) {
    const index = availableIndex()
    index.interfaces[12].completeness.evidenceRefs.push(value)
    expectRejected(index)
    const evidence = validTaskEvidence()
    evidence.evidencePaths = [value]
    expectRejected(evidence, safeCaseEvidence)
  }
})

test('EXCLUDED requires a scope-decision reference and source-only candidates remain selectable', () => {
  const excluded = initialIndex()
  Object.assign(excluded.interfaces[12], { disposition: 'EXCLUDED',
    decisionRef: 'docs/verification/ISSUE-018-range-acceptance.md#explicit-user-scope-decision' })
  assert.equal(validateEvidence(excluded), excluded)
  const candidate = availableIndex()
  candidate.runs.pop()
  Object.assign(candidate.interfaces[12], { disposition: 'NEEDS_VERIFICATION', taskStatus: 'NOT_RUN',
    cases: ['daily-source'], unresolved: ['TASK_EVIDENCE_MISSING'] })
  assert.equal(validateEvidence(candidate), candidate)
})

test('projection rejects objects or payload arrays smuggled through scalar fields', () => {
  for (const value of [{ data: { items: [[1, 2]] } }, new Error('private cause'), ['private payload']]) {
    const evidence = validTaskEvidence()
    evidence.reviewMethod = value
    expectRejected(evidence, safeCaseEvidence)
  }
  const evidence = validTaskEvidence()
  evidence.evidencePaths = [{ data: [[1, 2]] }]
  expectRejected(evidence, safeCaseEvidence)
})

test('malformed batch nodes always produce the fixed safe validation error', () => {
  const index = availableIndex()
  index.runs[0].cases[0].batchNodes = [null]
  expectRejected(index)
})

function setRootRanges(evidence, ranges) {
  const template = evidence.batchNodes[0]
  evidence.batchNodes = ranges.map(([start, end], i) => ({
    ...template, batchId: `root-${i}`, parentBatchId: null, start, end,
  }))
  evidence.requestCount = ranges.length
  evidence.leafCount = evidence.succeededLeafCount = ranges.length
  evidence.sourceRowCount = template.sourceRowCount * ranges.length
  if (evidence.phase === 'TASK') {
    evidence.insertedRows = template.insertedRows * ranges.length
    evidence.updatedRows = template.updatedRows * ranges.length
    evidence.sqlAfterKeyCount = evidence.sqlBeforeKeyCount + evidence.insertedRows
  }
}

function rootCoverageIndex(apiName, phase, ranges) {
  const index = initialIndex()
  const evidence = clone(availableIndex().runs[phase === 'SOURCE' ? 0 : 1].cases[0])
  evidence.apiName = apiName
  evidence.caseId = `${apiName}-${phase.toLowerCase()}`
  evidence.dateAxis = index.interfaces.find((row) => row.apiName === apiName).dateAxis
  setRootRanges(evidence, ranges)
  attachCase(index, evidence)
  const row = index.interfaces.find((entry) => entry.apiName === apiName)
  row[phase === 'SOURCE' ? 'sourceStatus' : 'taskStatus'] = 'PASS'
  return index
}

test('native PASS roots cover the entire declared range exactly once in both phases', () => {
  for (const phase of ['SOURCE', 'TASK']) {
    const complete = rootCoverageIndex('daily', phase,
      [['20260807', '20260810'], ['20260803', '20260806']])
    assert.equal(validateEvidence(complete), complete)
    for (const ranges of [
      [['20260803', '20260803']],
      [['20260804', '20260810']],
      [['20260803', '20260805'], ['20260807', '20260810']],
      [['20260803', '20260810'], ['20260803', '20260810']],
      [['20260803', '20260807'], ['20260807', '20260810']],
    ]) expectRejected(rootCoverageIndex('daily', phase, ranges))
  }
})

test('matching SOURCE and TASK declarations cannot promote incomplete or overlapping native roots to AVAILABLE', () => {
  for (const ranges of [
    [['20260803', '20260803']],
    [['20260803', '20260810'], ['20260803', '20260810']],
  ]) {
    const index = availableIndex()
    for (const run of index.runs) setRootRanges(run.cases[0], ranges)
    expectRejected(index)
  }
})

test('CALENDAR_DAYS PASS requires one-day roots for every natural date including weekends', () => {
  const days = Array.from({ length: 8 }, (_, i) => `202608${String(i + 3).padStart(2, '0')}`)
  for (const apiName of ['dividend', 'disclosure_date']) {
    for (const phase of ['SOURCE', 'TASK']) {
      const complete = rootCoverageIndex(apiName, phase, days.map((day) => [day, day]))
      assert.equal(validateEvidence(complete), complete)
      for (const ranges of [
        days.filter((day) => day !== '20260808').map((day) => [day, day]),
        days.concat('20260808').map((day) => [day, day]),
        [['20260803', '20260810']],
        [['20260803', '20260806'], ['20260807', '20260810']],
      ]) expectRejected(rootCoverageIndex(apiName, phase, ranges))
    }
  }
})

test('partial roots remain representable for FAILED and EVIDENCE_MISSING without reporting PASS', () => {
  for (const apiName of ['daily', 'dividend', 'disclosure_date']) {
    for (const phase of ['SOURCE', 'TASK']) {
      for (const status of ['FAILED', 'EVIDENCE_MISSING']) {
        const index = rootCoverageIndex(apiName, phase, [['20260803', '20260803']])
        const evidence = index.runs[0].cases[0]
        evidence.status = status
        evidence.errorCode = status === 'FAILED' ? 'SOURCE_TIMEOUT' : null
        index.interfaces.find((row) => row.apiName === apiName)[
          phase === 'SOURCE' ? 'sourceStatus' : 'taskStatus'] = status
        assert.equal(validateEvidence(index), index)
      }
    }
  }
})

test('TRADING_DAYS coverage retains cited calendar gaps without inferring weekdays', () => {
  for (const phase of ['SOURCE', 'TASK']) {
    const index = rootCoverageIndex('top_list', phase,
      [['20260803', '20260803'], ['20260810', '20260810']])
    const evidence = index.runs[0].cases[0]
    evidence.expectedCoverageSource = 'private/calendar-evidence.json#complete-calendar'
    evidence.reviewMethod = 'Compared one-day requests with the cited complete open-day sequence'
    evidence.requestCount += 1
    assert.equal(validateEvidence(index), index)
  }
})

// Task3: the old synchronous harness had one sample per API and accepted HTTP 200.

test('live SINGLE selection submits both stocks and queries before and after each of 74 tasks', () => {
  assert.equal(typeof liveContract.selectTaskCases, 'function')
  const selected = liveContract.selectTaskCases('single')
  assert.equal(selected.length, 74)
  assert.equal(new Set(selected.map((c) => c.apiName)).size, 40)
  assert.equal(new Set(selected.map((c) => c.caseId)).size, 74)
  assert.equal(selected.filter((c) => c.params.ts_code === '600000.SH').length, 34)
  assert.equal(selected.find((c) => c.apiName === 'stock_company' && c.params.ts_code === '600000.SH').params.exchange, 'SSE')
})

function fixedSinglePlan() {
  return {
    runId: 'issue018-t14-single-20260913T010203Z',
    cases: Object.entries(SINGLE_PARAMS).flatMap(([apiName, params]) =>
      (params.ts_code ? ['000001.SZ', '600000.SH'] : [null]).map((stock) => ({
        caseId: `independent-task-20260913-${apiName}-${stock ?? 'original'}`,
        apiName, mode: 'SINGLE',
        params: { ...params, ...(stock ? { ts_code: stock } : {}),
          ...(apiName === 'stock_company' && stock === '600000.SH' ? { exchange: 'SSE' } : {}) },
        dateAxis: null, start: null, end: null,
        evidenceRefs: ['docs/task-designs/ISSUE-018-T14-design.md'],
      }))),
  }
}

test('fixed SINGLE plans preserve independent case identities and canonical stock execution order', () => {
  const plan = fixedSinglePlan()
  const expected = clone(plan.cases)
  plan.cases.reverse()
  plan.cases.forEach((entry) => { entry.params = Object.fromEntries(Object.entries(entry.params).reverse()) })
  const original = clone(plan)
  const selected = liveContract.selectTaskCases('single', plan)
  assert.deepEqual(selected, expected)
  assert.deepEqual(plan, original)
  assert.equal(selected.length, 74)
  assert.equal(new Set(selected.map((entry) => entry.apiName)).size, 40)
})

test('fixed SINGLE plans reject missing extra duplicate or changed canonical samples', () => {
  for (const mutate of [
    (plan) => { plan.cases.pop() },
    (plan) => { plan.cases = [] },
    (plan) => { plan.cases.push({ ...clone(plan.cases[0]), caseId: 'additional-case' }) },
    (plan) => { plan.cases[1].caseId = plan.cases[0].caseId },
    (plan) => { plan.cases[1].params = clone(plan.cases[0].params) },
    (plan) => { plan.cases[0].params.ts_code = '000002.SZ' },
    (plan) => { plan.cases[0].params.list_status = 'D' },
    (plan) => { plan.cases.find((entry) => entry.apiName === 'daily').params.trade_date = '20260810' },
    (plan) => { plan.cases.find((entry) => entry.apiName === 'margin').params.exchange_id = 'SZSE' },
    (plan) => { plan.cases[0].params.extra = 'unexpected' },
    (plan) => { delete plan.cases[0].params.list_status },
    (plan) => { plan.cases[0].mode = 'RANGE' },
    (plan) => { plan.cases[0].apiName = 'unknown' },
    (plan) => { plan.cases[0].dateAxis = 'TRADE_DATE' },
    (plan) => { plan.cases[0].start = '20260807' },
    (plan) => { plan.cases[0].end = '20260807' },
    (plan) => { plan.cases[0].evidenceRefs = ['token=synthetic-forbidden-value'] },
    (plan) => { plan.runId = '' },
    (plan) => { plan.extra = true },
  ]) {
    const plan = fixedSinglePlan()
    mutate(plan)
    assert.throws(() => liveContract.selectTaskCases('single', plan), { message: SAFE_ERROR })
  }
  for (const invalid of [null, false, '', {}]) {
    assert.throws(() => liveContract.selectTaskCases('single', invalid), { message: SAFE_ERROR })
  }
  assert.throws(() => liveContract.selectTaskCases('single', fixedSinglePlan(), initialIndex()), { message: SAFE_ERROR })
})

test('task receipt rejects synchronous 200 and mismatched Location or submission fields', () => {
  assert.equal(typeof liveContract.validateTaskAcceptance, 'function')
  const request = { submissionId: '22222222-2222-4222-8222-222222222222', pluginId: 'tushare_pro', apiName: 'daily', mode: 'SINGLE', params: SINGLE_PARAMS.daily }
  const body = { requestId: 'request-1', taskId: '11111111-1111-4111-8111-111111111111', status: 'QUEUED', version: 1, createdAt: '2026-09-12T01:00:00Z' }
  const receipt = { status: 202, location: `/api/v1/download-tasks/${body.taskId}`, requestId: 'request-1', body }
  assert.equal(liveContract.validateTaskAcceptance(request, request, receipt), body)
  for (const bad of [{ ...receipt, status: 200 }, { ...receipt, location: '/api/v1/downloads' }, { ...receipt, requestId: 'other' }]) assert.throws(() => liveContract.validateTaskAcceptance(request, request, bad))
  assert.throws(() => liveContract.validateTaskAcceptance({ ...request, extra: 1 }, request, receipt))
})

test('range selection consumes clean matching SOURCE without circular AVAILABLE requirement', () => {
  assert.equal(typeof liveContract.selectTaskCases, 'function')
  const index = availableIndex()
  index.runs.pop()
  const daily = index.interfaces.find((c) => c.apiName === 'daily')
  Object.assign(daily, { taskStatus: 'NOT_RUN', disposition: 'NEEDS_VERIFICATION', cases: ['daily-source'] })
  const plan = validRangePlan()
  plan.runId = 'new-task-run'
  plan.cases[0].caseId = 'new-task-case'
  assert.equal(liveContract.selectTaskCases('range', plan, index).length, 1)
  for (const mutate of [
    (p, i) => { p.cases = [] },
    (p, i) => { p.cases[0].caseId = 'daily-source' },
    (p, i) => { p.cases[0].params.ts_code = '600000.SH' },
    (p, i) => { i.runs[0].cleanup.status = 'FAILED' },
    (p, i) => { i.interfaces.find((c) => c.apiName === 'daily').completeness = { kind: 'UNKNOWN', rowLimit: null, evidenceRefs: [] } },
  ]) {
    const p = clone(plan), i = clone(index)
    mutate(p, i)
    assert.throws(() => liveContract.selectTaskCases('range', p, i))
  }
})

test('SQL snapshot hashes every canonical key and exact value and protects other-stock history', () => {
  assert.equal(typeof liveContract.summarizeSqlRows, 'function')
  const rows = [['000001.SZ', '2026-08-07', '1.230000000000000000', null], ['600000.SH', '2026-08-07', '2.000000000000000000', 'NULL']]
  const before = liveContract.summarizeSqlRows(rows, [0, 1], 0, '000001.SZ')
  assert.equal(before.keyCount, 2)
  assert.equal(before.selectedCount, 1)
  const changed = clone(rows); changed[0][2] = '1.24'
  const after = liveContract.summarizeSqlRows(changed, [0, 1], 0, '000001.SZ')
  assert.notEqual(after.rowDigest, before.rowDigest)
  assert.equal(after.otherStockDigest, before.otherStockDigest)
  changed[1][3] = null
  assert.notEqual(liveContract.summarizeSqlRows(changed, [0, 1], 0, '000001.SZ').otherStockDigest, before.otherStockDigest)
  assert.throws(() => liveContract.summarizeSqlRows([...rows, rows[0]], [0, 1], 0, '000001.SZ'))
})

test('task read routes allow only owned UUIDs and exact bounded list parameters', () => {
  assert.equal(typeof liveContract.allowedTaskRead, 'function')
  const id = '11111111-1111-4111-8111-111111111111', ids = new Set([id])
  for (const route of [`/downloads/tasks/${id}`, `/api/v1/download-tasks/${id}`, `/api/v1/download-tasks/${id}/batches?page=2&pageSize=100&includeSplit=true`, '/api/v1/download-tasks?page=1&pageSize=20']) assert.equal(liveContract.allowedTaskRead(new URL(route, 'http://localhost'), ids), true)
  for (const route of [`/api/v1/download-tasks/${id}/retry`, `/api/v1/download-tasks/${id}/batches?page=1&pageSize=100&includeSplit=false`, '/api/v1/download-tasks?page=1&pageSize=20&token=x', '/api/v1/download-tasks/22222222-2222-4222-8222-222222222222']) assert.equal(liveContract.allowedTaskRead(new URL(route, 'http://localhost'), ids), false)
})

test('batch pages must cover the full actual split tree and match terminal leaf counts', () => {
  assert.equal(typeof liveContract.summarizeTaskBatches, 'function')
  const task = { status: 'SUCCEEDED', counts: { totalBatches: 2, pendingBatches: 0, runningBatches: 0, succeededBatches: 2, failedBatches: 0, splitBatches: 1, sourceRows: 5, insertedRows: 4, updatedRows: 1 } }
  const nodes = [
    { batchId: 'p', parentBatchId: null, status: 'SPLIT', rangeStart: '2026-08-03', rangeEnd: '2026-08-10', sourceRows: 0, insertedRows: 0, updatedRows: 0, error: null },
    { batchId: 'a', parentBatchId: 'p', status: 'SUCCEEDED', rangeStart: '2026-08-03', rangeEnd: '2026-08-06', sourceRows: 2, insertedRows: 2, updatedRows: 0, error: null },
    { batchId: 'b', parentBatchId: 'p', status: 'SUCCEEDED', rangeStart: '2026-08-07', rangeEnd: '2026-08-10', sourceRows: 3, insertedRows: 2, updatedRows: 1, error: null },
  ]
  const result = liveContract.summarizeTaskBatches(task, nodes)
  assert.equal(result.batchNodes.length, 3)
  assert.equal(result.leafCount, 2)
  assert.equal(result.sourceRowCount, 5)
  assert.throws(() => liveContract.summarizeTaskBatches(task, nodes.slice(1)))
  assert.throws(() => liveContract.summarizeTaskBatches(task, [...nodes, nodes[2]]))
  assert.throws(() => liveContract.summarizeTaskBatches({ ...task, counts: { ...task.counts, sourceRows: 9 } }, nodes))
})

test('SQL metadata accepts only static YAML identifiers and keeps composite business key order', () => {
  assert.equal(typeof liveContract.sqlMetadata, 'function')
  const yaml = readFileSync(new URL('../../data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/daily.yaml', import.meta.url), 'utf8')
  assert.deepEqual(liveContract.sqlMetadata(yaml), { table: 'tushare_pro__daily', keys: ['ts_code', 'trade_date'] })
  assert.throws(() => liveContract.sqlMetadata(yaml.replace('tushare_pro__daily', 'bad; SELECT 1')))
})

// Execute the tracked imperative functions without importing/registering Playwright tests.
// Only their I/O boundaries are supplied as inert test doubles; their bodies are never copied.
import { mkdtempSync, writeFileSync, chmodSync, lstatSync, rmSync, symlinkSync } from 'node:fs'
import { readFile, lstat } from 'node:fs/promises'
import { execFile } from 'node:child_process'
import { promisify } from 'node:util'
import path from 'node:path'
import { createHash } from 'node:crypto'
import { EventEmitter } from 'node:events'
import { PassThrough, Writable } from 'node:stream'
const harnessUrl = new URL('./tushare-live.spec.js', import.meta.url)
const harnessText = readFileSync(harnessUrl, 'utf8')
function harnessFunction(name, dependencies = {}, source = harnessText) {
  const pattern = new RegExp(`^(?:async )?function ${name}\\(`, 'm')
  const start = source.search(pattern)
  assert.notEqual(start, -1, `tracked harness defines ${name}`)
  const end = source.indexOf('\n}', start) + 2
  const body = source.slice(start, end).replaceAll('import.meta.url', JSON.stringify(harnessUrl.href))
  return new Function(...Object.keys(dependencies), `return (${body})`)(...Object.values(dependencies))
}
const check = (condition, message) => assert.ok(condition, message)
const uiExpect = (actual) => ({ toEqual: (wanted) => assert.deepEqual(actual, wanted), toBeVisible: async () => {}, toBeChecked: async () => assert.equal(actual.isChecked(), true), toHaveAttribute: async (name, value) => assert.equal(actual.getAttribute(name), value) })

// Evaluate real registration only; callbacks cannot launch a browser, JVM, SQL or source request.
function singleHarnessBootstrap(env = {}) {
  const registered = []
  const testApi = Object.assign((name) => registered.push(name), {
    use() {}, beforeAll() {}, afterAll() {},
    describe: Object.assign((name, callback) => callback(), { configure() {} }),
  })
  const dependencies = { ...liveContract, test: testApi, execFile, promisify, createHash,
    randomUUID: () => '11111111-1111-4111-8111-111111111111', path, readFileSync, lstatSync, readFile,
    process: { env: { ISSUE018_T13_PHASE: 'single', ...env }, getuid: () => process.getuid() },
  }
  const source = harnessText.replace(/^import[\s\S]*? from ['"][^'"]+['"]\n/gm, '')
    .replace(/^export /gm, '')
    .replaceAll('import.meta.url', JSON.stringify(harnessUrl.href))
  const state = new Function(...Object.keys(dependencies), `${source}\nreturn { evidence, INTERFACES, casePlanInput, evidenceInputsUnchanged }`)(...Object.values(dependencies))
  return { ...state, registered }
}

test('SINGLE harness loads a private plan with exact identities and binds its bytes through cleanup', async () => {
  const directory = mkdtempSync('/tmp/issue018-t14-single-plan-')
  const file = path.join(directory, 'plan.json')
  const plan = fixedSinglePlan()
  const bytes = Buffer.from(JSON.stringify(plan))
  chmodSync(directory, 0o700)
  writeFileSync(file, bytes, { mode: 0o600 })
  try {
    const run = singleHarnessBootstrap({ ISSUE018_T13_CASES_FILE: file })
    assert.equal(run.evidence.runId, 'issue018-t14-single-20260913T010203Z')
    assert.deepEqual(run.evidence.cases.map((entry) => entry.caseId), plan.cases.map((entry) => entry.caseId))
    assert.equal(run.registered.length, 40)
    assert.equal(run.evidence.scope.selectedSamples, 74)
    assert.deepEqual(run.INTERFACES.flatMap((entry) => entry.cases), plan.cases)
    assert.ok(run.evidence.cases.every((entry) => entry.status === 'NOT_RUN' && entry.taskId === null))
    assert.equal(run.casePlanInput.sha256, createHash('sha256').update(bytes).digest('hex'))
    assert.equal(await run.evidenceInputsUnchanged(), true)
    writeFileSync(file, `${bytes}\n`)
    assert.equal(await run.evidenceInputsUnchanged(), false)
    writeFileSync(file, bytes)
    chmodSync(file, 0o644)
    assert.equal(await run.evidenceInputsUnchanged(), false)
    chmodSync(file, 0o600)
    chmodSync(directory, 0o755)
    assert.equal(await run.evidenceInputsUnchanged(), false)
    chmodSync(directory, 0o700)
    rmSync(file)
    const replacement = path.join(directory, 'replacement.json')
    writeFileSync(replacement, bytes, { mode: 0o600 })
    symlinkSync(replacement, file)
    assert.equal(await run.evidenceInputsUnchanged(), false)
  } finally { rmSync(directory, { recursive: true, force: true }) }
})

test('SINGLE harness rejects unsafe private plans before registering any live work', () => {
  const directory = mkdtempSync('/tmp/issue018-t14-single-input-rejection-')
  const file = path.join(directory, 'plan.json')
  chmodSync(directory, 0o700)
  writeFileSync(file, JSON.stringify(fixedSinglePlan()), { mode: 0o600 })
  const load = (target = file) => singleHarnessBootstrap({ ISSUE018_T13_CASES_FILE: target })
  try {
    for (const target of ['', 'relative-plan.json', `${file}.missing`, directory]) {
      assert.throws(() => load(target), /Safe check failed: task evidence input/)
    }
    chmodSync(file, 0o644)
    assert.throws(() => load(), /Safe check failed: task evidence input/)
    chmodSync(file, 0o600)
    chmodSync(directory, 0o755)
    assert.throws(() => load(), /Safe check failed: task evidence input/)
    chmodSync(directory, 0o700)
    const link = path.join(directory, 'link.json')
    symlinkSync(file, link)
    assert.throws(() => load(link), /Safe check failed: task evidence input/)
    writeFileSync(file, '{invalid JSON')
    assert.throws(() => load(), /Safe check failed: task evidence input/)
    const incomplete = fixedSinglePlan(); incomplete.cases.pop()
    writeFileSync(file, JSON.stringify(incomplete))
    assert.throws(() => load(), { message: SAFE_ERROR })
  } finally { rmSync(directory, { recursive: true, force: true }) }
})

test('SINGLE harness without a plan retains 40 APIs 74 tasks and generated case identities', () => {
  const run = singleHarnessBootstrap()
  assert.equal(run.registered.length, 40)
  assert.equal(run.evidence.cases.length, 74)
  assert.equal(run.evidence.runId, 'issue018-t13-single-11111111-1111-4111-8111-111111111111')
  assert.equal(run.evidence.cases[0].caseId, `${run.evidence.runId}-stock_basic-single-1`)
  assert.equal(run.casePlanInput, undefined)
})

const TASK_QUERY_ID = '33333333-3333-4333-8333-333333333333'
const TASK_QUERY_CONTEXT = { pluginId: 'tushare_pro', apiName: 'daily', caseId: 'daily-task',
  taskId: '11111111-1111-4111-8111-111111111111' }
const taskQueryRoute = `/api/v1/download-tasks/${TASK_QUERY_CONTEXT.taskId}`
const taskQueryError = (code = 'TASK_NOT_FOUND') => ({ requestId: TASK_QUERY_ID, code,
  message: 'Task is unavailable.', retryable: false, fieldErrors: [] })
function taskQueryFixture({ body = taskQueryError(), header = TASK_QUERY_ID, status = 404, raw } = {}) {
  const prior = [{ taskId: TASK_QUERY_CONTEXT.taskId, task: { status: 'SUCCEEDED' } },
    { taskId: '22222222-2222-4222-8222-222222222222', task: { status: 'RUNNING' } }]
  const evidence = { taskObservations: clone(prior) }
  const requests = [], remembered = []
  const page = { evaluate: async (callback, route) => new Function('crypto', 'fetch', `return (${callback})(arguments[2])`)(
    { randomUUID: () => TASK_QUERY_ID }, async (pathname, options) => {
      requests.push({ pathname, options })
      return { status, headers: { get: () => header }, text: async () => raw ?? JSON.stringify(body) }
    }, route) }
  const read = harnessFunction('readTaskApi', {
    evidence, safeCheck: check, BASE_URL: 'http://localhost', ownedTaskIds: new Set([TASK_QUERY_CONTEXT.taskId]),
    allowedTaskRead: liveContract.allowedTaskRead, safeTaskQueryFailure: liveContract.safeTaskQueryFailure,
    assertSafeText: harnessFunction('assertSafeText', { safeCheck: check, forbiddenValues: () => [] }),
    validateApiError: (error) => check(error.code === 'QUERY_FAILED', 'legacy public code'),
    ledger: { rememberRequestId: (id) => remembered.push(id) },
  })
  return { read: (route = taskQueryRoute, context = TASK_QUERY_CONTEXT, parser = () => assert.fail('error must not use success parser')) => read(page, route, parser, context),
    evidence, requests, remembered, prior }
}

test('imperative task errors retain TASK_NOT_FOUND with sent/header/body identity and owned detail/batch context', async () => {
  for (const route of [taskQueryRoute, `${taskQueryRoute}/batches?page=2&pageSize=100&includeSplit=true`]) {
    const fixture = taskQueryFixture()
    await assert.rejects(fixture.read(route))
    assert.deepEqual(fixture.evidence.queryFailures, [{ operation: 'TASK_QUERY', ...TASK_QUERY_CONTEXT,
      pathname: route, httpStatus: 404, requestId: TASK_QUERY_ID, errorCode: 'TASK_NOT_FOUND' }])
    assert.deepEqual(fixture.requests, [{ pathname: route, options: { headers: { 'X-Request-Id': TASK_QUERY_ID } } }])
    assert.deepEqual(fixture.evidence.taskObservations, fixture.prior)
    assert.equal(JSON.stringify(fixture.evidence).includes('Task is unavailable.'), false)
  }
  const fixture = taskQueryFixture()
  await assert.rejects(fixture.read(taskQueryRoute, { ...TASK_QUERY_CONTEXT, pluginId: 'fixture', apiName: 'fixture_daily', caseId: null }))
  assert.equal(fixture.evidence.queryFailures[0].caseId, null)
  assert.equal(fixture.evidence.queryFailures[0].errorCode, 'TASK_NOT_FOUND')
})

test('imperative task query identity mismatches cannot become correlated classified evidence', async () => {
  const other = '44444444-4444-4444-8444-444444444444'
  for (const input of [
    { header: other, body: taskQueryError('QUERY_FAILED') },
    { header: null, body: taskQueryError('QUERY_FAILED') },
    { body: { ...taskQueryError('QUERY_FAILED'), requestId: other } },
    { header: other, body: { ...taskQueryError('QUERY_FAILED'), requestId: other } },
  ]) {
    const fixture = taskQueryFixture(input)
    await assert.rejects(fixture.read())
    assert.deepEqual(fixture.evidence.queryFailures, [{ operation: 'TASK_QUERY', httpStatus: 404, requestId: null, errorCode: null }])
    assert.deepEqual(fixture.evidence.taskObservations, fixture.prior)
    assert.equal(fixture.requests.length, 1)
    assert.deepEqual(fixture.remembered, [])
  }
})

test('imperative malformed unsafe and unknown task errors stop without accepting a classified observation', async () => {
  for (const input of [
    { raw: '{' }, { body: null }, { body: [] }, { body: taskQueryError('NOT_A_PUBLIC_CODE') },
    { body: { ...taskQueryError(), extra: 'unexpected' } },
    { body: { ...taskQueryError(), message: '' } },
    { body: { ...taskQueryError(), retryable: 'false' } },
    { body: { ...taskQueryError(), fieldErrors: [{ field: 'params' }] } },
    { body: { ...taskQueryError(), message: 'token=canary-do-not-persist' } },
    { body: { ...taskQueryError(), cause: { body: 'canary-do-not-persist' } } },
    { status: 302 },
  ]) {
    const fixture = taskQueryFixture(input)
    await assert.rejects(fixture.read())
    assert.equal((fixture.evidence.queryFailures ?? []).some((f) => f.errorCode !== null || f.requestId !== null), false)
    assert.equal(JSON.stringify(fixture.evidence).includes('canary-do-not-persist'), false)
    assert.deepEqual(fixture.evidence.taskObservations, fixture.prior)
    assert.equal(fixture.requests.length, 1)
    assert.deepEqual(fixture.remembered, [])
  }
})

test('imperative task query rejects mismatched owned context and returns the strict parser result', async () => {
  for (const [route, context] of [
    [`${taskQueryRoute}/retry`, TASK_QUERY_CONTEXT],
    [taskQueryRoute, { ...TASK_QUERY_CONTEXT, taskId: '22222222-2222-4222-8222-222222222222' }],
  ]) {
    const fixture = taskQueryFixture()
    await assert.rejects(fixture.read(route, context))
    assert.equal((fixture.evidence.queryFailures ?? []).some((f) => f.errorCode !== null), false)
  }
  const fixture = taskQueryFixture({ status: 200, body: { status: 'RUNNING' } })
  let parsed = 0
  const result = await fixture.read(taskQueryRoute, TASK_QUERY_CONTEXT, (body, requestId) => {
    assert.deepEqual(body, { status: 'RUNNING' })
    assert.equal(requestId, TASK_QUERY_ID)
    parsed += 1
    return Object.freeze({ status: 'PARSED' })
  })
  assert.deepEqual(result, { status: 'PARSED' })
  assert.equal(parsed, 1)
  assert.deepEqual(fixture.remembered, [TASK_QUERY_ID])
  assert.equal(fixture.evidence.queryFailures, undefined)
  const rejected = taskQueryFixture({ status: 200, body: {} })
  await assert.rejects(rejected.read(taskQueryRoute, TASK_QUERY_CONTEXT, () => { throw new Error('invalid task DTO') }))
})

test('imperative submission passes current case to detail and batch failures without rewriting RUNNING or SUCCEEDED', async () => {
  for (const status of ['RUNNING', 'SUCCEEDED']) {
    const fixture = taskQueryFixture(), observedTasks = []
    const submissionId = '22222222-2222-4222-8222-222222222222'
    const request = { submissionId, pluginId: 'tushare_pro', apiName: 'daily', mode: 'SINGLE', params: SINGLE_PARAMS.daily }
    const receipt = { requestId: TASK_QUERY_ID, taskId: TASK_QUERY_CONTEXT.taskId, status: 'QUEUED', version: 1, createdAt: '2026-09-12T01:00:00Z' }
    const task = { ...request, taskId: receipt.taskId, requestCount: 1, status }
    let posts = 0, reads = 0
    const control = { click: async () => { posts += 1 }, getByRole: () => control }
    const response = { status: () => 202, headers: () => ({ location: taskQueryRoute, 'x-request-id': TASK_QUERY_ID }),
      request: () => ({ postDataJSON: () => request, headers: () => ({ 'x-request-id': TASK_QUERY_ID }) }) }
    const page = { getByRole: () => control, locator: () => control, waitForResponse: async () => response }
    const submit = harnessFunction('submitDownload', {
      safeCheck: check, rateLimit: async () => {}, runDeadline: Number.MAX_SAFE_INTEGER, sourceRequests: 0, phase: 'single', runtimeFailure: undefined,
      ledger: { expectDownload() {}, rememberRequestId() {} }, safeJson: async () => receipt,
      validateTaskAcceptance: liveContract.validateTaskAcceptance, ownedTaskIds: new Set(), observedTasks,
      evidenceInteger: liveContract.evidenceInteger,
      expect: () => ({ toBeVisible: async () => {}, toBeEnabled: async () => {}, toHaveURL: async () => {} }), delay: async () => {},
      parseDownloadTask() {}, parseDownloadBatchPage() {},
      readTaskApi: async (page, route, parser, context) => {
        assert.deepEqual(context, TASK_QUERY_CONTEXT)
        if (reads++ === 0) return task
        return fixture.read(route, context)
      },
    })
    const evidenceCase = { caseId: TASK_QUERY_CONTEXT.caseId }
    await assert.rejects(submit(page, { downloads: () => posts, drain: async () => {} }, 'tushare_pro', { apiName: 'daily' }, SINGLE_PARAMS.daily, false, evidenceCase))
    assert.equal(reads, 2)
    assert.equal(posts, 2) // One submit button and the task-detail link, no second submission.
    assert.deepEqual(observedTasks[0].task, task)
    assert.equal(observedTasks[0].task.status, status)
    assert.equal(fixture.evidence.queryFailures[0].pathname, status === 'RUNNING' ? taskQueryRoute : `${taskQueryRoute}/batches?page=1&pageSize=100&includeSplit=true`)
    assert.equal(fixture.evidence.queryFailures[0].caseId, evidenceCase.caseId)
    assert.equal(fixture.evidence.queryFailures[0].errorCode, 'TASK_NOT_FOUND')
  }
})

const SQL_DEFAULT_LINES = ['[client]', 'host=127.0.0.1', 'port=3306', 'user=offline_user',
  'password=offline_password=with-equals', 'database=tensor_m14_t05_aabbcc', 'protocol=TCP']

async function withSqlInputFixture(run) {
  const directory = mkdtempSync('/tmp/issue018-t14-sql-defaults-')
  const file = path.join(directory, 'mysql.cnf')
  chmodSync(directory, 0o700)
  const env = { ISSUE018_T13_MYSQL_DEFAULTS_FILE: file,
    TENSOR_DB_URL: 'jdbc:mysql://127.0.0.1:3306/tensor_m14_t05_aabbcc',
    TENSOR_DB_USERNAME: 'offline_user', TENSOR_DB_PASSWORD: 'offline_password=with-equals' }
  try {
    await run(async (lines, overrides = {}) => {
      writeFileSync(file, `${lines.join('\n')}\n`, { mode: 0o600 })
      const validate = harnessFunction('validateSqlInputs', {
        mysqlDefaultsPath: undefined, path, lstat, readFile, safeCheck: check,
        objectWithExactKeys: harnessFunction('objectWithExactKeys', { safeCheck: check }),
        process: { env: { ...env, ...overrides }, getuid: () => process.getuid() },
      })
      return validate()
    })
  } finally { rmSync(directory, { recursive: true, force: true }) }
}

test('imperative SQL defaults accepts the legacy seven lines and explicit utf8mb4 eighth line', async () => {
  await withSqlInputFixture(async (validate) => {
    await assert.doesNotReject(validate(SQL_DEFAULT_LINES))
    await assert.doesNotReject(validate([...SQL_DEFAULT_LINES, 'default-character-set=utf8mb4']))
  })
})

test('imperative SQL defaults rejects other charsets duplicate keys and unknown options', async () => {
  await withSqlInputFixture(async (validate) => {
    for (const lines of [
      ...['utf8', 'latin1', 'UTF8MB4', ''].map((charset) => [...SQL_DEFAULT_LINES, `default-character-set=${charset}`]),
      [...SQL_DEFAULT_LINES, 'host=127.0.0.1'],
      [...SQL_DEFAULT_LINES, 'default-character-set=utf8mb4', 'default-character-set=utf8mb4'],
      [...SQL_DEFAULT_LINES, 'unknown=unexpected'],
      SQL_DEFAULT_LINES.map((line) => line.startsWith('protocol=') ? 'host=127.0.0.1' : line),
      SQL_DEFAULT_LINES.map((line) => line.startsWith('protocol=') ? 'unknown=unexpected' : line),
    ]) await assert.rejects(validate(lines), /SQL defaults/)
  })
})

test('imperative SQL defaults matches the full application account and schema in either accepted format', async () => {
  await withSqlInputFixture(async (validate) => {
    for (const lines of [SQL_DEFAULT_LINES, [...SQL_DEFAULT_LINES, 'default-character-set=utf8mb4']]) {
      for (const overrides of [
        { TENSOR_DB_URL: 'jdbc:mysql://127.0.0.2:3306/tensor_m14_t05_aabbcc' },
        { TENSOR_DB_URL: 'jdbc:mysql://127.0.0.1:3307/tensor_m14_t05_aabbcc' },
        { TENSOR_DB_URL: 'jdbc:mysql://127.0.0.1:3306/tensor_m14_t05_ddeeff' },
        { TENSOR_DB_USERNAME: 'different_user' },
        { TENSOR_DB_PASSWORD: 'different_password' },
      ]) await assert.rejects(validate(lines, overrides), /SQL matches application account and schema/)
      await assert.rejects(validate(lines.map((line) => line === 'protocol=TCP' ? 'protocol=SOCKET' : line)), /SQL matches application account and schema/)
    }
  })
})

test('imperative mysql pins utf8mb4 under the sanitized C locale and preserves Unicode/HEX transport', async () => {
  const unicode = '主营业务：软件服务／製品 café 🧪'
  const framed = [unicode, null, '12345678901234567890.001230'].map((value) => value === null ? 'N' : `V${Buffer.from(value).toString('hex').toUpperCase()}`).join('\t')
  for (const output of [unicode, framed]) {
    let invocation, input = ''
    const mysql = harnessFunction('mysql', {
      safeCheck: check, mysqlDefaultsPath: '/inert/private-mysql.cnf',
      process: { env: { PATH: '/inert/bin', LANG: 'C', LC_ALL: 'C', MYSQL_PWD: 'synthetic-secret', TENSOR_DB_PASSWORD: 'synthetic-secret' } },
      spawn: (...args) => {
        invocation = args
        const child = new EventEmitter()
        child.stdout = new PassThrough()
        child.stderr = new PassThrough()
        child.kill = () => assert.fail('successful inert child must not be killed')
        child.stdin = new Writable({
          write(chunk, encoding, callback) { input += chunk.toString('utf8'); callback() },
          final(callback) {
            // Split inside a multibyte character to exercise the actual Buffer decoder.
            const bytes = Buffer.from(`${output}\n`)
            child.stdout.write(bytes.subarray(0, 1))
            child.stdout.end(bytes.subarray(1))
            callback()
            child.emit('close', 0)
          },
        })
        return child
      },
    })
    const sql = `SELECT '${unicode}'`
    assert.equal(await mysql(sql), output)
    assert.equal(input, `${sql};\n`)
    assert.deepEqual(invocation, ['mysql', [
      '--defaults-file=/inert/private-mysql.cnf', '--no-login-paths', '--default-character-set=utf8mb4', '--batch', '--skip-column-names', '--raw',
    ], { shell: false, env: { PATH: '/inert/bin', LANG: 'C', LC_ALL: 'C' }, stdio: ['pipe', 'pipe', 'pipe'] }])
  }
  assert.deepEqual(framed.split('\t').map((cell) => cell === 'N' ? null : Buffer.from(cell.slice(1), 'hex').toString('utf8')), [unicode, null, '12345678901234567890.001230'])
})

test('imperative mysql rejects non-SELECT and multiple statements before spawning', async () => {
  const mysql = harnessFunction('mysql', { safeCheck: check, spawn: () => assert.fail('rejected SQL must not spawn a child') })
  for (const sql of ['UPDATE business SET value = 1', 'DELETE FROM business', 'SELECT 1; SELECT 2']) {
    await assert.rejects(mysql(sql), /read-only SQL statement/)
  }
})

test('imperative RANGE selection fills the chosen capability labels for every date axis', async () => {
  for (const prefix of ['交易', '公告', '报告期', '日历', '上网发行', '最新披露公告日']) {
    const descriptors = ['start_date', 'end_date'].map((name, i) => ({ name, label: `${prefix}${i ? '结束' : '开始'}日期`, type: 'DATE_RANGE_MEMBER' }))
    const single = descriptors.map((d, i) => ({ ...d, label: i ? '结束日期' : '开始日期' }))
    const candidate = { apiName: 'daily', dateAxis: 'TRADE_DATE', policyVersion: 'tushare-range-v2', planningMode: 'NATIVE_RANGE', completeness: { kind: 'ROW_LIMIT', rowLimit: 6000 } }
    const capability = { single: { available: true, parameters: single }, range: { ...candidate, availability: 'AVAILABLE', completenessRule: { kind: 'CONFIRMED_ROW_LIMIT', rowLimit: 6000 }, parameters: descriptors } }
    const responses = [{ body: [{ apiName: 'daily', parameters: single }] }, { body: { unparsed: true } }].map((r) => ({ ...r, status: () => 200, headers: () => ({ 'x-request-id': 'capability-request' }) }))
    let selectedMode
    const values = {}
    const page = {
      waitForResponse: async () => responses.shift(), keyboard: { press: async () => {} },
      locator: () => ({ getByText: () => ({}) }),
      getByRole: (role, options) => ({
        getAttribute: (name) => name === 'aria-pressed' ? String(selectedMode === options.name) : undefined,
        click: async () => {
          assert.deepEqual([role, options], ['button', { name: '批量下载', exact: true }])
          selectedMode = options.name
        },
      }),
      getByLabel: (pattern) => {
        assert.equal(selectedMode, '批量下载')
        const descriptor = descriptors.find((d) => pattern.test(`${d.label} *`))
        assert.ok(descriptor, `actual capability label must match ${pattern}`)
        return { fill: async (value) => { values[descriptor.name] = value }, press: async () => {}, inputValue: async () => values[descriptor.name] }
      },
    }
    const downloadParameters = new WeakMap()
    const common = { safeCheck: check, selectOption: async () => {}, expect: uiExpect, downloadParameters }
    const choose = harnessFunction('chooseDownload', { ...common, safeJson: async (r) => r.body, SUPPORTED_API_NAMES: ['daily'], phase: 'range', candidateIndex: { interfaces: [candidate] }, parseDownloadCapabilities: (wire) => { assert.deepEqual(wire, { unparsed: true }); return capability }, assertPageSafe: async () => {}, optionName: () => 'daily' })
    const fill = harnessFunction('fillSample', { ...common, PARAMETERS: Object.fromEntries(single.map((d) => [d.name, d])), escapeRegex: harnessFunction('escapeRegex'), compactDisplayValue: harnessFunction('compactDisplayValue') })
    await choose(page, 'tushare_pro', { apiName: 'daily', parameters: ['start_date', 'end_date'] })
    const sample = { start_date: '20260803', end_date: '20260810' }
    await fill(page, { apiName: 'daily' }, sample)
    assert.deepEqual(values, { start_date: '2026-08-03', end_date: '2026-08-10' })
    assert.deepEqual(sample, { start_date: '20260803', end_date: '20260810' })
  }
})

function imperativeCaseFixture(gap = false) {
  const fact = validTaskEvidence()
  fact.sourceRowCount = fact.insertedRows = fact.updatedRows = 0
  fact.emptyLeafCount = 1
  Object.assign(fact.batchNodes[0], { sourceRowCount: 0, insertedRows: 0, updatedRows: 0 })
  if (gap) fact.batchNodes[0].start = '20260804'
  const retained = safeCaseEvidence({ ...fact, status: 'EVIDENCE_MISSING' })
  const evidence = { cases: [retained] }, queryOptions = []
  const sample = { caseId: fact.caseId, apiName: fact.apiName, params: fact.params }
  const entry = { contract: { apiName: 'daily' }, cases: [sample], params: [sample.params] }
  const snapshot = { keyCount: 0, selectedCount: 0, otherStockDigest: 'same', keyDigest: '4'.repeat(64), ownershipSummary: '000001.SZ:0' }
  const run = harnessFunction('runLiveInterface', {
    evidence, safeCaseEvidence, validateEvidenceCase: liveContract.validateEvidenceCase,
    safeCheck: check, runCounters: { enter() {}, fail() {}, complete() {} }, liveCaseTimeoutMs: () => 600_000,
    beforeDeadline: (promise) => promise, initializeOwnedPage: async (browser, owned) => Object.assign(owned, { page: { getByText: () => ({}) }, monitor: { drain: async () => {} } }),
    openDataset: async () => ({}), queryDataset: async (...args) => { queryOptions.push(args[5]); return { totalElements: 0, items: [] } },
    sqlSnapshot: async () => snapshot, openDownloadFromDataset: async () => {}, fillSample: async () => {},
    submitDownload: async () => ({ body: fact, task: { status: 'SUCCEEDED', lastError: null } }), expect: uiExpect,
    signalOwnedApplication() {}, cleanupOwnedResources: async () => {},
  })
  return { run: () => run({}, entry), retained, queryOptions }
}

test('imperative rejected coverage validation cannot leave the retained case PASS', async () => {
  const fixture = imperativeCaseFixture(true)
  await assert.rejects(fixture.run())
  assert.equal(fixture.retained.status, 'EVIDENCE_MISSING')
})

test('imperative sample flow labels both records queries with its exact case and position', async () => {
  const fixture = imperativeCaseFixture()
  await fixture.run()
  assert.deepEqual(fixture.queryOptions, [
    { code: '000001.SZ', caseId: 'daily-task', position: 'BEFORE' },
    { code: '000001.SZ', caseId: 'daily-task', position: 'AFTER' },
  ])
})

test('imperative records errors preserve only fixed facts and never change observed task SUCCEEDED', async () => {
  for (const position of ['BEFORE', 'AFTER']) {
    const task = { status: 'SUCCEEDED' }
    const evidence = { taskObservations: [task] }
    const error = { requestId: `records-${position}`, code: 'QUERY_FAILED', message: 'Query could not complete.', retryable: false, fieldErrors: [] }
    const response = { status: () => 500, headers: () => ({ 'x-request-id': error.requestId }) }
    const page = { waitForResponse: async () => response, getByRole: () => ({ click: async () => {} }) }
    const query = harnessFunction('queryDataset', {
      evidence, safeCheck: check, safeJson: async () => error,
      ledger: { expectQuery() {}, rememberRequestId() {} }, validatePageBody: (r) => check(r.status() === 200, 'records status'),
      validateApiError: (body) => assert.equal(body.code, 'QUERY_FAILED'),
      safeRecordsQueryFailure: liveContract.safeRecordsQueryFailure,
    })
    await assert.rejects(query(page, { records: () => 0 }, 'tushare_pro', { apiName: 'daily' }, {}, { caseId: 'daily-task', position }))
    assert.deepEqual(evidence.queryFailures, [{ operation: 'RECORDS_QUERY', pluginId: 'tushare_pro', apiName: 'daily', caseId: 'daily-task', position, httpStatus: 500, requestId: error.requestId, errorCode: 'QUERY_FAILED' }])
    assert.deepEqual(evidence.taskObservations, [{ status: 'SUCCEEDED' }])
    assert.equal(JSON.stringify(evidence).includes(error.message), false)
  }
})

test('selection and imperative initial evidence share the same clean referenced SOURCE identity', () => {
  const index = availableIndex()
  index.runs.pop()
  const entry = index.interfaces.find((c) => c.apiName === 'daily')
  Object.assign(entry, { taskStatus: 'NOT_RUN', disposition: 'NEEDS_VERIFICATION', cases: ['daily-source'] })
  const source = index.runs[0].cases[0]
  source.expectedCoverage = 'chosen clean referenced coverage'
  for (const [runId, caseId, exitCode] of [['unclean', 'unclean-case', 1], ['unreferenced', 'unreferenced-case', 0]]) {
    const run = clone(index.runs.at(-1))
    Object.assign(run, { runId, exitCode })
    run.cases = [safeCaseEvidence({ ...source, caseId, expectedCoverage: `wrong ${caseId}` })]
    index.runs.unshift(run)
  }
  validateEvidence(index)
  const plan = validRangePlan(); plan.runId = 'new-task-run'; plan.cases[0].caseId = 'new-task-case'
  const sourceBindings = new Map()
  liveContract.selectTaskCases('range', plan, index, sourceBindings)
  const initial = harnessFunction('initialTaskEvidence', { candidateIndex: index, sourceBindings, safeCaseEvidence })(plan.cases[0])
  assert.equal(initial.expectedCoverage, 'chosen clean referenced coverage')
  assert.equal(initial.expectedCoverageSource, source.expectedCoverageSource)
  assert.deepEqual(sourceBindings.get('new-task-case'), { runId: 'issue018-t13-source-01', caseId: 'daily-source', expectedCoverage: source.expectedCoverage, expectedCoverageSource: source.expectedCoverageSource })
})

test('strict BigInt task and batch DTO counts become bounded numeric evidence without losing facts', () => {
  const task = { status: 'SUCCEEDED', counts: { totalBatches: 1n, pendingBatches: 0n,
    runningBatches: 0n, succeededBatches: 1n, failedBatches: 0n, splitBatches: 0n,
    sourceRows: 6n, insertedRows: 5n, updatedRows: 1n } }
  const nodes = [{ batchId: 'leaf', parentBatchId: null, status: 'SUCCEEDED',
    rangeStart: '2026-08-03', rangeEnd: '2026-08-10', sourceRows: 6n,
    insertedRows: 5n, updatedRows: 1n, error: null }]
  const summary = liveContract.summarizeTaskBatches(task, nodes)
  assert.equal(summary.sourceRowCount, 6)
  assert.equal(summary.insertedRows, 5)
  assert.equal(summary.updatedRows, 1)
})

test('runtime RESPONSE_ONLY contract checks persisted extraction and one whole-window leaf', () => {
  const candidate = { planningMode: 'NATIVE_RANGE', policyVersion: 'tushare-range-v2',
    completeness: { kind: 'RESPONSE_ONLY', rowLimit: null } }
  const params = { ts_code: '000001.SZ', start_date: '20260803', end_date: '20260810' }
  const task = { mode: 'RANGE', status: 'SUCCEEDED', requestCount: 1n,
    extraction: { policyVersion: 'tushare-range-v2', ruleKind: 'RESPONSE_ONLY' } }
  const leaf = { batchId: 'leaf', parentBatchId: null, status: 'SUCCEEDED', attemptCount: 1,
    rangeStart: '2026-08-03', rangeEnd: '2026-08-10' }
  assert.equal(liveContract.validateTaskRuntime(candidate, params, task, [leaf]), true)
  for (const [changedTask, batches] of [
    [{ ...task, extraction: { ...task.extraction, policyVersion: 'historical-v1' } }, [leaf]],
    [{ ...task, extraction: { ...task.extraction, ruleKind: 'CONFIRMED_ROW_LIMIT' } }, [leaf]],
    [{ ...task, requestCount: 2n }, [leaf]],
    [task, [{ ...leaf, rangeEnd: '2026-08-09' }]],
    [task, [leaf, { ...leaf, batchId: 'second' }]],
  ]) assert.throws(() => liveContract.validateTaskRuntime(candidate, params, changedTask, batches),
    { message: SAFE_ERROR })
})

test('imperative submission preserves failed evidence and accepts exact success outcomes', async () => {
  for (const [mode, kind, sourceRows, label, failed = false] of [
    ['RANGE', 'RESPONSE_ONLY', 4n, '返回记录已采集'],
    ['RANGE', 'RESPONSE_ONLY', 0n, '本次请求未返回记录'],
    ['RANGE', 'ROW_LIMIT', 4n, '已成功'],
    ['SINGLE', null, 4n, '已成功'],
    ['RANGE', 'RESPONSE_ONLY', 0n, '失败', true],
  ]) {
    const params = mode === 'RANGE'
      ? { ts_code: '000001.SZ', start_date: '20251229', end_date: '20260105' }
      : SINGLE_PARAMS.adj_factor
    const request = { submissionId: '22222222-2222-4222-8222-222222222222',
      pluginId: 'tushare_pro', apiName: 'adj_factor', mode, params }
    const receipt = { requestId: TASK_QUERY_ID, taskId: TASK_QUERY_CONTEXT.taskId,
      status: 'QUEUED', version: 1, createdAt: '2026-09-12T01:00:00Z' }
    const candidate = { apiName: 'adj_factor', policyVersion: 'tushare-range-v2',
      planningMode: 'NATIVE_RANGE', completeness: { kind, rowLimit: kind === 'ROW_LIMIT' ? 6000 : null } }
    const task = { ...request, taskId: receipt.taskId, status: 'SUCCEEDED', requestCount: 1n,
      planReady: true, lastError: null, canRetry: false, canResume: false,
      extraction: mode === 'RANGE' ? { policyVersion: candidate.policyVersion,
        ruleKind: kind === 'ROW_LIMIT' ? 'CONFIRMED_ROW_LIMIT' : kind } : null,
      counts: { totalBatches: 1n, pendingBatches: 0n, runningBatches: 0n,
        succeededBatches: 1n, failedBatches: 0n, splitBatches: 0n,
        sourceRows, insertedRows: sourceRows, updatedRows: 0n } }
    const leaf = { batchId: 'leaf', parentBatchId: null, status: 'SUCCEEDED', attemptCount: 1,
      rangeStart: mode === 'RANGE' ? '2025-12-29' : null,
      rangeEnd: mode === 'RANGE' ? '2026-01-05' : null,
      sourceRows, insertedRows: sourceRows, updatedRows: 0n, error: null }
    if (failed) {
      task.status = leaf.status = 'FAILED'
      task.lastError = leaf.error = { code: 'ADAPTER_TYPE_INVALID' }
      task.counts.succeededBatches = 0n
      task.counts.failedBatches = 1n
    }
    let posts = 0, reads = 0, statusChecks = 0
    const visible = []
    const control = { click: async () => {}, getByRole: () => control,
      locator: (selector) => selector === ':scope > .confirmation dd' ? { last: () => ({ text: sourceRows.toString() }) } : control,
      getByText: (text) => ({ text }) }
    const response = { status: () => 202,
      headers: () => ({ location: taskQueryRoute, 'x-request-id': TASK_QUERY_ID }),
      request: () => ({ postDataJSON: () => request, headers: () => ({ 'x-request-id': TASK_QUERY_ID }) }) }
    const page = { waitForResponse: async () => response,
      getByRole: (role, options) => options.name?.test?.('开始下载')
        ? { click: async () => { posts += 1 } } : control,
      locator: (selector) => selector === '.task-detail [data-task-status]' ? { text: label } : control }
    const pageExpect = (actual) => ({
      toBeVisible: async () => { if (actual.text) visible.push(actual.text) },
      toBeEnabled: async () => {}, toHaveURL: async () => {},
      toHaveText: async (expected) => { if (actual.text === label) statusChecks += 1; else visible.push(`来源行数 ${actual.text}`); assert.equal(actual.text, expected) },
    })
    const evidence = { downloads: [] }, observedTasks = []
    const submit = harnessFunction('submitDownload', {
      safeCheck: check, rateLimit: async () => {}, runDeadline: Number.MAX_SAFE_INTEGER,
      sourceRequests: 0, phase: mode.toLowerCase(), runtimeFailure: undefined,
      ledger: { expectDownload() {}, rememberRequestId() {} }, safeJson: async () => receipt,
      validateTaskAcceptance: liveContract.validateTaskAcceptance, ownedTaskIds: new Set(), observedTasks,
      evidenceInteger: liveContract.evidenceInteger, expect: pageExpect, candidateIndex: { interfaces: [candidate] },
      parseDownloadTask() {}, parseDownloadBatchPage() {},
      readTaskApi: async () => reads++ === 0 ? task : { page: 1, pageSize: 100, total: 1n, items: [leaf] },
      validateTaskRuntime: liveContract.validateTaskRuntime,
      summarizeTaskBatches: liveContract.summarizeTaskBatches,
      safeCaseEvidence: liveContract.safeCaseEvidence,
      evidence, lastDownloadFinishedAt: undefined, assertPageSafe: async () => {},
      assertResponseOnlyPage: harnessFunction('assertResponseOnlyPage', { expect: pageExpect }),
    })
    const evidenceCase = { caseId: 'status-regression', apiName: 'adj_factor', mode, params,
      status: 'EVIDENCE_MISSING' }
    const submission = submit(page, { downloads: () => posts, drain: async () => {} },
      'tushare_pro', { apiName: 'adj_factor' }, params, false, evidenceCase)
    if (failed) {
      await assert.rejects(submission, { message: SAFE_ERROR })
      assert.equal(evidence.downloads.length, 1)
      assert.equal(evidenceCase.status, 'FAILED')
      assert.equal(evidenceCase.errorCode, 'ADAPTER_TYPE_INVALID')
      assert.equal(evidenceCase.failedLeafCount, 1)
      assert.equal(evidenceCase.succeededLeafCount, 0)
      assert.equal(evidenceCase.emptyLeafCount, 0)
      assert.equal(evidenceCase.sqlAfterKeyCount, null)
      assert.equal(evidenceCase.batchNodes[0].status, 'FAILED')
      assert.equal(observedTasks[0].task.status, 'FAILED')
      assert.equal(posts, 1)
      assert.equal(reads, 2)
      assert.equal(statusChecks, 0)
      continue
    }
    const result = await submission
    assert.equal(result.body.sourceRowCount, Number(sourceRows))
    assert.equal(result.task.status, 'SUCCEEDED')
    assert.equal(posts, 1)
    assert.equal(reads, 2)
    assert.equal(statusChecks, 1)
    assert.equal(visible.includes('数据完整性未确认，可能存在上游截断'), kind === 'RESPONSE_ONLY')
    assert.ok(visible.includes(`来源行数 ${sourceRows}`))
  }
})

test('RESPONSE_ONLY task page checks exact outcome and persistent truncation notice', async () => {
  const checked = []
  const page = { locator: (selector) => {
    assert.equal(selector, '.task-detail')
    return { getByText: (value, options) => ({ value, options }) }
  } }
  const expectText = (actual) => ({ toBeVisible: async () => checked.push(actual) })
  const verify = harnessFunction('assertResponseOnlyPage', { expect: expectText })
  await verify(page, 6n)
  await verify(page, 0n)
  assert.deepEqual(checked, [
    { value: '返回记录已采集', options: { exact: true } },
    { value: '数据完整性未确认，可能存在上游截断', options: { exact: true } },
    { value: '本次请求未返回记录', options: { exact: true } },
    { value: '数据完整性未确认，可能存在上游截断', options: { exact: true } },
  ])
})

function responseOnlyImperativeFixture(sourceRowCount, reorderParams = false) {
  const index = responseOnlyIndex()
  const fact = clone(index.runs[1].cases[0])
  fact.sourceRowCount = fact.insertedRows = fact.sqlAfterKeyCount = sourceRowCount
  fact.updatedRows = 0
  fact.emptyLeafCount = sourceRowCount === 0 ? 1 : 0
  Object.assign(fact.batchNodes[0], { sourceRowCount, insertedRows: sourceRowCount, updatedRows: 0 })
  const retained = safeCaseEvidence({ ...fact, status: 'EVIDENCE_MISSING' })
  const evidence = { cases: [retained] }
  const sample = { caseId: fact.caseId, apiName: fact.apiName,
    params: reorderParams
      ? { end_date: fact.params.end_date, ts_code: fact.params.ts_code,
        start_date: fact.params.start_date }
      : fact.params }
  const entry = { contract: { apiName: 'adj_factor' }, cases: [sample], params: [sample.params] }
  const before = { keyCount: 0, selectedCount: 0, otherStockDigest: 'same',
    keyDigest: '4'.repeat(64), ownershipSummary: '000001.SZ:0' }
  const after = { ...before, keyCount: sourceRowCount, selectedCount: sourceRowCount,
    ownershipSummary: `000001.SZ:${sourceRowCount}` }
  const snapshots = [before, after]
  const run = harnessFunction('runLiveInterface', {
    evidence, safeCaseEvidence, validateEvidenceCase: liveContract.validateEvidenceCase,
    safeCheck: check, runCounters: { enter() {}, fail() {}, complete() {} },
    liveCaseTimeoutMs: () => 600_000,
    beforeDeadline: (promise) => promise,
    initializeOwnedPage: async (browser, owned) => Object.assign(owned, {
      page: { getByText: () => ({}) }, monitor: { drain: async () => {} },
    }),
    openDataset: async () => ({}),
    queryDataset: async (...args) => ({ totalElements: args[5].position === 'BEFORE' ? 0 : sourceRowCount,
      items: [] }),
    sqlSnapshot: async () => snapshots.shift(), openDownloadFromDataset: async () => {},
    fillSample: async () => {},
    submitDownload: async () => ({ body: fact,
      task: { status: 'SUCCEEDED', lastError: null,
        extraction: { policyVersion: 'tushare-range-v2', ruleKind: 'RESPONSE_ONLY' } } }),
    expect: uiExpect, signalOwnedApplication() {}, cleanupOwnedResources: async () => {},
    candidateIndex: index,
    sourceBindings: new Map([[fact.caseId, {
      runId: 'issue025-source-01', caseId: 'adj-factor-source',
      expectedCoverage: 'observed source response',
      expectedCoverageSource: 'official-source-observation',
    }]]),
  })
  return { run: () => run({}, entry), retained }
}

test('RESPONSE_ONLY sample records checked summary/SQL/page facts and passes when nonempty', async () => {
  const fixture = responseOnlyImperativeFixture(6)
  await fixture.run()
  assert.equal(fixture.retained.status, 'PASS')
  assert.equal(fixture.retained.reviewMethod,
    'verified persisted RESPONSE_ONLY policy/rule, one full-range request and successful leaf, source/write counts, read-only SQL, page outcome, and unconfirmed-completeness notice')
})

test('unexpected empty RESPONSE_ONLY sample keeps task SUCCEEDED facts but remains EVIDENCE_MISSING', async () => {
  const fixture = responseOnlyImperativeFixture(0)
  await fixture.run()
  assert.equal(fixture.retained.status, 'EVIDENCE_MISSING')
  assert.equal(fixture.retained.sourceRowCount, 0)
  assert.equal(fixture.retained.errorCode, null)
})

test('RESPONSE_ONLY runtime SOURCE binding compares reordered params by semantic value', async () => {
  const fixture = responseOnlyImperativeFixture(6, true)
  await fixture.run()
  assert.equal(fixture.retained.status, 'PASS')
})

test('imperative input loading binds parsed bytes and detects a valid replacement at cleanup', async () => {
  const directory = mkdtempSync('/tmp/issue018-t13-input-binding-')
  chmodSync(directory, 0o700)
  const file = path.join(directory, 'plan.json')
  const bytes = Buffer.from(JSON.stringify(validRangePlan()))
  writeFileSync(file, bytes, { mode: 0o600 })
  try {
    const loadInput = harnessFunction('readSafeInput', { process: { env: { ISSUE018_T13_CASES_FILE: file }, getuid: () => process.getuid() }, path, lstatSync, readFileSync, safeCheck: check, bindEvidenceInput: liveContract.bindEvidenceInput })
    const binding = loadInput('ISSUE018_T13_CASES_FILE')
    const originalHash = createHash('sha256').update(bytes).digest('hex')
    assert.equal(binding.sha256, originalHash)
    assert.deepEqual(binding.value, JSON.parse(bytes))
    const indexFile = path.join(directory, 'index.json')
    const indexBytes = Buffer.from(JSON.stringify(initialIndex()))
    writeFileSync(indexFile, indexBytes, { mode: 0o600 })
    const indexBinding = { ...liveContract.bindEvidenceInput(indexBytes), path: indexFile }
    const checkInputs = harnessFunction('evidenceInputsUnchanged', { casePlanInput: binding, evidenceIndexInput: indexBinding, readSafeInput: loadInput })
    assert.equal(await checkInputs(), true)
    writeFileSync(file, JSON.stringify({ ...validRangePlan(), runId: 'replacement-run' }))
    assert.equal(await checkInputs(), false)
    assert.equal(binding.sha256, originalHash)
    assert.equal(binding.value.runId, 'issue018-t13-source-01')
    writeFileSync(file, bytes)
    writeFileSync(indexFile, `${indexBytes.toString()}\n`)
    assert.equal(await checkInputs(), false)
    assert.equal(indexBinding.sha256, createHash('sha256').update(indexBytes).digest('hex'))
  } finally { rmSync(directory, { recursive: true, force: true }) }
})

for (const phase of ['single', 'range']) test(`imperative ${phase} evidence writer records original input fingerprints rather than replacement file hashes`, async () => {
  const evidence = { inputs: {}, downloads: [], queries: [], cleanup: { immutableInputs: false } }
  let written
  const write = harnessFunction('writeSafeEvidence', {
    evidence, evidenceWritten: false, observedTasks: [], runCounters: { snapshot: () => ({}) }, INTERFACES: [],
    manifestSampleCount: 48, liveScope: { sampleCount: 3 }, intervalMs: 2000, sourceRequests: 0,
    sha256: async () => '9'.repeat(64), execFileAsync: async () => ({ stdout: 'synthetic-commit' }),
    publicEnvironment: () => ({}), phase, casePlan: {}, candidateIndex: {},
    casePlanInput: { sha256: '1'.repeat(64) }, evidenceIndexInput: { sha256: '2'.repeat(64) },
    process: { env: { ISSUE018_T13_CASES_FILE: '/tmp/synthetic-plan', ISSUE018_T13_EVIDENCE_INDEX_FILE: '/tmp/synthetic-index' } },
    path, runDirectory: '/tmp/synthetic-run', assertSafeText() {},
    evidenceInteger: liveContract.evidenceInteger,
    writeFile: async (target, bytes) => { written = JSON.parse(bytes) }, chmod: async () => {}, console: { info() {} },
  })
  await write()
  assert.equal(written.inputs.casePlanSha256, '1'.repeat(64))
  assert.equal(written.inputs.evidenceIndexSha256, '2'.repeat(64))
  assert.equal(written.cleanup.immutableInputs, false)
})

test('imperative evidence writer serializes strict DTO BigInt counts as safe JSON integers', async () => {
  const evidence = { inputs: {}, downloads: [], queries: [], cleanup: {} }
  const counts = { totalBatches: 1n, pendingBatches: 0n, runningBatches: 0n,
    succeededBatches: 1n, failedBatches: 0n, splitBatches: 0n, sourceRows: 6n,
    insertedRows: 5n, updatedRows: 1n }
  const observedTasks = [{ taskId: 'task', submissionId: 'submission', apiName: 'daily',
    pluginId: 'tushare_pro', requestId: 'request', observedAt: '2026-09-14T00:00:00Z',
    task: { status: 'SUCCEEDED', requestCount: 1n, counts, lastError: null },
    batches: [{ batchId: 'batch', parentBatchId: null, status: 'SUCCEEDED',
      rangeStart: '2026-09-01', rangeEnd: '2026-09-14', attemptCount: 1,
      sourceRows: 6n, insertedRows: 5n, updatedRows: 1n, error: null }] }]
  let written
  const write = harnessFunction('writeSafeEvidence', {
    evidence, evidenceWritten: false, observedTasks,
    runCounters: { snapshot: () => ({}) }, INTERFACES: [], manifestSampleCount: 48,
    liveScope: { sampleCount: 1 }, intervalMs: 2000, sourceRequests: 1,
    sha256: async () => '9'.repeat(64), execFileAsync: async () => ({ stdout: 'commit' }),
    publicEnvironment: () => ({}), phase: 'range', casePlan: {}, candidateIndex: {},
    casePlanInput: undefined, evidenceIndexInput: undefined, process: { env: {} }, path,
    runDirectory: '/tmp/synthetic-run', assertSafeText() {},
    writeFile: async (target, bytes) => { written = JSON.parse(bytes) },
    chmod: async () => {}, console: { info() {} },
    evidenceInteger: liveContract.evidenceInteger,
  })
  await write()
  assert.equal(written.taskObservations[0].requestCount, 1)
  assert.equal(written.taskObservations[0].counts.sourceRows, 6)
  assert.equal(written.taskObservations[0].batchNodes[0].insertedRows, 5)
  assert.equal(written.totals.sourceRequestsObserved, 1)
})

test('records failure projection rejects unknown codes and mismatched request identity without raw text', () => {
  const context = { pluginId: 'tushare_pro', apiName: 'daily', caseId: 'daily-task', position: 'AFTER', httpStatus: 500, requestId: 'query-id' }
  const body = { requestId: 'query-id', code: 'QUERY_FAILED', message: 'private failure details', cause: { payload: 'raw details' } }
  const result = liveContract.safeRecordsQueryFailure(body, context)
  assert.equal(result.errorCode, 'QUERY_FAILED')
  assert.equal(JSON.stringify(result).includes('details'), false)
  for (const changed of [{ ...body, code: 'arbitrary provider message' }, { ...body, requestId: 'other-id' }]) assert.throws(() => liveContract.safeRecordsQueryFailure(changed, context))
})

for (const phase of ['single', 'range']) test(`imperative ${phase} cleanup marks changed evidence inputs immutable=false and fails the run`, async () => {
  let afterAll, writtenCleanup
  const testApi = Object.assign(() => {}, { use() {}, beforeAll() {}, afterAll: (callback) => { afterAll = callback }, setTimeout() {}, describe: Object.assign((name, callback) => callback(), { configure() {} }) })
  const bytes = Buffer.from('synthetic immutable input')
  const digest = createHash('sha256').update(bytes).digest('hex')
  const evidence = {}
  harnessFunction('registerTests', {
    test: testApi, phase, publicEnvironment: () => ({}), INTERFACES: [],
    bounded: (promise) => promise, drainAllMonitors: async () => {}, stopApplication: async () => {}, budgetTimer: undefined,
    jarValidated: true, artifactInitialized: true, setupFailed: true, runtimeFailure: undefined,
    sha256: async () => digest, jarHashBefore: digest, JAR_SHA: digest, process: { env: { ACCEPTANCE_JAR: '/tmp/synthetic.jar' } },
    createHash, readFile: async () => bytes, manifestPath: 'synthetic manifest', requestsPath: 'synthetic examples', MANIFEST_SHA: digest, REQUESTS_SHA: digest,
    safeCheck: check, verifyAllEvents: async () => {}, evidenceInputsUnchanged: async () => false,
    evidence, canConnectToPort: async () => false, logSink: { failed: false }, writeSafeEvidence: async () => { writtenCleanup = structuredClone(evidence.cleanup) },
  })()
  await assert.rejects(afterAll(), /cleanup failure/)
  assert.equal(writtenCleanup.immutableInputs, false)
})

// Bootstrap navigation: render the real layout entirely in Node, with inert route views.
// This creates no HTTP listener, browser, application backend, or API request.
import { createRequire } from 'node:module'
const requireForVue = createRequire(import.meta.url)
async function renderedNavigation() {
  const { parse, compileScript } = requireForVue('@vue/compiler-sfc')
  const vue = requireForVue('vue'), routerApi = requireForVue('vue-router'), icons = requireForVue('@element-plus/icons-vue')
  const { renderToString } = requireForVue('@vue/server-renderer')
  const { JSDOM } = requireForVue('jsdom')
  const source = readFileSync(new URL('../src/layouts/AppLayout.vue', import.meta.url), 'utf8')
  let compiled = compileScript(parse(source).descriptor, { id: 'offline-navigation', inlineTemplate: true }).content
  const downloadTask = { createDownloadTaskChannel: () => ({}), downloadTaskChannelKey: Symbol('downloadTaskChannel') }
  const modules = { vue, 'vue-router': routerApi, '@element-plus/icons-vue': icons, '../composables/useDownloadTask.js': downloadTask }
  const dependencies = {}
  compiled = compiled.replace(/import\s*\{([^}]+)\}\s*from\s*['"]([^'"]+)['"]/g, (_, names, module) => {
    assert.ok(module in modules)
    const exports = modules[module]
    for (const entry of names.split(',')) {
      const [exported, local = exported] = entry.trim().split(/\s+as\s+/)
      dependencies[local] = exports[exported]
    }
    return ''
  })
  const component = new Function(...Object.keys(dependencies), compiled.replace('export default', 'return'))(...Object.values(dependencies))
  const router = routerApi.createRouter({ history: routerApi.createMemoryHistory(), routes: ['/datasets', '/downloads', '/settings'].map((route) => ({ path: route, name: route.slice(1), component: { render: () => vue.h('div') } })) })
  await router.push('/datasets')
  const dom = new JSDOM(await renderToString(vue.createSSRApp(component).use(router)))
  try {
    return [...dom.window.document.querySelectorAll('nav a')].map((element) => ({ href: element.getAttribute('href'), name: element.textContent.replace(/\s+/g, ' ').trim(), ariaLabel: element.getAttribute('aria-label') }))
  } finally { dom.window.close() }
}

test('actual Studio layout navigation uses plain accessible link names', async () => {
  const links = await renderedNavigation()
  assert.deepEqual(links.slice(0, 2), [
    { href: '/downloads', name: '数据下载', ariaLabel: null },
    { href: '/datasets', name: '数据查看', ariaLabel: null },
  ])
})

test('actual download activation bootstraps once and dataset sources load only on mount', async () => {
  const source = readFileSync(new URL('../src/views/DownloadView.vue', import.meta.url), 'utf8')
  let loads = 0
  const activate = harnessFunction('startView', { mounted: true, viewActive: true, bootstrapped: false, load: async () => { loads += 1 }, taskList: { start: async () => {} } }, source)
  await activate()
  await activate()
  assert.equal(loads, 1, 'KeepAlive reactivation does not issue a fresh sources request')
  const dataset = readFileSync(new URL('../src/views/DatasetView.vue', import.meta.url), 'utf8')
  assert.match(dataset, /onMounted\(loadSources\)/)
  assert.equal(dataset.includes('onActivated'), false)
})

function navigationFixture(links, cached = false) {
  const pending = [], paths = [], chosen = [], checks = []
  let drained = false
  const definition = { pluginId: 'fixture', apiName: 'fixture_daily', columns: [{ name: 'ts_code', label: '证券代码' }] }
  const response = (pathname, body) => ({ status: () => 200, body, url: () => `http://127.0.0.1:8080${pathname}`, request: () => ({ method: () => 'GET' }) })
  const deliver = (pathname, body) => {
    const result = response(pathname, body)
    const waiter = pending.find((wait) => wait.predicate(result))
    assert.ok(waiter, `wait must be armed before ${pathname}`)
    pending.splice(pending.indexOf(waiter), 1)
    waiter.resolve(result)
  }
  const page = {
    waitForResponse: (predicate) => {
      const promise = new Promise((resolve, reject) => pending.push({ predicate, resolve, reject }))
      // Avoid an unhandled rejection when the old code's click fails before awaiting its response.
      void promise.catch(() => {})
      return promise
    },
    goto: async (route) => {
      assert.equal(drained, true, 'response scans drain before navigation')
      paths.push(route)
      deliver('/api/v1/data-sources', [{ pluginId: 'fixture', downloadAvailable: true }])
      return response(route, null)
    },
    getByRole: (role, { name, exact } = {}) => ({
      click: async () => {
        const link = links.find((link) => exact ? link.name === name : link.name.includes(name))
        assert.ok(link, `no actual link has exact name ${name}`)
        if (cached) {
          // One deterministic event boundary reproduces a cached activation with no sources response.
          for (const waiter of pending.splice(0)) waiter.reject(new Error('cached activation did not request sources'))
        }
      },
    }),
    getByLabel: () => ({ inputValue: async () => '' }),
  }
  const monitor = { drain: async () => { drained = true }, records: () => 0 }
  const common = {
    drainAllMonitors: monitor.drain, safeCheck: check, expect: uiExpect,
    assertPageSafe: async (page, stage) => checks.push(stage), safeJson: async (r) => { checks.push(r.url()); return r.body },
    isGet: (r, pathname) => r.request().method() === 'GET' && new URL(r.url()).pathname === pathname,
  }
  const openRoute = harnessFunction('openRoute', common)
  return {
    page, monitor, paths, chosen, checks, definition,
    download: harnessFunction('openDownloadFromDataset', { ...common, openRoute, chooseDownload: async (page, pluginId, contract) => chosen.push([pluginId, contract.apiName]) }),
    dataset: harnessFunction('openDataset', { ...common, openRoute, SOURCE_COLUMNS: ['source_plugin', 'source_api', 'ingested_at'], FIXTURE_OPTION: /fixture_daily/, FILTER_LABELS: { ts_code: ['证券代码 (ts_code)'] }, doubleAnimationFrame: async () => {},
      selectOption: async (page, label) => label === '数据源'
        ? deliver('/api/v1/data-sources/fixture/datasets', [{ apiName: 'fixture_daily' }])
        : deliver('/api/v1/data-sources/fixture/datasets/fixture_daily', definition),
    }),
  }
}

test('imperative download entrance opens a fresh checked route with Studio navigation links', async () => {
  const fixture = navigationFixture(await renderedNavigation())
  await fixture.download(fixture.page, 'fixture', { apiName: 'fixture_daily' })
  assert.deepEqual(fixture.paths, ['/downloads'])
  assert.deepEqual(fixture.chosen, [['fixture', 'fixture_daily']])
  assert.ok(fixture.checks.includes('http://127.0.0.1:8080/api/v1/data-sources'))
  assert.ok(fixture.checks.includes('page route'))
})

test('imperative return to cached dataset still requires fresh sources, list and definition', async () => {
  // Grant a matching link name to isolate KeepAlive from the independent numbered-link failure.
  const fixture = navigationFixture([{ href: '/datasets', name: '数据查看' }], true)
  const result = await fixture.dataset(fixture.page, fixture.monitor, 'fixture', { apiName: 'fixture_daily', columns: 1 }, true)
  assert.equal(result, fixture.definition)
  assert.deepEqual(fixture.paths, ['/datasets'])
  assert.deepEqual(fixture.checks.filter((entry) => entry.startsWith('http:')), [
    'http://127.0.0.1:8080/api/v1/data-sources',
    'http://127.0.0.1:8080/api/v1/data-sources/fixture/datasets',
    'http://127.0.0.1:8080/api/v1/data-sources/fixture/datasets/fixture_daily',
  ])
})
