<script setup>
import { computed, nextTick, onBeforeUnmount, provide, ref, watch } from 'vue'
import { Download, Grid, Setting, ArrowRight, Close, Check, Search, RefreshRight, Link } from '@element-plus/icons-vue'
import DownloadForm from './DownloadForm.vue'
import TaskList from './TaskList.vue'
import DatasetBrowser from './DatasetBrowser.vue'
import { createDemo, demoKey, statusLabels } from './demoState.js'

const demo = createDemo()
provide(demoKey, demo)
const navigation = [{ id: 'downloads', name: '数据下载', icon: Download }, { id: 'datasets', name: '数据查看', icon: Grid }, { id: 'settings', name: '外观设置', icon: Setting }]
const currentPage = computed(() => navigation.find(item => item.id === demo.page))
const search = ref('')
const category = ref('全部')
const visibleCatalog = computed(() => demo.catalog.filter(item => (category.value === '全部' || item.category === category.value) && `${item.name} ${item.id}`.toLowerCase().includes(search.value.toLowerCase())))
const detailDialog = ref(null)
const main = ref(null)
function navigate(id) { demo.page = id; nextTick(() => main.value?.focus()) }
watch(() => demo.detail, async value => {
  if (value) { await nextTick(); detailDialog.value?.showModal() }
  else detailDialog.value?.close()
})
onBeforeUnmount(demo.dispose)
</script>

