import { flushPromises } from '@vue/test-utils'

import { ClientError } from '../api/errors.js'
import { listDownloadTasks } from '../api/downloadTasks.js'
import { useDownloadTaskList } from './useDownloadTaskList.js'

vi.mock('../api/downloadTasks.js', () => ({
  listDownloadTasks: vi.fn(),
}))

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function pageResult(overrides = {}) {
  return Object.freeze({
    page: 1,
    pageSize: 20,
    total: 0n,
    items: Object.freeze([]),
    ...overrides,
  })
}

describe('useDownloadTaskList', () => {
  let visibility
  let list

  beforeEach(() => {
    vi.useFakeTimers()
    visibility = 'visible'
    vi.spyOn(document, 'visibilityState', 'get').mockImplementation(
      () => visibility,
    )
  })

  afterEach(() => {
    list?.dispose()
    list = null
    vi.clearAllTimers()
    vi.useRealTimers()
  })

  it('starts with the first server page and polls once five seconds after success', async () => {
    const first = pageResult({ total: 1n })
    const second = pageResult({ total: 2n })
    listDownloadTasks.mockResolvedValueOnce(first).mockResolvedValueOnce(second)
    list = useDownloadTaskList()

    const started = list.start()
    expect(listDownloadTasks).toHaveBeenCalledWith({ page: 1, pageSize: 20 })
    expect(list.loading.value).toBe(true)
    await started

    expect(list.result.value).toBe(first)
    expect(list.error.value).toBeNull()
    expect(list.lastUpdatedAt.value).toBeInstanceOf(Date)
    expect(vi.getTimerCount()).toBe(1)

    await vi.advanceTimersByTimeAsync(4_999)
    expect(listDownloadTasks).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1)
    await flushPromises()

    expect(listDownloadTasks).toHaveBeenCalledTimes(2)
    expect(list.result.value).toBe(second)
    expect(vi.getTimerCount()).toBe(1)
  })

  it('does not fetch while hidden and fetches immediately when visible', async () => {
    visibility = 'hidden'
    listDownloadTasks.mockResolvedValue(pageResult())
    list = useDownloadTaskList()

    await list.start()
    await vi.advanceTimersByTimeAsync(30_000)
    expect(listDownloadTasks).not.toHaveBeenCalled()

    visibility = 'visible'
    document.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()

    expect(listDownloadTasks).toHaveBeenCalledOnce()
    expect(vi.getTimerCount()).toBe(1)

    visibility = 'hidden'
    document.dispatchEvent(new Event('visibilitychange'))
    expect(vi.getTimerCount()).toBe(0)
  })

  it('never overlaps requests and coalesces refreshes into one follow-up', async () => {
    const first = deferred()
    listDownloadTasks.mockReturnValueOnce(first.promise).mockResolvedValueOnce(
      pageResult(),
    )
    list = useDownloadTaskList()

    const started = list.start()
    list.refresh()
    list.refresh()
    list.onAccepted()
    expect(listDownloadTasks).toHaveBeenCalledTimes(1)

    first.resolve(pageResult())
    await started
    await flushPromises()

    expect(listDownloadTasks).toHaveBeenCalledTimes(2)
    expect(listDownloadTasks.mock.calls[1]).toEqual([{ page: 1, pageSize: 20 }])
  })

  it('uses 5, 10, and 30 second failure backoff, then resets after success', async () => {
    const failure = new ClientError('NETWORK', 'list-request')
    listDownloadTasks
      .mockRejectedValueOnce(failure)
      .mockRejectedValueOnce(failure)
      .mockRejectedValueOnce(failure)
      .mockResolvedValueOnce(pageResult())
      .mockRejectedValueOnce(failure)
    list = useDownloadTaskList()

    await list.start()
    expect(list.error.value).toBe(failure)

    await vi.advanceTimersByTimeAsync(4_999)
    expect(listDownloadTasks).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1)
    expect(listDownloadTasks).toHaveBeenCalledTimes(2)

    await vi.advanceTimersByTimeAsync(9_999)
    expect(listDownloadTasks).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(1)
    expect(listDownloadTasks).toHaveBeenCalledTimes(3)

    await vi.advanceTimersByTimeAsync(29_999)
    expect(listDownloadTasks).toHaveBeenCalledTimes(3)
    await vi.advanceTimersByTimeAsync(1)
    expect(listDownloadTasks).toHaveBeenCalledTimes(4)

    await vi.advanceTimersByTimeAsync(5_000)
    expect(listDownloadTasks).toHaveBeenCalledTimes(5)
  })

  it('keeps a same-page snapshot on refresh failure and replaces the error after recovery', async () => {
    const snapshot = pageResult({ total: 3n })
    const recovered = pageResult({ total: 4n })
    const failure = new ClientError('NETWORK', 'list-request')
    listDownloadTasks
      .mockResolvedValueOnce(snapshot)
      .mockRejectedValueOnce(failure)
      .mockResolvedValueOnce(recovered)
    list = useDownloadTaskList()

    await list.start()
    const updatedAt = list.lastUpdatedAt.value
    await list.refresh()

    expect(list.result.value).toBe(snapshot)
    expect(list.lastUpdatedAt.value).toBe(updatedAt)
    expect(list.error.value).toBe(failure)

    await list.refresh()
    expect(list.result.value).toBe(recovered)
    expect(list.error.value).toBeNull()
    expect(list.lastUpdatedAt.value.valueOf()).toBeGreaterThanOrEqual(
      updatedAt.valueOf(),
    )
  })

  it('discards an old page response and runs only the latest page intent', async () => {
    const oldPage = deferred()
    const latest = pageResult({ page: 4, pageSize: 50, total: 80n })
    listDownloadTasks
      .mockReturnValueOnce(oldPage.promise)
      .mockResolvedValueOnce(latest)
    list = useDownloadTaskList()

    const started = list.start()
    list.changePage(3)
    list.changePageSize(50)
    list.changePage(4)

    expect(list.page.value).toBe(4)
    expect(list.pageSize.value).toBe(50)
    expect(list.result.value).toBeNull()
    expect(listDownloadTasks).toHaveBeenCalledTimes(1)

    oldPage.resolve(pageResult({ total: 99n }))
    await started
    await flushPromises()

    expect(listDownloadTasks.mock.calls).toEqual([
      [{ page: 1, pageSize: 20 }],
      [{ page: 4, pageSize: 50 }],
    ])
    expect(list.result.value).toBe(latest)
  })

  it('returns to page one after acceptance without inventing a client row', async () => {
    listDownloadTasks
      .mockResolvedValueOnce(pageResult())
      .mockResolvedValueOnce(pageResult({ page: 2 }))
      .mockResolvedValueOnce(pageResult({ total: 1n }))
    list = useDownloadTaskList()

    await list.start()
    await list.changePage(2)
    await list.onAccepted({ taskId: 'ignored-client-receipt' })

    expect(list.page.value).toBe(1)
    expect(listDownloadTasks.mock.calls.at(-1)).toEqual([
      { page: 1, pageSize: 20 },
    ])
    expect(list.result.value.items).toEqual([])
    expect(list.result.value.total).toBe(1n)
  })

  it('invalidates hidden and disposed requests without late state or timer writes', async () => {
    const hiddenRequest = deferred()
    const visibleRequest = deferred()
    listDownloadTasks
      .mockReturnValueOnce(hiddenRequest.promise)
      .mockReturnValueOnce(visibleRequest.promise)
    list = useDownloadTaskList()

    const started = list.start()
    visibility = 'hidden'
    document.dispatchEvent(new Event('visibilitychange'))
    hiddenRequest.resolve(pageResult({ total: 10n }))
    await started
    await flushPromises()
    expect(list.result.value).toBeNull()
    expect(vi.getTimerCount()).toBe(0)

    visibility = 'visible'
    document.dispatchEvent(new Event('visibilitychange'))
    expect(listDownloadTasks).toHaveBeenCalledTimes(2)
    expect(list.loading.value).toBe(true)

    list.dispose()
    const stateAtDispose = {
      result: list.result.value,
      error: list.error.value,
      updated: list.lastUpdatedAt.value,
      loading: list.loading.value,
    }
    visibleRequest.reject(new ClientError('NETWORK', 'late-request'))
    await flushPromises()

    expect({
      result: list.result.value,
      error: list.error.value,
      updated: list.lastUpdatedAt.value,
      loading: list.loading.value,
    }).toEqual(stateAtDispose)
    expect(vi.getTimerCount()).toBe(0)

    document.dispatchEvent(new Event('visibilitychange'))
    await list.refresh()
    await list.changePage(5)
    expect(listDownloadTasks).toHaveBeenCalledTimes(2)
  })

  it('consumes a queued page intent exactly once after an in-flight request settles hidden', async () => {
    const oldPage = deferred()
    listDownloadTasks
      .mockReturnValueOnce(oldPage.promise)
      .mockResolvedValueOnce(pageResult({ page: 2 }))
    list = useDownloadTaskList()

    const started = list.start()
    list.changePage(2)
    list.refresh()
    visibility = 'hidden'
    document.dispatchEvent(new Event('visibilitychange'))

    oldPage.resolve(pageResult())
    await started
    await flushPromises()
    expect(listDownloadTasks).toHaveBeenCalledTimes(1)

    visibility = 'visible'
    document.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()

    expect(listDownloadTasks.mock.calls).toEqual([
      [{ page: 1, pageSize: 20 }],
      [{ page: 2, pageSize: 20 }],
    ])
    expect(vi.getTimerCount()).toBe(1)
  })

  it('rejects page intents beyond the HTTP contract maximum', async () => {
    listDownloadTasks.mockResolvedValue(pageResult())
    list = useDownloadTaskList()
    await list.start()

    await list.changePage(2_147_483_648)

    expect(list.page.value).toBe(1)
    expect(listDownloadTasks).toHaveBeenCalledTimes(1)
  })

  it('pauses while its kept-alive view is inactive and refreshes immediately on return', async () => {
    listDownloadTasks.mockResolvedValue(pageResult())
    list = useDownloadTaskList()
    await list.start()

    list.setActive(false)
    await vi.advanceTimersByTimeAsync(30_000)
    expect(listDownloadTasks).toHaveBeenCalledTimes(1)
    expect(vi.getTimerCount()).toBe(0)

    list.setActive(true)
    await flushPromises()
    expect(listDownloadTasks).toHaveBeenCalledTimes(2)
    expect(vi.getTimerCount()).toBe(1)
  })

  it('invalidates an in-flight result while inactive and applies only the resumed query', async () => {
    const inactive = deferred()
    const resumed = deferred()
    listDownloadTasks
      .mockReturnValueOnce(inactive.promise)
      .mockReturnValueOnce(resumed.promise)
    list = useDownloadTaskList()

    const started = list.start()
    list.setActive(false)
    inactive.resolve(pageResult({ total: 9n }))
    await started
    await flushPromises()
    expect(list.result.value).toBeNull()
    expect(vi.getTimerCount()).toBe(0)

    list.setActive(true)
    expect(listDownloadTasks).toHaveBeenCalledTimes(2)
    resumed.resolve(pageResult({ total: 2n }))
    await flushPromises()

    expect(list.result.value.total).toBe(2n)
    expect(vi.getTimerCount()).toBe(1)
  })
})
