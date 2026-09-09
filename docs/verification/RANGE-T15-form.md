# RANGE-T15 区间表单验证

2026-09-09，对应[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的 RANGE-T15，依据[专属设计](../task-designs/RANGE-T15-design.md)和[入口交接](../task-handoffs/tensor-range/RANGE-T15-handoff.md)。表单实现、规定测试／构建以及独立规格、质量和最终集成／证据评审均通过，满足T15完成条件。

## 验收范围

本项只确认下载输入、日期语义、范围／月份摘要、一次请求和修改条件后的本地状态清理。结果状态及断连提示属于T16，失败任务页属于T17，受控后端、完整浏览器闭环及真实来源分别属于T18～T20。

前端测试输入由OpenAPI的49项目标清单、49份Dataset YAML和公开策略字段投影生成。它仅用于测试，不进入运行注册表；生产Java投影真实性引用[T14 HTTP验证](RANGE-T14-http.md)，不冒称本轮重新验证了服务端或真实来源。

## 资源与合同核对

独立Python核对器不调用Vue运行工具，逐项比较目标集合、mode／calendarProfile、九种参数形状及顺序、五字段策略和保留的原非日期描述符。最终逐项确认49唯一、19／15／1／3／11、38＋11及九种形状均匹配。49份Dataset、2份策略和7份生产迁移共58份资源摘要与开始时一致；791条原索引记录均保留，任务外文件及分支／HEAD保持不变。

静态合同首轮八组检查与四项变异拒绝均PASS，覆盖49目标、38迁移、11保留和29项AC。后端、OpenAPI、生产来源和日历没有纳入本次修改。

## 行为与命令证据

实现消费五字段公开策略，四类区间使用两端日期、服务端上限及常驻说明；原条件接口保持原参数数组。日期严格按公历校验，以UTC自然日期差计算包含两端天数，0001～0099不重映射；月份按年月枚举。31天跨三月仍提交原两端，不扩张日期或拆分请求。配置缺失／未知模式／不合法上限或配对字段、原生未知语义被明确阻止，包括零参数快速路径。修改条件清除本地结果、错误及旧失败重放信息；请求中沿用锁定。

本轮行为证据包含：真实49项分别实际mount／填值／校验及精确键顺序；股票规范化、市场条件保留；daily／income／margin／broker_recommend／trade_cal／new_share／namechange／stock_basic／index_classify的精确请求和原条件空对象；31允许／32拒绝、服务端上限改2的同步文案和校验、低年份／闰日／跨年／单日月份；原生及周线／月线文字、安全文本、首错聚焦、generic form和现有查询回归。配置测试逐项覆盖上限零／负／非整数、两端缺失／重复／类型错误／非必填／互指错误、三个旧日期键混入及原条件带新两端；均通过实际View观察明确错误和零下载。三个原生标签与说明、日期默认清空／reset、同形状及range→original→range切换均有显式测试。

| 命令（在control-plane运行，另注明者除外） | 最终结果 | 本地日志 |
|---|---|---|
| `npm test -- src/components/download/RangeDownloadForm.spec.js src/components/download/DynamicParameterForm.spec.js src/components/download/ApiDescription.spec.js src/views/DownloadView.spec.js src/composables/useDownloadFlow.spec.js src/api/api.spec.js src/utils/format.spec.js` | exit0，7文件96项 | `review-fix-focused.log` |
| `TZ=UTC npm test -- src/utils/format.spec.js src/components/download/RangeDownloadForm.spec.js` | exit0，2文件22项 | `review-fix-tz-utc.log` |
| `TZ=America/Los_Angeles npm test -- src/utils/format.spec.js src/components/download/RangeDownloadForm.spec.js` | exit0，2文件22项 | `review-fix-tz-la.log` |
| `npm test` | exit0，25文件215项 | `review-fix-full.log` |
| `npm run build` | exit0，1700模块 | `final-build.log` |
| 根目录 `PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py` | exit0，8组及4项变异拒绝 | `final-contract.log` |

最终测试均零失败／跳过；各行是独立运行，不能相加为唯一测试数。Node使用已安装的24.15.0，Vite8.2.2；build保留既有大于500kB的chunk提示，不更改打包配置以隐藏它。本项不修改后端，没有重跑MySQL或真实来源。

## 开发失败与修正

- 先行测试两个表单断言失败：旧标签没有策略语义，31天三月摘要缺失；同组`parametersChanged is not a function`单独记为缺接口，不能称作清理行为的断言RED。日志`red-behavior.log`为3失败／8通过。
- 在隔离的任务开始时源码副本中，用新增View测试重放两个场景：32天产生第二次下载调用，修改输入后旧失败结果仍存在。选中的2项均断言失败，无导入／缺接口错误；日志`baseline-regression-red.log`中17项是过滤未运行。这是实施后的基线回归证明，不冒称实施前TDD。
- 全量回归最初发现AppLayout切页测试仍使用旧下载metadata：175通过／1失败。仅迁移该测试为真实daily范围合同，布局实现未改；最终切页保留请求及查询回归通过。
- 核对发现原生日期语义与模式名共用标签表，会误接受`MONTH_RANGE`。View配置错误场景先RED（`red-native-collision.log`），随后使用独立原生语义表及own-key判断，原生未知／原型键不能通过。
- 关联日期错误清理最初会影响无上限的通用表单，并擦除另一端必填错误。修正为只在下载显式启用上限时清理生成的逆序／超长错误，保留required／type及通用行为；新增回归通过。

## 页面观察

本地Vite开发页、Playwright Chromium受控HTTP响应，桌面1440×1100和窄屏390×844一轮合并检查：日期摘要及三个月完整月份文字可读，桌面两列、窄屏单列，document横向宽度分别1440／390，无横向溢出。实际输入2026-01-31～03-02后，捕获恰好一次POST，参数为`start_date=20260131/end_date=20260302`；修改结束日为03-03清理旧结果，提交不增加请求并聚焦start_date错误。两种宽度均无pageerror，截图已人工查看；`visual-results.json`、`visual.log`及`form-1440.png`／`form-390.png`保留本地。

该页面观察仅证明表单消费受控合同，未连接运行后端，不作为T19完整浏览器闭环或真实数据源通过。初次本地启动因沙箱不能listen／启动Chromium失败，取得本地测试授权后执行；临时脚本的选择器歧义和变量声明错误修正后运行通过，这些不是产品缺陷。Impeccable检测三个修改Vue文件返回空发现列表（`impeccable.json`）。

停止开发服务时，其累计日志出现两条`ResizeObserver loop completed with undelivered notifications`通知。随后针对最终build启动本地生产预览，在相同两种尺寸重复上述合并检查，并同时捕获浏览器console error及pageerror，均为空；单次POST、编辑清理、32天拒绝、首错聚焦及无横向溢出再次通过，两张最终截图已人工查看。该通知在此次生产预览中未复现，未据此推断具体原因或修改产品。结果和截图记录于`final-visual-results.json`、`final-visual.log`及`final-form-1440.png`／`final-form-390.png`；两个本地服务均已停止。

## 独立评审与差异保护

独立规格／质量初审认为运行实现符合设计、没有新增运行缺陷，但以Important指出部分固定测试矩阵尚未编码。按原设计补齐配置拒绝、全部原生语义及四个精确请求示例，并补强默认值／reset和同形状切换；只改测试，最终96／22／22／215项通过。初审与修正记录保留在`task-review.md`和`implementation-report.md`；独立复审规格PASS、质量PASS，Important已关闭且无遗留发现。最终整体集成／证据评审规格PASS、质量PASS、完成就绪PASS，无Critical／Important／Minor遗留；独立复核17份最终文件摘要、49项fixture、58份资源、791条原索引以及实际最终日志，见`final-review.md`。

`git diff --check`与`git diff --cached --check`均退出0。三个新增前端文件已纳入Git，原791条索引内容保持原样；本报告及后继正式文档按流程加入Git，不暂存其他既有文件的新内容。

原始日志、开始时基线、任务内diff、独立核对脚本及本地截图位于仓库忽略目录`.superpowers/sdd/RANGE-T15/`；本报告是正式版本控制产物。开始时分支为`feat/date-range-download`，HEAD为`758f940503ded2d1185040bc8e324815c716a300`，原索引791条。既有T11～T14及ISSUE-017等工作保留；本轮不提交、发布或切换分支。
