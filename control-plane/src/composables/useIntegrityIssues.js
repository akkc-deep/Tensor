import { getCurrentScope, onScopeDispose, shallowReadonly, shallowRef } from 'vue'
import {
  listIntegrityIssues, validateIntegrityCheckId, validateIntegrityCriteria,
} from '../api/integrityChecks.js'

export function useIntegrityIssues() {
  const checkId = shallowRef(null)
  const criteria = shallowRef(Object.freeze({ page: 1, pageSize: 20 }))
  const page = shallowRef(null)
  const loading = shallowRef(false)
  const error = shallowRef(null)
  let generation = 0, controller = null, disposed = false

  function cancel(clear = false) {
    generation += 1
    controller?.abort()
    controller = null
    loading.value = false
    if (clear) {
      checkId.value = null
      criteria.value = Object.freeze({ page: 1, pageSize: 20 })
      page.value = null
      error.value = null
    }
  }

  async function load(id, next = {}) {
    if (disposed) return false
    const normalizedId = validateIntegrityCheckId(id)
    const normalizedCriteria = validateIntegrityCriteria('issues', next)
    const same = normalizedId === checkId.value &&
      JSON.stringify(normalizedCriteria) === JSON.stringify(criteria.value)
    cancel()
    const epoch = generation, requestController = new AbortController()
    controller = requestController
    checkId.value = normalizedId
    criteria.value = normalizedCriteria
    if (!same) page.value = null
    loading.value = true
    error.value = null
    const current = () => !disposed && epoch === generation && controller === requestController &&
      !requestController.signal.aborted
    try {
      const value = await listIntegrityIssues(normalizedId, normalizedCriteria, { signal: requestController.signal })
      if (!current()) return false
      page.value = value
      return true
    } catch (failure) {
      if (!current()) return false
      error.value = failure
      return false
    } finally {
      if (current()) loading.value = false
    }
  }

  function refresh() {
    return checkId.value ? load(checkId.value, criteria.value) : Promise.resolve(false)
  }

  function reset() { cancel(true) }
  function dispose() {
    if (disposed) return
    disposed = true
    cancel(true)
  }
  if (getCurrentScope()) onScopeDispose(dispose)

  return {
    checkId: shallowReadonly(checkId), criteria: shallowReadonly(criteria), page: shallowReadonly(page),
    loading: shallowReadonly(loading), error: shallowReadonly(error),
    load, refresh, reset, dispose,
  }
}
