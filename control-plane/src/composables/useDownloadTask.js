import { computed, ref } from 'vue'

import { ApiError } from '../api/errors.js'
import {
  getDownloadTask, listDownloadTaskBatches, retryDownloadTask, resumeDownloadTask,
} from '../api/downloadTasks.js'

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const FAILURE_DELAYS = [5000, 10000, 30000]
const UNCERTAIN_CODES = new Set([
  'PERSISTENCE_FAILED', 'QUERY_FAILED', 'INTERNAL_ERROR',
  'SOURCE_AUTH_FAILED', 'SOURCE_PERMISSION_DENIED', 'SOURCE_RATE_LIMITED',
  'SOURCE_UNAVAILABLE', 'SOURCE_NETWORK_ERROR', 'SOURCE_TIMEOUT',
  'SOURCE_PAYLOAD_INVALID', 'SOURCE_RANGE_MISMATCH',
])

export function useDownloadTask() {
  const taskId = ref(null), task = ref(null), batches = ref(null)
  const page = ref(1), pageSize = ref(20), loading = ref(false)
  const taskError = ref(null), batchesError = ref(null)
  const taskUpdatedAt = ref(null), batchesUpdatedAt = ref(null)
  const operation = ref(null), operationError = ref(null), operationMessage = ref('')
  const invalidTaskId = ref(false), controlsFresh = ref(false)
  const isVisible = ref(document.visibilityState !== 'hidden')
  const disposed = ref(false)
  const notFound = computed(() => taskError.value?.code === 'TASK_NOT_FOUND')
  const controlsEnabled = computed(() => task.value !== null && taskError.value === null &&
    controlsFresh.value && operation.value === null && isVisible.value && !disposed.value)
  const canRetry = computed(() => controlsEnabled.value && task.value.canRetry)
  const canResume = computed(() => controlsEnabled.value && task.value.canResume)

  // One slot spans both GETs or one control. Invalidating an intent never releases it.
  let inFlight = null, timer = null, queued = false, generation = 0, context = 0, failures = 0

  function clearTimer() {
    if (timer !== null) clearTimeout(timer)
    timer = null
  }
  function available() {
    return !disposed.value && isVisible.value && taskId.value !== null && !invalidTaskId.value
  }
  function current(epoch) {
    return available() && epoch === generation
  }
  function schedule(delay) {
    clearTimer()
    if (!available()) return
    timer = setTimeout(() => { timer = null; refresh() }, delay)
  }
  function finish(job, epoch, delay) {
    if (inFlight !== job) return
    inFlight = null
    if (disposed.value) return
    loading.value = false
    if (!available()) return
    if (queued) refresh()
    else if (epoch === generation && delay !== null) schedule(delay)
  }

  function refresh() {
    if (!available()) return Promise.resolve(false)
    clearTimer()
    queued = true
    if (inFlight) return inFlight
    queued = false
    const epoch = generation, id = taskId.value
    const criteria = { page: page.value, pageSize: pageSize.value }
    loading.value = true
    let delay = null
    const job = Promise.allSettled([
      getDownloadTask(id), listDownloadTaskBatches(id, criteria),
    ]).then(([detail, batchResult]) => {
      if (!current(epoch)) return false
      if (detail.status === 'rejected' && detail.reason?.code === 'TASK_NOT_FOUND') {
        taskError.value = detail.reason
        task.value = batches.value = taskUpdatedAt.value = batchesUpdatedAt.value = null
        batchesError.value = null
        controlsFresh.value = false
        return false
      }
      if (detail.status === 'fulfilled') {
        task.value = detail.value
        taskError.value = null
        taskUpdatedAt.value = new Date()
        controlsFresh.value = true
      } else taskError.value = detail.reason
      if (batchResult.status === 'fulfilled') {
        batches.value = batchResult.value
        batchesError.value = null
        batchesUpdatedAt.value = new Date()
      } else batchesError.value = batchResult.reason
      if (detail.status === 'rejected' || batchResult.status === 'rejected') {
        delay = FAILURE_DELAYS[Math.min(failures++, FAILURE_DELAYS.length - 1)]
        return false
      }
      failures = 0
      if (['QUEUED', 'RUNNING'].includes(task.value.status)) delay = 2000
      return true
    }).finally(() => finish(job, epoch, delay))
    inFlight = job
    return job
  }

  function load(value) {
    if (disposed.value) return Promise.resolve(false)
    const valid = typeof value === 'string' && UUID.test(value)
    const id = valid ? value.toLowerCase() : null
    if (valid && id === taskId.value) return refresh()
    generation += 1
    context += 1
    clearTimer()
    queued = false
    taskId.value = id
    invalidTaskId.value = !valid
    task.value = batches.value = taskError.value = batchesError.value = null
    taskUpdatedAt.value = batchesUpdatedAt.value = null
    operation.value = operationError.value = null
    operationMessage.value = ''
    controlsFresh.value = false
    loading.value = false
    page.value = 1
    pageSize.value = 20
    failures = 0
    return refresh()
  }

  function changeIntent(nextPage, nextSize) {
    if (disposed.value || invalidTaskId.value || taskId.value === null) return Promise.resolve(false)
    if (page.value !== nextPage || pageSize.value !== nextSize) {
      generation += 1
      page.value = nextPage
      pageSize.value = nextSize
      batches.value = batchesError.value = batchesUpdatedAt.value = null
    }
    return refresh()
  }
  function changePage(value) {
    if (!Number.isInteger(value) || value < 1 || value > 2147483647) return Promise.resolve(false)
    return changeIntent(value, pageSize.value)
  }
  function changePageSize(value) {
    if (![20, 50, 100].includes(value)) return Promise.resolve(false)
    return changeIntent(1, value)
  }

  function control(type) {
    if (!(type === 'retry' ? canRetry.value : canResume.value)) return Promise.resolve(false)
    const id = taskId.value, version = task.value.version, controlContext = context
    const previous = inFlight
    operation.value = type
    operationError.value = null
    operationMessage.value = ''
    controlsFresh.value = false
    generation += 1
    clearTimer()
    queued = true
    const valid = () => available() && controlContext === context
    // Reserving the slot before waiting prevents the old GET finally from dispatching a query.
    const job = (async () => {
      if (previous) await previous
      if (!valid()) return false
      try {
        await (type === 'retry' ? retryDownloadTask : resumeDownloadTask)(id, version)
        if (!valid()) return false
        operationMessage.value = type === 'retry' ? '重试请求已接收' : '恢复请求已接收'
        return true
      } catch (error) {
        if (!valid()) return false
        operationError.value = error
        operationMessage.value = error.code === 'TASK_STATE_CONFLICT'
          ? '任务状态已变化，已重新查询'
          : !(error instanceof ApiError) || UNCERTAIN_CODES.has(error.code)
            ? '操作结果尚未确认，正在重新查询任务'
            : error.message
        return false
      }
    })().finally(() => {
      if (!disposed.value) operation.value = null
      // Every result invalidates the old permit until a fresh detail GET succeeds.
      finish(job, generation, null)
    })
    inFlight = job
    return job
  }

  function handleVisibilityChange() {
    if (disposed.value) return
    isVisible.value = document.visibilityState !== 'hidden'
    if (!isVisible.value) {
      clearTimer()
      generation += 1
      context += 1
      queued = false
      loading.value = false
    } else refresh()
  }
  document.addEventListener('visibilitychange', handleVisibilityChange)

  function dispose() {
    if (disposed.value) return
    disposed.value = true
    generation += 1
    context += 1
    clearTimer()
    queued = false
    loading.value = false
    operation.value = null
    document.removeEventListener('visibilitychange', handleVisibilityChange)
  }

  return {
    taskId, task, batches, page, pageSize, loading, taskError, batchesError,
    taskUpdatedAt, batchesUpdatedAt, operation, operationError, operationMessage,
    notFound, invalidTaskId, canRetry, canResume,
    load, refresh, changePage, changePageSize,
    retry: () => control('retry'), resume: () => control('resume'), dispose,
  }
}
