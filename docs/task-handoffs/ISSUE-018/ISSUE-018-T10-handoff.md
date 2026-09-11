# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/ISSUE-018/ISSUE-018-task-board.md`。
- **Completed task:** `ISSUE-018-T09`，已先记录 COMPLETED、交付结果、四条验证门禁和独立审查关闭证据。
- **Next task:** `ISSUE-018-T10`，按预定义 Order 选中的下一项。
- **Design document:** `docs/task-designs/ISSUE-018-T10-design.md`，全文复核及独立就绪审查通过，已先回填看板 Design document。
- **Expected next status:** `READY`；本交接写入、核对并链接后执行 `NOT_STARTED -> READY`，尚未启动实现。

## Next Task

`ISSUE-018-T10`：前端模式表单、任务提交与近期列表。

下载页按能力选择 SINGLE / RANGE，经 task API 提交并从服务端近期列表找回任务。新增 downloadTasks.js、独立 DTO、提交快照存储及列表 composable/组件；修改 useDownloadFlow/DownloadView，复用既有动态表单和样式。保留旧 downloadDataset 与 DownloadResult 的兼容测试，页面不再调用同步下载。

验收：AVAILABLE 默认 RANGE，其余默认 SINGLE 并显示原因；模式切换重建参数且日期轴准确。SINGLE 明示单次不代表完整历史。200/202 只表示已接收，获得 ID 即释放锁并允许另一项提交；不确定响应、刷新和重放使用同一 submissionId/不可变快照。恢复先于元数据和普通列表，不依赖当前插件仍可用。列表使用数据库分页、动态批数、完整写入次数和时间，可见时五秒刷新、请求互斥、旧响应隔离，查询失败不更改任务状态。详情 path 链接准确，task int64 保真；本项专项/全前端单测和构建通过，记录实际证据。

## Dependencies

### ISSUE-018-T09

- **Artifact:** `docs/task-designs/ISSUE-018-T09-design.md`；外部合同 `docs/contracts/download-task.schema.json`、`docs/contracts/download-task-examples.json`、`docs/contracts/openapi-v1.yaml`、`docs/contracts/error-codes.md`；HTTP 实现 `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadTaskController.java`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DataSourceController.java`；DTO `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadTaskResponse.java`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadTaskReceipt.java`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadTaskPage.java`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadCapabilitiesResponse.java`。真实验证位于 `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadTaskControllerIT.java`、`data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadTaskContractTest.java`、`data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadTaskRequestBindingTest.java`。
- **Decision:** 首次提交持久化后返回 202+Location/Receipt，不等上游；等价 submissionId 重放为 200，优先于当前可用性检查。请求必须显式 mode，params 只允许字符串。GET 按 submissionId 找不到为 200 空页；历史不依赖插件。任务列表 total/成员/顺序固定，每行状态和计数来自同一快照；可在读取期间推进状态，不重新筛选。version/counts/total/rowLimit 为 JSON int64 **数值**，时间为 UTC ISO，参数为规范化 string map。
- **Rationale:** 浏览器响应丢失不证明未接收，不能更换键重复下载；数据库是任务事实来源，202 不等于下载成功。拆分使批数变化，当前计划不能成为固定百分比分母；新增/更新是提交操作次数。int64 大于 JavaScript Number 安全边界时必须保留原十进制值，尤其不能损失后续控制使用的版本。
- **Constraint:** 保持七类 HTTP 路由、严格状态/字段/null/枚举及错误 requestId 规则。PERSISTENCE_FAILED/500 可能发生在真实提交之后；未知成功响应亦视为未确认。不得因 metadata/能力失败阻断已存任务找回，不用 ErrorCode.retryable 推导后台状态或手动控制许可。保留旧同步 HTTP 和证券 LONG/DECIMAL string；34 项生产 RANGE 仍 NEEDS_VERIFICATION，6 项仅 SINGLE，不通过页面开放它们。
- **Usage:** 本项只调用能力、提交和列表三个入口，按已链接 T10 设计构造独立 DTO/错误边界。在任务请求的 Axios transformResponse 前读取原始 JSON，所有 number token 检查 JSON.parse context.source；int64 转 bigint，缺 source 明确拒绝，不改变共享 http.js。sessionStorage 先保存非敏感冻结快照再 POST；恢复精确查询 submissionId，空页后仅手动同键确认。列表独立生命周期、可见五秒刷新及 5/10/30 秒失败退避；接收后重新 GET 第一页，不伪造列表行。T11 消费同一 Task/bigint 合同实现详情与控制。
- **Readiness evidence:** 2026-09-12 T09 四条最终命令均退出 0：专属设计完整专项 selector 另加 DownloadTaskControllerTest，共 602 项/33 类；全单元 1019 项/63 类；生产 1019+4；验收 1019+4+3。全部失败/错误/跳过 0，各 Maven 生命周期前端 24 文件/170 项及 Vite 构建通过。MySQL 8.4.6、Java 21、Node 24.15.0/npm 11.12.1。HTTP 真实测试覆盖 202 先于来源完成、回执丢失后 GET 找回、同键重放、严格拒绝、查询快照及并发控制；独立 core/app 审查无开放问题。完整命令和结果在看板 T09，日志为 `/tmp/issue018-t09-focused-final.log`、`/tmp/issue018-t09-units-final.log`、`/tmp/issue018-t09-production-final.log`、`/tmp/issue018-t09-acceptance-final.log`。这些不代替尚未实施的 T10 页面证据。

