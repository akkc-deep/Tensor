# M10-T02 Routes and Desktop Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Vue/Vite demo with Tensor's stable `/downloads` and `/datasets` routes, semantic top navigation, desktop shell, placeholder views, and recoverable 404.

**Architecture:** A small router factory accepts an injectable Vue Router history so production uses `createWebHistory()` while unit tests use isolated memory histories. `App.vue` delegates to one semantic `AppLayout`, which owns the persistent header/nav and renders three stateless route views through `RouterView`; Element Plus is installed only at the production entry and test setup boundaries, not used to inflate the navigation.

**Tech Stack:** Node.js 24.15.0, Vue 3.5.42, Vue Router 4.6.4, Element Plus 2.14.5, Vite 8.2.2, Vitest 4.1.11, Vue Test Utils 2.5.0, jsdom 30.0.1.

**Spec:** `docs/task-designs/M10-T02-design.md`

## Global Constraints

- Use Node `>=24.15.0 <25`; select Node 24.15.0 through the existing nvm installation before every npm/Vitest/Vite command.
- Use the approved semantic top-navigation option: `header`/`nav`, exactly two real `RouterLink` items, and `main`/`RouterView`; do not use `el-menu`, a sidebar, or a sticky/fixed header.
- Freeze route names as `downloads`, `datasets`, and `not-found`; `/` redirects to `downloads`, and `/:pathMatch(.*)*` preserves the unknown URL while rendering the 404 view.
- Keep both business views stateless and network-free with their final titles and exact incomplete-module guidance; do not create forms, clients, stores, composables, fake data, or business states.
- Install the existing production router and Element Plus in `main.js`; keep M10-T01 package versions, lockfile, Vite/Vitest/Playwright configuration, and scripts unchanged.
- Preserve the owner-approved full Element Plus installation even though it produces the single reproducible Vite `Some chunks are larger than 500 kB after minification` advisory for the approximately 1.00 MB JS bundle. Treat only that advisory as non-blocking; do not raise `chunkSizeWarningLimit`, switch to on-demand registration, or accept any other warning or error.
- Use `body { min-width: 1280px; }` and browser-native horizontal scrolling below that width; never hide overflow, collapse navigation, or remove focus outlines.
- Use the shell-specific `--tensor-interactive-color: #1f5f99` for hover/active/action text and focus outlines; it must retain at least 4.5:1 contrast against white and the active `#ecf5ff` background without overriding Element Plus's global primary theme.
- Create/modify/delete exactly the 16 paths named by the design: 7 additions, 4 modifications, and 5 deletions; keep `public/favicon.svg` because `index.html` still references it.
- Do not implement server-side SPA fallback; direct-refresh support for history routes remains M13-T03 scope.
- Use strict test-first order and finish with the single implementation commit `feat(ui): add Tensor routes and desktop layout`.

---

### Task 1: Build the tested Tensor route shell

**Files:**

- Create: `control-plane/src/router/index.js`
- Create: `control-plane/src/layouts/AppLayout.vue`
- Create: `control-plane/src/views/DownloadView.vue`
- Create: `control-plane/src/views/DatasetView.vue`
- Create: `control-plane/src/views/NotFoundView.vue`
- Create: `control-plane/src/router/index.spec.js`
- Create: `control-plane/src/layouts/AppLayout.spec.js`
- Modify: `control-plane/src/App.vue`
- Modify: `control-plane/src/main.js`
- Modify: `control-plane/src/style.css`
- Modify: `control-plane/src/App.spec.js`
- Delete: `control-plane/src/components/HelloWorld.vue`
- Delete: `control-plane/src/assets/hero.png`
- Delete: `control-plane/src/assets/vite.svg`
- Delete: `control-plane/src/assets/vue.svg`
- Delete: `control-plane/public/icons.svg`

**Interfaces:**

- Consumes: M10-T01's exact Vue Router/Element Plus/Vitest/VTU/jsdom packages, `src/test/setup.js` cleanup/plugin behavior, `test`/`test:unit`/`build` scripts, and official-registry lockfile.
- Produces: `createAppRouter(history = createWebHistory())`, the default production router, named routes `downloads|datasets|not-found`, semantic `AppLayout`, final page titles `数据下载|数据查看|页面不存在`, and stable `/downloads|/datasets|catch-all` view boundaries for M11/M12/M13.

- [ ] **Step 1: Verify the clean M10-T01 baseline**

Run from the repository root:

```bash
git status --short
source /Users/qiangzhiwei/.nvm/nvm.sh
nvm use 24.15.0
cd control-plane
node -e 'const [major,minor]=process.versions.node.split(".").map(Number); if (major !== 24 || minor < 15) process.exit(1)'
npm ci --registry=https://registry.npmjs.org/
npm run test:unit -- --run
npm run build
```

Expected: the initial Git status is empty; Node reports v24.15.0; `npm ci` installs 172 packages and audits 173 with 0 vulnerabilities; the baseline runs 1 file/1 test; Vite builds successfully.

- [ ] **Step 2: Write the failing router contract tests**

Create `control-plane/src/router/index.spec.js` exactly as follows:

```js
import { createMemoryHistory } from 'vue-router'

import { createAppRouter } from './index.js'

describe('app router', () => {
  it('registers the named business routes', () => {
    const router = createAppRouter(createMemoryHistory())

    expect(router.resolve({ name: 'downloads' }).path).toBe('/downloads')
    expect(router.resolve({ name: 'datasets' }).path).toBe('/datasets')
  })

  it('redirects the root route to downloads', async () => {
    const router = createAppRouter(createMemoryHistory())

    await router.push('/')

    expect(router.currentRoute.value.name).toBe('downloads')
    expect(router.currentRoute.value.fullPath).toBe('/downloads')
  })

  it('keeps an unknown path on the not-found route', async () => {
    const router = createAppRouter(createMemoryHistory())

    await router.push('/missing')

    expect(router.currentRoute.value.name).toBe('not-found')
    expect(router.currentRoute.value.fullPath).toBe('/missing')
  })
})
```

- [ ] **Step 3: Write the failing layout behavior tests**

Create `control-plane/src/layouts/AppLayout.spec.js` exactly as follows:

```js
import { readFileSync } from 'node:fs'

import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'

import { createAppRouter } from '../router/index.js'
import AppLayout from './AppLayout.vue'

const styles = readFileSync('src/style.css', 'utf8')

let styleElement

beforeAll(() => {
  styleElement = document.createElement('style')
  styleElement.textContent = styles
  document.head.append(styleElement)
})

afterAll(() => styleElement.remove())

function declaration(selector, property) {
  const rule = [...styleElement.sheet.cssRules].find((candidate) =>
    candidate.selectorText
      ?.split(',')
      .map((part) => part.trim())
      .includes(selector),
  )

  return rule.style.getPropertyValue(property).trim()
}

function resolveColor(value) {
  const variable = value.match(/var\((--[\w-]+)(?:,\s*(#[\da-f]{6}))?\)/i)
  if (variable) return declaration(':root', variable[1]) || variable[2]

  return value.match(/#[\da-f]{6}/i)[0]
}

function luminance(hex) {
  const channels = hex
    .match(/[\da-f]{2}/gi)
    .map((channel) => Number.parseInt(channel, 16) / 255)
    .map((channel) =>
      channel <= 0.04045
        ? channel / 12.92
        : ((channel + 0.055) / 1.055) ** 2.4,
    )

  return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
}

function contrastRatio(foreground, background) {
  const values = [luminance(foreground), luminance(background)].sort(
    (left, right) => right - left,
  )
  return (values[0] + 0.05) / (values[1] + 0.05)
}

async function mountAt(path) {
  const router = createAppRouter(createMemoryHistory())
  await router.push(path)
  await router.isReady()

  return {
    router,
    wrapper: mount(AppLayout, {
      attachTo: document.body,
      global: { plugins: [router] },
    }),
  }
}

describe('AppLayout', () => {
  it('renders the two-item semantic navigation and active download view', async () => {
    const { wrapper } = await mountAt('/downloads')

    try {
      const nav = wrapper.get('nav[aria-label="主导航"]')
      const links = nav.findAll('a')
      expect(links.map((link) => link.text())).toEqual(['数据下载', '数据查看'])
      expect(links.map((link) => link.attributes('href'))).toEqual([
        '/downloads',
        '/datasets',
      ])
      expect(links[0].attributes('aria-current')).toBe('page')
      expect(links[1].attributes('aria-current')).toBeUndefined()
      const activeColor = resolveColor(
        declaration('.tensor-nav a.router-link-active', 'color'),
      )
      expect(contrastRatio(activeColor, '#ffffff')).toBeGreaterThanOrEqual(4.5)
      expect(contrastRatio(activeColor, '#ecf5ff')).toBeGreaterThanOrEqual(4.5)
      const focusColor = resolveColor(
        declaration('.tensor-nav a:focus-visible', 'outline'),
      )
      expect(contrastRatio(focusColor, '#ffffff')).toBeGreaterThanOrEqual(3)
      expect(wrapper.get('main h1').text()).toBe('数据下载')
      expect(wrapper.get('main p').text()).toBe(
        '数据下载模块尚未完成，后续任务将提供数据源、接口、参数和下载结果。',
      )
    } finally {
      wrapper.unmount()
    }
  })

  it('keeps dataset navigation focusable and switches the active view', async () => {
    const { router, wrapper } = await mountAt('/downloads')

    try {
      const links = wrapper.get('nav[aria-label="主导航"]').findAll('a')
      links[1].element.focus()
      expect(document.activeElement).toBe(links[1].element)

      await links[1].trigger('click')
      await flushPromises()

      expect(router.currentRoute.value.name).toBe('datasets')
      expect(links[0].attributes('aria-current')).toBeUndefined()
      expect(links[1].attributes('aria-current')).toBe('page')
      expect(wrapper.get('main h1').text()).toBe('数据查看')
      expect(wrapper.get('main p').text()).toBe(
        '数据查看模块尚未完成，后续任务将提供数据集筛选、表格和分页。',
      )
    } finally {
      wrapper.unmount()
    }
  })

  it('renders a recoverable not-found view for an unknown path', async () => {
    const { wrapper } = await mountAt('/missing')

    try {
      expect(wrapper.get('main h1').text()).toBe('页面不存在')
      expect(wrapper.get('main p').text()).toBe('当前地址不存在。')
      const returnLink = wrapper.get('main a')
      expect(returnLink.text()).toBe('返回数据下载')
      expect(returnLink.attributes('href')).toBe('/downloads')
      const actionColor = resolveColor(declaration('.page__action', 'color'))
      expect(contrastRatio(actionColor, '#ffffff')).toBeGreaterThanOrEqual(4.5)
    } finally {
      wrapper.unmount()
    }
  })
})
```

