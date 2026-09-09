# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-range/tensor-range-task-board.md`（[看板](tensor-range-task-board.md)）。
- **Completed task:** `RANGE-T15`，已记录COMPLETED；完成记录先于本设计与交接。
- **Next task:** `RANGE-T16`，按预定义Order选择16，观察到NOT_STARTED。
- **Design document:** `docs/task-designs/RANGE-T16-design.md`（[完整设计](../../task-designs/RANGE-T16-design.md)），已完整读取、自审及独立就绪评审通过，先回填看板Design document。
- **Expected next status:** `READY`；本交接写入并链接后执行NOT_STARTED -> READY，不自动启动实现。

## Next Task

`RANGE-T16`：下载状态、部分结果与断连提示。

消费T14本轮结果／错误合同及T15已迁移表单，准确展示等待、六种结果、已确认计数和实际范围；保留切页中的同一请求，明确通信未确认边界，为已保存失败任务提供T17消费的确定入口目标。

可观察验收：

- 部分完成按恢复单元显示完成2／失败1及确认R／I／U；合法空、全休市和未知不混淆。N／remaining的null不是0，F不是剩余总数，未知范围不伪造成功或失败。
- 新26码及四停止快照通过真实Axios解析，保留已确认小计；无快照通信错误不编造计数或任务，错误数据安全拒绝。
- 请求中条件与重复开始禁用，区间显示“区间下载已开始，不可终止，请等待结果。”；无进度、取消、暂停、自动重发或服务端结束推断。
- metadata错误仍能重新加载；移除旧DOWNLOAD原参数重放，主动再次提交仍是一次独立首次下载。切页不取消／重发，返回显示原请求唯一响应。
- 只有确认的taskId有特定任务入口，精确导航到既有downloads路由的tab=retry-tasks／taskId；T17接入实际页签／详情，T16不冒称任务页面已完成。
- 指定测试／build／静态合同、桌面与窄屏受控观察、差异保护及独立评审通过，记录八项AC增量。T17～T20边界保留。

## Dependencies

### RANGE-T14：正常响应及停止快照

- **Artifact:** [T14设计](../../task-designs/RANGE-T14-design.md) §3～§4、[T14 HTTP验证](../../verification/RANGE-T14-http.md)、[OpenAPI](../../contracts/openapi-v1.yaml)的DownloadResponse／DownloadExecutionResult／RecoveryScope／RecoveryFailure／ApiError、[错误目录](../../contracts/error-codes.md)。
- **Decision:** 正常200五outcome，停止错误UNCONFIRMED；结果恰18字段，S/F/N/H与R/I/U直接来自确认事实。N／remaining可以null；taskId只表示已确认保存且仍有失败的任务。失败、未开始、未知范围分别存在，不从数组长度或原日期推算。
- **Rationale:** 一个HTTP可以包含多个恢复单元，已提交单元不会因后续错误撤销；服务端记录确认与业务确认独立，客户端不能重建历史。
- **Constraint:** 字段、number／null、RequestId及四停止码沿用合同；不更改后端、OpenAPI、来源、日历或数据库。不把F、remaining、recordStatus互相替代；历史失败码文本可显示但不能泄露正文／诊断。
- **Usage:** 按T16固定设计实施最小结果守卫、26码／可选安全快照、结果组件和通信未知分支；真实Axios边界与完整18字段样例验证，不以构造ApiError mock代替wire。
- **Readiness evidence:** T14最终首三HTTP39、Core80、App236、显式MySQL114、完整verify829／acceptance832项Java及各170项前端测试／build通过；实际mapper计数number／null、四停止快照、同请求ID及SQL独立证据通过。该输入只确认HTTP／受控后端，不等于T16页面通过。

### RANGE-T15：当前表单和本地清理

