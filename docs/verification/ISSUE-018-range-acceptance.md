# ISSUE-018 RANGE 真实接口验收

2026-09-15 ISSUE-031已阻塞：82 PASS / 1 FAILED / 195 NOT_RUN；首个fina_indicator任务ADAPTER_TYPE_INVALID后停止。正式处置10 AVAILABLE/24 NEEDS_VERIFICATION/6 SINGLE_ONLY；当前候选能力33 AVAILABLE/1 NEEDS_VERIFICATION/6 UNSUPPORTED，fina_indicator已撤回v3。旧74 SINGLE和25 RANGE任务保留，详见[ISSUE-031验收](ISSUE-031-range-live-task-verification.md)，母任务尚未关闭。

## 范围与构建身份

权威状态见 [ISSUE-018看板](../task-handoffs/ISSUE-018/ISSUE-018-task-board.md)。40接口、34 RANGE/6 SINGLE_ONLY、31原生RANGE/3逐日RANGE不变；唯一结果为 [JSON索引](ISSUE-018-range-acceptance.json)，现有30轮/943个case、累计1081次Tushare请求，fixture另计。schema 2当前规则分布为22个ROW_LIMIT、11个RESPONSE_ONLY、1个CALENDAR_COVERAGE及6个SINGLE_ONLY UNKNOWN。旧T13三轮/329case及15个成功任务原样保留，新运行不追认旧失败。

T14从HEAD `34f3e283c0ee7f58c19bf72e8a470c1c858ac26d` 的完整未提交成果建立独立源码快照，实际重建两包并用新空schema验收。T14当时候选生产包SHA-256 `b6f1a90dc9fbe54916895fe1c896a7b69e1982dd3a2c8e26124e7e6451811cfa`，验收包 `cb6c7a750c0e75768ee54ad89cf5ed368690edd00f7210ac10dff33a1cdc1a5f`。固定输入、每轮源码指纹、初始SINGLE包与最终RANGE包区别、清理及门禁见 [T14运行登记](ISSUE-018-T14-runs.md)。

以下本地实施和前三个真实运行章节为T13历史记录，按当时身份/结果保留；当前逐项结论和40项表已随T14实际结果更新。未运行仍NOT_RUN，任务/SQL未发生值仍null。

## T13 本地实施验证（历史）

2026-09-13 接续完成证据校验器及40项初始JSON索引。校验器拒绝缺项、UNKNOWN或缺证据开放、失败/未运行冒充通过、错构建身份、异常批次树/计数和敏感字段；成功原生区间完整覆盖且不重叠，自然日规划逐日覆盖，交易日依赖完整日历证据。SOURCE保留真实来源事实，任务及SQL字段为null。

项目Node24命令 `data-plane/tensor-app/target/frontend/node/node --test control-plane/e2e/tushare-range-evidence.test.js`：最后一轮修复前38项/35通过/3失败，修复后38/38通过、0跳过、退出0（117.452167ms）。独立限定复审规格/质量均PASS，无剩余重要发现。初始索引仍40项、0运行、0 AVAILABLE，不能将这些离线检查作为真实验收。

SOURCE探针已完成离线实现及一轮审查修复：显式授权入口、私有输入/输出、5000请求/30分钟预算、完整日历、逐日规划、失败停止与安全摘要。新增SINGLE日历完整覆盖和真实日期谓词校验，财务公告日与报告期、快照无日期条件分别处理。专项Maven最终26/26通过、0错误/跳过、退出0；修复前26项/6失败。独立复审规格/质量PASS。181个固定私有用例通过Java输入校验，合成首项失败后的1 FAILED/180 NOT_RUN输出通过Node安全投影和完整索引校验；合成结果未写入真实索引。

探针专项命令：`mvn -f data-plane/pom.xml -pl tensor-plugin-tushare -am -Dtest=TushareRangeSourceProbeTest -Dsurefire.failIfNoSpecifiedTests=false test`。该类是零网络测试；上述离线阶段没有执行真实Probe或任务。账户harness迁移和后续真实运行结果见下文。

2026-09-13 设计要求的五类本地专项退出0：Probe26、Policies109、BatchDownload17、TradeCalendar4、Availability2，共158/158，失败/错误/跳过均0。同一Maven生命周期前端34文件/468项及Vite构建通过；真实账户环境变量已移除。命令为 `mvn -f data-plane/pom.xml -Dtest=TushareRangeSourceProbeTest,TushareBatchPoliciesTest,TushareBatchDownloadTest,TushareTradeCalendarTest,TushareBatchAvailabilityTest -Dsurefire.failIfNoSpecifiedTests=false test`；日志 `/private/tmp/issue018-t13-five-class-verification.log`。这属于本地专项，不代替真实运行或六条完整源码门禁。

2026-09-13 账户harness已迁移正式task API，保留40个SINGLE测试、74次提交/148次查询及fixture 2次提交/3次查询；RANGE使用固定清单和完整SOURCE索引，提交前核对运行时能力。补齐真实批次树、独立SQL摘要、两股票历史保留、任务/查询错误分离和安全清理。独立审查发现的五项重要问题已修复并限定复审规格/质量PASS。最终Node离线测试54/54、0失败/跳过、退出0；CI发现SINGLE精确40个API，RANGE合成3样本精确2个API，空RANGE拒绝，所有有效测试retries=0、workers=1、trace/screenshot/video关闭。发现与离线通过均不代替真实浏览器/SQL/账户执行。

2026-09-13 最终全量审查的I1/I2已完成限定离线修复：公开完整性索引保留19条行数合同及独立日历依据；TASK_QUERY仅在任务原生错误信封有效且sent/header/body请求身份一致时记录固定错误码、owned task/route与当前case，不保存错误消息或改写任务状态。项目Node24同一聚焦命令先RED（67项、62通过、5失败、0跳过、exit 1），再GREEN（67/67、0失败/跳过、exit 0）。子环境仅保留固定PATH和C语言区域，未执行真实账户、SQL或浏览器；完整命令与历史run/case保留检查见[持久化限定复审](ISSUE-018-T13-rereview.md)。这不提升任何旧run，也不改变0 AVAILABLE；独立限定复审由root另行记录。

收尾整体审查另发现公开合同未同步JSON、TASK_QUERY固定错误码及响应身份缺失。统一修复后使用`env -i PATH=/usr/bin:/bin:/usr/sbin:/sbin LANG=C LC_ALL=C data-plane/tensor-app/target/frontend/node/node --test control-plane/e2e/tushare-range-evidence.test.js`，RED为67项/62通过/5失败，GREEN为67/67、0失败/跳过、退出0。索引补齐公开规则，任务查询失败只保留经sent/header/body身份核对的固定事实，原任务状态不改写；全部历史runs/cases及接口状态/版本/处置保持不变。[整体审查](ISSUE-018-T13-review.md)两项Important均由[限定复审](ISSUE-018-T13-rereview.md)确认解决，规格/质量PASS。这些离线结果不改变下述真实失败结论。

## T13 真实运行记录（历史）

SOURCE run `issue018-t13-source-20260912T123840Z`：UTC `2026-09-12T17:18:49.462Z` 至 `2026-09-12T17:27:07.915Z`，退出1；181用例中88 PASS、89 EVIDENCE_MISSING、1 FAILED、3 NOT_RUN，累计244次请求。固定节流2000ms；私有日志扫描安全、输入未变、进程已回收，cleanup PASS。失败项为`trade_cal-bse-direct`，固定错误码`BATCH_COMPLETENESS_UNCONFIRMED`。没有自动重试或替换参数，完整失败run已导入JSON。

- 源码差异SHA-256：`cd3aa5ed8445890b0565328b606a126ca3562f485a3e0e408430133d085d2263`。
- 生产JAR SHA-256：`a408d3e69575d3386c4d6236eedabfc896c054d17e0db5270b65a970bd3a3a4d`。
- 验收JAR SHA-256：`31ade90bf11c948c712de657446f12c0adb819b8b27e96373cbadde986bc2ba8`。
- 执行命令：`TENSOR_TUSHARE_LIVE_E2E=1 mvn -f data-plane/pom.xml -pl tensor-plugin-tushare -am -Dtest=TushareRangeSourceProbe -Dsurefire.failIfNoSpecifiedTests=false test`；Token仅环境传入，原始响应不写仓库。

独立SINGLE首次bootstrap run `issue018-t13-single-43721811-2900-4e53-9d5f-51c156e1c5de`退出1。fixture提交前查询成功返回0行，随后页面导航等待超时；Tushare和fixture任务提交均0、Tushare请求0，74个真实样本全NOT_RUN，Playwright为1失败/39未运行。网络/日志/输入/自有JVM清理全部通过。该失败记录不伪装成74个TASK失败或任何账户通过；导航修复与后续独立运行见下一段。

独立SINGLE导航修复经59/59离线回归及规格/质量复审通过后，用新空schema执行run `issue018-t13-single-5f17f7aa-0b5e-40bd-b6d5-b8d816d94d65`（UTC `2026-09-12T17:43:28.700Z` 至 `2026-09-12T17:45:47.224Z`）。Playwright为7接口通过、1失败、32未运行；harness记录的74样本状态为14 PASS、1 EVIDENCE_MISSING、59 NOT_RUN；这些历史检查标记受本轮SQL编码缺陷和源码身份失败限制，不作为完整有效验收。实际Tushare任务15次提交/15次来源请求，均观察到SUCCEEDED；另fixture2次提交/3次查询，Tushare records查询29次。fina_mainbz首股票任务成功写入150行，SQL观察阶段失败，因此该case保留EVIDENCE_MISSING，未伪造任务FAILED。

只读排障确认SQL客户端编码问题：harness的LANG=C使客户端/连接/结果采用latin1，150个真实业务键经CAST转换只剩68个不同键；utf8mb4下为150行/150个键，原始存储未损坏。测试侧现已显式固定utf8mb4，Node61/61及独立规格/质量复审通过；root以相同LANG=C和修复参数只读复核为150行/150原始键/150转换后键。不重新下载、改写既有行或升级旧run。私有SQL原始行未进入仓库。

该轮浏览器、网络、自有JVM、日志及包/清单检查均完成；wrapper另检出运行期间来自并行前端工作的源码差异（vite.config和demo文件），`inputUnchanged=false`，所以canonical cleanup为FAILED。两包及manifest/examples哈希未变，仍不把本轮包装为完整清洁验收；原run和并行改动均保留，0 AVAILABLE。

本次累计保存3轮、329个case，Tushare请求为244次SOURCE与15次TASK，共259次；fixture另计2次来源调用。没有执行RANGE任务。完整失败身份参与最终判定，14个历史TASK PASS标记不能升级为有效的两股票最终验收。

后续SOURCE投影已补齐income/fina_indicator的同一行公告日与报告期整数比较，以及repurchase有效/不可用证券行数和去重证券数量。仅适用于未来真实观察，不保存证券列表，不回填本次数据。新增4项行为测试先RED（30项中4失败），相同探针专项命令最终30/30、0失败/错误/跳过，独立规格/质量PASS；此前五类158/158仍是当时Probe26的历史结果，未重复累加。三个目标API当前均为单次原生请求，未声称已测试未来多响应部分失败场景。

## 官方依据

本节保留各阶段官方摘录与当时结论；当前处置以第1段、40项结果表和ISSUE-031追加结论为准。

2026-09-12 对母issue链接的40页逐项执行公开GET，40/40成功取得对应接口正文；不携带Token，不访问数据API。先前沙箱DNS失败，获得本地网络权限后公开读取成功。本次接续复核缓存的40份HTML和正文SHA-256，与采集索引全部一致（0处不匹配），保留原采集时间。下面保留本次时间、URL、正文/HTML摘要、参数和限制准确引文。网页内容不能证明当前账户权限、上游筛选有效或任务入库完成。

11项UNKNOWN仍无明确完整提取规则。独立复核确认19个数值候选的官方正文明确写有“最大/最多”，可作为其ROW_LIMIT上界的依据；但实际来源筛选与任务闭环仍未验证。daily、forecast、dividend的原文仅描述每次/单次返回数量，继续记录候选值，不据此单独开放。`repurchase`的ann_date参数说明明确限定“如果都不填，单次默认返回2000条”；不能升级为指定区间硬阈值。`trade_cal`说明北交所参考上交所和深交所，但输入表未列BSE。`slb_sec/slb_sec_detail`导航仍标停，正文未给出停止日期或完整历史支持窗口。`fina_mainbz`列出P/D/I可选type而没有定义省略type的默认分类；本轮不混类型或改业务键。`monthly`正文称每月最后交易日，但同页样例含周日20180930等自然月末，已记录该页内部冲突，等待真实SOURCE裁决；不能将正文或样例单独当作已确认行为。

2026-09-13 最终审查修复将已完成的公开合同复核同步到JSON：19项ROW_LIMIT、1项CALENDAR_COVERAGE、20项UNKNOWN（11项原始未知规则、daily/forecast/dividend及6项既定SINGLE_ONLY）。每条已记录规则引用对应官方章节；UNKNOWN仍严格为null阈值与空完整性引用。索引逐项保留失败SOURCE身份 `issue018-t13-source-20260912T123840Z`、RANGE TASK未执行及真实语义缺口。34 NEEDS_VERIFICATION、6 SINGLE_ONLY、0 AVAILABLE不变，所有历史run/case、状态、策略版本和决定引用均不改写。公开合同索引不是生产规则核验或账户可用性结论。

### stock_basic

