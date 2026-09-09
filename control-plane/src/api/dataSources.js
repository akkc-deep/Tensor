import { http } from './http.js'

/**
 * @typedef {object} DataSourceSummary
 * @property {string} pluginId
 * @property {string} displayName
 * @property {string} description
 * @property {boolean} enabled
 * @property {boolean} credentialConfigured
 * @property {boolean} downloadAvailable
 * @property {string|null} unavailableReason
 */

/**
 * @typedef {object} ApiParameter
 * @property {string} name
 * @property {string} label
 * @property {'DATE'|'DATE_RANGE_MEMBER'|'MONTH'|'TS_CODE'|'ENUM'|'TEXT'} type
 * @property {boolean} required
 * @property {string} [description]
 * @property {string} [defaultValue]
 * @property {string[]} [allowedValues]
 * @property {string} [pattern]
 * @property {string} [relatedParameter]
 */

/**
 * @typedef {object} ApiDescriptor
 * @property {string} apiName
 * @property {string} displayName
 * @property {string} category
 * @property {'trade_date'|'ann_date'|'snapshot'|'date_range'} queryMode
 * @property {ApiParameter[]} parameters
 * @property {DownloadPolicy} downloadPolicy
 */

/**
 * @typedef {object} DownloadPolicy
 * @property {'TRADE_DATE_RANGE'|'ANN_DATE_RANGE'|'MONTH_RANGE'|'NATIVE_RANGE'|'ORIGINAL_PARAMS'} mode
 * @property {string|null} dateSemantic
 * @property {string} description
 * @property {string|null} calendarProfile
 * @property {{maxRangeDays: number}|null} limits
 */

/** @returns {Promise<DataSourceSummary[]>} */
export async function listDataSources() {
  const { data } = await http.get('/data-sources')
  return data
}

/** @param {string} pluginId @returns {Promise<ApiDescriptor[]>} */
export async function listApis(pluginId) {
  const { data } = await http.get(
    `/data-sources/${encodeURIComponent(pluginId)}/apis`,
  )
  return data
}
