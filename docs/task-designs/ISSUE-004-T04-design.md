# ISSUE-004-T04：下载工作台布局与反馈

## Goal

正式下载页形成左配置、右结果的工作台，所有真实状态都有可读反馈，继续使用一次请求及原参数重试。

## Scope

调整下载页与既有呈现组件，抽取实际重复的面板、状态与错误操作。相关共享组件接入设置页和查看页；查看页本轮只替换错误反馈的重复结构，完整布局由 T05 完成。业务 composable、API、日期工具、YAML 和依赖只读。

## Approach

视觉依据为已确认 HTML 的 `.download-grid`、`.panel-head`、`.setup-body`、`.result-body` 和 `.counts`。主任务顺序为选择来源/接口、阅读说明、填参数、开始下载，再读取结果。配置内紧密分组，配置与结果相隔 24px，面板白底、1px 主题边框、14px 圆角，无嵌套卡片。

新增 `src/components/common/WorkbenchPanel.vue`：必要 props 为 `headingId`、`title`，可选 `meta`（默认空字符串），默认 slot 承载内容。根节点为带 `aria-labelledby=headingId` 的 section，header 复用 `.panel-heading`，h2 使用 headingId，meta 用 code 文本插值。根类 `.workbench-panel`；调用者 class 透传。面板不持有状态、不发请求。下载页两个面板和设置页“外观与主题”都使用它；设置页原 form、ID、事件、文案与主题状态保持，仅替换外层 section/header。

`DownloadView.vue` 保留整个 script 中的流程与提交函数。在 PageHeading 后使用 `.download-grid`，依次放置：

- 配置面板：headingId=`download-config-title`，title=`下载配置`，meta 为已选来源 pluginId；body padding 24px。数据源与接口放入 `.workbench-selects` 两列；ApiDescription 作为平面说明；已选接口时显示“请求参数”分组及原 DynamicParameterForm；零参数显示“此接口无需填写请求参数。”。页脚保留 DownloadAction 和“结果将在本页显示”。
- 结果面板：headingId=`download-result-title`，title=`本次下载结果`，meta 为已选 apiName。内部 `.download-feedback` 最小高 346px，状态分支按下表处理；不增加结果快照、页脚样例或虚构的请求阶段。

| 条件 | 结果面板内容 |
| --- | --- |
| METADATA_LOADING | 原 LOADING 标题“正在加载下载配置”与“请稍候。” |
| metadataFailure | FAILURE“下载配置加载失败”，原 error.message / requestId，canRetry 时“重新加载” |
| SUBMITTING | LOADING“正在下载”，说明“请求已提交，请稍候。”；沿用 locked 禁用来源、接口、参数及按钮 |
| SUCCESS / EMPTY / 已选接口 FAILURE | 原 DownloadResult 的 state/result/error/canRetry 和 retry |
| 其余 | 原 INITIAL 选择接口或填写参数提示 |

扩展 `AsyncStatePanel.vue`，保持原 props `state/title/message`、actions slot 和 alert/status/live 语义；新增 SUCCESS（status、polite），默认 slot 用于状态下的业务内容，新增 `requestId` / `retryLabel`（均默认空字符串）及 `retry` 事件。requestId 显示原“请求 ID：…”文本；仅 retryLabel 非空时渲染按钮，点击发出 retry。调用方继续以各自 canRetry 决定 retryLabel，组件不推导业务重试资格。actions slot 仍可用，避免破坏已有公共调用。

所有状态的标记与标题/说明由 AsyncStatePanel 一处呈现：单个 aria-hidden SVG，依据 state 使用简单线性图形表示待操作、加载、成功、空、失败，颜色取主题变量，加载不使用虚假进度。样式不再重复绘制面板边框，使用统一 padding、22px 状态标题、12px 说明、自然换行与可见按钮焦点；可保持静态标记，避免依赖动画传达状态。

DownloadResult 保持原外部 props/events，SUCCESS 使用 AsyncStatePanel 的默认 slot 放原三项计数 dl；标题“下载成功”，说明“本次数据已完成写入。”，计数原值插值，等宽数字，三列且允许大数换行。EMPTY 保留原空提示且不显示计数，FAILURE 保留原错误和“使用原参数重试”。DownloadView 元数据错误、DownloadResult 错误以及 DatasetView 的元数据/查询错误均接入新 requestId/retryLabel/retry，删除重复的请求ID和按钮 slot。

ApiDescription 保留 displayName/apiName/category/queryMode 的全部现有事实和中文标签，不假设 DTO 含 description；调整为无外框的说明区，定义列表两列，长值可换行。选择控件的标签、宽度、44px 包装高度使用公共 `.workbench-selects` CSS；不复制控件逻辑。ApiSelect/DatasetSelect 现存分组搜索抽取按既定 T05 完成，本项不扩大选择行为。

下载 grid 默认 `minmax(0,1.35fr) minmax(290px,1fr)`；1250px 以下 gap18px、右列最小270px；1000px 以下单列、反馈最小高260px；680px 以下选择组单列，面板标题/body 18px padding。各层 min-width:0，长标签/错误/请求ID可换行，按钮动作容器可换行。公共 CSS 为 Element Plus 主按钮/输入/选择补44px操作高度和焦点，保留 T01 的根变量和对比度映射。

## Files

- 新建 `control-plane/src/components/common/WorkbenchPanel.vue`。
- 修改 `control-plane/src/views/DownloadView.vue`、`src/components/download/ApiDescription.vue`、`DownloadResult.vue`、`src/components/common/AsyncStatePanel.vue`、`src/style.css`。
- 修改 `control-plane/src/views/SettingsView.vue` 接入共享面板；`src/views/DatasetView.vue` 仅接入共享错误反馈。
- 更新 DownloadView / DownloadResult / AsyncStatePanel 既有测试；其他已覆盖行为复用现有用例，不新增样式镜像测试。

## Tests

在 control-plane 用 Node24.15.0，先更新 DownloadView 原“SUBMITTING 隐藏状态”的过时断言：结果区域始终存在，提交时区域内 status 包含“正在下载”、aria-live=polite，控件仍全部禁用；精确请求体及 daily/new_share 一次调用断言保持。运行后确认新增状态断言失败，再实现。

公共组件测试覆盖 SUCCESS polite + 默认内容、requestId 纯文本/可选重试发射、无授权不出现按钮；原四状态与 actions slot 继续通过。下载既有测试覆盖 metadata加载/重试、INITIAL、成功三计数、EMPTY无计数、安全错误、冻结参数重试和接口切换。设置/查看调用方回归覆盖表单事件、错误、查询重试及缓存。

```sh
npm test -- src/views/DownloadView.spec.js src/components/download src/components/common
npm test -- src/views/SettingsView.spec.js src/views/DatasetView.spec.js src/layouts/AppLayout.spec.js
npm test
npm run build
git diff --check
```

全部退出0后独立审查，检查所有被替换调用方，使用明确文件列表加入 Git 并形成独立提交。真实视口、焦点和计算颜色由 T06 批量验收。

## Acceptance

配置/结果双面板覆盖全部六类反馈；SUBMITTING 可见并保持禁用；计数来自响应，下载与元数据重试资格/参数不变；长内容和窄屏有布局约束；三个面板、所有错误操作与状态呈现实际复用。现有单次请求、表单日期、业务状态保留全部通过回归。

## Risks

AsyncStatePanel 增加 SUCCESS 必须保留原 alert/status 语义，计数不能被标题替换。设置外层替换必须保留原表单和ID。元数据无说明字段，不从参考样例补造产品事实。无未解决设计决定。
