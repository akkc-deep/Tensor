<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  apis: { type: Array, required: true },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue'])
const query = ref('')

const groups = computed(() => {
  const normalizedQuery = query.value.trim().toLowerCase()
  const grouped = new Map()

  for (const api of props.apis) {
    const matches =
      !normalizedQuery ||
      api.apiName.toLowerCase().includes(normalizedQuery) ||
      api.displayName.toLowerCase().includes(normalizedQuery)
    if (!matches) continue

    const existing = grouped.get(api.category)
    if (existing) existing.push(api)
    else grouped.set(api.category, [api])
  }

  return [...grouped].map(([category, apis]) => ({ category, apis }))
})

function filter(queryValue) {
  query.value = queryValue
}
</script>

<template>
  <div class="api-select">
    <label class="api-select__label" for="download-api">数据接口</label>
    <el-select
      id="download-api"
      :model-value="modelValue"
      :disabled="disabled"
      filterable
      default-first-option
      :filter-method="filter"
      placeholder="请选择数据接口"
      no-data-text="暂无接口"
      no-match-text="无匹配接口"
      aria-label="数据接口"
      @update:model-value="emit('update:modelValue', $event)"
    >
      <el-option-group
        v-for="group in groups"
        :key="group.category"
        :label="group.category"
      >
        <el-option
          v-for="api in group.apis"
          :key="api.apiName"
          :label="`${api.displayName} (${api.apiName})`"
          :value="api.apiName"
        >
          <span>{{ api.displayName }}</span>
          <code class="api-select__api-name">{{ api.apiName }}</code>
        </el-option>
      </el-option-group>
    </el-select>
  </div>
</template>

<style scoped>
.api-select {
  display: grid;
  gap: 8px;
}

.api-select__label {
  font-weight: 600;
}

.api-select__api-name {
  margin-left: 8px;
  color: var(--el-text-color-secondary, #909399);
}
</style>
