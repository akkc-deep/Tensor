<script setup>
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'

const route = useRoute()
const navItems = [
  {
    name: 'downloads',
    label: '数据下载',
    key: '01',
    paths: ['M12 3v12m-5-5 5 5 5-5', 'M4 16v4h16v-4'],
  },
  {
    name: 'datasets',
    label: '数据查看',
    key: '02',
    paths: ['M4 5h16v14H4z', 'M4 10h16M9 5v14'],
  },
  {
    name: 'settings',
    label: '设置',
    key: '03',
    paths: [
      'M4 7h7m4 0h5M4 17h3m4 0h9',
      'M15 7a2 2 0 1 1-4 0 2 2 0 0 1 4 0M11 17a2 2 0 1 1-4 0 2 2 0 0 1 4 0',
    ],
  },
]
const routeLabels = {
  downloads: '数据下载',
  datasets: '数据查看',
  settings: '设置',
  'not-found': '页面不存在',
}
const currentLabel = computed(() => routeLabels[route.name] ?? '页面不存在')
</script>

<template>
  <a class="skip-link" href="#workspace">跳转到工作区</a>
  <div class="app-shell">
    <aside class="app-nav">
      <div class="app-brand">
        <svg class="app-brand__mark" viewBox="0 0 32 36" fill="none" aria-hidden="true">
          <path d="m16 2 14 8v16l-14 8L2 26V10L16 2Z" />
          <path d="m2 10 14 8 14-8M16 18v16m-7-20V6m14 8v16M2 26l14-8" />
        </svg>
        <div>
          <div class="app-brand__word">Tensor</div>
          <small>证券数据工作台</small>
        </div>
      </div>

      <nav class="app-nav__links" aria-label="工作区导航">
        <RouterLink
          v-for="item in navItems"
          :key="item.name"
          class="app-nav__link"
          :to="{ name: item.name }"
        >
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path v-for="path in item.paths" :key="path" :d="path" />
          </svg>
          <span>{{ item.label }}</span>
          <small>{{ item.key }}</small>
        </RouterLink>
      </nav>

      <div class="app-nav__footer">
        <strong>多源证券数据平台</strong>
        <p>连接数据源<br>让数据进入研究流程</p>
        <span>Tensor / v1</span>
      </div>
    </aside>

    <div class="workspace-shell">
      <header class="workspace-bar">
        <div><span>工作空间</span><span>/</span><b>{{ currentLabel }}</b></div>
      </header>
      <main id="workspace" tabindex="-1">
        <RouterView v-slot="{ Component, route: activeRoute }">
          <KeepAlive :include="['DownloadView', 'DatasetView']" :max="2">
            <component :is="Component" :key="activeRoute.name" />
          </KeepAlive>
        </RouterView>
      </main>
    </div>
  </div>
</template>
