# RANGE-T05 参数绑定与恢复转换验证

本记录对应[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的 `RANGE-T05`，依据为[详细设计](../task-designs/RANGE-T05-design.md)。本轮执行日期为2026-09-08，实现、规定验证与独立评审均已完成。

## 实施结果

- `ParameterValidator.validate(ApiDescriptor, Map)` 校验投影参数并限制包含两端最多31天，列表重载保留原运行合同；日期／月份拒绝0000年，股票规范化后最多64字符。
- `DownloadParameters`及codec补齐股票＋范围、exchange_id＋范围；`resolveDownload`用真实投影及规范化值绑定。当前Deserializer仍调用原`resolve`，Controller、DownloadService、元数据DTO和日志继续消费sourceParameters。
- `SourceParameterMapper`集中转换DATE／MONTH／RANGE／NONE；DATE根据策略生成单日期或相等起止，月份保持完整月，RANGE保留原端点。REQUEST保留原股票／市场；即使策略增强为STOCK_TIME也保留REQUEST回退。STOCK只允许有明确ts_code来源合同且独立恢复已核实的受控策略。
- `DownloadParameterConverter`冻结原输入和公共参数，首次单元校验对象及日期包含关系，重试从副本提取展示日期，再共用mapper重建完整来源条件。原区间不裁剪或扩大失败选择器，原map保持不变；旧记录缺两端返回null展示范围，单边／非法／冲突内容返回安全RETRY_TASK_INVALID。
- 来源方式缺失或冲突保留SOURCE_REQUEST_UNCONFIRMED；结构转换不授予执行许可。没有日历、来源、数据库、自动拆分或重发副作用。

## 测试证据

### 先行失败与修正

1. 首个实施动作按交接写入31／32天用例。`mvn -f data-plane/pom.xml -pl tensor-core -am test`退出1：32天未抛异常，Core共89个测试、1个失败。再加入年份／股票长度边界后，同命令为4个失败；修正后89个Core测试全部通过。
2. Mapper测试先于类写入，插件API编译因缺少SourceParameterMapper及其嵌套类型失败。随后REQUEST在STOCK_TIME策略下的回退测试先行失败（97个API测试、1个错误）；移除过严的REQUEST策略限制后97个全部通过。
3. Core转换测试先于实现写入，编译确认DownloadParameterConverter缺失；同轮也发现AssertJ通配静态导入与DATE枚举歧义，改为明确断言导入。该首轮仅是编译失败证据，不作为运行行为反例。后续8个转换测试全部通过。
4. Web测试先于新入口和records写入，全量reactor到app时因缺少resolveDownload及两个records编译失败；Core、插件及前端当轮已通过。补齐绑定后全量测试通过。

Maven沿用T04已确认的沙箱外执行环境，以支持Mockito附加JVM；没有修改依赖或跳过断言规避环境限制。日志在`/tmp/tensor-range-t05/`，临时日志不作为交付文件。

### 实际命令

从仓库根执行；计数来自本轮日志，不沿用T04结果。

| 命令 | 退出码 | 实际结果 |
|---|---:|---|
| `mvn -f data-plane/pom.xml -pl tensor-plugin-api,tensor-core -am test` | 0 | API 97＋Core 97，共194个Java测试，零失败／错误／跳过 |
| `mvn -f data-plane/pom.xml test` | 0 | 593个Java测试（97／97／100／13／286），170个前端测试和前端构建通过；27.881秒 |
| `mvn -f data-plane/pom.xml verify` | 0 | 593个单测＋4个生产JAR合同，共597个Java测试；170个前端测试及构建通过，29.543秒 |
| `mvn -f data-plane/pom.xml -Pacceptance clean verify` | 0 | 593个单测＋4个生产JAR＋3个acceptance JAR合同，共600个Java测试；170个前端测试及构建通过，34.845秒 |
| `PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py` | 0 | 8组合同检查及4个内存变异反例全部PASS |
| `git diff --check`、`git diff --cached --check` | 0 | 暂存与未暂存空白检查通过；交接收尾时复核 |
| 实施前后SHA-256逐文件核对 | 0 | 49份Dataset YAML及2份生产策略资源完全不变 |

### 行为覆盖

| 范围 | 本轮可观察结果 |
|---|---|
| 日期边界 | 20260131～20260302允许、至20260303拒绝；单日、闰日、跨年、0001／9999边界合法；0000、非法闰日、逆序、缺失、类型及枚举错误拒绝。TS_CODE去空格转大写，64字符允许、65拒绝 |
| 投影绑定 | `DownloadParameterResolverTest`使用真实49项策略／Dataset投影，加fixture共50项逐项绑定；38区间／11原条件、9种生产形状、新records、规范化写回及suppliedFields过滤通过；旧日期单独及混用、未知键、非法日期／类型、32天拒绝 |
| 精确来源 | `SourceParameterMapperTest`及app实际策略样例检查daily、income、margin、fina_indicator、trade_cal、new_share、namechange、broker_recommend、stock_basic、index_classify；三原生接口单日均为相等起止。REQUEST多日保持端点；月份为完整月 |
| 原始区间 | `DownloadParameterConverterTest`证明原1～10日保持，3／7日分别只请求本日；首次单元越界及换股拒绝，重试展示范围不约束或改写合法选择器；跨三月按各完整月份映射，旧记录缺两端不推算展示区间 |
| 对象与拒绝 | 受控income的两股同日分别生成精确请求，STOCK主表不重复代码，REQUEST保留原股票；未知股票请求字段、重复承载、时间不兼容拒绝。49项生产策略均不能接受STOCK，8项未确认及forecast冲突保持来源错误 |
| 不可变与安全 | 输入修改不影响冻结结果，输出不可改；首次和重试同一单元结果相等。非法保存内容不变，错误为固定摘要且无cause；mapper无Core／Spring／Jackson依赖，转换组件无来源或数据库调用 |
| 当前HTTP回归 | `DownloadRequestBindingTest`新增旧month及超过31天原生范围回归，连同旧daily和原条件通过；fixture原scenario绑定保留。旧服务仍用列表校验，元数据和日志仍使用sourceParameters |

## 评审与验收边界

独立评审已读取完整T05设计、相对T04暂存基线的专属变更包、实现及本轮测试日志，规格符合性、代码质量和跨模块集成均PASS；无Critical／Important／可操作Minor遗留问题。评审未重复运行测试。补齐的REQUEST回退规则与设计一致，不改变策略或任务对象类型。

本任务仅交付独立参数绑定与转换能力，未切换运行HTTP入口，未实现区间执行、日历、取全、失败表或页面。当前POM不调度数据库`*IT.java`，Failsafe只运行打包合同；数据库／容器IT本轮仅编译，未执行。浏览器E2E及真实业务API均未运行；受控STOCK正例不计为真实独立恢复通过。ISSUE-008九项继续“不依赖，未解决”，未恢复真实调用。

AC-PRD-RANGE-03／04／05／06／14／19／22／25的本任务参数层证据回填[增量追踪](../traceability/tensor-range-requirements.md#range-t05-参数层增量证据)。最终功能与真实来源验收仍由看板后续任务负责。