- [ ] **Step 4: Change the root smoke test to the wished-for routed shell**

Replace `control-plane/src/App.spec.js` with:

```js
import { mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'

import App from './App.vue'
import { createAppRouter } from './router/index.js'

describe('App', () => {
  it('renders the routed Tensor application shell', async () => {
    const router = createAppRouter(createMemoryHistory())
    await router.push('/')
    await router.isReady()
    const wrapper = mount(App, {
      attachTo: document.body,
      global: { plugins: [router] },
    })

    try {
      expect(wrapper.findAll('header')).toHaveLength(1)
      expect(wrapper.findAll('nav[aria-label="主导航"]')).toHaveLength(1)
      expect(wrapper.findAll('main')).toHaveLength(1)
      expect(wrapper.findAll('h1')).toHaveLength(1)
      expect(wrapper.get('h1').text()).toBe('数据下载')
    } finally {
      wrapper.unmount()
    }
  })
})
```

- [ ] **Step 5: Run the three files and verify the expected RED**

Run from `control-plane` under Node 24.15.0:

```bash
npm run test:unit -- --run \
  src/App.spec.js \
  src/router/index.spec.js \
  src/layouts/AppLayout.spec.js
```

Expected: exit non-zero only because `src/router/index.js`, `src/layouts/AppLayout.vue`, or their view imports do not exist. If the failure names test syntax, Vue Test Utils, Node, or installed dependencies, fix the test setup and rerun until the missing target modules are the only cause.

- [ ] **Step 6: Create the three stateless route views**

Create `control-plane/src/views/DownloadView.vue`:

```vue
<template>
  <section class="page" aria-labelledby="downloads-title">
    <h1 id="downloads-title">数据下载</h1>
    <p>数据下载模块尚未完成，后续任务将提供数据源、接口、参数和下载结果。</p>
  </section>
</template>
```

Create `control-plane/src/views/DatasetView.vue`:

```vue
<template>
  <section class="page" aria-labelledby="datasets-title">
    <h1 id="datasets-title">数据查看</h1>
    <p>数据查看模块尚未完成，后续任务将提供数据集筛选、表格和分页。</p>
  </section>
</template>
```

Create `control-plane/src/views/NotFoundView.vue`:

```vue
<script setup>
import { RouterLink } from 'vue-router'
</script>

<template>
  <section class="page" aria-labelledby="not-found-title">
    <h1 id="not-found-title">页面不存在</h1>
    <p>当前地址不存在。</p>
    <RouterLink class="page__action" :to="{ name: 'downloads' }">
      返回数据下载
    </RouterLink>
  </section>
</template>
```