- 官方来源：[stock_basic](https://tushare.pro/document/2?doc_id=25)；本次获取 UTC `2026-09-12T12:10:56.635809+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`e500e7b8faf1a233708b60aece403d744068c885cec321a5863ae942ecb79ba4`；HTML SHA-256：`b73a84218881ab6596db2fc1cdde25bb009c6d258e238579e0d93c0042693ca0`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/stock_basic.html`，缓存不保证永久保留。
- 正文准确引文：接口：stock_basic，可以通过 数据工具 调试和查看数据 描述：获取基础信息数据，包括股票代码、名称、上市日期、退市日期等 限量：每次最多返回6000行数据（覆盖全市场A股，会随股票总数增长而增加） 权限：2000积分起，每分钟请求50次。此接口是基础信息，调取一次就可以拉取完，建议保存倒本地存储后使用
- 输入表准确引文：ts_code str N TS股票代码( 格式说明 )；list_status str N 上市状态 L上市 D退市 P暂停上市 G 未交易 UN未上市，默认是L；exchange str N 交易所 SSE上交所 SZSE深交所 BSE北交所
- 设计范围：SINGLE，输出轴 `无RANGE`。待核对：ts_code+list_status与目标状态一致
- T14早期结论（历史）：按设计保持SINGLE_ONLY；新完整SINGLE轮的本接口全部样本及SQL通过，不承诺完整历史，也不新增日期区间入口。

### stock_company

- 官方来源：[stock_company](https://tushare.pro/document/2?doc_id=112)；本次获取 UTC `2026-09-12T12:10:56.640125+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`fe088a5c058ca15d2cf880613a8c47c98052629ebbb9fc656bd9d77fc6bb8be9`；HTML SHA-256：`43aa31ac4c5c690ee0ef752ccf354d590e226482a06e2913244b868a53f5b4b4`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/stock_company.html`，缓存不保证永久保留。
- 正文准确引文：接口：stock_company，可以通过 数据工具 调试和查看数据。 描述：获取上市公司基础信息，单次提取4500条，可以根据交易所分批提取 积分：用户需要至少120积分才可以调取，具体请参阅 积分获取办法
- 输入表准确引文：ts_code str N 股票代码；exchange str N 交易所代码 ，SSE上交所 SZSE深交所 BSE北交所
- 设计范围：SINGLE，输出轴 `无RANGE`。待核对：ts_code+exchange必须一致
- T14早期结论（历史）：按设计保持SINGLE_ONLY；新完整SINGLE轮的本接口全部样本及SQL通过，不承诺完整历史，也不新增日期区间入口。

### income

- 官方来源：[income](https://tushare.pro/document/2?doc_id=33)；本次获取 UTC `2026-09-12T12:10:55.725343+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`f06663992c4239ae02f59fef93a46e6153e489123b22dce3dcaf3ae8abd435ba`；HTML SHA-256：`cf9c0591e3cb2e61469d12c8ea61af13134e1d7d078578fad2e6327f131e7b48`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/income.html`，缓存不保证永久保留。
- 正文准确引文：接口：income，可以通过 数据工具 调试和查看数据。 描述：获取上市公司财务利润表数据 积分：用户需要至少2000积分才可以调取，具体请参阅 积分获取办法 提示：当前接口只能按单只股票获取其历史数据，如果需要获取某一季度全部上市公司数据，请使用income_vip接口（参数一致），需积攒5000积分。
- 输入表准确引文：ts_code str Y 股票代码；ann_date str N 公告日期（YYYYMMDD格式，下同）；start_date str N 公告日开始日期；end_date str N 公告日结束日期；period str N 报告期(每个季度最后一天的日期，比如20171231表示年报，20170630半年报，20170930三季报)
- 设计范围：S/N，输出轴 `ann_date`。待核对：公告区间，普通单股票；补完整提取规则
- T14早期结论（历史）：NEEDS_VERIFICATION；新完整SINGLE及SQL通过，RANGE未验收。两股票新SOURCE各2行均已证明ann_date在区间、end_date在区间外；完整性仍UNKNOWN，尚缺有效整段/两端全套SOURCE及RANGE TASK。旧失败SOURCE身份仍保留，不能复用其PASS子集作为清洁整轮。

### balancesheet

- 官方来源：[balancesheet](https://tushare.pro/document/2?doc_id=36)；本次获取 UTC `2026-09-12T12:10:55.725475+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`9ff1caf2c51dadcdc7a31b790ae517d82a6dc7aaa6c67035cf85889dacfae76d`；HTML SHA-256：`717bfce9cf2c70d4402935076c77a01e7fdc7981e3796f3dd47b7da66919eda0`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/balancesheet.html`，缓存不保证永久保留。
- 正文准确引文：接口：balancesheet，可以通过 数据工具 调试和查看数据。 描述：获取上市公司资产负债表 积分：用户需要至少2000积分才可以调取，具体请参阅 积分获取办法 提示：当前接口只能按单只股票获取其历史数据，如果需要获取某一季度全部上市公司数据，请使用balancesheet_vip接口（参数一致），需积攒5000积分。
- 输入表准确引文：ts_code str Y 股票代码；ann_date str N 公告日期(YYYYMMDD格式，下同)；start_date str N 公告日开始日期；end_date str N 公告日结束日期；period str N 报告期(每个季度最后一天的日期，比如20171231表示年报，20170630半年报，20170930三季报)
- 设计范围：S/N，输出轴 `ann_date`。待核对：公告区间与报告期区分；补完整性
- T14早期结论（历史）：NEEDS_VERIFICATION；新完整SINGLE及SQL通过，RANGE未验收。公告窗2行；两股票SINGLE与两端为空。旧失败SOURCE身份仍保留，不能复用其PASS子集作为清洁整轮。

### cashflow

- 官方来源：[cashflow](https://tushare.pro/document/2?doc_id=44)；本次获取 UTC `2026-09-12T12:10:55.728926+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`6e6a62784b8262dde66bc49cea73e737696baac0ce55a54004918e81978ef0b6`；HTML SHA-256：`906e819056eb2ab63f73c904756bcb5a559105054cbd242ba058a3dde6c7fb80`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/cashflow.html`，缓存不保证永久保留。
- 正文准确引文：接口：cashflow，可以通过 数据工具 调试和查看数据。 描述：获取上市公司现金流量表 积分：用户需要至少2000积分才可以调取，具体请参阅 积分获取办法 提示：当前接口只能按单只股票获取其历史数据，如果需要获取某一季度全部上市公司数据，请使用cashflow_vip接口（参数一致），需积攒5000积分。
- 输入表准确引文：ts_code str Y 股票代码；ann_date str N 公告日期（YYYYMMDD格式，下同）；start_date str N 公告日开始日期；end_date str N 公告日结束日期；period str N 报告期(每个季度最后一天的日期，比如20171231表示年报，20170630半年报，20170930三季报)
- 设计范围：S/N，输出轴 `ann_date`。待核对：不用f_ann_date代替ann_date；补完整性
- T14早期结论（历史）：NEEDS_VERIFICATION；新完整SINGLE及SQL通过，RANGE未验收。公告窗1行；两股票SINGLE与两端为空，未观察f_ann_date对照。旧失败SOURCE身份仍保留，不能复用其PASS子集作为清洁整轮。

### fina_indicator

- 官方来源：[fina_indicator](https://tushare.pro/document/2?doc_id=79)；本次获取 UTC `2026-09-12T12:10:56.146773+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`685b6c80dd754f207ef9e35efd58aca352aed7e68e1b06a741fad9400aed01c2`；HTML SHA-256：`36e943d50ae8c175422a9e14b702c21bdeaa53716416ce53ac3e892eb5794db6`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/fina_indicator.html`，缓存不保证永久保留。
- 正文准确引文：接口：fina_indicator，可以通过 数据工具 调试和查看数据。 描述：获取上市公司财务指标数据，为避免服务器压力，现阶段每次请求最多返回100条记录，可通过设置日期多次请求获取更多数据。 权限：用户需要至少2000积分才可以调取，具体请参阅 积分获取办法 提示：当前接口只能按单只股票获取其历史数据，如果需要获取某一季度全部上市公司数据，请使用fina_indicator_vip接口（参数一致），需积攒5000积分。
- 输入表准确引文：ts_code str Y TS股票代码,e.g. 600001.SH/000001.SZ；ann_date str N 公告日期；start_date str N 报告期开始日期；end_date str N 报告期结束日期；period str N 报告期(每个季度最后一天的日期,比如20171231表示年报)
- 设计范围：S/N，输出轴 `end_date`。待核对：报告期；ann_date在区间外合法；满额证据分层
- ISSUE-031前记录（历史）：NEEDS_VERIFICATION；ISSUE-022两股票整段5/4行、两端均非空，报告期在内公告在外同一行证据成立；000001.SZ下端2行保留不按报告期去重。候选策略及RANGE TASK/SQL交ISSUE-026。 新完整SINGLE及SQL通过，旧SOURCE失败/空观察仍保存在原runs。

- ISSUE-031当前结论：NEEDS_VERIFICATION / tushare-range-v3；真实v2 TASK失败ADAPTER_TYPE_INVALID且SQL零写入，根因未确证，候选已撤回。报告期/原字段/业务键/内部100阈值保留；不得将来源PASS或空SINGLE当作修复证据，见[失败保全与撤回](ISSUE-031-range-live-task-verification.md#失败保全与撤回)。

### fina_audit

- 官方来源：[fina_audit](https://tushare.pro/document/2?doc_id=80)；本次获取 UTC `2026-09-12T12:10:55.749686+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`4350c9d8167a65fa3ebb92e33fca316d7bd05d70cf9c3ec0d8a951feb276dbd5`；HTML SHA-256：`85b3fea9022fc639a5f3f8a34dc2385ec9db2346541ae00593032736b8cfdaaa`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/fina_audit.html`，缓存不保证永久保留。
- 正文准确引文：接口：fina_audit 描述：获取上市公司定期财务审计意见数据 权限：用户需要至少2000积分才可以调取，具体请参阅 积分获取办法
- 输入表准确引文：ts_code str Y 股票代码；ann_date str N 公告日期；start_date str N 公告开始日期；end_date str N 公告结束日期；period str N 报告期(每个季度最后一天的日期,比如20171231表示年报)
- 设计范围：S/N，输出轴 `ann_date`。待核对：公告区间不传period；补完整性
- T14早期结论（历史）：NEEDS_VERIFICATION；新完整SINGLE及SQL通过，RANGE未验收。全部固定样本为空。旧失败SOURCE身份仍保留，不能复用其PASS子集作为清洁整轮。

### fina_mainbz

- 官方来源：[fina_mainbz](https://tushare.pro/document/2?doc_id=81)；本次获取 UTC `2026-09-12T12:10:56.306344+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`2da28b66b7abde03111a86729d59682e59a1e3c60a180bbb17df472176accd03`；HTML SHA-256：`b42cc26f193e80347d969716102a9a322405764fd39335eb88761b542760552b`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/fina_mainbz.html`，缓存不保证永久保留。
- 正文准确引文：接口：fina_mainbz 描述：获得上市公司主营业务构成，分地区/产品/行业等方式。 权限：用户需要至少2000积分才可以调取，具体请参阅 积分获取办法 ，单次最大提取100行，总量不限制，可循环获取。 提示：当前接口只能按单只股票获取其历史数据，如果需要获取某一季度全部上市公司数据，请使用fina_mainbz_vip接口（参数一致），需积攒5000积分。
- 输入表准确引文：ts_code str Y 股票代码；period str N 报告期(每个季度最后一天的日期,比如20171231表示年报)；type str N 类型：P-按产品 D-按地区 I-按行业（请输入大写字母）；start_date str N 报告期开始日期；end_date str N 报告期结束日期
- 设计范围：S/N，输出轴 `end_date`。待核对：未传type的实际默认范围；业务键无类型，不混P/D/I
- T14收尾结论（历史）：NEEDS_VERIFICATION；新完整SINGLE及SQL通过，RANGE未验收。省略type的默认分类集合仍未明确，禁止混P/D/I拼接；公开ROW_LIMIT=100只记录已发布合同；新完整SINGLE两股票各150行且SQL通过，SINGLE/RANGE上界适用范围尚未核定；旧SINGLE的SQL观察失败及cleanup FAILED保留为历史，不否定新74项清洁SINGLE轮，也不能追认旧run。旧失败SOURCE身份仍保留，不能复用其PASS子集作为清洁整轮。

- ISSUE-020当前观察：独立两轮18case中14 PASS、4达到候选100而EVIDENCE_MISSING。两股票SINGLE及RANGE均实际返回P/D/I；SINGLE各150、2025全年74/110、六年宽窗各150，报告期整段/单日/上下边界已补齐。所有新样本键唯一、跨类共用键0；不构成全历史无冲突保证。100并非本轮RANGE硬上限，150也未获确认为上限；用户已明确同意[工程采用方案A](../issues/proposals/ISSUE-020-fina-mainbz-default-type.md#决策记录)：默认上游原样分类、SINGLE单次快照、RANGE100工程拆分阈值，生产NEEDS_VERIFICATION/v1保持。

- ISSUE-031当前结论：AVAILABLE / tushare-range-v2；本接口全部固定TASK、SQL、代表场景与清洁运行/独立审查成立，见[本项验收](ISSUE-031-range-live-task-verification.md)。

### stk_rewards

- 官方来源：[stk_rewards](https://tushare.pro/document/2?doc_id=194)；本次获取 UTC `2026-09-12T12:10:56.490495+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`0139fbf20bc0333a46f0d956a4addd5bece2fce9bba289c053dcad3941ca3ec3`；HTML SHA-256：`2d9beb20ec322d1c48e9cf1b898d441bfd0fe31c4f3448496b664191dadf4225`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/stk_rewards.html`，缓存不保证永久保留。
- 正文准确引文：接口：stk_rewards 描述：获取上市公司管理层薪酬和持股 积分：用户需要2000积分才可以调取，5000积分以上频次相对较高，具体请参阅 积分获取办法
- 输入表准确引文：ts_code str Y TS股票代码，支持单个或多个代码输入；end_date str N 报告期
- 设计范围：SINGLE，输出轴 `无RANGE`。待核对：仅ts_code；单报告期不推成区间
- T14早期结论（历史）：按设计保持SINGLE_ONLY；新完整SINGLE轮的本接口全部样本及SQL通过，不承诺完整历史，也不新增日期区间入口。

### stk_holdernumber

- 官方来源：[stk_holdernumber](https://tushare.pro/document/2?doc_id=166)；本次获取 UTC `2026-09-12T12:10:56.137287+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`30328d1d54175d39d8400e713b1c3c067c1e429d735b0910f9afd77d14e64094`；HTML SHA-256：`92003ae3541b63110c9efac2405934892e1c55aedcbd874a06ade99e2f62d8fe`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/stk_holdernumber.html`，缓存不保证永久保留。
- 正文准确引文：接口：stk_holdernumber 描述：获取上市公司股东户数数据，数据不定期公布 限量：单次最大3000,总量不限制 积分：2000积分可调取，基础积分每分钟调取200次，5000积分以上频次相对较高。具体请参阅 积分获取办法
- 输入表准确引文：ts_code str N TS股票代码；ann_date str N 公告日期；enddate str N 截止日期；start_date str N 公告开始日期；end_date str N 公告结束日期
- 设计范围：S/N，输出轴 `ann_date`。待核对：区间end_date、输入enddate和输出截止日不同义
- T14早期结论（历史）：NEEDS_VERIFICATION；ISSUE-022两股票公告日20260815/20260828，整段、单日及事件位于上下边界共8项非空；同一行ann_date在内、截止日end_date在外，输入enddate未加入。候选策略及RANGE TASK/SQL交ISSUE-026。 新完整SINGLE及SQL通过，旧SOURCE失败/空观察仍保存在原runs。

### trade_cal

- 官方来源：[trade_cal](https://tushare.pro/document/2?doc_id=26)；本次获取 UTC `2026-09-12T12:10:55.589565+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`246e412ddc1eed4b6a2c6a692f4d59368bbbdf83be4b48a6ea2230d2a3310166`；HTML SHA-256：`4921eeacfd4cb2c94aa0b7dd43ddaf20004cc10be903ef9c9c71ab7addd8f8a0`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/trade_cal.html`，缓存不保证永久保留。
- 正文准确引文：接口：trade_cal，可以通过 数据工具 调试和查看数据。 描述：获取各大交易所交易日历数据,默认提取的是上交所。 三大交易所的交易日历都是一样的，北交所交易日历参考上交所和深交所。 积分：需2000积分
- 输入表准确引文：exchange str N 交易所 SSE上交所,SZSE深交所,CFFEX 中金所,SHFE 上期所,CZCE 郑商所,DCE 大商所,INE 上能源；start_date str N 开始日期 （格式：YYYYMMDD 下同）；end_date str N 结束日期
- 设计范围：E/N，输出轴 `cal_date`。待核对：SSE/SZSE完整日历、全休市；BSE直接输入独立核验
- T14收尾结论（历史）：NEEDS_VERIFICATION；新完整SINGLE及SQL通过，RANGE未验收。trade_cal-bse-direct在1次请求后BATCH_COMPLETENESS_UNCONFIRMED；不能据安全计数断言原响应为空或HTTP不支持；原2026年SSE/SZSE整段/两端来自失败SOURCE轮；新增SSE20180928–30完整3天及top_list日历观察不能补齐两交易所当前完整边界与BSE证据；官网有BJ参照沪深语义，但未列BSE直接输入；确定的BJ映射及实际BJ规划仍未验证，生产拒绝不变。旧失败SOURCE身份仍保留，不能复用其PASS子集作为清洁整轮。

- ISSUE-021当前结论：NEEDS_VERIFICATION/v1；14项新SSE/SZSE完整日历SOURCE覆盖整段、两端、国庆全休市、跨年和BJ参照窗口，成对开市日期一致。独立BSE新请求结构合法但0行，完整性失败，保留FAILED；不能据此断言HTTP不支持。BJ→SSE测试候选已实际非空验证，生产映射及TASK/SQL交ISSUE-026；直接BSE继续拒绝。 详见[三轮固定计划与事实](ISSUE-018-T14-runs.md#issue-021-日历交易所与交易日独立取证)和[任务输入](../issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-021-已交付输入)。

### margin

- 官方来源：[margin](https://tushare.pro/document/2?doc_id=58)；本次获取 UTC `2026-09-12T12:10:55.406423+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`6f3f9c10cafc815a476436455ae2de91e4a285dcd1f3fa453f49640f4190b943`；HTML SHA-256：`154cd0f895993c83b935da6cf4b842e832a954786c6f940ab1b2963d7693232c`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/margin.html`，缓存不保证永久保留。
- 正文准确引文：接口：margin 描述：获取融资融券每日交易汇总数据，交易所于每天8点30左右更新上一日数据，本接口最晚9点05分会更新完数据。 注：深交所和北交所每周五的数据在下周一上午更新。 限量：单次请求最大返回4000行数据，可根据日期循环 权限：2000积分可获得本接口权限，积分越高权限越大，具体参考 权限说明
- 输入表准确引文：trade_date str N 交易日期（格式：YYYYMMDD，下同）；start_date str N 开始日期；end_date str N 结束日期；exchange_id str N 交易所代码（SSE上交所SZSE深交所BSE北交所）
- 设计范围：I/N，输出轴 `trade_date`。待核对：保留exchange_id，核对返回交易所；BSE单列实际结果
- T14收尾结论（历史）：NEEDS_VERIFICATION；新完整SINGLE及SQL通过，RANGE未验收。新SOURCE的exchange_id=BSE返回6行；仍缺独立SZSE及有效整段/两端全套SOURCE和RANGE TASK，不据此推定BSE日历可用。旧失败SOURCE身份仍保留，不能复用其PASS子集作为清洁整轮。

- ISSUE-031前记录（历史）：NEEDS_VERIFICATION/v1；三交易所exchange_id=SSE/SZSE/BSE整段各6行、两端各1行，9项非空SOURCE通过且逐行归属正确。官网4000行合同与实际样本分别记录，参数/键未改；候选包及真实RANGE/SQL交ISSUE-026。 详见[三轮固定计划与事实](ISSUE-018-T14-runs.md#issue-021-日历交易所与交易日独立取证)和[任务输入](../issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-021-已交付输入)。

- ISSUE-031当前结论：AVAILABLE / tushare-range-v2；本接口全部固定TASK、SQL、代表场景与清洁运行/独立审查成立，见[本项验收](ISSUE-031-range-live-task-verification.md)。

### daily

- 官方来源：[daily](https://tushare.pro/document/2?doc_id=27)；本次获取 UTC `2026-09-12T12:10:55.046389+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`94468051958366cd41cd22ef03337a55957034d21b8733c44b15ba99b36d8083`；HTML SHA-256：`972db0b8f845d6dce1210369f285c7bd80f13ca9a5e988279a9699783dd9294a`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/daily.html`，缓存不保证永久保留。
- 正文准确引文：接口：daily，可以通过 数据工具 调试和查看数据 数据说明：交易日每天15点～16点之间入库。本接口是未复权行情，停牌期间不提供数据 调取说明：基础积分每分钟内可调取500次，每次6000条数据，一次请求相当于提取一个股票23年历史 描述：获取股票行情数据，或通过 通用行情接口 获取数据，包含了前后复权数据
- 输入表准确引文：ts_code str N 股票代码（支持多个股票同时提取，逗号分隔）；trade_date str N 交易日期（YYYYMMDD）；start_date str N 开始日期(YYYYMMDD)；end_date str N 结束日期(YYYYMMDD)
- 设计范围：S/N，输出轴 `trade_date`。待核对：多日、两端、重叠重下；停牌缺行不能按每天一行判错
- T14收尾结论（历史）：NEEDS_VERIFICATION；新完整SINGLE及SQL通过，RANGE未验收。每次6000条的措辞未明确最大截断边界；真实重叠更新TASK/SQL尚未观察。旧失败SOURCE身份仍保留，不能复用其PASS子集作为清洁整轮。
- ISSUE-031前记录（历史）：NEEDS_VERIFICATION；ISSUE-019采用方案A确认L=6000；两股票整段/端点/重叠SOURCE8项通过。真实重叠更新与RANGE TASK/SQL交ISSUE-026，当前生产准入不变。官方原文、工程采用决定与历史SOURCE分别保留。

- ISSUE-031当前结论：AVAILABLE / tushare-range-v2；本接口全部固定TASK、SQL、代表场景与清洁运行/独立审查成立，见[本项验收](ISSUE-031-range-live-task-verification.md)。

### weekly

- 官方来源：[weekly](https://tushare.pro/document/2?doc_id=144)；本次获取 UTC `2026-09-12T12:10:55.046733+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`699231136d50851351a728cab3e77579429755cf69cc505094bca93d2da6b64e`；HTML SHA-256：`27b02e23f287bdf4d21f1ac90707ad286b945dac749b8915ce073ebdb01da140`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/weekly.html`，缓存不保证永久保留。
- 正文准确引文：接口：weekly 描述：获取A股周线行情，本接口每周最后一个交易日更新，如需要使用每天更新的周线数据，请使用 日度更新的周线行情接口 。 限量：单次最大6000行，可使用交易日期循环提取，总量不限制 积分：用户需要至少2000积分才可以调取，具体请参阅 积分获取办法
- 输入表准确引文：ts_code str N TS代码 （ts_code,trade_date两个参数任选一）；trade_date str N 交易日期 （每周最后一个交易日期，YYYYMMDD格式）；start_date str N 开始日期；end_date str N 结束日期
- 设计范围：S/N，输出轴 `trade_date`。待核对：实际每周最后交易日，不能固定周五
- T14早期结论（历史）：NEEDS_VERIFICATION；ISSUE-022两股票整段各3行、两端及20240930周一最后交易日均非空，完整日历核对一致；2个休市周五空对照保留。候选策略及RANGE TASK/SQL交ISSUE-026。 新完整SINGLE及SQL通过，旧SOURCE失败/空观察仍保存在原runs。

### monthly

- 官方来源：[monthly](https://tushare.pro/document/2?doc_id=145)；本次获取 UTC `2026-09-12T12:10:55.048746+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`2a25f4ef85b80f1b9de02c5fde0719faed98ce0d446a92f805583bc05f467860`；HTML SHA-256：`3cbffadc8002fa2970fe4e3fb34e08c5db0fbf3882f974c24f5860c9250e6f24`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/monthly.html`，缓存不保证永久保留。
- 正文准确引文：接口：monthly 描述：获取A股月线数据 限量：单次最大4500行，总量不限制 积分：用户需要至少2000积分才可以调取，具体请参阅 积分获取办法
- 输入表准确引文：ts_code str N TS代码 （ts_code,trade_date两个参数任选一）；trade_date str N 交易日期 （每月最后一个交易日日期，YYYYMMDD格式）；start_date str N 开始日期；end_date str N 结束日期
- 设计范围：S/N，输出轴 `trade_date`。待核对：实际每月最后交易日，不能固定自然月末；官方正文与同页非交易日月末样例冲突，尚未实测裁决
- T14早期结论（历史）：NEEDS_VERIFICATION；ISSUE-022两股票20180928/20181031整段和两端有效，与两月完整日历最后开市日一致；2个20180930空对照保留，旧官网样例不作当前日期规范。候选策略及RANGE TASK/SQL交ISSUE-026。 新完整SINGLE及SQL通过，旧SOURCE失败/空观察仍保存在原runs。

### adj_factor

- 官方来源：[adj_factor](https://tushare.pro/document/2?doc_id=28)；本次获取 UTC `2026-09-12T12:10:55.049119+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`04d14dc60efb29c95720f571bc88a54d03f3d8358d9b020a0efc185fced48990`；HTML SHA-256：`6edba83aec7f155c7da7aecaf5535d2279213d6e1652454af9fd75a0b30d753e`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/adj_factor.html`，缓存不保证永久保留。
- 正文准确引文：接口：adj_factor，可以通过 数据工具 调试和查看数据。 更新时间：盘前9点15~20分完成当日复权因子入库 描述：本接口由Tushare自行生产，获取股票复权因子，可提取单只股票全部历史复权因子，也可以提取单日全部股票的复权因子。 积分要求：2000积分起，5000以上可高频调取
- 输入表准确引文：ts_code str N 股票代码；trade_date str N 交易日期(YYYYMMDD，下同)；start_date str N 开始日期；end_date str N 结束日期
- 设计范围：S/N，输出轴 `trade_date`。待核对：全历史声明不等于无限量；补完整提取规则
- T14早期结论（历史）：NEEDS_VERIFICATION；新完整SINGLE及SQL通过，RANGE未验收。全部历史查询能力声明没有完整提取规则；非空样本不能补齐该合同。旧失败SOURCE身份仍保留，不能复用其PASS子集作为清洁整轮。

### suspend_d

- 官方来源：[suspend_d](https://tushare.pro/document/2?doc_id=214)；本次获取 UTC `2026-09-12T12:10:55.260719+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`98aef54a789df2a8710ec8e79b82550098ba51aee0c9b2b894621ed5b9974841`；HTML SHA-256：`96615233147bbae817f1c6ae8f5d3fe60fef6f87337e51b80ba38d01e656a980`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/suspend_d.html`，缓存不保证永久保留。
- 正文准确引文：接口：suspend_d 更新时间：不定期 描述：至少需要2000积分 可以调用，5000积分可以获得更高的频次，按日期方式获取股票每日停复牌信息
- 输入表准确引文：ts_code str N 股票代码(可输入多值)；trade_date str N 交易日日期；start_date str N 停复牌查询开始日期；end_date str N 停复牌查询结束日期
- 设计范围：S/N，输出轴 `trade_date`。待核对：停复牌事件稀疏；补完整性，空样本不证明持续支持
- T14早期结论（历史）：NEEDS_VERIFICATION；新完整SINGLE及SQL通过，RANGE未验收。全部固定样本为空，缺停牌连续覆盖的实际样本。旧失败SOURCE身份仍保留，不能复用其PASS子集作为清洁整轮。

### daily_basic

- 官方来源：[daily_basic](https://tushare.pro/document/2?doc_id=32)；本次获取 UTC `2026-09-12T12:10:55.260497+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`30b092871df7e4aadb1bc4e2279b3051e5d3c30639039af719ca624382c20574`；HTML SHA-256：`3fcf5d044216fb62819dc3e81bca18b63b43e611f8c823e11d7a7627b730325d`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/daily_basic.html`，缓存不保证永久保留。
- 正文准确引文：接口：daily_basic，可以通过 数据工具 调试和查看数据。 更新时间：交易日每日15点～17点之间 描述：获取全部股票每日重要的基本面指标，可用于选股分析、报表展示等。单次请求最大返回6000条数据，可按日线循环提取全部历史。 积分：至少2000积分才可以调取，5000积分无总量限制，具体请参阅 积分获取办法
- 输入表准确引文：ts_code str Y 股票代码（二选一）；trade_date str N 交易日期 （二选一）；start_date str N 开始日期(YYYYMMDD)；end_date str N 结束日期(YYYYMMDD)
- 设计范围：S/N，输出轴 `trade_date`。待核对：股票条件与交易日期都有效，确认截断合同
- T14早期结论（历史）：AVAILABLE / tushare-range-v2；官方明确上界、干净优先SOURCE、完整SINGLE及同参数RANGE TASK/SQL均通过。整段与两端覆盖两股票；daily_basic另含跨年。见 [T14 RANGE结果](ISSUE-018-T14-runs.md#四接口-range-task-实际结果)。

### moneyflow

- 官方来源：[moneyflow](https://tushare.pro/document/2?doc_id=170)；本次获取 UTC `2026-09-12T12:10:55.265447+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`a3f640fd29d022e789b5dd88578ebf128f2eb7d61c7a402ce2ee827b0c54c21d`；HTML SHA-256：`e561c18d338b1f8f85d1f15c0017d38d04adbc8ee590f32b222d06a5c4f8dfb9`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/moneyflow.html`，缓存不保证永久保留。
- 正文准确引文：接口：moneyflow，可以通过 数据工具 调试和查看数据。 描述：获取沪深A股票资金流向数据，分析大单小单成交情况，用于判别资金动向，数据开始于2010年。 限量：单次最大提取6000行记录，总量不限制 积分：用户需要至少2000积分才可以调取，基础积分有流量控制，积分越多权限越大，请自行提高积分，具体请参阅 积分获取办法
- 输入表准确引文：ts_code str N 股票代码 （股票和时间参数至少输入一个）；trade_date str N 交易日期；start_date str N 开始日期；end_date str N 结束日期
- 设计范围：S/N，输出轴 `trade_date`。待核对：2010起历史说明与实际样本范围
- T14早期结论（历史）：AVAILABLE / tushare-range-v2；官方明确上界、干净优先SOURCE、完整SINGLE及同参数RANGE TASK/SQL均通过。整段与两端覆盖两股票；daily_basic另含跨年。见 [T14 RANGE结果](ISSUE-018-T14-runs.md#四接口-range-task-实际结果)。

### stk_limit

- 官方来源：[stk_limit](https://tushare.pro/document/2?doc_id=183)；本次获取 UTC `2026-09-12T12:10:55.260580+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`50d81b83cec39377018e8192ebcf28d5b14d085b4a1ce5f14163ab389389fdfd`；HTML SHA-256：`45a8e5d564f4150a60e997fb475f1a253b1bd5acb01a8bf99e0604640304ed40`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/stk_limit.html`，缓存不保证永久保留。
- 正文准确引文：接口：stk_limit 描述：获取全市场（包含A/B股和基金）每日涨跌停价格，包括涨停价格，跌停价格等，每个交易日9点左右更新当日股票涨跌停价格。 限量：单次最多提取5800条记录，可循环调取，总量不限制 积分：用户积2000积分可调取，单位分钟有流控，积分越高流量越大，请自行提高积分，具体请参阅 积分获取办法
- 输入表准确引文：ts_code str N 股票代码；trade_date str N 交易日期；start_date str N 开始日期；end_date str N 结束日期
- 设计范围：S/N，输出轴 `trade_date`。待核对：循环获取声明与每片截断规则分别核对
- T14早期结论（历史）：AVAILABLE / tushare-range-v2；官方明确上界、干净优先SOURCE、完整SINGLE及同参数RANGE TASK/SQL均通过。整段与两端覆盖两股票；daily_basic另含跨年。见 [T14 RANGE结果](ISSUE-018-T14-runs.md#四接口-range-task-实际结果)。

### top_list

- 官方来源：[top_list](https://tushare.pro/document/2?doc_id=106)；本次获取 UTC `2026-09-12T12:10:56.335764+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`db36b9299f1c7d47daf723aa3ac4b262269d9499fb38f2b5df854c09d78da5b8`；HTML SHA-256：`efa2404e4772fde6c0070c93c273822aa203710c516c7627b7bbff6a9bbaa640`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/top_list.html`，缓存不保证永久保留。
- 正文准确引文：接口：top_list 描述：龙虎榜每日交易明细 数据历史： 2005年至今 限量：单次请求返回最大10000行数据，可通过参数循环获取全部历史 积分：用户需要至少2000积分才可以调取，具体请参阅 积分获取办法
- 输入表准确引文：trade_date str Y 交易日期；ts_code str N 股票代码
- 设计范围：S/T，输出轴 `trade_date`。待核对：完整日历后才逐交易日传trade_date；BJ映射独立核验
- T14收尾结论（历史）：NEEDS_VERIFICATION；新完整SINGLE及SQL通过，RANGE未验收。新全休市SOURCE完整日历确认0开市/0叶子PASS；新SH确认6开市但证券为空，EVIDENCE_MISSING；SZ非空及完整边界仍缺；trade_cal开放依赖未闭环；BJ参照沪深有官方语义依据，但映射选择及实际BJ规划未验证。旧失败SOURCE身份仍保留，不能复用其PASS子集作为清洁整轮。

- ISSUE-031前记录（历史）：NEEDS_VERIFICATION/v1；000007.SZ、600318.SH各4个非空整段/事件/边界和1全休市SOURCE；920008.BJ同样5项均通过。先取得完整自然日日历再逐开市日，BJ日历SSE、证券代码保持BJ。SH/SZ/BJ全休市各0证券子请求；生产BJ仍拒绝，映射及真实日期序列持久化/SQL交ISSUE-026。 详见[三轮固定计划与事实](ISSUE-018-T14-runs.md#issue-021-日历交易所与交易日独立取证)和[任务输入](../issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-021-已交付输入)。

- ISSUE-031当前结论：AVAILABLE / tushare-range-v2；本接口全部固定TASK、SQL、代表场景与清洁运行/独立审查成立，见[本项验收](ISSUE-031-range-live-task-verification.md)。

### margin_detail

- 官方来源：[margin_detail](https://tushare.pro/document/2?doc_id=59)；本次获取 UTC `2026-09-12T12:10:55.423914+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`e210a4c8361f745c5e91680fe5bdcdcee98bb6cade88b271e255bddce8296c3d`；HTML SHA-256：`af3a73018d20859aa9132203421f06051d48650f2db028cad2413556ac4296fc`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/margin_detail.html`，缓存不保证永久保留。
- 正文准确引文：接口：margin_detail 描述：获取沪深两市每日融资融券明细，交易所于每天8点30左右更新上一日数据。 注：深交所和北交所每周五的数据在下周一上午更新。 限量：单次请求最大返回6000行数据，可根据日期循环 权限：2000积分可获得本接口权限，积分越高权限越大，具体参考 权限说明
- 输入表准确引文：trade_date str N 交易日期（格式：YYYYMMDD，下同）；ts_code str N TS代码；start_date str N 开始日期；end_date str N 结束日期
- 设计范围：S/N，输出轴 `trade_date`。待核对：股票/日期与业务键归属
- T14早期结论（历史）：AVAILABLE / tushare-range-v2；官方明确上界、干净优先SOURCE、完整SINGLE及同参数RANGE TASK/SQL均通过。整段与两端覆盖两股票；daily_basic另含跨年。见 [T14 RANGE结果](ISSUE-018-T14-runs.md#四接口-range-task-实际结果)。

### block_trade

- 官方来源：[block_trade](https://tushare.pro/document/2?doc_id=161)；本次获取 UTC `2026-09-12T12:10:55.445059+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`3f20dc912c1abd28347073fd391bb7a15b0e0723fb2f09eb208c29a3ff944766`；HTML SHA-256：`09de4ed63ab67cb8efa07276662fc748d0c58a08ab15be5fa3a9553539045f08`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/block_trade.html`，缓存不保证永久保留。
- 正文准确引文：接口：block_trade 描述：大宗交易 限量：单次最大1000条，总量不限制 积分：2000积分可调取，每分钟内限制次数，超过5000积分频次相对较高，具体请参阅 积分获取办法
- 输入表准确引文：ts_code str N TS代码（股票代码和日期至少输入一个参数）；trade_date str N 交易日期（格式：YYYYMMDD，下同）；start_date str N 开始日期；end_date str N 结束日期
- 设计范围：S/N，输出轴 `trade_date`。待核对：同股同日多条，不按股票日期先去重
- T14早期结论（历史）：NEEDS_VERIFICATION；ISSUE-023两基准股票各4个整段/单日/上下边界窗口均2行2键；官网20181227单日5行5键5种买卖方组合，保留同股同日多笔。真实RANGE TASK/SQL交ISSUE-026。 新完整SINGLE及SQL通过，旧失败/空SOURCE保存在原runs；当前不提前开放。

### slb_len

- 官方来源：[slb_len](https://tushare.pro/document/2?doc_id=331)；本次获取 UTC `2026-09-12T12:10:55.480699+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`cc9eb33cade4dc69436bc5b56f116d403a577603801b60a874affc52d7d711d5`；HTML SHA-256：`493a4716931bf8b0ebda316c3c06f1fd09974a44bae0cf23d09d6cda2a54b6a4`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/slb_len.html`，缓存不保证永久保留。
- 正文准确引文：接口：slb_len 描述：转融通融资汇总 限量：单次最大可以提取5000行数据，可循环获取所有历史 积分：2000积分每分钟请求200次，5000积分500次请求
- 输入表准确引文：trade_date str N 交易日期（YYYYMMDD格式，下同）；start_date str N 开始日期；end_date str N 结束日期
- 设计范围：D/N，输出轴 `trade_date`。待核对：无股票输入，融资汇总行不套股票校验；官网输出未含期限字段，实际行数由SOURCE核对
- T14早期结论（历史）：NEEDS_VERIFICATION；新完整SINGLE及SQL通过，RANGE未验收。全部固定样本为空，融资汇总实际基数未观察；官方输出没有期限字段。旧失败SOURCE身份仍保留，不能复用其PASS子集作为清洁整轮。

### slb_sec

- 官方来源：[slb_sec](https://tushare.pro/document/2?doc_id=332)；本次获取 UTC `2026-09-12T12:10:55.563824+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`f480202a784bad007078d5a03096cd3f161a43724bf331507ca65387b185c57c`；HTML SHA-256：`d9ea1e11c1bad1ba4411375f0a7f8281d44f107fbb5a6b8a8fa91517d4f836de`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/slb_sec.html`，缓存不保证永久保留。
- 正文准确引文：接口：slb_sec 描述：转融通转融券交易汇总 限量：单次最大可以提取5000行数据，可循环获取所有历史 积分：2000积分每分钟请求200次，5000积分500次请求
- 输入表准确引文：trade_date str N 交易日期（YYYYMMDD格式，下同）；ts_code str N 股票代码；start_date str N 开始日期；end_date str N 结束日期
- 设计范围：S/N，输出轴 `trade_date`。待核对：官网标停；记录有依据的历史窗口和实际可用范围
- T14早期结论（历史）：NEEDS_VERIFICATION；新完整SINGLE及SQL通过，RANGE未验收。新000001.SZ/20240620历史单日1行；仍缺两股票整段/两端及RANGE TASK，官方标停后的API可访问历史边界尚未核定；证监会2024-07-11业务暂停和2024-09-30存量了结不是Tushare API历史截止合同。旧失败SOURCE身份仍保留，不能复用其PASS子集作为清洁整轮。

### slb_sec_detail

- 官方来源：[slb_sec_detail](https://tushare.pro/document/2?doc_id=333)；本次获取 UTC `2026-09-12T12:10:55.565463+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`a56fb944c88f08d0c82b9183f49de88fda067cb4f74fc6a75e4b3daa8038667f`；HTML SHA-256：`7a4fb295b489158d941337f1bc303f902a88c4204a56a4b099a1787a8134ffe0`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/slb_sec_detail.html`，缓存不保证永久保留。
- 正文准确引文：接口：slb_sec_detail 描述：转融券交易明细 限量：单次最大可以提取5000行数据，可循环获取所有历史 积分：2000积分每分钟请求200次，5000积分500次请求
- 输入表准确引文：trade_date str N 交易日期（YYYYMMDD格式，下同）；ts_code str N 股票代码；start_date str N 开始日期；end_date str N 结束日期
- 设计范围：S/N，输出轴 `trade_date`。待核对：同上；当前空结果不能证明历史或持续更新
- T14早期结论（历史）：NEEDS_VERIFICATION；新完整SINGLE及SQL通过，RANGE未验收。新000001.SZ/20240620历史单日1行且字段通过；仍缺两股票整段/两端及RANGE TASK、期限/费率差异的代表样本和API可访问历史边界；证监会2024-07-11业务暂停和2024-09-30存量了结不是Tushare API历史截止合同。旧失败SOURCE身份仍保留，不能复用其PASS子集作为清洁整轮。

### forecast

- 官方来源：[forecast](https://tushare.pro/document/2?doc_id=45)；本次获取 UTC `2026-09-12T12:10:55.942717+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`30a3b83bac5faf375e5d3e815d75d869fd310ba646488185d04d840cef93f659`；HTML SHA-256：`834978aa0e0eec98b22fed323e2ba0db5d5f93a311a0d3065dc4a7bf08192820`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/forecast.html`，缓存不保证永久保留。
- 正文准确引文：接口：forecast，可以通过 数据工具 调试和查看数据。 描述：获取业绩预告数据 权限：用户需要至少2000积分才可以调取，每日20点~21点更新，单次3500行，具体请参阅 积分获取办法 提示：当前接口只能按单只股票获取其历史数据，如果需要获取某一季度全部上市公司数据，请使用forecast_vip接口（参数一致），需积攒5000积分。
- 输入表准确引文：ts_code str N 股票代码(二选一)；ann_date str N 公告日期 (二选一)；start_date str N 公告开始日期；end_date str N 公告结束日期；period str N 报告期(每个季度最后一天的日期，比如20171231表示年报，20170630半年报，20170930三季报)；type str N 预告类型(预增/预减/扭亏/首亏/续亏/续盈/略增/略减)
- 设计范围：S/N，输出轴 `ann_date`。待核对：保留必填股票，不因空结果改VIP
- T14收尾结论（历史）：NEEDS_VERIFICATION；新完整SINGLE及SQL通过，RANGE未验收。单次3500行的措辞未明确最大截断边界；全部固定样本为空。旧失败SOURCE身份仍保留，不能复用其PASS子集作为清洁整轮。
- ISSUE-031前记录（历史）：NEEDS_VERIFICATION；ISSUE-019采用方案A确认L=3500；两固定股票及官方样例的公告整段/两端包含性/单日SOURCE12项非空通过。RANGE TASK/SQL交ISSUE-026，当前生产准入不变。官方原文、工程采用决定与历史SOURCE分别保留。

- ISSUE-031当前结论：AVAILABLE / tushare-range-v2；本接口全部固定TASK、SQL、代表场景与清洁运行/独立审查成立，见[本项验收](ISSUE-031-range-live-task-verification.md)。

### express

- 官方来源：[express](https://tushare.pro/document/2?doc_id=46)；本次获取 UTC `2026-09-12T12:10:55.944665+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`f23248475ae2015db8c81337fad356dc2d41fea0cf75b79200a7b34d603bddd7`；HTML SHA-256：`b253d17fc1a34f7ebbad99c05b1e38190f6c5f889e4cace61ce72373128ff685`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/express.html`，缓存不保证永久保留。
- 正文准确引文：接口：express 描述：获取上市公司业绩快报 权限：用户需要至少2000积分才可以调取，具体请参阅 积分获取办法 提示：当前接口只能按单只股票获取其历史数据，如果需要获取某一季度全部上市公司数据，请使用express_vip接口（参数一致），需积攒5000积分。
- 输入表准确引文：ts_code str Y 股票代码；ann_date str N 公告日期；start_date str N 公告开始日期；end_date str N 公告结束日期；period str N 报告期(每个季度最后一天的日期,比如20171231表示年报，20170630半年报，20170930三季报)
- 设计范围：S/N，输出轴 `ann_date`。待核对：普通单股票；补完整提取规则
- T14早期结论（历史）：NEEDS_VERIFICATION；新完整SINGLE及SQL通过，RANGE未验收。全部固定样本为空。旧失败SOURCE身份仍保留，不能复用其PASS子集作为清洁整轮。

### dividend

- 官方来源：[dividend](https://tushare.pro/document/2?doc_id=103)；本次获取 UTC `2026-09-12T12:10:56.470250+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`e228bf4fe1c167347681e11ac4844facd3d2daea2bbacf4881a8d5770466ea33`；HTML SHA-256：`2d0eb65ee7d8bae576aa91f17748add56e5828376beb2b43c711adea3f909894`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/dividend.html`，缓存不保证永久保留。
- 正文准确引文：接口：dividend 描述：分红送股数据 权限：起始时间2000-01-01，单次查询返回2000行，每日20点~21点更新，用户需要至少2000积分才可以调取，具体请参阅 积分获取办法
- 输入表准确引文：ts_code str N TS代码；ann_date str N 公告日
- 设计范围：S/C，输出轴 `ann_date`。待核对：自然日含非交易日公告，不替换为交易日历
- T14收尾结论（历史）：NEEDS_VERIFICATION；新完整SINGLE及SQL通过，RANGE未验收。单次查询返回2000行未明确最大截断边界；已有20260815周六日期观察，但缺可复核公告事件依据及业务语义。旧失败SOURCE身份仍保留，不能复用其PASS子集作为清洁整轮。
- ISSUE-031前记录（历史）：NEEDS_VERIFICATION；ISSUE-019采用方案A确认逐自然日L=2000；两股票公告整段/两端包含性/单日SOURCE8项非空通过，周六事件有公开依据。10项空对照保留原EVIDENCE_MISSING及当前引用；ISSUE-026登记非空候选时明确选择28项匹配证据并保留全部历史，不将空状态改PASS。RANGE TASK/SQL尚未执行。官方原文、工程采用决定与历史SOURCE分别保留。

- ISSUE-031当前结论：AVAILABLE / tushare-range-v2；本接口全部固定TASK、SQL、代表场景与清洁运行/独立审查成立，见[本项验收](ISSUE-031-range-live-task-verification.md)。

### disclosure_date

- 官方来源：[disclosure_date](https://tushare.pro/document/2?doc_id=162)；本次获取 UTC `2026-09-12T12:10:56.472129+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`f7514ca908d053a32453b390c8b77a8065507dc030c9c05246d6e6465d70118e`；HTML SHA-256：`47939a450abb49e1aa0d5ead6fd3e8a6badc02d4beb748f7cc4420e59340954f`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/disclosure_date.html`，缓存不保证永久保留。
- 正文准确引文：接口：disclosure_date 描述：获取财报披露计划日期 限量：单次最大6000，总量不限制 积分：用户需要至少2000积分才可以调取，积分越多权限越大，具体请参阅 积分获取办法
- 输入表准确引文：ts_code str N TS股票代码；end_date str N 财报周期（每个季度最后一天的日期，比如20181231表示2018年年报，20180630表示中报)；ann_date str N 最新披露公告日
- 设计范围：S/C，输出轴 `ann_date`。待核对：最新披露公告，不承诺所有历史计划修改版本
- T14早期结论（历史）：NEEDS_VERIFICATION；ISSUE-023两基准最新公告20241009/20240813及两端、原报告期键重复复查成立，当前五列摘要匹配公开修订后预约日期；modify_date为空，未观察到跨时点变化，不承诺历史每版。实际更新入库交ISSUE-026。 新完整SINGLE及SQL通过，旧失败/空SOURCE保存在原runs；当前不提前开放。

### repurchase

- 官方来源：[repurchase](https://tushare.pro/document/2?doc_id=124)；本次获取 UTC `2026-09-12T12:10:55.944858+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`a38577f23eb9d9c24691b595f28116152b1e26f2cf72eb0d7b0d86fef49c0922`；HTML SHA-256：`dad913ca685137df6f63d2051d6ad178a9d4f1021df0135abcd083a5cd284088`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/repurchase.html`，缓存不保证永久保留。
- 正文准确引文：接口：repurchase 描述：获取上市公司回购股票数据 积分：用户需要至少2000积分才可以调取，具体请参阅 积分获取办法
- 输入表准确引文：ann_date str N 公告日期（任意填参数，如果都不填，单次默认返回2000条）；start_date str N 公告开始日期；end_date str N 公告结束日期
- 设计范围：D/N，输出轴 `ann_date`。待核对：默认无参数2000不是区间硬上限；合法多股票保留
- T14早期结论（历史）：NEEDS_VERIFICATION；新完整SINGLE及SQL通过，RANGE未验收。新SOURCE852行/585只股票，合法证券代码852、不可用0，多股票数量已观察；默认2000仅适用不传参，当前带日期请求完整性仍UNKNOWN，完整边界及RANGE TASK未闭环。旧失败SOURCE身份仍保留，不能复用其PASS子集作为清洁整轮。

### stk_holdertrade

- 官方来源：[stk_holdertrade](https://tushare.pro/document/2?doc_id=175)；本次获取 UTC `2026-09-12T12:10:56.142525+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`0b36342560fe093bfbe1e34f1213f5dc4bcc0766f8f788df06ca37d43150cb5d`；HTML SHA-256：`2f08e64760d6e41531a7086f284f18231898ae23f1482dcc366dd64e24294c20`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/stk_holdertrade.html`，缓存不保证永久保留。
- 正文准确引文：接口：stk_holdertrade 描述：获取上市公司增减持数据，了解重要股东近期及历史上的股份增减变化 限量：单次最大提取3000行记录，总量不限制 积分：用户需要至少2000积分才可以调取。基础积分有流量控制，积分越多权限越大，5000积分以上无明显限制，请自行提高积分，具体请参阅 积分获取办法
- 输入表准确引文：ts_code str N TS股票代码；ann_date str N 公告日期；start_date str N 公告开始日期；end_date str N 公告结束日期
- 设计范围：S/N，输出轴 `ann_date`。待核对：不按实际增减持起止日期校验
- T14早期结论（历史）：NEEDS_VERIFICATION；ISSUE-023两基准公告20210907/20241220各4窗口非空，单日中业务起止日均在公告范围外；原业务键不变。真实RANGE TASK/SQL交ISSUE-026。 新完整SINGLE及SQL通过，旧失败/空SOURCE保存在原runs；当前不提前开放。

### top10_holders

- 官方来源：[top10_holders](https://tushare.pro/document/2?doc_id=61)；本次获取 UTC `2026-09-12T12:10:56.308423+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`abfa3efb12517ae127778e6e1c52d6187fd3917725ae64e43c9570afd299e3b8`；HTML SHA-256：`24a8f5b3cbd7b89bab25e424a207d102afa39f4f933c4f10ae349285cbf1e823`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/top10_holders.html`，缓存不保证永久保留。
- 正文准确引文：接口：top10_holders 描述：获取上市公司前十大股东数据，包括持有数量和比例等信息 积分：需2000积分以上才可以调取本接口，5000积分以上频次会更高
- 输入表准确引文：ts_code str Y TS代码；period str N 报告期（YYYYMMDD格式，一般为每个季度最后一天）；ann_date str N 公告日期；start_date str N 报告期开始日期；end_date str N 报告期结束日期
- 设计范围：S/N，输出轴 `end_date`。待核对：报告期，公告日可越出区间；补完整性
- T14早期结论（历史）：NEEDS_VERIFICATION；新完整SINGLE及SQL通过，RANGE未验收。报告窗40行、上端10行；两股票SINGLE与下端为空，典型10条不能构造完整性规则。旧失败SOURCE身份仍保留，不能复用其PASS子集作为清洁整轮。

### top10_floatholders

- 官方来源：[top10_floatholders](https://tushare.pro/document/2?doc_id=62)；本次获取 UTC `2026-09-12T12:10:56.308599+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`0ccb51e424834a5fb2f0ed96e0796fe977bd2da7e2b970d512881104cce36acb`；HTML SHA-256：`e6bce8f5c309417189f6e19eefaa9f58277c6890857bc76f43f82d49cc825204`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/top10_floatholders.html`，缓存不保证永久保留。
- 正文准确引文：接口：top10_floatholders 描述：获取上市公司前十大流通股东数据 积分：需2000积分以上才可以调取本接口，5000积分以上频次会更高
- 输入表准确引文：ts_code str Y TS代码；period str N 报告期（YYYYMMDD格式，一般为每个季度最后一天）；ann_date str N 公告日期；start_date str N 报告期开始日期；end_date str N 报告期结束日期
- 设计范围：S/N，输出轴 `end_date`。待核对：同上
- T14早期结论（历史）：NEEDS_VERIFICATION；新完整SINGLE及SQL通过，RANGE未验收。报告窗40行、上端10行；两股票SINGLE与下端为空，典型10条不能构造完整性规则。旧失败SOURCE身份仍保留，不能复用其PASS子集作为清洁整轮。

### new_share

- 官方来源：[new_share](https://tushare.pro/document/2?doc_id=123)；本次获取 UTC `2026-09-12T12:10:55.620200+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`49d7de243cae92b566c68561450a5b3754cd70773fc8660daa4b0826c3fa0ecf`；HTML SHA-256：`4e3cb17e93d8a532d460648a9ff477213ec90da1825e675b0428b2599694a8c8`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/new_share.html`，缓存不保证永久保留。
- 正文准确引文：接口：new_share 描述：获取新股上市列表数据 限量：单次最大2000条，总量不限制 积分：用户需要至少120积分才可以调取，具体请参阅 积分获取办法
- 输入表准确引文：start_date str N 上网发行开始日期；end_date str N 上网发行结束日期
- 设计范围：D/N，输出轴 `ipo_date`。待核对：上网发行日期，不能用issue_date上市日期
- T14早期结论（历史）：NEEDS_VERIFICATION；ISSUE-022原非股票请求20180905～20180927整段10行、两端1/2行；同一行ipo_date申购与issue_date上市均不同，整段2行上市在外。候选策略及RANGE TASK/SQL交ISSUE-026。 新完整SINGLE及SQL通过，旧SOURCE失败/空观察仍保存在原runs。

### stk_managers

- 官方来源：[stk_managers](https://tushare.pro/document/2?doc_id=193)；本次获取 UTC `2026-09-12T12:10:55.945035+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`3c354d080c70afa5369808ffb191682574970c1ee975ebb26d6e13778c83ef54`；HTML SHA-256：`22c46acb64cb4935a8d27963c4334ad9ab951e6cf13f78ce41ad41484776ee1e`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/stk_managers.html`，缓存不保证永久保留。
- 正文准确引文：接口：stk_managers 描述：获取上市公司管理层 积分：用户需要2000积分才可以调取，5000积分以上频次相对较高，具体请参阅 积分获取办法
- 输入表准确引文：ts_code str N 股票代码，支持单个或多个股票输入；ann_date str N 公告日期（YYYYMMDD格式，下同）；start_date str N 公告开始日期；end_date str N 公告结束日期
- 设计范围：S/N，输出轴 `ann_date`。待核对：snapshot SINGLE之外的公告区间需真实证明
- T14早期结论（历史）：NEEDS_VERIFICATION；新完整SINGLE及SQL通过，RANGE未验收。公告窗上端为空；两股票snapshot不能替代两股票RANGE边界证据。旧失败SOURCE身份仍保留，不能复用其PASS子集作为清洁整轮。

### pledge_stat

- 官方来源：[pledge_stat](https://tushare.pro/document/2?doc_id=110)；本次获取 UTC `2026-09-12T12:10:56.474677+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`61eed4fe82c77c7cbd6c9a200f48c00a9aca03e7000ca6c4da53d03124ab7fe1`；HTML SHA-256：`375cd2cc439551a64d634ad024869d057be5d315338aa4415b24d6c08cf03612`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/pledge_stat.html`，缓存不保证永久保留。
- 正文准确引文：接口：pledge_stat 描述：获取股票质押统计数据 限量：单次最大1000 积分：用户需要至少2000积分才可以调取，具体请参阅 积分获取办法
- 输入表准确引文：ts_code str N 股票代码；end_date str N 截止日期
- 设计范围：SINGLE，输出轴 `无RANGE`。待核对：仅ts_code；截止日声明不足以构造日期枚举
- T14早期结论（历史）：按设计保持SINGLE_ONLY；新完整SINGLE轮的本接口全部样本及SQL通过，不承诺完整历史，也不新增日期区间入口。

### pledge_detail

- 官方来源：[pledge_detail](https://tushare.pro/document/2?doc_id=111)；本次获取 UTC `2026-09-12T12:10:56.144554+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`87918fca73c9321027d66dbeba5c6f324364aa75a6932c0d39ee3b2458d0f64e`；HTML SHA-256：`1730be36eb635b8a8762c2fc9f155542032e2b9c566e93f73ba9bfa1516b3a11`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/pledge_detail.html`，缓存不保证永久保留。
- 正文准确引文：接口：pledge_detail 描述：获取股票质押明细数据 限量：单次最大1000 积分：用户需要至少2000积分才可以调取，具体请参阅 积分获取办法
- 输入表准确引文：ts_code str N 股票代码；ann_date str N 公告日期；start_date str N 公告开始日期；end_date str N 公告结束日期
- 设计范围：S/N，输出轴 `ann_date`。待核对：输出start_date/end_date为质押业务日期
- T14早期结论（历史）：NEEDS_VERIFICATION；ISSUE-023经用户同意以000014.SZ/600000.SH完成非空整段和边界，公告与质押开始/结束/解押日期同一行对照成立；000001.SZ固定SINGLE仍0行并保留。此前“两股票各2行”为汇总错误，实际旧SINGLE源行数0/2。TASK/SQL交ISSUE-026。 新完整SINGLE及SQL通过，旧失败/空SOURCE保存在原runs；当前不提前开放。

### index_classify

- 官方来源：[index_classify](https://tushare.pro/document/2?doc_id=181)；本次获取 UTC `2026-09-12T12:10:56.656461+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`09e5aed3ce539e7a7f041af5b6559de11799329a15f69b063f8c8e0fce19742f`；HTML SHA-256：`20ea60b0f60d5ec72b7639563ed95b4f54abd55442df545f96a6a9560aae4aee`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/index_classify.html`，缓存不保证永久保留。
- 正文准确引文：接口：index_classify 描述：获取申万行业分类，可以获取申万2014年版本（28个一级分类，104个二级分类，227个三级分类）和2021年本版（31个一级分类，134个二级分类，346个三级分类）列表信息 权限：用户需2000积分可以调取，具体请参阅 积分获取办法
- 输入表准确引文：当前页面输入表没有本项筛选键。
- 设计范围：SINGLE，输出轴 `无RANGE`。待核对：无股票/日期输入，保留字典请求
- T14早期结论（历史）：按设计保持SINGLE_ONLY；新完整SINGLE轮的本接口全部样本及SQL通过，不承诺完整历史，也不新增日期区间入口。

### index_member_all

- 官方来源：[index_member_all](https://tushare.pro/document/2?doc_id=335)；本次获取 UTC `2026-09-12T12:10:56.658627+00:00`；HTTP GET成功（curl退出0），已取得接口正文。
- 正文 SHA-256：`8efca4b7e26ea1ce6edc1b7c601efbb31cb4d286b6e60492ad8c9f9a6747fc1c`；HTML SHA-256：`9c5b03ffaff0c2705a52b61d304ee5b430df4fb24ce35dde45420b5dca2af847`。正文指 `div.content.col-md-9` 的空格规范化文本，不含导航；本机原件 `/tmp/issue018-t13-official/index_member_all.html`，缓存不保证永久保留。
- 正文准确引文：接口：index_member_all 描述：按三级分类提取申万行业成分，可提供某个分类的所有成分，也可按股票代码提取所属分类，参数灵活 限量：单次最大2000行，总量不限制 权限：用户需2000积分可调取，积分获取方法请参阅 积分获取办法
- 输入表准确引文：ts_code str N 股票代码；is_new str N 是否最新（默认为“Y是”）
- 设计范围：SINGLE，输出轴 `无RANGE`。待核对：ts_code为股票，行业代码不是股票；保留默认最新归属语义
- T14早期结论（历史）：按设计保持SINGLE_ONLY；新完整SINGLE轮的本接口全部样本及SQL通过，不承诺完整历史，也不新增日期区间入口。

## 40项结果

此表与JSON当前决定一致。SOURCE列是所引用样本的聚合状态，仍须检查run退出和清理；SINGLE与RANGE分列，SINGLE PASS不等于RANGE可用。旧失败/未运行case保存在runs，不覆盖新清洁轮。

| API | SOURCE | SINGLE TASK / SQL | RANGE TASK / SQL | 当前处置 |
| --- | --- | --- | --- | --- |
| stock_basic | PASS | PASS | 不适用 | SINGLE_ONLY |
| stock_company | PASS | PASS | 不适用 | SINGLE_ONLY |
| income | PASS | PASS | NOT_RUN | NEEDS_VERIFICATION |
| balancesheet | PASS | PASS | NOT_RUN | NEEDS_VERIFICATION |
| cashflow | PASS | PASS | NOT_RUN | NEEDS_VERIFICATION |
| fina_indicator | PASS | PASS | FAILED | NEEDS_VERIFICATION |
| fina_audit | PASS | PASS | NOT_RUN | NEEDS_VERIFICATION |
| fina_mainbz | PASS | PASS | PASS | AVAILABLE |
| stk_rewards | PASS | PASS | 不适用 | SINGLE_ONLY |
| stk_holdernumber | PASS | PASS | NOT_RUN | NEEDS_VERIFICATION |
| trade_cal | PASS | PASS | EVIDENCE_MISSING | NEEDS_VERIFICATION |
| margin | PASS | PASS | PASS | AVAILABLE |
| daily | PASS | PASS | PASS | AVAILABLE |
| weekly | PASS | PASS | NOT_RUN | NEEDS_VERIFICATION |
| monthly | PASS | PASS | NOT_RUN | NEEDS_VERIFICATION |
| adj_factor | PASS | PASS | NOT_RUN | NEEDS_VERIFICATION |
| suspend_d | PASS | PASS | NOT_RUN | NEEDS_VERIFICATION |
| daily_basic | PASS | PASS | PASS | AVAILABLE |
| moneyflow | PASS | PASS | PASS | AVAILABLE |
| stk_limit | PASS | PASS | PASS | AVAILABLE |
| top_list | PASS | PASS | PASS | AVAILABLE |
| margin_detail | PASS | PASS | PASS | AVAILABLE |
| block_trade | PASS | PASS | NOT_RUN | NEEDS_VERIFICATION |
| slb_len | PASS | PASS | NOT_RUN | NEEDS_VERIFICATION |
| slb_sec | PASS | PASS | NOT_RUN | NEEDS_VERIFICATION |
| slb_sec_detail | PASS | PASS | NOT_RUN | NEEDS_VERIFICATION |
| forecast | PASS | PASS | PASS | AVAILABLE |
| express | PASS | PASS | NOT_RUN | NEEDS_VERIFICATION |
| dividend | PASS | PASS | PASS | AVAILABLE |
| disclosure_date | PASS | PASS | NOT_RUN | NEEDS_VERIFICATION |
| repurchase | PASS | PASS | NOT_RUN | NEEDS_VERIFICATION |
| stk_holdertrade | PASS | PASS | NOT_RUN | NEEDS_VERIFICATION |
| top10_holders | PASS | PASS | NOT_RUN | NEEDS_VERIFICATION |
| top10_floatholders | PASS | PASS | NOT_RUN | NEEDS_VERIFICATION |
| new_share | PASS | PASS | NOT_RUN | NEEDS_VERIFICATION |
| stk_managers | PASS | PASS | NOT_RUN | NEEDS_VERIFICATION |
| pledge_stat | PASS | PASS | 不适用 | SINGLE_ONLY |
| pledge_detail | PASS | PASS | NOT_RUN | NEEDS_VERIFICATION |
| index_classify | PASS | PASS | 不适用 | SINGLE_ONLY |
| index_member_all | PASS | PASS | 不适用 | SINGLE_ONLY |

逐项caseId、请求/节点及未发生字段见JSON；当前22个ROW_LIMIT包括19项原公开候选及ISSUE-019经用户确认采用的3项阈值，其中fina_mainbz已按ISSUE-020方案A明确仅作100工程阈值，不等于22项均可开放。没有EXCLUDED，34项RANGE目标不缩减。

### ISSUE-030前逐项证据缺口（历史）

`SOURCE_RUN_FAILED`指出旧SOURCE仍不可作为清洁证据，不表示新SINGLE失败。新增补证解决的观察已从当前缺口删除，原case不改写。

- `income`：OFFICIAL_COMPLETENESS_EVIDENCE_MISSING；RESPONSE_ONLY_IMPLEMENTATION_PENDING；RANGE_TASK_NOT_RUN；ISSUE-025两股票2024公告窗各4行及非空边界；同一行公告/报告期不同，年度窗各1行公告在窗而报告期在外。
- `balancesheet`：OFFICIAL_COMPLETENESS_EVIDENCE_MISSING；RESPONSE_ONLY_IMPLEMENTATION_PENDING；RANGE_TASK_NOT_RUN；ISSUE-025两股票2024公告窗6/7行及非空边界；公告与报告期同一行对照完成，不能由行数证明完整。
- `cashflow`：OFFICIAL_COMPLETENESS_EVIDENCE_MISSING；RESPONSE_ONLY_IMPLEMENTATION_PENDING；RANGE_TASK_NOT_RUN；ISSUE-025两股票2024公告窗6/5行及非空边界；ann/end不同，ann/f_ann共11行有效且均相同，没有观察到两公告列不同的实例，不能据此互换日期轴。
- `fina_indicator`：RANGE_TASK_NOT_RUN；ISSUE-022两股票整段5/4行、两端均非空，报告期在内公告在外同一行证据成立；000001.SZ下端2行保留不按报告期去重。候选策略及RANGE TASK/SQL交ISSUE-026。
- `fina_audit`：OFFICIAL_COMPLETENESS_EVIDENCE_MISSING；RESPONSE_ONLY_IMPLEMENTATION_PENDING；RANGE_TASK_NOT_RUN；ISSUE-025两股票年度窗各1行，公告20240315/20240430及单日边界非空，同一行报告期在2024年度窗外；旧空保持。
- `fina_mainbz`：RANGE_TASK_NOT_RUN；默认实际分类、SINGLE快照及100工程拆分阈值已按用户同意方案A采用，RANGE110/150与文档冲突仍保留为事实。两股票报告期整段/边界有效；4个旧满额SOURCE保持EVIDENCE_MISSING；ISSUE-029已另取得两股票全年/六年宽窗4项完整拆分SOURCE，完整身份见下文。当前interface引用和状态按旧事实保留，ISSUE-030负责候选引用，ISSUE-031消费原12项及新4项并完成匹配TASK/SQL后才可开放。
- `stk_holdernumber`：RANGE_TASK_NOT_RUN；ISSUE-022两股票公告日20260815/20260828，整段、单日及事件位于上下边界共8项非空；同一行ann_date在内、截止日end_date在外，输入enddate未加入。候选策略及RANGE TASK/SQL交ISSUE-026。
- `trade_cal`：RANGE_TASK_NOT_RUN；BSE直接输入本轮合法Envelope返回0行，完整性失败；官网未列BSE，继续明确拒绝，不能默默替换用户exchange。该负例保留，sourceStatus为FAILED不冒充整体PASS。；14项SSE/SZSE完整日历SOURCE已验证；ISSUE-026须以支持的SSE/SZSE参数及新候选包完成真实RANGE/SQL。
- `margin`：RANGE_TASK_NOT_RUN；SSE/SZSE/BSE整段及两端共9项非空SOURCE有效；保留exchange_id和4000行规则，候选包TASK/SQL待ISSUE-026。
- `daily`：RANGE_TASK_NOT_RUN；ISSUE-019采用方案A确认L=6000；两股票整段/端点/重叠SOURCE8项通过。真实重叠更新与RANGE TASK/SQL交ISSUE-026，当前生产准入不变。
- `weekly`：RANGE_TASK_NOT_RUN；ISSUE-022两股票整段各3行、两端及20240930周一最后交易日均非空，完整日历核对一致；2个休市周五空对照保留。候选策略及RANGE TASK/SQL交ISSUE-026。
- `monthly`：RANGE_TASK_NOT_RUN；ISSUE-022两股票20180928/20181031整段和两端有效，与两月完整日历最后开市日一致；2个20180930空对照保留，旧官网样例不作当前日期规范。候选策略及RANGE TASK/SQL交ISSUE-026。
- `adj_factor`：OFFICIAL_COMPLETENESS_EVIDENCE_MISSING；RESPONSE_ONLY_IMPLEMENTATION_PENDING；RANGE_TASK_NOT_RUN；ISSUE-025两股票跨年整段各4行及非空两端已验证；历史能力声明仍无未截断判据。
- `suspend_d`：OFFICIAL_COMPLETENESS_EVIDENCE_MISSING；RESPONSE_ONLY_IMPLEMENTATION_PENDING；RANGE_TASK_NOT_RUN；ISSUE-025两基准股票有限历史220/147行，S为211/142、R为9/5；非空两端及各连续五日验证完成，另有官网两股票各五日停牌。实际首末日不等于支持边界，日内时间非空0。
- `top_list`：RANGE_TASK_NOT_RUN；SH/SZ/BJ各5项SOURCE均有效（含各1全休市）；BJ→SSE仅测试Probe候选。ISSUE-026须实施生产映射并保留原BJ证券代码，依赖已验证trade_cal、候选版本和真实TASK/SQL；当前生产BJ仍拒绝。
- `block_trade`：RANGE_TASK_NOT_RUN；ISSUE-023两基准股票各4个整段/单日/上下边界窗口均2行2键；官网20181227单日5行5键5种买卖方组合，保留同股同日多笔。真实RANGE TASK/SQL交ISSUE-026。
- `slb_len`：RANGE_TASK_NOT_RUN；ISSUE-024新融资整段13行/13原键、一日一行及两端非空；20240711和20240930仍可查，无期限字段。历史保留起止/持续可访问承诺未取得，用户已采用方案A按历史查询能力验收，精确起止/持续保留保证仍未知。
- `slb_sec`：RANGE_TASK_NOT_RUN；ISSUE-024两股票20240601～20240620各13行，实际0603/0620边界非空；20240711各1行，0930～1001各0行保留。实际可查日不等于全历史截止，用户已采用方案A按历史查询能力验收，精确起止/持续保留保证仍未知。
- `slb_sec_detail`：RANGE_TASK_NOT_RUN；ISSUE-024两股票整段/边界非空；长窗242/190行、9/7期限、54/39费率，同股同日多期限41/33组，原键未去重。0711及0930～1001各0行保留；历史起止/持续可访问承诺未取得，用户已采用方案A按历史查询能力验收，精确起止/持续保留保证仍未知。
- `forecast`：RANGE_TASK_NOT_RUN；ISSUE-019采用方案A确认L=3500；两固定股票及官方样例的公告整段/两端包含性/单日SOURCE12项非空通过。RANGE TASK/SQL交ISSUE-026，当前生产准入不变。
- `express`：OFFICIAL_COMPLETENESS_EVIDENCE_MISSING；RESPONSE_ONLY_IMPLEMENTATION_PENDING；RANGE_TASK_NOT_RUN；ISSUE-025浦发20180106、平安20220114快报及两端窗口非空；平安旧2018窗0行与所有邻日空保持，未改股票或VIP。
- `dividend`：RANGE_TASK_NOT_RUN；ISSUE-019采用方案A确认逐自然日L=2000；两股票公告整段/两端包含性/单日SOURCE8项非空通过，周六事件有公开依据。10项空对照保留原EVIDENCE_MISSING及当前引用；ISSUE-026登记非空候选时明确选择28项匹配证据并保留全部历史，不将空状态改PASS。RANGE TASK/SQL尚未执行。
- `disclosure_date`：RANGE_TASK_NOT_RUN；ISSUE-023两基准最新公告20241009/20240813及两端、原报告期键重复复查成立，当前五列摘要匹配公开修订后预约日期；modify_date为空，未观察到跨时点变化，不承诺历史每版。实际更新入库交ISSUE-026。
- `repurchase`：OFFICIAL_COMPLETENESS_EVIDENCE_MISSING；RESPONSE_ONLY_IMPLEMENTATION_PENDING；RANGE_TASK_NOT_RUN；ISSUE-025原无股票202608公告窗852行/585股票、两端非空；无参数默认2000不适用于日期请求硬上限。
- `stk_holdertrade`：RANGE_TASK_NOT_RUN；ISSUE-023两基准公告20210907/20241220各4窗口非空，单日中业务起止日均在公告范围外；原业务键不变。真实RANGE TASK/SQL交ISSUE-026。
- `top10_holders`：OFFICIAL_COMPLETENESS_EVIDENCE_MISSING；RESPONSE_ONLY_IMPLEMENTATION_PENDING；RANGE_TASK_NOT_RUN；ISSUE-025两股票2017报告期窗40/60行及两端非空；浦发另有20170831/20170904非季末报告日期，同一行end在窗/ann在窗外已观察，不能按四季乘10枚举完整集合。
- `top10_floatholders`：OFFICIAL_COMPLETENESS_EVIDENCE_MISSING；RESPONSE_ONLY_IMPLEMENTATION_PENDING；RANGE_TASK_NOT_RUN；ISSUE-025两股票2017报告期窗54/46行及两端非空；同一行end在窗/ann在窗外已观察，业务名称不能作为服务器返回上限。
- `new_share`：RANGE_TASK_NOT_RUN；ISSUE-022原非股票请求20180905～20180927整段10行、两端1/2行；同一行ipo_date申购与issue_date上市均不同，整段2行上市在外。候选策略及RANGE TASK/SQL交ISSUE-026。
- `stk_managers`：OFFICIAL_COMPLETENESS_EVIDENCE_MISSING；RESPONSE_ONLY_IMPLEMENTATION_PENDING；RANGE_TASK_NOT_RUN；ISSUE-025两股票公告窗5/8行及非空两端；上任与公告全部不同、离任有效2/3行且与公告不同，缺失3/5行单计；snapshot未代替RANGE。
- `pledge_detail`：RANGE_TASK_NOT_RUN；ISSUE-023经用户同意以000014.SZ/600000.SH完成非空整段和边界，公告与质押开始/结束/解押日期同一行对照成立；000001.SZ固定SINGLE仍0行并保留。此前“两股票各2行”为汇总错误，实际旧SINGLE源行数0/2。TASK/SQL交ISSUE-026。

## T14早期代表场景（历史）

新daily_basic跨年SOURCE及RANGE TASK/SQL已通过（20251229–20260105）；四项两股票整段/两端的任务闭环成立。daily自己的重叠更新TASK、disclosure_date最新公告修订、部分特殊事件与日历依赖仍未闭环，不能由四项通过替代。

新income两股票均有同一行公告在窗、报告期在窗外；fina_indicator两股票各有1行相反对照；repurchase852行/585股票。新monthly历史月窗输出20180928，20180930单日为空；slb两项在20240620各1行。这些SOURCE观察没有补成对应RANGE TASK或全部历史边界，详见T14追加章节。

新top_list休市窗为0开市/0叶子PASS，SH为6开市但证券空，仍EVIDENCE_MISSING；margin BSE新SOURCE6行PASS。旧trade_cal BSE的BATCH_COMPLETENESS_UNCONFIRMED不变，不能从融资数据推定日历支持。stock_basic输入L与归属检查不能代替未返回的list_status；fina_mainbz默认分类及100/150适用关系仍待上游澄清。

## T14早期受控边界（历史）

[T12基础设施证据](ISSUE-018-task-infrastructure.md)记录已实际执行的受控完整性/故障验证，可复用但不计为真实来源：

- `TushareBatchPoliciesTest.confirmedThresholdUsesRawRowsIncludingEqualityAndSingleDay`：原始行数小于/等于/大于候选L，包含单日满额。
- `DownloadTaskRunnerTest.unknownAndUnsplittableResponsesNeverCommit`、`splitParentDoesNotAdaptOrConsumeSuccessfulRowBudget`：UNKNOWN/最小满额不提交，SPLIT父不计证券成功。
- `TushareTradeCalendarTest.missingDuplicateAndOpenOnlyCalendarsNeverBecomePartialPlans`、`TushareBatchDownloadTest.wholeClosedCalendarMakesEmptyPlanButEmptyResponseFailsAndNoResultsAreCached`：先完整日历后筛开市，合法全休市与缺日历区分。
- `DownloadTaskRepositoryIT.planAndSplitAreAtomicOnSecondInsertFailure`、`BatchCommitServiceIT.successUpdateFailureRollsBackBothNewAndUpdatedSecurities`：真实MySQL原子拆分和证券/成功共同回滚。
- T12最终G1=1282、G2=468、G3=1025、G4=1028、G5=3、G6=126，均为T12历史完成证据；本次未声称重新执行。

## T14早期未完成事项（历史）

- 11项上游完整性仍UNKNOWN；2026-09-14用户“可以接受不完整”，已采用[RESPONSE_ONLY合同](../issues/proposals/ISSUE-025-extraction-contracts.md#决策记录)。产品承诺已决定，生产语义/能力/页面及TASK/SQL仍待ISSUE-026落实。daily/forecast/dividend已按ISSUE-019用户同意方案A采用6000/3500/2000阈值；fina_mainbz已按ISSUE-020用户同意方案A采用默认实际分类、SINGLE快照及100工程阈值，原文档/实测矛盾不冒充上游已解释。它们的RANGE TASK/SQL仍待ISSUE-026完成。公开补证范围见 [T14官方补证](ISSUE-018-T14-official-evidence.md)。
- 其余30项RANGE尚待真实TASK/SQL；ISSUE-019～025已交付其负责接口的采用合同与SOURCE，其中BJ/BSE来源结论已明确，生产BJ映射留待候选实现。历史起止/保留承诺按ISSUE-024已接受口径继续注明未知；公告修订的实际更新入库及各代表场景TASK/SQL仍待验证。后续若需新SOURCE，须先有依据并登记固定轮次。
- 全部SINGLE已由新74任务清洁轮完成；旧bootstrap、SQL观察和源码身份失败保持历史，既不重试也不追认。
- 四项策略及相应能力预期已更新v2；六条当前源码门禁的实际结果见 [运行登记](ISSUE-018-T14-runs.md)。完整发布脚本未运行，main/干净输入/HEAD前置未满足；不自动提交、合并或发布制造条件。
- T14尚未满足Acceptance第4/6项，T13及母issue不标COMPLETED；当前范围不缩减，不创建T15。保留所有账户库和成功数据，恢复条件归属 [T14交接](../task-handoffs/ISSUE-018/ISSUE-018-T14-handoff.md)。

## T14 新固定四接口 SOURCE（2026-09-13）

运行 `issue018-t14-priority-source-20260913T092938Z` 全33项PASS、33来源请求、exit0/cleanup PASS、输入稳定；独立重建两包及新库准备见 [T14运行登记](ISSUE-018-T14-runs.md)。四接口的两股票SINGLE、两股票整段/两端及daily_basic跨年来源证据均成立，该SOURCE阶段尚无TASK/SQL或AVAILABLE；后续完整SINGLE及25项RANGE结果见下文。完整新run追加于原索引，原三轮329 case保持不变。

## T14 历史与日期语义补证（2026-09-13）

完整固定运行 `issue018-t14-history-source-20260913T093708Z`：6请求，5 PASS / 1 EVIDENCE_MISSING，exit0 / cleanup PASS / 输入稳定，全部case原样追加索引，旧run不变。实际日期与引用见 [运行登记](ISSUE-018-T14-runs.md#历史与月线-source-实际结果)。

- monthly：000001.SZ 的2018年9月整段返回唯一日期20180928；独立20180928单日同样返回1行，20180930单日返回0行。SSE历史完整日历28–30共3自然日、1开市。当前此样本符合最后交易日，并说明官网20180930示例不能直接用作当前返回日期规范；没有扩成所有月份/第二股票的结论，空单日仍EVIDENCE_MISSING。
- slb_sec / slb_sec_detail：000001.SZ/20240620各返回1行，股票/日期/字段通过；本账户至少可读取这个官网示例历史日。证监会业务暂停生效为2024-07-11、存量最迟2024-09-30了结，但不能将这些日期当Tushare API停用或完整历史终点。
- 11项UNKNOWN与daily/forecast/dividend含糊限量本次公开HTML/静态文档仍未给出可核验的新完整提取规则。fina_mainbz官方bz_code定义P/D/I，但默认集合及SINGLE150与官网100矛盾仍未解释。详见 [公开补证](ISSUE-018-T14-official-evidence.md)。

上述新增均为SOURCE/公开事实，无RANGE TASK/SQL，不自动开放monthly或slb接口。

## T14 完整 SINGLE TASK（2026-09-13）

`issue018-t14-single-20260913T093339Z` 已完成40API/74TASK/148records，74/74PASS、exit0/cleanupPASS、输入稳定，fixture另2提交/3查询。SQL来源5265/插入5265/更新0；每API样本及来源/股票/业务键/第一股票历史保留事实见 [T14完整SINGLE结果](ISSUE-018-T14-runs.md#完整-single-实际结果)。这是完整的新轮，不是对旧15任务或旧14PASS的追认；旧全部run/case保留。SINGLE成功不解除RANGE完整性或fina_mainbz默认type与100/150冲突。

## T14 同一行日期、多股票及旧未执行项补证（2026-09-13）

新固定SOURCE `issue018-t14-representative-source-20260913T094124Z` 共8项、14请求，7PASS/1EVIDENCE_MISSING、exit0/cleanupPASS，输入稳定：income两股票各2行均公告日落入区间而报告期在外；fina_indicator两股票各有1行报告期在区间而公告日在外；repurchase852行均合法证券代码，实际585只股票。只追加本轮安全整数投影，不回填旧run。

原三个NOT_RUN分别在新case执行：top_list休市窗以完整日历确认0开市/0叶子并PASS；SH日历确认6开市但证券全空，保留EVIDENCE_MISSING；margin以exchange_id=BSE返回6行，交易所/交易日期核对PASS。原trade_cal BSE覆盖失败仍保留，不能借margin BSE成功推定日历支持；也不能从这些来源样本推定完整性或TASK已验收。完整逐项观察见 [T14登记](ISSUE-018-T14-runs.md#日期轴多股票及旧not_run补证-source-实际结果)。

## T14 四接口 RANGE TASK（2026-09-13）

`issue018-t14-priority-range-20260913T094551Z` 的25/25 TASK PASS，50次records查询，25次Tushare请求，exit0/cleanupPASS且输入稳定。SQL来源68/插入52/更新16；fixture另2任务/3查询。两股票整段/两端及daily_basic跨年均核对页面、完整批次、SQL股票/业务键/摘要及日志身份。四项当前为AVAILABLE/v2，其余30项仍关闭。详见 [实际RANGE结果](ISSUE-018-T14-runs.md#四接口-range-task-实际结果)。

RANGE记录的expectedCoverage沿用绑定SOURCE取证时的“完整性未确认”标签；它是来源阶段预期，实际TASK PASS另由运行时AVAILABLE/v2/行数规则、完整叶子及SQL验证成立。原始元数据保留，详见运行登记的审查说明。

最终当前源码门禁G1=1318、G2=468、G3=1060、G4=1063、G5=3、G6=7文件/126项全部通过（各exit0）；Node77/77及独立审查通过。首次普通浏览器失败和第二轮中断已保留，修正测试模式动作后完整新轮通过。自有测试容器/秘密/preview已清理，原账户库保留；完整发布脚本未运行。见 [最终门禁与清理](ISSUE-018-T14-runs.md#最终门禁与环境清理) 与 [T14暂停交接](../task-handoffs/ISSUE-018/ISSUE-018-T14-handoff.md)。

## ISSUE-019 独立 SOURCE 与限量采用（2026-09-13）

专属[设计](../task-designs/ISSUE-019-design.md)、[固定计划/公开引用/构建身份/实际结果](ISSUE-018-T14-runs.md#issue-019-独立来源取证)。本次无生产代码、策略版本或准入变更。用户已明确“同意”方案A，三项completeness采用ROW_LIMIT=6000/3500/2000并链接[决策记录](../issues/proposals/ISSUE-019-documented-range-limits.md#决策记录)。这属于工程采用口径，不是新上游保证；原SOURCE取证时的UNKNOWN记录不回填。

| 新runId | case / 来源请求 | PASS / 空样本 / 失败 | 退出 / 清理 |
| --- | --- | --- | --- |
| `issue019-source-20260913T122126Z` | 32 / 40 | 24 / 8 / 0 | 0 / PASS |
| `issue019-dividend-source-20260913T122801Z` | 6 / 10 | 4 / 2 / 0 | 0 / PASS |

- daily：两股票整段各6行、单日两端各1行、重叠各4行，共8项PASS。新请求不借用原失败run；真实重叠更新仍需TASK/SQL。
- forecast：000001.SZ/20160121、600000.SH/20081014及官方样例000005.SZ/20190131，每组整段、公告位于下边界/上边界、公告单日均1行，12项PASS。三组都有执行前公开依据；原202608空样本完整保存在旧run。
- dividend：000001.SZ/20260815周六，整段/公告单日/两种公告边界窗口各2行；600000.SH/20260331对应预案公告，各1行，共8项非空PASS。各自前后两日为空，第一轮浦发同周末6项也为空；全部10项EVIDENCE_MISSING原样保留。两个整段分别枚举连续3个自然日，没有只请求有公告的日子。公开资料用于事件交叉核对，不声明上游完整性保证或发行人原件已取得。
- 当前SOURCE引用改用两轮新独立观察；旧8轮/475case逐对象不变，历史TASK引用不变，另外37个接口决定不变。dividend的SOURCE聚合仍EVIDENCE_MISSING，因为引用中保留了全部空对照；非空代表场景已经有证据，不将空状态强改PASS。
- 两轮均privateLogSafe/inputUnchanged=true，输出无新taskId、写入数或SQL事实。后续28项非空候选与10项空对照的使用规则见运行登记；仅提供待ISSUE-026使用的参数映射，不代表TASK已登记、运行或验收。
- 本轮独立构建1063项后端、468项前端检查通过，Node证据工具77项通过。未重新执行T14六条完整门禁；先前因沙箱导致的Mockito附加失败保留。

### ISSUE-019 决策实施与验收

- 2026-09-13 用户明确同意方案A；T13/T14只对daily、forecast、dividend记录采用例外，保留其他UNKNOWN、repurchase默认值和原准入门禁。正式索引仅更新这三项completeness/decisionRef/当前未决说明，生产Policy、版本、SOURCE/TASK引用及全部10轮/513case不变。
- 修改独立预期后先运行RED：两项检查因三项仍UNKNOWN而失败，exit1。更新规则和决策引用后完整Node证据测试78/78，0失败/跳过，exit0；新增用真实索引检验“只具备已确认限量和成功SOURCE/SINGLE时，冒充AVAILABLE仍被拒绝”。
- 相关Maven回归160项通过（Policy109、Download21、Probe30），失败/错误/跳过均0、exit0。使用Java21、离线依赖及不含真实账户/DB变量的环境；没有新增真实请求、数据库写入或重跑账户验收。日志见 `ISSUE-018-T14-runs.md#issue-019-方案a确认与最终验收`。
- [ISSUE-026已接收的输入](../issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-019-已交付输入)明确28项非空SOURCE、逐项参数/规则依据、10项空对照的保留方式及TASK/SQL要求。离线调用现有selectTaskCases完成28项精确runId/caseId绑定，未登记或提交实际TASK。

## ISSUE-020 分类与限量补证（2026-09-13）

[两轮固定计划与真实结果](ISSUE-018-T14-runs.md#issue-020-分类与限量独立取证)已追加唯一索引：18case/18请求，14 PASS、4满额EVIDENCE_MISSING，无空/失败/未执行，两个run均exit0/cleanup PASS。原10轮/513case及其他39接口逐对象保留。默认省略type在所选两股票均返回P/D/I，不能把普通文档显式P示例当默认值；RANGE也实测110/150，因此不存在已证明的SINGLE150/RANGE100区别。

两股票20250629～20250701整段、20250630单日和报告期落在上下边界的窗口分别37/55行，日期/分类计数相符；没有用2025全年110或宽窗150冒充完整性PASS。用户明确“同意方案A（推荐）”，[决定](../issues/proposals/ISSUE-020-fina-mainbz-default-type.md#决策记录)接受默认原样分类、SINGLE快照及RANGE工程阈值100。索引记录ROW_LIMIT=100的工程采用引用及矛盾事实，不确认上游实际截断规则，不调整生产策略、版本、键或准入。12项RANGE SOURCE参数已[交给ISSUE-026](../issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-020-已交付输入)，4个满额对照保留；母issue及T13/T14原状态不变。

ISSUE-020已按用户明确同意方案A完成本项规则/来源/交付验收，见[最终记录](ISSUE-018-T14-runs.md#issue-020-方案a确认与最终验收)：Node78/78、Maven163/163、12项绑定/4项满额拒绝及限定复审通过，全部12轮/531case及其他39接口保持。当前fina_mainbz的EVIDENCE_MISSING源自保留4个满额对照；该接口RANGE及母issue仍待ISSUE-026真实验收，不因本issue关闭提前开放。

## ISSUE-021 日历与市场来源补证（2026-09-13）

三轮39case/63请求已追加唯一索引：38 PASS（35非空、3完整休市零证券请求），1 BSE直接完整性FAILED；两成功轮exit0、BSE负例轮exit1，三个cleanup PASS。14项沪深完整日历、9项三交易所margin、15项SH/SZ/BJ top_list代表场景已记录；旧12轮/531case、其他37接口与顶层输入哈希保留。当前总计15轮/570case/542请求。

BSE直接请求实际收到合法结构的0行响应，不能形成完整日历，继续拒绝；BJ股票引用SSE日历有官网同历说明和独立非空实测，证券请求保持原BJ代码。映射只在测试Probe验证，生产代码尚未改变。38项匹配来源已交[ISSUE-026](../issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-021-已交付输入)，继续承担候选版本、生产映射、RANGE TASK/SQL及六门禁；三接口未提前开放，母issue/T13/T14状态保持。

## ISSUE-022 日期口径与区间边界补证（2026-09-13）

[两轮固定计划与实际结果](ISSUE-018-T14-runs.md#issue-022-第一轮固定来源计划)已追加唯一JSON：39case/39请求，35非空PASS（31项五接口来源+4项辅助日历）、4空对照EVIDENCE_MISSING，两轮均exit0/cleanup PASS、输入稳定。旧15轮/570case与顶层输入哈希逐对象保留；其他34接口不变，trade_cal只追加4项真实辅助日历引用，既有结论不变。总计17轮/609case/581请求。

- 周线两股票整段实际20240927/20240930/20241011，与完整日历逐周最后开市日一致；国庆周为周一0930，1004周五为空。月线两股票20180928/20181031与完整9/10月日历相符，0930自然月末均为空。结论支持现有“实际最后交易日”合同，不推断所有股票/月份有数据，不以官网旧样例硬编码自然月末。
- fina_indicator两股票整段5/4行，下端2/1行，上端各1行；同一行报告期在内而公告日在外，单日边界全部行均可区分。000001.SZ同报告期2条原样保留。
- stk_holdernumber两股票公告20260815/20260828，整段、单日及事件位于上下边界8项各1行，ann_date均在窗内、截止日end_date均在外；没有传入另一个语义的enddate。
- new_share原非股票区间10行，下端1行、上端2行；13次被观察行的ipo_date/issue_date均有效且不同，整段2行上市日位于窗外。此计数含重叠取样，不是13个不同证券；申购日与上市日明确区分。
- 两个休市周五和两个自然月末空样本保留EVIDENCE_MISSING，使weekly/monthly聚合仍为EVIDENCE_MISSING；不影响已取得的有效整段/边界，但不能作为可成功TASK来源。其他三个主责接口SOURCE聚合PASS。五接口当前引用为本次SOURCE及原干净SINGLE TASK，旧run全部保留；monthly重复参数因此明确绑定本轮，不改消费者或历史数据。

35项精确来源候选（含辅助日历）交[ISSUE-026](../issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-022-已交付输入)，四项空对照独立保留。仅测试Probe扩展安全整数投影，生产策略/版本/准入未变，TASK/SQL及母issue仍待后续完成。

## ISSUE-023 稀疏事件来源结果（2026-09-13）

三轮39case/131次真实SOURCE，38非空PASS、1个000001.SZ质押SINGLE空EVIDENCE_MISSING，三个exit0/cleanup PASS。精确计划、参数、两包/源码及原始安全对象见[运行登记](ISSUE-018-T14-runs.md#issue-023-第一轮固定来源计划)；唯一JSON现20轮/648case/712累计请求，旧17轮/609case和其他36接口原样保留。

两股票大宗交易均同日2原键，官方单日5键5买卖方组合；增减持两股票事件单日6/1行且起止日在公告窗外。用户明确同意以000014.SZ、600000.SH补足质押非空整段与两端，原000001.SZ空结果保留；质押开始/结束/解押与公告逐行比较，无效/缺失副轴不算范围外。

披露两股票最新公告20241009/20240813对应2024Q3/2024H1，整段、单日、上下边界及独立同键复查摘要一致。公开原预约20241026/20240829已改为20241019/20240820，SOURCE五列摘要精确匹配修订后值；modify_date均为空，未声称Tushare返回过旧版或本轮发生新修订。实际数据库更新仍交ISSUE-026。

35项唯一参数的非空RANGE候选已[交付ISSUE-026](../issues/problems/ISSUE-026-range-task-final-acceptance.md#issue-023-已交付输入)；两次同参数披露复查保留原case，只用于稳定性对照，不重复计唯一候选。当前质押SOURCE聚合EVIDENCE_MISSING来自保留的空SINGLE，其余三接口SOURCE PASS；四接口生产仍NEEDS_VERIFICATION/v1，没有本轮TASK/SQL或母issue关闭结果。

## ISSUE-024 历史来源进展（2026-09-13）

[两轮运行登记](ISSUE-018-T14-runs.md#issue-024-历史来源验证)：39case/39请求、33非空PASS、6空EVIDENCE_MISSING，两个exit0/cleanup PASS。唯一索引22轮/687case/751累计请求，旧20轮/648case及其他37接口保持。三接口5000上界未触及；原键有效行数等于原始行数，无重复键，不输出原金额或期限/费率值。

融资原非股票窗口20240601～20240620返回13行，对应0603/0620非空两端，原trade_date+ob键与一日一行基数成立；不发明期限。证券汇总两股票各13行；明细分别2/3行，实际首日0611/0605、末日0620，对应两端已独立验证。20230101～20240620明细长窗分别242/190行，9/7种期限、54/39种费率；同股同日不同期限41/33组，股日同期限不同费率组均0（不冒称观察到该组合）。

融资20240930仍1行，转融券汇总20240711各1行，明细20240710各1行、0711各0；两转融券接口两股票0930～1001均0。此6项空只说明固定请求当次无记录，不证明全历史无数据或最后数据日。历史保留精确起止/持续可访问保证仍未知，2026-09-14用户已明确采用[方案A](../issues/proposals/ISSUE-024-historical-support.md#决策记录)，按历史查询能力验收；未改变生产NEEDS_VERIFICATION/v1或TASK/SQL状态。

### ISSUE-024 采用完成（2026-09-14）

用户明确批准[方案A](../issues/proposals/ISSUE-024-historical-support.md#决策记录)，T13/T14及专属设计已限定修订，ISSUE-024按其关闭条件完成。33项输入正式交ISSUE-026，6空和全部22轮/687case/751请求保持；历史精确起止/持续保留仍未知。决定后Node78/78、33匹配/15拒绝及独立复审通过，详见[最终验收](ISSUE-018-T14-runs.md#issue-024-方案a确认与最终验收)。生产三接口NEEDS_VERIFICATION/v1和实际TASK/SQL要求保持。

## ISSUE-025 十一接口来源进展（2026-09-14）

[三轮运行](ISSUE-018-T14-runs.md#issue-025-十一接口提取规则与来源)共135case/135请求，92非空PASS、43空EVIDENCE_MISSING，均exit0/cleanup PASS。唯一索引25轮/822case/886请求；旧22轮687case及其他29接口原对象保持。十一项完整性UNKNOWN，生产4 AVAILABLE/30 NEEDS_VERIFICATION/6 SINGLE_ONLY及版本保持，TASK/SQL未新增。

两只基准股票各API的代表整段及非空两端已观察，repurchase保留原无股票方式。SOURCE PASS仅为响应结构/语义/范围校验成功；端点外的空对照或旧空继续影响sourceStatus汇总，不删除它们制造全PASS。全部上游完整性合同仍缺；用户后续明确接受不完整，具体决定见下文。

| API | 新场景与准确边界 |
| --- | --- |
| adj_factor | ISSUE-025两股票跨年整段各4行及非空两端已验证；历史能力声明仍无未截断判据。 |
| income | ISSUE-025两股票2024公告窗各4行及非空边界；同一行公告/报告期不同，年度窗各1行公告在窗而报告期在外。 |
| balancesheet | ISSUE-025两股票2024公告窗6/7行及非空边界；公告与报告期同一行对照完成，不能由行数证明完整。 |
| cashflow | ISSUE-025两股票2024公告窗6/5行及非空边界；ann/end不同，ann/f_ann共11行有效且均相同，没有观察到两公告列不同的实例，不能据此互换日期轴。 |
| fina_audit | ISSUE-025两股票年度窗各1行，公告20240315/20240430及单日边界非空，同一行报告期在2024年度窗外；旧空保持。 |
| express | ISSUE-025浦发20180106、平安20220114快报及两端窗口非空；平安旧2018窗0行与所有邻日空保持，未改股票或VIP。 |
| repurchase | ISSUE-025原无股票202608公告窗852行/585股票、两端非空；无参数默认2000不适用于日期请求硬上限。 |
| stk_managers | ISSUE-025两股票公告窗5/8行及非空两端；上任与公告全部不同、离任有效2/3行且与公告不同，缺失3/5行单计；snapshot未代替RANGE。 |
| top10_holders | ISSUE-025两股票2017报告期窗40/60行及两端非空；浦发另有20170831/20170904非季末报告日期，同一行end在窗/ann在窗外已观察，不能按四季乘10枚举完整集合。 |
| top10_floatholders | ISSUE-025两股票2017报告期窗54/46行及两端非空；同一行end在窗/ann在窗外已观察，业务名称不能作为服务器返回上限。 |
| suspend_d | ISSUE-025两基准股票有限历史220/147行，S为211/142、R为9/5；非空两端及各连续五日验证完成，另有官网两股票各五日停牌。实际首末日不等于支持边界，日内时间非空0。 |

2026-09-14用户明确“可以接受不完整”，采用[方案 A](../issues/proposals/ISSUE-025-extraction-contracts.md#决策记录)。本段记录ISSUE-025交付时快照：十一项完整性仍UNKNOWN，decisionRef关联决定，待决码改为RESPONSE_ONLY_IMPLEMENTATION_PENDING；旧SOURCE不回填。ISSUE-030现已按下文贯通RESPONSE_ONLY候选和87组精确来源绑定，但真实TASK/SQL仍未发生，本决定不完成T14或母issue。

## ISSUE-029 工具与主营业务拆分SOURCE

2026-09-14，消费者支持schema1/2、独立RESPONSE_ONLY及完整run/case来源身份；严格HTTP摘要、单请求单成功叶子、SQL和页面事实共同核对。schema1不能承载新规则，UNKNOWN仍不可选择或开放；原25轮822case逐对象保持。真实SOURCE仍记录上游完整性UNKNOWN，按ISSUE-020用户采用的100工程阈值判断拆分覆盖，不升级为上游完整性保证。

新run `issue026-mainbz-split-source-20260914T013736Z`，UTC 2026-09-14T02:09:53.597Z～02:11:21.644Z，exit0、cleanup PASS；4 SOURCE PASS、42请求、19 SPLIT父及23成功叶。全部叶子<100且每项至少一叶非空，父不计成功行数或投影。全年20250101～20251231，六年20200101～20251231，均REPORT_PERIOD/end_date且省略type。

| 完整runId后的case后缀 | 股票 | 请求 | SPLIT父 | 成功叶 | 成功叶原始行数 |
| --- | --- | ---: | ---: | ---: | ---: |
| 000001-annual | 000001.SZ | 1 | 0 | 1 | 74 |
| 600000-annual | 600000.SH | 3 | 1 | 2 | 110 |
| 000001-wide | 000001.SZ | 15 | 7 | 8 | 458 |
| 600000-wide | 600000.SH | 23 | 11 | 12 | 657 |

每项caseId为上述完整runId加`-`和后缀，完整树及安全P/D/I/业务键/日期投影见[唯一JSON](ISSUE-018-range-acceptance.json)；全部run对象与私有原件完全相同。原12项SOURCE和新4项在离线候选中分别按完整身份绑定通过，新000001-annual不会回退到旧同参数74行whole；无身份歧义和错run均拒绝。该离线检查没有创建TASK或生产候选。

隔离879文件快照、生产/验收包、manifest/examples及清单在执行前后均稳定；本地Node98、Probe67、相关Tushare154、完整acceptance后端/包1132及前端524通过，失败/错误/跳过0。详细身份与实际运行见[T14登记](ISSUE-018-T14-runs.md#issue-029-主营业务拆分source)和[本项验收](ISSUE-029-range-evidence-and-split-source.md)。本段是ISSUE-029完成时快照；原四个满额EVIDENCE_MISSING不重标，SOURCE无TASK/SQL，后续当前候选状态见下一节，真实任务仍由ISSUE-031完成。

## ISSUE-030 正式候选引用与272项SOURCE绑定（2026-09-14历史快照）

本节为ISSUE-030交付时的历史快照，当前进展见ISSUE-031。彼时正式索引保持schema 2并同步30项本地候选规则：18项新ROW_LIMIT、11项RESPONSE_ONLY、1项CALENDAR_COVERAGE，全部候选版本为`tushare-range-v2`。四个既有AVAILABLE策略及其真实RANGE引用不变，30项正式处置仍为NEEDS_VERIFICATION；当前`taskStatus=PASS`只聚合原清洁SINGLE TASK，不能解除每项保留的`RANGE_TASK_NOT_RUN`和`RANGE_SQL_NOT_VERIFIED`。daily重叠更新、mainbz真实拆分、disclosure更新、三项slb历史保留保证及RESPONSE_ONLY上游完整性等仍真实存在的关注分别保留。

每项候选completeness引用均包含采用决定、本文逐接口章节和官方URL。11项RESPONSE_ONLY的`decisionRef`精确为[ISSUE-025决策记录](../issues/proposals/ISSUE-025-extraction-contracts.md#决策记录)，只承诺一次完整父窗响应采集，继续明确上游完整性未确认。其余接口沿用ISSUE-019～024已采用规则；历史查询决定不生成精确起止或持续保留保证。

| 输入组 | 精确SOURCE数 |
| --- | ---: |
| ISSUE-019 | 28 |
| ISSUE-020 | 12 |
| ISSUE-021 | 38 |
| ISSUE-022 | 35 |
| ISSUE-023 | 35 |
| ISSUE-024 | 33 |
| ISSUE-025 | 87 |
| ISSUE-029 mainbz拆分SOURCE | 4 |
| 合计 | 272 |

当前候选引用只包含上述272个清洁RANGE SOURCE和适用的原清洁SINGLE TASK。按接口的RANGE SOURCE数为：daily 8、forecast 12、dividend 8、fina_mainbz 16、trade_cal 18、margin 9、top_list 15、weekly 8、monthly 6、fina_indicator 6、stk_holdernumber 8、new_share 3、block_trade 10、disclosure_date 9、stk_holdertrade 9、pledge_detail 7、slb_len 5、slb_sec 14、slb_sec_detail 14、adj_factor 8、suspend_d 16、income 8、balancesheet 8、cashflow 8、fina_audit 4、express 6、repurchase 5、stk_managers 8、top10_holders 8、top10_floatholders 8。

fina_mainbz保留母issue指定旧12项，并新增`issue026-mainbz-split-source-20260914T013736Z`的`000001-annual`、`600000-annual`、`000001-wide`、`600000-wide`四项。旧000001全年whole与新000001 annual参数相同，但通过完整来源身份明确区分；消费者不回退到同参数另一个case。旧四个满额对照、trade_cal BSE直接负例、各轮空样本、两个disclosure同键复查和其他未选来源仍完整保存在原26个run中，不再进入当前候选cases。

重建工具用`validateEvidence`、`validateCasePlan`和`selectTaskCases`逐项验证272项计划，并保存恰一个完整SOURCE引用的`sourceBindings`。错误run、同参数无身份歧义、旧满额、未引用SOURCE、RESPONSE_ONLY缺完整身份均拒绝；仅有SOURCE时不能将正式接口改为AVAILABLE。私有固定输入位于`/private/tmp/issue030-control/`，目录权限0700、文件0600：`candidate-inputs.json` SHA-256 `9c2cb2a9daab04c13bf9aeed5ac69a0d403453611c4a3b40b85d8685837c7b3d`，`source-bindings.json` SHA-256 `a4ce0c747643c91745726b87b4ea5ae682152ac2e542a6783bcd0402acbbba02`。历史runs对象摘要为`95b2e7e0a4743f982d1818540ea8075f694050b9e0ac51a13617f63529381313`，仍为26轮/826 case/928次请求。

本节没有新建或修改run/case事实，没有提交真实TASK、查询业务数据库或生成TASK/SQL结论。272项只是ISSUE-031可冻结输入；真实RANGE TASK/SQL失败时仍须保存事实并按后继合同撤回候选。
