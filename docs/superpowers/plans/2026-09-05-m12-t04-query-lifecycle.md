# M12-T04 Query Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付保存不可变请求快照、隔离陈旧响应并接受服务端规范分页结果的 `useDatasetQuery`。

**Architecture:** 单一 composable 管理五态、分页 refs、当前/失败请求快照和单调 generation。所有查询动作汇入一个内部执行函数，M10 `queryDataset` 保持唯一网络边界；新查询和重置让旧响应失效，分页和重试只复用已冻结的请求上下文。

**Tech Stack:** Vue 3.5.42、Vitest 4.1.11、M10 `queryDataset`、Node.js 24.15.0。

## Global Constraints

- 只创建 `control-plane/src/composables/useDatasetQuery.js` 与 `control-plane/src/composables/useDatasetQuery.spec.js`，不修改任何既有文件。
- 公开状态固定为 `UNQUERIED | LOADING | SUCCESS | EMPTY | FAILURE`，初始 page/pageSize 固定为 1/50。
- 新 `query()` 固定第 1 页并保留当前 page size；`changePageSize()` 固定第 1 页；翻页保留来源、数据集和筛选。
- `totalElements === 0` 是唯一 `EMPTY` 判据；响应 `page/pageSize` 是服务端规范后的最终事实。
- `LOADING` 时只允许新 `query()` 和 `reset()` 取代旧请求；分页和重试不请求。
- `retry()` 只重放 retryable 失败的完整请求快照，不读取当前筛选表单。
- generation 只忽略 stale success/failure，不取消底层 Promise。
- 不校验、转换或解释筛选，不计算 totals/page，不切片数据，不解释错误正文，不引入取消、缓存、状态库、持久化、定时器或直接 Axios/fetch。
- 使用 Node.js 24.15.0；最终聚焦测试为 8/8，完整前端回归为 19 files / 112 tests。
- 实现提交只含两个新文件，提交消息固定为 `feat(ui): manage dataset query lifecycle`。

---

### Task 1: Implement `useDatasetQuery`

**Files:**
- Create: `control-plane/src/composables/useDatasetQuery.spec.js`
- Create: `control-plane/src/composables/useDatasetQuery.js`

**Interfaces:**
- Consumes: `queryDataset(pluginId, apiName, criteria): Promise<PageResponse>` from `control-plane/src/api/datasets.js`.
- Produces: `useDatasetQuery()` with refs `state/result/error/page/pageSize`, computed `loading/canRetry`, async actions `query/changePage/changePageSize/retry`, and synchronous `reset` exactly as frozen in `docs/task-designs/M12-T04-design.md`.

- [ ] **Step 1: Verify the baseline and protected working state**

Run from the repository root:

```bash
TENSOR_NODE=/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin/node
TENSOR_NPM=/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/lib/node_modules/npm/bin/npm-cli.js
"$TENSOR_NODE" --version
git status --short --untracked-files=all
cd control-plane
"$TENSOR_NODE" "$TENSOR_NPM" run test:unit -- --run
```

Expected: Node prints `v24.15.0`; the existing unrelated `.idea/misc.xml` and `data-plane/**/target/` changes remain outside this task; frontend baseline passes 18 files / 104 tests. Stop and diagnose any existing frontend failure before creating a task file.

- [ ] **Step 2: Create the complete failing behavior test**

Create `control-plane/src/composables/useDatasetQuery.spec.js` with the complete eight-test contract below while `useDatasetQuery.js` is still absent:

