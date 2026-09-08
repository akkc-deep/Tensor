# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-range/tensor-range-task-board.md`
- **Completed task:** `RANGE-T06`，已记录COMPLETED及本轮验证证据。
- **Next task:** `RANGE-T07`，按预定义Order选择的后继（Order 7）。
- **Design document:** `docs/task-designs/RANGE-T07-design.md`，已完成、完整读取、独立就绪复审READY并链接本项看板行。
- **Expected next status:** `READY`；写本交接时状态为NOT_STARTED，先链接本交接，再执行NOT_STARTED -> READY。READY不表示实现已启动。

## Next Task

`RANGE-T07`：Tushare 完整批次获取与来源错误。

实施明确的完整批次fetchBatch入口和Tushare执行器，使用接口专属来源Session确定合法分页及结束依据。所有页、身份／参数／字段协议、游标／实际请求进展、总数及全集检查全部通过才返回完整FetchResult；后页失败、矛盾、已知截断和取全未知均整批拒绝，保留原参数、精确数值和安全来源错误，不自动重试或生成逐股失败。

验收见[专属设计](../../task-designs/RANGE-T07-design.md)：生产49项保持Q／C证据拒绝，trade_cal的BSE条件额外拒绝；受控两页、合法空、后页失败、游标循环、总数矛盾、参数污染、上下文故障及来源范围矛盾都有精确输入和错误预期。生产来源合同注册表固定为空；合成page_token不代表真实Tushare分页支持。当前三参download与HTTP保持既定迁移边界，新入口不能回退旧方法绕过取全。完成规定验证及来源层追踪，不提前实施T08／T09／T12／T14。

## Dependencies

### RANGE-T01

- **Artifact:** `docs/research/RANGE-T01-source-capabilities.md`、`docs/research/RANGE-T01-source-evidence.json`；来源核实设计为`docs/task-designs/RANGE-T01-design.md`。
- **Decision:** 49目标19／15／1／3／11；40 DOCUMENTED_CANDIDATE、8 UNCONFIRMED、forecast CONFLICT；全部REQUEST及整体取全／分页未确认。45份可读输入表均未列通用limit/offset，4份缺页。stock_basic的单次全量说明及trade_cal逐交易所／自然日全集判据保留为继续核实依据，不能直接升级为当前运行通过。
- **Rationale:** 请求合法性、整体取全、分页和独立恢复是分别成立的门槛；HTTP200、短页、达到上限、两次一致或返回股票列都不能独立证明完整性。
- **Constraint:** 没有合法请求先SOURCE_REQUEST_UNCONFIRMED；缺完整合同SOURCE_COMPLETENESS_UNCONFIRMED；只有确认截断才SOURCE_TRUNCATED。trade_cal(BSE)没有公开请求依据；公告／报告期差异、缺股票及forecast冲突不补猜。ISSUE-008九项真实排除不变。
- **Usage:** T07按请求证据先守门，再查生产空来源合同注册表；受控Session只验证机制，不生成真实来源分页键或结束保证，不加载docs作为运行配置。保留所有原条件及完整REQUEST范围。
- **Readiness evidence:** 看板T01已COMPLETED；49集合、分组、YAML摘要、9排除、5日期差异、来源引用和报告校验PASS。研究未调用业务API，候选数量不是本轮可执行数量；该输入足以实现明确拒绝和受控机制，不证明生产取全可用。

### RANGE-T04

