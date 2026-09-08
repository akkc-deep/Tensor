# RANGE-T02 适用日历核实结论

研究日期：2026-09-08。任务：[RANGE-T02 看板](../task-handoffs/tensor-range/tensor-range-task-board.md)，执行合同：[专属设计](../task-designs/RANGE-T02-design.md)。逐项原文、URL、读取时间和 SHA-256 见 [证据 JSON](RANGE-T02-calendar-evidence.json)。

## 结论与证据边界

**19 项已逐项形成研究结论：1 项 DOCUMENTED、16 项 UNCONFIRMED、2 项 CONFLICT；没有执行功能或真实业务验收。** DOCUMENTED 为 `hsgt_top10` 的静态适用关系，且该接口属于 ISSUE-008 真实调用排除项；本次不将其计为账号可执行或休市过滤已可用。其余任一必要证据未关闭前必须返回 `CALENDAR_UNCONFIRMED`。

共保存 40 个来源记录，包含 20 个原接口页面（19 项及 trade_cal）和 20 个权威日历／规则／访问缺口。检索仅用作导航，结论取自原发布者；Tushare页面在本轮重新获取。原页未公开发布时间的字段为 null，读取时间不冒充发布时间。错误或空内容响应保留响应摘要，用于复核访问结果，不作为有效日历。临时原件在 `/tmp/tensor-range-t02/`，可清理；Git 内的摘录和摘要为持久证据。

本次确定的重点：

- `trade_cal` 输入及输出明确 SSE／SZSE，未列 BSE；本地 BSE 枚举和 margin 的 BSE 支持均不能补足该缺口。
- `moneyflow` 明确沪深 A 股；`stk_limit` 明确 A/B股和基金，且输出交易所 SSE／SZSE／BSE。其他仅给股票、A股或历史样例的接口，不能据样例推断完整市场集合。
- `margin_detail` 的“沪深两市”与同页“北交所周五数据”说明冲突；`monthly` 的最后交易日说明与周末月末样例冲突，均须澄清。
- 中证金融第21条提供有条件的业务交易日依据，仍缺所指交易所集合及差异日期处理；转融券2024年暂停通知不是普通休市日历。
- `hsgt_top10` 明确北向沪股通及深股通。港交所公开2026年度北向／南向表，不能混用普通港股日历；hk_hold、moneyflow_hsgt 原页缺失，实际方向仍未知。

## 19 项适用关系表

“已证实部分”只表示原文样例至少出现这些市场，不能作为完整集合驱动请求。每行的覆盖／更新规则与下一节通用规则共同适用；完整文字见 JSON 同名行。