```js
import { ClientError } from '../api/errors.js'

const api = vi.hoisted(() => ({ queryDataset: vi.fn() }))

vi.mock('../api/datasets.js', () => ({
  queryDataset: api.queryDataset,
}))

import { useDatasetQuery } from './useDatasetQuery.js'

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function pageResponse(overrides = {}) {
  return {
    requestId: 'request-1',
    pluginId: 'fixture',
    apiName: 'daily',
    page: 1,
    pageSize: 50,
    totalElements: 1,
    totalPages: 1,
    columns: ['ts_code'],
    items: [{ ts_code: '000001.SZ' }],
    ...overrides,
  }
}

beforeEach(() => {
  vi.resetAllMocks()
})

describe('useDatasetQuery', () => {
  it('starts unqueried and submits an immutable first-page request without auto-querying', async () => {
    const flow = useDatasetQuery()
    expect(flow.state.value).toBe('UNQUERIED')
    expect(flow.result.value).toBeNull()
    expect(flow.error.value).toBeNull()
    expect(flow.page.value).toBe(1)
    expect(flow.pageSize.value).toBe(50)
    expect(flow.loading.value).toBe(false)
    expect(flow.canRetry.value).toBe(false)
    expect(api.queryDataset).not.toHaveBeenCalled()

    const pending = deferred()
    api.queryDataset.mockReturnValueOnce(pending.promise)
    const criteria = { tsCode: '000001.SZ' }
    const querying = flow.query('fixture', 'daily', criteria)

    expect(flow.state.value).toBe('LOADING')
    expect(flow.loading.value).toBe(true)
    expect(flow.page.value).toBe(1)
    expect(flow.pageSize.value).toBe(50)
    expect(api.queryDataset).toHaveBeenCalledWith('fixture', 'daily', {
      tsCode: '000001.SZ', page: 1, pageSize: 50,
    })
    criteria.tsCode = 'changed'
    expect(api.queryDataset.mock.calls[0][2].tsCode).toBe('000001.SZ')

    pending.resolve(pageResponse())
    expect(await querying).toBe(true)
  })

  it('clears old results and preserves current success and empty responses by server totals', async () => {
    const first = pageResponse()
    api.queryDataset.mockResolvedValueOnce(first)
    const flow = useDatasetQuery()
    expect(await flow.query('fixture', 'daily', {})).toBe(true)
    expect(flow.state.value).toBe('SUCCESS')
    expect(flow.result.value).toBe(first)

    const pending = deferred()
    const empty = pageResponse({
      requestId: 'request-2', apiName: 'weekly', totalElements: 0,
      totalPages: 0, items: [],
    })
    api.queryDataset.mockReturnValueOnce(pending.promise)
    const querying = flow.query('fixture', 'weekly', { tradeDateFrom: '2026-09-01' })
    expect(flow.state.value).toBe('LOADING')
    expect(flow.result.value).toBeNull()
    expect(flow.error.value).toBeNull()

    pending.resolve(empty)
    expect(await querying).toBe(true)
    expect(flow.state.value).toBe('EMPTY')
    expect(flow.result.value).toBe(empty)
    expect(flow.page.value).toBe(1)
    expect(flow.pageSize.value).toBe(50)
  })

  it('retains safe failures without automatic retry and derives retryability', async () => {
    const retryable = new ClientError('NETWORK', 'request-network')
    const finalFailure = new ClientError('UNEXPECTED', 'request-final')
    api.queryDataset
      .mockRejectedValueOnce(retryable)
      .mockRejectedValueOnce(finalFailure)

    const retryableFlow = useDatasetQuery()
    expect(await retryableFlow.query('fixture', 'daily', {})).toBe(false)
    expect(retryableFlow.state.value).toBe('FAILURE')
    expect(retryableFlow.result.value).toBeNull()
    expect(retryableFlow.error.value).toBe(retryable)
    expect(retryableFlow.canRetry.value).toBe(true)
    expect(api.queryDataset).toHaveBeenCalledTimes(1)

    const finalFlow = useDatasetQuery()
    expect(await finalFlow.query('fixture', 'weekly', {})).toBe(false)
    expect(finalFlow.error.value).toBe(finalFailure)
    expect(finalFlow.canRetry.value).toBe(false)
    expect(await finalFlow.retry()).toBe(false)
    expect(api.queryDataset).toHaveBeenCalledTimes(2)
  })

  it('retries the exact failed request snapshot and adopts the recovered server page', async () => {
    const flow = useDatasetQuery()
    api.queryDataset.mockResolvedValueOnce(pageResponse())
    expect(await flow.query('fixture', 'daily', { tsCode: '000001.SZ' })).toBe(true)

    api.queryDataset.mockResolvedValueOnce(pageResponse({ pageSize: 20 }))
    expect(await flow.changePageSize(20)).toBe(true)

    const failure = new ClientError('TIMEOUT', 'request-timeout')
    api.queryDataset.mockRejectedValueOnce(failure)
    expect(await flow.changePage(3)).toBe(false)
    expect(flow.page.value).toBe(3)
    expect(flow.pageSize.value).toBe(20)
    expect(flow.canRetry.value).toBe(true)

    const recovered = pageResponse({ page: 2, pageSize: 20, totalElements: 21, totalPages: 2 })
    api.queryDataset.mockResolvedValueOnce(recovered)
    expect(await flow.retry()).toBe(true)
    expect(api.queryDataset.mock.calls.slice(-2)).toEqual([
      ['fixture', 'daily', { tsCode: '000001.SZ', page: 3, pageSize: 20 }],
      ['fixture', 'daily', { tsCode: '000001.SZ', page: 3, pageSize: 20 }],
    ])
    expect(flow.result.value).toBe(recovered)
    expect(flow.page.value).toBe(2)
    expect(flow.canRetry.value).toBe(false)

    api.queryDataset.mockRejectedValueOnce(failure)
    expect(await flow.changePage(1)).toBe(false)
    const replacement = deferred()
    api.queryDataset.mockReturnValueOnce(replacement.promise)
    const replacing = flow.query('fixture', 'weekly', {})
    const calls = api.queryDataset.mock.calls.length
    expect(await flow.retry()).toBe(false)
    expect(api.queryDataset).toHaveBeenCalledTimes(calls)
    replacement.resolve(pageResponse({ apiName: 'weekly', pageSize: 20 }))
    expect(await replacing).toBe(true)
  })

  it('ignores stale successes and failures after a newer query or reset', async () => {
    const flow = useDatasetQuery()
    const staleSuccess = deferred()
    const currentSuccess = deferred()
    api.queryDataset
      .mockReturnValueOnce(staleSuccess.promise)
      .mockReturnValueOnce(currentSuccess.promise)

    const stale = flow.query('fixture', 'daily', { tsCode: 'old' })
    const current = flow.query('fixture', 'weekly', { tsCode: 'new' })
    const currentResponse = pageResponse({ requestId: 'current', apiName: 'weekly' })
    currentSuccess.resolve(currentResponse)
    expect(await current).toBe(true)
    staleSuccess.resolve(pageResponse({ requestId: 'stale' }))
    expect(await stale).toBe(false)
    expect(flow.result.value).toBe(currentResponse)

    const staleFailure = deferred()
    const newestSuccess = deferred()
    api.queryDataset
      .mockReturnValueOnce(staleFailure.promise)
      .mockReturnValueOnce(newestSuccess.promise)
    const rejected = flow.query('fixture', 'daily', {})
    const newest = flow.query('fixture', 'weekly', {})
    const newestResponse = pageResponse({ requestId: 'newest', apiName: 'weekly' })
    newestSuccess.resolve(newestResponse)
    expect(await newest).toBe(true)
    staleFailure.reject(new ClientError('NETWORK', 'stale-error'))
    expect(await rejected).toBe(false)
    expect(flow.result.value).toBe(newestResponse)
    expect(flow.error.value).toBeNull()

    const resetSuccess = deferred()
    api.queryDataset.mockReturnValueOnce(resetSuccess.promise)
    const afterReset = flow.query('fixture', 'daily', {})
    flow.reset()
    resetSuccess.resolve(pageResponse({ requestId: 'after-reset' }))
    expect(await afterReset).toBe(false)
    expect(flow.state.value).toBe('UNQUERIED')
    expect(flow.result.value).toBeNull()

    const resetFailure = deferred()
    api.queryDataset.mockReturnValueOnce(resetFailure.promise)
    const rejectedAfterReset = flow.query('fixture', 'daily', {})
    flow.reset()
    resetFailure.reject(new ClientError('NETWORK', 'after-reset-error'))
    expect(await rejectedAfterReset).toBe(false)
    expect(flow.state.value).toBe('UNQUERIED')
    expect(flow.error.value).toBeNull()
  })

  it('preserves query context across pages and accepts the normalized server page', async () => {
    const flow = useDatasetQuery()
    api.queryDataset.mockResolvedValueOnce(pageResponse({ totalElements: 40, totalPages: 2 }))
    expect(await flow.query('fixture', 'daily', { annDateFrom: '2026-09-01' })).toBe(true)

    const normalized = pageResponse({ page: 2, totalElements: 40, totalPages: 2 })
    api.queryDataset.mockResolvedValueOnce(normalized)
    const moving = flow.changePage(3)
    expect(flow.page.value).toBe(3)
    expect(api.queryDataset).toHaveBeenLastCalledWith('fixture', 'daily', {
      annDateFrom: '2026-09-01', page: 3, pageSize: 50,
    })
    expect(await moving).toBe(true)
    expect(flow.page.value).toBe(2)
    expect(flow.result.value).toBe(normalized)

    expect(await flow.changePage(2)).toBe(false)
    expect(api.queryDataset).toHaveBeenCalledTimes(2)
    api.queryDataset.mockResolvedValueOnce(pageResponse({ page: 1, totalElements: 40, totalPages: 2 }))
    expect(await flow.changePage(1)).toBe(true)
    expect(api.queryDataset).toHaveBeenLastCalledWith('fixture', 'daily', {
      annDateFrom: '2026-09-01', page: 1, pageSize: 50,
    })
  })

  it('changes page size from success or empty and blocks pagination while loading', async () => {
    const flow = useDatasetQuery()
    api.queryDataset.mockResolvedValueOnce(pageResponse())
    await flow.query('fixture', 'daily', { tsCode: '000001.SZ' })
    api.queryDataset.mockResolvedValueOnce(pageResponse({ pageSize: 20 }))
    expect(await flow.changePageSize(20)).toBe(true)
    expect(api.queryDataset).toHaveBeenLastCalledWith('fixture', 'daily', {
      tsCode: '000001.SZ', page: 1, pageSize: 20,
    })
    expect(await flow.changePageSize(20)).toBe(false)

    const emptyFlow = useDatasetQuery()
    api.queryDataset.mockResolvedValueOnce(pageResponse({
      apiName: 'weekly', totalElements: 0, totalPages: 0, items: [],
    }))
    await emptyFlow.query('fixture', 'weekly', {})
    api.queryDataset.mockResolvedValueOnce(pageResponse({
      apiName: 'weekly', pageSize: 100, totalElements: 0, totalPages: 0, items: [],
    }))
    expect(await emptyFlow.changePageSize(100)).toBe(true)
    expect(api.queryDataset).toHaveBeenLastCalledWith('fixture', 'weekly', {
      page: 1, pageSize: 100,
    })

    const pending = deferred()
    api.queryDataset.mockReturnValueOnce(pending.promise)
    const loading = emptyFlow.query('fixture', 'weekly', { tradeDateTo: '2026-09-05' })
    const calls = api.queryDataset.mock.calls.length
    expect(await emptyFlow.changePage(2)).toBe(false)
    expect(await emptyFlow.changePageSize(20)).toBe(false)
    expect(await emptyFlow.retry()).toBe(false)
    expect(api.queryDataset).toHaveBeenCalledTimes(calls)
    pending.resolve(pageResponse({ apiName: 'weekly', pageSize: 100 }))
    expect(await loading).toBe(true)
  })

  it('keeps page size for a new filter query and reset restores all defaults', async () => {
    const flow = useDatasetQuery()
    api.queryDataset.mockResolvedValueOnce(pageResponse())
    await flow.query('fixture', 'daily', {})
    api.queryDataset.mockResolvedValueOnce(pageResponse({ pageSize: 20 }))
    await flow.changePageSize(20)
    api.queryDataset.mockResolvedValueOnce(pageResponse({
      page: 2, pageSize: 20, totalElements: 21, totalPages: 2,
    }))
    await flow.changePage(2)

    const filtered = pageResponse({ page: 1, pageSize: 20 })
    api.queryDataset.mockResolvedValueOnce(filtered)
    expect(await flow.query('fixture', 'daily', { tsCode: '000002.SZ' })).toBe(true)
    expect(api.queryDataset).toHaveBeenLastCalledWith('fixture', 'daily', {
      tsCode: '000002.SZ', page: 1, pageSize: 20,
    })
    expect(flow.page.value).toBe(1)
    expect(flow.pageSize.value).toBe(20)

    const calls = api.queryDataset.mock.calls.length
    flow.reset()
    expect(flow.state.value).toBe('UNQUERIED')
    expect(flow.result.value).toBeNull()
    expect(flow.error.value).toBeNull()
    expect(flow.page.value).toBe(1)
    expect(flow.pageSize.value).toBe(50)
    expect(flow.loading.value).toBe(false)
    expect(flow.canRetry.value).toBe(false)
    expect(await flow.changePage(2)).toBe(false)
    expect(api.queryDataset).toHaveBeenCalledTimes(calls)
  })
})
```

