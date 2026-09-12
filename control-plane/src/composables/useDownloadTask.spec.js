import { flushPromises } from '@vue/test-utils'
import examples from '../../../docs/contracts/download-task-examples.json'
import { parseDownloadTask } from '../api/downloadTaskDtos.js'
import { ApiError, ClientError } from '../api/errors.js'
import * as api from '../api/downloadTasks.js'
import { useDownloadTask } from './useDownloadTask.js'

vi.mock('../api/downloadTasks.js', () => ({
  getDownloadTask: vi.fn(), listDownloadTaskBatches: vi.fn(),
  retryDownloadTask: vi.fn(), resumeDownloadTask: vi.fn(),
}))

const ID = '22222222-2222-4222-8222-222222222222'
const OTHER = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
const REQUEST_ID = '11111111-1111-4111-8111-111111111111'
function task(name = 'runningTask', overrides = {}) {
  return parseDownloadTask({ ...examples.examples.find((e) => e.name === name).value, ...overrides }, REQUEST_ID)
}
function batchPage(page = 1, pageSize = 20) {
  return Object.freeze({ page, pageSize, total: 0n, items: Object.freeze([]) })
}
function failure(code = 'QUERY_FAILED') {
  return new ApiError({ code, message: code, requestId: REQUEST_ID, retryable: false, fieldErrors: [] })
}
function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

let flow, visibility
beforeEach(() => {
  vi.resetAllMocks()
  vi.useFakeTimers()
  visibility = 'visible'
  vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visibility)
  api.getDownloadTask.mockResolvedValue(task())
  api.listDownloadTaskBatches.mockImplementation((id, criteria) => Promise.resolve(batchPage(criteria.page, criteria.pageSize)))
})
afterEach(() => {
  flow?.dispose()
  flow = null
  vi.clearAllTimers()
  vi.useRealTimers()
})
function visible(value) {
  visibility = value
  document.dispatchEvent(new Event('visibilitychange'))
}

it('queries the URL identity without setup, polls at two seconds, and stops at a terminal snapshot', async () => {
  flow = useDownloadTask()
  expect(api.getDownloadTask).not.toHaveBeenCalled()
  await flow.load(ID)
  expect(flow.task.value.status).toBe('RUNNING')
  expect(flow.taskUpdatedAt.value).toBeInstanceOf(Date)
  expect(flow.batchesUpdatedAt.value).toBeInstanceOf(Date)
  expect(api.getDownloadTask).toHaveBeenCalledWith(ID)
  expect(api.listDownloadTaskBatches).toHaveBeenCalledWith(ID, { page: 1, pageSize: 20 })
  await vi.advanceTimersByTimeAsync(1999)
  expect(api.getDownloadTask).toHaveBeenCalledTimes(1)
  api.getDownloadTask.mockResolvedValue(task('succeededTask'))
  await vi.advanceTimersByTimeAsync(1)
  expect(flow.task.value.status).toBe('SUCCEEDED')
  await vi.advanceTimersByTimeAsync(60_000)
  expect(api.getDownloadTask).toHaveBeenCalledTimes(2)
  expect(vi.getTimerCount()).toBe(0)
})

it.each(['queuedTask', 'partialFailedTask', 'unplannedFailedTask', 'interruptedTask'])('uses server status for %s polling', async (name) => {
  api.getDownloadTask.mockResolvedValue(task(name))
  flow = useDownloadTask()
  await flow.load(ID)
  await vi.advanceTimersByTimeAsync(2000)
  expect(api.getDownloadTask).toHaveBeenCalledTimes(name === 'queuedTask' ? 2 : 1)
})

it('holds the whole cycle until both GETs settle and merges repeated refreshes', async () => {
  const detail = deferred(), batches = deferred()
  api.getDownloadTask.mockReturnValueOnce(detail.promise)
  api.listDownloadTaskBatches.mockReturnValueOnce(batches.promise)
  flow = useDownloadTask()
  flow.load(ID)
  flow.refresh(); flow.refresh()
  detail.resolve(task())
  await flushPromises()
  await vi.advanceTimersByTimeAsync(10_000)
  expect(api.getDownloadTask).toHaveBeenCalledTimes(1)
  expect(api.listDownloadTaskBatches).toHaveBeenCalledTimes(1)
  batches.resolve(batchPage())
  await flushPromises()
  expect(api.getDownloadTask).toHaveBeenCalledTimes(2)
  expect(flow.loading.value).toBe(false)
})

