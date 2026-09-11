# ISSUE-018 Project Task Board

## Project

- **Project ID:** `ISSUE-018`。
- **Goal:** 用户提交单接口 SINGLE / RANGE 下载后取得持久化任务 ID；后台按批执行并原子入库，支持刷新后查询、失败批次手动重试和服务中断后手动恢复。
- **Scope:** 13 项任务，覆盖可选插件合同、参数绑定、任务存储、后台执行、Tushare 策略、HTTP、前端和验证。保留 40 项下载入口、34 项股票必填 / 6 项非股票规则及旧同步 `/api/v1/downloads`；RANGE 目标为 31 项原生区间 + 3 项逐日，另 6 项仅 SINGLE。不恢复已移除的 9 项接口，不增加多股票、多接口合并、取消、定时、自动失败重试、重启自动续跑、多实例协调、任务删除、历史数据回滚或通用游标框架。
- **Completion condition:** 13 项均为 `COMPLETED`，且详细设计第 6 节与母 issue 关闭条件均有证据。T12 证明基础设施与自动化闭环；T13 逐项证明上游语义、完整性和开放状态。仍有待验证项时，不将母 issue 宣称全部完成；明确不纳入的项目须有已记录的范围决定。
- **Authority:** 本看板是 ISSUE-018 子任务身份、Order、范围、直接依赖、状态和交接路径的唯一权威；不变更 tensor-v1 或 ISSUE-004 看板。
- **Project design:** [docs/task-designs/ISSUE-018-design.md](../../task-designs/ISSUE-018-design.md)，2026-09-11 版本；原文状态仍为详细设计待复核、尚未实施，本次拆分不代替设计复核或真实验证。
- **Sources:** [母 issue 与 40 项官网依据](../../issues/problems/ISSUE-018-date-range-batch-downloads.md) → [已确认方案](../../issues/proposals/ISSUE-018-date-range-batch-downloads.md) → [详细设计](../../task-designs/ISSUE-018-design.md)。实施约束以详细设计为准，母 issue 保留来源与关闭条件。
- **Source baseline:** `452832ed17d484f4f4b4af310546242c604d5f8d`，`feat/download-by-date-range`；与本次读取的 HEAD 一致。

## Workflow

- **Execution:** 用户于 2026-09-11 要求依据 issue18 详细设计拆任务，随后明确要求执行当前任务。初始化 T01 为 `READY`，其他任务为 `NOT_STARTED`；后续执行事实记录在各任务 State evidence。串行执行由用户管理，本看板不额外执行跨任务互斥检查。
- **Next-task selection:** 当前任务完成后，选择 Order 更大的未完成任务中 Order 最小的一项；Dependencies 只记录直接消费的输入，不代替 Order。
- **Successor preparation:** 先记录当前任务完成证据，再完成并链接所选后继的专属设计，之后创建 next-task 交接并准备 `READY`。后继设计或输入有缺口时，保留前项完成事实，不创建不完整交接。
- **Allowed transitions:** `NOT_STARTED -> READY`、`READY -> IN_PROGRESS`、`IN_PROGRESS -> PAUSED`、`PAUSED -> IN_PROGRESS`、`READY -> BLOCKED`、`IN_PROGRESS -> BLOCKED`、`BLOCKED -> READY`、`IN_PROGRESS -> COMPLETED`。
- **Design references:** 总体详细设计是各任务的共享来源，不冒充任何子任务的专属设计。启动实现前完成该任务专属设计，并将准确路径回填对应单元格；不生成占位设计或在前项完成前编写后继交接。
- **Handoff paths:** 沿用 `docs/task-handoffs/README.md` 的项目目录约定：`docs/task-handoffs/ISSUE-018/<task-id>-handoff.md`。首项无前驱交接，各行记录准确交接路径或 `None`。
- **Verification:** 每项实现包含其范围内的行为测试及证据，不把测试全部推迟到 T12。T12 汇总跨模块、真实测试后端和构建门禁；T13 的真实 API 证据与受控上游证据分别记录，不互相替代。

## Tasks

| Order | Task ID | Title | Status | Dependencies | Design document | Handoff |
| ---: | --- | --- | --- | --- | --- | --- |
| 1 | ISSUE-018-T01 | 可选批量插件合同与区间参数绑定 | COMPLETED | None | docs/task-designs/ISSUE-018-T01-design.md | None |
| 2 | ISSUE-018-T02 | 任务与批次模型、迁移和仓储 | COMPLETED | ISSUE-018-T01 | docs/task-designs/ISSUE-018-T02-design.md | docs/task-handoffs/ISSUE-018/ISSUE-018-T02-handoff.md |
| 3 | ISSUE-018-T03 | 能力发现、幂等接收与任务查询 | COMPLETED | ISSUE-018-T01, ISSUE-018-T02 | docs/task-designs/ISSUE-018-T03-design.md | docs/task-handoffs/ISSUE-018/ISSUE-018-T03-handoff.md |
| 4 | ISSUE-018-T04 | 证券数据与批次成功状态原子提交 | COMPLETED | ISSUE-018-T02 | docs/task-designs/ISSUE-018-T04-design.md | docs/task-handoffs/ISSUE-018/ISSUE-018-T04-handoff.md |
| 5 | ISSUE-018-T05 | Tushare 请求上下文与共享节流 | COMPLETED | ISSUE-018-T01 | docs/task-designs/ISSUE-018-T05-design.md | docs/task-handoffs/ISSUE-018/ISSUE-018-T05-handoff.md |
| 6 | ISSUE-018-T06 | Tushare 区间策略、日期规划与参数纠正 | COMPLETED | ISSUE-018-T01, ISSUE-018-T05 | docs/task-designs/ISSUE-018-T06-design.md | docs/task-handoffs/ISSUE-018/ISSUE-018-T06-handoff.md |
| 7 | ISSUE-018-T07 | 通用批次执行、拆分与资源预算 | COMPLETED | ISSUE-018-T03, ISSUE-018-T04, ISSUE-018-T06 | docs/task-designs/ISSUE-018-T07-design.md | docs/task-handoffs/ISSUE-018/ISSUE-018-T07-handoff.md |
| 8 | ISSUE-018-T08 | 手动重试、恢复与后台生命周期 | COMPLETED | ISSUE-018-T07 | docs/task-designs/ISSUE-018-T08-design.md | docs/task-handoffs/ISSUE-018/ISSUE-018-T08-handoff.md |
| 9 | ISSUE-018-T09 | 任务 HTTP 合同、应用装配与日志 | COMPLETED | ISSUE-018-T03, ISSUE-018-T08 | docs/task-designs/ISSUE-018-T09-design.md | docs/task-handoffs/ISSUE-018/ISSUE-018-T09-handoff.md |
| 10 | ISSUE-018-T10 | 前端模式表单、任务提交与近期列表 | READY | ISSUE-018-T09 | docs/task-designs/ISSUE-018-T10-design.md | docs/task-handoffs/ISSUE-018/ISSUE-018-T10-handoff.md |
| 11 | ISSUE-018-T11 | 任务详情、轮询与手动操作 | NOT_STARTED | ISSUE-018-T09, ISSUE-018-T10 | None | None |
| 12 | ISSUE-018-T12 | 跨模块故障验证、浏览器闭环与交付门禁 | NOT_STARTED | ISSUE-018-T08, ISSUE-018-T09, ISSUE-018-T11 | None | None |
| 13 | ISSUE-018-T13 | 真实接口完整性验收与逐项开放 | NOT_STARTED | ISSUE-018-T06, ISSUE-018-T12 | None | None |

## Task Details

以下 Sources 均按阅读顺序列出；前置任务的专属设计、实现与验证结果是执行时应消费的输入，以对应行及 State evidence 为准。未完成项提及的新类型、文件及测试仍是待实施产物，不能作为已存在或已通过的证据。

### ISSUE-018-T01

