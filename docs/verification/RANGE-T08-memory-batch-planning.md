# RANGE-T08 内存批次规划验证

2026-09-08执行，对应[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的RANGE-T08，依据为[专属设计](../task-designs/RANGE-T08-design.md)。本记录只证明首次规划机制、受控来源建议及生产拒绝边界，不表示真实Tushare下载能力已经启用。

## 交付行为

- 新增独立的`DownloadBatchPlanner.planInitial`，先校验并冻结原参数，再检查插件身份及可用性。原始展示区间保持用户输入；每批冻结精确REQUEST时间边界、来源参数和原恢复策略，不创建独立股票恢复单元。
- 交易模式一次确认完整原条件和原日期集合，消费全部必要日历的开盘并集；RANGE候选按连续开盘片段规划，DATE候选逐开盘日。全闭市跳过来源预检，仍完成覆盖及最终服务端检查后返回零批。
- 公告保留周末及全部自然日；月份展开完整年月；原生范围始终一批，单日映射为相等起止；原条件精确保留输入或空map。11项原条件中9个NONE候选受控通过，hs_const／index_member按现有未知方式明确拒绝，不为pledge_stat补股票。
- 新增`planBatch`及Tushare来源`plan`的默认拒绝入口。Tushare预检和取数共用同一只读来源表及身份／请求守门；预检不调用open、Session、业务client或旧download。生产来源及日历表保持空。
- 每个最终批次必须取得针对其精确参数的肯定建议。只有正常返回SINGLE_DATE才允许交易／公告多日候选在取数前分日，每天重新映射和预检；异常不触发降级或自动拆批。原生模式拒绝逐日建议。
- 返回前联合检查公共对象条件与时间原子；逐键重建来源map并拒绝换股、条件丢失、策略变化、重复、重叠、遗漏、扩大或逆序。计划及嵌套集合不可变，不缓存跨次日历或来源建议。预检通过不豁免后续fetchBatch的独立完整性验证。

## 实际命令与结果

命令从仓库根运行；需要Mockito附加JVM及本机WireMock的Maven命令使用已建立的沙箱外执行环境。未更改依赖、禁用断言或跳过检查。临时日志位于`/private/tmp/tensor-range-t08/`；下表保存持久结果。

| 命令 | 退出码 | 实际结果 |
|---|---:|---|
| `mvn -f data-plane/pom.xml -pl tensor-core -am -Dtest=DownloadBatchPlannerTest -Dsurefire.failIfNoSpecifiedTests=false test` | 0 | 矩阵阶段18项通过；后补3项身份／证据／跨模式边界用例由最终模块及完整构建覆盖，最终规划器21项 |
| `mvn -f data-plane/pom.xml -pl tensor-plugin-api,tensor-core,tensor-plugin-tushare -am test` | 0 | 最终370项（API99／Core118／Tushare153），13.708秒 |
| `mvn -f data-plane/pom.xml test` | 0 | 669项Java单测（99／118／153／13／286）、170项前端测试及构建，29.286秒 |
| `mvn -f data-plane/pom.xml verify` | 0 | 669项Java单测＋4项生产JAR合同，共673；170项前端测试及构建，31.588秒 |
| `mvn -f data-plane/pom.xml -Pacceptance clean verify` | 0 | 669项Java单测＋4项生产JAR＋3项acceptance JAR合同，共676；170项前端测试及构建，36.651秒 |
| `PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py` | 0 | 8组合同检查及4个内存变异反例全部PASS |
| `PYTHONDONTWRITEBYTECODE=1 python3 /private/tmp/tensor-range-t08/audit-resources.py` | 0 | 51份资源摘要、49份T01摘要、分类／证据／日历／排除边界全部PASS |
| `PYTHONDONTWRITEBYTECODE=1 python3 /private/tmp/tensor-range-t08/audit-scope.py` | 0 | 变更仅涉及T08交付及工作流文档，原分支及HEAD保持 |
| `git diff --check`、`git diff --cached --check`及文档相对链接检查 | 0 | 空白与链接检查通过；本轮交付已加入Git暂存，未提交或发布 |

实际调度检查均零失败、零错误、零跳过；保留既有Maven编码及Mockito动态代理／JVM附加警告。全量test运行期间最终将income成功样例从9月4～6日对齐设计的4～7日；随后最终模块及两次打包验证覆盖该精确样例，生产代码未因此变化。

## 测试先行及修正

1. 首个实施动作写入broker_recommend的20260131～20260302三完整月及至20260303的32天零回调反例。定向命令退出1，仅因缺少DownloadBatchPlanner类型；日志`01-missing-planner-red.log`，这是编译缺实现证据。
2. 最小骨架只校验参数后抛UnsupportedOperationException；随后先写默认预检拒绝和显式SINGLE_DATE用例。`02-missing-plan-spi-red.log`确认SPI方法缺失；增加默认方法后，`04-planner-behavior-red.log`中19项测试有2个运行错误，均为未实现规划，32天拒绝已通过。实施算法后同一组19项全绿。
3. Tushare预检测试先于入口实现，`06-tushare-plan-red.log`编译确认执行器plan缺失；接线后45项定向测试通过。随后补充取数独立性、身份／可用性、精确生产条件及完整Core矩阵，最终三模块370项通过。
4. 两次测试设置错误分别为DATE静态导入歧义及误用ApiDescriptor.description访问器，修正后继续验证，未计为行为反例。后续矩阵为回归扩展，不宣称每个新增断言都单独见过失败。

## 可观察覆盖

| 范围 | 本轮断言 |
|---|---|
| 五类精确序列 | margin开盘1／2／4／7日产生1～2、4、7三个不跨休市的批次；显式分日后为四个相等起止请求，保留exchange_id。daily／top_list／top_inst只用trade_date，dividend／disclosure_date／fina_indicator只用ann_date |
| 公告与原生 | income规范股票并覆盖9月4～7日含周末；原生三接口多日一批、单日相等起止，trade_cal保留exchange、不添加is_open，不确认辅助日历 |
| 月份与原条件 | 31天跨1／2／3月，每个完整月仅一次；单日月份、跨年、0001／9999边界通过。11原条件分9候选／2未知，枚举变体及空参数按实际合同保留 |
| 日历与初始校验 | 所有四类范围32天均零回调拒绝；缺端、非法日期、类型、枚举及旧字段先拒绝。日历按完整原条件／日期确认一次，多市场取并集，错误scope／null拒绝，全闭零预检；非交易类别无日历调用 |
| 预检与失败 | RANGE优先整体确认；明确逐日建议后每个单日独立确认。第二个单日拒绝即停止后续预检，不返回前缀。来源请求／完整性／超时异常不触发降级；null、UNCONFIRMED及不兼容建议按设计拒绝 |
| 联合覆盖反例 | 重复／重叠／缺失／越界／休市空档／逆序、月份重复或遗漏、原生拆批、原条件重复或空批、非交易skipped及错误时间形状均拒绝。换股、删股票／市场、改枚举、加键、改恢复策略或STOCK scope均拒绝 |
| 冻结与服务端故障 | 输入及外部集合修改不污染计划，输出集合不可修改。相同输入下次重新确认和规划；初始、日历前后、预检前后及最终6个检查位置均保持同一故障实例且不返回Plan |
| Tushare生产与来源合同 | 49项逐一为9项请求拒绝／40项完整性拒绝；stock_basic L/P/D及trade_cal SSE/SZSE不误启用，BSE额外请求拒绝。精确脚本、默认／空／未知建议、源与服务端异常、插件成功委托均有零业务调用及顺序断言 |
| 预检与fetch独立 | 预检成功后，fetch的open拒绝、UNCONFIRMED、TRUNCATED或后页异常仍整批失败；T07原分页、全集及context事件数测试继续通过，无旧download回退 |

## 独立评审与验收边界

独立评审完整核对T08设计、十个Java文件的专属增量、21个Core测试方法及API／Tushare新增测试，规格PASS、质量APPROVED，无遗留Critical／Important／Minor问题。最终集成复核独立逐类汇总370／669／673／676项日志并核对前端测试、时长、JAR合同及文档验收边界，同样PASS／APPROVED；未重复运行测试。资源与范围审计由主流程实际执行通过。

当前DownloadService、HTTP、fixture运行入口和生产配置与本轮基线相同；新规划器未作为Spring Bean接入。51份受保护资源逐文件SHA-256不变，分类19／15／1／3／11、请求状态40／8／1、49项REQUEST／完整性未知、日历19项的1／16／2状态及九项真实调用排除均保持。

当前Surefire不调度数据库`*IT.java`，Failsafe仅执行规定的JAR合同。数据库／容器IT仅编译、未执行；浏览器E2E、真实日历和真实业务API未运行，未读取真实凭证。受控预检不证明真实来源支持，ISSUE-008保持“不依赖，未解决”。

本任务回填AC-PRD-RANGE-02／03／04／05／06／14／15／18的[规划层证据](../traceability/tensor-range-requirements.md#range-t08-内存规划层增量证据)，最终功能AC、恢复单元、数据库事务及HTTP闭环仍由后续任务验收。
