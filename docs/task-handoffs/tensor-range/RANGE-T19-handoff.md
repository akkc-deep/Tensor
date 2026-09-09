# Next Task Handoff

## Handoff Type

next-task

## Task Link

- **Task board:** `docs/task-handoffs/tensor-range/tensor-range-task-board.md`（[看板](tensor-range-task-board.md)）。
- **Completed task:** `RANGE-T18`，已记录COMPLETED；以下设计与交接准备发生在该完成记录之后。
- **Next task:** `RANGE-T19`，按预定义Order选择19，观察状态NOT_STARTED。
- **Design document:** `docs/task-designs/RANGE-T19-design.md`（[完整设计](../../task-designs/RANGE-T19-design.md)），已全文读取、自审及独立就绪评审PASS，先仅回填Design单元格。最终SHA-256为`6ba18160fc9e8c014bc2f5579b6a759b51f664e0e767c70d186b19ea9c806e01`。
- **Expected next status:** `READY`；本交接写入、链接后执行NOT_STARTED→READY，不自动启动实施。

## Next Task

`RANGE-T19`：浏览器闭环与现有页面回归。

在正式页面完成区间下载、查询已提交数据、失败任务查看和反复手动重试。消费T18受控来源与真实隔离MySQL后端；49表单合同、日期边界、结果、列表／详情、切页／刷新／断连和现有只读查询均有明确证据。不使用真实Tushare调用替代可控故障，不实施T20。

可观察验收：

- 新JAR实际供页，四spec／两尺寸的专用Playwright配置、前端全量单测／build、acceptance clean verify及bootstrap真实HTTP回归通过；实际数量／失败／跳过与本轮原始日志对应。
- 真实REQUEST原1～10、失败3/7→仅7→无，以及STOCK同日两股2→1→0；一次初次POST、原UUID零字节execute、原task JSON/created_at、来源精确参数／页序、业务和两失败表SQL互相核对。
- 切页／刷新／重启及真实浏览器连接丢失不取消或重发；在执行中真实409，最后删除后404不重插，unknown不伪造计数／任务或推断成功。无取消／暂停／进度入口，键盘可用。
- 49形状／日期、七计数／nullable、长文本转义及查询精确数值／null／分页在桌面1440×1100和窄屏390×844可用；29项AC逐项注明REAL或FRONTEND_CONTRACT及后端证据，截图对应最终JAR资源。
- 58生产资源、fixture YAML/V6、原索引和任务外工作保护；新正式文件入Git，不提交／发布，最终独立规格／质量／集成／证据评审通过。只在完成记录之后按流程准备T20，不能猜造来源／账号就绪。

## Dependencies

### RANGE-T15：49表单与日期语义

- **Artifact:** [T15设计](../../task-designs/RANGE-T15-design.md)、[表单验证](../../verification/RANGE-T15-form.md)；`control-plane/src/components/download/DynamicParameterForm.vue`、`ApiDescription.vue`、`control-plane/src/composables/useParameterForm.js`、`control-plane/src/utils/downloadPolicy.js`及`control-plane/src/test/fixtures/range-apis.json`。
- **Decision:** 49项分为19/15/1/3/11，38区间使用两日期、11保留原条件；股票／市场条件沿用真实必填与枚举。公历日期包含两端、上限31天，输入原两端不被月份展开改写。
- **Rationale:** 下载日期与来源日期语义不同，31天可覆盖三个自然月；表单合同与真实来源可执行性必须分开。
- **Constraint:** 不增加未来日期禁限、股票输入或浏览器分批；不恢复旧trade_date/ann_date/month形状。修改条件清旧结果、首错聚焦、校验失败零POST；原查询通用表单保持。
- **Usage:** range-contracts逐项使用现有49fixture及相同填值规则；真实fixture验证日期／模式代表。生产来源disabled时用明确前端metadata合同层，不通过开启Tushare绕过可用性限制。
- **Readiness evidence:** T15已COMPLETED；96项指定、两时区各22、全量215及build通过，49逐项独立核对及最终评审PASS。下游T17/T18完整前端已回归为356项；这些是输入历史数字，不作为T19的新结果。

### RANGE-T16：结果、固定上下文与通信未知

