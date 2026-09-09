# RANGE-T11 恢复单元事务验证

本记录对应[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的 `RANGE-T11`，依据为[专属设计](../task-designs/RANGE-T11-design.md)及[入口交接](../task-handoffs/tensor-range/RANGE-T11-handoff.md)。执行日期为2026-09-09。本轮验证单元事务机制；首次／重试编排、HTTP、浏览器和真实来源仍归后续任务。

## 实现及输入边界

- 新增一个 `BatchCommitService`，使用 REQUIRED、60秒、每单元独立事务。首次成功不写任务；重试按任务主表锁→精确项存在→既有 PersistenceService→精确删除→剩余项检查→更新时间或删除空主表执行，所有 SQL 共用同一 manager 和物理连接。
- 提取 `CommittedKeyIndex.checkConfirmable` 为包内只读预检查；确认方法保持原检查及原子更新。T11从不确认索引或累计本轮结果；消费者只在 Committed 后串行确认票据并累计。
- App只增加一个服务bean，复用既有持久化、仓储、参数校验器、事务管理器和Clock。没有修改 PersistenceService、数据集锁、Upsert、失败仓储、迁移、Dataset、策略或运行下载入口。
- 完成观察器区分确认提交、提交阶段之前的确认回滚、回调未进入时的启动不可用及结果未知。只有确认提交携带R／I／U；确认提交后框架再抛错保留计数并设置stopExecution。beforeCommit之后即使收到回滚通知也保守归未知。
- 全休市入口消费已经完整确认的整轮日历，保留全部必要市场身份、投影本项完整日期，并在任务锁内绑定冻结公共条件及T05精确重建。只删除已有精确失败项；普通合法空与同轮重复仍通过真实Processor票据进入普通事务。

已按入口核对TRD事务／日历屏障、T09票据与摘要所有权、T10独立保存与仓储参与边界以及实际持久化集成测试。生产日历和完整来源注册表仍为空，49项恢复策略仍为REQUEST；受控STOCK测试不增加生产来源能力。

## 测试先行及修正

1. 首先写入“B跨两组写入后精确明细DELETE失败，A和旧业务保留”的真实MySQL用例，执行规定首条命令。5处缺少BatchCommitService类型的编译诊断只作缺接口证据，不冒充运行回滚反例。
2. Core测试先于实现创建。最小骨架的首次运行有1个实际索引断言失败及7个Mockito沙箱初始化错误；沿用授权测试环境后，16项中8个断言失败、0运行错误，证明测试可捕获尚未实现行为。未修改Mockito或POM。
3. 实现后修正TensorException为抽象类型的编译问题。首轮MySQL16项中13项通过，另有1个断言失败、2个测试设置错误；后者分别修正JDBC URL分隔符及日期描述符投影。
4. 提交故障用例发现：代理在delegate.commit之前抛错后，Spring恢复setAutoCommit(true)仍可让MySQL提交待定事务。最终代理记录该调用顺序，独立连接验证业务与删除已提交，服务仍返回Unconfirmed。此处修正测试的错误预期，没有让生产服务按事后表值猜结果。
5. 股票DATE／RANGE用例改用实际注册的fina_audit定义和适配器；daily实际没有ts_code来源参数，不修改其定义来伪造股票能力。最终测试另覆盖SQLState08、日历预检零SQL、空单元不取数据集锁及App实际工厂装配。

## 实际命令与结果

命令从仓库根运行，测试进程统一使用下列环境前缀，未读取真实凭证、改变全局环境或调用真实来源：

```sh
env -u TENSOR_TUSHARE_TOKEN \
  DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
  TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

Maven在授权沙箱外环境运行，以支持Mockito附加和Colima。MySQL固定为隔离Testcontainers的 `mysql:8.4.6`，App使用实际Flyway迁移（含V8）及实际注册的daily／fina_audit业务表、键和适配器；daily定义副本仅将SQL分组大小设为2。没有清理用户业务数据库。

| 命令 | 本轮结果 |
|---|---|
| `mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=BatchCommitServiceIT -Dsurefire.failIfNoSpecifiedTests=false test` | 退出0；22项，零失败／错误／跳过；31.598秒（评审修正后重跑） |
| `mvn -f data-plane/pom.xml -pl tensor-core -am -Dtest=BatchCommitServiceTest,CommittedKeyIndexTest,RecoveryUnitProcessorTest,RetryTaskStorageServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` | 退出0；9＋9＋41＋11＝70项，零失败／错误／跳过；2.064秒 |
| `mvn -f data-plane/pom.xml -pl tensor-core -am -Dtest=PersistenceServiceIT,ExistingKeyRepositoryIT -Dsurefire.failIfNoSpecifiedTests=false test` | 退出0；8＋8＝16项，零失败／错误／跳过；19.375秒 |
| `mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=BatchCommitServiceIT,RetryTaskStorageIT,FlywaySchemaContractIT,ProductionApplicationContextIT -Dsurefire.failIfNoSpecifiedTests=false test` | 退出0；22＋13＋53＋1＝89项，零失败／错误／跳过；62秒 |
| `mvn -f data-plane/pom.xml verify` | 退出0；751项Java单测＋4项生产JAR合同＝755项，170项前端测试及构建通过；33.511秒 |
| `mvn -f data-plane/pom.xml -Pacceptance clean verify` | 退出0；751项Java单测＋4项生产JAR＋3项acceptance合同＝758项，170项前端测试及构建通过；39.026秒 |
| `PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py` | 退出0；8组合同及4个内存变异反例通过 |

六份MySQL XML在acceptance clean前复制并逐一读取；R1测试清理修正后再次执行完整22项并刷新T11的XML，其余五份保留。最终tests>0且failures／errors／skipped均为0。真实类名与数量为：

| 测试类 | 数量 |
|---|---:|
| com.akkc.tensor.db.BatchCommitServiceIT | 22 |
| com.akkc.tensor.core.persistence.PersistenceServiceIT | 8 |
| com.akkc.tensor.core.persistence.ExistingKeyRepositoryIT | 8 |
| com.akkc.tensor.db.RetryTaskStorageIT | 13 |
| com.akkc.tensor.db.FlywaySchemaContractIT | 53 |
| com.akkc.tensor.observability.ProductionApplicationContextIT | 1 |
| 合计（重复运行同类不重复计数） | 105 |

原始日志与XML副本位于 `.superpowers/sdd/RANGE-T11-design/evidence/`。Surefire默认不调度数据库IT，故上面显式执行的105项不能用verify或acceptance的打包结果替代。

## 可观察行为

| 设计场景 | 实际证据 |
|---|---|
| 分组／删除／收尾回滚 | DELETE、最后业务SQL组、touch和最后主表DELETE分别注入45000；独立连接核对B所有组插入消失、旧行更新恢复、明细与主表原值保留，A完整保留。主表touch真实返回0仍允许提交 |
| 完整键及计数 | 同日两股、3／7日、完整REQUEST RANGE、完整STOCK RANGE和另一taskId分别隔离；最后一项才移除主表。来源重复R=3、实际I=1／U=1；新执行可更新历史同键，不按affectedRows推算I／U |
| 空及同轮重复 | 真实Processor分别产生空Ready与已确认同内容的重复Ready，R=0／R=1而I=U=0；首次不写失败表、重试删除原项，全部旧业务列不变。另一线程占用数据集锁时空重试仍能完成 |
| 全休市与整轮屏障 | 两市场逐日false才清理本项；另一项open不影响当前完整全闭项。缺日期、任一必要市场open、错公共条件／原始日期／exchange、错模式、NONE／MONTH和不完整RANGE均保留原项。前项闭市而后项缺日历时，驱动T11调用为零、原两项都保留 |
| 同连接及锁顺序 | 实际App工厂和依赖身份断言；任务FOR UPDATE、业务键查询、两组Upsert、完整键DELETE、主表收尾记录在同一物理连接，首次与重试各一次物理commit。双线程有界latch／future证明先锁任务再等待数据集，persist返回后至清理及物理提交／回滚结束前数据集锁不释放，其他任务／数据集独立 |
| 票据及事务入口 | 外层SUPPORTS同步／实际事务、已确认／跨线程票据在事务前拒绝；预检不修改索引。NOT_FOUND／错数据集／错选择器不执行业务SQL，不补建明细。来源数据与日历测试驱动在实际事务和同步均inactive时运行 |
| 提交证据 | 真实commit之后代理丢答复、commit之前代理抛错、启用rollbackOnCommitFailure后回滚成功均得到Unconfirmed；不发布R／I／U、不确认索引、不更新或重建失败项。rollback代理失败同样未知。测试清理连接后的读库结果不用于生产推断 |
| 完成阶段异常 | manager在COMMITTED后抛错仍返回正确计数与stopExecution=true；消费者先确认该单元再停止。单测另覆盖afterCommit抛错与正常返回却缺少完成通知 |
| 明确回滚及存储不可用 | 45000且回滚完成得到RolledBack(false)，独立updateReason确认返回SavedFailure后才处理C；保存失败则C调用为零。SQLState08且回滚明确得到RolledBack(true)，启动连接失败为Unavailable，均停止且不保存原因 |
| 安全与异常结构 | cause／nextException循环按对象身份去重；连接异常族与SQLState08判定，不匹配message。原异常哨兵／SQL不进入公开结果、固定安全异常或失败原因。Error不吞入正常结果 |

消费驱动用于证明T11结果的规定使用顺序，没有实现T12／T13。测试事务代理仅在测试代码中；rollback故障代理对隔离连接作清理后向Spring抛错，服务观察结果仍未知，不能用该测试清理事实声称生产回滚已经确认。

## 评审及工作区核对

独立首审确认生产规格PASS及18组场景覆盖，发现R1：两个无业务锁测试在executor关闭之后才释放主线程持有的数据集锁，回归时可能死锁。已将unlock的finally移到资源块内部，使其先于close执行；保留5秒超时和原行为断言，22项MySQL重新通过。R1范围复审确认ADDRESSED、无新增问题；规格PASS、质量通过、无遗留发现。最终独立集成／证据评审亦PASS，无Critical／Important／Minor或延期问题。看板据此记录T11完成，随后准备T12设计与交接。

两次完整构建发生在R1测试清理调整之前；生产代码和其余测试与构建验证版本相同，唯一后续改动由完整22项MySQL复测覆盖。没有执行故意引发死锁的变异测试，清理顺序由代码复审核对。预先记录的58份资源为7份生产迁移、49份Dataset和2份生产策略，最终摘要与基线相同；分支仍为feat/date-range-download、HEAD仍为758f940503ded2d1185040bc8e324815c716a300。Git差异与暂存差异空白检查退出0，原暂存未撤销。并行ISSUE-017-demo及其他issue成果由其他工作维护，本项保留。

Maven已有编码／Mockito动态agent警告，以及故障注入时Spring的回滚覆盖诊断均保留；没有通过关闭断言、修改日志配置或跳过容器测试消除输出。新服务不记录原始异常或来源内容。

本项仅提供 AC-PRD-RANGE-07／13／15／21／24／26 的单元事务证据，见[增量追踪](../traceability/tensor-range-requirements.md#range-t11-单元事务层增量证据)。生产来源与日历支持、整轮计数／槽位、HTTP和浏览器验收仍待后续任务；ISSUE-008九项保持“不依赖，未解决”，本轮未执行其真实调用。
