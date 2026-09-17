<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listApis, listDataSources } from '../api/dataSources.js'
import { getIntegrityCheck, validateIntegrityCheckId } from '../api/integrityChecks.js'
import IntegrityCheckForm from '../components/integrity/IntegrityCheckForm.vue'
import IntegrityCheckHistory from '../components/integrity/IntegrityCheckHistory.vue'
import IntegrityScopePreview from '../components/integrity/IntegrityScopePreview.vue'
import PageHeading from '../components/common/PageHeading.vue'
import { useIntegrityCheck } from '../composables/useIntegrityCheck.js'
import { validateIntegritySelection } from '../utils/integrityForm.js'

const router = useRouter()
const route = useRoute()
const flow = useIntegrityCheck()
const sources = ref([])
const sourcesLoading = ref(true)
const sourcesError = ref(null)
const categories = ref({})
const categoryError = ref(null)
const draftSymbols = ref([])
const confirmed = ref(false)
const copyLoading = ref(false)
const copyError = ref(null)
const copiedDetail = ref(null)
const copyStarted = ref(false)
const copySuperseded = ref(false)
const form = ref(null)
const selection = ref({
  pluginId: '',
  symbols: [],
  startDate: '',
  endDate: '',
  apiNames: [],
})
let sourceGeneration = 0
let categoryGeneration = 0
let choiceGeneration = 0
let copyGeneration = 0
let copyController = null
let navigated = false

if (flow.pendingSubmission.value) {
  const pending = flow.pendingSubmission.value
  selection.value = {
    pluginId: pending.pluginId,
    symbols: [...pending.symbols],
    startDate: pending.startDate,
    endDate: pending.endDate,
    apiNames: [...(pending.apiNames ?? [])],
  }
}

function shanghaiToday() {
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts().filter(({ type }) => type !== 'literal').map(({ type, value }) => [type, value]))
  return `${parts.year}-${parts.month}-${parts.day}`
}

const previewSelection = computed(() => ({
  ...selection.value,
  symbols: [...new Set([...selection.value.symbols, ...draftSymbols.value])],
}))
const fromCheckId = computed(() => typeof route.query.fromCheckId === 'string' ? route.query.fromCheckId : '')
const copiedSource = computed(() => sources.value.find(({ pluginId }) => pluginId === selection.value.pluginId))
const sourceUnavailable = computed(() => !!copiedDetail.value && !sourcesLoading.value && !copiedSource.value?.enabled)
const copyBlocked = computed(() => !!fromCheckId.value && !copySuperseded.value && !copiedDetail.value &&
  (!flow.pendingSubmission.value || copyStarted.value))
const displaySources = computed(() => sourceUnavailable.value && !copiedSource.value ? [...sources.value, {
  pluginId: selection.value.pluginId, displayName: `${selection.value.pluginId}（原数据源已不可用）`, enabled: false,
}] : sources.value)
const validation = computed(() => {
  const value = validateIntegritySelection(previewSelection.value, flow.capability.value, shanghaiToday())
  if (!sourceUnavailable.value) return value
  return { ...value, valid: false, errors: { ...value.errors, pluginId: '原数据源目前不可用，请选择新的数据源' } }
})
const locked = computed(() => ['submitting', 'recovering', 'uncertain'].includes(flow.submissionState.value))
const formLocked = computed(() => locked.value || flow.submissionState.value === 'prepared')
const busy = computed(() => ['submitting', 'recovering'].includes(flow.submissionState.value))
const selectedSource = computed(() => sources.value.find(({ pluginId }) => pluginId === selection.value.pluginId))

watch(() => flow.capability.value?.capabilityHash, (value, oldValue) => {
  if (value !== oldValue) confirmed.value = false
})

async function loadCategories(pluginId) {
  const epoch = ++categoryGeneration
  categoryError.value = null
  try {
    const apis = await listApis(pluginId)
    if (epoch !== categoryGeneration || selection.value.pluginId !== pluginId) return
    categories.value = Object.fromEntries(apis.map((api) => [api.apiName, api.category || '未分类']))
  } catch (error) {
    if (epoch === categoryGeneration && selection.value.pluginId === pluginId) categoryError.value = error
  }
}

async function chooseSource(pluginId, defaultApis = true) {
  const epoch = ++choiceGeneration
  confirmed.value = false
  categories.value = {}
  categoryError.value = null
  if (!pluginId) return
  loadCategories(pluginId)
  const loaded = await flow.loadCapabilities(pluginId)
  if (!loaded || epoch !== choiceGeneration || selection.value.pluginId !== pluginId) return
  if (defaultApis && selection.value.apiNames.length === 0) {
    selection.value = {
      ...selection.value,
      apiNames: flow.capability.value.apis.map((api) => api.apiName),
    }
  }
}

