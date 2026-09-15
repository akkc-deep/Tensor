# ISSUE-032：最终回归与母任务收尾设计

## 2026-09-15最终验收结果

本次批准范围已完成最终验收与状态收尾，详见[逐条结果](../verification/ISSUE-032-range-final-closure.md)。六门禁1401/524/1134/1137/15/138、111证据测试与独立终审通过；30个RANGE/251项纳入、四接口27项排除，SINGLE保持。032先完成，T14→T13→017/018→026随后完成；033未开始。下文旧阶段数字和不启动032的决定保留为历史，不作为当前未完成状态；原合同按明确范围决定解释。

## Goal

按用户2026-09-15最新“完成issue32”指令启动最终验收，完成六项源码门禁、逐条母合同核对和独立终审，再按权威看板实际状态关闭ISSUE-032、ISSUE-026、T14/T13及ISSUE-017/018。

本次启动取代此前“不开始32”的时序决定；[四接口范围决定](../issues/proposals/ISSUE-026-range-scope.md)继续有效。旧预检原件保留在`/private/tmp/issue032-preflight-c469lima/`，本次起始工作树与暂存差异备份在`/private/tmp/issue032-final-m_or9ii3/`；此前草稿不作为最终通过证明。

## Scope

- 当前验收为30个RANGE接口/251项固定TASK通过、四接口/27项明确排除、原六个SINGLE_ONLY保持；40接口SINGLE及原业务键、参数、日期轴不变。
- 完整消费ISSUE-031已交付的45轮/1217case/1362来源请求及各自源码/包身份、SOURCE/TASK/SQL和清理证据。排除项原4 FAILED/23 NOT_RUN继续保留，不计PASS；不执行四接口诊断或重试，ISSUE-033仍NOT_STARTED。
- 在含现有全部未提交成果的新隔离副本执行六门禁，复核最终运行代码、正式HTTP/schema、历史快照、独立能力预期、页面、手册一致性。当前没有确定的新增功能或生产修复；如发现缺陷，定位根因后最小修复并按影响复验。
- 完成当前任务及母任务的有证据状态转换，不自动提交、推送或发布。旧数据库、私有证据及前序工作保留。

## Approach

### 1. 确认前置与冻结最终源码

ISSUE-031权威子看板已COMPLETED，251项SOURCE/精确参数/日期轴/候选包绑定和清洁TASK/SQL已有事实；四接口冲突/适配缺口由明确范围决定解除本次关闭前置。启动前复用scope-audit的只读断言并输出至本次新目录：核对251纳入、27排除、45轮逐对象保全、各自包绑定和30/4/6矩阵，再运行111项证据测试。

不以HEAD代替现有工作树。保存起始文件和暂存/未暂存差异，创建自有隔离clone后逐文件复制完整工作字节，记录文件清单与SHA-256；只共享已安装工具/缓存，不复用旧target测试结果。记录当前分支/HEAD，保留原分支。所有门禁使用同一冻结源码，文档收尾后另外记录与冻结副本的差异，不将新文档哈希冒充旧构建身份。

### 2. 六门禁与可复用判断

前序1137后端/打包、524前端等结果继续作为局部历史；不能证明规定的最终命令及执行顺序，本次六项全部按Tests顺序执行。每项保存起止时间、实际命令、退出码、XML/JSON报告、测试/失败/错误/跳过数及清理证据。首项含全部Test/IT及真实MySQL/生命周期浏览器；普通clean verify及acceptance clean verify不替代首项。

普通环境仅保留工具路径、HOME/TMPDIR/locale及自有Docker设置，剔除真实Token、外部数据库和live标志；Java21.0.11、Node24.15.0/npm11.12.1。前三个Maven命令各自归档报告；第3项保存生产包，第4项保存实际生产/验收两包，记录哈希。Maven内的前端524用例单列，不与JUnit重复混计。

第4项通过后按[T12设计](ISSUE-018-T12-design.md)建立自有preview4173、MySQL8.4.6容器和四个新空schema、0700目录/0600映射和defaults文件；绑定当次验收JAR及哈希。8080/4173不得占用他人进程。浏览器workers=1/retries=0，默认七文件且明确排除专用账户套件；第5项通过后再执行第6项。无新源码影响不机械重做此前真实账户任务。

任何失败保留原报告，先辨明环境/测试/生产根因；修复后记录适用源码和受影响复验，最终顺序不能由不同代码拼接。只清理本次进程/容器/秘密；保留旧成功schema。

### 2026-09-15首轮门禁发现

