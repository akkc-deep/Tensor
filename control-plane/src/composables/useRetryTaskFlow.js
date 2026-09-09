import { computed, readonly, ref, shallowRef } from 'vue'

import { executeRetryTask, getRetryTask, listRetryTasks } from '../api/retryTasks.js'
import { ApiError, ClientError } from '../api/errors.js'

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const PAGE_SIZES = new Set([20, 50, 100])
const INVALID_TASK = Object.freeze({ message: '任务标识无效', requestId: null })

function filters(value = {}) {
  return Object.freeze({
    pluginId: typeof value?.pluginId === 'string' ? value.pluginId : '',
    apiName: typeof value?.apiName === 'string' ? value.apiName : '',
  })
}

function rangeMode(status) {
  if (status === 'NOT_APPLICABLE') return false
  return ['RECORDED', 'NOT_RECORDED'].includes(status)
}

/** @param {{isDownloadLocked: () => boolean}} options */
export function useRetryTaskFlow({ isDownloadLocked } = { isDownloadLocked: () => false }) {
  const listState = ref('INITIAL')
  const listResult = shallowRef(null)
  const listError = shallowRef(null)
  const detailState = ref('INITIAL')
  const detail = shallowRef(null)
  const detailError = shallowRef(null)
  const selectedTaskId = ref(null)
  const page = ref(1)
  const pageSize = ref(20)
  const appliedFilters = shallowRef(filters())
  const executionState = ref('INITIAL')
  const executionResult = shallowRef(null)
  const executionError = shallowRef(null)
  const executionContext = shallowRef(null)
  const needsRefresh = ref(false)
  let listGeneration = 0
  let detailGeneration = 0
  let settling = false
  let hasQueuedSelection = false
  let queuedSelection

  const locked = computed(() => executionState.value === 'SUBMITTING')
  const downloadLocked = () => typeof isDownloadLocked === 'function' && isDownloadLocked() === true
  const canExecute = computed(() =>
    !locked.value && !downloadLocked() && !needsRefresh.value && detailState.value === 'SUCCESS' &&
    detail.value?.canExecute === true && detail.value.retrying === false)
  const executionMessage = computed(() => {
    if (locked.value) {
      return executionContext.value?.rangeMode
        ? '区间下载已开始，不可终止，请等待结果。'
        : '下载请求已提交，请等待结果。'
    }
    if (downloadLocked()) return '已有下载正在执行。'
    if (executionResult.value?.taskId === null && executionResult.value?.remainingFailedUnits === 0 &&
      detailState.value === 'NOT_FOUND') {
      return '本轮响应确认当前任务已无剩余失败项。'
    }
    if (detailState.value === 'NOT_FOUND') return ''
    if (needsRefresh.value) return '记录可能已变化，请刷新详情。'
    if (detailState.value === 'SUCCESS' && detail.value.executionBlocker) {
      return detail.value.executionBlocker.message
    }
    return ''
  })

  function clearExecution() {
    executionState.value = 'INITIAL'
    executionResult.value = null
    executionError.value = null
    executionContext.value = null
  }

  async function requestList(targetPage, targetPageSize, targetFilters) {
    const currentGeneration = ++listGeneration
    listResult.value = null
    listError.value = null
    listState.value = 'LOADING'
    try {
      const result = await listRetryTasks({
        ...(targetFilters.pluginId.trim() ? { pluginId: targetFilters.pluginId } : {}),
        ...(targetFilters.apiName.trim() ? { apiName: targetFilters.apiName } : {}),
        page: targetPage,
        pageSize: targetPageSize,
      })
      if (currentGeneration !== listGeneration) return false
      listResult.value = result
      page.value = result.page
      pageSize.value = result.pageSize
      listState.value = result.items.length === 0 ? 'EMPTY' : 'SUCCESS'
      return true
    } catch (failure) {
      if (currentGeneration !== listGeneration) return false
      listError.value = failure
      listState.value = 'FAILURE'
      return false
    }
  }

  async function loadList(nextFilters = {}) {
    if (locked.value) return false
    appliedFilters.value = filters(nextFilters)
    page.value = 1
    return requestList(1, pageSize.value, appliedFilters.value)
  }

  async function changePage(nextPage) {
    if (locked.value || !Number.isSafeInteger(nextPage) || nextPage < 1) return false
    return requestList(nextPage, pageSize.value, appliedFilters.value)
  }

  async function changePageSize(nextSize) {
    if (locked.value || !PAGE_SIZES.has(nextSize)) return false
    page.value = 1
    pageSize.value = nextSize
    return requestList(1, nextSize, appliedFilters.value)
  }

  async function refreshList() {
    if (locked.value) return false
    return requestList(page.value, pageSize.value, appliedFilters.value)
  }

  function resetDetail({ clearResult = true } = {}) {
    detailGeneration += 1
    selectedTaskId.value = null
    detailState.value = 'INITIAL'
    detail.value = null
    detailError.value = null
    needsRefresh.value = false
    if (clearResult) clearExecution()
  }

  function invalidDetail() {
    detailGeneration += 1
    selectedTaskId.value = null
    detail.value = null
    detailError.value = INVALID_TASK
    detailState.value = 'FAILURE'
    needsRefresh.value = false
    clearExecution()
  }

  async function requestDetail(taskId, { refreshListOnNotFound = true } = {}) {
    const currentGeneration = ++detailGeneration
    detail.value = null
    detailError.value = null
    detailState.value = 'LOADING'
    needsRefresh.value = true
    try {
      const result = await getRetryTask(taskId)
      if (currentGeneration !== detailGeneration || selectedTaskId.value !== taskId) return false
      detail.value = result
      detailState.value = 'SUCCESS'
      needsRefresh.value = false
      return true
    } catch (failure) {
      if (currentGeneration !== detailGeneration || selectedTaskId.value !== taskId) return false
      detailError.value = failure
      detailState.value = failure instanceof ApiError && failure.code === 'RETRY_TASK_NOT_FOUND'
        ? 'NOT_FOUND'
        : 'FAILURE'
      if (detailState.value === 'NOT_FOUND' && refreshListOnNotFound) await refreshList()
      return false
    }
  }

  async function applySelection(rawTaskId) {
    if (rawTaskId === null) {
      resetDetail()
      return true
    }
    if (typeof rawTaskId !== 'string' || !UUID.test(rawTaskId)) {
      invalidDetail()
      return false
    }
    const taskId = rawTaskId.toLowerCase()
    if (selectedTaskId.value === taskId && detailState.value !== 'INITIAL') return true
    clearExecution()
    selectedTaskId.value = taskId
    needsRefresh.value = true
    return requestDetail(taskId)
  }

  async function selectTask(taskId) {
    if (settling) {
      hasQueuedSelection = true
      queuedSelection = taskId
      return true
    }
    return applySelection(taskId)
  }

  async function refreshDetail() {
    if (locked.value || selectedTaskId.value === null) return false
    return requestDetail(selectedTaskId.value)
  }

  async function execute() {
    if (!canExecute.value) return false
    const currentDetail = detail.value
    const taskId = selectedTaskId.value
    const params = Object.freeze({ ...currentDetail.taskParams })
    executionContext.value = Object.freeze({
      operation: 'RETRY',
      taskId,
      pluginId: currentDetail.pluginId,
      apiName: currentDetail.apiName,
      params,
      rangeMode: rangeMode(currentDetail.originalDateRangeStatus),
    })
    executionResult.value = null
    executionError.value = null
    needsRefresh.value = true
    executionState.value = 'SUBMITTING'
    settling = true
    let success = false
    let refreshAfterNotFound = false

    try {
      const result = await executeRetryTask(taskId)
      if (result.pluginId !== currentDetail.pluginId || result.apiName !== currentDetail.apiName) {
        throw new ClientError('INVALID_RESPONSE', result.requestId)
      }
      executionResult.value = result
      executionState.value = result.outcome
      success = true
      await refreshList()
      if (result.taskId === taskId) {
        await requestDetail(taskId, { refreshListOnNotFound: false })
      } else if (result.taskId === null && result.remainingFailedUnits === 0) {
        detailGeneration += 1
        detail.value = null
        detailError.value = null
        detailState.value = 'NOT_FOUND'
      }
    } catch (failure) {
      const snapshot = failure instanceof ApiError ? failure.downloadResult : null
      if (snapshot && (snapshot.pluginId !== currentDetail.pluginId || snapshot.apiName !== currentDetail.apiName)) {
        executionError.value = new ClientError('INVALID_RESPONSE', failure.requestId)
        executionState.value = 'UNCONFIRMED'
      } else {
        const normalizedFailure = failure instanceof ApiError || failure instanceof ClientError
          ? failure
          : new ClientError('UNEXPECTED')
        executionError.value = normalizedFailure
        executionResult.value = snapshot
        executionState.value = snapshot || normalizedFailure instanceof ClientError ? 'UNCONFIRMED' : 'FAILURE'
        if (failure instanceof ApiError && failure.code === 'RETRY_TASK_NOT_FOUND') {
          detailGeneration += 1
          detail.value = null
          detailError.value = failure
          detailState.value = 'NOT_FOUND'
          refreshAfterNotFound = true
          executionState.value = 'FAILURE'
        }
      }
      if (refreshAfterNotFound) await refreshList()
    } finally {
      settling = false
      if (hasQueuedSelection) {
        const next = queuedSelection
        hasQueuedSelection = false
        queuedSelection = undefined
        await applySelection(next)
      }
    }
    return success
  }

  return {
    listState,
    listResult,
    listError,
    detailState,
    detail,
    detailError,
    selectedTaskId,
    page,
    pageSize,
    appliedFilters,
    executionState,
    executionResult,
    executionError,
    executionContext: readonly(executionContext),
    locked,
    canExecute,
    needsRefresh,
    executionMessage,
    loadList,
    changePage,
    changePageSize,
    refreshList,
    selectTask,
    refreshDetail,
    execute,
  }
}
