# ISSUE-004 Project Task Board

## Project

- **Project ID:** `ISSUE-004`。
- **Goal:** 在正式前端交付已确认的数据工作台 UI，保留现有业务与后端契约。
- **Scope:** 六项前端任务，覆盖主题、导航与设置、业务状态保留、日期控件与原参数契约、下载与查看布局及验收；不含后端改动、其他 ISSUE、监控、后台队列或任务历史。
- **Completion condition:** 六项任务均为 `COMPLETED`，且 `docs/issues/problems/ISSUE-004-ui-visual-redesign.md` 的全部关闭条件有本轮真实验收证据。
- **Authority:** 本看板只管理 ISSUE-004 的子任务。`docs/task-handoffs/tensor-v1/tensor-v1-task-board.md` 已明确排除后续另行规划的缺陷修复，本次不改变其任务身份、顺序、依赖和状态。
- **Project design:** `docs/task-designs/ISSUE-004-design.md`。
- **Implementation plan:** `docs/superpowers/plans/2026-09-07-issue-004-ui-redesign.md`。

## Workflow

- **Execution:** 串行执行由用户掌握；创建本看板不启动实施。首项按初始化规则为 READY，其余为 NOT_STARTED。
- **Next-task selection:** 当前任务完成后，选择 Order 更大的未完成任务中 Order 最小的一项。
- **Successor preparation:** 先完成并链接后继的专属设计，再创建 next-task 交接和准备 READY；后继设计失败不改变前项已完成事实。
- **Allowed transitions:** `NOT_STARTED -> READY`、`READY -> IN_PROGRESS`、`IN_PROGRESS -> PAUSED`、`PAUSED -> IN_PROGRESS`、`READY -> BLOCKED`、`IN_PROGRESS -> BLOCKED`、`BLOCKED -> READY`、`IN_PROGRESS -> COMPLETED`。
- **Design references:** 当前总体技术设计和六项实施步骤是共享来源；子任务尚未创建独立设计，所以 Design document 如实为 None。创建专属设计后必须回填对应行，不能将总设计冒充六份已完成的专属设计。
- **Date decision:** 2026-09-07 用户明确沿用现有日期能力：单日期保持单日期，原生起止保持起止。移除前端区间执行策略及相关待确认项，T03 收敛为控件呈现与原参数契约保留；任务 ID、Order 和状态不变。
- **Reuse rule:** 2026-09-07 用户补充：优先复用现有组件，多次出现且职责相同的 UI 必须抽取为可复用组件，共用逻辑集中实现，各调用处统一接入，抽象保持简单。每项实现任务检查重复代码并记录实际复用位置，T06 汇总验证；具体共享文件写入专属设计，不改变现有业务流程和日期契约。

## Tasks

| Order | Task ID | Title | Status | Dependencies | Design document | Handoff |
| ---: | --- | --- | --- | --- | --- | --- |
| 1 | ISSUE-004-T01 | 主题计算与全局样式基础 | COMPLETED | None | docs/task-designs/ISSUE-004-T01-design.md | None |
| 2 | ISSUE-004-T02 | 侧栏、设置与业务状态保留 | COMPLETED | ISSUE-004-T01 | docs/task-designs/ISSUE-004-T02-design.md | docs/task-handoffs/ISSUE-004/ISSUE-004-T02-handoff.md |
| 3 | ISSUE-004-T03 | 日期控件与原参数契约 | COMPLETED | ISSUE-004-T02 | docs/task-designs/ISSUE-004-T03-design.md | docs/task-handoffs/ISSUE-004/ISSUE-004-T03-handoff.md |
| 4 | ISSUE-004-T04 | 下载工作台布局与反馈 | IN_PROGRESS | ISSUE-004-T02, ISSUE-004-T03 | docs/task-designs/ISSUE-004-T04-design.md | docs/task-handoffs/ISSUE-004/ISSUE-004-T04-handoff.md |
| 5 | ISSUE-004-T05 | 查看工作台与精确表格展示 | NOT_STARTED | ISSUE-004-T02, ISSUE-004-T04 | None | None |
| 6 | ISSUE-004-T06 | 正式前端回归与视觉验收 | NOT_STARTED | ISSUE-004-T04, ISSUE-004-T05 | None | None |

## Task Details

### ISSUE-004-T01

