# ISSUE-025：十一接口提取规则与来源取证

## Goal

落实用户2026-09-14“完成issue25”的请求，为十一接口取得准确的提取规则、日期/事件/边界来源和ISSUE-026输入。沿用[T13](ISSUE-018-T13-design.md)、[T14](ISSUE-018-T14-design.md)已授权取证流程。用户随后明确“可以接受不完整”，按[已批准方案 A](../issues/proposals/ISSUE-025-extraction-contracts.md#决策记录)修订为响应采集合同与来源验收，上游完整性仍未知。

## Scope

仅`adj_factor,suspend_d,income,balancesheet,cashflow,fina_audit,express,repurchase,stk_managers,top10_holders,top10_floatholders`。保留原22轮/687case/751请求及其状态；其他29接口、40/34/6结构、生产策略/版本/列/键不变。SOURCE不写成TASK/SQL；UNKNOWN不因非空、窄窗相同或默认数量而解除。

## Approach

1. 复核十一官网静态文档及FAQ/HTTP/循环建议/频次/历史表/通用行情页面，保存URL、UTC和原始摘要。逐接口列出可核验事实和仍缺的截断/完成规则；repurchase无参数默认2000、top10名称、HTTP成功均不能成为范围请求的硬上限。没有完整性规则时先完成独立取证，再交付可 review 的具体采用方案；未获决定前不改变规则。
2. 最小扩展测试侧`TushareRangeSourceProbe.Projection`：balancesheet/cashflow/fina_audit/express复用income的同一行ann_date/end_date计数；top10两项复用fina_indicator的报告期计数。cashflow另用现有`DateComparison`输出ann/f_ann有效、不可比、不同、主轴在窗而副轴在窗外计数；不添加fields。stk_managers复用EventProjection比较上任/离任日期。suspend_d只计S、R、不可用类型及非空日内停牌时间行数，具体日期仍用原actualDates，不从样本推导全历史连续性。
3. 只在成功通过Envelope、主轴和股票校验后投影；有效+不可比较等于观察行数，空成功输出0，无成功/未执行不输出，失败状态保持。只输出整数/既有日期摘要，不保存人员姓名、原始行或完整业务键。SINGLE与其他接口不扩展。
4. 首轮固定：adj_factor两股票20251229～20260105；income/balancesheet/cashflow/fina_audit两股票20240101～20241231；express两股票20180101～20180701（官网示例窗）；top10两项两股票20170101～20171231（官网示例窗）；managers两股票20180101～20190630（官网公告示例）；repurchase无股票20260801～20260831（已有852行依据）。suspend_d两基准股票固定20000101～20251231作有限历史调查，另以官网000029.SZ/600310.SH的20200309～20200313观察连续停牌，不替代原代表股票要求。共23项原生SOURCE/23请求，30分钟/5000总上限不变。
5. 首轮后只依据其合法实际日期另登记整段收紧到首/末非空日、两端单日和端点外相邻日；新run/case身份全局唯一，保留所有旧空/失败，不循环换日求成功。若稀疏接口基准股票仍无数据，公开补证或样本调整决定另记，不宣称取证完成。
6. 在`docs/verification/ISSUE-018-T14-runs.md`预登记每轮精确params/来源/预算/私有清单摘要。0700目录/0600文件；复用私有wrapper、HTTPS、至少2000ms请求间隔、无自动重试、失败停轮。完整Git管理源码复制独立clone逐文件核对，隔离构建及离线回归通过后固定源码、两包、manifest/examples身份；每轮核对退出、清理、输入与秘密隔离。
7. 追加唯一JSON和运行登记，同步十一项当前缺口及40行报告，其他29接口/旧22轮对象不改写。非空且清洁的精确SOURCE仅作为条件TASK输入，UNKNOWN时消费者必须拒绝。最终独立复审；仅在所有关闭条件实际满足后完成issue，不能由测试通过代替上游合同或必要用户决定。

### 方案 A 已批准后的收尾（2026-09-14）

用户明确“可以接受不完整”，本轮只修订提案、T13/T14对应条款、本设计与issue关闭条件；十一项当前索引仅新增decisionRef并将待决码改为RESPONSE_ONLY_IMPLEMENTATION_PENDING，UNKNOWN规则、v1、SOURCE/TASK与生产状态不动。同步Node独立预期，验证带决定引用和真实非空SOURCE仍不能绕过现有UNKNOWN门禁。保留全部25轮822case886请求、其他29接口；87组精确参数交ISSUE-026，任务执行前必须落实独立语义。完成核对和独立复审后才记录关闭；本次不新增真实调用或重跑已经完成的Maven回归。

## Files

- 本设计、`docs/superpowers/plans/2026-09-14-issue-025.md`、T13限定取证说明：范围与步骤。
- `data-plane/tensor-plugin-tushare/src/test/java/com/akkc/tensor/plugin/tushare/batch/TushareRangeSourceProbe.java`及`TushareRangeSourceProbeTest.java`：安全计数及行为回归。
- `docs/verification/ISSUE-018-T14-official-evidence.md`、`ISSUE-018-T14-runs.md`、`ISSUE-018-range-acceptance.json`和`.md`：公开依据、唯一轮次及未决项。
- ISSUE-025问题、必要采用提案、ISSUE-026输入、issues索引和后续看板：真实进度及决定，新增文件Git纳管，不自动提交。
- `control-plane/e2e/tushare-range-evidence.test.js`：实际证据预期及拒绝提前开放检查。

## Tests

```sh
mvn -o -f data-plane/pom.xml -pl tensor-plugin-tushare -am -Dtest=TushareRangeSourceProbeTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -o -f data-plane/pom.xml -pl tensor-plugin-tushare -am -Dtest=TushareRangeSourceProbeTest,TushareBatchPoliciesTest,TushareBatchDownloadTest,TushareTradeCalendarTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -o -f data-plane/pom.xml -Pacceptance verify
node --test control-plane/e2e/tushare-range-evidence.test.js
git diff --check
```

普通命令环境排除账户/DB，用Java21/项目Node24。新增行为先RED缺计数再GREEN，覆盖不同/相同/非法/缺失副轴、报告期反向关系、S/R/非法类型、SINGLE/空/失败/未运行隔离；非法主轴不能被投影救活。新真实清单/原对象绑定、旧22轮及其他29接口保持、UNKNOWN任务拒绝与本地链接/差异核对均须通过。

## Acceptance

1. 按2026-09-14用户“可以接受不完整”的决定，十一项各有适用当前请求形状的 `RESPONSE_ONLY` 响应采集合同：逐项股票/日期轴、单次响应、校验/适配/入库成功及空/失败、页面说明、版本与历史证据规则均明确。此项替代原完整提取依据要求，UNKNOWN不改成上游保证；本issue只记录合同和决定，生产实现、能力/页面与真实TASK/SQL由ISSUE-026完成。
2. 两基准股票/原非股票的整段、有效两端、日期关系与停复牌代表场景有真实来源，空/失败如实保留。若调整代表股票，须明确决定。
3. 清洁运行、回归及最终复审通过；准确规则和匹配SOURCE交ISSUE-026承担TASK/SQL，不提前开放生产或关闭母issue。

## Risks

没有可核验的统一响应上界；用户已接受无法排除的漏数，有限取样仍不能证明没有截断。suspend_d两基准股票已有有效历史与连续五日样本，但实际首末日不是支持边界；cashflow本轮未观察到ann_date与f_ann_date不同的行，不能据此互换。管理层部分离任日期缺失。如后续改变采用方式或样本范围，仍须记录准确依据与必要决定。
