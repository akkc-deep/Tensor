# ISSUE-031：真实区间任务与SQL验收（原278项，当前251项）

## 2026-09-15四接口排除决定

用户已明确本次不支持balancesheet、cashflow、repurchase、fina_indicator的RANGE批量下载，且不开始ISSUE-032，详见[范围决定](../issues/proposals/ISSUE-026-range-scope.md)。本设计的当前验收目标调整为其余30个RANGE接口的251项，原27项为用户明确排除，保持4 FAILED/23 NOT_RUN及全部历史。下文278/272项、仅财务指标递延及三接口阻塞均为旧阶段合同；本节限定替代这些范围要求。

Acceptance 1改为251项固定任务逐项有原参数/准确SOURCE、实际页面/批次/SQL、清洁运行及各自包身份；Acceptance 2的mainbz、disclosure、日期/重叠/历史/BJ要求保持；Acceptance 3的本次RESPONSE_ONLY集合为原十一项减去balancesheet/cashflow/repurchase的八项，四排除接口保留问题；Acceptance 4核对历史保全、相关回归及既有独立运行审查后仅收尾本项。按用户要求不准备或启动032、不执行其六门禁、不关闭母任务。范围记录、JSON处置及阶段断言更新属于本项收尾，不新增真实请求或生产改动。


## 2026-09-15用户批准的续办范围

用户明确“财务指标先跳过，记录一个issue，再处理其他数据源”。此项新指令优先于下文原278项全量执行要求：fina_indicator的6项固定TASK移交[ISSUE-033](../issues/problems/ISSUE-033-fina-indicator-adaptation.md)，保留1 FAILED/5 NOT_RUN及v3撤回，当前不执行额外SOURCE诊断。ISSUE-031当前验收目标为其余33接口的272项（已通过82，待执行190）；交付时分别报告272项结果与6项递延，不把递延记为PASS或缩减母任务原完整关闭条件。

- 当前dates续轮排除fina_indicator，剩29项/5 API；因这29个原TASK caseId已登记NOT_RUN，新ID固定为`issue031-resume-<原完整TASK caseId>`，参数/日期轴/准确SOURCE及相对顺序不变，新runId/新空schema。旧dates失败轮35项完整保留。原未登记的后六轮161项保持旧ID。
- 后续仍按dates29/events37/history33/response-trade24/response-announcement42/response-holders-repurchase21/regression4依次执行。新计划和29条映射冻结在`/private/tmp/issue031-resume-control/`，每轮仍先登记、精确绑定、检查身份、5000请求/30分钟、间隔至少2000ms、零自动重试。
- 使用已完整验证的撤回构建`/private/tmp/issue031-withdrawal-20260914T182807Z`：897文件snapshot `632a8308f20b33908542f2cb65e821a0a3037a3abba78194ae95dbda54e064cf`；生产JAR `ff30109702e240d34782618f62ab9efed4bdd977e8a691f322d05f6806838bb5`，验收JAR `6522cbbd6d02decf91136bc47766de59ec55e369b84fd3e9d00390bd47e5d7f1`。422相关源码/合同与当前已提交f563bd9的工作字节一致；旧四轮仍归原v2构建，新结果单独登记。
- 正式接口的当前cases引用仅在新对应TASK实际通过、SQL/清理/审查合格后，移除被替代的旧NOT_RUN引用并加入新结果；原run/case对象始终保留。该映射不适用于原FAILED的fina_indicator，不改变其NEEDS_VERIFICATION/v3。
- 新失败仍立即停止本轮、保全及按合同撤回；用户本次仅指定跳过fina_indicator，不能推定其他失败接口也获准跳过。ISSUE-033缺口仍阻止声称全部34 RANGE和母任务完整关闭。

## 2026-09-15验收脚本文案修正与续验

response-trade原轮 `issue031-range-response-trade-20260914T193118Z` 在首项adj_factor后停止：后端TASK/批次SUCCEEDED，1请求、source4/insert4/update0；harness在通用成功文案断言处失败，原验收case为EVIDENCE_MISSING，另23项NOT_RUN。补充只读SQL证实4个原键，未补造缺少的AFTER records/连续SQL验收。原run、原schema和冻结包保留。

已定位为harness硬编码“已成功”与既有RESPONSE_ONLY页面“返回记录已采集/本次请求未返回记录”冲突；这是验收工具缺陷，没有后端候选失败证据，故不撤回adj_factor或递增业务策略版本。只修正该断言，保留完整性提示和全部后续检查；先以实际submitDownload函数的非空/空RESPONSE_ONLY、严格RANGE和SINGLE回归观察RED→GREEN，再完整离线构建并冻结新源码/两包。受影响UI合同由已有受控浏览器再核对，无新增财务指标诊断。

