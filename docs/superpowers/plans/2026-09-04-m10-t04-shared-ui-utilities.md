# M10-T04 Shared UI Utilities Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build strict download-date conversion, precision-safe display and validation helpers, plus accessible async-state and field-error components for the M11 and M12 pages.

**Architecture:** Three focused ESM utility modules provide pure date, display, and validation functions without business state or network access. Two focused Vue SFCs render caller-owned state text with fixed native ARIA semantics; two colocated Vitest suites drive all five production modules through one strict RED/GREEN cycle.

**Tech Stack:** Node.js `24.15.0`, JavaScript ESM, Vue `3.5.42`, Vitest `4.1.11`, Vue Test Utils `2.5.0`, jsdom `30.0.1`, platform `Intl.DateTimeFormat`.

**Spec:** `docs/task-designs/M10-T04-design.md`

## Global Constraints

- Work only in the seven new files named by the spec; do not modify dependencies, configuration, router, layout, views, API clients, styles, Java, SQL, or contracts.
- Export only `toApiDate`, `toApiMonth`, `formatDate` from `date.js`; only `formatIngestedAt`, `formatCell` from `format.js`; and only `hasValue`, `matchesPattern`, `isRangeOrdered` from `validation.js`.
- Accept only strict string `YYYY-MM-DD`/`YYYY-MM` values for download conversion; never accept `Date` or convert M12 ISO query filters to compact values.
- Map only `null` and `undefined` cells to `--`; preserve numeric `0`, the empty string, and every `DECIMAL`/`LONG` string without parsing, rounding, computation, sorting, or localization.
- Format valid ingestion instants as `YYYY-MM-DD HH:mm:ss`; default or fall back to `Asia/Shanghai`, and preserve invalid inputs.
- Limit `AsyncStatePanel` to `INITIAL | LOADING | EMPTY | FAILURE`; keep success content, business state, requests, `ApiError` interpretation, retry rules, `aria-describedby`, and focus movement in later callers.
- Render every message as text through Vue interpolation; do not use `v-html`, `innerHTML`, Element Plus internals, or third-party date/validation libraries.
- Use Node.js `24.15.0`; the production build must exit 0 and may contain only the previously approved Element Plus chunk-size warning.
- Preserve the single approved implementation commit `feat(ui): add shared display and accessibility utilities` with exactly seven new files, so intermediate RED/GREEN checkpoints remain uncommitted until final verification.

---

### Task 1: Add the complete shared utilities and components through one strict RED/GREEN cycle

**Files:**
- Create: `control-plane/src/utils/format.spec.js`
- Create: `control-plane/src/components/common/AsyncStatePanel.spec.js`
- Create: `control-plane/src/utils/date.js`
- Create: `control-plane/src/utils/validation.js`
- Create: `control-plane/src/utils/format.js`
- Create: `control-plane/src/components/common/AsyncStatePanel.vue`
- Create: `control-plane/src/components/common/FieldError.vue`

**Interfaces:**
- Consumes: native JavaScript strings and values, `DatasetColumn`-shaped `{ name, logicalType }` metadata from `control-plane/src/api/datasets.js`, platform `Intl.DateTimeFormat`, Vue props/slots, Vitest, and Vue Test Utils.
- Produces: `toApiDate(value): string | null`, `toApiMonth(value): string | null`, `formatDate(value): unknown`, `formatIngestedAt(value, timeZone?): unknown`, `formatCell(value, column, timeZone?): unknown`, `hasValue(value): boolean`, `matchesPattern(value, pattern): boolean`, and `isRangeOrdered(start, end): boolean`.
- Produces: `AsyncStatePanel` with required string props `state`, `title`, `message`, allowed states `INITIAL | LOADING | EMPTY | FAILURE`, and optional `actions` slot.
- Produces: `FieldError` with required string props `id`, `message`; an empty message renders no error element and a non-empty message renders text with `role="alert"`.

