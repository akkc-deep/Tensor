import { effectScope } from 'vue'
import { flushPromises } from '@vue/test-utils'
import examples from '../../../docs/contracts/integrity-check-examples.json'
import { ApiError, ClientError } from '../api/errors.js'
import * as api from '../api/integrityChecks.js'
import { useIntegrityCheck } from './useIntegrityCheck.js'

vi.mock('../api/integrityChecks.js', () => ({
  getIntegrityCapabilities: vi.fn(), submitIntegrityCheck: vi.fn(), listIntegrityChecks: vi.fn(),
  getIntegrityCheck: vi.fn(), listIntegrityResults: vi.fn(),
  validateIntegrityCheckId: (value) => {
    if (typeof value !== 'string' || !/^[a-f\d]{8}(-[a-f\d]{4}){3}-[a-f\d]{12}$/i.test(value)) throw new TypeError('Invalid ID')
    return value.toLowerCase()
  },
  validateIntegrityCriteria: (kind, criteria) => ({ page: 1, pageSize: 20, ...criteria }),
}))

const ID = '33333333-3333-4333-8333-333333333333'
const OTHER = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
const REQUEST_ID = '11111111-1111-4111-8111-111111111111'
const KEY = 'tensor.integrityChecks.pending.v1'
const example = (name) => structuredClone(examples.examples.find((e) => e.name === name).value)
const selection = () => { const { submissionId, ...value } = example('submission'); return value }
const capability = () => example('availableCapability')
const page = (items = []) => ({ page: 1, pageSize: 20, total: BigInt(items.length), items })
const task = (id = ID, status = 'RUNNING') => ({ ...example('runningDetail'), checkId: id, status })
const error = (code = 'QUERY_FAILED') => new ApiError({ code, message: code, requestId: REQUEST_ID, retryable: true, fieldErrors: [] })
function deferred() { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
const flows = []
const make = (options) => { const flow = useIntegrityCheck(options); flows.push(flow); return flow }
async function prepared() {
  const flow = make()
  await flow.loadCapabilities('fixture')
  flow.confirmCapability(capability().capabilityHash)
  flow.prepareSubmission(selection())
  return flow
}
beforeEach(() => {
  vi.resetAllMocks()
  vi.useFakeTimers()
  sessionStorage.clear()
  api.getIntegrityCapabilities.mockResolvedValue(capability())
  api.submitIntegrityCheck.mockImplementation(async (request) => ({ checkId: ID, submissionId: request.submissionId, pluginId: request.pluginId, status: 'QUEUED' }))
  api.listIntegrityChecks.mockResolvedValue(page())
  api.getIntegrityCheck.mockImplementation(async (id) => task(id))
  api.listIntegrityResults.mockImplementation(async (id, criteria) => ({ ...page(), ...criteria }))
})
afterEach(() => { flows.splice(0).forEach((flow) => flow.dispose?.()); vi.clearAllTimers(); vi.useRealTimers() })

it('locks double submissions and stores an immutable request before one POST', async () => {
  const flow = await prepared(), pending = deferred()
  api.submitIntegrityCheck.mockReturnValueOnce(pending.promise)
  const snapshot = flow.pendingSubmission.value
  expect(Object.isFrozen(snapshot.symbols)).toBe(true)
  const first = flow.submit(), second = flow.submit()
  expect(api.submitIntegrityCheck).toHaveBeenCalledTimes(1)
  expect(JSON.parse(sessionStorage.getItem(KEY)).request).toEqual(snapshot)
  expect(() => flow.prepareSubmission(selection())).toThrow(TypeError)
  pending.resolve({ checkId: ID, submissionId: snapshot.submissionId })
  await Promise.all([first, second])
  expect(flow.submissionState.value).toBe('accepted')
  expect(sessionStorage.getItem(KEY)).toBeNull()
  await flow.submit()
  expect(api.submitIntegrityCheck).toHaveBeenCalledTimes(1)
})

it('requires loaded, explicitly confirmed capability and preserves form order and omitted APIs', async () => {
  const flow = make(), form = selection()
  delete form.apiNames
  form.symbols = [' 600000.sh ', '000001.SZ']
  expect(() => flow.prepareSubmission(form)).toThrow(TypeError)
  await flow.loadCapabilities('fixture')
  expect(() => flow.prepareSubmission(form)).toThrow(TypeError)
  expect(() => flow.confirmCapability('b'.repeat(64))).toThrow(TypeError)
  flow.confirmCapability(form.capabilityHash)
  flow.prepareSubmission(form)
  form.symbols.reverse()
  expect(flow.pendingSubmission.value.symbols).toEqual([' 600000.sh ', '000001.SZ'])
  expect(flow.pendingSubmission.value).not.toHaveProperty('apiNames')
  await flow.submit()
  const old = flow.pendingSubmission.value.submissionId
  flow.prepareSubmission(selection())
  expect(flow.pendingSubmission.value.submissionId).not.toBe(old)
})

it.each(['TIMEOUT', 'NETWORK', 'INVALID_RESPONSE', 'PERSISTENCE_FAILED', 'QUERY_FAILED', 'INTERNAL_ERROR'])('recovers %s by the original submission ID before any possible resend', async (kind) => {
  const flow = await prepared(), failure = ['TIMEOUT', 'NETWORK', 'INVALID_RESPONSE'].includes(kind) ? new ClientError(kind, REQUEST_ID) : error(kind)
  const snapshot = flow.pendingSubmission.value
  api.submitIntegrityCheck.mockRejectedValueOnce(failure)
  api.listIntegrityChecks.mockResolvedValueOnce(page([{ checkId: ID, submissionId: snapshot.submissionId }]))
  await flow.submit()
  expect(api.listIntegrityChecks).toHaveBeenCalledWith({ page: 1, pageSize: 20, submissionId: snapshot.submissionId }, { signal: expect.any(AbortSignal) })
  expect(flow.submissionState.value).toBe('accepted')
  expect(flow.recoveredTask.value.checkId).toBe(ID)
  expect(flow.submissionError.value.requestId).toBe(REQUEST_ID)
  expect(api.submitIntegrityCheck).toHaveBeenCalledTimes(1)
})

it('requires an empty recovery before explicit resend with the exact same frozen payload', async () => {
  const flow = await prepared(), snapshot = flow.pendingSubmission.value
  api.submitIntegrityCheck.mockRejectedValueOnce(new ClientError('NETWORK', REQUEST_ID))
  await flow.submit()
  expect(flow.submissionState.value).toBe('uncertain')
  expect(flow.canResend.value).toBe(true)
  expect(() => flow.prepareSubmission(selection())).toThrow(TypeError)
  await flow.submit()
  expect(api.submitIntegrityCheck).toHaveBeenCalledTimes(1)
  await flow.resendSubmission()
  expect(api.submitIntegrityCheck.mock.calls[1][0]).toBe(snapshot)
  expect(flow.submissionState.value).toBe('accepted')
})

it('retains initial and recovery request IDs and blocks resend after recovery failure', async () => {
  const flow = await prepared()
  api.submitIntegrityCheck.mockRejectedValueOnce(new ClientError('TIMEOUT', REQUEST_ID))
  api.listIntegrityChecks.mockRejectedValueOnce(new ClientError('NETWORK', OTHER))
  await flow.submit()
  expect(flow.submissionError.value.requestId).toBe(REQUEST_ID)
  expect(flow.recoveryError.value.requestId).toBe(OTHER)
  expect(flow.canResend.value).toBe(false)
  await flow.resendSubmission()
  expect(api.submitIntegrityCheck).toHaveBeenCalledTimes(1)
  await flow.recoverSubmission()
  expect(flow.canResend.value).toBe(true)
  expect(api.submitIntegrityCheck).toHaveBeenCalledTimes(1)
})

it.each(['SUBMISSION_CONFLICT', 'INTEGRITY_QUEUE_FULL', 'INTEGRITY_LIMIT_EXCEEDED'])('does not automatically retry a definite %s rejection', async (code) => {
  const flow = await prepared(), id = flow.pendingSubmission.value.submissionId
  api.submitIntegrityCheck.mockRejectedValueOnce(error(code))
  await flow.submit()
  expect(flow.submissionState.value).toBe('rejected')
  expect(flow.pendingSubmission.value.submissionId).toBe(id)
  expect(api.listIntegrityChecks).not.toHaveBeenCalled()
  await flow.submit(); await flow.resendSubmission()
  expect(api.submitIntegrityCheck).toHaveBeenCalledTimes(1)
})

it('refreshes changed capability without modifying the old payload or confirming new rules', async () => {
  const flow = await prepared(), snapshot = flow.pendingSubmission.value
  api.submitIntegrityCheck.mockRejectedValueOnce(error('INTEGRITY_DEFINITION_CHANGED'))
  api.getIntegrityCapabilities.mockResolvedValueOnce({ ...capability(), capabilityHash: 'b'.repeat(64) })
  await flow.submit()
  expect(flow.submissionState.value).toBe('definition-changed')
  expect(flow.pendingSubmission.value).toBe(snapshot)
  expect(flow.confirmedCapabilityHash.value).toBeNull()
  expect(flow.capability.value.capabilityHash).toBe('b'.repeat(64))
  expect(() => flow.prepareSubmission({ ...selection(), capabilityHash: 'b'.repeat(64) })).toThrow(TypeError)
  flow.confirmCapability('b'.repeat(64))
  flow.prepareSubmission({ ...selection(), capabilityHash: 'b'.repeat(64) })
  expect(flow.pendingSubmission.value.submissionId).not.toBe(snapshot.submissionId)
  expect(api.submitIntegrityCheck).toHaveBeenCalledTimes(1)
})

it('restores pending cache without POST and recovers old hashes independently of current capability', async () => {
  const request = example('submission')
  sessionStorage.setItem(KEY, JSON.stringify({ schemaVersion: 1, request }))
  const flow = make()
  expect(flow.submissionState.value).toBe('uncertain')
  expect(flow.pendingSubmission.value).toEqual(request)
  await flow.submit(); await flow.resendSubmission()
  expect(api.submitIntegrityCheck).not.toHaveBeenCalled()
  api.listIntegrityChecks.mockResolvedValueOnce(page([{ submissionId: request.submissionId, checkId: ID }]))
  await flow.recoverSubmission()
  expect(flow.recoveredTask.value.checkId).toBe(ID)
  expect(api.getIntegrityCapabilities).not.toHaveBeenCalled()
})

it.each(['{broken', '{"schemaVersion":2,"request":{}}'])('reports corrupt cache without a replacement ID or POST', (serialized) => {
  sessionStorage.setItem(KEY, serialized)
  const flow = make()
  expect(flow.submissionError.value).toBeInstanceOf(Error)
  expect(flow.pendingSubmission.value).toBeNull()
  expect(api.submitIntegrityCheck).not.toHaveBeenCalled()
})

it('does not POST if storing the only recovery payload fails', async () => {
  const storage = { getItem: () => null, setItem: () => { throw new Error('quota') } }
  const flow = make({ storage })
  await flow.loadCapabilities('fixture'); flow.confirmCapability(capability().capabilityHash); flow.prepareSubmission(selection())
  await flow.submit()
  expect(flow.submissionError.value).toBeInstanceOf(Error)
  expect(api.submitIntegrityCheck).not.toHaveBeenCalled()
})

it('clears a transient storage error before preserving the first actual POST failure through resends', async () => {
  let writeFails = true
  const storage = {
    getItem: (key) => sessionStorage.getItem(key),
    setItem: (key, value) => { if (writeFails) throw new Error('quota'); sessionStorage.setItem(key, value) },
    removeItem: (key) => sessionStorage.removeItem(key),
  }
  const flow = make({ storage })
  await flow.loadCapabilities('fixture'); flow.confirmCapability(capability().capabilityHash); flow.prepareSubmission(selection())
  await flow.submit()
  expect(api.submitIntegrityCheck).not.toHaveBeenCalled()
  writeFails = false
  const original = new ClientError('NETWORK', REQUEST_ID)
  api.submitIntegrityCheck.mockRejectedValueOnce(original)
  await flow.submit()
  expect(flow.submissionError.value).toBe(original)
  expect(flow.submissionError.value.requestId).toBe(REQUEST_ID)
  expect(flow.storageError.value).toBeNull()
  api.submitIntegrityCheck.mockRejectedValueOnce(new ClientError('TIMEOUT', OTHER))
  await flow.resendSubmission()
  expect(flow.submissionError.value).toBe(original)
  expect(flow.canResend.value).toBe(true)
  await flow.resendSubmission()
  expect(flow.submissionState.value).toBe('accepted')
  expect(flow.submissionError.value).toBe(original)
})

it('keeps the original POST failure when storage fails during an explicit resend', async () => {
  const flow = await prepared(), original = new ClientError('TIMEOUT', REQUEST_ID)
  api.submitIntegrityCheck.mockRejectedValueOnce(original)
  await flow.submit()
  const write = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('quota') })
  try {
    await flow.resendSubmission()
    expect(flow.submissionError.value).toBe(original)
    expect(flow.storageError.value).toBeInstanceOf(Error)
    expect(flow.submissionState.value).toBe('uncertain')
    expect(api.submitIntegrityCheck).toHaveBeenCalledTimes(1)
  } finally { write.mockRestore() }
  await flow.resendSubmission()
  expect(flow.storageError.value).toBeNull()
  expect(flow.submissionError.value).toBe(original)
  expect(flow.submissionState.value).toBe('accepted')
})

