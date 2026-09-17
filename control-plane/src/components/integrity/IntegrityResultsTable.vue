<script setup>
import { ref, watch } from 'vue'
import { CircleCheckFilled, Clock, WarningFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import { formatIngestedAt } from '../../utils/format.js'
import { formatIntegrityCount, formatIntegrityRate } from '../../utils/integrityReport.js'
import IntegrityPagination from './IntegrityPagination.vue'

const props = defineProps({
  page: { type: Object, default: null }, criteria: { type: Object, required: true },
  symbols: { type: Array, default: () => [] }, apiNames: { type: Array, default: () => [] },
  loading: Boolean, error: { type: Object, default: null },
})
const emit = defineEmits(['query', 'update:page', 'update:pageSize', 'select-result'])
const symbol = ref(''), apiName = ref(''), overallStatus = ref('')
const statuses = { PASS: '通过', FAIL: '有问题', WARN: '待核实', UNKNOWN: '无法判定', NOT_APPLICABLE: 'N/A（不适用）' }
const units = { PENDING: '待执行', RUNNING: '执行中', COMPLETED: '已计算', ERROR: '执行错误', NOT_RUN: '未运行' }
const statusIcons = { PASS: CircleCheckFilled, FAIL: CircleCloseFilled, WARN: WarningFilled, UNKNOWN: WarningFilled, NOT_APPLICABLE: Clock }
const unitIcons = { PENDING: Clock, RUNNING: Clock, COMPLETED: CircleCheckFilled, ERROR: CircleCloseFilled, NOT_RUN: WarningFilled }
watch(() => props.criteria, (value) => {
  symbol.value = value.symbol ?? ''; apiName.value = value.apiName ?? ''; overallStatus.value = value.overallStatus ?? ''
}, { immediate: true, deep: true })
function query(reset = false) {
  const value = { page: 1, pageSize: props.criteria.pageSize }
  if (!reset && symbol.value) value.symbol = symbol.value
  if (!reset && apiName.value) value.apiName = apiName.value
  if (!reset && overallStatus.value) value.overallStatus = overallStatus.value
  if (reset) symbol.value = apiName.value = overallStatus.value = ''
  emit('query', value)
}
</script>
<template>
  <section class="results" aria-labelledby="integrity-results-title">
    <header><h2 id="integrity-results-title" tabindex="-1">检查结果</h2><span v-if="loading" role="status">正在查询结果…</span></header>
    <div class="filters">
      <label>结果股票<select v-model="symbol" aria-label="结果股票" :disabled="loading"><option value="">全部股票</option><option v-for="item in symbols" :key="item" :value="item">{{ item }}</option></select></label>
      <label>结果接口<select v-model="apiName" aria-label="结果接口" :disabled="loading"><option value="">全部接口</option><option v-for="item in apiNames" :key="item" :value="item">{{ item }}</option></select></label>
      <label>数据结论<select v-model="overallStatus" aria-label="数据结论" :disabled="loading"><option value="">全部结论</option><option v-for="(label,value) in statuses" :key="value" :value="value">{{ label }}</option></select></label>
      <div class="actions"><button type="button" aria-label="查询结果" :disabled="loading" @click="query()">查询结果</button><button type="button" aria-label="重置筛选" :disabled="loading" @click="query(true)">重置筛选</button></div>
    </div>
    <p v-if="error" class="error" role="alert">结果查询失败<span v-if="error.requestId"> · 请求 ID：<code>{{ error.requestId }}</code></span></p>
    <p v-if="page && page.items.length === 0 && !loading" role="status">没有符合条件的检查单元</p>
    <div v-if="page?.items.length" class="table-wrap" tabindex="0" aria-label="检查结果表格，可横向滚动">
      <table><thead><tr><th>股票 / 范围</th><th>接口与日期轴</th><th>执行</th><th>结论</th><th>数量与覆盖率</th><th>问题计数</th><th>操作</th></tr></thead>
        <tbody><tr v-for="item in page.items" :key="item.resultId">
          <td><b>{{ item.report.scope.symbol ?? '范围说明，不按股票逐只检查' }}</b><code>{{ item.resultId }}</code></td>
          <td><b>{{ item.report.scope.datasetKey.apiName }}</b><span>{{ item.report.descriptor ? `${item.report.descriptor.dateLabel}${item.report.descriptor.dateField ? `（${item.report.descriptor.dateField}）` : ''}` : '覆盖规则未实现' }}</span><span>{{ item.report.scope.startDate }} 至 {{ item.report.scope.endDate }}</span></td>
          <td><span class="status"><component :is="unitIcons[item.report.unitStatus]" aria-hidden="true" />{{ units[item.report.unitStatus] }}</span><em v-if="item.report.incomplete">统计未完成</em><em v-if="!item.report.issuesComplete">问题明细不完整</em><span>{{ item.report.reasonCode }} · {{ item.report.message }}</span></td>
          <td><span v-for="([label,value]) in [['总体',item.report.overallStatus],['覆盖',item.report.coverageStatus],['业务键',item.report.keyStatus],['字段',item.report.fieldStatus]]" :key="label" class="status"><component :is="statusIcons[value]" aria-hidden="true" />{{ label }}：{{ statuses[value] }}</span></td>
          <td><span>实际 {{ formatIntegrityCount(item.report.statistics.actualCount) }}</span><span>预期 {{ formatIntegrityCount(item.report.statistics.expectedCount) }}</span><span>命中 {{ formatIntegrityCount(item.report.statistics.matchedCount) }}</span><b>覆盖率 {{ formatIntegrityRate(item.report.statistics.coverageRate) }}</b></td>
          <td><span>确认缺失 {{ formatIntegrityCount(item.report.statistics.missingCount) }}</span><span>疑似缺口 {{ formatIntegrityCount(item.report.statistics.suspectedMissingCount) }}</span><span>额外 {{ formatIntegrityCount(item.report.statistics.extraCount) }}</span><span>必填字段 {{ formatIntegrityCount(item.report.statistics.requiredFieldIssueCount) }}</span></td>
          <td><button type="button" aria-label="查看问题" :data-result-id="item.resultId" @click="emit('select-result', item, $event)">查看问题</button></td>
        </tr></tbody></table>
      <div class="bases"><details v-for="item in page.items" :key="`${item.resultId}-basis`"><summary>保存的检查依据 · {{ item.report.scope.symbol ?? item.report.scope.datasetKey.apiName }}</summary>
          <div class="basis">
            <p>定义哈希：<code>{{ item.report.definitionHash }}</code></p><p>发布范围：{{ item.report.publishedRange ? `${item.report.publishedRange.startDate} 至 ${item.report.publishedRange.endDate}` : '尚无' }}</p>
            <p>快照开始：{{ item.report.scope.snapshotStartedAt ? `${formatIngestedAt(item.report.scope.snapshotStartedAt)}（Asia/Shanghai）` : '尚无' }}</p><p>完成时间：{{ item.report.finishedAt ? `${formatIngestedAt(item.report.finishedAt)}（Asia/Shanghai）` : '尚无' }}</p>
            <template v-if="item.report.descriptor"><p>范围类型：{{ item.report.descriptor.scopeKind }} · {{ item.report.descriptor.dateLabel }}</p><p v-for="limit in item.report.descriptor.limitations" :key="limit">限制：{{ limit }}</p><p v-for="dependency in item.report.descriptor.dependencies" :key="`${dependency.datasetKey.pluginId}/${dependency.datasetKey.apiName}`">依赖：{{ dependency.datasetKey.pluginId }}/{{ dependency.datasetKey.apiName }} · {{ dependency.purpose }}</p></template>
            <p v-else>覆盖规则未实现；仍显示已保存的规则执行结果。</p>
            <article v-for="rule in item.report.ruleResults" :key="`${rule.descriptor.ruleId}@${rule.descriptor.version}`"><b>{{ rule.descriptor.displayName }}</b> · {{ rule.descriptor.ruleId }}@{{ rule.descriptor.version }} · {{ rule.descriptor.dimension }}<p>{{ rule.reasonCode }} · {{ rule.message }}</p><p>{{ rule.descriptor.description }}</p></article>
            <p v-for="evidence in item.report.evidence" :key="`${evidence.source}-${evidence.readAt}`">依据：{{ evidence.source }}@{{ evidence.ruleVersion }} · {{ evidence.range.startDate }} 至 {{ evidence.range.endDate }} · {{ evidence.summary }}</p>
          </div></details></div>
    </div>
    <IntegrityPagination v-if="page" :page="criteria.page" :page-size="criteria.pageSize" :total="page.total" :disabled="loading" @update:page="emit('update:page', $event)" @update:page-size="emit('update:pageSize', $event)" />
  </section>
</template>

<style scoped>
.results{min-width:0;padding:24px;border:1px solid var(--tensor-line);background:var(--tensor-surface)}header,.filters,.actions{display:flex;align-items:center}header{justify-content:space-between;gap:16px}h2,p{margin:0}.filters{flex-wrap:wrap;gap:12px;margin:20px 0}.filters label{display:grid;gap:5px;color:var(--tensor-muted);font-size:12px}.filters select,.actions button,td button{padding:8px 10px;border:1px solid var(--tensor-line);border-radius:4px;background:var(--tensor-raised);color:var(--tensor-text)}.actions{align-self:flex-end;gap:8px}.error{margin-bottom:12px;color:var(--tensor-error);overflow-wrap:anywhere}.table-wrap{max-width:100%;overflow-x:auto;outline-offset:3px}table{width:100%;min-width:1180px;border-collapse:collapse;text-align:left;font-size:12px}th,td{padding:12px 10px;border-bottom:1px solid var(--tensor-line);vertical-align:top}th{color:var(--tensor-muted);font-weight:500}td{min-width:120px;overflow-wrap:anywhere}td>*{display:block;margin-bottom:5px}td code{max-width:180px;color:var(--tensor-muted);white-space:normal}.status{font-weight:600}em{color:var(--tensor-warning);font-style:normal}.basis-row td{padding-top:0}.basis-row summary{cursor:pointer;color:var(--tensor-accent)}.basis{display:grid;gap:7px;margin-top:10px;padding:12px;background:var(--tensor-raised)}.basis article{padding-top:8px;border-top:1px solid var(--tensor-line)}:disabled{cursor:not-allowed;opacity:.55}@media(max-width:680px){.results{padding:18px}.filters{align-items:stretch;flex-direction:column}.actions{align-self:stretch}.actions button{flex:1}}
.status{display:flex;align-items:center;gap:5px}.status svg{width:14px;flex:0 0 auto}
.bases{display:grid;gap:8px;margin-top:12px}.bases details{padding:10px 12px;border:1px solid var(--tensor-line)}.bases summary{cursor:pointer;color:var(--tensor-accent)}
</style>
