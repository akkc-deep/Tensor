<script setup>
import { computed, inject, ref, watch } from 'vue'
import { Search, ArrowLeft, ArrowRight, Document } from '@element-plus/icons-vue'
import { demoKey } from './demoState.js'
const demo = inject(demoKey)
const id = ref('daily')
const code = ref('')
const from = ref('')
const to = ref('')
const applied = ref({ code: '', from: '', to: '' })
const page = ref(1)
const loading = ref(false)
const error = ref('')
const dataset = computed(() => demo.catalog.find(item => item.id === id.value))
const dateField = computed(() => dataset.value.filters.find(f => ['trade_date', 'ann_date'].includes(f)))
const filtered = computed(() => dataset.value.rows.filter(row => {
  const date = String(row[dateField.value] ?? '').replaceAll('-', '')
  return (!applied.value.code || row.ts_code === applied.value.code) && (!applied.value.from || date >= applied.value.from.replaceAll('-', '')) && (!applied.value.to || date <= applied.value.to.replaceAll('-', ''))
}))
const pages = computed(() => Math.max(1, Math.ceil(filtered.value.length / 20)))
const rows = computed(() => filtered.value.slice((page.value - 1) * 20, page.value * 20))
watch(id, () => { code.value = ''; from.value = ''; to.value = ''; applied.value = { code: '', from: '', to: '' }; page.value = 1; error.value = '' })
async function query() {
  error.value = ''
  if (from.value && to.value && from.value > to.value) { error.value = '开始日期不能晚于结束日期。'; return }
  loading.value = true
  applied.value = { code: code.value.trim().toUpperCase(), from: from.value, to: to.value }
  page.value = 1
  await new Promise(resolve => setTimeout(resolve, 250))
  loading.value = false
}
function reset() { code.value = ''; from.value = ''; to.value = ''; query() }
function display(value) { return value === null || value === undefined ? '空值' : String(value) }
</script>

<template>
  <section class="data-browser">
    <form class="query-form" @submit.prevent="query">
      <label class="field dataset-choice"><span>数据集</span><select v-model="id"><option v-for="item in demo.catalog" :key="item.id" :value="item.id">{{ item.name }} · {{ item.id }}</option></select></label>
      <label v-if="dataset.filters.includes('ts_code')" class="field"><span>股票代码</span><input v-model="code" placeholder="全部股票" autocomplete="off" /></label>
      <template v-if="dateField"><label class="field"><span>{{ dateField === 'ann_date' ? '公告' : '交易' }}开始日期</span><input v-model="from" type="date" /></label><label class="field"><span>结束日期</span><input v-model="to" type="date" /></label></template>
      <button class="button primary" :disabled="loading"><Search />{{ loading ? '查询中…' : '查询' }}</button>
      <button class="text-button" type="button" @click="reset">重置</button>
    </form>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <header class="data-caption"><h2>{{ dataset.name }}<code>{{ dataset.id }}</code></h2><span>只读 · {{ filtered.length }} 条示例记录</span></header>
    <div v-if="loading" class="loading-state" role="status">正在筛选示例数据…</div>
    <div v-else-if="!rows.length" class="empty-state"><Document /><h3>没有匹配的示例记录</h3><p>试试其他筛选条件，或选择「日线行情」查看示例。</p><button class="button secondary" @click="id === 'daily' ? reset() : id = 'daily'">查看日线示例</button></div>
    <div v-else class="table-scroll data-scroll"><table class="records-table"><thead><tr><th v-for="column in dataset.columns" :key="column">{{ column }}</th></tr></thead><tbody><tr v-for="(row, index) in rows" :key="index"><td v-for="column in dataset.columns" :key="column" :class="{ numeric: typeof row[column] === 'number' }">{{ display(row[column]) }}</td></tr></tbody></table></div>
    <footer class="pagination"><span>本地样例，最多展示前 24 条记录、前 8 个字段</span><div><button class="icon-button" :disabled="page <= 1" aria-label="上一页" @click="page--"><ArrowLeft /></button><span>{{ page }} / {{ pages }}</span><button class="icon-button" :disabled="page >= pages" aria-label="下一页" @click="page++"><ArrowRight /></button></div></footer>
  </section>
</template>
