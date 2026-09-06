# ISSUE-004 UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将已确认的数据工作台设计落到正式 Vue 前端，并保留全部现有业务和接口契约。

**Architecture:** 复用既有页面、表单组件和 composable；以根 CSS 变量及 Element Plus 映射统一主题，以 KeepAlive 保留业务实例。日期控件按现有元数据呈现，沿用每次提交一次请求的下载流程。

**Tech Stack:** Vue 3.5.42、Vue Router 4.6.4、Element Plus 2.14.5、Axios 1.20.0、Vite 8.2.2、Vitest 4.1.11、Playwright 1.62.1；Node `>=24.15.0 <25`。

**Spec:** [总体技术设计](../../task-designs/ISSUE-004-design.md) · [已确认的视觉方案](../../issues/proposals/ISSUE-004-ui-visual-concepts.md)。

**Task board:** [ISSUE-004](../../task-handoffs/ISSUE-004/ISSUE-004-task-board.md)。看板是任务身份、次序、依赖和状态的唯一权威；下列复选项仅用于任务内执行记录。任务内复选项随各项验收更新，权威状态以看板为准。

## Global Constraints

- 实施范围为前端，后端接口路径、HTTP 方法、参数、响应结构、状态码、错误码及语义保持不变。
- 数据源、接口、参数、数据集和筛选条件继续由现有元数据驱动，正式页面覆盖全部已接入的数据集。
- 主题偏好只保存在当前浏览器；设置无后端请求。页面往返保留日期、筛选条件、分页和操作状态。
- 主色与五类表面及主按钮白字的对比度至少 5.5:1；成功 / 错误保留固定语义色。
- 下载单日期参数保持一个日期控件，原生起止参数保持两个独立控件；每次有效提交只发送一次请求。查询日期保持 `YYYY-MM-DD`，下载日期保持 `YYYYMMDD`。
- 不增加依赖、后台队列、任务历史、数据持久化 store 或浮点数值转换。参考页中的示例工具和假数据不进入产品。
- 优先复用现有组件；多次出现且职责相同的 UI 必须抽取为可复用组件，共用有状态逻辑放入 composable，纯逻辑放入工具函数，仅样式重复使用 CSS 类和主题变量。只保留必要的 props、事件和 slots，并将相关调用处统一接入，避免复制和过度抽象。
- 每项任务在实现及验收时检查重复代码并记录实际复用位置，T06 汇总核验。下列 Files 允许补充 `control-plane/src/components/common/` 或对应业务目录中的共享组件，以及 `src/composables/`、`src/utils/` 中的共用 UI 逻辑和实际调用处；具体文件写入专属设计，既有业务只读依赖仍保持只读。
- 2026-09-07 用户确认沿用接口现有日期能力；移除前端区间执行策略及其待确认门槛，不新增日期枚举、批次进度或续传。
- 每项业务行为测试随任务完成，T06 负责跨页与浏览器验收，不将所有测试拖到最后。
- 本计划所有 `npm` / `npx` 命令均在 `control-plane` 目录、Node 24.15.0 或满足 engines 的 Node 24 环境执行；Git 操作使用任务明确列出的文件，不使用 `git add .`。

---

### ISSUE-004-T01：主题计算与全局样式基础

**Files:** 新建 `control-plane/src/utils/theme.js`、`theme.spec.js`、`control-plane/src/composables/useTheme.js`、`useTheme.spec.js`；修改 `control-plane/src/App.vue`、`App.spec.js`、`control-plane/src/style.css`。

**Interfaces:** 输入已确认 HTML 的 palette / 混色算法；输出 `createTheme(value)`、`contrastRatio(a,b)`、`createThemeState()`、`useTheme()` 和 `--tensor-*` / Element Plus 变量。业务页不参与主题保存。

- [x] 在 `theme.spec.js` 先覆盖非法 HEX、完整默认 palette，以及白 / 黄 / 绿 / 黑 / 自定义色的实际对比度。例如：

