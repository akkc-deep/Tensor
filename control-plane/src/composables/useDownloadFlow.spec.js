import { ApiError, ClientError } from '../api/errors.js'
import { PENDING_SUBMISSION_KEY } from '../utils/downloadTaskSubmission.js'

const api = vi.hoisted(() => ({
  listDataSources: vi.fn(),
  listApis: vi.fn(),
  getDownloadCapabilities: vi.fn(),
  submitDownloadTask: vi.fn(),
  listDownloadTasks: vi.fn(),
}))

vi.mock('../api/dataSources.js', () => ({
  listDataSources: api.listDataSources,
  listApis: api.listApis,
}))
vi.mock('../api/downloadTasks.js', () => ({
  getDownloadCapabilities: api.getDownloadCapabilities,
  submitDownloadTask: api.submitDownloadTask,
  listDownloadTasks: api.listDownloadTasks,
}))

import { useDownloadFlow } from './useDownloadFlow.js'

const submissionId = '33333333-3333-4333-8333-333333333333'

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

const source = () => ({
  pluginId: 'contract_fixture',
  displayName: 'Fixture',
  description: 'Fixture source',
  enabled: true,
  credentialConfigured: true,
  downloadAvailable: true,
  unavailableReason: null,
})
const descriptor = () => ({
  apiName: 'daily',
  displayName: 'Daily',
  category: 'Market',
  queryMode: 'trade_date',
  parameters: [],
})
const parameter = (name, type, extra = {}) => ({ name, label: name, type, required: true, ...extra })
const capabilities = () => ({
  single: {
    available: true,
    parameters: [parameter('ts_code', 'TS_CODE'), parameter('trade_date', 'DATE')],
  },
  range: {
    availability: 'AVAILABLE',
    unavailableReason: null,
    dateAxis: 'TRADE_DATE',
    dateLabel: '交易日期',
    startParameter: 'start_date',
    endParameter: 'end_date',
    parameters: [
      parameter('ts_code', 'TS_CODE'),
      parameter('start_date', 'DATE_RANGE_MEMBER', { relatedParameter: 'end_date' }),
      parameter('end_date', 'DATE_RANGE_MEMBER', { relatedParameter: 'start_date' }),
    ],
    planningMode: 'TRADING_DAYS',
    splittable: false,
    policyVersion: 'fixture-v1',
    completenessRule: { kind: 'VERIFIED_RULE', rowLimit: null, evidence: 'fixture' },
  },
})
const receipt = () => ({
  requestId: '11111111-1111-4111-8111-111111111111',
  taskId: '22222222-2222-4222-8222-222222222222',
  status: 'QUEUED',
  version: 1n,
  createdAt: '2026-09-12T00:00:00Z',
})

async function readyFlow(options = {}) {
  api.listDataSources.mockResolvedValueOnce([source()])
  api.listApis.mockResolvedValueOnce([descriptor()])
  api.getDownloadCapabilities.mockResolvedValueOnce(capabilities())
  const flow = useDownloadFlow(options)
  await flow.load()
  await flow.selectSource('contract_fixture')
  await flow.selectApi('daily')
  return flow
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.spyOn(globalThis.crypto, 'randomUUID').mockReturnValue(submissionId)
  sessionStorage.clear()
})
afterEach(() => {
  vi.restoreAllMocks()
  sessionStorage.clear()
})

it('persists a frozen whitelist snapshot before one POST and rejects double clicks', async () => {
  const pending = deferred()
  api.submitDownloadTask.mockImplementationOnce((request) => {
    expect(JSON.parse(sessionStorage.getItem(PENDING_SUBMISSION_KEY))).toEqual({ schemaVersion: 1, request })
    return pending.promise
  })
  const flow = await readyFlow()
  const params = {
    ts_code: '000001.SZ',
    start_date: '20260901',
    end_date: '20260902',
    ignored: 'secret',
  }

  const submitting = flow.submit(params)
  expect(flow.submissionState.value).toBe('SUBMITTING')
  expect(await flow.submit({})).toBe(false)
  expect(api.submitDownloadTask).toHaveBeenCalledTimes(1)
  const sent = api.submitDownloadTask.mock.calls[0][0]
  expect(sent).toEqual({
    submissionId,
    pluginId: 'contract_fixture',
    apiName: 'daily',
    mode: 'RANGE',
    params: { ts_code: '000001.SZ', start_date: '20260901', end_date: '20260902' },
  })
  expect(Object.isFrozen(sent)).toBe(true)
  expect(Object.isFrozen(sent.params)).toBe(true)
  params.start_date = 'changed'
  expect(sent.params.start_date).toBe('20260901')

  pending.resolve(receipt())
  expect(await submitting).toBe(true)
  expect(flow.submissionState.value).toBe('ACCEPTED')
  expect(sessionStorage.getItem(PENDING_SUBMISSION_KEY)).toBeNull()
})

