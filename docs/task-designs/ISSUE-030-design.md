# ISSUE-030：剩余接口候选策略与日历映射

## Goal

消费ISSUE-027～029已交付的后端、HTTP/页面与严格证据工具，将剩余30接口配置为本地真实任务验收可用的候选，交付确定的来源映射和稳定源码/两包。身份为[子看板](../task-handoffs/ISSUE-026/ISSUE-026-task-board.md)Order4；本设计在ISSUE-029完成后准备，不代表候选已经实现。

## Scope

按[共享设计第1/2/4节](ISSUE-026-design.md)配置11个RESPONSE_ONLY、18个ROW_LIMIT和1个CALENDAR_COVERAGE候选，加入top_list的BJ→SSE日历参照，同步独立HTTP/页面预期及正式证据候选引用。保留现有四个AVAILABLE/v2策略、40注册/34RANGE/6SINGLE_ONLY、31原生/3逐日结构及参数/字段/原键。

本项不执行真实SOURCE、RANGE TASK或SQL取证，不新增run/case，不把SOURCE事实包装成TASK。正式索引30项仍NEEDS_VERIFICATION；本地候选能力AVAILABLE是后继验收入口，最终真实验收由ISSUE-031/032完成。不新增分页、type、VIP、自动重试、生产验证开关、数据迁移或发布。

## Approach

### 1. 固定独立候选矩阵

以下是新30项的唯一规则表。股票范围沿用原STOCK，margin保留EXCHANGE_ID，trade_cal保留EXCHANGE，slb_len/new_share/repurchase保留DATES；不从生产注册生成测试预期。

| API | RuleKind / rowLimit | planningMode | dateAxis / 输出列 | 直接采用依据 |
| --- | --- | --- | --- | --- |
| daily | ROW_LIMIT / 6000 | NATIVE_RANGE | TRADE_DATE / trade_date | ISSUE-019决定 |
| forecast | ROW_LIMIT / 3500 | NATIVE_RANGE | ANNOUNCEMENT_DATE / ann_date | ISSUE-019决定 |
| dividend | ROW_LIMIT / 2000 | CALENDAR_DAYS | ANNOUNCEMENT_DATE / ann_date | ISSUE-019决定 |
| fina_mainbz | ROW_LIMIT / 100 | NATIVE_RANGE | REPORT_PERIOD / end_date | ISSUE-020决定及ISSUE-029新树 |
| trade_cal | CALENDAR_COVERAGE / null | NATIVE_RANGE | CALENDAR_DATE / cal_date | ISSUE-021完整日历 |
| margin | ROW_LIMIT / 4000 | NATIVE_RANGE | TRADE_DATE / trade_date | ISSUE-021 |
| top_list | ROW_LIMIT / 10000 | TRADING_DAYS | TRADE_DATE / trade_date | ISSUE-021 BJ参照 |
| weekly | ROW_LIMIT / 6000 | NATIVE_RANGE | TRADE_DATE / trade_date | ISSUE-022实际周末交易日 |
| monthly | ROW_LIMIT / 4500 | NATIVE_RANGE | TRADE_DATE / trade_date | ISSUE-022实际月末交易日 |
| fina_indicator | ROW_LIMIT / 100 | NATIVE_RANGE | REPORT_PERIOD / end_date | ISSUE-022 |
| stk_holdernumber | ROW_LIMIT / 3000 | NATIVE_RANGE | ANNOUNCEMENT_DATE / ann_date | ISSUE-022 |
| new_share | ROW_LIMIT / 2000 | NATIVE_RANGE | ISSUE_DATE / ipo_date | ISSUE-022 |
| block_trade | ROW_LIMIT / 1000 | NATIVE_RANGE | TRADE_DATE / trade_date | ISSUE-023 |
| disclosure_date | ROW_LIMIT / 6000 | CALENDAR_DAYS | ANNOUNCEMENT_DATE / ann_date | ISSUE-023最新披露公告日 |
| stk_holdertrade | ROW_LIMIT / 3000 | NATIVE_RANGE | ANNOUNCEMENT_DATE / ann_date | ISSUE-023 |
| pledge_detail | ROW_LIMIT / 1000 | NATIVE_RANGE | ANNOUNCEMENT_DATE / ann_date | ISSUE-023及已定代表股票 |
| slb_len、slb_sec、slb_sec_detail | ROW_LIMIT / 各5000 | NATIVE_RANGE | TRADE_DATE / trade_date | ISSUE-024历史查询决定 |
| adj_factor、suspend_d | RESPONSE_ONLY / null | NATIVE_RANGE | TRADE_DATE / trade_date | ISSUE-025决定 |
| income、balancesheet、cashflow、fina_audit、express、repurchase、stk_managers | RESPONSE_ONLY / null | NATIVE_RANGE | ANNOUNCEMENT_DATE / ann_date | ISSUE-025决定 |
| top10_holders、top10_floatholders | RESPONSE_ONLY / null | NATIVE_RANGE | REPORT_PERIOD / end_date | ISSUE-025决定 |

