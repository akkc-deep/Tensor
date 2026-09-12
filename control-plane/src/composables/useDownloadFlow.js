import { computed, ref, shallowRef } from 'vue'

import { listApis, listDataSources } from '../api/dataSources.js'
import { ApiError } from '../api/errors.js'
import {
  getDownloadCapabilities,
  listDownloadTasks,
  submitDownloadTask,
} from '../api/downloadTasks.js'
import {
  CorruptSubmissionError,
  clearPendingSubmission,
  createSubmissionRequest,
  readPendingSubmission,
  removePendingSubmission,
  writePendingSubmission,
} from '../utils/downloadTaskSubmission.js'

const EXPLICIT_REJECTIONS = new Set([
  'PARAM_REQUIRED',
  'PARAM_INVALID',
  'PLUGIN_DISABLED',
  'DATASET_MISCONFIGURED',
  'BATCH_DOWNLOAD_UNAVAILABLE',
  'TASK_STATE_CONFLICT',
  'TASK_DEFINITION_CHANGED',
  'TASK_QUEUE_FULL',
])

const STORAGE_MESSAGES = Object.freeze({
  READ: '无法读取本地提交记录，请重试。',
  WRITE: '无法保存提交记录，暂不能提交。',
  REMOVE: '无法清除本地提交记录，请重试。',
  CORRUPT: '本地提交记录无法恢复，请先核对近期任务。',
})

function storageFailure(kind) {
  return Object.freeze({ kind, message: STORAGE_MESSAGES[kind] })
}