```js
const theme = createTheme('#ffff00')
expect(theme.requested).toBe('#ffff00')
expect(theme.applied).not.toBe(theme.requested)
for (const key of ['bg', 'surface', 'raised', 'nav', 'accentBg']) {
  expect(contrastRatio(theme.applied, theme.colors[key])).toBeGreaterThanOrEqual(5.5)
}
expect(contrastRatio(theme.applied, '#ffffff')).toBeGreaterThanOrEqual(5.5)
expect(createTheme('#123')).toBeNull()
```

- [x] 运行 `npm test -- src/utils/theme.spec.js`，确认缺少新能力的断言失败。
- [x] 提取纯函数；应用级主题状态读取 / 写入单个 HEX，向根节点写变量，在 App 提供共享实例。调用关系为 `App -> createThemeState -> createTheme -> document.documentElement.style`；`apply` 校验成功后才写状态、DOM 和存储。
- [x] 在 `useTheme.spec.js` 测读取损坏值、读写抛错、非法输入不改旧值、保存成功和默认重置。验证 `localStorage.setItem('tensor-issue004-accent', '#2857b4')`，而非持久化整套 palette。
- [x] 运行 `npm test -- src/utils/theme.spec.js src/composables/useTheme.spec.js src/App.spec.js` 与 `npm run build`；全部通过。此时仅建立主题基础，不拆现有页面布局，避免破坏未调整的布局测试。
- [x] 核对改动范围，将本任务文件加入 Git 并形成独立提交。

**Acceptance:** 默认主题精确匹配；合法颜色整套生效且操作色达标；非法输入保持旧主题；存储故障仍能预览；无 API 客户端依赖。

### ISSUE-004-T02：侧栏、设置与业务状态保留

**Files:** 新建 `control-plane/src/views/SettingsView.vue`、`SettingsView.spec.js`；修改 `control-plane/src/layouts/AppLayout.vue`、`AppLayout.spec.js`、`control-plane/src/router/index.js`、`index.spec.js`、`control-plane/src/views/DownloadView.vue`、`DatasetView.vue`、`control-plane/src/style.css`。

**Interfaces:** 使用 T01 的 `useTheme()`；保留现有业务页内部 refs。新增命名路由 `settings`；缓存具名 DownloadView / DatasetView，容量 2，key 为路由名。

- [x] 扩充路由 / 布局测试：三项语义导航、404、设置新开无 API 请求；下载表单填值、进入设置再返回后仍保持，`listDataSources` 不增加调用。
- [x] 为设置写交互测试，先证明当前缺少功能。示例验收动作：

```js
await wrapper.get('input[type="text"]').setValue('#ffff00')
await wrapper.get('form').trigger('submit')
expect(theme.requested.value).toBe('#ffff00')
expect(wrapper.text()).toContain(theme.applied.value.toUpperCase())
```

- [x] 实现响应式侧栏与设置表单，主题控件只出现在设置页。业务缓存结构如下，外层 KeepAlive 保持挂载，404 和设置不纳入 include：

```vue
<RouterView v-slot="{ Component, route }">
  <KeepAlive :include="['DownloadView', 'DatasetView']" :max="2">
    <component :is="Component" :key="route.name" />
  </KeepAlive>
</RouterView>
```

- [x] 增加延迟 Promise 测试：发起下载 / 查询后进入设置，响应到达再返回，状态正确且不重复发送；覆盖查询第 2 页、每页 100 条及错误重试快照保留。
- [x] 移除旧 1280px 页面下限，保留标签、焦点、跳转工作区入口；更新双导航与旧 CSS 断言，业务断言继续保留。
- [x] 检查应用壳、设置及现有页面中同职责的重复结构，复用现有组件或抽取简单组件并接入相关调用处；确认设置仍无 API 依赖、业务缓存生命周期保持。
- [x] 运行 `npm test -- src/router/index.spec.js src/layouts/AppLayout.spec.js src/views/SettingsView.spec.js` 与 `npm run build`；全部通过后核对范围、加入 Git 并独立提交。