四个原策略daily_basic6000、stk_limit5800、moneyflow6000、margin_detail6000仍为原规则及v2，原SOURCE引用/核对日期保持；六个SINGLE_ONLY没有RANGE候选。RESPONSE_ONLY不做数值阈值、不可拆、计划始终为完整父窗一叶；trade_cal不可拆且逐自然日完整覆盖；其余原生ROW_LIMIT可拆，两个自然日和一个交易日规划保持不可拆。

### 2. 最小生产配置与BJ参照

修改`TushareBatchPolicies.createPolicies/add`：保留当前34项结构声明和已有四项sourceCases，增加显式列出上述30接口的候选依据映射；只有映射中具备已采用规则及有效SOURCE的接口才成为本次sourceVerified。将十一项明确指定为RESPONSE_ONLY，不再从null limit推导其为UNKNOWN；其他null仅trade_cal。禁止把所有entry统一设true或用API数量替代逐项证据。

每个新候选固定`policyVersion=tushare-range-v2`，`documentationCheckedOn`记录实施时实际复核日期，`verificationEvidence`包含相应已批准决定（适用时）、`docs/verification/ISSUE-018-range-acceptance.md#<apiName>`、官方URL及有效完整SOURCE run/case。将已确认的工程阈值、历史查询和响应采集说明写入documentationNote，不继续称其为“尚待决定”，也不称上游已保证完整。复用现有descriptor/assessment和持久summary实现，不改变公共枚举/HTTP schema或快照格式。

`exchangeForStock`增加`.BJ → SSE`，只作用于top_list规划日历：向trade_cal请求exchange=SSE，后续top_list仍传原920008.BJ等BJ证券及各开市日trade_date。`requireLocalConditions`中trade_cal `exchange=BSE`仍在来源调用前抛BATCH_DOWNLOAD_UNAVAILABLE；不得把直接BSE请求重写成SSE。margin原exchange_id三个值完全保留。

先在策略/下载/HTTP独立测试中写出上述矩阵和负例观察RED，再最小实现。UNKNOWN、错误评估匹配、Envelope、范围/股票、预算和原子入库拒绝仍适用，不因允许响应不完整而豁免。

### 3. 机械建立精确来源映射

以[母issue七组已交付输入表](../issues/problems/ISSUE-026-range-task-final-acceptance.md)为准确ID清单，以[唯一索引](../verification/ISSUE-018-range-acceptance.json)读取对应run/case及原params/dateAxis；不是扫描同参数取第一个PASS。逐组旧输入数为28/12/38/35/35/33/87，共268（含4辅助日历），另加下表四新mainbz为272项SOURCE映射；两项disclosure重下及四接口回归的TASK扩展仍按共享设计第5节，最终278TASK由ISSUE-031执行。本项可离线生成这些固定输入，不登记成已发生的TASK。

新mainbz runId统一为`issue026-mainbz-split-source-20260914T013736Z`：

