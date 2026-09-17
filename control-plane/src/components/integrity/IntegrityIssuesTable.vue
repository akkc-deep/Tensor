<script setup>
import { ref, watch } from 'vue'
import { CircleCheckFilled, Clock, WarningFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import { validateIntegrityCriteria } from '../../api/integrityChecks.js'
import { formatIngestedAt } from '../../utils/format.js'
import { formatIntegrityCount, formatIntegrityValue } from '../../utils/integrityReport.js'
import IntegrityPagination from './IntegrityPagination.vue'

const props = defineProps({
  page: { type: Object, default: null }, result: { type: Object, required: true },
  criteria: { type: Object, required: true }, loading: Boolean, error: { type: Object, default: null },
})
const emit = defineEmits(['query', 'update:page', 'update:pageSize', 'retry', 'close'])
const type = ref(''), status = ref(''), dateFrom = ref(''), dateTo = ref(''), validationError = ref('')
const types = ['MISSING','SUSPECTED_MISSING','EXTRA','REQUIRED_FIELD_MISSING','BUSINESS_KEY_INVALID','SOURCE_IDENTITY_MISMATCH','REFERENCE_INCOMPLETE','DATE_SCOPE_UNRESOLVED','RULE_EXECUTION_FAILED']
const statuses = { PASS: '通过', FAIL: '有问题', WARN: '待核实', UNKNOWN: '无法判定', NOT_APPLICABLE: 'N/A（不适用）' }
const statusIcons = { PASS: CircleCheckFilled, FAIL: CircleCloseFilled, WARN: WarningFilled, UNKNOWN: WarningFilled, NOT_APPLICABLE: Clock }
watch(() => props.criteria, (value) => {
  type.value = value.type ?? ''; status.value = value.status ?? ''; dateFrom.value = value.dateFrom ?? ''; dateTo.value = value.dateTo ?? ''
  validationError.value = ''
}, { immediate: true, deep: true })
function build(reset = false) {
  const value = { page: 1, pageSize: props.criteria.pageSize, resultId: props.result.resultId }
  if (!reset && type.value) value.type = type.value
  if (!reset && status.value) value.status = status.value
  if (!reset && dateFrom.value) value.dateFrom = dateFrom.value
  if (!reset && dateTo.value) value.dateTo = dateTo.value
  try { return validateIntegrityCriteria('issues', value) } catch { validationError.value = '日期格式无效或起止顺序错误'; return null }
}
function query(reset = false) {
  if (reset) type.value = status.value = dateFrom.value = dateTo.value = ''
  const value = build(reset)
  if (value) { validationError.value = ''; emit('query', value) }
}
function ruleName(item) {
  return props.result.report.ruleResults.find(({ descriptor }) =>
    descriptor.ruleId === item.ruleId && descriptor.version === item.ruleVersion)?.descriptor.displayName
}
</script>
<template>
  <section class="issues" aria-labelledby="integrity-issues-title">
    <header><div><h2 id="integrity-issues-title" tabindex="-1">问题明细</h2><p>{{ result.report.scope.symbol ?? '范围说明' }} · {{ result.report.scope.datasetKey.apiName }} · <code>{{ result.resultId }}</code></p></div><button class="close" type="button" aria-label="关闭问题明细" @click="emit('close')">关闭</button></header>
    <p v-if="!result.report.issuesComplete" class="warning" role="status">问题明细不完整，以下仅为已保存的问题。</p>
    <div class="filters">
      <label>问题类型<select v-model="type" aria-label="问题类型" :disabled="loading"><option value="">全部类型</option><option v-for="item in types" :key="item" :value="item">{{ item }}</option></select></label>
      <label>问题结论<select v-model="status" aria-label="问题结论" :disabled="loading"><option value="">全部结论</option><option v-for="(label,value) in statuses" :key="value" :value="value">{{ label }}</option></select></label>
      <label>问题开始日期<input v-model="dateFrom" type="date" min="1000-01-01" max="9999-12-31" aria-label="问题开始日期" aria-describedby="issues-date-error" :disabled="loading"></label>
      <label>问题结束日期<input v-model="dateTo" type="date" min="1000-01-01" max="9999-12-31" aria-label="问题结束日期" aria-describedby="issues-date-error" :disabled="loading"></label>
      <div class="actions"><button type="button" aria-label="查询问题" :disabled="loading" @click="query()">查询问题</button><button type="button" aria-label="重置问题筛选" :disabled="loading" @click="query(true)">重置问题筛选</button></div>
    </div>
    <p id="issues-date-error" class="error" role="alert">{{ validationError }}</p>
    <p v-if="criteria.dateFrom || criteria.dateTo" class="warning" role="status">日期筛选仅包含可定位日期的问题，已排除日期未知项。</p>
    <div v-if="error" class="error query-error" role="alert"><span>问题查询失败<span v-if="error.requestId"> · 请求 ID：<code>{{ error.requestId }}</code></span></span><button type="button" aria-label="重新查询问题" :disabled="loading" @click="emit('retry')">重新查询问题</button></div>
    <p v-if="loading && !page" role="status">正在查询问题…</p>
    <p v-if="page && page.items.length === 0 && !loading" role="status">当前条件下没有问题记录</p>
    <div v-if="page?.items.length" class="table-wrap" tabindex="0" aria-label="问题明细表格，可横向滚动">
      <table><thead><tr><th>问题</th><th>定位</th><th>完整业务键</th><th>原因与规则</th><th>保存的依据</th></tr></thead><tbody>
        <tr v-for="item in page.items" :key="item.issueId.toString()">
          <td><b>{{ item.issue.type }}</b><span class="status"><component :is="statusIcons[item.issue.status]" aria-hidden="true" />{{ statuses[item.issue.status] }}</span><code>{{ formatIntegrityCount(item.issueId) }}</code><em v-if="item.issue.incomplete">统计未完成</em></td>
          <td><span>{{ item.issue.symbol ?? '范围说明' }} · {{ item.issue.apiName }}</span><span>{{ item.issue.dateField ?? '未提供日期字段' }}</span><b>{{ item.issue.date ?? '日期未知 / 整个检查范围' }}</b><span v-if="item.issue.field">字段：{{ item.issue.field }}</span><dl v-if="Object.keys(item.issue.relatedDates).length"><div v-for="(value,key) in item.issue.relatedDates" :key="key"><dt>{{ key }}</dt><dd>{{ value }}</dd></div></dl></td>
          <td><dl v-if="Object.keys(item.issue.businessKey).length" class="business-key"><div v-for="(value,key) in item.issue.businessKey" :key="key"><dt>{{ key }}</dt><dd>{{ formatIntegrityValue(value) }}</dd></div></dl><span v-else>未提供业务键</span></td>
          <td><b>{{ item.issue.reasonCode }}</b><p>{{ item.issue.message }}</p><p>{{ ruleName(item) ?? item.ruleId }}</p><code>{{ item.ruleId }}@{{ item.ruleVersion }}</code><em v-if="!ruleName(item)">未保存规则名称</em></td>
          <td><article v-for="evidence in item.issue.evidence" :key="`${evidence.source}-${evidence.readAt}`"><b>{{ evidence.source }}@{{ evidence.ruleVersion }}</b><span>{{ evidence.range.startDate }} 至 {{ evidence.range.endDate }}</span><time :datetime="evidence.readAt" :title="evidence.readAt">{{ formatIngestedAt(evidence.readAt) }}（Asia/Shanghai）</time><p>{{ evidence.summary }}</p></article><span v-if="!item.issue.evidence.length">未提供依据</span></td>
        </tr>
      </tbody></table>
    </div>
    <IntegrityPagination v-if="page" :page="criteria.page" :page-size="criteria.pageSize" :total="page.total" :disabled="loading" @update:page="emit('update:page', $event)" @update:page-size="emit('update:pageSize', $event)" />
  </section>
</template>

<style scoped>
.issues{min-width:0;padding:24px;border:1px solid var(--tensor-accent);background:var(--tensor-surface);scroll-margin-top:18px}header,.filters,.actions,.query-error{display:flex;align-items:center}header{justify-content:space-between;gap:16px}h2,p{margin:0}header p{margin-top:5px;color:var(--tensor-muted);font-size:12px;overflow-wrap:anywhere}header button,.actions button,.query-error button,select,input{padding:8px 10px;border:1px solid var(--tensor-line);border-radius:4px;background:var(--tensor-raised);color:var(--tensor-text)}.filters{flex-wrap:wrap;gap:12px;margin-top:18px}.filters label{display:grid;gap:5px;color:var(--tensor-muted);font-size:12px}.actions{align-self:flex-end;gap:8px}.warning{margin-top:12px;color:var(--tensor-warning);overflow-wrap:anywhere}.error{min-height:1em;margin-top:7px;color:var(--tensor-error);overflow-wrap:anywhere}.query-error{justify-content:space-between;gap:12px;margin:12px 0}.table-wrap{max-width:100%;margin-top:16px;overflow-x:auto;outline-offset:3px}table{width:100%;min-width:1180px;border-collapse:collapse;text-align:left;font-size:12px}th,td{padding:12px 10px;border-bottom:1px solid var(--tensor-line);vertical-align:top}th{color:var(--tensor-muted);font-weight:500}td{min-width:170px;overflow-wrap:anywhere}td>*{display:block;margin-bottom:6px}td code{max-width:220px;white-space:normal}td em{color:var(--tensor-warning);font-style:normal}dl{margin:8px 0 0}dl div{display:grid;grid-template-columns:minmax(70px,.7fr) minmax(0,1fr);gap:8px}dt{color:var(--tensor-muted)}dd{margin:0;overflow-wrap:anywhere}article{display:grid;gap:4px}:disabled{cursor:not-allowed;opacity:.55}@media(max-width:680px){.issues{padding:18px}header{align-items:flex-start}.filters{align-items:stretch;flex-direction:column}.actions{align-self:stretch}.actions button{flex:1}.query-error{align-items:flex-start;flex-direction:column}}
.status{display:flex;align-items:center;gap:5px;font-weight:600}.status svg{width:14px;flex:0 0 auto}.close{flex-shrink:0;white-space:nowrap}
</style>
