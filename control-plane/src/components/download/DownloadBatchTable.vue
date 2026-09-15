<script setup>
import { computed, nextTick, watch } from 'vue'

import { parseTaskJson } from '../../api/downloadTaskDtos.js'
import AsyncStatePanel from '../common/AsyncStatePanel.vue'
import WorkbenchPanel from '../common/WorkbenchPanel.vue'

const PAGE_SIZES = [20, 50, 100]
const MAX_PAGE = 2_147_483_647n
const STATUS_LABELS = Object.freeze({
  PENDING: '待执行',
  RUNNING: '运行中',
  SUCCEEDED: '已成功',
  FAILED: '失败',
  SPLIT: '已拆分',
})

const props = defineProps({
  page: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  result: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  error: { type: Object, default: null },
  lastUpdatedAt: { type: Date, default: null },
})

const emit = defineEmits(['refresh', 'update:page', 'update:pageSize'])

const total = computed(() => props.result?.total ?? 0n)
const totalPages = computed(() => {
  if (total.value === 0n) return 0
  const pages =
    (total.value + BigInt(props.pageSize) - 1n) / BigInt(props.pageSize)
  return Number(pages > MAX_PAGE ? MAX_PAGE : pages)
})
const rows = computed(() => props.result?.items ?? [])
let ignoredClamp = null

watch(
  totalPages,
  (nextTotalPages) => {
    if (props.page <= nextTotalPages) return
    const clampTarget = Math.max(1, nextTotalPages)
    ignoredClamp = clampTarget
    nextTick(() => {
      if (ignoredClamp === clampTarget) ignoredClamp = null
    })
  },
  { flush: 'sync' },
)

function integer(value) {
  return value.toString()
}

function parameters(params) {
  return Object.entries(params)
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([name, value]) => `${name}=${value}`)
}

function formatTime(value) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'medium',
  }).format(new Date(value))
}

function errorMessage(error) {
  if (
    error?.kind === 'INVALID_RESPONSE' &&
    typeof parseTaskJson('{"n":1}') === 'string'
  ) {
    return '当前浏览器无法保真处理任务数值，请更新浏览器后重试。'
  }
  return error.message
}

function updatePage(nextPage) {
  if (nextPage === ignoredClamp) {
    ignoredClamp = null
    return
  }
  if (nextPage !== props.page) emit('update:page', nextPage)
}

function updatePageSize(nextPageSize) {
  emit('update:pageSize', nextPageSize)
}
</script>

<template>
  <WorkbenchPanel class="download-batch-table" heading-id="download-batches-title" title="批次明细">
    <div class="download-batch-table__toolbar">
      <span data-total>共 {{ integer(total) }} 个叶子批次，第 {{ page }} / {{ totalPages }} 页</span>
      <el-button data-refresh native-type="button" :aria-busy="loading" @click="emit('refresh')">刷新批次</el-button>
    </div>
    <div v-if="error && result" class="download-batch-table__warning" role="status">
      <strong>状态暂时无法更新</strong>
      <span>{{ errorMessage(error) }}</span>
      <span v-if="error.requestId">请求 ID：{{ error.requestId }}</span>
      <span v-if="lastUpdatedAt">上次更新 {{ formatTime(lastUpdatedAt) }}</span>
    </div>
    <AsyncStatePanel v-if="loading && !result" state="LOADING" title="正在加载批次" message="请稍候。" />
    <AsyncStatePanel v-else-if="error && !result" state="FAILURE" title="批次加载失败"
      :message="errorMessage(error)" :request-id="error.requestId ?? ''" retry-label="重新加载" @retry="emit('refresh')" />
    <AsyncStatePanel v-else-if="!result" state="INITIAL" title="批次尚未加载" message="页面可见时将自动加载。" />
    <template v-else>
      <div v-if="rows.length" class="download-batch-table__scroll" role="region" aria-label="批次表格" tabindex="0">
        <table>
          <thead><tr>
            <th scope="col">批次 / 区间</th><th scope="col">来源参数</th><th scope="col">状态 / 尝试次数</th>
            <th scope="col">行数 / 写入次数</th><th scope="col">错误原因</th><th scope="col">时间</th>
          </tr></thead>
          <tbody>
            <tr v-for="batch in rows" :key="batch.batchId">
              <td>
                <strong>{{ batch.batchKey }}</strong>
                <code>{{ batch.batchId }}</code>
                <small v-if="batch.parentBatchId">父批次 {{ batch.parentBatchId }}</small>
                <strong :class="{ 'download-batch-table__status--failed': batch.status === 'FAILED' }">
                  {{ batch.rangeStart === null ? '单次请求' : `${batch.rangeStart} 至 ${batch.rangeEnd}` }}
                </strong>
              </td>
              <td><code v-for="parameter in parameters(batch.sourceParams)" :key="parameter">{{ parameter }}</code></td>
              <td>
                <strong class="download-batch-table__status" :class="`download-batch-table__status--${batch.status.toLowerCase()}`">{{ STATUS_LABELS[batch.status] }}</strong>
                <span>尝试次数 {{ batch.attemptCount }}{{ batch.attemptCount === 0 ? '（尚未尝试）' : '' }}</span>
              </td>
              <td>
                <span>来源行数 {{ integer(batch.sourceRows) }}</span>
                <span>新增记录次数 {{ integer(batch.insertedRows) }}</span>
                <span>更新记录次数 {{ integer(batch.updatedRows) }}</span>
              </td>
              <td>
                <template v-if="batch.error"><strong>{{ batch.error.code }}</strong><span>{{ batch.error.message }}</span></template>
                <span v-else>无</span>
              </td>
              <td>
                <span v-for="[key, label] in [['updatedAt', '更新'], ['createdAt', '创建'], ['startedAt', '开始'], ['finishedAt', '结束']]" :key="key">
                  {{ label }}
                  <time v-if="batch[key]" :datetime="batch[key]" :title="batch[key]">{{ formatTime(batch[key]) }}</time>
                  <template v-else>尚无</template>
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="download-batch-table__empty">
        <p>{{ page === 1 ? '暂无批次' : '本页暂无批次' }}</p>
        <el-button v-if="page !== 1" native-type="button" @click="emit('update:page', 1)">返回第一页</el-button>
      </div>
      <nav class="download-batch-table__pagination" aria-label="批次分页" :aria-busy="loading">
        <el-pagination :current-page="page" :page-size="pageSize" :page-count="totalPages" :disabled="total === 0n"
          :page-sizes="PAGE_SIZES" :pager-count="5" layout="sizes, prev, pager, next"
          prev-text="上一页" next-text="下一页" :hide-on-single-page="false"
          @update:current-page="updatePage" @update:page-size="updatePageSize" />
        <span>批次按服务端计划排序，拆分可能改变总批数。</span>
      </nav>
    </template>
  </WorkbenchPanel>
