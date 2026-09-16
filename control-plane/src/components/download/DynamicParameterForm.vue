<script setup>
import { computed, ref } from 'vue'

import FieldError from '../common/FieldError.vue'
import { useFormValidation } from '../../composables/useFormValidation.js'
import { useParameterForm } from '../../composables/useParameterForm.js'

const props = defineProps({
  parameters: { type: Array, required: true },
  disabled: { type: Boolean, default: false },
  range: { type: Object, default: null },
})

const groups = computed(() => {
  if (!props.range) return [{ key: 'parameters', parameters: props.parameters }]
  const endpoints = [props.range.startParameter, props.range.endParameter]
  return [
    { key: 'parameters', parameters: props.parameters.filter(({ name }) => !endpoints.includes(name)) },
    { key: 'range', parameters: endpoints.map((name) => props.parameters.find((parameter) => parameter.name === name)).filter(Boolean) },
  ]
})
const orderedParameters = computed(() => groups.value.flatMap((group) => group.parameters))
const { values, errors, firstError, setValue, validateValues, normalizedValues, reset } = useParameterForm(orderedParameters)
const formElement = ref(null)
const { setControl, validate } = useFormValidation(() => {
  // Native date/month segments can be incomplete without emitting an input event.
  for (const control of formElement.value.querySelectorAll('input, select')) {
    setValue(control.name, values[control.name], control.validity.badInput)
  }
  return validateValues()
}, firstError)
const controlId = (name) => `download-parameter-${name}`
const description = (parameter) => parameter.description || (parameter.type === 'TS_CODE' ? '代码与市场，例如 000001.SZ' : '')
const inputType = (type) => ['DATE', 'DATE_RANGE_MEMBER'].includes(type) ? 'date' : type === 'MONTH' ? 'month' : 'text'

function controlAttributes(parameter) {
  const id = controlId(parameter.name)
  return {
    id,
    name: parameter.name,
    required: parameter.required,
    disabled: props.disabled,
    'aria-required': String(parameter.required),
    'aria-invalid': errors[parameter.name] ? 'true' : undefined,
    'aria-describedby': [description(parameter) && `${id}-description`, errors[parameter.name] && `${id}-error`].filter(Boolean).join(' ') || undefined,
  }
}

function updateValue(name, control) {
  if (!props.disabled) setValue(name, control.value, control.validity.badInput)
}

defineExpose({ validate, normalizedValues, reset })
</script>

<template>
  <div ref="formElement" class="dynamic-parameter-form">
    <section v-for="group in groups" v-show="group.parameters.length" :key="group.key" :class="{ 'range-configuration': group.key === 'range' }" :aria-label="group.key === 'range' ? `${range.dateLabel}范围` : '请求参数'">
      <header v-if="group.key === 'range'" class="mode-heading">
        <h3>{{ range.dateLabel }}范围</h3><span>包含起止当天</span>
      </header>
      <div :class="group.key === 'range' ? 'range-fields' : 'parameter-grid'">
        <div v-for="parameter in group.parameters" :key="parameter.name" class="field" :data-parameter="parameter.name">
          <div class="field-label"><label :for="controlId(parameter.name)">{{ parameter.label }}</label><small>{{ parameter.required ? '必填' : '选填' }}</small></div>
          <select v-if="parameter.type === 'ENUM'" v-bind="controlAttributes(parameter)" :ref="(control) => setControl(parameter.name, control)" :value="values[parameter.name]" @change="updateValue(parameter.name, $event.target)">
            <option value="">{{ parameter.required ? '请选择' : '全部' }}</option>
            <option v-for="value in parameter.allowedValues" :key="value" :value="value">{{ value }}</option>
          </select>
          <input v-else v-bind="controlAttributes(parameter)" :ref="(control) => setControl(parameter.name, control)" :type="inputType(parameter.type)" :value="values[parameter.name]" :placeholder="parameter.type === 'TS_CODE' ? '例如 000001.SZ' : parameter.label" autocomplete="off" @input="updateValue(parameter.name, $event.target)" />
          <small v-if="description(parameter)" :id="`${controlId(parameter.name)}-description`" class="field-help">{{ description(parameter) }}</small>
          <FieldError :id="`${controlId(parameter.name)}-error`" :message="errors[parameter.name] ?? ''" />
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.parameter-grid { display: grid; gap: 2.1rem 1.8rem; }
.range-configuration { margin-top: 2.4rem; }
.mode-heading { display: flex; align-items: center; justify-content: space-between; gap: 1.2rem; margin-bottom: 1.2rem; }
.mode-heading h3 { margin: 0; font-size: 1.4rem; font-weight: 500; }
.mode-heading > span { color: var(--tensor-muted); font-size: 1.2rem; }
.range-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1.6rem; }
.field { display: flex; flex-direction: column; gap: 0.9rem; min-width: 0; }
.field-label { display: flex; justify-content: space-between; align-items: center; gap: 1rem; font-size: 1.4rem; font-weight: 500; }
.field-label small { color: var(--tensor-muted); font-size: 1.2rem; font-weight: 400; white-space: nowrap; }
.field input, .field select { width: 100%; height: 4.3rem; min-width: 0; border: 0.1rem solid var(--tensor-line); border-radius: 0.6rem; padding: 0 1.1rem; background: var(--tensor-surface); color: var(--tensor-text); font: inherit; font-size: 1.4rem; caret-color: var(--tensor-accent); }
.field select { padding-right: 2.3rem; text-overflow: ellipsis; }
.field input::placeholder { color: var(--tensor-muted); }
.field-help { color: var(--tensor-muted); font-size: 1.2rem; line-height: 1.6; }
.field label, .field-help, .field :deep(.field-error) { overflow-wrap: anywhere; }
.field :is(input, select)[aria-invalid="true"] { border-color: var(--el-color-danger); }
.field :is(input, select):disabled { opacity: .55; cursor: not-allowed; }
</style>
