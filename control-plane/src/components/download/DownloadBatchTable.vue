<script setup>
import { computed } from 'vue'

import { parseTaskJson } from '../../api/downloadTaskDtos.js'
import AsyncStatePanel from '../common/AsyncStatePanel.vue'


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

</script>

<template>
  <section class="download-batch-table" aria-labelledby="download-batches-title">
    <header class="download-batch-table__heading"><h3 id="download-batches-title">批次明细 <span class="count">{{ integer(total) }}</span></h3><button data-refresh class="text-button" type="button" :disabled="loading" :aria-busy="loading" @click="emit('refresh')">{{ loading ? '刷新中…' : '刷新批次' }}</button></header>
    <div v-if="error && result" class="download-batch-table__warning" role="status">
      <strong>状态暂时无法更新</strong><span>{{ errorMessage(error) }}</span>
      <span v-if="error.requestId">请求 ID：{{ error.requestId }}</span>
      <span v-if="lastUpdatedAt">上次更新 {{ formatTime(lastUpdatedAt) }}</span>
    </div>
    <AsyncStatePanel v-if="loading && !result" state="LOADING" title="正在加载批次" message="请稍候。" />
    <AsyncStatePanel v-else-if="error && !result" state="FAILURE" title="批次加载失败"
      :message="errorMessage(error)" :request-id="error.requestId ?? ''" retry-label="重新加载" @retry="emit('refresh')" />
    <AsyncStatePanel v-else-if="!result" state="INITIAL" title="批次尚未加载" message="页面可见时将自动加载。" />
    <template v-else>
      <ol v-if="rows.length" aria-label="批次列表">
        <li v-for="batch in rows" :key="batch.batchId">
          <div class="batch-heading"><code>{{ batch.batchKey }}</code><span class="batch-status" :class="`batch-status--${batch.status.toLowerCase()}`">{{ STATUS_LABELS[batch.status] }}</span></div>
          <p>{{ batch.rangeStart === null ? '单次请求' : `${batch.rangeStart} 至 ${batch.rangeEnd}` }}</p>
          <div class="batch-meta"><span>尝试次数 {{ batch.attemptCount }}{{ batch.attemptCount === 0 ? '（尚未尝试）' : '' }}</span><span>来源行数 {{ integer(batch.sourceRows) }}</span></div>
          <div class="batch-meta"><span>新增记录次数 {{ integer(batch.insertedRows) }}</span><span>更新记录次数 {{ integer(batch.updatedRows) }}</span></div>
          <p v-if="batch.error" class="batch-error"><strong>{{ batch.error.code }}</strong><br />{{ batch.error.message }}</p>
          <p v-if="batch.status === 'SPLIT'" class="batch-meta">此批次已拆分，执行结果见子批次。</p>
          <details>
            <summary>批次记录</summary>
            <p>批次 <code>{{ batch.batchId }}</code></p><p v-if="batch.parentBatchId">父批次 <code>{{ batch.parentBatchId }}</code></p>
            <div class="batch-parameters"><code v-for="parameter in parameters(batch.sourceParams)" :key="parameter">{{ parameter }}</code></div>
            <p v-if="!batch.error">错误原因：无</p>
            <p v-for="[key, label] in [['updatedAt', '更新'], ['createdAt', '创建'], ['startedAt', '开始'], ['finishedAt', '结束']]" :key="key">
              {{ label }} <time v-if="batch[key]" :datetime="batch[key]" :title="batch[key]">{{ formatTime(batch[key]) }}</time><template v-else>尚无</template>
            </p>
          </details>
        </li>
      </ol>
      <div v-else class="download-batch-table__empty">
        <p>{{ page === 1 ? '暂无批次' : '本页暂无批次' }}</p>
        <button v-if="page !== 1" data-first-page class="text-button" type="button" @click="emit('update:page', 1)">返回第一页</button>
      </div>
      <nav class="download-batch-table__pagination" aria-label="批次分页" :aria-busy="loading">
        <span data-total>共 {{ integer(total) }} 个叶子批次<template v-if="totalPages">，第 {{ page }} / {{ totalPages }} 页</template></span>
        <label>每页 <select :value="pageSize" aria-label="每页批次数" :disabled="loading" @change="emit('update:pageSize', Number($event.target.value))"><option v-for="size in PAGE_SIZES" :key="size" :value="size">{{ size }}</option></select> 条</label>
        <div class="page-controls"><button class="btn-prev text-button" type="button" :disabled="loading || total === 0n || page <= 1" @click="emit('update:page', page - 1)">上一页</button><button class="btn-next text-button" type="button" :disabled="loading || page >= totalPages" @click="emit('update:page', page + 1)">下一页</button></div>
      </nav>
    </template>
    <p class="detail-footnote">当前展示叶子批次。批次按服务端计划排序，拆分可能改变总批数。</p>
  </section>
