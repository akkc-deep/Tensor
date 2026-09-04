import axios, { AxiosHeaders } from 'axios'

import { normalizeError } from './errors.js'

export const http = axios.create({
  baseURL: '/api/v1',
  timeout: 130000,
})

export function configureHttp(options) {
  if (options === null || typeof options !== 'object' || Array.isArray(options)) {
    throw new TypeError('HTTP options must be an object')
  }

  const baseURL = Object.hasOwn(options, 'baseURL')
    ? options.baseURL
    : http.defaults.baseURL
  const timeout = Object.hasOwn(options, 'timeout')
    ? options.timeout
    : http.defaults.timeout

  if (typeof baseURL !== 'string' || baseURL.trim() === '') {
    throw new TypeError('HTTP baseURL must be a non-blank string')
  }
  if (!Number.isSafeInteger(timeout) || timeout <= 0) {
    throw new TypeError('HTTP timeout must be a positive safe integer')
  }

  http.defaults.baseURL = baseURL
  http.defaults.timeout = timeout
}

http.interceptors.request.use((config) => {
  config.headers = AxiosHeaders.from(config.headers)
  config.headers.set('X-Request-Id', globalThis.crypto.randomUUID())
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => Promise.reject(normalizeError(error)),
)
