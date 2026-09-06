<script setup>
import { computed, ref } from 'vue'

import FieldError from './FieldError.vue'

const props = defineProps({
  id: { type: String, required: true },
  label: { type: String, required: true },
  type: { type: String, required: true },
  modelValue: { type: String, default: '' },
  required: { type: Boolean, default: false },
  description: { type: String, default: '' },
  error: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  allowedValues: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:modelValue'])
const control = ref()

const descriptionId = computed(() => `${props.id}-description`)
const errorId = computed(() => `${props.id}-error`)
const describedBy = computed(() => [
  props.description ? descriptionId.value : null,
  props.error ? errorId.value : null,
].filter(Boolean).join(' ') || undefined)

function syncInputAttributes(element, binding) {
  const input = element.querySelector('input')
  if (!input) return

  for (const [name, value] of Object.entries(binding.value)) {
    if (value === undefined) input.removeAttribute(name)
    else input.setAttribute(name, value)
  }
}

const vInputA11y = {
  mounted: syncInputAttributes,
  updated: syncInputAttributes,
}

function updateValue(value) {
  if (!props.disabled) emit('update:modelValue', value)
}

function focus() {
  control.value?.focus()
}

defineExpose({ focus })
</script>

<template>
  <div
    v-input-a11y="{
      id,
      'aria-required': required ? 'true' : 'false',
      'aria-invalid': error ? 'true' : undefined,
      'aria-describedby': describedBy,
    }"
    class="metadata-field"
  >
    <label class="metadata-field__label" :for="id">
      {{ label }}<span
        v-if="required"
        class="metadata-field__required"
        aria-hidden="true"
      >*</span>
    </label>

    <el-date-picker
      v-if="type === 'DATE' || type === 'DATE_RANGE_MEMBER'"
      :id="id"
      ref="control"
      :model-value="modelValue"
      type="date"
      value-format="YYYY-MM-DD"
      :disabled="disabled"
      :aria-required="required ? 'true' : 'false'"
      :aria-invalid="error ? 'true' : undefined"
      :aria-describedby="describedBy"
      @update:model-value="updateValue"
    />
    <el-date-picker
      v-else-if="type === 'MONTH'"
      :id="id"
      ref="control"
      :model-value="modelValue"
      type="month"
      value-format="YYYY-MM"
      :disabled="disabled"
      :aria-required="required ? 'true' : 'false'"
      :aria-invalid="error ? 'true' : undefined"
      :aria-describedby="describedBy"
      @update:model-value="updateValue"
    />
    <el-select
      v-else-if="type === 'ENUM'"
      :id="id"
      ref="control"
      :model-value="modelValue"
      :disabled="disabled"
      :aria-required="required ? 'true' : 'false'"
      :aria-invalid="error ? 'true' : undefined"
      :aria-describedby="describedBy"
      @update:model-value="updateValue"
    >
      <el-option
        v-for="value in allowedValues"
        :key="value"
        :label="value"
        :value="value"
      />
    </el-select>
    <el-input
      v-else
      :id="id"
      ref="control"
      :model-value="modelValue"
      :disabled="disabled"
      :aria-required="required ? 'true' : 'false'"
      :aria-invalid="error ? 'true' : undefined"
      :aria-describedby="describedBy"
      @update:model-value="updateValue"
    />

    <p
      v-if="description"
      :id="descriptionId"
      class="metadata-field__description"
    >
      {{ description }}
    </p>
    <FieldError :id="errorId" :message="error" />
  </div>
</template>

<style scoped>
.metadata-field {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.metadata-field__label {
  color: var(--app-text, var(--el-text-color-primary));
  font-size: 12px;
  font-weight: 600;
}

.metadata-field__required {
  color: var(--el-color-danger, #f56c6c);
}

.metadata-field__description {
  margin: 0;
  color: var(--app-text-muted, var(--el-text-color-secondary));
  overflow-wrap: anywhere;
}

.metadata-field > :deep(.el-date-editor),
.metadata-field > :deep(.el-input),
.metadata-field > :deep(.el-select) {
  width: 100%;
  min-width: 0;
}

.metadata-field :deep(.el-input__wrapper),
.metadata-field :deep(.el-select__wrapper) {
  min-height: 44px;
}

.metadata-field :deep(.field-error) {
  overflow-wrap: anywhere;
}
</style>
