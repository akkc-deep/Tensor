import { http } from './http.js'

/**
 * @typedef {object} DatasetFilter
 * @property {'ts_code'|'trade_date'|'ann_date'} field
 * @property {'EQ'|'BETWEEN'} operator
 * @property {'TEXT'|'DATE_RANGE'} controlType
 */

/**
 * @typedef {object} DatasetSummary
 * @property {string} pluginId
 * @property {string} apiName
 * @property {string} displayName
 * @property {string} category
 * @property {'trade_date'|'ann_date'|'snapshot'|'date_range'} queryMode
 * @property {DatasetFilter[]} filters
 * @property {string} fixedColumn
 */

/**
 * @typedef {object} DatasetColumn
 * @property {string} name
 * @property {string} label
 * @property {'STRING'|'TEXT'|'DATE'|'MONTH'|'LONG'|'DECIMAL'|'ENUM'} logicalType
 * @property {boolean} nullable
 * @property {number} displayOrder
 * @property {number} [length]
 * @property {number} [precision]
 * @property {number} [scale]
 * @property {string[]} [allowedValues]
 * @property {boolean} [longText]
 */

/** @typedef {DatasetSummary & {columns: DatasetColumn[]}} DatasetDefinitionResponse */

/**
 * @typedef {object} QueryCriteria
 * @property {string|null} [tsCode]
 * @property {string|null} [tradeDateFrom]
 * @property {string|null} [tradeDateTo]
 * @property {string|null} [annDateFrom]
 * @property {string|null} [annDateTo]
 * @property {number} [page]
 * @property {20|50|100} [pageSize]
 */

/**
 * @typedef {object} PageResponse
 * @property {string} requestId
 * @property {string} pluginId
 * @property {string} apiName
 * @property {number} page
 * @property {20|50|100} pageSize
 * @property {number} totalElements
 * @property {number} totalPages
 * @property {string[]} columns
 * @property {Array<Record<string, string|null>>} items
 */

const QUERY_PARAMETERS = [
  'tsCode',
  'tradeDateFrom',
  'tradeDateTo',
  'annDateFrom',
  'annDateTo',
  'page',
  'pageSize',
]

function datasetPath(pluginId, apiName = null) {
  const base = `/data-sources/${encodeURIComponent(pluginId)}/datasets`
  return apiName === null ? base : `${base}/${encodeURIComponent(apiName)}`
}

/** @returns {Promise<DatasetSummary[]>} */
export async function listDatasets(pluginId) {
  const { data } = await http.get(datasetPath(pluginId))
  return data
}

/** @returns {Promise<DatasetDefinitionResponse>} */
export async function getDataset(pluginId, apiName) {
  const { data } = await http.get(datasetPath(pluginId, apiName))
  return data
}

/** @param {string} pluginId @param {string} apiName @param {QueryCriteria} criteria @returns {Promise<PageResponse>} */
export async function queryDataset(pluginId, apiName, criteria) {
  const params = Object.fromEntries(
    QUERY_PARAMETERS.filter((name) => criteria[name] !== undefined).map(
      (name) => [name, criteria[name]],
    ),
  )
  const { data } = await http.get(`${datasetPath(pluginId, apiName)}/records`, {
    params,
  })
  return data
}
