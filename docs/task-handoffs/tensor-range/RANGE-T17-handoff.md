# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-range/tensor-range-task-board.md`（[看板](tensor-range-task-board.md)）。
- **Completed task:** `RANGE-T16`，已记录COMPLETED；完成记录先于本设计和交接。
- **Next task:** `RANGE-T17`，按预定义Order选择17，观察到NOT_STARTED。
- **Design document:** `docs/task-designs/RANGE-T17-design.md`（[完整设计](../../task-designs/RANGE-T17-design.md)），已完整读取、自审及独立就绪评审PASS，并先回填看板Design document；SHA-256为`93dac155c8cc2ca31eb35aee999ee32ed16ae969d34757b9ac37e76178adace3`。
- **Expected next status:** `READY`；本交接写入并链接后执行NOT_STARTED -> READY，不自动启动实现。

## Next Task

`RANGE-T17`：失败任务列表、详情与手动重试页面。

在现有下载页增加“发起下载／失败任务”页签，消费任务API及T16入口，提供独立筛选、分页、原始区间与当前失败范围、只读详情和直接“重试一次”。不增加历史、任务编辑、确认弹窗、取消、轮询或自动重发。

可观察验收：

- T16精确taskId入口真实GET并展示任务，默认20、可选50／100，插件和接口独立筛选，离线标识仍可查看；列表为空时“暂无失败任务”，读取错误不伪装为空。
- 原始1～10日与当前3／7日分开，部分重试后原始不变、GET明细只剩7日；同日两股分别显示。原条件“不适用”、旧区间“未记录”、畸形或未知模式“原始区间未确认”，不恢复历史计数。
- 点击“重试一次”直接发原UUID的一个零字节body POST，不携带原始日期、表单或公共条件。首次与重试双向互锁，canExecute／retrying仅GET快照，POST仍能真实404／409。
- 正常执行后按实际GET更新剩余记录，最后项确认解决后移除；404刷新实际列表，409禁用并说明，不重建／排队。通信未知保留已确认快照或无计数提示，旧执行权限须显式刷新，不自动重发或推断查不到即成功。
- 切页保留同一Promise与执行身份，刷新／重挂只GET当前记录。指定测试、全量／build、静态合同、两尺寸页面观察、保护及独立评审通过，记录六项AC前端增量；T18～T20边界不升级。

## Dependencies

### RANGE-T14：当前失败任务HTTP合同

- **Artifact:** [T14设计](../../task-designs/RANGE-T14-design.md) §2～§6、[HTTP验证](../../verification/RANGE-T14-http.md)、[OpenAPI](../../contracts/openapi-v1.yaml)的三个retry-tasks端点与RetryTaskSummary／Page／Detail／Item／OriginalDateRange／DownloadResponse／ApiError；[错误目录](../../contracts/error-codes.md)。当前生产投影位于`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/RetryTaskResponse.java`。
- **Decision:** 列表／详情只读当前保存记录，双独立筛选、默认20及50／100、updatedAt DESC／taskId DESC、越界回末页、明细完整不分页。Summary11字段，Detail17字段（11＋六项），Page6，Item7；执行结果另为18字段。原始区间由保存两端派生、四status区分，不能从失败范围推算。execute只有UUID，body必须零字节。
- **Rationale:** 成功项原子删除，原任务可以在GET与POST之间变化。保存的原始日期只解释任务来源，重试由服务端重读实际剩余选择器；客户端不能扩大范围或恢复已删除记录。
- **Constraint:** GET不调用业务／日历网络且不占槽，离线插件仍保留保存标识。执行前重新校验，busy409先于不存在404；canExecute／retrying／blocker仅进程快照。taskParams是安全公开字符串对象，不是JSON字符串或可编辑输入；元数据缺失不隐藏任务。全部正文普通文本，不回显凭证／来源响应。
- **Usage:** 按T17已固定的retryTasks.js接口和精确守卫接入，不改变后端或OpenAPI；通过实际Axios验证三个响应边界，通过adapter data undefined及浏览器零字节证明execute请求。列表／详情按合同分别展示原始区间与当前明细；HTTP404／409和错误快照按设计处理。
- **Readiness evidence:** T14已COMPLETED，最终首三HTTP39、Core80、App236、显式MySQL114，完整verify829／acceptance832项Java及各170项前端测试／build通过；实际mapper计数number／null、任务17字段投影、三任务端点、四停止快照、同RequestId和独立SQL证据通过。仅确认受控HTTP／后端输入，不等于T17页面完成或真实来源支持。

### RANGE-T16：本轮结果、错误安全边界与任务入口

