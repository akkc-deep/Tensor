# RANGE-T02 适用日历与来源核实设计

## Goal

交付 [区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md) `RANGE-T02` 的 19 项“接口—实际市场／业务—权威日历—覆盖和更新判据”证据，为 T03／T04／T06／T20 区分可采用来源与 `CALENDAR_UNCONFIRMED` 拒绝边界。研究完成不表示 19 项均已具备休市过滤能力。

## Scope

- 固定 PRD 附录 A.1 的 19 个交易日期接口，核实 C-A／C-M／C-S／C-N／C-X；不增减接口，不改既有股票／市场条件。
- 逐项确定必要市场或业务方向、可靠权威来源、开盘并集规则、日期覆盖、新鲜度、特殊休市修订、来源冲突和明确无法确认时的处理。
- 区分市场休市、业务不开放、个股停牌、合法空数据及来源停止更新；不以周末过滤或单一 SSE 代替实际日历。
- 不实现提供器、联网缓存、策略资源、HTTP／前端或数据库逻辑；不访问凭证或调用业务 API，不以真实下载尝试探测日历。公开日历和规则只读研究属于本任务，实际来源验证交 T20。
- ISSUE-008 的 9 项不恢复真实调用，其中本任务的 `hk_hold/hsgt_top10/moneyflow_hsgt/top_inst` 仅保留公开研究与目标登记；不关闭 ISSUE-008。

## Approach

按看板 Sources 顺序完整消费相关章节：① [PRD v1.4 §4及附录 A.1](../design/Tensor_区间下载_PRD_v1.0.md)；② [TRD v1.10 §5.1、§13.2](../design/Tensor_区间下载_TRD_v1.0.md)；③ [trade_cal.yaml](../../data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/trade_cal.yaml)；④ [ISSUE-008](../issues/problems/ISSUE-008-tushare-live-coverage-gap.md)。看板直接任务依赖为 None，T01 的完成不作为日历已核实证据。

### 1. 固定 19 项调查清单

| calendarProfile | apiName（逐项独立结论，不能合成一行验收） | 必须核实的范围问题 |
|---|---|---|
| C-A | adj_factor、block_trade、daily、daily_basic、margin_detail、moneyflow、monthly、stk_limit、suspend_d、top_inst、top_list、weekly | 从每个原接口的业务范围和证券类型查证必要市场；逐项判定 SSE／SZSE／BSE 是否适用，不能把“境内”直接固定成三个市场，也不能漏掉适用 B 股／基金。检查 margin_detail 市场描述与更新说明、moneyflow 沪深范围、stk_limit A/B股和基金等具体边界。 |
| C-M | margin | 根据原 `exchange_id` 的 SSE／SZSE／BSE 三个分支分别核实融资融券业务日历；仅使用所选市场，不能用另一交易所替换。 |
| C-S | slb_len、slb_sec、slb_sec_detail | 核实中国证券金融及相关交易所发布的转融资／转融券业务规则、实际交易日及停止／变更安排；只有存在正式等价依据才能复用交易所日历。 |
| C-N | hk_hold、hsgt_top10 | 逐接口核实实际沪／深股通或其他方向；从上交所、深交所、港交所的对应互联互通安排取来源。旧接口缺页不能借新接口或普通港股日历补结论。 |
| C-X | moneyflow_hsgt | 核实实际覆盖北向／南向及沪／深各业务方向；分别有来源，再取所需方向的开盘并集，不能由其中一个方向停市跳过全部业务。 |

公开研究从原接口页和相关交易所／业务发布者的官网日历、业务规则、年度安排及后续修订开始。原接口 URL 可从 [既有官方调研](../research/2026-09-08-tushare-range-batch-research.md) 定位；调研的旧事务讨论不消费。第三方转载只能作为寻找原文的线索，不能取代权威来源。精确 URL、发布／生效日期和摘录是本次研究要收集的结果，不能预填猜测链接。

### 2. 证据产物格式

创建 `docs/research/RANGE-T02-calendar-evidence.json`，顶层为 `taskId`、`reviewDate`、`sources`、`interfaces`，固定 `taskId=RANGE-T02`。JSON 仅用于研究交付，不作为生产配置。

`sources` 每项包含：`id`、`publisher`、`title`、`url`、`retrievedAt`、`publishedAt`、`effectiveRange`、`rawSha256`、`excerpt`、`status`。来源不可访问或日期未公开时，相关字段为 null 并在 `status/excerpt` 写明原因；不能伪造日期或摘要。已访问页面保存所消费的公开摘录及原内容 SHA-256 到 Git 内的 JSON，临时原件可另存 `/tmp/tensor-range-t02/`，不得只交付临时路径。`status` 为 `READABLE/UNAVAILABLE`，若有修订来源，以新 source id 引用并保存适用关系，不覆盖旧证据。

`interfaces` 恰 19 项，每项字段固定如下：