it('keeps the same identity after a lost receipt and finds it without metadata', async () => {
  api.submitDownloadTask.mockRejectedValueOnce(new ClientError('NETWORK', 'submit-request'))
  const flow = await readyFlow()
  await flow.submit({ ts_code: '000001.SZ', start_date: '20260901', end_date: '20260902' })
  expect(flow.submissionState.value).toBe('UNCERTAIN')
  const stored = sessionStorage.getItem(PENDING_SUBMISSION_KEY)
  flow.dispose()

  const recoveredTask = { taskId: receipt().taskId, submissionId, status: 'QUEUED', version: 1n }
  api.listDownloadTasks.mockResolvedValueOnce({ page: 1, pageSize: 20, total: 1n, items: [recoveredTask] })
  const accepted = vi.fn()
  const restored = useDownloadFlow({ onAccepted: accepted })
  expect(await restored.recoverSubmission()).toBe(true)
  expect(api.listDownloadTasks).toHaveBeenCalledWith({ submissionId })
  expect(restored.recoveredTask.value).toBe(recoveredTask)
  expect(restored.receipt.value).toBeNull()
  expect(accepted).toHaveBeenCalledWith(recoveredTask)
  expect(stored).not.toBeNull()
  expect(sessionStorage.getItem(PENDING_SUBMISSION_KEY)).toBeNull()
})

it('does not replay an empty recovery until explicitly asked, then reuses the exact request', async () => {
  const request = {
    submissionId,
    pluginId: 'contract_fixture',
    apiName: 'daily',
    mode: 'RANGE',
    params: { start_date: '20260901', end_date: '20260902' },
  }
  sessionStorage.setItem(PENDING_SUBMISSION_KEY, JSON.stringify({ schemaVersion: 1, request }))
  api.listDownloadTasks.mockResolvedValueOnce({ page: 1, pageSize: 20, total: 0n, items: [] })
  api.submitDownloadTask.mockResolvedValueOnce(receipt())
  const flow = useDownloadFlow()

  expect(await flow.recoverSubmission()).toBe(false)
  expect(flow.submissionState.value).toBe('UNCERTAIN')
  expect(api.submitDownloadTask).not.toHaveBeenCalled()
  expect(await flow.replaySubmission()).toBe(true)
  expect(api.submitDownloadTask).toHaveBeenCalledWith(request)
})

it('loads available capabilities as RANGE and replaces the parameter model on mode switches', async () => {
  const flow = await readyFlow()
  expect(flow.metadataState.value).toBe('READY')
  expect(flow.mode.value).toBe('RANGE')
  expect(flow.parameters.value.map(({ name }) => name)).toEqual(['ts_code', 'start_date', 'end_date'])
  const key = flow.formKey.value
  expect(flow.selectMode('SINGLE')).toBe(true)
  expect(flow.parameters.value.map(({ name }) => name)).toEqual(['ts_code', 'trade_date'])
  expect(flow.formKey.value).toBeGreaterThan(key)
})

it('automatically looks up a submission conflict without changing its identity', async () => {
  const conflict = new ApiError({
    requestId: '11111111-1111-4111-8111-111111111111',
    code: 'SUBMISSION_CONFLICT',
    message: 'Submission conflict',
    retryable: false,
    fieldErrors: [],
  })
  api.submitDownloadTask.mockRejectedValueOnce(conflict)
  const existing = { taskId: receipt().taskId, submissionId, status: 'QUEUED', version: 1n }
  api.listDownloadTasks.mockResolvedValueOnce({ page: 1, pageSize: 20, total: 1n, items: [existing] })
  const flow = await readyFlow()

  expect(await flow.submit({ ts_code: '000001.SZ', start_date: '20260901', end_date: '20260902' })).toBe(true)
  expect(api.listDownloadTasks).toHaveBeenCalledWith({ submissionId })
  expect(flow.submissionState.value).toBe('ACCEPTED')
  expect(flow.recoveredTask.value).toBe(existing)
  expect(flow.submissionError.value).toBe(conflict)
})

