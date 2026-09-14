# ISSUE-024：转融资与转融券历史来源验证

## Goal

完成`slb_len`、`slb_sec`、`slb_sec_detail`的历史范围调查、代表性SOURCE与ISSUE-026任务输入。用户2026-09-13明确要求“完成issue24”；沿用[T13](ISSUE-018-T13-design.md)和[T14](ISSUE-018-T14-design.md)已授权取证流程。

## Scope

两证券接口使用000001.SZ、600000.SH；融资汇总保持无股票原方式。旧20轮/648case/712请求保留；不修改生产参数、列、业务键、5000阈值、版本和准入，不把SOURCE写成TASK/SQL。后继和母issue不提前完成。

## Approach

1. 核对官网331/332/333与证监会2024-07-10公告，保留URL、UTC、原文哈希。区分“可循环获取所有历史”的公开措辞、监管20240711暂停新业务/20240930存量了结、账户在固定窗口实际返回日期；前两项都不是API历史起止保证。
2. 在`TushareRangeSourceProbe.java`增加仅RANGE的三接口安全投影，消费已通过原Envelope、日期及股票验证的响应，不增加fields或账户请求。按YAML原键分别为`[trade_date,ob]`、`[trade_date,ts_code]`、`[trade_date,ts_code,tenor,fee_rate]`，十进制规范化后仅输出有效/不可用键行数、不同键数、原始重复键行数及键集合SHA-256。融资按日期、证券按股票/日期分组，统计一组多键；不输出原始金额、数量、名称或完整业务键。
3. 明细再统计不同期限、费率、期限/费率组合数，同股同日不同期限组数、同股同日同期限不同费率组数。期限须为正整数、费率须为合法十进制；不可用行单计，不发明不存在的期限列。成功空响应输出0，无成功响应/未运行不输出；部分失败只留已成功观察且状态仍失败。
4. 首轮固定官网20240601～20240620整段，融资另有0603/0620两端；两证券各两股票同窗口和0620官网日。明细另固定20230101～20240620较长历史窗调查期限/费率差异，不能据此推导全历史起点。监管日期附近固定20240701～20240711、20240930～20241001仅调查停业前后，不预设非空。每轮精确case、params、依据、预算在T14登记后执行；后续端点仅依据本轮合法实际日期另登记，不循环换日期求成功。
5. 先合成行为测试RED，再最小实现与GREEN。完整Git管理源码复制独立clone并逐文件核对；白名单普通环境排除账户/DB，隔离acceptance verify及Node证据校验通过后登记源码/两包/manifest/examples身份。
6. 复用私有wrapper；目录0700/清单0600、HTTPS、请求间隔至少2000ms、30分钟/5000次上限及35分钟外层超时，串行、失败停轮、无自动重试。检查退出、秘密隔离、输入/源码/包稳定及清理。新文件Git纳管，不自动提交。
7. 原对象仅追加唯一JSON与T14运行登记，同步40行报告和三接口当前缺口。非空通过且身份清洁的精确SOURCE可作为ISSUE-026候选输入；空或失败拒绝。历史支持依据仍不足时，完成能做的取证并交付具体采用方案供用户决策，保持issue未完成，不自行豁免历史合同或排除接口。

## Files

- `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/batch/TushareRangeSourceProbe.java`及`TushareRangeSourceProbeTest.java`：安全计数与行为回归。
- 本设计、`docs/superpowers/plans/2026-09-13-issue-024.md`、T13限定说明：取证边界与步骤。
- `docs/verification/ISSUE-018-T14-official-evidence.md`、`ISSUE-018-T14-runs.md`、`ISSUE-018-range-acceptance.json`和`.md`：公开资料、唯一轮次与当前缺口。
- ISSUE-024问题、ISSUE-026交付、issues索引和后续看板：真实状态与准确输入；必要时新增ISSUE-024决策提案。
- `control-plane/e2e/tushare-range-evidence.test.js`：同步真实证据预期及禁止提前开放的拒绝检查。

## Tests

```sh
mvn -o -f data-plane/pom.xml -pl tensor-plugin-tushare -am -Dtest=TushareRangeSourceProbeTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -o -f data-plane/pom.xml -pl tensor-plugin-tushare -am -Dtest=TushareRangeSourceProbeTest,TushareTradeCalendarTest,TushareBatchPoliciesTest,TushareBatchDownloadTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -o -f data-plane/pom.xml -Pacceptance verify
node --test control-plane/e2e/tushare-range-evidence.test.js
git diff --check
```

首次新增投影测试预期因缺计数失败；实现后全部通过。覆盖同日期不同ob、不按日期去重、同股同日期限/费率差异、等值十进制重复、非法/缺失键、脱敏、空/错误/未运行及SINGLE隔离。复核原20轮及其他37接口不变，所有新run与私有原对象/清单一致，ISSUE-026候选参数精确匹配且空/失败拒绝。

## Acceptance

1. 按2026-09-14用户批准的方案A，取得可核验的历史查询能力及两股票/原非股票整段、非空边界证据；实际可查日期与上游承诺区分。精确历史起止及持续保留保证仍未知，不再作为本issue关闭前提。
2. 明细覆盖期限和费率差异；融资汇总实际基数有观察，保持所有原列/原业务键。
3. 真实运行、构建、清理、回归与独立最终复审通过；准确TASK输入交ISSUE-026。历史合同缺口若仍存在，必须取得用户具体决定才能据修订要求关闭，原未知不得改写为上游保证。

## Risks

官网未给历史保留起止及持续可访问承诺；固定股票明细可能稀疏。扩大固定窗只提供额外观察，不能自动补齐上游合同。SOURCE单片达到5000仍未确认；空样本不证明全部历史不存在。

## 历史支持采用决定（2026-09-14）

用户明确“同意方案 A（推荐）”，采用[具体方案及差异](../issues/proposals/ISSUE-024-historical-support.md#决策记录)。本决定将原“完整历史支持依据”要求限定为历史查询能力与代表性窗口；不再等待精确起止或持续保留保证，这些保证继续未知。不把实测日期当作API支持边界，不新增日期限制、不排除接口、不要求停业后继续产生新业务数据。

5000上界、达到/超过上界拆分及单日满额失败、原参数/业务键均保持。两轮39case及6项空不重标，准确33项TASK输入交ISSUE-026；生产NEEDS_VERIFICATION/v1及真实TASK/SQL要求保持。本节仅落实明确用户决定，不是新的上游事实或运行结果。

## 完成证据（2026-09-14）

方案A获明确批准后，三项验收均成立：39项来源覆盖原非股票及两股票整段/边界，33非空、6空保留；明细期限9/7、费率54/39及原键/汇总基数有真实观察；33项精确输入交ISSUE-026，15负例拒绝。决定后Node78/78、全部原对象/历史记录/其他接口/固定源码和包核对通过，独立复审无剩余发现。详见[最终验收](../verification/ISSUE-018-T14-runs.md#issue-024-方案a确认与最终验收)。本issue完成，未知历史保证和后续TASK/SQL边界保持。