- **Goal:** 默认和自定义主题通过统一变量作用于应用与 Element Plus。
- **Scope:** 纯 palette / 对比度计算、应用级主题状态、本地存储降级、App 初始化及默认 CSS 映射；不含设置页面和布局改版。
- **Acceptance:** 完整默认 palette 精确匹配；操作色对五类表面与白字达到 5.5:1；非法 HEX 不变更；存储失败仍可预览；重置恢复完整默认值；针对性单测与构建通过。
- **Dependencies:** None。
- **Sources:** ① `docs/issues/proposals/ISSUE-004-ui-visual-concepts.md`；② `docs/issues/proposals/ISSUE-004-ui-visual-concepts.html` 的主题脚本；③ `docs/task-designs/ISSUE-004-design.md` 主题部分；④ `docs/superpowers/plans/2026-09-07-issue-004-ui-redesign.md` 的 ISSUE-004-T01；⑤ `control-plane/src/App.vue`、`control-plane/src/style.css`。
- **First action:** 依据上述来源完成 `docs/task-designs/ISSUE-004-T01-design.md`，写清主题状态注入、变量清单及测试，并回填本行。
- **Start evidence:** 2026-09-07 用户明确要求按权威看板执行 ISSUE-004；已完整读取总体设计、实施计划及 T01 专属设计，Handoff 为 None；READY -> IN_PROGRESS。
- **State evidence:** 2026-09-07 用户要求查看 ISSUE-004 技术实现并完成任务拆分；本看板初始化首项为 READY，未收到启动实现请求、未执行实施步骤。现有前端基线为 20 文件 / 120 项单测通过、构建退出 0；不作为本任务完成证据。

- **Execution evidence:** 2026-09-07 IN_PROGRESS -> COMPLETED；ba043c9；默认 palette、5.5:1、HEX 原子校验、读写降级及重置通过行为测试；针对性 3 文件/29 项、全套 22 文件/148 项与构建退出 0；独立审查规格/质量通过。计算共用 theme.js，状态共用 useTheme.js，App provide；根 CSS 统一映射。正式弹层计算样式由 T06 验证；既有 chunk 提示不扩展处理。

### ISSUE-004-T02

- **Goal:** 三入口导航与外观设置可用，往返业务页面保留状态且不重发请求。
- **Scope:** 侧栏、响应式应用壳、settings 路由与表单、KeepAlive、键盘导航和路由回归；复用或抽取同职责重复结构，不改下载与查询流程。
- **Acceptance:** 下载 / 查看 / 设置路由及 404 正常；取色器、HEX、实际应用色、存储提示和重置有效；表单、日期、结果、错误、分页及在途请求保留；设置无新 API 请求；本次涉及的同职责重复结构已复用；路由和设置测试通过。
- **Dependencies:** ISSUE-004-T01；消费其共享主题状态和根变量。
- **Sources:** ① `docs/task-designs/ISSUE-004-design.md` 导航 / 设置 / 生命周期部分；② `docs/superpowers/plans/2026-09-07-issue-004-ui-redesign.md` 的 ISSUE-004-T02；③ `control-plane/src/layouts/AppLayout.vue`、`control-plane/src/router/index.js`；④ `control-plane/src/views/DownloadView.vue`、`control-plane/src/views/DatasetView.vue`；⑤ T01 的主题实现与验证结果。
- **First action:** 在前项完成后，完成并链接 `docs/task-designs/ISSUE-004-T02-design.md`，明确两个缓存实例与延迟响应测试。
- **State evidence:** None。

- **Execution evidence:** 2026-09-07 NOT_STARTED -> READY；前项已完成，后继专属设计已完整读取并链接，直接依赖已验证，next-task 交接已写入。

- **Execution evidence:** 2026-09-07 READY -> IN_PROGRESS；用户已明确要求执行 ISSUE-004 全部任务；已完整读取本任务设计和链接交接，按既定顺序启动。

- **Execution evidence:** 2026-09-07 IN_PROGRESS -> COMPLETED；7f577e4；三入口、设置取色/HEX/校正/降级/重置及设置零 API 请求通过；真实业务页面往返保留日期、结果、每页100/第2页、失败原快照及在途下载/查询且不重发；PageHeading 在三页复用。针对性18项、全套23文件/157项、构建及 diff --check 均退出0，独立规格/质量审查通过。正式视口、焦点和计算样式按设计由 T06 组合验收。

