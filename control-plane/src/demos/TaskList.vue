<script setup>
import { computed, inject, ref } from 'vue'
import { ArrowRight, RefreshRight, Clock } from '@element-plus/icons-vue'
import { demoKey, statusLabels } from './demoState.js'
const demo = inject(demoKey)
const filter = ref('all')
const tasks = computed(() => demo.tasks.filter(t => filter.value === 'all' || (filter.value === 'active' ? ['RUNNING', 'QUEUED'].includes(t.status) : filter.value === 'done' ? t.status === 'SUCCEEDED' : ['FAILED', 'PARTIAL_FAILED', 'INTERRUPTED'].includes(t.status))))
const active = computed(() => demo.tasks.filter(t => ['RUNNING', 'QUEUED'].includes(t.status)).length)
const actionLabel = task => task.status === 'INTERRUPTED' ? '恢复' : '重试'
const retryable = task => ['FAILED', 'PARTIAL_FAILED', 'INTERRUPTED'].includes(task.status)
</script>

<template>
  <section class="task-section" aria-label="下载任务">
    <header class="section-heading"><div><h2>最近任务 <span class="count">{{ demo.tasks.length }}</span></h2></div><span class="section-meta">示例记录</span></header>
    <div class="task-filters" aria-label="任务状态筛选">
      <button v-for="item in [{ id: 'all', label: '全部' }, { id: 'active', label: '进行中' }, { id: 'done', label: '已完成' }, { id: 'error', label: '需处理' }]" :key="item.id" :class="{ active: filter === item.id }" :aria-pressed="filter === item.id" @click="filter = item.id">{{ item.label }}<span v-if="item.id === 'active' && active">{{ active }}</span></button>
    </div>
    <div v-if="!tasks.length" class="empty-state"><Clock /><h3>这里暂时没有任务</h3><p>新建一个下载，或切换其他状态查看。</p><button class="text-button" @click="filter = 'all'">查看全部任务 <ArrowRight /></button></div>
    <div v-else class="task-items">
      <article v-for="task in tasks" :key="task.id" class="task-row">
        <button class="task-name" @click="demo.detail = task">{{ task.name }}<span>{{ task.apiName }} <span class="task-time">{{ task.time }}</span></span></button>
        <div class="task-trailing"><span class="status" :class="task.status">{{ statusLabels[task.status] }}</span><button v-if="retryable(task)" class="text-button" :aria-label="`${actionLabel(task)}${task.name}`" @click="demo.retry(task)"><RefreshRight />{{ actionLabel(task) }}</button><small v-else>{{ task.status === 'SUCCEEDED' ? `${task.rows} 行已写入` : '等待完成' }}</small></div>
      </article>
    </div>
    <footer v-if="tasks.length" class="task-footer"><span>共 {{ tasks.length }} 个示例任务</span><span>点击名称查看详情</span></footer>
  </section>
</template>
