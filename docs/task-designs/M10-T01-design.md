# M10-T01 Vue 依赖、Vitest、VTU 和 Playwright 配置——任务设计

任务编号：`M10-T01`

对应任务：[M10-T01](../superpowers/plans/tensor-modules/M10-frontend-foundation.md#task-m10-t01-依赖与测试基线20h)

实施产物：可重复安装、可运行组件测试并可承接后续 E2E 的 `control-plane` 前端基础

## Goal

把当前只有 Vue/Vite 示例页的 `control-plane` 固定为 Node.js 24 LTS 下可重复安装和验证的前端工程：精确锁定 Vue Router、Element Plus、Axios、Vitest、Vue Test Utils、jsdom 与 Playwright 版本，建立单位测试、构建、E2E 命令和 `/api` 开发代理，并用一个真实挂载当前 `App.vue` 的 smoke test 证明测试链路有效。

该任务只建立依赖和测试基础，不实现路由、业务 API 客户端、页面布局或 E2E 业务流程。后续 M10-T02～T04 与 M14 必须能直接消费本任务的脚本和配置，不再选择测试运行器、DOM 环境、浏览器项目、代理环境变量或目录约定。

## Scope

包含：

- 将 `control-plane/package.json` 的顶层依赖改为无 `^`/`~` 的精确版本，并声明 `>=24.15.0 <25` 的 Node engine；
- 新增 `test`、`test:unit`、`test:e2e` 脚本，保留现有 `dev`、`build`、`preview`；
- 由 npm 生成并提交 lockfile v3 `control-plane/package-lock.json`，后续安装统一使用 `npm ci`；
- 让 Vite 开发服务器把 `/api` 原路径代理到 `TENSOR_BACKEND_URL`，未配置时使用 `http://127.0.0.1:8080`；
- 建立 Vitest + jsdom + Vue Test Utils 配置，测试范围仅为 `src/**/*.spec.js`；
- 在测试 setup 中全局安装 Element Plus，并在每个用例后清理 DOM、mock、stubbed globals 和 stubbed environment；
- 建立面向桌面 Chrome 的 Playwright 基础配置，固定 `control-plane/e2e` 为未来测试目录，使用 `PLAYWRIGHT_BASE_URL` 或默认 `http://127.0.0.1:8080`；
- 新增一个挂载当前 `App.vue`、断言示例页 `Get started` 标题并卸载 wrapper 的 smoke test；
- 按严格 RED/GREEN 顺序验证缺失脚本、精确版本、单位测试、配置加载、代理和生产构建。

排除：

- 不修改 `App.vue`、`main.js`、样式、示例组件/资源、`index.html` 或 README；示例清理属于 M10-T02；
- 不创建 router、layout、view、API client、DTO、composable、Pinia store、格式化工具或业务组件；
- 不创建 `control-plane/e2e/*.spec.js`、不启动后端、不安装或运行浏览器；真实页面 E2E 属于 M14；
- 不读取 Java 实现来推导前端字段，不复制 OpenAPI DTO，不调用网络 API；
- 不保存、显示或发送 Token，不使用 `VITE_` 前缀暴露后端地址，不在代理配置中加入 Authorization、Cookie 或请求体日志；
- 不修改 `docs/contracts/openapi-v1.yaml`、`docs/contracts/error-codes.md` 或其他已完成任务产物。

## Approach

### 运行时和精确版本

`package.json` 保持 ESM，并固定以下顶层版本；这些版本于 2026-09-04 通过 npm registry 的指定主版本查询确认，peer/engine 组合兼容 Vue 3.5、Vite 8 和 Node 24：

| 类别 | 包 | 精确版本 |
|---|---|---:|
| runtime | `vue` | `3.5.42` |
| runtime | `vue-router` | `4.6.4` |
| runtime | `element-plus` | `2.14.5` |
| runtime | `axios` | `1.20.0` |
| development | `@vitejs/plugin-vue` | `6.0.8` |
| development | `vite` | `8.2.2` |
| development | `vitest` | `4.1.11` |
| development | `@vue/test-utils` | `2.5.0` |
| development | `jsdom` | `30.0.1` |
| development | `@playwright/test` | `1.62.1` |

本任务以当前 Node 24 LTS 补丁基线 24.15.0 为最小验收环境，因此 `engines.node` 精确设为 `>=24.15.0 <25`；所有 npm、Vitest、Vite 和配置验证都必须在该范围内运行。当前工作机只有 Node 22.22.3，不得用它生成最终 lockfile 或充当验收环境。

脚本固定为：

```json
{
  "dev": "vite",
  "build": "vite build",
  "test": "vitest run",
  "test:unit": "vitest",
  "test:e2e": "playwright test",
  "preview": "vite preview"
}
```

先手工写入精确顶层版本，再以 Node 24 运行 `npm install --package-lock-only --ignore-scripts` 生成 lockfile，随后运行 `npm ci`。不得提交 `node_modules`、`dist`、Playwright trace/screenshot/video 或 npm cache；Playwright 失败产物统一写入已被仓库忽略的 `node_modules/.cache/tensor-playwright`。

### Vite 开发代理

`vite.config.js` 继续只注册 `vue()`，并增加：

```js
const backendUrl = process.env.TENSOR_BACKEND_URL || 'http://127.0.0.1:8080'

server: {
  proxy: {
    '/api': {
      target: backendUrl,
      changeOrigin: true,
    },
  },
}
```

代理不 rewrite，因此 M00-T03 冻结的 `/api/v1/**` 路径逐字到达后端。`TENSOR_BACKEND_URL` 只由 Vite 配置进程读取，不使用会注入浏览器 bundle 的 `VITE_` 前缀；配置不增加凭证、header 转发钩子或请求/响应日志。

### Vitest、VTU 和 setup

`vitest.config.js` 使用 `defineConfig` 与 `@vitejs/plugin-vue`，固定：

```js
test: {
  environment: 'jsdom',
  globals: true,
  include: ['src/**/*.spec.js'],
  setupFiles: ['./src/test/setup.js'],
  clearMocks: true,
  restoreMocks: true,
  unstubGlobals: true,
  unstubEnvs: true,
}
```

`src/test/setup.js` 把 `ElementPlus` 加入 `@vue/test-utils` 的 `config.global.plugins`。`afterEach` 必须执行 `document.body.innerHTML = ''`、`vi.restoreAllMocks()`、`vi.unstubAllGlobals()` 和 `vi.unstubAllEnvs()`；setup 不创建 Axios/fetch mock server，不发网络请求，也不隐藏 Vue 警告。

`src/App.spec.js` 只含一个 smoke 用例：`mount(App, { attachTo: document.body })`，断言唯一 `h1` 文本为 `Get started`，并在 `finally` 中 `wrapper.unmount()`。该测试验证 SFC 转换、jsdom、VTU、当前图片/SVG import 和 Element Plus setup 可共同运行，不提前断言 M10-T02 的路由或布局。

### Playwright 基线

`playwright.config.js` 使用 `defineConfig` 和 `devices['Desktop Chrome']`，固定：

- `testDir: './e2e'`；
- 单个项目名 `chromium`，项目 `use` 展开 `devices['Desktop Chrome']`；
- `use.baseURL` 为 `process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:8080'`；
- `trace: 'retain-on-failure'`、`screenshot: 'only-on-failure'`、`video: 'off'`；
- `reporter: 'list'`、`outputDir: 'node_modules/.cache/tensor-playwright'`、`forbidOnly: Boolean(process.env.CI)`、CI 下 `retries: 1`/`workers: 1`，本地为零重试/默认 worker。

配置不声明 `webServer`：M14 会按其任务设计启动并等待已打包 JAR，再用 `PLAYWRIGHT_BASE_URL` 指向真实页面。M10-T01 只通过 ESM import 验证配置可加载，不安装浏览器，也不以“零 E2E 用例”冒充通过的用户流程。

### TDD 和失败处理

先创建完整 `App.spec.js`，但不改 `package.json` 或创建 Vitest 配置；运行 `npm run test:unit -- --run`，必须只因缺少 `test:unit` script 非零。若先因 Node 版本不符合设计失败，应先切换到 Node 24.15+；不得把 Node 22 的结果记录成任务 RED。

GREEN 阶段一次只补任务卡需要的 package/config/setup，生成 lockfile 并安装后运行单位测试和构建。依赖解析、peer、SFC 编译、jsdom 或配置错误都属于真实缺陷，必须定位后修复；不得用 `--force`、`--legacy-peer-deps`、跳过 install script、放宽版本范围或删除断言绕过。

## Files

修改：

- `control-plane/package.json`：Node engine、精确 runtime/dev dependencies 和六个保留/新增 scripts；
- `control-plane/vite.config.js`：Vue plugin 与不 rewrite 的可配置 `/api` 开发代理。

创建：

- `control-plane/package-lock.json`：npm 在 Node 24 下生成的 lockfile v3；
- `control-plane/vitest.config.js`：Vue SFC、jsdom、测试匹配和 setup 配置；
- `control-plane/playwright.config.js`：未来 `e2e/` 的桌面 Chrome、base URL 与失败产物策略；
- `control-plane/src/test/setup.js`：Element Plus 的 VTU 全局插件和用例后清理；
- `control-plane/src/App.spec.js`：当前根组件的一项真实 mount smoke test。

不创建、修改或删除其他文件。实现提交固定为 `build(ui): establish frontend test foundation`，精确包含上述 7 个文件（5 新增、2 修改）；本设计、交接、看板、`node_modules`、`dist` 和测试产物不得混入实现提交。

## Tests

所有命令从仓库根开始；涉及 npm 的命令都在 `control-plane` 中以 Node `>=24.15.0 <25` 运行。

1. 前置和版本环境：

```bash
git status --short
cd control-plane
node -e 'const [major,minor]=process.versions.node.split(".").map(Number); if (major !== 24 || minor < 15) process.exit(1)'
npm --version
```

预期：开始实现前工作树只含已批准的任务状态/设计/交接文档变更；Node 检查退出 0。当前默认 `v22.22.3` 不满足，实施者必须先在外部工具链切换或安装 Node 24.15+。

2. 严格 RED：只创建完整 `src/App.spec.js`，运行：

```bash
npm run test:unit -- --run
```

预期：退出非 0，直接原因是 `Missing script: "test:unit"`；不是测试语法、依赖下载或 SFC import 错误。

3. 生成锁文件并可重复安装：

```bash
npm install --package-lock-only --ignore-scripts
npm ci
npm ls --depth=0
```

预期：三条命令退出 0；无 `ERESOLVE`、engine 或 peer 错误；顶层十个包与版本表逐项一致，lockfile 版本为 3。

4. 精确 package/lock 合同：

```bash
node --input-type=module -e 'import assert from "node:assert/strict"; import fs from "node:fs"; const p=JSON.parse(fs.readFileSync("package.json")); const l=JSON.parse(fs.readFileSync("package-lock.json")); const runtime={vue:"3.5.42","vue-router":"4.6.4","element-plus":"2.14.5",axios:"1.20.0"}; const dev={"@vitejs/plugin-vue":"6.0.8",vite:"8.2.2",vitest:"4.1.11","@vue/test-utils":"2.5.0",jsdom:"30.0.1","@playwright/test":"1.62.1"}; assert.deepEqual(p.dependencies,runtime); assert.deepEqual(p.devDependencies,dev); assert.equal(p.engines.node,">=24.15.0 <25"); assert.equal(l.lockfileVersion,3); assert.deepEqual(l.packages[""].dependencies,runtime); assert.deepEqual(l.packages[""].devDependencies,dev)'
```

预期：退出 0；package 与 lockfile 顶层依赖没有范围符或漂移。

5. 配置合同：

```bash
TENSOR_BACKEND_URL=http://127.0.0.1:19090 node --input-type=module -e 'import assert from "node:assert/strict"; const c=(await import("./vite.config.js")).default; assert.equal(c.server.proxy["/api"].target,process.env.TENSOR_BACKEND_URL); assert.equal(c.server.proxy["/api"].changeOrigin,true); assert.equal(c.server.proxy["/api"].rewrite,undefined)'
node --input-type=module -e 'import assert from "node:assert/strict"; const c=(await import("./vitest.config.js")).default; assert.equal(c.test.environment,"jsdom"); assert.deepEqual(c.test.include,["src/**/*.spec.js"]); assert.deepEqual(c.test.setupFiles,["./src/test/setup.js"])'
node --input-type=module -e 'import assert from "node:assert/strict"; const c=(await import("./playwright.config.js")).default; assert.equal(c.testDir,"./e2e"); assert.equal(c.projects.length,1); assert.equal(c.projects[0].name,"chromium"); assert.equal(c.use.baseURL,"http://127.0.0.1:8080")'
```

预期：三条命令退出 0；代理保留 `/api/v1` 路径且只从服务端进程环境取 target，Vitest 只收集 `src` 单测，Playwright 配置可在不启动浏览器/后端时加载。

6. GREEN 与回归：

```bash
npm run test:unit -- --run
npm test
npm run build
```

预期：前两条各执行 1 个文件、1 个 smoke 用例并全部通过；build 退出 0，`dist/index.html` 与哈希 assets 生成，无 Vue、Vite、Element Plus 或 chunk 错误。测试不得发出网络请求。

7. 范围、安全与格式：

```bash
cd ..
git diff --check
git status --short --untracked-files=all -- control-plane
rg -n 'token|password|authorization|cookie|VITE_' \
  control-plane/package.json control-plane/vite.config.js \
  control-plane/vitest.config.js control-plane/playwright.config.js \
  control-plane/src/test/setup.js control-plane/src/App.spec.js
git check-ignore control-plane/node_modules control-plane/dist \
  control-plane/node_modules/.cache/tensor-playwright
```

预期：格式检查退出 0；status 精确显示 Files 节 7 个文件；敏感/浏览器暴露前缀扫描无输出并按预期退出 1；三个生成路径均被忽略。暂存后 `git diff --cached --name-status` 精确为 5 个新增、2 个修改，提交消息为 `build(ui): establish frontend test foundation`。

## Acceptance

- Node `>=24.15.0 <25` 下 `npm ci` 从提交的 lockfile 可重复安装，十个顶层依赖均为版本表中的精确补丁，且无 engine/peer 解析错误；
- `dev`、`build`、`test`、`test:unit`、`test:e2e` 脚本存在，既有 `preview` 保留；
- `npm run test:unit -- --run` 和 `npm test` 各运行唯一 App smoke 用例并通过，真实挂载当前 SFC 且不访问网络；
- Vitest 使用 jsdom、Vue plugin、固定 setup 和 `src/**/*.spec.js` 边界；Element Plus 可供后续组件测试全局使用，每个用例后 DOM/mocks/stubs 被清理；
- Vite `/api` 代理默认指向 `127.0.0.1:8080`、可由非浏览器暴露的 `TENSOR_BACKEND_URL` 覆盖，且不 rewrite M00-T03 的 `/api/v1/**`；
- Playwright 配置加载成功，固定未来 `e2e/`、单一桌面 Chrome 项目、可配置页面 base URL、CI worker/retry 和失败产物策略，不伪造零用例 E2E 成功；
- `npm run build` 生成生产 bundle；未实现路由、API client、业务页面、Pinia、凭证或 M14 E2E；
- 实现提交精确包含 Files 节 7 个文件（5 新增、2 修改），不含文档、`node_modules`、`dist` 或测试产物，并使用固定提交消息。

## Risks

- 当前工作机仅安装 Node 22.22.3，低于本设计和 `jsdom@30.0.1` 的 Node 24.15 下限。该事实不改变代码方案，但实施和最终验证前必须先取得 Node 24.15+ 环境；不得通过放宽 engine、降级 jsdom 或忽略 engine 警告掩盖环境缺口。
- `vue-router` 的 npm latest 已进入 5.x，本任务按任务卡明确保留 4.x 并固定 `4.6.4`；后续不得用无主版本约束的 upgrade 改写 lockfile。
- Playwright 浏览器二进制和真实后端不属于本任务。配置 import 通过只证明基线可消费；M14 必须在其受控环境安装桌面浏览器、启动打包 JAR 并执行真实页面用例。
- Element Plus 被设置为 VTU 全局 plugin，后续个别测试若需要精确 stub 组件，应在测试自己的 mount options 中局部覆盖，不能删除共享 setup 或引入真实网络。
