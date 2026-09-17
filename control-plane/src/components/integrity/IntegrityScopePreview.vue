<script setup>
import { computed } from 'vue'

const props = defineProps({
  selection: { type: Object, required: true },
  apis: { type: Array, default: () => [] },
  validation: { type: Object, required: true },
  confirmed: Boolean,
  disabled: Boolean,
  busy: Boolean,
})
const emit = defineEmits(['confirm', 'submit'])
const selected = computed(() => props.selection.apiNames.map((name) =>
  props.apis.find((api) => api.apiName === name) ?? {
    apiName: name, displayName: name, removed: true, descriptor: null,
  }))

function scopeText(descriptor) {
  if (!descriptor) return '缺少覆盖规则，结论可能无法判定'
  if (descriptor.scopeKind === 'STOCK_SNAPSHOT') return '当前快照不能证明所选历史窗口完整'
  if (descriptor.scopeKind === 'NON_STOCK') return '不按股票逐只检查'
  return '按股票与日期范围检查'
}
</script>

<template>
  <section class="preview" aria-labelledby="scope-preview-title">
    <header>
      <h2 id="scope-preview-title">检查口径预览</h2>
      <span>提交前确认范围和判断依据</span>
    </header>
    <dl class="metrics">
      <div><dt>股票数</dt><dd>{{ selection.symbols.length }}</dd></div>
      <div><dt>接口数</dt><dd>{{ selection.apiNames.length }}</dd></div>
      <div><dt>计划单元数</dt><dd>{{ validation.plannedUnits }}</dd></div>
      <div>
        <dt>日期范围</dt>
        <dd>{{ selection.startDate || '—' }} 至 {{ selection.endDate || '—' }}（{{ validation.rangeDays }} 天）</dd>
      </div>
    </dl>
    <ul class="scope-list">
      <li v-for="api in selected" :key="api.apiName">
        <h3>{{ api.displayName }} <code>{{ api.apiName }}</code></h3>
        <p>{{ scopeText(api.descriptor) }}</p>
        <template v-if="api.descriptor">
          <p>
            日期口径：{{ api.descriptor.dateLabel }}
            <span v-if="api.descriptor.dateField">（{{ api.descriptor.dateField }}）</span>
          </p>
          <p>数据集：{{ api.descriptor.datasetKey?.pluginId }}/{{ api.descriptor.datasetKey?.apiName }}</p>
          <p v-for="item in api.descriptor.limitations" :key="item">限制：{{ item }}</p>
          <p
            v-for="dependency in api.descriptor.dependencies"
            :key="`${dependency.datasetKey.pluginId}/${dependency.datasetKey.apiName}`"
          >
            依赖：{{ dependency.datasetKey.pluginId }}/{{ dependency.datasetKey.apiName }} · {{ dependency.purpose }}
          </p>
          <p v-for="rule in api.descriptor.rules" :key="rule.ruleId ?? rule.description">
            依据：{{ rule.description }}
          </p>
        </template>
      </li>
    </ul>
    <div v-if="Object.keys(validation.errors).length" class="validation" role="alert">
      <p v-for="(message, key) in validation.errors" :key="key">{{ message }}</p>
    </div>
    <label class="confirmation">
      <input
        type="checkbox"
        aria-label="我已确认以上检查口径"
        :checked="confirmed"
        :disabled="disabled"
        @change="emit('confirm', $event.target.checked)"
      >
      我已确认以上检查口径
    </label>
    <button
      class="submit"
      type="button"
      aria-label="开始检查"
      :disabled="disabled || busy || !confirmed || !validation.valid"
      :aria-busy="busy ? 'true' : undefined"
      @click="emit('submit')"
    >
      {{ busy ? '正在处理…' : '开始检查' }}
    </button>
  </section>
</template>

<style scoped>
.preview {
  min-width: 0;
  padding: 24px;
  border: 1px solid var(--tensor-line);
  background: var(--tensor-surface);
}
header { display: grid; gap: 4px; }
h2, h3, p { margin: 0; }
header span { color: var(--tensor-muted); font-size: 12px; }
.metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1px;
  margin: 22px 0;
  border: 1px solid var(--tensor-line);
  background: var(--tensor-line);
}
.metrics div { min-width: 0; padding: 13px; background: var(--tensor-raised); }
.metrics dt { color: var(--tensor-muted); font-size: 12px; }
.metrics dd { margin: 6px 0 0; overflow-wrap: anywhere; }
.scope-list { display: grid; gap: 10px; margin: 0; padding: 0; list-style: none; }
.scope-list li { min-width: 0; padding: 14px; border: 1px solid var(--tensor-line); }
.scope-list h3, .scope-list p { overflow-wrap: anywhere; }
.scope-list h3 { font-size: 14px; }
.scope-list p { margin-top: 5px; color: var(--tensor-muted); font-size: 12px; }
.scope-list code { font-weight: normal; }
.validation { margin-top: 14px; color: var(--tensor-error); }
.confirmation { display: flex; gap: 8px; margin-top: 18px; }
.submit {
  margin-top: 14px;
  padding: 10px 18px;
  border: 0;
  border-radius: 4px;
  background: var(--tensor-accent);
  color: white;
  font-weight: 600;
  cursor: pointer;
}
.submit:disabled { cursor: not-allowed; opacity: .55; }
@media (max-width: 680px) {
  .preview { padding: 18px; }
  .metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
