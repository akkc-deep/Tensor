# Tensor 数据完整性检验规则

版本：1.1；核对日期：2026-09-17；适用范围：当前 40 个 Tushare 接口。

第一次阅读可以先看[逐项详解与例子](data-integrity-explained.md)，其中解释了每个规则编号及 40 个接口的判断方法。

本文同时说明首版本地检查和更广的验收规范。当前已实现指定股票/日期/接口的本地检查、后台任务、持久报告、问题定位及再次检查；只读扫描完整业务键，执行必填字段、业务键与来源身份通用规则。Tushare 的40项口径与日/周/月候选缺口已接入；缺少可靠生产全集时覆盖结论仍是 UNKNOWN，预期数与覆盖率为null。入口与运行约束见[运行配置](configuration.md#本地数据完整性检查)。

在线源端全量对账、定时巡检、自动补数、财务勾稽及行情合理性规则尚未实现。下文对应规范和建议频率不表示这些能力已经自动执行，也不代表实际生产数据库通过检查。本地检查按已保存的规则版本解释，结果证据见[T13验收记录](../verification/DATA-INTEGRITY-T13.md)。

例如可靠预期20个键，本地20行中只有19个命中、另有1个额外键，覆盖率是19/20=95%，缺失和额外分别报告。可靠空集显示已验证空、比例null；依据不可靠显示UNKNOWN；N/A只表示不适用。任务COMPLETED不等于数据PASS。acceptance fixture的可靠窗口及版本2/3仅为合成验收数据，不能用于承诺Tushare生产覆盖率。

## 1. 检查目标与边界

每次检验必须回答四个问题：本次来源响应是否正确入库？目标范围是否采集完？本地是否与可信来源一致？字段是否满足数据合同和业务规则？四项分别给出结论。

“完整”必须限定为：**在固定检查时点、证券范围、日期口径、来源可提供范围和规则版本下，应有记录全部存在。** 不存在脱离范围和证据的“全库完整”。

### 1.1 固定检查口径

检查开始前保存以下信息，缺少决定性信息时将对应结论记为 `UNKNOWN`：

| 项目 | 必须记录的内容 |
| --- | --- |
| 身份 | 检查 ID、开始/结束时间、应用版本、数据集定义版本或哈希、检查规则版本 |
| 接口 | pluginId、apiName、SINGLE/RANGE、请求参数及隐式默认参数 |
| 范围 | 证券清单及其来源/版本、交易所、日期闭区间、日期字段、其他分类条件 |
| 日期 | 区分 trade_date、ann_date、end_date、cal_date、ipo_date；业务日期使用对应市场口径，采集时间单独记录时区 |
| 时点 | 数据截至何时、来源发布时间、允许延迟、源端响应取得时间、本地读取快照时间 |
| 证据 | taskId、批次、任务保存的策略、来源响应或其可追溯归档、本地查询条件与结果摘要 |
| 比较方式 | 业务键、字段列表、单位、复权方式、币种、版本保留方式、允许的规范化与业务公式误差 |

全市场检查需要先固定证券全集，包括目标历史中已退市或暂停上市的证券。不能从行情表中已有的证券反推全集，也不能用当前在市名单代替历史名单。当前 `stock_basic` 表没有保存 `list_status`、`delist_date`，且下载要求单只股票；仅靠这张表不足以构造完整的历史上市区间，应另取可靠名单及上市状态证据。

固定目标后不得为了通过而删除失败证券、日期、接口或字段。范围调整须形成新版本，同时保留原范围的失败与未知。

### 1.2 统一结果词汇

本地完整性报告使用以下结论；API中的不适用值为 `NOT_APPLICABLE`，页面显示 `N/A`。这些数据结论与下载及检查任务的执行状态分别保存。

| 结果 | 判定 |
| --- | --- |
| `PASS` | 在声明范围内，证据充分且适用规则全部满足 |
| `FAIL` | 有直接证据证明违反规则，例如缺业务键、错值、混股、写入计数不符 |
| `WARN` | 存在待解释异常，例如合理性检查异常、历史行数突变；尚不足以证明数据错误 |
| `UNKNOWN` | 未检查、无法执行、依据不足、参考数据不全、源端版本不可比或源端完整性未知 |
| `N/A` | 规则确实不适用，并写明原因；不能用来表示失败或未执行 |

总览同时保留各结果数量；若需要单一状态，按 `FAIL > UNKNOWN > WARN > PASS` 聚合，`N/A` 不参与。全部 `N/A` 不得汇总为 `PASS`。存在确定漏数同时又缺其他证据时，总览 `FAIL`，仍列出所有未知项。

## 2. 通用硬规则

除明确标注外，规则违反为 `FAIL`；缺少检验依据或未执行为 `UNKNOWN`。业务推断只能产生疑似缺失，确认缺失需要充分的预期记录证据。

### 2.1 范围、任务与计划

| 编号 | 检查规则 |
| --- | --- |
| S01 | 请求参数、来源响应和本地取数使用相同证券、交易所、日期轴及分类口径；目标范围内不能遗漏整只证券或整个分区。 |
| S02 | 校验任务保存的 definition_hash、policy_snapshot 及参数；历史任务按当时保存的策略解释，不能用当前能力替换旧证据。 |
| S03 | HTTP 202 仅代表已受理；任务结束后核对 SUCCEEDED/PARTIAL_FAILED/FAILED/INTERRUPTED 与实际批次。运行中或未计划完成不得判为已完成。 |
| S04 | 原生日期区间：最终叶子闭区间并集等于目标区间，同一任务内叶子不重叠；拆分子区间精确覆盖父区间。跨任务有意重叠允许，但统计时按业务键去重。 |
| S05 | 按自然日规划：叶子日期集合等于区间内所有自然日。按交易日规划：先证明指定日历完整，再要求叶子集合等于开市日集合。 |
| S06 | 最终叶子全部 SUCCEEDED，无 PENDING/RUNNING/FAILED；SPLIT 是父节点，不能作为成功叶子重复统计。已验证全休市、预期叶子集合为空时可以完成，必须保存日历证据。 |
| S07 | TASK_LIMIT_EXCEEDED、执行中断、失败、超时和限流不得转为空响应；成功兄弟批次不能掩盖未完成范围。 |
| S08 | 返回记录的作用域及过滤日期必须符合请求；只检查用于过滤的日期字段，不能要求同一行其他业务日期也落在该窗口内。 |

### 2.2 原始响应与提取完整性

| 编号 | 检查规则 |
| --- | --- |
| E01 | 来源请求成功且响应结构合法；fields 与定义中申请字段及顺序一致，行宽一致，声明行数等于实际原始行数。错误、截断解析、权限不足均不能当作空数据。 |
| E02 | 对 CONFIRMED_ROW_LIMIT，使用去重、过滤和转换前的原始行数 N：N < L 才满足当前阈值规则；N >= L 必须继续合法拆分，不能用入库行数代替 N。 |
| E03 | 原生区间可拆时继续按日期拆；最小单日仍满额，或逐日接口当日满额，判提取完整性 UNKNOWN，并保留 BATCH_COMPLETENESS_UNCONFIRMED 失败。不得猜测新增分页、改参数或丢弃满额父批来宣称成功。 |
| E04 | trade_cal 要求请求内每个自然日恰好一条、交易所匹配、日期不越界、is_open 为 0/1。完整休市日集合有效；非空区间的空日历不是完整日历。 |
| E05 | RESPONSE_ONLY 无完整性保证；单响应非空、为空、多次重复一致、区间拆小后数量一致，都不能单独证明全范围完整。UNKNOWN 规则也不得产生完整结论。 |
| E06 | SINGLE 的成功证明该次响应完成处理；除非另有独立完整提取依据，不证明该证券全部历史或全市场完整。 |
| E07 | 规则证据应适用于相同接口、参数形状、权限和时点。默认返回量、样例行数或其他接口的上限不能作为本接口上限。规则出现反证时撤回相应结论，保留旧证据。 |

工程阈值与上游保证分开记录。当前若干阈值来自已采用的工程口径，特别是 daily/forecast/dividend、fina_mainbz 和历史 slb 接口；满足阈值可记为“按当前提取策略覆盖”，不能据此宣称所有上游历史记录绝无遗漏。

### 2.3 字段、业务键与规范化

| 编号 | 检查规则 |
| --- | --- |
| D01 | 所有数据列按对应 YAML 的 logicalType、nullable、length、precision、scale、allowedValues 检查；不能将允许为空的字段统一改成必填。 |
| D02 | 非空字段、COMPOSITE 业务键各字段必须有值；FINGERPRINT 输入字段可以按定义为空，但生成的 business_key 必须存在且可重算。 |
| D03 | STRING 按当前转换规则 trim，空串转 null，长度按 Unicode 码点计算；TEXT 保留原字符串。对账应使用相同规则，不能统一清除内部空格或大小写。 |
| D04 | DATE 为合法日期，MONTH 为合法年月；LONG 为可精确表示的有符号 64 位整数；DECIMAL 使用精确十进制并满足定义精度，禁止通过四舍五入或二进制浮点误差“修复”不合合同的数据。 |
| D05 | 同一响应的完全相同行可去重并记录数量；同业务键但内容不同的行必须报告冲突，不能任意取第一条/最后一条。当前适配器对这种冲突整批失败。 |
| D06 | 最终表按定义业务键唯一，检查源端不同业务事件是否被错误合并。数据库主键无重复只证明存储唯一，不能证明业务键设计没有漏维度。 |
| D07 | FINGERPRINT 使用当前 FingerprintKeyCodec，在转换后的值上按定义字段顺序计算；保留 null 标记、字节长度及值，不能使用简单字符串拼接或任意 JSON 哈希替代。 |
| D08 | source_plugin/source_api 与目标表匹配，ingested_at 可追溯；采集时间更新只说明发生写入，不说明业务日期最新或历史齐全。 |

### 2.4 入库与计数

对单个成功批次，记原始行数为 R、规范化后唯一业务键数为 U、完全重复行数为 D、插入数为 I、更新数为 M：

```text
无业务键冲突时：R = U + D
成功提交时：  U = I + M
因此：        R >= I + M
```

| 编号 | 检查规则 |
| --- | --- |
| P01 | 与原始响应/适配结果核对上式。只有数据库计数时，可以检查 R >= I + M，但不能把差额未经核实全部认定为合法重复。 |
| P02 | 批次写入和 SUCCEEDED 标记在同一事务提交；异常回滚该批次新增/更新及成功标记，不影响已成功兄弟批次。历史原子性需要事务/故障证据，单次最终 SELECT 不能证明。 |
| P03 | 按任务汇总时只累计成功叶子的 source_rows/inserted_rows/updated_rows；SPLIT 和未成功批次计数应为零。请求次数包含重试和规划请求，不应等于叶子数。 |
| P04 | 表当前行数与请求/写入操作数分开比较。只有排除其他写入、删除和范围变化，并使用固定快照时，表总净增行数才可与期间插入操作数对应。 |
| P05 | 相同来源响应重放后业务键集合及业务字段不变，新增数为零；允许 updatedRows 和 ingested_at 变化。源端已修订时应按版本规则更新，不能要求内容永远不变。 |
| P06 | 通过 records 接口核验时读取全部页，使用稳定排序和一致数据时点；首屏、pageSize、页面截图或估算表行数不能替代全量集合。 |

## 3. 预期集合与源端对账

### 3.1 先证明预期集合可靠

预期业务键集合 E 可以来自完整且适用的源端导出、具有已验证终止条件的提取方式、权威事件清单，或具备充分业务依据的日历/证券集合。记录其来源、版本和构建规则。

- 日历推导必须考虑上市/退市、生效区间、全天停牌、来源服务范围及发布时间。部分时段停牌不等于全天没有日线。
- 资金流向、估值、两融、复权因子等不能直接套用日线的证券覆盖与停牌规则；必须有本接口适用依据。
- 周/月线不能要求每个自然周/月都有一条，也不能固定为周五/月末自然日；只验证已发布周期及来源采用的实际交易日期规则。
- 财务与事件数据不能要求每日一条、每季度必有一条或前十大股东每期恰好十条。报告类型、公告版本、上市期限、披露义务和源端收录范围都影响记录数。
- 参考表不完整、生命周期未知或源端历史保留边界未知时，输出疑似缺口和 UNKNOWN；不能据此直接给出缺失率。

### 3.2 集合与字段比较

在相同范围和可比版本下，记本地业务键集合为 A：

```text
缺失键 = E - A
额外键 = A - E
共同键 = E ∩ A
覆盖率 = |E ∩ A| / |E|       （仅 E 已证实完整且 |E| > 0 时计算）
```

1. 缺失键非空：范围完整性 FAIL，输出具体证券、日期、键及来源证据。
2. 额外键非空：核实本地保留历史版本、源端修订/撤回或范围错配；确认不该存在则 FAIL，无法解释则 UNKNOWN。保留合法历史不属于错误。
3. 共同键：比较全部纳入检查的业务字段；排除 ingested_at 等采集元数据。相同源端快照经合法规范化后应精确一致，差异为 FAIL；源端先后版本不可比时先标 UNKNOWN 并补证。
4. 业务公式中的舍入误差按字段单位和来源精度单独规定；公式容差不能用于掩盖源端到本地的传输精度损失。
5. E 已可靠证明为空，且 A 为空：记录“已验证空集合”，coverage_rate 为 null，不做 0/0。E 是否完整未知时，两边均为空也为 UNKNOWN。
6. 原始响应哈希只证明归档未改变；全量规范化、稳定排序后的键/内容哈希可辅助快速比较，但需保留数量和差异定位能力。相同的不完整响应也会产生相同哈希。

### 3.3 结论强度

报告应同时提供以下四项，不强行合并成一个“成功”：

| 结论项 | PASS 条件 |
| --- | --- |
| 响应入库一致 | 原始响应规范化后的全部唯一键、业务字段与其对应提交结果一致，计数可解释 |
| 计划与提取策略覆盖 | 目标请求集合完整执行，并满足任务保存的适用完整性规则；工程阈值限制须明示 |
| 目标范围完整 | 独立或充分验证的预期集合 E 可靠，全部目标分区核验，无缺失、无未解释额外键；仅有 RESPONSE_ONLY 或工程阈值不足以证明此项 |
| 数据质量与时效 | 适用字段、业务及发布时间规则均通过；源端截止时间/发布延迟不明时，时效单列 UNKNOWN |

抽样通过只能给出“样本通过”。要给整个范围的结论，检查必须覆盖声明范围；不同来源的抽样一致也不能代替完整全集证据。

## 4. 当前 40 个接口的专用规则

下列能力来自 2026-09-15 源码：30 AVAILABLE = 21 阈值规则 + 1 日历规则 + 8 RESPONSE_ONLY；4 NEEDS_VERIFICATION；6 UNSUPPORTED（仅限制 RANGE）。实际部署以对应版本能力接口为准。

表中业务键列完整列出当前键。`F(...)` 表示对括号字段使用 FingerprintKeyCodec；其余为 COMPOSITE。RANGE 日期轴来自批量策略，不可直接套用 SINGLE 的 queryMode。

### 4.1 已开放：21 个阈值接口

除明确写出逐日外均为原生区间；原始行数达到阈值后的处理遵循 E02/E03。

| 接口 | RANGE 过滤字段 | 阈值 L | 业务键 | 专用检查 |
| --- | --- | ---: | --- | --- |
| `daily` | trade_date | 6000 | ts_code, trade_date | 按适用交易日、上市区间及全天停牌证据找疑似缺口；前收盘价不能一律等同前条收盘价。 |
| `weekly` | trade_date | 6000 | ts_code, trade_date | 核实实际每周最后交易日及发布口径，不能固定周五；只对完整可比周期做日线聚合比较。 |
| `monthly` | trade_date | 4500 | ts_code, trade_date | 核实实际每月最后交易日及发布口径，不能固定自然月末。 |
| `daily_basic` | trade_date | 6000 | ts_code, trade_date | 按本接口证券/停牌覆盖规则核对；亏损情况下 PE 为空或为负不能自动判错。 |
| `stk_limit` | trade_date | 5800 | trade_date, ts_code | 检查上下限关系；考虑无涨跌幅限制时期，不能用固定 10%/20% 推导全部记录。 |
| `moneyflow` | trade_date | 6000 | ts_code, trade_date | 买卖量、金额按字段含义检查非负；净流量可负；分档合计须先确认单位与统计口径。 |
| `margin` | trade_date | 4000 | trade_date, exchange_id | 按请求 exchange_id 核验汇总；SSE/SZSE/BSE 各自核验，不用股票数量推导行数。 |
| `margin_detail` | trade_date | 6000 | trade_date, ts_code | 仅对当时适用的两融标的及来源覆盖生成预期；普通股票缺记录不直接算漏数。 |
| `block_trade` | trade_date | 1000 | trade_date, ts_code, buyer, seller, price, vol | 稀疏事件；同股同日多笔合法，保留买卖方、价格、数量维度。 |
| `slb_len` | trade_date | 5000 | trade_date, ob | 保留 ob 维度；转融券暂停日期不能作为该融资接口的历史截止日。 |
| `slb_sec` | trade_date | 5000 | trade_date, ts_code | 历史查询按实际证据解释；空响应和业务暂停均不能证明 API 最后可查日。 |
| `slb_sec_detail` | trade_date | 5000 | trade_date, ts_code, tenor, fee_rate | 保留同日不同期限、费率；不能按股票日期去重；不假定每日都有明细。 |
| `new_share` | ipo_date | 2000 | ts_code | RANGE 按上网发行日期 ipo_date；issue_date 是上市日期，不能互换。 |
| `forecast` | ann_date | 3500 | ts_code, end_date, ann_date, type | 公告事件可在非交易日；同报告期不同公告日期和类型分别保留。 |
| `stk_holdernumber` | ann_date | 3000 | ts_code, end_date, ann_date | 区间按公告日，报告期用于键与分组；不要求日更或每期固定一条。 |
| `stk_holdertrade` | ann_date | 3000 | ts_code, ann_date, holder_name, in_de, change_vol | 公告日与持股变动时点分开；股东、增减方向及变动量是键的一部分。 |
| `pledge_detail` | ann_date | 1000 | F(ts_code, ann_date, holder_name, pledge_amount, start_date, end_date, is_release, release_date, pledgor, holding_amount, pledged_amount, p_total_ratio, h_total_ratio, is_buyback) | 过滤公告日，不是质押起止日；全业务字段参与指纹，内容变化可能生成新键，不能据此宣称保留了源端事件 ID。 |
| `fina_mainbz` | end_date | 100 | ts_code, end_date, bz_item, curr_type | 保持当前省略 type/period/ann_date 的请求口径；按业务项目、币种保留记录，不能暗中只取某分类。 |
| `top_list` | trade_date（逐交易日） | 10000 | trade_date, ts_code, reason | 没上榜可以无记录；同日多原因合法。SH/SZ 用对应日历，BJ 当前参照 SSE 且证券仍保留 BJ，记录此日历假设。 |
| `dividend` | ann_date（逐自然日） | 2000 | F(ts_code, end_date, ann_date, div_proc) | 非交易日公告也请求；方案阶段、公告版本必须保留，不能按股票报告期合并。 |
| `disclosure_date` | ann_date（逐自然日） | 6000 | ts_code, end_date | ann_date 表示最新披露公告日；同键的新公告/计划更新原行，旧公告日查询行数可能减少，应按键追踪修订。 |

### 4.2 已开放：1 个日历接口

| 接口 | RANGE 过滤字段 | 业务键 | 专用检查 |
| --- | --- | --- | --- |
| `trade_cal` | cal_date | exchange, cal_date | 执行 E04；SSE/SZSE 分别覆盖全部自然日。当前 RANGE 对直接请求 BSE 拒绝，不能将其记为已验证；BJ top_list 的 SSE 参考口径不等于存在 BSE 日历。 |

附加检查：pretrade_date 若有值，应早于 cal_date；若其落在已验证日历覆盖范围内，应指向符合来源语义的最近前序开市日。需要区间前驱日期时扩展参考日历，不能因前驱不在本次窗口就判为漏数。该附加关系属于巡检规则，不冒充当前 E04 已自动实现的检查。

### 4.3 已开放：8 个仅采集响应的接口

这八项可以检验字段与入库一致性。未补齐可靠全集依据时，目标范围完整性必须为 UNKNOWN。

| 接口 | RANGE 过滤字段 | 业务键 | 专用检查 |
| --- | --- | --- | --- |
| `adj_factor` | trade_date | ts_code, trade_date | 因子应按源端适用规则检查；长期相同合法，不能只保留发生变化的日期；与行情对齐前先证明各自覆盖规则。 |
| `suspend_d` | trade_date | ts_code, trade_date | 事件表，无停复牌事件可为空；区分全天、盘中停牌和复牌，不能把出现一条事件当作应删除当日日线。 |
| `income` | ann_date | ts_code, end_date, report_type, ann_date | 分报告类型及公告版本；不把单季、累计、合并及母公司口径混为一体。 |
| `fina_audit` | ann_date | ts_code, end_date, ann_date | 按实际审计披露义务和来源记录核对，不要求每个季度都有审计报告。 |
| `express` | ann_date | ts_code, end_date, ann_date | 业绩快报为披露事件，不要求每只股票每期都发布。 |
| `stk_managers` | ann_date | F(ts_code, ann_date, name, gender, lev, title, birthday, begin_date) | 同人不同职位/任期合法；end_date 不在键内，其修订可更新原行；未知任期不能猜补。 |
| `top10_holders` | end_date | ts_code, end_date, holder_name, ann_date | RANGE 按报告期，SINGLE 按公告日；保留公告版本，不强制每期恰好 10 行。 |
| `top10_floatholders` | end_date | ts_code, end_date, holder_name, ann_date | 与前十大股东采用不同持股口径；不能要求两表集合或比例相等。 |

### 4.4 当前撤回 RANGE：4 个接口

四项现为 NEEDS_VERIFICATION，RANGE 提交被拒绝；SINGLE 入口保留不等于所有真实请求已通过验证。下表日期是候选策略的定义，不能用于宣称已开放。重新纳入时需解决原始问题并完成对应验证。

| 接口 | 候选 RANGE 过滤字段 | 业务键 | 专用检查 |
| --- | --- | --- | --- |
| `balancesheet` | ann_date | ts_code, end_date, report_type, ann_date | 检查原适配失败/键冲突；会计关系必须使用同报告口径与版本，不能改键或删行以通过。 |
| `cashflow` | ann_date | ts_code, end_date, report_type, ann_date | ann_date 与 f_ann_date 不混用；现金流量、期末余额关系需同币种、单位及期间。 |
| `repurchase` | ann_date | ts_code, ann_date, proc | 多证券公告请求，不能假定单股票范围；同键冲突须保留调查，无参数默认 2000 不作为 RANGE 完整性依据。 |
| `fina_indicator` | end_date | ts_code, end_date, ann_date | 报告期与公告日分开；原适配问题尚未解决，候选 100 阈值不能视为当前有效完整性保证。 |

### 4.5 仅支持 SINGLE：6 个接口

这些接口 RANGE 能力为 UNSUPPORTED。核验当前响应及已定义快照范围；没有历史快照/完整提取依据时，历史完整性为 UNKNOWN。快照型查询可以返回多期记录，snapshot 标签不证明只含当前一条，也不保证全部历史。

| 接口 | 当前请求范围 | 业务键 | 专用检查 |
| --- | --- | --- | --- |
| `stock_basic` | ts_code + list_status | ts_code | L/P/D 状态参数与来源一致；本地没有完整上市状态历史，历史全集见 1.1。 |
| `stock_company` | ts_code + exchange | ts_code | 证券与交易所匹配；公司信息覆盖不能直接等同所有证券品种覆盖。 |
| `index_classify` | 无参数，来源默认范围 | index_code | 核对 src、level、parent_code 的分类版本；无参数响应不自动证明全部分类体系/版本。 |
| `index_member_all` | ts_code | l1_code, l2_code, l3_code, ts_code, in_date | 单只股票范围；保留分类层级和加入日期，核实退出日期及版本，不能宣称下载了所有指数成分。 |
| `pledge_stat` | ts_code | ts_code, end_date | 按统计日期核对；不得要求每日一条，明细汇总对账必须同口径同截止时间。 |
| `stk_rewards` | ts_code | ts_code, ann_date, end_date, name | 多人员、多公告和报告期分别保留；未知薪酬/持股数允许按字段定义为空。 |

## 5. 业务合理性规则与正常例外

下列规则默认用于发现 WARN；只有确认适用的业务合同、单位、精度和前置数据均成立后，违反才升级为 FAIL。规则依赖字段为空时记录 N/A 或 UNKNOWN（取决于是否应有该字段），不能用零代替。

| 编号 | 规则 | 前置条件及例外 |
| --- | --- | --- |
| B01 | 行情 low <= high，low <= open/close <= high，价格和成交量/额非负 | 全部使用同一行同一复权口径；字段为空不自动补零。 |
| B02 | change 与 close - pre_close、pct_chg 与 change / pre_close × 100 一致 | pre_close 非零；使用源端精度推导容差。除权除息会影响前收盘口径，不能简单对比上一行 close。 |
| B03 | 周/月 OHLC 与同期日线首开、最高、最低、末收一致；量额与日线合计一致 | 日线已完整，周期已按来源规则发布，复权方式、单位与时间窗口相同；不足条件为 UNKNOWN。 |
| B04 | down_limit <= up_limit；行情是否在有效价格限制内 | 已确认该证券当日确有对应价格限制；新股、特殊交易机制等不能套统一涨跌幅。 |
| B05 | 资产 = 负债 + 含少数股东权益；现金期末余额与期初余额、净变动衔接 | 同版本、期间、报表类型、币种、单位且所需字段齐全；不要用缺少行业专属项目的部分字段强凑合计。 |
| B06 | 资金净流量与买卖流量、两融余额与分项等关系一致 | 逐接口核实分项是否穷尽及统计口径；净利润、净现金流、净流量、增长率允许为负。 |
| B07 | 数量/金额/比例满足字段业务域；方向与增减持变化相符 | 不能统一规定所有比例在 0～100：周转率等可超 100；百分数与小数、股与手、元与万元分别确认。 |
| B08 | 生效起止、任期、加入退出日期关系合理 | 日期字段确属同一事件生命周期，空结束日可表示仍有效；预计日期可以在未来。 |
| B09 | 证券、行业、公司、日历引用存在且版本匹配 | 参考表已证实覆盖目标历史；参考表缺失时先报 UNKNOWN，不能直接断言业务记录非法。 |
| B10 | 同分区行数、空值率、数值分布或最新业务日期出现异常变化 | 用历史可比窗口作告警基线，不能用“较昨天减少 20%”等经验阈值直接证明漏数；已验证小缺口也不能因低于阈值被忽略。 |

每个公式需要预先记录“比较字段、单位换算、适用类型、容差及依据”；未定义时不执行猜测公式。对同源响应到数据库的逐字段对账，规范化后误差容忍为零。

## 6. 时效、修订与历史保留

| 编号 | 检查规则 |
| --- | --- |
| T01 | 时效使用业务日期/来源发布时间，与预先声明的采集 SLA 比较；不能仅检查 ingested_at 新不新。SLA 未定义或发布时点未知时，时效 UNKNOWN。 |
| T02 | 尚未发布的当日行情、未完成周/月周期、未到披露时点的报告，不纳入已到期预期集合；显式记录检查截止线。 |
| T03 | 同键非键字段修订按当前 upsert 更新；键包含公告版本的保留多版本；指纹键包含变更字段的可能新增键。报告需按各自规则解释数量变化。 |
| T04 | 当前表以业务键 upsert，未提供每次写入的完整历史快照；不能仅靠当前表证明“某时点已知全部信息”或回测无未来数据。此类验收需要另有时点快照/归档。 |
| T05 | 源端下一次响应缺少旧键，不自动推断应删除本地数据；先判定源端返回是否完整、是否撤回、是否版本更新。异常修复须保留证据。 |
| T06 | 历史接口的起止和持续可访问保证若未知，继续列为未知；不能把首条观测日期、最后非空样本或业务停业日期当作已证实的服务边界。 |

建议执行频率（这是运维起点，不是 Tushare 发布承诺）：

- 每批提交后：响应结构、作用域、键冲突、计数和入库一致性。
- 每个任务结束后：计划覆盖、所有叶子、阈值、失败与中断、该任务涉及范围的集合检查。
- 每日：在各接口约定发布时间加采集 SLA 后检查已到期数据；日频数据可先回查最近 5 个已完成交易日并按观测修订时延调整窗口。
- 每周：复核疑似缺口、持续 UNKNOWN、异常空响应，以及财务/事件已知修订；短窗口不能替代较早公告的复查。
- 每月及重大变更后：按证券与时间分区完成一次声明范围的全量对账。若受来源限制仅抽样，明确记录未覆盖范围并保留 UNKNOWN。
- 数据迁移、适配器/业务键/策略升级后：检查受影响历史分区、重复提交和失败回滚；数据库主键变更尤其需要检查是否合并了原事件。

## 7. 报告、验收与修复闭环

### 7.1 每条检查结果

以下为更广检查规范的报告字段建议；当前本地完整性报告的实际字段与三表合同以[公开schema](../contracts/integrity-check.schema.json)为准：

```text
audit_id, checked_at, scope_id, api_name, task_id, batch_id
definition_version, task_policy_version, audit_rule_version
rule_id, status, evidence_kind, expected_basis
expected_count, actual_count, missing_count, extra_count
conflict_count, field_mismatch_count, exact_duplicate_count
coverage_rate, affected_keys, evidence_reference, explanation
```

无法确定的计数填 null 并解释，不能填 0。大规模缺失清单可单独归档，报告给出总数和少量样例。记录原始响应时保存数据、参数与版本，不将 Token 等认证材料写入报告。

### 7.2 验收门槛

要签署“指定范围已验证完整且数据可用”，必须同时满足：

1. 检查范围、截止时间、定义与策略版本固定，全部证券和分区被检查。
2. 提取依据与预期集合可靠；对所有声明目标，没有完整性 UNKNOWN 或未执行项。
3. 确认缺失键、未解释额外键、业务键冲突、源端到本地字段差异、硬规则违规全部为 0。
4. 批次、计划、入库计数可解释，所有到期目标完成；未到期范围明确单列。
5. 时效及适用业务规则通过。WARN 必须给出证据化的处置；未解释 WARN 不得写“全部规则通过”。

仅达到工程阈值、任务完成或响应一致时，分别签署对应较弱结论；不把“按当前策略覆盖”改写成“全源历史齐全”。检查比例必须覆盖声明范围的 100%，不能以全库平均覆盖率掩盖某一证券或日期缺失。

### 7.3 修复顺序

1. 保存异常业务键、原始响应、任务/规则版本和读取时点。
2. 区分采集未完成、适配失败、业务键冲突、持久化错误、参考数据不足、源端修订或源端范围未知。
3. 对未完成范围按现有合同 retry/resume；提交当前 expectedVersion，遇 TASK_DEFINITION_CHANGED 先核实定义差异。
4. 成功但已确认漏数的范围，按原作用域定向重取并对账；不能期望 retry 自动发现成功批次里的未知漏数。
5. 源端完整性不足时补充分区/全集证据或继续 UNKNOWN；不能通过降低阈值、删除失败记录、填零或猜补行情制造通过。
6. 修复后重跑失败规则及受影响依赖规则，保留修复前后结果；参考日历或证券全集修复后，重算所有依赖它的缺失检查。

## 8. 两个判定示例

**daily 缺口：** 目标为某股票一月的已发布日线。可靠证券生命周期、日历和停牌证据确定 20 个预期键，本地 19 个。即使任务 SUCCEEDED、来源返回 19 < 6000，目标范围仍 FAIL，缺失 1，覆盖率 95%。若停牌证据不全，只能先给出疑似缺口与 UNKNOWN，不能先认定预期必为 20。

**income 响应采集：** 任务 SUCCEEDED，原始响应 8 行、8 个唯一键，本地 8 键且字段一致。响应入库一致 PASS；因当前规则为 RESPONSE_ONLY，且没有完整报告清单，提取完整性与目标范围完整性 UNKNOWN。不能写“财报完整率 100%”。

## 9. 规则依据

- [当前 40 项接口清单](../data-template/manifest.json)：仅用接口成员清单，历史样例参数和行数不是当前全集基线。
- [运行配置与任务计数语义](configuration.md)。
- [当前 RANGE 最终验收与能力边界](../verification/ISSUE-032-range-final-closure.md)。
- [daily/forecast/dividend 工程阈值决定](../issues/proposals/ISSUE-019-documented-range-limits.md)。
- [fina_mainbz 默认分类与阈值](../issues/proposals/ISSUE-020-fina-mainbz-default-type.md)。
- [slb 历史支持范围](../issues/proposals/ISSUE-024-historical-support.md)。
- [仅采集响应的合同](../issues/proposals/ISSUE-025-extraction-contracts.md)。
- [四接口 RANGE 排除范围](../issues/proposals/ISSUE-026-range-scope.md)。
- [40 个数据集 YAML](../../data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/)：字段、键、SINGLE 参数的依据。
- [批量策略](../../data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/batch/TushareBatchPolicies.java)、[日历验证](../../data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/batch/TushareTradeCalendar.java)。
- [字段转换](../../data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/ValueConverter.java)、[适配与去重](../../data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/GenericDatasetAdapter.java)、[指纹键](../../data-plane/tensor-core/src/main/java/com/akkc/tensor/core/adapter/FingerprintKeyCodec.java)、[写入计数](../../data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/WriteCounts.java)。
