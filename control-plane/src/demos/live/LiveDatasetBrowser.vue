<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef } from 'vue'
import { Search, ArrowLeft, ArrowRight, Document } from '@element-plus/icons-vue'
import { listDataSources } from '../../api/dataSources.js'
import { getDataset, listDatasets } from '../../api/datasets.js'
import { useDatasetFilters } from '../../composables/useDatasetFilters.js'
import { useDatasetQuery } from '../../composables/useDatasetQuery.js'
import { formatCell } from '../../utils/format.js'
import ErrorNotice from './ErrorNotice.vue'

const sources = shallowRef([]), datasets = shallowRef([]), definition = shallowRef(null)
const pluginId = ref(''), apiName = ref(''), metadataError = shallowRef(null), loading = ref(false), form = ref(null)
const query = reactive(useDatasetQuery())
const filters = reactive(useDatasetFilters(computed(() => definition.value?.filters ?? [])))
const fields = computed(() => (definition.value?.filters ?? []).flatMap(filter => {
  if (filter.field === 'ts_code' && filter.operator === 'EQ' && filter.controlType === 'TEXT') return [{ name: 'tsCode', label: '股票代码', type: 'text' }]
  if (filter.operator !== 'BETWEEN' || filter.controlType !== 'DATE_RANGE') return []
  const prefix = { trade_date: ['tradeDate', '交易'], ann_date: ['annDate', '公告'] }[filter.field]
  return prefix ? [{ name: `${prefix[0]}From`, label: `${prefix[1]}开始日期`, type: 'date' }, { name: `${prefix[0]}To`, label: '结束日期', type: 'date' }] : []
}))
const columns = computed(() => [...(definition.value?.columns ?? []),
  { name: 'source_plugin', label: '来源插件' }, { name: 'source_api', label: '来源接口' }, { name: 'ingested_at', label: '入库时间' },
])
let generation = 0, retryMetadata = () => loadSources()
function begin() {
  definition.value = null
  query.reset()
  metadataError.value = null
  loading.value = true
  return ++generation
}
async function loadSources() {
  const epoch = begin()
  try {
    const result = await listDataSources()
    if (epoch !== generation) return
    sources.value = result
    if (result.length) return selectSource((result.find(item => item.pluginId === 'tushare_pro') ?? result[0]).pluginId)
    loading.value = false
  } catch (error) {
    if (epoch !== generation) return
    metadataError.value = error; loading.value = false; retryMetadata = loadSources
  }
}
async function selectSource(id) {
  const epoch = begin()
  pluginId.value = id; apiName.value = ''; datasets.value = []
  try {
    const result = await listDatasets(id)
    if (epoch !== generation) return
    datasets.value = result
    if (result.length) return selectDataset((result.find(item => item.apiName === 'daily') ?? result[0]).apiName)
    loading.value = false
  } catch (error) {
    if (epoch !== generation) return
    metadataError.value = error; loading.value = false; retryMetadata = () => selectSource(id)
  }
}
async function selectDataset(id) {
  const epoch = begin()
  apiName.value = id
  try {
    const result = await getDataset(pluginId.value, id)
    if (epoch !== generation) return
    definition.value = result
    loading.value = false
  } catch (error) {
    if (epoch !== generation) return
    metadataError.value = error; loading.value = false; retryMetadata = () => selectDataset(id)
  }
}
async function submit() {
  if (!definition.value || query.loading || loading.value) return
  if (!filters.validateValues()) {
    await nextTick()
    form.value?.querySelector('[aria-invalid="true"]')?.focus()
    return
  }
  await query.query(pluginId.value, apiName.value, filters.criteria())
}
function reset() { filters.reset(); query.reset() }
onMounted(loadSources)
onBeforeUnmount(() => { generation++; query.reset() })
</script>

