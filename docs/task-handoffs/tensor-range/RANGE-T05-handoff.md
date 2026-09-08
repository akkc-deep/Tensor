# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-range/tensor-range-task-board.md`
- **Completed task:** `RANGE-T04`，已记录COMPLETED及验证证据。
- **Next task:** `RANGE-T05`，按既定Order选中的后继（Order 5）。
- **Design document:** `docs/task-designs/RANGE-T05-design.md`，详细设计已完成、完整读取并链接本项看板行。
- **Expected next status:** `READY`；写入本交接时观察为NOT_STARTED，先链接交接，再执行NOT_STARTED -> READY。READY不表示T05已启动。

## Next Task

`RANGE-T05`：区间参数绑定与恢复请求转换。

目标是让首次区间输入与失败选择器通过同一规则构造合法、精确的来源参数，区分原始展示日期与实际恢复时间。范围包括投影参数绑定、两个新增参数records、31天及日期／股票校验、插件内SourceParameterMapper与Core的DownloadParameterConverter；不实现下载循环、日历、取全、存储或运行HTTP切换。

验收按[详细设计](../../task-designs/RANGE-T05-design.md)：38项区间／11项原条件及9种形状可绑定；31天允许、32天及非法／旧／混用条件拒绝。DATE按候选生成单日期或相等起止，MONTH恢复完整月，RANGE保留原端点，NONE不增时间。首次对象／时间不能越过原输入；重试先从副本移除展示日期，再组合选择器，不覆盖条件、不改原值。旧记录缺两端可恢复，单边／非法日期拒绝；未确认来源不启用，当前运行HTTP仍消费sourceParameters。规定单测、verify、acceptance打包及合同检查通过，准确记录AC-PRD-RANGE-03／04／05／06／14／19／22／25的参数层证据。

## Dependencies

### RANGE-T04

- **Artifact:** `docs/task-designs/RANGE-T04-design.md`、`docs/verification/RANGE-T04-plugin-contracts.md`；`data-plane/tensor-plugin-api/src/main/java/com/akkc/tensor/plugin/api/descriptor/ApiDescriptor.java`；同模块 `download/DownloadPolicy.java`、`DownloadParameterProjection.java`、`RecoverySelector.java`、`RecoveryPolicy.java`；`data-plane/tensor-plugin-tushare/src/main/resources/download/tushare-pro-policies.yaml`及同目录schema；当前Core的`validation/ParameterValidator.java`和Web的`download/DownloadParameterResolver.java`、`ParameterShape.java`、`ParameterJsonReader.java`、`ParameterCodec.java`、`DownloadParameters.java`。
- **Decision:** ApiDescriptor.parameters为下载投影，sourceParameters从原Dataset派生；38／11及19／15／1／3／11分类固定。来源方式DATE／MONTH／RANGE／NONE与页面模式分开，49项均REQUEST，完整性未确认；8项候选为空，forecast为请求冲突。选择器保留STOCK／REQUEST及DATE／MONTH／RANGE／NONE，日期相等使用DATE。当前HTTP元数据、绑定、执行及日志统一消费sourceParameters，T14统一切换。
- **Rationale:** 页面输入范围不等于上游合法请求形状；原生单日仍需相等起止，公告范围不能代替报告期。原展示日期必须保留而不能覆盖当前失败范围；已有sourceParameters过渡边界避免暴露不能执行的新表单。
- **Constraint:** 不改Dataset或策略以凑转换通过；候选可构造不代表来源取全已确认，REQUEST不豁免完整性。STOCK_TIME的targetField是行归属字段，不能猜成任意请求键；T05仅消费明确已声明ts_code及已验证恢复策略，生产49项不升级。无股票新入口、真实来源调用、数据库写入、前端或当前HTTP切换。原始股票／交易所条件保留，不换API，不解除ISSUE-008九项排除。
- **Usage:** 复用T04类型和投影列表实现两个HTTP records与resolveDownload；在插件API集中来源映射、Core复用校验器完成首次冻结／显示日期提取／恢复组合。明确拒绝不兼容保存内容，保持原map；mapInitial检查原股票及日期包含关系，mapRetry只依保存selector恢复，不把展示范围当执行范围。
- **Readiness evidence:** 看板已记录T04完成；49策略与T01／T02／T03逐项独立核对、49份Dataset完整定义及SHA-256不变、26码HTTP／retryable核对均PASS。完整test为521 Java＋170前端；verify另含4生产JAR合同，acceptance clean verify另含3验收JAR合同，均退出0。规格／质量／跨模块独立评审PASS，无遗留问题；代码及报告新文件已暂存，无提交或发布。数据库IT本轮仅编译未执行；无浏览器或真实API验证。以上证明T05所需合同可消费，不证明来源日历、取全或区间执行已可用。

本项唯一直接依赖为T04；其内部保留的T01／T02证据及T03参数／错误合同约束一致。monthly与margin_detail的日历冲突仍保留，T05不处理日历提供器。设计复核已补齐首次STOCK不可替换已指定股票的规则，不存在待接手人决定的材料性范围或接口缺口。

## Start Here

按顺序读取：

1. 完整读取 `docs/task-designs/RANGE-T05-design.md`，再核对本看板T05状态及本交接；收到明确启动请求后执行READY -> IN_PROGRESS并保留交接路径。
2. `docs/design/Tensor_区间下载_PRD_v1.0.md` §3、§7.1；`docs/design/Tensor_区间下载_TRD_v1.0.md` §4.3、§7.1.1、§7.1.3。
3. `data-plane/tensor-app/src/main/java/com/akkc/tensor/web/download/`、`data-plane/tensor-core/src/main/java/com/akkc/tensor/core/validation/ParameterValidator.java`。
4. 上述T04实际类型、生产策略、设计及验证报告；必要时对照`docs/contracts/openapi-v1.yaml`的9种参数形状和`docs/contracts/error-codes.md`，来源限制以策略引用的T01／T02证据为准。

**First action:** 在 `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/validation/ParameterValidatorTest.java` 用共享T04类型构造受控tradeRange描述符，先写20260131～20260302允许、至20260303拒绝的31／32天断言，执行 `mvn -f data-plane/pom.xml -pl tensor-core -am test`，确认现有ApiDescriptor重载尚未拒绝32天，再按已完成设计实现校验与转换。Core测试不新增Tushare依赖；当前HTTP入口切换留在T14。

## Risks

- T05代码、转换测试和本项验证报告尚未实施；本交接只准备进入T05，不能将T04测试结果写成T05通过。
- Map形状通过不证明来源合法性／完整性／日历已确认，后续执行须保留独立门槛；8项缺方式、forecast冲突及9项真实调用排除继续保留。
- 原始展示范围、公共条件和恢复selector各有用途；不能将task_params整体透传，不能因缺少旧展示日期推算原区间，不能把同日两股折叠。
- T05新绑定只作为独立入口验证；Deserializer、Controller、执行、元数据和日志仍按T04源参数运行，后续切换须同版完成。
- 数据库IT、浏览器和真实来源未验证；本项继续准确报告各类证据，不能以打包结果替代数据库或真实来源通过。
