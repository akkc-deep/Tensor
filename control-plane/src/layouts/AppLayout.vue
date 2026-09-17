<script setup>
import { computed, provide } from 'vue'
import { CircleCheck, Download, Grid, Setting } from '@element-plus/icons-vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { createDownloadTaskChannel, downloadTaskChannelKey } from '../composables/useDownloadTask.js'

provide(downloadTaskChannelKey, createDownloadTaskChannel())

const route = useRoute()
const navItems = [
  { name: 'downloads', label: '数据下载', icon: Download },
  { name: 'datasets', label: '数据查看', icon: Grid },
  { name: 'integrity', label: '数据完整性', icon: CircleCheck },
  { name: 'settings', label: '外观设置', icon: Setting },
]
const currentLabel = computed(() =>
  navItems.find(item => item.name === route.name)?.label
    ?? (route.name === 'download-task' ? '数据下载' : route.name === 'integrity-check' ? '数据完整性' : '页面不存在'),
)
</script>

<template>
  <a class="skip-link" href="#workspace">跳转到工作区</a>
  <div class="app-shell">
    <header class="app-nav top-nav">
      <div class="app-brand">
        <svg class="app-brand__mark" viewBox="0 0 32 36" fill="none" aria-hidden="true">
          <path d="m16 2 14 8v16l-14 8L2 26V10L16 2Z" />
          <path d="m2 10 14 8 14-8M16 18v16m-7-20V6m14 8v16M2 26l14-8" />
        </svg>
        <span class="app-brand__name">Tensor</span>
      </div>
      <nav class="app-nav__links" aria-label="工作区导航">
        <RouterLink
          v-for="item in navItems"
          :key="item.name"
          class="app-nav__link"
          :class="{ 'router-link-active': (item.name === 'downloads' && route.name === 'download-task') || (item.name === 'integrity' && route.name === 'integrity-check') }"
          :aria-current="item.name === route.name || (item.name === 'downloads' && route.name === 'download-task') || (item.name === 'integrity' && route.name === 'integrity-check') ? 'page' : undefined"
          :to="{ name: item.name }"
        >
          <component :is="item.icon" aria-hidden="true" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>
      <div class="top-workspace">
        <span class="connection-dot" aria-hidden="true" />个人工作空间
        <span class="workspace-avatar" aria-hidden="true">研</span>
      </div>
    </header>
    <div class="workspace-shell">
      <div class="workspace-bar">
        <span>工作空间</span><span class="slash">/</span><b>{{ currentLabel }}</b>
      </div>
      <main id="workspace" tabindex="-1">
        <RouterView v-slot="{ Component, route: activeRoute }">
          <KeepAlive :include="['DownloadView', 'DatasetView']" :max="2">
            <component :is="Component" :key="activeRoute.name === 'download-task' ? 'downloads' : activeRoute.name" />
          </KeepAlive>
        </RouterView>
      </main>
    </div>
  </div>
  <RouterView name="task" />
</template>