it('handles a throwing default storage getter while keeping read-only queries available', async () => {
  const descriptor = Object.getOwnPropertyDescriptor(globalThis, 'sessionStorage')
  Object.defineProperty(globalThis, 'sessionStorage', {
    configurable: true,
    get: () => { throw new DOMException('Storage disabled', 'SecurityError') },
  })
  try {
    let flow
    expect(() => { flow = make() }).not.toThrow()
    expect(flow.storageError.value).toBeInstanceOf(Error)
    expect(flow.submissionError.value).toBeInstanceOf(Error)
    await flow.load(ID)
    expect(flow.detail.value.checkId).toBe(ID)
    expect(flow.connected.value).toBe(true)
    await flow.loadCapabilities('fixture'); flow.confirmCapability(capability().capabilityHash); flow.prepareSubmission(selection())
    await flow.submit()
    expect(flow.storageError.value).toBeInstanceOf(Error)
    expect(api.submitIntegrityCheck).not.toHaveBeenCalled()
  } finally { Object.defineProperty(globalThis, 'sessionStorage', descriptor) }
})

it('only removes cache belonging to the accepted submission', async () => {
  const flow = await prepared(), pending = deferred()
  api.submitIntegrityCheck.mockReturnValueOnce(pending.promise)
  const job = flow.submit(), replacement = { ...example('submission'), submissionId: OTHER }
  sessionStorage.setItem(KEY, JSON.stringify({ schemaVersion: 1, request: replacement }))
  pending.resolve({ checkId: ID })
  await job
  expect(JSON.parse(sessionStorage.getItem(KEY)).request.submissionId).toBe(OTHER)
})

