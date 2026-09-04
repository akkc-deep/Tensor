# M10-T04 日期、空值、精度格式化和无障碍状态组件——任务设计

任务编号：`M10-T04`

对应任务：[M10-T04](../superpowers/plans/tensor-modules/M10-frontend-foundation.md#task-m10-t04-通用格式化与状态组件20h)

实施产物：供 M11 下载页和 M12 数据集页直接复用的纯格式化/校验函数，以及无业务状态的异步状态与字段错误组件

## Goal

在现有 Vue 3、Vitest 和 Vue Test Utils 基线上，建立日期提交、日期/入库时间展示、空值与高精度单元格展示、元数据表单校验和可访问状态提示的共享边界。M11 必须能够把日期控件的显示字符串校验后转换为上游下载参数，M12 必须能够安全展示服务端字符串记录且不损失 `DECIMAL`/`LONG` 精度；两个页面还必须能够复用一致的初始、加载、空结果、失败和字段错误语义。

本任务只提供无业务含义的原语。页面、composable 和后续业务组件负责选择状态、请求数据、解释 `ApiError`、渲染成功结果、关联字段错误以及移动焦点，不得把这些职责提前放入 M10-T04。

## Scope

包含：

- 创建严格字符串日期工具 `toApiDate`、`toApiMonth` 和 `formatDate`；
- 创建入库时间与表格单元格工具 `formatIngestedAt` 和 `formatCell`；
- 创建校验原语 `hasValue`、`matchesPattern` 和 `isRangeOrdered`；
- 创建仅表达 `INITIAL | LOADING | EMPTY | FAILURE` 的 `AsyncStatePanel.vue`；
- 创建安全显示字段错误文本的 `FieldError.vue`；
- 用两个测试文件覆盖全部公开函数、状态语义、可选 actions 插槽和错误文本渲染；
- 在 Node.js 24.15.0 下执行严格 RED、聚焦测试、完整前端单元测试和生产构建。

排除：

- 不修改 M10-T01～T03 已交付的依赖、配置、路由、布局、页面或 API 客户端；
- 不创建 M11/M12 的动态表单、表格、分页、结果组件、composable 或页面集成；
- 不发起网络请求，不管理加载/重试/竞态/分页状态，不解释或显示 `ApiError`；
- 不接受 JavaScript `Date` 作为下载日期输入，不把 M12 查询筛选日期转换为紧凑格式；
- 不解析、计算、排序、舍入或本地化 `DECIMAL`/`LONG` 字符串；
- 不提供成功状态面板；成功内容和成功播报由调用方根据业务结果渲染；
- 不负责调用方的 `aria-describedby`、字段标签或首个错误字段聚焦；
- 不引入第三方日期、校验、状态或 HTML 清理依赖，不修改 OpenAPI、Java 或 SQL。

## Approach

### 日期转换和展示

`control-plane/src/utils/date.js` 只导出三个具名纯函数：

```js
toApiDate(value): string | null
toApiMonth(value): string | null
formatDate(value): unknown
```

`toApiDate` 只接受严格的 `YYYY-MM-DD` 原始字符串。四位年、两位月、两位日以及公历中真实存在的日期全部有效时，返回 `YYYYMMDD`；空字符串、前后含空格、非字符串、格式错误、无效月份/日期和不存在的日期均返回 `null`。函数不接收或隐式转换 `Date`，也不抛面向用户的异常。

`toApiMonth` 使用相同边界，只接受严格的 `YYYY-MM` 原始字符串；月份为 `01`～`12` 时返回 `YYYYMM`，其他输入返回 `null`。两个转换函数只供 M11 下载动态参数使用。M12 查询 API 的 `tradeDateFrom/To` 和 `annDateFrom/To` 继续提交 OpenAPI 规定的 ISO 日期，不调用这两个函数。

`formatDate` 面向 M12 服务端 `logicalType === 'DATE'` 的单元格：严格有效的 `YYYY-MM-DD` 字符串按原格式返回，不经过 `Date` 或时区换算；非法日期、非字符串值保持原值，以便暴露而不是掩盖服务端契约异常。`null`/`undefined` 的占位规则由 `formatCell` 统一处理。

### 入库时间和单元格展示

`control-plane/src/utils/format.js` 导出：

```js
formatIngestedAt(value, timeZone = 'Asia/Shanghai'): unknown
formatCell(value, column, timeZone = 'Asia/Shanghai'): unknown
```

`formatIngestedAt` 接受服务端入库时间字符串，把可解析为有效时刻的值转换到指定 IANA 时区，并固定输出 `YYYY-MM-DD HH:mm:ss`。调用方可显式传入合法 IANA 时区；未传时使用 `Asia/Shanghai`。非法时区不向 UI 抛出 `RangeError`，而是回退 `Asia/Shanghai`；非法时间或非字符串值保持原值。实现使用平台 `Intl.DateTimeFormat` 的 `formatToParts` 组装固定 ASCII 数字和分隔符，并使用 24 小时制，不把宿主环境的本地时区或本地化标点泄漏到结果。

`formatCell` 按以下固定优先级处理值：

1. `null` 或 `undefined` 返回字符串 `--`；
2. `column?.name === 'ingested_at'` 时调用 `formatIngestedAt(value, timeZone)`；
3. `column?.logicalType === 'DATE'` 时调用 `formatDate(value)`；
4. 其他值原样返回。

因此数值 `0` 保持数值 `0`，空字符串保持空字符串；`logicalType` 为 `DECIMAL` 或 `LONG` 的十进制字符串不会传入 `Number`、`parseFloat`、`BigInt` 或任何格式化器，不发生计算、舍入、补零或科学计数法转换。缺失或未知 column 元数据走原样返回分支。

### 校验原语

`control-plane/src/utils/validation.js` 只导出：

```js
hasValue(value): boolean
matchesPattern(value, pattern): boolean
isRangeOrdered(start, end): boolean
```

- `hasValue` 对 `null`、`undefined`、空字符串和仅含空白的字符串返回 `false`；对 `0`、`false` 和其他值返回 `true`。
- `matchesPattern` 把 pattern 视为服务端元数据提供的 JavaScript 正则表达式源字符串。value 和 pattern 都必须是字符串；pattern 为空或语法非法时返回 `false`，否则以新建且无 flags 的 `RegExp` 测试 value。函数不自动加锚点、不修改 value，也不把异常交给调用方；是否跳过空的可选字段由表单先调用 `hasValue` 决定。
- `isRangeOrdered` 在任一端没有值时返回 `true`，让 required 校验独立负责缺失值；两端均为字符串时按字符串顺序判断 `start <= end`。M11/M12 只把已经通过严格 `YYYY-MM-DD` 形状校验的日期传入，因此该顺序与日期顺序一致；两端都有值但任一端不是字符串时返回 `false`。

这些函数只给出确定性布尔值，不包含字段名、中文错误文案、元数据类型分派或首错聚焦逻辑。

### 状态与字段错误组件

`AsyncStatePanel.vue` 的公开表面固定为：

```text
required props: state, title, message
optional slot: actions
allowed state: INITIAL | LOADING | EMPTY | FAILURE
```

三个 prop 均为必填字符串；`state` 使用 Vue prop validator 拒绝闭集之外的值。四种状态都渲染可见的原生 `<section>`、标题和消息文本，但语义不同：

- `INITIAL` 不设置 `role` 或 `aria-live`，初始静态说明不会被当作异步更新播报；
- `LOADING` 和 `EMPTY` 设置 `role="status"` 与 `aria-live="polite"`；
- `FAILURE` 设置 `role="alert"`，依赖 alert 的 assertive live-region 语义，不额外设置冲突的 `aria-live`；
- 仅调用方提供 `actions` 插槽时渲染 actions 容器，组件不生成按钮、不决定 retryable，也不绑定业务事件。

组件使用原生语义标记和 scoped style，样式只负责面板间距、边框与标题/消息层级，不查询或覆盖 Element Plus 内部 DOM。消息全部通过 Vue 文本插值渲染，不使用 `v-html`。

`FieldError.vue` 只接收必填字符串 prop `id` 和 `message`。`message` 为空字符串时不渲染任何元素；非空时渲染带该 `id` 和 `role="alert"` 的错误文本元素，并使用文本插值保证形似 HTML 的服务端或本地消息不会成为 DOM。组件不推导输入框 ID，不设置输入框的 `aria-invalid`/`aria-describedby`，也不移动焦点。

### 数据流和失败边界

M11 的数据流固定为“控件中的 `YYYY-MM-DD`/`YYYY-MM` 字符串 → `hasValue`、`matchesPattern`、`isRangeOrdered` → `toApiDate`/`toApiMonth` → 紧凑下载参数”。M12 的筛选日期保持 ISO 字符串直接交给 M10-T03 API；M12 的记录展示为“服务端字符串记录 + column 元数据 + 可选显示时区 → `formatCell` → 文本单元格”。

所有工具函数都在不可信值或非法元数据下返回 `null`、`false` 或原值，不向 UI 抛出面向用户的异常。业务请求失败、服务端 `ApiError`、重试提示、成功内容和页面级 live region 均留给 M11/M12。

## Files

创建且只创建以下 7 个实现文件：

- `control-plane/src/utils/date.js`：严格下载日期/月转换和无时区的 DATE 展示；
- `control-plane/src/utils/format.js`：入库时间与单元格展示分派；
- `control-plane/src/utils/validation.js`：三个无业务文案的校验原语；
- `control-plane/src/components/common/AsyncStatePanel.vue`：四态可访问提示与 actions 插槽；
- `control-plane/src/components/common/FieldError.vue`：安全的字段错误 live region；
- `control-plane/src/utils/format.spec.js`：三个工具模块的 9 项行为测试；
- `control-plane/src/components/common/AsyncStatePanel.spec.js`：两个通用组件的 6 项行为测试。

不修改或删除其他生产、测试、依赖、配置和文档文件。实现提交固定为 `feat(ui): add shared display and accessibility utilities`，精确包含上述 7 个文件；本设计、后续实施计划、交接和看板不得混入该实现提交。

## Tests

所有命令从仓库根目录开始，并把 Node.js 24.15.0 放在 PATH 首位：

```bash
export PATH="/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin:$PATH"
node --version
```

预期输出 `v24.15.0`。当前基线为 4 个测试文件、19 项测试。

### 严格 RED

先只创建两个完整测试文件，不创建五个生产模块，然后运行：

```bash
cd control-plane
npm run test:unit -- --run src/utils/format.spec.js src/components/common/AsyncStatePanel.spec.js
```

预期命令非零，两个测试套件在收集阶段只因其导入的五个目标生产模块不存在而失败；不得出现测试语法、依赖、Vitest、Vue SFC 或 setup 错误。Vitest 可能只报告每个套件遇到的第一个未解析 import，但错误路径必须属于这五个目标模块。

### 工具函数 9 项

`control-plane/src/utils/format.spec.js` 固定为 9 个 `it`：

1. `toApiDate` 把普通日期和闰日从 `YYYY-MM-DD` 转为 `YYYYMMDD`；
2. `toApiDate` 对空值、非字符串、空白、宽松格式和不存在日期返回 `null`；
3. `toApiMonth` 只转换严格有效的 `YYYY-MM`，其他输入返回 `null`；
4. `formatDate` 保持严格有效 ISO 日期，非法日期和非字符串保持原值；
5. `formatIngestedAt` 默认以 `Asia/Shanghai` 输出到秒且丢弃毫秒显示；
6. `formatIngestedAt` 接受显式 IANA 时区，非法时区回退上海，非法时间保持原值；
7. `formatCell` 把 `null`/`undefined` 映射为 `--`，同时保持数值 `0` 和空字符串不同；
8. `formatCell` 原样保留 `DECIMAL`/`LONG` 字符串，并按 column 分派 DATE 和 `ingested_at`；
9. `hasValue`、`matchesPattern`、`isRangeOrdered` 覆盖正常、空值、逆序、类型错误和非法正则边界。

### 组件 6 项

`control-plane/src/components/common/AsyncStatePanel.spec.js` 固定为 6 个 `it`：

1. `INITIAL` 显示 title/message，但没有 `role` 或 `aria-live`；
2. `LOADING` 使用 `role="status"` 和 `aria-live="polite"`；
3. `EMPTY` 使用 `role="status"` 和 `aria-live="polite"`；
4. `FAILURE` 使用 `role="alert"`，并仅在提供时显示 actions 插槽；
5. `FieldError` 在空 message 时不渲染错误元素；
6. `FieldError` 在非空 message 时保留 id、使用 `role="alert"`，并把形似 HTML 的内容当作纯文本。

### GREEN、回归和构建

创建五个最小生产模块后运行：

```bash
cd control-plane
npm run test:unit -- --run src/utils/format.spec.js src/components/common/AsyncStatePanel.spec.js
npm run test:unit -- --run
npm run build
```

预期：聚焦测试为 2 files / 15 tests 全部通过；完整前端回归为 6 files / 34 tests 全部通过；Vite 生产构建退出 0。构建只允许 M10-T02 已接受的 Element Plus bundle chunk-size 提示，不允许新增 Vue、可访问性、测试或编译警告。

### 范围、公开表面和安全检查

```bash
cd ..
git diff --check
git status --short --untracked-files=all -- control-plane
rg -n 'v-html|innerHTML|axios|fetch\(|ApiError|Authorization|token|password|parseFloat|parseInt|Number\(|BigInt\(' \
  control-plane/src/utils/date.js \
  control-plane/src/utils/format.js \
  control-plane/src/utils/validation.js \
  control-plane/src/components/common/AsyncStatePanel.vue \
  control-plane/src/components/common/FieldError.vue
```

预期：格式检查退出 0；status 精确显示 Files 节 7 个文件；敏感能力、网络、业务错误解释、HTML 注入和数值解析扫描无输出并按预期退出 1。暂存后 `git diff --cached --name-status` 精确显示 7 个新增文件，提交消息与 Files 节固定值一致。

## Acceptance

- `date.js` 只导出 `toApiDate`、`toApiMonth`、`formatDate`；下载转换只接受严格字符串，合法日期/月返回紧凑格式，空值、类型错误、宽松格式和不存在日期返回 `null`；
- M12 查询日期保持 `YYYY-MM-DD`，没有被共享工具误转为下载格式；
- `formatIngestedAt` 默认以 `Asia/Shanghai` 输出 `YYYY-MM-DD HH:mm:ss`，支持显式合法 IANA 时区，非法时区回退上海，非法时间保持原文；
- `formatCell` 将 `null`/`undefined` 显示为 `--`，保持 `0` 与空字符串不同，原样保留 `DECIMAL`/`LONG` 字符串，并正确分派 DATE 和 `ingested_at`；
- `validation.js` 只导出 `hasValue`、`matchesPattern`、`isRangeOrdered`，非法元数据正则返回 `false`，所有失败边界均不抛面向用户的异常；
- `AsyncStatePanel` 的状态闭集为 `INITIAL | LOADING | EMPTY | FAILURE`，初始状态不播报，加载/空状态礼貌播报，失败使用 alert，成功内容由调用方渲染；
- `FieldError` 的空消息不渲染，非空消息以纯文本和 `role="alert"` 显示；调用方仍负责 `aria-describedby` 和首错聚焦；
- 两个组件不请求数据、不管理业务状态、不解释 `ApiError`，不使用 `v-html`，不依赖 Element Plus 内部 DOM；
- Node.js 24.15.0 下严格 RED 原因正确，聚焦 2 files / 15 tests、全量 6 files / 34 tests 全部通过，生产构建退出 0 且只有既有 chunk-size 提示；
- 实现提交精确包含 Files 节 7 个新增文件，无 M11/M12 业务实现、依赖/配置修改、生成物或文档，并使用固定提交消息。

## Risks

- `Intl.DateTimeFormat` 的默认字符串在不同运行时会包含不同标点、顺序或午夜表示。实现必须用 `formatToParts` 和 24 小时制组装固定输出；测试在任务基线 Node.js 24.15.0/jsdom 中验证结果。
- `formatDate` 刻意不使用 `Date`，避免纯日期因宿主时区偏移到前一天；`formatIngestedAt` 则必须按真实时刻转换，两条路径不可合并。
- 元数据 pattern 由受信服务端契约提供，但其语法仍可能非法；`matchesPattern` 捕获构造错误并返回 `false`。本任务不增加 regex 超时机制，也不接受用户自行提供 pattern。
- `role="alert"` 在组件首次挂载时即可能播报。调用方应只在真实失败或字段错误出现时挂载相应内容，不得把隐藏的预创建错误节点作为状态缓存。
