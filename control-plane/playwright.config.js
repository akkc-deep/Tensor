import { defineConfig, devices } from '@playwright/test'

const taskLive = process.env.TENSOR_TASK_LIVE_E2E === '1'
const tushareLive = process.env.TENSOR_TUSHARE_LIVE_E2E === '1'
if (taskLive && tushareLive) throw new Error('Select only one dedicated E2E suite')
if (taskLive) {
  let url
  try { url = new URL(process.env.PLAYWRIGHT_BASE_URL) } catch { throw new Error('Task lifecycle E2E requires an explicit loopback URL') }
  if (url.protocol !== 'http:' || !['127.0.0.1', '[::1]'].includes(url.hostname) || url.username || url.password || url.pathname !== '/' || url.search || url.hash) {
    throw new Error('Task lifecycle E2E requires an explicit loopback URL')
  }
  const scenario = process.env.TENSOR_TASK_LIFECYCLE_SCENARIO
  if (!['flow', 'resume'].includes(scenario)) throw new Error('Task lifecycle E2E requires flow or resume scenario')
  if (scenario === 'resume' && !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(process.env.TENSOR_TASK_LIFECYCLE_TASK_ID ?? '')) {
    throw new Error('Task lifecycle resume requires a task UUID')
  }
}

export default defineConfig({
  testDir: './e2e',
  testMatch: taskLive ? 'download-task-lifecycle.spec.js' : tushareLive ? 'tushare-live.spec.js' : '**/*.spec.js',
  testIgnore: taskLive || tushareLive ? [] : ['**/download-task-lifecycle.spec.js', '**/tushare-live.spec.js'],
  forbidOnly: Boolean(process.env.CI),
  retries: taskLive ? 0 : process.env.CI ? 1 : 0,
  workers: 1,
  reporter: taskLive ? [['list'], ['json']] : 'list',
  outputDir: 'node_modules/.cache/tensor-playwright',
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:8080',
    trace: tushareLive ? 'off' : 'retain-on-failure',
    screenshot: tushareLive ? 'off' : 'only-on-failure',
    video: 'off',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