- [ ] **Step 3: Run the strict RED and inspect the cause**

Run from `control-plane`:

```bash
"$TENSOR_NODE" "$TENSOR_NPM" run test:unit -- --run src/composables/useDatasetQuery.spec.js
```

Expected: command exits non-zero before collecting tests only because `./useDatasetQuery.js` cannot be resolved. Fix test syntax, imports, mocks, or environment first if any different error appears; do not create production code until this precise missing-module RED is observed.

- [ ] **Step 4: Add the minimal composable implementation**

Create `control-plane/src/composables/useDatasetQuery.js`:

```js
import { computed, ref, shallowRef } from 'vue'

import { queryDataset } from '../api/datasets.js'

function copyRequest(request) {
  return { ...request, criteria: { ...request.criteria } }
}

export function useDatasetQuery() {
  const state = ref('UNQUERIED')
  const result = shallowRef(null)
  const error = shallowRef(null)
  const page = ref(1)
  const pageSize = ref(50)
  let generation = 0
  let currentRequest = null
  let failedRequest = null

  const loading = computed(() => state.value === 'LOADING')
  const canRetry = computed(() =>
    state.value === 'FAILURE' &&
    failedRequest !== null &&
    error.value?.retryable === true,
  )

  async function execute(request) {
    const saved = copyRequest(request)
    const currentGeneration = ++generation
    currentRequest = saved
    failedRequest = null
    page.value = saved.page
    pageSize.value = saved.pageSize
    result.value = null
    error.value = null
    state.value = 'LOADING'

    try {
      const response = await queryDataset(saved.pluginId, saved.apiName, {
        ...saved.criteria,
        page: saved.page,
        pageSize: saved.pageSize,
      })
      if (currentGeneration !== generation) return false

      result.value = response
      page.value = response.page
      pageSize.value = response.pageSize
      currentRequest = copyRequest({
        ...saved,
        page: response.page,
        pageSize: response.pageSize,
      })
      failedRequest = null
      state.value = response.totalElements === 0 ? 'EMPTY' : 'SUCCESS'
      return true
    } catch (failure) {
      if (currentGeneration !== generation) return false

      result.value = null
      error.value = failure
      failedRequest = saved
      currentRequest = null
      state.value = 'FAILURE'
      return false
    }
  }

  function query(pluginId, apiName, criteria) {
    return execute({
      pluginId,
      apiName,
      criteria,
      page: 1,
      pageSize: pageSize.value,
    })
  }

  function changePage(nextPage) {
    if (loading.value || currentRequest === null || nextPage === page.value) {
      return Promise.resolve(false)
    }
    return execute({ ...currentRequest, page: nextPage })
  }

  function changePageSize(nextPageSize) {
    if (
      loading.value ||
      currentRequest === null ||
      nextPageSize === pageSize.value
    ) {
      return Promise.resolve(false)
    }
    return execute({ ...currentRequest, page: 1, pageSize: nextPageSize })
  }

  function retry() {
    if (loading.value || !canRetry.value) return Promise.resolve(false)
    return execute(failedRequest)
  }

  function reset() {
    generation += 1
    currentRequest = null
    failedRequest = null
    state.value = 'UNQUERIED'
    result.value = null
    error.value = null
    page.value = 1
    pageSize.value = 50
  }

  return {
    state,
    result,
    error,
    page,
    pageSize,
    loading,
    canRetry,
    query,
    changePage,
    changePageSize,
    retry,
    reset,
  }
}
```