it('keeps successful snapshots separately, backs off 5/10/30 seconds, and resets on success', async () => {
  flow = useDownloadTask()
  await flow.load(ID)
  const originalTask = flow.task.value, originalBatches = flow.batches.value
  api.getDownloadTask.mockRejectedValue(failure())
  api.listDownloadTaskBatches.mockRejectedValue(new ClientError('NETWORK'))
  await flow.refresh()
  expect(flow.task.value).toBe(originalTask)
  expect(flow.batches.value).toBe(originalBatches)
  expect(flow.task.value.status).toBe('RUNNING')
  for (const [delay, calls] of [[5000, 3], [10000, 4], [30000, 5], [30000, 6]]) {
    await vi.advanceTimersByTimeAsync(delay - 1)
    expect(api.getDownloadTask).toHaveBeenCalledTimes(calls - 1)
    await vi.advanceTimersByTimeAsync(1)
    expect(api.getDownloadTask).toHaveBeenCalledTimes(calls)
  }
  api.getDownloadTask.mockResolvedValue(task('partialFailedTask'))
  await vi.advanceTimersByTimeAsync(30000)
  expect(flow.task.value.status).toBe('PARTIAL_FAILED')
  expect(flow.taskError.value).toBeNull()
  expect(flow.batchesError.value.kind).toBe('NETWORK')
  expect(flow.canRetry.value).toBe(true)
  api.listDownloadTaskBatches.mockResolvedValue(batchPage())
  await vi.advanceTimersByTimeAsync(30000)
  expect(flow.batchesError.value).toBeNull()
  expect(vi.getTimerCount()).toBe(0)
  api.getDownloadTask.mockRejectedValueOnce(failure())
  await flow.refresh()
  const calls = api.getDownloadTask.mock.calls.length
  await vi.advanceTimersByTimeAsync(5000)
  expect(api.getDownloadTask).toHaveBeenCalledTimes(calls + 1)
})

it('does not let batch success override detail 404 and allows manual recovery', async () => {
  flow = useDownloadTask()
  await flow.load(ID)
  api.getDownloadTask.mockRejectedValueOnce(failure('TASK_NOT_FOUND'))
  await flow.refresh()
  expect(flow.notFound.value).toBe(true)
  expect(flow.task.value).toBeNull()
  expect(flow.batches.value).toBeNull()
  await vi.advanceTimersByTimeAsync(30_000)
  expect(api.getDownloadTask).toHaveBeenCalledTimes(2)
  await flow.refresh()
  expect(flow.notFound.value).toBe(false)
  expect(flow.task.value.status).toBe('RUNNING')
})

it.each(['not-a-uuid', '', null, ['a']])('rejects an invalid route without HTTP: %s', async (id) => {
  flow = useDownloadTask()
  await flow.load(id)
  expect(flow.invalidTaskId.value).toBe(true)
  expect(api.getDownloadTask).not.toHaveBeenCalled()
  expect(api.listDownloadTaskBatches).not.toHaveBeenCalled()
})

it('normalizes uppercase and resets page, data and errors on task ID change without overlap', async () => {
  flow = useDownloadTask()
  await flow.load(ID)
  await flow.changePageSize(50)
  const detail = deferred(), batches = deferred(), latest = deferred()
  api.getDownloadTask.mockReturnValueOnce(detail.promise).mockReturnValueOnce(latest.promise)
  api.listDownloadTaskBatches.mockReturnValueOnce(batches.promise)
  flow.changePage(2)
  flow.load(OTHER.toUpperCase())
  expect(flow.taskId.value).toBe(OTHER)
  expect(flow.task.value).toBeNull()
  expect(flow.batches.value).toBeNull()
  expect(flow.page.value).toBe(1)
  expect(flow.pageSize.value).toBe(20)
  expect(api.getDownloadTask).toHaveBeenCalledTimes(3)
  detail.reject(failure())
  batches.resolve(batchPage(2, 50))
  await flushPromises()
  expect(flow.taskError.value).toBeNull()
  expect(flow.loading.value).toBe(true)
  expect(api.getDownloadTask).toHaveBeenLastCalledWith(OTHER)
  latest.resolve(task('succeededTask', { taskId: OTHER }))
  await flushPromises()
  expect(flow.task.value.taskId).toBe(OTHER)
  expect(flow.batches.value.page).toBe(1)
  expect(vi.getTimerCount()).toBe(0)
})

it('coalesces pages and page sizes, clears only old page batches, ignores old failures/finally', async () => {
  flow = useDownloadTask()
  await flow.load(ID)
  const oldTask = flow.task.value, detail = deferred(), batches = deferred(), latest = deferred()
  api.getDownloadTask.mockReturnValueOnce(detail.promise).mockReturnValueOnce(latest.promise)
  api.listDownloadTaskBatches.mockReturnValueOnce(batches.promise)
  flow.changePage(2)
  flow.changePage(3)
  flow.changePageSize(100)
  flow.changePage(7)
  expect(flow.task.value).toBe(oldTask)
  expect(flow.batches.value).toBeNull()
  expect(flow.batchesUpdatedAt.value).toBeNull()
  detail.resolve(task('succeededTask'))
  batches.reject(failure())
  await flushPromises()
  expect(api.listDownloadTaskBatches).toHaveBeenLastCalledWith(ID, { page: 7, pageSize: 100 })
  expect(api.getDownloadTask).toHaveBeenCalledTimes(3)
  expect(flow.task.value).toBe(oldTask)
  expect(flow.batchesError.value).toBeNull()
  expect(flow.loading.value).toBe(true)
  latest.resolve(task())
  await flushPromises()
  expect(flow.batches.value.page).toBe(7)
})

