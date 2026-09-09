# RANGE-T12 首次下载编排验证

2026-09-09，对应[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的 RANGE-T12，依据[专属设计](../task-designs/RANGE-T12-design.md)与[入口交接](../task-handoffs/tensor-range/RANGE-T12-handoff.md)。Core、显式MySQL、两次完整构建及独立规格／质量／集成评审均已通过。

## 交付行为

- `DownloadService.executeInitial` 先检查访问、适配器和投影参数，再取得共用槽位；完整规划及全部Session预检通过后才开始取数。五类计划、31天上限、原始日期和公共条件保持冻结。
- 完整获取后按时间、股票顺序处理单元及明确来源失败。首次失败保存正常返回才接纳taskId，后续追加同一个任务；保存确认后才继续。九类来源错误均继续本轮后续请求，不自动重试、拆批或调用旧download。
- 只有确认提交才确认同轮摘要并累计S及R/I/U；明确回滚、存储不可用、提交未知和提交确认后的框架故障分别处理。失败保存未知保留F；未知业务单元只在unknown中，不补建失败。N按已知未处理单元计算，未来股票成员未知时为null。
- 进程内唯一槽位覆盖新／旧下载及重试预留lease，在取数、提交、失败保存期间持续持有；等待者不再接收结果不会释放槽位。结果不可变，停止异常只携带安全快照。
- App迁移至唯一八参构造器；旧Controller、DTO和日志仍消费旧execute。生产来源／日历注册表和49项REQUEST策略未升级。

## 测试先行及修正

1. 首先保留原DownloadServiceTest并新增公告DATE的20260901～20260910场景：3日限流、7日超时，逐失败保存确认后才继续，8成功／2失败且原日期冻结。首次定向命令因新结果／槽位类型及executeInitial缺失而编译失败；这仅是缺接口证据。
2. 骨架上的第一次行为测试因沙箱内Mockito无法附加JVM，4项均为初始化错误，不作为行为反例。授权环境重跑4项中原3项通过，新场景因实际事件序列为空失败（1 failure、0 error），形成编排缺失的可执行反例，日志`red-behavior-authorized.log`。
3. 初次实现定向98项通过；扩展过程中分别修正测试定义与API来源参数不一致、Mockito重设桩触发旧answer、公告RANGE／月份来源字段设置错误。另一次沙箱附加错误同样排除，均未放松生产校验。最终Core114项和独立Service20项通过；矩阵扩展不冒称每项均单独观察过失败。
4. App测试先按固定API写入，在Core完成首个RED/GREEN后执行；不把其首轮通过当作App运行RED。首次10项REQUEST／事务与binding25项通过，随后补齐独立STOCK证据并纳入最终六类IT。

## 实际命令与结果

从仓库根执行。Maven按已有授权环境运行，支持Mockito JVM附加、WireMock本地端口及隔离Testcontainers。没有更改依赖、禁用断言或跳过规定检查。

| 命令 | 本轮结果 |
|---|---|
| `mvn -f data-plane/pom.xml -pl tensor-core -am -Dtest=DownloadServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` | 退出0；20项，1.958秒，`service-final-green.log` |
| `mvn -f data-plane/pom.xml -pl tensor-core -am -Dtest=DownloadServiceTest,DownloadExecutionSlotTest,DownloadExecutionResultTest,DownloadBatchPlannerTest,RecoveryUnitProcessorTest,CommittedKeyIndexTest,BatchCommitServiceTest,RetryTaskStorageServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` | 退出0；114项，4.532秒，`core-r2-green.log` |
| `env -u TENSOR_TUSHARE_TOKEN DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=InitialDownloadServiceIT,BatchCommitServiceIT,RetryTaskStorageIT,DownloadControllerIT,FixtureFlowIT,ProductionApplicationContextIT -Dsurefire.failIfNoSpecifiedTests=false test` | 退出0；62项，1分19秒；`app-test-evidence/app-combined.log` |
| `mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=DownloadRequestBindingTest,ControllerUseCaseTest -Dsurefire.failIfNoSpecifiedTests=false test` | 退出0；25＋6＝31项，14.177秒；`app-test-evidence/app-helpers.log` |
| `mvn -f data-plane/pom.xml verify` | 退出0；775项Java检查（771单测＋4生产JAR），170项前端测试及构建，33.423秒 |
| `mvn -f data-plane/pom.xml -Pacceptance clean verify` | 退出0；778项Java检查（771单测＋4生产JAR＋3验收JAR），170项前端测试及构建，38.747秒 |
| `PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py` | 退出0；8组合同及4个变异反例通过 |

上述已完成运行的tests均大于0，failures／errors／skipped均为0。Core八类实际数量为20／1／2／21／41／9／9／11。临时原始日志、最终XML副本及工作区基线位于`.superpowers/sdd/RANGE-T12-design/`；正式结论保存在本文，不使用默认verify的JAR检查代替MySQL。

## MySQL及Core可观察证据

六类显式App IT的最终XML已在clean之前逐一复制并独立解析核对：

| 测试类 | 数量 | 证明范围 |
|---|---:|---|
| InitialDownloadServiceIT | 11 | 首次编排、两表保存门槛、单元回滚／提交未知、同批STOCK隔离、事务外取数及数据集锁边界 |
| BatchCommitServiceIT | 22 | 真实单元提交及精确失败删除的既有事务回归 |
| RetryTaskStorageIT | 13 | 真实两表创建、追加、读取和存储故障回归 |
| DownloadControllerIT | 10 | 唯一八参构造器、两个四参方法及旧HTTP合同回归 |
| FixtureFlowIT | 5 | 原受控Web下载与查询闭环回归 |
| ProductionApplicationContextIT | 1 | 单例slot／commit／storage依赖复用，无凭证及新入口生产拒绝 |

全部使用隔离`mysql:8.4.6`及App真实Flyway迁移，没有复制DDL、使用H2或跳过Docker。测试只清理各自隔离库；来源及SQL故障由本地受控实现、触发器或连接代理注入。

| 场景 | 独立观察和本轮结果 |
|---|---|
| 十日首次下载，3／7日来源失败 | 十日各fetch一次，后续fetch可见此前确认失败；8业务行，一主表、两明细，原始1～10日JSON不变。S/F/N/H=8/2/0/0，R/I/U=8/8/0 |
| REQUEST日期A/B/C，B后SQL组失败 | B的更新／新增候选全部回滚，旧值8保留；A/C已写入，只有B日失败，失败保存完成后才获取C。S/F/N/H=2/1/0/0，R/I/U=2/2/0 |
| 同批独立STOCK A/B/C | 一次乱序完整fina_audit响应；B的3个复合键跨2组，末组失败使整个B回滚，旧B整行不变，A/C保留，只保存B=000002.SZ及DATE。事务轨迹为A提交、B两组后回滚、失败保存提交、C提交；仅一个冻结任务。S/F/N/H=2/1/0/0，R/I/U=2/2/0 |
| 首明细INSERT失败 | 先前A保留，B业务回滚；新主表和明细均回滚，C未调用。S/F/N/H=1/1/1/0，R/I/U=1/1/0，taskId及remaining=null |
| append后的touch失败 | 第一条限流记录及主表原值完整保留；第二条超时失败已明确，保存未确认，C未调用。S/F/N/H=0/2/1/0，R/I/U=0/0/0，保留确认ID，remaining=null |
| 物理commit后丢失回复 | 独立连接可见第一日业务行，编排仍按unknown处理：S/F=0/0、N=2、R/I/U=0/0/0；第一日仅列unknown，两失败表为空，无后续fetch |
| 确认commit后框架抛错 | 当前S=1、R=1、I=1保留，F=0、N=2、unknown为空，以INTERNAL_ERROR停止，两失败表为空 |
| 空与全休市 | 空响应实际完成3个零行单元，旧业务哨兵完整且无任务；全休市无业务／失败事务。日历、plan、fetch均断言没有实际事务或同步范围，另一线程数据集锁不阻止来源获取 |
| 后续故障保留先前成果（Core） | 第1日保存失败任务，第2日确认R=2/I=1/U=1，第3日分别注入4类停止，第4日未开始。保留原taskId及remaining=1；stopExecution当前成功也累计，存储不可用当前计F，提交未知仅计unknown；精确N和范围不补造成失败 |
| 槽位生命周期（Core） | 分别阻塞fetch、commit、create及append，等待者Future.get超时后原线程仍持有slot；新旧入口和重试lease均忙，释放受控阻塞后完成全部计划。不调用Future.cancel，不从客户端状态释放槽位 |

STOCK测试只在内存复制真实fina_audit定义，保留真实表、列、复合业务键，并与受控API一致地将来源ts_code设为可选、声明已核实独立能力、缩小SQL组大小以触发故障。资源文件和生产策略完全不变；此能力不作为真实Tushare支持证据。

Core还覆盖全部9类允许来源错误、相同来源行与同轮重复的R/I/U区分、时间再股票的事件顺序、全局末行归属屏障、STOCK与整体REQUEST失败共用固定map、全部已知Failure先计F、未来未知股票成员N=null、31／32天与旧字段拒绝、外层事务／同步拒绝、完整计划／Session前置、五类执行及异常finally释放。

## 独立评审

独立生产规格及质量审查未发现阻塞代码缺陷；初始发现两处规定边界的测试证据缺口：R1缺同批独立STOCK的真实MySQL组合证据，补齐后实际运行并范围复审PASS；R2缺“先有失败ID及成功小计、后发生提交停止”的组合，已补测试并通过114项覆盖运行，范围复审PASS。规格／质量最终PASS，无未解决Important／Critical问题。最终独立集成评审PASS：重新解析775／778构建、114 Core、62 App IT及31 helper的XML，核对两次170项前端测试与实际构建、14文件专属增量、R1/R2断言及58份资源／135份生产Java摘要，结果一致；未重复运行Maven或修改源码，无遗留问题。

## 工作区及验收边界

开始前记录分支`feat/date-range-download`、HEAD `758f940503ded2d1185040bc8e324815c716a300`、原暂存／未暂存差异及58份摘要（49 Dataset、2生产策略、7生产迁移）。两次完整构建后58份摘要、分支／HEAD及原Git索引逐项复核一致，差异及暂存差异空白检查退出0；保留上一任务及并行ISSUE-017内容，本项不提交／发布。

本项只确认首次用例、共用槽位及受控MySQL组合。T13精确重试、T14新HTTP响应／迁移、T18真实socket断连、T20真实来源尚未验收；Core等待者测试不冒充网络证据。未执行真实业务API、读取真实凭证或运行浏览器；生产完整来源／日历表为空，ISSUE-008九项仍“不依赖，未解决，用户后续单独处理”。本项机制AC已链接至[需求追踪](../traceability/tensor-range-requirements.md)，不升级29项最终功能AC。
