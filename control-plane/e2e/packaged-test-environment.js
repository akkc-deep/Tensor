import { constants, closeSync, fstatSync, openSync, readFileSync } from 'node:fs'
import { isAbsolute } from 'node:path'

const prefixes = {
  'download-outcomes': 'tensor_m14_t02_',
  'dataset-query': 'tensor_m14_t03_',
  'tushare-metadata': 'tensor_m14_t04_',
  'fixture-flow': 'tensor_issue018_t12_fixture_',
}
const fields = ['TENSOR_DB_URL', 'TENSOR_DB_USERNAME', 'TENSOR_DB_PASSWORD', 'M14_DB_SCHEMA', 'M14_MYSQL_DEFAULTS_FILE']
const exactKeys = (value, keys) => value && typeof value === 'object' && !Array.isArray(value) &&
  Object.keys(value).length === keys.length && keys.every((key) => Object.hasOwn(value, key))

export function configurePackagedEnvironment(key) {
  const path = process.env.TENSOR_PACKAGED_E2E_ENV_FILE
  if (path === undefined) return
  let fd
  try {
    if (!Object.hasOwn(prefixes, key) || !isAbsolute(path)) throw new Error()
    fd = openSync(path, constants.O_RDONLY | constants.O_NOFOLLOW)
    const stat = fstatSync(fd)
    if (!stat.isFile() || stat.uid !== process.getuid() || (stat.mode & 0o7777) !== 0o600) throw new Error()
    const data = JSON.parse(readFileSync(fd, 'utf8'))
    if (!exactKeys(data, Object.keys(prefixes))) throw new Error()
    for (const [name, prefix] of Object.entries(prefixes)) {
      const entry = data[name]
      if (!exactKeys(entry, fields) || fields.some((field) => typeof entry[field] !== 'string' || !entry[field].trim())) throw new Error()
      if (!new RegExp(`^${prefix}[0-9a-f]+$`).test(entry.M14_DB_SCHEMA) || !isAbsolute(entry.M14_MYSQL_DEFAULTS_FILE)) throw new Error()
      const jdbc = entry.TENSOR_DB_URL.match(/^jdbc:mysql:\/\/127\.0\.0\.1:([0-9]+)\/([^?]+)(?:\?.*)?$/)
      if (!jdbc || Number(jdbc[1]) < 1 || Number(jdbc[1]) > 65535 || jdbc[2] !== entry.M14_DB_SCHEMA) throw new Error()
    }
    Object.assign(process.env, data[key])
  } catch {
    throw new Error('Invalid packaged E2E environment configuration')
  } finally {
    if (fd !== undefined) closeSync(fd)
  }
}
