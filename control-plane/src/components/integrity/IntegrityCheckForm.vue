<script setup>
import { computed, ref, watch } from 'vue'
import { parseIntegritySymbols } from '../../utils/integrityForm.js'

const props = defineProps({
  sources: { type: Array, default: () => [] }, capability: { type: Object, default: null },
  categories: { type: Object, default: () => ({}) }, categoryError: { type: Object, default: null },
  modelValue: { type: Object, required: true }, errors: { type: Object, default: () => ({}) }, disabled: Boolean,
})
const emit = defineEmits(['update:modelValue', 'draft-change', 'retry-categories'])
const draft = ref('')
const groups = computed(() => {
  const result = new Map()
  for (const api of props.capability?.apis ?? []) {
    const name = props.categories[api.apiName] || '未分类'
    if (!result.has(name)) result.set(name, [])
    result.get(name).push(api)
  }
  const known = new Set((props.capability?.apis ?? []).map((api) => api.apiName))
  const removed = props.modelValue.apiNames.filter((name) => !known.has(name))
  if (removed.length) result.set('已失效接口', removed.map((apiName) => ({ apiName, displayName: apiName, removed: true, descriptor: null })))
  return [...result.entries()].map(([name, apis]) => ({ name, apis }))
})
function update(change) { const value = { ...props.modelValue, ...change }; emit('update:modelValue', value); return value }
function parsedDraft() { return parseIntegritySymbols(draft.value, props.modelValue.pluginId) }
function notifyDraft() { emit('draft-change', parsedDraft()) }
function commitPendingSymbols() {
  const value = update({ symbols: [...new Set([...props.modelValue.symbols, ...parsedDraft()])] })
  draft.value = ''; emit('draft-change', []); return value
}
function removeSymbol(symbol) { update({ symbols: props.modelValue.symbols.filter((item) => item !== symbol) }) }
function toggleApi(apiName, checked) {
  const selected = new Set(props.modelValue.apiNames)
  if (checked) selected.add(apiName)
  else selected.delete(apiName)
  const known = new Set((props.capability?.apis ?? []).map((api) => api.apiName))
  const apiNames = [
    ...(props.capability?.apis ?? []).map((api) => api.apiName).filter((name) => selected.has(name)),
    ...props.modelValue.apiNames.filter((name) => !known.has(name) && selected.has(name)),
  ]
  update({ apiNames })
}
function allApis() { update({ apiNames: (props.capability?.apis ?? []).map((api) => api.apiName) }) }
watch(() => props.modelValue.pluginId, notifyDraft)
defineExpose({ commitPendingSymbols })
</script>

<template>
  <section class="integrity-section" aria-labelledby="integrity-form-title">
    <header><h2 id="integrity-form-title">创建检查</h2></header>
    <div class="form-grid">
      <label class="field"><span>数据源</span><select aria-label="数据源" :value="modelValue.pluginId" :disabled="disabled" aria-describedby="integrity-plugin-error" @change="update({ pluginId: $event.target.value })"><option value="">请选择数据源</option><option v-for="source in sources" :key="source.pluginId" :value="source.pluginId" :disabled="!source.enabled">{{ source.displayName }}</option></select><small id="integrity-plugin-error" class="error">{{ errors.pluginId }}</small></label>
      <div class="field symbols-field"><label for="integrity-symbols">股票代码</label><div class="symbol-entry"><textarea id="integrity-symbols" v-model="draft" aria-label="股票代码" aria-describedby="integrity-symbols-error" :disabled="disabled" rows="2" placeholder="如 600000.SH，可粘贴多个" @input="notifyDraft" /><button type="button" aria-label="添加" :disabled="disabled || !draft.trim()" @click="commitPendingSymbols">添加</button></div><div class="chips" aria-live="polite"><span v-for="symbol in modelValue.symbols" :key="symbol" class="chip">{{ symbol }}<button type="button" :aria-label="`移除 ${symbol}`" :disabled="disabled" @click="removeSymbol(symbol)">×</button></span><small>共 {{ modelValue.symbols.length + parsedDraft().filter((item) => !modelValue.symbols.includes(item)).length }} 个</small></div><small id="integrity-symbols-error" class="error">{{ errors.symbols }}</small></div>
      <label class="field" for="integrity-start-date"><span>开始日期</span><input id="integrity-start-date" type="date" aria-label="开始日期" aria-describedby="integrity-start-error" min="1000-01-01" max="9999-12-31" :value="modelValue.startDate" :disabled="disabled" @input="update({ startDate: $event.target.value })"><small id="integrity-start-error" class="error">{{ errors.startDate }}</small></label>
      <label class="field" for="integrity-end-date"><span>结束日期</span><input id="integrity-end-date" type="date" aria-label="结束日期" aria-describedby="integrity-end-error" min="1000-01-01" max="9999-12-31" :value="modelValue.endDate" :disabled="disabled" @input="update({ endDate: $event.target.value })"><small id="integrity-end-error" class="error">{{ errors.endDate || errors.range }}</small></label>
    </div>
    <div class="api-heading"><div><h3>检查接口</h3><p>已选择 {{ modelValue.apiNames.length }} 个</p></div><div class="compact-actions"><button type="button" aria-label="全选" :disabled="disabled || !capability?.apis?.length" @click="allApis">全选</button><button type="button" aria-label="清空" :disabled="disabled || !modelValue.apiNames.length" @click="update({ apiNames: [] })">清空</button></div></div>
    <p v-if="categoryError" class="notice" role="status">接口分类获取失败，全部接口仍可选择。<button type="button" :disabled="disabled" @click="emit('retry-categories')">重新获取分类</button></p>
    <div class="api-groups" aria-describedby="integrity-api-error"><fieldset v-for="group in groups" :key="group.name"><legend>{{ group.name }}</legend><label v-for="api in group.apis" :key="api.apiName" class="api-option" :class="{ invalid: api.removed }"><input type="checkbox" :checked="modelValue.apiNames.includes(api.apiName)" :disabled="disabled" @change="toggleApi(api.apiName, $event.target.checked)"><span><b>{{ api.displayName }}</b><code>{{ api.apiName }}</code><em v-if="api.removed">此接口已失效，请取消选择</em><em v-else-if="!api.descriptor">覆盖规则未实现</em></span><details v-if="api.descriptor"><summary>查看口径</summary><p>{{ api.descriptor.dateLabel }} · {{ api.descriptor.scopeKind }}</p></details></label></fieldset></div>
    <p id="integrity-api-error" class="error">{{ errors.apiNames || errors.units }}</p>
  </section>