export function useDownloadFlow({ onAccepted } = {}) {
  const metadataState = ref('INITIAL')
  const sources = shallowRef([])
  const apis = shallowRef([])
  const selectedPluginId = ref('')
  const selectedApiName = ref('')
  const capabilities = shallowRef(null)
  const mode = ref('SINGLE')
  const formKey = ref(0)
  const metadataError = shallowRef(null)

  const submissionState = ref('IDLE')
  const pendingSubmission = shallowRef(null)
  const receipt = shallowRef(null)
  const recoveredTask = shallowRef(null)
  const submissionError = shallowRef(null)
  const storageError = shallowRef(null)

  let metadataGeneration = 0
  let submissionGeneration = 0
  let failedMetadata = null
  let failedRemoval = null
  let disposed = false

  const selectedSource = computed(
    () => sources.value.find(({ pluginId }) => pluginId === selectedPluginId.value) ?? null,
  )
  const selectedApi = computed(
    () => apis.value.find(({ apiName }) => apiName === selectedApiName.value) ?? null,
  )
  const parameters = computed(
    () => capabilities.value?.[mode.value.toLowerCase()]?.parameters ?? [],
  )
  const locked = computed(
    () => submissionState.value === 'SUBMITTING' || submissionState.value === 'RECOVERING',
  )
  const modeAvailable = computed(() =>
    mode.value === 'SINGLE'
      ? capabilities.value?.single.available === true
      : capabilities.value?.range.availability === 'AVAILABLE',
  )
  const storageBlocksSubmit = computed(
    () =>
      storageError.value?.kind === 'READ' ||
      storageError.value?.kind === 'CORRUPT' ||
      (storageError.value?.kind === 'REMOVE' && failedRemoval?.submissionId === null),
  )
  const canSubmit = computed(
    () =>
      !disposed &&
      !locked.value &&
      pendingSubmission.value === null &&
      !storageBlocksSubmit.value &&
      metadataState.value === 'READY' &&
      selectedSource.value?.downloadAvailable === true &&
      selectedApi.value !== null &&
      capabilities.value !== null &&
      modeAvailable.value,
  )

  function resetCapabilities() {
    capabilities.value = null
    mode.value = 'SINGLE'
    formKey.value += 1
  }

  function beginMetadata() {
    metadataError.value = null
    failedMetadata = null
    metadataState.value = 'LOADING'
    return ++metadataGeneration
  }

  async function load() {
    if (disposed || locked.value) return false
    const generation = beginMetadata()
    sources.value = []
    apis.value = []
    selectedPluginId.value = ''
    selectedApiName.value = ''
    resetCapabilities()
    try {
      const loaded = await listDataSources()
      if (disposed || generation !== metadataGeneration) return false
      sources.value = loaded
      metadataState.value = 'READY'
      return true
    } catch (failure) {
      if (disposed || generation !== metadataGeneration) return false
      metadataError.value = failure
      failedMetadata = { type: 'SOURCES' }
      metadataState.value = 'FAILURE'
      return false
    }
  }

  async function selectSource(pluginId) {
    if (
      disposed ||
      locked.value ||
      (pluginId !== '' && !sources.value.some((source) => source.pluginId === pluginId))
    ) return false
    const generation = beginMetadata()
    selectedPluginId.value = pluginId
    selectedApiName.value = ''
    apis.value = []
    resetCapabilities()
    if (pluginId === '') {
      metadataState.value = 'READY'
      return true
    }
    try {
      const loaded = await listApis(pluginId)
      if (disposed || generation !== metadataGeneration) return false
      apis.value = loaded
      metadataState.value = 'READY'
      return true
    } catch (failure) {
      if (disposed || generation !== metadataGeneration) return false
      metadataError.value = failure
      failedMetadata = { type: 'APIS', pluginId }
      metadataState.value = 'FAILURE'
      return false
    }
  }

  async function selectApi(apiName) {
    if (
      disposed ||
      locked.value ||
      (apiName !== '' && !apis.value.some((api) => api.apiName === apiName))
    ) return false
    const generation = beginMetadata()
    selectedApiName.value = apiName
    resetCapabilities()
    if (apiName === '') {
      metadataState.value = 'READY'
      return true
    }
    const pluginId = selectedPluginId.value
    try {
      const loaded = await getDownloadCapabilities(pluginId, apiName)
      if (disposed || generation !== metadataGeneration) return false
      capabilities.value = loaded
      mode.value = loaded.range.availability === 'AVAILABLE' ? 'RANGE' : 'SINGLE'
      formKey.value += 1
      metadataState.value = 'READY'
      return true
    } catch (failure) {
      if (disposed || generation !== metadataGeneration) return false
      metadataError.value = failure
      failedMetadata = { type: 'CAPABILITIES', pluginId, apiName }
      metadataState.value = 'FAILURE'
      return false
    }
  }

  function selectMode(nextMode) {
    if (disposed || locked.value || !capabilities.value) return false
    const available =
      nextMode === 'SINGLE'
        ? capabilities.value.single.available === true
        : nextMode === 'RANGE' && capabilities.value.range.availability === 'AVAILABLE'
    if (!available) return false
    mode.value = nextMode
    formKey.value += 1
    return true
  }

  async function retryMetadata() {
    if (disposed || locked.value || failedMetadata === null) return false
    const failed = failedMetadata
    if (failed.type === 'SOURCES') return load()
    if (failed.type === 'APIS' && selectedPluginId.value === failed.pluginId) {
      return selectSource(failed.pluginId)
    }
    if (
      failed.type === 'CAPABILITIES' &&
      selectedPluginId.value === failed.pluginId &&
      selectedApiName.value === failed.apiName
    ) {
      return selectApi(failed.apiName)
    }
    return false
  }

  function notifyAccepted(value) {
    if (typeof onAccepted !== 'function') return
    try {
      onAccepted(value)
    } catch {
      // Acceptance remains a server fact even if a view callback fails.
    }
  }

  function clearMatchingPending(request) {
    try {
      const removed = removePendingSubmission(request.submissionId)
      storageError.value = null
      failedRemoval = null
      if (removed) pendingSubmission.value = null
      else pendingSubmission.value = readPendingSubmission()
      return true
    } catch {
      storageError.value = storageFailure('REMOVE')
      failedRemoval = { submissionId: request.submissionId }
      pendingSubmission.value = null
      return false
    }
  }

  function accept(value, request, recovered = false) {
    submissionState.value = 'ACCEPTED'
    if (recovered) {
      recoveredTask.value = value
      receipt.value = null
    } else {
      receipt.value = value
      recoveredTask.value = null
      submissionError.value = null
    }
    clearMatchingPending(request)
    notifyAccepted(value)
  }

  function rejectInitial(failure, request) {
    submissionError.value = failure
    submissionState.value = 'REJECTED'
    clearMatchingPending(request)
  }

  function markUncertain(failure = null) {
    submissionError.value = failure
    submissionState.value = 'UNCERTAIN'
  }

  async function post(request, replay) {
    const generation = ++submissionGeneration
    submissionError.value = null
    receipt.value = null
    recoveredTask.value = null
    submissionState.value = 'SUBMITTING'
    try {
      const result = await submitDownloadTask(request)
      if (disposed || generation !== submissionGeneration) return false
      accept(result, request)
      return true
    } catch (failure) {
      if (disposed || generation !== submissionGeneration) return false
      if (!replay && failure instanceof ApiError && failure.code === 'SUBMISSION_CONFLICT') {
        return findPending(request, failure)
      }
      if (!replay && failure instanceof ApiError && EXPLICIT_REJECTIONS.has(failure.code)) {
        rejectInitial(failure, request)
      } else {
        markUncertain(failure)
      }
      return false
    }
  }

  async function submit(params) {
    if (disposed || !canSubmit.value) return false
    let request
    try {
      request = createSubmissionRequest(
        {
          pluginId: selectedPluginId.value,
          apiName: selectedApiName.value,
          mode: mode.value,
          params,
        },
        parameters.value.map(({ name }) => name),
      )
      writePendingSubmission(request)
    } catch {
      storageError.value = storageFailure('WRITE')
      return false
    }
    storageError.value = null
    failedRemoval = null
    pendingSubmission.value = request
    return post(request, false)
  }

  async function findPending(request, retainedError = null) {
    const generation = ++submissionGeneration
    submissionError.value = retainedError
    receipt.value = null
    recoveredTask.value = null
    submissionState.value = 'RECOVERING'
    try {
      const page = await listDownloadTasks({ submissionId: request.submissionId })
      if (disposed || generation !== submissionGeneration) return false
      if (page.total === 1n && page.items.length === 1) {
        accept(page.items[0], request, true)
        if (retainedError) submissionError.value = retainedError
        return true
      }
      markUncertain(retainedError)
      return false
    } catch (failure) {
      if (disposed || generation !== submissionGeneration) return false
      markUncertain(failure)
      return false
    }
  }

  async function recoverSubmission() {
    if (disposed || locked.value) return false
    let request = pendingSubmission.value
    if (request === null) {
      try {
        request = readPendingSubmission()
      } catch (failure) {
        const kind = failure instanceof CorruptSubmissionError ? 'CORRUPT' : 'READ'
        storageError.value = storageFailure(kind)
        if (kind === 'CORRUPT') submissionState.value = 'UNCERTAIN'
        return false
      }
    }
    storageError.value = null
    pendingSubmission.value = request
    if (request === null) {
      if (submissionState.value !== 'ACCEPTED' && submissionState.value !== 'REJECTED') {
        submissionState.value = 'IDLE'
      }
      return true
    }
    return findPending(request)
  }

  async function replaySubmission() {
    if (disposed || locked.value || submissionState.value !== 'UNCERTAIN' || !pendingSubmission.value) {
      return false
    }
    return post(pendingSubmission.value, true)
  }

  async function retryStorage() {
    if (disposed || storageError.value === null) return false
    if (storageError.value.kind === 'READ') return recoverSubmission()
    if (storageError.value.kind === 'WRITE') {
      storageError.value = null
      return true
    }
    if (storageError.value.kind === 'REMOVE' && failedRemoval?.submissionId) {
      try {
        removePendingSubmission(failedRemoval.submissionId)
        failedRemoval = null
        storageError.value = null
        return true
      } catch {
        return false
      }
    }
    if (storageError.value.kind === 'REMOVE' && failedRemoval?.submissionId === null) {
      try {
        clearPendingSubmission()
        failedRemoval = null
        storageError.value = null
        submissionState.value = 'IDLE'
        return true
      } catch {
        return false
      }
    }
    return false
  }

  function clearCorruptSubmission() {
    if (disposed || storageError.value?.kind !== 'CORRUPT') return false
    try {
      clearPendingSubmission()
      storageError.value = null
      pendingSubmission.value = null
      submissionState.value = 'IDLE'
      return true
    } catch {
      storageError.value = storageFailure('REMOVE')
      failedRemoval = { submissionId: null }
      return false
    }
  }

  function dispose() {
    if (disposed) return
    disposed = true
    metadataGeneration += 1
    submissionGeneration += 1
  }

  return {
    metadataState,
    sources,
    apis,
    selectedPluginId,
    selectedApiName,
    selectedSource,
    selectedApi,
    capabilities,
    mode,
    parameters,
    formKey,
    metadataError,
    submissionState,
    pendingSubmission,
    receipt,
    recoveredTask,
    submissionError,
    storageError,
    locked,
    canSubmit,
    load,
    selectSource,
    selectApi,
    selectMode,
    retryMetadata,
    submit,
    recoverSubmission,
    replaySubmission,
    retryStorage,
    clearCorruptSubmission,
    dispose,
  }
}
