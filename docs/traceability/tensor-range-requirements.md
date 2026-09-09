# Tensor 区间下载需求追踪索引

- 任务：[RANGE-T03](../task-handoffs/tensor-range/tensor-range-task-board.md#range-t03)；实施合同：[专属设计](../task-designs/RANGE-T03-design.md)。
- 来源：[PRD v1.4 §9.1](../design/Tensor_区间下载_PRD_v1.0.md#91-验收矩阵)、[TRD v1.10](../design/Tensor_区间下载_TRD_v1.0.md)、[T01来源](../research/RANGE-T01-source-capabilities.md)及[T02日历](../research/RANGE-T02-calendar-capabilities.md)。PRD 列原样保留矩阵中的 PRD-01～17 简写，对应 PRD-RANGE-01～17。
- Result 记录功能验收结果：T03完成时仅校验文档合同，彼时运行接口尚未迁移；T14的HTTP运行迁移与受控证据另列于文末，页面及真实来源仍待对应任务验收。实现、受控行为、真实来源通过分别登记。Expected evidence 是后续任务需提供的证据，不是已生成的产物或通过结果。
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

## RANGE-T11 单元事务层增量证据

2026-09-09的[恢复单元事务验证](../verification/RANGE-T11-unit-commit.md)记录本项机制及真实MySQL证据：六类显式数据库IT共105项、Core定向70项通过。下述仅确认单元事务；上表最终闭环功能结果保持原状态，首次／重试编排及HTTP由后续任务验证。

- **AC-PRD-RANGE-07：** 真实Processor生成合法空与同轮重复票据，事务成功后精确清理原失败项，全部旧业务列保留；R分别为0与原来源行数，I／U均为0，首次两表不变。
- **AC-PRD-RANGE-13：** 仅可信COMMITTED发布完整来源R及实际待写键I／U候选；重复来源R=3而I=1／U=1，索引及本轮累计由消费者在提交确认后更新，不按SQL组数或affectedRows推算。
- **AC-PRD-RANGE-15：** 整轮完整日历成功后保留全部必要身份，按本项完整DATE／RANGE及锁内冻结公共条件检查，全闭才删除已有项。前项闭市但后项缺日历时T11调用为零、两项保留；测试不代表生产日历来源已核实。
- **AC-PRD-RANGE-21：** 同日两股、不连续日期、完整REQUEST／STOCK RANGE和其他任务按完整五字段键隔离；仅最后一项成功才删除主表，其他成功只改变updated_at，task_params和created_at不变。
- **AC-PRD-RANGE-24：** 真实任务锁先于数据集锁，全部业务组和管理SQL共用一次物理事务；分组、精确明细DELETE、touch或空主表DELETE失败均回滚B，已提交A和旧数据保留。两锁持续到完成，失败原因在独立事务确认保存后才允许C继续。
- **AC-PRD-RANGE-26：** 启动不可用、提交阶段前明确回滚、结果未知及确认提交后的框架异常分别表达。真实commit丢答复、rollback失败、commit失败后rollback成功均不冒充明确失败；未知不计数、不确认索引、不更新／补建失败项，已确认提交后框架异常则保留已知计数并停止。


## RANGE-T12 首次编排层增量证据

2026-09-09的[首次下载编排验证](../verification/RANGE-T12-initial-execution.md)记录独立Core首次用例、共用槽位和真实MySQL组合证据：Core定向114项、Service独立20项、六类显式App IT62项及App helper31项通过；生产verify775、acceptance778项Java检查与各170项前端测试／构建通过。以下仅为本项机制；上表最终功能AC保持原状态，精确重试、新HTTP及真实断连／来源仍由后续任务验收。

- **AC-PRD-RANGE-05／07：** 首次合法单日、全空、空与失败组合及同轮重复有准确结果；空单元经过确认提交才计S，R保留完整来源行数，I/U只含实际提交。真实空下载保留旧业务哨兵且两失败表为空。
- **AC-PRD-RANGE-08：** 一次受控完整fina_audit响应包含独立STOCK A/B/C，B跨SQL组后组失败使B全部回滚，旧B整行不变、A/C保留，只有B＋DATE失败。独立SQL及事务轨迹确认失败保存提交先于C写入；测试内存能力不升级生产STOCK策略。
- **AC-PRD-RANGE-10／12：** 3／7日限流／超时后仍实际调用十日全部范围，一个任务仅两项失败且原1～10日JSON不变。Core对9类业务来源错误逐一验证保存后继续，包括retryable=false；无自动重试、缩小失败批次或旧download回退。
- **AC-PRD-RANGE-13：** S/F/N/H按恢复单元及不同休市日期计，R/I/U只累计已确认提交。显式已知失败先计F，不当未开始；未来独立股票成员未知时N=null。已有失败taskId及R=2/I=1/U=1小计在后续四类停止中保留，确认提交后框架故障仍累计当前成功。
- **AC-PRD-RANGE-15／17：** 完整日历、完整计划与全部轻量Session检查均先于首个业务请求。全闭无业务／失败事务；日历未确认、参数或适配配置拒绝不创建任务。生产日历／完整来源表仍为空，新入口安全拒绝而不启用真实业务。
- **AC-PRD-RANGE-23／27／28：** 新旧首次及重试lease共用一个进程槽位。fetch、commit、create和append各处阻塞时保持busy，等待者超时不取消原线程；结束或真实异常退出后才释放。此项证明Core生命周期，真实HTTP断连与新错误响应映射留待T14／T18。
- **AC-PRD-RANGE-26：** 真实首明细保存失败无半条结构、追加touch失败保留原记录并停止；首次保存未知ID=null，追加未知保留确认ID且remaining=null。物理提交丢回复只报告unknown及以前确认小计，不补建失败；存储不可用、明确回滚和提交未知分别处理，不重建未开始范围。

## RANGE-T13 精确重试层增量证据

2026-09-09的[原任务精确重试验证](../verification/RANGE-T13-exact-retry.md)记录独立Core重试用例和App真实MySQL组合。最终Core定向130项、七类App最新复合证据73项（原七类70项＋新Retry11项替换旧8项）及helper31项通过；verify795、acceptance798项Java与各170项前端测试／构建通过。完整构建后的评审补测只改测试，已由定向运行覆盖；独立Core／App规格与质量复审和最终集成／证据评审均PASS，无未解决缺口。以下仅确认本项机制，上表最终功能AC保持原状态。

- **AC-PRD-RANGE-03／14：** 精确重建当前DATE／MONTH／RANGE／NONE；REQUEST策略增强后仍整体执行，完整RANGE不拆分，原始展示日期不扩张实际请求。margin及原生日历单日使用相等起止；MONTH只恢复保存月份。
- **AC-PRD-RANGE-12／17／25：** 九类明确来源错误只更新原项安全原因、确认保存后继续。全部映射／Session／整轮日历／来源计划先于首个业务或任务写入；生产日历拒绝保留已有任务，不启用真实来源能力。
- **AC-PRD-RANGE-15：** 全闭清理不计S/R/I/U，H对跨股票同日去重并排除保留RANGE覆盖日；混合开闭RANGE保持完整请求。全闭DELETE实际回滚时原项仍在，不能直接从内存抹除。
- **AC-PRD-RANGE-21／22／24：** MySQL原1～10日仅请求当前3／7日，3解决后重建服务只读7，JSON及created_at不变；同日三股使用真实fina_audit复合键，B跨SQL组后精确删除失败使B全部回滚、旧行保留，A/C各自提交删除。独立连接确认B原因已提交后才获取C，最后成功项原子删除主表。
- **AC-PRD-RANGE-23／28：** 新旧首次与重试共用槽位，busy先于重试唯一读取；find／fetch／普通提交／闭市清理／原因更新阻塞时持有，等待者超时不取消。服务重建不重插已删除项，缺失任务返回Core NOT_FOUND；真实HTTP映射／断连仍由T14／T18验收。
- **AC-PRD-RANGE-26：** 业务、精确删除与主表收尾故障同事务回滚；原因保存未知停止且不覆盖邻项。真实commit丢答复和COMMITTED后框架故障分别处理，S/F/N/H、R/I/U及剩余项依据确认事实；不readback、补记或重建原任务。独立MySQL确认最后两表已删但回复丢失时taskId及remaining均null且仍unknown；最后业务／全闭COMMITTED后框架异常保留确认事实，taskId=null、remaining=0，全闭业务计数为0、H=1。

## RANGE-T14 HTTP层增量证据

2026-09-09的[下载与失败任务HTTP验证](../verification/RANGE-T14-http.md)记录运行元数据、首次下载和三个失败任务端点的本轮行为，精确命令、最终计数、生产mapper及显式MySQL证据以该报告为准。下述仅确认HTTP与受控来源／数据库组合；上表最终功能AC保持原状态，页面、真实socket／进程中断及真实来源由后续任务验收。

- **AC-PRD-RANGE-01／19：** 真实49项按独立OpenAPI target逐项核对19／15／1／3／11、九种形状及38＋11；元数据使用下载投影和五字段公开策略，股票／市场保留。HTTP改走executeInitial，新区间接受、旧日期单独／混用拒绝，11项不增加日期；未将静态候选计为真实来源通过。
- **AC-PRD-RANGE-06：** 同日／闰日／公历年份／逆序／缺端及31天允许、32天拒绝通过实际绑定检查；非法输入先于业务，前端表单限制与提示仍由T15交付。
- **AC-PRD-RANGE-13：** 首次及重试分别五种正常200结果；18字段结果从Core确认快照逐项映射，nullable计数显式null、大整数为JSON整数，实际生产Spring mapper保持原records精度。全闭S0/H>0，F不等于剩余失败总数；停止错误保留此前确认R/I/U。
- **AC-PRD-RANGE-22：** MySQL＋HTTP原1～10日、当前3／7日、重试后仅7日及最终删除闭环通过；GET保留公共JSON／created_at，原日期四状态与全部当前selector独立。离线／移除插件保留保存标识；列表无N+1，GET零来源／适配／写入／槽位获取。
- **AC-PRD-RANGE-23／25：** execute完整UUID和真正零字节body先校验，无效400、合法busy409且SQL0、空槽缺失404。详情一次原子slot快照，busy→插件→静态兼容→来源→日历优先；过滤仅影响安全展示，原map仍用于不兼容判定，点击时重新校验。损坏／孤儿记录安全QUERY_FAILED，不隐藏或修复。
- **AC-PRD-RANGE-26：** 四停止码500及retryable／安全快照通过；首次保存失败仍是已知F，物理commit丢回复只报unknown，不从SQL反推HTTP确认。最后项实际已删但回复未知时taskId／remaining为null，确认删除后框架错误则null／0；13种固定安全原因、26码映射及敏感哨兵通过。
- **AC-PRD-RANGE-27／28：** 两个POST同步，fetch／commit／原因更新有界阻塞期间等待者超时不cancel、slot保持busy、GET200和竞争POST409，真实结束才释放。业务结束后的MockMvc输出IOException不重发、不改变保存记录；没有真实socket断连或进程kill验收，不能用本项证据替代T18。

生产FixturePlugin仍只有旧download；HTTP成功fixture是明确的测试内批次包装，并有未包装fixture的完整性拒绝／零写入证据。生产fixture新区间能力留T18，生产Tushare日历与完整来源注册表仍为空、REQUEST策略未升级、ISSUE-008九项未调用。

## RANGE-T15 表单层增量证据

2026-09-09的[区间表单验证](../verification/RANGE-T15-form.md)记录本轮表单实现、命令、开发失败与修正、独立评审及验收边界。以下只确认输入／提交和本地状态行为；上表最终功能AC仍需其后续责任任务提供完整证据，不把表单通过解释为来源或日历已经可用。

- **AC-PRD-RANGE-01／19：** 测试fixture独立逐项对照49份原Dataset、公开策略及OpenAPI；19／15／1／3／11、38＋11、九形状及原非日期条件匹配。每个实际mount的表单保留声明参数顺序，区间使用新两端，原条件不新增日期；下载请求仅一次POST。
- **AC-PRD-RANGE-03：** 股票规范化及原市场条件保留；income与margin等示例提交原条件加新起止日期。该证据只覆盖输入，不替代T13原任务精确重试。
- **AC-PRD-RANGE-04：** 2026-01-31～03-02共31天、覆盖2026-01／02／03三个月；摘要列完整月份但提交仍为原两端。同月、单日和跨年年月不混淆，浏览器不展开批次。
- **AC-PRD-RANGE-05／06：** 日期显示YYYY-MM-DD、提交YYYYMMDD，公历及低年份运算、包含两端31／32天、服务端上限改2的校验和提示通过。非法字段阻止请求并沿用首错聚焦；两时区结果一致。不增加未来／最早历史日期限制，不推测未来数据已经可下载。
- **AC-PRD-RANGE-18：** 下载方式和时间语义来自公开policy；月份、交易日期和原生日期说明分开，保留服务端来源／日历未核实限定。原生日期文案不是筛选与取全的实测证据。
- **AC-PRD-RANGE-20：** 修改条件或切换接口清理旧result／error／failedOperation，执行期间保留锁定，新增首次提交仍一次请求。不会修改数据库失败任务；T16继续处理新结果和断连，T17提供当前失败任务页面。

本轮只用受控metadata／响应和本地表单，不调用Tushare业务API。生产完整来源和日历门禁保持原状，ISSUE-008九项仍不依赖、未解决；T18～T20验收范围保持。

## RANGE-T16 下载结果与通信边界增量证据

2026-09-09的[下载结果与断连提示验证](../verification/RANGE-T16-results.md)记录18字段／26码Axios边界、页面状态及受控浏览器结果。指定137项、全量278项及构建通过；本节只确认前端适用部分，不将导航合同视为T17任务内容页，也不将受控HTTP响应视为真实来源或后端闭环。

- **AC-PRD-RANGE-07：** EMPTY显示已完成恢复单元和确认0行；合法空单元加失败仍为PARTIAL，不生成占位记录或把R0重新判为空。数据库不删旧数据仍由后端证据验收。
- **AC-PRD-RANGE-13：** 显示S/F/N/H及确认R/I/U；A/C成功、B失败为2／1与10／7／3。F和当前remaining分别展示，N／remaining的null不是0，不按日期数、数组长度或全表行数重算。
- **AC-PRD-RANGE-15：** NO_OPEN_DATES为S0/H3并明确适用日历跳过，不混同来源合法空；没有特定任务入口。真实来源日历仍待后续验收。
- **AC-PRD-RANGE-17：** CALENDAR_UNCONFIRMED展示“未开始下载：日历未确认”，无计数／任务，保留真实拒绝原因；不将所有前置错误都宣称业务调用为零。
- **AC-PRD-RANGE-20：** 改日期／股票／枚举或切来源／API清除本轮结果和上下文；只有响应确认taskId有“查看重试任务”，精确query交给T17，不自动execute。主动再次提交是新RequestId的一次首次下载。
- **AC-PRD-RANGE-26：** 四停止码快照保留此前确认小计及实际失败／未开始／未知范围；未知保存仍可显示已确认taskId，确认删除不补回旧ID。TIMEOUT／NETWORK／INVALID_RESPONSE／UNEXPECTED无伪造计数或任务，明确未终止／回滚／成功的未知边界。
- **AC-PRD-RANGE-27：** 本地等待期间禁用条件和重复提交；DOWNLOAD_BUSY显示真实拒绝，不轮询排队。metadata重载保留，下载失败不再重放原区间；任务内容execute的按钮／404闭环由T17验收。
- **AC-PRD-RANGE-28：** 区间等待固定不可终止文案，无取消或进度。设置／数据查看切页保留同一请求及晚到结果，彻底卸载重挂只读metadata；本地通信结束不宣称服务端槽位已释放。实际socket／进程中断仍由T18验收。


## RANGE-T17 失败任务页面增量证据

2026-09-09的[失败任务页面验证](../verification/RANGE-T17-retry-page.md)记录本次215项指定／356项全量前端、1708模块构建、静态合同及Chromium1440／390受控页面证据。以下仅确认前端适用部分；上表最终功能AC保持原状态，MySQL故障／真实socket和进程中断、完整后端浏览器及真实来源分别由T18～T20验收。

- **AC-PRD-RANGE-20：** 六种本轮重试状态复用确认计数与范围，任务详情只展示当前保存记录；四停止码Axios快照保留小计和null，四类通信未知不补造历史计数／任务身份。首次结果入口真实打开失败任务页签并GET。
- **AC-PRD-RANGE-21：** 原UUID直接执行零字节POST，原始日期与公共条件不回传；稀疏3／7仅重试当前记录，明确响应后读取的列表与详情仅剩7，不按S／F自行删除明细。
- **AC-PRD-RANGE-22：** 原始1～10日始终与剩余失败范围分开；同日两股分别展示和消除。四种原始状态、完整MONTH／RANGE／NONE、保存标识／公共条件／原因可读；独立筛选和20／50／100分页；真实多页延迟读取保留第2页刷新及第3页导航，无隐式第1页GET，读取错误不假空，最后解决读取真实空列表。
- **AC-PRD-RANGE-23：** 首次与重试双向本地互锁包含异步表单校验后的复检；快速重复点击只有一个execute。GET的canExecute／retrying仅快照，409后须显式成功GET才能再次主动重试，无轮询、排队或自动重发。
- **AC-PRD-RANGE-25：** 离线／未知插件仍显示保存标识；blocked及未知原始区间保留只读明细，错误原因／代码可见，条件按安全投影普通文本展示。未知执行及GET失败令旧许可失效；404不重建记录，不将查不到任务解释为无响应执行成功。
- **AC-PRD-RANGE-27：** 两个非销毁页签和原KeepAlive保留同一Promise及执行身份；等待时切设置／返回无第二POST，路由改目标在原执行结算后消费。彻底卸载重挂只GET现存任务；固定不可终止说明，无取消／进度及历史恢复。浏览器受控网络错误不代表真实服务器断连已验收。

## RANGE-T18 受控后端集成增量证据

2026-09-09 的[受控来源／MySQL验收报告](../verification/RANGE-T18-controlled-mysql.md)按TRD §12.3的17行索引本轮真实HTTP、受控来源、独立业务／两失败表SQL及新JAR进程证据。只升级下列后端适用事实；页面闭环、账号实测分别留给T19／T20，不改写上表的最终验收口径。

- **AC-02／05／15／17／18：** 受控日历缺日拒绝且业务0，全闭H独立计数、旧业务保持，重试闭日只删原项；ANN／MONTH／原生范围不套交易日历。native DATE的trade_cal/new_share/namechange在实际controller映射中保留紧凑相等start/end及真实日期语义，均有真实SQL。生产日历证据未升级。
- **AC-03／04／06／14：** 49形状与附加条件回归，31/32天、闰日／跨年、完整月及11原条件；income真实必填股票按股取数，REQUEST不升级，STOCK RANGE不裁剪，原08/15～09/03失败月份精确整9月重试。
- **AC-07／08／09／11／29：** 完整分页才发布，A/C已提交、B适配或SQL分组失败只回滚B；缺股不猜失败、不完整或归属未知保存精确REQUEST；合法空retry不删旧业务。同轮重复键确认由本轮重跑的BatchCommitServiceIT `emptyAndSameRoundDuplicateTicketsKeepDifferentSourceCountsAndOldBusinessColumns`及Core processor合同补证，不把来源行数当恢复单元数。
- **AC-10／12：** 九种来源错误各连续失败后仍尝试下一日期，显式retry第一项失败后下一项成功；两个多日开市段在同一execute中前段失败已保存后仍取后段，并提交真实3行。
- **AC-13／24／26：** I/U混合的业务写入与明细／最后主表删除同事务回滚；业务／创建／原因／删除各有提交前实际rollback和delegate.commit后未知证据，不反查补计数。框架已确认COMMITTED的异常单独保留计数；记录保存停止保留确认小计、remaining及未来股票N可为null。
- **AC-16：** 本轮显式BatchCommitServiceIT `wholeCalendarDecisionProjectsDatesWithoutDroppingMarketsAndPreservesBusiness`、`closedCalendarRejectsMissingDatesWrongConditionsAndOpenNecessaryMarketBeforeDeleting`核对完整多市场判定与实际旧业务保持；受控并集不证明真实来源日历覆盖，也不将停牌解释为休市。
- **AC-21／22／23／25：** 同日两股2→1→0分别删除；原1～10与当前3/7→7、created_at不变；进程重启只读已保存项，busy409／删除后404不排队或重插。不兼容记录来源0且原SQL保持；DB禁止坏JSON文本／未知enum的分支准确标为repository读取边界注入。
- **AC-27／28：** 新JAR真实RST与read-timeout-close覆盖首次／最后重试，断连后服务继续、实际结束前槽位仍忙；真实强杀／重启只恢复查询现存失败，不补未开始／中断项，没有取消或自动重发。

最终命令分别为fixture82、Core153、显式MySQL12类216、verify891、acceptance clean verify894（两构建各另356前端）；构建后HTTP schema补强27项、Process7＋Load3共10项、SQL cause补强46项分别零失败／错误／跳过，不拼成一次总运行。固定18,600／10,000／5,000行三类负载18轮、50ms采样、独立SQL与schema，安全及源码／包摘要见报告。49生产Dataset、两能力表和七生产迁移未变；ISSUE-008九项仍“不依赖，未解决”，本轮没有真实Tushare调用。