| caseId为完整runId加以下后缀 | ts_code | start_date | end_date | 已观察请求 / 来源行 |
| --- | --- | --- | --- | --- |
| -000001-annual | 000001.SZ | 20250101 | 20251231 | 1 / 74 |
| -600000-annual | 600000.SH | 20250101 | 20251231 | 3 / 110 |
| -000001-wide | 000001.SZ | 20200101 | 20251231 | 15 / 458 |
| -600000-wide | 600000.SH | 20200101 | 20251231 | 23 / 657 |

四项均REPORT_PERIOD/end_date、省略type，未来TASK caseId=`issue026-task-<完整SOURCE caseId>`。原12项仍用母输入表指定旧SOURCE，不能用新annual覆盖旧whole，或反过来用旧74行whole替代新annual。来源数不是预计写入数，未来任务必须独立核对SQL。

按下列顺序更新正式schema2的interface对象，所有26轮826case/928历史请求逐对象不动：

1. 30项填入本节已采用的completeness、相应evidenceRefs/decisionRef及候选v2。十一项decisionRef必须精确为`docs/issues/proposals/ISSUE-025-extraction-contracts.md#决策记录`；evidenceRefs含该决定、逐接口报告和原官方URL。其余采用决定和引用从已有条目/交付文档保留，不重新猜测。
2. 当前cases只选交付表对应清洁SOURCE及适用的旧清洁SINGLE TASK；mainbz加入新四项、保留指定旧12。旧空、失败、满额及BSE直接负例移出当前候选引用，但继续完整留在原runs。trade_cal辅助日历引用按精确身份去重。sourceStatus/taskStatus根据所选事实重算。
3. 30项disposition保持NEEDS_VERIFICATION，保留RANGE_TASK_NOT_RUN等仍真实存在的缺口；仅清除已由本次代码/采用依据解决的旧合同缺口。四项原AVAILABLE及其引用保持，不添加伪造TASK或清空真实未完成项。
4. 新私有计划的每个case.evidenceRefs包含当前接口的全部规则依据和恰一个`docs/verification/ISSUE-018-range-acceptance.json#<runId>/<caseId>`；用`validateEvidence/validateCasePlan/selectTaskCases`逐项核对并保存`sourceBindings`。对两同参数mainbz身份、错误run、旧四满额、未引用SOURCE及缺完整身份的新响应采集计划继续拒绝；初始/运行期共享同一绑定，RESPONSE_ONLY TASK预期由合同生成而非复制旧SOURCE UNKNOWN。

### 4. 独立能力预期与候选包

在`TushareBatchPoliciesTest`、`TushareBatchDownloadTest`、`TushareBatchAvailabilityTest`以明确30+4矩阵断言候选能力、policyVersion、规则、阈值/可拆及参数形状。`control-plane/e2e/ui-redesign.fixtures.js`的RANGE_EXPECTATIONS和rangeCapability同步独立预期，十一项splittable=false；`tushare-metadata.spec.js`按ROW_LIMIT/VERIFIED_RULE/RESPONSE_ONLY分别检查实际HTTP，不能统一期待CONFIRMED_ROW_LIMIT。受影响的受控任务fixture从该明确矩阵取得正确保存摘要，历史严格/UNKNOWN fixture继续明确保留历史值。

页面核对响应采集表单、列表/详情的完整性未确认提示及非空/空说明；旧严格和SINGLE语义不变。没有新的UI功能、确认弹窗或用户步骤。

相关测试、来源绑定和独立审查通过后，将全部当前未提交源码复制隔离checkout并逐文件核对，执行完整acceptance构建，记录实际源码diff/snapshot、生产/验收JAR及manifest/examples SHA-256。新候选会改变生产源码，必须产生新的构建身份，不能复用ISSUE-029两包冒充候选。私有输入0700/0600、普通测试不带真实Tushare账户或业务DB；受控HTTP/浏览器使用已有隔离测试环境。记录准备事实供ISSUE-031固定新schema与十轮执行，本项不调用真实Probe或提交真实RANGE任务。

## Files

