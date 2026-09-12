import { afterEach, test } from 'node:test'
import assert from 'node:assert/strict'
import { chmodSync, mkdtempSync, rmSync, symlinkSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { configurePackagedEnvironment } from './packaged-test-environment.js'

const prefixes = {
  'download-outcomes': 'tensor_m14_t02_',
  'dataset-query': 'tensor_m14_t03_',
  'tushare-metadata': 'tensor_m14_t04_',
  'fixture-flow': 'tensor_issue018_t12_fixture_',
}
const initial = { ...process.env }
const dirs = []
afterEach(() => {
  for (const key of Object.keys(process.env)) if (!(key in initial)) delete process.env[key]
  Object.assign(process.env, initial)
  dirs.splice(0).forEach((path) => rmSync(path, { recursive: true, force: true }))
})
function fixture() {
  const dir = mkdtempSync(join(tmpdir(), 'tensor-t12-env-'))
  dirs.push(dir)
  const data = Object.fromEntries(Object.entries(prefixes).map(([key, prefix]) => [key, {
    TENSOR_DB_URL: `jdbc:mysql://127.0.0.1:3307/${prefix}abcdef`,
    TENSOR_DB_USERNAME: key,
    TENSOR_DB_PASSWORD: 'synthetic-canary',
    M14_DB_SCHEMA: `${prefix}abcdef`,
    M14_MYSQL_DEFAULTS_FILE: join(dir, `${key}.cnf`),
  }]))
  const file = join(dir, 'environment.json')
  const save = () => writeFileSync(file, JSON.stringify(data), { mode: 0o600 })
  save()
  process.env.TENSOR_PACKAGED_E2E_ENV_FILE = file
  return { dir, data, file, save }
}
test('keeps single-file invocation environment unchanged', () => {
  delete process.env.TENSOR_PACKAGED_E2E_ENV_FILE
  const before = { ...process.env }
  configurePackagedEnvironment('download-outcomes')
  assert.deepEqual({ ...process.env }, before)
})
for (const key of Object.keys(prefixes)) test(`selects exactly five fields for ${key}`, () => {
  const { data } = fixture()
  const before = { ...process.env }
  configurePackagedEnvironment(key)
  assert.deepEqual({ ...process.env }, { ...before, ...data[key] })
})
for (const [label, change] of Object.entries({
  'missing root key': ({ data }) => { delete data['fixture-flow'] },
  'extra root key': ({ data }) => { data.unexpected = {} },
  'extra field': ({ data }) => { data['dataset-query'].JAVA_TOOL_OPTIONS = 'canary' },
  'missing field': ({ data }) => { delete data['fixture-flow'].TENSOR_DB_PASSWORD },
  'empty field': ({ data }) => { data['dataset-query'].TENSOR_DB_PASSWORD = '' },
  'wrong value type': ({ data }) => { data['download-outcomes'].TENSOR_DB_PASSWORD = 1 },
  'wrong prefix': ({ data }) => { data['download-outcomes'].M14_DB_SCHEMA = 'external_business' },
  'JDBC schema mismatch': ({ data }) => { data['dataset-query'].TENSOR_DB_URL += 'f' },
  'relative defaults path': ({ data }) => { data['fixture-flow'].M14_MYSQL_DEFAULTS_FILE = 'relative.cnf' },
})) test(`rejects ${label} without partial environment assignment or secrets`, () => {
  const f = fixture()
  change(f); f.save()
  const before = { ...process.env }
  assert.throws(() => configurePackagedEnvironment('download-outcomes'), { message: 'Invalid packaged E2E environment configuration' })
  assert.deepEqual({ ...process.env }, before)
})
for (const mode of [0o644, 0o400, 0o660]) test(`rejects file mode ${mode.toString(8)}`, () => {
  const { file } = fixture(); chmodSync(file, mode)
  assert.throws(() => configurePackagedEnvironment('download-outcomes'), /Invalid packaged E2E environment configuration/)
})
test('rejects symlink and non-file paths', () => {
  const { file, dir } = fixture()
  const link = join(dir, 'link.json'); symlinkSync(file, link)
  for (const path of [link, dir, 'relative.json']) {
    process.env.TENSOR_PACKAGED_E2E_ENV_FILE = path
    assert.throws(() => configurePackagedEnvironment('download-outcomes'), /Invalid packaged E2E environment configuration/)
  }
})
test('rejects invalid JSON and unknown selector', () => {
  const { file } = fixture(); writeFileSync(file, '{secret-canary')
  assert.throws(() => configurePackagedEnvironment('download-outcomes'), { message: 'Invalid packaged E2E environment configuration' })
  assert.throws(() => configurePackagedEnvironment('unknown'), /Invalid packaged E2E environment configuration/)
})