- [ ] **Step 5: Run the focused GREEN**

Run from `control-plane`:

```bash
"$TENSOR_NODE" "$TENSOR_NPM" run test:unit -- --run src/composables/useDatasetQuery.spec.js
```

Expected: 1 test file and 8 tests pass with no unhandled rejection, console output, Vue warning, or Vitest warning. If a test fails, preserve the approved expectation and make the smallest production correction.

- [ ] **Step 6: Run full regression and production build**

Run from `control-plane`:

```bash
"$TENSOR_NODE" "$TENSOR_NPM" run test:unit -- --run
"$TENSOR_NODE" "$TENSOR_NPM" run build
```

Expected: 19 files / 112 tests pass. Vite exits 0 and may print only the existing Element Plus chunk-size notice; no new warning or error is accepted.

- [ ] **Step 7: Verify exports, scope, safety, formatting, and tracking**

Run from the repository root:

```bash
"$TENSOR_NODE" --input-type=module -e 'const m=await import("./control-plane/src/composables/useDatasetQuery.js"); if (Object.keys(m).join(",") !== "useDatasetQuery") process.exit(1)'
git diff --check
git status --short --untracked-files=all -- control-plane/src/composables
git diff --exit-code -- \
  control-plane/package.json control-plane/package-lock.json \
  control-plane/vite.config.js control-plane/vitest.config.js \
  control-plane/playwright.config.js control-plane/src/api \
  control-plane/src/components control-plane/src/router \
  control-plane/src/layouts control-plane/src/views \
  control-plane/src/style.css control-plane/src/utils
rg -n 'v-html|innerHTML|axios|fetch\(|setTimeout|setInterval|AbortController|localStorage|sessionStorage|Authorization|Cookie|token|password|console\.|Math\.(ceil|floor|round)|slice\(' \
  control-plane/src/composables/useDatasetQuery.js
```

Expected: export, protected paths, and `git diff --check` exit 0; scoped status shows only the two new task files. The final `rg` prints nothing and exits 1. Review the complete diff and confirm that public names, state values, request keys, 1/50 defaults, 20/50/100 behavior, and eight literal test expectations match the design.

- [ ] **Step 8: Stage exact paths, inspect, and commit**

Run from the repository root:

```bash
git add \
  control-plane/src/composables/useDatasetQuery.js \
  control-plane/src/composables/useDatasetQuery.spec.js
git diff --cached --check
git diff --cached --name-status
git diff --cached
git commit -m "feat(ui): manage dataset query lifecycle" -- \
  control-plane/src/composables/useDatasetQuery.js \
  control-plane/src/composables/useDatasetQuery.spec.js
```

Expected: cached name-status contains exactly two added files, the cached diff contains no generated output or unrelated change, and the commit subject is exact. After committing, re-run `git show --stat --oneline HEAD` and confirm the commit contains only those two files.
