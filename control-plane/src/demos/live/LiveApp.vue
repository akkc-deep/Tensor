<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { Download, Grid, Setting, ArrowLeft, ArrowRight, Check, Search, RefreshRight } from '@element-plus/icons-vue'
import { useDownloadFlow } from '../../composables/useDownloadFlow.js'
import { useDownloadTaskList } from '../../composables/useDownloadTaskList.js'
import { taskStatusLabel, taskExtractionNotice } from '../../utils/downloadTaskText.js'
import LiveDownloadForm from './LiveDownloadForm.vue'
import LiveTaskDialog from './LiveTaskDialog.vue'
import LiveDatasetBrowser from './LiveDatasetBrowser.vue'
import ErrorNotice from './ErrorNotice.vue'

const list = reactive(useDownloadTaskList())
const flow = reactive(useDownloadFlow({ onAccepted: () => list.onAccepted() }))
const page = ref('downloads'), search = ref(''), category = ref(''), detailId = ref(null), main = ref(null)
const accent = ref('#3565b6')
const navigation = [{ id: 'downloads', name: '数据下载', icon: Download }, { id: 'datasets', name: '数据查看', icon: Grid }, { id: 'settings', name: '外观设置', icon: Setting }]
const currentPage = computed(() => navigation.find(item => item.id === page.value))
const categories = computed(() => [...new Set(flow.apis.map(item => item.category))])
const visibleCatalog = computed(() => flow.apis.filter(item => (!category.value || item.category === category.value) && `${item.displayName} ${item.apiName}`.toLowerCase().includes(search.value.trim().toLowerCase())))
const statusOptions = { QUEUED: '排队中', RUNNING: '运行中', SUCCEEDED: '已成功', PARTIAL_FAILED: '部分失败', FAILED: '失败', INTERRUPTED: '已中断' }
const hasNext = computed(() => list.result && BigInt(list.page) * BigInt(list.pageSize) < list.result.total)
const taskFilterDraft = reactive({ pluginId: '', apiName: '', submissionId: '' })
const taskFilterFields = [
  { name: 'pluginId', label: '任务数据源', placeholder: '例如 tushare_pro' },
  { name: 'apiName', label: '任务接口', placeholder: '例如 daily' },
  { name: 'submissionId', label: '提交标识', placeholder: '完整 UUID，可选' },
]
let disposed = false

async function applyTaskFilters() {
  if (!await list.changeFilters(taskFilterDraft)) {
    await nextTick()
    main.value?.querySelector('.live-task-filters [aria-invalid="true"]')?.focus()
  }
}
function resetTaskFilters() {
  for (const key of Object.keys(taskFilterDraft)) taskFilterDraft[key] = ''
  list.changeFilters({}, '')
}
async function selectSource(id) {
  search.value = category.value = ''
  if (await flow.selectSource(id)) {
    const selected = flow.apis.find(api => api.apiName === 'daily') ?? flow.apis[0]
    if (selected && !disposed) await flow.selectApi(selected.apiName)
  }
}
async function connect() {
  if (await flow.load()) {
    const selected = flow.sources.find(source => source.downloadAvailable) ?? flow.sources[0]
    if (selected && !disposed) await selectSource(selected.pluginId)
  }
}
function name(task) {
  return (task.pluginId === flow.selectedPluginId && flow.apis.find(api => api.apiName === task.apiName)?.displayName) || task.apiName
}
async function navigate(id) {
  page.value = id
  await nextTick()
  main.value?.focus()
}
watch(page, value => list.setActive(value === 'downloads'))
onMounted(async () => {
  list.start()
  await flow.recoverSubmission()
  if (!disposed) await connect()
})
onBeforeUnmount(() => { disposed = true; flow.dispose(); list.dispose() })
</script>

