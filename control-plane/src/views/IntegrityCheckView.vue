<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { validateIntegrityCheckId } from '../api/integrityChecks.js'
import PageHeading from '../components/common/PageHeading.vue'
import IntegritySummary from '../components/integrity/IntegritySummary.vue'
import IntegrityResultsTable from '../components/integrity/IntegrityResultsTable.vue'
import IntegrityIssuesTable from '../components/integrity/IntegrityIssuesTable.vue'
import { useIntegrityCheck } from '../composables/useIntegrityCheck.js'
import { useIntegrityIssues } from '../composables/useIntegrityIssues.js'

const props = defineProps({ checkId: { type: String, required: true } })
const router = useRouter(), flow = useIntegrityCheck(), issues = useIntegrityIssues()
const invalid = ref(false), selectedResult = ref(null)
let issueTrigger = null

watch(() => props.checkId, async (value) => {
  selectedResult.value = null
  issues.reset()
  try {
    const id = validateIntegrityCheckId(value)
    invalid.value = false
    await flow.load(id)
  } catch {
    invalid.value = true
    flow.setActive(false)
  }
}, { immediate: true })

function changeResults(criteria) { flow.changeResults(criteria) }
function changeResultPage(page) { flow.changeResults({ ...flow.resultsCriteria.value, page }) }
function changeResultPageSize(pageSize) { flow.changeResults({ ...flow.resultsCriteria.value, page: 1, pageSize }) }
async function selectResult(result, event) {
  selectedResult.value = result
  issueTrigger = event?.currentTarget ?? null
  await issues.load(flow.detail.value.checkId, { page: 1, pageSize: 20, resultId: result.resultId })
  await nextTick()
  const heading = document.getElementById('integrity-issues-title')
  heading?.scrollIntoView?.({ block: 'start', behavior: 'smooth' })
  heading?.focus()
}
function queryIssues(criteria) { issues.load(flow.detail.value.checkId, criteria) }
function changeIssuePage(page) { issues.load(flow.detail.value.checkId, { ...issues.criteria.value, page }) }
function changeIssuePageSize(pageSize) { issues.load(flow.detail.value.checkId, { ...issues.criteria.value, page: 1, pageSize }) }
async function closeIssues() {
  const target = issueTrigger
  selectedResult.value = null
  issueTrigger = null
  issues.reset()
  await nextTick()
  if (target?.isConnected) target.focus()
  else document.getElementById('integrity-results-title')?.focus()
}
function repeatCheck() {
  router.push({ name: 'integrity', query: { fromCheckId: flow.detail.value.checkId } })
}
onBeforeUnmount(() => { flow.dispose(); issues.dispose() })
defineExpose({ flow, issues })
</script>

<template>
  <div class="report-page">
    <PageHeading id="integrity-report-heading" title="检查报告" description="查看保存的执行范围、数据结论与具体问题。" />
    <nav class="toolbar" aria-label="报告操作">
      <RouterLink :to="{ name: 'integrity' }">返回数据完整性</RouterLink>
      <button type="button" aria-label="重新连接" :disabled="invalid || flow.loading.value" @click="flow.reconnect()">重新连接</button>
      <button v-if="flow.detail.value" type="button" aria-label="再次检查" @click="repeatCheck">再次检查</button>
    </nav>

    <section v-if="invalid" class="page-state" role="alert"><h2>检查编号无效</h2><p>请从检查历史重新打开报告。</p></section>
    <section v-else-if="flow.loading.value && !flow.detail.value" class="page-state" role="status">正在加载检查报告…</section>
    <section v-else-if="flow.detailError.value && !flow.detail.value" class="page-state" role="alert">
      <h2>{{ flow.detailError.value.code === 'INTEGRITY_CHECK_NOT_FOUND' ? '检查报告不存在' : '检查报告加载失败' }}</h2>
      <p v-if="flow.detailError.value.requestId">请求 ID：<code>{{ flow.detailError.value.requestId }}</code></p>
      <button type="button" @click="flow.reconnect()">重新读取报告</button>
    </section>

    <template v-if="!invalid && flow.detail.value">
      <section v-if="!flow.connected.value && (flow.detailError.value || flow.resultsError.value)" class="connection" role="alert">
        <b>连接已中断，以下为上次读取结果</b>
        <span v-if="flow.detailError.value?.requestId">详情请求 ID：<code>{{ flow.detailError.value.requestId }}</code></span>
        <span v-if="flow.resultsError.value?.requestId">结果请求 ID：<code>{{ flow.resultsError.value.requestId }}</code></span>
      </section>
      <IntegritySummary :detail="flow.detail.value" />
      <IntegrityResultsTable
        :page="flow.results.value" :criteria="flow.resultsCriteria.value"
        :symbols="flow.detail.value.scope.symbols" :api-names="flow.detail.value.scope.apiNames"
        :loading="flow.loading.value" :error="flow.resultsError.value"
        @query="changeResults" @update:page="changeResultPage" @update:page-size="changeResultPageSize"
        @select-result="selectResult"
      />
      <IntegrityIssuesTable
        v-if="selectedResult" :result="selectedResult" :page="issues.page.value"
        :criteria="issues.criteria.value" :loading="issues.loading.value" :error="issues.error.value"
        @query="queryIssues" @update:page="changeIssuePage" @update:page-size="changeIssuePageSize"
        @retry="issues.refresh()" @close="closeIssues"
      />
    </template>
  </div>
</template>

<style scoped>
.report-page{display:grid;min-width:0;gap:18px;padding:28px}.toolbar{display:flex;align-items:center;flex-wrap:wrap;gap:10px}.toolbar a{margin-right:auto;color:var(--tensor-accent)}button{padding:8px 12px;border:1px solid var(--tensor-line);border-radius:4px;background:var(--tensor-raised);color:var(--tensor-text)}.page-state,.connection{min-width:0;padding:20px;border:1px solid var(--tensor-line);background:var(--tensor-surface)}.page-state h2,.page-state p{margin:0 0 8px}.connection{display:grid;gap:6px;color:var(--tensor-error);overflow-wrap:anywhere}:disabled{cursor:not-allowed;opacity:.55}@media(max-width:680px){.report-page{gap:14px;padding:18px}.toolbar a{width:100%;margin:0}}
</style>
