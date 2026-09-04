const API_ERROR_KEYS = [
  'requestId',
  'code',
  'message',
  'retryable',
  'fieldErrors',
]
const FIELD_ERROR_KEYS = ['field', 'message']

const API_RULES = Object.freeze({
  PARAM_REQUIRED: [400, false],
  PARAM_INVALID: [400, false],
  PLUGIN_DISABLED: [409, false],
  DATASET_MISCONFIGURED: [409, false],
  SOURCE_AUTH_FAILED: [502, false],
  SOURCE_PERMISSION_DENIED: [502, false],
  SOURCE_RATE_LIMITED: [502, true],
  SOURCE_UNAVAILABLE: [502, true],
  SOURCE_NETWORK_ERROR: [502, true],
  SOURCE_TIMEOUT: [504, true],
  SOURCE_PAYLOAD_INVALID: [502, true],
  ADAPTER_FIELD_MISSING: [422, false],
  ADAPTER_TYPE_INVALID: [422, false],
  PERSISTENCE_FAILED: [500, true],
  QUERY_FAILED: [500, true],
  INTERNAL_ERROR: [500, false],
})

const CLIENT_RULES = Object.freeze({
  TIMEOUT: ['请求超时，请稍后重试。', true],
  NETWORK: ['无法连接服务，请检查网络后重试。', true],
  INVALID_RESPONSE: ['服务返回了无法识别的响应。', false],
  UNEXPECTED: ['请求未能发送。', false],
})

function isObjectWithExactKeys(value, keys) {
  return (
    value !== null &&
    typeof value === 'object' &&
    !Array.isArray(value) &&
    Object.keys(value).length === keys.length &&
    keys.every((key) => Object.hasOwn(value, key))
  )
}

function isNonBlankString(value) {
  return typeof value === 'string' && value.trim() !== ''
}

function validFieldErrors(value) {
  return (
    Array.isArray(value) &&
    value.every(
      (item) =>
        isObjectWithExactKeys(item, FIELD_ERROR_KEYS) &&
        isNonBlankString(item.field) &&
        isNonBlankString(item.message),
    )
  )
}

function header(headers, name) {
  const value =
    (typeof headers?.get === 'function' ? headers.get(name) : undefined) ??
    headers?.[name] ??
    headers?.[name.toLowerCase()]
  return isNonBlankString(value) ? value : null
}

function outgoingRequestId(error) {
  return header(error?.config?.headers, 'X-Request-Id')
}

/** A validated OpenAPI FieldError snapshot. */
export class ApiError extends Error {
  constructor({ requestId, code, message, retryable, fieldErrors }) {
    super(message)
    this.name = 'ApiError'
    this.requestId = requestId
    this.code = code
    this.retryable = retryable
    this.fieldErrors = Object.freeze(
      fieldErrors.map(({ field, message: fieldMessage }) =>
        Object.freeze({ field, message: fieldMessage }),
      ),
    )
  }
}

/** A safe browser-to-application failure without a server error code. */
export class ClientError extends Error {
  constructor(kind, requestId = null) {
    const rule = CLIENT_RULES[kind]
    if (!rule) throw new TypeError('Unknown client error kind')
    super(rule[0])
    this.name = 'ClientError'
    this.kind = kind
    this.retryable = rule[1]
    this.requestId = requestId
  }
}

function apiError(response) {
  const body = response.data
  if (!isObjectWithExactKeys(body, API_ERROR_KEYS)) return null

  const rule = API_RULES[body.code]
  const responseRequestId = header(response.headers, 'X-Request-Id')
  if (
    !rule ||
    response.status !== rule[0] ||
    body.retryable !== rule[1] ||
    !isNonBlankString(body.requestId) ||
    !isNonBlankString(body.message) ||
    !validFieldErrors(body.fieldErrors) ||
    responseRequestId !== body.requestId
  ) {
    return null
  }

  return new ApiError(body)
}

export function normalizeError(error) {
  const requestId = outgoingRequestId(error)
  if (error?.code === 'ECONNABORTED' || error?.code === 'ETIMEDOUT') {
    return new ClientError('TIMEOUT', requestId)
  }
  if (error?.response) {
    return apiError(error.response) ?? new ClientError('INVALID_RESPONSE', requestId)
  }
  if (error?.code === 'ERR_NETWORK') {
    return new ClientError('NETWORK', requestId)
  }
  return new ClientError('UNEXPECTED', requestId)
}