it('classifies storage access TypeErrors as READ failures rather than corrupt records', async () => {
  vi.spyOn(Storage.prototype, 'getItem').mockImplementationOnce(() => {
    throw new TypeError('storage implementation detail')
  })
  const flow = useDownloadFlow()

  expect(await flow.recoverSubmission()).toBe(false)
  expect(flow.storageError.value).toEqual({
    kind: 'READ',
    message: '无法读取本地提交记录，请重试。',
  })
  expect(flow.storageError.value.message).not.toContain('implementation detail')
})

it('blocks POST when storage cannot write and keeps the safe manual retry state', async () => {
  const flow = await readyFlow()
  vi.spyOn(Storage.prototype, 'setItem').mockImplementationOnce(() => {
    throw new DOMException('quota detail', 'QuotaExceededError')
  })

  expect(await flow.submit({ start_date: '20260901', end_date: '20260902' })).toBe(false)
  expect(api.submitDownloadTask).not.toHaveBeenCalled()
  expect(flow.storageError.value).toEqual({
    kind: 'WRITE',
    message: '无法保存提交记录，暂不能提交。',
  })
  expect(flow.canSubmit.value).toBe(true)
})

it('clears only a corrupt task key and can retry a failed corrupt-key removal', async () => {
  sessionStorage.setItem('keep.me', 'yes')
  sessionStorage.setItem(PENDING_SUBMISSION_KEY, '{bad')
  const flow = useDownloadFlow()
  await flow.recoverSubmission()
  const remove = vi.spyOn(Storage.prototype, 'removeItem').mockImplementationOnce(() => {
    throw new DOMException('denied detail', 'SecurityError')
  })

  expect(flow.clearCorruptSubmission()).toBe(false)
  expect(flow.storageError.value.kind).toBe('REMOVE')
  remove.mockRestore()
  expect(await flow.retryStorage()).toBe(true)
  expect(sessionStorage.getItem(PENDING_SUBMISSION_KEY)).toBeNull()
  expect(sessionStorage.getItem('keep.me')).toBe('yes')
  expect(flow.submissionState.value).toBe('IDLE')
})

it('keeps accepted memory state across removal failure and retries the matching key', async () => {
  api.submitDownloadTask.mockResolvedValueOnce(receipt())
  const flow = await readyFlow()
  const remove = vi.spyOn(Storage.prototype, 'removeItem').mockImplementationOnce(() => {
    throw new DOMException('denied detail', 'SecurityError')
  })

  expect(await flow.submit({ ts_code: '000001.SZ', start_date: '20260901', end_date: '20260902' })).toBe(true)
  expect(flow.submissionState.value).toBe('ACCEPTED')
  expect(flow.locked.value).toBe(false)
  expect(flow.storageError.value.kind).toBe('REMOVE')
  expect(flow.pendingSubmission.value).toBeNull()
  expect(flow.canSubmit.value).toBe(true)
  remove.mockRestore()
  expect(await flow.retryStorage()).toBe(true)
  expect(flow.pendingSubmission.value).toBeNull()
})

it('reuses the in-memory frozen pending request for repeated recovery attempts', async () => {
  const original = {
    submissionId,
    pluginId: 'contract_fixture',
    apiName: 'daily',
    mode: 'RANGE',
    params: { start_date: '20260901', end_date: '20260902' },
  }
  const replacement = {
    ...original,
    submissionId: '44444444-4444-4444-8444-444444444444',
    apiName: 'weekly',
  }
  sessionStorage.setItem(PENDING_SUBMISSION_KEY, JSON.stringify({ schemaVersion: 1, request: original }))
  api.listDownloadTasks.mockResolvedValue({ page: 1, pageSize: 20, total: 0n, items: [] })
  const flow = useDownloadFlow()

  await flow.recoverSubmission()
  sessionStorage.setItem(PENDING_SUBMISSION_KEY, JSON.stringify({ schemaVersion: 1, request: replacement }))
  await flow.recoverSubmission()

  expect(api.listDownloadTasks).toHaveBeenCalledTimes(2)
  expect(api.listDownloadTasks.mock.calls).toEqual([
    [{ submissionId }],
    [{ submissionId }],
  ])
  expect(flow.pendingSubmission.value.submissionId).toBe(submissionId)
})

