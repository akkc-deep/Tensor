# ISSUE-019：三接口限量依据与独立来源取证

## Goal

明确 `daily / forecast / dividend` 的限量采用口径，补齐可追查的整段、边界、两股票及非交易日公告 SOURCE，向 ISSUE-026 提供匹配的 RANGE TASK / SQL 输入。

## Scope

沿用 [T13](ISSUE-018-T13-design.md)、[T14](ISSUE-018-T14-design.md) 的工具、隔离、身份、字段和失败合同。用户于 2026-09-13 要求“解决issue19”，授权继续调查和来源取证。仅处理这三个接口，不执行后续 issue，不改生产 Policy、业务键、YAML 或公共接口。

限量[方案A](../issues/proposals/ISSUE-019-documented-range-limits.md#决策记录)已于2026-09-13获用户明确“同意”。SOURCE此前按原严格合同独立完成；本次只将正式索引的三项完整性采用为ROW_LIMIT，补齐用户决定和官方证据引用。生产sourceVerified=false、v1、RANGE NEEDS_VERIFICATION保持；原SOURCE的UNKNOWN文字反映当时状态，不回填。

## Approach

1. **公开依据。** 复核已持久化的官方 doc 27 / 45 / 103；准确引文和原始摘要保留在原验收报告。新增公开公告引用、获取 UTC、字节摘要、日期语义及查询计划追加到 [T14 运行登记](../verification/ISSUE-018-T14-runs.md)。新浪为公开第三方公告记录，不冒充发行人原件或 Tushare 截断保证；官方 `forecast` 的 000005.SZ / 20190131 样例与新浪同股同日记录交叉核对。
2. **固定新计划。** 使用唯一 `issue019-source-<UTC>` runId；caseId 均加该前缀，并与现有 475 case 检查不重复。`daily` 两股票分别复用 20260803～20260810、两个单日端点及 20260805～20260810 重叠窗口。`forecast` 分别取有公开依据的 000001.SZ / 20160121、600000.SH / 20081014；另以官方样例 000005.SZ / 20190131 交叉验证普通单股票接口，不代替原两股票。每个公告取 D−1～D+1 整段、D～D+1（公告落在下边界）、D−1～D（公告落在上边界）及 D 单日。`dividend` 两股票分别取 20260814～20260816、三个单日及 20260815～20260816、20260814～20260815。000001.SZ 的 20260815 周六预案有公开记录；不预设第二股票同日必有公告。
3. **语义与预期。** daily 检查 trade_date 闭区间和股票，不要求停牌日有行；forecast 检查 ann_date，报告期不替代公告日，预告与快报不混用；dividend 始终枚举全部自然日，ann_date 指预案/决案公告，不能用实施公告日或除息日替代。公告边界窗口要包含真实公告，不仅验证空端点。预期非空仍以来源实际结果判断；若第二股票或历史样本为空，原样 EVIDENCE_MISSING，不能删掉、换日期重试或按另一股票成功推断通过。
4. **隔离执行。** 从当前工作树复制全部 Git 管理文件（包括已有暂存成果）至独立 clone，逐文件核对 SHA-256。构建依赖独立复制，重新执行 acceptance verify；构建环境移除真实账户和数据库变量。生产/验收包、源码全差异、manifest/examples 及私有清单指纹绑定该轮，运行前后保持稳定。只调用现有 `TushareRangeSourceProbe`，Token 从已授权环境进入该后端进程，无数据库连接或任务提交。目录0700、输入0600；固定 HTTPS、至少2000ms间隔、30分钟/5000次来源许可，失败停止且保留 NOT_RUN，不自动重试。
5. **结果归属。** 全部实际 case（包括空与失败）及 run 身份追加到唯一 `ISSUE-018-range-acceptance.json`；逐对象确认旧8轮/475 case不变。每接口案例引用和未决项按实际更新；SOURCE PASS只表示参数/字段/股票/日期观察有效，不单独改变完整性规则或RANGE准入。保留旧失败的历史身份，不能复制旧 PASS 作为新请求。
6. **实施已确认方案A。** 用户已于2026-09-13明确“同意”，记录其原话和日期，并修订 T13/T14 对仅这三项的采用口径：分别接受6000/3500/2000作为工程阈值；小于阈值按该口径处理，达到或超过阈值按现有原生区间拆分，单日仍满额失败；dividend 单日满额失败。先修改独立索引预期观察 RED，再更新索引完整性及依据，验证 GREEN。生产代码已有这些候选阈值，本 issue 不为来源取证开放它们。方案B保留为历史备选，不是当前采用口径。
7. **交付 ISSUE-026。** 只有当前规则口径和有效 SOURCE 均满足，才将各精确参数、SOURCE caseId、公告依据、候选策略版本要求交给 ISSUE-026。其 TASK 使用全新 caseId 和新库，验证真实持久化、重叠更新及 SQL；本 issue 不虚构 taskId、SQL 或提前开放 RANGE。未满足部分准确登记，不能关闭本 issue 或母 issue。

## Files

- 本设计、ISSUE-019 问题/方案、后续 issue 看板：范围、决策与执行事实。
- `docs/verification/ISSUE-018-T14-runs.md`、`ISSUE-018-range-acceptance.md` / `.json`：唯一新来源计划、实际结果及接口结论。
- 实施已确认方案A时修改 T13/T14 限量采用条款、`control-plane/e2e/tushare-range-evidence.test.js` 的独立规则预期；现有 helper、Probe、生产 Policy 仅在发现具体缺陷后最小修复。

## Tests

来源轮次的构建与实际取证从隔离源码执行。决定后仅改证据索引和测试预期的离线回归可在当前工作树执行，不改写已有隔离源码或构建身份；两阶段均使用Java21/项目Node24，普通命令不带账户/DB环境：

```sh
mvn -f data-plane/pom.xml -Pacceptance verify
node --test control-plane/e2e/tushare-range-evidence.test.js
mvn -f data-plane/pom.xml -pl tensor-plugin-tushare -am -Dtest=TushareBatchPoliciesTest,TushareBatchDownloadTest,TushareRangeSourceProbeTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期退出0、无失败；确认34 RANGE候选/6 SINGLE_ONLY、11无规则项、现有4 AVAILABLE及三项仍关闭。构建用于 SOURCE 身份，不宣称执行了 T14 的六条完整门禁。实际来源命令：

```sh
TENSOR_TUSHARE_LIVE_E2E=1 mvn -f data-plane/pom.xml -pl tensor-plugin-tushare -am -Dtest=TushareRangeSourceProbe -Dsurefire.failIfNoSpecifiedTests=false test
```

仅在安全清单、隔离包身份和离线校验成立后执行；通过现有受限 wrapper保存实际退出码、请求数和清理事实。SOURCE 的空样本不会令命令一定失败，须单独读取每个case状态。追加索引后运行Node证据校验、旧run逐对象对比和 `git diff --check`。所有新文件显式 `git add`，不提交或发布。

## Acceptance

- 限量采用决定与官方事实分别记录，不能把用户接受工程口径写成上游追加保证。
- 两股票整段、边界、daily重叠及非交易日分红 SOURCE 有真实结果和安全清洁身份；forecast需要有公告依据的非空证据，空/失败仍保留。
- 索引新旧身份、状态和清理符合共享合同；没有新 TASK / SQL 时对应值为null。
- 规则与SOURCE齐全后提供 ISSUE-026 匹配输入；未决项仍然阻止本 issue 完成。生产开放由匹配任务验收决定。

## Risks

历史预告可能不在当前账户范围；第三方公告分类可能混入快报，选定银行样本分别有区间预期/明确同比预测，但仍须检查真实普通 forecast。第二股票在平安银行公告窗口为空只说明该固定窗口无有效事件，不能证明该股票分红 RANGE 已闭环。公开公告只用于选样和语义交叉核对，不证明全部来源历史完整。

## 2026-09-13 来源补证登记

首轮真实运行完成后，浦发银行同周末窗口为空。取得东方财富明确的PLAN_NOTICE_DATE=20260331及与新浪相符的实施日期后，按上文失败/空样本保留合同，在T14运行登记中先固定独立6case补证轮：20260330～20260401整段、三个单日、20260331～20260401与20260330～20260331两种公告边界窗口。复用已核验且源码未变的隔离构建；不改首轮或生产规则。本次取样时尚未取得限量决定；后续用户明确同意方案A，见上文Scope。