**Acceptance:** 三入口可用；默认 / 自定义 / 错误 / 降级 / 重置状态正确；设置及回到已缓存页不产生新 API 请求；业务状态和在途操作保持；本次涉及的同职责重复结构已复用。

### ISSUE-004-T03：日期控件与原参数契约

**Files:** 调整 `control-plane/src/components/download/DynamicParameterForm.vue`、`control-plane/src/components/dataset/DynamicFilterForm.vue` 的控件呈现及各自 `.spec.js`；仅在请求覆盖缺失时补充 `control-plane/src/views/DownloadView.spec.js`。`useParameterForm.js`、`useDatasetFilters.js`、`useDownloadFlow.js`、`utils/date.js` 和 API 客户端作为只读依赖，不新增日期工具或流程接口。

**Interfaces:** 下载表单保留 `validate()`、`normalizedValues()`、`reset()`，View 继续调用 `submit(params)`；查看表单保留 `validate()`、`criteria()`、`reset()`。字段数量、名称、类型、默认值和必填规则来自各自元数据。

- [x] 按用户明确的日期约束完成并链接本任务专属设计，列出单日期、原生起止、月份及无日期表单的现有控件和请求示例。
- [x] 运行 `npm test -- src/components/download/DynamicParameterForm.spec.js src/components/dataset/DynamicFilterForm.spec.js src/composables/useDatasetFilters.spec.js src/views/DownloadView.spec.js src/api/api.spec.js`，检查既有覆盖；已有行为测试直接复用，不为样式调整重复编写同构测试。
- [x] 统一两个表单中日期控件的宽度、标签、错误与窄屏排布；同职责的重复字段呈现抽取为共享组件，重复的输入属性处理逻辑集中复用。DATE 仍为一个 `type="date"`，DATE_RANGE_MEMBER 各保留一个独立 `type="date"`；MONTH 仍为月份。继续使用既有 `value-format="YYYY-MM-DD"` 及归一化，不增加推导字段。
- [x] 检查必填、非法日期、原生起止倒序、错误聚焦和接口切换清理；查看侧保留可空 / 单边日期。通过缓存页面往返测试确认控件值保持。
- [x] 核对实际请求：daily 输入 2026-08-07 后只发一次 `params: { trade_date: '20260807' }`；new_share 输入起止日期后只发一次原范围参数。仅对既有用例未覆盖的请求边界补充断言，例如：

```js
// 通过页面设置 new_share 原生起止参数并提交后，检查 mock 客户端。
expect(downloadDataset).toHaveBeenCalledTimes(1)
expect(downloadDataset).toHaveBeenCalledWith({
  pluginId: 'tushare_pro', apiName: 'new_share',
  params: { start_date: '20260803', end_date: '20260807' },
})
```

- [x] 重跑上述针对性测试及 `npm test -- src/composables/useDownloadFlow.spec.js`；全部通过后核对日期工具、业务流程和请求契约未改，将本任务改动加入 Git 并独立提交。

**Acceptance:** 单日期保持单日期，原生起止保持两个独立控件；现有日期校验、筛选语义和参数格式不变；每次有效下载提交只发一次请求，原计数与重试行为保持；两个表单的同职责重复呈现和输入属性处理已实际共用，标签、错误关联与聚焦无回归。

### ISSUE-004-T04：下载工作台布局与反馈

**Files:** 修改 `control-plane/src/views/DownloadView.vue`、`DownloadView.spec.js`、`control-plane/src/components/download/` 的既有组件与对应测试、`control-plane/src/components/common/AsyncStatePanel.vue`、`AsyncStatePanel.spec.js`、`control-plane/src/style.css`。

**Interfaces:** 消费既有下载流程的 `state/result/error/canRetry` 与 T03 的元数据表单；保留选择、校验、提交与 retry 事件，不新建第二套业务状态。

