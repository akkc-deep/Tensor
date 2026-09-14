# ISSUE-021：交易所日历与交易日来源验证

## Goal and scope

用户于2026-09-13要求“完成issue21”。本项负责`trade_cal`、`margin`、`top_list`的规则、市场语义和固定SOURCE验证，向ISSUE-026交付匹配的任务输入；沿用[T13](ISSUE-018-T13-design.md)、[T14](ISSUE-018-T14-design.md)。不修改40份YAML、业务键、生产准入或历史证据，不提交TASK或连接数据库。

## Evidence and approach

1. 官网26明确“三大交易所的交易日历都是一样的，北交所交易日历参考上交所和深交所”，输入枚举不含BSE。当前生产`exchangeForStock`仅接受SH/SZ；`trade_cal(exchange=BSE)`的plan/sourceParameters拒绝，SINGLE不改。`TushareTradeCalendar.openDays`要求请求交易所一致、每天唯一、没有缺日，再枚举is_open=1；全休市完整日历与空响应不同。旧BSE校验失败不说明HTTP不支持或原响应为空。
2. 先按TDD在测试Probe增加安全日历摘要：成功且结构合法的日历响应行数、请求交易所；完整覆盖校验后才记录开市日期集合。校验失败仍可保留合法响应行数，未返回/非法Envelope不记录；不输出原始行、金额、错误body或凭证，不把失败节点的sourceRowCount改成成功数据。覆盖0行、缺日、重复、错交易所、非法Envelope、传输异常及完整休市的回归。
3. 第一轮固定SSE/SZSE的完整区间、两端单日、全休市和跨年；margin沿用既有20260803～20260810窗口，分别对SSE/SZSE/BSE请求整段及两端，保留`exchange_id`且逐行校验归属。top_list使用官网或公开事件中有依据的SH/SZ股票及日期，整段、事件单日和事件位于上下边界分别验证；完整日历的每个开市日都执行，空证券子请求不跳过。
4. BSE直接输入独立一轮，避免预期完整性失败使其他case变成NOT_RUN。直接请求原始BSE参数；仅依据实际安全摘要下结论，不以沪深结果替代BSE支持性。
5. 取得上述沪深实际完整日历后，依官网同历说明固定BJ→SSE候选。仅扩展测试Probe的BJ取证编排：日历请求SSE，完整校验后股票请求仍是原`.BJ`代码，绝不将证券或返回exchange改写成沪市；生产拒绝路径保持。先回归再重建独立包，另开BJ固定来源轮，包含开市窗口和全休市。此候选是已授权市场映射调查，未改变存储范围或放宽完整性；未来生产实现和版本递增交ISSUE-026与真实TASK/SQL共同验收。
6. 使用完整Git管理文件（含既有暂存成果）的独立clone，逐文件核对；新文件先Git纳管。普通测试/acceptance verify环境移除账户及DB变量。每轮登记精确runId/caseId、参数、依据、预算、私有输入哈希、源码指纹、生产/验收包哈希；稳定后执行现有受限wrapper。0700目录/0600清单、HTTPS、至少2000ms间隔、30分钟/5000请求、35分钟外层超时、失败停轮不自动重试，核对退出码和清理。SOURCE无数据库。
7. 只追加唯一JSON和T14运行登记；逐对象保留旧12轮/531case、479请求及其他37接口。不将失败/空对照改成非空PASS。每个候选TASK复制有效SOURCE精确参数/dateAxis，使用新身份并绑定来源及规则；保留BSE失败结论、旧SH空样本和未执行生产任务。

## Verification and acceptance

- `TushareRangeSourceProbeTest`先RED再GREEN；相关Policies/BatchDownload/TradeCalendar回归证明生产准入、BSE拒绝、margin原参数保持。
- 每次用于真实来源的新源码运行`mvn -o -f data-plane/pom.xml -Pacceptance verify`；Node证据校验`node --test control-plane/e2e/tushare-range-evidence.test.js`通过。只改测试Probe和文档不触发生产源码六门禁的重复任务。
- SSE/SZSE每天唯一且整段/两端/全休市有效；直接BSE和BJ候选分别有事实结论。
- margin三交易所整段及两端有非空、正确归属来源；top_list两只股票非空整段/边界、完整交易日枚举和休市对照有效。
- 规则、真实来源、准确任务输入交ISSUE-026；最终本issue状态依据全部条件更新，母issue/T13/T14保留其剩余验收。缺失或矛盾不得靠改口径消失；如需要实质业务范围决定，先完成独立取证再明确提交用户选择。

## 完成证据（2026-09-13）

- 第一轮31case/47请求全部PASS，独立BSE1case/1请求合法结构0行后完整性FAILED，BJ候选轮7case/15请求全部PASS；三个cleanup PASS，旧证据不重标。
- SSE/SZSE完整日历14项（含两端、国庆全休市、跨年和BJ参照窗口）成对同历；margin9项三交易所非空且exchange_id正确；top_list15项SH/SZ/BJ非空整段/边界及闭市对照按完整日历执行。BJ候选仅测试侧，直接BSE生产拒绝不变。
- 相关170项Maven、78项Node通过；两份完整隔离源码分别1068/1069后端及各468前端检查通过。38项匹配TASK输入及1项BSE负例拒绝已离线核对；旧12轮/531case、其他37接口与顶层输入哈希逐对象不变。
- [T14最终验收](../verification/ISSUE-018-T14-runs.md#issue-021-最终验收)记录实际身份、回归、复审和状态；[ISSUE-026](../issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-021-已交付输入)接收38项精确绑定、规则与生产映射要求。三项关闭条件成立，本issue完成；生产准入及母issue的剩余任务没有提前完成。
