import { ClientError } from '../api/errors.js'

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
  return {
    apiName: 'daily',
    displayName: '日线行情',
    category: '行情与估值',
    queryMode: 'trade_date',
    parameters: [
      {
        name: 'trade_date',
        label: '交易日期',
        type: 'DATE',
        required: true,
      },
    ],
    ...overrides,
  }
}

function response(overrides = {}) {
  return {
    requestId: 'request-1',
    outcome: 'SUCCESS',
    pluginId: 'fixture',
    apiName: 'daily',
    sourceRowCount: 1,
    insertedRows: 1,
    updatedRows: 0,
    message: '下载完成',
    ...overrides,
  }
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
    expect(await flow.submit({ trade_date: '20260904' })).toBe(true)
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

    expect(await flow.submit({ trade_date: '20260904' })).toBe(false)
    expect(flow.error.value).toBe(failure)
    expect(flow.canRetry.value).toBe(true)
    expect(flow.selectApi('weekly')).toBe(true)
    expect(flow.result.value).toBeNull()
    expect(flow.error.value).toBeNull()
    expect(flow.canRetry.value).toBe(false)
    expect(flow.selectApi('daily')).toBe(true)
    expect(await flow.retry()).toBe(false)
    expect(api.downloadDataset).toHaveBeenCalledTimes(1)

    const completed = response()
    api.downloadDataset.mockResolvedValueOnce(completed)
    expect(await flow.submit({ trade_date: '20260905' })).toBe(true)
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

  it('submits a frozen request snapshot and rejects every action while locked', async () => {
    const flow = await readyFlow()
    const pending = deferred()
    api.downloadDataset.mockReturnValueOnce(pending.promise)
    const params = { trade_date: '20260904' }
    const submitting = flow.submit(params)

    expect(flow.state.value).toBe('SUBMITTING')
    expect(flow.locked.value).toBe(true)
    expect(flow.canSubmit.value).toBe(false)
    expect(api.downloadDataset).toHaveBeenCalledWith({
      pluginId: 'fixture',
      apiName: 'daily',
      params: { trade_date: '20260904' },
    })
    expect(api.downloadDataset.mock.calls[0][0].params).not.toBe(params)
    params.trade_date = 'changed'
    expect(api.downloadDataset.mock.calls[0][0].params).toEqual({
      trade_date: '20260904',
    })

    expect(await flow.load()).toBe(false)
    expect(await flow.selectSource('other')).toBe(false)
    expect(flow.selectApi('other')).toBe(false)
    expect(await flow.submit({ trade_date: '20260905' })).toBe(false)
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

  it('maps successful responses only by outcome and preserves each response', async () => {
    const flow = await readyFlow()
    const success = response({
      sourceRowCount: 0,
      insertedRows: 0,
      message: '计数不参与状态判断',
    })
    const empty = response({
      requestId: 'request-2',
      outcome: 'EMPTY',
      sourceRowCount: 2,
      insertedRows: 2,
      message: '结果只服从 outcome',
    })
    api.downloadDataset.mockResolvedValueOnce(success).mockResolvedValueOnce(empty)

    expect(await flow.submit({ trade_date: '20260904' })).toBe(true)
    expect(flow.state.value).toBe('SUCCESS')
    expect(flow.result.value).toBe(success)
    expect(flow.error.value).toBeNull()

    expect(await flow.submit({ trade_date: '20260905' })).toBe(true)
    expect(flow.state.value).toBe('EMPTY')
    expect(flow.result.value).toBe(empty)
    expect(flow.error.value).toBeNull()
  })

  it('retries download failures with frozen inputs and rejects unsafe retry paths', async () => {
    const flow = await readyFlow({
      apis: [descriptor(), descriptor({ apiName: 'weekly' })],
    })
    const retryable = new ClientError('NETWORK', 'download-request')
    const nonRetryable = new ClientError('UNEXPECTED', 'unsafe-request')
    const params = { trade_date: '20260904' }
    api.downloadDataset
      .mockRejectedValueOnce(retryable)
      .mockResolvedValueOnce(response())
      .mockRejectedValueOnce(nonRetryable)
      .mockRejectedValueOnce(retryable)

    expect(await flow.submit(params)).toBe(false)
    expect(flow.state.value).toBe('FAILURE')
    expect(flow.locked.value).toBe(false)
    expect(flow.canSubmit.value).toBe(true)
    expect(flow.error.value).toBe(retryable)
    expect(flow.canRetry.value).toBe(true)
    expect(api.downloadDataset).toHaveBeenCalledTimes(1)

    params.trade_date = 'changed'
    expect(await flow.retry()).toBe(true)
    expect(api.downloadDataset.mock.calls[1][0]).toEqual({
      pluginId: 'fixture',
      apiName: 'daily',
      params: { trade_date: '20260904' },
    })
    expect(api.downloadDataset.mock.calls[1][0].params).not.toBe(
      api.downloadDataset.mock.calls[0][0].params,
    )
    expect(flow.state.value).toBe('SUCCESS')

    expect(await flow.submit({ trade_date: '20260905' })).toBe(false)
    expect(flow.error.value).toBe(nonRetryable)
    expect(flow.canRetry.value).toBe(false)
    expect(await flow.retry()).toBe(false)
    expect(api.downloadDataset).toHaveBeenCalledTimes(3)

    expect(await flow.submit({ trade_date: '20260906' })).toBe(false)
    expect(flow.canRetry.value).toBe(true)
    expect(flow.selectApi('weekly')).toBe(true)
    expect(flow.canRetry.value).toBe(false)
    expect(await flow.retry()).toBe(false)
    expect(api.downloadDataset).toHaveBeenCalledTimes(4)
  })
})