- [x] 用真实组件测试补齐 SUBMITTING 时右侧可见状态和禁用控件；失败显示原错误、requestId 与适用的重试入口。例如 `expect(wrapper.get('[role="status"]').text()).toContain('正在下载')`；断言实际新视图在实现前失败。
- [x] 将配置分组与结果分组接入参考方案的双面板布局；数据源与接口并排，说明、参数、提交分区，计数由实际响应提供。
- [x] 复用现有 AsyncStatePanel 统一待操作、加载、空和失败反馈；重复且职责相同的面板结构抽取为共享组件，仅样式重复使用公共 CSS。成功仍由 DownloadResult 呈现，组件只接收必要参数与插槽。长接口名、说明、错误、请求 ID 可换行。
- [x] 下载错误保留“使用原参数重试”；元数据错误保持独立的“重新加载”入口，均沿用原 `canRetry` 条件。
- [x] 运行 `npm test -- src/views/DownloadView.spec.js src/components/download src/components/common`；全部通过后核对范围、加入 Git 并独立提交。

**Acceptance:** 桌面左配置右结果、窄屏纵向；所有状态可见；动态参数、本次响应计数、锁和既有重试入口行为无回归，无样例业务值；同职责重复面板与反馈已通过公共组件复用。

### ISSUE-004-T05：查看工作台与精确表格展示

**Files:** 修改 `control-plane/src/views/DatasetView.vue`、`DatasetView.spec.js`、`control-plane/src/components/dataset/` 的既有组件与对应测试、`control-plane/src/utils/format.js`、`format.spec.js`、`control-plane/src/style.css`。

**Interfaces:** 保留 `criteria()` 与 `useDatasetQuery` 全部接口；DatasetTable 新增可选 `pluginId`、`apiName`（默认空字符串），用于限定 daily / weekly 的前端展示映射。精确值仍通过 `formatCell` 展示。

- [x] 在现有 155 列、长文本和精度测试上增加数字对齐、固定列主题与涨跌符号用例。例如：

```js
expect(formatCell('12345678901234567890.123456789012345678', {
  name: 'precise', logicalType: 'DECIMAL',
})).toBe('12345678901234567890.123456789012345678')
// 实际单元格另外断言 change='0.0100' 显示 '+0.0100'；
// change='-0.0000' 不使用跌色；普通持仓数量不随正负上色。
```

- [x] 运行 `npm test -- src/components/dataset/DatasetTable.spec.js src/utils/format.spec.js`，确认新增行为未满足。
- [x] 组合上方选择 / 筛选面板和下方表格 / 分页面板；保留 `tsCode`、交易日及公告日起止字段、查询 / 重置和分页快照，默认 50 条、可选 20 / 50 / 100。
- [x] 检查与下载页共用的选择、面板和反馈，接入已有共享实现；本任务发现的同职责重复部分在此抽取并同步替换相关调用处，保留两页各自业务流程。公共组件变更后运行 `npm test -- src/views/DownloadView.spec.js src/components/common` 验证受影响调用方。
- [x] 表格数字右对齐、等宽数字；使用字符串判断符号和零，不调用 Number 处理业务值。小型展示映射限定 tushare_pro 的 daily / weekly，保留其它元数据列 label、顺序、来源列及格式化行为。
- [x] 固定列、表头、hover、tooltip 和空态使用共享主题；布局每层 `min-width: 0`，分页窄屏换行，表格横向滚动不撑开页面。
- [x] 运行 `npm test -- src/views/DatasetView.spec.js src/components/dataset src/composables/useDatasetQuery.spec.js src/composables/useDatasetFilters.spec.js src/utils/format.spec.js src/api/api.spec.js`；全部通过后核对范围、加入 Git 并独立提交。

**Acceptance:** 全业务列与来源列可见，宽表格内部滚动；数值精度和单位正确；筛选、单边日期、重置、分页、错误重试和无 ts_code 数据集均可用；跨页同职责重复 UI 已共用组件，两页行为回归通过。

### ISSUE-004-T06：正式前端回归与视觉验收