<template>
  <div class="demo-root theme-studio" :style="demo.settingsColor ? { '--accent': demo.settingsColor } : {}">
    <a href="#demo-workspace" class="skip-link">跳转到工作区</a>
    <header class="demo-banner">
      <a class="demo-label" href="/ui-demos.html"><span class="demo-label-mark">T</span><span>工作台设计<span class="demo-label-sub">交互 Demo</span></span></a>
      <div class="demo-environment"><span>示例数据</span><a href="/downloads" target="_blank" rel="noopener" title="打开现有控制面">原控制面 <Link /></a></div>
    </header>

    <div class="app-frame top-layout">
      <header class="top-nav">
        <div class="brand"><span class="brand-mark" aria-hidden="true"></span><span>Tensor</span></div>
        <nav aria-label="工作区导航"><button v-for="item in navigation" :key="item.id" :class="{ active: demo.page === item.id }" :aria-current="demo.page === item.id ? 'page' : undefined" @click="navigate(item.id)"><component :is="item.icon" />{{ item.name }}</button></nav>
        <div class="top-workspace"><span class="connection-dot"></span>个人工作空间<span class="workspace-avatar">研</span></div>
      </header>

      <div class="main-shell">
        <div class="workspace-topline"><span>工作空间 <span class="slash">/</span> {{ currentPage.name }}</span><span class="sample-note">本地交互预览</span></div>
        <main id="demo-workspace" ref="main" tabindex="-1">
          <header class="page-heading">
            <div><h1>{{ demo.page === 'downloads' ? '下载工作台' : currentPage.name }}</h1><p>{{ demo.page === 'downloads' ? '接口、参数、任务，在同一视野。' : demo.page === 'datasets' ? '找到需要的数据，让研究继续。' : '让工作台更合你的习惯。' }}</p></div>
            <span v-if="demo.page === 'downloads'" class="source-indicator"><span class="connection-dot"></span>Tushare Pro <span class="muted">/ {{ demo.catalog.length }} 个接口</span></span>
          </header>

            <div v-if="demo.page === 'downloads'" class="studio-layout">
              <section class="catalog-panel" aria-label="接口目录">
                <header><h2>接口目录 <span class="count">{{ demo.catalog.length }}</span></h2></header>
                <label class="catalog-search"><Search /><input v-model="search" aria-label="搜索接口" placeholder="搜索名称或接口…" /></label>
                <select v-model="category" class="category-select" aria-label="接口分类"><option>全部</option><option v-for="c in [...new Set(demo.catalog.map(item => item.category))]" :key="c">{{ c }}</option></select>
                <div class="catalog-list"><button v-for="item in visibleCatalog" :key="item.id" :class="{ selected: demo.selectedId === item.id }" :aria-pressed="demo.selectedId === item.id" @click="demo.select(item.id)"><span>{{ item.name }}<code>{{ item.id }}</code></span><ArrowRight v-if="demo.selectedId === item.id" /></button><p v-if="!visibleCatalog.length" class="catalog-empty">没有匹配的接口，请换个关键词。</p></div>
                <footer>{{ visibleCatalog.length }} 个接口 · Tushare Pro</footer>
              </section>
              <section class="studio-form"><div class="selected-api-heading"><span class="api-glyph"><Grid /></span><div><h2>{{ demo.selected.name }}</h2><code>{{ demo.selected.id }}</code></div></div><DownloadForm /></section>
              <aside class="studio-tasks"><TaskList /></aside>
            </div>

          <DatasetBrowser v-else-if="demo.page === 'datasets'" />

          <section v-else class="settings-content">
            <div class="settings-intro"><Setting /><h2>外观与主题</h2><p>调整工作台的主题色。</p></div>
            <div class="accent-setting">
              <div><h3>主题色</h3><p>仅用于本次 Demo，刷新后恢复默认。</p></div>
              <div class="color-options">
                <button v-for="color in ['#28745a', '#3565b6', '#745942', '#383d43']" :key="color" :style="{ background: color }" :aria-label="`使用主题色 ${color}`" :aria-pressed="(demo.settingsColor || '#3565b6') === color" @click="demo.settingsColor = color"><Check v-if="(demo.settingsColor || '#3565b6') === color" /></button>
                <button class="text-button" @click="demo.settingsColor = ''; demo.notify('已恢复工作台默认主题色')">恢复默认</button>
              </div>
            </div>
          </section>
        </main>
        <footer class="design-caption"><span><b>Studio</b>三栏工作区 · 快速切换</span></footer>
      </div>
    </div>

    <div v-if="demo.notice" class="toast" role="status"><Check />{{ demo.notice }}<button class="icon-button" aria-label="关闭提示" @click="demo.notice = ''"><Close /></button></div>

    <dialog ref="detailDialog" class="detail-dialog" aria-labelledby="detail-title" @close="demo.detail = null" @click="event => { if (event.target === detailDialog) demo.detail = null }">
      <template v-if="demo.detail"><header><div><span class="muted">示例任务 {{ demo.detail.id }}</span><h2 id="detail-title">{{ demo.detail.name }}</h2></div><button class="icon-button" aria-label="关闭任务详情" autofocus @click="demo.detail = null"><Close /></button></header><span class="status" :class="demo.detail.status">{{ statusLabels[demo.detail.status] }}</span><dl class="confirmation"><div><dt>数据源</dt><dd>Tushare Pro</dd></div><div><dt>数据接口</dt><dd><code>{{ demo.detail.apiName }}</code></dd></div><div><dt>下载方式</dt><dd>单次下载</dd></div><div v-for="(value, key) in demo.detail.params" :key="key"><dt>{{ key }}</dt><dd>{{ value }}</dd></div><div><dt>写入行数</dt><dd>{{ demo.detail.rows }}</dd></div></dl><div v-if="['FAILED', 'PARTIAL_FAILED', 'INTERRUPTED'].includes(demo.detail.status)" class="task-error"><h3>{{ demo.detail.status === 'INTERRUPTED' ? '任务执行已中断' : '数据源暂时未响应' }}</h3><p>这是异常状态示例。{{ demo.detail.status === 'INTERRUPTED' ? '恢复后将继续处理未完成的批次。' : '可以重新尝试这次下载。' }}</p><button class="button primary" @click="demo.retry(demo.detail)"><RefreshRight />{{ demo.detail.status === 'INTERRUPTED' ? '恢复任务' : '重试任务' }}</button></div><div class="batch-preview"><h3>批次明细 <span class="count">1</span></h3><div><code>batch-001</code><span class="status" :class="demo.detail.status">{{ statusLabels[demo.detail.status] }}</span><span>{{ demo.detail.rows }} 行</span></div></div><p class="detail-footnote">这是本地交互演示，任务及进度均为示例。</p></template>
    </dialog>
  </div>
</template>
