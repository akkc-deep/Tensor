<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { CircleCheckFilled, Clock, WarningFilled } from '@element-plus/icons-vue'
import { listIntegrityChecks } from '../../api/integrityChecks.js'
import { formatIngestedAt } from '../../utils/format.js'

const props = defineProps({ sources: { type: Array, default: () => [] } })
const pluginId = ref('')
const status = ref('')
const page = ref(1)
const pageSize = ref(20)
const snapshot = ref(null)
const error = ref(null)
const loading = ref(false)
let generation = 0
let controller = null

const statusText = {
  QUEUED: '排队中', RUNNING: '执行中', COMPLETED: '已完成', FAILED: '失败', INTERRUPTED: '已中断',
}
const statusIcon = {
  QUEUED: Clock, RUNNING: Clock, COMPLETED: CircleCheckFilled,
  FAILED: WarningFilled, INTERRUPTED: WarningFilled,
}
const total = computed(() => snapshot.value?.total ?? 0n)
const totalPages = computed(() => total.value === 0n
  ? 0n
  : (total.value + BigInt(pageSize.value) - 1n) / BigInt(pageSize.value))
const canNext = computed(() => !loading.value &&
  BigInt(page.value) * BigInt(pageSize.value) < total.value && page.value < 2147483647)
const criteria = computed(() => {
  const value = { page: page.value, pageSize: pageSize.value }
  if (pluginId.value) value.pluginId = pluginId.value
  if (status.value) value.status = status.value
  return value
})

function load(preserve = true) {
  controller?.abort()
  controller = new AbortController()
  const activeController = controller
  const epoch = ++generation
  loading.value = true
  error.value = null
  if (!preserve) snapshot.value = null
  const expected = { ...criteria.value }
  return listIntegrityChecks(expected, { signal: activeController.signal })
    .then((value) => {
      if (epoch === generation && !activeController.signal.aborted) snapshot.value = value
    }, (reason) => {
      if (epoch === generation && !activeController.signal.aborted) error.value = reason
    })
    .finally(() => {
      if (epoch === generation) loading.value = false
    })
}
function changeFilter() {
  page.value = 1
  load(false)
}
function changeSize() {
  page.value = 1
  load(false)
}
function previous() {
  if (page.value <= 1) return
  page.value -= 1
  load(false)
}
function next() {
  if (!canNext.value) return
  page.value += 1
  load(false)
}
function sourceName(id) {
  return props.sources.find((item) => item.pluginId === id)?.displayName ?? id
}

onMounted(() => load(false))
onBeforeUnmount(() => {
  generation += 1
  controller?.abort()
})
</script>

