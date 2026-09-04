# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M12-T03`
- **Next task:** `M12-T04`
- **Design document:** `docs/task-designs/M12-T04-design.md`
- **Expected next status:** `READY`; transition from `NOT_STARTED` only after this handoff is written.

## Next Task

- **Task:** `M12-T04` — 查询 composable、竞态和超界页处理。
- **Goal:** 交付 `useDatasetQuery`，以不可变请求快照统一管理 `UNQUERIED | LOADING | SUCCESS | EMPTY | FAILURE`、服务端分页、retryable 失败重试和单调 generation，使新查询或 reset 后的旧成功/失败不能覆盖当前页面。
- **Scope:** 只创建 `control-plane/src/composables/useDatasetQuery.js` 和 `useDatasetQuery.spec.js`；不修改页面、组件、router、API 客户端、依赖或配置，不加载元数据、不校验筛选、不解释错误正文、不取消/缓存/持久化请求，也不在客户端计算或修正分页。
- **Acceptance:** `useDatasetQuery()` 精确公开设计冻结的五项 refs、两个 computed 和五个动作；新查询与 page-size 变化回第 1 页，翻页保留来源/数据集/筛选，retry 只重放 retryable 失败快照，stale success/failure 均被忽略，服务端响应页码成为最终事实；严格 RED、聚焦 8/8、完整前端 112/112、构建及精确两文件范围达到设计预期。

## Dependencies

### `M10-T03`

- **Artifact:** `docs/task-designs/M10-T03-design.md`、`control-plane/src/api/datasets.js`、`control-plane/src/api/errors.js` 和 `control-plane/src/api/api.spec.js`；实现提交 `8e4ff0d`、边界修复提交 `caa9987` 与 `890ed88`。
- **Decision:** `queryDataset(pluginId, apiName, criteria)` 是分页记录查询的唯一客户端网络边界，只投影 `tsCode/tradeDateFrom/tradeDateTo/annDateFrom/annDateTo/page/pageSize` 七个查询参数并原样返回 `PageResponse`；HTTP、超时、网络和非契约失败在 API 层归一化为公开 `ApiError` 或 `ClientError`。
- **Rationale:** 路径编码、Axios 配置、请求 ID、参数白名单和错误安全属于共享 API 边界；查询 composable 只需要管理页面内存状态、请求快照、generation、分页和操作感知重试，不能建立第二套网络或错误解释层。
- **Constraint:** 只能导入并调用 `queryDataset`，不得直接使用 Axios/fetch、改写成功 DTO、读取原始错误对象或复制 API schema；筛选键保持 camelCase、动态记录键保持 snake_case，`page/pageSize` 由 composable 加入快照，total/page 响应值不得客户端重算。
- **Usage:** 内部 `execute(snapshot)` 调用 `queryDataset(snapshot.pluginId, snapshot.apiName, {...snapshot.criteria, page, pageSize})`；成功对象原样保存，失败对象原样保存并只读取公开 `retryable`，stale Promise 结果在 composable generation 边界消费。
- **Readiness evidence:** 权威看板中 M10-T03 为 `COMPLETED`；其完成证据记录聚焦 12/12、完整前端 19/19、Vite 构建、公开导出、参数序列化、安全扫描与六文件范围均通过，最终只读复审无 Critical/Important/Minor 并给出 `Ready to merge: Yes`。

唯一直接依赖与 M12-T04 无冲突：M10-T03 冻结请求投影和安全成功/失败边界，M12-T04 在其上增加状态、快照、generation 与用户操作语义，不修改或重复该边界。

## Start Here

按以下顺序读取：

1. `docs/task-designs/M12-T04-design.md`；
2. `docs/superpowers/plans/2026-09-05-m12-t04-query-lifecycle.md`；
3. `docs/task-handoffs/tensor-v1-task-board.md` 的 M12-T04 行与详情；
4. `docs/superpowers/plans/tensor-modules/M12-dataset-ui.md` 的 Global Constraints、Task M12-T04 和 Module Gate；
5. `docs/task-designs/M10-T03-design.md`、`control-plane/src/api/datasets.js`、`errors.js` 与 `api.spec.js`；
6. `docs/design/Tensor_多源证券数据平台_PRD_v1.0.md` 的 6.6、6.7、7.4 和 AC-014，以及 `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 的 11.2、12.4、13.5～13.7 和 20.4；
7. `docs/contracts/openapi-v1.yaml` 的 records 查询参数、`PageResponse` 和 `ApiError` 合同；
8. `docs/task-designs/M11-T03-design.md`、`control-plane/src/composables/useDownloadFlow.js` 及其 spec，只参考 generation、失败快照和 stale Promise 的既有局部模式，不导入或扩展下载状态；
9. `control-plane/src/composables/useDatasetFilters.js` 与 `control-plane/src/components/dataset/DatasetPagination.vue`，只核对筛选快照和受控分页调用接口。

首个实施动作：在 Node.js 24.15.0 下确认 18 files / 104 tests 基线和完整暂存区状态，再只创建计划给出的完整 `useDatasetQuery.spec.js`，运行聚焦命令并取得仅因 `useDatasetQuery.js` 不存在而无法收集测试的严格 RED。

## Risks

- generation 只忽略 stale 结果，不取消底层请求；旧 Promise 必须完整消费，M10 的 130 秒超时仍负责最终释放。
- 公开 page/pageSize 在加载或失败时表示请求意图，成功后必须改为服务端响应事实，并同步内部当前快照。
- retry 使用失败时快照而不是当前表单；筛选修改后调用方必须走新的校验与 `query()`。
- `totalElements === 0` 是唯一 EMPTY 判据；不得改用 `items.length` 或在客户端修补合同不一致。
- 当前工作树存在与本任务无关的 `.idea/misc.xml`、`docs/issues` 暂存项和 `data-plane/**/target/` 变化；实施必须检查完整暂存区、只提交两个精确任务文件并保留这些用户变化。
