# Tensor 区间下载需求追踪索引

- 任务：[RANGE-T03](../task-handoffs/tensor-range/tensor-range-task-board.md#range-t03)；实施合同：[专属设计](../task-designs/RANGE-T03-design.md)。
- 来源：[PRD v1.4 §9.1](../design/Tensor_区间下载_PRD_v1.0.md#91-验收矩阵)、[TRD v1.10](../design/Tensor_区间下载_TRD_v1.0.md)、[T01来源](../research/RANGE-T01-source-capabilities.md)及[T02日历](../research/RANGE-T02-calendar-capabilities.md)。PRD 列原样保留矩阵中的 PRD-01～17 简写，对应 PRD-RANGE-01～17。
- Result 记录功能验收结果：T03仅校验文档合同，运行接口尚未迁移；实现、受控行为、真实来源通过分别登记。Expected evidence 是后续任务需提供的证据，不是已生成的产物或通过结果。
- 全部29项最终证据汇总责任为 RANGE-T21；本文件是区间增量，不改写[平台历史追踪](tensor-v1-requirements.md)。
- 分别报告49项请求／策略、38项区间目标、49项恢复策略及具体账号实测范围。REQUEST不豁免整体取全；未确认来源或必要日历按明确错误拒绝，不能凭拒绝边界关闭功能AC。
- T04 的增量合同证据单列于文末；下表 Result 保留最终功能验收未执行的状态，不将 SPI／策略测试计为区间闭环通过。
- [ISSUE-008](../issues/problems/ISSUE-008-tushare-live-coverage-gap.md)九项仍“不依赖，未解决，用户后续单独处理”：`top_inst`、`broker_recommend`、`share_float`、`hs_const`、`moneyflow_hsgt`、`hk_hold`、`index_member`、`namechange`、`hsgt_top10`。保留合同和受控覆盖，真实调用不恢复。

| AC | PRD | TRD | Contract | Implementation tasks | Verification tasks | Expected evidence | Result |
|---|---|---|---|---|---|---|---|
| AC-PRD-RANGE-01 | PRD-01 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §4.1、§4.3 | [DownloadRequest](../contracts/openapi-v1.yaml#/components/schemas/DownloadRequest)、[DownloadPolicy](../contracts/openapi-v1.yaml#/components/schemas/DownloadPolicy) | RANGE-T04、RANGE-T05、RANGE-T14、RANGE-T15 | RANGE-T18、RANGE-T19、RANGE-T20 | 49目标形状／策略分类、38范围及具体账号口径分列 | 未执行；T03仅合同校验 |
| AC-PRD-RANGE-02 | PRD-04 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §5.1、§3.2～3.3 | [DownloadExecutionResult](../contracts/openapi-v1.yaml#/components/schemas/DownloadExecutionResult)、[ApiError](../contracts/openapi-v1.yaml#/components/schemas/ApiError) | RANGE-T06、RANGE-T08、RANGE-T12、RANGE-T13 | RANGE-T18、RANGE-T20 | 首次及重试日历确认后实际业务日期 | 未执行；T03仅合同校验 |
| AC-PRD-RANGE-03 | PRD-02 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §4.4、§7.1.3 | [StockRangeParams](../contracts/openapi-v1.yaml#/components/schemas/StockRangeParams)、[ExchangeIdRangeParams](../contracts/openapi-v1.yaml#/components/schemas/ExchangeIdRangeParams)、[RecoveryScope](../contracts/openapi-v1.yaml#/components/schemas/RecoveryScope) | RANGE-T05、RANGE-T13、RANGE-T14 | RANGE-T18、RANGE-T20 | income股票、margin市场参数精确还原 | 未执行；T03仅合同校验 |
| AC-PRD-RANGE-04 | PRD-03 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §4.3～4.4、§7.1.3 | [RangeParams](../contracts/openapi-v1.yaml#/components/schemas/RangeParams)、[RecoveryScope](../contracts/openapi-v1.yaml#/components/schemas/RecoveryScope) | RANGE-T05、RANGE-T08、RANGE-T13、RANGE-T15 | RANGE-T18、RANGE-T19 | 31天跨三月、跨年、完整月份及失败月份 | 未执行；T03仅合同校验 |
| AC-PRD-RANGE-05 | PRD-02、06 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §5.1、§6.4 | [DownloadResponse](../contracts/openapi-v1.yaml#/components/schemas/DownloadResponse) | RANGE-T05、RANGE-T06、RANGE-T08、RANGE-T12 | RANGE-T18、RANGE-T19 | 单日开盘／休市／公告／合法空 | 未执行；T03仅合同校验 |
| AC-PRD-RANGE-06 | PRD-02 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §4.3、§10.1 | [RangeParams](../contracts/openapi-v1.yaml#/components/schemas/RangeParams)、[ApiError](../contracts/openapi-v1.yaml#/components/schemas/ApiError) | RANGE-T05、RANGE-T14、RANGE-T15 | RANGE-T18、RANGE-T19 | 非法／31／32天请求捕获及零业务调用 | 未执行；T03仅合同校验 |
| AC-PRD-RANGE-07 | PRD-06、08 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §6.4、§7.3 | [DownloadResponse](../contracts/openapi-v1.yaml#/components/schemas/DownloadResponse) | RANGE-T11、RANGE-T12、RANGE-T16 | RANGE-T18、RANGE-T19 | 合法空不占位、不删旧数据，空失败混合计数 | 未执行；T03仅合同校验 |
| AC-PRD-RANGE-08 | PRD-07、13 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §5.2、§6.1～6.3 | [RecoveryFailure](../contracts/openapi-v1.yaml#/components/schemas/RecoveryFailure)、[DownloadResponse](../contracts/openapi-v1.yaml#/components/schemas/DownloadResponse) | RANGE-T09、RANGE-T11、RANGE-T12 | RANGE-T18、RANGE-T20 | 同响应A/C业务表保留、仅B失败；来源独立恢复另证 | 未执行；T03仅合同校验 |
| AC-PRD-RANGE-09 | PRD-09 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §6.2～6.3 | [RecoveryFailure](../contracts/openapi-v1.yaml#/components/schemas/RecoveryFailure) | RANGE-T09、RANGE-T11、RANGE-T12 | RANGE-T18 | 同键相同去重／不同当前单元失败 | 未执行；T03仅合同校验 |
| AC-PRD-RANGE-10 | PRD-10、13 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §2.2、§5.2 | [RecoveryFailure](../contracts/openapi-v1.yaml#/components/schemas/RecoveryFailure)、[DownloadResponse](../contracts/openapi-v1.yaml#/components/schemas/DownloadResponse) | RANGE-T07、RANGE-T12、RANGE-T13 | RANGE-T18 | 来源失败保存后继续全部后续范围 | 未执行；T03仅合同校验 |
| AC-PRD-RANGE-11 | PRD-07、10 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §5.2、§6.1 | [RecoveryScope](../contracts/openapi-v1.yaml#/components/schemas/RecoveryScope)、[RecoveryFailure](../contracts/openapi-v1.yaml#/components/schemas/RecoveryFailure) | RANGE-T07、RANGE-T09 | RANGE-T18、RANGE-T20 | 后页错误／截断整批不提交及完整恢复边界 | 未执行；T03仅合同校验 |
| AC-PRD-RANGE-12 | PRD-10、11 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §2.2、§3.2～3.3 | [RecoveryFailure](../contracts/openapi-v1.yaml#/components/schemas/RecoveryFailure)、[DownloadResponse](../contracts/openapi-v1.yaml#/components/schemas/DownloadResponse) | RANGE-T07、RANGE-T12、RANGE-T13 | RANGE-T18 | 局部／来源连续失败及后续成功，无自动重试 | 未执行；T03仅合同校验 |
| AC-PRD-RANGE-13 | PRD-08 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §6.4、§8.3 | [DownloadExecutionResult](../contracts/openapi-v1.yaml#/components/schemas/DownloadExecutionResult) | RANGE-T11、RANGE-T12、RANGE-T14、RANGE-T16 | RANGE-T18、RANGE-T19 | S/F与R/I/U确认小计及2/1示例 | 未执行；T03仅合同校验 |
| AC-PRD-RANGE-14 | PRD-02、12 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §4.4、§7.1.3 | [RecoveryScope](../contracts/openapi-v1.yaml#/components/schemas/RecoveryScope)、[DownloadPolicy](../contracts/openapi-v1.yaml#/components/schemas/DownloadPolicy) | RANGE-T04、RANGE-T05、RANGE-T08、RANGE-T09、RANGE-T13 | RANGE-T18、RANGE-T20 | REQUEST不拆小、不转STOCK、原条件无日期 | 未执行；T03仅合同校验 |
| AC-PRD-RANGE-15 | PRD-04、06 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §5.1、§7.3 | [DownloadResponse](../contracts/openapi-v1.yaml#/components/schemas/DownloadResponse) | RANGE-T06、RANGE-T12、RANGE-T13 | RANGE-T18、RANGE-T19 | 全休市零业务请求，新任务无记录、原项按完整确认移除 | 未执行；T03仅合同校验 |
| AC-PRD-RANGE-16 | PRD-04 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §5.1 | [DownloadPolicy](../contracts/openapi-v1.yaml#/components/schemas/DownloadPolicy)、[ApiError](../contracts/openapi-v1.yaml#/components/schemas/ApiError) | RANGE-T06、RANGE-T08 | RANGE-T18、RANGE-T20 | 多市场并集、停牌与休市区分 | 未执行；T03仅合同校验 |
| AC-PRD-RANGE-17 | PRD-04、13 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §5.1、§9.1 | [ApiError](../contracts/openapi-v1.yaml#/components/schemas/ApiError) | RANGE-T06、RANGE-T12、RANGE-T13、RANGE-T14 | RANGE-T18、RANGE-T19 | 日历未知零业务，首次无任务、原项保留 | 未执行；T03仅合同校验 |
| AC-PRD-RANGE-18 | PRD-03、04 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §4.1、§5.1 | [DownloadPolicy](../contracts/openapi-v1.yaml#/components/schemas/DownloadPolicy) | RANGE-T04、RANGE-T06、RANGE-T08、RANGE-T15 | RANGE-T18、RANGE-T19、RANGE-T20 | 非交易日期不进日历，trade_cal闭市行保留 | 未执行；T03仅合同校验 |
| AC-PRD-RANGE-19 | PRD-02、12 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §4.3 | [DownloadRequest](../contracts/openapi-v1.yaml#/components/schemas/DownloadRequest) | RANGE-T05、RANGE-T14、RANGE-T15 | RANGE-T18、RANGE-T19 | 旧／混用拒绝与附加条件保留 | 未执行；T03仅合同校验 |
| AC-PRD-RANGE-20 | PRD-05、16 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §8.2～8.3、§9 | [DownloadExecutionResult](../contracts/openapi-v1.yaml#/components/schemas/DownloadExecutionResult)、[RetryTaskPage](../contracts/openapi-v1.yaml#/components/schemas/RetryTaskPage) | RANGE-T15、RANGE-T16、RANGE-T17 | RANGE-T19 | 切换／响应丢失不重发、不推测成功 | 未执行；T03仅合同校验 |
| AC-PRD-RANGE-21 | PRD-13、15 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §7.1.2、§7.3～7.4 | [RetryTaskDetail](../contracts/openapi-v1.yaml#/components/schemas/RetryTaskDetail) | RANGE-T10、RANGE-T11、RANGE-T13、RANGE-T17 | RANGE-T18、RANGE-T19 | 两股同日分别保存／删除，最后删除主表 | 未执行；T03仅合同校验 |
| AC-PRD-RANGE-22 | PRD-14 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §7.1、§8.1 | [OriginalDateRange](../contracts/openapi-v1.yaml#/components/schemas/OriginalDateRange)、[RetryTaskDetail](../contracts/openapi-v1.yaml#/components/schemas/RetryTaskDetail) | RANGE-T05、RANGE-T10、RANGE-T13、RANGE-T14、RANGE-T17 | RANGE-T18、RANGE-T19 | 原1—10与剩余3/7、部分后仅7，重启仍正确 | 未执行；T03仅合同校验 |
| AC-PRD-RANGE-23 | PRD-05、15 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §8.1～8.2 | [ApiError](../contracts/openapi-v1.yaml#/components/schemas/ApiError) | RANGE-T12、RANGE-T13、RANGE-T14、RANGE-T17 | RANGE-T18、RANGE-T19 | 忙409、无任务404，无重建或排队 | 未执行；T03仅合同校验 |
| AC-PRD-RANGE-24 | PRD-07、16 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §7.3、§9.2 | [RecoveryFailure](../contracts/openapi-v1.yaml#/components/schemas/RecoveryFailure)、[DownloadExecutionResult](../contracts/openapi-v1.yaml#/components/schemas/DownloadExecutionResult) | RANGE-T10、RANGE-T11、RANGE-T13 | RANGE-T18 | SQL／删除同事务回滚与A/C保留 | 未执行；T03仅合同校验 |
| AC-PRD-RANGE-25 | PRD-02、11 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §7.1.3、§11 | [RetryTaskDetail](../contracts/openapi-v1.yaml#/components/schemas/RetryTaskDetail)、[ApiError](../contracts/openapi-v1.yaml#/components/schemas/ApiError) | RANGE-T04、RANGE-T05、RANGE-T13、RANGE-T14、RANGE-T17 | RANGE-T18、RANGE-T19 | 不兼容拒绝且原记录不改源／范围 | 未执行；T03仅合同校验 |
| AC-PRD-RANGE-26 | PRD-08、16 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §6.4、§9 | [DownloadExecutionResult](../contracts/openapi-v1.yaml#/components/schemas/DownloadExecutionResult)、[ApiError](../contracts/openapi-v1.yaml#/components/schemas/ApiError) | RANGE-T10、RANGE-T11、RANGE-T12、RANGE-T13、RANGE-T14、RANGE-T16 | RANGE-T18、RANGE-T19 | 保存与提交未知区别，确认小计、N=null | 未执行；T03仅合同校验 |
| AC-PRD-RANGE-27 | PRD-17 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §2.4、§8.3 | [DownloadExecutionResult](../contracts/openapi-v1.yaml#/components/schemas/DownloadExecutionResult) | RANGE-T12、RANGE-T13、RANGE-T16、RANGE-T17 | RANGE-T18、RANGE-T19 | 无取消／暂停入口，执行结束才解锁 | 未执行；T03仅合同校验 |
| AC-PRD-RANGE-28 | PRD-05、17 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §2.4、§8.2、§9 | [ApiError](../contracts/openapi-v1.yaml#/components/schemas/ApiError)、[DownloadExecutionResult](../contracts/openapi-v1.yaml#/components/schemas/DownloadExecutionResult) | RANGE-T12、RANGE-T13、RANGE-T14、RANGE-T16 | RANGE-T18、RANGE-T19 | 断连继续、不释放槽位、不自动重提 | 未执行；T03仅合同校验 |
| AC-PRD-RANGE-29 | PRD-07、10 | [TRD](../design/Tensor_区间下载_TRD_v1.0.md) §5.2、§6.1 | [RecoveryScope](../contracts/openapi-v1.yaml#/components/schemas/RecoveryScope)、[RecoveryFailure](../contracts/openapi-v1.yaml#/components/schemas/RecoveryFailure) | RANGE-T07、RANGE-T09 | RANGE-T18、RANGE-T20 | 缺股票不猜失败，整体归属未过不拆分 | 未执行；T03仅合同校验 |

事务、来源继续及中断用例通过本轮结果、保存的当前选择器和错误观察，仍需后续业务表／失败表及调用序列独立核对。Schema不能证明事务、来源完整性或执行事实。列表与详情只展示实际当前记录；原始区间不决定重试范围，也不还原历史计数。

## RANGE-T04 合同层增量证据

2026-09-08 的[插件合同验证报告](../verification/RANGE-T04-plugin-contracts.md)记录本轮实现和验证。以下四项仅合同层通过，功能与真实来源结果继续由上表责任任务提供。

- **AC-PRD-RANGE-01：** 49 项策略及下载参数投影与 T03 目标逐项一致，分类为 19／15／1／3／11；原 `sourceParameters` 和 Dataset 定义保留，HTTP 仍展示并接受原可执行条件。
- **AC-PRD-RANGE-14：** 49 项生产策略明确为 REQUEST，独立恢复未启用；两类对象及四类时间选择器有合法性、身份区分及不可变性测试，11 项原条件投影保持原数组。
- **AC-PRD-RANGE-18：** 仅 19 项交易日期策略携带日历类别／证据；其他模式日历字段为空。默认运行日历明确拒绝，受控并集及精确日期覆盖测试不作为真实日历确认。
- **AC-PRD-RANGE-29：** 恢复和取全证据未升级；FetchResult 只接受明确、唯一的来源单元失败。旧下载服务遇非空失败先拒绝，适配及持久化均为零；完整取数、归属分组与独立事务仍待后续任务验证。

## RANGE-T05 参数层增量证据

2026-09-08的[参数绑定与转换验证](../verification/RANGE-T05-parameter-conversion.md)记录独立入口的本轮行为证据。当前HTTP入口继续消费sourceParameters，上表最终功能结果不作升级。

- **AC-PRD-RANGE-03：** 规范股票及原交易所条件不丢失，income／margin来源参数按选择器精确重建；实际对象类型决定公共条件，REQUEST回退保留股票。
- **AC-PRD-RANGE-04：** 31天跨三月输入合法，各MONTH选择器转换成完整月份，不被原始展示日期裁剪；月份枚举由T08实施。
- **AC-PRD-RANGE-05／06：** 单日、闰日、跨年合法，31天允许、32天拒绝；严格年份／类型／必填／枚举和股票长度检查通过，新绑定入口本身不调用日历或来源。休市与空数据行为尚待执行层验证。
- **AC-PRD-RANGE-14：** DATE按声明方式映射单日或相等起止，RANGE保留端点；REQUEST不改成STOCK、不丢原条件，原条件NONE不新增日期。
- **AC-PRD-RANGE-19：** 真实49项投影逐项绑定，38区间／11原条件与9种形状通过；旧日期／混用／未知字段拒绝，原HTTP入口回归保留，运行迁移留待T14。
- **AC-PRD-RANGE-22：** 原1～10日被冻结，失败3／7日分别恢复且原map不变；旧记录缺两端返回null展示范围，不从剩余项反推原区间。存储与页面持久展示由后续任务验证。
- **AC-PRD-RANGE-25：** 单边或非法原日期、公共条件与selector冲突、重复股票、未知股票映射或时间不兼容安全拒绝；未确认来源保持来源错误，生产策略没有升级。

## RANGE-T06 日历提供器层增量证据

2026-09-08的[日历确认验证](../verification/RANGE-T06-calendar-confirmation.md)记录受控来源及生产拒绝的本轮证据。19项生产日历仍未确认，上表最终功能结果保持未执行。

- **AC-PRD-RANGE-02：** 获取前确定全部必要身份并检查来源齐全；同源本轮读取一次、下次重新获取。实际失败3／7日精确确认，不用原始1～10日条件扩张范围。
- **AC-PRD-RANGE-15：** 两身份逐日完整确认闭市时openDates为空；空来源、缺日或缺身份均拒绝，不能冒充全休市。实际零计数及任务处理由执行层验证。
- **AC-PRD-RANGE-16：** 受控SSE两日闭、SZSE仅3日开，结果仅3日；margin三分支各只选其市场，CSF及北向互联互通保持专属身份。
- **AC-PRD-RANGE-17：** 来源缺失、冲突、身份错误、生效范围不足或修订未确认均固定CALENDAR_UNCONFIRMED；来源故障安全转换、context故障原样传播，无数据库／任务调用。首次无任务及重试明细保留的实际闭环尚待T12／T13／T18。
- **AC-PRD-RANGE-18：** 19项证据分类及1／16／2状态与T02一致，30项非交易模式误调用拒绝且无辅助获取，原download回归保持。生产空来源注册表未伪造日历，非交易类别执行层绕过屏障尚待T08验证。


## RANGE-T07 完整批次来源层增量证据

2026-09-08的[完整批次获取验证](../verification/RANGE-T07-complete-batch-fetch.md)记录本项来源机制及生产拒绝证据。当前49项生产完整批次合同仍未启用，受控分页不代表真实来源支持，上表最终功能结果保持未执行。

- **AC-PRD-RANGE-10：** 后页HTTP、传输及协议错误使本批次不返回FetchResult，保持安全分类；后续批次继续及失败保存仍由T12／T13／T18验证。
- **AC-PRD-RANGE-11：** 全部页的身份、参数、字段顺序、行宽、游标、实际请求进展、总数及全集回调通过后才返回完整包络。明确截断与完整性未知分开拒绝，不输出首页部分结果；数据库不提交的实际效果由后续任务核对。
- **AC-PRD-RANGE-12：** 来源失败不自动重试、拆小或从失败页续传；主动再次fetch从新Session及第一页开始。跨批次失败后继续的执行编排仍待验证。
- **AC-PRD-RANGE-14：** 完整包络恢复原批次参数，不被最后一页覆盖；来源分页参数不能改写股票、日期、市场或其他声明业务条件。真实mapper到WireMock的DATE／MONTH／RANGE／NONE形状逐键通过，REQUEST未改成STOCK。
- **AC-PRD-RANGE-29：** 来源全集检查可拒绝越界日期、缺日、重复日掩盖缺日及错市场；不根据缺少股票生成单元失败。T09所有行归属及恢复单元隔离仍是提交前独立门槛。

## RANGE-T08 内存规划层增量证据

2026-09-08的[内存批次规划验证](../verification/RANGE-T08-memory-batch-planning.md)记录首次规划的受控行为及生产拒绝证据。新入口尚未接入HTTP，生产日历和完整批次来源表仍为空，上表最终功能结果保持未执行。

- **AC-PRD-RANGE-02：** 初始参数校验后一次确认完整原条件／日期scope，按开盘并集和连续片段规划，休市差集只用于本轮；不跨日历空档，不缓存跨次确认。
- **AC-PRD-RANGE-03：** 公告日期保留全部自然日及规范股票条件，包含周末；DATE候选只用已声明公告字段，不将公告区间误传报告期。逐键映射及联合对象／时间覆盖拒绝换股或丢条件。
- **AC-PRD-RANGE-04：** 20260131～20260302先通过31天校验，再生成2026-01／02／03三个完整MONTH批次；单日覆盖整月，跨年按年月区分，原始展示区间不随展开改变。
- **AC-PRD-RANGE-05／06：** 单日、合法闰日、跨年及0001／9999边界通过；四种区间模式32天均在任何日历或来源回调前拒绝。合法空来源结果仍由取数／执行层验收。
- **AC-PRD-RANGE-14：** 每个最终批次有精确预检，只有正常SINGLE_DATE建议允许交易／公告前置分日；异常不拆批，原生单日／多日均一批，11原条件按9候选／2未知保留或明确拒绝。所有scope保持REQUEST，不预造股票恢复单元。
- **AC-PRD-RANGE-15：** 受控完整日历全闭时返回合法零批，预检／fetchBatch／download均零次，仍完成覆盖及最终服务端检查；实际响应计数和失败任务处理由执行层验收。
- **AC-PRD-RANGE-18：** 公告、完整月份、三原生范围及11原条件均不调用辅助日历；trade_cal保持原完整日期条件，不添加is_open。真实日历数据含休市记录及来源语义仍待后续验证。

## RANGE-T09 恢复单元层增量证据

2026-09-08～09的[恢复单元机制验证](../verification/RANGE-T09-recovery-units.md)记录本项Core内存行为。生产日历与完整来源注册表仍为空，49项生产恢复策略保持REQUEST；下述机制通过不升级上表最终功能AC，实际业务提交／失败表／HTTP闭环仍由后续任务验证。

- **AC-PRD-RANGE-07：** 严格完整的全空响应只形成原scope的零行ReadyUnit；保存STOCK空重试保留原selector。缺股／缺日不推导空成功，KnownMembers不展开全空。没有占位、业务删除或失败明细删除操作，实际事务效果留待T11／T12／T18。
- **AC-PRD-RANGE-08：** 已核实STOCK_TIME下A／C可形成ReadyUnit，B普通字段错误或明确来源失败只拒绝B；无独立依据、公共参数不兼容或来源时间形状不匹配时在取数前保持REQUEST。所有实际STOCK selector均通过冻结参数重建；真实独立恢复许可及入库保留另行验证。
- **AC-PRD-RANGE-09：** 单元内按全部规范业务内容精确字节比较，同键相同去重、不同整单元DATA_CONFLICT。跨单元只查询已确认提交摘要；回滚／未知不更新，混合新键和冲突不会部分更新索引；新执行为空索引，不读取历史业务内容。
- **AC-PRD-RANGE-11：** 完整来源消费替身在后页超时／截断时只调用sourceFailed，缓存A行不进入accept／validate。全局包络、行归属及跨单元业务键检查先于任何单元发布；即使有整scope REQUEST failure，结构矛盾仍先拒绝。该证据不替代真实来源取全及数据库事务验证。
- **AC-PRD-RANGE-14：** 保存REQUEST不会升级为STOCK，保存股票RANGE在非空、空及失败时都保留原边界；原条件REQUEST/NONE与原生单日相等端点可重建，原股票、exchange／exchange_id及原始展示日期不丢失。单日来源＋RANGE恢复可合法形成股票DATE，MONTH来源不生成DATE恢复单元。
- **AC-PRD-RANGE-29：** 所有行包含明确失败单元的行均先检查归属。无已知成员时不推测全集；已知非空结果缺成员整scope完整性未确认，范围外成员优先payload错误。Session一次终结，单元发布后不能再生成覆盖它们的REQUEST回退。

## RANGE-T10 失败存储层增量证据

2026-09-09的[两表存储验证](../verification/RANGE-T10-failure-storage.md)记录本项实现及实际MySQL行为：六类规定数据库IT共89项、追加三类App回归共16项通过；生产verify745、acceptance748项Java检查及两次170项前端测试／构建通过。以下仅确认存储部分，上表最终闭环功能结果保持原状态。

- **AC-PRD-RANGE-21：** V8仅新增两张失败表，完整对象／时间联合主键区分同日两股；追加、重复原因更新、存在检查和事务原语精确定位。同一事务删除指定项及空主表的原语、外层回滚和锁生命周期通过；业务Upsert＋删除组合仍由T11验证。
- **AC-PRD-RANGE-22：** 真实T05转换器生成的REQUEST／STOCK公共Map落库后保持原始1～10日；追加3／7日和原因更新不覆盖参数，STOCK不重复保存代码。旧日期可读、重建服务可读，原始范围不由剩余项反推；双DESC稳定分页和并发读取快照通过，HTTP／页面展示仍由后续任务验证。
- **AC-PRD-RANGE-26：** 首次一主一子、追加及重复原因更新后半段SQL故障均真实回滚，旧记录完整保留。仅事务正常返回发布SavedFailure；提交抛错按未确认安全报告、零自动重试。MySQL JSON有损数字在事务内回读比较后拒绝并回滚，不将Java精度承诺误当数据库能力。业务提交未知及整轮计数仍由T11～T14承担。