function updateSelection(value) {
  cancelCopyRequest()
  const changedSource = value.pluginId !== selection.value.pluginId
  if (changedSource) {
    copiedDetail.value = null
    copyError.value = null
    if (fromCheckId.value) copySuperseded.value = true
  }
  selection.value = changedSource ? { ...value, apiNames: [] } : value
  if (changedSource) chooseSource(value.pluginId)
}

function updateDraft(value) {
  const changed = value.length !== draftSymbols.value.length || value.some((item, index) => item !== draftSymbols.value[index])
  draftSymbols.value = value
  if (changed) cancelCopyRequest()
}

function cancelCopyRequest() {
  copyGeneration += 1
  copyController?.abort()
  copyController = null
  copyLoading.value = false
}

async function loadCopiedScope() {
  if (!fromCheckId.value || flow.pendingSubmission.value && flow.submissionState.value !== 'rejected') return false
  const requestedId = fromCheckId.value
  cancelCopyRequest()
  copyStarted.value = true
  copySuperseded.value = false
  copyError.value = null
  copiedDetail.value = null
  confirmed.value = false
  draftSymbols.value = []
  selection.value = { pluginId: '', symbols: [], startDate: '', endDate: '', apiNames: [] }
  let id
  try { id = validateIntegrityCheckId(requestedId) } catch {
    copyError.value = new TypeError('原检查编号无效。')
    return false
  }
  const epoch = copyGeneration, controller = new AbortController()
  copyController = controller
  copyLoading.value = true
  const current = () => epoch === copyGeneration && copyController === controller && !controller.signal.aborted && fromCheckId.value === requestedId
  try {
    const detail = await getIntegrityCheck(id, { signal: controller.signal })
    if (!current()) return false
    copiedDetail.value = detail
    selection.value = {
      pluginId: detail.scope.pluginId, symbols: [...detail.scope.symbols],
      startDate: detail.scope.startDate, endDate: detail.scope.endDate, apiNames: [...detail.scope.apiNames],
    }
    await chooseSource(detail.scope.pluginId, false)
    return current()
  } catch (error) {
    if (!current()) return false
    copyError.value = error
    return false
  } finally {
    if (current()) copyLoading.value = false
  }
}

async function loadSources() {
  const epoch = ++sourceGeneration
  sourcesLoading.value = true
  sourcesError.value = null
  try {
    const value = await listDataSources()
    if (epoch !== sourceGeneration) return
    sources.value = value
    if (!flow.pendingSubmission.value && !fromCheckId.value && !selection.value.pluginId && value.length === 1) {
      selection.value = { ...selection.value, pluginId: value[0].pluginId }
      await chooseSource(value[0].pluginId)
    }
  } catch (error) {
    if (epoch === sourceGeneration) sourcesError.value = error
  } finally {
    if (epoch === sourceGeneration) sourcesLoading.value = false
  }
}

function confirmScope(value) {
  confirmed.value = false
  if (!value) return
  try {
    flow.confirmCapability(flow.capability.value?.capabilityHash)
    confirmed.value = true
  } catch {
    confirmed.value = false
  }
}

function navigateAccepted() {
  const checkId = flow.receipt.value?.checkId ?? flow.recoveredTask.value?.checkId
  if (!checkId || navigated) return
  navigated = true
  router.push({ name: 'integrity-check', params: { checkId } })
}

async function submit() {
  if (flow.submissionState.value === 'prepared') {
    await flow.submit()
    navigateAccepted()
    return
  }
  if (sourceUnavailable.value) return
  const committed = form.value?.commitPendingSymbols() ?? previewSelection.value
  selection.value = committed
  const checked = validateIntegritySelection(committed, flow.capability.value, shanghaiToday())
  if (!checked.valid || !confirmed.value) return
  try {
    flow.prepareSubmission({
      ...committed,
      capabilityHash: flow.capability.value.capabilityHash,
    })
  } catch {
    return
  }
  await flow.submit()
  navigateAccepted()
}

async function recover() {
  await flow.recoverSubmission()
  navigateAccepted()
}

async function resend() {
  await flow.resendSubmission()
  navigateAccepted()
}

