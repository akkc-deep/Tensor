# STUDIO-T05：最近任务、状态筛选与轮询

## Goal

Studio 右栏展示来自真实任务 API 的最近任务，四个状态分组、总数和分页使用同一服务端查询范围。任务身份以 `docs/task-handoffs/studio-frontend-task-board.md` 为准。

## Scope

- 迁移右栏标题、状态筛选、任务卡片、分页、空态及刷新反馈；验收 1024／1280／1440px PC 浏览器。
- 最小扩展任务查询契约，复用现有轮询、任务受理回调和详情路由。
- 保留历史任务的真实接口、参数、进度、写入次数、时间、错误和完整性语义；详细字段可折叠展开。
- 详情弹窗、批次展示、重试／恢复按钮及后继任务准备不属于本轮。

## Approach

### 分组查询和计数

- `GET /api/v1/download-tasks` 新增可选 `statusGroup`，严格接受 `ACTIVE`（QUEUED／RUNNING）、`DONE`（SUCCEEDED）、`ERROR`（PARTIAL_FAILED／FAILED／INTERRUPTED）。全部不传该字段。与旧 `status` 互斥；空值、未知值、重复参数和两者并传均报 PARAM_INVALID。原来源／接口／提交标识筛选和单状态调用保持兼容。
- Repository `TaskFilter` 增加分组字段和兼容旧调用的四参构造；数据库以参数化 IN 条件先筛选，再按既有 created_at DESC、task_id DESC 排序、计数和分页。响应仍为 page／pageSize／total／items，不添加汇总端点或拉取全量任务。
- 列表 HTTP 响应的任务状态、批次数值、total 和页成员在同一个 REPEATABLE_READ 事务读取。避免旧控制器分页后逐个重查详情导致“进行中”页混入已经完成的任务。独立详情请求保持不变。
- 标题数字和页脚均取当前查询响应的 total（BigInt），页脚明确标注当前分组。与 Demo 的区别：不显示独立的全局“进行中”徽标，也不将选中分组数量冒充全局总数；标题数字具有当前分组的可访问名称。分页继续支持 20／50／100 和最大 int32 页码，越界空页提供回第一页，不隐式改页。

### 状态和生命周期

- `useDownloadTaskList` 增加 `statusGroup` 和 `changeStatusGroup`。切分组回第一页并清空旧查询结果；沿用请求串行、generation 丢弃过期响应和重复刷新合并。
- 分组与旧单状态互斥；保留其他查询条件。提交受理回第一页且保留分组，重新从服务器获取，不向当前页插入模拟行。
- 成功后 5 秒轮询；失败按 5／10／30 秒退避。同查询刷新失败保留最后成功结果并展示错误及上次更新时间；切查询失败不展示旧范围数据。隐藏、停用和卸载停止轮询，恢复可见／重新进入立即刷新。

### 界面

- 按已锁定 Demo：16px 标题、四个带 aria-pressed 的下划线筛选按钮、14px 名称／12px 元信息、列表分隔线和右侧文字状态。标题旁是原生刷新按钮；不展示演示文案。
- 名称使用真实 apiName，来源为 pluginId，链接仍为 `/downloads/tasks/:taskId`。不从 Demo 名称快照推断历史接口显示名。
- 卡片主区显示单次／批量、成功批次／当前计划、尚未生成计划及 lastError；RESPONSE_ONLY／UNKNOWN 完整性提示始终可读。状态继续使用 taskStatusLabel，避免把采集成功写成完整历史保证。
- 原列表的完整参数、批次分项、拆分数、来源行数、写入次数、创建／更新时间和规则版本放入原生 details／summary，避免右栏横向表格，同时保留查看能力及整数精度。
- 使用现有主题变量，长标识／错误可换行，右栏内部滚动；筛选、刷新、展开、链接和分页支持键盘焦点。初次加载、失败、空结果、末页为空都有紧凑反馈和明确恢复入口。

## Files

- `control-plane/src/components/download/DownloadTaskList.vue`、`views/DownloadView.vue`：列表视图和事件接入。
- `control-plane/src/composables/useDownloadTaskList.js`、`api/downloadTasks.js`：分组状态、查询白名单和互斥校验。
- `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskRepository.java`、`DownloadTaskQueryService.java`：分组 SQL 和列表完整快照。
- `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadTaskRequestArgumentResolver.java`、`DownloadTaskController.java`：严格 HTTP 参数和快照映射。
- 对应前后端测试、`control-plane/e2e/studio-tasks.spec.js` 和受影响浏览器定位器；`docs/contracts` 中任务查询契约说明。
- `docs/verification/STUDIO-T05.md` 和截图：记录执行结果；任务板回填设计及状态。

## Tests

- 在 `control-plane` 使用 Node 24：`npm test -- src/api/downloadTasks.spec.js src/composables/useDownloadTaskList.spec.js src/components/download/DownloadTaskList.spec.js src/views/DownloadView.spec.js`，先证明新增分组与卡片行为失败，再实施至通过。
- `npm test`、`npm run build`：现有单元／集成和构建通过。
- `mvn -o -f data-plane/pom.xml -pl tensor-app -am -Dskip.npm=true -Dskip.installnodenpm=true -DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar -Dtest=DownloadTaskRequestArgumentResolverTest,DownloadTaskControllerTest,DownloadTaskQueryServiceTest,DownloadTaskContractTest -Dsurefire.failIfNoSpecifiedTests=false test`：合法分组、非法／重复／互斥参数、兼容单状态及快照映射通过。
- `DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock mvn -o -f data-plane/pom.xml -pl tensor-core -am -Dtest=DownloadTaskRepositoryIT -Dsurefire.failIfNoSpecifiedTests=false test`：显式通过 Surefire 运行 *IT（项目未绑定 Failsafe），真实 MySQL 验证多页混合状态、筛选交集、稳定排序、准确 total、越尾空页和并发一致性。
- 本机 Vite 下运行 `PLAYWRIGHT_BASE_URL=http://127.0.0.1:4174 TENSOR_UI_BASE_URL=http://127.0.0.1:4174 npm run test:e2e -- studio-tasks.spec.js download-tasks.spec.js studio-submission.spec.js studio-shell.spec.js ui-redesign.spec.js`：桌面对照、查询／分页、刷新失败／恢复、受理联动和页面生命周期通过。模拟响应仅用于可重复前端验收；数据库查询另外由 MySQL 集成测试证明。

## Acceptance

- 四组含义与 Demo 相同，多页混合状态下条目、总数和页码均来自对应服务端查询。
- 新任务受理刷新；快速切组不回填旧响应；隐藏、离开、重新进入和卸载的轮询正确。
- 卡片准确呈现真实状态、批量进度、错误和完整性；完整字段仍可访问；长文本和大整数不破坏 PC 右栏。
- 测试通过，Demo 对照和范围限制有记录，新增文件加入 Git。

## Risks

- 前后端需一起发布以支持新参数；不降级为当前页过滤。旧单状态客户端不受影响。
- 历史任务无 displayName，因此显示真实接口编码。独立全局徽标省略，准确计数限定当前查询范围。
- 后端扩展和事务一致性验证使工作量靠近本项原估算上沿（约 1 人日）；不涉及采集或批量执行策略变更。
