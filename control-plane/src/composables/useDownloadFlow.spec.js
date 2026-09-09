import { ApiError, ClientError } from '../api/errors.js'

const api = vi.hoisted(() => ({
  listDataSources: vi.fn(),
  listApis: vi.fn(),
  downloadDataset: vi.fn(),
}))

vi.mock('../api/dataSources.js', () => ({
  listDataSources: api.listDataSources,
  listApis: api.listApis,
}))

vi.mock('../api/downloads.js', () => ({
  downloadDataset: api.downloadDataset,
}))

import { useDownloadFlow } from './useDownloadFlow.js'
import results from '../test/fixtures/range-results.json'
import rangeApis from '../test/fixtures/range-apis.json'

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function source(overrides = {}) {
  return {
    pluginId: 'fixture',
    displayName: 'Fixture',
    description: 'Fixture 数据源',
    enabled: true,
    credentialConfigured: true,
    downloadAvailable: true,
    unavailableReason: null,
    ...overrides,
  }
}

function descriptor(overrides = {}) {
  return { ...rangeApis.apis.find((api) => api.apiName === 'daily'), ...overrides }
}

function response(overrides = {}) {
  return { ...structuredClone(results[overrides.outcome ?? 'SUCCESS']), pluginId: 'fixture', ...overrides }
}

async function readyFlow({ sources = [source()], apis = [descriptor()] } = {}) {
  api.listDataSources.mockResolvedValueOnce(sources)
  api.listApis.mockResolvedValueOnce(apis)
  const flow = useDownloadFlow()
  expect(await flow.load()).toBe(true)
  expect(await flow.selectSource(sources[0].pluginId)).toBe(true)
  expect(flow.selectApi(apis[0].apiName)).toBe(true)
  return flow
}

beforeEach(() => {
  vi.resetAllMocks()
})