it('ignores a late capability and its finally after switching source', async () => {
  const flow = make(), old = deferred(), current = deferred()
  api.getIntegrityCapabilities.mockReturnValueOnce(old.promise).mockReturnValueOnce(current.promise)
  const first = flow.loadCapabilities('fixture'), second = flow.loadCapabilities('tushare_pro')
  expect(api.getIntegrityCapabilities.mock.calls[0][1].signal.aborted).toBe(true)
  old.reject(new ClientError('NETWORK', REQUEST_ID)); await first
  expect(flow.capabilityLoading.value).toBe(true)
  expect(flow.capabilityError.value).toBeNull()
  current.resolve({ ...capability(), pluginId: 'tushare_pro' }); await second
  expect(flow.capability.value.pluginId).toBe('tushare_pro')
})

it('starts immediately, waits for both GETs then polls after 2000ms without overlap', async () => {
  const flow = make(), delayed = deferred()
  api.listIntegrityResults.mockReturnValueOnce(delayed.promise)
  const first = flow.load(ID)
  await flushPromises()
  expect(flow.detail.value.checkId).toBe(ID)
  await vi.advanceTimersByTimeAsync(10000)
  flow.refresh(); flow.refresh()
  expect(api.getIntegrityCheck).toHaveBeenCalledTimes(1)
  expect(api.listIntegrityResults).toHaveBeenCalledTimes(1)
  delayed.resolve(page()); await first
  await vi.advanceTimersByTimeAsync(1999)
  expect(api.getIntegrityCheck).toHaveBeenCalledTimes(1)
  await vi.advanceTimersByTimeAsync(1)
  expect(api.getIntegrityCheck).toHaveBeenCalledTimes(2)
})