- [ ] **Step 1: Confirm the approved inputs, clean baseline, and Node runtime**

Run from the repository root:

```bash
git status --short
git branch --show-current
sed -n '1,260p' docs/task-designs/M10-T04-design.md
sed -n '1,220p' docs/task-handoffs/M10-T04-handoff.md
export PATH="/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin:$PATH"
cd control-plane
node --version
npm run test:unit -- --run
npm run build
```

Expected: worktree output is empty; branch is `main`; the design and handoff are readable; Node prints `v24.15.0`; the unchanged baseline runs 4 files / 19 tests with zero failures; the build exits 0 with only the approved Element Plus chunk-size warning.

- [ ] **Step 2: Write all nine failing utility tests**

Create `control-plane/src/utils/format.spec.js` with exactly this content:

```js
import { formatDate, toApiDate, toApiMonth } from './date.js'
import { formatCell, formatIngestedAt } from './format.js'
import { hasValue, isRangeOrdered, matchesPattern } from './validation.js'

describe('date utilities', () => {
  it('converts strict calendar dates to compact download values', () => {
    expect(toApiDate('2026-09-04')).toBe('20260904')
    expect(toApiDate('2024-02-29')).toBe('20240229')
  })

  it('rejects empty, non-string, loose, and nonexistent dates', () => {
    for (const value of [
      null,
      undefined,
      new Date('2026-09-04T00:00:00Z'),
      '',
      ' 2026-09-04 ',
      '2026-9-04',
      '2026-02-29',
      '2026-13-01',
    ]) {
      expect(toApiDate(value)).toBeNull()
    }
  })

  it('converts only strict valid months', () => {
    expect(toApiMonth('2026-09')).toBe('202609')

    for (const value of [null, undefined, new Date(), '', '2026-9', '2026-00', '2026-13']) {
      expect(toApiMonth(value)).toBeNull()
    }
  })

  it('keeps valid display dates and preserves invalid values', () => {
    expect(formatDate('2026-09-04')).toBe('2026-09-04')
    expect(formatDate('2026-02-29')).toBe('2026-02-29')
    expect(formatDate(20260904)).toBe(20260904)
  })
})

describe('display utilities', () => {
  it('formats ingestion time in Asia/Shanghai to whole seconds by default', () => {
    expect(formatIngestedAt('2026-08-25T02:30:15.123Z')).toBe(
      '2026-08-25 10:30:15',
    )
  })

  it('supports an explicit zone, falls back from a bad zone, and preserves bad time values', () => {
    expect(formatIngestedAt('2026-08-25T02:30:15.123Z', 'UTC')).toBe(
      '2026-08-25 02:30:15',
    )
    expect(
      formatIngestedAt('2026-08-25T02:30:15.123Z', 'Not/A_Zone'),
    ).toBe('2026-08-25 10:30:15')
    expect(formatIngestedAt('not-a-time')).toBe('not-a-time')

    const nonString = { value: '2026-08-25T02:30:15.123Z' }
    expect(formatIngestedAt(nonString)).toBe(nonString)
  })

  it('maps only nullish cells to the placeholder', () => {
    expect(formatCell(null, {})).toBe('--')
    expect(formatCell(undefined, {})).toBe('--')
    expect(formatCell(0, {})).toBe(0)
    expect(formatCell('', {})).toBe('')
  })

  it('preserves precise numeric strings and dispatches date and ingestion columns', () => {
    const decimal = '12345678901234567890.123456789012345678'
    const long = '9223372036854775807'

    expect(formatCell(decimal, { logicalType: 'DECIMAL' })).toBe(decimal)
    expect(formatCell(long, { logicalType: 'LONG' })).toBe(long)
    expect(formatCell('2026-09-04', { logicalType: 'DATE' })).toBe(
      '2026-09-04',
    )
    expect(
      formatCell(
        '2026-08-25T02:30:15.123Z',
        { name: 'ingested_at', logicalType: 'DATE' },
        'UTC',
      ),
    ).toBe('2026-08-25 02:30:15')
  })
})

describe('validation utilities', () => {
  it('handles required values, metadata patterns, and ordered optional ranges', () => {
    expect(hasValue(null)).toBe(false)
    expect(hasValue(undefined)).toBe(false)
    expect(hasValue('')).toBe(false)
    expect(hasValue('   ')).toBe(false)
    expect(hasValue(0)).toBe(true)
    expect(hasValue(false)).toBe(true)

    expect(matchesPattern('000001.SZ', '^[0-9]{6}\\.(SZ|SH)$')).toBe(true)
    expect(matchesPattern('000001', '^[0-9]{6}\\.(SZ|SH)$')).toBe(false)
    expect(matchesPattern('anything', '[')).toBe(false)
    expect(matchesPattern('anything', '')).toBe(false)
    expect(matchesPattern(1, '^[0-9]+$')).toBe(false)

    expect(isRangeOrdered('2026-09-01', '2026-09-04')).toBe(true)
    expect(isRangeOrdered('2026-09-04', '2026-09-01')).toBe(false)
    expect(isRangeOrdered('', '2026-09-04')).toBe(true)
    expect(isRangeOrdered('2026-09-01', '')).toBe(true)
    expect(isRangeOrdered(1, 2)).toBe(false)
  })
})
```

