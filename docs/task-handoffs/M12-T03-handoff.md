# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M12-T02`
- **Next task:** `M12-T03`
- **Design document:** `docs/task-designs/M12-T03-design.md`
- **Expected next status:** `READY`; transition from `NOT_STARTED` only after this handoff is written.

## Next Task

- **Task:** `M12-T03` — 20/50/100 分页组件。
- **Goal:** 交付只显示服务端规范分页状态并向调用方发送页码或每页条数变化的受控 `DatasetPagination`，精确提供 20/50/100 选择、总记录数/当前页/总页数摘要、零页和加载禁用语义，以及可见标签和键盘可操作的 Element Plus 控件。
- **Scope:** 只创建 `control-plane/src/components/dataset/DatasetPagination.vue` 和 `DatasetPagination.spec.js`；不修改页面、router、API、composable、共享组件、依赖或配置，不请求网络，不保存/归一/重算分页状态，不管理筛选、查询生命周期、结果、空态、失败、重试或请求世代。
- **Acceptance:** 公开 props 为 `page/pageSize/totalElements/totalPages/disabled`，公开事件仅为 `update:page/update:pageSize`；page size 固定 20/50/100 且默认 50，服务端 totals 摘要原样显示；零页显示 `共 0 条，第 1 / 0 页` 并保留可用 sizes，只有显式 disabled 才整体禁用；严格 RED、聚焦 6/6、全量 104/104、构建及精确两文件范围达到 `docs/task-designs/M12-T03-design.md` 的预期。

## Dependencies

### `M10-T04`

- **Artifact:** `docs/task-designs/M10-T04-design.md`、`control-plane/src/components/common/AsyncStatePanel.vue` 与 `AsyncStatePanel.spec.js`；初始实现提交 `0a61e3f`、严格时间戳修复提交 `0818fbc`。
- **Decision:** M10-T04 把 `INITIAL/LOADING/EMPTY/FAILURE` 查询状态及其 live-region 语义保持为调用方组合职责，业务成功内容由业务组件渲染；共享组件不请求数据、不管理业务状态或生成业务按钮，调用方负责可见标签、ARIA 关联和焦点。
- **Rationale:** M12-T03 必须提供分页自身的可见总数/页码与键盘表面，但不能把加载、空结果或失败状态复制进分页组件；页面组合才能用唯一查询状态驱动面板和 `disabled`。
- **Constraint:** 不修改或导入 `AsyncStatePanel`；分页摘要可使用自身 polite/atomic status 语义，根 nav 必须有可见用途对应的可访问名称，焦点样式不得移除。加载时只响应调用方传入的 `disabled`，不得自行生成 loading/empty/failure 文案或从零页推断整体禁用。
- **Usage:** `DatasetPagination` 遵循 M10-T04 的职责分层，仅渲染成功/零结果均可解释的服务端分页摘要与控件；M12-T05 后续根据 M12-T04 状态决定是否组合状态面板、分页组件以及 disabled 值，无运行时 import。
- **Readiness evidence:** 权威看板中 M10-T04 为 `COMPLETED`；当前设计、`AsyncStatePanel.vue` 和其 spec 相对最终修复提交 `0818fbc` 的范围化差异检查退出 0。

唯一直接依赖与 M12-T03 无冲突：M10-T04 冻结查询状态和无障碍职责边界，M12-T03 只负责服务端分页摘要、受控事件和原生可操作表面。Node.js 24.15.0 下交接前新鲜基线为 17 files / 98 tests 全部通过；Vite 8.2.2 构建转换 1676 modules 并退出 0，仅有既有 Element Plus chunk-size 提示。

## Start Here

按以下顺序读取：

1. `docs/task-designs/M12-T03-design.md`；
2. `docs/task-handoffs/tensor-v1-task-board.md` 的 M12-T03 行与详情；
3. `docs/superpowers/plans/tensor-modules/M12-dataset-ui.md` 的 Global Constraints、Task M12-T03 和 Module Gate；
4. `docs/design/Tensor_多源证券数据平台_PRD_v1.0.md` 的 6.6、7.4 和 AC-014，以及 `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 的 11.2、12.4、13.5～13.7 和 20.4；
5. `docs/contracts/openapi-v1.yaml` 的 records `page/pageSize` 参数与 `PageResponse` 合同；
6. `docs/task-designs/M10-T04-design.md` 与 `control-plane/src/components/common/AsyncStatePanel.vue`、`AsyncStatePanel.spec.js`，只消费状态/无障碍职责边界；
7. 当前 Element Plus 2.14.5 pagination 的公开接口和现有 `control-plane/src/components/dataset` 真实组件测试，只复用受控 props/events、原生交互与局部样式模式。

首个实施动作：在 Node.js 24.15.0 下确认 17 files / 98 tests 基线仍通过并完整检查暂存区，然后只创建完整 `DatasetPagination.spec.js`，运行聚焦命令，取得仅因 `DatasetPagination.vue` 不存在而无法收集测试的严格 RED。

## Risks

- Element Plus 在 `pageCount=0` 时会边界禁用上一页/下一页；不得把它扩大成整个 pagination disabled，page-size 选择器必须继续可用。
- `totalPages` 必须作为唯一 `page-count` 输入；不得再用 `totalElements/pageSize` 派生或规范化页数。
- jsdom 不完整模拟浏览器 Enter/Space 默认点击；单元测试锁定原生按钮/combobox、标签、ARIA、焦点及真实 click/选择，完整键盘闭环留给 M14-T03。
- 当前 JavaScript API DTO 以 number 承载 OpenAPI int64 totals；本任务遵循既有合同，不扩大为 BigInt/string 协议调整。
- 当前工作树包含与任务无关的 `.idea/misc.xml`、`docs/issues` 和 `data-plane/**/target/` 变化；实施必须用精确路径提交并保留这些用户变化。