it('pauses hidden cycles and refreshes visible terminal tasks without releasing pending GETs early', async () => {
  const detail = deferred(), batches = deferred()
  api.getDownloadTask.mockReturnValueOnce(detail.promise).mockResolvedValue(task('succeededTask'))
  api.listDownloadTaskBatches.mockReturnValueOnce(batches.promise)
  flow = useDownloadTask()
  flow.load(ID)
  visible('hidden')
  await vi.advanceTimersByTimeAsync(30_000)
  visible('visible'); visible('visible')
  expect(api.getDownloadTask).toHaveBeenCalledTimes(1)
  detail.resolve(task()); batches.reject(failure())
  await flushPromises()
  expect(api.getDownloadTask).toHaveBeenCalledTimes(2)
  expect(flow.task.value.status).toBe('SUCCEEDED')
  expect(flow.batchesError.value).toBeNull()
  visible('hidden'); visible('visible')
  await flushPromises()
  expect(api.getDownloadTask).toHaveBeenCalledTimes(3)
})

it('does not start while hidden and removes listener/timer and all late state writes after dispose', async () => {
  visibility = 'hidden'
  const remove = vi.spyOn(document, 'removeEventListener')
  flow = useDownloadTask()
  await flow.load(ID)
  expect(api.getDownloadTask).not.toHaveBeenCalled()
  const detail = deferred()
  api.getDownloadTask.mockReturnValueOnce(detail.promise)
  visible('visible')
  flow.dispose()
  detail.resolve(task())
  await flushPromises()
  expect(flow.task.value).toBeNull()
  expect(flow.loading.value).toBe(false)
  expect(remove).toHaveBeenCalledWith('visibilitychange', expect.any(Function))
  visible('hidden'); visible('visible')
  await flow.load(OTHER); await flow.refresh(); await flow.changePage(2); await flow.changePageSize(50)
  await flow.retry(); await flow.resume()
  expect(api.getDownloadTask).toHaveBeenCalledTimes(1)
  expect(vi.getTimerCount()).toBe(0)
})

it('uses independent server controls even for nonretryable errors, and disables on a detail query failure', async () => {
  api.getDownloadTask.mockResolvedValue(task('partialFailedTask', { canRetry: true, canResume: true }))
  flow = useDownloadTask()
  await flow.load(ID)
  expect(flow.canRetry.value).toBe(true)
  expect(flow.canResume.value).toBe(true)
  api.getDownloadTask.mockRejectedValueOnce(failure())
  await flow.refresh()
  expect(flow.task.value.canRetry).toBe(true)
  expect(flow.canRetry.value).toBe(false)
  await flow.retry()
  expect(api.retryDownloadTask).not.toHaveBeenCalled()
})

it('captures the original bigint version, waits for GETs, and prevents double or different operations', async () => {
  api.getDownloadTask.mockResolvedValue(task('partialFailedTask', { version: 9007199254740993n, canResume: true }))
  flow = useDownloadTask()
  await flow.load(ID)
  const detail = deferred(), batches = deferred(), post = deferred()
  api.getDownloadTask.mockReturnValueOnce(detail.promise)
  api.listDownloadTaskBatches.mockReturnValueOnce(batches.promise)
  api.retryDownloadTask.mockReturnValueOnce(post.promise)
  flow.refresh()
  flow.retry(); flow.retry(); flow.resume()
  expect(flow.operation.value).toBe('retry')
  expect(flow.canRetry.value).toBe(false)
  expect(api.retryDownloadTask).not.toHaveBeenCalled()
  detail.resolve(task('partialFailedTask', { version: 9007199254740994n }))
  await flushPromises()
  expect(api.retryDownloadTask).not.toHaveBeenCalled()
  batches.resolve(batchPage())
  await flushPromises()
  expect(api.retryDownloadTask).toHaveBeenCalledExactlyOnceWith(ID, 9007199254740993n)
  expect(api.resumeDownloadTask).not.toHaveBeenCalled()
  const confirmed = deferred()
  api.getDownloadTask.mockReturnValueOnce(confirmed.promise)
  post.resolve({ taskId: ID, status: 'SUCCEEDED', version: 9007199254740995n })
  await flushPromises()
  expect(flow.operation.value).toBeNull()
  expect(flow.operationMessage.value).toBe('重试请求已接收')
  expect(flow.task.value.status).toBe('PARTIAL_FAILED')
  expect(flow.canRetry.value).toBe(false)
  confirmed.resolve(task())
  await flushPromises()
  expect(flow.task.value.status).toBe('RUNNING')
  await vi.advanceTimersByTimeAsync(2000)
  expect(api.getDownloadTask).toHaveBeenCalledTimes(4)
})

