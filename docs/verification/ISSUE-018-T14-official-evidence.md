# ISSUE-018-T14 公开依据补证

本次公开调查没有取得足以解除 11 项 UNKNOWN 或 `daily / forecast / dividend` 含糊截断合同的新规则。取得的主要新增依据是证监会转融券暂停/存量了结日期、可交叉核验的 monthly 历史判别样本，以及 `fina_mainbz.bz_code` 的官方分类定义。它们为固定新 SOURCE 样本提供依据，不构成 SOURCE、TASK、SQL 或接口开放结果。

调查于 2026-09-13 UTC 进行。完整读取 [T14 设计](../task-designs/ISSUE-018-T14-design.md)、[T13 设计](../task-designs/ISSUE-018-T13-design.md)、[T13 官方复核](../../.superpowers/sdd/2026-09-12-issue-018-t13/official-review.md)、[T13 候选评估](../../.superpowers/sdd/2026-09-12-issue-018-t13/task-4-candidate-assessment.md)。仅访问公开文档、搜索引擎及监管机构网页；未调用 `api.tushare.pro`、未读取账户/数据库环境、未运行 Probe、未修改生产代码、验收索引或看板。

## 1. 转融券：业务暂停日期已经有监管原文

证监会 [《证监会依法批准暂停转融券业务 进一步强化融券逆周期调节》](https://www.csrc.gov.cn/csrc/c100028/c7493852/content.shtml)，页面标注发布日 **2024-07-10**，本次获取 **2026-09-13T09:30:54.039596Z**。准确引文：

> 证监会依法批准中证金融公司暂停转融券业务的申请，自2024年7月11日起实施。存量转融券合约可以展期，但不得晚于9月30日了结。

由此可以明确 **2024-07-11 是业务暂停生效日，2024-09-30 是该通知规定的存量合约最迟了结日**。不能将 7 月 10 日、7 月 11 日或 9 月 30 日直接写成 Tushare 的 API 停用日、最后返回日期或全部可调用历史的终点；存量处理与新业务暂停也不能合成同一日期。

[slb_sec](https://tushare.pro/document/2?doc_id=332) 与 [slb_sec_detail](https://tushare.pro/document/2?doc_id=333) 当前侧栏仍分别标“转融券交易汇总(停）”“转融券交易明细(停）”，正文均为“单次最大可以提取5000行数据，可循环获取所有历史”。两页均展示 **20240620 / 000001.SZ**：汇总样例该股 `lent_qnt=1.43`，明细样例 `tenor=14, fee_rate=2.20, lent_qnt=1.43`。这是公开示例事实，并非本轮账户观察。

可供 root 登记的具体新样本依据：两接口分别请求 `ts_code=000001.SZ,start_date=20240620,end_date=20240620`，在另外的固定轮次核验日期、股票、明细业务键与历史可用性。该单日处于监管暂停之前，且两页直接列出目标股，因此具有取样依据；但单日成功不能证明全部历史完整，也不能代替 T13 两股票、整段及端点要求。页面没有展示 600000.SH 的该日结果，不能预填其非空。

**剩余硬缺口：** Tushare 没有公开两 API 的确切停用时间、历史保留起止及现账户可访问性。监管通知只能补业务背景，实际历史范围仍须 SOURCE；继续保留待验证。

## 2. monthly：跨官方页面确认冲突，形成可区分的历史样本

[monthly](https://tushare.pro/document/2?doc_id=145) 的输入说明仍为“交易日期 （每月最后一个交易日日期，YYYYMMDD格式）”，同页样例仍将 000001.SZ 的 2018 年 9 月数据标为 `20180930`。该日为周日。

新增交叉依据来自官方 [调取 pro 版数据](https://tushare.pro/document/1?doc_id=40)，获取 **2026-09-13T09:28:33.834176Z**。其 `trade_cal` 输出表的逐字行是：

```text
10         SSE       20180930        0      20180928
```

对应列为 `exchange, cal_date, is_open, pretrade_date`。因此 **monthly 样例日期是官方日历样例中的休市日，前一交易日为 20180928**。这比只用本地星期计算更直接，但仍不能证明当前月线 API 总是用自然月末或已经修正了旧样例。

可供登记的判别组为 `000001.SZ` 的 `20180901～20180930` 整段，以及 `20180928`、`20180930` 两个独立单日；另以 `trade_cal(exchange=SSE,start_date=20180928,end_date=20180930)` 核对完整日历。它们有公开历史依据，且能区分最后交易日与自然月末；实际返回日期、空结果和过滤边界均须如实记录，不预设哪一日必须 PASS。此组不自动替代设计要求的另一股票及一般端点样本。

另查 [周/月线行情（每日更新）](https://tushare.pro/document/2?doc_id=336)，该页属于另一个 API，不能将其日期语义移植为当前 `monthly` 合同。**L=4500 仍是明确“单次最大”声明；日期冲突仍未裁决。**

## 3. fina_mainbz：分类字段有明确定义，默认集合和 100/150 仍未解释

当前 [HTML](https://tushare.pro/document/2?doc_id=81) 和官网 [静态 Markdown](https://tushare.pro/wctapi/documents/81.md) 一致：

- 输入 `type` 为可选；准确引文：“类型：P-按产品 D-按地区 I-按行业（请输入大写字母）”。没有省略 `type` 时的默认值或默认集合。
- 输出存在 `bz_code`；准确引文：“主营业务来源类型（P-按产品 D-按地区 I-按行业）”。这是能够标识实际分类的公开字段；不能仅凭 `bz_item` 的自然语言名称推断类别。
- 限量准确引文：“单次最大提取100行，总量不限制，可循环获取。”该句没有限定只适用于 RANGE，也没有写 `ts_code` 快照可返回 150，或依 type/积分使用另一阈值。
- 唯一普通接口示例显式传 `type='P'`；它不能证明不传时也默认 P。VIP 示例也不能解释本项目普通接口的未传 type 行为。

T13 候选评估记录的两股票 SINGLE 各 150 行是历史运行事实，本调查未重查或改写账户证据。**现公开合同不能解释该 150 与 100 的关系，不能直接把 150 改成新 L，也不能因为范围样本 74<100 就忽略矛盾。**

后续若要检查默认分类，应在设计允许的测试侧投影中核对真实返回 `bz_code` 的合法值及各类计数，并仍需可核验的默认集合说明。只观察几个类别只能证明所选样本出现这些类别，不能证明所有类别都已完整返回。是否需要增加投影由 root 按设计门禁处理；本调查不请求 `fields/type`，不改 YAML、业务键或生产返回列，不混 P/D/I。

## 4. 11 项 UNKNOWN：HTML 与官网静态 Markdown 交叉复核

下表每行均重新获取接口 HTML（`fina_audit` 使用 curl 补取）及同编号静态 Markdown。静态文档入口来自官网 Skills 页面链接的公开归档，仅作为文档读取，未安装、执行或采纳其中操作指令。HTML/Markdown 没有给出能够替换 UNKNOWN 的截断或完整提取规则。

| API / 文档 | 本次准确引文或明确字段语义 | 仍无法证明之处 |
| --- | --- | --- |
| [adj_factor / 28](https://tushare.pro/document/2?doc_id=28) | “可提取单只股票全部历史复权因子，也可以提取单日全部股票的复权因子” | 有历史提取能力声明，没有单次上限或可验证的未截断标识；不能解释为无限响应。 |
| [suspend_d / 214](https://tushare.pro/document/2?doc_id=214) | “按日期方式获取股票每日停复牌信息” | 有区间与连续日期语义，没有响应上限/完整提取规则；空结果不能补规则。 |
| [income / 33](https://tushare.pro/document/2?doc_id=33) | “当前接口只能按单只股票获取其历史数据”；输入为“公告日开始日期”“公告日结束日期” | 普通单股票的公告区间有依据，但没有明确截断合同。 |
| [balancesheet / 36](https://tushare.pro/document/2?doc_id=36) | “当前接口只能按单只股票获取其历史数据” | 公告区间、报告期独立存在；没有单次最大或完整提取规则。 |
| [cashflow / 44](https://tushare.pro/document/2?doc_id=44) | “当前接口只能按单只股票获取其历史数据” | `ann_date/f_ann_date/end_date` 是不同列，不能从任一日期样例补出完整性规则。 |
| [fina_audit / 80](https://tushare.pro/document/2?doc_id=80) | “获取上市公司定期财务审计意见数据” | 公告区间与报告期参数有定义，未给最大行数/提取完成规则。 |
| [express / 46](https://tushare.pro/document/2?doc_id=46) | “当前接口只能按单只股票获取其历史数据” | 有单股票范围，未公布截断合同。 |
| [repurchase / 124](https://tushare.pro/document/2?doc_id=124) | “公告日期（任意填参数，如果都不填，单次默认返回2000条）” | 2000 只说明全参数省略的默认响应，不能作为本项目公告区间的 L；非股票输入允许多股票结果。 |
| [stk_managers / 193](https://tushare.pro/document/2?doc_id=193) | “股票代码，支持单个或多个股票输入”；示例注释“获取单个公司高管全部数据” | “全部数据”注释不是可执行的截断判定；本地仍单股票，公告 RANGE 的完整性未知。 |
| [top10_holders / 61](https://tushare.pro/document/2?doc_id=61) | 输入“报告期开始日期”“报告期结束日期” | 前十大名称和典型 10 行不是服务器响应限量；不能由 4 季×10 推导覆盖。 |
| [top10_floatholders / 62](https://tushare.pro/document/2?doc_id=62) | 输入“报告期开始日期”“报告期结束日期” | 同样没有最大行数或报告期完整集合规则。 |

另外读取官方 [FAQ](https://tushare.pro/document/1?doc_id=122)、[HTTP 说明](https://tushare.pro/document/1?doc_id=130)、[循环取数建议](https://tushare.pro/document/1?doc_id=230)、[数据更新说明](https://tushare.pro/document/1?doc_id=108)、[积分频次表](https://tushare.pro/document/1?doc_id=290)。这些页面未提供可套用于上述 11 项的统一 L：

- 积分频次表列的是“每天总量上限”，如“100000次/个API”“常规数据无上限”，不是单次返回行数。
- HTTP 示例的 `code=0` 仅说明请求成功，文档没有将它定义为“未截断”。
- FAQ 说明财务修订可能产生重复记录：“update_flag=1为修正后的数据，update_flag=0为初始数据”。这支持不能用典型行数断言完整，也不能擅自按报告期压成一行。
- 循环示例只是调用建议，未为 UNKNOWN 提供具体截断阈值；其中重试示例不改变 T14 禁止自动重试的合同。

## 5. daily / forecast / dividend：三个数字仍没有明确“最大”合同

| API | HTML 与静态 Markdown 一致的准确引文 | 结论 |
| --- | --- | --- |
| [daily / 27](https://tushare.pro/document/2?doc_id=27) | “每次6000条数据，一次请求相当于提取一个股票23年历史” | 数字仍是原措辞；未取得足以判定 `<6000` 未截断的新增合同。循环按日期抓取全市场的建议也没有补出截断语义。 |
| [forecast / 45](https://tushare.pro/document/2?doc_id=45) | “单次3500行” | 未写最大/最多，也未定义截断信号；不借 VIP 行为补普通接口。 |
| [dividend / 103](https://tushare.pro/document/2?doc_id=103) | “起始时间2000-01-01，单次查询返回2000行” | 数字仍不是明确最大；自然日公告规划与完整性是两个问题。其样例还展示早于所述起点的除息/登记日期，不能据此扩大当前可访问范围。 |

本报告不推翻 T13 对另外 19 个“最大/最多”公开候选的整体评估，但只对本次涉及的 monthly、fina_mainbz、slb_sec、slb_sec_detail 重新核对了该措辞。19 项实际适用于当前账户/请求形状的判断仍由新 SOURCE 和 root 的完整验收处理；fina_mainbz 矛盾单列如上。

## 6. BJ/BSE：参考日历有官方依据，直接输入未获证明

[trade_cal / 26](https://tushare.pro/document/2?doc_id=26) 本次原文仍为：

> 三大交易所的交易日历都是一样的，北交所交易日历参考上交所和深交所。

输入 `exchange` 的枚举仍为 `SSE,SZSE,CFFEX,SHFE,CZCE,DCE,INE`；输出交易所说明仍列 SSE/SZSE。因此可支持 **BJ 参考沪深日历的业务语义**，不能支持 `exchange=BSE` 的直接接口参数或返回值。该页也没有独立证明当前请求闭区间每天恰好一条。

如候选实现采用 BJ→SSE 或 BJ→SZSE，仍须明确确定的映射规则、完整自然日校验与新的真实 SOURCE；不能把网页句子变成已经验收的 BJ 任务。T13 `trade_cal-bse-direct` 只记录覆盖未确认，不能由本调查扩大成“HTTP 不支持 BSE”。`margin` 的 BSE 支持与日历接口是不同合同，本报告没有请求任何 BSE 数据。

## 7. 下一独立 SOURCE 轮的固定样本建议

下面是供 root 编制新轮清单的**建议，尚未登记或执行**。caseId 后缀只是建议名称，实际须添加新轮身份并与全部历史 caseId 检查唯一性；所有模式为 RANGE，日期轴分别是接口的 `trade_date` 或日历 `cal_date`。原失败/空样本继续保留。不得将新观察回填旧 case，也不预言任何非空结果。

| 建议 caseId 后缀 | 对应旧 case / 缺口 | API 与精确 params | 准确依据 | 预期观察及不能证明之处 |
| --- | --- | --- | --- | --- |
| `monthly-201809-whole` | `monthly-period-last-trading-day`、`monthly-range`；旧 20260731/20260831 无法判别 | `monthly`：`{"ts_code":"000001.SZ","start_date":"20180901","end_date":"20180930"}` | 本报告 §2：doc 145 同股样例含 20180930；doc 40 将其列为休市 | 记录当前返回的所有日期及实际行数，核对范围/股票；一次结果不能定义所有月份的通用规则。 |
| `monthly-201809-last-open` | monthly 日期语义冲突；不是旧下端 PASS 的替换 | `monthly`：`{"ts_code":"000001.SZ","start_date":"20180928","end_date":"20180928"}` | doc 40 `20180930` 的 `pretrade_date=20180928` | 观察按最后交易日过滤是否返回，与整段比较；空也保留，不改日重试。 |
| `monthly-201809-natural-end` | monthly 日期语义冲突；不是旧上端 PASS 的替换 | `monthly`：`{"ts_code":"000001.SZ","start_date":"20180930","end_date":"20180930"}` | doc 145 的自然月末样例 + doc 40 休市样例 | 观察按自然月末过滤是否返回；与 28 日的差别只说明这一固定历史月。 |
| `trade-cal-201809-monthly-reference` | monthly 新样本的日历依据；不代替 `trade_cal-bse-direct` | `trade_cal`：`{"exchange":"SSE","start_date":"20180928","end_date":"20180930"}` | doc 40 的 SSE 28/30 日期关系 | 必须每自然日唯一完整覆盖并记录开休市值；公开样例不能直接填真实日历结果。 |
| `slb-sec-20240620-history` | `slb_sec-range`、两端与 `slb_sec-single-000001` 的旧空样本 | `slb_sec`：`{"ts_code":"000001.SZ","start_date":"20240620","end_date":"20240620"}` | 本报告 §1：doc 332 同股同日样例；证监会 2024-07-11 暂停前 | 观察当前账户能否读该历史日、日期/股票及汇总业务键。不能证明历史完整起止、600000.SH 非空或持续更新。 |
| `slb-sec-detail-20240620-history` | `slb_sec_detail-range`、两端与 `slb_sec_detail-single-000001` 的旧空样本 | `slb_sec_detail`：`{"ts_code":"000001.SZ","start_date":"20240620","end_date":"20240620"}` | doc 333 同股同日明细列含 tenor/fee_rate；同一监管时间依据 | 观察明细字段及现有完整业务键，不按股日先去重；不能以汇总接口成功代替明细成功。 |

该最小组为 6 个计划 case，未触及阈值且日历一次覆盖时是 6 次来源请求；正式预算必须允许既有拆分行为并受 T14 的总期限/请求预算约束，实际计数以运行记录为准。本组特意只解决有公开依据的判别和历史可读性，不声称已覆盖两股票、一般两端、完整历史、BJ/BSE 或 fina_mainbz 的全部缺口。是否加入历史 SINGLE、第二股票、额外端点或更宽窗，由 root 先逐项登记可核验依据和目的，不临场改样本直到非空。

对于 11 UNKNOWN、三个含糊数值、fina_mainbz 默认集合/100 与 150，本次没有能够仅靠新增少量 SOURCE 样本解决的完整性合同。它们需要上游明确规则、可以核验的服务实现/版本约束或其他足以证明完整提取的资料。`bz_code` 投影只能补实际分类观察；Tushare API 的确切停止/保留范围仍需单独依据。以上硬缺口不能通过重复账户调用、缩小区间后行数相同、换样本、以 SOURCE PASS 代替合同或将接口移出目标来消除。

## 8. 证据保存、失败与本地核对

公开缓存目录为 `/private/tmp/issue018-t14-official-20260913T092638Z`，目录初始权限 0700。包含原始下载文件、可读文本及逐次 JSON 索引；保留失败记录，不覆盖为成功。`*_md.html` 是下载器沿用扩展名保存的**原始 Markdown 响应**，不是将其当 HTML 来源；SHA-256 对原始字节计算。curl 的补取 UTC 使用目标文件完成写入的 mtime；其他请求使用发起 UTC。

普通沙箱首个公开请求 DNS 失败；只读网络升级经自动审批后可访问。Python 部分 TLS 请求报 `UNEXPECTED_EOF_WHILE_READING`：原失败保存在各索引；`fina_audit` 与官方归档改用 curl 成功，`fina_mainbz` 静态页亦用 curl 补取。`general_api / 109` 和变更记录 `/1?doc_id=9` 的失败未补取，不声明读到了它们的正文。

搜索失败也按实际保留：Bing 多词/英文 API 查询返回不相关词典或泳联结果；DuckDuckGo 返回 202 人工验证挑战，未尝试解题；百度返回 227 字节空壳；Google 返回跳转提示。改用单一中文标题关键词后，Bing 找到证监会原文，最终结论引用完整原文而非搜索摘要。搜索没有结果不能解释为这些规则在互联网不存在。

本次写入仓库的唯一文件是本报告，root 统一 `git add`。本地核对只检查文档引文、URL/获取时间/文件 SHA-256、11 项与 3 项覆盖、Markdown 空白；不跑业务测试、不宣称完整性或 T14 验收通过。缓存索引及逐项下载身份如下。

### 下载索引身份

| 缓存索引 | SHA-256 |
| --- | --- |
| `docs-index.json` | `c50b56d0aba0e3655356e254908d7fd887318e6350047bfd8c76532f7c285cae` |
| `supplement-index.json` | `529cd408ac10e1a4d3daf71ce944d7386723fa7610559b1b2097da7a5a37529f` |
| `general-index.json` | `41d3b4ed0d9269d0b465b46ae282ae964d78bb5f601e5eeecc71335ecc9d2c9d` |
| `focused-index.json` | `6c944941cd974c08627c7d1eeee5918df8eff88b73c97a66e167fcd724f71466` |
| `markdown-index.json` | `8fb2f2a8f7aa6800f0447f610837fad06adbbf7a6a483ace8d53225a4c6bfe3d` |
| `archive-index.json` | `6f5305f96a46ab12de9aa745a6f725297bbb68085fbe9bdf68075c71a1bbf8c4` |
| `curl-index.json` | `b10b6445aefe715846929ed1834a72987ea6e659a61e19de22e6394752530f1c` |

### 主要原文身份

表内 SHA-256 是原始响应字节的完整摘要；对应可读正文摘要保存在索引中。

| 缓存名称 / 公开 URL | 获取 UTC | 原始 SHA-256 |
| --- | --- | --- |
| [daily](https://tushare.pro/document/2?doc_id=27) | 2026-09-13T09:26:49.037085Z | `9fae772ed0b49453388fbf6916303bf51c9af0f8465a758e78513f92e4cfd08e` |
| [forecast](https://tushare.pro/document/2?doc_id=45) | 2026-09-13T09:26:49.037496Z | `5a1795e33eca35b2131b81f15611647bfdbf123ee3b9c130976fd0ba6503a985` |
| [dividend](https://tushare.pro/document/2?doc_id=103) | 2026-09-13T09:26:49.037645Z | `064f3e72142c523cc828771b3c159cc90e1bceeee9a61a7b62d2faf50df5f5e1` |
| [monthly](https://tushare.pro/document/2?doc_id=145) | 2026-09-13T09:26:49.037773Z | `4555f6f520c81def9288a4937a773b65cab4669f926331eb95954e6bd328fb8b` |
| [fina_mainbz](https://tushare.pro/document/2?doc_id=81) | 2026-09-13T09:26:49.184408Z | `ff0793cd132fca776afe16c73b23dabb3e10284f5ad3105c59e3eba362819b9d` |
| [trade_cal](https://tushare.pro/document/2?doc_id=26) | 2026-09-13T09:26:49.201979Z | `caaf1548399d21968e7d60158e028e5b4134f93deb2a5ea553cd30e68d0244a3` |
| [slb_sec](https://tushare.pro/document/2?doc_id=332) | 2026-09-13T09:26:49.207422Z | `d653503d870b39d4911d45c7132d751186f9ae4fc243340f8a1dd5956bc0e803` |
| [slb_sec_detail](https://tushare.pro/document/2?doc_id=333) | 2026-09-13T09:26:49.207684Z | `7215c2fa5570fe2db5b6e3cc16c26b22363b1dba4e705768ca17ebde122afd14` |
| [adj_factor](https://tushare.pro/document/2?doc_id=28) | 2026-09-13T09:26:49.351457Z | `f235f23bd5a026937b9bf73acc9bdf60393b10613a8eb8427ced5991b6f46a37` |
| [suspend_d](https://tushare.pro/document/2?doc_id=214) | 2026-09-13T09:26:49.358740Z | `69d018b5ceac6aded11f6278728d15aa8c03eae2e3a588a073fe742c06b37fcd` |
| [income](https://tushare.pro/document/2?doc_id=33) | 2026-09-13T09:26:49.368931Z | `e2594dc1b37af89a11b84215f48fe8e551c69416ada7eca9246ea3e832b150ee` |
| [balancesheet](https://tushare.pro/document/2?doc_id=36) | 2026-09-13T09:26:49.369069Z | `c0036b8aa3183b3c30f9a4faa749e2b39929acee7526838ed50241ddeb549d5b` |
| [cashflow](https://tushare.pro/document/2?doc_id=44) | 2026-09-13T09:26:49.513615Z | `4a74156404d055cadf821eae5bd62bd9b94807704160c97b851c71ec2aaca2cb` |
| [express](https://tushare.pro/document/2?doc_id=46) | 2026-09-13T09:26:49.679373Z | `f878243a1e48640e22b6993b9040912b99de048098fc8c2ff93a2c97df0a891a` |
| [repurchase_retry](https://tushare.pro/document/2?doc_id=124) | 2026-09-13T09:27:44.187358Z | `904a2b14b022568cd76008aaade3306d7090b441aefb804d8e4f32dead89e241` |
| [stk_managers_retry](https://tushare.pro/document/2?doc_id=193) | 2026-09-13T09:27:44.318268Z | `97df9d9d4990a34147862843a42ea0258bf58315c21ef2e37503d8d9d854d046` |
| [top10_holders](https://tushare.pro/document/2?doc_id=61) | 2026-09-13T09:26:50.527182Z | `677a5aff94481e7438670ab030fbb3fcdb29a6eb34c0a596e8f417cb8a05cbdc` |
| [top10_floatholders_retry](https://tushare.pro/document/2?doc_id=62) | 2026-09-13T09:27:44.459296Z | `ed4e3859dc79c31a384cbc224a398a76194111d0a6344f8b3aa5bd0da4e84ce7` |
| [csrc_suspension](https://www.csrc.gov.cn/csrc/c100028/c7493852/content.shtml) | 2026-09-13T09:30:54.039596Z | `1b4539059370e52eb40e0cf3ce7994bc1340e22fd0d14f3de3b6f3b2c7fffd69` |
| [call_rules](https://tushare.pro/document/1?doc_id=40) | 2026-09-13T09:28:33.834176Z | `3d7cddd537439086cf2f2ef70e652076144635848a2804eb568121f4fec49439` |
| [faq_actual](https://tushare.pro/document/1?doc_id=122) | 2026-09-13T09:28:33.833742Z | `2dc45da47d0c9de0eecfdee749087ae9f4efb5e88c1f60d834d86e10e80ce43b` |
| [http_usage](https://tushare.pro/document/1?doc_id=130) | 2026-09-13T09:28:33.834321Z | `49a41194a8c77d7eb71d1c0466b08515ff0c3aff09857deb4a8e1088492d5a06` |
| [elegant_usage](https://tushare.pro/document/1?doc_id=230) | 2026-09-13T09:28:33.834460Z | `6fdb342fbf11672ac31c2ddfde4ec168e3088ccdffa3a3ee0be0edaa54408a95` |
| [frequency](https://tushare.pro/document/1?doc_id=290) | 2026-09-13T09:28:33.957425Z | `765453b00496cdf9749a7d733c189766e3a2411d3ada03f3d96bb98c3c3ecb27` |
| [limits](https://tushare.pro/document/1?doc_id=108) | 2026-09-13T09:27:44.017565Z | `b6b3ebb0fde4ae63056292ed0aec987bd1793e0cb61e970794a967a5076adeaf` |
| [skills_page](https://tushare.pro/document/1?doc_id=450) | 2026-09-13T09:28:33.968695Z | `bfb0d4fea9aaef25937cc510788a8565c13e7d9a361e6d039aa0e38b9e5393a7` |
| [weekly_monthly_daily](https://tushare.pro/document/2?doc_id=336) | 2026-09-13T09:27:44.158225Z | `e385f3f897f725a6dd0c45589e80ff1b898bc1919d1a814c8e85a6cb11561647` |
| [tushare-data-curl.zip](https://tushare.pro/files/pro/tushare-data.zip) | 2026-09-13T09:29:57.344167Z | `44c22c5cd7110a52c6455f8fe4f8766952cc15a61671534a1251ed8d2f427fcc` |
| [fina_audit_curl.html](https://tushare.pro/document/2?doc_id=80) | 2026-09-13T09:29:57.486416Z | `a2d5fe5c7fdceac8e1260f0a844d977edc5b59e5b5e4de0e87d7c250ea0ef146` |
| [fina_mainbz_md_curl.md](https://tushare.pro/wctapi/documents/81.md) | 2026-09-13T09:31:56.336268Z | `d0d5d6992c024518c0041ad73679c17df3afdb303ae2d6aef54c347a941662f2` |

16 个目标静态 Markdown 的逐项 URL、获取时间、原始摘要均在 `markdown-index.json`（15 个成功，fina_mainbz 原失败）与 `curl-index.json`（fina_mainbz 补取成功）中。HTML 与静态页是同一官方文档的两种公开呈现，不能当成两个独立服务端完整性保证。所有缓存均为公开资料。

### 本地核对实际结果

2026-09-13T09:37:30.941246Z 的 `local-checks.json` 记录：56 组原始/可读文本摘要匹配、3 份 curl 原始摘要匹配、36/36 引文或公开样例片段逐字核对通过、11/11 UNKNOWN 行覆盖、6/6 固定样本建议具备参数；8 次索引内请求失败仍保留。核对脚本退出 0。`git diff --no-index --check /dev/null docs/verification/ISSUE-018-T14-official-evidence.md` 退出 0，无空白错误。以上仅是公开调查报告的本地检查，业务测试及账户 SOURCE 均未在本子任务运行。

## 12. ISSUE-023 稀疏事件公开依据（2026-09-13）

本节只记录公开选样依据，未将公开网页写成账户SOURCE结果。汇总`/private/tmp/issue023-research/public-evidence.json`，SHA-256 `2cadc26467b694cb02e8fb4838d2ff37127c72419d8737e9f7f094e02f21229f`；15份缓存摘要逐一复核相符。官网161/162/175/111为旧缓存复读，其余为本次curl公开读取，表中UTC取下载完成缓存mtime，不冒称精确请求起始时间。

| 依据 | 公开事件与实际含义 | 下载完成UTC / 缓存SHA-256 |
| --- | --- | --- |
| [sina-block-000001-20230220](https://vip.stock.finance.sina.com.cn/q/go.php/vInvestConsult/kind/dzjy/index.phtml?symbol=sz000001&bdate=2023-02-20&edate=2023-02-20) | 000001.SZ 20230220同日两笔，同价同买卖方、成交量不同；不按股日合并。 | `2026-09-13T14:40:57.718407+00:00` / `be7fad27c525cddfa44933b3e39cf3e80e110631c93b3c59cb2efb9b0954e19b` |
| [sina-block-600000-20220401](https://vip.stock.finance.sina.com.cn/q/go.php/vInvestConsult/kind/dzjy/index.phtml?symbol=sh600000&bdate=2022-04-01&edate=2022-04-01) | 600000.SH 20220401同日两笔，同价同买卖方、成交量不同。 | `2026-09-13T14:40:59.739367+00:00` / `4b6cbcae62959e1656a2dc0afa9ba37ceae6a342cfcfc59919892793eeb9880d` |
| [eastmoney-holder-pingan-data](https://datacenter-web.eastmoney.com/api/data/v1/get?reportName=RPT_SHARE_HOLDER_INCREASE&columns=ALL&source=WEB&client=WEB&pageSize=10&sortColumns=END_DATE&sortTypes=-1&filter=%28SECURITY_CODE%3D%22000001%22%29) | 000001.SZ公告20210907，业务期间20210901～20210906；公开公告索引同日有部分董监高及配偶买入公告。 | `2026-09-13T14:43:23.092811+00:00` / `99bf795bd98c892077415786d77abb3667071fdc8486d58efc5b8a226f8f8b60` |
| [sina-holder-pufa-20241220](https://vip.stock.finance.sina.com.cn/corp/view/vCB_AllBulletinDetail.php?stockid=600000&id=10659767) | 600000.SH公告20241220，正文业务发生20241219；落款日不能代替公告日。 | `2026-09-13T14:44:57.383352+00:00` / `b0d6ca6804ffb37bc8bf068ee0fb5a5fb1ac7511022dc49a3361dafa5ddcae01` |
| [eastmoney-disclosure-pingan-2024q3](https://datacenter-web.eastmoney.com/api/data/v1/get?reportName=RPT_PUBLIC_BS_APPOIN&columns=ALL&source=WEB&client=WEB&pageSize=10&filter=%28SECURITY_CODE%3D%22000001%22%29%28REPORT_DATE%3D%272024-09-30%27%29) | 000001.SZ报告期20240930：首次预约20241026，首次变更为20241019，实际20241019。 | `2026-09-13T14:45:01.344433+00:00` / `c323f4526d75336b4789dd632bc0972094c541a7a4abda9f1cc1dac81b1fac67` |
| [eastmoney-disclosure-pufa-2024h1](https://datacenter-web.eastmoney.com/api/data/v1/get?reportName=RPT_PUBLIC_BS_APPOIN&columns=ALL&source=WEB&client=WEB&pageSize=10&filter=%28SECURITY_CODE%3D%22600000%22%29%28REPORT_DATE%3D%272024-06-30%27%29) | 600000.SH报告期20240630：首次预约20240829，首次变更为20240820，实际20240820。 | `2026-09-13T14:45:36.292495+00:00` / `90f1cc88a6df8af3396d8715533dc9b49d7b8471925bb2836f1dd1f09bc00b21` |
| [eastmoney-pledge-pufa-data](https://datacenter-web.eastmoney.com/api/data/v1/get?reportName=RPTA_APP_ACCUMDETAILS&columns=ALL&source=WEB&client=WEB&pageSize=10&filter=%28SECURITY_CODE%3D%22600000%22%29) | 600000.SH两个公告20140324/20021231，质押开始分别20130730/20011213；不能假设Tushare同源同日期。 | `2026-09-13T14:41:30.002738+00:00` / `ec9d87faa05c807f91746a1adad69dc578568facccfd996f872267bb5d92c4ae` |

- 东方财富公开数据入口来自其网页JS：股东增减持`RPT_SHARE_HOLDER_INCREASE`、预约时间`RPT_PUBLIC_BS_APPOIN`、质押`RPTA_APP_ACCUMDETAILS`。表中链接含精确reportName/股票/报告期筛选；它们是公开网页数据，不使用Tushare凭证。
- `FIRST_CHANGE_DATE`是变更后的预计披露日，`EITIME`是该公开数据记录时间，均没有依据映射成Tushare“最新披露公告日”。固定公告索引窗口内未见独立改期公告标题，不能推断没有改期或猜其公布日。仅据明确报告期和发生过的预约修改，登记有限的逐自然日SOURCE调查：000001.SZ 20240930～20241026（27日）、600000.SH 20240630～20240829（61日）；返回后只按实际ann_date制定边界与同键复查新轮。
- 四官网原文明确字段及上界：block_trade 1000、disclosure_date 6000、stk_holdertrade 3000、pledge_detail 1000。官网大宗交易的601318.SH/20181227五笔且买方不同、质押000014.SZ多公告日及业务日期、增减持300115.SZ/20190426两笔、披露300619.SZ/20181228为补充代表样本；不是基准股票来源替代，也不证明当前账户全历史可读。
- 000001.SZ质押本次未取得可追查非空事件，不能用600000.SH或000014.SZ成功推断它非空。后续结论以固定SOURCE实际结果为准。

## 13. ISSUE-024 历史合同复核（2026-09-13）

本轮重新HTTP GET官网静态Markdown331/332/333及证监会原公告，四份均HTTP200；原件与获取摘要保存在`/private/tmp/issue024-control/`。三接口原文仍为“单次最大可以提取5000行数据，可循环获取所有历史”，没有提供历史保留起始日、最后数据日、永久可访问性或停业后服务保证。静态Markdown不含导航，“停”标记来自先前HTML，不将本轮Markdown伪称导航复核。

| 来源 | 获取UTC | 原始字节SHA-256 |
| --- | --- | --- |
| [331](https://tushare.pro/wctapi/documents/331.md) | 2026-09-13T15:24:37.644299+00:00 | `d53a7eb16e86ae8dc02cfb08a6e3a560c5c62f31a5f7e39adc3f9c918131592c` |
| [332](https://tushare.pro/wctapi/documents/332.md) | 2026-09-13T15:24:37.862353+00:00 | `75c11c87c2c7be63b59ea6551620c8adaf5e2bb1fe09178ab6655dafa280f608` |
| [333](https://tushare.pro/wctapi/documents/333.md) | 2026-09-13T15:24:38.047580+00:00 | `27c08d5058baf53a113ebcec6d6f1cc3c651877a1862b86c800615277d11b48e` |
| [csrc](https://www.csrc.gov.cn/csrc/c100028/c7493852/content.shtml) | 2026-09-13T15:24:38.311269+00:00 | `1b4539059370e52eb40e0cf3ce7994bc1340e22fd0d14f3de3b6f3b2c7fffd69` |

- 331示例固定`20240601～20240620`，列出13行、首尾20240603/20240620，输出只有trade_date、ob、auc_amount、repo_amount、repay_amount、cb，无期限列。现有业务键`[trade_date,ob]`不修改；示例一日一行仅为该窗口事实。
- 332/333同为20240620；000001.SZ有公开汇总及明细（14天、2.20%），其他股票费率不同；该截断显示的示例没有600000.SH，也未直接展示另一期限，因此第二股票及期限差异须由新SOURCE调查。
- 证监会原文仍为“自2024年7月11日起实施”“存量转融券合约可以展期，但不得晚于9月30日了结”。通知针对转融券，不据此宣告转融资停业，更不能解释成三API最后返回日期。
- 当前可核验官方依据支持按5000上界调查历史，未填补精确保留起止和持续提供承诺。所登记20230101起的长窗、停业前后和第二股票均是固定调查输入，不是上游已承诺可查的边界。Google搜索本次仅返回重定向壳，未取得可引用的新历史合同；不把搜索失败解释成上游不存在承诺。

## 14. ISSUE-025 十一接口提取合同复核（2026-09-14）

北京时间2026-09-14重新取得十一官网静态Markdown及六个通用页，17/17 HTTP200。下表UTC为实际请求起止；原字节缓存`/private/tmp/issue025-control/public/`，索引SHA-256 `a01f8699e2c880f9d029b4049c9851af1917e19945cdf7222ca3562ce8bb8178`。普通沙箱首个28.md请求DNS失败（curl exit6），公开只读网络升级后取证成功，未调用账户API。十一接口文本按行与此前静态缓存完全一致；URL/原始字节身份按本轮记录。

| 来源 | 请求起止UTC | 原始SHA-256 |
| --- | --- | --- |
| [adj_factor](https://tushare.pro/wctapi/documents/28.md) | `2026-09-13T16:17:21.957175+00:00`～`2026-09-13T16:17:22.091117+00:00` | `cd1bd45fbac60cb549bce59d4ed6b6b1fc2f8b6e7346453eb46181283587c522` |
| [suspend_d](https://tushare.pro/wctapi/documents/214.md) | `2026-09-13T16:17:21.957415+00:00`～`2026-09-13T16:17:22.080826+00:00` | `427a547dc8cba89a842ab102e587297c8ce247ba1dfc54bf786b369fd8560388` |
| [income](https://tushare.pro/wctapi/documents/33.md) | `2026-09-13T16:17:21.958592+00:00`～`2026-09-13T16:17:22.101777+00:00` | `66ccf296f51019f88520f2be723ddeb2bf59e95c645da596a80f56759ef82980` |
| [balancesheet](https://tushare.pro/wctapi/documents/36.md) | `2026-09-13T16:17:21.960435+00:00`～`2026-09-13T16:17:22.080693+00:00` | `d267a3eadd7cd6f6e5ab659c637c8dfcc8232381c2e1f2c47ba84edb3ce1c791` |
| [cashflow](https://tushare.pro/wctapi/documents/44.md) | `2026-09-13T16:17:22.081103+00:00`～`2026-09-13T16:17:22.182642+00:00` | `9786d854831be61e43652f6f1470b01d9dcb7329b1e0c069234a9f9be47e5c9c` |
| [fina_audit](https://tushare.pro/wctapi/documents/80.md) | `2026-09-13T16:17:22.081183+00:00`～`2026-09-13T16:17:22.182476+00:00` | `9ea741dfcfa5fc06548ca3431c18bd3b1febebedcfbd6359ff8f41e553c4b185` |
| [express](https://tushare.pro/wctapi/documents/46.md) | `2026-09-13T16:17:22.091433+00:00`～`2026-09-13T16:17:22.180877+00:00` | `e21ef2230bf1f9f682c38a6a2748a0587d0fc4b053d4879c6734fdab126f5927` |
| [repurchase](https://tushare.pro/wctapi/documents/124.md) | `2026-09-13T16:17:22.101990+00:00`～`2026-09-13T16:17:22.201460+00:00` | `48399ac9c0f621fa2e6494d9f23133ff24a79621bd298fa0e810a964ae60f954` |
| [stk_managers](https://tushare.pro/wctapi/documents/193.md) | `2026-09-13T16:17:22.181104+00:00`～`2026-09-13T16:17:42.283298+00:00` | `b5c1a9c3f583dec3d9ae66c8616e450f0c5f72e9e12f1d285b6842b4f046eeaa` |
| [top10_holders](https://tushare.pro/wctapi/documents/61.md) | `2026-09-13T16:17:22.182850+00:00`～`2026-09-13T16:17:22.360150+00:00` | `912e649074da9bbf0024419ef0a0120ea6bb7bf3ec769345ebef673ccddb9431` |
| [top10_floatholders](https://tushare.pro/wctapi/documents/62.md) | `2026-09-13T16:17:22.184187+00:00`～`2026-09-13T16:17:22.279993+00:00` | `19cf78ff554ccf0edd1a14c6dd1d307b6744b7df00821d6e1952a4b58aeb8dbe` |
| [faq](https://tushare.pro/wctapi/documents/122.md) | `2026-09-13T16:17:22.201686+00:00`～`2026-09-13T16:17:23.311691+00:00` | `2a04f9e7e3c2dd6353fb662366fb7e59bf9fcc095a409b1c6b9d88fb008131be` |
| [http](https://tushare.pro/wctapi/documents/130.md) | `2026-09-13T16:17:22.280211+00:00`～`2026-09-13T16:17:24.371789+00:00` | `b470bf956f76577e1af8f9906b9524c7f647d36581d1e56f4a5f50adf6e2d675` |
| [elegant](https://tushare.pro/wctapi/documents/230.md) | `2026-09-13T16:17:22.360361+00:00`～`2026-09-13T16:17:22.449983+00:00` | `f779eb3783537f9b8cb5d72f8d52de6a96da94a859818756dd49682ceb571fab` |
| [frequency](https://tushare.pro/wctapi/documents/290.md) | `2026-09-13T16:17:22.450222+00:00`～`2026-09-13T16:17:23.541461+00:00` | `1eb08eee7cc4f53977b0935fc6cb49815a5c971fdde45f0ed770ca6c8677d7bd` |
| [limits](https://tushare.pro/wctapi/documents/108.md) | `2026-09-13T16:17:23.311929+00:00`～`2026-09-13T16:17:23.420482+00:00` | `0f7bc3f402f4ea08b437c9ab270b8675440851cdf810a87ada088d1c392010d5` |
| [general](https://tushare.pro/wctapi/documents/109.md) | `2026-09-13T16:17:23.420707+00:00`～`2026-09-13T16:17:23.511483+00:00` | `d673e4c7ec5b9921020d289997318a384ac530c55c6cdf0188c4563a3aa20794` |

本轮逐项结论沿用本报告§4的准确引文：

| 接口 | 当前请求已有依据 | 仍缺的完整提取依据 / 可技术验证内容 |
| --- | --- | --- |
| adj_factor | 28：“可提取单只股票全部历史复权因子”；start/end日期参数 | 没有单次上限或未截断标识；两股票交易日整段/边界可验证 |
| suspend_d | 214：“停复牌查询开始日期/结束日期”，输出“停复牌日期（覆盖从停牌到覆盖期间的连续日期）” | 无行数上界/连续事件全集保证；两基准股票有限历史和官网事件连续日期、S/R可验证 |
| income | 33：“公告日开始日期/公告日结束日期”，普通接口限单股票历史 | 没有公告窗口的截断规则；ann_date和报告期可按同一行比较 |
| balancesheet | 36：公告起止，输出ann_date/f_ann_date/end_date独立 | 同上；两股票同一行公告/报告期、两端可验证 |
| cashflow | 44：公告起止，f_ann_date为“实际公告日期” | 无截断规则；ann_date/end_date/f_ann_date须分别计数，相等样本不能证明可互换 |
| fina_audit | 80：公告起止；官网600000.SH例含20180428公告/20171231报告期 | 无单次最大或完成信号；新两股票年度窗/非空边界可验证 |
| express | 46：单股票公告窗，官网请求20180101～20180701 | 无截断规则；不改VIP，实际公告与报告期及边界可验证 |
| repurchase | 124：“公告日期（任意填参数，如果都不填，单次默认返回2000条）” | 该2000不适用于带日期条件的硬上限；无股票原方式、多证券与边界可验证 |
| stk_managers | 193：公告起止，示例注释“获取单个公司高管全部数据” | 注释未给可执行完成规则；ann与任期日期须区分，RANGE不能由snapshot代替 |
| top10_holders | 61：“报告期开始日期/报告期结束日期” | 名称“前十大”不是响应最大10；同一行end在窗/ann在窗外及两端可验证 |
| top10_floatholders | 62：同上 | 同上，独立来源验证，不能以另一接口通过替代 |

六个通用页没有提供可应用于上述十一项的统一上界：122为FAQ，130成功响应只说明调用成功；230以daily/trade_cal演示循环及效率，不证明十一接口逐日响应无截断，其示例重试建议不采用。290表头为每天总量/频次而非响应行数；108的“全部历史”是可用历史/更新说明。此次补取先前失败的109后确认它是SDK层`pro_bar`集成行情接口，原文“目前暂时没法用http的方式调取通用行情接口”，不是统一HTTP分页/截断合同。

`ROW_LIMIT`需要与当前参数形状匹配的上界及小于上界可视为完整的采用依据，`CALENDAR_COVERAGE`需要已知应有日期全集。上述十一项均尚未取得这类规则；不能从公告频率、典型10条、默认2000、有限窗口宽窄相同或HTTP成功填造。准确缺口可以通过上游给出适用版本/权限/参数范围的截断或完成规则解决；若用户选择改为仅收集返回行，则是产品承诺修订，须单独决定及设计，不称为新上游保证。

### ISSUE-025 平安银行业绩快报补充选样

[东方财富公开公告索引](https://np-anotice-stock.eastmoney.com/api/security/ann?sr=-1&page_size=100&page_index=1&ann_type=A&stock_list=000001&begin_time=2022-01-01&end_time=2022-01-31)返回`AN202201131540179440`，标题“平安银行:平安银行股份有限公司2021年度业绩快报”，公告日`2022-01-14`。下载完成UTC `2026-09-13T16:32:34.019462+00:00`，原字节SHA-256 `3964050df974d9982102455d22dfaf3711d3cbea2772e124fed4c296e82c3d96`，缓存`/private/tmp/issue025-control/public/pingan-2022-notices.json`。仅按公开标题和公告日选样，未宣称已取得发行人公告全文。新浪预告栏目混列同日数据，故不使用其“预升”分类判断快报；本轮新浪2022Q1公告页面显示暂时没有数据，失败调查保留为`pingan-2022q1.html`，不作为无公告的证明。

在首轮express 000001.SZ的2018窗口为空后，按该明确2021年度快报登记20220113～20220115、公告日及两侧窗口/邻日，不改旧空、股票或普通API，也不预设新请求必非空。