- [ ] **Step 3: Write all six failing component tests**

Create `control-plane/src/components/common/AsyncStatePanel.spec.js` with exactly this content:

```js
import { mount } from '@vue/test-utils'
import { h } from 'vue'

import AsyncStatePanel from './AsyncStatePanel.vue'
import FieldError from './FieldError.vue'

function mountPanel(state, options = {}) {
  return mount(AsyncStatePanel, {
    props: {
      state,
      title: `${state} title`,
      message: `${state} message`,
    },
    ...options,
  })
}

describe('AsyncStatePanel', () => {
  it('renders INITIAL guidance without a live region', () => {
    const wrapper = mountPanel('INITIAL')

    expect(wrapper.get('h2').text()).toBe('INITIAL title')
    expect(wrapper.get('p').text()).toBe('INITIAL message')
    expect(wrapper.get('section').attributes('role')).toBeUndefined()
    expect(wrapper.get('section').attributes('aria-live')).toBeUndefined()
    expect(wrapper.find('.async-state-panel__actions').exists()).toBe(false)
  })

  it('announces LOADING politely', () => {
    const panel = mountPanel('LOADING').get('section')

    expect(panel.attributes('role')).toBe('status')
    expect(panel.attributes('aria-live')).toBe('polite')
  })

  it('announces EMPTY politely', () => {
    const panel = mountPanel('EMPTY').get('section')

    expect(panel.attributes('role')).toBe('status')
    expect(panel.attributes('aria-live')).toBe('polite')
  })

  it('uses alert semantics for FAILURE and renders caller actions', () => {
    const wrapper = mountPanel('FAILURE', {
      slots: {
        actions: () => h('button', { type: 'button' }, '重试'),
      },
    })
    const panel = wrapper.get('section')

    expect(panel.attributes('role')).toBe('alert')
    expect(panel.attributes('aria-live')).toBeUndefined()
    expect(wrapper.get('.async-state-panel__actions button').text()).toBe('重试')
  })
})

describe('FieldError', () => {
  it('renders no error element for an empty message', () => {
    const wrapper = mount(FieldError, {
      props: { id: 'trade-date-error', message: '' },
    })

    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('renders a non-empty message as alert text with the supplied id', () => {
    const wrapper = mount(FieldError, {
      props: {
        id: 'trade-date-error',
        message: '<strong>日期无效</strong>',
      },
    })
    const error = wrapper.get('[role="alert"]')

    expect(error.attributes('id')).toBe('trade-date-error')
    expect(error.text()).toBe('<strong>日期无效</strong>')
    expect(error.find('strong').exists()).toBe(false)
  })
})
```

