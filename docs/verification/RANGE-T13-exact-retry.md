# RANGE-T13 原任务精确重试验证

2026-09-09，对应[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的 RANGE-T13，依据[专属设计](../task-designs/RANGE-T13-design.md)及[入口交接](../task-handoffs/tensor-range/RANGE-T13-handoff.md)。定向验证、评审补测及两次完整构建通过，独立规格、质量和最终集成／证据评审均PASS，无未解决缺口。

## 实现范围

- 新增独立 `RetryDownloadService`，唯一七参构造器及 `execute(UUID, RequestId)`。共用槽位内重新读取原任务一次，只处理当前失败项；全部参数、Session、整轮必要日历及业务来源规划通过后才开始执行。
- REQUEST 和 STOCK 均保留原完整选择器；不同日期、同日不同股票分别处理，不按展示区间扩大请求。再次明确失败只更新原完整键的安全原因，保存确认后才继续；不创建新任务、不补造已删除项。
- 业务与精确删除复用现有单元事务。仅确认提交才接纳删除和业务计数；明确回滚、存储不可用、提交未知、原因保存未知及确认后的框架异常分别处理，保留前序确认事实。
- 全闭仅清理原项，不计 S/R/I/U；混合开闭 RANGE 完整请求。H 按实际跳过的不同日期计算，排除保留范围覆盖的日期；N 不计全闭项，停止范围仍披露尚未处理的闭项。
- 剩余项由初始项减去确认删除项计算。最后一项删除未知时 `taskId=null`、`remainingFailedUnits=null`；已有确定兄弟项时保留原 ID。服务不通过事后查询反推本次提交。
- App 只装配一个共享依赖的 bean。旧 DownloadService 表面、Controller、DTO、生产来源／日历能力、迁移和 Dataset 保持。

## 测试先行与修正

1. 先写 margin 原1～10日仅重试3／7及完整RANGE、同日两股精确删除、trade_cal单日相等起止／合法空三组字面测试。缺接口阶段编译缺少 RetryDownloadService；加入七参／两参 throwing skeleton 后，授权环境实际运行3项，均以 `UnsupportedOperationException: Retry execution is not implemented` 报错（0断言失败／3错误）。这是缺执行行为的运行证据，不记作3个断言反例。
2. 沙箱内 Mockito 附加错误单独记录为环境失败；没有修改依赖或关闭断言。最小实现随后3项全部通过。扩展矩阵首次运行20项通过，不声称每个新增场景都单独观察过 RED。
3. App 图测试先于 bean，修正新IT一个导入设置错误后，真实生产图在新服务 bean 数量为0处失败（1 failure／0 error）。增加唯一共享 bean 后，新IT8项及图测试1项通过。
4. 自检补入 C 获取前独立读库确认 B 原因已提交的断言。规定七类回归首次因新Core测试缺两个导入停止，尚未执行App；修正导入后七类全部通过，新增断言已实际执行。测试设置错误不计为业务反例。
5. Core独立评审后补9个测试方法及其矩阵，29项重试服务测试与九类130项回归通过。首次补测因测试内validate重载调用类型不明确而编译失败，修正类型后通过；未修改生产服务，不将该编译错误记作业务反例。

## 实际命令和证据

命令从仓库根执行，Maven使用允许本地测试端口、JVM附加及Colima的授权环境。含MySQL的命令只为测试子进程添加：

```sh
env -u TENSOR_TUSHARE_TOKEN \
  DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
  TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

| 命令 | 本轮结果 |
|---|---|
| `mvn -f data-plane/pom.xml -pl tensor-core -am -Dtest=RetryDownloadServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` | 退出0；20项，零失败／错误／跳过 |
| `mvn -f data-plane/pom.xml -pl tensor-core -am -Dtest=RetryDownloadServiceTest,DownloadServiceTest,DownloadExecutionSlotTest,DownloadExecutionResultTest,DownloadParameterConverterTest,RecoveryUnitProcessorTest,CommittedKeyIndexTest,BatchCommitServiceTest,RetryTaskStorageServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` | 初轮121项；评审补测后的最终130项（Retry29＋既有101）退出0，5.261秒，零失败／错误／跳过 |
| `mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=RetryDownloadServiceIT,InitialDownloadServiceIT,BatchCommitServiceIT,RetryTaskStorageIT,DownloadControllerIT,FixtureFlowIT,ProductionApplicationContextIT -Dsurefire.failIfNoSpecifiedTests=false test` | 退出0；70项，1分29秒，零失败／错误／跳过 |
| `mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=RetryDownloadServiceIT -Dsurefire.failIfNoSpecifiedTests=false test` | App评审补测后退出0；11项，26.297秒，零失败／错误／跳过；仅测试增量 |
| `mvn -f data-plane/pom.xml -pl tensor-app -am -Dtest=DownloadRequestBindingTest,ControllerUseCaseTest -Dsurefire.failIfNoSpecifiedTests=false test` | 退出0；25＋6＝31项，零失败／错误／跳过 |
| `mvn -f data-plane/pom.xml verify` | 退出0；795项Java检查（791单测＋4生产JAR）、170项前端测试及构建，32.707秒 |
| `mvn -f data-plane/pom.xml -Pacceptance clean verify` | 退出0；798项Java检查（791单测＋4生产JAR＋3验收JAR）、170项前端测试及构建，43.423秒 |
| `PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py` | 退出0；8组合同和4个变异反例通过 |

七类显式App最新XML已逐一独立解析。以下73项是原七类70项运行，加上评审后11项Retry定向运行替换旧8项的复合证据，不是一次73项运行。原始70项日志保持，clean前已另存当时XML；更新11项另存于 `app-review-round1/`：

| 测试类 | tests |
|---|---:|
| RetryDownloadServiceIT | 11 |
| InitialDownloadServiceIT | 11 |
| BatchCommitServiceIT | 22 |
| RetryTaskStorageIT | 13 |
| DownloadControllerIT | 10 |
| FixtureFlowIT | 5 |
| ProductionApplicationContextIT | 1 |
| 最新合计（两次运行） | 73 |

每份XML的tests>0，failures/errors/skipped均0。原始日志及XML副本位于 `/tmp/tensor-range-t13/verification/`：`core-final/`保存历史121项，`core-review-round1/`保存最终130项，`app-seven-final/`和`app-helpers-final/`保存App结果；`app-seven-final/summary.json`明确列出两次运行来源。两次完整构建发生在评审补测之前；两份生产文件摘要未变，新增测试由最终Core130项及Retry IT11项定向运行覆盖。默认verify／acceptance的打包合同不代替显式MySQL。

## 直接可观察结果

| 场景 | 本轮事实 |
|---|---|
| 原1～10日、剩余3／7日 | 真实仓储读入，来源仅请求3及7；3成功删除，7超时更新原原因。S/F/N/H=1/1/0/0、R/I/U=1/1/0，MySQL JSON文本及created_at不变，另一同条件任务不变。重建服务图只请求7并删最后主表，再次同ID为NOT_FOUND且零来源调用 |
| 同日股票及SQL分组 | 使用真实fina_audit表及(ts_code,ann_date,end_date)复合键，测试内batchSize=2。B三行跨两组，完整五字段明细DELETE注入45000，使B全部候选回滚，旧B值8保留；A/C分别写入并删除自己的明细。C获取前独立连接确认B的PERSISTENCE_FAILED原因已提交 |
| 明细后主表收尾故障 | 最后主表DELETE失败使业务及已删明细一起回滚，随后仅更新原项原因；非最后touch失败后，独立原因更新的touch再失败，原原因、JSON、主表时间和旧业务保留，后续7日未获取，remaining=null |
| 空与全闭清理 | 合法空经普通事务删原项，S1/R0、EMPTY，旧业务完整保留。完整全闭DATE不调用plan/fetch，S0/H1、NO_OPEN_DATES；闭市DELETE故障回滚并记录原项持久化失败，不从内存假装已清理 |
| 真实commit丢答复 | delegate.commit已写业务并删除3日，代理随后抛SQLState08；独立连接只见7日剩余，但服务保持COMMIT_UNCONFIRMED，S/F0、N1、remaining=null及当前unknown，保留有确定兄弟项的ID。SQL轨迹无事后find/readback；下一服务只执行实际剩余7日 |
| 最后项commit丢答复 | 独立连接证实业务已提交、最后明细和主表均已删除；结果仍COMMIT_UNCONFIRMED、S/F/R/I/U=0、taskId与remaining均null，仅当前unknown。无原因更新／重插／事后find；重建服务同ID为NOT_FOUND且零新来源调用 |
| COMMITTED后框架异常 | 先接纳当前已确认S/R/I和删除，再以INTERNAL_ERROR停止；unknown为空，未处理7日及原ID保留，不能撤销已提交小计 |
| 最后业务／全闭COMMITTED后框架异常 | 两个独立MySQL场景均已删除最后明细及主表，结果INTERNAL_ERROR但taskId=null、remaining=0、unknown为空。业务保持S/R/I=1；全闭保持S/F/R/I/U=0、H=1且旧业务不变。正常全闭成功也独立确认两表均为0 |
| 原因保存丢答复 | 当前来源超时已明确，原因UPDATE实际commit后丢答复；F1、N1、remaining=null，原ID保留且7日未执行。独立读库结果只作测试证据，不回填确认 |
| Core前置与停止 | 全项参数／Session／日历／plan屏障、MONTH/NONE、原生单日、REQUEST增强不拆、混合RANGE、9类来源失败继续、完整返回键检查及前序成功／失败后各停止分支均通过 |
| Core补充组合 | 真实Processor局部Failure、有效前缀后外部股票末行、适配缺字段／类型错误和DATA_CONFLICT均保留整个原key，更新确认后才开始下一项；同轮重复R保留且第二项无候选写入；同一原任务下一轮允许旧键新值Upsert。末Session／plan异常及未确认建议均零执行副作用；业务／全闭T11矩阵保留前序非零I/U，N排除未来全闭项但scope披露，最后全闭清理区分确认删除／明确失败／未知 |
| 共用槽位与生产边界 | find、fetch、commitRetry、commitClosedRetry、updateReason各阻塞时仍持有槽位，等待者超时不cancel，真实退出才释放。生产bean唯一且七个依赖共享；生产日历未确认时原任务／明细保留 |

## 独立评审

- App首审发现最后项提交丢回复和最后业务／全闭COMMITTED后框架故障的集成证据不足。补入三个真实MySQL测试及正常全闭的独立两表读回后，11项定向通过；首轮范围复审将P1、P2均判为ADDRESSED，规格和质量PASS。生产装配无需修正。
- Core首审未确认生产行为缺陷，要求补充真实Processor的局部／适配／冲突失败、重复与跨次更新、后项前置拒绝及闭市提交矩阵；补9项后130项回归通过，两个P1及报告P2范围复审均ADDRESSED，规格／质量PASS。其后仅把普通Committed(false)补入既有业务／闭市矩阵，再次130项通过；最终独立评审已核对该最后增量，确认计数和继续门槛符合设计。
- 最终独立整体验收评审逐项核对Core与App连接、精确选择器／槽位／事务／结果边界、7份评审文件摘要，以及Core130、App复合73／Retry11、helper31、verify795、acceptance798的XML及实际前端170项和构建日志；规格、质量、集成／证据全部PASS，无遗留。评审未重跑Maven或修改Git；报告保存在本轮内部评审档案中。

## 工作区与验收边界

开始前保存 `feat/date-range-download`、HEAD `758f940503ded2d1185040bc8e324815c716a300`、原暂存及58份资源摘要（49 Dataset、2策略、7生产迁移）；保留既有任务和并行ISSUE-017内容，不提交／发布。

父流程已独立核对58份摘要、分支／HEAD、任务外原索引及两种Git空白检查，全部通过；App评审前后两份生产文件摘要一致。报告不把仅新增测试后的定向运行冒称为重新执行了完整构建。

本项只确认Core精确重试及App共享装配与受控MySQL机制。HTTP参数迁移、真实404／409和错误快照接入由T14完成；真实进程中断／socket断连由T18验证；生产来源与日历证据由T20处理。服务重建和等待者超时不冒称进程kill或真实断连。未调用真实来源、读取真实凭证或运行浏览器，生产空能力边界保持，ISSUE-008九项仍“不依赖，未解决，用户后续单独处理”。