- **Artifact:** [T16设计](../../task-designs/RANGE-T16-design.md)、[结果验证](../../verification/RANGE-T16-results.md)；`control-plane/src/api/downloadResult.js`、`errors.js`、`http.js`、`control-plane/src/composables/useDownloadFlow.js`、`control-plane/src/components/download/DownloadResult.vue`、`control-plane/src/test/fixtures/range-results.json`。
- **Decision:** 执行结果18字段，200为SUCCESS/EMPTY/NO_OPEN_DATES/PARTIAL/FAILED；四停止码的可选快照为UNCONFIRMED。S/F/N/H和R/I/U分开，null不是0；三组范围与当前remaining不混为历史统计。
- **Rationale:** 页面只能显示服务端本轮确认事实，失去响应不证明服务端停止、回滚或成功；本轮与持久化当前任务不是同一数据。
- **Constraint:** Axios130000ms及一次POST、X-Request-Id保持；KeepAlive不自动重发。只有真实非null taskId提供入口，query为tab=retry-tasks与taskId；不从输入ID补未知结果。
- **Usage:** 迁移两旧e2e的8字段／三计数／旧标题，真实fixture结果对照SQL；复杂commit未知的显示用明确合同层并引用T18真实事务证据。透明代理仅切断本轮浏览器下游，不能改产品timeout或伪装正常响应。
- **Readiness evidence:** T16已COMPLETED；指定137、全量278、build及四停止码真实Axios补证通过，双尺寸页面观察和最终独立评审PASS；T18两次构建各356项前端通过。T16的拦截页面不代替本项真实后端闭环。

### RANGE-T17：当前任务页与精确重试

- **Artifact:** [T17设计](../../task-designs/RANGE-T17-design.md)、[任务页验证](../../verification/RANGE-T17-retry-page.md)；`control-plane/src/views/DownloadView.vue`、`control-plane/src/api/retryTasks.js`、`control-plane/src/composables/useRetryTaskFlow.js`、`control-plane/src/components/download/RetryTaskList.vue`、`RetryTaskDetail.vue`及`control-plane/src/test/fixtures/retry-tasks.json`。
- **Decision:** 两页签保留实例；列表／详情仅读现存任务，原始区间与当前完整选择器分开。默认20、可选50/100、筛选相互独立；execute原UUID、真正零body、无条件query、无确认弹窗。
- **Rationale:** 原始区间解释任务来源，重试只由现存明细决定；GET权限是快照，稍后真实POST仍可能409/404。
- **Constraint:** 不把同日两股合并，不根据3/7首尾推原区间；旧缺日期为“未记录”，原条件“不适用”。未知／409需要显式GET恢复权限，404不重建、不推断成功。列表加载期不得因page-count=0自动回第1页。
- **Usage:** 主线真实source／SQL核对3/7→7与两股逐项删除；41项真实预置覆盖page2刷新／page3延迟GET、50/100及独立筛选。实际GET原因由后端safeReason按code映射；512长原因显示仅在合法前端合同响应中验证。查询页面没有行详情入口，只验证现有表格／tooltip／表内滚动。
- **Readiness evidence:** T17已COMPLETED；指定215、全量356、build1708模块、两尺寸实际Axios/受控浏览器及最终评审PASS；Element Plus额外page1真实RED已修复并全量重跑。前端历史截图使用API拦截，T19应连接T18受控后端。

### RANGE-T18：实际受控来源、JAR与独立MySQL

