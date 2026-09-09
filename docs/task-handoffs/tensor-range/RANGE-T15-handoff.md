# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-range/tensor-range-task-board.md`（[看板](tensor-range-task-board.md)）。
- **Completed task:** `RANGE-T14`，已记录COMPLETED；完成记录先于本设计和交接创建。
- **Next task:** `RANGE-T15`，按预定义Order选择15；观察到NOT_STARTED。
- **Design document:** `docs/task-designs/RANGE-T15-design.md`（[完整设计](../../task-designs/RANGE-T15-design.md)），已完整读取、自审就绪并回填看板Design document。
- **Expected next status:** `READY`；本交接写入并链接后执行NOT_STARTED -> READY，不自动启动实现。

## Next Task

`RANGE-T15`：区间表单、日期说明与参数迁移。

下载页消费已迁移的公开策略与新请求合同，准确展示五类输入／时间含义、自然日及完整月份摘要，每次提交一个合法请求。复用现有日期控件和共享表单，只增加下载所需校验；保留只读数据查看的合同、布局和主题。

可观察验收：

- 49项按19／15／1／3／11呈现，38项新两端、11项原条件；股票／市场条件及默认值保留，选择接口日期为空。
- 日期YYYY-MM-DD显示、YYYYMMDD提交，无时区偏移；上限来自服务端，31允许32拒绝，同日／闰日／逆序／跨年正确。
- 2026-01-31～03-02共31天，摘要列完整2026-01／02／03三个月；提交原两端，不展开输入或浏览器分批。
- 时间说明保留公开原生语义及来源未核实限定，周线／月线不变为每日或完整周／月；原条件不宣称“最新快照”。
- 无效字段首错聚焦且零下载，配置不完整明确错误并阻止提交；合法请求恰好一个POST。修改条件／切换接口清理旧结果及旧失败操作，执行中沿用锁定。
- 规定表单／DOM／Axios及两个时区测试、完整前端测试／build和静态合同通过；记录AC-PRD-RANGE-01、03、04、05、06、18、19、20的表单增量，不把T16～T20工作计为完成。

## Dependencies

### RANGE-T14：公开元数据

- **Artifact:** [T14设计](../../task-designs/RANGE-T14-design.md) §3、[T14最终验证](../../verification/RANGE-T14-http.md)、[OpenAPI](../../contracts/openapi-v1.yaml)的ApiDescriptor／DownloadPolicy及`x-tensor-range-targets`；运行投影为`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/dto/ApiDescriptorResponse.java`。
- **Decision:** parameters已是下载投影；策略只含mode／dateSemantic／description／calendarProfile／limits。38项DATE_RANGE_MEMBER两端必填互指、非日期条件在前，11项参数保持原数组；四区间limits.maxRangeDays=31，原条件limits=null。
- **Rationale:** 页面必须与同版服务端绑定一致，时间含义和输入上限由已发布元数据提供；查询定义独立。
- **Constraint:** 不读sourceParameters或私有来源证据，不按queryMode猜模式，不硬编码31默认，不改变49项／九形状。原生CALENDAR_DATE／IPO_DATE／ANN_DATE可用于文字，但保留description的实际筛选／完整性待核实限定；静态策略不是实测可用证明。
- **Usage:** 按T15设计接入downloadPolicy、局部标签副本／配置错误、范围上限及摘要，按真实资源和独立target准备49项测试输入。不得修改生产策略或Dataset以适配页面。
- **Readiness evidence:** T14真实49项HTTP逐项核对19／15／1／3／11、38＋11、九形状、五字段／null／C-A通过；最终首三HTTP39、App九类236及显式MySQL八类114项零失败／错误／跳过。生产投影和Spring mapper／servlet已检验；这不是T15表单测试结果。

### RANGE-T14：首次下载请求合同