it('ignores stale capability success and failure after a newer API selection', async () => {
  const old = deferred()
  const current = deferred()
  api.listDataSources.mockResolvedValueOnce([source()])
  api.listApis.mockResolvedValueOnce([descriptor(), { ...descriptor(), apiName: 'weekly' }])
  api.getDownloadCapabilities.mockReturnValueOnce(old.promise).mockReturnValueOnce(current.promise)
  const flow = useDownloadFlow()
  await flow.load()
  await flow.selectSource('contract_fixture')

  const stale = flow.selectApi('daily')
  const latest = flow.selectApi('weekly')
  current.resolve(capabilities())
  expect(await latest).toBe(true)
  old.reject(new ClientError('NETWORK', 'stale-request'))
  expect(await stale).toBe(false)
  expect(flow.selectedApiName.value).toBe('weekly')
  expect(flow.metadataState.value).toBe('READY')
  expect(flow.metadataError.value).toBeNull()
})

it('does not let disposed POST continuations change state, storage, or callbacks', async () => {
  const post = deferred()
  const accepted = vi.fn()
  api.submitDownloadTask.mockReturnValueOnce(post.promise)
  const flow = await readyFlow({ onAccepted: accepted })
  const submitting = flow.submit({ ts_code: '000001.SZ', start_date: '20260901', end_date: '20260902' })
  const stored = sessionStorage.getItem(PENDING_SUBMISSION_KEY)
  flow.dispose()
  post.resolve(receipt())

  expect(await submitting).toBe(false)
  expect(flow.submissionState.value).toBe('SUBMITTING')
  expect(sessionStorage.getItem(PENDING_SUBMISSION_KEY)).toBe(stored)
  expect(accepted).not.toHaveBeenCalled()
})

it('does not persist or POST after dispose even when canSubmit was already cached true', async () => {
  const flow = await readyFlow()
  expect(flow.canSubmit.value).toBe(true)
  flow.dispose()

  expect(await flow.submit({ ts_code: '000001.SZ', start_date: '20260901', end_date: '20260902' })).toBe(false)
  expect(sessionStorage.getItem(PENDING_SUBMISSION_KEY)).toBeNull()
  expect(api.submitDownloadTask).not.toHaveBeenCalled()
})

it('does not overwrite a corrupt record when clearing it fails', async () => {
  const flow = await readyFlow()
  const corruptRecord = '{bad'
  sessionStorage.setItem(PENDING_SUBMISSION_KEY, corruptRecord)
  await flow.recoverSubmission()
  const remove = vi.spyOn(Storage.prototype, 'removeItem').mockImplementationOnce(() => {
    throw new DOMException('denied detail', 'SecurityError')
  })

  expect(flow.clearCorruptSubmission()).toBe(false)
  expect(flow.storageError.value.kind).toBe('REMOVE')
  expect(flow.canSubmit.value).toBe(false)
  expect(await flow.submit({ ts_code: '000001.SZ', start_date: '20260901', end_date: '20260902' })).toBe(false)
  expect(sessionStorage.getItem(PENDING_SUBMISSION_KEY)).toBe(corruptRecord)
  expect(api.submitDownloadTask).not.toHaveBeenCalled()
})

it('clears an initial explicit rejection but keeps every replay failure uncertain', async () => {
  const rejection = new ApiError({
    requestId: '11111111-1111-4111-8111-111111111111',
    code: 'PARAM_INVALID',
    message: 'Invalid parameters',
    retryable: false,
    fieldErrors: [{ field: 'start_date', message: 'Invalid date' }],
  })
  api.submitDownloadTask.mockRejectedValueOnce(rejection)
  const flow = await readyFlow()

  expect(await flow.submit({ ts_code: '000001.SZ', start_date: 'bad', end_date: '20260902' })).toBe(false)
  expect(flow.submissionState.value).toBe('REJECTED')
  expect(flow.submissionError.value).toBe(rejection)
  expect(sessionStorage.getItem(PENDING_SUBMISSION_KEY)).toBeNull()

  const request = {
    submissionId,
    pluginId: 'contract_fixture',
    apiName: 'daily',
    mode: 'RANGE',
    params: { start_date: '20260901', end_date: '20260902' },
  }
  sessionStorage.setItem(PENDING_SUBMISSION_KEY, JSON.stringify({ schemaVersion: 1, request }))
  api.listDownloadTasks.mockResolvedValueOnce({ page: 1, pageSize: 20, total: 0n, items: [] })
  api.submitDownloadTask.mockRejectedValueOnce(rejection)
  const replay = useDownloadFlow()
  await replay.recoverSubmission()
  expect(await replay.replaySubmission()).toBe(false)
  expect(replay.submissionState.value).toBe('UNCERTAIN')
  expect(replay.pendingSubmission.value).toEqual(request)
  expect(sessionStorage.getItem(PENDING_SUBMISSION_KEY)).not.toBeNull()
})

