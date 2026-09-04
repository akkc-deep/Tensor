# M10-T02 `/downloads`、`/datasets` 路由和桌面布局——任务设计

任务编号：`M10-T02`

对应任务：[M10-T02](../superpowers/plans/tensor-modules/M10-frontend-foundation.md#task-m10-t02-路由与桌面布局20h)

实施产物：具有固定顶部一级导航、两个稳定业务入口和轻量 404 的 Tensor 桌面端应用壳

## Goal

把当前 Vue/Vite 示例页替换为 Tensor 控制面的稳定路由与桌面布局：`/` 重定向到 `/downloads`，`/downloads` 与 `/datasets` 分别承载数据下载和数据查看页面，未知路径进入可恢复的 404；两个业务页面先呈现最终标题和明确的“模块尚未完成”引导，供 M11、M12 只替换页面主体而不改变路由、导航或应用壳。

采用项目所有者批准的方案 1：使用语义化 `header`/`nav`、两个真实 `RouterLink` 和 `main`/`RouterView` 实现顶部导航，不以 Element Plus 菜单或侧边栏增加组件层级。页面在桌面 Chrome 和 1280px 基线下完整展示；较窄视口允许整页横向滚动，导航和主内容不折叠或隐藏。

## Scope

包含：

- 创建 Vue Router 4 路由表和可注入 history 的 router factory，固定 `downloads`、`datasets`、`not-found` 三个路由名；
- 创建语义化顶部导航应用壳，导航项固定为“数据下载”和“数据查看”，当前页面由 Vue Router 的 `aria-current="page"` 与可见激活样式共同表达；
- 创建 `DownloadView`、`DatasetView` 的最终页面标题和明确未完成引导，以及提供“返回数据下载”入口的轻量 `NotFoundView`；
- 让 `App.vue` 只承载应用壳，让 `main.js` 安装 router 与 Element Plus，并加载 Element Plus 基础样式；
- 重写示例 CSS 为 Tensor 桌面壳样式，保留键盘焦点，固定 1280px 最小页面宽度和横向滚动行为；
- 严格 TDD 创建 router/layout 测试，并把 M10-T01 的 App smoke test 更新为真实路由应用壳测试；
- 删除路由壳不再引用的 Vite/Vue 示例组件和资源；
- 在 Node 24.15+ 下运行聚焦测试、完整单测和生产构建，并以精确文件范围提交。

排除：

- 不实现数据源、接口、参数、下载结果、数据集、筛选、表格、分页或任何 API 请求；这些分别属于 M10-T03～T04、M11 和 M12；
- 不创建 Axios client、DTO、composable、Pinia store、格式化/校验工具、通用异步状态组件或 E2E 用例；
- 不增加登录、权限、移动端折叠菜单、面包屑、页签、主题切换、路由懒加载、跨页面状态保留或页面过渡动画；
- 不修改 `package.json`、`package-lock.json`、Vite/Vitest/Playwright 配置、`index.html`、public favicon、README、OpenAPI、后端或其他已完成任务产物；
- 不启动后端、不调用网络、不安装 Playwright 浏览器，也不把零 E2E 用例表示为页面流程通过。

## Approach

### Router contract

`control-plane/src/router/index.js` 固定导出一个 factory 和一个生产 router：

```js
export function createAppRouter(history = createWebHistory())
export default createAppRouter()
```

factory 接收 Vue Router history，使单测使用 `createMemoryHistory()` 隔离导航状态，生产默认使用无 hash 的 `createWebHistory()`。路由表保持声明顺序：

| Path | Name | Component/behavior |
|---|---|---|
| `/` | 无 | `{ name: 'downloads' }` 重定向 |
| `/downloads` | `downloads` | `DownloadView` |
| `/datasets` | `datasets` | `DatasetView` |
| `/:pathMatch(.*)*` | `not-found` | `NotFoundView` |

页面组件使用静态 import；本任务只有三个轻量页面，不增加懒加载分块和 loading/error 分支。404 必须保持浏览器地址不变，由页面内 `RouterLink` 返回命名路由 `downloads`。生产 `createWebHistory()` 所需的服务端 SPA fallback 仍由 M13-T03 实现，本任务不越界修改后端。

### Application shell and navigation

`App.vue` 只渲染 `AppLayout`。`AppLayout.vue` 的 DOM 层级固定为：

```text
div.tensor-shell
├── header.tensor-header
│   └── div.tensor-header__inner
│       ├── div.tensor-brand       -> 文本 Tensor
│       └── nav.tensor-nav         -> aria-label="主导航"
│           ├── RouterLink         -> 数据下载 / named route downloads
│           └── RouterLink         -> 数据查看 / named route datasets
└── main.tensor-main
    └── RouterView
```

导航链接不使用 click handler，不阻止浏览器默认焦点，也不复制路由状态。Vue Router 自动为当前链接设置 `aria-current="page"`；CSS 同时对 `.router-link-active` 使用主色文字和浅色背景，错误不能只靠颜色表达的规则由 `aria-current` 满足。焦点使用显式 3px outline 和 offset，不能设置 `outline: none`。header 在每个路由都渲染，但不使用 `position: fixed|sticky`，避免遮挡锚点或引入高度补偿。

`main.js` 固定按以下职责组装生产入口：加载 `element-plus/dist/index.css` 与项目 `style.css`，创建 App，安装生产 router 和 Element Plus，再挂载 `#app`。Element Plus 的全局安装是后续 M11/M12 控件在真实页面运行的基础；导航本身仍使用语义 HTML 与 RouterLink，不使用 `el-menu`。

### Placeholder views

三个 view 均为无脚本、无状态、无网络的单根 `section.page`，并用 `aria-labelledby` 关联唯一 `h1`：

- `DownloadView.vue`：`id="downloads-title"` 的标题“数据下载”；正文“数据下载模块尚未完成，后续任务将提供数据源、接口、参数和下载结果。”；
- `DatasetView.vue`：`id="datasets-title"` 的标题“数据查看”；正文“数据查看模块尚未完成，后续任务将提供数据集筛选、表格和分页。”；
- `NotFoundView.vue`：`id="not-found-title"` 的标题“页面不存在”；正文“当前地址不存在。”；随后提供文本“返回数据下载”的 `RouterLink`，目标为 `{ name: 'downloads' }`。

初始引导不是加载、成功或失败状态，因此本任务不添加 `aria-live`；M10-T04 将建立统一异步状态组件。两个业务页面不创建假表单、禁用按钮或空表格，也不以占位数据伪装已完成业务功能。

### Desktop CSS

`style.css` 删除全部 Vite 示例选择器和嵌套 demo 样式，固定以下最小规则：

- `:root` 使用系统无衬线字体、`color-scheme: light`、Element Plus 的背景/文字/边框变量及对应安全 fallback，并定义壳层专用 `--tensor-interactive-color: #1f5f99`；该颜色对白色和 active 浅蓝 `#ecf5ff` 的对比度分别为 6.656:1 和 6.045:1；
- `* { box-sizing: border-box; }`，`html`、`body`、`#app` 至少占满视口高度；
- `body` 为零 margin 且 `min-width: 1280px`，较窄 viewport 因此产生浏览器原生横向滚动，不使用 `overflow-x: hidden`；
- header 为白色背景和下边框，内部高度 64px、左右 padding 32px，品牌与导航横向排列；品牌字号 20px、字重 600，导航链接间距 8px、单项 padding 8px 16px；
- 导航链接为无下划线圆角文本，hover、active、404 返回链接与 focus outline 使用 `--tensor-interactive-color`，active 具有 Element Plus 浅色背景；`:focus-visible` 使用 3px outline 和 3px offset；
- `main` 使用 32px padding；`.page` 使用白色背景、1px 边框、8px 圆角和 32px padding；页面 `h1` 为 28px 且正文保持可读行高；
- 不增加窄屏 media query，不折叠导航，不隐藏主操作，不恢复示例 dark mode 或动画。

### TDD and failure handling

先完整创建 `router/index.spec.js`、`layouts/AppLayout.spec.js`，并把现有 `App.spec.js` 改成路由应用壳 smoke test；此时不创建 router/layout/views，也不修改生产入口，聚焦命令必须因目标模块不存在而 RED。测试要捕获的生产回归分别是错误路由表/重定向、未知路径未进入 404、导航语义或激活态丢失、链接不可获得键盘焦点，以及根 App 未接入布局。

GREEN 只实现本设计的 router、layout、三个 view、入口和 CSS，然后删除确定无引用的示例文件。路由和 layout 测试使用真实 Vue Router memory history、真实 RouterLink/RouterView 和真实 view，不 stub 路由组件、不断言 mock 元素；layout 测试加载真实 `style.css`，解析最终交互色并验证 active/404 链接在批准背景上的对比度至少 4.5:1。任何 Vue warning、未处理导航 rejection、测试后残留 DOM、丢失资源或下述已批准提示之外的构建警告都视为缺陷；不得通过静默 warning、跳过用例或保留无引用示例资源绕过。

2026-09-04，项目所有者批准把生产入口按既定合同全量安装 Element Plus 后，Vite 对约 1.00 MB JS 超过默认 500 kB 阈值产生的唯一 `Some chunks are larger than 500 kB after minification` 提示记录为非阻断风险。本任务保持 `.use(ElementPlus)`、`element-plus/dist/index.css`、既有 Vite 配置与 16 文件范围，不提高 `chunkSizeWarningLimit` 掩盖 bundle 体积，也不改成会改变 M11/M12 消费合同的按需注册；构建仍必须退出 0，且不能出现其他 warning 或 error。

## Files

创建：

- `control-plane/src/router/index.js`：四条路由、三个稳定路由名、可注入 history 的 factory 与生产 router；
- `control-plane/src/layouts/AppLayout.vue`：品牌、语义化顶部导航、主内容和 RouterView；
- `control-plane/src/views/DownloadView.vue`：最终“数据下载”标题与未完成引导；
- `control-plane/src/views/DatasetView.vue`：最终“数据查看”标题与未完成引导；
- `control-plane/src/views/NotFoundView.vue`：轻量 404 和返回下载页入口；
- `control-plane/src/router/index.spec.js`：3 个真实 router 场景；
- `control-plane/src/layouts/AppLayout.spec.js`：3 个真实导航、激活、焦点和 404 恢复场景。

修改：

- `control-plane/src/App.vue`：从示例组件切换为唯一 `AppLayout`；
- `control-plane/src/main.js`：安装生产 router 和 Element Plus，并加载 Element Plus/project CSS；
- `control-plane/src/style.css`：以固定桌面导航、页面壳、焦点和横向滚动规则替换示例 CSS；
- `control-plane/src/App.spec.js`：保留 1 个真实 mount smoke test，改为使用 memory router 验证根 App 接入应用壳和下载入口。

删除：

- `control-plane/src/components/HelloWorld.vue`；
- `control-plane/src/assets/hero.png`；
- `control-plane/src/assets/vite.svg`；
- `control-plane/src/assets/vue.svg`；
- `control-plane/public/icons.svg`。

不创建、修改或删除其他文件。实现提交固定为 `feat(ui): add Tensor routes and desktop layout`，精确包含上述 16 个文件（7 新增、4 修改、5 删除）；设计、交接、看板、`node_modules`、`dist` 和测试产物不得混入实现提交。

## Tests

所有命令从仓库根开始；npm/Vitest/Vite 命令均在 Node `>=24.15.0 <25` 下运行。

1. 前置与 M10-T01 基线：

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

预期：开始实现前工作树为空；Node 检查退出 0；官方 registry 可从提交 lockfile 安装 172 packages 且审计 173 packages/0 vulnerabilities；M10-T01 基线为 1 file/1 test；构建退出 0。

2. 严格 RED：先创建两个完整新测试并修改现有 App smoke test，不创建任何目标生产文件，运行：

```bash
npm run test:unit -- --run \
  src/App.spec.js \
  src/router/index.spec.js \
  src/layouts/AppLayout.spec.js
```

预期：退出非 0，直接原因只是不解析 `src/router/index.js`、`src/layouts/AppLayout.vue` 或其 view import；测试语法、Vue/VTU API、Node 版本和依赖安装不是有效 RED。

3. GREEN 聚焦测试固定 7 项：

- router factory 暴露 named `/downloads` 与 `/datasets` 路由；
- 导航 `/` 后当前 route 为 `downloads` 和 `/downloads`；
- 未知路径保持原 path 并命中 `not-found`；
- 应用壳恰有一个 `nav[aria-label="主导航"]`、两个真实链接，下载链接在下载页具有 `aria-current="page"`，并显示批准的下载模块未完成引导；
- 数据查看链接可取得 DOM focus，点击后 route/view/`aria-current` 切换且下载链接不再 active，并显示批准的数据查看模块未完成引导；
- 未知地址显示“页面不存在”和“当前地址不存在。”，并提供 href 为 `/downloads` 的“返回数据下载”链接；
- 根 App 通过 memory router 真实挂载，恰有一个 header、nav、main 和标题“数据下载”。

实现后重跑同一聚焦命令，预期 3 files/7 tests 全部通过，无 warning、error 或网络请求。

4. 完整回归与构建：

```bash
npm test
npm run build
```

预期：3 files/7 tests 全部通过；Vite 生产构建退出 0，生成 `dist/index.html` 与哈希 assets，不出现未解析组件、路由、CSS、示例资源、chunk 错误或其他 warning；仅允许 TDD and failure handling 中批准的 Element Plus chunk-size 提示。

5. 示例清理合同：

```bash
rg -n 'HelloWorld|hero\.png|vite\.svg|vue\.svg|icons\.svg' src public
for path in \
  src/components/HelloWorld.vue \
  src/assets/hero.png src/assets/vite.svg src/assets/vue.svg \
  public/icons.svg; do
  test ! -e "$path"
done
```

预期：示例引用扫描无输出并按预期退出 1，五个目标路径均不存在。路由合同已由第 3、4 项真实 jsdom 测试覆盖；不使用缺少浏览器 `window` 的裸 Node 导入冒充生产路由验证。`public/favicon.svg` 仍由 `index.html` 引用，不属于无引用删除范围。

6. 范围、安全、格式和 Git：

```bash
cd ..
git diff --check
git status --short --untracked-files=all -- control-plane
rg -n 'token|password|authorization|cookie|VITE_' \
  control-plane/src/App.vue control-plane/src/main.js \
  control-plane/src/style.css control-plane/src/App.spec.js \
  control-plane/src/router control-plane/src/layouts control-plane/src/views
git check-ignore control-plane/node_modules control-plane/dist \
  control-plane/node_modules/.cache/tensor-playwright
```

预期：格式检查退出 0；status 精确为 Files 节 7 新增、4 修改、5 删除；敏感/浏览器暴露前缀扫描无输出并按预期退出 1；三个生成路径继续被忽略。暂存后 `git diff --cached --name-status` 保持同一 16 文件集合，提交消息精确为 `feat(ui): add Tensor routes and desktop layout`。

## Acceptance

- `/` 真实重定向到 named route `downloads`；`/downloads`、`/datasets` 和 catch-all 404 路由按固定 name/path/component 工作，未知地址不被重写成其他前端地址；
- 每个页面都有且只有一个最终标题：数据下载、数据查看或页面不存在；两个业务页显示明确未完成引导且不伪造表单、数据或业务状态，404 可返回数据下载页；
- 应用每页都显示语义化顶部导航，导航内恰有“数据下载”“数据查看”两个 RouterLink；当前项同时具有 `aria-current="page"` 与可见激活样式，链接可获得键盘焦点且焦点轮廓未被移除；壳层交互色对白色和 active 浅蓝的对比度均至少 4.5:1，因此同色 focus outline 也超过 3:1；
- `main.js` 在生产入口安装同一 router 和 Element Plus，`App.vue` 只承载应用壳；测试不以全局 setup 掩盖生产入口缺少 Element Plus 注册；
- 1280px 及以上完整呈现 header/nav/main；更窄 viewport 使用浏览器横向滚动，不折叠、不隐藏导航或未来主操作；
- M10-T01 的 Vue/Vitest/VTU/Playwright/package 基线保持不变，3 files/7 tests 与生产构建通过且不访问网络；构建最多只出现已批准的 Element Plus chunk-size 提示，不出现其他 warning 或 error；
- 所有已无引用的示例组件和资源按 Files 节删除，仍被 `index.html` 引用的 favicon 保留；未引入 API client、业务页面实现、状态存储、E2E 或后端变更；
- 实现提交精确为 Files 节 16 个文件（7 新增、4 修改、5 删除），提交消息固定且不包含设计、交接、看板或生成物。

## Risks

- 全量 `.use(ElementPlus)` 为后续 M11/M12 保留全局组件消费合同，但当前生产 bundle 约 1.00 MB JS，并触发 Vite 默认 500 kB chunk-size 提示。项目所有者已批准该唯一提示不阻断 M10-T02；本任务不调高阈值或越界优化，后续若有真实加载性能目标，应另行设计按需注册或拆包并验证对 M11/M12 的兼容性。
- 生产 router 使用 `createWebHistory()`；开发服务器可处理 history fallback，但打包 JAR 对 `/downloads`、`/datasets` 和未知前端地址的直接刷新支持明确属于 M13-T03。本任务只保证客户端导航和生产 bundle，不把后端 fallback 未实现误报为路由缺陷。
- `src/test/setup.js` 已全局安装 Element Plus，可能让组件测试在 `main.js` 未安装生产 plugin 时仍通过；因此本设计明确要求 `main.js` 安装 Element Plus，并用入口/范围审查确认，不能只依赖测试 setup。
- Vitest/jsdom 能证明语义导航、真实路由、active 属性和 DOM focus，不能替代桌面 Chrome 的像素布局、横向滚动和焦点可见性 E2E；本任务以 CSS/构建门禁冻结基线，最终浏览器行为由 M14-T03 验证。
