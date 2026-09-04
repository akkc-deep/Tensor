<script setup>
import { computed, watch } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  sources: { type: Array, required: true },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue'])

const selectedSource = computed(() => {
  if (props.modelValue) {
    return (
      props.sources.find(({ pluginId }) => pluginId === props.modelValue) ?? null
    )
  }
  return props.sources.length === 1 ? props.sources[0] : null
})

watch(
  [() => props.modelValue, () => props.sources],
  ([modelValue, sources]) => {
    if (modelValue === '' && sources.length === 1) {
      emit('update:modelValue', sources[0].pluginId)
    }
  },
  { immediate: true },
)
</script>

<template>
  <div class="data-source-select">
    <label class="data-source-select__label" for="download-data-source">
      数据源
    </label>
    <el-select
      id="download-data-source"
      :model-value="modelValue"
      :disabled="disabled"
      placeholder="请选择数据源"
      aria-label="数据源"
      @update:model-value="emit('update:modelValue', $event)"
    >
      <el-option
        v-for="source in sources"
        :key="source.pluginId"
        :label="source.displayName"
        :value="source.pluginId"
        :disabled="!source.downloadAvailable"
      />
    </el-select>
    <p
      v-if="selectedSource && !selectedSource.downloadAvailable"
      class="data-source-select__reason"
      role="status"
    >
      {{ selectedSource.unavailableReason }}
    </p>
  </div>
</template>

<style scoped>
.data-source-select {
  display: grid;
  gap: 8px;
}

.data-source-select__label {
  font-weight: 600;
}

.data-source-select__reason {
  margin: 0;
  color: var(--el-color-danger, #f56c6c);
}
</style>