| 字段 | 内容与规则 |
|---|---|
| `apiName/calendarProfile` | 与上表精确相同 |
| `scopeEvidence` | 原接口实际市场／证券／业务覆盖结论及 source id；无法确认写明缺口，不能仅用名称猜范围 |
| `requiredCalendars` | 已有依据的必要市场／方向集合，每项含 `marketOrDirection`、`sourceIds`、`condition`、`equivalenceEvidence`；C-M 必须分别记录三个原 exchange_id 的分支结果；未核实集合不伪填为已确认 |
| `unresolvedScope` | 尚未解决的市场／方向及为什么影响下载日期；无缺口用空数组。`requiredCalendars=[]` 不表示全休市 |
| `unionRule` | 固定为“任一必要日历确认开盘即保留，仅全部确认休市才跳过”；必要集合未知或其中一份未知时不得计算部分并集 |
| `coverageRule` | 明确当前来源的市场身份、覆盖范围、每个候选自然日的开／闭市结论、重复一致去重、冲突／缺日／空来源／非法值处理及两端核对方式；不能只数记录行数 |
| `freshnessRule` | 明确本轮重新获取／确认、原文发布时间及生效期、后续修订核查方式；没有更新依据写明未知。无跨次缓存 TTL，不凭文件存在认定最新 |
| `status` | `DOCUMENTED`：适用集合、权威来源、覆盖与更新规则均有直接依据；`UNCONFIRMED`：任一必要证据缺失；`CONFLICT`：资料矛盾未解决。DOCUMENTED 只是静态文档依据，本轮运行仍须覆盖和新鲜度检查 |
| `rejection` | 所有项固定 `CALENDAR_UNCONFIRMED`，条件为本轮必要日历任一未确认；业务请求为零、首次不建失败项、已有失败项原样保留 |
| `liveExcluded/liveStatus` | 4 个本任务 ISSUE-008 项为 true；其余 false；全部 `liveStatus=NOT_RUN` |
| `remainingEvidence` | 对具体缺口列明证据要求；真实调用排除项不安排 T20 调用，其他项仅交 T20 按设计选择 |

创建 `docs/research/RANGE-T02-calendar-capabilities.md`，依次写“结论与证据边界”“19 项适用关系表”“按类别的来源与等价依据”“覆盖、新鲜度及拒绝规则”“待验证事实与排除”“AC 来源前提追踪”“验证结果”。逐项表必须展示 apiName、类别、必要集合／条件、source id／链接、覆盖／更新判据、状态和缺口；详细来源可引用 JSON，不能仅列类别和泛化规则。

### 3. 决策与失败规则

- `trade_cal` 本地允许 SSE／SZSE／BSE 只是本地合同。核对原官方输入表及样例，分别建立可用来源依据；缺少 BSE 依据不得用 SSE／SZSE 或 `margin` 对 BSE 的支持替代。未确认分支保留原枚举并返回未确认，不悄悄删除条件或换市场。
- C-A 的多市场并集先确认全部必要日历；C-M 仅所选市场；C-S 要正式业务等价来源；C-N／C-X 按实际业务方向而非地理市场简单相交或并集。年度节假日表还需证明平日／周末、特殊安排和修订规则足以覆盖每一日期，不能用周末经验补未知。
- 每次首次下载或重试在本轮内重新获取／确认，允许同轮复用，不跨次缓存、不要求用户先下载 trade_cal。重试仅处理实际明细日期；来源返回较宽范围时只消费待处理日期，不能产生范围外业务下载。
- 任一必要来源未知、空、缺日、身份错误、非法开闭值、冲突、获取失败或新鲜度未确认都返回 `CALENDAR_UNCONFIRMED`。已知局部日历不能驱动部分下载或删除部分失败项；全休市只有在所有必要来源完整确认后成立。
- 公告、月份、3 个原生范围及11个原条件不套屏障；业务下载 trade_cal 保留开／闭市记录。个股停牌不表示市场休市，来源停更不表示每个日期休市，weekly/monthly 没有新周期记录也不是市场休市。
- 未确认或冲突是本研究允许的结论，必须有精确缺口与拒绝条件；若需要改变产品范围或规则才能解决，停止该变更并按任务流程记录，不用设计猜测补齐。研究完成与可用性验收分开。

### 4. 执行与交接顺序

开始时读取本设计及看板所链交接，记录用户启动证据并 `READY -> IN_PROGRESS`。**实施第一步：** 按本设计第 1 节写入 19 个精确 apiName／calendarProfile 的 JSON 骨架，从 `trade_cal` 原官方输入表对 SSE／SZSE／BSE 的逐项依据核读开始，将原文和缺口写入 sources 及对应条件分支；随后逐项查证范围、权威来源和更新规则，不把空骨架作为完成结果。

输出实际资料与结论、执行下列校验、记录结果后完成 T02。随后才按 Order 选择 T03，消费 T01／T02 的直接输入，完成 T03 专属设计并回填，再写 T03 的 next-task 交接并准备 READY；本设计不提前设计 HTTP 合同字段。

## Files