</template>

<style scoped>
.integrity-section{min-width:0;padding:24px;border:1px solid var(--tensor-line);background:var(--tensor-surface)}header,.api-heading,.symbol-entry,.compact-actions,.chips{display:flex;align-items:center}header{gap:12px;margin-bottom:22px}h2,h3,p{margin:0}.section-kicker{color:var(--tensor-accent);font:12px monospace}.form-grid{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:18px}.field{display:grid;min-width:0;gap:7px;color:var(--tensor-text);font-size:13px}.symbols-field{grid-column:1/-1}select,input,textarea{min-width:0;padding:9px 11px;border:1px solid var(--tensor-line);border-radius:4px;background:var(--tensor-surface);color:var(--tensor-text);font:inherit}textarea{width:100%;resize:vertical}.symbol-entry{align-items:stretch;gap:8px}.symbol-entry button{flex-shrink:0;padding-inline:14px;white-space:nowrap}.symbol-entry button,.compact-actions button,.notice button{border:1px solid var(--tensor-line);border-radius:4px;background:var(--tensor-raised);color:var(--tensor-text);cursor:pointer}.chips{flex-wrap:wrap;gap:6px}.chip{display:inline-flex;gap:5px;padding:4px 7px;border-radius:4px;background:var(--tensor-raised);overflow-wrap:anywhere}.chip button{border:0;background:none;color:inherit;cursor:pointer}.api-heading{justify-content:space-between;gap:16px;margin-top:24px}.api-heading p{margin-top:4px;color:var(--tensor-muted);font-size:12px}.compact-actions{gap:8px}.compact-actions button{padding:6px 11px}.api-groups{display:grid;gap:12px;margin-top:14px}.api-groups fieldset{min-width:0;margin:0;padding:12px;border:1px solid var(--tensor-line)}.api-groups legend{padding:0 6px;color:var(--tensor-muted);font-size:12px}.api-option{display:grid;grid-template-columns:auto minmax(0,1fr);gap:9px;padding:9px 0;border-top:1px solid var(--tensor-line)}.api-option:first-of-type{border-top:0}.api-option span{min-width:0;overflow-wrap:anywhere}.api-option code,.api-option em{display:block;margin-top:3px;color:var(--tensor-muted);font-size:12px;font-style:normal}.api-option details{grid-column:2;font-size:12px;color:var(--tensor-muted)}.notice{margin-top:12px;color:var(--tensor-warning);overflow-wrap:anywhere}.error{min-height:1em;color:var(--tensor-error);overflow-wrap:anywhere}:disabled{cursor:not-allowed;opacity:.6}@media(max-width:680px){.integrity-section{padding:18px}.form-grid{grid-template-columns:minmax(0,1fr)}.symbols-field{grid-column:auto}.api-heading{align-items:flex-start;flex-direction:column}}
</style>