</template>

<style scoped>
.download-batch-table {
  min-width: 0;
}

.download-batch-table__toolbar,
.download-batch-table__warning,
.download-batch-table__empty,
.download-batch-table__pagination {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 18px 24px;
}

.download-batch-table__toolbar {
  justify-content: space-between;
  border-bottom: 1px solid var(--tensor-line);
}

.download-batch-table__toolbar span,
.download-batch-table__warning,
.download-batch-table__empty,
.download-batch-table__pagination {
  color: var(--tensor-muted);
  font-size: 12px;
  line-height: 1.8;
}

.download-batch-table__warning {
  color: var(--tensor-text);
  background: var(--tensor-accent-bg);
}

.download-batch-table__warning span:last-child {
  color: var(--tensor-muted);
}

.download-batch-table__scroll {
  max-width: 100%;
  overflow-x: auto;
  outline-offset: -3px;
}

.download-batch-table__scroll:focus-visible {
  outline: 3px solid var(--tensor-interactive-color);
}

table {
  width: 100%;
  min-width: 1380px;
  border-collapse: collapse;
  color: var(--tensor-text);
  font-size: 12px;
  line-height: 1.6;
}

th,
td {
  padding: 16px;
  text-align: left;
  vertical-align: top;
  border-bottom: 1px solid var(--tensor-line);
}

th {
  color: var(--tensor-muted);
  font-weight: 600;
  background: var(--tensor-raised);
}

td > strong,
td > span,
td > code,
td > small {
  display: block;
}

td > * + * {
  margin-top: 5px;
}

td code,
td time,
[data-total] {
  font-variant-numeric: tabular-nums;
}

td { max-width: 290px; overflow-wrap: anywhere; }

td code,
td small {
  color: var(--tensor-muted);
}

.download-batch-table__status {
  color: var(--tensor-muted);
}

.download-batch-table__status--running,
.download-batch-table__status--pending {
  color: var(--tensor-accent);
}

.download-batch-table__status--succeeded {
  color: var(--tensor-success);
}

.download-batch-table__status--partial_failed,
.download-batch-table__status--failed,
.download-batch-table__status--interrupted {
  color: var(--tensor-error);
}

.download-batch-table__empty {
  min-height: 120px;
  justify-content: center;
}

.download-batch-table__empty p {
  margin: 0;
}

.download-batch-table__pagination {
  border-top: 1px solid var(--tensor-line);
}

.download-batch-table__pagination :deep(.el-pagination) {
  display: flex;
  flex-wrap: wrap;
  min-width: 0;
}

@media (max-width: 680px) {
  .download-batch-table__toolbar,
  .download-batch-table__warning,
  .download-batch-table__empty,
  .download-batch-table__pagination {
    padding-right: 18px;
    padding-left: 18px;
  }

  .download-batch-table__pagination :deep(.el-pagination__sizes) {
    flex: 1 0 100%;
    margin-right: 0;
  }
}
</style>