<template>
  <section class="data-browser" aria-label="数据查看">
    <form ref="form" class="query-form" novalidate @submit.prevent="submit">
      <label class="field"><span>数据源</span><select :value="pluginId" :disabled="!sources.length" @change="selectSource($event.target.value)"><option v-if="!sources.length" value="">等待连接</option><option v-for="item in sources" :key="item.pluginId" :value="item.pluginId">{{ item.displayName }}</option></select></label>
      <label class="field dataset-choice"><span>数据集</span><select :value="apiName" :disabled="!datasets.length" @change="selectDataset($event.target.value)"><option v-if="!datasets.length" value="">暂无数据集</option><option v-for="item in datasets" :key="item.apiName" :value="item.apiName">{{ item.displayName }} · {{ item.apiName }}</option></select></label>
      <label v-for="field in fields" :key="field.name" class="field"><span>{{ field.label }}</span><input :name="field.name" :type="field.type" :value="filters.values[field.name]" :placeholder="field.type === 'text' ? '全部股票' : undefined" :aria-invalid="Boolean(filters.errors[field.name])" :aria-describedby="filters.errors[field.name] ? `query-error-${field.name}` : undefined" autocomplete="off" @input="filters.setValue(field.name, $event.target.value)" /><small v-if="filters.errors[field.name]" :id="`query-error-${field.name}`" class="form-error" role="alert">{{ filters.errors[field.name] }}</small></label>
      <button class="button primary" type="submit" :disabled="!definition || loading || query.loading"><Search />{{ query.loading ? '查询中…' : '查询' }}</button>
      <button class="text-button" type="button" @click="reset">重置</button>
    </form>
    <ErrorNotice :error="metadataError" title="数据集加载失败" retry-label="重新加载数据集" :disabled="loading" @retry="retryMetadata()" />
    <ErrorNotice :error="query.error" title="查询失败" :retry-label="query.canRetry ? '重试原查询' : undefined" :disabled="query.loading" @retry="query.retry" />
    <header class="data-caption"><h2>{{ definition?.displayName || '数据记录' }}<code>{{ definition?.apiName }}</code></h2><span>{{ query.result ? `共 ${query.result.totalElements} 条记录` : '只读查询' }}</span></header>
    <div v-if="loading || query.loading" class="loading-state" role="status">{{ loading ? '正在加载数据集定义…' : '正在查询数据…' }}</div>
    <div v-else-if="!query.result" class="empty-state"><Document /><h3>{{ query.state === 'FAILURE' ? '查询暂未完成' : '选择条件，查看已入库数据' }}</h3><p>{{ query.state === 'FAILURE' ? '检查上方反馈后重新查询。' : '可按股票和日期筛选，留空则查询全部记录。' }}</p></div>
    <div v-else-if="!query.result.items.length" class="empty-state"><Document /><h3>没有匹配的记录</h3><p>调整筛选条件，或先在下载工作台采集数据。</p></div>
    <div v-else class="table-scroll data-scroll" tabindex="0" role="region" aria-label="数据表格，可横向滚动">
      <table class="records-table"><thead><tr><th v-for="column in columns" :key="column.name" scope="col"><span>{{ column.label }}</span><code v-if="column.label !== column.name">{{ column.name }}</code></th></tr></thead><tbody><tr v-for="(row, index) in query.result.items" :key="index"><td v-for="column in columns" :key="column.name" :title="String(formatCell(row[column.name], column))" :class="{ numeric: ['LONG', 'DECIMAL'].includes(column.logicalType) }">{{ formatCell(row[column.name], column) }}</td></tr></tbody></table>
    </div>
    <footer v-if="query.result" class="pagination live-data-pagination"><label>每页 <select :value="query.pageSize" :disabled="query.loading" aria-label="每页记录数" @change="query.changePageSize(Number($event.target.value))"><option v-for="size in [20, 50, 100]" :key="size" :value="size">{{ size }}</option></select> 条 · {{ columns.length }} 个字段</label><div><button class="icon-button" aria-label="数据上一页" :disabled="query.page <= 1 || query.loading" @click="query.changePage(query.page - 1)"><ArrowLeft /></button><span>{{ query.page }} / {{ Math.max(1, query.result.totalPages) }}</span><button class="icon-button" aria-label="数据下一页" :disabled="query.page >= query.result.totalPages || query.loading" @click="query.changePage(query.page + 1)"><ArrowRight /></button></div></footer>
  </section>
</template>
