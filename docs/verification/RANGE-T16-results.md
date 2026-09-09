# RANGE-T16 下载结果与断连提示验证

2026-09-09，对应[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的RANGE-T16，依据[专属设计](../task-designs/RANGE-T16-design.md)和[入口交接](../task-handoffs/tensor-range/RANGE-T16-handoff.md)。本轮已实现结果合同校验、页面状态和结果展示，规定测试、构建及受控页面观察通过；完成状态以看板及下文最终评审记录为准。

## 行为证据

- 正常200的SUCCESS／EMPTY／NO_OPEN_DATES／PARTIAL／FAILED按原outcome展示；停止错误显示UNCONFIRMED。结果守卫严格检查18字段、原始类型、JS安全整数、nullable N／remaining、完整UUID、对象／时间选择器及既有结果语义；不允许200未知结果，不通过数字转换或默认0掩盖坏数据。
- Axios边界保留一次POST，校验body／响应头RequestId及本次pluginId／apiName。26码的HTTP状态／retryable、原五字段错误和四种停止码可选快照均经真实Axios adapter验证。快照只接受UNCONFIRMED、匹配外层RequestId并复制冻结所有范围数组和元素；未知码、非法快照／字段／计数／范围安全降级为INVALID_RESPONSE，不回显正文哨兵。原查询错误和精确数字合同回归通过。
- 页面显示七项S/F/N/H、R/I/U及独立的剩余失败项／记录状态。A/C已完成、B失败显示2／1及10／7／3；合法空单元与失败混合仍为PARTIAL。全休市S0/H3与EMPTY分开，日历未确认无虚构计数／任务。N及remaining的null显示“未确认”，0保持0，不从范围数组推算数量；F1／remaining2分别展示。
- 三组范围各自保留次序和边界：同日不同股、REQUEST的3／7日、完整RANGE、完整月份和NONE均可读，不合并稀疏日期或股票。REQUEST附加条件排除start/end并按原顺序显示；全部文本由Vue转义。仅实际非null taskId出现“查看重试任务”，最新保存未确认时仍保留此前确认的任务ID，确认删除后不补回旧ID。
- 本地executionContext固定本次标识、参数副本和rangeMode。执行中锁定来源／API／实际日期输入及重复按钮，显示固定不可终止提示，无进度或取消。KeepAlive切到设置或数据查看再返回仍为同一请求；晚到PARTIAL和错误快照保持原上下文。彻底卸载重挂只重新加载metadata，不重发下载。
- TIMEOUT／NETWORK／INVALID_RESPONSE／UNEXPECTED均为结果未确认且无伪造计数／任务；本地等待结束可解除控件锁定，明确服务端可能仍在执行。仅metadata保留重新加载，移除DOWNLOAD原参数重放。主动再次提交是新的首次POST，真实Axios和浏览器均验证新RequestId；若收到DOWNLOAD_BUSY则展示真实拒绝，不排队／轮询。
- 修改日期、股票、枚举、来源或接口清空旧结果／错误／上下文。任务按钮只接受当前result.taskId，导航到`{name:'downloads',query:{tab:'retry-tasks',taskId}}`，丢弃旧query且不增加下载POST。T17尚未交付任务内容，本项只验收导航合同。

## 命令与新鲜结果

工作目录为`control-plane/`（另注明者除外），Node24.15.0、Vitest4.1.11、Vite8.2.2。测试日志和任务开始时源码／索引快照位于仓库忽略目录`.superpowers/sdd/RANGE-T16-design/`。以下各行是独立运行，不相加为唯一用例数。

| 命令 | 实际结果 | 本地日志 |
|---|---|---|
| 启动基线`npm test` | exit0，25文件215项 | `baseline-test.log` |
| `npm test -- src/api/downloadResult.spec.js src/api/api.spec.js src/api/errors.spec.js src/composables/useDownloadFlow.spec.js src/components/download/DownloadResult.spec.js src/views/DownloadView.spec.js src/layouts/AppLayout.spec.js` | exit0，7文件137项，零失败／跳过 | `focused-final.log` |
| `npm test` | exit0，27文件278项，零失败／跳过 | `full-final.log` |
| `npm run build` | exit0，1701模块；保留既有大于500kB的chunk提示 | `build-final.log` |
| 根目录`PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py` | exit0，8组合同检查及4项变异拒绝；49目标／38迁移／11保留／29AC | `contract-final.log` |
| 根目录`node .superpowers/sdd/RANGE-T16-design/stopped-axios-probe.mjs` | exit0，四停止码逐项经过生产downloadDataset／http及真实Axios adapter；每项一次POST、500及retryable正确、null／小计／复制冻结范围保留、四个不同请求ID | `stopped-axios-probe.log` |
| 根目录`git diff --check`、`git diff --cached --check` | 均exit0 | 本轮工具记录 |
| Impeccable对两个修改Vue文件的检测 | exit0，发现列表为空 | `impeccable.json` |

本轮完整前端包含T15真实49项／31天／月份／归一化、公共表单、旧查询错误、精确数值、metadata重载及主题布局回归。API独立指定组28项通过；verbose集成日志记录现有故意非法点击／畸形元数据测试的Vue警告，未关闭断言或为消除提示改动公共组件。没有运行MySQL或真实来源，前端测试不替代后端验收。

## 开发失败记录

先行真实页面两项行为RED：PARTIAL没有结果面板；超时实际显示“下载失败”及“使用原参数重试”。`ui-red.log`为2断言失败，过滤未运行38项，无导入错误。Axios500快照先行RED实际被降级为ClientError，见`api-red-axios500.log`。守卫缺文件导入失败单列于`api-red-guard-import.log`；最小导出后的6项行为失败单列于`api-red-guard-behavior.log`，不把导入失败冒称行为证据。26码／错误快照／正常身份边界RED为5失败／13通过，见`api-red-boundaries.log`。

集成读取发现正则会把单元素数组转换成字符串，可能接受非字符串标识或UUID。增加原始类型变异测试先出现2失败／8通过，再加入显式string检查，最终API28项通过。未改变服务端执行限制或records大数合同。

本地UI首次修复后的超时测试用了全页dl选择器，误含既有ApiDescription；改为只检查DownloadResult。股票／枚举编辑测试首次漏填margin必填市场，未发请求即查结果导致测试设置失败；补填SSE后5项定向测试通过。API快照尚未合入时UI107项有9项预期集成失败，合入后最终指定137项全部通过；这些中间日志不作为最终通过依据。

最终评审发现初版报告把四停止码的normalizeError单元覆盖写成全部通过Axios：原Axios用例只覆盖TASK_RECORD_SAVE_UNCONFIRMED，浏览器覆盖COMMIT_UNCONFIRMED。已补独立四码Axios探针及逐项日志，覆盖PERSISTENCE_FAILED和INTERNAL_ERROR的真实拦截器路径；产品及14份已冻结源码／测试／fixture未改动，原137／278项结果继续对应同一内容。探针是四项额外验收观察，不计入Vitest用例数。

## 页面观察

使用最终生产构建、Vite本地预览和Playwright Chromium，视口1440×1100及390×844，受控GET元数据／POST完整响应。每种尺寸先提交PARTIAL，等待时检查禁用及不可终止文案，切设置再返回只接收同一请求；键盘聚焦任务按钮并按Enter，验证精确tab/taskId query且POST仍为1。再次主动提交收到HTTP500 UNCONFIRMED，保留N=null、确认小计、长股票代码／长原因及三组范围，新POST使用不同RequestId。

两种尺寸document宽度分别等于1440／390，无横向溢出；桌面三列计数、窄屏两列、长字符串换行可读，HTML样例仅显示普通文本。四张截图已人工查看：`partial-1440.png`、`unconfirmed-1440.png`、`partial-390.png`、`unconfirmed-390.png`。浏览器版本、构建index摘要、精确请求、计数和尺寸位于`visual-results.json`，运行日志`visual-final.log`。每种尺寸一条由受控HTTP500引发的浏览器网络控制台错误作为预期证据单列，其他console error与pageerror均为空。

预览listen和Chromium启动首次被沙箱限制，使用已授权本地测试权限后运行。临时脚本的精确label选择器未匹配带必填标记的日期输入，改用已知字段容器；随后将预期500网络消息单独记录后完成整轮检查。没有为观察修改产品，本地预览服务已停止。该验证不连接实际后端，不作为T19完整闭环、实际socket／进程中断或真实来源通过。

## 评审与保护

- UI独立规格／质量：PASS，无Critical／Important／Minor发现，见`ui-review.md`；API边界和最终页面证据作为父任务集成门槛。
- API独立规格／质量：PASS，无Critical／Important／Minor发现，见`api-review.md`。最终集成／证据评审复核四码Axios补证后APPROVED，实现规格／质量PASS，唯一Important证据缺口已关闭，无遗留Critical／Important／Minor，见`final-review.md`。
- 保护校验`protect.py`确认58份生产资源、799份开始时文件中的任务外内容、797条原索引以及分支`feat/date-range-download`／HEAD`758f940503ded2d1185040bc8e324815c716a300`保持。正式新增文件按精确路径纳入Git，保留既有文件的原暂存内容；不提交、发布、切换或重置。

本项仅确认AC-PRD-RANGE-07／13／15／17／20／26／27／28的前端适用增量，见[需求追踪](../traceability/tensor-range-requirements.md)。生产Tushare来源／日历、生产fixture批次能力和任务内容页均未在本项升级；T17交付列表／详情／手动重试，T18～T20仍负责受控后端、完整浏览器及真实来源。ISSUE-008九项继续“不依赖，未解决”。
