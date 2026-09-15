import { ref } from 'vue'

import { listDownloadTasks } from '../api/downloadTasks.js'

const POLL_DELAY = 5_000
const FAILURE_DELAYS = [5_000, 10_000, 30_000]
const PAGE_SIZES = new Set([20, 50, 100])

export function useDownloadTaskList() {
  const page = ref(1)
  const pageSize = ref(20)
  const result = ref(null)
  const loading = ref(false)
  const error = ref(null)
  const lastUpdatedAt = ref(null)

  let started = false
  let disposed = false
  let active = true
  let generation = 0
  let inFlight = null
  let queued = false
  let timer = null
  let failureCount = 0

  function visible() {
    return active && document.visibilityState !== 'hidden'
  }

  function clearTimer() {
    if (timer !== null) clearTimeout(timer)
    timer = null
  }

  function schedule(delay) {
    clearTimer()
    if (!started || disposed || !visible()) return
    timer = setTimeout(() => {
      timer = null
      request()
    }, delay)
  }

  function request() {
    if (!started || disposed || !visible()) return Promise.resolve(false)
    clearTimer()
    if (inFlight) {
      queued = true
      return inFlight
    }

    queued = false
    const requestGeneration = generation
    const criteria = { page: page.value, pageSize: pageSize.value }
    loading.value = true
    const operation = listDownloadTasks(criteria)

    inFlight = operation
      .then((nextResult) => {
        if (disposed || requestGeneration !== generation || !visible()) {
          return false
        }
        result.value = nextResult
        error.value = null
        lastUpdatedAt.value = new Date()
        failureCount = 0
        return true
      })
      .catch((failure) => {
        if (disposed || requestGeneration !== generation || !visible()) {
          return false
        }
        error.value = failure
        failureCount += 1
        return false
      })
      .finally(() => {
        inFlight = null
        if (disposed || !active) return
        loading.value = false
        if (!started || !visible()) return
        if (queued) {
          queued = false
          request()
          return
        }
        const delay =
          error.value === null
            ? POLL_DELAY
            : FAILURE_DELAYS[Math.min(failureCount, FAILURE_DELAYS.length) - 1]
        schedule(delay)
      })

    return inFlight
  }

  function start() {
    if (disposed || started) return Promise.resolve(false)
    started = true
    document.addEventListener('visibilitychange', handleVisibilityChange)
    return visible() ? request() : Promise.resolve(false)
  }

  function refresh() {
    return request()
  }

  function changeIntent(nextPage, nextPageSize) {
    if (disposed || !started) return Promise.resolve(false)
    if (page.value === nextPage && pageSize.value === nextPageSize) {
      return request()
    }
    generation += 1
    page.value = nextPage
    pageSize.value = nextPageSize
    result.value = null
    error.value = null
    lastUpdatedAt.value = null
    return request()
  }

  function changePage(nextPage) {
    if (
      !Number.isInteger(nextPage) ||
      nextPage < 1 ||
      nextPage > 2_147_483_647
    ) {
      return Promise.resolve(false)
    }
    return changeIntent(nextPage, pageSize.value)
  }

  function changePageSize(nextPageSize) {
    if (!PAGE_SIZES.has(nextPageSize)) return Promise.resolve(false)
    return changeIntent(1, nextPageSize)
  }

  function onAccepted() {
    if (disposed || !started) return Promise.resolve(false)
    if (page.value !== 1) return changeIntent(1, pageSize.value)
    return request()
  }

  function handleVisibilityChange() {
    if (disposed || !started || !active) return
    if (!visible()) {
      clearTimer()
      generation += 1
      return
    }
    request()
  }

  function setActive(nextActive) {
    if (disposed || active === nextActive) return Promise.resolve(false)
    active = nextActive
    generation += 1
    queued = false
    clearTimer()
    if (!active) {
      loading.value = false
      return Promise.resolve(true)
    }
    return started ? request() : Promise.resolve(true)
  }

  function dispose() {
    if (disposed) return
    disposed = true
    started = false
    generation += 1
    queued = false
    clearTimer()
    document.removeEventListener('visibilitychange', handleVisibilityChange)
    loading.value = false
  }

  return {
    page,
    pageSize,
    result,
    loading,
    error,
    lastUpdatedAt,
    start,
    refresh,
    changePage,
    changePageSize,
    onAccepted,
    setActive,
    dispose,
  }
}
