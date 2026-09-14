const RESPONSE_ONLY_DECISION = 'docs/issues/proposals/ISSUE-025-extraction-contracts.md#决策记录'

const product = (left, right, join = (a, b) => `${a}${b}`) =>
  left.flatMap((a) => right.map((b) => join(a, b)))

const identities = (group, runId, suffixes) => suffixes.map((suffix) => ({
  group,
  runId,
  caseId: `${runId}-${suffix}`,
}))

const issue019 = [
  ...identities('ISSUE-019', 'issue019-source-20260913T122126Z', [
    ...product(['daily-000001-', 'daily-600000-'], ['whole', 'lower', 'upper', 'overlap']),
    ...product(['forecast-000001-', 'forecast-600000-', 'forecast-000005-'],
      ['whole', 'event-at-lower', 'event-at-upper', 'event']),
    ...['whole', 'event', 'event-at-lower', 'event-at-upper'].map((suffix) =>
      `dividend-000001-${suffix}`),
  ]),
  ...identities('ISSUE-019', 'issue019-dividend-source-20260913T122801Z',
    ['whole', 'event', 'event-at-lower', 'event-at-upper']),
]

const issue020 = [
  ...identities('ISSUE-020', 'issue020-boundary-source-20260913T131859Z',
    ['000001-whole', '600000-whole']),
  ...identities('ISSUE-020', 'issue020-source-20260913T131245Z', [
    ...product(['000001-', '600000-'], ['half', 'annual', 'event-at-upper', 'event-at-lower']),
    '000001-whole', '000001-both-boundaries',
  ]),
]

const issue021 = [
  ...identities('ISSUE-021', 'issue021-source-20260913T135006Z', [
    ...product(['trade_cal-sse-', 'trade_cal-szse-'],
      ['whole', 'lower', 'upper', 'closed', 'cross-year', 'current']),
    ...product(['margin-sse-', 'margin-szse-', 'margin-bse-'], ['whole', 'lower', 'upper']),
    ...product(['top_list-000007-', 'top_list-600318-'],
      ['whole', 'event', 'event-at-lower', 'event-at-upper', 'closed']),
  ]),
  ...identities('ISSUE-021', 'issue021-bj-source-20260913T135704Z', [
    'calendar-sse', 'calendar-szse',
    '920008-whole', '920008-event', '920008-event-at-lower',
    '920008-event-at-upper', '920008-closed',
  ]),
]

const issue022 = [
  ...identities('ISSUE-022', 'issue022-source-20260913T141544Z', [
    'cal-week-SSE', 'cal-week-SZSE', 'cal-month-SSE', 'cal-month-SZSE',
    ...product(['weekly-000001-', 'weekly-600000-'], ['whole', 'lower', 'upper', 'holiday-last']),
    ...product(['monthly-000001-', 'monthly-600000-'], ['whole', 'lower', 'upper']),
    ...product(['indicator-000001-', 'indicator-600000-'], ['whole', 'lower', 'upper']),
    ...['whole', 'event', 'lower-edge', 'upper-edge'].map((suffix) => `holder-000001-${suffix}`),
    'holder-600000-whole', 'ipo-whole', 'ipo-lower', 'ipo-upper',
  ]),
  ...identities('ISSUE-022', 'issue022-holder-source-20260913T142200Z',
    ['holder-600000-event', 'holder-600000-lower-edge', 'holder-600000-upper-edge']),
]

const issue023 = [
  ...identities('ISSUE-023', 'issue023-source-20260913T144308Z', [
    'block-official', 'holder-official', 'pledge-official', 'disclosure-official',
    ...product(['block-000001-', 'block-600000-'], ['whole', 'event', 'lower-edge', 'upper-edge']),
  ]),
  ...identities('ISSUE-023', 'issue023-events-source-20260913T145005Z', [
    ...product(['holder-000001-', 'holder-600000-'], ['whole', 'event', 'lower-edge', 'upper-edge']),
    'disclosure-000001-revision-window', 'disclosure-600000-revision-window',
  ]),
  ...identities('ISSUE-023', 'issue023-boundary-source-20260913T145716Z', [
    'block-official-event', 'pledge-000014-lower', 'pledge-000014-upper',
    ...['whole', 'event', 'lower-edge', 'upper-edge'].map((suffix) => `pledge-600000-${suffix}`),
    ...product(['disclosure-000001-', 'disclosure-600000-'],
      ['event', 'lower-edge', 'upper-edge']),
  ]),
]