新控制目录 `/private/tmp/issue031-harnessfix-control/` 承接尚未合格的91项。response-trade24固定新TASK ID为 `issue031-statusfix-<原完整TASK caseId>`，原参数/日期轴/完整SOURCE及顺序保持；后续announcement42/holders21/regression4原ID保持。固定新runId、新空schema、原预算/零自动重试及逐轮审查条件不变。这是已定位工具修复后的明确补验，不是未变条件下的自动重试。旧trade24和旧dates29当前引用只在对应新项全部通过后按显式映射替代；历史run/case不改。原adj_factor EVIDENCE_MISSING可移出当前引用，原fina_indicator FAILED不适用。

逐case构建身份表必须分别保留原82项v2包、续办99项撤回包和本次修复后91项新包，不能把旧结果归于新包。新的真实失败仍停止保全，不推定其他数据源也可跳过。当前目标仍272项加递延ISSUE-033的6项。

## 2026-09-15资产负债表失败保全与有界定位

公告日期轮 `issue031-statusfix-range-response-announcement-20260914T195234Z` 实际8 PASS观察/1 FAILED/33 NOT_RUN、exit1；8项income尚不能计入清洁验收或开放接口，当前清洁通过205/272。balancesheet首项真实TASK和批次均为FAILED/ADAPTER_TYPE_INVALID，1请求/1尝试，SQL0→0；持久sourceRows0是成功适配计数，不证明来源为空。原始safe-results因成功专用校验先抛错而记EVIDENCE_MISSING，保留原件，仅依据taskObservations及只读SQL对规范索引补记真实失败；旧run/case不改。balancesheet候选与正式条目撤回至v3/NEEDS_VERIFICATION，SINGLE保持可用。

下一步属于已发生适配缺陷的必要定位：以原失败包和原参数对balancesheet做至多一次只读调用，区分转换错误与原业务键冲突。它单独登记为诊断，不重验或覆盖已合格SOURCE，不计TASK/PASS，也不开放接口。该限定调整基于用户“处理其他数据源”的既有修复范围；用户仅递延fina_indicator，不能据此递延balancesheet。没有收到额外的用户诊断批准，不把可选偏好问题的等待时间视为批准。

- 私有计划 `/private/tmp/issue031-balancesheet-diagnostic/registration.json`：`issue031-balancesheet-diagnostic-20260914T200411Z`；只允许balancesheet，参数 `000001.SZ/20240101/20241231`，最多1请求、150秒、零重试，不启动应用、不访问数据库、不提交任务，不调用fina_indicator。
- 使用失败轮冻结验收包SHA `99ae75e9370a6b4fdf45391d2092524c382461106b9893622b65260eb6c1dda5` 内原client/definition/adapter；运行前核对JAR及全部提取依赖字节，登记诊断源码/类/包装器哈希，一次性启动标记拒绝再次执行。
- 响应只在内存适配与比较。白名单结果仅含错误码、分支、行索引、已知字段名/类型及精度/小数位/长度、冲突字段名；不写Token、响应原文、财务值、业务键值或原始异常。包装器仅传必要环境，stdout/stderr先在内存检查，只有通过白名单校验的JSON可落盘；诊断失败即停，不自动重试。
- 先独立审查该具体诊断。若需改变业务键、字段语义或有损数值处理，先形成证据及具体方案，不能靠猜测改合同。修复后重新验证/冻结；公告轮重新执行须有新ID及显式映射，保留失败轮全部历史。其余轮仍未获准跨过真实失败恢复门禁。
- 验收工具另以真实FAILED回归修复失败事实丢失，保留成功专用严格校验，不能把失败放宽成PASS。

### 已定位冲突后的独立接口恢复

一次诊断已完成：原参数返回6行，原adapter确认KEY_CONFLICT；转换失败0，两组冲突行仅`total_share/update_flag`不同。公开doc36只称update_flag为“更新标识”，不足以确定版本保留规则。balancesheet保持v3撤回，其8项仍在当前272目标中，尚未另行递延；用户关于是否另记issue的偏好问题待答，不视作批准。禁止追加诊断或按返回顺序任意取值。