describe('useDownloadFlow', () => {
  it('loads sources explicitly and replaces all prior page state', async () => {
    const firstSources = [source()]
    const firstApis = [descriptor()]
    const completed = response()
    api.listDataSources.mockResolvedValueOnce(firstSources)
    api.listApis.mockResolvedValueOnce(firstApis)
    api.downloadDataset.mockResolvedValueOnce(completed)
    const flow = useDownloadFlow()

    expect(flow.state.value).toBe('INITIAL')
    expect(flow.sources.value).toEqual([])
    expect(flow.apis.value).toEqual([])
    expect(flow.selectedPluginId.value).toBe('')
    expect(flow.selectedApiName.value).toBe('')
    expect(flow.result.value).toBeNull()
    expect(flow.error.value).toBeNull()
    expect(api.listDataSources).not.toHaveBeenCalled()

    expect(await flow.load()).toBe(true)
    expect(await flow.selectSource('fixture')).toBe(true)
    expect(flow.selectApi('daily')).toBe(true)
    expect(await flow.submit({ start_date: '20260904', end_date: '20260904' })).toBe(true)
    expect(flow.result.value).toBe(completed)

    const nextSources = [source({ pluginId: 'replacement' })]
    const pending = deferred()
    api.listDataSources.mockReturnValueOnce(pending.promise)
    const loading = flow.load()

    expect(flow.state.value).toBe('METADATA_LOADING')
    expect(flow.sources.value).toEqual([])
    expect(flow.apis.value).toEqual([])
    expect(flow.selectedPluginId.value).toBe('')
    expect(flow.selectedApiName.value).toBe('')
    expect(flow.result.value).toBeNull()
    expect(flow.error.value).toBeNull()
    pending.resolve(nextSources)
    expect(await loading).toBe(true)
    expect(flow.state.value).toBe('READY')
    expect(flow.sources.value).toBe(nextSources)
    expect(api.listDataSources).toHaveBeenCalledTimes(2)
  })

  it('loads APIs for accepted sources and derives only valid selectable descriptors', async () => {
    const available = source()
    const unavailable = source({
      pluginId: 'disabled',
      displayName: 'Disabled',
      downloadAvailable: false,
      unavailableReason: '未配置凭证',
    })
    const daily = descriptor()
    const disabledApi = descriptor({ apiName: 'weekly', displayName: '周线行情' })
    api.listDataSources.mockResolvedValueOnce([available, unavailable])
    api.listApis
      .mockResolvedValueOnce([daily])
      .mockResolvedValueOnce([disabledApi])
    const flow = useDownloadFlow()

    expect(await flow.load()).toBe(true)
    const selecting = flow.selectSource('fixture')
    expect(flow.state.value).toBe('METADATA_LOADING')
    expect(flow.selectedPluginId.value).toBe('fixture')
    expect(flow.selectedApiName.value).toBe('')
    expect(await selecting).toBe(true)
    expect(flow.selectedSource.value).toBe(available)
    expect(flow.selectedApi.value).toBeNull()
    expect(flow.canSubmit.value).toBe(false)

    expect(flow.selectApi('daily')).toBe(true)
    expect(flow.selectedApi.value).toBe(daily)
    expect(flow.canSubmit.value).toBe(true)
    expect(flow.selectApi('unknown')).toBe(true)
    expect(flow.selectedApi.value).toBeNull()
    expect(flow.canSubmit.value).toBe(false)

    expect(await flow.selectSource('disabled')).toBe(true)
    expect(flow.selectApi('weekly')).toBe(true)
    expect(flow.selectedSource.value).toBe(unavailable)
    expect(flow.selectedApi.value).toBe(disabledApi)
    expect(flow.canSubmit.value).toBe(false)
    expect(api.listApis.mock.calls).toEqual([['fixture'], ['disabled']])
  })

  it('retains safe metadata errors and retries only retryable failed operations', async () => {
    const sources = [source()]
    const apis = [descriptor()]
    const sourceError = new ClientError('NETWORK', 'source-request')
    const apiError = new ClientError('TIMEOUT', 'api-request')
    const finalError = new ClientError('UNEXPECTED', 'final-request')
    api.listDataSources
      .mockRejectedValueOnce(sourceError)
      .mockResolvedValueOnce(sources)
      .mockRejectedValueOnce(finalError)
    api.listApis.mockRejectedValueOnce(apiError).mockResolvedValueOnce(apis)
    const flow = useDownloadFlow()

    expect(await flow.load()).toBe(false)
    expect(flow.state.value).toBe('FAILURE')
    expect(flow.error.value).toBe(sourceError)
    expect(flow.canRetry.value).toBe(true)
    expect(api.listDataSources).toHaveBeenCalledTimes(1)
    expect(await flow.retry()).toBe(true)
    expect(flow.state.value).toBe('READY')
    expect(flow.error.value).toBeNull()
    expect(flow.sources.value).toBe(sources)

    expect(await flow.selectSource('fixture')).toBe(false)
    expect(flow.error.value).toBe(apiError)
    expect(flow.canRetry.value).toBe(true)
    expect(await flow.retry()).toBe(true)
    expect(flow.state.value).toBe('READY')
    expect(flow.apis.value).toBe(apis)
    expect(api.listApis.mock.calls).toEqual([['fixture'], ['fixture']])

    expect(await flow.load()).toBe(false)
    expect(flow.error.value).toBe(finalError)
    expect(flow.canRetry.value).toBe(false)
    expect(await flow.retry()).toBe(false)
    expect(api.listDataSources).toHaveBeenCalledTimes(3)
  })

  it('ignores stale API successes and failures after a newer source selection', async () => {
    const sources = [
      source({ pluginId: 'first' }),
      source({ pluginId: 'second' }),
    ]
    const firstSuccess = deferred()
    const secondSuccess = deferred()
    const staleFailure = deferred()
    const currentSuccess = deferred()
    api.listDataSources.mockResolvedValueOnce(sources)
    api.listApis
      .mockReturnValueOnce(firstSuccess.promise)
      .mockReturnValueOnce(secondSuccess.promise)
      .mockReturnValueOnce(staleFailure.promise)
      .mockReturnValueOnce(currentSuccess.promise)
    const flow = useDownloadFlow()
    await flow.load()

    const staleSuccessResult = flow.selectSource('first')
    const secondResult = flow.selectSource('second')
    const secondApis = [descriptor({ apiName: 'second_daily' })]
    secondSuccess.resolve(secondApis)
    expect(await secondResult).toBe(true)
    firstSuccess.resolve([descriptor({ apiName: 'stale_daily' })])
    expect(await staleSuccessResult).toBe(false)
    expect(flow.selectedPluginId.value).toBe('second')
    expect(flow.apis.value).toBe(secondApis)
    expect(flow.state.value).toBe('READY')

    const staleFailureResult = flow.selectSource('first')
    const currentResult = flow.selectSource('second')
    const currentApis = [descriptor({ apiName: 'second_weekly' })]
    currentSuccess.resolve(currentApis)
    expect(await currentResult).toBe(true)
    staleFailure.reject(new ClientError('NETWORK', 'stale-request'))
    expect(await staleFailureResult).toBe(false)
    expect(flow.selectedPluginId.value).toBe('second')
    expect(flow.apis.value).toBe(currentApis)
    expect(flow.error.value).toBeNull()
    expect(flow.canRetry.value).toBe(false)
    expect(flow.state.value).toBe('READY')
  })

  it('clears download state and retry context when the source or API changes', async () => {
    const sources = [source()]
    const daily = descriptor()
    const weekly = descriptor({ apiName: 'weekly', displayName: '周线行情' })
    const originalSources = structuredClone(sources)
    const failure = new ClientError('NETWORK', 'download-request')
    const flow = await readyFlow({ sources, apis: [daily, weekly] })
    api.downloadDataset.mockRejectedValueOnce(failure)

    expect(await flow.submit({ start_date: '20260904', end_date: '20260904' })).toBe(false)
    expect(flow.error.value).toBe(failure)
    expect(flow.canRetry.value).toBe(false)
    expect(flow.selectApi('weekly')).toBe(true)
    expect(flow.result.value).toBeNull()
    expect(flow.error.value).toBeNull()
    expect(flow.canRetry.value).toBe(false)
    expect(flow.selectApi('daily')).toBe(true)
    expect(await flow.retry()).toBe(false)
    expect(api.downloadDataset).toHaveBeenCalledTimes(1)

    const completed = response()
    api.downloadDataset.mockResolvedValueOnce(completed)
    expect(await flow.submit({ start_date: '20260905', end_date: '20260905' })).toBe(true)
    expect(flow.result.value).toBe(completed)
    const apiCalls = api.listApis.mock.calls.length
    expect(await flow.selectSource('')).toBe(true)
    expect(flow.selectedPluginId.value).toBe('')
    expect(flow.apis.value).toEqual([])
    expect(flow.selectedApiName.value).toBe('')
    expect(flow.result.value).toBeNull()
    expect(flow.error.value).toBeNull()
    expect(api.listApis).toHaveBeenCalledTimes(apiCalls)
    expect(flow.sources.value).toBe(sources)
    expect(sources).toEqual(originalSources)
  })

  it('clears prior result and failed retry context when parameters change', async () => {
    const failure = new ClientError('NETWORK', 'download-request')
    const flow = await readyFlow()
    api.downloadDataset.mockRejectedValueOnce(failure)

    expect(await flow.submit({ start_date: '20260904', end_date: '20260904' })).toBe(false)
    expect(flow.canRetry.value).toBe(false)
    expect(flow.parametersChanged()).toBe(true)
    expect(flow.state.value).toBe('READY')
    expect(flow.result.value).toBeNull()
    expect(flow.error.value).toBeNull()
    expect(flow.canRetry.value).toBe(false)
    expect(await flow.retry()).toBe(false)
    expect(api.downloadDataset).toHaveBeenCalledTimes(1)

    const completed = response()
    api.downloadDataset.mockResolvedValueOnce(completed)
    expect(await flow.submit({ start_date: '20260905', end_date: '20260905' })).toBe(true)
    expect(flow.result.value).toBe(completed)
    expect(flow.parametersChanged()).toBe(true)
    expect(flow.state.value).toBe('READY')
    expect(flow.result.value).toBeNull()
  })

  it('submits a frozen request snapshot and rejects every action while locked', async () => {
    const flow = await readyFlow()
    const pending = deferred()
    api.downloadDataset.mockReturnValueOnce(pending.promise)
    const params = { start_date: '20260904', end_date: '20260904' }
    const submitting = flow.submit(params)

    expect(flow.state.value).toBe('SUBMITTING')
    expect(flow.locked.value).toBe(true)
    expect(flow.executionContext.value.rangeMode).toBe(true)
    expect(flow.parametersChanged()).toBe(false)
    expect(flow.canSubmit.value).toBe(false)
    expect(api.downloadDataset).toHaveBeenCalledWith({
      pluginId: 'fixture',
      apiName: 'daily',
      params: { start_date: '20260904', end_date: '20260904' },
    })
    expect(api.downloadDataset.mock.calls[0][0].params).not.toBe(params)
    params.start_date = 'changed'
    expect(api.downloadDataset.mock.calls[0][0].params).toEqual({
      start_date: '20260904', end_date: '20260904',
    })

    expect(await flow.load()).toBe(false)
    expect(await flow.selectSource('other')).toBe(false)
    expect(flow.selectApi('other')).toBe(false)
    expect(await flow.submit({ start_date: '20260905', end_date: '20260905' })).toBe(false)
    expect(await flow.retry()).toBe(false)
    expect(flow.state.value).toBe('SUBMITTING')
    expect(flow.selectedPluginId.value).toBe('fixture')
    expect(flow.selectedApiName.value).toBe('daily')
    expect(api.listDataSources).toHaveBeenCalledTimes(1)
    expect(api.listApis).toHaveBeenCalledTimes(1)
    expect(api.downloadDataset).toHaveBeenCalledTimes(1)

    pending.resolve(response())
    expect(await submitting).toBe(true)
    expect(flow.locked.value).toBe(false)
    expect(flow.state.value).toBe('SUCCESS')
  })

  it.each(['SUCCESS', 'EMPTY', 'NO_OPEN_DATES', 'PARTIAL', 'FAILED'])('preserves the complete %s response without reclassifying or recounting it', async (outcome) => {
    const flow = await readyFlow()
    const completed = response({ outcome })
    api.downloadDataset.mockResolvedValueOnce(completed)
    expect(await flow.submit({ start_date: '20260901', end_date: '20260910' })).toBe(true)
    expect(flow.state.value).toBe(outcome)
    expect(flow.result.value).toBe(completed)
    expect(flow.error.value).toBeNull()
  })

  it.each(['TIMEOUT', 'NETWORK', 'INVALID_RESPONSE', 'UNEXPECTED'])('leaves %s unknown, ends only the local lock and requires a new explicit submission', async (kind) => {
    const flow = await readyFlow()
    const failure = new ClientError(kind, 'first-request')
    const params = { start_date: '20260901', end_date: '20260910' }
    api.downloadDataset.mockRejectedValueOnce(failure).mockResolvedValueOnce(response({ requestId: 'new-request' }))
    expect(await flow.submit(params)).toBe(false)
    expect(flow.state.value).toBe('UNCONFIRMED')
    expect(flow.locked.value).toBe(false)
    expect(flow.canSubmit.value).toBe(true)
    expect(flow.canRetry.value).toBe(false)
    expect(flow.result.value).toBeNull()
    expect(flow.error.value).toBe(failure)
    expect(flow.executionContext.value).toEqual({ pluginId: 'fixture', apiName: 'daily', params, rangeMode: true })
    params.start_date = '20260905'
    expect(flow.executionContext.value.params.start_date).toBe('20260901')
    expect(await flow.retry()).toBe(false)
    expect(api.downloadDataset).toHaveBeenCalledOnce()
    expect(await flow.submit(params)).toBe(true)
    expect(api.downloadDataset).toHaveBeenCalledTimes(2)
    expect(flow.result.value.requestId).toBe('new-request')
    expect(flow.executionContext.value.params.start_date).toBe('20260905')
  })

  it.each(['TASK_RECORD_SAVE_UNCONFIRMED', 'COMMIT_UNCONFIRMED', 'PERSISTENCE_FAILED', 'INTERNAL_ERROR'])('preserves the confirmed snapshot for %s without a DOWNLOAD retry', async (code) => {
    const flow = await readyFlow()
    const failure = new ApiError({ requestId: 'range-unconfirmed', code, message: '结果未确认', retryable: code === 'PERSISTENCE_FAILED', fieldErrors: [], downloadResult: response({ outcome: 'UNCONFIRMED' }) })
    api.downloadDataset.mockRejectedValueOnce(failure)
    await flow.submit({ start_date: '20260901', end_date: '20260910' })
    expect(flow.state.value).toBe('UNCONFIRMED')
    expect(flow.result.value).toBe(failure.downloadResult)
    expect(flow.result.value).toMatchObject({ completedUnits: 2, failedUnits: 1, notStartedUnits: null, sourceRowCount: 10, insertedRows: 7, updatedRows: 3 })
    expect(flow.error.value).toBe(failure)
    expect(await flow.retry()).toBe(false)
    expect(api.downloadDataset).toHaveBeenCalledOnce()
  })

  it.each([{ pluginId: 'wrong_plugin' }, { apiName: 'wrong_api' }])('rejects a stopped snapshot with mismatched identity %j', async (mismatch) => {
    const flow = await readyFlow()
    api.downloadDataset.mockRejectedValueOnce(new ApiError({ requestId: 'range-unconfirmed', code: 'COMMIT_UNCONFIRMED', message: '安全错误', retryable: false, fieldErrors: [], downloadResult: response({ outcome: 'UNCONFIRMED', ...mismatch }) }))
    await flow.submit({ start_date: '20260901', end_date: '20260910' })
    expect(flow.state.value).toBe('UNCONFIRMED')
    expect(flow.result.value).toBeNull()
    expect(flow.error.value).toBeInstanceOf(ClientError)
    expect(flow.error.value).toMatchObject({ kind: 'INVALID_RESPONSE', requestId: 'range-unconfirmed' })
  })

  it('shows a rejected busy request without a snapshot and does not retry even when retryable', async () => {
    const flow = await readyFlow()
    const failure = new ApiError({ requestId: 'busy-request', code: 'DOWNLOAD_BUSY', message: '已有下载正在执行', retryable: true, fieldErrors: [] })
    api.downloadDataset.mockRejectedValueOnce(failure)
    await flow.submit({ start_date: '20260901', end_date: '20260910' })
    expect(flow.state.value).toBe('FAILURE')
    expect(flow.result.value).toBeNull()
    expect(flow.error.value).toBe(failure)
    expect(flow.canRetry.value).toBe(false)
    expect(await flow.retry()).toBe(false)
    expect(api.downloadDataset).toHaveBeenCalledOnce()
  })

  it.each(['load', 'selectSource', 'selectApi', 'parametersChanged'])('clears the entire execution context through %s', async (operation) => {
    const flow = await readyFlow()
    api.downloadDataset.mockResolvedValueOnce(response({ outcome: 'PARTIAL' }))
    await flow.submit({ start_date: '20260901', end_date: '20260910' })
    expect(flow.executionContext.value).not.toBeNull()
    api.listDataSources.mockResolvedValueOnce([])
    await flow[operation](operation === 'selectSource' ? '' : 'daily')
    expect(flow.executionContext.value).toBeNull()
    expect(flow.result.value).toBeNull()
    expect(flow.error.value).toBeNull()
    expect(flow.canRetry.value).toBe(false)
    expect(api.downloadDataset).toHaveBeenCalledOnce()
  })
})
