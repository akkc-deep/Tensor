<script setup>
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <div class="tensor-shell">
    <header class="tensor-header">
      <div class="tensor-header__inner">
        <div class="tensor-brand">Tensor</div>
        <nav class="tensor-nav" aria-label="主导航">
          <RouterLink :to="{ name: 'downloads' }">数据下载</RouterLink>
          <RouterLink :to="{ name: 'datasets' }">数据查看</RouterLink>
        </nav>
      </div>
    </header>
    <main class="tensor-main">
      <RouterView />
    </main>
  </div>
</template>