const issue024 = [
  ...identities('ISSUE-024', 'issue024-source-20260913T153146Z', [
    'len-whole', 'len-lower', 'len-upper',
    'slb_sec-000001-whole', 'slb_sec-000001-official-day',
    'slb_sec-600000-whole', 'slb_sec-600000-official-day',
    'slb_sec_detail-000001-whole', 'slb_sec_detail-000001-official-day',
    'slb_sec_detail-600000-whole', 'slb_sec_detail-600000-official-day',
    'detail-000001-long', 'detail-600000-long', 'slb_len-all-suspension',
    'slb_len-all-settlement', 'slb_sec-000001-suspension', 'slb_sec-600000-suspension',
    'slb_sec_detail-000001-suspension', 'slb_sec_detail-600000-suspension',
  ]),
  ...identities('ISSUE-024', 'issue024-boundary-source-20260913T153639Z', [
    ...product(['slb_sec-000001-', 'slb_sec-600000-'],
      ['lower', 'lower-edge', '20240710', '20240711']),
    ...product(['slb_sec_detail-000001-', 'slb_sec_detail-600000-'],
      ['lower', 'lower-edge', '20240710']),
  ]),
]

const issue025 = [
  ...identities('ISSUE-025', 'issue025-source-20260913T162759Z', [
    ...product([
      'adj_factor-', 'income-', 'balancesheet-', 'cashflow-', 'fina_audit-',
      'top10_holders-', 'top10_floatholders-', 'stk_managers-',
    ], ['000001-whole', '600000-whole']),
    'express-600000-whole', 'suspend_d-000001-whole', 'suspend_d-600000-whole',
    'repurchase-all-whole', 'suspend_d-000029-whole', 'suspend_d-600310-whole',
  ]),
  ...identities('ISSUE-025', 'issue025-boundaries-20260913T163148Z', [
    ...product(['adj_factor-000001-', 'adj_factor-600000-'], ['lower', 'upper', 'after']),
    ...product([
      'income-000001-', 'income-600000-', 'balancesheet-000001-', 'balancesheet-600000-',
      'cashflow-000001-', 'cashflow-600000-', 'top10_holders-000001-',
      'top10_holders-600000-', 'top10_floatholders-000001-',
      'top10_floatholders-600000-', 'stk_managers-000001-', 'stk_managers-600000-',
    ], ['bounded', 'lower', 'upper']),
    'fina_audit-000001-bounded', 'fina_audit-600000-bounded', 'express-600000-bounded',
    ...product(['suspend_d-000001-', 'suspend_d-600000-'], ['bounded', 'lower', 'upper']),
    'repurchase-all-lower', 'repurchase-all-upper', 'repurchase-all-before',
    'repurchase-all-after',
    ...product(['suspend_d-000029-', 'suspend_d-600310-'], ['lower', 'upper']),
    'suspend_d-000001-consecutive', 'suspend_d-600000-consecutive',
  ]),
  ...identities('ISSUE-025', 'issue025-express-20260913T163418Z',
    ['whole', 'day', 'lower-window', 'upper-window']),
]

const issue029 = identities('ISSUE-029', 'issue026-mainbz-split-source-20260914T013736Z',
  ['000001-annual', '600000-annual', '000001-wide', '600000-wide'])

export const ISSUE030_SOURCE_GROUPS = Object.freeze({
  'ISSUE-019': issue019,
  'ISSUE-020': issue020,
  'ISSUE-021': issue021,
  'ISSUE-022': issue022,
  'ISSUE-023': issue023,
  'ISSUE-024': issue024,
  'ISSUE-025': issue025,
  'ISSUE-029': issue029,
})

export const ISSUE030_GROUP_COUNTS = Object.freeze({
  'ISSUE-019': 28,
  'ISSUE-020': 12,
  'ISSUE-021': 38,
  'ISSUE-022': 35,
  'ISSUE-023': 35,
  'ISSUE-024': 33,
  'ISSUE-025': 87,
  'ISSUE-029': 4,
})

export const ISSUE030_SOURCE_INPUTS = Object.freeze(Object.values(ISSUE030_SOURCE_GROUPS).flat())