### ISSUE-004-T03

- **Goal:** 日期控件按现有下载参数和查看筛选元数据呈现，改版后原契约保持。
- **Scope:** 原日期控件的标签 / 宽度 / 错误 / 窄屏呈现及必要的参数回归；抽取两个表单中同职责重复的字段组件与输入属性处理逻辑；保留原校验、格式、请求流程和重试，不增加日期字段或批次能力。
- **Acceptance:** DATE 保持一个日期控件，原生 DATE_RANGE_MEMBER 保持独立起止控件；原必填 / 格式 / 顺序校验与查看可空 / 单边筛选有效；每次有效下载提交只发一次原契约请求；进入设置再返回保留控件值；同职责重复呈现和逻辑已实际共用，标签、错误关联与聚焦无回归。
- **Dependencies:** ISSUE-004-T02；消费应用壳、共享主题和业务页缓存，用于控件呈现与切页保留验收。
- **Sources:** ① `docs/issues/proposals/ISSUE-004-ui-visual-concepts.md` 的日期约束；② `docs/task-designs/ISSUE-004-design.md` 日期部分；③ `docs/superpowers/plans/2026-09-07-issue-004-ui-redesign.md` 的 ISSUE-004-T03；④ `control-plane/src/components/download/DynamicParameterForm.vue`、`control-plane/src/components/dataset/DynamicFilterForm.vue`、`control-plane/src/api/downloads.js`；⑤ `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/` 的参数定义。
- **First action:** 按已确认的原日期契约完成并链接 `docs/task-designs/ISSUE-004-T03-design.md`，列出控件形态、原校验及一次请求的验收示例。
- **State evidence:** 2026-09-07 用户明确要求沿用接口现有日期能力；已将本任务收敛为日期控件与原参数契约，撤销区间执行待确认项。状态保持 NOT_STARTED，本次仅修订设计与任务定义，未启动实现。

- **Execution evidence:** 2026-09-07 NOT_STARTED -> READY；前项已完成，后继专属设计已完整读取并链接，直接依赖已验证，next-task 交接已写入。

- **Execution evidence:** 2026-09-07 READY -> IN_PROGRESS；用户已明确要求执行 ISSUE-004 全部任务；已完整读取本任务设计和链接交接，按既定顺序启动。

- **Execution evidence:** 2026-09-07 IN_PROGRESS -> COMPLETED；b4e486b、decb5c7；两个表单实际共用 MetadataField 和 useFormValidation，删除遗留 ARIA 副本；单日期/原生起止/月/可空及单边筛选、标签/错误关联/焦点和禁用通过。daily 与 new_share 明确断言精确原参数且仅一次请求。针对性7文件/58项、全套23文件/158项、构建退出0；审查修正后2文件/14项与 diff --check 通过，独立复审规格/质量通过。62个业务/API/日期/元数据及依赖文件散列未变；真实视口与弹层由T06组合验收。

### ISSUE-004-T04

- **Goal:** 下载页按左配置 / 右结果布局呈现真实业务反馈。
- **Scope:** 既有下载组件重排、双面板、统一 AsyncStatePanel、加载与失败呈现、响应式样式；抽取同职责重复面板与反馈组件，消费 T03 表单并复用既有下载流程。
- **Acceptance:** 元数据加载、待操作、提交、成功、空、失败均可见；三项真实计数及适用重试正常；长参数 / 错误可读；桌面双栏、窄屏纵向；同职责重复面板与反馈已通过公共组件复用；下载与公共组件回归通过。
- **Dependencies:** ISSUE-004-T02、ISSUE-004-T03；分别消费应用壳 / 主题与原参数表单及其回归结果。
- **Sources:** ① `docs/issues/proposals/ISSUE-004-ui-visual-concepts.html` 下载页；② `docs/task-designs/ISSUE-004-design.md` 页面部分；③ `docs/superpowers/plans/2026-09-07-issue-004-ui-redesign.md` 的 ISSUE-004-T04；④ `control-plane/src/views/DownloadView.vue`、`control-plane/src/components/download/`、`control-plane/src/components/common/AsyncStatePanel.vue`；⑤ T02 / T03 的直接输入与验证结果。
- **First action:** 完成并链接 `docs/task-designs/ISSUE-004-T04-design.md`，固定六类状态在两个面板中的位置及组件职责。
- **State evidence:** None。

