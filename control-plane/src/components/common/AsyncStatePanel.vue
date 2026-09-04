<script setup>
import { computed } from 'vue'

const props = defineProps({
  state: {
    type: String,
    required: true,
    validator: (value) =>
      ['INITIAL', 'LOADING', 'EMPTY', 'FAILURE'].includes(value),
  },
  title: { type: String, required: true },
  message: { type: String, required: true },
})

const role = computed(() => {
  if (props.state === 'FAILURE') return 'alert'
  if (props.state === 'LOADING' || props.state === 'EMPTY') return 'status'
  return undefined
})

const live = computed(() =>
  props.state === 'LOADING' || props.state === 'EMPTY' ? 'polite' : undefined,
)
</script>

<template>
  <section class="async-state-panel" :role="role" :aria-live="live">
    <h2 class="async-state-panel__title">{{ title }}</h2>
    <p class="async-state-panel__message">{{ message }}</p>
    <div v-if="$slots.actions" class="async-state-panel__actions">
      <slot name="actions" />
    </div>
  </section>
</template>

<style scoped>
.async-state-panel {
  padding: 24px;
  border: 1px solid var(--el-border-color-light, #e4e7ed);
  border-radius: 8px;
}

.async-state-panel__title {
  margin: 0 0 8px;
  font-size: 18px;
}

.async-state-panel__message {
  margin: 0;
  line-height: 1.6;
}

.async-state-panel__actions {
  margin-top: 16px;
}
</style>