- `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchPolicies.java`：30项显式候选依据/规则/版本及BJ→SSE参照。
- 同插件`src/test/java/.../batch/TushareBatchPoliciesTest.java`、`TushareBatchDownloadTest.java`、`TushareTradeCalendarTest.java`；`data-plane/tensor-app/src/test/java/com/akkc/tensor/web/TushareBatchAvailabilityTest.java`：独立矩阵、响应采集/阈值/日历/HTTP与零调用拒绝。
- `control-plane/e2e/ui-redesign.fixtures.js`、`tushare-metadata.spec.js`及受影响的`ui-redesign.spec.js`/`download-tasks.spec.js`：独立能力/持久任务/页面预期；证据test补实际新引用与不可仅凭SOURCE开放断言。
- `docs/verification/ISSUE-018-range-acceptance.json`及`.md`、`ISSUE-018-T14-runs.md`：仅当前候选引用/采用规则及实际候选构建登记，不新增run/case。
- 本issue问题文档、权威子看板及`docs/verification/ISSUE-030-range-candidate-policies.md`：实际验收和后继交付；新增文件加入Git，不自动commit/push。

## Tests

第一动作：在`TushareBatchPoliciesTest`和`TushareBatchAvailabilityTest`补上本设计30项独立能力预期（含十一项不可拆RESPONSE_ONLY和新v2），运行观察RED，再实现候选配置。测试全部使用Java21/Node24白名单环境；期望0失败/错误/未解释跳过，实际数量在实施时记录，不把本设计命令当作结果。

```sh
mvn -o -f data-plane/pom.xml -Dtest=TushareBatchPoliciesTest,TushareBatchDownloadTest,TushareTradeCalendarTest,TushareBatchAvailabilityTest -Dsurefire.failIfNoSpecifiedTests=false test
node --test control-plane/e2e/tushare-range-evidence.test.js
npm --prefix control-plane test
mvn -o -f data-plane/pom.xml -Pacceptance verify
npm --prefix control-plane run test:e2e -- e2e/ui-redesign.spec.js e2e/download-tasks.spec.js
npm --prefix control-plane run test:e2e -- e2e/tushare-metadata.spec.js
git diff --check
git diff --cached --check
```

最后两类E2E沿用各自受控fixture/packaged-test-environment入口，预先配置其要求的本地隔离测试库、包路径/摘要和临时监听；不注入真实Tushare凭据或选择tushare-live。按实际影响复用ISSUE-027/028 runner/HTTP验证；出现影响才补对应测试，不把完整账户回归当作本项前置。

必测边界：精确40/34/6及31/3/6矩阵；11项计划一叶、合法空/非空和结构/日期/股票错误；18限量等号及最小窗口满额失败；trade_cal每日含休市无缺漏重复；BJ先查SSE完整日历而后保留原股票；直接BSE零调用拒绝、margin exchange_id三值；原四v2不变；272来源映射、mainbz旧12/新4及响应任务完整身份；候选有SOURCE而无RANGE TASK/SQL仍不能将正式disposition改AVAILABLE；全部26轮826case及原失败/空原样保留。

## Acceptance

1. 30候选逐项匹配上述规则/参数/日期/原键及已交付来源，UNKNOWN未被猜测阈值替代，十一项以RESPONSE_ONLY呈现；四旧v2及六SINGLE_ONLY保持。
2. BJ参照、直接BSE/其他非法调用、阈值等号/最小满额、响应采集单请求/页面/持久摘要及独立能力预期均通过；原结构和失败处理不变。
3. 新候选完整源码、两包和输入具有实际可复核身份，局部/必要集成与隔离构建、独立审查通过，精确来源映射可交付ISSUE-031。
4. 正式索引30项仍NEEDS_VERIFICATION、所有26轮826case不改写；没有新真实SOURCE/TASK/SQL或提前声称最终开放。仅完成本项并准备后继，不提前关闭母任务。

## Risks

候选AVAILABLE和正式验收AVAILABLE不同，须在文档与索引中明确。多同参数来源必须保留完整身份；旧四mainbz满额、BSE负例和历史空不能借候选整理重标。上游仍可能截断/修订或后继任务意外为空，SOURCE通过与本地候选不能预先证明278TASK/SQL。真实任务失败撤回及版本递增归ISSUE-031；本项没有未决定的提取合同。