失败轮停止、保全、撤回、根因定位已完成。其余接口没有该接口的写入依赖；为继续用户已授权的“其他数据源”，先冻结新包，将待验收的公告日期任务拆为独立34项（income8/cashflow8/fina_audit4/express6/stk_managers8）和暂挂balancesheet8，再执行其余holders/repurchase21与regression4。该顺序调整不缩小272目标，不把balancesheet记PASS或宣称全部完成；版本取舍决定只影响暂挂8项。

公告日期34项的旧ID均已登记，因此固定新ID`issue031-keyconflict-<原完整TASK caseId>`，原params/dateAxis/准确SOURCE和接口内顺序不变。income旧8项虽然观察PASS，但所属exit1轮，必须全量清洁补验；仅新34项对应成功并经SQL/审查/清理后移出原当前引用，全部旧run/case保留。替代清单逐项钉住原状态/任务ID和失败run身份；原PASS只允许作为失败轮中的未接受观察被替代，原FAILED的balancesheet不适用。当前205已接受任务保持原包绑定，新59项使用新构建；balancesheet8继续保留原失败轮包和未决项。每轮仍独立新空schema、预登记/纯选择/--list、原预算和零重试；新失败再次停止该轮并保全撤回。

### 现金流量表失败后的逐接口续验

独立公告34项轮`issue031-keyconflict-range-response-announcement-rest-20260914T202312Z`在现金流量表首项失败后停止，实际8 PASS观察/1 FAILED/25 NOT_RUN，exit1/cleanup PASS、9请求/17 records。现金流量表原参数`000001.SZ/20240101/20241231`，ADAPTER_TYPE_INVALID，1尝试；新harness已直接保留FAILED/失败叶，未补造SQL-after。只读SQL确认income8原键、cashflow0。cashflow撤回v3，原字段/业务键/日期轴与SINGLE保持；没有额外诊断，不能由balancesheet冲突推断它的根因。

当前272目标中，205项已清洁接受，balancesheet8/cashflow8暂挂且仍计入目标，另51项可独立继续。为避免再把已执行接口卷入其他接口失败轮，将剩余任务按API分为income8、fina_audit4、express6、stk_managers8、repurchase5、top10_holders8、top10_floatholders8、regression4；regression保留原4接口共同回归组。每API单独run/新空schema/预算/审查/归档，原接口内顺序与窗口保持，无跨API数据依赖。

新公告26项的caseId固定为`issue031-perapi-<当前keyconflict完整caseId>`，其精确SOURCE/params/dateAxis不变；仍未登记的其余25项沿用旧ID。替代映射直接指向最终新case，保留原公告失败轮及本轮全部历史；income两批各8 PASS只是两个exit1轮中的未接受观察，只有新独立income8全清洁通过后才能移出当前引用并开放。映射例外严格限定这两个已知失败run中的income，两个原FAILED接口不适用。所有205已接受项保持原包归属，新51项使用再次冻结的新源码/两包；held16保留各自失败包/版本身份。

本调整落实用户继续其他数据源的授权，不擅自扩大ISSUE-033、不新建递延issue、不缩小272目标；balancesheet版本规则/递延偏好仍待答，cashflow根因未确认。新接口失败继续停止并保全该独立run、撤回其准入，其余没有数据依赖的接口按新冻结身份继续；不自动重试失败接口或追加SOURCE诊断。

### 回购失败后的剩余接口续验

独立回购轮 `issue031-perapi-range-repurchase-20260914T211042Z` 首项FAILED / ADAPTER_TYPE_INVALID，1请求/1尝试/1次BEFORE records，另4项NOT_RUN，exit1/cleanup PASS。只读SQL另证回购表0；规范case的SQL前后仍null，不补造AFTER，不从成功source计数0推断来源为空。原41轮、当前失败包和schema保留；根因未定位，本轮不自动诊断或重试。

按既有失败合同撤回repurchase至v3，保持原DATES参数、公告日期、业务键及SINGLE。原参数准入回归先RED，再完成策略/HTTP及独立页面能力预期的最小修改；非股票数据保留合同继续使用受控已验证策略测试。相关回归、完整隔离构建和受影响metadata通过后，冻结新包再继续top10_holders8、top10_floatholders8、regression4，共20项。三组仍逐轮新空schema、准确原SOURCE/参数/ID/顺序、预算、SQL、清理和独立审查，尚未使用的20个ID不变。

