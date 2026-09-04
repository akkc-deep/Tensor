<script setup>
import { nextTick, toRef } from 'vue'

import FieldError from '../common/FieldError.vue'
import { useParameterForm } from '../../composables/useParameterForm.js'

const props = defineProps({
  parameters: { type: Array, required: true },
  disabled: { type: Boolean, default: false },
})

const controls = new Map()
const {
  values,
  errors,
  firstError,
  setValue,
  validateValues,
  normalizedValues,
  reset,
} = useParameterForm(toRef(props, 'parameters'))

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

function controlId(name) {
  return `download-parameter-${name}`
}

function descriptionId(name) {
  return `${controlId(name)}-description`
}

function errorId(name) {
  return `${controlId(name)}-error`
}

function describedBy(parameter) {
  return [
    parameter.description ? descriptionId(parameter.name) : null,
    errors[parameter.name] ? errorId(parameter.name) : null,
  ]
    .filter(Boolean)
    .join(' ') || undefined
}

function updateValue(name, value) {
  if (!props.disabled) setValue(name, value)
}

function setControl(name, control) {
  if (control) controls.set(name, control)
  else controls.delete(name)
}

async function validate() {
  const valid = validateValues()
  if (!valid) {
    await nextTick()
    controls.get(firstError.value)?.focus()
  }
  return valid
}

defineExpose({ validate, normalizedValues, reset })
</script>

<template>
  <div class="dynamic-parameter-form">
    <div
      v-for="parameter in parameters"
      :key="parameter.name"
      v-input-a11y="{
        id: controlId(parameter.name),
        'aria-required': parameter.required ? 'true' : 'false',
        'aria-invalid': errors[parameter.name] ? 'true' : undefined,
        'aria-describedby': describedBy(parameter),
      }"
      class="parameter-field"
      :data-parameter="parameter.name"
    >
      <label
        class="parameter-field__label"
        :for="controlId(parameter.name)"
      >
        {{ parameter.label }}<span
          v-if="parameter.required"
          class="parameter-field__required"
          aria-hidden="true"
        >*</span>
      </label>

      <el-date-picker
        v-if="parameter.type === 'DATE' || parameter.type === 'DATE_RANGE_MEMBER'"
        :id="controlId(parameter.name)"
        :ref="(control) => setControl(parameter.name, control)"
        :model-value="values[parameter.name]"
        type="date"
        value-format="YYYY-MM-DD"
        :disabled="disabled"
        :aria-required="parameter.required ? 'true' : 'false'"
        :aria-invalid="errors[parameter.name] ? 'true' : undefined"
        :aria-describedby="describedBy(parameter)"
        @update:model-value="updateValue(parameter.name, $event)"
      />
      <el-date-picker
        v-else-if="parameter.type === 'MONTH'"
        :id="controlId(parameter.name)"
        :ref="(control) => setControl(parameter.name, control)"
        :model-value="values[parameter.name]"
        type="month"
        value-format="YYYY-MM"
        :disabled="disabled"
        :aria-required="parameter.required ? 'true' : 'false'"
        :aria-invalid="errors[parameter.name] ? 'true' : undefined"
        :aria-describedby="describedBy(parameter)"
        @update:model-value="updateValue(parameter.name, $event)"
      />
      <el-select
        v-else-if="parameter.type === 'ENUM'"
        :id="controlId(parameter.name)"
        :ref="(control) => setControl(parameter.name, control)"
        :model-value="values[parameter.name]"
        :disabled="disabled"
        :aria-required="parameter.required ? 'true' : 'false'"
        :aria-invalid="errors[parameter.name] ? 'true' : undefined"
        :aria-describedby="describedBy(parameter)"
        @update:model-value="updateValue(parameter.name, $event)"
      >
        <el-option
          v-for="value in parameter.allowedValues"
          :key="value"
          :label="value"
          :value="value"
        />
      </el-select>
      <el-input
        v-else
        :id="controlId(parameter.name)"
        :ref="(control) => setControl(parameter.name, control)"
        :model-value="values[parameter.name]"
        :disabled="disabled"
        :aria-required="parameter.required ? 'true' : 'false'"
        :aria-invalid="errors[parameter.name] ? 'true' : undefined"
        :aria-describedby="describedBy(parameter)"
        @update:model-value="updateValue(parameter.name, $event)"
      />

      <p
        v-if="parameter.description"
        :id="descriptionId(parameter.name)"
        class="parameter-field__description"
      >
        {{ parameter.description }}
      </p>
      <FieldError
        :id="errorId(parameter.name)"
        :message="errors[parameter.name] ?? ''"
      />
    </div>
  </div>
</template>

<style scoped>
.dynamic-parameter-form,
.parameter-field {
  display: grid;
  gap: 8px;
}

.parameter-field__label {
  font-weight: 600;
}

.parameter-field__required {
  color: var(--el-color-danger, #f56c6c);
}

.parameter-field__description {
  margin: 0;
  color: var(--el-text-color-secondary, #909399);
}
</style>