G1实际1401项中1失败：`DownloadTaskApplicationConfigurationIT.productionServletGraphStartsAfterFlywayAndCatalogWithOneSharedRunAndBudget`仍期待34 AVAILABLE/0 NEEDS_VERIFICATION，实际30/4符合四接口范围决定和独立策略/HTTP矩阵。仅修正该集成测试两条数量断言，40总数/6 UNSUPPORTED/零来源及其余装配约束保持；无生产代码修改。保留首轮失败原件，更新完整源码指纹并从G1重新按序验证，不把原失败改写为通过。

### 2026-09-15首次完整浏览器轮

首次G6为83通过/3失败/52因串行前例失败未执行；完整输入和G1～G6原件保留在`failed-browser-round/`，自有资源清理成功。`dataset-query`和`download-outcomes`仍按旧默认填写SINGLE交易日期，现AVAILABLE默认RANGE；各在选择Tushare接口后显式选择并断言“单次请求”，其余请求/计数/SQL约束保持。metadata的7表头失败另以原用例采集诊断，不直接当作通过或删除断言。最终结果与诊断见最终报告。

### 3. 兼容性与母合同核对

| 合同 | 要核对的证据与结论边界 |
| --- | --- |
| ISSUE-026 Acceptance 1 | RESPONSE_ONLY实现/HTTP/schema/页面的非空、空、失败；原11规则保留，本次8纳入；21 ROW_LIMIT和1 CALENDAR_COVERAGE纳入；UNKNOWN拒绝及原严格边界保持 |
| ISSUE-026 Acceptance 2 | 251纳入精确来源/参数/包、真实批次/SQL/清理；原278总追踪和四mainbz新SOURCE/四TASK通过（其中三项实际拆分）、两disclosure重下、四接口回归；原27排除及74 SINGLE保留 |
| ISSUE-026 Acceptance 3 | 40项元数据/页面/手册：正式索引30 AVAILABLE/4 EXCLUDED/6 SINGLE_ONLY；运行30 AVAILABLE/4 NEEDS_VERIFICATION(v3)/6 UNSUPPORTED；历史任务仍读持久规则和原版本 |
| ISSUE-026 Acceptance 4 | 同一适用源码六门禁、111项证据校验、冻结身份与历史保全、独立终审无阻断 |
| ISSUE-026 Acceptance 5 | 本子issue先完成，再逐项核对母状态；T14/T13阻塞解除须有实际合同与范围决定，不能只改状态 |
| 总体设计§6第1～8项 | 用最终Test/IT和浏览器映射异步ID/任务持久化、参数链、原子事务、retry/resume、拆分/计数、预算/节流、独立来源/SINGLE、包/真实接口证据；仅四接口RANGE适用范围排除 |
| T13/T14 Acceptance各六项 | 40矩阵/30纳入真实证据、代表样本及明确差异、能力/历史/页面/回归；holders原样本没有观察同股东同报告期多公告日，按实际合同记录观察边界，不由每期超过10行推定 |
| ISSUE-017九条关闭条件 | 原74 SINGLE/148查询与SQL两股票归属、34股票/6非股票形状；接口差异及元数据/合同/回归逐条核对 |
| ISSUE-018六条关闭条件 | 最终支持清单、原参数/日期链、代表边界/历史、拆分/失败/幂等、特殊接口和六门禁/逐项实测，四接口仅按批准范围排除 |

独立审查按上述逐项读取实际源码和证据，不能把测试全部通过等同于母合同成立。用户已接受的RESPONSE_ONLY可能漏数、工程阈值、主营默认分类、历史保留未知和质押代表股票决定分别保留；不改成上游保证。

### 4. 收尾顺序

本项设计完成且启动授权成立后，按NOT_STARTED → READY → IN_PROGRESS逐次记录，Handoff=None保留（用户直接启动本项，031已完成且未生成后继交接）。本项四条Acceptance成立后先记录IN_PROGRESS → COMPLETED，再读取母合同及看板：先完成T14、再以同批事实完成T13，各按BLOCKED → READY → IN_PROGRESS → COMPLETED逐次记录解除、启动和验收证据；随后ISSUE-017/018逐条勾选关闭条件，最后ISSUE-026按IN_PROGRESS → COMPLETED收尾，使其Acceptance 5包含已记录的T14/T13及母issue结果。

任何实质母合同缺口如实保留，不提前关闭；ISSUE-033本次明确跳过，不准备为READY，不创建T15。完整发布脚本的main/干净输入/HEAD前置单列，未运行不声称发布通过。

### 历史预检（启动前，保留当时身份）

2026-09-15从工作树HEAD `f563bd9fb57ca00508dd997548c296d7f5cafb93`读取实际未提交文件，重新执行证据测试和只读审计：