| apiName | 类别 | 必要集合／条件 | 原接口与日历依据 | 覆盖／更新判据及具体缺口 | 状态 |
|---|---|---|---|---|---|
| `adj_factor` | C-A | 已证实部分：SZSE；完整集合未确认 | [TS-adj_factor](https://tushare.pro/document/2?doc_id=28)；TS-trade_cal、SZSE-calendar | SSE／BSE及A/B股等证券覆盖未逐项声明；“全部股票”不足以冻结完整必要集合。 全部必要市场逐日覆盖、权威修订及trade_cal更新依据确认后才可启用。 | UNCONFIRMED |
| `block_trade` | C-A | 已证实部分：SSE；完整集合未确认 | [TS-block_trade](https://tushare.pro/document/2?doc_id=161)；TS-trade_cal、SSE-2026-notice、SSE-holiday-notices | SZSE／BSE、B股或基金等是否覆盖未由原页完整说明。 全部必要市场逐日覆盖、权威修订及trade_cal更新依据确认后才可启用。 | UNCONFIRMED |
| `daily` | C-A | 已证实部分：SZSE；完整集合未确认 | [TS-daily](https://tushare.pro/document/2?doc_id=27)；TS-trade_cal、SZSE-calendar | SSE／BSE完整市场集合仍需原接口覆盖依据；不得借 stock_basic 的枚举证明 daily。 全部必要市场逐日覆盖、权威修订及trade_cal更新依据确认后才可启用。 | UNCONFIRMED |
| `daily_basic` | C-A | 已证实部分：SSE、SZSE；完整集合未确认 | [TS-daily_basic](https://tushare.pro/document/2?doc_id=32)；TS-trade_cal、SSE-2026-notice、SSE-holiday-notices、SZSE-calendar | 是否覆盖 BSE、B股等未清楚限定；样例仅证明已出现的市场，不证明穷尽。 全部必要市场逐日覆盖、权威修订及trade_cal更新依据确认后才可启用。 | UNCONFIRMED |
| `margin_detail` | C-A | 已证实部分：SSE、SZSE；完整集合未确认 | [TS-margin_detail](https://tushare.pro/document/2?doc_id=59)；TS-trade_cal、SSE-2026-notice、SSE-holiday-notices、SZSE-calendar | BSE范围存在同页矛盾，无法确定应为沪深并集还是三市场并集；不能按说明任取其一。 全部必要市场逐日覆盖、权威修订及trade_cal更新依据确认后才可启用。 | CONFLICT |
| `moneyflow` | C-A | SSE、SZSE | [TS-moneyflow](https://tushare.pro/document/2?doc_id=170)；TS-trade_cal、SSE-2026-notice、SSE-holiday-notices、SZSE-calendar | SSE/SZSE集合明确；逐日核对trade_cal与各交易所原安排及修订；SZSE页面未取得完整逐日结果，trade_cal修订依据未确认。 | UNCONFIRMED |
| `monthly` | C-A | 已证实部分：SZSE；完整集合未确认 | [TS-monthly](https://tushare.pro/document/2?doc_id=145)；TS-trade_cal、SZSE-calendar | 完整A股市场集合未枚举；样例月末非交易日与输入语义不一致，须先确认日历过滤会否漏掉真实月线。 全部必要市场逐日覆盖、权威修订及trade_cal更新依据确认后才可启用。 | CONFLICT |
| `stk_limit` | C-A | SSE、SZSE、BSE | [TS-stk_limit](https://tushare.pro/document/2?doc_id=183)；TS-trade_cal、SSE-2026-notice、SSE-holiday-notices、SZSE-calendar、BSE-home | 三个市场分别逐日核对；BSE官网本轮403且trade_cal未列BSE；不能用沪深替代；各证券类别仍保留。 | UNCONFIRMED |
| `suspend_d` | C-A | 已证实部分：SSE、SZSE；完整集合未确认 | [TS-suspend_d](https://tushare.pro/document/2?doc_id=214)；TS-trade_cal、SSE-2026-notice、SSE-holiday-notices、SZSE-calendar | BSE及证券类型覆盖未穷尽；连续停牌日期与市场休市必须分开。 全部必要市场逐日覆盖、权威修订及trade_cal更新依据确认后才可启用。 | UNCONFIRMED |
| `top_inst` | C-A | 已证实部分：SZSE；完整集合未确认 | [TS-top_inst](https://tushare.pro/document/2?doc_id=107)；TS-trade_cal、SZSE-calendar | SSE／BSE及证券类型适用范围未明确；真实调用排除。 全部必要市场逐日覆盖、权威修订及trade_cal更新依据确认后才可启用。 | UNCONFIRMED |
| `top_list` | C-A | 已证实部分：SZSE；完整集合未确认 | [TS-top_list](https://tushare.pro/document/2?doc_id=106)；TS-trade_cal、SZSE-calendar | SSE／BSE及证券类型范围未完整声明；没有上榜股票不表示休市。 全部必要市场逐日覆盖、权威修订及trade_cal更新依据确认后才可启用。 | UNCONFIRMED |
| `weekly` | C-A | 已证实部分：SZSE；完整集合未确认 | [TS-weekly](https://tushare.pro/document/2?doc_id=144)；TS-trade_cal、SZSE-calendar | SSE／BSE市场集合未穷尽；开盘日不一定有新周线记录。 全部必要市场逐日覆盖、权威修订及trade_cal更新依据确认后才可启用。 | UNCONFIRMED |
| `margin` | C-M | exchange_id=SSE／SZSE／BSE 三分支；每次仅所选市场 | [TS-margin](https://tushare.pro/document/2?doc_id=58)；TS-trade_cal、SSE-2026-notice、SSE-holiday-notices、SZSE-calendar、BSE-home | SSE有年度／后续安排，SZSE未取得逐日结果，BSE来源未确认；三个分支均需融资融券业务适用及修订依据，周五延后发布不是周五休市。 | UNCONFIRMED |
| `slb_len` | C-S | CSF_REFINANCING | [TS-slb_len](https://tushare.pro/document/2?doc_id=331)；CSF-business-rules、CSF-rules、CSF-announcements、CSF-faq | 第21条“证券交易所”对应的必要市场集合及差异日期处理未在所读条款中明示，暂无完整逐业务日历。 逐轮核对第21条及第85/92条调整公告。含再借／偿还，不只竞价日。 | UNCONFIRMED |
| `slb_sec` | C-S | CSF_SECURITIES_REFINANCING | [TS-slb_sec](https://tushare.pro/document/2?doc_id=332)；CSF-business-rules、CSF-rules、CSF-announcements、CSF-suspension | 第21条“证券交易所”对应的必要市场集合及差异日期处理未在所读条款中明示，暂无完整逐业务日历。 逐轮核对第21条及第85/92条调整公告。2024-07-11暂停与存量归还需分开，历史规则与停更待证。 | UNCONFIRMED |
| `slb_sec_detail` | C-S | CSF_SECURITIES_REFINANCING | [TS-slb_sec_detail](https://tushare.pro/document/2?doc_id=333)；CSF-business-rules、CSF-rules、CSF-announcements、CSF-suspension | 第21条“证券交易所”对应的必要市场集合及差异日期处理未在所读条款中明示，暂无完整逐业务日历。 逐轮核对第21条及第85/92条调整公告。2024-07-11暂停与存量归还需分开，历史规则与停更待证。 | UNCONFIRMED |
| `hk_hold` | C-N | 未知集合，不是空日历 | [TS-hk_hold](https://tushare.pro/document/2?doc_id=188)；原页404，方向未确认 | 沪股通／深股通／南向港股通各方向是否实际覆盖未确认；持股披露日期、业务开放日期及披露制度变更的关系待证。 | UNCONFIRMED |
| `hsgt_top10` | C-N | NORTHBOUND_SH、NORTHBOUND_SZ | [TS-hsgt_top10](https://tushare.pro/document/2?doc_id=48)；HKEX-connect-calendar、HKEX-2026-csv、HKEX-2026-pdf、HKEX-trading-hours、HKEX-connect-faq、HKEX-enhancement、HKEX-weather、SZSE-connect-rules-pdf | 2026表的北向栏+北向周一至周五规则覆盖自然日；每轮重取对应年表并核查业务及天气修订。其他年份需其当时有效安排；真实排除。 | DOCUMENTED |
| `moneyflow_hsgt` | C-X | 未知集合，不是空日历 | [TS-moneyflow_hsgt](https://tushare.pro/document/2?doc_id=47)；原页404，方向未确认 | 沪股通／深股通／南向港股通各方向是否实际覆盖未确认；沪深北向与沪深南向是否同时覆盖及历史变化待证。 | UNCONFIRMED |

## 按类别的来源与等价依据

### C-A／C-M：市场身份先于并集

`TS-trade_cal` 原参数表列 SSE、SZSE、CFFEX、SHFE、CZCE、DCE、INE；输出说明为 SSE／SZSE，未列 BSE。本地 `trade_cal.yaml` 的 `[SSE,SZSE,BSE]` 是待保持的合同，不是来源可用证据。`margin` 原页另行明确三市场，两个合同不能相互替代。

SSE 年度通知 `SSE-2026-notice` 于2025-12-22发布，明确2026节假日、调休周末休市及复市日期；2026-06-11端午节公告 `SSE-dragon-notice` 明确引用年度通知，证明执行时还要核对后续公告。年度表本身不替代所有自然日和特殊停市检查，也不能覆盖其他年份。SZSE 官网日历 HTML 是动态空容器，本轮未取得有效逐日数据；所引用脚本的读取也未补足。BSE 官网本轮403。以上为本轮访问和证据范围，不能推导“来源不存在”。

C-M 三分支固定如下：

| 原条件 | 已有依据 | 启用前仍须关闭 |
|---|---|---|
| SSE | margin原页支持；trade_cal原页有市场身份；SSE年度及后续休市公告 | 融资融券业务适用关系、实际任务日期逐日覆盖、trade_cal与权威修订一致性 |
| SZSE | margin／trade_cal原页明确该市场 | 融资融券适用关系、可获取的完整权威日历、逐日覆盖及更新确认 |
| BSE | margin原页明确该市场；本地原枚举保留 | BSE独立权威日历及修订、合法读取方式；trade_cal未列BSE，不能推测支持 |

个股停牌不改变市场日历；daily明确停牌期间不提供数据。weekly的周期产出不意味着每个开盘日都有新周线。monthly的样例包含2018-09-30（周日）及2018-06-30（周六），与输入“每月最后一个交易日”存在未解决差异；不能先套日历后宣称月线日期已可靠。

### C-S：转融通业务规则与暂停分开

中证金融 [第21条正式规则](http://www.csf.com.cn/publish/main/1040/1045/20230707103811731229229/1688697506450.pdf)（PDF第5—6页）：“本公司开展转融通业务的交易日为每周一至周五，国家法定节假日和证券交易所公告的休市日除外”；同条允许经证监会同意调整交易日期／时间并公告。第82—85条（第18—19页）规定逐日披露及重大事件公告，第92条规定异常业务暂停。因此可以研究有条件复用，但“证券交易所”未逐名穷举，未证明交易所开盘并集与转融通业务等价；不得直接固化SSE或C-A并集。

转融资包括竞价、再借、偿还及余额，CSF-faq明示每交易日再借和上一交易日信息披露。不能将竞价日当作slb_len整个业务日历。

[2024-07-10暂停通知](http://www.csf.com.cn/publish/main/1009/1010/20240710172038468505945/index.html)自2024-07-11暂停转融券，存量合约不晚于2024-09-30归还。规则版本、历史业务日期、暂停期间存量及数据留存须分别确认；不能将两个原接口导航“停”、API空响应或暂停通知直接变成无限期全闭市日历。转融资不由此通知自动停用。

### C-N／C-X：按业务方向消费正式日历

`hsgt_top10` 原说明明确沪股通／深股通，market_type为1／3；本地不提供方向筛选，因此必要集合是两条北向业务。港交所 [2026日历](https://www.hkex.com.hk/-/media/HKEX-Market/Mutual-Market/Stock-Connect/Reference-Materials/Trading-Hour,-Trading-and-Settlement-Calendar/2026-Calendar_csv_e.csv)把香港、沪深市场、北向及南向分栏；[北向交易时间](https://www.hkex.com.hk/Services/Trading-hours-and-Severe-Weather-Arrangements/Trading-Hours?sc_lang=en)明确周一至周五（排除非北向交易日）。深交所正式办法第32条将深股通交易日发布归于联交所证券交易服务公司，第84条将南向发布和特殊调整归于本所服务公司。

2026表261个工作日都有一行，普通交易栏留白，休市为Closed，南向2026-12-24／31为Half Day；此年度表须结合正式交易时间和规则解读，不能推广成任意来源空值等于开盘。2026-04-03／07内地开盘而北向Closed，说明普通A股日历不能替代互联互通。Half Day为开盘。完整必要方向确认后取开盘并集；年度中南北同闭的样例不证明方向永久等价。

港交所FAQ §1.2明确2023-04-24起交易日历优化，旧假日前资金结算限制不能套到之后；天气安排自2024-09-23变化，不能一律按台风自动休市。每轮须取对应年度表并核查相应方向公告、制度生效区间及特殊修订。静态DOCUMENTED只确认hsgt_top10适用关系及有权威确认路径，不意味着本轮日期已确认，也不开放其真实调用。

`hk_hold` 与 `moneyflow_hsgt` 原页为404。前者实际持股方向及披露制度、后者北向／南向和沪／深覆盖尚不能确认。HKEX业务日历和SSE／SZSE港股通安排只是方向明确后的候选，不能借新API、接口名称或本地列名补上方向。两个接口的requiredCalendars为空表示未知，必须拒绝。

## 覆盖、新鲜度及拒绝规则

1. 输入先按既有规则校验；仅19个交易日期接口进入日历屏障。先冻结接口实际市场或业务方向，再取得全部必要来源。C-M只选用户的exchange_id；不是三个市场一起查询。
2. 对实际待处理自然日逐一核对市场身份、历史生效范围及开闭值，包含首次起止两端；重复一致可去重，冲突、缺日、非法值、空来源均未确认。不能以记录条数等于天数代替日期集合检查。
3. 每轮重新获取或重新确认来源及更新／修订，仅同轮复用；不跨次缓存、不要求预先下载trade_cal。无法确认修订有效性即失败，不用固定TTL或读取时刻代替来源更新依据。
4. 全部必要来源完整确认后，任一开盘即保留，全部明确休市才跳过；局部日历已知不能驱动局部业务下载。
5. 任一必要来源或适用关系未确认均为 `CALENDAR_UNCONFIRMED`：本轮业务请求与业务入库均为零；首次不建失败任务，重试已有明细原样保留，不能提前删除已知部分。
6. 重试只处理现存失败日期（例如3／7日）；可读较宽日历但仅消费3／7日，不能扩成3～7日或原始整段范围。
7. 非交易日期类别不进屏障；原生trade_cal业务下载保留开／闭市行。停牌、未产出新周期、合法空业务结果、停止更新均不等于市场休市。

## 待验证事实与排除

每项 remainingEvidence 已在JSON独立登记。未排除的15项只向T20提供候选待验证事实，最终接口、年份、权限和执行方法仍由T20专属设计选择；本研究未预订真实调用。尚缺范围或日历依据时须先补齐来源，不能用下载尝试猜市场。

本任务4项真实排除为 `hk_hold/hsgt_top10/moneyflow_hsgt/top_inst`。全项目ISSUE-008九项仍为“不依赖，未解决，用户后续单独处理”；不关闭问题，也不恢复真实请求。全部19项liveStatus=NOT_RUN。

T06应验证：两市场一开一闭保留；全部明确闭市跳过；任一未知时整轮零业务请求；缺日／冲突／过时／空来源拒绝；3／7日精确重试；非交易日期类别不进屏障。T18验证业务请求、业务表及失败记录实际效果。合成例子不证明来源真实适用性。

## AC 来源前提追踪

| AC | 本次来源前提 | 后续结果证据 |
|---|---|---|
| AC-PRD-RANGE-02 | 19行必要集合、条件与并集边界；每行精确缺口 | T06规划／日历、T18受控行为、T20来源实测 |
| AC-PRD-RANGE-15 | 全部日历确认后才允许“全休市”；SSE及HKEX休市资料 | T06／T18全休市零业务请求、零记录计数 |
| AC-PRD-RANGE-16 | 逐市场／方向完整确认，不能部分并集 | T06／T18混合日历及跨市场情形 |
| AC-PRD-RANGE-17 | 缺失／冲突／不可用来源及CALENDAR_UNCONFIRMED规则 | T06／T18首次不建任务、重试明细保留；T20选定来源 |
| AC-PRD-RANGE-18 | 原接口边界、C-S条件等价、互联互通方向及非交易日期排除 | T04策略／T06日历、T18受控覆盖、T20选定适用性 |

以上仅来源前提可追踪，不登记五项AC功能验收已通过。

## 验证结果

2026-09-08 本轮执行：

- `python3 -m json.tool docs/research/RANGE-T02-calendar-evidence.json > /dev/null`：退出0。
- 完整执行专属设计 Tests 中的Python校验：`PASS: 19项/5类/来源引用/状态/排除边界`；分类12／1／3／2／1，4项真实排除，19项NOT_RUN。
- 补充Python校验：40份原内容SHA-256一致、固定字段及scope引用、报告恰19行、margin三分支、HKEX年表261个不重复工作日且与2026周一至周五集合相等、标识集合、monthly两个样例确为周末、报告／设计／入口交接相对链接均PASS。
- `git diff --check`、`git diff --cached --check`：均退出0。
- 逐项人工复核：12个C-A原页分别记录完整集合或已证实部分；margin三个选项保留且各有缺口；CSF第21条及暂停通知分开；hsgt_top10方向、2026覆盖规则与修订依据均可追原文；两个404接口没有伪填方向；DOCUMENTED只有hsgt_top10，仍排除真实调用。已查看CSF第5页和HKEX年表PDF，并核读规则相关页文字。

以上仅为研究产物验证。未运行Maven／前端功能测试，未读取凭证、未调用业务API、未产生新功能验收通过记录。
