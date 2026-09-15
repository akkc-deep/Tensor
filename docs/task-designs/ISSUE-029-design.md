# ISSUE-029：验收工具与主营业务拆分来源

## Goal

消费[ISSUE-028实际HTTP合同](../verification/ISSUE-028-response-only-contract-ui.md)，使证据消费者严格识别响应采集及指定SOURCE身份，并取得fina_mainbz两个股票、全年和六年宽窗的四项完整拆分SOURCE。身份/顺序由[子看板](../task-handoffs/ISSUE-026/ISSUE-026-task-board.md)管理。设计时仅作后继准备，尚未执行新来源；现已完成，实际结果见[验收记录](../verification/ISSUE-029-range-evidence-and-split-source.md)。

## Scope

落实[共享设计第4节及mainbz拆分约定](ISSUE-026-design.md)：schema1/2读取、RESPONSE_ONLY证据规则、指定来源绑定、harness对能力/任务摘要与单请求单叶子的核对；扩展测试侧Probe并执行四项固定SOURCE。保持40接口、生产4 AVAILABLE/30 NEEDS_VERIFICATION/6 SINGLE_ONLY、原业务参数/股票/键和全部旧run/case。不开生产候选，不执行TASK/SQL，不重做其他来源；候选和278任务分别归ISSUE-030/031。

## Approach

### 1. 严格证据合同

`validateEvidence`读取schemaVersion=1/2，顶层、run、case字段白名单保持。schema1不允许RESPONSE_ONLY；schema2允许interface.completeness.kind=RESPONSE_ONLY，仅限adj_factor、suspend_d、income、balancesheet、cashflow、fina_audit、express、repurchase、stk_managers、top10_holders、top10_floatholders。要求NATIVE_RANGE、rowLimit=null、非空evidenceRefs，interface.decisionRef指向`docs/issues/proposals/ISSUE-025-extraction-contracts.md#决策记录`。依据同时关联决定、逐接口报告和原官方来源说明；不重写旧SOURCE的UNKNOWN事实。

UNKNOWN继续不可选择/不可AVAILABLE。新增RESPONSE_ONLY TASK的expectedCoverage固定`RESPONSE_ONLY`，expectedCoverageSource关联决定及精确SOURCE；不能直接复制旧SOURCE的结果语义，也不能用COMPLETE冒充。AVAILABLE仍要求>=v2、同参数不同阶段SOURCE/TASK、清洁run和完整任务/SQL事实；新规则TASK恰一个覆盖父窗口的成功叶子、requestCount=1。`validateAvailable`及case/tree检查共同拒绝多请求、SPLIT、缺叶子、范围缺口、缺SQL或不清洁身份。严格ROW_LIMIT/CALENDAR_COVERAGE检查保持。

消费者拒绝测试通过后才将唯一索引顶层schemaVersion从1升2；旧25轮822case逐对象不变。支持新枚举不等于把十一项生产或当前索引条目开放：无匹配TASK/SQL仍NEEDS_VERIFICATION，候选接口规则/版本与引用的配置归ISSUE-030。新四项SOURCE仅追加run/case，不能重标旧失败。

### 2. 明确来源绑定

保留`selectTaskCases(phase, plan, index, sourceBindings = new Map())`入口及Map结构。对RANGE候选先解析evidenceRefs中`docs/verification/ISSUE-018-range-acceptance.json#<runId>/<caseId>`，恰一个指定SOURCE；按身份定位后校验entry.cases引用、run清洁退出/清理、SOURCE/RANGE/PASS、API、dateAxis和精确params，以及候选包含全部规则依据。不存在、run/case错配、多指定SOURCE或条件不符均拒绝，不能回退首个同参数对象。

旧`#caseId`仅在全局唯一且明确指向合法来源时兼容；无指定身份的旧计划仅在恰一个合法同参数候选时兼容，多个候选拒绝歧义。新清单一律完整身份。保留sourceBindings项`{runId,caseId,expectedCoverage,expectedCoverageSource}`，`tushare-live.spec.js`的initialTaskEvidence及实际执行共用同一Map；响应采集的结果预期由当前采用合同生成，SOURCE身份单独保留。