- **Goal:** 形成不依赖 Web / 数据库 / Tushare 的可选批量合同，并使 HTTP 参数层能明确区分 SINGLE 与 RANGE 描述。
- **Scope:** 新增 `BatchDownloadSupport`、`download/batch/` 值对象与调用上下文；保留原 `DataSourcePlugin.download`。扩展 `DownloadParameters`、唯一 Codec 与 `DownloadParameterResolver` 的显式描述入口，复用已有 JSON 读取、形状匹配和参数校验。新增两类股票 / exchange_id 区间参数，复用两类已有区间参数；补齐公共 ErrorCode 及受新增枚举影响的现有 HTTP / 前端映射，供后续任务直接使用。本项不接收任务、不执行上游、不修改 40 份 YAML。
- **Acceptance:** 四类区间形状各自唯一绑定；日期轴、三种规划模式、能力状态、策略版本与完整性结果表达完整。未知完整性不能表达为已确认成功；非法闭区间及非法描述被拒绝。重复 / 未知字段、非字符串值、非法日期、缺端点、逆序和额外股票条件严格拒绝；新增错误枚举的 switch 分支和 §3.11 兜底映射完整，旧插件与旧绑定回归通过。
- **Dependencies:** None；使用现有插件、参数描述与参数绑定基线。
- **Sources:** ① `docs/task-designs/ISSUE-018-design.md` §3.2–3.3、§3.11、§4、§5.1；② `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/`；③ `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/download/`；④ `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/validation/ParameterValidator.java`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/GlobalExceptionHandler.java`、`control-plane/src/api/errors.js`；⑤ `data-plane/tensor-app/src/test/java/com/akkc/tensor/web/download/DownloadParameterResolverTest.java`、`data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadRequestBindingTest.java`。
- **First action:** 完成本任务专属设计，固定可选接口、值对象不变量、四类 Codec 归属与显式描述解析入口，并回填本行 Design document。
- **State evidence:** 2026-09-11 用户明确要求“按issue18任务看板执行当前任务；先读取其设计文档和交接文件（如有），再按既定工作流完成任务”。完整读取总体设计，完成并链接本项专属设计；无前驱交接，按该启动请求执行 `READY -> IN_PROGRESS`。实施计划：`docs/superpowers/plans/2026-09-11-issue-018-t01.md`。
- **Completion evidence:** 2026-09-11 `IN_PROGRESS -> COMPLETED`。已提供可选 `BatchDownloadSupport`、闭区间 / 模式 / 调用上下文 / 完整性合同；AVAILABLE 拒绝 UNKNOWN，非法端点与描述拒绝。四类 RANGE 形状各唯一 Codec，显式 ApiDescriptor 入口校验并规范化，旧 DatasetKey 入口保留原值及错误顺序。重复 JSON 顶层 / params 字段从旧 200 改为 400，未知 / 非字符串 / 日期 / 股票约束回归通过。十项错误码、SourceException 范围分类、HTTP 兜底、前端和 OpenAPI 枚举一致；旧 DataSourcePlugin、插件依赖、40 份 dataset YAML 未变，未提前实现任务 HTTP / DB / worker。
- **Verification evidence:** `mvn -f data-plane/pom.xml -Dtest='*Test,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' -Dsurefire.failIfNoSpecifiedTests=false test` 退出 0：plugin-api 87、core 88、Tushare 91、fixture 12、app 359，共 637 项，失败 / 错误 / 跳过均 0；同一 Maven 生命周期用 Node 24.15.0 / npm 11.12.1 执行前端 24 文件 / 170 测试及 Vite 生产构建，均成功。专项后端 263 项与前端 API 12 项通过，红灯阶段已观察缺失合同 / 错误映射及重复字段 200 与预期 400 的失败。首次沙箱运行被 JVM 附加 / WireMock 本地端口限制阻断；上述最终命令在获准的正常本地权限下完整重跑通过。`git diff --check` 通过。独立只读代码审查核对专属设计、实现及验证日志，无需修复项。本项不涉及数据库迁移或真实上游验收，不把这次单元回归当作 T12 / T13 完成。

### ISSUE-018-T02

- **Goal:** 任务、计划、批次树及请求快照可以持久化，并通过受约束的 JDBC 操作提供可靠状态和计数事实。
- **Scope:** 新增 V8 两张任务表、领域记录 / 状态、`DownloadTaskRepository`、受限 JSON 编解码与摘要，增加 core 的 Jackson databind 依赖。实现任务 / 批次条件更新、计划和拆分的原子存储、查询聚合所需访问；更新本项触及的迁移与包结构精确断言。接收用例、证券写入参与逻辑、任务执行分别由 T03 / T04 / T07 负责。
- **Acceptance:** 表字段、CHECK / FK / 唯一键、索引、UTC 时间和 JSON 大小限制符合 §3.6；幂等键依赖数据库唯一约束。计划与 `plan_ready`、SPLIT 父节点与两子节点分别同事务提交，失败不留下半份数据。叶子计数排除 SPLIT，成功计数保留；请求与定义摘要稳定且不含凭证。MySQL 8.4.6 新建 / 升级 / 重复迁移通过，V1–V7 不修改；生产 51 表 / 7 迁移，测试 52 表 / 8 迁移，生产注册证券数据集仍为 40，任务表不成为证券数据集。
- **Dependencies:** ISSUE-018-T01；消费日期范围、模式和策略合同以存储规范化快照。
- **Sources:** ① `docs/task-designs/ISSUE-018-design.md` §3.6–3.11、§4 迁移计数、§5.1；② T01 已链接专属设计及合同实现；③ `data-plane/tensor-app/src/main/resources/db/migration/`；④ `data-plane/tensor-core/pom.xml`；⑤ `data-plane/tensor-app/src/test/java/com/akkc/tensor/db/FlywaySchemaContractIT.java`、`data-plane/tensor-app/src/test/java/com/akkc/tensor/build/PackagedJarContractTest.java`、`data-plane/tensor-app/src/test/java/com/akkc/tensor/build/AcceptancePackagedJarContractTest.java`。
- **First action:** 全文读取已链接专属设计，按其中固定的规范化请求摘要和 8 KiB 边界新增 `DownloadTaskJsonTest` 失败用例，再实现最小 JSON 工具及 V8 / 仓储；不再重复设计任务。
- **State evidence:** 2026-09-11 在 T01 完成证据记录后，按预定义 Order 选中本项；完成并链接 `docs/task-designs/ISSUE-018-T02-design.md`，核对 T01 合同输入及迁移基线。设计独立复核发现的高层事务归属和 MySQL JSON 回读大小边界已修正并复核关闭；专属设计可直接实施。随后写入并链接 `docs/task-handoffs/ISSUE-018/ISSUE-018-T02-handoff.md`，执行 `NOT_STARTED -> READY`。本次仅完成后继准备，尚未启动 T02 实现。
- **Start evidence:** 2026-09-11 用户明确要求按 issue18 看板执行当前任务；全文读取本项设计与 T02 交接，并核对 T01 合同及总体设计来源，执行 `READY -> IN_PROGRESS`，保留原交接为入口上下文。实施计划：`docs/superpowers/plans/2026-09-11-issue-018-t02.md`。


- **Completion evidence:** 2026-09-11 `IN_PROGRESS -> COMPLETED`。新增 V8 两张任务表及不可变事实记录；受限 JSON / 稳定 SHA-256 排除凭证，公开策略校验错误固定且无原始 cause。仓储高层拥有事务、T04 三入口加入调用方事务；启动 ID / 轮次 / 状态 / 版本与截止条件隔离旧写入。真实 MySQL 故障触发器证明计划和拆分第二次插入失败全部回滚；并发提交 / 领取只一成功；一致性快照、叶子计数、成功计数保留、重排与中断恢复符合设计。成功计数溢出及调用方旧 RR 快照隐藏兄弟批次计数均以失败回归重现并修复，严格日期拒绝 YYYYMMDDZ。
- **Verification evidence:** 2026-09-11 最终四条设计命令全部退出 0：`mvn -f data-plane/pom.xml -Dtest=DownloadTaskJsonTest,DownloadTaskRepositoryIT,FlywaySchemaContractIT -Dsurefire.failIfNoSpecifiedTests=false test` 实际执行 JSON 20 + 仓储 MySQL IT 17 + Flyway MySQL IT 47 = 84 项；`mvn -f data-plane/pom.xml -Dtest='*Test,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' -Dsurefire.failIfNoSpecifiedTests=false test` 后端 657 项（87/108/91/12/359）；`mvn -f data-plane/pom.xml clean verify` 657 单元 + 4 生产包合同；`mvn -f data-plane/pom.xml -Pacceptance clean verify` 657 单元 + 4 生产包 + 3 验收包合同。所有失败 / 错误 / 跳过均 0；各完整 Maven 生命周期前端 24 文件 / 170 测试及生产构建通过。MySQL 固定 8.4.6，使用 `DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`，Java 21、构建内 Node 24.15.0 / npm 11.12.1。最初环境发现与 app 单模块依赖解析尝试未通过，均已由以上最终 reactor 命令替代；既有 Maven 编码和 Flyway 版本提示不影响实际通过结果。
- **Schema / review evidence:** MySQL 生产新建 7 迁移 / 51 表，测试新建 8 迁移 / 52 表 / 1051 列 / 52 主索引 / 48 非主索引；V7 升级仅应用 V8，旧 checksum 与证券样例保留，重复迁移 0。逐项 CHECK / FK / 唯一约束非法 SQL 被真实拒绝；生产包排除 V6，验收包保留 V8 原字节并只增加 fixture / V6。V1–V7 与生产 40 项 YAML 未变，注册表 789 声明业务列 / 912 实际列 / 34 非主索引保持。JSON、迁移及最终整体独立只读审查通过，所有发现已修复复核关闭；`git diff --check` 与暂存差异检查通过。新增文件已加入 Git，保留已有 T01 暂存成果，未创建提交。
- **Evidence boundary:** 本项仅证明存储与状态事务，不宣称证券数据共同提交、任务接收、worker、真实上游或 T12/T13 已完成。依赖 main / 干净 HEAD 的发布脚本未运行，其断言更新仍按 T12 跟踪。完整日志位于 `/tmp/issue018-t02-focused-final.log`、`/tmp/issue018-t02-units-final.log`、`/tmp/issue018-t02-production-final.log`、`/tmp/issue018-t02-acceptance-final.log`；以上命令及结果为持久完成证据。

### ISSUE-018-T03

- **Goal:** 任务可以只经本地检查接收、幂等找回，并从数据库一致地查询历史及结果。
- **Scope:** 实现 `DownloadTaskService` 的能力组合与提交用例、`DownloadTaskQueryService`；校验参数 / 插件启用 / 能力 / 范围 / 容量，持久化快照、提交幂等与排队时间。提供 retry / resume 后续复用的接收检查；本项不实现 worker、重试转换或 Controller。
- **Acceptance:** 首次接收只在事务提交后返回任务，零上游调用；相同 submissionId 与相同规范化请求返回既有任务，不再次入队，不同请求冲突。已有任务找回优先于当前插件可用性检查。并发容量检查与插入由单实例接收锁保护，排队满或校验失败不留新任务。历史查询在插件停用后仍可用；分页、筛选、稳定排序及默认叶子视图符合 §3.11；详情状态与聚合取自同一只读一致性快照，未规划零批次不冒充成功。
- **Dependencies:** ISSUE-018-T01、ISSUE-018-T02；分别消费 SINGLE / RANGE 描述与解析合同、持久化快照 / 唯一约束 / 仓储查询。
- **Sources:** ① `docs/task-designs/ISSUE-018-design.md` §3.3、§3.6–3.8、§3.10–3.11、§3.13；② T01 / T02 已链接设计与实现；③ `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/DownloadService.java`；④ `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DataSourceController.java`。
- **First action:** 全文读取已链接专属设计与交接，先新增 DownloadTaskServiceTest 的 SINGLE 首次接收和同键规范化重放失败用例，再实现最小服务闭环；不重复设计。
- **State evidence:** 2026-09-11 在 T02 完成与四条门禁证据记录后，按预定义 Order 选中本项，观察源状态 NOT_STARTED。完成 `docs/task-designs/ISSUE-018-T03-design.md`，独立只读复核确认接口、幂等顺序、事务归属、定义变化及测试可直接实施，无待澄清项；先回填 Design document，再创建并链接 `docs/task-handoffs/ISSUE-018/ISSUE-018-T03-handoff.md`。核对 T01/T02 直接输入无冲突，随后执行 `NOT_STARTED -> READY`。仅准备后继，未启动 T03 实现。

- **Start evidence:** 2026-09-11 用户明确要求按 issue18 看板执行当前任务；全文读取 T03 专属设计与交接，核对 T01/T02 输入及总体设计约束后执行 `READY -> IN_PROGRESS`。保留原交接为入口上下文，在已有 `feat/download-by-date-range` 工作区继续并保留前两项暂存成果。实施计划：`docs/superpowers/plans/2026-09-11-issue-018-t03.md`。

- **Completion evidence:** 2026-09-11 `IN_PROGRESS -> COMPLETED`。新增 DownloadTaskService / DownloadTaskQueryService；能力组合保留 SINGLE 描述与 RANGE 门禁，仅本地元数据 / 纯参数转换。接收锁在仓储事务之前取得，覆盖容量与 INSERT；findSubmission 优先于当前开关 / 插件 / 容量，规范化同键重放保持全部任务事实，异义冲突。RANGE 用持久策略重放；SINGLE 原样快照不依赖已移除元数据。validateReplay 先比较定义再校验参数，查询只使用数据库事实和同一 snapshot，不将未规划零批次推导为成功。意外插件元数据 / 参数元数据错误固定脱敏，已分类 TensorException 保留；未增加 worker、HTTP、重试转换或上游执行。
- **Verification evidence:** 四条专属设计命令全部退出 0：`mvn -f data-plane/pom.xml -Dtest=DownloadTaskJsonTest,DownloadTaskServiceTest,DownloadTaskServiceIT,DownloadTaskQueryServiceTest,DownloadTaskRepositoryIT -Dsurefire.failIfNoSpecifiedTests=false test` 为 JSON 20 + Service 17 + Query 5 + MySQL RepositoryIT 17 + ServiceIT 11 = 70；`mvn -f data-plane/pom.xml -Dtest='*Test,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' -Dsurefire.failIfNoSpecifiedTests=false test` 为 679 后端单元（87/130/91/12/359）；`mvn -f data-plane/pom.xml clean verify` 为 679 + 4 生产包合同；`mvn -f data-plane/pom.xml -Pacceptance clean verify` 为 679 + 4 生产包 + 3 验收包合同。所有失败 / 错误 / 跳过均 0；每轮前端 24 文件 / 170 测试及生产构建成功。Java 21、构建内 Node 24.15.0；MySQL 8.4.6 使用 `DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`。Mockito / 容器需要正常本地权限，沙箱 JVM 附加失败已由最终实跑替代。
- **Outcome / review evidence:** MySQL 证明 submit 返回后独立连接可见 QUEUED/version=1/generation=0/planReady=false/零批次；容量 1 的同时接收仅一成功，旧启动 ID 排队项计容量。JDBC latch 强制空查询后另一连接插入同键，等义竞争返回原任务、异义拒绝；触发器拒绝 INSERT 不产生任务，后续接收正常。关闭接收 / 停用插件 / 终态可找回；RANGE 非 Tushare 名称、四标准形状、闰日 / 未来 / 自然日上限、规范化 8 KiB 与 +1、定义改名 / 策略 / 业务键变化及历史分页 / SPLIT 快照均有测试。红灯实证无锁时接收两项、唯一冲突未分类和缺少 RANGE / 事务检查，之后全部转绿。查询专项与整体独立只读审查均通过，错误分类发现已修复复核；V1–V8、来源 YAML、旧同步 DownloadService 和 DataSourceController 相对入口未变，`git diff --check` / 暂存差异检查通过。
- **Evidence boundary:** 未宣称 T04 证券共同事务、HTTP 202、worker 或真实 RANGE 开放已交付，不运行依赖 main / 干净 HEAD 的发布脚本。完整日志：`/tmp/issue018-t03-focused-final.log`、`/tmp/issue018-t03-units-final.log`、`/tmp/issue018-t03-production-final.log`、`/tmp/issue018-t03-acceptance-final.log`；本段命令 / 数量与结果为持久证据。沿用现有分支与暂存工作，不创建提交。

### ISSUE-018-T04

- **Goal:** 每批证券数据、写入计数与 SUCCEEDED 状态要么一起提交，要么一起回滚，包括空批次。
- **Scope:** 为 `PersistenceService` 增加 `PersistenceParticipant` 重载，旧入口委托空参与实现；由 `BatchCommitService` 检查执行许可并写成功状态。保留数据集锁、业务键 Upsert 与现有 60 秒事务上限；persistence 不依赖 task，不将下载或适配放入事务。
- **Acceptance:** 固定数据集锁→任务行→批次行顺序；禁止持有外层数据库事务等待数据集锁。beforeWrite 校验启动 ID、轮次与 RUNNING；旧 worker 的晚到写入回滚。证券写入、计数或成功状态任一失败均整批回滚，失败状态另用短事务保存；空批次同样原子记成功。旧 persist 行为回归通过，`BatchCommitServiceIT` / `PersistenceServiceIT` 用真实 MySQL 证明上述结果。
- **Dependencies:** ISSUE-018-T02；消费任务 / 批次记录、执行许可字段及仓储锁定和更新操作。
- **Sources:** ① `docs/task-designs/ISSUE-018-design.md` §3.9、§3.14、§5.1；② T02 已链接设计与仓储实现；③ `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/PersistenceService.java`；④ `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence/PersistenceServiceIT.java`。
- **First action:** 全文读取已链接专属设计与交接，先在 PersistenceServiceIT 写带真实参与者的空批事务失败用例，再实现最小参与接口 / 重载；不重复设计。
- **State evidence:** 2026-09-11 在 T03 COMPLETED 和四条门禁证据写入后，按预定义 Order 选中本项，观察源状态 NOT_STARTED。完成 `docs/task-designs/ISSUE-018-T04-design.md` 并独立复核通过，锁序、空批、执行许可、错误 / 失败记录归属及测试均可直接实施；明确旧外层事务加入行为按本任务锁序要求收紧，保留旧无参与空批快速路径。先回填 Design document，再写入并链接 `docs/task-handoffs/ISSUE-018/ISSUE-018-T04-handoff.md`，核对 T02 直接输入无冲突后执行 `NOT_STARTED -> READY`。仅完成后继准备，未启动 T04 实现。

- **Start evidence:** 2026-09-11 用户明确要求按 issue18 看板执行当前任务；全文读取 T04 专属设计和交接，并核对 T02 仓储、总体事务约束及现有持久化实现后执行 `READY -> IN_PROGRESS`。保留原交接为入口上下文，沿用当前分支及已有 T01–T03 暂存成果。实施计划：`docs/superpowers/plans/2026-09-11-issue-018-t04.md`。

- **Completion evidence:** 2026-09-11 `IN_PROGRESS -> COMPLETED`。新增通用 PersistenceParticipant、PersistenceService 重载和 BatchCommitService。两持久化入口拒绝外层事务，旧 NONE 空批快速路径保留；真实参与者空批同样开启事务。数据集锁覆盖 REQUIRED / 60 秒事务的实际提交或回滚，固定数据集锁→任务行→批次行；锁内复验运行许可、数据集与截止。证券 Upsert、实际业务键计数与 SUCCEEDED 共同提交；第二 JDBC 批、成功状态触发器或累计 long 溢出均整批回滚，已成功兄弟不变。失败由事务外调用者独立 failBatch，服务不增加失败循环或任务终态推断。旧 RR 预查与 recover/requeue/claim 交错后仍拒绝旧许可；等待锁到期拒绝，已获许可的在途事务可在任务截止后完成。SQL / 事务错误固定脱敏，persistence 不依赖 task。
- **Verification evidence:** 四条专属设计命令全部退出 0：`mvn -f data-plane/pom.xml -Dtest=PersistenceServiceIT,BatchCommitServiceIT,DownloadTaskRepositoryIT,DownloadServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` 实际执行 PersistenceServiceIT 16 + BatchCommitServiceIT 15 + RepositoryIT 17 + 旧 DownloadServiceTest 3 = 51；`mvn -f data-plane/pom.xml -Dtest='*Test,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' -Dsurefire.failIfNoSpecifiedTests=false test` 后端 679（87/130/91/12/359）；`mvn -f data-plane/pom.xml clean verify` 679 单元 + 4 生产包合同；`mvn -f data-plane/pom.xml -Pacceptance clean verify` 679 单元 + 4 生产包 + 3 验收包合同。各项失败 / 错误 / 跳过均 0，四次 reactor 前端 24 文件 / 170 测试及 Vite 构建通过。Java 21.0.11、Maven 3.9.15、构建内 Node 24.15.0 / npm 11.12.1；真实 MySQL 8.4.6 使用 `DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`。最终日志：`/tmp/issue018-t04-focused-final.log`、`/tmp/issue018-t04-units-final.log`、`/tmp/issue018-t04-production-final.log`、`/tmp/issue018-t04-acceptance-final.log`。
- **Review / evidence boundary:** 缺少参与接口与提交服务的红灯已观察；扩展 IT 曾因测试回调使用不同 DataSource 导致审计写入未加入事务，修正测试资源身份后通过。独立最终审查通过，无未解决发现；同线程可重入的锁证明改为跨线程有界 future，另用 doCommit gate 证明实际提交前持锁。`git diff --check` 及暂存差异检查通过；新增实现 / 测试已加入 Git，保留既有 T01–T03 暂存成果，未创建提交。T04 未改 T02 三个参与方法、V1–V8、依赖、40 项 YAML、应用装配或 HTTP；未运行需 main / 干净 HEAD 的发布脚本，不将本项当作 worker 生命周期或真实 API 完整性验收。

### ISSUE-018-T05

- **Goal:** Tushare 的实际请求统一遵守任务预算、停止信号、剩余时限和来源节流，兼容同步入口。
- **Scope:** 扩展 `TushareProClient` 带上下文入口，新增共享 `TushareRequestGate`，在 `TushareProperties` 增加最小请求间隔。每次实际请求先预约预算，再节流与调用；本项不决定日期策略、持久化任务或开放接口。
- **Acceptance:** 同客户端所有请求路径共用节流，默认 1500 毫秒且保留更慢的账户 / 接口约束；规划、批量、后台 SINGLE 与旧同步入口均不能绕开。等待检查停止 / 截止，读超时不超过剩余预算；保持既有 64 MiB 默认响应限制与错误分类。可控时钟 / 受控上游测试证明预算预约、共享间隔和超时，不用长 sleep 猜测；凭证和原始异常不进入任务参数或日志。
- **Dependencies:** ISSUE-018-T01；消费 `BatchCallContext` 预算预约、截止与停止合同。
- **Sources:** ① `docs/task-designs/ISSUE-018-design.md` §3.2、§3.13、§5.1；② T01 已链接设计与调用上下文；③ `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareProClient.java`、`data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/config/TushareProperties.java`；④ `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/client/TushareProClientTest.java`。
- **First action:** 全文读取已链接专属设计与交接，先写 TushareRequestGateTest，以受控 UTC / 单调时钟和计数 context 验证预约→节流→调用及默认 0/1500/3000ms 顺序；观察缺少 Gate 的失败后再实现，不重复设计。
- **State evidence:** 2026-09-11 在 T04 COMPLETED 和四条门禁证据记录后，按 Order 选中本项，观察源状态 NOT_STARTED。完成 `docs/task-designs/ISSUE-018-T05-design.md`，独立设计与传输复核通过；明确保守共享节流、每请求独立超时、发送前停止检查、亚毫秒剩余预算拒绝，以及真实 I/O join 后最终复验，无未解决发现。先回填 Design document，再写入并链接 `docs/task-handoffs/ISSUE-018/ISSUE-018-T05-handoff.md`，核对 T01 和现有客户端直接输入一致后执行 `NOT_STARTED -> READY`。仅完成后继准备，尚未实现 T05。

- **Start evidence:** 2026-09-11 用户明确要求按 issue18 看板执行当前任务；全文读取 T05 专属设计与交接，核对 T01 合同、总体设计和现有客户端后执行 `READY -> IN_PROGRESS`，保留原交接为入口上下文。实施计划：`docs/superpowers/plans/2026-09-11-issue-018-t05.md`。

- **Completion evidence:** 2026-09-11 `IN_PROGRESS -> COMPLETED`。新旧 execute 入口共享实例 Gate，预算恰好预约一次且早于等待；默认完成到下一请求间隔 1500ms，显式 0 与更慢配置均保留。可中断锁等待 / 节流、实际发送前检查和独立超时覆盖全部传输路径；取消和普通完成都 join 实际虚拟 I/O 线程，之后才释放 Gate，拒绝迟到成功 / 失败结果。请求上下文仅存本地 attribute，上游仍为原四字段协议；严格 JSON、股票归属、64 MiB、固定无 cause / suppressed 错误及无自动重试回归通过。
- **Verification evidence:** 2026-09-11 四条设计门禁均退出 0：`mvn -f data-plane/pom.xml -Dtest=TushareRequestGateTest,TushareProClientTest,TushareProClientControlTest,TushareRestClientFactoryTest,TushareProPluginTest,StockScopedDownloadTest -Dsurefire.failIfNoSpecifiedTests=false test` 实际 82 项（Gate 12、Client 17、Control 23、Factory 17、Plugin 9、Stock 4）；`mvn -f data-plane/pom.xml -Dtest='*Test,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' -Dsurefire.failIfNoSpecifiedTests=false test` 后端 723 项（87/130/135/12/359）；`mvn -f data-plane/pom.xml clean verify` 为 723 单元 + 4 生产包合同；`mvn -f data-plane/pom.xml -Pacceptance clean verify` 为 723 单元 + 4 生产包 + 3 验收包合同。失败 / 错误 / 跳过均 0；四次 reactor 的前端 24 文件 / 170 测试与 Vite 生产构建均通过。Java 21.0.11，构建内 Node 24.15.0 / npm 11.12.1。
- **Review / regression evidence:** 独立只读设计符合性与代码质量复核通过，全部发现关闭。受控 HttpServer 证明响应头、慢 body 和非 2xx close 三阶段的停止 / 截止 / 配置超时；忽略一次中断的传输证明 Future 取消后仍保持 gate 至实际退出。先重现并修复 Spring body / close 丢失超时分类、毫秒下取整丢失任务来源、清理期间覆盖停止原因；补上真实 client→gate→I/O 初始化 latch 测试，1500ms 节流 + 2250ms 调度后 native timeout 为 6250ms，停止 / 到期零 HTTP 且保留一次预约。全单元发现的包内方法精确断言已同步并重跑关闭。`git diff --check` 通过。
- **Evidence boundary:** 本项未修改 BatchCallContext、插件公开下载合同、生产 YAML、数据库迁移、任务 worker 或前端；没有真实 Tushare token/API 验收，不宣称 RANGE 已开放或 T12/T13 已完成。原沙箱被本地端口权限限制的命令已用正常本地权限完整重跑。最终日志：`/tmp/issue018-t05-focused-final.log`、`/tmp/issue018-t05-units-final.log`、`/tmp/issue018-t05-production-final.log`、`/tmp/issue018-t05-acceptance-final.log`。保留当前分支已有 T01–T04 暂存成果，未创建提交。

### ISSUE-018-T06

- **Goal:** Tushare 对 34 项 RANGE 目标提供准确的参数、日期轴、规划和完整性策略，同时保留 40 项 SINGLE 入口。
- **Scope:** 新增显式 `TushareBatchPolicies` 注册表、原生 / 自然日 / 交易日规划、纯参数转换及响应范围 / 完整性判断，`TushareProPlugin` 实现可选接口。纠正 `fina_mainbz.yaml` 为 `ts_code` + snapshot，同步旧请求示例及相关回归。注册官网 URL、核验日期、版本与验证标记；不凭自动化测试将真实接口开放，不改变其他旧单次 YAML 合同、不混合 P/D/I。
- **Acceptance:** 清单严格为 31 原生 + top_list 交易日 + dividend / disclosure_date 自然日，另 6 项仅 SINGLE；RANGE 29 项必填股票 / 5 项非股票与全部 34 / 6 股票规则一致。日期校验使用策略规定输出列，包含 ipo_date、ann_date 与报告期 end_date 的差异；不靠过滤越界数据掩盖错误。`<L / =L / >L`、空响应、UNKNOWN 均有正确判断；11 项未知依据保持 NEEDS_VERIFICATION。交易日历无缺日 / 重复 / 越界后才取开市日，空开市列表合法；未验证 BJ 映射与 BSE 直接输入拒绝，不替换交易所。fina_mainbz 的非法 ann_date 请求为 400，默认 type 语义保留待验；移除接口未恢复。
- **Dependencies:** ISSUE-018-T01、ISSUE-018-T05；分别消费可选能力 / 参数合同、可计预算并受节流的真实调用入口。
- **Sources:** ① `docs/task-designs/ISSUE-018-design.md` §2.2、§3.2–3.5、§3.13、§5.1、§7；② `docs/issues/problems/ISSUE-018-date-range-batch-downloads.md` 的 40 项官网依据；③ T01 / T05 已链接设计与实现；④ `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/TushareProPlugin.java`、`data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/fina_mainbz.yaml`；⑤ `docs/contracts/download-request-examples.json`、`data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/metadata/TushareMetadataContractTest.java`、`data-plane/tensor-app/src/test/java/com/akkc/tensor/web/StockScopedDownloadTest.java`。
- **First action:** 全文读取已链接专属设计与交接，新增 TushareBatchPoliciesTest，独立列出 34 项策略和 6 项 SINGLE-only 预期，断言全部生产 NEEDS_VERIFICATION / UNKNOWN、31 原生 / 3 逐日、29 / 5 参数形状及官方来源；观察缺失策略类失败后实现最小注册表，不重复设计。
- **State evidence:** 2026-09-11 在 T05 COMPLETED、四条门禁与独立审查证据记录后，按预定义 Order 选中本项，观察源状态 NOT_STARTED。完成并全文复核 `docs/task-designs/ISSUE-018-T06-design.md`，固定 34 项策略、全部生产未验证门禁、T03 多日纯预检兼容、日历检查顺序及明确验证命令；T01 / T05 直接输入无未解决冲突。先回填 Design document，再写入并链接 `docs/task-handoffs/ISSUE-018/ISSUE-018-T06-handoff.md`，随后执行 `NOT_STARTED -> READY`。本次仅准备后继，未启动 T06 实现。

- **Start evidence:** 2026-09-11 用户要求按 issue18 看板执行当前任务；全文读取 T06 专属设计和交接，核对 T01/T05 合同与总体来源后执行 `READY -> IN_PROGRESS`。保留原交接为入口上下文，继续使用现有 `feat/download-by-date-range` 工作区；启动时工作树干净。实施计划：`docs/superpowers/plans/2026-09-11-issue-018-t06.md`。

- **Completion evidence:** 2026-09-11 `IN_PROGRESS -> COMPLETED`。新增 TushareBatchPolicies / TushareTradeCalendar，插件实现可选五方法；31 原生 + 3 逐日、29 股票 / 5 非股票策略和 6 SINGLE-only 精确保留。34 生产描述全部 NEEDS_VERIFICATION / UNKNOWN，候选依据与受控已验证副本隔离，配置凭证也不开放 RANGE。参数转换兼容 T03 多日纯预检；完整自然日日历先验证再筛开市日，BJ/BSE 门禁不替换条件。响应身份 / 参数 / 字段 / 股票 / 日期轴检查先于完整性，所有22候选阈值的等号也要求拆分；未知空响应不冒充成功。日历、原生、逐日及后台 SINGLE 通过现有 T05 客户端/context，一请求一预约、无自动重试；停止/超时/大小及来源错误保留分类。fina_mainbz SINGLE 已改 ts_code + snapshot，旧 ann_date/type/period/起止参数400且零上游/写入，合法股票请求200并保持原业务键。
- **Verification evidence:** 四条专属设计 Maven 命令最终均退出0：专项12类449项；`mvn -f data-plane/pom.xml -Dtest='*Test,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' -Dsurefire.failIfNoSpecifiedTests=false test` 后端867项；`mvn -f data-plane/pom.xml clean verify` 867单元+4生产包合同；`mvn -f data-plane/pom.xml -Pacceptance clean verify` 867单元+4生产包+3验收包合同。失败/错误/跳过均0；每条生命周期均执行前端24文件/170测试及Vite生产构建。新增批量测试130项（Policies109/Calendar4/Download17）；应用专项213项（Availability2/Stock5/Resolver87/Binding119）。Java21.0.11、构建内Node24.15.0/npm11.12.1。红灯观察到缺失策略类、缺少插件扩展、旧ann_date200和单股票400，修正后通过。中间测试夹具与模块可用测试依赖问题已解决；沙箱JVM/本地端口限制使用获准本地权限重跑，最后验收命令首次自动审批连接中断未执行，核对本地profile后同命令重试通过。
- **Browser / review evidence:** `PATH="$PWD/data-plane/tensor-app/target/frontend/node:$PATH" npm --prefix control-plane run test:e2e -- e2e/stock-download-parameters.spec.js` Chromium3项通过（25.4秒），覆盖34股票表单、6原参数表单及重试快照；本次preview已停止。仅fina_mainbz.yaml变化，其余39 YAML原字节不变；40请求示例保留，固定REQUESTS_SHA为 `6d4c74a1a539b59ac20fb0cbd3ba1fba0954c40ef1209b652f7dcc2192ec932f`，两个专用harness均同步固定值，manifest/表/迁移未改。插件接线专项审查和整体设计/质量独立审查均通过，无开放问题；新文件已加入Git，未创建提交。`git diff --check`、暂存差异检查通过。
- **Evidence boundary:** 本项不执行真实Tushare API、专用MySQL元数据harness或依赖main/干净HEAD的发布脚本；34生产RANGE保持待T13真实验证。未实施worker、HTTP任务路由、前端任务或数据库修改；受控策略和浏览器mock不代替T12/T13。完整日志 `/tmp/issue018-t06-focused-final.log`、`/tmp/issue018-t06-units-final.log`、`/tmp/issue018-t06-production-final.log`、`/tmp/issue018-t06-acceptance-final.log`、`/tmp/issue018-t06-browser.log`；以上命令和结果为持久完成证据。

### ISSUE-018-T07

- **Goal:** 核心执行器能够按持久化计划串行下载、拆分和提交，真实反映部分失败且不绑定 HTTP 生命周期。
- **Scope:** 实现 `DateRangePlanner` 与 `DownloadTaskRunner` 的领取、计划核对、批次执行、二分、预算、错误分类处置与终态聚合。复用 T03 接收任务、T04 原子提交和插件能力；SINGLE 直接生成参数快照相同且无日期范围的一条根批，不套 RANGE 完整性承诺。本项以受控协调器测试执行；生产启动恢复 / 关闭由 T08 装配。
- **Acceptance:** 闭区间二分无漏无重，含跨年、闰年和非标准报告期；原生计划覆盖整个范围，自然日计划逐日完整，交易日计划合法唯一不越界。满额父响应不入库，最小片段仍需拆分或 UNKNOWN 记完整性失败。单批网络 / 适配 / 可记录写入错误后继续其他批次；鉴权、权限、停用、定义变化、限流与预算超限停止任务，保留成功及未执行批次。默认范围 36600 天、节点 10000、每轮预约 5000 次、运行 30 分钟、累计成功 source_rows 1000000 的边界有验证；规划和 SPLIT 父请求计预算，重试不清累计成功行数。未规划不判成功，合法全空可成功，计数不含父响应。用非 Tushare 的 `symbol/from/to` 测试插件与旧 SINGLE fixture 验证核心无来源字段 / 接口分支；超时晚到结果不得入库，调用未退出保持活动登记。
- **Dependencies:** ISSUE-018-T03、ISSUE-018-T04、ISSUE-018-T06；分别消费已接收任务 / 能力与快照、事务提交服务、插件规划与判断实现。core 只依赖可选合同，不导入 Tushare 实现。
- **Sources:** ① `docs/task-designs/ISSUE-018-design.md` §3.2、§3.5、§3.7–3.9、§3.13–3.14、§5.1；② T03 / T04 / T06 已链接设计与实现；③ `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/DownloadService.java`、`data-plane/tensor-core/src/test/java/com/akkc/tensor/core/download/DownloadServiceTest.java`。
- **First action:** 全文读取已链接专属设计与交接，新增 DateRangePlannerTest 的跨闰日 / 跨年闭区间二分和首尾自然日枚举失败用例，观察缺失类红灯后实现最小算法；随后验证 SINGLE 空批原子提交和第二批失败后继续第三批，不重复设计任务。
- **State evidence:** 2026-09-11 在 T06 COMPLETED、四条 Maven 门禁、浏览器与独立审查证据记录后，按预定义 Order 选中本项，观察源状态 NOT_STARTED。完成并全文复核 `docs/task-designs/ISSUE-018-T07-design.md`，独立就绪评审通过，无开放问题；固定同步 runner / 结果合同、持久参数与全范围纯预检的区别、T04兼容停止重载、活动租约实际退出边界、全部叶子成功时的确定终态、领取前截止溢出、五项预算和明确测试矩阵。T03 / T04 / T06 直接输入无未解决冲突。先仅回填 Design document，再写入并链接 `docs/task-handoffs/ISSUE-018/ISSUE-018-T07-handoff.md`，核对交接结构及28项已有文件路径后执行 `NOT_STARTED -> READY`。本次仅完成后继准备，未启动 T07 实现；其新增接口和测试均为待实施内容。

- **Start evidence:** 2026-09-11 用户明确要求按 issue18 看板执行当前任务。全文读取 T07 专属设计与交接，核对总体设计和 T03/T04/T06 直接输入后执行 `READY -> IN_PROGRESS`；保留 T07 交接为入口上下文，保持现有 `feat/download-by-date-range` 分支及 T06 暂存成果。实施计划：`docs/superpowers/plans/2026-09-11-issue-018-t07.md`。

- **Completion evidence:** 2026-09-12 `IN_PROGRESS -> COMPLETED`。新增 DateRangePlanner 与同步 DownloadTaskRunner，复用 T03 包内执行定义、持久计划/片段参数、真实许可与 T04 原子提交；无线程调度或 HTTP 装配。三种计划严格核对，原生闭区间二分保留父节点但不适配/计入成功来源行；空批及已验证空交易日计划正确成功，未规划失败不冒充成功。独立批次失败后继续兄弟；鉴权/限流/定义/预算停止并保留 PENDING，存储结果不明停止后续请求。默认 36600 天、10000 节点（含9998+2/9999+2）、5000预约、30分钟与1000000成功来源行边界通过。已有计划不重建参数，累计计数重排后保留；动态定义变化、普通 SINGLE 晚到响应、owner线程中断和取消后实际调用占用均有回归。新增五参数commit覆盖等待数据集锁期间停止，已获准事务及最终全部成功汇总仍可完成。
- **Verification evidence:** 2026-09-12 专属设计四条 Maven 命令均退出0：显式专项 `DateRangePlannerTest,DownloadTaskRunnerTest,DownloadTaskRunnerIT,DownloadTaskServiceTest,DownloadTaskServiceIT,DownloadTaskRepositoryIT,BatchCommitServiceIT,PersistenceServiceIT,DownloadServiceTest,FixtureDownloadTaskRunnerTest` 共149项（core145、fixture4），含MySQL8.4.6的RunnerIT8/CommitIT18/RepositoryIT17/ServiceIT11/PersistenceIT16；全单元926项（plugin-api87/core185/Tushare269/fixture16/app369）；生产 `clean verify` 为926+4包合同；验收 `-Pacceptance clean verify` 为926+4+3包合同。所有失败/错误/跳过均0，每条完整生命周期前端24文件/170测试及Vite构建通过。日志：`/tmp/issue018-t07-focused-final.log`、`/tmp/issue018-t07-units-final.log`、`/tmp/issue018-t07-production-final.log`、`/tmp/issue018-t07-acceptance-final.log`。Java21；构建内Node24.15.0/npm11.12.1；Colima已启动，MySQL用既有DOCKER_HOST及TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE。本地权限运行解决Mockito附加限制；测试清理外键顺序及fixture BigDecimal数值断言的初始失败均已修正并完整重跑。
- **Review / boundary evidence:** 独立只读审查无重要生产缺陷；五项证据缺口已补齐并复核通过，包括真实事务doCommit完成后抛TransactionSystemException，证明计划/树/数据/成功状态已提交时不会自动重发或降级。三批部分失败、触发器回滚与失败标记不可写、真实退出后恢复及旧许可拒绝均由真实MySQL测试证明。`git diff --check`和暂存差异检查通过，新增生产/测试/计划文件已加入Git，保留T06暂存成果，未创建提交。本项不改34生产RANGE待验证门禁、40项注册、迁移、旧同步入口，不宣称T08生命周期、T09HTTP、T12浏览器或T13真实验收完成；依赖main/干净HEAD的发布脚本未运行。

### ISSUE-018-T08

- **Goal:** 同一任务可手动重试或恢复未完成工作；进程重启和旧 worker 迟到均不能重跑已成功批或污染新执行。
- **Scope:** 完成服务 retry / resume、版本控制、定义摘要比对、启动恢复、单 worker 协调器、活动登记及关闭边界；通过 `DownloadTaskConfiguration` 装配生命周期。数据库队列轮询每秒执行，禁止 CallerRunsPolicy；不添加自动失败重试或重启自动续跑。
- **Acceptance:** FAILED / PARTIAL_FAILED 的 retry 重排 FAILED 并继续已有 PENDING，SUCCEEDED / SPLIT 保留；INTERRUPTED 的 resume 仅重排 PENDING 与 EXECUTION_INTERRUPTED 批，原普通失败保留。双操作及旧版本只有一次转换成功；定义变化拒绝重放，凭证与预算配置变化不改定义摘要。领取才递增轮次 / 尝试数，重排清本轮时间且保留历史计数；队列满不改变原状态。启动在 Flyway / 数据集验证后生成启动 ID，旧 QUEUED / RUNNING 中全部成功的重算终态，其余中断且零自动上游请求。失败状态也无法保存时停止 worker，恢复连接后仅对确认无活动 worker 的任务标中断；旧调用未退出不得 resume 或启动替代 worker。关闭允许在途事务完成，执行许可阻止旧轮次写库；三批第二批失败后仅重试第二批且计数不重复。
- **Dependencies:** ISSUE-018-T07；消费执行器、状态 / 快照 / 原子提交链路及活动 worker 边界，以实现控制转换与生命周期。
- **Sources:** ① `docs/task-designs/ISSUE-018-design.md` §3.7–3.10、§3.13–3.14、§5.1–5.2；② T07 已链接设计、执行器及其直接输入；③ `data-plane/tensor-app/src/main/java/com/akkc/tensor/config/ApplicationConfiguration.java`；④ T04 的事务故障验证结果，用于确认迟到提交保护。
- **First action:** 全文读取已链接专属设计与交接，新增 DownloadTaskRecoveryIT 的真实三批重试失败用例：第二批 SOURCE_NETWORK_ERROR、第一和第三批已提交，调用 service.retry(taskId, expectedVersion) 先观察缺少入口失败，再实现最小控制路径并证明仅第二批再次执行、计数与尝试数不重复；不重复设计。
- **State evidence:** 2026-09-12 在 T07 COMPLETED、四条 Maven 门禁及独立审查证据记录后，按预定义 Order 选中本项，观察源状态 NOT_STARTED。完成并全文复核 `docs/task-designs/ISSUE-018-T08-design.md`；独立就绪评审提出的生命周期故障状态和控制前动态 readiness 两项缺口已修正并复核关闭，无开放问题。固定共用 admissionLock、提交前租约及实际退出释放、版本与定义校验、数据库事实恢复、FAULTED 隔离、关闭时当前启动全部未完成任务恢复，以及 T08 lite 生命周期配置 / T09 生产导入边界。T07 直接输入无未解决冲突；先仅回填 Design document，再写入并链接 `docs/task-handoffs/ISSUE-018/ISSUE-018-T08-handoff.md`，核对模板、18 项既有文件路径及设计链接后执行 `NOT_STARTED -> READY`。仅完成后继准备，尚未启动 T08 实现。

- **Start evidence:** 2026-09-12 用户明确要求按 issue18 看板执行当前任务；全文读取 T08 专属设计与交接，核对总体设计、T07 输入及应用装配后执行 `READY -> IN_PROGRESS`。保留 T08 交接为入口上下文及现有 `feat/download-by-date-range` 分支；开始时工作区干净。实施计划：`docs/superpowers/plans/2026-09-12-issue-018-t08.md`。


- **Completion evidence:** 2026-09-12 `IN_PROGRESS -> COMPLETED`。服务新增 retry/resume/controls，版本、终态、动态 readiness、定义与容量按固定顺序复验；重排及回读失败不盲目重试。三批仅再次下载第二批，累计成功从2增至3，尝试数为1/2/1；resume保留普通失败，SUCCEEDED/SPLIT及已存计划不反转。单协调锁覆盖接收、控制、派发和恢复；提交runnable前登记租约，实际退出finally后释放。启动只恢复旧任务事实，数据库不确定时暂停并仅探测/恢复；executor拒绝及无可靠返回的异常进入不可自动解除的FAULTED。关闭等待实际worker退出，保留已获准事务和最终SUCCEEDED，并恢复当前启动未领取QUEUED。新增显式导入lite配置，真实初始化/catalog先于共享runId及启动；生产导入留T09。
- **Verification evidence:** 2026-09-12 专属设计四条Maven命令全部退出0：显式专项 `DownloadTaskCoordinatorTest,DownloadTaskRecoveryIT,DownloadTaskConfigurationTest,DownloadTaskConfigurationIT,DownloadTaskServiceTest,DownloadTaskServiceIT,DownloadTaskRunnerTest,DownloadTaskRunnerIT,DownloadTaskRepositoryIT,BatchCommitServiceIT,PersistenceServiceIT,FixtureDownloadTaskRunnerTest` 共184项（core173/fixture4/app7），含真实MySQL8.4.6 RecoveryIT16、RunnerIT8、RepositoryIT17、ServiceIT11、CommitIT18、PersistenceIT16、ConfigurationIT4；全单元947项（plugin-api87/core203/Tushare269/fixture16/app372）；生产 `clean verify` 为947+4包合同，验收 `-Pacceptance clean verify` 为947+4+3包合同。全部失败/错误/跳过0，四个生命周期前端24文件/170项及Vite构建均通过。完整日志：`/tmp/issue018-t08-focused-final.log`、`/tmp/issue018-t08-units-final.log`、`/tmp/issue018-t08-production-final.log`、`/tmp/issue018-t08-acceptance-final.log`。Java21，构建内Node24.15.0/npm11.12.1，既有Colima连接；正常本机权限解决Mockito附加及Docker沙箱限制，未清理用户容器。
- **Review evidence:** 独立最终只读审查通过，无开放重要问题。启动恢复失败保留尚未启动门禁且禁止同实例重新start；新控制已在共享锁内确认终态后清除旧PERMIT_LOST排除记录，真实retry/resume提交成功但回执丢失回归证明关闭仍恢复新QUEUED。配置测试初始不合法策略种子已修正并完整重跑；首个TDD红灯为真实三批测试调用缺失retry，后续专项通过。`git diff --check`及暂存差异检查通过；新建实现/测试/计划已加入Git，未创建提交。
- **Boundary evidence:** 本项保持T07 runner公开合同、Repository/V8、40项注册及34项生产RANGE门禁不变；历史查询不依赖协调器。尚未导入生产任务装配、暴露HTTP、完成T12进程/浏览器或T13真实来源验收。要求main/干净HEAD的发布脚本未运行，继续由T12及既有发布流程跟踪；本项完成不关闭母issue。

### ISSUE-018-T09

- **Goal:** 页面与调用方获得完整任务 HTTP 合同，提交立即接收、查询如实返回后台结果，旧同步接口保持兼容。
- **Scope:** 新增任务 Controller / 请求反序列化 / DTO，扩展 DataSourceController 能力入口；装配服务与配置，复用 T01 公共错误码和兜底映射，落实各任务路由的错误响应及任务日志。落地 `docs/contracts/download-task.schema.json`、`docs/contracts/download-task-examples.json`、错误码文档。生命周期机制由 T08 提供；本项负责对外暴露与整体应用接线。
- **Acceptance:** §3.11 七类路由及形状完整；首次创建和操作成功为 202，带规定 Location / ID / version，幂等重放为 200，未知任务 404、版本 / 提交冲突 409、队列满 429。非法字段 / 参数 / 分页和模式描述错配严格拒绝，提交失败零任务 / 零上游；受控上游阻塞时仍先返回接收结果。后台批次失败的 GET 仍为 200 + 状态 / 错误，意外异常兜底映射覆盖新增枚举，旧同步合同与股票规则通过回归。关闭任务功能后停接收 / 领取但历史可查；HTTP 202 不记下载成功，批次事件在状态提交后记录，MDC finally 清理且错误信息脱敏。
- **Dependencies:** ISSUE-018-T03、ISSUE-018-T08；分别消费能力 / 提交 / 查询用例、重试 / 恢复与生命周期服务。
- **Sources:** ① `docs/task-designs/ISSUE-018-design.md` §3.3、§3.11、§3.13、§4、§5.1；② T03 / T08 已链接设计与服务；③ `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/`、`data-plane/tensor-app/src/main/java/com/akkc/tensor/config/ApplicationConfiguration.java`、`data-plane/tensor-app/src/main/resources/application.yml`；④ `data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/error/ErrorCode.java`、`control-plane/src/api/errors.js`；⑤ `docs/contracts/error-codes.md`、`data-plane/tensor-app/src/test/java/com/akkc/tensor/web/DownloadControllerIT.java`。
- **First action:** 全文读取已链接专属设计与交接，新增 DownloadTaskRequestBindingTest：通过生产绑定配置与 precisionModule，把合法 SINGLE 提交读成尚不存在的 DownloadTaskRequest，观察缺失类型/模块红灯；再实现最小绑定并补插件停用后的同 submissionId 重放用例，不重复设计。
- **State evidence:** 2026-09-12 在 T08 COMPLETED、四条Maven门禁及独立最终审查证据记录后，按预定义Order选中本项，源状态NOT_STARTED。使用 designing-task-contracts 完成并全文复核 `docs/task-designs/ISSUE-018-T09-design.md`；独立就绪审查通过，无开放实质问题。固定七路由/严格绑定、历史重放优先级、数字DTO与快照分页、同runId生产接线、兼容afterCommit观察与脱敏日志；枚举/数值serializer/测试依赖已按真实代码核对，运行手册保留T12。T03/T08直接输入无冲突；先仅回填Design document，再写入、核对并链接 `docs/task-handoffs/ISSUE-018/ISSUE-018-T09-handoff.md`，最后执行 `NOT_STARTED -> READY`。仅完成后继准备，尚未启动T09实现。

- **Start evidence:** 2026-09-12 用户明确要求按 issue18 看板执行当前任务；全文读取 T09 专属设计和交接，并核对总体设计及 T03/T08 直接输入，执行 `READY -> IN_PROGRESS`，保留 T09 交接作为入口上下文。实施计划：`docs/superpowers/plans/2026-09-12-issue-018-t09.md`。
- **Completion evidence:** 2026-09-12 全文复核专属设计及 IN_PROGRESS 源状态后执行 `IN_PROGRESS -> COMPLETED`。七类 HTTP 路由、严格提交/控制/查询绑定、白名单数值 DTO、稳定成员及逐项一致性快照、同 runId/settings 的生产生命周期装配、提交后脱敏观察日志及外部 schema/35 个示例均已实现。真实 MySQL HTTP 证明来源阻塞前返回已提交 202，历史重放在满队列/恢复/FAULTED/元数据移除后可找回；三批仅失败批重试、并发控制一 202 一 409、resume 保留普通失败、真实提交回执丢失后 GET 找回及 stale 409 均通过。拒绝请求不污染持久行；disabled 保留历史；202 不增加旧同步成功指标，观察器异常隔离及 MDC 清理已验证。
- **Verification evidence:** 四条设计门禁最终均退出 0：专属设计完整专项 selector 另加 `DownloadTaskControllerTest`，602 项/33 类；`mvn -f data-plane/pom.xml -Dtest='*Test,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' -Dsurefire.failIfNoSpecifiedTests=false test`，1019 项/63 类；`mvn -f data-plane/pom.xml clean verify`，1019 单元 + 4 生产包合同；`mvn -f data-plane/pom.xml -Pacceptance clean verify`，1019 单元 + 4 生产包 + 3 验收包合同。全部失败/错误/跳过为 0，各生命周期前端 24 文件/170 测试及 Vite 构建通过。Java 21、MySQL 8.4.6、Node 24.15.0/npm 11.12.1；专项使用 `DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`。最终日志 `/tmp/issue018-t09-focused-final.log`、`/tmp/issue018-t09-units-final.log`、`/tmp/issue018-t09-production-final.log`、`/tmp/issue018-t09-acceptance-final.log`；前期夹具失败已由以上最终证据替代。
- **Review evidence:** core 与 app/合同独立规格及质量审查通过，无开放问题；app 最初指出的 HTTP 故障/重试证据缺口已补齐并独立复核关闭。生产上下文明确使用编译后的生产迁移，仅 V1–V5/V7/V8；旧测试上下文完整类路径包含 V6，八项迁移断言与实际一致。`git diff --check` 和暂存差异检查通过，新实现/测试/合同/计划已加入 Git；保留 `feat/download-by-date-range`，未创建提交。
- **Boundary evidence:** V1–V8、40 项注册与股票规则、34 项生产 RANGE 的 NEEDS_VERIFICATION 门禁、单 worker 及旧同步接口保持。本项不实现前端，不声称 T12 进程/浏览器/发布门禁或 T13 真实 API 验收通过；母 issue 尚未完成。接续仅准备 Order 10 的 T10 专属设计和交接。

### ISSUE-018-T10

- **Goal:** 下载页按能力选择 SINGLE / RANGE，通过后台任务提交并从服务端近期列表找回任务。
- **Scope:** 新增 `downloadTasks.js` API 与独立 DTO 校验；修改 `useDownloadFlow`、`DownloadView`，复用动态参数表单与现有布局，新增 `DownloadTaskList`。实现模式选择、非敏感提交快照 / submissionId 的 sessionStorage 恢复、列表分页 / 刷新及详情入口；详情页与手动操作由 T11 实现，不重做视觉系统。
- **Acceptance:** AVAILABLE 默认 RANGE，否则默认 SINGLE 并展示原因；模式切换重建参数，日期轴准确且旧单日参数不残留。SINGLE 明示单次不代表完整历史；收到 ID 仅提示任务已接收并释放提交禁用，支持继续选择其他接口。响应不明时同键重放或按 submissionId 找回，刷新优先恢复未确认提交，重复操作不创建第二任务。列表以数据库分页为准，展示参数 / 范围、状态、动态批数、新增 / 更新记录次数与时间；页面可见每 5 秒刷新，请求不重叠并隔离旧响应。旧单次前端 API 方法保留兼容测试；相关组件与提交测试通过。
- **Dependencies:** ISSUE-018-T09；消费能力、提交、任务列表 / submissionId 查询合同与错误映射。
- **Sources:** ① `docs/task-designs/ISSUE-018-design.md` §3.3、§3.11–3.12、§5.1–5.2；② T09 已链接设计、HTTP 实现与新外部合同；③ `control-plane/src/api/downloads.js`、`control-plane/src/composables/useDownloadFlow.js`、`control-plane/src/views/DownloadView.vue`；④ `control-plane/src/components/download/DynamicParameterForm.vue`、`control-plane/src/composables/useDownloadFlow.spec.js`、`control-plane/src/views/DownloadView.spec.js`。
- **First action:** 全文读取已链接专属设计与交接，先新增 downloadTasks.spec.js，通过真实 Axios adapter 返回原始 JSON/202/请求头/Location，断言尚不存在的 submitDownloadTask 发送一次完整提交并返回 bigint version Receipt；观察 RED 后实现最小传输，再补未确认提交与同键恢复，不重复设计。
- **State evidence:** 2026-09-12 在 T09 COMPLETED、四条门禁及独立审查关闭证据记录后，按预定义 Order 选中本项，观察源状态 NOT_STARTED。使用 designing-task-contracts 检查实际 Axios/表单/路由和 T09 合同，完成并全文复核 `docs/task-designs/ISSUE-018-T10-design.md`；独立就绪审查通过，无开放问题。固定能力模式与干净表单、不可变 sessionStorage 提交恢复、服务端列表分页/可见性/退避/互斥、全部数值原 token 校验及 bigint；小数舍入和卸载后异步续体边界已明确，T11/T12 范围保持。T09 直接输入无冲突；先仅回填 Design document，再写入、核对模板及 30 项既有产物路径并链接 `docs/task-handoffs/ISSUE-018/ISSUE-018-T10-handoff.md`，最后执行 `NOT_STARTED -> READY`。仅完成后继准备，尚未启动 T10 实现。

### ISSUE-018-T11

- **Goal:** 用户可以直接打开持久化任务详情，准确查看批次结果并手动 retry / resume。
- **Scope:** 新增 `useDownloadTask`、`DownloadTaskView`、详情路由及必要批次表组件，复用任务 API / DTO 和公共 UI。实现详情 / 批次分页、轮询、旧响应隔离、失败查询提示和手动操作；接入 T10 列表入口及返回下载页导航。
- **Acceptance:** `/downloads/tasks/:taskId` 刷新后直接 GET 同一任务，关闭重开可经服务端列表找回。运行任务详情每 2 秒轮询，终态停止；隐藏暂停时恢复可见立即查询，请求不重叠，卸载 / taskId 切换丢弃旧响应。查询临时失败按 5/10/30 秒退避并提示状态暂不能更新，不把任务改 FAILED。展示失败 / 未执行区间、原因、attemptCount、更新时间与动态批数，不使用固定百分比。retry / resume 依服务端能力独立显示，传 expectedVersion、操作期间禁重复，202 恢复轮询、409 刷新并提示；计数说明与 SINGLE / RANGE 成功含义准确，浏览器交互及前端状态测试通过。
- **Dependencies:** ISSUE-018-T09、ISSUE-018-T10；分别消费详情 / 批次 / 控制合同，以及任务 API、列表导航和提交后的任务身份。
- **Sources:** ① `docs/task-designs/ISSUE-018-design.md` §3.7、§3.10–3.12、§5.1–5.2；② T09 / T10 已链接设计与实现；③ `control-plane/src/router/index.js`、`control-plane/src/api/http.js`、`control-plane/src/composables/useDatasetQuery.js`；④ `control-plane/e2e/download-outcomes.spec.js`、`control-plane/e2e/stock-download-parameters.spec.js`、`control-plane/e2e/ui-redesign.fixtures.js`。
- **First action:** 完成本任务专属设计，固定详情轮询 / 退避状态机、批次分页及版本冲突交互，并回填本行。
- **State evidence:** None。

### ISSUE-018-T12

- **Goal:** 用真实测试后端、MySQL 和受控上游证明基础设施闭环，并完成当前源码、生产包、验收包及浏览器门禁。
- **Scope:** 补齐 §5.1 自动化覆盖与 §3.14 故障窗口，执行跨模块恢复 / 并发 / 事务 / 多来源验证；实现 `DownloadTaskLifecycleIT` 与受其启动的生命周期浏览器 spec，更新普通浏览器 fixtures / 断言和 Playwright 排除规则。核对迁移 / 打包 / `scripts/verify-contracts.sh` 精确断言，补 configuration / first-run 运行手册。汇总基础设施证据，不宣称真实接口全部已验收。
- **Acceptance:** 真实测试后端接收后，浏览器断开时后台调用 / 提交计数继续增长；刷新、关闭重开均查询同任务。同数据库重建应用后不自动重发，手动恢复只执行未完成工作；三批部分失败、响应丢失、拆分原子性、旧轮次、双 retry、数据库不可用等故障有可复现证据。非 Tushare 插件通过任务 / 事务 / 重试闭环，HTTP 测试复用已支持参数形状且未知形状拒绝，不改变生产 40 项注册。生命周期 IT 显式传 URL 与 `TENSOR_TASK_LIVE_E2E=1`，检查子进程退出 0 且 spec 实际执行；默认浏览器套件不误跑受控 spec。§5.2 六条当前源码 / 单测 / 双构建 / 浏览器命令均退出 0，无未解释跳过；MySQL 用 8.4.6，不能用 H2 或纯 route mock 替代。发布脚本保留 main / 干净源码 / HEAD 前置条件，待满足条件按既有流程运行并记录；未运行不写成通过。手册涵盖配置、单实例、接收含义、手动恢复及计数限制。
- **Dependencies:** ISSUE-018-T08、ISSUE-018-T09、ISSUE-018-T11；分别消费生命周期与故障控制、正式 HTTP / 配置 / 包装配、最终页面及轮询操作链路。
- **Sources:** ① `docs/task-designs/ISSUE-018-design.md` §3.14、§4、§5.1–5.2、§6；② T08 / T09 / T11 已链接设计、实现与各项验证记录；③ `data-plane/tensor-app/src/test/java/com/akkc/tensor/`、`control-plane/playwright.config.js`、`control-plane/e2e/`；④ `scripts/verify-contracts.sh`、`docs/runbook/configuration.md`、`docs/runbook/first-run.md`。
- **First action:** 完成本任务专属设计，将自动化场景和故障窗口逐项映射到测试、进程控制、断言与证据位置，并回填本行。
- **State evidence:** None。

### ISSUE-018-T13

- **Goal:** 为每个拟开放 RANGE 接口取得真实语义与完整提取依据，形成 40 项最终处理清单及母 issue 关闭证据。
- **Scope:** 按 §5.3 使用已授权账户开展真实 API 验收，补齐待验证依据；核对日期轴、边界、股票 / 交易所、历史可用范围与业务键计数，按证据更新策略版本 / 验证标记及对应测试。记录 34 项 RANGE 目标和 6 项仅 SINGLE 的逐项结论，关联 ISSUE-017 的 fina_mainbz 未完成项；不自行缩减范围、不凭样例猜上限、不增加类型 / VIP / 新供应商。
- **Acceptance:** 每个开放接口有请求条件、预期覆盖、官方或可核验完整性依据、真实批次数 / 成败空批数、source / insert / update 计数及复查结果。至少覆盖 daily 多日重叠、income 公告区间、fina_indicator 报告期、repurchase 非股票、top_list 交易日、dividend 非交易日公告、disclosure_date 最新公告、trade_cal 完整日历 / 全休市范围；受控满额拆分证据与真实语义证据分别列明。11 项缺失依据未解决前保持 NEEDS_VERIFICATION，少量样例或宽窄区间暂时一致不足以开放；BJ / BSE、标停两项历史范围及 fina_mainbz 默认 type 均有明确处理依据。能力门禁更新后相关回归通过；所有纳入项满足详细设计 §6 与母 issue 关闭条件后才记录完成、更新 issue 状态。仍有待验证项时保留未完成事实；范围排除须引用已有明确决定，不能由看板自行认定。
- **Dependencies:** ISSUE-018-T06、ISSUE-018-T12；分别消费策略清单 / 来源依据 / 可用性门禁、已验证基础设施和故障 / 拆分测试证据。
- **Sources:** ① `docs/task-designs/ISSUE-018-design.md` §3.4、§5.3、§6–7；② `docs/issues/problems/ISSUE-018-date-range-batch-downloads.md` 的逐项官方链接与关闭条件；③ T06 / T12 已链接设计、实现与证据；④ `docs/issues/problems/ISSUE-017-stock-scoped-downloads.md`、`docs/verification/ISSUE-017-stock-scoped-downloads.md`；⑤ `docs/runbook/configuration.md`、`docs/issues/README.md`。
- **First action:** 完成本任务专属设计，建立 40 项验收矩阵，区分已知规则、待补证据、真实用例与开放门禁，并回填本行。
- **State evidence:** None。

## Risks

- **完整性证据缺失：** `adj_factor, suspend_d, income, balancesheet, cashflow, fina_audit, express, repurchase, stk_managers, top10_holders, top10_floatholders` 共 11 项原生区间尚无完整提取依据；这是 T13 的已知外部前置条件，不妨碍先实现 NEEDS_VERIFICATION 门禁，也不表示 T13 已进入 BLOCKED。
- **特殊来源语义：** BJ 日历映射、BSE 直接输入、`slb_sec/slb_sec_detail` 标停历史范围和 `fina_mainbz` 默认类型需实测；数值上限或文档可访问不证明账户权限与数据可用性。
- **事务与执行许可：** 计划、父子拆分及证券数据 / 成功状态均须原子提交；数据库不可用或调用未退出时不能盲目续发。单实例前提继续成立，启动 ID / 轮次校验不构成多实例支持。
- **兼容与演进：** 保留旧同步下载、普通 SINGLE 插件和 40 项查询注册；任务表不计入证券数据集。新参数形状、游标来源仍可能需要扩展合同；旧任务定义摘要变化必须拒绝语义重放。
- **证据边界：** 路由 mock 不能证明浏览器断开后的后台执行；`clean verify` 不自动覆盖所有 `*IT`。真实生命周期验证需要 Docker、Java 21、Maven、Node / npm 与 Chromium；发布脚本的 main / 干净源码条件不可删除。
- **数据语义：** 上游可在批次之间补数 / 修订，不承诺跨批同一时刻快照；新增 / 更新是已提交操作次数，不能当作整段去重证券总量。基础设施通过不等于全部 RANGE 目标完成。
