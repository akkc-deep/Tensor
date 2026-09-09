import { ApiError, ClientError } from '../api/errors.js'
import retryTasks from '../test/fixtures/retry-tasks.json'
import rangeResults from '../test/fixtures/range-results.json'
import { ref } from 'vue'

const api = vi.hoisted(() => ({
  listRetryTasks: vi.fn(),
  getRetryTask: vi.fn(),
  executeRetryTask: vi.fn(),
}))

vi.mock('../api/retryTasks.js', () => api)

import { useRetryTaskFlow } from './useRetryTaskFlow.js'

const TASK_ID = retryTasks.detail.taskId
const OTHER_ID = retryTasks.stocks.taskId

function deferred() {
  let resolve
  let reject
  const promise = new Promise((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}

function serverError(code, downloadResult = null) {
  return new ApiError({
    requestId: 'server-request', code,
    message: code === 'RETRY_TASK_NOT_FOUND' ? '当前失败任务不存在，请刷新列表' : '服务器拒绝',
    retryable: ['DOWNLOAD_BUSY', 'QUERY_FAILED'].includes(code), fieldErrors: [],
    ...(downloadResult ? { downloadResult } : {}),
  })
}

function createFlow(isDownloadLocked = () => false) {
  return useRetryTaskFlow({ isDownloadLocked })
}

beforeEach(() => {
  vi.resetAllMocks()
  api.listRetryTasks.mockResolvedValue(structuredClone(retryTasks.page))
  api.getRetryTask.mockResolvedValue(structuredClone(retryTasks.detail))
})

describe('useRetryTaskFlow', () => {
  it('loads and paginates independent filters with stale-list protection', async () => {
    const first = deferred()
    const second = deferred()
    api.listRetryTasks.mockReset().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const flow = createFlow()

    const stale = flow.loadList({ pluginId: 'tushare_pro' })
    const current = flow.loadList({ apiName: 'daily' })
    expect(flow.listState.value).toBe('LOADING')
    expect(flow.appliedFilters.value).toEqual({ pluginId: '', apiName: 'daily' })
    second.resolve(structuredClone(retryTasks.page))
    expect(await current).toBe(true)
    first.reject(new ClientError('NETWORK', 'stale'))
    expect(await stale).toBe(false)
    expect(flow.listState.value).toBe('SUCCESS')
    expect(flow.listError.value).toBeNull()

    api.listRetryTasks.mockResolvedValueOnce({ ...structuredClone(retryTasks.page), page: 2 })
    expect(await flow.changePage(2)).toBe(true)
    expect(flow.page.value).toBe(2)
    await flow.changePageSize(50)
    expect(api.listRetryTasks.mock.calls.at(-1)[0]).toEqual({ apiName: 'daily', page: 1, pageSize: 50 })
    expect(flow.pageSize.value).toBe(50)
    await flow.refreshList()
    expect(api.listRetryTasks.mock.calls.at(-1)[0]).toEqual({ apiName: 'daily', page: 1, pageSize: 50 })
  })

  it('keeps list failure separate and never presents an old page as current', async () => {
    const flow = createFlow()
    await flow.loadList()
    const failure = new ClientError('NETWORK', 'list')
    api.listRetryTasks.mockRejectedValueOnce(failure)
    expect(await flow.refreshList()).toBe(false)
    expect(flow.listState.value).toBe('FAILURE')
    expect(flow.listResult.value).toBeNull()
    expect(flow.listError.value).toBe(failure)
    expect(flow.executionState.value).toBe('INITIAL')
  })

  it('selects details once, ignores stale detail responses and clears on null', async () => {
    const stale = deferred()
    const current = deferred()
    api.getRetryTask.mockReset().mockReturnValueOnce(stale.promise).mockReturnValueOnce(current.promise)
    const flow = createFlow()
    const first = flow.selectTask(TASK_ID)
    const second = flow.selectTask(OTHER_ID)
    current.resolve({ ...structuredClone(retryTasks.stocks), taskId: OTHER_ID })
    expect(await second).toBe(true)
    stale.resolve(structuredClone(retryTasks.detail))
    expect(await first).toBe(false)
    expect(flow.selectedTaskId.value).toBe(OTHER_ID)
    expect(flow.detail.value.taskId).toBe(OTHER_ID)
    expect(await flow.selectTask(OTHER_ID)).toBe(true)
    expect(api.getRetryTask).toHaveBeenCalledTimes(2)
    expect(await flow.selectTask(null)).toBe(true)
    expect(flow.detailState.value).toBe('INITIAL')
    expect(flow.detail.value).toBeNull()
  })

  it('freezes retry context and refreshes the list plus current detail after PARTIAL', async () => {
    const flow = createFlow()
    await flow.selectTask(TASK_ID)
    expect(flow.executionMessage.value).toBe('')
    const result = { ...structuredClone(rangeResults.PARTIAL), taskId: TASK_ID }
    api.executeRetryTask.mockResolvedValueOnce(result)
    api.getRetryTask.mockResolvedValueOnce(structuredClone(retryTasks.remaining))

    expect(await flow.execute()).toBe(true)
    expect(api.executeRetryTask).toHaveBeenCalledOnce()
    expect(api.executeRetryTask).toHaveBeenCalledWith(TASK_ID)
    expect(api.listRetryTasks).toHaveBeenCalledOnce()
    expect(api.getRetryTask).toHaveBeenCalledTimes(2)
    expect(flow.executionState.value).toBe('PARTIAL')
    expect(flow.executionResult.value).toBe(result)
    expect(flow.detail.value.items).toHaveLength(1)
    expect(flow.needsRefresh.value).toBe(false)
    expect(flow.executionContext.value).toEqual({
      operation: 'RETRY', taskId: TASK_ID, pluginId: 'tushare_pro', apiName: 'daily',
      params: { start_date: '20260901', end_date: '20260910' }, rangeMode: true,
    })
    expect(Object.isFrozen(flow.executionContext.value)).toBe(true)
    expect(Object.isFrozen(flow.executionContext.value.params)).toBe(true)
  })

  it('queues only the latest selected task, including null, until POST and automatic GETs settle', async () => {
    const post = deferred()
    const list = deferred()
    const detail = deferred()
    const flow = createFlow()
    await flow.selectTask(TASK_ID)
    api.executeRetryTask.mockReturnValueOnce(post.promise)
    api.listRetryTasks.mockReturnValueOnce(list.promise)
    api.getRetryTask.mockReturnValueOnce(detail.promise)
    const executing = flow.execute()
    expect(flow.locked.value).toBe(true)
    expect(await flow.selectTask(OTHER_ID)).toBe(true)
    expect(await flow.selectTask(null)).toBe(true)
    expect(api.getRetryTask).toHaveBeenCalledOnce()
    post.resolve({ ...structuredClone(rangeResults.PARTIAL), taskId: TASK_ID })
    await Promise.resolve()
    expect(flow.locked.value).toBe(false)
    list.resolve(structuredClone(retryTasks.page))
    detail.resolve(structuredClone(retryTasks.remaining))
    expect(await executing).toBe(true)
    expect(flow.selectedTaskId.value).toBeNull()
    expect(flow.detailState.value).toBe('INITIAL')
    expect(flow.executionResult.value).toBeNull()
    expect(api.getRetryTask).toHaveBeenCalledTimes(2)
  })

  it('clears a confirmed final task after one list refresh and does not synthesize a detail GET', async () => {
    const flow = createFlow()
    await flow.selectTask(TASK_ID)
    const result = { ...structuredClone(rangeResults.SUCCESS), taskId: null, remainingFailedUnits: 0 }
    api.executeRetryTask.mockResolvedValueOnce(result)
    expect(await flow.execute()).toBe(true)
    expect(flow.executionState.value).toBe('SUCCESS')
    expect(flow.detailState.value).toBe('NOT_FOUND')
    expect(flow.detail.value).toBeNull()
    expect(api.listRetryTasks).toHaveBeenCalledOnce()
    expect(api.getRetryTask).toHaveBeenCalledOnce()
    expect(flow.executionMessage.value).toContain('本轮响应确认当前任务已无剩余失败项')
  })

  it('preserves snapshots and requires a successful explicit detail refresh after rejected or unknown execution', async () => {
    const snapshot = { ...structuredClone(rangeResults.UNCONFIRMED), taskId: null }
    const flow = createFlow()
    await flow.selectTask(TASK_ID)
    api.executeRetryTask.mockRejectedValueOnce(serverError('COMMIT_UNCONFIRMED', snapshot))
    expect(await flow.execute()).toBe(false)
    expect(flow.executionState.value).toBe('UNCONFIRMED')
    expect(flow.executionResult.value).toEqual(snapshot)
    expect(flow.needsRefresh.value).toBe(true)
    expect(flow.canExecute.value).toBe(false)
    expect(api.listRetryTasks).not.toHaveBeenCalled()

    const firstExecution = flow.executionResult.value
    api.getRetryTask.mockResolvedValueOnce(structuredClone(retryTasks.detail))
    expect(await flow.refreshDetail()).toBe(true)
    expect(flow.executionResult.value).toBe(firstExecution)
    expect(flow.needsRefresh.value).toBe(false)

    api.executeRetryTask.mockRejectedValueOnce(new ClientError('TIMEOUT', 'timeout'))
    expect(await flow.execute()).toBe(false)
    expect(flow.executionState.value).toBe('UNCONFIRMED')
    expect(flow.executionResult.value).toBeNull()
    expect(flow.needsRefresh.value).toBe(true)
    expect(api.executeRetryTask).toHaveBeenCalledTimes(2)
  })

  it('classifies an unexpected thrown value as an unconfirmed client result', async () => {
    const flow = createFlow()
    await flow.selectTask(TASK_ID)
    api.executeRetryTask.mockRejectedValueOnce(new Error('unsafe internal detail'))

    expect(await flow.execute()).toBe(false)
    expect(flow.executionState.value).toBe('UNCONFIRMED')
    expect(flow.executionResult.value).toBeNull()
    expect(flow.executionError.value).toMatchObject({ kind: 'UNEXPECTED' })
    expect(flow.executionError.value.message).not.toContain('unsafe internal detail')
  })

  it('suppresses an unavailable refresh-detail instruction after GET 404', async () => {
    const flow = createFlow()
    api.getRetryTask.mockRejectedValueOnce(serverError('RETRY_TASK_NOT_FOUND'))
    expect(await flow.selectTask(TASK_ID)).toBe(false)
    expect(flow.detailState.value).toBe('NOT_FOUND')
    expect(flow.selectedTaskId.value).toBe(TASK_ID)
    expect(flow.needsRefresh.value).toBe(true)
    expect(flow.executionMessage.value).toBe('')
    expect(api.listRetryTasks).toHaveBeenCalledOnce()
  })

  it('suppresses an unavailable refresh-detail instruction after POST 404', async () => {
    const flow = createFlow()
    await flow.selectTask(TASK_ID)
    api.executeRetryTask.mockRejectedValueOnce(serverError('RETRY_TASK_NOT_FOUND'))
    expect(await flow.execute()).toBe(false)
    expect(flow.detailState.value).toBe('NOT_FOUND')
    expect(flow.executionState.value).toBe('FAILURE')
    expect(flow.executionError.value.code).toBe('RETRY_TASK_NOT_FOUND')
    expect(flow.needsRefresh.value).toBe(true)
    expect(flow.executionMessage.value).toBe('')
    expect(api.listRetryTasks).toHaveBeenCalledOnce()
  })

  it('suppresses an unavailable refresh-detail instruction when the automatic detail GET returns 404', async () => {
    const flow = createFlow()
    await flow.selectTask(TASK_ID)
    const result = { ...structuredClone(rangeResults.PARTIAL), taskId: TASK_ID }
    api.executeRetryTask.mockResolvedValueOnce(result)
    api.getRetryTask.mockRejectedValueOnce(serverError('RETRY_TASK_NOT_FOUND'))

    expect(await flow.execute()).toBe(true)
    expect(flow.executionState.value).toBe('PARTIAL')
    expect(flow.executionResult.value).toBe(result)
    expect(flow.detailState.value).toBe('NOT_FOUND')
    expect(flow.needsRefresh.value).toBe(true)
    expect(flow.executionMessage.value).toBe('')
    expect(api.listRetryTasks).toHaveBeenCalledOnce()
    expect(api.getRetryTask).toHaveBeenCalledTimes(2)
  })

  it('rejects mismatched result identities and guards every execute precondition in code', async () => {
    const downloadLocked = ref(false)
    const flow = createFlow(() => downloadLocked.value)
    expect(await flow.execute()).toBe(false)
    await flow.selectTask(TASK_ID)
    downloadLocked.value = true
    expect(await flow.execute()).toBe(false)
    downloadLocked.value = false
    expect(flow.canExecute.value).toBe(true)

    api.executeRetryTask.mockResolvedValueOnce({ ...structuredClone(rangeResults.PARTIAL), taskId: TASK_ID, pluginId: 'fixture' })
    expect(await flow.execute()).toBe(false)
    expect(flow.executionError.value).toMatchObject({ kind: 'INVALID_RESPONSE' })
    expect(flow.needsRefresh.value).toBe(true)

    const blocked = { ...structuredClone(retryTasks.blocked), taskId: TASK_ID }
    api.getRetryTask.mockResolvedValueOnce(blocked)
    await flow.refreshDetail()
    expect(flow.canExecute.value).toBe(false)
    expect(await flow.execute()).toBe(false)
    expect(api.executeRetryTask).toHaveBeenCalledOnce()
  })

  it('derives range modes and execution messages without guessing from failure selectors', async () => {
    const flow = createFlow()
    api.getRetryTask.mockResolvedValueOnce(structuredClone(retryTasks.original))
    await flow.selectTask(TASK_ID)
    expect(flow.executionMessage.value).toBe('')
    api.executeRetryTask.mockImplementationOnce(() => {
      expect(flow.executionMessage.value).toBe('下载请求已提交，请等待结果。')
      return Promise.reject(serverError('DOWNLOAD_BUSY'))
    })
    await flow.execute()
    expect(flow.executionContext.value).toMatchObject({
      pluginId: 'tushare_pro', apiName: 'stock_basic', params: { list_status: 'L' }, rangeMode: false,
    })
    expect(flow.executionMessage.value).toBe('记录可能已变化，请刷新详情。')

    api.getRetryTask.mockResolvedValueOnce({ ...structuredClone(retryTasks.detail), canExecute: false, executionBlocker: { code: 'PLUGIN_DISABLED', message: '原插件不可用' } })
    await flow.refreshDetail()
    expect(flow.executionMessage.value).toBe('原插件不可用')
  })
})
