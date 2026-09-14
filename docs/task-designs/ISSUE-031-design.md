# ISSUE-031：278项真实区间任务与SQL验收

## Goal

消费ISSUE-029的严格证据工具/四项mainbz SOURCE和ISSUE-030已完成的候选/两包/272绑定，按固定十轮取得278项真实TASK的页面、批次、SQL和清洁运行证据。身份为[ISSUE-026子看板](../task-handoffs/ISSUE-026/ISSUE-026-task-board.md)Order5。本设计在ISSUE-030记录COMPLETED之后创建；设计就绪不代表本项已经启动或有新的真实结果。

## Scope

执行[共享设计第5节](ISSUE-026-design.md)的十轮28/16/38/35/37/33/24/42/21/4，共278TASK。消费268旧来源（含4辅助日历）和新4mainbz，增加2次disclosure重下与4接口回归；逐轮登记、验证、追加唯一索引，失败按母合同停止/撤回候选/递增版本。保留40注册、34RANGE/6SINGLE_ONLY、31原生/3逐日、原参数/日期轴/字段/业务键、全部成功数据和历史run。

本项不重新调查或重跑已合格SOURCE，不重跑完整74 SINGLE，不新增分页、type/VIP、自动重试或生产验证开关。只有实际缺陷才最小修改实现并重新冻结受影响身份；最终六门禁和母任务关闭归ISSUE-032，不因278是计划数而提前标通过。

## Approach

### 1. 接收并核对确定输入

按以下顺序消费，不从生产注册反推规则或从同参数PASS中挑第一项：

