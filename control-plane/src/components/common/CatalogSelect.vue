<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  id: { type: String, required: true },
  label: { type: String, required: true },
  items: { type: Array, required: true },
  modelValue: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  emptyText: { type: String, required: true },
  noMatchText: { type: String, required: true },
})

const emit = defineEmits(['update:modelValue'])
const query = ref('')

const groups = computed(() => {
  const normalizedQuery = query.value.trim().toLowerCase()
  const grouped = new Map()

  for (const item of props.items) {
    const matches = !normalizedQuery ||
      item.apiName.toLowerCase().includes(normalizedQuery) ||
      item.displayName.toLowerCase().includes(normalizedQuery)
    if (!matches) continue
    const group = grouped.get(item.category)
    if (group) group.push(item)
    else grouped.set(item.category, [item])
  }

  return [...grouped].map(([category, items]) => ({ category, items }))
})

function updateQuery(value) {
  query.value = value
}

function clearQuery() {
  query.value = ''
}

function updateValue(value) {
  if (!props.disabled) emit('update:modelValue', value)
}
</script>

<template>
  <div class="catalog-select" @keydown.capture.esc="clearQuery">
    <label class="catalog-select__label" :for="id">{{ label }}</label>
    <el-select
      :id="id"
      :model-value="modelValue"
      :disabled="disabled"
      filterable
      default-first-option
      :filter-method="updateQuery"
      :placeholder="`请选择${label}`"
      :no-data-text="emptyText"
      :no-match-text="noMatchText"
      :aria-label="label"
      @update:model-value="updateValue"
    >
      <template #empty>
        {{ query.trim() && items.length ? noMatchText : emptyText }}
      </template>
      <el-option-group
        v-for="group in groups"
        :key="group.category"
        :label="group.category"
      >
        <el-option
          v-for="item in group.items"
          :key="item.apiName"
          :label="`${item.displayName} (${item.apiName})`"
          :value="item.apiName"
        >
          <span>{{ item.displayName }}</span>
          <code class="catalog-select__api-name">{{ item.apiName }}</code>
        </el-option>
      </el-option-group>
    </el-select>
  </div>
</template>

<style scoped>
.catalog-select {
  display: grid;
  min-width: 0;
  gap: 8px;
}

.catalog-select__label {
  font-weight: 600;
}

.catalog-select__api-name {
  margin-left: 8px;
  color: var(--tensor-muted);
}

.catalog-select .el-select {
  width: 100%;
}
</style>