- **Artifact:** `docs/task-designs/RANGE-T04-design.md`、`docs/verification/RANGE-T04-plugin-contracts.md`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/DataSourcePlugin.java`及同模块`download/FetchBatch.java`、`FetchResult.java`、`DownloadEnvelope.java`、`DownloadContext.java`、`DownloadPolicy.java`；`data-plane/tensor-plugin-tushare/src/main/resources/download/tushare-pro-policies.yaml`。
- **Decision:** FetchBatch冻结完整原来源参数及恢复策略；FetchResult保存完整包络及明确单元失败。生产49项恢复为REQUEST，完整性／分页状态仅UNCONFIRMED。DownloadContext检查服务端故障，异常原样传播。当前三参download仍承接原HTTP路径。
- **Rationale:** 同一完整来源批次和最终包络参数必须一致，不能变成最后一页参数；类型结构不授予取全或独立恢复许可。当前HTTP仍接受原sourceParameters，必须与后续执行切换配套。
- **Constraint:** 不恢复旧两参download或增加二进制兼容层；T07新增fetchBatch是明确完整批次语义入口，默认拒绝且绝不委托旧download。原HTTP路径保留，T12完整执行只用新入口，T14再统一切换。无可信逐股来源状态时failures必须为空。
- **Usage:** 扩展共享SPI及相应穷举反射测试；复用现有FetchBatch／FetchResult／Envelope结构，不改策略资源。Tushare执行器逐页调用现有client，最后以原baseParams构造完整包络。服务端初始、每次POST前后和最终检查按T07精确顺序实施。
- **Readiness evidence:** 看板T04已COMPLETED；SPI／49策略／投影／旧运行回归及独立评审通过，报告记录521个Java单测、170个前端测试及生产／acceptance打包合同通过；数据库IT仅编译。取全策略未被这些测试升级。

### RANGE-T05

- **Artifact:** `docs/task-designs/RANGE-T05-design.md`、`docs/verification/RANGE-T05-parameter-conversion.md`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/download/SourceParameterMapper.java`；`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/download/DownloadParameterConverter.java`及其测试；`data-plane/tensor-plugin-api/src/test/java/com/akkc/tensor/plugin/api/download/SourceParameterMapperTest.java`。
- **Decision:** DATE／MONTH／RANGE／NONE共用精确转换；REQUEST保留原股票和市场，原生单日为相等起止，月份为完整年月。原始展示日期不替代失败选择器；转换不授予执行许可。
- **Rationale:** 首次与重试必须从同一规则重建原来源条件，不能为分页或取全缩小股票／日期／市场，不能根据原区间重建已成功部分。
- **Constraint:** 未确认来源／冲突仍为来源请求错误，非法保存条件为RETRY_TASK_INVALID；不猜字段、不将REQUEST改STOCK。当前HTTP仍消费sourceParameters，T07不得切换绑定／Core旧服务／元数据／日志。
- **Usage:** 用真实SourceParameterMapper及REQUEST选择器生成T07测试批次，验证daily、income、margin、月份、原生范围和原条件到WireMock实际JSON逐键一致。执行器只添加来源合同明确的分页控制，不重新转换业务条件，合并结果恢复原完整参数。
- **Readiness evidence:** 看板T05已COMPLETED；49投影、31天边界、精确首次／恢复映射和原HTTP回归通过，规定全量593个Java单测、170个前端测试及两种打包合同通过，独立评审无遗留。未实施取全、规划、数据库或真实来源验证。

三个直接输入一致：T01提供能力依据及缺口，T04表达未确认策略和批次合同，T05只转换合法候选参数。新完整批次入口以来源合同独立守门；生产空注册表保留未确认事实，与旧HTTP待T14切换的边界不冲突。

## Start Here

按顺序读取：

1. 完整读取`docs/task-designs/RANGE-T07-design.md`，核对看板本项状态与本交接；收到明确启动请求后才执行READY -> IN_PROGRESS，保留本交接为入口上下文。
2. `docs/design/Tensor_区间下载_TRD_v1.0.md` §4.4／§5.2、`docs/research/2026-09-08-tushare-range-batch-research.md` §5。
3. `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/client/TushareProClient.java`、同目录`TushareResponseValidator.java`／`TushareErrorClassifier.java`，及对应`TushareProClientTest.java`。
4. T01完整报告及直接消费的逐项JSON，T04／T05设计、验证和上述共享批次／mapper合同。

**First action:** 在`PluginApiSurfaceTest`／`DownloadContractsTest`及`TushareProPluginTest`先添加fetchBatch签名、默认不委托旧download、生产daily候选请求完整性未确认且client调用0的断言，运行`mvn -f data-plane/pom.xml -pl tensor-plugin-api,tensor-plugin-tushare -am test`记录新增入口缺失的先行失败。再按已完成设计实施缺省门槛与Tushare委托；随后先写受控两页及后页失败用例，再实施最小执行器。

## Risks

- T07代码、完整批次SPI、Session和本项验证报告尚未实施或运行；READY只表示设计及交接已准备。
- 当前生产49项完整批次仍无启用合同，不能以受控COMPLETE回调、短响应、总数一致或修改状态字符串代替来源依据。未来注册生产来源需补足同API、同原条件的合法分页、稳定遍历、结束、对象／日期及截断证据。
- T07整体来源协议和覆盖检查不替代T09恢复归属或事务隔离；T12／T14必须证明完整执行只调用fetchBatch，不回退旧download。
- 服务端故障检查不是客户端取消；明细存储、断连语义及来源失败后的后续批次继续由相应任务实现。
- 来源真实支持、数据库／浏览器及最终功能AC仍未验证；ISSUE-008继续“不依赖，未解决”。本交接不启动T07实现。
