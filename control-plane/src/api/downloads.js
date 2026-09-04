import { http } from './http.js'

/**
 * @typedef {object} DownloadRequest
 * @property {string} pluginId
 * @property {string} apiName
 * @property {Record<string, string>} params
 */

/**
 * @typedef {object} DownloadResponse
 * @property {string} requestId
 * @property {'SUCCESS'|'EMPTY'} outcome
 * @property {string} pluginId
 * @property {string} apiName
 * @property {number} sourceRowCount
 * @property {number} insertedRows
 * @property {number} updatedRows
 * @property {string} message
 */

/** @param {DownloadRequest} request @returns {Promise<DownloadResponse>} */
export async function downloadDataset(request) {
  const { data } = await http.post('/downloads', {
    pluginId: request.pluginId,
    apiName: request.apiName,
    params: request.params,
  })
  return data
}