- `data-plane/tensor-app/target/frontend/node/node --test control-plane/e2e/tushare-range-evidence.test.js`：111/111通过，失败/取消/跳过0，退出0。
- 重用已审阅的ISSUE-031 `final-audit.mjs`核对逻辑，将报告输出改至本次新目录，保留旧报告。272项精确参数/日期轴/包绑定、251 PASS/3 FAILED/18 NOT_RUN、45轮/1217case/1362来源请求、正式30 AVAILABLE/4 NEEDS_VERIFICATION/6 SINGLE_ONLY均核对通过。原26轮及42轮基线逐对象保持，最后三轮输入/安全输出/身份/清理一致。
- `/private/tmp/issue032-preflight-c469lima/preflight.json`及同目录`evidence-audit.mjs`、`evidence-audit.json`、`evidence-audit.log`保存本次审计；没有新增真实来源、TASK或SQL。
- 当前422个相关文件（data-plane、control-plane、scripts、docs/contracts）相对冻结副本`/private/tmp/issue031-repurchase-20260914T211953Z`仅`control-plane/e2e/tushare-range-evidence.test.js`变化。冻结898文件快照、两包、manifest及请求样例重新计算哈希，与记录一致；不声称整个当前工作树等同旧快照。

| 身份 | 本次复核的SHA-256 |
| --- | --- |
| 冻结898文件快照 | `258b7df2a635a9cd3f53f7aaf6abb9257a55ab2551549269551bf09602da3b9b` |
| 生产JAR | `4d4f1e3985c10aca803a0ca4d8d1a535e2637909b0f68192c977d193c40735be` |
| 验收JAR | `5ecb993e13d6c1340013eb48ca87ea4a91782906dd06bd51f2108b7b51c1bfd5` |
| 当前唯一验收索引 | `1a83add3b0288d2823c1f2bf1d862bc279ac0b8c59cd0ab912c1a2a1c2321558` |

## Files

- `docs/task-designs/ISSUE-032-design.md`：本次可执行设计、历史预检及最终结果入口。
- `docs/verification/ISSUE-032-range-final-closure.md`：新增最终命令/数量/身份/历史保全、母合同逐条映射、审查与清理报告。
- `docs/verification/ISSUE-018-range-acceptance.md`、`ISSUE-018-T14-runs.md`：最终回归链接；唯一JSON只有实际必要元数据修正才修改，全部run/case保持。
- 两份runbook、issues索引及017/018/026/032、026子看板与018两个看板、task-handoffs索引：当前边界/关闭事实，旧状态证据保留。
- 如发现实现缺陷，在对应最小消费者修复并记录实际影响。新增仓库文件加入Git。

## Tests

先核对证据及源码身份，再使用上述隔离环境按顺序执行（下列是计划命令，不是结果）：

```sh
node --test control-plane/e2e/tushare-range-evidence.test.js
mvn -f data-plane/pom.xml -Dtest='*Test,*IT,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' -Dsurefire.failIfNoSpecifiedTests=false test
npm --prefix control-plane test
mvn -f data-plane/pom.xml clean verify
mvn -f data-plane/pom.xml -Pacceptance clean verify
npm --prefix control-plane run test:e2e -- e2e/download-tasks.spec.js
npm --prefix control-plane run test:e2e
git diff --check
git diff --cached --check
```

要求退出0、失败/错误0、无未解释跳过，报告实际数目、用例清单与清理。兼容检查映射SINGLE extraction=null、旧RANGE持久规则不随当前策略变化、策略变化拒绝重放、RESPONSE_ONLY非空/空/失败提示及严格边界。

## Acceptance

1. 251纳入/27排除与30 AVAILABLE/4 EXCLUDED/6 SINGLE_ONLY有精确实际依据，原失败/未运行、45轮历史、原74 SINGLE及各自包身份保留。
2. 同一最终适用源码六门禁按序通过，实际数量/退出/跳过/清理及最终两包身份可复核；必要兼容测试与正式合同/页面/手册一致。
3. 母合同逐条有证据及已批准差异说明，独立终审无剩余阻断；接受漏数等决定不冒充上游保证。
4. 本项先按合法状态COMPLETED，再核对并完成本次范围内母任务；未满足的合同准确保留，033不启动，不自动提交或发布。

## Risks

- 真实SOURCE/TASK绑定各自原包，本次构建不能改写它们；文档更新也不等于运行代码变化。
- 临时证据原件可能丢失，须重新核验；缺失结果不能臆造或重跑来源来掩盖。
- 测试需要Docker、本地监听和缓存访问；环境失败保留并定位，不能以跳过代替验证。
