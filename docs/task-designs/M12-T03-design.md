# M12-T03 20/50/100 分页组件——任务设计

任务编号：`M12-T03`

对应任务：[M12-T03](../superpowers/plans/tensor-modules/M12-dataset-ui.md#task-m12-t03-服务端分页组件20h)

实施产物：只展示服务端分页状态并向调用方发送页码或每页条数变更的受控数据集分页组件

## Goal

在现有 Vue 3、Element Plus、Vitest 和 Vue Test Utils 基线上交付 `DatasetPagination`。后续数据查看页传入服务端已经归一化的 `page`、`pageSize`、`totalElements`、`totalPages` 和查询禁用状态后，组件必须完整显示总记录数、当前页、总页数以及精确的 20/50/100 每页条数选择，并通过两个受控事件把用户意图交给调用方。

组件不保存查询状态、不请求数据、不根据总数重算页数，也不自行把每页条数变化转换成第 1 页查询。M12-T04/M12-T05 负责保留筛选条件、修改每页条数后回到第 1 页、接受后端超界页归一结果以及在加载期间传入 `disabled=true`。

## Scope

包含：

- 创建只消费 `page/pageSize/totalElements/totalPages/disabled` 的受控 `DatasetPagination`；
- 固定提供 `[20, 50, 100]` 三个 page-size 选项，`pageSize` 默认 50，`page` 默认 1；
- 显示精确中文摘要 `共 N 条，第 P / T 页`，包含服务端返回的总记录数、当前页和总页数；
- 通过 `update:page` 和 `update:pageSize` 分别发送翻页与每页条数变化，不修改 prop；
- 使用 Element Plus 原生分页按钮和选择器提供可见的上一页/下一页标签、ARIA 名称、焦点和键盘操作；
- `totalPages=0` 时仍显示 `共 0 条，第 1 / 0 页` 并保留可用的 page-size 选择器；
- 只在 `disabled=true` 时禁用整个分页控件并阻止新事件，零页只让没有合法目标的翻页按钮保持边界禁用；
- 用一份真实组件测试覆盖默认值、固定选项、正常摘要、受控事件、禁用和零页；
- 执行严格 RED、聚焦测试、完整前端回归、生产构建及范围/安全检查。

排除：

- 不修改 `DatasetView.vue`、router、layout、全局样式、API 客户端、composable、共享组件、依赖或配置；
- 不创建 M12-T04/M12-T05 的查询生命周期或页面组合，不发起网络请求；
- 不保存或复制 page/pageSize，不直接修改 prop，不在 page-size 变化时额外发送 `update:page`；
- 不根据 `totalElements/pageSize` 计算 `totalPages`，不对服务端 page 或 totals 做截断、修正、回退或猜测；
- 不管理筛选条件、数据源、数据集、结果表格、空/失败提示、重试或请求世代；
- 不提供跳页输入、客户端数据切片、无限滚动、全量加载、排序、导出或写操作；
- 不引入第三方分页、状态或无障碍依赖，不修改 OpenAPI、Java 或 SQL。

## Approach

### 公开接口与受控边界

`control-plane/src/components/dataset/DatasetPagination.vue` 的公开接口固定为：

```text
props:
  page: number，默认 1；服务端返回的一基页码
  pageSize: number，默认 50；OpenAPI 值域为 20 | 50 | 100
  totalElements: number，默认 0；服务端返回的非负总记录数
  totalPages: number，默认 0；服务端返回的非负总页数
  disabled: boolean，默认 false
emits:
  update:page(nextPage: number)
  update:pageSize(nextPageSize: number)
expose: none
```

数字 prop 只声明 Vue `Number` 类型和上述默认值。组件信任 OpenAPI `PageResponse` 已满足 `page >= 1`、`pageSize in [20,50,100]`、totals 非负以及空结果 `page=1,totalPages=0,totalElements=0`，不复制服务端 schema validator，也不规范化输入。固定 page-size 数组在组件模块内声明一次，不从 prop、总数或当前结果生成。

公开事件名严格为项目所有者批准的 `update:page` 与 `update:pageSize`。`ElPagination` 的 `update:current-page` 只转发为 `update:page`；`update:page-size` 只转发为 `update:pageSize`。两个处理器在 `disabled=true` 时直接返回，其他情况下原样发送 Element Plus 给出的 number。组件不发送第三种业务事件，不在 page-size 变化时发送页码 1，也不改变当前 prop；父层收到 size 事件后负责把查询页码重置为 1。

### 服务端页数与控件组成

根元素使用原生 `<nav aria-label="数据集分页">`，并令 `aria-disabled` 只等于 `disabled`。其中先渲染分页摘要，再渲染一个受控 `el-pagination`：

```text
current-page = page
page-size = pageSize
page-count = totalPages
page-sizes = [20, 50, 100]
layout = "sizes, prev, pager, next"
prev-text = "上一页"
next-text = "下一页"
disabled = disabled
hide-on-single-page = false
```

`page-count` 而非 `totalElements/pageSize` 驱动页码按钮，因此服务端 `totalPages` 是唯一页数事实；组件不把 `totalElements` 传给 Element Plus 触发另一套页数计算。固定 layout 始终保留 sizes、上一页、页码和下一页，不提供 jumper。`hide-on-single-page=false` 保证零页和单页不会隐藏整个分页区域。

Element Plus 2.14.5 的 sizes 控件在显式 `page-count` 与 page-size listener 下可用。上一页、下一页使用原生 `button type="button"`，页大小使用带 combobox 语义的 `el-select`；保留库提供的焦点样式、键盘行为和 ARIA，不覆盖内部 DOM 或键盘事件。可见的“上一页”“下一页”和外层“数据集分页”名称让控件不只依赖图标。

### 摘要、零页与禁用语义

摘要固定使用 Vue 文本插值生成：

```text
共 {{ totalElements }} 条，第 {{ page }} / {{ totalPages }} 页
```

摘要节点设置 `role="status"`、`aria-live="polite"` 和 `aria-atomic="true"`，使成功查询后的总数/页码变化可整体播报，同时不使用 `v-html`。正常示例 `page=2,totalElements=123,totalPages=7` 必须显示 `共 123 条，第 2 / 7 页`；即使 `123/50` 与 7 不一致也显示 7，以证明没有客户端重算。

空结果严格保持后端合同：`page=1,totalElements=0,totalPages=0` 显示 `共 0 条，第 1 / 0 页`。此时仍向 `ElPagination` 传 `disabled=false`（除非调用方显式禁用），page-size 选择器继续可聚焦并可发送 20/50/100 变化；上一页/下一页因不存在合法目标页而由 Element Plus 执行边界禁用。这不等于整体禁用，根 `aria-disabled` 和子组件 `disabled` 仍为 false。

`disabled=true` 时根 `aria-disabled="true"` 且 `ElPagination.disabled=true`，选择器、上一页、下一页和页码交互均由 Element Plus 禁用；包装事件处理器再做一次 guard，防止测试或程序化子事件绕过控件状态。摘要仍可见，不增加 loading 文案或遮罩；M12-T04/M12-T05 负责在查询中传入该 prop 并展示查询状态。

### 样式与失败边界

scoped style 只让根分页区可换行对齐摘要和 Element Plus 控件，并保留合理间距；不重置按钮 outline、不隐藏标签、不查询或覆盖 `.el-pagination` 内部结构。1280px 桌面布局和更窄页面的滚动由既有 layout 负责。

组件没有异步流程和业务异常分支。非法服务器状态应在 API/查询层暴露，分页组件不吞掉、记录或修补；空结果不是失败。严格 TDD 先完整创建唯一 spec，在生产组件不存在时取得可归因收集失败，再创建最小组件。

## Files

创建且只创建以下 2 个实施文件：

- `control-plane/src/components/dataset/DatasetPagination.vue`：受控分页、服务端 totals 摘要、固定 page-size 选项、禁用与零页语义；
- `control-plane/src/components/dataset/DatasetPagination.spec.js`：六项真实组件行为测试。

不修改或删除其他生产、测试、依赖、配置和文档文件。初始实现提交固定为 `feat(ui): add server dataset pagination`，精确包含上述 2 个新增文件；本设计、交接和看板不得混入实现提交。

## Tests

所有 npm 命令从仓库根目录开始，使用 Node.js 24.15.0：

```bash
TENSOR_NODE=/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin/node
TENSOR_NPM=/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/lib/node_modules/npm/bin/npm-cli.js
"$TENSOR_NODE" --version
```

预期输出 `v24.15.0`。M12-T02 最终基线为 17 个测试文件、98 项测试；开始实施时须重新运行基线，若既有测试失败，先停止并定位，不能记作本任务 RED。

### 严格 RED

只创建完整 `DatasetPagination.spec.js`，不创建 `DatasetPagination.vue`，然后运行：

```bash
cd control-plane
"$TENSOR_NODE" "$TENSOR_NPM" run test:unit -- --run src/components/dataset/DatasetPagination.spec.js
```

预期命令非零，Vitest 只因 `DatasetPagination.vue` 不存在而无法收集该测试文件；不得出现测试语法、依赖、Node、Element Plus、Vue setup 或既有测试失败。

### `DatasetPagination` 六项

1. 省略全部可选 prop 时，根 `nav` 的可访问名称为“数据集分页”，`ElPagination` 收到 `currentPage=1`、`pageSize=50`、`pageCount=0`、`pageSizes=[20,50,100]`、固定 layout、可见上一页/下一页文本及 `hideOnSinglePage=false`；输入 page-size 数组不暴露为可变 prop；
2. 传入 `page=2,pageSize=50,totalElements=123,totalPages=7` 时，真实状态节点精确显示 `共 123 条，第 2 / 7 页` 并具有 polite/atomic live-region 语义；更新 props 后文本同步变化，且页数始终等于传入 `totalPages` 而不是按总数重算；
3. 聚焦真实下一页原生按钮并点击后只发送一次 `update:page(3)`，按钮保留 `type=button`、可见标签和 Element Plus ARIA；当前 `page` prop 仍为 2，证明组件受控且键盘可通过原生按钮完成翻页；
4. 确认 page-size 选择器只包含 20、50、100 且当前为 50；令真实 Element Plus sizes 控件选择 100 后只发送一次 `update:pageSize(100)`，不发送 `update:page`，传入 `pageSize` 仍为 50；
5. `disabled=true` 时根和 `ElPagination` 均标记禁用，真实翻页按钮与 page-size 选择器不可交互；直接触发子组件的页码/size 更新事件也不产生公开事件。切换为 false 后控件恢复可用；
6. `page=1,pageSize=50,totalElements=0,totalPages=0,disabled=false` 时精确显示 `共 0 条，第 1 / 0 页`，分页组件没有整体禁用且 page-size 选择器仍存在、可聚焦并可发送 size 变化；只有没有合法目标页的上一页/下一页按钮保持边界禁用。

每项测试在写测试体前必须能命名一个会令其失败的生产缺陷；期望值使用字面值，不从组件常量或实现逻辑生成。真实 Element Plus 子组件和 DOM 用于验证交互与无障碍表面，不用浅渲染 stub 代替。

### GREEN、回归与构建

最小创建 `DatasetPagination.vue` 后运行：

```bash
"$TENSOR_NODE" "$TENSOR_NPM" run test:unit -- --run src/components/dataset/DatasetPagination.spec.js
"$TENSOR_NODE" "$TENSOR_NPM" run test:unit -- --run
"$TENSOR_NODE" "$TENSOR_NPM" run build
```

预期：聚焦测试为 1 file / 6 tests 全部通过；在 17/98 基线上，完整前端回归为 18 files / 104 tests 全部通过；Vite 生产构建退出 0，只允许既有 Element Plus `Some chunks are larger than 500 kB after minification` 提示，不允许新增 Vue、Element Plus、可访问性、测试或编译 warning/error。

### 范围、格式与安全检查

```bash
cd ..
git diff --check
git status --short --untracked-files=all -- control-plane/src/components/dataset
git diff --exit-code -- \
  control-plane/package.json control-plane/package-lock.json \
  control-plane/vite.config.js control-plane/vitest.config.js \
  control-plane/playwright.config.js control-plane/src/api \
  control-plane/src/composables control-plane/src/router \
  control-plane/src/layouts control-plane/src/views \
  control-plane/src/style.css control-plane/src/utils
rg -n 'v-html|innerHTML|axios|fetch\(|queryDataset|setTimeout|setInterval|Math\.(ceil|floor|round)|slice\(|totalElements\s*/|emit\(.update:page.[^)]*1' \
  control-plane/src/components/dataset/DatasetPagination.vue
```

预期：格式和受保护路径检查退出 0；范围化 status 只新增 Files 节两个实施文件；禁止 HTML 注入、网络、查询、定时器、客户端页数计算/切片及 size 变化隐式页码重置的扫描无输出并按预期退出 1。暂存前后检查完整 `git diff --cached`；只以精确路径提交两个任务文件，不能混入工作树中已有的 `.idea`、`docs/issues` 或 `data-plane/**/target/` 变化。

## Acceptance

- `DatasetPagination` 只接收 `page/pageSize/totalElements/totalPages/disabled`，默认 1/50/0/0/false，且只发送 `update:page`、`update:pageSize`；
- page-size 选择固定且仅为 20、50、100，默认 50；改变 size 不由组件额外发送页码 1，调用方负责重置并查询；
- 可见摘要精确显示服务端总记录数、当前页和总页数；`page-count` 直接使用 `totalPages`，不从 total/pageSize 重算或规范化任何服务端状态；
- 正常翻页使用受控事件且不修改 prop；上一页、下一页和 sizes 控件具有可见标签、焦点、键盘及 ARIA 语义，焦点样式未被覆盖；
- 空结果显示 `共 0 条，第 1 / 0 页`，保留且启用 page-size 选择器，分页整体不因零页自动禁用；无合法目标的翻页按钮保持边界禁用；
- 只有 `disabled=true` 才禁用整体控件并阻止公开事件；摘要仍显示，组件不自行增加 loading 或错误状态；
- 组件不请求网络、不保存查询状态、不管理筛选/竞态/空态/失败、不切片数据、不重算页数，也不修改页面或共享模块；
- Node.js 24.15.0 下严格 RED 原因正确，聚焦 6/6、完整前端 104/104、生产构建、范围、安全和格式门禁达到预期；
- 初始实现提交精确包含 2 个新增文件，提交消息为 `feat(ui): add server dataset pagination`。

## Risks

- Element Plus 在 `pageCount=0` 时会边界禁用上一页/下一页，这是避免发出无效页码的单项控件行为；不得把它误改为整个 `ElPagination.disabled=true`，sizes 选择器必须保持可用。
- 同时把 `totalElements` 和 `totalPages` 交给分页库可能产生客户端派生页数；实现只把 `totalPages` 作为 `page-count`，总数仅用于自有摘要文本。
- jsdom 不会完整模拟浏览器由 Enter/Space 到 click 的默认动作；单元测试锁定原生 button/combobox、可见标签、ARIA、焦点和真实 click/选择事件，端到端键盘闭环留给 M14-T03。
- `totalElements/totalPages` 来自 OpenAPI int64，而当前 JavaScript API DTO 使用 number；本任务按既有客户端合同原样显示和传递，不扩大为 BigInt/string 协议变更。
- 工作树存在与本任务无关的 `.idea`、`docs/issues` 和后端 `target/` 变化；设计、交接和后续实施提交必须使用精确路径，检查完整暂存区并保留这些用户变化。
