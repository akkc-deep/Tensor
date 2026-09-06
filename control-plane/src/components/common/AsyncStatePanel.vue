<script setup>
import { computed } from 'vue'

const props = defineProps({
  state: {
    type: String,
    required: true,
    validator: (value) =>
      ['INITIAL', 'LOADING', 'SUCCESS', 'EMPTY', 'FAILURE'].includes(value),
  },
  title: { type: String, required: true },
  message: { type: String, required: true },
  requestId: { type: String, default: '' },
  retryLabel: { type: String, default: '' },
})

const emit = defineEmits(['retry'])

const role = computed(() => {
  if (props.state === 'FAILURE') return 'alert'
  if (['LOADING', 'SUCCESS', 'EMPTY'].includes(props.state)) return 'status'
  return undefined
})

const live = computed(() =>
  ['LOADING', 'SUCCESS', 'EMPTY'].includes(props.state) ? 'polite' : undefined,
)
</script>

<template>
  <section
    class="async-state-panel"
    :class="`async-state-panel--${state.toLowerCase()}`"
    :role="role"
    :aria-live="live"
  >
    <span class="async-state-panel__mark" aria-hidden="true">
      <svg viewBox="0 0 24 24" fill="none">
        <path v-if="state === 'INITIAL'" d="M5 12h14M12 5v14" />
        <path v-else-if="state === 'LOADING'" d="M20 12a8 8 0 1 1-8-8" />
        <path v-else-if="state === 'SUCCESS'" d="m5 12 4 4 10-10" />
        <path
          v-else-if="state === 'EMPTY'"
          d="M4 6h16v13H4V6Zm0 8h5l2 2h2l2-2h5"
        />
        <path v-else d="m12 3 9 17H3l9-17Zm0 6v5m0 3v.5" />
      </svg>
    </span>
    <h2 class="async-state-panel__title">{{ title }}</h2>
    <p class="async-state-panel__message">{{ message }}</p>
    <slot />
    <p v-if="requestId" class="async-state-panel__request-id">
      请求 ID：<code>{{ requestId }}</code>
    </p>
    <div
      v-if="retryLabel || $slots.actions"
      class="async-state-panel__actions"
    >
      <el-button
        v-if="retryLabel"
        native-type="button"
        @click="emit('retry')"
      >
        {{ retryLabel }}
      </el-button>
      <slot name="actions" />
    </div>
  </section>
</template>

<style scoped>
.async-state-panel {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: flex-start;
  padding: 30px 26px;
  color: var(--tensor-text);
}

.async-state-panel__mark {
  display: grid;
  width: 46px;
  height: 46px;
  place-items: center;
  margin-bottom: 23px;
  color: var(--tensor-muted);
  border: 1px solid var(--tensor-line);
  border-radius: 50%;
  background: var(--tensor-raised);
}

.async-state-panel__mark svg {
  width: 23px;
  height: 23px;
  stroke: currentColor;
  stroke-width: 1.7;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.async-state-panel--loading .async-state-panel__mark {
  color: var(--tensor-accent);
}

.async-state-panel--success .async-state-panel__mark {
  color: var(--tensor-success);
}

.async-state-panel--failure .async-state-panel__mark {
  color: var(--tensor-error);
}

.async-state-panel__title {
  margin: 0;
  font-size: 22px;
  font-weight: 550;
  letter-spacing: -0.02em;
  line-height: 1.35;
  overflow-wrap: anywhere;
  text-wrap: balance;
}

.async-state-panel__message,
.async-state-panel__request-id {
  max-width: 70ch;
  margin: 10px 0 0;
  color: var(--tensor-muted);
  font-size: 12px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}

.async-state-panel__request-id code {
  font-variant-numeric: tabular-nums;
  white-space: normal;
  overflow-wrap: anywhere;
}

.async-state-panel__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 16px;
}

.async-state-panel__actions :deep(.el-button:focus-visible) {
  outline: 3px solid var(--tensor-interactive-color);
  outline-offset: 3px;
}

@media (max-width: 680px) {
  .async-state-panel {
    padding: 27px 18px;
  }
}
</style>