- **Artifact:** [T16设计](../../task-designs/RANGE-T16-design.md)、[结果验证](../../verification/RANGE-T16-results.md)；`control-plane/src/api/downloadResult.js`、`downloads.js`、`errors.js`、`http.js`；`control-plane/src/components/download/DownloadResult.vue`、`control-plane/src/composables/useDownloadFlow.js`、`control-plane/src/views/DownloadView.vue`及对应spec／`src/test/fixtures/range-results.json`。
- **Decision:** 正常五outcome及错误UNCONFIRMED；18字段保留原始类型、S/F/N/H与R/I/U、nullable N／remaining、三组实际范围和确认taskId。26码及四停止快照严格解析并复制冻结。DownloadResult的view-task只接受当前结果ID，目标精确为`{name:'downloads',query:{tab:'retry-tasks',taskId}}`。
- **Rationale:** 本轮小计与当前保存失败记录是不同事实；错误不撤销已确认提交，失联不能推算失败、停止或成功。任务入口只传服务端确认的身份，T17负责实际内容及原任务执行。
- **Constraint:** 首次下载的executionContext、表单清理、metadata-only重载和KeepAlive保持。ClientError无虚构计数／任务，不自动重发；taskId=null不能用路径或旧详情补回。T16不提供任务页，不能将入口测试视为列表／execute验收。
- **Usage:** 任务执行复用isDownloadResult和DownloadResult，只导出已有四字段范围守卫，并增加设计规定的两处重试上下文文案。新增独立任务flow，DownloadView协调双向本地互锁、路由GET和非销毁页签；不修改useDownloadFlow、T15表单、查询、路由定义或AppLayout实现。
- **Readiness evidence:** T16已COMPLETED：7文件137项指定、27文件278项全量零失败／跳过，1701模块build、合同8组＋4变异、四停止码真实Axios探针通过。受控Chromium1440／390的PARTIAL／UNCONFIRMED、null／小计、长范围、切页一POST、键盘精确query及新首次RequestId通过。UI／API独立规格质量及最终集成／证据APPROVED；唯一Axios覆盖报告缺口已补证关闭，无遗留。58生产资源、799开始文件中的任务外内容、797原索引及分支HEAD保护通过，14源码／测试／fixture摘要保持；五个新正式文件纳入Git，未提交／发布。

### 现有页面组件与生命周期

- **Artifact:** `control-plane/src/components/common/WorkbenchPanel.vue`、`AsyncStatePanel.vue`、`PageHeading.vue`；`control-plane/src/components/dataset/DatasetPagination.vue`；`control-plane/src/utils/date.js`、`format.js`；`control-plane/src/layouts/AppLayout.vue`及`control-plane/src/router/index.js`。
- **Decision:** 下载页已有KeepAlive，公共状态面板已有通告及错误重载；Element Plus提供页签、输入、按钮和分页。数据查看分页默认50，任务分页独立默认20。日期使用现有严格工具，创建／更新时间用formatIngestedAt并标注北京时间。
- **Rationale:** 当前任务只需局部增量，复用已有结构能保持输入及请求生命周期，不引入第二套主题、全局任务状态或查询改动。
- **Constraint:** 不改AppLayout／路由／DatasetPagination实现，不在activated／deactivated取消或重发。列表筛选不依赖下载metadata，两个flow的状态和错误分开；长范围需换行，所有值Vue转义。
- **Usage:** DownloadView持有两个flow和页签，RetryTaskList／Detail复用公共面板和现有控件；任务分页在自身组件使用el-pagination，保留查询默认值与ARIA。等待中仍可切页，最新路由目标在原执行结算后消费。
- **Readiness evidence:** T16全量278项包含当前首次下载、布局KeepAlive、查询精确数值及公共组件回归；没有新增失败任务页的功能证据。本交接不运行T17测试。

以上直接输入一致：T14提供实际当前记录及UUID执行，T16提供本轮结果及精确入口；原始区间不会成为POST参数，GET快照不会成为历史或永久执行许可。设计就绪评审已将详情字段数更正为实际17并核对Java DTO／OpenAPI；没有待决产品事实。

## Start Here

1. 完整读取[本项设计](../../task-designs/RANGE-T17-design.md)，核对[看板](tensor-range-task-board.md)的RANGE-T17状态和本交接。
2. 按设计顺序读PRD §1.1／§5.5～§5.8、TRD §8、当前DownloadView／DownloadResult／useDownloadFlow／HTTP／公共组件，再消费T14设计／验证、T16设计／验证及精确OpenAPI样例。
3. 获得本项明确启动请求后记录READY -> IN_PROGRESS，保存当时分支／HEAD／原索引和任务外文件摘要；不能把本交接中的T16基线当成T17新基线。
4. **实施第一动作：** 按完成设计整理独立OpenAPI任务fixture，先写真实入口GET展示原1～10／剩3、7、实际Axios零data POST后GET仅7，以及超时不重发／刷新404的三组行为RED；导入缺失与行为失败单列。然后实现最小任务API、独立flow和两个任务组件，无需另补设计。

## Risks

- READY只表示设计与交接就绪，不表示T17已实施。T16的137／278项是直接输入结果，T17必须执行设计的全部命令和场景，记录实际新数量。
- GET与execute之间任务／槽位可变化；404／409是正常并发边界，不能为改善体验增加轮询、排队、确认弹窗或重建任务。未知后显式刷新只读取现存记录，查不到不证明无响应执行成功。
- 任务Detail17字段与DownloadExecutionResult18字段不同；原始compact日期与选择器ISO日期不同，不能共享错误的计数或日期规则。缺少原始日期／元数据时按status展示，不猜模式或历史成功。
- 生产Tushare日历和完整来源仍保守关闭，生产fixture批次能力留T18；完整后端浏览器、真实socket／进程中断及真实来源按T18～T20验收。ISSUE-008九项继续不依赖、未解决。
- 保留开始时原分支／索引和任务外工作，新增正式文件加入Git，不提交／发布；不自动开始本项。