it.each(['COMPLETED', 'FAILED', 'INTERRUPTED'])('stops all timers on %s regardless of the data conclusion', async (status) => {
  const flow = make()
  api.getIntegrityCheck.mockResolvedValueOnce({ ...task(ID, status), overallStatus: 'FAIL' })
  await flow.load(ID); await vi.advanceTimersByTimeAsync(6000)
  expect(flow.detail.value.overallStatus).toBe('FAIL')
  expect(api.getIntegrityCheck).toHaveBeenCalledTimes(1)
  expect(vi.getTimerCount()).toBe(0)
})

it.each(['detail', 'results'])('preserves snapshots and request IDs on %s failure, stops, and reconnects with GET only', async (which) => {
  const flow = make(); await flow.load(ID)
  const saved = which === 'detail' ? flow.detail.value : flow.results.value
  const fn = which === 'detail' ? api.getIntegrityCheck : api.listIntegrityResults
  fn.mockRejectedValueOnce(error())
  await vi.advanceTimersByTimeAsync(2000)
  expect((which === 'detail' ? flow.detail : flow.results).value).toBe(saved)
  expect((which === 'detail' ? flow.detailError : flow.resultsError).value.requestId).toBe(REQUEST_ID)
  expect(flow.connected.value).toBe(false)
  await vi.advanceTimersByTimeAsync(10000)
  expect(api.getIntegrityCheck).toHaveBeenCalledTimes(2)
  fn.mockRejectedValueOnce(error()); await flow.reconnect()
  expect(flow.connected.value).toBe(false)
  await flow.reconnect()
  expect(flow.connected.value).toBe(true)
  expect(api.submitIntegrityCheck).not.toHaveBeenCalled()
  await vi.advanceTimersByTimeAsync(2000)
  expect(api.getIntegrityCheck).toHaveBeenCalledTimes(5)
})