新控制目录 `/private/tmp/issue031-repurchase-control` 保留272位置及既有逐case构建绑定，仅将未执行的20项绑定新冻结包；原已接受231项与held21保持各自原包身份。repurchase5与balancesheet8/cashflow8仍在272目标内，未经用户决定不另行递延，也不计PASS。新失败继续单轮停止保全、撤回和重新冻结；财务指标6项始终由ISSUE-033承接。

### 现金流量表的一次有界根因定位

逐接口剩余任务完成后，对仍未定位的cashflow适配失败执行一次明确登记的诊断。这是用户“处理其他数据源”范围内的缺陷定位；对上一节“不自动追加SOURCE诊断”的限定调整仅适用于下述一次调用，不重新验收旧SOURCE，不自动重试TASK，不调用财务指标或再次调用balancesheet。诊断完成不改变272目标或替代真实TASK/SQL验收。

- 私有登记 `/private/tmp/issue031-cashflow-diagnostic/registration.json`，run `issue031-cashflow-diagnostic-20260914T210414Z`，登记SHA `e014e06eacf7730c06d8a587c9fc54e84a7c8c90a2db822b0386d62274d70d36`。原失败参数cashflow/000001.SZ/20240101～20241231，最多1请求、150秒、无重试；等待其他真实轮次全部结束，保持来源请求间隔≥2秒。
- 使用原失败轮验收包 `65a809227e4c3d485799af51a9826d37943ecc623c9deaf6aed3167aed19654e` 内原client/definition/adapter及全部54个依赖；执行前校验JAR、Java、诊断源码/类/包装器及白名单输入的固定哈希。一次性execution-start标记拒绝重复执行。
- 不启动应用、不传数据库配置、不创建TASK或写业务表。响应只在内存中转换/比较，输出白名单错误码、适配分支、行索引、字段名/类型和精度/长度元数据、冲突字段名；不保存Token、原响应、财务值、业务键值或异常原文。输出未通过校验或来源调用失败即非零退出，不重试。
- 已完成本地编译及7项输出校验，零真实请求；实际执行前独立审查具体实现与登记。原cashflow FAILED及缺失SQL-after保持，诊断结果单独归档。若涉及版本保留、业务键或字段语义选择，先给出证据和具体方案，不以行顺序或未经证明的update_flag含义选值。

执行前准备修订2：公开现金流文档doc44明确`update_flag`为“更新标志(1最新）”，保存HTML SHA `c72936927f2038160b357766e350aba40241404f02aebe6e12edac9283e17b9c`。同一次内存响应另汇总完全转换且满足原必填键约束的行数/原键组数、冲突组数，以及冲突组内标记1的不同完整行数为0/1/多个的组数；先按原adapter的Map相等规则去掉完全重复行。全部输出为固定名称非负整数，三个分箱之和必须等于冲突组数。0只表示未观察到标记1，不解释为全部过期；诊断不选行、不改合同、不写入，也不推广该规则到其他接口。

修订2登记SHA `8af2822b2b0b4971a4367be71e7df03f32364cc34e183a4c2b912d4c8f772b49`，旧e014登记/源码/类保留在`revision1-prepared-not-run/`作为未执行准备历史；仍是同一个run、总计最多一次请求。编译与21项纯检查通过，包含0/1/多最新、完全重复最新行、转换不完整/必填缺失排除和严格输出校验，零真实请求。修改后身份需重新独立审查，不能沿用旧预检结论直接执行。

### 回购的一次有界根因定位

为落实用户继续处理其他数据源的范围，对已保全的repurchase适配失败作一次明确限定的诊断；仅调整前述“不自动诊断”边界中的这一项，不重试TASK或改参数。登记 `/private/tmp/issue031-repurchase-diagnostic/registration.json`，run `issue031-repurchase-diagnostic-20260914T212829Z`，SHA `57b870e3af74e97a5928a349c59b91d18da7341d03642c291f23f8ffe662e463`；原DATES参数20260801～20260831，无ts_code，使用原失败验收包a77d99ac…c732d3内原client/definition/adapter及54依赖。

其他真实TASK结束后、独立预检通过才执行；最多1请求/150秒，无重试、不启动应用/访问DB/提交TASK。Java/原包/诊断源类/包装器/白名单输入哈希执行前核对，一次启动标记拒绝重复。仅输出原适配错误分支、行索引、已知字段名/类型/精度长度与冲突字段名；原响应、数值、业务键值和异常原文只在内存，不落盘。原FAILED及null SQL-after保持，诊断不计PASS、不开放接口。编译及7项输出校验通过，零来源请求；字段或键语义若需改变，先形成具体证据与方案，不能猜精度或覆盖冲突行。

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
