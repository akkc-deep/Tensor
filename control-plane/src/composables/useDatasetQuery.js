import { computed, ref, shallowRef } from 'vue'

import { queryDataset } from '../api/datasets.js'

function copyRequest(request) {
  return { ...request, criteria: { ...request.criteria } }
}

export function useDatasetQuery() {
  const state = ref('UNQUERIED')
  const result = shallowRef(null)
  const error = shallowRef(null)
  const page = ref(1)
  const pageSize = ref(50)
  let generation = 0
  let currentRequest = null
  let failedRequest = null

  const loading = computed(() => state.value === 'LOADING')
  const canRetry = computed(() =>
    state.value === 'FAILURE' &&
    failedRequest !== null &&
    error.value?.retryable === true,
  )

  async function execute(request) {
    const saved = copyRequest(request)
    const currentGeneration = ++generation
    currentRequest = saved
    failedRequest = null
    page.value = saved.page
    pageSize.value = saved.pageSize
    result.value = null
    error.value = null
    state.value = 'LOADING'

    try {
      const response = await queryDataset(saved.pluginId, saved.apiName, {
        ...saved.criteria,
        page: saved.page,
        pageSize: saved.pageSize,
      })
      if (currentGeneration !== generation) return false

      result.value = response
      page.value = response.page
      pageSize.value = response.pageSize
      currentRequest = copyRequest({
        ...saved,
        page: response.page,
        pageSize: response.pageSize,
      })
      failedRequest = null
      state.value = response.totalElements === 0 ? 'EMPTY' : 'SUCCESS'
      return true
    } catch (failure) {
      if (currentGeneration !== generation) return false

      result.value = null
      error.value = failure
      failedRequest = saved
      currentRequest = null
      state.value = 'FAILURE'
      return false
    }
  }

  function query(pluginId, apiName, criteria) {
    return execute({
      pluginId,
      apiName,
      criteria,
      page: 1,
      pageSize: pageSize.value,
    })
  }

  function changePage(nextPage) {
    if (loading.value || currentRequest === null || nextPage === page.value) {
      return Promise.resolve(false)
    }
    return execute({ ...currentRequest, page: nextPage })
  }

  function changePageSize(nextPageSize) {
    if (
      loading.value ||
      currentRequest === null ||
      nextPageSize === pageSize.value
    ) {
      return Promise.resolve(false)
    }
    return execute({ ...currentRequest, page: 1, pageSize: nextPageSize })
  }

  function retry() {
    if (loading.value || !canRetry.value) return Promise.resolve(false)
    return execute(failedRequest)
  }

  function reset() {
    generation += 1
    currentRequest = null
    failedRequest = null
    state.value = 'UNQUERIED'
    result.value = null
    error.value = null
    page.value = 1
    pageSize.value = 50
  }

  return {
    state,
    result,
    error,
    page,
    pageSize,
    loading,
    canRetry,
    query,
    changePage,
    changePageSize,
    retry,
    reset,
  }
}
