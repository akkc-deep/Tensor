import { http } from './http.js'
import { isDownloadResult } from './downloadResult.js'
import { ClientError } from './errors.js'

/**
 * @typedef {object} DownloadRequest
 * @property {string} pluginId
 * @property {string} apiName
 * @property {Record<string, string>} params
 */

/**
 * @typedef {object} DownloadResponse
 * @property {string} requestId
 * @property {'SUCCESS'|'EMPTY'|'NO_OPEN_DATES'|'PARTIAL'|'FAILED'} outcome
 * @property {string} pluginId
 * @property {string} apiName
 * @property {number} sourceRowCount
 * @property {number} insertedRows
 * @property {number} updatedRows
 * @property {string} message
 * @property {number} completedUnits
 * @property {number} failedUnits
 * @property {number} notStartedUnits
 * @property {number} skippedClosedDates
 * @property {string|null} taskId
 * @property {number} remainingFailedUnits
 * @property {'NOT_REQUIRED'|'CONFIRMED'} failureRecordStatus
 * @property {object[]} failures
 * @property {object[]} notStartedScopes
 * @property {object[]} unconfirmedScopes
 */

function header(headers, name) {
  const value =
    (typeof headers?.get === 'function' ? headers.get(name) : undefined) ??
    headers?.[name] ??
    headers?.[name.toLowerCase()]
  return typeof value === 'string' && value.trim() !== '' ? value : null
}

/** @param {DownloadRequest} request @returns {Promise<DownloadResponse>} */
export async function downloadDataset(request) {
  const response = await http.post('/downloads', {
    pluginId: request.pluginId,
    apiName: request.apiName,
    params: request.params,
  })
  const requestId = header(response.config?.headers, 'X-Request-Id')
  const responseRequestId = header(response.headers, 'X-Request-Id')
  if (
    !isDownloadResult(response.data) ||
    response.data.requestId !== responseRequestId ||
    response.data.pluginId !== request.pluginId ||
    response.data.apiName !== request.apiName
  ) {
    throw new ClientError('INVALID_RESPONSE', requestId)
  }
  return response.data
}
