# M11-T01 数据源与接口分组搜索选择组件——任务设计

任务编号：`M11-T01`

对应任务：[M11-T01](../superpowers/plans/tensor-modules/M11-download-ui.md#task-m11-t01-数据源与接口选择25h)

实施产物：供下载页组合的数据源选择、接口分组搜索和接口说明三个受控 Vue 组件

## Goal

在现有 Vue 3、Element Plus、Vitest 和 M10 API 描述符基础上，交付三个不发起请求、不保存页面业务状态的受控组件。用户可以看见并选择已注册数据源，在数据源不可下载时看见公开的不可用原因；可以在当前来源的接口描述符中按分类浏览全部选项，按接口名或中文显示名搜索并选择一个接口；选择后可以看见接口标识、中文说明和查询方式。

本任务只建立选择与说明边界。M11-T03 负责加载元数据、持有选择状态、调用 M10 API 以及在切换来源或接口后清空参数、校验和下载结果；M11-T05 负责页面组合。组件只消费调用方传入的 M10 标准化描述符并发出 `update:modelValue`。

## Scope

包含：

- 创建 `DataSourceSelect.vue`，显示数据源、单来源默认选择、不可用选项和不可用原因；
- 创建 `ApiSelect.vue`，按描述符 `category` 原值分组，支持 `apiName`/`displayName` 不区分大小写的包含搜索并发出选择更新；
- 创建 `ApiDescription.vue`，安全展示当前接口的标识、中文显示名、分类和查询方式；
- 创建三个同目录测试文件，使用真实 Element Plus 组件验证可见标签、禁用、默认选择、当前 49 接口的七组投影、搜索、键盘选择、事件和纯文本渲染；
- 在 Node.js 24.15.0 下执行严格 RED、聚焦测试、完整前端单测和生产构建。

排除：

- 不修改 M10 API、通用组件、工具、路由、布局、页面、依赖、配置、Java、YAML、OpenAPI 或 PRD；
- 不调用 `listDataSources`、`listApis` 或其他网络函数，不解释 `ApiError`/`ClientError`，不管理加载、失败、重试或请求世代；
- 不渲染参数表单、下载按钮或下载结果，不在组件内清空下游状态；
- 不自动选择接口，不按 `apiName` 写分支，不嵌入 49 接口清单；
- 暂不把“互联互通与转融通”拆成两个分类，也不建立分类翻译或排序表；
- 不使用 `v-html`、`innerHTML`、Element Plus 内部类名或额外状态/搜索依赖。

## Approach

### 数据源选择

`control-plane/src/components/download/DataSourceSelect.vue` 的公开接口固定为：

```text
props:
  modelValue: string，默认 ''
  sources: DataSourceSummary[]，必填
  disabled: boolean，默认 false
emits:
  update:modelValue(pluginId: string)
```

组件使用可见 `<label>` 和 Element Plus 单选下拉框。每个选项以 `pluginId` 为 value，显示 `displayName`；`downloadAvailable === false` 的选项禁用。组件不显示或接收 Token，只消费公开的 `credentialConfigured`、`downloadAvailable` 和 `unavailableReason` 字段。

当 `sources` 恰有一项且 `modelValue` 为空时，组件发出一次该来源的 `pluginId` 作为首期单来源默认选择；即使该来源不可下载也保持这一规则，使调用方可以确定当前来源并禁用接口与下载动作。已经有选择、来源不止一个或来源为空时不自动发出更新，组件绝不直接修改 props。

当前选择对应不可下载来源时，在控件下方以 `role="status"` 显示 `unavailableReason`；单一不可下载来源尚未完成父级 v-model 回写时也立即显示同一原因。原因使用 Vue 文本插值，不翻译、不拼接配置位置且不解释其内容。调用方根据同一来源的 `downloadAvailable` 决定是否给 `ApiSelect` 和后续下载动作传入 disabled。

### 接口分组与搜索

`control-plane/src/components/download/ApiSelect.vue` 的公开接口固定为：

```text
props:
  modelValue: string，默认 ''
  apis: ApiDescriptor[]，必填
  disabled: boolean，默认 false
emits:
  update:modelValue(apiName: string)
```

组件使用带可见 `<label>` 的可搜索 Element Plus 单选下拉框。每个接口选项以 `apiName` 为 value，并同时显示 `displayName` 与 `apiName`。选择只发出 `update:modelValue`；清空搜索文本不改变选择，接口切换后的参数、校验和结果重置由调用方在接收事件后完成。

分组只读取 `ApiDescriptor.category`，使用输入数组中分类首次出现的顺序建立组，并保持每组内的输入顺序；未知或未来新增分类同样直接形成新组。项目所有者于 2026-09-04 明确决定暂不拆分现有分类，因此当前 Tushare Pro 49 个描述符形成 `basic_organization` 11 项、`行情与估值` 7 项、`交易与资金` 6 项、`互联互通与转融通` 6 项、`财务与披露` 9 项、`公司行动` 3 项、`股东与治理` 7 项，共七组；组件没有固定七组闭集，也没有八分类映射，后端未来拆分元数据后会自然形成八组。

搜索先对输入去除首尾空白并转为小写，再以不区分大小写的包含匹配同时检查 `apiName` 和 `displayName`。空搜索恢复全部分组和原顺序；没有匹配项时显示固定文本“无匹配接口”。搜索只投影可见结果，不修改 `apis`、描述符或当前 modelValue。

### 接口说明

`control-plane/src/components/download/ApiDescription.vue` 的公开接口固定为：

```text
prop:
  api: ApiDescriptor | null，默认 null
```

`api === null` 时不渲染说明区域。存在接口时渲染带可关联标题的原生 `<section>`，显示 `displayName`、`apiName`、`category` 和查询方式。查询方式只映射 OpenAPI 闭集：`trade_date -> 交易日`、`ann_date -> 公告日`、`snapshot -> 快照`、`date_range -> 日期范围`；意外值原样显示以暴露契约偏差。所有动态内容通过文本插值渲染，不使用 HTML 注入。

### 数据流与失败边界

父级数据流固定为“`listDataSources()` 结果 → `DataSourceSelect` → 当前 `pluginId` → `listApis(pluginId)` 结果 → `ApiSelect` → 当前 `apiName` → `ApiDescription`”。本任务从 `sources`/`apis` 开始，到选择更新和说明展示结束，不调用 API。

三个组件信任 M10-T03 对成功 DTO 的字段投影，不增加第二套运行时 schema。空数组形成空选择器；缺失选择形成空字符串 modelValue；网络或契约失败由调用方用后续异步状态处理。`disabled` 只锁定用户交互，不主动修改或清空已有 modelValue。

## Files

创建且只创建以下六个文件：

- `control-plane/src/components/download/DataSourceSelect.vue`：数据源选择、单来源默认值和不可用原因；
- `control-plane/src/components/download/ApiSelect.vue`：元数据分类、双字段搜索和接口选择；
- `control-plane/src/components/download/ApiDescription.vue`：当前接口说明与查询方式；
- `control-plane/src/components/download/DataSourceSelect.spec.js`：数据源组件 3 项行为测试；
- `control-plane/src/components/download/ApiSelect.spec.js`：接口组件 5 项行为测试；
- `control-plane/src/components/download/ApiDescription.spec.js`：接口说明组件 2 项行为测试。

不修改或删除其他生产、测试、依赖、配置和文档文件。实现提交固定为 `feat(ui): add download source and API selectors`，精确包含上述六个新增文件；设计、计划、看板和交接不得混入实现提交。

## Tests

所有命令从仓库根目录开始，并把 Node.js 24.15.0 放在 PATH 首位：

```bash
export PATH="/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin:$PATH"
cd control-plane
node --version
```

预期输出 `v24.15.0`。当前前端基线为 6 个测试文件、34 项测试。

严格 RED：先完整创建三个 spec，不创建三个生产组件，然后运行：

```bash
npm run test:unit -- --run \
  src/components/download/DataSourceSelect.spec.js \
  src/components/download/ApiSelect.spec.js \
  src/components/download/ApiDescription.spec.js
```

预期命令非零，三个套件在收集阶段只因对应目标 `.vue` 文件不存在而失败；不得出现测试语法、依赖、Element Plus、setup 或既有测试失败。

GREEN 固定为 3 files / 10 tests：

1. `DataSourceSelect` 显示可见标签和单一来源，并在空 modelValue 时发出唯一来源 ID；
2. 多来源时不自动选择，已有 modelValue 不被覆盖；
3. 不可下载选项被禁用，单一或当前不可下载来源的原因以纯文本 status 显示，外部 disabled 锁定选择；
4. `ApiSelect` 把 49 个输入选项按当前七个元数据分类分组且每项恰出现一次，并保持组和组内顺序；
5. 按 `apiName` 的大小写不敏感子串搜索只保留命中项；
6. 按中文 `displayName` 搜索，并在无命中时显示“无匹配接口”；
7. 清空搜索恢复全部选项且不改变或修改 modelValue/输入描述符；
8. 聚焦真实 combobox 后用键盘选择可用接口并发出唯一 `update:modelValue`，disabled 时不发出；
9. `ApiDescription` 在 api 为 null 时不渲染说明区域；
10. 非空 api 显示四项内容，四种 queryMode 使用固定中文文本，形似 HTML 的动态值保持纯文本。

运行：

```bash
npm run test:unit -- --run \
  src/components/download/DataSourceSelect.spec.js \
  src/components/download/ApiSelect.spec.js \
  src/components/download/ApiDescription.spec.js
npm run test:unit -- --run
npm run build
```

预期聚焦为 3 files / 10 tests、完整前端为 9 files / 44 tests，全部通过且无未处理 rejection、console 或 Vue warning；Vite 构建退出 0，只允许既有 Element Plus chunk-size 提示。

从仓库根目录运行：

```bash
git diff --check
git status --short --untracked-files=all -- control-plane
rg -n 'v-html|innerHTML|axios|fetch\(|listDataSources|listApis|ApiError|ClientError|Authorization|token|password' \
  control-plane/src/components/download/DataSourceSelect.vue \
  control-plane/src/components/download/ApiSelect.vue \
  control-plane/src/components/download/ApiDescription.vue
git diff -- control-plane/package.json control-plane/package-lock.json \
  control-plane/vite.config.js control-plane/vitest.config.js \
  control-plane/src/api control-plane/src/router control-plane/src/layouts \
  control-plane/src/views control-plane/src/style.css
```

预期格式检查退出 0；status 精确显示 Files 节六个新增文件；禁止网络、错误解释、凭证和 HTML 注入扫描无输出并退出 1；受保护路径无差异。暂存后 `git diff --cached --name-status` 精确显示六个新增文件，提交消息与 Files 节固定值一致。

## Acceptance

- 三个组件只消费 M10 `DataSourceSummary`/`ApiDescriptor`，不请求网络、不解释错误、不拥有页面加载或下载状态；
- 单一来源在空 modelValue 时默认发出其 ID，多来源不擅自选择；不可下载来源不可操作且公开原因可见，不显示 Token 或配置内容；
- 接口选择同时显示中文名与接口名，按 `category` 原值和首次出现顺序分组，当前 49 项形成七组且无缺失、重复或 API 名分支；
- 搜索同时覆盖 `apiName` 和 `displayName`，不区分大小写，清空后恢复全部原序选项；接口不自动选择；
- 选择器具有可见标签并可用键盘完成主要操作，外部 disabled 时不发出用户选择更新；
- 接口说明在无选择时不渲染，在有选择时安全显示标识、中文说明、分类和四种查询方式文本；
- 切换只发出 `update:modelValue`，下游清理由调用方负责，三个组件不修改输入描述符或自行保存业务状态；
- Node.js 24.15.0 下严格 RED 原因正确，聚焦 10/10、完整前端 44/44 和生产构建达到预期，且只有既有 chunk-size 提示；
- 实现提交精确包含六个新增组件/测试文件，不修改依赖、配置、API、路由、页面、样式、Java、YAML 或契约。

## Risks

- PRD 附录仍按八个业务分类表达 49 接口，而当前已完成元数据把“互联互通”和“转融通”合为一组。项目所有者已明确决定暂不拆分；本组件按元数据通用分组并以当前七组验收。未来若上游拆分 `category`，本组件无需代码分支即可显示八组，但对应测试期望需要随授权契约更新。
- 当前“基础与组织”元数据值为 `basic_organization`。本任务按已批准方案直接展示服务端 category，不增加局部翻译表；若产品要求统一中文分类，应先修订上游公开元数据或另行批准全局展示映射。
- Element Plus 下拉层可能 Teleport 到 `document.body`。交互测试必须 `attachTo: document.body`、等待 Vue/Popper 更新并在 finally 中 unmount，避免测试间 DOM 或焦点泄漏。
- 组件对成功 DTO 不做运行时校验；异常形状属于 M10 API/后端契约问题，页面级失败展示属于 M11-T03/M11-T05，不在本任务静默修复。
