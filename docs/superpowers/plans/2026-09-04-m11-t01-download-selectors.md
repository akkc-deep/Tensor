# M11-T01 Download Selectors Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build controlled, metadata-driven data-source and API selectors plus an API description panel for the download page.

**Architecture:** Three focused Vue SFCs consume the normalized M10 descriptors and expose only controlled selection events or presentational output. Data sources retain the single-source default and public unavailable reason; APIs are grouped and searched directly from descriptor fields, so the current seven groups can later become eight without an API-name branch.

**Tech Stack:** Node.js `24.15.0`, Vue `3.5.42`, Element Plus `2.14.5`, Vitest `4.1.11`, Vue Test Utils `2.5.0`, jsdom `30.0.1`.

**Spec:** `docs/task-designs/M11-T01-design.md`

## Global Constraints

- Create only the three SFCs and three colocated specs named by the design; do not modify API modules, common components, utilities, dependencies, configuration, router, layout, views, styles, Java, YAML, OpenAPI, or PRD.
- Consume caller-provided M10 `DataSourceSummary[]` and `ApiDescriptor[]`; do not call `listDataSources`, `listApis`, Axios, fetch, or any other network boundary.
- Emit only `update:modelValue`; keep metadata loading, error interpretation, downstream resets, requests, retries, parameters, actions, and results in later tasks.
- Group by `ApiDescriptor.category` exactly and preserve first-category and item order; the current 49 descriptors remain seven groups, including one combined `互联互通与转融通` group.
- Do not embed the 49 API list, branch on `apiName`, translate or reorder categories, or automatically select an API.
- Render dynamic values only through Vue text interpolation; never use `v-html`, `innerHTML`, Element Plus internal selectors, or credential content.
- Use Node.js `24.15.0`; the production build may emit only the previously approved Element Plus chunk-size warning.
- Commit the six implementation files together as `feat(ui): add download source and API selectors`.

---

### Task 1: Add the three download selectors through one strict RED/GREEN cycle

**Files:**
- Create: `control-plane/src/components/download/DataSourceSelect.spec.js`
- Create: `control-plane/src/components/download/ApiSelect.spec.js`
- Create: `control-plane/src/components/download/ApiDescription.spec.js`
- Create: `control-plane/src/components/download/DataSourceSelect.vue`
- Create: `control-plane/src/components/download/ApiSelect.vue`
- Create: `control-plane/src/components/download/ApiDescription.vue`

**Interfaces:**
- Consumes: M10 `DataSourceSummary` objects with `pluginId/displayName/description/enabled/credentialConfigured/downloadAvailable/unavailableReason`.
- Consumes: M10 `ApiDescriptor` objects with `apiName/displayName/category/queryMode/parameters`.
- Produces: `DataSourceSelect` props `modelValue: string`, `sources: DataSourceSummary[]`, `disabled: boolean`; emits `update:modelValue(pluginId: string)`.
- Produces: `ApiSelect` props `modelValue: string`, `apis: ApiDescriptor[]`, `disabled: boolean`; emits `update:modelValue(apiName: string)`.
- Produces: `ApiDescription` prop `api: ApiDescriptor | null`; emits nothing.

- [ ] **Step 1: Confirm the approved design and clean Node 24 baseline**

Run from the repository root:

```bash
git status --short
git branch --show-current
sed -n '1,240p' docs/task-designs/M11-T01-design.md
sed -n '25,39p' docs/superpowers/plans/tensor-modules/M11-download-ui.md
export PATH="/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin:$PATH"
cd control-plane
node --version
npm run test:unit -- --run
npm run build
```

Expected: the worktree is empty; branch is `main`; the design and task card both preserve the approved seven-group metadata decision; Node prints `v24.15.0`; the unchanged baseline is 6 files / 34 tests with zero failures; build exits 0 with only the approved Element Plus chunk-size warning.

- [ ] **Step 2: Write all ten failing component tests**

Create `control-plane/src/components/download/DataSourceSelect.spec.js` with exactly:

```js
import { mount } from '@vue/test-utils'
import { ElOption, ElSelect } from 'element-plus'

import DataSourceSelect from './DataSourceSelect.vue'

function source(overrides = {}) {
  return {
    pluginId: 'tushare_pro',
    displayName: 'Tushare Pro',
    description: 'Tushare Pro 证券数据源',
    enabled: true,
    credentialConfigured: true,
    downloadAvailable: true,
    unavailableReason: null,
    ...overrides,
  }
}

describe('DataSourceSelect', () => {
  it('shows a visible label and defaults an empty single source', () => {
    const wrapper = mount(DataSourceSelect, {
      props: { modelValue: '', sources: [source()] },
    })

    expect(wrapper.get('label[for="download-data-source"]').text()).toBe(
      '数据源',
    )
    expect(wrapper.getComponent(ElOption).props()).toMatchObject({
      label: 'Tushare Pro',
      value: 'tushare_pro',
      disabled: false,
    })
    expect(wrapper.emitted('update:modelValue')).toEqual([['tushare_pro']])
  })

  it('does not default multiple sources or overwrite an existing value', async () => {
    const sources = [
      source({ pluginId: 'fixture', displayName: 'Fixture' }),
      source(),
    ]
    const wrapper = mount(DataSourceSelect, {
      props: { modelValue: '', sources },
    })

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()

    await wrapper.setProps({ modelValue: 'fixture' })
    expect(wrapper.getComponent(ElSelect).props('modelValue')).toBe('fixture')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('disables unavailable interaction and renders its reason as text', () => {
    const unavailable = source({
      credentialConfigured: false,
      downloadAvailable: false,
      unavailableReason: '<strong>Credentials missing</strong>',
    })
    const wrapper = mount(DataSourceSelect, {
      props: { modelValue: '', sources: [unavailable], disabled: true },
    })

    expect(wrapper.getComponent(ElSelect).props('disabled')).toBe(true)
    expect(wrapper.getComponent(ElOption).props('disabled')).toBe(true)
    const reason = wrapper.get('[role="status"]')
    expect(reason.text()).toBe('<strong>Credentials missing</strong>')
    expect(reason.find('strong').exists()).toBe(false)
  })
})
```

Create `control-plane/src/components/download/ApiSelect.spec.js` with exactly:

```js
import { flushPromises, mount } from '@vue/test-utils'
import { ElOption, ElOptionGroup, ElSelect } from 'element-plus'
import { nextTick } from 'vue'

import ApiSelect from './ApiSelect.vue'

const CATEGORY_COUNTS = [
  ['basic_organization', 11],
  ['行情与估值', 7],
  ['交易与资金', 6],
  ['互联互通与转融通', 6],
  ['财务与披露', 9],
  ['公司行动', 3],
  ['股东与治理', 7],
]

function descriptor(apiName, displayName, category = '行情与估值') {
  return {
    apiName,
    displayName,
    category,
    queryMode: 'trade_date',
    parameters: [],
  }
}

function currentApis() {
  let index = 0
  return CATEGORY_COUNTS.flatMap(([category, count]) =>
    Array.from({ length: count }, () => {
      index += 1
      if (index === 1) return descriptor('daily', '日线行情', category)
      if (index === 2) return descriptor('weekly', '周线行情', category)
      return descriptor(`api_${index}`, `接口 ${index}`, category)
    }),
  )
}

function filter(wrapper, query) {
  wrapper.getComponent(ElSelect).props('filterMethod')(query)
  return nextTick()
}

describe('ApiSelect', () => {
  it('groups all 49 options by the current seven metadata categories', () => {
    const apis = currentApis()
    const wrapper = mount(ApiSelect, {
      props: { modelValue: '', apis },
    })

    expect(
      wrapper.findAllComponents(ElOptionGroup).map((group) =>
        group.props('label'),
      ),
    ).toEqual(CATEGORY_COUNTS.map(([category]) => category))
    const options = wrapper.findAllComponents(ElOption)
    expect(options).toHaveLength(49)
    expect(options.map((option) => option.props('value'))).toEqual(
      apis.map(({ apiName }) => apiName),
    )
    expect(new Set(options.map((option) => option.props('value'))).size).toBe(
      49,
    )
  })

  it('searches API names case-insensitively', async () => {
    const wrapper = mount(ApiSelect, {
      props: { modelValue: '', apis: currentApis() },
    })

    await filter(wrapper, '  DAI  ')

    expect(
      wrapper.findAllComponents(ElOption).map((option) => option.props('value')),
    ).toEqual(['daily'])
  })

  it('searches display names and exposes the fixed no-match text', async () => {
    const wrapper = mount(ApiSelect, {
      props: { modelValue: '', apis: currentApis() },
    })

    await filter(wrapper, '周线')
    expect(wrapper.findAllComponents(ElOption)).toHaveLength(1)
    expect(wrapper.getComponent(ElOption).props('value')).toBe('weekly')

    await filter(wrapper, '不存在')
    expect(wrapper.findAllComponents(ElOption)).toHaveLength(0)
    expect(wrapper.getComponent(ElSelect).props('noMatchText')).toBe(
      '无匹配接口',
    )
  })

  it('restores original options without changing selection or descriptors', async () => {
    const apis = currentApis()
    const snapshot = structuredClone(apis)
    const wrapper = mount(ApiSelect, {
      props: { modelValue: 'daily', apis },
    })

    await filter(wrapper, 'weekly')
    await filter(wrapper, '')

    expect(wrapper.findAllComponents(ElOption)).toHaveLength(49)
    expect(wrapper.getComponent(ElSelect).props('modelValue')).toBe('daily')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(apis).toEqual(snapshot)
  })

  it('supports keyboard selection and locks interaction when disabled', async () => {
    const wrapper = mount(ApiSelect, {
      attachTo: document.body,
      props: {
        modelValue: '',
        apis: [
          descriptor('daily', '日线行情'),
          descriptor('weekly', '周线行情'),
        ],
      },
    })

    try {
      const combobox = wrapper.get('input[role="combobox"]')
      combobox.element.focus()
      expect(document.activeElement).toBe(combobox.element)

      await combobox.trigger('keydown', { key: 'ArrowDown' })
      await flushPromises()
      await combobox.trigger('keydown', { key: 'Enter' })
      await flushPromises()
      expect(wrapper.emitted('update:modelValue')).toEqual([['daily']])

      await wrapper.setProps({ disabled: true })
      expect(wrapper.getComponent(ElSelect).props('disabled')).toBe(true)
      expect(wrapper.get('input[role="combobox"]').attributes('disabled')).toBe(
        '',
      )
    } finally {
      wrapper.unmount()
    }
  })
})
```