it('ignores late task A data, errors and finally while task B is loading', async () => {
  const flow = make(), oldDetail = deferred(), oldPage = deferred(), newDetail = deferred()
  api.getIntegrityCheck.mockReturnValueOnce(oldDetail.promise).mockReturnValueOnce(newDetail.promise)
  api.listIntegrityResults.mockReturnValueOnce(oldPage.promise)
  const a = flow.load(ID), b = flow.load(OTHER)
  expect(api.getIntegrityCheck.mock.calls[0][1].signal.aborted).toBe(true)
  oldDetail.reject(error()); oldPage.resolve(page()); await a
  expect(flow.detailError.value).toBeNull()
  expect(flow.detail.value).toBeNull()
  expect(flow.loading.value).toBe(true)
  newDetail.resolve(task(OTHER)); await b
  expect(flow.detail.value.checkId).toBe(OTHER)
})

it('keeps current results criteria when an old page resolves late', async () => {
  const flow = make(); await flow.load(ID)
  const old = deferred()
  api.listIntegrityResults.mockReturnValueOnce(old.promise)
  const previous = flow.changeResults({ page: 2, pageSize: 37, overallStatus: 'FAIL' })
  await flushPromises()
  await flow.changeResults({ page: 3, pageSize: 37, overallStatus: 'UNKNOWN' })
  old.resolve({ ...page(), page: 2 }); await previous
  expect(flow.results.value.page).toBe(3)
  expect(flow.resultsCriteria.value).toEqual({ page: 3, pageSize: 37, overallStatus: 'UNKNOWN' })
  await flow.load(OTHER)
  expect(flow.resultsCriteria.value).toEqual({ page: 1, pageSize: 20 })
})

