# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/data-integrity-task-board.md`。
- **Completed task:** `DATA-INTEGRITY-T04`（COMPLETED）。
- **Next task:** `DATA-INTEGRITY-T05`，按Order=5选择。
- **Design document:** `docs/task-designs/DATA-INTEGRITY-T05-design.md`。
- **Expected next status:** `READY`；完整设计链接后写入本交接，再从`NOT_STARTED`转为`READY`，不表示已经开始实施。

## Next Task

`DATA-INTEGRITY-T05`：报告表、原子持久化与分页查询。

实现三张`tensor_integrity_check_*`报告表、仓库和持久JSON；原子保存任务及完整计划、单元报告及全部问题。历史、结果、问题列表使用共享设计第8节全部过滤、排序、分页约定，总数和页数据在一个RR读取事务内取得。保存当时定义/规则、原范围、时点、精确计数和未完成标记，历史不调用当前规则重算。

验收须实际运行MySQL，证明唯一/外键/状态/计数/日期约束、写失败回滚无孤立或半份报告、并发读快照一致、精确值及null往返、全部过滤/边界/稳定排序。unit_key固定为有序[apiName,symbol]规范JSON的SHA-256。此项不启动任务线程、不接入队列或HTTP、不改变证券表、不自动清理报告。

## Dependencies

### DATA-INTEGRITY-T01

- **Artifact:** `docs/task-designs/DATA-INTEGRITY-T01-design.md`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/integrity/`中的`IntegrityScope`、`IntegrityDescriptor`、`IntegrityRuleDescriptor`、`IntegrityRuleResult`、`IntegrityIssue`、`IntegrityStatistics`、`IntegrityUnitResult`、任务/单元/结论枚举；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/integrity/IntegrityCheckJson.java`。
- **Decision:** 保存不可变原范围、定义/规则版本及证据。Long和BigDecimal通过既有精确JSON写为十进制字符串，未知为null；覆盖率仅展示且不决定PASS，执行状态与业务结论分离。T05仓库接受这些plugin-api合同及自身NewIssue，不依赖执行器的嵌套record。
- **Rationale:** 历史报告必须使用检查当时口径；JSON展示值不能重新转浮点或按当前定义重判，当前插件升级不能改变已保存结果。
- **Constraint:** 不改变既有能力/定义/请求哈希的编码合同；新持久文档使用schemaVersion=1外壳，issue定位JSON保留裸对象/数组。保留完整业务键、date/relatedDates、范围、读取时点、incomplete/issuesComplete。无Token/原始响应/堆栈；T01未规定短文本上限的规则ID、版本、reason和message使用LONGTEXT，不静默截断。
- **Usage:** 实现三表映射、create/saveResult事务、精确JSON读写和历史分页；全部状态/计数验证依照T01不变量及T05专属设计。缺描述、NON_STOCK和未执行计划仍保存明确报告槽位。
- **Readiness evidence:** T01已验收，见`docs/verification/DATA-INTEGRITY-T01.md`。本次T04最终后端回归1318项、专项59项、真实MySQL专项37项均0失败/错误/跳过；其中core与fixture两组T01合同24项仍通过，plugin-api完整88项通过。这些是既有合同可用证据，不是T05持久化验收结果。

仅以上直接依赖；其精确JSON、不可变范围和历史版本约束与T05设计一致，无未解决输入冲突。

## Start Here

1. 完整读取`docs/task-designs/DATA-INTEGRITY-T05-design.md`及看板T05。
2. `docs/task-designs/DATA-INTEGRITY-design.md`第5–8节与上述T01合同。
3. `data-plane/tensor-app/src/main/resources/db/migration/V8__create_download_task_tables.sql`。
4. `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/task/DownloadTaskRepository.java`、`DownloadTaskJson.java`及`data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/task/DownloadTaskRepositoryIT.java`。

第一步：确认迁移最大版本仍为V8，然后在`IntegrityCheckRepositoryIT`写真实MySQL的任务+两个计划单元原子创建用例，并注入第二单元插入失败验证全部回滚；观察缺少迁移/仓库的RED，再按已完成设计实现V9和create。不得先实现T06队列/幂等决策。

## Risks

工作区为`.worktrees/data-integrity` / `feat/data-integrity`，已有Studio及T01–T04混合暂存改动，保留它们；尚未合回原工作区。V9在实施时再次核对。报告JSON是历史解释权威副本，SQL筛选列须由同一结果生成并原子保存。MySQL IT需要本机Docker/端口权限，必须实际执行、不能skip计通过。T05尚无实现或验收结果。
