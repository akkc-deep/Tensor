<script setup>
import { computed, ref } from 'vue'
import { ArrowDown, ArrowRight, Search } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  apis: { type: Array, required: true },
  disabled: { type: Boolean, default: false },
  sourceName: { type: String, default: '' },
})
const emit = defineEmits(['update:modelValue'])
const search = ref('')
const category = ref('')
const categories = computed(() => [...new Set(props.apis.map(api => api.category))])
const visibleApis = computed(() => {
  const query = search.value.trim().toLowerCase()
  return props.apis.filter(api =>
    (!category.value || api.category === category.value) &&
    (api.displayName.toLowerCase().includes(query) || api.apiName.toLowerCase().includes(query)),
  )
})

function select(apiName) {
  if (!props.disabled && apiName !== props.modelValue) emit('update:modelValue', apiName)
}
</script>

<template>
  <div class="api-select">
    <label class="catalog-search">
      <Search aria-hidden="true" />
      <input v-model="search" type="search" aria-label="搜索接口" placeholder="搜索名称或接口…" :disabled="disabled" />
    </label>
    <div class="category-filter">
      <select v-model="category" class="category-select" aria-label="接口分类" :disabled="disabled">
        <option value="">全部</option>
        <option v-for="item in categories" :key="item" :value="item">{{ item }}</option>
      </select>
      <ArrowDown aria-hidden="true" />
    </div>
    <div class="catalog-list" role="group" aria-label="数据接口">
      <button
        v-for="api in visibleApis"
        :key="api.apiName"
        type="button"
        :disabled="disabled"
        :class="{ selected: modelValue === api.apiName }"
        :aria-pressed="modelValue === api.apiName"
        @click="select(api.apiName)"
      >
        <span>{{ api.displayName }}<code>{{ api.apiName }}</code></span>
        <ArrowRight v-if="modelValue === api.apiName" aria-hidden="true" />
      </button>
      <p v-if="!visibleApis.length" class="catalog-empty" role="status">
        {{ apis.length ? '没有匹配的接口，请换个关键词或分类。' : '此数据源暂无接口。' }}
      </p>
    </div>
    <footer aria-live="polite">{{ visibleApis.length }} 个接口<span v-if="sourceName"> · {{ sourceName }}</span></footer>
  </div>
</template>

<style scoped>
.api-select { display: flex; flex: 1; min-height: 0; flex-direction: column; }
.catalog-search { display: flex; align-items: center; gap: 0.7rem; height: 3.6rem; margin: 0 1.4rem 0.8rem; padding: 0 1.2rem; border: 0.1rem solid var(--tensor-line); border-radius: 0.6rem; background: var(--tensor-surface); }
.catalog-search svg { width: 1.4rem; height: 1.4rem; flex-shrink: 0; color: var(--tensor-muted); }
.catalog-search input { width: 100%; min-width: 0; height: 100%; padding: 0; border: 0; background: transparent; color: var(--tensor-text); font-size: 1.4rem; caret-color: var(--tensor-accent); }
.catalog-search input::placeholder { color: var(--tensor-muted); }
.catalog-search input:focus { outline: 0; }
.category-filter { position: relative; margin: 0 1.4rem 1.2rem; }
.category-filter > svg { position: absolute; top: 50%; right: 1.3rem; width: 1.4rem; height: 1.4rem; transform: translateY(-50%); color: var(--tensor-muted); pointer-events: none; }
.category-select { appearance: none; width: 100%; min-width: 0; height: 3.6rem; padding: 0 3.4rem 0 1.2rem; border: 0.1rem solid var(--tensor-line); border-radius: 0.6rem; background: var(--tensor-surface); color: var(--tensor-text); font-size: 1.4rem; }
.catalog-list { max-height: 41.7rem; overflow: auto; padding: 0 0.8rem 0.8rem; scrollbar-width: thin; scrollbar-color: var(--tensor-line) transparent; }
.catalog-list button { display: flex; align-items: center; justify-content: space-between; gap: 0.8rem; width: 100%; padding: 1rem 1.2rem; border: 0; border-radius: 0.5rem; background: transparent; color: var(--tensor-text); text-align: left; font-size: 1.4rem; cursor: pointer; }
.catalog-list button span { min-width: 0; overflow-wrap: anywhere; }
.catalog-list code { display: block; margin-top: 0.2rem; color: var(--tensor-muted); font: 1.2rem 'SFMono-Regular', Consolas, monospace; }
.catalog-list button:hover:enabled { background: var(--tensor-raised); }
.catalog-list button.selected { background: var(--tensor-accent-bg); color: var(--tensor-accent); }
.catalog-list button:focus-visible { outline: 0.2rem solid var(--tensor-accent); outline-offset: -0.2rem; }
.catalog-list svg { width: 1.2rem; height: 1.2rem; flex-shrink: 0; }
:disabled { cursor: not-allowed; opacity: .65; }
footer { margin-top: auto; padding: 1.3rem 1.8rem; border-top: 0.1rem solid var(--tensor-line); color: var(--tensor-muted); font-size: 1.2rem; overflow-wrap: anywhere; }
.catalog-empty { margin: 0; padding: 2rem 1rem; color: var(--tensor-muted); font-size: 1.2rem; }
</style>
