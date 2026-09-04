# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M10-T03`
- **Next task:** `M10-T04`
- **Design document:** `docs/task-designs/M10-T04-design.md`
- **Expected next status:** `READY`

## Next Task

- **Task ID:** `M10-T04`
- **Title:** 日期、空值、精度格式化和无障碍状态组件
- **Goal:** 为 M11 下载页和 M12 数据集页交付严格下载日期转换、时区明确且精度安全的展示/校验工具，以及无业务状态的可访问异步状态和字段错误组件。
- **Scope:** 精确创建 `date.js`、`format.js`、`validation.js`、`AsyncStatePanel.vue`、`FieldError.vue` 和两个测试文件；只提供已批准的 8 个纯函数、四态提示、actions 插槽与文本错误。不修改依赖、配置、路由、布局、页面或 API 客户端，不创建 M11/M12 业务组件、composable 或成功结果，不请求数据、不解释 `ApiError`，不解析 `DECIMAL`/`LONG` 字符串。
- **Acceptance:** 严格日期/月字符串按 M11 规则转换而 M12 查询日期保持 ISO；入库时间默认/回退 `Asia/Shanghai` 并固定输出到秒；`null`/`undefined`、`0`、空字符串和高精度字符串保持明确区分；三个校验原语在非法正则等边界不抛用户异常；`INITIAL` 不播报、`LOADING`/`EMPTY` 礼貌播报、`FAILURE` 与非空字段错误使用 alert，所有消息为纯文本。Node 24.15.0 下严格 RED 原因正确，聚焦 15/15、全量 34/34 和生产构建满足设计结果，单个实现提交精确包含 7 个新增文件。

## Dependencies

### `M10-T01`

- **Artifact:** `control-plane/package.json`、`control-plane/package-lock.json`、`control-plane/vite.config.js`、`control-plane/vitest.config.js` 和 `control-plane/src/test/setup.js`；对应完成提交为 `90c2029`。
- **Decision:** 前端固定使用 Node `>=24.15.0 <25`、Vue `3.5.42`、Vitest `4.1.11`、Vue Test Utils `2.5.0` 与 jsdom `30.0.1`；`test:unit` 运行 Vitest，测试匹配 `src/**/*.spec.js`，setup 全局安装 Element Plus 并在每个用例后清理 DOM、mock、global 和 environment stub；生产构建使用现有 Vite 8.2.2 配置。
- **Rationale:** M10-T01 建立了可重复安装、真实 SFC mount、DOM 清理和生产构建的统一前端基础，使 M10-T04 不必重新选择运行时、测试器、DOM 环境或插件安装策略。
- **Constraint:** 使用 Node 24.15.0 执行 RED/GREEN、回归和构建；不得修改 package、lock、Vite/Vitest 配置或共享 setup，不得绕开现有清理与 Element Plus 测试行为，也不得引入新依赖。
- **Usage:** 两个新增 spec 由现有 Vitest/jsdom/VTU 链路收集并挂载 Vue SFC；五个生产模块使用当前 Vue/JavaScript/Intl 能力；最终以现有 `test:unit` 与 `build` scripts 验证 34 项回归和生产 bundle。
- **Readiness evidence:** M10-T01 在权威看板中为 `COMPLETED`；上述 package、lock、Vite、Vitest 和 setup 文件当前相对提交 `90c2029` 无差异。2026-09-04 以 Node 24.15.0 重新运行当前前端基线，Vitest 为 4 files / 19 tests 全部通过，Vite 转换 1599 modules 并退出 0，只产生已批准的 Element Plus chunk-size 提示。

M10-T04 仅有这一项权威直接依赖，其运行时、测试和构建约束与 M10-T04 的纯工具、原生语义组件、严格 TDD 和七文件范围互补，无冲突。

## Start Here

1. `docs/task-designs/M10-T04-design.md` 全文。
2. `docs/superpowers/plans/2026-09-04-m10-t04-shared-ui-utilities.md` 全文。
3. `docs/task-handoffs/tensor-v1-task-board.md` 的 `M10-T04` 行与任务详情。
4. `docs/superpowers/plans/tensor-modules/M10-frontend-foundation.md` 的 Global Constraints、M10-T04 任务卡和 Module Gate。
5. `docs/task-designs/M10-T01-design.md`，以及 `control-plane/package.json`、`control-plane/vitest.config.js`、`control-plane/src/test/setup.js` 和现有四个 spec 的测试风格。
6. **First action:** 从仓库根确认工作树为空且 Node 为 `v24.15.0`，然后只创建完整的 `control-plane/src/utils/format.spec.js` 与 `control-plane/src/components/common/AsyncStatePanel.spec.js`，运行 `cd control-plane && npm run test:unit -- --run src/utils/format.spec.js src/components/common/AsyncStatePanel.spec.js`，记录两个 suite 只因目标生产模块尚不存在而在收集期失败的严格 RED。

## Risks

- `Intl.DateTimeFormat` 的默认字符串依赖运行时 locale；实现必须按设计使用 `formatToParts`、24 小时制和固定 ASCII 分隔符，不能直接返回本地化字符串。
- 纯 DATE 不得经过 `Date` 或时区转换，入库时间则必须按真实时刻和指定 IANA 时区转换；合并两条路径会产生日期偏移。
- 元数据 pattern 可能语法非法；`matchesPattern` 必须返回 `false`，不能把 `RegExp` 构造异常泄漏到 UI。
- 当前生产构建稳定产生已批准的 Element Plus chunk-size 提示；它是唯一允许的 warning，不得通过修改 Vite 阈值或依赖注册方式隐藏，也不得接受新增 warning。
- `role="alert"` 在挂载时可能立即播报；后续调用方只能在真实失败或字段错误存在时渲染相应内容。
