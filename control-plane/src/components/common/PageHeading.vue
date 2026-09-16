<script setup>
defineProps({
  id: { type: String, required: true },
  title: { type: String, required: true },
  description: { type: String, required: true },
})
</script>

<template>
  <header class="page-heading">
    <div>
      <h1 :id="id">{{ title }}</h1>
      <p>{{ description }}</p>
    </div>
    <slot />
  </header>
</template>