onMounted(async () => {
  const sourceJob = loadSources()
  if (flow.pendingSubmission.value) await recover()
  await sourceJob
})
watch(fromCheckId, () => {
  cancelCopyRequest()
  copyError.value = null
  copiedDetail.value = null
  copyStarted.value = false
  copySuperseded.value = false
  confirmed.value = false
  if (fromCheckId.value && !flow.pendingSubmission.value) {
    draftSymbols.value = []
    selection.value = { pluginId: '', symbols: [], startDate: '', endDate: '', apiNames: [] }
    loadCopiedScope()
  }
}, { immediate: true })
onBeforeUnmount(() => {
  sourceGeneration += 1
  categoryGeneration += 1
  cancelCopyRequest()
  flow.dispose()
})
defineExpose({ flow })
</script>

<template>
  <div class="integrity-page">
    <PageHeading
      id="integrity-heading"
      title="数据完整性"
      description="检查本地数据，查看缺失与无法判定的原因"
    />

    <section v-if="fromCheckId && copyLoading" class="copy-state" role="status">正在读取原报告条件…</section>
    <section v-else-if="fromCheckId && copyError" class="copy-state copy-error" role="alert">
      <b>原报告条件读取失败</b>
      <span>{{ copyError.message }}</span>
      <span v-if="copyError.requestId">请求 ID：<code>{{ copyError.requestId }}</code></span>
      <button type="button" aria-label="重新读取原条件" @click="loadCopiedScope">重新读取原条件</button>
    </section>
    <section v-else-if="copiedDetail" class="copy-state" role="status">
      <b>已复制原报告的固定执行范围</b>
      <p>{{ copiedDetail.scope.pluginId }} · {{ copiedDetail.scope.symbols.join('、') }} · {{ copiedDetail.scope.startDate }} 至 {{ copiedDetail.scope.endDate }} · {{ copiedDetail.scope.apiNames.join('、') }}</p>
      <p v-if="sourceUnavailable">原数据源目前停用或不存在，当前条件不可提交；请选择新的数据源。</p>
      <p>当前检查能力已重新读取，请确认后创建新的检查报告。</p>
    </section>
    <section v-else-if="fromCheckId && flow.pendingSubmission.value && !copyStarted" class="copy-state" role="status">
      <b>请先确认上一次检查的提交结果，再从报告发起新检查</b>
      <p>当前待确认请求保持优先，不会读取或覆盖新的报告条件。</p>
      <button v-if="flow.submissionState.value === 'rejected'" type="button" aria-label="读取这份报告的条件" :disabled="copyLoading" @click="loadCopiedScope">读取这份报告的条件</button>
    </section>

    <section v-if="flow.pendingSubmission.value" class="pending-scope" aria-labelledby="pending-scope-title">
      <h2 id="pending-scope-title">待确认的原检查请求</h2>
      <dl>
        <div><dt>提交编号</dt><dd><code>{{ flow.pendingSubmission.value.submissionId }}</code></dd></div>
        <div><dt>数据源</dt><dd>{{ flow.pendingSubmission.value.pluginId }}</dd></div>
        <div><dt>股票</dt><dd>{{ flow.pendingSubmission.value.symbols.join('、') }}</dd></div>
        <div><dt>日期</dt><dd>{{ flow.pendingSubmission.value.startDate }} 至 {{ flow.pendingSubmission.value.endDate }}</dd></div>
        <div><dt>接口</dt><dd>{{ flow.pendingSubmission.value.apiNames?.join('、') }}</dd></div>
      </dl>
    </section>

    <section v-if="sourcesLoading" class="page-state" role="status">正在加载数据源…</section>
    <section v-else-if="sourcesError" class="page-state" role="alert">
      <h2>数据源加载失败</h2>
      <p>无法获取可检查的数据源。</p>
      <p v-if="sourcesError.requestId">请求 ID：<code>{{ sourcesError.requestId }}</code></p>
      <button type="button" @click="loadSources">重新加载数据源</button>
    </section>
    <section v-else-if="sources.length === 0" class="page-state" role="status">
      <h2>暂无数据源</h2>
      <p>当前没有可用于本地检查的数据源。</p>
    </section>

    <template v-else>
      <p v-if="selectedSource && !selectedSource.downloadAvailable && flow.capability.value?.localCheckAvailable" class="availability" role="status">
        下载不可用，本地检查可用<span v-if="selectedSource.unavailableReason">：{{ selectedSource.unavailableReason }}</span>
      </p>
      <section v-if="flow.capabilityLoading.value" class="capability-state" role="status">正在获取检查能力…</section>
      <section v-if="flow.capabilityError.value" class="capability-state" role="alert">
        <b>检查能力获取失败</b>
        <span v-if="flow.capabilityError.value.requestId">请求 ID：{{ flow.capabilityError.value.requestId }}</span>
        <button type="button" @click="chooseSource(selection.pluginId)">重新获取</button>
      </section>
      <p v-else-if="flow.capability.value && !flow.capability.value.localCheckAvailable" class="capability-state" role="status">
        {{ flow.capability.value.unavailableReason }}
      </p>

      <IntegrityCheckForm
        ref="form"
        :sources="displaySources"
        :capability="flow.capability.value"
        :categories="categories"
        :category-error="categoryError"
        :model-value="selection"
        :errors="validation.errors"
        :disabled="formLocked"
        @update:model-value="updateSelection"
        @draft-change="updateDraft"
        @retry-categories="loadCategories(selection.pluginId)"
      />
      <IntegrityScopePreview
        :selection="previewSelection"
        :apis="flow.capability.value?.apis ?? []"
        :validation="validation"
        :confirmed="confirmed"
        :disabled="locked || copyBlocked || sourceUnavailable || flow.capabilityLoading.value || !!flow.capabilityError.value || !flow.capability.value?.localCheckAvailable"
        :busy="busy"
        @confirm="confirmScope"
        @submit="submit"
      />

    </template>

    <section v-if="flow.submissionState.value === 'definition-changed'" class="operation-message" role="alert">
      检查口径已更新，请重新确认。
    </section>
    <section v-if="flow.submissionError.value" class="operation-message" role="alert">
      <b>{{ flow.submissionError.value.message }}</b>
      <span v-if="flow.submissionError.value.requestId">请求 ID：{{ flow.submissionError.value.requestId }}</span>
      <ul v-if="flow.submissionError.value.fieldErrors?.length">
        <li v-for="item in flow.submissionError.value.fieldErrors" :key="item.field">{{ item.field }}：{{ item.message }}</li>
      </ul>
    </section>
    <section v-if="flow.recoveryError.value" class="operation-message" role="alert">
      <b>查询提交结果失败</b>
      <span v-if="flow.recoveryError.value.requestId">请求 ID：{{ flow.recoveryError.value.requestId }}</span>
    </section>
    <section v-if="flow.storageError.value && flow.storageError.value !== flow.submissionError.value" class="operation-message" role="alert">
      {{ flow.storageError.value.message }}
    </section>
    <div v-if="flow.submissionState.value === 'uncertain'" class="recovery-actions">
      <button type="button" aria-label="查询提交结果" :disabled="busy" @click="recover">查询提交结果</button>
      <button type="button" aria-label="使用原请求重发" :disabled="busy || !flow.canResend.value" @click="resend">使用原请求重发</button>
    </div>

    <IntegrityCheckHistory :sources="sources" />
  </div>