- **Execution evidence:** 2026-09-07 NOT_STARTED -> READY；T03完成后已完成并链接T04专属设计，直接依赖输入一致，next-task交接已写入并链接。

- **Execution evidence:** 2026-09-07 READY -> IN_PROGRESS；用户明确要求按权威看板执行ISSUE-004，已完整读取本项设计和交接，按既定顺序启动。

### ISSUE-004-T05

- **Goal:** 查看页形成清晰筛选 / 结果布局，宽表格保留全部字段和精确值。
- **Scope:** 查看组件重排、独立日期布局、数字对齐 / 行情符号、限定的 daily / weekly 展示映射、固定列 / tooltip / 分页主题；复用公共选择、面板与反馈，本任务发现的同职责重复部分同步抽取并替换相关调用处；不改变查询状态机或筛选契约。
- **Acceptance:** 交易日 / 公告日 / 单边日期及无筛选数据集可用；查询、重置、分页、每页条数及重试不变；152 业务列 + 3 来源列不丢失；数字字符串不丢精度；日 / 周单位正确；宽表格内部滚动；跨页同职责重复 UI 已共用组件，两页行为回归通过。
- **Dependencies:** ISSUE-004-T02、ISSUE-004-T04；消费应用壳 / 缓存及统一公共状态组件样式。
- **Sources:** ① `docs/issues/proposals/ISSUE-004-ui-visual-concepts.html` 查看页；② `docs/task-designs/ISSUE-004-design.md` 表格 / 响应式部分；③ `docs/superpowers/plans/2026-09-07-issue-004-ui-redesign.md` 的 ISSUE-004-T05；④ `control-plane/src/views/DatasetView.vue`、`control-plane/src/components/dataset/`、`control-plane/src/utils/format.js`、`control-plane/src/api/datasets.js`；⑤ T02 / T04 的直接输入与验证结果。
- **First action:** 完成并链接 `docs/task-designs/ISSUE-004-T05-design.md`，写清展示映射、精确字符串符号规则及宽表格验收。
- **State evidence:** None。

### ISSUE-004-T06

- **Goal:** 用正式 Vue 构建证明视觉方案、跨页行为、全部 UI 元数据与客户端契约均已满足。
- **Scope:** 独立 Playwright UI 配置、49 项元数据 UI 覆盖、主题 / 状态 / 日期 / 查询浏览器场景、多视口视觉检查、组件与逻辑复用审查、证据和 ISSUE 收尾；不执行真实 Tushare 下载或扩大为后端验收。
- **Acceptance:** 全部前端单测、构建和指定 UI spec 通过；设置零请求、切页状态保留、原日期参数与一次请求、精确宽表、五种视口及键盘 / 对比度检查有证据；结果写入 `docs/verification/ISSUE-004-ui-redesign.md`，包含共享实现、实际使用位置及调用处回归结果，截图可追踪；全部关闭条件满足后才关闭 ISSUE。
- **Dependencies:** ISSUE-004-T04、ISSUE-004-T05；消费最终下载 / 查看页面及其包含的主题、设置和元数据表单。
- **Sources:** ① `docs/issues/problems/ISSUE-004-ui-visual-redesign.md` 关闭条件；② `docs/task-designs/ISSUE-004-design.md` Tests / Acceptance；③ `docs/superpowers/plans/2026-09-07-issue-004-ui-redesign.md` 的 ISSUE-004-T06；④ `control-plane/src/api/`、`docs/data-template/manifest.json`、`control-plane/e2e/tushare-metadata.spec.js` 的公开契约常量；⑤ T04 / T05 的页面和验证结果。
- **First action:** 完成并链接 `docs/task-designs/ISSUE-004-T06-design.md`，明确 route stub、49 项覆盖映射、截图及验收记录格式。
- **State evidence:** None。

## Risks

- 真实数字精度、日 / 周单位、49 种元数据和 155 列宽表不能因视觉改版缩水。
- KeepAlive 保留内存状态，不承诺刷新后恢复业务状态或撤回在途后端操作。
- 现有构建 chunk 提示及其他 ISSUE 保留各自范围；本看板完成不等于 tensor-v1 的真实上游、性能、安全或发布门禁完成。
