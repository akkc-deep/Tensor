# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/ISSUE-018/ISSUE-018-task-board.md`。
- **Completed task:** `ISSUE-018-T02`，已先记录 `COMPLETED` 与验证证据。
- **Next task:** `ISSUE-018-T03`，按预定义 Order 选中的后继。
- **Design document:** `docs/task-designs/ISSUE-018-T03-design.md`，已完成、独立复核并回填看板。
- **Expected next status:** `READY`；本交接写入、链接后执行 `NOT_STARTED -> READY`，未启动实现。

## Next Task

`ISSUE-018-T03`：能力发现、幂等接收与任务查询。

目标是只经本地检查接收持久化任务、以 submissionId 找回同一规范化请求，并从数据库一致查询历史和结果。实现 DownloadTaskService 的能力组合 / 提交 / 后续控制复用检查及 DownloadTaskQueryService；不实现 worker、retry / resume 转换、HTTP、证券事务或真实上游。

验收：首次创建只在事务提交后返回；相同键相同请求返回原 task，不同请求冲突。已有键查找优先于当前可用性 / 容量检查；单实例锁包住容量与 INSERT，失败零新任务 / 零上游。历史查询不依赖当前插件，分页筛选、排序、叶子视图及详情一致性保持。完整接口、顺序、错误规则和测试已固定在专属设计。

## Dependencies

### ISSUE-018-T01

- **Artifact:** `docs/task-designs/ISSUE-018-T01-design.md`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/BatchDownloadSupport.java`、`download/batch/`、`descriptor/`、`error/ErrorCode.java`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/validation/ParameterValidator.java`；app `web/download/DownloadParameterResolver.java` 的显式描述入口。
- **Decision:** SINGLE 与 RANGE 参数描述独立；DateRange 是 LocalDate 闭区间，参数列表顺序决定区间端点方向。UNKNOWN 不可 AVAILABLE。batchDescriptor 只读本地元数据，sourceParameters 是纯转换；旧插件只实现 download 仍合法。
- **Rationale:** 接收时必须确认范围和具体组合且零上游，不让数据库核心依赖 Tushare 字段名或 Web 参数类型。
- **Constraint:** T03 不调用 plan/download/downloadBatch/assess/adapt/persist；不把 UNKNOWN 或未验证能力开放；core 不导入 app Codec，后续 T09 再使用强类型绑定。
- **Usage:** 服务选择原单次 / 独立区间 ApiDescriptor，复用 ParameterValidator；RANGE 通过纯 sourceParameters 做组合检查。消费公共错误码和描述不变量，不复制字段规则。
- **Readiness evidence:** 看板 T01 已完成。T02 最终完整回归中 plugin-api 87 项及全部后端 657 项通过，旧绑定和插件兼容回归仍通过；前端 170 项与构建通过。它们不构成真实 RANGE 开放证据。

### ISSUE-018-T02