Create `control-plane/src/components/download/ApiDescription.spec.js` with exactly:

```js
import { mount } from '@vue/test-utils'

import ApiDescription from './ApiDescription.vue'

function descriptor(queryMode = 'trade_date') {
  return {
    apiName: 'daily',
    displayName: '<strong>日线行情</strong>',
    category: '<em>行情与估值</em>',
    queryMode,
    parameters: [],
  }
}

describe('ApiDescription', () => {
  it('renders no description without a selected API', () => {
    const wrapper = mount(ApiDescription, { props: { api: null } })

    expect(wrapper.find('section').exists()).toBe(false)
  })

  it('renders descriptor text and every query-mode label safely', async () => {
    const wrapper = mount(ApiDescription, {
      props: { api: descriptor() },
    })

    expect(wrapper.get('h2').text()).toBe('接口说明')
    expect(wrapper.get('.api-description__display-name').text()).toBe(
      '<strong>日线行情</strong>',
    )
    expect(wrapper.get('.api-description__api-name').text()).toBe('daily')
    expect(wrapper.get('.api-description__category').text()).toBe(
      '<em>行情与估值</em>',
    )
    expect(wrapper.find('.api-description strong').exists()).toBe(false)
    expect(wrapper.find('.api-description em').exists()).toBe(false)

    for (const [queryMode, label] of [
      ['trade_date', '交易日'],
      ['ann_date', '公告日'],
      ['snapshot', '快照'],
      ['date_range', '日期范围'],
      ['future_mode', 'future_mode'],
    ]) {
      await wrapper.setProps({ api: descriptor(queryMode) })
      expect(wrapper.get('.api-description__query-mode').text()).toBe(label)
    }
  })
})
```

- [ ] **Step 3: Run the focused suites to verify strict RED**

Run from `control-plane`:

```bash
npm run test:unit -- --run \
  src/components/download/DataSourceSelect.spec.js \
  src/components/download/ApiSelect.spec.js \
  src/components/download/ApiDescription.spec.js
```

Expected: exit non-zero during collection because the three imported target SFCs do not exist. Every unresolved path must be one of `./DataSourceSelect.vue`, `./ApiSelect.vue`, or `./ApiDescription.vue`; there must be no test syntax, dependency, Element Plus, setup, or existing-suite failure. Do not commit the RED checkpoint.

- [ ] **Step 4: Implement the controlled data-source selector**

Create `control-plane/src/components/download/DataSourceSelect.vue` with exactly:

```vue
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
```

- [ ] **Step 5: Implement metadata grouping, search, and API selection**

Create `control-plane/src/components/download/ApiSelect.vue` with exactly:

```vue
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
```

- [ ] **Step 6: Implement the text-only API description**

Create `control-plane/src/components/download/ApiDescription.vue` with exactly:

```vue
<script setup>
const props = defineProps({
  api: { type: Object, default: null },
})

const QUERY_MODE_LABELS = {
  trade_date: '交易日',
  ann_date: '公告日',
  snapshot: '快照',
  date_range: '日期范围',
}

function queryModeLabel(queryMode) {
  return QUERY_MODE_LABELS[queryMode] ?? queryMode
}
</script>

<template>
  <section
    v-if="props.api"
    class="api-description"
    aria-labelledby="api-description-title"
  >
    <h2 id="api-description-title">接口说明</h2>
    <dl>
      <div>
        <dt>中文说明</dt>
        <dd class="api-description__display-name">{{ props.api.displayName }}</dd>
      </div>
      <div>
        <dt>接口名</dt>
        <dd><code class="api-description__api-name">{{ props.api.apiName }}</code></dd>
      </div>
      <div>
        <dt>分类</dt>
        <dd class="api-description__category">{{ props.api.category }}</dd>
      </div>
      <div>
        <dt>查询方式</dt>
        <dd class="api-description__query-mode">
          {{ queryModeLabel(props.api.queryMode) }}
        </dd>
      </div>
    </dl>
  </section>
</template>

<style scoped>
.api-description {
  padding: 16px;
  border: 1px solid var(--el-border-color-light, #e4e7ed);
  border-radius: 8px;
}

.api-description h2 {
  margin: 0 0 12px;
  font-size: 18px;
}

.api-description dl {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin: 0;
  gap: 16px;
}

.api-description dt {
  color: var(--el-text-color-secondary, #909399);
}

.api-description dd {
  margin: 4px 0 0;
}
</style>
```

- [ ] **Step 7: Run the focused suites and verify GREEN**

Run from `control-plane`:

```bash
npm run test:unit -- --run \
  src/components/download/DataSourceSelect.spec.js \
  src/components/download/ApiSelect.spec.js \
  src/components/download/ApiDescription.spec.js
```

Expected: 3 files / 10 tests pass; the 49-option fixture produces the exact seven approved category groups; both search fields, single-source default, unavailable reason, keyboard selection, disabled state, query labels, and text-only rendering pass without warnings.

- [ ] **Step 8: Run full regression and production build**

Run from `control-plane`:

```bash
npm run test:unit -- --run
npm run build
```

Expected: 9 files / 44 tests pass with zero failures; build exits 0 and emits no warning except the approved Element Plus chunk-size message.

- [ ] **Step 9: Verify scope, safe rendering, and the complete diff**

Run from the repository root:

```bash
git diff --check
git status --short --untracked-files=all -- control-plane
rg -n 'v-html|innerHTML|axios|fetch\(|listDataSources|listApis|ApiError|ClientError|Authorization|token|password' \
  control-plane/src/components/download/DataSourceSelect.vue \
  control-plane/src/components/download/ApiSelect.vue \
  control-plane/src/components/download/ApiDescription.vue
git diff -- control-plane/package.json control-plane/package-lock.json \
  control-plane/vite.config.js control-plane/vitest.config.js \
  control-plane/src/api control-plane/src/components/common \
  control-plane/src/utils control-plane/src/router control-plane/src/layouts \
  control-plane/src/views control-plane/src/style.css
git diff -- control-plane/src/components/download
```

Expected: format check exits 0; status shows exactly the six new design files; the forbidden scan has no output and exits 1; protected paths have no diff; the complete component diff matches Steps 2 and 4–6 with no API-name branch, category map, request, business state, hidden credential, or unrelated abstraction.

- [ ] **Step 10: Create the single approved implementation commit**

Run from the repository root:

```bash
git add control-plane/src/components/download/DataSourceSelect.spec.js \
  control-plane/src/components/download/ApiSelect.spec.js \
  control-plane/src/components/download/ApiDescription.spec.js \
  control-plane/src/components/download/DataSourceSelect.vue \
  control-plane/src/components/download/ApiSelect.vue \
  control-plane/src/components/download/ApiDescription.vue
git diff --cached --check
git diff --cached --name-status
git commit -m "feat(ui): add download source and API selectors"
```

Expected: staged format check exits 0; name/status shows exactly six added files; commit succeeds with the exact approved message.

- [ ] **Step 11: Re-run result gates from committed state and request review**

Run from the repository root with Node.js 24.15.0 first on PATH:

```bash
git status --short
git show --format='%h %s' --name-status HEAD
cd control-plane
npm run test:unit -- --run \
  src/components/download/DataSourceSelect.spec.js \
  src/components/download/ApiSelect.spec.js \
  src/components/download/ApiDescription.spec.js
npm run test:unit -- --run
npm run build
cd ..
git diff HEAD^ HEAD --check
git status --short
```

Expected: both status outputs are empty; `git show` reports `feat(ui): add download source and API selectors` and exactly six added files; focused 10/10 and full 44/44 pass; build exits 0 with only the approved chunk-size warning; committed diff check exits 0. Request an independent read-only review against `docs/task-designs/M11-T01-design.md`; resolve every Critical or Important issue before completing M11-T01.