必须用同参数的两个清洁来源证明两种指定绑定都正确，并拒绝无身份歧义。真实回归是000001.SZ/20250101～20251231：新`000001-annual`不能绑定到旧`issue020-source-20260913T131245Z-000001-whole`的74行来源。原12项mainbz仍指定旧有效SOURCE，新四项指定各自新拆分SOURCE。

### 3. harness消费HTTP及页面

复用ISSUE-028的`parseDownloadCapabilities`/`parseDownloadTask`严格解析，无兼容性默认值。新规则核对capability RESPONSE_ONLY、rowLimit=null、NATIVE_RANGE、splittable=false、候选policyVersion，以及task.extraction精确保存的版本/规则；历史规则不能依据当前API名推断。所有批次终态后核对恰一个完整成功叶子和requestCount=1，并保留现有SQL/归属/原键/日志和清洁身份检查。

RESPONSE_ONLY的reviewMethod由harness生成已核对事实，涵盖持久摘要、单请求单成功叶子、原始/写入数、SQL及页面完整性未确认提示；不靠自由文本关键词放行。页面SUCCEEDED非空应为“返回记录已采集”，空应为“本次请求未返回记录”，持续显示“数据完整性未确认，可能存在上游截断”。合法空是产品成功，但固定非空代表性样本意外为空时证据仍EVIDENCE_MISSING，保留真实SUCCEEDED，不覆盖旧SOURCE或换日期重试。

### 4. fina_mainbz测试侧二分

在`TushareRangeSourceProbe`现有`run`及受控Source入口中，仅为fina_mainbz RANGE增加递归执行；复用当前HTTPS客户端、参数/Envelope/股票/日期校验和Context预算。不调用sourceVerified门禁后的生产plan，不改生产策略true。

采用ISSUE-020已批准ROW_LIMIT=100：原始响应<100为成功叶子（含合法空）；>=100且多日，使用现有`DateRangePlanner.split`产生相邻闭区间，深度优先先左后右；单日>=100报BATCH_COMPLETENESS_UNCONFIRMED。每次父/子请求均预约现有预算和>=2000ms间隔，失败即停当轮，不自动重试。其他API和SINGLE行为保持。

batchNodes记录真实根、SPLIT父、两子节点及最终叶子，沿用现有字段。SPLIT父不计成功行数、写入或P/D/I分类/业务键/日期投影，请求数仍包含父。仅成功叶子汇总安全投影。整树完整覆盖、叶子均<100且至少一叶非空、run清洁完成后才SOURCE PASS。残缺/失败/未执行/最小满额保留实际失败或EVIDENCE_MISSING；candidateLimitReached如保留只表示遇过满额父，reviewMethod需区分拆分父数与未解决满额叶子，不能据该标记直接将完整树失败或放行部分树。

### 5. 固定四项SOURCE与登记

执行前用实际UTC登记`issue026-mainbz-split-source-<UTC>`，caseId为该完整runId加下表后缀；dateAxis=REPORT_PERIOD、输出日期end_date、均省略type。

| 后缀 | ts_code | start_date | end_date |
| --- | --- | --- | --- |
| 000001-annual | 000001.SZ | 20250101 | 20251231 |
| 600000-annual | 600000.SH | 20250101 | 20251231 |
| 000001-wide | 000001.SZ | 20200101 | 20251231 |
| 600000-wide | 600000.SH | 20200101 | 20251231 |

先固定私有清单、父窗口、二分算法、来源/决定引用和哈希。按完整日数计算二叉树最坏请求数，实际执行仍受每轮5000次/30分钟上限；不能假定最坏树必能完成，也不能按数据跳日缩窗。子窗口是既定算法结果。完整源码（含未提交文件）复制隔离checkout逐文件核对，构建并冻结生产/验收两包、manifest/examples身份后才执行；目录0700、文件0600，Token仅注入受控后端/Probe进程，SOURCE无业务数据库。

