import { createHash } from 'node:crypto'
import { chmodSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import {
  ISSUE030_GROUP_COUNTS,
  applyIssue030CandidateIndex,
  buildIssue030CandidatePlan,
} from './issue030-range-candidates.js'
import { selectTaskCases, validateCasePlan, validateEvidence } from './tushare-range-evidence.js'

const root = resolve(import.meta.dirname, '../..')
const indexPath = resolve(root, 'docs/verification/ISSUE-018-range-acceptance.json')
const reportPath = resolve(root, 'docs/verification/ISSUE-018-range-acceptance.md')
const privateDirectory = '/private/tmp/issue030-control'

const bytes = (value) => Buffer.from(`${JSON.stringify(value, null, 2)}\n`)
const sha256 = (value) => createHash('sha256').update(value).digest('hex')
const writePrivate = (name, value) => {
  const output = bytes(value)
  const path = resolve(privateDirectory, name)
  writeFileSync(path, output, { mode: 0o600 })
  chmodSync(path, 0o600)
  return { path, sha256: sha256(output) }
}

const original = JSON.parse(readFileSync(indexPath, 'utf8'))
const index = validateEvidence(applyIssue030CandidateIndex(original))
const plan = validateCasePlan(buildIssue030CandidatePlan(index))
const sourceBindings = new Map()
selectTaskCases('range', plan, index, sourceBindings)

mkdirSync(privateDirectory, { recursive: true, mode: 0o700 })
chmodSync(privateDirectory, 0o700)
const planOutput = writePrivate('candidate-inputs.json', plan)
const bindingsOutput = writePrivate('source-bindings.json', Object.fromEntries(sourceBindings))
const summary = {
  schemaVersion: index.schemaVersion,
  groupCounts: ISSUE030_GROUP_COUNTS,
  candidateInterfaceCount: 30,
  planCaseCount: plan.cases.length,
  sourceBindingCount: sourceBindings.size,
  runCount: index.runs.length,
  historicalCaseCount: index.runs.reduce((count, run) => count + run.cases.length, 0),
  historicalRequestCount: index.runs.reduce((count, run) => count
    + run.cases.reduce((subtotal, evidence) => subtotal + (evidence.requestCount ?? 0), 0), 0),
  runsSha256: sha256(Buffer.from(JSON.stringify(index.runs))),
  planSha256: planOutput.sha256,
  sourceBindingsSha256: bindingsOutput.sha256,
  disposition: 'NEEDS_VERIFICATION',
  unresolved: 'RANGE_TASK_NOT_RUN',
}
const summaryOutput = writePrivate('candidate-summary.json', summary)

const indexOutput = bytes(index)
writeFileSync(indexPath, indexOutput)
const report = readFileSync(reportPath, 'utf8')
  .replace(/(`candidate-inputs\.json` SHA-256 `)[a-f0-9]{64}(`)/,
    `$1${planOutput.sha256}$2`)
  .replace(/(`source-bindings\.json` SHA-256 `)[a-f0-9]{64}(`)/,
    `$1${bindingsOutput.sha256}$2`)
writeFileSync(reportPath, report)
console.log(JSON.stringify({
  ...summary,
  indexPath,
  indexSha256: sha256(indexOutput),
  privateFiles: [planOutput, bindingsOutput, summaryOutput],
}, null, 2))
