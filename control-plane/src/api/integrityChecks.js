import { parseTaskJson } from './downloadTaskDtos.js'
import {
  createIntegritySubmission,
  parseIntegrityCapabilities,
  parseIntegrityDetail,
  parseIntegrityIssuePage,
  parseIntegrityReceipt,
  parseIntegrityResultPage,
  parseIntegrityTaskPage,
  validateIntegrityCheckId,
  validateIntegrityCriteria,
} from './integrityDtos.js'
import { ClientError } from './errors.js'
import { http } from './http.js'

export { validateIntegrityCheckId, validateIntegrityCriteria }

const IDENTIFIER = /^[a-z][a-z0-9_]{1,63}$/

function header(headers, name) {
  return (typeof headers?.get === 'function' ? headers.get(name) : undefined) ??
    headers?.[name] ?? headers?.[name.toLowerCase()]
}

function requestId(response) {
  return response.config.headers.get('X-Request-Id')
}

function invalid(response) {
  throw new ClientError('INVALID_RESPONSE', requestId(response))
}

function validateResponse(response, statuses) {
  const outgoing = requestId(response)
  if (!statuses.includes(response.status) || header(response.headers, 'X-Request-Id') !== outgoing) invalid(response)
  return outgoing
}

function options(signal) {
  return { responseType: 'text', transformResponse: [parseTaskJson], signal }
}

function params(values) {
  return new URLSearchParams(Object.entries(values).map(([key, value]) => [key, String(value)]))
}

export async function getIntegrityCapabilities(pluginId, { signal } = {}) {
  if (typeof pluginId !== 'string' || !IDENTIFIER.test(pluginId)) {
    throw new TypeError('Integrity pluginId is invalid')
  }
  const response = await http.get(
    `/data-sources/${encodeURIComponent(pluginId)}/integrity-capabilities`,
    options(signal),
  )
  const capability = parseIntegrityCapabilities(response.data, validateResponse(response, [200]))
  if (capability.pluginId !== pluginId) invalid(response)
  return capability
}

export async function submitIntegrityCheck(request, { signal } = {}) {
  if (request === null || typeof request !== 'object' || Array.isArray(request) ||
      !Object.hasOwn(request, 'submissionId')) throw new TypeError('Integrity submission is invalid')
  const snapshot = createIntegritySubmission(request, request.submissionId)
  const response = await http.post('/integrity-checks', snapshot, options(signal))
  const receipt = parseIntegrityReceipt(response.data, validateResponse(response, [200, 202]))
  if (receipt.submissionId.toLowerCase() !== snapshot.submissionId.toLowerCase() ||
      receipt.pluginId !== snapshot.pluginId ||
      header(response.headers, 'Location') !== `/api/v1/integrity-checks/${receipt.checkId}`) invalid(response)
  return receipt
}

export async function listIntegrityChecks(criteria = {}, { signal } = {}) {
  const values = validateIntegrityCriteria('tasks', criteria)
  const response = await http.get('/integrity-checks', { ...options(signal), params: params(values) })
  const page = parseIntegrityTaskPage(response.data, validateResponse(response, [200]))
  if (page.page !== values.page || page.pageSize !== values.pageSize) invalid(response)
  if (values.submissionId !== undefined) {
    const found = page.total === 1n && page.items.length === 1 &&
      page.items[0].submissionId.toLowerCase() === values.submissionId
    const absent = page.total === 0n && page.items.length === 0
    if (!found && !absent) invalid(response)
  }
  return page
}

export async function getIntegrityCheck(checkId, { signal } = {}) {
  const id = validateIntegrityCheckId(checkId)
  const response = await http.get(`/integrity-checks/${id}`, options(signal))
  const detail = parseIntegrityDetail(response.data, validateResponse(response, [200]))
  if (detail.checkId.toLowerCase() !== id) invalid(response)
  return detail
}

export async function listIntegrityResults(checkId, criteria = {}, { signal } = {}) {
  const id = validateIntegrityCheckId(checkId)
  const values = validateIntegrityCriteria('results', criteria)
  const response = await http.get(`/integrity-checks/${id}/results`, {
    ...options(signal), params: params(values),
  })
  const page = parseIntegrityResultPage(response.data, validateResponse(response, [200]))
  if (page.page !== values.page || page.pageSize !== values.pageSize ||
      page.items.some(({ checkId: itemCheckId }) => itemCheckId.toLowerCase() !== id)) invalid(response)
  return page
}

export async function listIntegrityIssues(checkId, criteria = {}, { signal } = {}) {
  const id = validateIntegrityCheckId(checkId)
  const values = validateIntegrityCriteria('issues', criteria)
  const response = await http.get(`/integrity-checks/${id}/issues`, {
    ...options(signal), params: params(values),
  })
  const page = parseIntegrityIssuePage(response.data, validateResponse(response, [200]))
  if (page.page !== values.page || page.pageSize !== values.pageSize) invalid(response)
  return page
}
