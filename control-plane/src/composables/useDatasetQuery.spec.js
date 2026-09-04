import { ClientError } from '../api/errors.js'

const api = vi.hoisted(() => ({ queryDataset: vi.fn() }))

vi.mock('../api/datasets.js', () => ({
  queryDataset: api.queryDataset,
}))

import { useDatasetQuery } from './useDatasetQuery.js'

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function pageResponse(overrides = {}) {
  return {
    requestId: 'request-1',
    pluginId: 'fixture',
    apiName: 'daily',
    page: 1,
    pageSize: 50,
    totalElements: 1,
    totalPages: 1,
    columns: ['ts_code'],
    items: [{ ts_code: '000001.SZ' }],
    ...overrides,
  }
}

beforeEach(() => {
  vi.resetAllMocks()
})

describe('useDatasetQuery', () => {
  it('starts unqueried and submits an immutable first-page request without auto-querying', async () => {
    const flow = useDatasetQuery()
    expect(flow.state.value).toBe('UNQUERIED')
    expect(flow.result.value).toBeNull()
    expect(flow.error.value).toBeNull()
    expect(flow.page.value).toBe(1)
    expect(flow.pageSize.value).toBe(50)
    expect(flow.loading.value).toBe(false)
    expect(flow.canRetry.value).toBe(false)
    expect(api.queryDataset).not.toHaveBeenCalled()

    const pending = deferred()
    api.queryDataset.mockReturnValueOnce(pending.promise)
    const criteria = { tsCode: '000001.SZ' }
    const querying = flow.query('fixture', 'daily', criteria)

    expect(flow.state.value).toBe('LOADING')
    expect(flow.loading.value).toBe(true)
    expect(flow.page.value).toBe(1)
    expect(flow.pageSize.value).toBe(50)
    expect(api.queryDataset).toHaveBeenCalledWith('fixture', 'daily', {
      tsCode: '000001.SZ', page: 1, pageSize: 50,
    })
    criteria.tsCode = 'changed'
    expect(api.queryDataset.mock.calls[0][2].tsCode).toBe('000001.SZ')

    pending.resolve(pageResponse())
    expect(await querying).toBe(true)
  })

  it('clears old results and preserves current success and empty responses by server totals', async () => {
    const first = pageResponse()
    api.queryDataset.mockResolvedValueOnce(first)
    const flow = useDatasetQuery()
    expect(await flow.query('fixture', 'daily', {})).toBe(true)
    expect(flow.state.value).toBe('SUCCESS')
    expect(flow.result.value).toBe(first)

    const pending = deferred()
    const empty = pageResponse({
      requestId: 'request-2', apiName: 'weekly', totalElements: 0,
      totalPages: 0, items: [],
    })
    api.queryDataset.mockReturnValueOnce(pending.promise)
    const querying = flow.query('fixture', 'weekly', { tradeDateFrom: '2026-09-01' })
    expect(flow.state.value).toBe('LOADING')
    expect(flow.result.value).toBeNull()
    expect(flow.error.value).toBeNull()

    pending.resolve(empty)
    expect(await querying).toBe(true)
    expect(flow.state.value).toBe('EMPTY')
    expect(flow.result.value).toBe(empty)
    expect(flow.page.value).toBe(1)
    expect(flow.pageSize.value).toBe(50)
  })

  it('retains safe failures without automatic retry and derives retryability', async () => {
    const retryable = new ClientError('NETWORK', 'request-network')
    const finalFailure = new ClientError('UNEXPECTED', 'request-final')
    api.queryDataset
      .mockRejectedValueOnce(retryable)
      .mockRejectedValueOnce(finalFailure)

    const retryableFlow = useDatasetQuery()
    expect(await retryableFlow.query('fixture', 'daily', {})).toBe(false)
    expect(retryableFlow.state.value).toBe('FAILURE')
    expect(retryableFlow.result.value).toBeNull()
    expect(retryableFlow.error.value).toBe(retryable)
    expect(retryableFlow.canRetry.value).toBe(true)
    expect(api.queryDataset).toHaveBeenCalledTimes(1)

    const finalFlow = useDatasetQuery()
    expect(await finalFlow.query('fixture', 'weekly', {})).toBe(false)
    expect(finalFlow.error.value).toBe(finalFailure)
    expect(finalFlow.canRetry.value).toBe(false)
    expect(await finalFlow.retry()).toBe(false)
    expect(api.queryDataset).toHaveBeenCalledTimes(2)
  })

  it('retries the exact failed request snapshot and adopts the recovered server page', async () => {
    const flow = useDatasetQuery()
    api.queryDataset.mockResolvedValueOnce(pageResponse())
    expect(await flow.query('fixture', 'daily', { tsCode: '000001.SZ' })).toBe(true)

    api.queryDataset.mockResolvedValueOnce(pageResponse({ pageSize: 20 }))
    expect(await flow.changePageSize(20)).toBe(true)

    const failure = new ClientError('TIMEOUT', 'request-timeout')
    api.queryDataset.mockRejectedValueOnce(failure)
    expect(await flow.changePage(3)).toBe(false)
    expect(flow.page.value).toBe(3)
    expect(flow.pageSize.value).toBe(20)
    expect(flow.canRetry.value).toBe(true)

    const recovered = pageResponse({ page: 2, pageSize: 20, totalElements: 21, totalPages: 2 })
    api.queryDataset.mockResolvedValueOnce(recovered)
    expect(await flow.retry()).toBe(true)
    expect(api.queryDataset.mock.calls.slice(-2)).toEqual([
      ['fixture', 'daily', { tsCode: '000001.SZ', page: 3, pageSize: 20 }],
      ['fixture', 'daily', { tsCode: '000001.SZ', page: 3, pageSize: 20 }],
    ])
    expect(flow.result.value).toBe(recovered)
    expect(flow.page.value).toBe(2)
    expect(flow.canRetry.value).toBe(false)

    api.queryDataset.mockRejectedValueOnce(failure)
    expect(await flow.changePage(1)).toBe(false)
    const replacement = deferred()
    api.queryDataset.mockReturnValueOnce(replacement.promise)
    const replacing = flow.query('fixture', 'weekly', {})
    const calls = api.queryDataset.mock.calls.length
    expect(await flow.retry()).toBe(false)
    expect(api.queryDataset).toHaveBeenCalledTimes(calls)
    replacement.resolve(pageResponse({ apiName: 'weekly', pageSize: 20 }))
    expect(await replacing).toBe(true)
  })

  it('ignores stale successes and failures after a newer query or reset', async () => {
    const flow = useDatasetQuery()
    const staleSuccess = deferred()
    const currentSuccess = deferred()
    api.queryDataset
      .mockReturnValueOnce(staleSuccess.promise)
      .mockReturnValueOnce(currentSuccess.promise)

    const stale = flow.query('fixture', 'daily', { tsCode: 'old' })
    const current = flow.query('fixture', 'weekly', { tsCode: 'new' })
    const currentResponse = pageResponse({ requestId: 'current', apiName: 'weekly' })
    currentSuccess.resolve(currentResponse)
    expect(await current).toBe(true)
    staleSuccess.resolve(pageResponse({ requestId: 'stale' }))
    expect(await stale).toBe(false)
    expect(flow.result.value).toBe(currentResponse)

    const staleFailure = deferred()
    const newestSuccess = deferred()
    api.queryDataset
      .mockReturnValueOnce(staleFailure.promise)
      .mockReturnValueOnce(newestSuccess.promise)
    const rejected = flow.query('fixture', 'daily', {})
    const newest = flow.query('fixture', 'weekly', {})
    const newestResponse = pageResponse({ requestId: 'newest', apiName: 'weekly' })
    newestSuccess.resolve(newestResponse)
    expect(await newest).toBe(true)
    staleFailure.reject(new ClientError('NETWORK', 'stale-error'))
    expect(await rejected).toBe(false)
    expect(flow.result.value).toBe(newestResponse)
    expect(flow.error.value).toBeNull()

    const resetSuccess = deferred()
    api.queryDataset.mockReturnValueOnce(resetSuccess.promise)
    const afterReset = flow.query('fixture', 'daily', {})
    flow.reset()
    resetSuccess.resolve(pageResponse({ requestId: 'after-reset' }))
    expect(await afterReset).toBe(false)
    expect(flow.state.value).toBe('UNQUERIED')
    expect(flow.result.value).toBeNull()

    const resetFailure = deferred()
    api.queryDataset.mockReturnValueOnce(resetFailure.promise)
    const rejectedAfterReset = flow.query('fixture', 'daily', {})
    flow.reset()
    resetFailure.reject(new ClientError('NETWORK', 'after-reset-error'))
    expect(await rejectedAfterReset).toBe(false)
    expect(flow.state.value).toBe('UNQUERIED')
    expect(flow.error.value).toBeNull()
  })

  it('preserves query context across pages and accepts the normalized server page', async () => {
    const flow = useDatasetQuery()
    api.queryDataset.mockResolvedValueOnce(pageResponse({ totalElements: 40, totalPages: 2 }))
    expect(await flow.query('fixture', 'daily', { annDateFrom: '2026-09-01' })).toBe(true)

    const normalized = pageResponse({ page: 2, totalElements: 40, totalPages: 2 })
    api.queryDataset.mockResolvedValueOnce(normalized)
    const moving = flow.changePage(3)
    expect(flow.page.value).toBe(3)
    expect(api.queryDataset).toHaveBeenLastCalledWith('fixture', 'daily', {
      annDateFrom: '2026-09-01', page: 3, pageSize: 50,
    })
    expect(await moving).toBe(true)
    expect(flow.page.value).toBe(2)
    expect(flow.result.value).toBe(normalized)

    expect(await flow.changePage(2)).toBe(false)
    expect(api.queryDataset).toHaveBeenCalledTimes(2)
    api.queryDataset.mockResolvedValueOnce(pageResponse({ page: 1, totalElements: 40, totalPages: 2 }))
    expect(await flow.changePage(1)).toBe(true)
    expect(api.queryDataset).toHaveBeenLastCalledWith('fixture', 'daily', {
      annDateFrom: '2026-09-01', page: 1, pageSize: 50,
    })
  })

  it('changes page size from success or empty and blocks pagination while loading', async () => {
    const flow = useDatasetQuery()
    api.queryDataset.mockResolvedValueOnce(pageResponse())
    await flow.query('fixture', 'daily', { tsCode: '000001.SZ' })
    api.queryDataset.mockResolvedValueOnce(pageResponse({ pageSize: 20 }))
    expect(await flow.changePageSize(20)).toBe(true)
    expect(api.queryDataset).toHaveBeenLastCalledWith('fixture', 'daily', {
      tsCode: '000001.SZ', page: 1, pageSize: 20,
    })
    expect(await flow.changePageSize(20)).toBe(false)

    const emptyFlow = useDatasetQuery()
    api.queryDataset.mockResolvedValueOnce(pageResponse({
      apiName: 'weekly', totalElements: 0, totalPages: 0, items: [],
    }))
    await emptyFlow.query('fixture', 'weekly', {})
    api.queryDataset.mockResolvedValueOnce(pageResponse({
      apiName: 'weekly', pageSize: 100, totalElements: 0, totalPages: 0, items: [],
    }))
    expect(await emptyFlow.changePageSize(100)).toBe(true)
    expect(api.queryDataset).toHaveBeenLastCalledWith('fixture', 'weekly', {
      page: 1, pageSize: 100,
    })

    const pending = deferred()
    api.queryDataset.mockReturnValueOnce(pending.promise)
    const loading = emptyFlow.query('fixture', 'weekly', { tradeDateTo: '2026-09-05' })
    const calls = api.queryDataset.mock.calls.length
    expect(await emptyFlow.changePage(2)).toBe(false)
    expect(await emptyFlow.changePageSize(20)).toBe(false)
    expect(await emptyFlow.retry()).toBe(false)
    expect(api.queryDataset).toHaveBeenCalledTimes(calls)
    pending.resolve(pageResponse({ apiName: 'weekly', pageSize: 100 }))
    expect(await loading).toBe(true)
  })

  it('keeps page size for a new filter query and reset restores all defaults', async () => {
    const flow = useDatasetQuery()
    api.queryDataset.mockResolvedValueOnce(pageResponse())
    await flow.query('fixture', 'daily', {})
    api.queryDataset.mockResolvedValueOnce(pageResponse({ pageSize: 20 }))
    await flow.changePageSize(20)
    api.queryDataset.mockResolvedValueOnce(pageResponse({
      page: 2, pageSize: 20, totalElements: 21, totalPages: 2,
    }))
    await flow.changePage(2)

    const filtered = pageResponse({ page: 1, pageSize: 20 })
    api.queryDataset.mockResolvedValueOnce(filtered)
    expect(await flow.query('fixture', 'daily', { tsCode: '000002.SZ' })).toBe(true)
    expect(api.queryDataset).toHaveBeenLastCalledWith('fixture', 'daily', {
      tsCode: '000002.SZ', page: 1, pageSize: 20,
    })
    expect(flow.page.value).toBe(1)
    expect(flow.pageSize.value).toBe(20)

    const calls = api.queryDataset.mock.calls.length
    flow.reset()
    expect(flow.state.value).toBe('UNQUERIED')
    expect(flow.result.value).toBeNull()
    expect(flow.error.value).toBeNull()
    expect(flow.page.value).toBe(1)
    expect(flow.pageSize.value).toBe(50)
    expect(flow.loading.value).toBe(false)
    expect(flow.canRetry.value).toBe(false)
    expect(await flow.changePage(2)).toBe(false)
    expect(api.queryDataset).toHaveBeenCalledTimes(calls)
  })
})