const rowLimits = {
  daily: 6000, forecast: 3500, dividend: 2000, fina_mainbz: 100, margin: 4000,
  top_list: 10000, weekly: 6000, monthly: 4500, fina_indicator: 100,
  stk_holdernumber: 3000, new_share: 2000, block_trade: 1000,
  disclosure_date: 6000, stk_holdertrade: 3000, pledge_detail: 1000,
  slb_len: 5000, slb_sec: 5000, slb_sec_detail: 5000,
}

const decisions = {
  daily: 'docs/issues/proposals/ISSUE-019-documented-range-limits.md#决策记录',
  forecast: 'docs/issues/proposals/ISSUE-019-documented-range-limits.md#决策记录',
  dividend: 'docs/issues/proposals/ISSUE-019-documented-range-limits.md#决策记录',
  fina_mainbz: 'docs/issues/proposals/ISSUE-020-fina-mainbz-default-type.md#决策记录',
  trade_cal: 'docs/task-designs/ISSUE-021-design.md',
  margin: 'docs/task-designs/ISSUE-021-design.md',
  top_list: 'docs/task-designs/ISSUE-021-design.md',
  weekly: 'docs/task-designs/ISSUE-022-design.md',
  monthly: 'docs/task-designs/ISSUE-022-design.md',
  fina_indicator: 'docs/task-designs/ISSUE-022-design.md',
  stk_holdernumber: 'docs/task-designs/ISSUE-022-design.md',
  new_share: 'docs/task-designs/ISSUE-022-design.md',
  block_trade: 'docs/task-designs/ISSUE-023-design.md',
  disclosure_date: 'docs/task-designs/ISSUE-023-design.md',
  stk_holdertrade: 'docs/task-designs/ISSUE-023-design.md',
  pledge_detail: 'docs/task-designs/ISSUE-023-design.md#质押非空样本决定2026-09-13',
  slb_len: 'docs/issues/proposals/ISSUE-024-historical-support.md#决策记录',
  slb_sec: 'docs/issues/proposals/ISSUE-024-historical-support.md#决策记录',
  slb_sec_detail: 'docs/issues/proposals/ISSUE-024-historical-support.md#决策记录',
  ...Object.fromEntries([
    'adj_factor', 'suspend_d', 'income', 'balancesheet', 'cashflow', 'fina_audit',
    'express', 'repurchase', 'stk_managers', 'top10_holders', 'top10_floatholders',
  ].map((apiName) => [apiName, RESPONSE_ONLY_DECISION])),
}

const preservedEvidenceRefs = {
  daily: ['docs/task-designs/ISSUE-019-design.md'],
  forecast: ['docs/task-designs/ISSUE-019-design.md'],
  dividend: ['docs/task-designs/ISSUE-019-design.md'],
  fina_mainbz: ['docs/verification/ISSUE-018-T14-runs.md#issue-020-分类与限量独立取证'],
  trade_cal: [
    'docs/task-designs/ISSUE-018-T13-design.md#40项实施矩阵',
    '.superpowers/sdd/2026-09-12-issue-018-t13/task-4-candidate-assessment.md#有效事实最多的后续候选素材',
    'docs/verification/ISSUE-018-T14-runs.md#issue-021-日历交易所与交易日独立取证',
  ],
  margin: ['docs/verification/ISSUE-018-T14-runs.md#issue-021-日历交易所与交易日独立取证'],
  top_list: ['docs/verification/ISSUE-018-T14-runs.md#issue-021-日历交易所与交易日独立取证'],
}

export const ISSUE030_CANDIDATE_APIS = Object.freeze(Object.keys(decisions))

function unique(values) {
  return [...new Set(values)]
}

function indexedCases(index) {
  return new Map(index.runs.flatMap((run) => run.cases.map((evidence) => [
    `${run.runId}/${evidence.caseId}`,
    { run, evidence },
  ])))
}

function candidateCaseId(identity) {
  if (identity.group === 'ISSUE-022') {
    return `issue026-issue022-${identity.caseId.slice(identity.runId.length + 1)}`
  }
  if (['ISSUE-023', 'ISSUE-024', 'ISSUE-025'].includes(identity.group)) {
    return `issue026-${identity.caseId}`
  }
  return `issue026-task-${identity.caseId}`
}