it('does not overlap an aborted same-key request before it settles', async () => {
  const flow = make(), slow = deferred()
  api.getIntegrityCheck.mockReturnValueOnce(slow.promise)
  const old = flow.load(ID)
  flow.setActive(false)
  const next = flow.setActive(true)
  expect(api.getIntegrityCheck).toHaveBeenCalledTimes(1)
  slow.resolve(task()); await old; await next
  expect(api.getIntegrityCheck).toHaveBeenCalledTimes(2)
  expect(flow.connected.value).toBe(true)
})

it('polls detail alone for hidden results and loads results when visible again', async () => {
  const flow = make()
  flow.setResultsVisible(false); await flow.load(ID)
  await vi.advanceTimersByTimeAsync(2000)
  expect(api.getIntegrityCheck).toHaveBeenCalledTimes(2)
  expect(api.listIntegrityResults).not.toHaveBeenCalled()
  await flow.setResultsVisible(true)
  expect(api.listIntegrityResults).toHaveBeenCalledTimes(1)
})

it('cancels on route leave and scope disposal without late state or timer leaks', async () => {
  const scope = effectScope(), slow = deferred()
  let flow
  scope.run(() => { flow = make() })
  api.getIntegrityCheck.mockReturnValueOnce(slow.promise)
  const pending = flow.load(ID)
  flow.setActive(false)
  expect(api.getIntegrityCheck.mock.calls[0][1].signal.aborted).toBe(true)
  scope.stop(); slow.reject(error()); await pending
  await flow.load(OTHER); await vi.advanceTimersByTimeAsync(10000)
  expect(flow.detailError.value).toBeNull()
  expect(api.getIntegrityCheck).toHaveBeenCalledTimes(1)
  expect(vi.getTimerCount()).toBe(0)
})

it('starts idle with no pending operation', () => {
  const flow = make()
  expect(flow.submissionState?.value).toBe('idle')
})

it('discards late POST acceptance after changing source and allows GET recovery of the original ID', async () => {
  const flow = await prepared(), delayed = deferred(), snapshot = flow.pendingSubmission.value
  api.submitIntegrityCheck.mockReturnValueOnce(delayed.promise)
  const pending = flow.submit()
  api.getIntegrityCapabilities.mockResolvedValueOnce({ ...capability(), pluginId: 'tushare_pro' })
  await flow.loadCapabilities('tushare_pro')
  expect(api.submitIntegrityCheck.mock.calls[0][1].signal.aborted).toBe(true)
  delayed.resolve({ ...example('createdReceipt'), submissionId: snapshot.submissionId }); await pending
  expect(flow.receipt.value).toBeNull()
  expect(flow.submissionState.value).toBe('uncertain')
  expect(flow.pendingSubmission.value).toBe(snapshot)
  api.listIntegrityChecks.mockResolvedValueOnce(page([{ ...example('historicalTaskPage').items[0], submissionId: snapshot.submissionId }]))
  await flow.recoverSubmission()
  expect(flow.submissionState.value).toBe('accepted')
  expect(api.submitIntegrityCheck).toHaveBeenCalledTimes(1)
})

it('does not accept ambiguous or mismatched recovery records', async () => {
  const flow = await prepared()
  api.submitIntegrityCheck.mockRejectedValueOnce(new ClientError('NETWORK', REQUEST_ID))
  api.listIntegrityChecks.mockResolvedValueOnce(page([{ ...example('historicalTaskPage').items[0], submissionId: OTHER }]))
  await flow.submit()
  expect(flow.submissionState.value).toBe('uncertain')
  expect(flow.canResend.value).toBe(false)
  expect(flow.recoveryError.value.kind).toBe('INVALID_RESPONSE')
})

it('stops on not-found and preserves the existing report for error presentation', async () => {
  const flow = make(); await flow.load(ID)
  const saved = flow.detail.value
  api.getIntegrityCheck.mockRejectedValueOnce(error('INTEGRITY_CHECK_NOT_FOUND'))
  await vi.advanceTimersByTimeAsync(2000)
  expect(flow.detail.value).toBe(saved)
  expect(flow.detailError.value.code).toBe('INTEGRITY_CHECK_NOT_FOUND')
  expect(vi.getTimerCount()).toBe(0)
})