</template>

<style scoped>
.download-batch-table { min-width: 0; font-size: 1.2rem; line-height: 1.6; overflow-wrap: anywhere; }
.download-batch-table__heading, .batch-heading, .batch-meta, .page-controls { display: flex; align-items: center; justify-content: space-between; gap: 1.2rem; }
.download-batch-table__heading { margin-bottom: 1.2rem; }
h3 { margin: 0; font-size: 1.4rem; font-weight: 600; }
.count { display: inline-grid; place-items: center; min-width: 2rem; height: 1.9rem; padding: 0 0.5rem; margin-left: 0.5rem; border-radius: 0.4rem; background: var(--tensor-raised); color: var(--tensor-muted); font-size: 1.2rem; font-weight: 400; }
ol { margin: 0; padding: 0; list-style: none; }
li { padding: 1.5rem 0; border-top: 0.1rem solid var(--tensor-line); }
li p { margin: 0.7rem 0 0; }
code, time, [data-total], .batch-meta { font-variant-numeric: tabular-nums; }
.batch-heading code { min-width: 0; }
.batch-status { display: inline-flex; align-items: center; flex-shrink: 0; gap: 0.5rem; color: var(--tensor-muted); font-size: 1.2rem; }
.batch-status::before { content: ''; width: 0.5rem; height: 0.5rem; border-radius: 50%; background: currentColor; }
.batch-status--running, .batch-status--pending { color: var(--tensor-accent); }
.batch-status--succeeded { color: var(--tensor-success); }
.batch-status--failed { color: var(--tensor-warning); }
.batch-meta { flex-wrap: wrap; margin-top: 0.8rem; color: var(--tensor-muted); }
.batch-meta span { min-width: 0; }
.batch-error { color: var(--tensor-warning); }
details { margin-top: 1.2rem; color: var(--tensor-muted); }
summary { cursor: pointer; }
.batch-parameters { display: grid; gap: 0.4rem; margin-top: 0.8rem; }
.download-batch-table__warning { display: grid; gap: 0.4rem; padding: 1.2rem; margin: 1.2rem 0; color: var(--tensor-text); background: var(--tensor-accent-bg); border-radius: 0.7rem; }
.download-batch-table__empty { padding: 2.4rem 0; text-align: center; color: var(--tensor-muted); }
.download-batch-table__pagination { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 1.2rem; padding-top: 1.6rem; border-top: 0.1rem solid var(--tensor-line); color: var(--tensor-muted); }
[data-total] { flex-basis: 100%; }
select { border: 0.1rem solid var(--tensor-line); border-radius: 0.5rem; padding: 0.4rem; font: inherit; color: var(--tensor-text); background: var(--tensor-surface); }
.detail-footnote { margin: 2.5rem 0 0; color: var(--tensor-muted); }
button:disabled { opacity: .55; cursor: not-allowed; }
:is(button, select, summary):focus-visible { outline: 0.2rem solid var(--tensor-interactive-color); outline-offset: 0.3rem; }
</style>