it.each([
  ['TASK_STATE_CONFLICT', '任务状态已变化，已重新查询'],
  ['TASK_DEFINITION_CHANGED', 'TASK_DEFINITION_CHANGED'],
  ['QUERY_FAILED', '操作结果尚未确认，正在重新查询任务'],
  ['TASK_QUEUE_FULL', 'TASK_QUEUE_FULL'],
  ['TASK_NOT_FOUND', 'TASK_NOT_FOUND'],
  ['NETWORK', '操作结果尚未确认，正在重新查询任务'],
  ['INVALID_RESPONSE', '操作结果尚未确认，正在重新查询任务'],
])('requeries after %s without replaying or mutating old facts; confirmation failure backs off', async (code, message) => {
  api.getDownloadTask.mockResolvedValue(task('interruptedTask'))
  flow = useDownloadTask()
  await flow.load(ID)
  const oldTask = flow.task.value
  const error = ['NETWORK', 'INVALID_RESPONSE'].includes(code) ? new ClientError(code) : failure(code)
  api.resumeDownloadTask.mockRejectedValue(error)
  api.getDownloadTask.mockRejectedValueOnce(failure())
  await flow.resume()
  await flushPromises()
  expect(flow.operationMessage.value).toContain(message)
  expect(flow.operationError.value).toBe(error)
  expect(flow.task.value).toBe(oldTask)
  expect(flow.canResume.value).toBe(false)
  expect(api.resumeDownloadTask).toHaveBeenCalledExactlyOnceWith(ID, oldTask.version)
  expect(api.getDownloadTask).toHaveBeenCalledTimes(2)
  await vi.advanceTimersByTimeAsync(5000)
  expect(flow.canResume.value).toBe(true)
  expect(api.getDownloadTask).toHaveBeenCalledTimes(3)
  expect(api.resumeDownloadTask).toHaveBeenCalledTimes(1)
})

it.each(['hide', 'switch', 'dispose'])('abandons an unsent control after %s while keeping the query slot', async (action) => {
  api.getDownloadTask.mockResolvedValue(task('partialFailedTask'))
  flow = useDownloadTask()
  await flow.load(ID)
  const detail = deferred()
  api.getDownloadTask.mockReturnValueOnce(detail.promise)
  flow.refresh()
  flow.retry()
  if (action === 'hide') { visible('hidden'); visible('visible') }
  if (action === 'switch') { flow.load(OTHER); api.getDownloadTask.mockResolvedValue(task('succeededTask', { taskId: OTHER })) }
  if (action === 'dispose') flow.dispose()
  expect(api.getDownloadTask).toHaveBeenCalledTimes(2)
  detail.resolve(task('partialFailedTask'))
  await flushPromises()
  expect(api.retryDownloadTask).not.toHaveBeenCalled()
  expect(flow.operation.value).toBeNull()
  expect(api.getDownloadTask).toHaveBeenCalledTimes(action === 'dispose' ? 2 : 3)
})

it.each(['hide', 'switch', 'dispose'])('keeps a sent POST in its slot and isolates its late result after %s', async (action) => {
  api.getDownloadTask.mockResolvedValue(task('partialFailedTask'))
  flow = useDownloadTask()
  await flow.load(ID)
  const post = deferred()
  api.retryDownloadTask.mockReturnValueOnce(post.promise)
  flow.retry()
  await flushPromises()
  expect(api.retryDownloadTask).toHaveBeenCalledTimes(1)
  if (action === 'hide') visible('hidden')
  if (action === 'switch') { flow.load(OTHER); api.getDownloadTask.mockResolvedValue(task('succeededTask', { taskId: OTHER })) }
  if (action === 'dispose') flow.dispose()
  flow.refresh()
  expect(api.getDownloadTask).toHaveBeenCalledTimes(1)
  post.resolve({ taskId: ID })
  await flushPromises()
  expect(flow.operationMessage.value).toBe('')
  if (action === 'hide') {
    expect(api.getDownloadTask).toHaveBeenCalledTimes(1)
    visible('visible')
    await flushPromises()
  }
  expect(api.getDownloadTask).toHaveBeenCalledTimes(action === 'dispose' ? 1 : 2)
  if (action === 'switch') expect(flow.task.value.taskId).toBe(OTHER)
})
