# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Completed task:** `M00-T04`
- **Next task:** `M01-T01`
- **Design document:** `docs/task-designs/M01-T01-designs.md`
- **Expected next status:** `READY`

## Next Task

- **Task ID:** `M01-T01`
- **Title:** 五模块 Maven 聚合骨架
- **Goal:** 把现有 `data-plane` 改为包含 `tensor-plugin-api`、`tensor-core`、`tensor-plugin-tushare`、`tensor-plugin-fixture`、`tensor-app` 的 Maven 父聚合工程，并形成 `com.akkc.tensor:tensor-*:1.0-SNAPSHOT` 子模块坐标。
- **Scope:** 只修改 `data-plane/pom.xml` 并创建五个子模块 POM；保留 `data-plane/src/main/java/com/akkc/Main.java`，不添加 Java/Boot/测试依赖、插件管理、模块间依赖、源码、资源、测试、架构门禁或前端变更。
- **Acceptance:** 根 POM packaging 为 `pom` 且按 TRD 3.3 固定顺序聚合五个模块；每个最小子 POM 精确继承 `com.akkc.tensor:data-plane:1.0-SNAPSHOT` 并只声明目录同名 artifactId；结构 RED/GREEN、父子坐标、reactor、Maven validate、旧入口无差异、范围与格式门禁均得到设计注明的预期结果。

## Dependencies

### `M00-T04`

- **Artifact:** `docs/superpowers/task-templates/task-design.md`、`docs/superpowers/task-templates/acceptance-evidence.md`。
- **Decision:** M01～M14 的任务设计固定使用“做什么、怎么做、如何测试、如何验证、依赖什么信息”五部分；实施证据保留 requirement IDs、变更文件、命令、带时区时间、退出码、通过/失败计数、有限摘要和敏感扫描结果，但模板不承载看板状态、权限、事件、交接或恢复信息。
- **Rationale:** 后端骨架任务需要在实施前冻结范围和失败/通过门禁，并在实施后以一致字段证明结果，同时保持项目设计、执行证据和权威看板职责分离。
- **Constraint:** `docs/task-designs/M01-T01-designs.md` 是本任务唯一设计；实施者不得在执行中新增材料接口、依赖版本、模块依赖或证据文件路径，也不得把验证结果写回设计或模板。
- **Usage:** 直接按已完成设计中的精确 POM 结构、命令和预期结果实施；执行摘要按验收证据模板字段保留结果，但当前任务卡未授权创建新的证据文件路径。
- **Readiness evidence:** M00-T04 在权威看板中为 `COMPLETED`；两份模板与冻结设计正文逐字一致，任务级五项门禁、范围/链接检查、M00 模块总门禁和 `git diff --check` 均得到预期结果。

## Start Here

1. `docs/task-designs/M01-T01-designs.md` 全文。
2. `docs/task-handoffs/tensor-v1-task-board.md` 的 M01-T01 行与任务详情。
3. `docs/superpowers/plans/tensor-modules/M01-backend-foundation.md` 的 Task M01-T01。
4. `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 3.3。
5. `data-plane/pom.xml`。
6. `docs/superpowers/task-templates/task-design.md` 与 `docs/superpowers/task-templates/acceptance-evidence.md`。
7. **First action:** 在修改任何 POM 前运行设计“如何测试”中的确定性 XML 结构契约，记录它因缺少 parent packaging、modules 和五个子 POM 而退出 1；确认失败原因准确后再进入最小 POM 实现。

## Risks

- Maven Help Plugin 可能需要本地缓存或网络；插件解析失败是环境问题，不能代替确定性结构契约的预期 RED，也不能作为骨架完成证据。
- 旧 `com.akkc.Main` 文件在父工程改为 `pom` 后仍保留，这是任务卡明确延后到 M09 的范围边界，不得在 M01-T01 删除或替换。
- 当前工作区保留 M00 文档变更；M01-T01 实施与提交必须把范围限制到 `data-plane` 的六个 POM，避免把前驱文档混入后端骨架提交。
