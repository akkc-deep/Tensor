# ISSUE-004-T03：日期控件与原参数契约

## Goal

统一下载参数和查看筛选的字段呈现，在复用控件、错误关联与聚焦逻辑的同时保留原日期能力和一次请求契约。

## Scope

仅调整两个动态表单的 UI 与共享 UI 逻辑，并补齐缺少的页面请求回归。`useParameterForm.js`、`useDatasetFilters.js`、`useDownloadFlow.js`、`useDatasetQuery.js`、`utils/date.js`、API 客户端和生产 YAML 只读；不增加日期字段、批次或新的表单接口。

## Approach

新建 `control-plane/src/components/common/MetadataField.vue`，集中现有标签、Element Plus 控件、描述、FieldError 和原生 input 属性同步。必要 props 为 `id`、`label`、`type`、`modelValue`、`required`（默认 false）、`description` / `error`（默认空字符串）、`disabled`、`allowedValues`（默认空数组）；只发出 `update:modelValue`，禁用时忽略更新。保留原始值，不做格式转换、校验或业务请求。通过单一控件 ref 暴露 `focus()`。

类型映射固定：DATE 和 DATE_RANGE_MEMBER 各一个 `el-date-picker type="date" value-format="YYYY-MM-DD"`；MONTH 为 `type="month" value-format="YYYY-MM"`；ENUM 为现有 allowedValues 选项；TS_CODE 和 TEXT 为 `el-input`。不把两个日期合成范围控件。label 的 for 使用 id，描述和错误 ID 为 `${id}-description` / `${id}-error`；必填星号对读屏隐藏。统一在组件内维护原 `vInputA11y` 的 mounted / updated 属性同步，更新或移除内部 input 的 id、aria-required、aria-invalid、aria-describedby，描述和错误均使用文本插值。调用方无需再复制输入属性逻辑。

两个表单仍负责各自元数据到字段的映射、调用原 composable、阻止禁用更新及原公开接口。下载 ID 保持 `download-parameter-${name}`，查看 ID 保持 `dataset-filter-${key}`；原 `data-parameter` / `data-filter` 标识继续落在字段根节点。查看支持的 descriptor、字段顺序、现有中文标签、可空及单边筛选保持。共享组件标签样式为 `.metadata-field__label`，测试可替换旧样式选择器，但保留全部行为断言。

新建 `control-plane/src/composables/useFormValidation.js`，仅抽取两个表单相同的控件注册和错误聚焦：`useFormValidation(validateValues, firstError)` 返回 `setControl(name, control)` 与异步 `validate()`。每次调用创建私有 Map，移除卸载控件；先执行传入校验，失败后 await nextTick，再 focus 第一个错误字段，返回原布尔结果。两套业务校验和快照继续分别保存在原 composable 中。表单各自暴露的 normalizedValues / criteria / reset 不变。

字段根与控件 min-width 为 0；共享字段用 grid、8px 内部间距，标签 12px，描述和错误自然换行。日期、选择和文本控件统一 width:100%，输入包装高度至少 44px，颜色沿用根变量。表单采用两列 `repeat(2, minmax(0, 1fr))` 与 22px/18px 行列间距；680px 以下一列，保证 360px 视口长标签和日期可读。布局差异通过两个表单的容器 CSS 表达，不引入组件变体或 UI 框架。

原契约的具体验收输入：

| 来源 | 控件与输入 | 验收结果 |
| --- | --- | --- |
| daily.yaml | 唯一必填 trade_date，2026-08-07 | 一次 POST，params 仅 `{ trade_date: '20260807' }` |
| new_share.yaml | 独立必填 start_date=2026-08-03、end_date=2026-08-07 | 一次 POST，原两个参数为 20260803 / 20260807；缺失或倒序不请求 |
| broker_recommend.yaml | 唯一必填 month，2026-08 | 一个月份控件，归一化 202608 |
| index_classify.yaml | 无下载参数、无查看筛选 | 不生成日期；空参数/筛选仍可正常校验 |
| daily 查看 | tsCode 与 tradeDateFrom/To | 可空、单边，日期以 YYYY-MM-DD 发送，不使用下载日期能力推导筛选 |
| income 查看 | tsCode 与 annDateFrom/To | 公告日起止独立，保留原顺序校验和错误聚焦 |

## Files

- 新建 `control-plane/src/components/common/MetadataField.vue`、`control-plane/src/composables/useFormValidation.js`。
- 修改 `control-plane/src/components/download/DynamicParameterForm.vue`、`control-plane/src/components/dataset/DynamicFilterForm.vue` 及原 `.spec.js`：接入共享呈现与聚焦，保留现有覆盖。
- 修改 `control-plane/src/views/DownloadView.spec.js`：补生产 daily / new_share 一次请求的缺口；T02 布局缓存测试作为现成跨页回归。
- 不为 CSS 或已有行为另写同构测试；若发现共享字段特有边界未被原测试覆盖，在相应调用方用行为测试补充。

## Tests

在 `control-plane` 使用 Node 24.15.0，先运行现有覆盖：

```sh
npm test -- src/components/download/DynamicParameterForm.spec.js src/components/dataset/DynamicFilterForm.spec.js src/composables/useDatasetFilters.spec.js src/views/DownloadView.spec.js src/api/api.spec.js
```

既有用例覆盖全部类型、defaults/reset、必填、非法日期/月、ENUM/TEXT/代码、互相关联范围、错误文字/ARIA/首错误聚焦、接口切换清理和 disabled 更新。共享化后保持通过；新请求边界通过真实页面组件操作，在 API 边界断言一次请求及精确 body。增加 new_share 缺失/倒序阻止提交与改正后一次原参数请求；daily 使用上表生产日期及 pluginId=tushare_pro。不得调用表单私有业务逻辑绕过 UI。

```sh
npm test -- src/components/download/DynamicParameterForm.spec.js src/components/dataset/DynamicFilterForm.spec.js src/composables/useDatasetFilters.spec.js src/views/DownloadView.spec.js src/api/api.spec.js src/composables/useDownloadFlow.spec.js src/layouts/AppLayout.spec.js
npm test
npm run build
```

预期全部退出 0，已有缓存往返及延迟响应测试继续通过；所有只读依赖没有改动。

## Acceptance

DATE 保持单日期，原生范围保留两个独立参数，MONTH / ENUM / 代码 / 文本 / 无参数均可用。两页实际共用 MetadataField 和 useFormValidation；标签、错误关联、聚焦和禁用无回归。每次有效下载只发一次原请求，参数格式、重试和计数不变。真实视口与日期弹层由 T06 最终组合验收。

## Risks

Element Plus 的内部 input 可能覆盖透传 ARIA，必须保留组件内 mounted / updated 同步并通过实际 DOM 验证。共享 focus 只管理 UI 控件，不接管业务快照或缓存生命周期。无未解决的设计决定。
