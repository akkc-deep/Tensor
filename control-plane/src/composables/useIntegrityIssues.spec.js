import { effectScope } from 'vue'
import * as api from '../api/integrityChecks.js'
import { useIntegrityIssues } from './useIntegrityIssues.js'

vi.mock('../api/integrityChecks.js', async (original) => ({
  ...(await original()), listIntegrityIssues: vi.fn(),
}))

const CHECK_A = '11111111-1111-4111-8111-111111111111'
const CHECK_B = '22222222-2222-4222-8222-222222222222'
const RESULT_A = '33333333-3333-4333-8333-333333333333'
const RESULT_B = '44444444-4444-4444-8444-444444444444'
const emptyPage = (page = 1, pageSize = 20) => ({ page, pageSize, total: 0n, items: [] })
function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

beforeEach(() => {
  vi.resetAllMocks()
  api.listIntegrityIssues.mockResolvedValue(emptyPage())
})

it('loads a selected result with validated defaults and exposes read-only criteria', async () => {
  const flow = useIntegrityIssues()
  await flow.load(CHECK_A, { resultId: RESULT_A, type: 'MISSING' })
  expect(api.listIntegrityIssues).toHaveBeenCalledWith(CHECK_A, {
    page: 1, pageSize: 20, resultId: RESULT_A, type: 'MISSING',
  }, expect.objectContaining({ signal: expect.any(AbortSignal) }))
  expect(flow.checkId.value).toBe(CHECK_A)
  expect(flow.criteria.value).toEqual({ page: 1, pageSize: 20, resultId: RESULT_A, type: 'MISSING' })
  expect(flow.page.value).toEqual(emptyPage())
})

it('clears an old page and aborts when applied criteria change', async () => {
  const flow = useIntegrityIssues()
  await flow.load(CHECK_A, { resultId: RESULT_A })
  const slow = deferred()
  api.listIntegrityIssues.mockReturnValueOnce(slow.promise).mockResolvedValueOnce(emptyPage(2))
  const old = flow.load(CHECK_A, { page: 1, pageSize: 20, resultId: RESULT_A, status: 'FAIL' })
  const oldSignal = api.listIntegrityIssues.mock.calls[1][2].signal
  const current = flow.load(CHECK_A, { page: 2, pageSize: 20, resultId: RESULT_A, status: 'FAIL' })
  expect(oldSignal.aborted).toBe(true)
  expect(flow.page.value).toBeNull()
  slow.resolve(emptyPage()); await old; await current
  expect(flow.page.value.page).toBe(2)
  expect(flow.loading.value).toBe(false)
})

it('keeps the same-condition snapshot when refresh fails', async () => {
  const flow = useIntegrityIssues(), saved = { ...emptyPage(), total: 1n, items: [{ issueId: 1n }] }
  api.listIntegrityIssues.mockResolvedValueOnce(saved)
  await flow.load(CHECK_A, { resultId: RESULT_A })
  const failure = Object.assign(new Error('offline'), { requestId: 'issues-request' })
  api.listIntegrityIssues.mockRejectedValueOnce(failure)
  await flow.refresh()
  expect(flow.page.value).toBe(saved)
  expect(flow.error.value).toBe(failure)
})

it('ignores late success, error, and finally after switching task and result', async () => {
  const flow = useIntegrityIssues(), old = deferred(), current = deferred()
  api.listIntegrityIssues.mockReturnValueOnce(old.promise).mockReturnValueOnce(current.promise)
  const first = flow.load(CHECK_A, { resultId: RESULT_A })
  const second = flow.load(CHECK_B, { resultId: RESULT_B })
  old.reject(Object.assign(new Error('late'), { requestId: 'old-request' })); await first
  expect(flow.loading.value).toBe(true)
  expect(flow.error.value).toBeNull()
  current.resolve(emptyPage()); await second
  expect(flow.checkId.value).toBe(CHECK_B)
  expect(flow.criteria.value.resultId).toBe(RESULT_B)
})

it('rejects invalid date ranges locally without issuing a GET', async () => {
  const flow = useIntegrityIssues()
  await expect(flow.load(CHECK_A, { resultId: RESULT_A, dateFrom: '2026-02-30' })).rejects.toBeInstanceOf(TypeError)
  expect(api.listIntegrityIssues).not.toHaveBeenCalled()
})

it('aborts and prevents late writes on dispose and Vue scope disposal', async () => {
  const scope = effectScope(), pending = deferred()
  api.listIntegrityIssues.mockReturnValueOnce(pending.promise)
  let flow
  scope.run(() => { flow = useIntegrityIssues() })
  const request = flow.load(CHECK_A, { resultId: RESULT_A })
  const signal = api.listIntegrityIssues.mock.calls[0][2].signal
  scope.stop()
  expect(signal.aborted).toBe(true)
  pending.reject(new Error('late')); await request
  expect(flow.error.value).toBeNull()
  expect(flow.loading.value).toBe(false)
})
