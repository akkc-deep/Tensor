<script setup>
import { RouterLink } from 'vue-router'
</script>

<template>
  <section class="page" aria-labelledby="not-found-title">
    <h1 id="not-found-title">页面不存在</h1>
    <p>当前地址不存在。</p>
    <RouterLink class="page__action" :to="{ name: 'downloads' }">
      返回数据下载
    </RouterLink>
  </section>
</template>