- **Artifact:** [T15设计](../../task-designs/RANGE-T15-design.md)、[T15表单验证](../../verification/RANGE-T15-form.md)；`control-plane/src/components/download/DynamicParameterForm.vue`、`ApiDescription.vue`；`control-plane/src/composables/useParameterForm.js`、`useDownloadFlow.js`；`control-plane/src/views/DownloadView.vue`；`control-plane/src/api/downloads.js`及`control-plane/src/test/fixtures/range-apis.json`。
- **Decision:** 表单消费公开策略，上限来自服务端；49项19／15／1／3／11、38＋11和九形状保持。起止日期选择后为空、合法输入一次POST；用户修改通过change→parametersChanged清理result／error／failedOperation并增加generation，锁定时不改。
- **Rationale:** 输入范围和当前结果必须对应，修改条件不应携带旧结果或旧参数重放；范围规划统一由服务端完成。
- **Constraint:** 不改日期输入／归一化、只读查询、布局主题或生产资源，不加旧参数回退、自动重试或浏览器分批。新结果和旧DOWNLOAD重放尚未迁移，需要T16在既有清理基础上处理，而不是改表单。
- **Usage:** 保留T15表单，扩展useDownloadFlow的本轮executionContext及结束状态；清理上下文与既有状态同步。将现有下载相关旧8字段响应mock迁移为完整18字段，保留真实49项和通用表单回归。
- **Readiness evidence:** T15已COMPLETED：最终7文件96项指定测试、UTC／LA各2文件22项、全量25文件215项、Vite1700模块build、合同8组＋4变异、58资源／791原索引／17文件摘要保护通过。独立规格／质量复审和最终集成／证据PASS，无遗留。受控Chromium1440／390宽度一次POST、32天拒绝、清理和聚焦通过；未调用真实来源，不是T19闭环。

### 现有错误解析和页面生命周期

- **Artifact:** `control-plane/src/api/http.js`、`errors.js`；`control-plane/src/components/download/DownloadResult.vue`；`control-plane/src/layouts/AppLayout.vue`、`control-plane/src/router/index.js`；`control-plane/src/components/common/AsyncStatePanel.vue`及相邻spec。
- **Decision:** http保持RequestId拦截器和130000ms客户端timeout；AppLayout现有KeepAlive保留DownloadView／DatasetView；已有downloads命名路由可承载T17入口query。公共AsyncStatePanel仍只用INITIAL／LOADING／SUCCESS／EMPTY／FAILURE，T16结果映射至现有样式，不扩展公共枚举。
- **Rationale:** 单实例同步执行与浏览器等待不是同一生命周期；切页不应取消Promise，通信丢失也不能推断服务端已结束。复用已有结构能保持最小改动。
- **Constraint:** 当前errors.js只接受旧16码／五字段，不能直接把新错误当作已经可用；T16必须按设计迁移。canRetry只保留metadata重载，不能把error.retryable解释成重发原下载。任务入口只固定T17路由合同，实际任务内容仍由T17交付。
- **Usage:** 在T16读取既有实现后按固定接口扩展；不改AppLayout实现、不添加activation重发或取消钩子，不用Web存储恢复历史。本地通信等待结束可解除控件锁定，但文案必须保留服务端可能继续执行的事实。
- **Readiness evidence:** T15全量215项包含实际新daily合同下的切页保留请求、通用错误／查询及布局回归；生命周期无需另造全局任务状态。

上述直接输入一致：T14提供新wire合同，T15输入已迁移；旧错误／结果展示是T16明确待实施范围。生产来源／日历和任务页未完成是任务边界，不通过放宽校验或假造任务补齐。

## Start Here

1. 完整读取[本项设计](../../task-designs/RANGE-T16-design.md)，核对[看板](tensor-range-task-board.md)的RANGE-T16状态及本交接。
2. 按设计顺序读PRD §2／§5.4／§5.8、TRD §6.4／§8.3／§9、当前flow／result／view／HTTP错误解析，再读T14／T15设计及验证和精确OpenAPI样例。
3. 获得本项明确启动请求后记录READY -> IN_PROGRESS，保存开始时分支／HEAD／原索引和任务外摘要。
4. **实施第一动作：** 按已完成设计整理完整18字段测试样例，先对真实页面PARTIAL的2／1计数、Axios500快照的N=null与确认小计、超时未知且无原参数重试三项写行为RED；然后实现最小响应守卫与状态／结果展示。无需另补设计。

## Risks

- 本交接未运行T16测试，READY不代表新结果页面已经实施；T15的215项是输入基线。T16仍需按设计逐场景验证，不能只替换mock文案。
- 时间线中已保存任务可能继续变化，taskId与计数只属于当前响应。丢回复不能补造任务，查询不到也不证明成功；断连后的再次主动提交可能被服务端409拒绝。
- T17任务内容页尚未实现，本项入口仅建立精确query合同；T17／T19必须验证导航后的真实内容和手动重试，不以链接存在关闭页面验收。
- 生产Tushare日历与完整来源仍保守关闭，生产fixture批次能力留T18，真实socket／进程中断和来源分别由T18／T20验收。ISSUE-008九项继续不依赖、未解决。
- 保留原分支／索引和任务外工作，新增正式文件加入Git，不提交／发布；不自动开始本项。