it('retains uncertain submission state while metadata selections continue independently', async () => {
  api.submitDownloadTask.mockRejectedValueOnce(new ClientError('NETWORK', 'submit-request'))
  const flow = await readyFlow()
  await flow.submit({ ts_code: '000001.SZ', start_date: '20260901', end_date: '20260902' })
  const pending = flow.pendingSubmission.value

  api.listApis.mockResolvedValueOnce([descriptor()])
  api.getDownloadCapabilities.mockResolvedValueOnce(capabilities())
  expect(await flow.selectSource('contract_fixture')).toBe(true)
  expect(await flow.selectApi('daily')).toBe(true)
  expect(flow.submissionState.value).toBe('UNCERTAIN')
  expect(flow.pendingSubmission.value).toBe(pending)
  expect(flow.canSubmit.value).toBe(false)
})

it('defaults unavailable ranges to SINGLE and blocks every unavailable mode', async () => {
  const unavailableRange = capabilities()
  unavailableRange.range = {
    ...unavailableRange.range,
    availability: 'NEEDS_VERIFICATION',
    unavailableReason: '待验证',
  }
  api.listDataSources.mockResolvedValueOnce([source()])
  api.listApis.mockResolvedValueOnce([descriptor()])
  api.getDownloadCapabilities.mockResolvedValueOnce(unavailableRange)
  const flow = useDownloadFlow()
  await flow.load()
  await flow.selectSource('contract_fixture')
  await flow.selectApi('daily')
  expect(flow.mode.value).toBe('SINGLE')
  expect(flow.selectMode('RANGE')).toBe(false)
  expect(flow.canSubmit.value).toBe(true)

  const neither = capabilities()
  neither.single = { available: false, parameters: [] }
  neither.range = { ...neither.range, availability: 'UNSUPPORTED', unavailableReason: '不支持' }
  api.getDownloadCapabilities.mockResolvedValueOnce(neither)
  await flow.selectApi('daily')
  expect(flow.mode.value).toBe('SINGLE')
  expect(flow.canSubmit.value).toBe(false)
  expect(flow.selectMode('SINGLE')).toBe(false)
})

it('surfaces capability failures without stale data and retries only the same selection', async () => {
  const failure = new ClientError('TIMEOUT', 'capabilities-request')
  api.listDataSources.mockResolvedValueOnce([source()])
  api.listApis.mockResolvedValueOnce([descriptor()])
  api.getDownloadCapabilities.mockRejectedValueOnce(failure).mockResolvedValueOnce(capabilities())
  const flow = useDownloadFlow()
  await flow.load()
  await flow.selectSource('contract_fixture')

  expect(await flow.selectApi('daily')).toBe(false)
  expect(flow.metadataState.value).toBe('FAILURE')
  expect(flow.metadataError.value).toBe(failure)
  expect(flow.capabilities.value).toBeNull()
  expect(await flow.retryMetadata()).toBe(true)
  expect(flow.metadataState.value).toBe('READY')
  expect(flow.capabilities.value).toEqual(capabilities())
})

it('ignores disposed metadata continuations independently of submission state', async () => {
  const pending = deferred()
  api.listDataSources.mockReturnValueOnce(pending.promise)
  const flow = useDownloadFlow()
  const loading = flow.load()
  expect(flow.metadataState.value).toBe('LOADING')
  flow.dispose()
  pending.resolve([source()])

  expect(await loading).toBe(false)
  expect(flow.sources.value).toEqual([])
  expect(flow.metadataState.value).toBe('LOADING')
})
