# RANGE-T09 恢复单元机制验证

2026-09-08～09执行，对应[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的RANGE-T09，依据为[专属设计](../task-designs/RANGE-T09-design.md)及[入口交接](../task-handoffs/tensor-range/RANGE-T09-handoff.md)。本记录证明Core内存机制及受控消费顺序，不表示生产独立恢复、数据库事务或HTTP闭环已经启用。

## 输入与交付行为

按入口核对TRD §4.2／§5.2／§6、现有适配与业务键实现、T04共享合同和T07完整来源边界，并复用T05参数转换器；以BRD／PRD的成功保留、完整范围和冻结重试规则核对验收边界。

- `RecoveryUnitProcessor`在来源调用前冻结精确scope、FetchBatch、公共条件及context；Session只能终结一次。先检查包络身份／原参数／有序字段、全部行的对象时间归属和业务键唯一归属，再发布不可变单元。结构矛盾优先于明确的整scope来源失败。
- 首次请求只有来源时间形状、STOCK_TIME依据及公共条件均兼容时才拆分；每个实际股票selector还须用冻结参数通过T05重建。无法合法拆分时保持REQUEST。保存REQUEST及股票RANGE始终保留原边界，单日来源＋RANGE恢复可使用股票DATE。
- 明确局部来源失败与普通业务字段错误只影响独立单元。KnownMembers只通过包可见受控入口验证已知成员分支；未知成员不推测全集，非空结果缺少已知成员不能冒充合法空。完整全空仅产生原scope的零行ReadyUnit。
- `GenericDatasetAdapter.adaptRows`保留来源行顺序和重复，`adaptKeyRow`只转换键列并复用旧指纹。旧adapt仍使用原Map.equals去重、原冲突码和既有错误优先级；ValueConverter、FingerprintKeyCodec、BusinessKeyExtractor及业务唯一键未改。
- `BusinessContentCodec`使用版本1、元数据列顺序、固定类型码、空标记、大端长度及UTF-8编码全部持久化业务列；精确保留小数声明精度／尺度，并排除审计元数据及派生business_key。单元内同键精确比较规范字节，同内容去重、不同内容拒绝整个当前单元。
- `CommittedKeyIndex`绑定本轮数据集及串行使用线程，只保存BusinessKey、编码版本和SHA-256摘要。validate只读；显式确认票据后才原子加入摘要，重复确认／跨索引／版本误用拒绝。回滚或提交未知均不确认，来源行数与待写行数分别保留。

实施限定为4个生产Java文件和4个测试文件；运行入口、SPI、业务事务、失败表及生产策略未接入或扩大。

## 实际命令与结果

命令从仓库根运行。需要Mockito附加JVM及本机测试服务的Maven命令沿用授权执行环境，没有变更依赖、禁用断言或跳过检查。本轮原始日志位于`.superpowers/sdd/RANGE-T09-design/`，下表保存可长期追踪的结果。

| 命令 | 退出码 | 最终实际结果 |
|---|---:|---|
| `mvn -f data-plane/pom.xml -pl tensor-core -am -Dtest=RecoveryUnitProcessorTest,BusinessContentCodecTest,CommittedKeyIndexTest,GenericDatasetAdapterTest,BusinessKeyExtractorTest,ValueConverterTest -Dsurefire.failIfNoSpecifiedTests=false test` | 0 | 6类89项，1.910秒；Processor 41／Codec 4／Index 8／GenericAdapter 16／BusinessKey 8／ValueConverter 12 |
| `mvn -f data-plane/pom.xml verify` | 0 | 725项Java单测＋4项生产JAR合同，共729项；170项前端测试及生产构建；31.090秒 |
| `mvn -f data-plane/pom.xml -Pacceptance clean verify` | 0 | 725项Java单测＋4项生产JAR＋3项acceptance JAR合同，共732项；170项前端测试及生产构建；36.829秒 |
| `PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py` | 0 | 8组合同检查＋4个内存变异反例全部PASS |
| `python3 .superpowers/sdd/RANGE-T09-design/audit-final.py focused-final.log verify-final.log acceptance-final.log` | 0 | 逐类测试数、失败／错误／跳过、构建成功、前端结果和时长核对；51资源、337任务外代码／构建文件、原分支及HEAD全部一致 |

所有实际调度测试均零失败、零错误、零跳过。保留既有Maven编码和Mockito动态agent／JVM附加警告，没有将警告解释为新增功能失败，也没有通过关闭测试消除警告。两个全量构建都实际执行前端170项测试及Vite生产构建。

设计原先误列不存在的`FingerprintKeyCodecTest`；实施核对后已更正为实际六类命令。旧指纹黄金断言位于`GenericDatasetAdapterTest#encodesApprovedFingerprintValuesAndRejectsBrokenCodecContracts`及`#appendsStableFingerprintBusinessKeyAfterBusinessColumns`，包括固定摘要、字段次序、类型拒绝和派生键，已随该类实际执行；没有虚计第七类，也没有删除覆盖。

## 测试先行与修正

1. 先写A／B／C隔离、最后坏归属、同键冲突及未确认索引场景。缺类型／方法的首轮编译失败单独记录；最小骨架之后实际4项中有3项断言失败、0运行错误。固定拒绝骨架偶然满足冲突断言，未将它冒充独立RED。
2. 适配器／编码扩展18项先有5个断言失败，实施后通过。随后必填null编码、null时间归属和旧adapt的早期冲突优先级由实际失败定位并修正。首个交付版本的六类66项通过，但不以此代替独立评审。
3. 首审发现首次股票单元重建验证不足、REQUEST显式失败提前绕过归属检查、KnownMembers空元素错误码及必需测试缺口。新增回归26项中4个断言失败、0运行错误；最小修正后同组通过。补充明确的来源／提交消费替身和设计矩阵后六类87项通过。
4. 定向复审发现DATE来源＋RANGE恢复的合法单日组合被过度限制，以及非空重试测试替换掉原空测试。R1先得到40项中1个断言失败，再放宽这一合法组合、保留MONTH／DATE拒绝并恢复独立空重试测试。Processor最终41项通过；最终六类89项及两次全量构建均在该修正后执行。

## 可观察覆盖

| 范围 | 本轮实际断言 |
|---|---|
| 完整来源消费 | `strictCompleteSourceDriverNeverPassesBufferedRowsAfterALaterPageFailure`用受控后页超时／截断证明缓存A行不进入accept／validate，只形成来源失败；不调用旧download回退 |
| 整体优先 | 最后目标null／非法、日期非法／越界、不可转换键、跨单元同键及成员范围外行均整scope拒绝，adaptRows为零；显式失败单元的行同样检查，整REQUEST failure不绕过屏障 |
| A／B／C隔离 | 普通B字段转换错误及必填缺失只拒绝B，A／C可Ready；DATE、MONTH和完整RANGE分组可重建，单日DATE来源＋RANGE恢复保留隔离；无依据／条件不共用／缺ts_code声明时保持REQUEST |
| 包络与明确失败 | 错身份／原参数／字段顺序／失败包络拒绝；行数／宽度／重复列按构造不变量直接拒绝。兼容REQUEST局部失败稳定取首错误，越界／子RANGE／DATE与相交失败对全局拒绝 |
| 已知与未知成员 | 未知时仅处理可观察单元，不补缺股；已知整体来源失败可表达共同失败。非空缺成员为完整性未确认，范围外成员优先payload错误；错数据集／批次、重复／空／控制字符证据和混合或相交时间单位在取数前拒绝 |
| 冻结重试与空 | 保存REQUEST不升级；保存股票1～10日RANGE返回3／7日仍为原范围，来源及全局失败同样保持原selector。保存STOCK的完整空响应独立验证一个精确单元、零来源行和零写入行；完整首次空不按KnownMembers展开 |
| 条件与归属 | 原ts_code、exchange／exchange_id及原展示日期冻结；原条件REQUEST/NONE、原生单日相等端点、错策略／配置、受来源代码pattern限制的行、nullable指纹键及归属兼键转换错误均有断言 |
| 规范业务内容 | Map顺序、1.2／1.20／1.200、精确小数、日期／月／Long边界、中文／emoji字节长度、null／空串／TEXT空白、ENUM类型标签、列边界分帧、业务列变化和仅审计元数据变化；固定格式字节及其实际SHA-256黄金值 |
| 去重与同轮冲突 | 同键同内容保留首写行且来源行数不减少；当前单元异内容整体拒绝。已确认重复跳过写入，重复＋新增＋冲突混合单元不留下新摘要；版本误用、跨数据集及外部processor单元拒绝 |
| 确认顺序 | `serialCommitDriverKeepsRollbacksUnconfirmedAndStopsAfterUnknownBeforeC`覆盖SUCCESS／ROLLBACK／UNKNOWN，未知时不处理C且索引不变；确认票据跨索引／重复／冲突原子拒绝，新执行从空索引开始 |
| 生命周期与安全 | UnitInput／ReadyUnit私有构造，KnownMembers入口包内可见；成功accept及确认后Session不再回退。context与未知局部异常实例原样传播；新摘要不包含来源／行哨兵、cause或suppressed |

来源驱动和提交驱动均是测试代码中的消费顺序替身，未增加生产来源提供器、证券全集、事务服务或执行编排；它们不能证明真实分页取全或MySQL提交效果。

## 独立评审与验收边界

独立首审的I1／I2／I3／M1已修正；第一轮定向复审的R1／R2亦已修正。第二轮复审规格PASS、质量APPROVED，无遗留Critical／Important／Minor。主流程独立读取最终日志，逐类汇总89／729／732及前端170项，核对资源和范围；最终独立集成复核亦确认生产规格PASS、质量／集成APPROVED；终轮发现的1.200精确尺度测试／报告差异通过仅测试数据补充关闭，定向复核无新增问题。该补充保留一行待写并断言三行来源，随后六类89项重新执行通过（1.910秒）；生产代码与两次全量构建的已验证版本逐文件一致，两次全量构建发生在最后生产修正之后、这一测试数据补充之前。

51份生产Dataset／策略资源逐文件SHA-256不变，337份任务外代码及构建文件不变。分支保持`feat/date-range-download`，HEAD保持`277fcfff9e47e5a541589ca7aecfc374a780c13f`，未提交或发布。生产`TushareProPlugin`仍构造空日历／完整批次来源表，49项仍为REQUEST；DownloadService及Spring运行配置没有接入新处理器。

当前Surefire默认不调度数据库`*IT.java`，Failsafe只执行打包合同；数据库IT仅编译、未执行，不能因acceptance名称将其宣称通过。浏览器E2E和真实业务API未运行，未读取真实凭证。MySQL提交确认、业务写入与失败明细原子删除、S／F／N与R／I／U和HTTP闭环由后续任务验证；ISSUE-008九项仍“不依赖，未解决”。

Git差异及暂存差异空白检查、正式文档相对链接核对均通过；正式代码／测试／设计修正／验证记录及追踪已纳入Git，内部日志与评审草稿不作为交付文件。

本项六条机制证据已加入[增量需求追踪](../traceability/tensor-range-requirements.md#range-t09-恢复单元层增量证据)，最终功能AC保持由后续任务验收。