直接输入无未解决冲突。T10 设计已对照实际 Axios、动态表单、路由和 T09 Schema 复核；独立审查指出的小数舍入边界已修正，所有数值原 token 校验及相应用例已固定。恢复之后元数据与普通列表独立启动，卸载后的 await 续体不得重新启动请求。现有 Node 24.15.0 原生 source 支持已核对；这不是 T10 实现测试结果。

## Start Here

按顺序读取：

1. `docs/task-designs/ISSUE-018-T10-design.md` 全文，使用其 API/DTO、模式与存储状态机、分页生命周期、文件和测试合同。
2. `docs/task-handoffs/ISSUE-018/ISSUE-018-task-board.md` T10 范围与验收；`docs/task-designs/ISSUE-018-design.md` §3.3、§3.11–3.12、§5.1–5.2。
3. 上述 T09 专属设计、Schema/示例及真实 HTTP 实现；再读 `control-plane/src/api/http.js`、`control-plane/src/api/errors.js`、`control-plane/src/api/api.spec.js` 的真实 Axios adapter 测法，保留旧 `control-plane/src/api/downloads.js`。
4. `control-plane/src/composables/useDownloadFlow.js`、`control-plane/src/composables/useDownloadFlow.spec.js`、`control-plane/src/views/DownloadView.vue`、`control-plane/src/views/DownloadView.spec.js`；`control-plane/src/components/download/DynamicParameterForm.vue`、`control-plane/src/composables/useParameterForm.js`、`control-plane/src/components/dataset/DatasetPagination.vue`、`control-plane/src/router/index.js`。

第一项实施动作：新增 `control-plane/src/api/downloadTasks.spec.js`，使用真实 http 实例的 adapter 返回原始 JSON text、202、X-Request-Id 和 Location，断言尚不存在的 submitDownloadTask 发送一次完整 submissionId/mode/string params 并返回 version 为 bigint 的 Receipt；先观察缺失模块/入口 RED，再实现最小传输/Receipt 校验，紧接着补不确定提交及同键恢复测试。不要重新设计任务或先改全局 HTTP。

明确启动后记录 `READY -> IN_PROGRESS`，保留本交接。运行 T10 设计中的前端专项、全量测试和 build，保留旧同步 API/组件覆盖；使用 package.json 要求的 Node 24，构建已安装在 `data-plane/tensor-app/target/frontend/node/node`（clean 后可能重建）。新增文件加入 Git，保持 `feat/download-by-date-range` 和当前暂存成果，不自动提交。T10 完成后先记录结果，再按 Order 准备 T11 设计和交接。

## Risks

- 生产 RANGE 没有因基础设施/HTTP 通过而开放；本项 AVAILABLE 为受控夹具，T13 才承担真实来源证据。
- sessionStorage 只保留当前标签页未确认快照，关闭后靠服务端近期列表；损坏内容不能猜键重发，读取/写入失败不得无记录提交。
- 不支持 JSON.parse source 的浏览器无法保真读取数值任务响应，明确拒绝且保留原状态；T11 必须延续 bigint version 和 JSON number token 控制请求，不能退回 Number。
- T10 仅提供 `/downloads/tasks/{taskId}` 链接，实际详情路由/页和 retry/resume 在 T11；T12 接续普通 e2e fixtures 和进程/浏览器/发布门禁，不能把本项模拟 API 测试当作后台持续执行证据。
- 轮询、元数据和提交的 generation 独立；隐藏/卸载不取消后台任务，旧请求的成功、失败及 finally 都不能污染当前状态。