- **Artifact:** `docs/task-designs/ISSUE-018-T02-design.md`；core `download/task/DownloadTask.java`、`DownloadBatch.java`、`DownloadTaskRepository.java`、`DownloadTaskJson.java`；app `db/migration/V8__create_download_task_tables.sql`；core `DownloadTaskJsonTest.java` / `DownloadTaskRepositoryIT.java`、app `FlywaySchemaContractIT.java`。
- **Decision:** QUEUED 和 submission 唯一键是持久事实；NewTask 自行生成 requestHash，仓储 insert 提交后才返回。高层操作拥有事务并拒绝调用方已有事务；只读分页 / snapshot 使用 REPEATABLE_READ。仅 lockTask/lockBatch/succeedBatch 参与 T04 外层事务，T03 不使用这三入口。
- **Rationale:** 接收不能返回未提交任务，容量锁不能代替唯一索引；状态 / 计数不应从内存或分开的不一致读取拼接。
- **Constraint:** 参数规范化后 8 KiB，批次 / 策略 16 KiB，解析保护 128 KiB、深度 16，排除凭证。SHA-256 包含语义和参数顺序，排除展示 / 可用性 / 资源预算。SINGLE 策略只有 schemaVersion/mode，无法在永久移除元数据后猜旧参数类型。现有 QUERY_FAILED / PERSISTENCE_FAILED、DuplicateKeyException、TASK_STATE_CONFLICT 可区分；不能解析原始 SQL 异常文本。
- **Usage:** submit 组合 findSubmission / queuedCount / insert；query 组合 snapshot / tasks / batches。Counts 的实际访问器是 pending/running/succeeded/failed 等，Page 只有 total/items；T09 再做 HTTP 命名 / 页码封装。RANGE 重放在 JSON 工具增加专属设计规定的包内描述读取方法，复用严格 schema；SINGLE 停用后的规范化使用仍保留的 registry 描述，原样快照重放优先且不依赖元数据。
- **Readiness evidence:** T02 看板已记录四条设计命令全部退出 0：最终专项 JSON 20 + MySQL 仓储 17 + MySQL Flyway 47 = 84 项；完整后端单元 657 项；生产 clean verify 为 657 + 4 包合同，acceptance clean verify 为 657 + 4 + 3 包合同；前端 170 项及生产构建均通过，失败 / 错误 / 跳过均 0。MySQL 8.4.6 实证计划 / 拆分回滚、唯一 / 外键 / CHECK、旧许可、计数、快照、生产 51 表 / 7 迁移和测试 52 表 / 8 迁移；V7 升级旧 checksum / 数据保留，重复 0。最终独立审查无剩余问题。尚未交付 T03 接收或 T04 证券共同事务。

两项直接输入的类型、参数方向、JSON 边界及事务归属与 T03 设计一致，无未解决依赖冲突。

## Start Here

按顺序阅读：

1. `docs/task-designs/ISSUE-018-T03-design.md`，全文。
2. `docs/task-designs/ISSUE-018-design.md` §3.3、§3.6–3.8、§3.10–3.11、§3.13。
3. T01/T02 专属设计、上述合同与仓储源文件，以及看板完成证据。
4. core `registry/PluginRegistry.java`、`registry/AdapterRegistry.java`、`catalog/DatasetCatalog.java`、`validation/ParameterValidator.java`、`download/DownloadService.java`。
5. core `download/task/DownloadTaskRepositoryIT.java` 的真实 MySQL / latch 装配；app `web/DataSourceController.java` 仅作为未来接口边界参考。

第一个实施动作：新建 core `download/task/DownloadTaskServiceTest.java`，按专属设计先写 SINGLE 首次提交与同键规范化等价重放的失败用例，再实现最小服务闭环。以用户下一次明确启动请求记录 `READY -> IN_PROGRESS`，保留本交接为入口上下文，不重复设计。

## Risks

- PluginRegistry.find 只返回可下载插件，但 descriptors 保留停用的单次元数据；幂等找回不能先调用会过滤可用性的旧 listApis。SINGLE 永久删除元数据后的非原样请求无法猜旧规范化规则，按设计明确冲突，历史查询继续可用。
- validateReplay 必须先比较当前定义 hash，再用当前合同校验已存参数，否则字段改名会误报普通参数错误。检查通过不等于容量预留或 worker 已停止，控制转换仍由 T08 完成。
- 单实例锁须覆盖容量与创建，且在数据库事务外取得；T03 不继承调用方事务，不新增下载线程或后台通知依赖。
- 测试需要 Java 21、MySQL 8.4.6 和 Docker。当前 Colima 需 `DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`；设计的实际 MySQL IT 不能由 clean verify 代替。
- 所有 T01/T02 成果及文档已在当前 `feat/download-by-date-range` 工作区暂存，未创建提交；不要重置或覆盖这些直接输入。发布脚本 / 生命周期 / 真实接口开放不由本次存储证据证明。