**Files:** 新建 `control-plane/playwright.ui.config.js`、`control-plane/e2e/ui-redesign.spec.js`、`docs/verification/ISSUE-004-ui-redesign.md`；按验收结果更新 `docs/issues/README.md`、`docs/issues/problems/ISSUE-004-ui-visual-redesign.md`。前五项发现的缺陷在其原职责文件定点修正。

**Interfaces:** 使用构建后的真实 Vue 页面；Playwright route stub 仅实现 `src/api` 现有 DTO 和路径。新配置不收集旧的 JVM / 真实 Tushare spec。

- [ ] 建立独立配置，复用已有 Chromium 和本地 preview：

```js
import { defineConfig, devices } from '@playwright/test'
export default defineConfig({
  testDir: './e2e', testMatch: 'ui-redesign.spec.js',
  workers: 1, retries: 0, reporter: 'list',
  outputDir: 'node_modules/.cache/tensor-ui-playwright',
  use: { baseURL: 'http://127.0.0.1:4173', screenshot: 'only-on-failure' },
  webServer: {
    command: 'npm run preview -- --host 127.0.0.1 --port 4173 --strictPort',
    url: 'http://127.0.0.1:4173', reuseExistingServer: false,
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
```

- [ ] 在新 spec 中构造全部 49 接口身份及参数 / 筛选矩阵；来源见总体设计的 Tests，明确 manifest 本身不包含完整参数元数据。构造响应时依据 `src/api/downloads.js`、`datasets.js`、`errors.js` 的真实字段，设置匹配的 `X-Request-Id`；未声明的 API 请求直接使测试失败。
- [ ] 覆盖三页往返、主题取色 / HEX / 存储 / 重置、延迟响应、单日期与原生起止参数的一次提交 / 原参数重试、查询 / 重置 / 翻页；独立上下文检查设置新开为零 API。加入 DECIMAL 高精度字符串、LONG 大整数、空值、长文本和 152+3 列响应。
- [ ] 一批检查 1440、1024、768、390、360 宽度；断言 `document.documentElement.scrollWidth <= innerWidth`，表格可内部滚动且固定列正确。对默认、`#b52c63`、`#ffff00`、`#000000` 检查主操作、背景、按钮白字及弹层样式，切主题前后同状态的布局矩形相等。
- [ ] 捕获三页桌面 / 窄屏截图，检查信息层次、真实计数、文字截断、错误、日期弹层和键盘焦点，并与已确认 HTML 对照。一次集中修正，最多再做一次针对性确认。
- [ ] 在 `control-plane` 运行 `npm test`、`npm run build`、`npx playwright test --config=playwright.ui.config.js --project=chromium --workers=1 --retries=0`；记录退出码、用例数、截图位置和局限。新增截图只保存可重现的合成数据。
- [ ] 将可分享截图保存至 `docs/verification/ISSUE-004-ui-redesign/`，与验收文档一起加入 Git；不把 node_modules 缓存或测试临时输出加入仓库。
- [ ] 汇总 T01–T05 的复用检查，在验收文档列出共享组件 / 逻辑、实际调用位置和相关回归结果；检查同职责重复代码已替换、抽象接口简单，公共组件不是建好后无人使用。
- [ ] 对照 ISSUE 关闭条件逐项记录证据；全部通过后按看板流程完成本任务，再更新 ISSUE 为已解决。未通过项不能以 120 项历史基线或 HTML 预览检查代替。

**Acceptance:** 六项任务的结果级条件全部满足；新正式页面的单测、构建、API 请求断言、49 项 UI 元数据覆盖、跨页和响应式 / 视觉验收通过并可追踪；组件与逻辑的实际复用及调用处回归有记录。

## 本轮规划验证

2026-09-07 使用本机 Node 24.15.0 验证未修改的正式前端：`npm test` 为 20 文件 / 120 项通过；`npm run build` 退出 0，有既有 500 kB chunk 提示。这是实施前基线，不代表上述步骤已执行，也不代表 UI 改版验收通过。