- [ ] **Step 4: Run the focused suites to verify strict RED**

Run from `control-plane`:

```bash
npm run test:unit -- --run src/utils/format.spec.js src/components/common/AsyncStatePanel.spec.js
```

Expected: exit non-zero during collection because the imported target production modules do not exist. Each reported unresolved path must be one of `./date.js`, `./format.js`, `./validation.js`, `./AsyncStatePanel.vue`, or `./FieldError.vue`; there must be no test syntax, dependency, SFC transform, or setup failure. Do not commit the RED checkpoint.

- [ ] **Step 5: Implement strict date conversion and no-timezone date display**

Create `control-plane/src/utils/date.js` with exactly this content:

```js
const DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/
const MONTH_PATTERN = /^(\d{4})-(\d{2})$/

function isLeapYear(year) {
  return year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0)
}

function parseDate(value) {
  if (typeof value !== 'string') return null

  const match = DATE_PATTERN.exec(value)
  if (!match) return null

  const year = +match[1]
  const month = +match[2]
  const day = +match[3]
  const days = [31, isLeapYear(year) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]

  if (year === 0 || month < 1 || month > 12) return null
  if (day < 1 || day > days[month - 1]) return null

  return match
}

export function toApiDate(value) {
  const match = parseDate(value)
  return match ? `${match[1]}${match[2]}${match[3]}` : null
}

export function toApiMonth(value) {
  if (typeof value !== 'string') return null

  const match = MONTH_PATTERN.exec(value)
  if (!match) return null

  const year = +match[1]
  const month = +match[2]
  return year > 0 && month >= 1 && month <= 12 ? `${match[1]}${match[2]}` : null
}

export function formatDate(value) {
  const compact = toApiDate(value)
  return compact
    ? `${compact.slice(0, 4)}-${compact.slice(4, 6)}-${compact.slice(6)}`
    : value
}
```

- [ ] **Step 6: Implement the three safe validation primitives**

Create `control-plane/src/utils/validation.js` with exactly this content:

```js
export function hasValue(value) {
  if (value === null || value === undefined) return false
  return typeof value !== 'string' || value.trim().length > 0
}

export function matchesPattern(value, pattern) {
  if (typeof value !== 'string' || typeof pattern !== 'string' || !pattern) {
    return false
  }

  try {
    return new RegExp(pattern).test(value)
  } catch {
    return false
  }
}

export function isRangeOrdered(start, end) {
  if (!hasValue(start) || !hasValue(end)) return true
  return typeof start === 'string' && typeof end === 'string' && start <= end
}
```

- [ ] **Step 7: Implement ingestion-time and cell formatting**

Create `control-plane/src/utils/format.js` with exactly this content:

```js
import { formatDate } from './date.js'

const DEFAULT_TIME_ZONE = 'Asia/Shanghai'

function formatter(timeZone) {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  })
}

function formatterFor(timeZone) {
  try {
    return formatter(timeZone)
  } catch {
    return formatter(DEFAULT_TIME_ZONE)
  }
}

export function formatIngestedAt(value, timeZone = DEFAULT_TIME_ZONE) {
  if (typeof value !== 'string') return value

  const instant = new Date(value)
  if (Number.isNaN(instant.getTime())) return value

  const parts = Object.fromEntries(
    formatterFor(timeZone)
      .formatToParts(instant)
      .filter(({ type }) => type !== 'literal')
      .map(({ type, value: part }) => [type, part]),
  )

  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`
}

export function formatCell(value, column, timeZone = DEFAULT_TIME_ZONE) {
  if (value === null || value === undefined) return '--'
  if (column?.name === 'ingested_at') {
    return formatIngestedAt(value, timeZone)
  }
  if (column?.logicalType === 'DATE') return formatDate(value)
  return value
}
```

- [ ] **Step 8: Run the utility suite and verify its isolated GREEN**

Run from `control-plane`:

```bash
npm run test:unit -- --run src/utils/format.spec.js
```

Expected: 1 file / 9 tests pass; there are no warnings or network calls. Leave the three production utilities and utility test uncommitted so the final implementation commit remains atomic.

- [ ] **Step 9: Implement the closed async-state panel**

Create `control-plane/src/components/common/AsyncStatePanel.vue` with exactly this content:

```vue
<script setup>
import { computed } from 'vue'