- [ ] **Step 7: Implement the injectable router factory and production router**

Create `control-plane/src/router/index.js`:

```js
import { createRouter, createWebHistory } from 'vue-router'

import DatasetView from '../views/DatasetView.vue'
import DownloadView from '../views/DownloadView.vue'
import NotFoundView from '../views/NotFoundView.vue'

const routes = [
  { path: '/', redirect: { name: 'downloads' } },
  { path: '/downloads', name: 'downloads', component: DownloadView },
  { path: '/datasets', name: 'datasets', component: DatasetView },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: NotFoundView,
  },
]

export function createAppRouter(history = createWebHistory()) {
  return createRouter({ history, routes })
}

export default createAppRouter()
```

- [ ] **Step 8: Implement the semantic application layout**

Create `control-plane/src/layouts/AppLayout.vue`:

```vue
<script setup>
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <div class="tensor-shell">
    <header class="tensor-header">
      <div class="tensor-header__inner">
        <div class="tensor-brand">Tensor</div>
        <nav class="tensor-nav" aria-label="主导航">
          <RouterLink :to="{ name: 'downloads' }">数据下载</RouterLink>
          <RouterLink :to="{ name: 'datasets' }">数据查看</RouterLink>
        </nav>
      </div>
    </header>
    <main class="tensor-main">
      <RouterView />
    </main>
  </div>
</template>
```

Replace `control-plane/src/App.vue` with:

```vue
<script setup>
import AppLayout from './layouts/AppLayout.vue'
</script>

<template>
  <AppLayout />
</template>
```

- [ ] **Step 9: Assemble the production entry with router and Element Plus**

Replace `control-plane/src/main.js` with:

```js
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import { createApp } from 'vue'

import App from './App.vue'
import router from './router/index.js'
import './style.css'

createApp(App).use(router).use(ElementPlus).mount('#app')
```

- [ ] **Step 10: Replace the demo CSS with the fixed desktop shell**

Replace `control-plane/src/style.css` with:

```css
:root {
  --tensor-interactive-color: #1f5f99;

  font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  color: var(--el-text-color-primary, #303133);
  background: var(--el-bg-color-page, #f5f7fa);
  color-scheme: light;
  font-synthesis: none;
  text-rendering: optimizeLegibility;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

* {
  box-sizing: border-box;
}

html,
body,
#app {
  min-height: 100%;
}

body {
  min-width: 1280px;
  margin: 0;
  background: var(--el-bg-color-page, #f5f7fa);
}

.tensor-shell {
  min-height: 100vh;
}

.tensor-header {
  border-bottom: 1px solid var(--el-border-color-light, #e4e7ed);
  background: var(--el-bg-color, #ffffff);
}

.tensor-header__inner {
  display: flex;
  align-items: center;
  height: 64px;
  padding: 0 32px;
  gap: 48px;
}

.tensor-brand {
  color: var(--el-text-color-primary, #303133);
  font-size: 20px;
  font-weight: 600;
}

.tensor-nav {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tensor-nav a {
  padding: 8px 16px;
  border-radius: 6px;
  color: var(--el-text-color-regular, #606266);
  font-weight: 500;
  text-decoration: none;
}

.tensor-nav a:hover {
  color: var(--tensor-interactive-color);
}

.tensor-nav a.router-link-active {
  color: var(--tensor-interactive-color);
  background: var(--el-color-primary-light-9, #ecf5ff);
}

.tensor-nav a:focus-visible,
.page__action:focus-visible {
  outline: 3px solid var(--tensor-interactive-color);
  outline-offset: 3px;
}

.tensor-main {
  padding: 32px;
}

.page {
  padding: 32px;
  border: 1px solid var(--el-border-color-light, #e4e7ed);
  border-radius: 8px;
  background: var(--el-bg-color, #ffffff);
}

.page h1 {
  margin: 0 0 16px;
  font-size: 28px;
}

.page p {
  margin: 0;
  color: var(--el-text-color-regular, #606266);
  line-height: 1.7;
}

.page__action {
  display: inline-block;
  margin-top: 24px;
  color: var(--tensor-interactive-color);
}
```

- [ ] **Step 11: Delete only the five unreferenced demo paths**

Run from the repository root:

```bash
git rm \
  control-plane/src/components/HelloWorld.vue \
  control-plane/src/assets/hero.png \
  control-plane/src/assets/vite.svg \
  control-plane/src/assets/vue.svg \
  control-plane/public/icons.svg
```

Expected: exactly those five tracked paths are staged as deleted. Do not remove `control-plane/public/favicon.svg`; `control-plane/index.html` still references it.

- [ ] **Step 12: Run the focused GREEN test cycle**

Run from `control-plane` under Node 24.15.0:

```bash
npm run test:unit -- --run \
  src/App.spec.js \
  src/router/index.spec.js \
  src/layouts/AppLayout.spec.js
```

Expected: 3 files/7 tests pass with no Vue warning, unhandled navigation rejection, network request, or residual DOM failure. If a test fails, change production code rather than weakening the assertions.

- [ ] **Step 13: Run full unit and production-build regression**

Run from `control-plane`:

```bash
npm test
npm run build
```

Expected: `npm test` reports 3 files/7 tests; Vite builds `dist/index.html` plus hashed assets and exits 0 without unresolved router, component, CSS, deleted-resource, or chunk errors. The only permitted build advisory is the owner-approved Element Plus chunk-size message named in Global Constraints; no other warning or error is permitted.

- [ ] **Step 14: Verify cleanup, scope, security, and generated-path boundaries**

Run from `control-plane`:

```bash
rg -n 'HelloWorld|hero\.png|vite\.svg|vue\.svg|icons\.svg' src public
for path in \
  src/components/HelloWorld.vue \
  src/assets/hero.png src/assets/vite.svg src/assets/vue.svg \
  public/icons.svg; do
  test ! -e "$path"
done
```

Expected: `rg` produces no output and exits 1; every `test ! -e` exits 0.

Run from the repository root:

```bash
git diff --check
git status --short --untracked-files=all -- control-plane
rg -n 'token|password|authorization|cookie|VITE_' \
  control-plane/src/App.vue control-plane/src/main.js \
  control-plane/src/style.css control-plane/src/App.spec.js \
  control-plane/src/router control-plane/src/layouts control-plane/src/views
git check-ignore control-plane/node_modules control-plane/dist \
  control-plane/node_modules/.cache/tensor-playwright
```

Expected: formatting exits 0; status contains exactly the design's 7 additions, 4 modifications, and 5 deletions; the sensitive/browser-prefix scan has no output and exits 1; all three generated paths are ignored.

- [ ] **Step 15: Stage and verify the exact implementation boundary**

Run from the repository root:

```bash
git add \
  control-plane/src/router/index.js \
  control-plane/src/layouts/AppLayout.vue \
  control-plane/src/views/DownloadView.vue \
  control-plane/src/views/DatasetView.vue \
  control-plane/src/views/NotFoundView.vue \
  control-plane/src/router/index.spec.js \
  control-plane/src/layouts/AppLayout.spec.js \
  control-plane/src/App.vue \
  control-plane/src/main.js \
  control-plane/src/style.css \
  control-plane/src/App.spec.js
git diff --cached --name-status
git diff --cached --check
```

Expected: the staged diff lists exactly 16 paths total—7 `A`, 4 `M`, and the 5 already-staged `D` paths from Step 11—with no design, plan, handoff, board, package, lock, config, `node_modules`, or `dist` change.

- [ ] **Step 16: Commit the single reviewed implementation unit**

```bash
git commit -m "feat(ui): add Tensor routes and desktop layout"
git show --stat --oneline --summary HEAD
git status --short
```

Expected: the commit subject is exact, its diff is the same 16-file 7-add/4-modify/5-delete set, and the working tree is empty.

- [ ] **Step 17: Request independent review and run final submitted-tree verification**

Request a read-only reviewer for the range from the task-start state commit through the implementation commit. Require findings grouped as Critical/Important/Minor and fix all Critical or Important findings without expanding the 16-file scope; re-review the final range after any fix.

Then run on the final implementation commit under Node 24.15.0:

```bash
cd control-plane
npm ci --registry=https://registry.npmjs.org/
npm run test:unit -- --run
npm test
npm run build
cd ..
git diff --check
git status --short
```

Expected: official-registry clean install succeeds with 0 vulnerabilities; both test commands report 3 files/7 tests; build and formatting exit 0, with at most the single owner-approved Element Plus chunk-size advisory and no other warning or error; independent review has no unresolved Critical/Important; working tree is empty. Only then may the task board record `IN_PROGRESS -> COMPLETED` and its outcome-level evidence.