1. [ISSUE-030验收](../verification/ISSUE-030-range-candidate-policies.md)及[构建登记](../verification/ISSUE-018-T14-runs.md#issue-030-候选构建与离线交付2026-09-15)：冻结隔离副本`/private/tmp/issue030-work-20260914T025010Z`；893文件snapshot `c05f7a306c73cea3e526120b165672878f3aba2bab4052bee96b1dffd68c10db`，422相关源码字节已与工作树核对。
2. 生产包`data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar` SHA `de8130e205f20493a01c566d74390a14483bb1296bcf955df5c2e4f389ff3ac1`；验收包`data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar` SHA `518add550be578917ef064ca626982f4293f2d493acb1f919358af0f34c5ce2a`。路径均相对该隔离副本，sourceDiff/manifest/examples其余准确哈希见同一登记；不能混用ISSUE-029包。
3. `/private/tmp/issue030-control/candidate-inputs.json` SHA `9c2cb2a9daab04c13bf9aeed5ac69a0d403453611c4a3b40b85d8685837c7b3d`和`source-bindings.json` SHA `a4ce0c747643c91745726b87b4ea5ae682152ac2e542a6783bcd0402acbbba02`，各272项；`candidate-summary.json`、`delivery-audit.json`、`frozen-identity.json`提供核对事实。
4. [母issue七组准确输入表](../issues/problems/ISSUE-026-range-task-final-acceptance.md)、[schema2唯一索引](../verification/ISSUE-018-range-acceptance.json)和[ISSUE-029验收](../verification/ISSUE-029-range-evidence-and-split-source.md)。初始索引SHA `a281a8460c4fdf57cf5a5d7ebdf13324f6579b15d61867d937f28d2e566c6490`，26轮826case928请求，正式4 AVAILABLE/30 NEEDS_VERIFICATION/6 SINGLE_ONLY；本地候选34 AVAILABLE仅为入口。

若私有输入丢失，从`control-plane/e2e/issue030-range-candidates.js`导出的`ISSUE030_SOURCE_GROUPS`/`ISSUE030_SOURCE_INPUTS`和`buildIssue030CandidatePlan(index)`重建并复核全部272身份/原参数。只调用纯计划构造及现有校验；不得在已有新TASK后运行`issue030-range-candidates-write.js`或`applyIssue030CandidateIndex`重置正式索引、当前TASK引用和未决项。缺失包则从完整当前源码重新构建、逐文件核对并登记新实际身份，旧结果不能伪装成新包结果。

### 2. 机械固定十轮和278个case

从272项离线计划按其完整SOURCE身份分组，原caseId、params、dateAxis、start/end、规则引用和恰一个完整`JSON#runId/caseId`引用保持。每轮实际启动前固定`runId=issue026-range-<组名>-<UTC>`，写成既有`{runId,cases}`清单；中性`issue026-candidate-inputs`不能作为真实runId。每个新增TASK caseId须与全部历史case及其他轮计划不同；不重命名已承诺ID。

| 顺序 / 组名 | 唯一固定集合 | TASK数 / --list API测试数 |
| --- | --- | ---: |
| 1 / limits | ISSUE-019全部daily/forecast/dividend | 28 / 3 |
| 2 / mainbz | ISSUE-020原12 + ISSUE-029新4 | 16 / 1 |
| 3 / calendar | ISSUE-021全部trade_cal/margin/top_list，含BJ | 38 / 3 |
| 4 / dates | ISSUE-022全部五API + 4辅助trade_cal | 35 / 6 |
| 5 / events | ISSUE-023全部35 + 下述2次disclosure重下 | 37 / 4 |
| 6 / history | ISSUE-024三SLB全部 | 33 / 3 |
| 7 / response-trade | ISSUE-025 adj_factor8、suspend_d16 | 24 / 2 |
| 8 / response-announcement | income/balancesheet/cashflow各8、fina_audit4、express6、stk_managers8 | 42 / 6 |
| 9 / response-holders-repurchase | top10_holders/top10_floatholders各8、repurchase5 | 21 / 3 |
| 10 / regression | 下述四旧AVAILABLE接口各1 | 4 / 4 |

--list按API生成Playwright测试，不是278个Playwright测试；另以`selectTaskCases(...).length`核对每轮TASK数和所有sourceBindings。总278项、完整SOURCE身份272+4回归=276个，另2个TASK重复使用指定disclosure SOURCE。

有效执行顺序沿用harness的manifest API顺序；一API全部在该轮同一schema内，按母输入表股票/交易所首次出现顺序将同股票case归组，第一股票全部完成后处理第二股票。每组先whole，再annual、wide、revision-window/long，最后其余端点/事件/重叠；同类保持原272清单稳定顺序。无这些后缀的官方/特殊样本仍按原顺序保留。两个disclosure重下紧随各自原event，不能被一般排序移到末尾。mainbz同参数的旧whole和新annual都执行，不去重；所有whole/annual/wide在该股票端点重下前。非股票接口按原方式分组，不能擅自加ts_code或合并证券。

新增2次重下精确如下，均ANNOUNCEMENT_DATE/ann_date。原event case及引用从272计划复制，仅更换TASK caseId：

| 新TASK caseId | 原SOURCE完整caseId（runId为caseId前缀至Z） | 参数 |
| --- | --- | --- |
| issue026-disclosure-000001-update-recheck | issue023-boundary-source-20260913T145716Z-disclosure-000001-event | ts_code=000001.SZ，start_date=end_date=20241009 |
| issue026-disclosure-600000-update-recheck | issue023-boundary-source-20260913T145716Z-disclosure-600000-event | ts_code=600000.SH，start_date=end_date=20240813 |

4个回归SOURCE run均为`issue018-t14-priority-source-20260913T092938Z`，完整caseId为runId加`-`和下表后缀。新TASK caseId=`issue026-regression-<完整SOURCE caseId>`，均000001.SZ、TRADE_DATE/trade_date；规则引用从该接口现有正式条目复制，并加入恰一个准确完整SOURCE引用。

| SOURCE后缀 | start_date / end_date | 原v2阈值 |
| --- | --- | ---: |
| daily_basic-cross-year | 20251229 / 20260105 | 6000 |
| stk_limit-range | 20260803 / 20260810 | 5800 |
| moneyflow-range | 20260803 / 20260810 | 6000 |
| margin_detail-range | 20260803 / 20260810 | 6000 |

mainbz新4绑定固定run `issue026-mainbz-split-source-20260914T013736Z`及000001-annual/600000-annual/000001-wide/600000-wide后缀；新TASK ID继续`issue026-task-<完整SOURCE caseId>`。annual精确20250101～20251231，wide精确20200101～20251231，REPORT_PERIOD/end_date、省略type，股票分别000001.SZ/600000.SH。原12不替换，旧4满额SOURCE不能被选中。

### 3. 每轮准备门禁和执行

每轮在`docs/verification/ISSUE-018-T14-runs.md`先登记实际runId/case清单、来源关系、规则、预期观察、请求预算、私有文件摘要和新schema，再执行。私有目录0700、普通自有非symlink文件0600；复制当轮完整索引为只读消费快照，运行期间清单/索引/权限/源码/包不得变化。使用既有`validateEvidence`、`validateCasePlan`、`selectTaskCases`核对初始sourceBindings，harness执行和清理继续使用同一绑定。RESPONSE_ONLY预期由合同生成，不能复制旧SOURCE的UNKNOWN语义。

Java21/Node24、MySQL8.4.6新空`tensor_m14_t05_<hex>` schema，开始0表，应用启动后8迁移/52业务与任务表。核对JDBC和`ISSUE018_T13_MYSQL_DEFAULTS_FILE`指向同一新库、UTF-8和既有最小单库权限，凭据不进日志/命令行；成功数据与旧库保留。本地8080必须空闲，只管理本轮自有JVM。真实账户只用于已固定轮次，Token只传后端环境，上游固定HTTPS。

沿用harness现有环境名：`ISSUE018_T13_PHASE=range`、`ISSUE018_T13_CASES_FILE`、`ISSUE018_T13_EVIDENCE_INDEX_FILE`、`ISSUE018_T13_MYSQL_DEFAULTS_FILE`、`ACCEPTANCE_JAR`、`ISSUE_017_ACCEPTANCE_JAR_SHA256`、`M14_T05_ARTIFACT_DIR`、`M14_T05_CALL_INTERVAL_MS`及现有DB/Token配置。运行前核对源码diff、完整snapshot、两包、manifest/examples及两份输入hash；若只有后续登记文档变化，分开记录，不能缩小源码覆盖掩盖实现漂移。

先--list确认上表精确API集合及TASK总数，再实际执行；list没有TASK/SQL事实。workers=1、retries=0、串行，trace/screenshot/video全off，不使用grep省略计划项。每轮来源间隔至少2000ms、30分钟/5000请求硬上限；预算登记基于固定窗口、已知日历与SOURCE观察，包含日历规划及潜在拆分请求，实际计数必须实测。ROW_LIMIT不因预算而改样本，触限停止，不能把预计请求数填为实际值。

### 4. 每个TASK的闭环与特别场景

harness通过页面选择接口/原参数→202和Location→详情与全部批次→两次records查询→SQL快照与日志核对。逐项检查持久extraction与候选规则/版本一致，全部批次终态、叶子闭区间覆盖、SQL原业务键/股票或交易所归属、其他股票保留、摘要及实际source/insert/update；父SPLIT不适配/写入，最终键数与插入/更新操作分开。调用数包含实际规划/来源，不从旧SOURCE行数推导数据库写入数。每轮fixture自检的2提交/3查询单独记录，不计入278；目标records查询按每TASK两次为556，实际失败/未运行不能补造。

- daily：whole先于overlap，记录重叠实际更新和SQL键数，后股票写入不抹掉先股票。
- fina_mainbz：四新TASK绑定新四SOURCE，验证真实完整分裂树、父不写、成功叶子<100、日期无缺口重叠、原P/D/I及业务键。至少一项真实TASK须观察到SPLIT父及成功子树；本轮未触发时任务真实终态保留，拆分场景仍EVIDENCE_MISSING，不能用受控拆分或旧SOURCE树替代。单日满额失败仍由现有回归约束。
- trade_cal/top_list/margin：含每日休市的完整日历、三个top_list全休市0叶子计划、SH/SZ/BJ证券保留、BJ仅查SSE日历和三exchange_id。直接BSE拒绝已有受控证据，不新增真实负例调用。
- weekly/monthly及其他日期轴：用该轮四辅助日历、实际最后开市日与原输出列核对；公告、报告期、截止日、申购/上市日不互换。
- disclosure_date：原event与指定重下紧邻，核对实际update操作、原键和最终SQL；真实上游未变时如实记录，不制造旧修订内容。受控同键内容替换由已交付runner/MySQL证据支持，新增影响才补验。
- SLB：按固定历史窗口、停业/结算边界与原期限/费率键核对，同库保留早期窗口；历史起止/持续上游保留承诺仍未知，不增日期限制。
- 11 RESPONSE_ONLY：原生父窗一叶子、requestCount=1、rowLimit=null、splittable=false，expectedCoverage=RESPONSE_ONLY，核对持久规则、所有返回行的适配/写入、SQL及页面完整性未确认提示。合法空可真实SUCCEEDED，但本计划除三个top_list休市外要求非空代表性；意外空保留任务真终态、代表性证据记EVIDENCE_MISSING，不反写SOURCE或换日期求通过。

### 5. 结果、失败和逐接口验收

每轮完成后先核对输入/包不变、私有日志安全、自有进程退出和清理、实际退出码，再把安全真实run/case追加唯一JSON，登记实际请求数/状态、SQL、批次与身份。原26轮826case928请求及旧成功/失败/空逐对象保留；未执行保持NOT_RUN，不复制PASS。成功数据库保留，清理自有临时进程/秘密不等于删除成功数据。

任何失败、环境或输入变化立即停止本轮并保留已执行/在途/未运行事实，不自动重试或改参数。按共享设计和T14合同撤回失败接口的本轮候选并递增policyVersion，同步独立能力预期、最小相关测试和新构建身份；不重写原任务快照，不把旧版本结果算作新候选验证。若不能继续，记录准确暂停/阻塞与恢复条件，未合格轮不计入清洁验收。

每接口只有其全部固定任务/代表场景、采用合同、准确SOURCE、匹配版本/包TASK+SQL、清洁身份和相关审查均成立，才清除相应真实缺口并更新正式disposition；不能批量清空unresolved。trade_cal还消费dates轮辅助日历，依本接口全计划完成情况判断。最终形成278逐项结果和逐接口证明，交ISSUE-032进行统一门禁/终审；原四AVAILABLE和74 SINGLE证据保留，原四版本无失败/语义变化则仍v2。

## Files

- `docs/verification/ISSUE-018-T14-runs.md`：十轮先登记后追加实际执行证据、schema/预算/身份、失败或清理事实。
- `docs/verification/ISSUE-018-range-acceptance.json`及`.md`：唯一真实run/case和逐接口验收；原历史保全。
- 新建`docs/verification/ISSUE-031-range-live-task-verification.md`：278项/十轮总表、SQL与代表场景、版本/包身份、真实缺口和审查。
- `docs/issues/problems/ISSUE-031-range-live-task-verification.md`、`docs/issues/README.md`、`docs/task-handoffs/ISSUE-026/ISSUE-026-task-board.md`：实际状态；完成后再准备ISSUE-032专属设计/交接。
- `control-plane/e2e/tushare-range-evidence.test.js`：正式索引的阶段断言随实际TASK证据更新，保留独立规则矩阵、历史保全和所有拒绝测试。
- 私有固定清单、索引快照、sourceBindings、环境核对和实际安全输出置于本次自有0700目录，路径/哈希登记到运行文档。复用`control-plane/e2e/issue030-range-candidates.js`及`tushare-range-evidence.js`、`tushare-live.spec.js`；没有已知缺陷需要预先重写harness。
- 若实际失败要求撤回/修复，最小修改`TushareBatchPolicies.java`、对应策略/HTTP/前端独立预期及受影响实现；记录原因、测试与重新构建身份。

新增仓库文件加入Git，保留前序未提交成果；不自动commit、push、合并或发布。

## Tests

第一动作：只读核对ISSUE-030冻结源码/包/输入；在私有目录从272项准确绑定按第2节扩展固定278项、十轮清单与顺序，使用既有纯校验核对276唯一SOURCE身份、每轮计数、重下紧邻、mainbz旧12/新4和规则引用；再预登记第一轮及独立新schema条件。此动作不能调用真实接口或写业务表。

普通回归移除真实账户和DB变量；无源码变化时复用ISSUE-030已验证的适用结果，新增数据/逻辑或失败才补相应检查。当前证据测试含ISSUE-030阶段的固定4/30/6、仅SOURCE和RANGE_TASK_NOT_RUN断言；新真实TASK使事实变化后，最小调整这些正式索引测试为逐接口核对实际已验证TASK/SQL与剩余缺口，不能继续要求所有30项永远待验证，也不能直接把预期全改为AVAILABLE。保留纯候选重建及“仅SOURCE不能开放”的拒绝用例；先用实际追加结果观察阶段断言RED，再按事实调整，禁止为通过测试重置真实索引。证据追加后用当前唯一索引重跑严格Node校验，负例保持拒绝：

```sh
node --test control-plane/e2e/tushare-range-evidence.test.js
git diff --check
git diff --cached --check
```

期望全部通过、0失败/错误/未解释跳过；当前Node基线104项，新增测试计数按实际记录。每轮准备门禁成立后，在该轮私有环境与冻结隔离副本执行：

```sh
TENSOR_TUSHARE_LIVE_E2E=1 ISSUE018_T13_PHASE=range npm --prefix control-plane run test:e2e -- e2e/tushare-live.spec.js --list
TENSOR_TUSHARE_LIVE_E2E=1 ISSUE018_T13_PHASE=range npm --prefix control-plane run test:e2e -- e2e/tushare-live.spec.js
```

发现应为上表各API集合，实际任务应精确覆盖对应case数。期望278真实合格TASK、全部必要SQL/树/代表场景和十轮清洁退出；未执行或失败不是通过。若改源码，先相关定向测试和完整`mvn -o -f data-plane/pom.xml -Pacceptance verify`，再核对新两包；普通浏览器/Maven及真实轮次均串行。最终六门禁由ISSUE-032汇总，不能把本项局部检查称为全部最终门禁。

## Acceptance

1. 十轮278固定计划逐项有真实合格TASK、两次records、完整批次/SQL/日志和稳定输入/构建身份，计数与最终键数分开；旧SOURCE、74 SINGLE和全部历史/成功数据保持。
2. 四mainbz真实TASK绑定新四SOURCE，至少一项有完整真实SPLIT子树、父无写入，原业务键与单日满额规则保持；两项disclosure重下有实际update/SQL证据，其他日期/重叠/历史/BJ场景成立。
3. 十一项按RESPONSE_ONLY验收且持续说明完整性未确认，意外空的任务终态与代表性缺口分开；所有拟标AVAILABLE的接口都有采用合同、准确SOURCE、匹配候选TASK/SQL和清洁运行，未决事实不批量清除。
4. 输入/身份/历史保全检查、相关回归及独立运行审查通过，向ISSUE-032交付准确汇总。仅完成本项，不关闭ISSUE-026、T13/T14或母issue。

## Risks

账户权限、上游内容与历史保留可能变化；准备就绪不保证真实运行通过。相同参数SOURCE必须保留完整身份，mainbz拆分发生与SQL写入都需实测。旧candidate重置工具不能覆盖未来TASK。若输入或包丢失，按既定合同重建并登记新身份，不能猜失落结果。RESPONSE_ONLY仍可能截断；用户接受的是返回记录采集，不是上游完整保证。
