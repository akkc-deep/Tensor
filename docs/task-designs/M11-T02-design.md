# M11-T02 元数据驱动动态参数表单——任务设计

任务编号：`M11-T02`

对应任务：[M11-T02](../superpowers/plans/tensor-modules/M11-download-ui.md#task-m11-t02-动态参数表单30h)

实施产物：供下载页组合的元数据驱动动态参数表单，以及只管理该表单值、校验错误和规范化快照的 composable

## Goal

在现有 Vue 3、Element Plus、M10 API 描述符和共享校验原语基础上，交付一个不请求网络、不解释业务错误的动态参数表单。调用方传入任意 `ParameterDescriptor[]` 后，表单按六种冻结元数据类型生成可访问控件，使用元数据中的必填性、默认值、枚举、说明、正则和日期关联完成本地校验，并向后续下载流程提供顺序稳定的规范化参数快照。

本任务只拥有当前参数表单的输入值、本地字段错误和最近一次成功校验产生的快照。M11-T03 负责来源/API 选择、异步状态、请求世代和提交；M11-T05 负责页面组合，并在提交前依次调用 `validate()` 与 `normalizedValues()`。组件不接收 `apiName`，不保存下载结果，也不调用 M10 请求函数。

## Scope

包含：

- 创建 `DynamicParameterForm.vue`，按 `ParameterDescriptor.type` 渲染 `DATE`、`DATE_RANGE_MEMBER`、`MONTH`、`TS_CODE`、`ENUM` 和 `TEXT` 控件；
- 创建 `useParameterForm.js`，按描述符顺序管理原始显示值、本地错误、默认值、规范化、范围关系和成功快照；
- 使用 M10-T04 的 `toApiDate`、`toApiMonth`、`hasValue`、`matchesPattern`、`isRangeOrdered` 与 `FieldError`，不复制第二套日期或通用正则工具；
- 为每个字段建立可见 label、可选说明、`aria-required`、`aria-invalid`、`aria-describedby` 和安全的字段错误文本；
- 暴露异步 `validate()`、同步 `normalizedValues()` 和同步 `reset()`，并在失败时把焦点移动到描述符顺序中的首个错误控件；
- 支持 `disabled` 锁定所有参数控件，供 M11-T03/M11-T05 在提交期间使用；
- 创建一个同目录测试文件，以真实 Element Plus 组件覆盖六种类型、无参数、默认值、规范化、必填、格式、正则、逆序范围、禁用和首错聚焦；
- 在 Node.js 24.15.0 下执行严格 RED、聚焦测试、完整前端单测和生产构建。

排除：

- 不修改 M10 API、共享工具、共享组件、依赖、配置、路由、布局、页面、样式、Java、YAML、OpenAPI 或 PRD；
- 不调用 `listDataSources`、`listApis`、`downloadDataset`、Axios、fetch 或其他网络边界；
- 不接收或比较具体 `apiName`，不嵌入 49 接口参数清单，不根据参数名选择控件；
- 不拥有来源/API 选择、加载、提交、重试、请求世代、结果或服务端 `ApiError`；
- 不生成下载按钮，不自动提交，不在校验失败时调用网络；
- 不显示、回填、保存或从错误信息泄露 Token、请求正文或凭证内容；
- 不使用 `v-html`、`innerHTML`、Element Plus 内部类名、第三方表单/日期/状态依赖或 TypeScript。

## Approach

### 公开接口和职责

`control-plane/src/components/download/DynamicParameterForm.vue` 的公开表面固定为：

```text
props:
  parameters: ParameterDescriptor[]，必填
  disabled: boolean，默认 false
emits:
  无
expose:
  validate(): Promise<boolean>
  normalizedValues(): Record<string, string>
  reset(): void
```

`parameters` 直接消费 M10-T03 的成功 DTO，保持数组顺序和字段原值，不建立第二套运行时 schema。组件不接收 `apiName`；同名参数在不同接口中的行为完全由描述符字段决定。

`validate()` 清除并重新计算本地错误。通过时返回 `true`，保存按描述符顺序构造的规范化快照；失败时返回 `false`、清空成功快照，并在 Vue 更新完成后调用对应 Element Plus 组件公开的 `focus()`，聚焦描述符顺序中的首个错误字段。不得查询 Element Plus 内部类名。

`normalizedValues()` 返回最近一次成功 `validate()` 的新对象副本；组件初始化后、`reset()` 后、字段值变化后或校验失败后均返回空对象。调用方必须先得到 `validate() === true` 才能提交该快照，因而不会误用旧值或部分非法值。

`reset()` 清除全部错误和成功快照，并把字段恢复为元数据默认值对应的显示值；无默认值时恢复为空字符串。`parameters` 引用变化时自动执行同一 reset，使接口切换不会保留旧字段状态；M11-T03 仍负责选择、下载结果和其他页面状态的清理。

### 表单状态和默认值

`control-plane/src/composables/useParameterForm.js` 只导出具名 `useParameterForm(parameters)`。`parameters` 是 Vue ref，composable 返回 `values`、`errors`、`firstError`、`setValue(name, value)`、`validateValues()`、`normalizedValues()` 和 `reset()`；这些对象和函数只服务当前表单，不导出全局 store。

状态键只来自当前 `parameters`，并按描述符顺序初始化。`setValue` 保存控件给出的字符串，清除该字段的旧错误与全部成功快照，不修改描述符。元数据默认值已经由服务端 readiness 校验；显示转换固定为：

- `DATE`、`DATE_RANGE_MEMBER` 的紧凑 `YYYYMMDD` 默认值转换为 `YYYY-MM-DD`；
- `MONTH` 的紧凑 `YYYYMM` 默认值转换为 `YYYY-MM`；
- `TS_CODE`、`ENUM`、`TEXT` 默认值原样进入控件；
- 没有 `defaultValue` 时使用空字符串。

当前 49 个 Tushare Pro 描述符的 `defaultValue` 和 `pattern` 均为空，所有现有参数均为必填；设计仍以合成描述符覆盖 OpenAPI 已允许的默认值、可选参数、TEXT 和 pattern，不能把当前事实硬编码进生产实现。

### 六种控件映射

组件按描述符原序逐项渲染，每项使用稳定 ID `download-parameter-<name>`，错误 ID 为 `download-parameter-<name>-error`，说明 ID 为 `download-parameter-<name>-description`。

| 元数据类型 | Element Plus 控件 | 显示值 | 规范化提交值 |
|---|---|---|---|
| `DATE` | `el-date-picker type="date"` | `YYYY-MM-DD` | `toApiDate(value)` 得到 `YYYYMMDD` |
| `DATE_RANGE_MEMBER` | 独立的 `el-date-picker type="date"`；关联成员按描述符关系校验 | `YYYY-MM-DD` | `toApiDate(value)` 得到 `YYYYMMDD` |
| `MONTH` | `el-date-picker type="month"` | `YYYY-MM` | `toApiMonth(value)` 得到 `YYYYMM` |
| `TS_CODE` | `el-input` | 用户输入字符串 | `trim().toUpperCase()` 后必须完整匹配 `[A-Z0-9]+\.[A-Z0-9]+` |
| `ENUM` | 单选 `el-select` + `allowedValues` 原序选项 | 精确枚举值 | 原值；必须属于 `allowedValues` |
| `TEXT` | `el-input` | 用户输入字符串 | `trim()`，保留内部内容 |

日期与月份 picker 固定 `value-format` 为表格中的显示格式，不创建 JavaScript `Date`。所有控件透传 `disabled`。描述符 `label` 作为可见 `<label>` 文本；`required` 同时形成可见必填标记和 `aria-required="true"`；非空 `description` 以 Vue 文本插值显示。

存在说明或错误时，控件的 `aria-describedby` 按“说明 ID、错误 ID”顺序组合；只有错误时设置 `aria-invalid="true"`。每个错误通过 M10-T04 `FieldError` 渲染，动态 label、description、allowed value 和错误内容都不作为 HTML 解释。

### 校验与规范化

`validateValues()` 按描述符顺序建立每个字段至多一个类型/必填错误，并在类型校验后处理日期范围：

1. `required === true` 且 `hasValue(value) === false`：错误固定为 `此项为必填项`；可选空值不报错且从快照省略。
2. `DATE`、`DATE_RANGE_MEMBER`：`toApiDate(value) === null` 时错误为 `请选择有效日期`。
3. `MONTH`：`toApiMonth(value) === null` 时错误为 `请选择有效月份`。
4. `TS_CODE`：规范化后不完整匹配“代码.市场”时错误为 `请输入代码.市场格式，例如 000001.SZ`。
5. `ENUM`：值不属于 `allowedValues` 时错误为 `请选择有效选项`。
6. `TEXT`：使用 trim 后的字符串；必填 TEXT 若 trim 后为空走必填错误。
7. 非空 `pattern` 在上述类型规范化之后交给 M10-T04 `matchesPattern`；不匹配或 JavaScript 正则语法非法时错误为 `输入格式不正确`，不显示 pattern 或原始值。
8. `DATE_RANGE_MEMBER` 只处理互相反向关联的一对：数组中先声明者为下界，后声明者为上界。两项各自有效且存在时调用 `isRangeOrdered`；逆序只在先声明字段记录 `开始日期不得晚于结束日期`，相等合法，不重复在关联字段报错。

必填/类型错误优先于范围错误；任一范围成员缺失或类型无效时不增加范围错误。错误对象只使用可信描述符 name 作为键，不包含值、pattern、Token、API 名或网络错误。快照只包含通过全部规则的字段，键顺序与 `parameters` 一致，并且每次读取都返回新对象。

### 空表单和失败边界

`parameters` 为空数组时不渲染输入控件或虚假的字段错误；`validate()` 返回 `true`，`normalizedValues()` 随后返回新空对象，`reset()` 无副作用。组件不额外显示“无参数”文案，该页面级说明由 M11-T05 决定。

成功 DTO 的类型闭集、ENUM 非空 allowedValues、DATE_RANGE_MEMBER 双向关系和默认值有效性由 M03/M05/M09 readiness 保证。本任务不静默修复异常元数据，也不增加第二套 metadata schema；真实合同之外的异常形状属于调用方加载失败边界，不转换成用户字段错误。

## Files

创建且只创建以下三个文件：

- `control-plane/src/components/download/DynamicParameterForm.vue`：六类型控件、可访问字段结构、公开方法和首错聚焦；
- `control-plane/src/composables/useParameterForm.js`：本地值、错误、默认值、校验、规范化快照和 reset；
- `control-plane/src/components/download/DynamicParameterForm.spec.js`：组件与 composable 公开行为的 9 项测试。

不修改或删除其他生产、测试、依赖、配置和文档文件。实现提交固定为 `feat(ui): render download parameters from metadata`，精确包含上述三个新增文件；设计、看板和交接不得混入实现提交。

## Tests

所有命令从仓库根目录开始，并把 Node.js 24.15.0 放在 PATH 首位：

```bash
export PATH="/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin:$PATH"
cd control-plane
node --version
```

预期输出 `v24.15.0`。M11-T01 完成后的前端基线为 9 个测试文件、44 项测试。

严格 RED：先完整创建 `DynamicParameterForm.spec.js`，不创建组件或 composable，然后运行：

```bash
npm run test:unit -- --run src/components/download/DynamicParameterForm.spec.js
```

预期命令非零，套件在收集阶段只因 `./DynamicParameterForm.vue` 不存在而失败；不得出现测试语法、依赖、Element Plus、setup 或既有测试失败。不得提交 RED 检查点。

GREEN 固定为 1 file / 9 tests：

1. 六种元数据类型按描述符顺序渲染日期、关联日期、月份、证券代码、枚举和文本控件，具有可见 label、说明、必填和 aria 关系，且不消费 `apiName`；
2. DATE/DATE_RANGE_MEMBER/MONTH 的紧凑默认值按 ISO 显示，其他默认值原样显示；修改后 `reset()` 恢复默认值并清空错误/快照，替换参数数组执行同一重置；
3. 合法日期、范围、月份、带空白小写证券代码、枚举和文本按描述符顺序形成 `YYYYMMDD`、`YYYYMM`、大写证券代码、精确枚举和 trim 文本的新快照，且不修改输入描述符；
4. 空参数数组不渲染控件，校验成功并返回新空对象；
5. 多个必填缺失在字段旁显示纯文本错误，控件获得 `aria-invalid/aria-describedby`，并只聚焦描述符顺序中的首个错误；
6. 不存在日期、非法月份、缺少市场的证券代码、未知枚举和不匹配/非法 pattern 分别被拦截，失败后快照为空且错误不回显原始值或 pattern；
7. 可选空字段从快照省略，TEXT trim 后为空重新应用必填规则，字段修改清除该字段旧错误并使旧成功快照失效；
8. 三组当前日期范围形状所需的通用双向关联规则接受下界早于或等于上界，拒绝逆序且只在先声明成员显示 `开始日期不得晚于结束日期` 并聚焦该字段；
9. `disabled` 使六种 Element Plus 控件均不可交互，不修改现有值、不产生成功快照，也不改变 reset 的确定性结果。

运行：

```bash
npm run test:unit -- --run src/components/download/DynamicParameterForm.spec.js
npm run test:unit -- --run
npm run build
```

预期聚焦为 1 file / 9 tests、完整前端为 10 files / 53 tests，全部通过且无未处理 rejection、console 或 Vue warning；Vite 构建退出 0，只允许既有 Element Plus chunk-size 提示。

从仓库根目录运行：

```bash
git diff --check
git status --short --untracked-files=all -- control-plane
rg -n 'apiName|v-html|innerHTML|axios|fetch\(|listDataSources|listApis|downloadDataset|ApiError|ClientError|Authorization|token|password' \
  control-plane/src/components/download/DynamicParameterForm.vue \
  control-plane/src/composables/useParameterForm.js
git diff -- control-plane/package.json control-plane/package-lock.json \
  control-plane/vite.config.js control-plane/vitest.config.js \
  control-plane/src/api control-plane/src/components/common \
  control-plane/src/utils control-plane/src/router control-plane/src/layouts \
  control-plane/src/views control-plane/src/style.css
```

预期格式检查退出 0；status 精确显示 Files 节三个新增文件；禁止 API 名分支、网络、错误解释、凭证和 HTML 注入扫描无输出并退出 1；受保护路径无差异。暂存后 `git diff --cached --name-status` 精确显示三个新增文件，提交消息与 Files 节固定值一致。

## Acceptance

- `DynamicParameterForm` 只消费调用方提供的 `ParameterDescriptor[]` 和 `disabled`，不接收 `apiName`，不请求网络、不解释错误、不拥有页面或下载状态；
- `DATE`、`DATE_RANGE_MEMBER`、`MONTH`、`TS_CODE`、`ENUM`、`TEXT` 分别使用批准的 Element Plus 控件与显示/提交格式，不根据参数名或具体接口写分支；
- 默认值按类型进入正确显示格式，reset 和参数数组切换清除旧输入、错误及成功快照；
- 必填、真实日期、月份、代码.市场、枚举、元数据 pattern 和双向日期范围得到字段级校验，逆序范围只标记先声明成员；
- 字段具有可见 label、可选说明、必填与错误 aria 关系，错误为安全纯文本，失败时聚焦描述符顺序中的首个错误；
- `validate()` 只在全部规则通过时返回 true；`normalizedValues()` 只返回最近一次成功校验的全新、顺序稳定快照，输入变化或失败后不暴露旧值；
- 空参数接口不渲染控件且可产生合法空参数对象，disabled 锁定全部参数控件；
- 组件和 composable 不修改描述符或输入字符串，不显示值/pattern/Token，不使用 Element Plus 内部类名或引入新依赖；
- Node.js 24.15.0 下严格 RED 原因正确，聚焦 9/9、完整前端 53/53 和生产构建达到预期，且只有既有 chunk-size 提示；
- 实现提交精确包含 Files 节三个新增文件，不修改依赖、配置、API、共享组件、共享工具、路由、页面、样式、Java、YAML 或契约。

## Risks

- 当前 49 个接口没有 TEXT、pattern、默认值或可选参数，但 OpenAPI 已允许这些字段。测试必须使用合成描述符覆盖它们，生产实现仍只读取元数据，不把首期现状写成闭集。
- M10-T04 `matchesPattern` 使用无 flags 的 JavaScript `RegExp` 且不自动加锚；当前 49 个 pattern 均为空。未来元数据若新增 pattern，必须提供浏览器兼容的表达式并在需要整串匹配时自行声明锚点。
- Element Plus date picker 和 select 会渲染 Teleport/Popper。测试应优先断言公开组件 props、事件、原生 input 焦点和 aria，不依赖内部 class；挂载到 `document.body` 的用例必须可靠 unmount。
- `normalizedValues()` 的成功快照会在任何字段变化后失效，这是防止提交旧参数的安全边界；M11-T05 必须始终先 await `validate()`，再读取并立即提交快照。
