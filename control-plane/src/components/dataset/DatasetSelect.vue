<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  datasets: { type: Array, required: true },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue'])
const query = ref('')

const groups = computed(() => {
  const normalizedQuery = query.value.trim().toLowerCase()
  const grouped = new Map()

  for (const dataset of props.datasets) {
    const matches = !normalizedQuery ||
      dataset.apiName.toLowerCase().includes(normalizedQuery) ||
      dataset.displayName.toLowerCase().includes(normalizedQuery)
    if (!matches) continue
    const group = grouped.get(dataset.category)
    if (group) group.push(dataset)
    else grouped.set(dataset.category, [dataset])
  }

  return [...grouped].map(([category, datasets]) => ({ category, datasets }))
})

function filter(value) {
  query.value = value
}

function updateValue(value) {
  if (!props.disabled) emit('update:modelValue', value)
}
</script>

<template>
  <div class="dataset-select" @keydown.capture.esc="filter('')">
    <label class="dataset-select__label" for="dataset-select">数据集</label>
    <el-select
      id="dataset-select"
      :model-value="modelValue"
      :disabled="disabled"
      filterable
      default-first-option
      :filter-method="filter"
      placeholder="请选择数据集"
      no-data-text="暂无数据集"
      no-match-text="无匹配数据集"
      aria-label="数据集"
      @update:model-value="updateValue"
    >
      <template #empty>
        {{ query.trim() && datasets.length ? '无匹配数据集' : '暂无数据集' }}
      </template>
      <el-option-group
        v-for="group in groups"
        :key="group.category"
        :label="group.category"
      >
        <el-option
          v-for="dataset in group.datasets"
          :key="dataset.apiName"
          :label="`${dataset.displayName} (${dataset.apiName})`"
          :value="dataset.apiName"
        >
          <span>{{ dataset.displayName }}</span>
          <code class="dataset-select__api-name">{{ dataset.apiName }}</code>
        </el-option>
      </el-option-group>
    </el-select>
  </div>
</template>

<style scoped>
.dataset-select {
  display: grid;
  gap: 8px;
}

.dataset-select__label {
  font-weight: 600;
}

.dataset-select__api-name {
  margin-left: 8px;
  color: var(--el-text-color-secondary, #909399);
}
</style>
