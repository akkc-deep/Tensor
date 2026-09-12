export const PENDING_SUBMISSION_KEY = 'tensor.downloadTasks.pending.v1'

const UUID = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/
const IDENTIFIER = /^[a-z][a-z0-9_]{1,63}$/
const MODES = new Set(['SINGLE', 'RANGE'])
const REQUEST_KEYS = ['submissionId', 'pluginId', 'apiName', 'mode', 'params']
const MAX_PARAMS_BYTES = 8192

export class CorruptSubmissionError extends TypeError {}

function exactKeys(value, keys) {
  return (
    value !== null &&
    typeof value === 'object' &&
    !Array.isArray(value) &&
    Object.keys(value).length === keys.length &&
    keys.every((key) => Object.hasOwn(value, key))
  )
}

function validParams(params) {
  return (
    params !== null &&
    typeof params === 'object' &&
    !Array.isArray(params) &&
    Object.entries(params).every(
      ([name, value]) => IDENTIFIER.test(name) && typeof value === 'string',
    ) &&
    new TextEncoder().encode(JSON.stringify(params)).byteLength <= MAX_PARAMS_BYTES
  )
}

function freezeRequest(value) {
  if (
    !exactKeys(value, REQUEST_KEYS) ||
    typeof value.submissionId !== 'string' ||
    !UUID.test(value.submissionId) ||
    typeof value.pluginId !== 'string' ||
    !IDENTIFIER.test(value.pluginId) ||
    typeof value.apiName !== 'string' ||
    !IDENTIFIER.test(value.apiName) ||
    !MODES.has(value.mode) ||
    !validParams(value.params)
  ) {
    throw new TypeError('Invalid pending download task submission')
  }
  const params = Object.freeze({ ...value.params })
  return Object.freeze({
    submissionId: value.submissionId,
    pluginId: value.pluginId,
    apiName: value.apiName,
    mode: value.mode,
    params,
  })
}

export function createSubmissionRequest(selection, parameterNames, submissionId = globalThis.crypto.randomUUID()) {
  if (
    !Array.isArray(parameterNames) ||
    selection?.params === null ||
    typeof selection?.params !== 'object' ||
    Array.isArray(selection.params)
  ) {
    throw new TypeError('Invalid submission inputs')
  }
  const params = {}
  for (const name of parameterNames) {
    if (Object.hasOwn(selection.params, name)) params[name] = selection.params[name]
  }
  return freezeRequest({
    submissionId,
    pluginId: selection.pluginId,
    apiName: selection.apiName,
    mode: selection.mode,
    params,
  })
}

export function writePendingSubmission(request, storage = globalThis.sessionStorage) {
  const snapshot = freezeRequest(request)
  storage.setItem(
    PENDING_SUBMISSION_KEY,
    JSON.stringify({ schemaVersion: 1, request: snapshot }),
  )
  return snapshot
}

export function readPendingSubmission(storage = globalThis.sessionStorage) {
  const serialized = storage.getItem(PENDING_SUBMISSION_KEY)
  if (serialized === null) return null
  let envelope
  try {
    envelope = JSON.parse(serialized)
  } catch {
    throw new CorruptSubmissionError('Invalid pending download task submission')
  }
  if (!exactKeys(envelope, ['schemaVersion', 'request']) || envelope.schemaVersion !== 1) {
    throw new CorruptSubmissionError('Invalid pending download task submission')
  }
  try {
    return freezeRequest(envelope.request)
  } catch {
    throw new CorruptSubmissionError('Invalid pending download task submission')
  }
}

export function removePendingSubmission(submissionId, storage = globalThis.sessionStorage) {
  const current = readPendingSubmission(storage)
  if (current === null || current.submissionId !== submissionId) return false
  storage.removeItem(PENDING_SUBMISSION_KEY)
  return true
}

export function clearPendingSubmission(storage = globalThis.sessionStorage) {
  storage.removeItem(PENDING_SUBMISSION_KEY)
}