<template>
  <section class="history" aria-labelledby="integrity-history-title">
    <header>
      <h2 id="integrity-history-title">检查历史</h2>
      <button type="button" aria-label="刷新历史" :disabled="loading" @click="load(true)">
        刷新历史
      </button>
    </header>
    <div class="filters">
      <label>
        历史数据源
        <select v-model="pluginId" aria-label="历史数据源" :disabled="loading" @change="changeFilter">
          <option value="">全部数据源</option>
          <option v-for="source in sources" :key="source.pluginId" :value="source.pluginId">
            {{ source.displayName }}
          </option>
        </select>
      </label>
      <label>
        执行状态
        <select v-model="status" aria-label="执行状态" :disabled="loading" @change="changeFilter">
          <option value="">全部状态</option>
          <option v-for="(label, value) in statusText" :key="value" :value="value">
            {{ label }}
          </option>
        </select>
      </label>
    </div>
    <p v-if="loading && !snapshot" role="status">正在加载检查历史…</p>
    <div v-if="error" class="history-error" role="alert">
      <b>检查历史加载失败</b>
      <span v-if="error.requestId">请求 ID：<code>{{ error.requestId }}</code></span>
      <button type="button" @click="load(true)">重试</button>
    </div>
    <p v-if="!loading && !error && snapshot && snapshot.items.length === 0" role="status">
      {{ pluginId || status ? '没有符合筛选条件的检查' : '还没有检查历史' }}
    </p>
    <div v-if="snapshot?.items.length" class="history-table">
      <table>
        <thead>
          <tr><th>来源</th><th>股票摘要</th><th>起止范围</th><th>接口数</th><th>创建时间</th><th>执行状态</th><th>结论入口</th></tr>
        </thead>
        <tbody>
          <tr v-for="item in snapshot.items" :key="item.checkId">
            <td>{{ sourceName(item.pluginId) }}</td>
            <td>
              <details>
                <summary>
                  {{ item.scope.symbols.slice(0, 3).join('、') }}
                  <span v-if="item.scope.symbols.length > 3">等 {{ item.scope.symbols.length }} 个</span>
                </summary>
                <span>{{ item.scope.symbols.join('、') }}</span>
              </details>
            </td>
            <td>{{ item.scope.startDate }} 至 {{ item.scope.endDate }}</td>
            <td>{{ item.scope.apiNames.length }}</td>
            <td>{{ formatIngestedAt(item.createdAt) }}（Asia/Shanghai）</td>
            <td class="status-cell">
              <component :is="statusIcon[item.status]" aria-hidden="true" />
              {{ statusText[item.status] }}
            </td>
            <td><a :href="`/integrity/checks/${item.checkId}`">查看报告结论</a></td>
          </tr>
        </tbody>
      </table>
    </div>
    <footer v-if="snapshot">
      <label>
        每页条数
        <select v-model.number="pageSize" aria-label="每页条数" :disabled="loading" @change="changeSize">
          <option :value="20">20</option><option :value="50">50</option><option :value="100">100</option>
        </select>
      </label>
      <span>共 {{ total.toString() }} 条</span>
      <span>第 {{ snapshot.total === 0n ? 0 : page }} 页 / 共 {{ totalPages.toString() }} 页</span>
      <button type="button" aria-label="上一页" :disabled="loading || page <= 1" @click="previous">上一页</button>
      <button type="button" aria-label="下一页" :disabled="!canNext" @click="next">下一页</button>
    </footer>
  </section>
</template>

<style scoped>
.history {
  min-width: 0;
  padding: 24px;
  border: 1px solid var(--tensor-line);
  background: var(--tensor-surface);
}
header, .filters, footer, .status-cell {
  display: flex;
  align-items: center;
}
header { justify-content: space-between; gap: 12px; }
h2, p { margin: 0; }
button, select {
  padding: 7px 10px;
  border: 1px solid var(--tensor-line);
  border-radius: 4px;
  background: var(--tensor-raised);
  color: var(--tensor-text);
}
.filters { flex-wrap: wrap; gap: 14px; margin: 20px 0; }
.filters label, footer label {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--tensor-muted);
  font-size: 12px;
}
.history-error {
  display: flex;
  flex-wrap: wrap;
  gap: 9px;
  margin: 12px 0;
  color: var(--tensor-error);
  overflow-wrap: anywhere;
}
.history-table { max-width: 100%; overflow-x: auto; }
table { width: 100%; min-width: 850px; border-collapse: collapse; text-align: left; font-size: 13px; }
th, td { padding: 12px 10px; border-bottom: 1px solid var(--tensor-line); vertical-align: top; }
th { color: var(--tensor-muted); font-size: 12px; font-weight: 500; }
td, details span { overflow-wrap: anywhere; }
.status-cell { gap: 6px; }
.status-cell svg { width: 15px; height: 15px; color: var(--tensor-muted); }
a { color: var(--tensor-accent); }
footer { justify-content: flex-end; flex-wrap: wrap; gap: 10px; margin-top: 16px; font-size: 12px; }
@media (max-width: 680px) {
  .history { padding: 18px; }
  header { align-items: flex-start; }
  .filters { align-items: stretch; flex-direction: column; }
  .filters label { justify-content: space-between; }
  .filters select { min-width: 0; max-width: 60%; }
  footer { justify-content: flex-start; }
}
</style>