</template>

<style scoped>
.integrity-page {
  display: grid;
  min-width: 0;
  gap: 18px;
}
.page-state,
.capability-state,
.availability,
.operation-message {
  min-width: 0;
  padding: 16px 18px;
  border: 1px solid var(--tensor-line);
  background: var(--tensor-surface);
  overflow-wrap: anywhere;
}
.pending-scope {
  min-width: 0;
  padding: 18px;
  border: 1px solid var(--tensor-line);
  background: var(--tensor-surface);
}
.copy-state {
  display: grid;
  min-width: 0;
  gap: 7px;
  padding: 16px 18px;
  border: 1px solid var(--tensor-line);
  background: var(--tensor-surface);
  overflow-wrap: anywhere;
}
.copy-state p { margin: 0; }
.copy-state button {
  width: max-content;
  max-width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--tensor-line);
  border-radius: 4px;
  background: var(--tensor-raised);
  color: var(--tensor-text);
}
.copy-error { color: var(--tensor-error); }
.pending-scope h2 {
  margin: 0 0 12px;
  font-size: 16px;
}
.pending-scope dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 18px;
  margin: 0;
}
.pending-scope dt {
  color: var(--tensor-muted);
  font-size: 12px;
}
.pending-scope dd {
  margin: 3px 0 0;
  overflow-wrap: anywhere;
}
.page-state h2,
.page-state p {
  margin: 0 0 8px;
}
.page-state button,
.capability-state button,
.recovery-actions button {
  padding: 8px 12px;
  border: 1px solid var(--tensor-line);
  border-radius: 4px;
  background: var(--tensor-raised);
  color: var(--tensor-text);
}
.capability-state,
.operation-message {
  display: grid;
  gap: 6px;
}
.availability {
  color: var(--tensor-success);
}
.operation-message {
  color: var(--tensor-error);
}
.recovery-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
@media (max-width: 680px) {
  .integrity-page {
    gap: 14px;
  }
  .pending-scope dl {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