function candidateUnresolved(apiName) {
  const values = ['RANGE_TASK_NOT_RUN', 'RANGE_SQL_NOT_VERIFIED']
  if (apiName === 'daily') values.push('RANGE_OVERLAP_UPDATE_NOT_VERIFIED')
  if (apiName === 'fina_mainbz') values.push('RANGE_SPLIT_NOT_VERIFIED')
  if (apiName === 'disclosure_date') values.push('RANGE_UPDATE_NOT_VERIFIED')
  if (['slb_len', 'slb_sec', 'slb_sec_detail'].includes(apiName)) {
    values.push('HISTORICAL_RETENTION_GUARANTEE_UNKNOWN')
  }
  if (decisions[apiName] === RESPONSE_ONLY_DECISION) {
    values.push('UPSTREAM_COMPLETENESS_UNCONFIRMED')
  }
  return values
}

export function applyIssue030CandidateIndex(input) {
  const index = structuredClone(input)
  const byIdentity = indexedCases(index)
  const byCaseId = new Map(index.runs.flatMap((run) => run.cases.map((evidence) => [
    evidence.caseId,
    { run, evidence },
  ])))
  const sourceCases = new Map(ISSUE030_CANDIDATE_APIS.map((apiName) => [apiName, []]))
  for (const identity of ISSUE030_SOURCE_INPUTS) {
    const found = byIdentity.get(`${identity.runId}/${identity.caseId}`)
    if (!found || found.run.exitCode !== 0 || found.run.cleanup.status !== 'PASS'
      || found.evidence.phase !== 'SOURCE' || found.evidence.mode !== 'RANGE'
      || found.evidence.status !== 'PASS' || !sourceCases.has(found.evidence.apiName)) {
      throw new Error(`Invalid ISSUE-030 SOURCE identity: ${identity.runId}/${identity.caseId}`)
    }
    sourceCases.get(found.evidence.apiName).push(found.evidence.caseId)
  }
  index.schemaVersion = 2
  for (const entry of index.interfaces.filter(({ apiName }) => decisions[apiName])) {
    const decisionRef = decisions[entry.apiName]
    const singleTasks = entry.cases.filter((caseId) => {
      const found = byCaseId.get(caseId)
      return found?.run.exitCode === 0 && found.run.cleanup.status === 'PASS'
        && found.evidence.phase === 'TASK' && found.evidence.mode === 'SINGLE'
        && found.evidence.status === 'PASS'
    })
    entry.completeness = {
      kind: entry.apiName === 'trade_cal' ? 'CALENDAR_COVERAGE'
        : Object.hasOwn(rowLimits, entry.apiName) ? 'ROW_LIMIT' : 'RESPONSE_ONLY',
      rowLimit: rowLimits[entry.apiName] ?? null,
      evidenceRefs: unique([
        ...entry.completeness.evidenceRefs,
        ...(preservedEvidenceRefs[entry.apiName] ?? []),
        decisionRef,
        `docs/verification/ISSUE-018-range-acceptance.md#${entry.apiName}`,
        entry.officialUrl,
      ]),
    }
    entry.sourceStatus = 'PASS'
    entry.taskStatus = singleTasks.length === 0 ? 'NOT_RUN' : 'PASS'
    entry.disposition = 'NEEDS_VERIFICATION'
    entry.policyVersion = 'tushare-range-v2'
    entry.cases = [...sourceCases.get(entry.apiName), ...singleTasks]
    entry.unresolved = candidateUnresolved(entry.apiName)
    entry.decisionRef = decisionRef
  }
  return index
}

export function buildIssue030CandidatePlan(index) {
  const byIdentity = indexedCases(index)
  const interfaces = new Map(index.interfaces.map((entry) => [entry.apiName, entry]))
  return {
    runId: 'issue026-candidate-inputs',
    cases: ISSUE030_SOURCE_INPUTS.map((identity) => {
      const found = byIdentity.get(`${identity.runId}/${identity.caseId}`)
      if (!found) throw new Error(`Missing ISSUE-030 SOURCE identity: ${identity.runId}/${identity.caseId}`)
      const entry = interfaces.get(found.evidence.apiName)
      return {
        caseId: candidateCaseId(identity),
        apiName: found.evidence.apiName,
        mode: 'RANGE',
        params: structuredClone(found.evidence.params),
        dateAxis: found.evidence.dateAxis,
        start: found.evidence.params.start_date,
        end: found.evidence.params.end_date,
        evidenceRefs: [
          ...entry.completeness.evidenceRefs,
          `docs/verification/ISSUE-018-range-acceptance.json#${identity.runId}/${identity.caseId}`,
        ],
      }
    }),
  }
}