const props = defineProps({
  state: {
    type: String,
    required: true,
    validator: (value) =>
      ['INITIAL', 'LOADING', 'EMPTY', 'FAILURE'].includes(value),
  },
  title: { type: String, required: true },
  message: { type: String, required: true },
})

const role = computed(() => {
  if (props.state === 'FAILURE') return 'alert'
  if (props.state === 'LOADING' || props.state === 'EMPTY') return 'status'
  return undefined
})

const live = computed(() =>
  props.state === 'LOADING' || props.state === 'EMPTY' ? 'polite' : undefined,
)
</script>

<template>
  <section class="async-state-panel" :role="role" :aria-live="live">
    <h2 class="async-state-panel__title">{{ title }}</h2>
    <p class="async-state-panel__message">{{ message }}</p>
    <div v-if="$slots.actions" class="async-state-panel__actions">
      <slot name="actions" />
    </div>
  </section>
</template>

<style scoped>
.async-state-panel {
  padding: 24px;
  border: 1px solid var(--el-border-color-light, #e4e7ed);
  border-radius: 8px;
}

.async-state-panel__title {
  margin: 0 0 8px;
  font-size: 18px;
}

.async-state-panel__message {
  margin: 0;
  line-height: 1.6;
}

.async-state-panel__actions {
  margin-top: 16px;
}
</style>
```

- [ ] **Step 10: Implement the text-only field error**

Create `control-plane/src/components/common/FieldError.vue` with exactly this content:

```vue
<script setup>
defineProps({
  id: { type: String, required: true },
  message: { type: String, required: true },
})
</script>

<template>
  <p v-if="message" :id="id" class="field-error" role="alert">
    {{ message }}
  </p>
</template>

<style scoped>
.field-error {
  margin: 4px 0 0;
  color: var(--el-color-danger, #f56c6c);
  line-height: 1.5;
}
</style>
```

- [ ] **Step 11: Run the component suite and verify its isolated GREEN**

Run from `control-plane`:

```bash
npm run test:unit -- --run src/components/common/AsyncStatePanel.spec.js
```

Expected: 1 file / 6 tests pass; `INITIAL` has no live-region attributes, `LOADING`/`EMPTY` are polite statuses, `FAILURE` and non-empty field errors are alerts, and HTML-shaped text is not parsed. Leave all changes uncommitted until the full gate passes.

- [ ] **Step 12: Run focused, full-regression, and production-build gates**

Run from `control-plane`:

```bash
npm run test:unit -- --run src/utils/format.spec.js src/components/common/AsyncStatePanel.spec.js
npm run test:unit -- --run
npm run build
```

Expected: focused result is 2 files / 15 tests; full result is 6 files / 34 tests; all tests pass with zero failures; build exits 0 and emits no warning except the already approved Element Plus chunk-size message.

- [ ] **Step 13: Verify exact exports and deterministic edge behavior**

Run from `control-plane`:

```bash
node --input-type=module -e 'import assert from "node:assert/strict"; import * as d from "./src/utils/date.js"; import * as f from "./src/utils/format.js"; import * as v from "./src/utils/validation.js"; assert.deepEqual(Object.keys(d).sort(),["formatDate","toApiDate","toApiMonth"]); assert.deepEqual(Object.keys(f).sort(),["formatCell","formatIngestedAt"]); assert.deepEqual(Object.keys(v).sort(),["hasValue","isRangeOrdered","matchesPattern"]); assert.equal(d.toApiDate("2026-02-29"),null); assert.equal(d.toApiDate(new Date()),null); assert.equal(f.formatCell("9223372036854775807",{logicalType:"LONG"}),"9223372036854775807"); assert.equal(f.formatIngestedAt("2026-08-25T02:30:15.123Z","Not/A_Zone"),"2026-08-25 10:30:15"); assert.equal(v.matchesPattern("x","["),false)'
```

Expected: exit 0 with no output; every module has only the approved named exports, strict date rejection, precision preservation, time-zone fallback, and invalid-regex safety hold outside the test runner.

- [ ] **Step 14: Verify scope, safe rendering, and forbidden capabilities**

Run from the repository root:

```bash
git diff --check
git status --short --untracked-files=all -- control-plane
rg -n 'v-html|innerHTML|axios|fetch\(|ApiError|Authorization|token|password|parseFloat|parseInt|BigInt\(' \
  control-plane/src/utils/date.js \
  control-plane/src/utils/format.js \
  control-plane/src/utils/validation.js \
  control-plane/src/components/common/AsyncStatePanel.vue \
  control-plane/src/components/common/FieldError.vue
git diff -- control-plane/package.json control-plane/package-lock.json \
  control-plane/vite.config.js control-plane/vitest.config.js \
  control-plane/src/api control-plane/src/router control-plane/src/layouts \
  control-plane/src/views control-plane/src/style.css
```

Expected: format check exits 0; status lists exactly the seven new files in this task; forbidden-capability scan has no output and exits 1; protected existing paths have no diff.

- [ ] **Step 15: Review the complete seven-file implementation diff**

Run from the repository root:

```bash
git diff -- control-plane/src/utils/date.js \
  control-plane/src/utils/format.js \
  control-plane/src/utils/validation.js \
  control-plane/src/utils/format.spec.js \
  control-plane/src/components/common/AsyncStatePanel.vue \
  control-plane/src/components/common/FieldError.vue \
  control-plane/src/components/common/AsyncStatePanel.spec.js
```

Expected: the diff matches Steps 2, 3, 5–7, 9, and 10 exactly; there are no unrelated comments, abstractions, dependencies, business copy, request logic, success-state branch, or Element Plus internal selectors.

- [ ] **Step 16: Create the single approved implementation commit**

Run from the repository root:

```bash
git add control-plane/src/utils/date.js \
  control-plane/src/utils/format.js \
  control-plane/src/utils/validation.js \
  control-plane/src/utils/format.spec.js \
  control-plane/src/components/common/AsyncStatePanel.vue \
  control-plane/src/components/common/FieldError.vue \
  control-plane/src/components/common/AsyncStatePanel.spec.js
git diff --cached --check
git diff --cached --name-status
git commit -m "feat(ui): add shared display and accessibility utilities"
```

Expected: staged format check exits 0; name/status output contains exactly seven added files; commit succeeds with the exact approved message.

- [ ] **Step 17: Re-run every result gate from committed state**

Run from the repository root with Node.js 24.15.0 still first on PATH:

```bash
git status --short
git show --format='%h %s' --name-status HEAD
cd control-plane
npm run test:unit -- --run src/utils/format.spec.js src/components/common/AsyncStatePanel.spec.js
npm run test:unit -- --run
npm run build
node --input-type=module -e 'import assert from "node:assert/strict"; import * as d from "./src/utils/date.js"; import * as f from "./src/utils/format.js"; import * as v from "./src/utils/validation.js"; assert.deepEqual(Object.keys(d).sort(),["formatDate","toApiDate","toApiMonth"]); assert.deepEqual(Object.keys(f).sort(),["formatCell","formatIngestedAt"]); assert.deepEqual(Object.keys(v).sort(),["hasValue","isRangeOrdered","matchesPattern"])'
cd ..
git diff HEAD^ HEAD --check
git status --short
```

Expected: both status outputs are empty; `git show` reports the exact message and seven added files; focused 15/15 and full 34/34 tests pass; build exits 0 with only the approved chunk-size warning; exact-export check and committed diff check exit 0.