- **Artifact:** [T18设计](../../task-designs/RANGE-T18-design.md)、[受控／MySQL报告](../../verification/RANGE-T18-controlled-mysql.md)；`data-plane/tensor-plugin-fixture/src/main/java/com/akkc/tensor/plugin/fixture/FixtureBatchSource.java`、`FixturePlugin.java`、`FixtureConfiguration.java`；`data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture/support/ControlledRangeSource.java`；`RangeFixtureHttpIT.java`、`RangeProcessIT.java`、`RangeFailureMySqlIT.java`（后三者在`data-plane/tensor-app/src/test/java/com/akkc/tensor/fixture/`）；四份`data-plane/tensor-app/src/test/resources/fixture/range-browser-{initial,partial,success,two-stocks}.json`。
- **Decision:** acceptance runtime支持五mode；DATE模式可选STOCK_TIME，默认REQUEST；全部分页证明完整前不发布数据。ControlledRangeSource提供精确apiName/params/page匹配、replaceScript/calls/clearCalls及hold/entered/release；四脚本含REQUEST十日和STOCK精确股票重试键。
- **Rationale:** 浏览器需要实际可执行后端及跨重启保留的父来源；测试控制不能进入产品HTTP／打包。前页失败不能先写入，不可靠成员不能猜单股失败。
- **Constraint:** 仅loopback、真实Tushare关闭、真实Flyway及隔离mysql:8.4.6；不改58生产资源、fixture四业务列或V6。JSON数值由reader与client保留BigDecimal。终页缺证安全REQUEST；错误不回显来源body/header或异常cause。清理只处理自己创建的进程和容器。
- **Usage:** 新test-only RangeBrowserHarness直接复用source API，持有容器、来源和实际JAR，并用原始JDBC snapshot。四脚本原件只读，派生脚本在临时目录；ANN REQUEST使用initial→partial→success，STOCK使用two-stocks→partial→success。公共日期compact YYYYMMDD，存储selector为ISO；Java桥与Node之间只使用设计固定stdin/stdout协议。bootstrap显式RangeFixtureHttpIT编译桥并生成真实classpath，不能让T19重写分页或依赖旧target文件。
- **Readiness evidence:** T18已COMPLETED，最终独立规格／质量／集成／证据PASS，无Important/Critical。fixture82／Core153／显式MySQL216／verify891／acceptance894各通过；构建后HTTP schema27、Process7＋Load3、SQL cause46各单独零失败／错误／跳过，不能拼成同一次总数。三类负载18轮及17行来源／独立SQL完整。T18 JAR摘要见设计，207runtime、58资源、816原索引保护通过；13新文件已入Git，无提交。

以上输入没有未决冲突：T15表单、T16确认口径、T17当前失败查询与T18实际后端共同遵守原条件冻结／精确剩余重试。设计复审纠正了safeReason导致真实长原因不直接展示、当前数据查看没有行详情、旧spec viewport覆盖窄屏配置三处，均已关闭。历史其他e2e和真实来源套件未选，不计为本项通过。

## Start Here

1. 完整读取[本项设计](../../task-designs/RANGE-T19-design.md)，核对[看板](tensor-range-task-board.md)的RANGE-T19和本交接；READY只表示准备就绪。
2. 按设计顺序读取PRD指定章节及29AC、TRD §8/§12，再读现有两个e2e、package／Playwright配置和上述T15～T18设计／验证／实际接口。
3. 获得本项明确启动请求后记录READY→IN_PROGRESS，保存当时的新分支／HEAD／原索引和任务外／58资源基线；不直接复用T18的816或测试数量作为本项证据。
4. **实施第一动作：** 将`fixture-flow.spec.js`迁移为现有18字段／七计数／“下载完成”合同，并建立设计已固定的最小Java测试桥及Node helper，先运行SUCCESS→真实数据查询小闭环。旧断言不匹配只记验收迁移证据；真正UI行为失败先RED再最小修复。无需再选择运行结构、协议、测试范围或补设计，随后按四spec、双尺寸及29AC矩阵推进。

## Risks

- T19尚未实施、未运行其e2e；依赖历史通过只证明输入可用。所有本项真实结果必须有自己的最终构建／截图／来源／SQL和原始测试输出。
- 49表单及难以稳定注入的commit未知属于明确前端合同层，主线必须连接真实fixture／MySQL；受控通过不外推真实Tushare，ISSUE-008不恢复调用。
- 历史e2e有旧参数、旧字段和固定包／环境；专用配置明确四spec，两迁移文件移除硬编码viewport，实际window尺寸要核对，未选历史套件不得报告通过。
- 透明代理只关闭浏览器下游，JAR直接socket故障引用T18；不修改产品等待／增加取消或自动重发。测试桥不打包，stdin EOF和异常均清理自有资源。
- 实际SQL遵守JSON/selector CHECK及512/255字符边界；任务API固定安全原因，查询只能使用现有单元格／tooltip，不为了测试新增行详情或回显原文。
- T20账号权限及实际代表来源范围需后续依据真实输入设计；本交接不预定或假定其可执行性。新增正式文件入Git，原索引／任务外工作保持，不提交／发布。