it('does not show errors from results hidden while their request was pending', async () => {
  const flow = make(), slow = deferred()
  api.listIntegrityResults.mockReturnValueOnce(slow.promise)
  const first = flow.load(ID)
  await flushPromises()
  await flow.setResultsVisible(false)
  slow.reject(error()); await first
  expect(flow.resultsError.value).toBeNull()
  expect(flow.connected.value).toBe(true)
  await vi.advanceTimersByTimeAsync(2000)
  expect(api.listIntegrityResults).toHaveBeenCalledTimes(1)
})

it('does not clear a newly running submission from an older canceled operation finally', async () => {
  const flow = await prepared(), slow = deferred(), recovering = deferred()
  api.submitIntegrityCheck.mockReturnValueOnce(slow.promise)
  const first = flow.submit()
  flow.setActive(false); await flow.setActive(true)
  api.listIntegrityChecks.mockReturnValueOnce(recovering.promise)
  const lookup = flow.recoverSubmission()
  slow.reject(new ClientError('TIMEOUT', REQUEST_ID)); await first
  expect(flow.submissionState.value).toBe('recovering')
  await flow.resendSubmission()
  expect(api.submitIntegrityCheck).toHaveBeenCalledTimes(1)
  recovering.resolve(page()); await lookup
  expect(flow.canResend.value).toBe(true)
})

it('connects capability, lost POST, exact history recovery and polling through the real HTTP adapter', async () => {
  const { http } = await import('../api/http.js')
  const { AxiosError } = await import('axios')
  const actual = await vi.importActual('../api/integrityChecks.js')
  const originalAdapter = http.defaults.adapter, requests = []
  let submitted, terminal = false
  for (const name of ['getIntegrityCapabilities', 'submitIntegrityCheck', 'listIntegrityChecks', 'getIntegrityCheck', 'listIntegrityResults']) api[name].mockImplementation(actual[name])
  http.defaults.adapter = async (config) => {
    requests.push(config)
    let data
    if (config.method === 'post') {
      submitted = JSON.parse(config.data)
      throw new AxiosError('connection lost', 'ERR_NETWORK', config)
    }
    if (config.url.endsWith('integrity-capabilities')) data = capability()
    else if (config.url === '/integrity-checks') {
      expect(config.params.get('submissionId')).toBe(submitted.submissionId)
      data = example('historicalTaskPage')
      data.items[0].submissionId = submitted.submissionId
      data.items[0].originalRequest = submitted
      data.items[0].checkId = ID
    } else if (config.url.endsWith('/results')) {
      data = { page: 1, pageSize: 20, total: '1', items: [example('failedDataResult')] }
      data.items[0].checkId = ID
      data.items[0].report.statistics.actualCount = '9223372036854775807'
    } else {
      data = { ...task(ID, terminal ? 'COMPLETED' : 'RUNNING'), submissionId: submitted.submissionId, originalRequest: submitted, overallStatus: 'FAIL' }
    }
    return { config, data: JSON.stringify(data), status: 200, statusText: 'OK', headers: { 'X-Request-Id': config.headers.get('X-Request-Id') }, request: {} }
  }
  try {
    const flow = await prepared()
    await flow.submit()
    expect(flow.submissionState.value).toBe('accepted')
    expect(flow.submissionError.value.kind).toBe('NETWORK')
    expect(flow.submissionError.value.requestId).toBe(requests.find((r) => r.method === 'post').headers.get('X-Request-Id'))
    await flow.load(flow.recoveredTask.value.checkId)
    expect(flow.results.value.items[0].report.statistics.actualCount).toBe(9223372036854775807n)
    terminal = true
    await vi.advanceTimersByTimeAsync(2000)
    expect(flow.detail.value.status).toBe('COMPLETED')
    expect(flow.detail.value.overallStatus).toBe('FAIL')
    expect(vi.getTimerCount()).toBe(0)
    expect(requests.filter((r) => r.method === 'post')).toHaveLength(1)
  } finally { http.defaults.adapter = originalAdapter }
})
