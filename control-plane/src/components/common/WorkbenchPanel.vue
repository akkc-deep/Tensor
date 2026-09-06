<script setup>
defineProps({
  headingId: { type: String, required: true },
  title: { type: String, required: true },
  meta: { type: String, default: '' },
})
</script>

<template>
  <section class="workbench-panel" :aria-labelledby="headingId">
    <header class="panel-heading">
      <h2 :id="headingId">{{ title }}</h2>
      <code v-if="meta" class="workbench-panel__meta">{{ meta }}</code>
    </header>
    <slot />
  </section>
</template>