新SOURCE expectedCoverage=`ACCEPTED_ROW_LIMIT_COVERAGE`，expectedCoverageSource关联ISSUE-020决定；原validateTree和来源身份检查通过后追加唯一索引、Markdown及运行登记。保留原四满额EVIDENCE_MISSING及旧对象；不产生TASK/SQL。失败/预算/清理问题如实记录并停止，不重试或伪造来源成功。把四项实际run/case/参数/树和源码两包身份交给ISSUE-030/031；未来四TASK命名为`issue026-task-<完整新SOURCE caseId>`。

## Files

- `control-plane/e2e/tushare-range-evidence.js`、`.test.js`：schema版本与独立规则、指定来源选择、树/任务/SQL拒绝检查和受控回归。
- `control-plane/e2e/tushare-live.spec.js`：共享sourceBindings、按采用合同生成初始TASK证据、capability/task摘要及页面核对；保持运行隔离/预算/清理。
- `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/batch/TushareRangeSourceProbe.java`及`TushareRangeSourceProbeTest.java`：仅mainbz RANGE的二分取证、完整树与叶子投影。
- `data-plane/tensor-plugin-tushare/pom.xml`：`DateRangePlanner`实际位于tensor-core，增加仅test作用域依赖以直接复用；生产依赖与拆分算法保持。
- `docs/verification/ISSUE-018-range-acceptance.json`、`.md`和`ISSUE-018-T14-runs.md`：消费者通过后版本升级和四项实际来源追加，旧对象保持。
- 本issue问题文档、权威子看板和实际验收记录：按最终事实交付，不把设计或支持枚举当作来源通过。

## Tests

第一步在`tushare-range-evidence.test.js`添加两个同参数清洁SOURCE的指定身份选择/歧义拒绝，以及schema1拒绝RESPONSE_ONLY、schema2合法/非法合同测试，运行观察RED。再实现最小消费者和harness；Probe先增加多层二分、父不计投影与单日满额失败RED再实现。

```sh
node --test control-plane/e2e/tushare-range-evidence.test.js
mvn -o -f data-plane/pom.xml -pl tensor-plugin-tushare -am -Dtest=TushareRangeSourceProbeTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -o -f data-plane/pom.xml -Pacceptance verify
git diff --check
git diff --cached --check
```

使用Java21/Node24和白名单受控环境；期望0失败/错误/未解释跳过，实际数量记录在实施验收。覆盖schema1旧对象读取、schema1新规则拒绝、schema2错API/决定/阈值/模式/缺引用、假COMPLETE/多请求/多叶子/缺SQL拒绝；全身份/旧唯一caseId/唯一候选兼容、错误身份不回退；旧12来源与新四来源精确区分。Probe覆盖多层左右树、合法空叶子、坏Envelope/越界、预算/停止/部分树和非mainbz/SINGLE隔离。独立审查与本地门禁通过、冻结身份后才显式运行四项真实Probe；执行命令沿用现有私有wrapper/环境入口，并在当轮登记实际参数和哈希，不能将未执行命令写成结果。

## Acceptance

1. schema1/2合同与严格HTTP消费者一致；新规则拒绝不完整依据、伪完整和缺任务/SQL；旧25轮822case逐对象保持。
2. 指定run/case精确绑定，两个同参数来源各自可选，无身份或错身份不回退；旧12及新四来源分别正确引用。
3. 四新SOURCE均有完整合法覆盖、全部成功叶子<100、至少一叶非空、清洁退出/清理及稳定源码/两包；旧失败不重标。
4. Node/Probe、隔离构建和独立审查通过，向后继交付可复核来源；生产与TASK/SQL边界保持。新增文件Git纳管，不自动commit/push/发布。

## Risks

最坏二分树可能超过固定预算，不能预先承诺四SOURCE均成功；达到预算或上游失败应保留实情。两个同参数来源必须携带完整身份，否则旧74行来源会被误用。接受十一接口不完整不适用于mainbz的100阈值和完整拆分要求。四项真实run时间/哈希/行数属于执行时事实，设计时尚未产生，实际结果已另记验收文档；不能用受控树代替真实树。
