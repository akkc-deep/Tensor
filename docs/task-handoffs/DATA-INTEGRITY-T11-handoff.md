# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/data-integrity-task-board.md`
- **Completed task:** `DATA-INTEGRITY-T10`
- **Next task:** `DATA-INTEGRITY-T11`
- **Design document:** `docs/task-designs/DATA-INTEGRITY-T11-design.md`
- **Expected next status:** `READY`；本交接写入并链接后执行NOT_STARTED→READY，不表示开始实现。

## Next Task

`DATA-INTEGRITY-T11`：创建检查、口径预览与历史页面。新增顶部“数据完整性”入口、`/integrity`创建/历史页面、口径确认与已保存报告入口。消费T10状态，在提交前固定完整股票/日期/接口范围；缺Token仍允许本地检查；历史、错误及恢复可操作。完整报告内容由T12承担，不改后端或下载流程。

验收包括股票粘贴/去重且不依赖本地清单，严格日期与能力限额、默认全选及清空禁提交、日期轴/快照/N/A/未知预览，能力变化重新确认、提交丢失按原ID查回、历史分页及正确详情URL。1440/1024/390布局和键盘可用，专项/完整前端/构建与stub浏览器通过，实际证据回填并将新文件加入Git。

## Dependencies

### DATA-INTEGRITY-T10

- **Artifact:** `control-plane/src/api/integrityChecks.js`、`integrityDtos.js`、`composables/useIntegrityCheck.js`及同目录spec，`api/errors.js`；设计 `docs/task-designs/DATA-INTEGRITY-T10-design.md`，验收 `docs/verification/DATA-INTEGRITY-T10.md`。
- **Decision:** 六API严格校验requestId、身份和分页；计数字符串转BigInt、null保持null、下载rowLimit走无损JSON。composable拥有能力确认、冻结提交与会话恢复；只在显式submit/resend时POST，不确定先GET原submissionId，GET重连不POST。
- **Rationale:** 报告精确且旧口径可追溯，网络异常/双击/切换页面不能重复创建或显示旧响应。当前能力与下载Token不改变旧提交恢复身份。
- **Constraint:** 历史TaskSummary没有overallStatus，不能推导PASS/UNKNOWN或隐式N+1；显示“查看报告结论”链接。默认能力/apis本地可用性独立于下载。提交必须先confirmCapability再prepare/submit，uncertain锁定新请求；原载荷/ID不可改写。恢复筛选固定page=1/pageSize=20/submissionId且无status，其他历史筛选支持pluginId/status。只读refs通过方法更新；recoveryError、storageError与最初submissionError/requestId分别呈现。新检查由明确prepare才生成新ID。
- **Usage:** 页面创建一个useIntegrityCheck实例，挂载发现待确认请求时显式执行一次GET恢复，接受后跳转receipt/recoveredTask的checkId。历史组件按需调用listIntegrityChecks，使用generation/AbortController，无后台全历史轮询；组件使用BigInt比较分页边界。能力列表为选项全集，接口分类从既有元数据按apiName附加。
- **Readiness evidence:** 看板T10已COMPLETED；Node24.15.0专项4文件/66项、完整前端40文件/663项全部通过，0失败/跳过，生产构建与原生ESM导入通过。独立最终审查Spec/Quality均PASS；两项存储P2已用RED/GREEN回归修复。这里只是adapter/单元证据，真实fixture闭环仍由T13验收。

### 现有工作区与元数据

- **Artifact:** `control-plane/src/layouts/AppLayout.vue`、`router/index.js`、`views/DownloadView.vue`、`components/common/PageHeading.vue`、`components/common/AsyncStatePanel.vue`、`api/dataSources.js`、`utils/date.js`、`utils/format.js`、`style.css`。
- **Decision:** 保持顶部导航、现有视觉token和通用控件；listDataSources提供来源，listApis只提供分类，T10 capability提供实际检查全集与口径。
- **Rationale:** 完整性是当前工作区中的操作流程，导航与键盘行为应一致；分类或下载不可用不能漏掉本地可检查范围。
- **Constraint:** 不复用按downloadAvailable禁用来源的DataSourceSelect；不将BigInt total塞给Number分页组件；不改写既有下载路由/状态和KeepAlive。元数据异步结果需独立generation隔离。
- **Usage:** 按T11设计新增表单、预览、历史和最小报告入口；复用PageHeading/AsyncStatePanel和样式，日期显示沿用现有工具，新增纯表单校验utils。
- **Readiness evidence:** 当前工作区既有页面包含在T10完整前端663项及构建回归中，未被本次修改。

直接输入无未解决冲突。历史结论缺字段、下载来源门禁和Number分页边界均已在T11设计给出具体处理，不需扩展后端。

## Start Here

1. 完整阅读 `docs/task-designs/DATA-INTEGRITY-T11-design.md`，核对本板T11状态；仅收到用户显式启动后转IN_PROGRESS。
2. 依次读共享设计第2、3、9节，T10设计/验收和三个模块，再读上列工作区、元数据、通用控件和样式。
3. 首个实现动作：在 `control-plane/src/utils/integrityForm.spec.js` 编写本地不存在的合法股票仍保留、大小写重复去重，以及恰好达到/超过能力限额的RED；随后实现纯表单解析/校验。按完整设计接入页面，不需重新选择交互方案。

工作区固定为 `.worktrees/data-integrity`、分支 `feat/data-integrity`。保留原Studio/T01–T10混合暂存基线，新增文件按精确路径加入Git；不提交混合基线或合并主分支。

## Risks

历史不含结论时只提供报告结论入口，完整详情由T12实现。分类查询失败不能缩小API选择范围。能力更新不得悄悄删除旧选择；需要用户明确确认。现有构建仍有大chunk提示；本项不开展无关打包重构。stub浏览器用例与T13真实后端证据分开记录。