| 文件 | 操作与责任 |
|---|---|
| `docs/task-designs/RANGE-T02-design.md` | 本研究合同；本轮后继准备只创建此设计，不执行研究 |
| `docs/research/RANGE-T02-calendar-evidence.json` | T02 执行时创建，保存逐项来源、适用集合、覆盖／更新规则及状态 |
| `docs/research/RANGE-T02-calendar-capabilities.md` | T02 执行时创建，19 项判断、具体证据缺口、AC 追踪和实际验证结果 |
| `docs/task-handoffs/tensor-range/tensor-range-task-board.md` | 链接设计与启动／完成证据；当前 T01 只准备 T02 READY |
| `docs/task-handoffs/tensor-range/RANGE-T02-handoff.md` | 本设计完成并链接后写入的 T02 入口交接 |

生产代码、YAML、共享需求和历史验收文件保持不在本研究修改范围；新建产物加入 Git，保留已有工作区修改。

## Tests

仅研究文档校验；不运行 Maven／前端测试或业务 API。T02 完成时在仓库根运行：

```sh
python3 -m json.tool docs/research/RANGE-T02-calendar-evidence.json > /dev/null
python3 - <<'PY'
import json
from collections import Counter
from pathlib import Path
e = json.loads(Path('docs/research/RANGE-T02-calendar-evidence.json').read_text())
expected = {
    'C-A': 'adj_factor block_trade daily daily_basic margin_detail moneyflow monthly stk_limit suspend_d top_inst top_list weekly'.split(),
    'C-M': ['margin'], 'C-S': 'slb_len slb_sec slb_sec_detail'.split(),
    'C-N': ['hk_hold', 'hsgt_top10'], 'C-X': ['moneyflow_hsgt']}
rows = e['interfaces']
assert e['taskId'] == 'RANGE-T02' and len(rows) == 19
assert {r['apiName']: r['calendarProfile'] for r in rows} == {a: k for k, v in expected.items() for a in v}
assert Counter(r['calendarProfile'] for r in rows) == {'C-A': 12, 'C-M': 1, 'C-S': 3, 'C-N': 2, 'C-X': 1}
source_ids = {s['id'] for s in e['sources']}
assert len(source_ids) == len(e['sources']) and source_ids
for r in rows:
    assert r['scopeEvidence'] and r['unionRule'] and r['coverageRule'] and r['freshnessRule']
    assert r['status'] in ['DOCUMENTED', 'UNCONFIRMED', 'CONFLICT']
    assert r['rejection'] == 'CALENDAR_UNCONFIRMED' and r['liveStatus'] == 'NOT_RUN'
    assert r['liveExcluded'] == (r['apiName'] in ['hk_hold', 'hsgt_top10', 'moneyflow_hsgt', 'top_inst'])
    if r['status'] == 'DOCUMENTED':
        assert r['requiredCalendars'] and not r['unresolvedScope']
    else:
        assert r['remainingEvidence']
    for c in r['requiredCalendars']:
        assert c['sourceIds'] and set(c['sourceIds']) <= source_ids
print('PASS: 19项/5类/来源引用/状态/排除边界')
PY
git diff --check
git diff --cached --check
```

人工复核并在报告逐项登记：19 项与 PRD 映射一致；margin 三个条件分支都有结论；每个 DOCUMENTED 项的范围、权威来源、覆盖及新鲜度均能追到原文；未确认集合不被算作休市；新建文件相对链接有效；所有实际消费的 READABLE 来源 SHA-256 与临时原文相等。特别检查 C-S 等价、C-N／C-X 方向、BSE 差异、来源停更及股票停牌，不以机械字段非空作为来源已核实证据。

为 T06 固定应验证的规则样例（本任务不运行功能测试）：两市场一开一闭保留；全部明确闭市跳过；一个未知即整轮零业务请求；缺日／冲突／过时／空来源均未确认；重试只含 3／7 日不能扩成 3～7；非交易日期接口不进日历屏障。来源适用性不能由这些合成例子证明。

## Acceptance

- 19 项逐行有实际范围、必要市场／业务方向、权威来源及原文、并集／覆盖／更新判据、核实状态、缺口和拒绝条件。
- 不能确认的项目明确返回 `CALENDAR_UNCONFIRMED` 且零业务请求；不把未确认算作过滤能力已通过。C-S 无等价证据不复用 C-A，C-N／C-X 不套普通香港日历。
- margin 三个现有 exchange_id 分支分别说明；BSE 支持不由本地枚举或其他接口推断；非交易日期和个股停牌边界清楚。
- AC-PRD-RANGE-02／15／16／17／18 分别映射到来源依据和 T06／T18／T20 的验证责任；研究状态与功能／真实验收分列。
- 结构检查及逐项证据人工复核有本轮结果；9 项 ISSUE-008 真实排除不变。新产物入 Git，看板完成与后继准备按既定先后执行。

## Risks

实际市场范围、业务日历等价、BSE 来源、互联互通方向与权威修订机制都是本任务的研究对象，当前不能预填为已确认。这些未知不改变研究的字段、流程或验收规则；有缺口时交付未确认及具体拒绝条件。若影响后续正式业务可用性，仍须后续证据关闭，不能仅登记风险后宣称 19 项休市过滤已经可用。
