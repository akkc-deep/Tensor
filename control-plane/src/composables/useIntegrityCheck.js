import { computed, getCurrentScope, onScopeDispose, shallowReadonly, shallowRef } from 'vue'
import { ApiError, ClientError } from '../api/errors.js'
import {
  getIntegrityCapabilities, submitIntegrityCheck, listIntegrityChecks,
  getIntegrityCheck, listIntegrityResults, validateIntegrityCriteria, validateIntegrityCheckId,
} from '../api/integrityChecks.js'
import { createIntegritySubmission } from '../api/integrityDtos.js'

const PENDING_KEY = 'tensor.integrityChecks.pending.v1'
const UNCERTAIN_CODES = new Set(['PERSISTENCE_FAILED', 'QUERY_FAILED', 'INTERNAL_ERROR'])
const ACTIVE_STATUSES = new Set(['QUEUED', 'RUNNING'])

export function useIntegrityCheck({ storage } = {}) {
  const capability = shallowRef(null), capabilityLoading = shallowRef(false), capabilityError = shallowRef(null)
  const confirmedCapabilityHash = shallowRef(null), pendingSubmission = shallowRef(null)
  const submissionState = shallowRef('idle'), submissionError = shallowRef(null), recoveryError = shallowRef(null)
  const storageError = shallowRef(null)
  const receipt = shallowRef(null), recoveredTask = shallowRef(null), emptyRecovery = shallowRef(false)
  const checkId = shallowRef(null), detail = shallowRef(null), results = shallowRef(null)
  const resultsCriteria = shallowRef(Object.freeze({ page: 1, pageSize: 20 }))
  const detailError = shallowRef(null), resultsError = shallowRef(null), loading = shallowRef(false), connected = shallowRef(false)
  let disposed = false, active = true, resultsVisible = true, source = null
  let capabilityGeneration = 0, capabilityController = null
  let submissionGeneration = 0, submissionController = null, submissionJob = null
  let generation = 0, timer = null, round = null
  const requests = new Map()
  const canResend = computed(() => !disposed && active && submissionState.value === 'uncertain' && emptyRecovery.value)
  const getStorage = () => storage === undefined ? globalThis.sessionStorage : storage

  function readPending() {
    const raw = getStorage().getItem(PENDING_KEY)
    if (raw === null) return null
    const value = JSON.parse(raw)
    if (!value || Object.keys(value).length !== 2 || value.schemaVersion !== 1 || !Object.hasOwn(value, 'request')) {
      throw new TypeError('保存的检查请求无法识别。')
    }
    if (typeof value.request?.submissionId !== 'string') throw new TypeError('保存的检查请求无法识别。')
    return createIntegritySubmission(value.request, value.request.submissionId)
  }
  try {
    pendingSubmission.value = readPending()
    if (pendingSubmission.value) submissionState.value = 'uncertain'
  } catch {
    storageError.value = submissionError.value = new TypeError('保存的检查请求无法读取，请核对后重新准备检查。')
    submissionState.value = 'rejected'
  }

  function cancelSubmission() {
    submissionGeneration += 1
    submissionController?.abort()
    submissionJob = null
    emptyRecovery.value = false
    if (['submitting', 'recovering'].includes(submissionState.value)) submissionState.value = 'uncertain'
  }

  async function loadCapabilities(pluginId) {
    if (disposed || !active) return false
    if (source !== pluginId) {
      cancelSubmission()
      confirmedCapabilityHash.value = null
      capability.value = null
    }
    source = pluginId
    capabilityController?.abort()
    const controller = new AbortController(), epoch = ++capabilityGeneration
    capabilityController = controller
    capabilityLoading.value = true
    capabilityError.value = null
    const current = () => !disposed && active && epoch === capabilityGeneration && !controller.signal.aborted
    try {
      const value = await getIntegrityCapabilities(pluginId, { signal: controller.signal })
      if (!current()) return false
      if (value.capabilityHash !== capability.value?.capabilityHash || !value.localCheckAvailable) confirmedCapabilityHash.value = null
      capability.value = value
      return true
    } catch (error) {
      if (!current()) return false
      capabilityError.value = error
      confirmedCapabilityHash.value = null
      return false
    } finally {
      if (current()) capabilityLoading.value = false
    }
  }

  function confirmCapability(hash) {
    if (disposed || !active || capabilityLoading.value || capabilityError.value || !capability.value?.localCheckAvailable ||
      !hash || hash !== capability.value.capabilityHash) throw new TypeError('请先获取并确认当前检查口径。')
    confirmedCapabilityHash.value = hash
    return true
  }

  function prepareSubmission(selection) {
    if (disposed || !active || ['submitting', 'recovering', 'uncertain'].includes(submissionState.value) ||
      capabilityLoading.value || capabilityError.value || !capability.value?.localCheckAvailable ||
      selection?.pluginId !== capability.value.pluginId || selection?.capabilityHash !== confirmedCapabilityHash.value ||
      confirmedCapabilityHash.value !== capability.value.capabilityHash) throw new TypeError('当前检查尚未确认，不能准备新请求。')
    const snapshot = createIntegritySubmission(selection)
    cancelSubmission()
    pendingSubmission.value = snapshot
    submissionState.value = 'prepared'
    submissionError.value = recoveryError.value = receipt.value = recoveredTask.value = null
    return snapshot
  }

  function accepted(value, recovered) {
    if (recovered) recoveredTask.value = value
    else receipt.value = value
    submissionState.value = 'accepted'
    emptyRecovery.value = false
    try {
      if (readPending()?.submissionId === pendingSubmission.value.submissionId) getStorage().removeItem(PENDING_KEY)
    } catch {
      // Acceptance is already confirmed; a cache cleanup failure must never trigger another POST.
    }
  }

  function submissionOperation(work) {
    if (submissionJob) return submissionJob
    const epoch = ++submissionGeneration, controller = new AbortController()
    submissionController = controller
    const current = () => !disposed && active && epoch === submissionGeneration && !controller.signal.aborted
    const job = work(controller.signal, current).finally(() => { if (submissionJob === job) submissionJob = null })
    submissionJob = job
    return job
  }

  async function recover(signal, current) {
    const request = pendingSubmission.value
    submissionState.value = 'recovering'
    emptyRecovery.value = false
    recoveryError.value = null
    try {
      const found = await listIntegrityChecks({ page: 1, pageSize: 20, submissionId: request.submissionId }, { signal })
      if (!current()) return false
      if (found.total === 1n && found.items.length === 1 && found.items[0].submissionId.toLowerCase() === request.submissionId.toLowerCase()) {
        accepted(found.items[0], true)
        return true
      }
      if (found.total !== 0n || found.items.length !== 0) throw new ClientError('INVALID_RESPONSE')
      submissionState.value = 'uncertain'
      emptyRecovery.value = true
      return false
    } catch (error) {
      if (!current()) return false
      recoveryError.value = error
      submissionState.value = 'uncertain'
      return false
    }
  }

  function recoverSubmission() {
    if (disposed || !active || !pendingSubmission.value || !['uncertain', 'recovering'].includes(submissionState.value)) return Promise.resolve(false)
    return submissionOperation(recover)
  }

  function post() {
    const request = pendingSubmission.value
    try { getStorage().setItem(PENDING_KEY, JSON.stringify({ schemaVersion: 1, request })) }
    catch {
      const error = new TypeError('无法保存检查请求，本次未发送，请恢复会话存储后重试。')
      if (!submissionError.value || submissionError.value === storageError.value) submissionError.value = error
      storageError.value = error
      return Promise.resolve(false)
    }
    if (submissionError.value === storageError.value) submissionError.value = null
    storageError.value = null
    submissionState.value = 'submitting'
    emptyRecovery.value = false
    return submissionOperation(async (signal, current) => {
      try {
        const value = await submitIntegrityCheck(request, { signal })
        if (!current()) return false
        accepted(value, false)
        return true
      } catch (error) {
        if (!current()) return false
        submissionError.value ??= error
        if (error.code === 'INTEGRITY_DEFINITION_CHANGED') {
          submissionState.value = 'definition-changed'
          confirmedCapabilityHash.value = null
          await loadCapabilities(request.pluginId)
        } else if (!(error instanceof ApiError) || UNCERTAIN_CODES.has(error.code)) {
          return recover(signal, current)
        } else submissionState.value = 'rejected'
        return false
      }
    })
  }

  function submit() {
    if (disposed || !active) return Promise.resolve(false)
    if (submissionJob) return submissionJob
    if (submissionState.value !== 'prepared') return Promise.resolve(false)
    if (!capability.value?.localCheckAvailable || capabilityLoading.value || capabilityError.value ||
      pendingSubmission.value.capabilityHash !== confirmedCapabilityHash.value ||
      pendingSubmission.value.pluginId !== capability.value.pluginId) return Promise.resolve(false)
    return post()
  }
  function resendSubmission() { return canResend.value && !submissionJob ? post() : Promise.resolve(false) }

  function clearTimer() { if (timer !== null) clearTimeout(timer); timer = null }
  function invalidate() {
    generation += 1
    clearTimer()
    round?.controller.abort()
    round = null
    loading.value = false
  }
  function available() { return !disposed && active && checkId.value !== null }

  // A key remains reserved through abort until the underlying request settles.
  function request(key, controller, send) {
    const previous = requests.get(key)
    const start = () => controller.signal.aborted ? null : send()
    let pending
    try { pending = previous ? previous.catch(() => {}).then(start) : Promise.resolve(start()) }
    catch (error) { pending = Promise.reject(error) }
    const job = pending.finally(() => { if (requests.get(key) === job) requests.delete(key) })
    requests.set(key, job)
    return job
  }

  function refresh() {
    if (!available()) return Promise.resolve(false)
    clearTimer()
    if (round) return round.promise
    const id = checkId.value, criteria = resultsCriteria.value, epoch = generation
    const controller = new AbortController()
    const current = () => available() && epoch === generation && checkId.value === id && !controller.signal.aborted
    const job = { controller, promise: null }
    round = job
    loading.value = true
    let failed = false
    const consume = (key, send, valueRef, errorRef) => request(key, controller, send).then((value) => {
      if (!current()) return
      valueRef.value = value
      errorRef.value = null
    }, (error) => {
      if (!current()) return
      errorRef.value = error
      failed = true
      connected.value = false
      clearTimer()
    })
    const options = { signal: controller.signal }
    job.promise = Promise.all([
      consume(`detail:${id}`, () => getIntegrityCheck(id, options), detail, detailError),
      resultsVisible ? consume(`results:${id}:${JSON.stringify(criteria)}`, () => listIntegrityResults(id, criteria, options), results, resultsError) : Promise.resolve(),
    ]).then(() => {
      if (!current()) return false
      connected.value = !failed
      return !failed
    }).finally(() => {
      if (!current() || round !== job) return
      round = null
      loading.value = false
      if (!failed && ACTIVE_STATUSES.has(detail.value?.status)) timer = setTimeout(() => { timer = null; refresh() }, 2000)
    })
    return job.promise
  }

  function load(value) {
    if (disposed) return Promise.resolve(false)
    const id = validateIntegrityCheckId(value)
    active = true
    if (checkId.value === id) return refresh()
    invalidate()
    checkId.value = id
    detail.value = results.value = detailError.value = resultsError.value = null
    resultsCriteria.value = Object.freeze({ page: 1, pageSize: 20 })
    connected.value = false
    return refresh()
  }
  function changeResults(criteria = {}) {
    if (disposed) return Promise.resolve(false)
    const next = Object.freeze(validateIntegrityCriteria('results', criteria))
    if (JSON.stringify(next) === JSON.stringify(resultsCriteria.value)) return refresh()
    invalidate()
    resultsCriteria.value = next
    results.value = resultsError.value = null
    return refresh()
  }
  function setResultsVisible(value) {
    if (typeof value !== 'boolean') throw new TypeError('结果可见状态必须是布尔值。')
    if (disposed || value === resultsVisible) return Promise.resolve(false)
    invalidate()
    resultsVisible = value
    if (!value) resultsError.value = null
    return refresh()
  }
  function stopCreation() {
    capabilityGeneration += 1
    capabilityController?.abort()
    capabilityLoading.value = false
    cancelSubmission()
  }
  function setActive(value) {
    if (typeof value !== 'boolean') throw new TypeError('活动状态必须是布尔值。')
    if (disposed || value === active) return Promise.resolve(false)
    active = value
    invalidate()
    if (!value) stopCreation()
    return value ? refresh() : Promise.resolve(true)
  }
  function dispose() {
    if (disposed) return
    invalidate()
    stopCreation()
    disposed = true
  }
  if (getCurrentScope()) onScopeDispose(dispose)

  const state = { capability, capabilityLoading, capabilityError, confirmedCapabilityHash, pendingSubmission,
    submissionState, submissionError, recoveryError, storageError, receipt, recoveredTask, canResend,
    checkId, detail, results, resultsCriteria, detailError, resultsError, loading, connected }
  return {
    ...Object.fromEntries(Object.entries(state).map(([key, value]) => [key, shallowReadonly(value)])),
    loadCapabilities, confirmCapability, prepareSubmission, submit, recoverSubmission, resendSubmission,
    load, changeResults, setResultsVisible, refresh, reconnect: refresh, setActive, dispose,
  }
}