- **Artifact:** [T14设计](../../task-designs/RANGE-T14-design.md) §3～§4、[验证报告](../../verification/RANGE-T14-http.md)、[OpenAPI](../../contracts/openapi-v1.yaml)的DownloadRequest／DownloadExecutionResult；实现为`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/DownloadController.java`及`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/download/DownloadRequestDeserializer.java`／`data-plane/tensor-app/src/main/java/com/akkc/tensor/web/download/DownloadParameterResolver.java`。
- **Decision:** 一次`POST /api/v1/downloads`只提交`{pluginId,apiName,params}`。标识及声明参数为JSON字符串，日期YYYYMMDD；38项旧trade_date／ann_date／month单独或混用都拒绝。服务端仍验证包含两端31天并调用executeInitial，客户端不拆日／月请求。
- **Rationale:** 区间规划、执行继续／停止、结果确认由服务端统一处理；前端需要提交原输入范围和原股票／市场条件。
- **Constraint:** 不增加旧参数兼容、自动重试、取消或额外输入范围；原生单日仍两端相等，MONTH_RANGE仍传原日期。结果按18字段返回且可有新outcome，不能把参数提交验收当作T16新结果展示已经实现。
- **Usage:** 沿用downloadDataset的单次POST及既有请求ID；更新请求mock和JSDoc，Axios adapter验证真实body。首次调用前完成表单校验，非法零请求；修改条件清理旧本地结果和旧失败操作，数据库任务不受影响。
- **Readiness evidence:** T14已补正并复验生产Spring mapper未知外层字段与布尔标识强转两项缺陷，实际servlet均返回安全400，合法字符串进入原有来源门禁。最终39／80／236／114项聚焦及verify829／acceptance832项Java、各170项前端测试和build通过；独立Core／App与最终集成评审PASS，无遗留发现。T14来源门禁仍保守关闭，不承诺合法表单可从真实账号取全。

### 现有下载页与共享表单

- **Artifact:** `control-plane/src/components/download/DynamicParameterForm.vue`、`control-plane/src/components/download/ApiDescription.vue`；`control-plane/src/composables/useParameterForm.js`、`control-plane/src/composables/useFormValidation.js`、`control-plane/src/composables/useDownloadFlow.js`；`control-plane/src/utils/date.js`；`control-plane/src/views/DownloadView.vue`；`control-plane/src/api/dataSources.js`、`control-plane/src/api/downloads.js`及邻近spec。
- **Decision:** 保留MetadataField和现有Element Plus日期控件、通用归一化／首错聚焦；以可选maxRangeDays参数启用下载长度校验。通过表单change和parametersChanged清理旧本地状态，生产下载页传策略并先检查配置，零参数分支也不绕过策略守卫。
- **Rationale:** 现有控件已有日期格式、股票trim／大写和关联日期校验；最小修改可复用这些行为，避免影响只读查询。
- **Constraint:** 不修改props／通用默认行为／查询合同或ISSUE-017样式。保留执行锁定与一次POST，不实现T16状态机、T17失败任务页。0001～0099不能用会重映射年份的Date.UTC快捷方式；月份枚举仅在输入长度合法后进行。
- **Usage:** 从完整设计的具体文件／接口和测试表实施；旧generic组件测试保留，下载View旧日期mock按新合同迁移。既有synthetic49数量测试不能冒充实际49项覆盖。
- **Readiness evidence:** T14最后两次完整构建各24前端测试文件／170项及Vite build通过，前端源码在T14期间未改。这只确认已有基线；范围上限、月份摘要、策略接入和参数修改清理尚未实施，需T15本轮验证。

以上输入决策一致，无未解决冲突：T14已提供公开合同，现有页面复用机制能按T15固定设计接入；来源／日历未确认和T16状态缺口是明确的任务边界，不通过改后端或伪造页面结果补齐。

## Start Here

1. 完整阅读[本项设计](../../task-designs/RANGE-T15-design.md)，核对[看板](tensor-range-task-board.md)的RANGE-T15状态及本交接。
2. 阅读[PRD §2.2、§3、§7](../../design/Tensor_区间下载_PRD_v1.0.md)和[TRD §4.3、§8.3、§10.1](../../design/Tensor_区间下载_TRD_v1.0.md)，再读T14设计／验证、OpenAPI公开元数据及请求合同。
3. 读取上列现有组件、composable、日期工具和API测试，保留当前分支／HEAD／原索引及任务外工作。
4. **实施第一动作：** 获得本项明确启动请求并记录READY -> IN_PROGRESS后，保存当前基线，按设计准备真实49项测试输入，先为daily只提交新两端、31天跨三月／32天零请求、修改参数清理旧结果三条可观察行为写RED；再实现固定的最小接口和流程。无需另补设计。

## Risks

- 服务端合同已迁移，旧前端参数请求会被拒绝；不增加兼容回退。来源和日历尚未真实核实，downloadAvailable只表示配置可用。
- 生产fixture只实现旧download，新HTTP正向fixture是T14测试内包装；生产批次能力由T18提供，本项不要改fixture或调用真实来源以制造成功。
- T15只完成输入／说明／清理。新结果、保存／提交未知和通信未确认由T16处理，失败任务页由T17处理，真实浏览器与来源验收由T19／T20处理。
- 共享表单长度校验必须显式启用，日期两时区／低年份及完整月份必须实测；不额外设置未来或最早历史日期禁用。
- 本设计及交接未运行T15功能测试；READY不代表表单已完成。新增正式文件加入Git，不提交／发布，保留原暂存和ISSUE-017工作。ISSUE-008九项真实调用仍不依赖、未解决。
