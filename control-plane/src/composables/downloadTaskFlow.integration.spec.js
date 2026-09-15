import { AxiosError } from 'axios'

import examples from '../../../docs/contracts/download-task-examples.json'
import { http } from '../api/http.js'
import { useDownloadFlow } from './useDownloadFlow.js'

const STORAGE_KEY = 'tensor.downloadTasks.pending.v1'
const DEFAULT_ADAPTER = http.defaults.adapter
const sample = (name) => structuredClone(examples.examples.find((item) => item.name === name).value)
let flows
let requests

function flow() {
  const result = useDownloadFlow()
  flows.push(result)
  return result
}

function respond(config, value, status = 200) {
  return {
    config, request: {}, status, statusText: 'OK',
    headers: {
      'X-Request-Id': config.headers.get('X-Request-Id'),
      ...(status === 202 ? { Location: `/api/v1/download-tasks/${sample('queuedTask').taskId}` } : {}),
    },
    data: typeof value === 'string' ? value : JSON.stringify(value),
  }
}

function savePending() {
  const request = sample('rangeSubmission')
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ schemaVersion: 1, request }))
  return request
}

beforeEach(() => {
  flows = []
  requests = []
  sessionStorage.clear()
})

afterEach(() => {
  flows.forEach((item) => item.dispose?.())
  http.defaults.adapter = DEFAULT_ADAPTER
  sessionStorage.clear()
})

describe('task flow through the real Axios and DTO boundary', () => {
  it('restores a persisted request without metadata and retains an exact int64 version', async () => {
    const request = savePending()
    http.defaults.adapter = async (config) => {
      requests.push(config)
      const body = JSON.stringify({ page: 1, pageSize: 20, total: 1, items: [sample('queuedTask')] })
        .replace('"version":1', '"version":9007199254740993')
      return respond(config, body)
    }

    const current = flow()
    await current.recoverSubmission()

    expect(requests).toHaveLength(1)
    expect(requests[0].method).toBe('get')
    expect(http.getUri(requests[0])).toBe(`/api/v1/download-tasks?page=1&pageSize=20&submissionId=${request.submissionId}`)
    expect(current.submissionState.value).toBe('ACCEPTED')
    expect(current.recoveredTask.value.version).toBe(9007199254740993n)
    expect(current.recoveredTask.value.taskId).toBe('22222222-2222-4222-8222-222222222222')
    expect(current.receipt.value).toBeNull()
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull()
  })

  it('keeps the same request across an empty lookup, an invalid receipt and a remount', async () => {
    const request = savePending()
    let submitted = false
    http.defaults.adapter = async (config) => {
      requests.push(config)
      if (config.method === 'post') {
        submitted = true
        expect(JSON.parse(sessionStorage.getItem(STORAGE_KEY)).request).toEqual(request)
        const receipt = { ...sample('createdReceipt'), requestId: config.headers.get('X-Request-Id') }
        return respond(config, JSON.stringify(receipt).replace('"version":1', '"version":1.0000000000000001'), 202)
      }
      return respond(config, { page: 1, pageSize: 20, total: submitted ? 1 : 0, items: submitted ? [sample('queuedTask')] : [] })
    }

    const original = flow()
    await original.recoverSubmission()
    expect(original.submissionState.value).toBe('UNCERTAIN')
    expect(requests.map((item) => item.method)).toEqual(['get'])
    await original.replaySubmission()
    expect(original.submissionState.value).toBe('UNCERTAIN')
    expect(original.submissionError.value.kind).toBe('INVALID_RESPONSE')
    expect(JSON.parse(requests[1].data)).toEqual(request)
    expect(JSON.parse(sessionStorage.getItem(STORAGE_KEY)).request).toEqual(request)
    original.dispose()

    const restored = flow()
    await restored.recoverSubmission()
    expect(requests.map((item) => item.method)).toEqual(['get', 'post', 'get'])
    expect(restored.submissionState.value).toBe('ACCEPTED')
    expect(restored.recoveredTask.value.submissionId).toBe(request.submissionId)
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull()
  })

  it('retains an uncertain request even when its later replay receives a validated rejection', async () => {
    const request = savePending()
    http.defaults.adapter = async (config) => {
      requests.push(config)
      if (config.method === 'get') return respond(config, { page: 1, pageSize: 20, total: 0, items: [] })
      const response = respond(config, {
        requestId: config.headers.get('X-Request-Id'), code: 'TASK_QUEUE_FULL',
        message: 'Download task queue is full', retryable: true, fieldErrors: [],
      }, 429)
      throw new AxiosError('raw transport detail', 'ERR_BAD_REQUEST', config, {}, response)
    }
    const current = flow()
    await current.recoverSubmission()
    await current.replaySubmission()

    expect(current.submissionState.value).toBe('UNCERTAIN')
    expect(current.submissionError.value.code).toBe('TASK_QUEUE_FULL')
    expect(current.locked.value).toBe(false)
    expect(current.canSubmit.value).toBe(false)
    expect(JSON.parse(sessionStorage.getItem(STORAGE_KEY)).request).toEqual(request)
    expect(requests.map((item) => item.method)).toEqual(['get', 'post'])
  })
})
