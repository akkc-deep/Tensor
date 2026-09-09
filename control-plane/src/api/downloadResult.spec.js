import rangeResults from '../test/fixtures/range-results.json'

import { isDownloadResult, isRecoveryScope } from './downloadResult.js'

function changed(source, changes) {
  return Object.assign(structuredClone(source), changes)
}

describe('isDownloadResult', () => {
  it('exports the existing four-field recovery scope guard for retry-task reuse', () => {
    expect(isRecoveryScope({
      targetType: 'REQUEST', targetValue: '', timeType: 'DATE', timeValue: '2026-09-03',
    })).toBe(true)
    expect(isRecoveryScope({
      targetType: 'REQUEST', targetValue: '', timeType: 'DATE', timeValue: '2026-02-29',
    })).toBe(false)
    expect(isRecoveryScope({
      targetType: 'REQUEST', targetValue: '', timeType: 'DATE', timeValue: '2026-09-03', extra: true,
    })).toBe(false)
  })
  it('accepts the five complete HTTP 200 outcomes and preserves null as an error-only count', () => {
    for (const outcome of ['SUCCESS', 'EMPTY', 'NO_OPEN_DATES', 'PARTIAL', 'FAILED']) {
      expect(isDownloadResult(rangeResults[outcome])).toBe(true)
    }
    expect(isDownloadResult(rangeResults.UNCONFIRMED)).toBe(false)
    expect(isDownloadResult(rangeResults.UNCONFIRMED, { allowUnconfirmed: true })).toBe(true)
    expect(changed(rangeResults.UNCONFIRMED, { notStartedUnits: 0 })).not.toEqual(rangeResults.UNCONFIRMED)
    expect(isDownloadResult(changed(rangeResults.UNCONFIRMED, { notStartedUnits: 0 }), { allowUnconfirmed: true })).toBe(true)
  })

  it('requires exactly the 18 result fields and strict scalar identities', () => {
    const { message, ...missing } = rangeResults.SUCCESS
    const invalid = [
      missing,
      { ...rangeResults.SUCCESS, extra: true },
      changed(rangeResults.SUCCESS, { requestId: ' ' }),
      changed(rangeResults.SUCCESS, { message: '' }),
      changed(rangeResults.SUCCESS, { pluginId: 'Tushare' }),
      changed(rangeResults.SUCCESS, { pluginId: 'a' }),
      changed(rangeResults.SUCCESS, { pluginId: ['tushare_pro'] }),
      changed(rangeResults.SUCCESS, { pluginId: { value: 'tushare_pro' } }),
      changed(rangeResults.SUCCESS, { apiName: 'daily-feed' }),
      changed(rangeResults.SUCCESS, { apiName: ['daily'] }),
      changed(rangeResults.SUCCESS, { apiName: 123 }),
      changed(rangeResults.SUCCESS, { failureRecordStatus: 'SAVED' }),
    ]

    for (const value of invalid) expect(isDownloadResult(value)).toBe(false)
  })

  it('rejects missing, coerced, negative, fractional and unsafe counts', () => {
    const requiredCounts = [
      'sourceRowCount',
      'insertedRows',
      'updatedRows',
      'completedUnits',
      'failedUnits',
      'notStartedUnits',
      'skippedClosedDates',
      'remainingFailedUnits',
    ]
    for (const field of requiredCounts) {
      const snapshot = structuredClone(rangeResults.UNCONFIRMED)
      delete snapshot[field]
      expect(isDownloadResult(snapshot, { allowUnconfirmed: true })).toBe(false)
    }

    for (const field of requiredCounts) {
      for (const value of ['0', -1, 0.5, Number.MAX_SAFE_INTEGER + 1, Number.NaN]) {
        const snapshot = changed(rangeResults.UNCONFIRMED, { [field]: value })
        expect(isDownloadResult(snapshot, { allowUnconfirmed: true })).toBe(false)
      }
    }
  })

  it('allows null only for not-started and remaining counts', () => {
    expect(isDownloadResult(rangeResults.UNCONFIRMED, { allowUnconfirmed: true })).toBe(true)
    expect(isDownloadResult(changed(rangeResults.UNCONFIRMED, { sourceRowCount: null }), { allowUnconfirmed: true })).toBe(false)
    expect(isDownloadResult(changed(rangeResults.UNCONFIRMED, { completedUnits: null }), { allowUnconfirmed: true })).toBe(false)
    expect(isDownloadResult(changed(rangeResults.UNCONFIRMED, { skippedClosedDates: null }), { allowUnconfirmed: true })).toBe(false)
  })

  it('validates exact recovery scopes and strict calendar values', () => {
    const validScopes = [
      { targetType: 'STOCK', targetValue: '000001.SZ', timeType: 'DATE', timeValue: '2024-02-29' },
      { targetType: 'REQUEST', targetValue: '', timeType: 'MONTH', timeValue: '2026-09' },
      { targetType: 'REQUEST', targetValue: '', timeType: 'RANGE', timeValue: '2026-09-03/2026-09-07' },
      { targetType: 'REQUEST', targetValue: '', timeType: 'NONE', timeValue: '' },
    ]
    const valid = changed(rangeResults.UNCONFIRMED, {
      notStartedScopes: validScopes,
      unconfirmedScopes: validScopes,
    })
    expect(isDownloadResult(valid, { allowUnconfirmed: true })).toBe(true)

    const invalidScopes = [
      { ...validScopes[0], extra: true },
      { ...validScopes[0], targetType: 'REQUEST' },
      { ...validScopes[0], targetValue: '000001.sz' },
      { ...validScopes[0], targetValue: '000001.SZ,000002.SZ' },
      { ...validScopes[0], timeValue: '2026-02-29' },
      { ...validScopes[1], timeValue: '2026-13' },
      { ...validScopes[2], timeValue: '2026-09-07/2026-09-03' },
      { ...validScopes[2], timeValue: '2026-09-03/2026-09-03' },
      { ...validScopes[3], timeValue: 'latest' },
    ]
    for (const scope of invalidScopes) {
      const snapshot = changed(rangeResults.UNCONFIRMED, { notStartedScopes: [scope] })
      expect(isDownloadResult(snapshot, { allowUnconfirmed: true })).toBe(false)
    }
  })

  it('validates failure fields while allowing historical non-catalogue codes', () => {
    const historical = changed(rangeResults.UNCONFIRMED, {
      failures: [{
        targetType: 'STOCK',
        targetValue: '000001.SZ',
        timeType: 'DATE',
        timeValue: '2026-09-03',
        errorCode: 'HISTORICAL_CODE',
        errorMessage: '历史原因',
      }],
    })
    expect(isDownloadResult(historical, { allowUnconfirmed: true })).toBe(true)

    for (const failure of [
      { ...historical.failures[0], extra: true },
      { ...historical.failures[0], errorCode: '' },
      { ...historical.failures[0], errorCode: 'x'.repeat(65) },
      { ...historical.failures[0], errorMessage: ' ' },
      { ...historical.failures[0], errorMessage: 'x'.repeat(513) },
    ]) {
      const snapshot = changed(historical, { failures: [failure] })
      expect(isDownloadResult(snapshot, { allowUnconfirmed: true })).toBe(false)
    }
  })

  it('enforces outcome count semantics without inventing count relationships', () => {
    const invalid = [
      changed(rangeResults.SUCCESS, { completedUnits: 0, sourceRowCount: 0, insertedRows: 0, updatedRows: 0 }),
      changed(rangeResults.SUCCESS, { sourceRowCount: 0 }),
      changed(rangeResults.SUCCESS, { failedUnits: 1 }),
      changed(rangeResults.EMPTY, { completedUnits: 0 }),
      changed(rangeResults.EMPTY, { sourceRowCount: 1 }),
      changed(rangeResults.EMPTY, { insertedRows: 1 }),
      changed(rangeResults.NO_OPEN_DATES, { skippedClosedDates: 0 }),
      changed(rangeResults.NO_OPEN_DATES, { completedUnits: 1 }),
      changed(rangeResults.PARTIAL, { completedUnits: 0, sourceRowCount: 0, insertedRows: 0, updatedRows: 0 }),
      changed(rangeResults.PARTIAL, { failedUnits: 0 }),
      changed(rangeResults.FAILED, { completedUnits: 1 }),
      changed(rangeResults.FAILED, { failedUnits: 0 }),
      changed(rangeResults.FAILED, { sourceRowCount: 1 }),
    ]
    for (const value of invalid) expect(isDownloadResult(value)).toBe(false)

    expect(isDownloadResult(changed(rangeResults.PARTIAL, {
      sourceRowCount: 0,
      insertedRows: 0,
      updatedRows: 0,
    }))).toBe(true)
    expect(isDownloadResult(changed(rangeResults.UNCONFIRMED, {
      failedUnits: 1,
      remainingFailedUnits: 2,
    }), { allowUnconfirmed: true })).toBe(true)
  })

  it('enforces the completed-zero row invariant for every outcome', () => {
    for (const field of ['sourceRowCount', 'insertedRows', 'updatedRows']) {
      expect(isDownloadResult(changed(rangeResults.FAILED, { [field]: 1 }))).toBe(false)
    }
  })

  it('enforces the HTTP 200 completion and confirmed-record boundary', () => {
    for (const outcome of ['SUCCESS', 'EMPTY', 'NO_OPEN_DATES']) {
      expect(isDownloadResult(changed(rangeResults[outcome], {
        taskId: '33333333-3333-4333-8333-333333333333',
      }))).toBe(false)
      expect(isDownloadResult(changed(rangeResults[outcome], { remainingFailedUnits: 1 }))).toBe(false)
    }

    for (const outcome of ['PARTIAL', 'FAILED']) {
      const source = rangeResults[outcome]
      expect(isDownloadResult(changed(source, { taskId: null }))).toBe(false)
      expect(isDownloadResult(changed(source, { taskId: 'not-a-uuid' }))).toBe(false)
      expect(isDownloadResult(changed(source, { taskId: [source.taskId] }))).toBe(false)
      expect(isDownloadResult(changed(source, { taskId: { value: source.taskId } }))).toBe(false)
      expect(isDownloadResult(changed(source, { remainingFailedUnits: 0 }))).toBe(false)
      expect(isDownloadResult(changed(source, { remainingFailedUnits: null }))).toBe(false)
      expect(isDownloadResult(changed(source, { failureRecordStatus: 'UNCONFIRMED' }))).toBe(false)
      expect(isDownloadResult(changed(source, { failures: [] }))).toBe(false)
    }

    for (const changes of [
      { notStartedUnits: null },
      { notStartedUnits: 1 },
      { notStartedScopes: [{ targetType: 'REQUEST', targetValue: '', timeType: 'DATE', timeValue: '2026-09-04' }] },
      { unconfirmedScopes: [{ targetType: 'REQUEST', targetValue: '', timeType: 'DATE', timeValue: '2026-09-04' }] },
    ]) {
      expect(isDownloadResult(changed(rangeResults.SUCCESS, changes))).toBe(false)
    }
  })

  it('keeps task, failure and remaining counts independent in unconfirmed snapshots', () => {
    expect(isDownloadResult(changed(rangeResults.UNCONFIRMED, {
      taskId: null,
      failedUnits: 1,
      failures: [],
      remainingFailedUnits: 2,
    }), { allowUnconfirmed: true })).toBe(true)
    expect(isDownloadResult(changed(rangeResults.UNCONFIRMED, {
      taskId: '33333333-3333-4333-8333-333333333333',
      failedUnits: 0,
      remainingFailedUnits: 0,
    }), { allowUnconfirmed: true })).toBe(true)
  })
})
