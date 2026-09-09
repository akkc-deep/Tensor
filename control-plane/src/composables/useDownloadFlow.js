import { computed, readonly, ref, shallowRef } from 'vue'

import { listApis, listDataSources } from '../api/dataSources.js'
import { downloadDataset } from '../api/downloads.js'
import { ApiError, ClientError } from '../api/errors.js'
import { isRangeMode } from '../utils/downloadPolicy.js'

export function useDownloadFlow() {
  const state = ref('INITIAL')
  const sources = shallowRef([])
  const apis = shallowRef([])
  const selectedPluginId = ref('')
  const selectedApiName = ref('')
  const result = shallowRef(null)
  const error = shallowRef(null)
  const executionContext = shallowRef(null)
  let generation = 0
  let failedOperation = null

  const selectedSource = computed(
    () =>
      sources.value.find(
        (source) => selectedPluginId.value === source.pluginId,
      ) ?? null,
  )
  const selectedApi = computed(
    () =>
      apis.value.find((api) => selectedApiName.value === api.apiName) ??
      null,
  )
  const locked = computed(() => state.value === 'SUBMITTING')
  const canSubmit = computed(
    () =>
      selectedSource.value?.downloadAvailable === true &&
      selectedApi.value !== null &&
      state.value !== 'METADATA_LOADING' &&
      state.value !== 'SUBMITTING',
  )
  const canRetry = computed(
    () =>
      state.value === 'FAILURE' &&
      failedOperation !== null &&
      error.value?.retryable === true,
  )

  function clearDownloadState() {
    result.value = null
    error.value = null
    failedOperation = null
    executionContext.value = null
  }

  async function load() {
    if (locked.value) return false

    const currentGeneration = ++generation
    sources.value = []
    apis.value = []
    selectedPluginId.value = ''
    selectedApiName.value = ''
    clearDownloadState()
    state.value = 'METADATA_LOADING'

    try {
      const loadedSources = await listDataSources()
      if (currentGeneration !== generation) return false
      sources.value = loadedSources
      state.value = 'READY'
      return true
    } catch (failure) {
      if (currentGeneration !== generation) return false
      error.value = failure
      failedOperation = { type: 'SOURCES' }
      state.value = 'FAILURE'
      return false
    }
  }

  async function selectSource(pluginId) {
    if (locked.value) return false

    const currentGeneration = ++generation
    selectedPluginId.value = pluginId
    selectedApiName.value = ''
    apis.value = []
    clearDownloadState()
    if (pluginId.length === 0) {
      state.value = 'READY'
      return true
    }

    state.value = 'METADATA_LOADING'
    try {
      const loadedApis = await listApis(pluginId)
      if (currentGeneration !== generation) return false
      apis.value = loadedApis
      state.value = 'READY'
      return true
    } catch (failure) {
      if (currentGeneration !== generation) return false
      error.value = failure
      failedOperation = { type: 'APIS', pluginId }
      state.value = 'FAILURE'
      return false
    }
  }

  function selectApi(apiName) {
    if (locked.value) return false

    generation += 1
    selectedApiName.value = apiName
    clearDownloadState()
    state.value = 'READY'
    return true
  }

  function parametersChanged() {
    if (locked.value) return false
    generation += 1
    clearDownloadState()
    state.value = 'READY'
    return true
  }

  async function submit(params) {
    if (locked.value || !canSubmit.value) return false

    const request = {
      pluginId: selectedPluginId.value,
      apiName: selectedApiName.value,
      params: { ...params },
    }
    const currentGeneration = ++generation
    clearDownloadState()
    executionContext.value = Object.freeze({
      ...request,
      params: Object.freeze({ ...request.params }),
      rangeMode: isRangeMode(selectedApi.value.downloadPolicy?.mode),
    })
    state.value = 'SUBMITTING'

    try {
      const response = await downloadDataset(request)
      if (currentGeneration !== generation) return false
      result.value = response
      state.value = response.outcome
      return true
    } catch (failure) {
      if (currentGeneration !== generation) return false
      if (failure instanceof ApiError) {
        const snapshot = failure.downloadResult
        if (snapshot && (snapshot.pluginId !== request.pluginId || snapshot.apiName !== request.apiName)) {
          error.value = new ClientError('INVALID_RESPONSE', failure.requestId)
          state.value = 'UNCONFIRMED'
        } else {
          error.value = failure
          result.value = snapshot ?? null
          state.value = snapshot ? 'UNCONFIRMED' : 'FAILURE'
        }
      } else {
        error.value = failure instanceof ClientError ? failure : new ClientError('UNEXPECTED')
        state.value = 'UNCONFIRMED'
      }
      return false
    }
  }

  async function retry() {
    if (locked.value || !canRetry.value) return false

    const operation = failedOperation
    if (operation.type === 'SOURCES') return load()
    if (
      operation.type === 'APIS' &&
      selectedPluginId.value === operation.pluginId
    ) {
      return selectSource(operation.pluginId)
    }
    return false
  }

  return {
    state,
    sources,
    apis,
    selectedPluginId,
    selectedApiName,
    result,
    error,
    executionContext: readonly(executionContext),
    selectedSource,
    selectedApi,
    locked,
    canSubmit,
    canRetry,
    load,
    selectSource,
    selectApi,
    parametersChanged,
    submit,
    retry,
  }
}
