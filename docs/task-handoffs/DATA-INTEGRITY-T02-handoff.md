# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/data-integrity-task-board.md`。
- **Completed task:** `DATA-INTEGRITY-T01`（COMPLETED）。
- **Next task:** `DATA-INTEGRITY-T02`（按 Order=2 选择）。
- **Design document:** `docs/task-designs/DATA-INTEGRITY-T02-design.md`。
- **Expected next status:** `READY`；本交接写入后从 `NOT_STARTED` 转为 `READY`，尚未启动实施。

## Next Task

`DATA-INTEGRITY-T02`：本地能力发现与 40 接口口径。

使 enabled=true 且缺 Token 的插件仍可本地检查；为 Tushare 全部 40 个接口建立固定日期轴、股票范围、依赖和限制。范围包含 PluginRegistry 独立本地入口、Tushare 可选能力和规范化、静态描述及明确 UNKNOWN 覆盖规则。daily/weekly/monthly 候选算法在 T08。

验收：旧 find 下载语义不变；旧插件、显式停用和重复 ID 有明确拒绝；本地不存在的合法股票保留；40 项与 manifest/YAML 精确对应、字段有效；两项 snapshot 保留 HISTORY_NOT_STORED，四项 NON_STOCK 为 N/A，其余无可靠全集时 UNKNOWN；撤回 RANGE 的本地描述仍可见；所有描述和规则不访问网络。具体测试与命令见已完成设计。

## Dependencies

### DATA-INTEGRITY-T01

- **Artifact:** `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/IntegrityCheckSupport.java`、同级 `integrity/` 合同；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/integrity/IntegrityCheckJson.java`；`docs/task-designs/DATA-INTEGRITY-T01-design.md`。
- **Decision:** 可选能力继承 DataSourcePlugin；股票接口一条 COVERAGE、NON_STOCK 无规则；通过 `IntegrityContracts.validate/validatePlugin` 验证实际实现与声明；核心三条规则描述由 coreRules(definition) 提供。能力哈希输入有序 ApiSnapshot，包含目标/参考定义及全部规则版本。
- **Rationale:** 来源规则可扩展且不改变旧插件；固定口径可追溯，能力变化必须使旧快照失效。
- **Constraint:** plugin-api 不依赖 core/Jackson；规则不持有网络/SQL；Long/BigDecimal 精确编码、null 不变零；快照没有历史轴，未知不冒充 N/A；不改下载可用性以开放本地检查。
- **Usage:** Tushare 实现三个可选方法、建立 40 项描述及规则，用 T01 校验器检查定义；注册层按 enabled 和能力类型提供独立查找。后续 core 消费 T01 哈希接口，来源代码不反向依赖 core。
- **Readiness evidence:** `docs/verification/DATA-INTEGRITY-T01.md`：17 项 IntegrityPluginContractTest 与 1158 项后端单元测试通过，0 失败/错误/跳过；独立审查问题已修复并复查。范围不包含 SQL/真实比较/执行器行为验收。

直接输入均遵循共享设计，没有已知冲突。T01 fixture 的 PROVEN 是合成测试依据，不能替代 Tushare 生产全集。

## Start Here

1. `docs/task-designs/DATA-INTEGRITY-T02-design.md`（已完成的实施设计）。
2. `docs/task-designs/DATA-INTEGRITY-design.md` 第 1–3、10 节和本任务板 T02。
3. `docs/data-template/manifest.json`、`data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/`。
4. T01 上述合同与 `docs/verification/DATA-INTEGRITY-T01.md`。
5. `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/registry/PluginRegistry.java`、对应 `RegistryTest.java`。
6. `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TushareProPlugin.java`。

第一步：在 core 的 `IntegrityPluginContractTest` 编写“启用但缺 Token 的可选检查插件：findIntegrity 可用、find 仍为空”的失败测试，再实现设计固定的独立入口。不要重新选择日期轴或从 SINGLE/queryMode 推断口径。

## Risks

在 `.worktrees/data-integrity` / `feat/data-integrity` 继续，已有 Studio 暂存基线需保留；T01 改动已暂存但没有将其他任务改动打包提交，也未合回原工作区。当前 `verify-contracts.sh` 要求专用 Maven 缓存与 clean main，未在本分支通过；本地全后端单元回归需要 Mockito agent 与 WireMock 端口权限。实际 MySQL/全链路验收属于后续任务。