<template>
  <div class="demo-root theme-studio live-root" :style="{ '--accent': accent, '--accent-soft': `color-mix(in srgb, ${accent} 10%, white)` }">
    <a href="#live-workspace" class="skip-link">跳转到工作区</a>
    <header class="demo-banner">
      <a class="demo-label" href="/studio-live.html"><span class="demo-label-mark">T</span><span>Studio 工作台<span class="demo-label-sub">后端接入版</span></span></a>
      <div class="demo-environment"><span>真实数据</span><a href="/ui-demos.html" target="_blank" rel="noopener">原版 Demo</a><a href="/downloads" target="_blank" rel="noopener">正式控制面</a></div>
    </header>
    <div class="app-frame top-layout">
      <header class="top-nav">
        <div class="brand"><span class="brand-mark" aria-hidden="true"></span><span>Tensor</span></div>
        <nav aria-label="工作区导航"><button v-for="item in navigation" :key="item.id" :class="{ active: page === item.id }" :aria-current="page === item.id ? 'page' : undefined" @click="navigate(item.id)"><component :is="item.icon" />{{ item.name }}</button></nav>
        <div class="top-workspace"><span class="connection-dot" :class="{ offline: flow.metadataState === 'FAILURE' }"></span>{{ flow.metadataState === 'FAILURE' ? '连接需检查' : flow.sources.length ? '已连接数据源' : '正在连接' }}<span class="workspace-avatar">研</span></div>
      </header>
      <div class="main-shell">
        <div class="workspace-topline"><span>工作空间 <span class="slash">/</span> {{ currentPage.name }}</span><span class="sample-note">{{ page === 'downloads' ? '下载将在后台执行' : page === 'datasets' ? '读取已入库数据' : '当前页面外观' }}</span></div>
        <main id="live-workspace" ref="main" tabindex="-1">
          <header class="page-heading">
            <div><h1>{{ page === 'downloads' ? '下载工作台' : currentPage.name }}</h1><p>{{ page === 'downloads' ? '接口、参数、任务，在同一视野。' : page === 'datasets' ? '找到需要的数据，让研究继续。' : '让工作台更合你的习惯。' }}</p></div>
            <span v-if="page === 'downloads' && flow.selectedSource" class="source-indicator">{{ flow.selectedSource.displayName }} <span class="muted">/ {{ flow.apis.length }} 个接口</span></span>
          </header>
          <div v-show="page === 'downloads'" class="studio-layout">
            <section class="catalog-panel" aria-label="接口目录">
              <header><h2>接口目录 <span class="count">{{ flow.apis.length }}</span></h2></header>
              <label class="live-source field"><span>数据源</span><select :value="flow.selectedPluginId" :disabled="flow.locked || !flow.sources.length" @change="selectSource($event.target.value)"><option v-if="!flow.sources.length" value="">等待连接</option><option v-for="source in flow.sources" :key="source.pluginId" :value="source.pluginId">{{ source.displayName }}</option></select></label>
              <label class="catalog-search"><Search /><input v-model="search" aria-label="搜索接口" placeholder="搜索名称或接口…" /></label>
              <select v-model="category" class="category-select" aria-label="接口分类"><option value="">全部分类</option><option v-for="item in categories" :key="item">{{ item }}</option></select>
              <div class="catalog-list"><button v-for="item in visibleCatalog" :key="item.apiName" :disabled="flow.locked" :class="{ selected: flow.selectedApiName === item.apiName }" :aria-pressed="flow.selectedApiName === item.apiName" @click="flow.selectedApiName !== item.apiName && flow.selectApi(item.apiName)"><span>{{ item.displayName }}<code>{{ item.apiName }}</code></span><ArrowRight v-if="flow.selectedApiName === item.apiName" /></button><p v-if="!visibleCatalog.length" class="catalog-empty">{{ flow.metadataState === 'LOADING' ? '正在加载接口…' : '暂无匹配接口' }}</p></div>
              <footer>{{ visibleCatalog.length }} 个接口</footer>
            </section>
            <section class="studio-form" :aria-busy="flow.metadataState === 'LOADING'">
              <div v-if="flow.selectedApi" class="selected-api-heading"><span class="api-glyph"><Grid /></span><div><h2>{{ flow.selectedApi.displayName }}</h2><code>{{ flow.selectedApiName }}</code></div></div>
              <div v-if="flow.metadataError" class="live-inset"><ErrorNotice :error="flow.metadataError" title="下载配置加载失败" retry-label="重新连接" :disabled="flow.locked" @retry="connect" /></div>
              <p v-if="flow.selectedSource?.unavailableReason" class="live-notice live-inset">{{ flow.selectedSource.unavailableReason }}</p>
              <LiveDownloadForm :flow="flow" @detail="detailId = $event" />
              <p v-if="!flow.selectedApi && !flow.metadataError" class="empty-state">{{ flow.metadataState === 'LOADING' ? '正在读取下载配置…' : '选择左侧接口，开始下载。' }}</p>
            </section>
            <aside class="studio-tasks">
              <section class="task-section" aria-label="下载任务">
                <header class="section-heading"><h2>最近任务 <span v-if="list.result" class="count">{{ list.result.total }}</span></h2><button class="icon-button" aria-label="刷新任务" :disabled="list.loading" @click="list.refresh"><RefreshRight /></button></header>
                <label class="field live-task-filter"><span>任务状态</span><select :value="list.status" aria-label="任务状态" @change="list.changeStatus($event.target.value)"><option value="">全部状态</option><option v-for="(label, value) in statusOptions" :key="value" :value="value">{{ label }}</option></select></label>
                <details class="live-task-search">
                  <summary>更多筛选</summary>
                  <form class="live-task-filters" novalidate @submit.prevent="applyTaskFilters">
                    <label v-for="field in taskFilterFields" :key="field.name" class="field">
                      <span>{{ field.label }}</span>
                      <input v-model="taskFilterDraft[field.name]" :name="`task-${field.name}`" :placeholder="field.placeholder" autocomplete="off" :aria-invalid="Boolean(list.filterErrors[field.name])" :aria-describedby="list.filterErrors[field.name] ? `task-filter-error-${field.name}` : undefined" />
                      <small v-if="list.filterErrors[field.name]" :id="`task-filter-error-${field.name}`" class="form-error" role="alert">{{ list.filterErrors[field.name] }}</small>
                    </label>
                    <div class="live-actions"><button type="submit" class="button secondary">应用筛选</button><button type="button" class="text-button" @click="resetTaskFilters">重置任务筛选</button></div>
                  </form>
                </details>
                <ErrorNotice :error="list.error" title="任务刷新失败" retry-label="重新加载任务" :disabled="list.loading" @retry="list.refresh" />
                <p v-if="list.loading && !list.result" class="empty-state" role="status">正在加载任务…</p>
                <div v-else-if="list.result && !list.result.items.length" class="empty-state"><h3>没有匹配的任务</h3><p>调整筛选条件，或创建一个下载。</p></div>
                <div v-if="list.result" class="task-items">
                  <article v-for="task in list.result.items" :key="task.taskId" class="task-row">
                    <button class="task-name" @click="detailId = task.taskId">{{ name(task) }}<span>{{ task.pluginId }} / {{ task.apiName }}</span><small class="task-batch-count">{{ task.mode === 'RANGE' ? '批量' : '单次' }} · {{ task.planReady ? `${task.counts.succeededBatches} / ${task.counts.totalBatches} 批成功` : '正在规划' }}</small></button>
                    <div class="task-trailing"><span class="status" :class="task.status">{{ taskStatusLabel(task) }}</span><small>{{ task.counts.sourceRows }} 行返回</small><button v-if="task.canRetry || task.canResume" class="text-button" :aria-label="`${task.canResume ? '恢复' : '重试'}${name(task)}`" @click="detailId = task.taskId">{{ task.canResume ? '恢复' : '重试' }} <ArrowRight /></button></div>
                    <p v-if="taskExtractionNotice(task)" class="live-task-footnote">{{ taskExtractionNotice(task) }}</p>
                  </article>
                </div>
                <footer class="pagination live-task-pagination">
                  <label>每页 <select :value="list.pageSize" aria-label="每页任务数" @change="list.changePageSize(Number($event.target.value))"><option v-for="size in [20, 50, 100]" :key="size" :value="size">{{ size }}</option></select> 条</label>
                  <div><span>第 {{ list.page }} 页</span><button class="icon-button" aria-label="任务上一页" :disabled="list.page <= 1 || list.loading" @click="list.changePage(list.page - 1)"><ArrowLeft /></button><button class="icon-button" aria-label="任务下一页" :disabled="!hasNext || list.loading" @click="list.changePage(list.page + 1)"><ArrowRight /></button></div>
                </footer>
              </section>
            </aside>
          </div>
          <KeepAlive><LiveDatasetBrowser v-if="page === 'datasets'" /></KeepAlive>
          <section v-if="page === 'settings'" class="settings-content">
            <div class="settings-intro"><Setting /><h2>外观与主题</h2><p>调整工作台的主题色。</p></div>
            <div class="accent-setting"><div><h3>主题色</h3><p>应用于本页，刷新后恢复默认。</p></div><div class="color-options"><button v-for="color in ['#28745a', '#3565b6', '#745942', '#383d43']" :key="color" :style="{ background: color }" :aria-label="`使用主题色 ${color}`" :aria-pressed="accent === color" @click="accent = color"><Check v-if="accent === color" /></button><button class="text-button" @click="accent = '#3565b6'">恢复默认</button></div></div>
          </section>
        </main>
        <footer class="design-caption"><span><b>Studio</b>真实任务 · 后台执行</span></footer>
      </div>
    </div>
    <LiveTaskDialog v-if="detailId" :task-id="detailId" @close="detailId = null; list.refresh()" @updated="list.refresh()" />
  </div>
</template>
