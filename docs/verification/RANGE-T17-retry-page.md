# RANGE-T17 失败任务页面与手动重试验证

2026-09-09，对应[区间下载看板](../task-handoffs/tensor-range/tensor-range-task-board.md)的RANGE-T17，依据[专属设计](../task-designs/RANGE-T17-design.md)和[入口交接](../task-handoffs/tensor-range/RANGE-T17-handoff.md)。本报告记录本次新增任务页面的实际证据；完成状态以看板及最终评审为准。

## 行为证据

- 下载页提供“发起下载／失败任务”两个保留实例的页签。T16任务入口、列表选择及结果入口只写精确tab/taskId query，唯一route watcher负责详情GET；首次进入列表与详情各一次。非法单值、数组、非下载路由不会发送非法详情请求；切页保留输入、结果及同一在途Promise，彻底卸载重挂只GET现存记录。
- 列表的插件／接口筛选独立，不依赖当前元数据；默认20，可切50／100，应用条件／改变页大小回1，越界接受服务端末页。列表和详情各自有generation与读取错误，迟到响应不覆盖新选择，GET错误不伪装为空。显式筛选提交时首个错误标识获得焦点，两个标识输入关闭拼写检查。
- 严格校验Page6／Summary11／Detail17／Item7及OriginalDateRange2字段、原始类型、真实公历／UTC毫秒、计数、排序、分页、完整selector唯一、公共条件安全、blocker与执行权限。坏数据安全降为INVALID_RESPONSE，不回显响应正文或凭证哨兵；实际Axios校验响应身份，nullable taskId不补原任务ID。
- 列表与详情分开展示原始20260901～20260910和当前3／7日；明确PARTIAL后由新GET展示仅剩7，原始区间不变。同日两股各一项，原因和更新时间独立；REQUEST结合公共条件，MONTH／RANGE／NONE保留完整语义。原条件“不适用”、旧区间“未记录”、畸形／未知模式“原始区间未确认”。不把GET快照变成历史计数。
- “重试一次”直接发送原UUID的一个POST，Axios data为undefined，query为undefined；不传原始日期、公共参数或首次表单。首次与重试互锁包含事件／flow内复检和首次异步校验后的竞争保护；等待时仍可切路由。A执行期间路由改为B只排队最新目标，A结算后再读取B并清除A的可见本轮结果。
- 五种正常结果及UNCONFIRMED复用DownloadResult，七计数、三组范围和实际nullable身份原样保留。四停止码经过真实Axios及页面，保留此前已确认小计；四类ClientError无虚构计数、无自动重发，只有显式成功详情GET才能恢复主动重试权限。409使旧许可失效，不轮询或排队；GET／POST404各更新一次实际列表，不推断无响应执行成功。明确最后解决的null／0响应显示确认说明，固定未知边界仍保留。
- 详情执行说明按等待／占用、待刷新、blocker、区间不可终止顺序展示，避免重复。404状态不提供无法执行的“刷新详情”指令；缺失说明与此前未知结果独立。首次下载行为只增双向本地锁，useDownloadFlow、T15表单、路由、AppLayout及DatasetPagination实现未改。

## 命令与结果

工作目录`control-plane/`，Node24.15.0、Vitest4.1.11、Vite8.2.2。本地原始日志、起点摘要／副本和评审材料在忽略目录`.superpowers/sdd/RANGE-T17-design/`；以下运行不相加为唯一用例数。

| 命令 | 实际结果 | 本地日志 |
|---|---|---|
| 启动基线 `npm test` | exit0，27文件278项 | `baseline-test.log` |
| 设计指定11个spec的 `npm test -- …` | exit0，11文件215项，零失败／跳过 | `focused-final.log` |
| `npm test` | exit0，31文件356项，零失败／跳过 | `full-final.log` |
| `npm run build` | exit0，1708模块；既有大chunk提示保留 | `build-final.log` |
| 根目录 `PYTHONDONTWRITEBYTECODE=1 python3 docs/contracts/verify_range_contract.py` | exit0，8组合同＋4项变异拒绝 | `contract-final.log` |
| 四个修改Vue的Impeccable检测 | exit0，发现列表为空 | `impeccable.json` |
| `git diff --check`、`git diff --cached --check`及保护脚本 | 均exit0，原索引／HEAD及任务外保护通过 | `protect-final.log` |

指定命令为：

```sh
npm test -- src/api/retryTasks.spec.js src/api/downloadResult.spec.js src/api/api.spec.js src/api/errors.spec.js src/composables/useRetryTaskFlow.spec.js src/composables/useDownloadFlow.spec.js src/components/download/RetryTaskList.spec.js src/components/download/RetryTaskDetail.spec.js src/components/download/DownloadResult.spec.js src/views/DownloadView.spec.js src/layouts/AppLayout.spec.js
```

完整回归包含T15真实49项表单／31天／日期归一化，首次下载、查询精确数值、公共组件、主题和KeepAlive。本项不运行MySQL或真实来源，不将前端结果升级为后端验收。

## 开发与评审修复记录

初始`ui-red.log`为三项真实任务入口／零body及剩余范围／未知后404行为失败，50项过滤未运行。新增组件与API／flow的导入缺失分别记录，最小导出后的行为RED分别为17失败／20通过、17失败／11通过；导入错误不作为行为证据。

集成揭示无快照ApiError的optional chaining误判、数组标识被正则转换及大小写身份边界，已用真实Axios回归修正。异步表单校验竞争测试起初spy了错误的VTU公共代理，改为组件exposed边界；绿色后临时移除校验后的combined lock产生一个真实失败，再完整恢复，见`validation-race-mutation-red.log`。旧EMPTY断言误匹配拼接页签文本，改为仅检查结果组件。API stub阶段的集成失败不作为最终结果。

组件独立评审发现重复执行说明及标识首错聚焦／spellcheck缺口，先3失败／18通过，再修复为三个组件spec共43项通过；初次字符串ref在v-for内形成数组，改为键控function ref，保留原测试。API独立评审发现404后提示不存在的刷新入口，保持内部权限失效，仅抑制NOT_FOUND通用文案。View评审发现原条件fixture与真实描述符不符，以及重试后的模拟列表与详情不一致，按当前合同修正fixture和两列断言；这些是本轮证据修正，不改后端或生产来源。

最终集成评审发现真实Element Plus在加载期page-count=0时自动归一到第1页，引发额外GET并抢占generation。真实组件＋Axios先3失败／85通过，独立旧构建Chromium探针也收到`[1,2,2,1]`，见`pagination-red.log`和`pagination-browser-red.log`。局部两绑定修复：无result时临时page-count使用受控page，分页控件禁用，仍展示真实加载／失败状态；未改变flow。新增真实第2页刷新、第2→3页、拒绝后第3页显式重载、执行后第2页自动刷新，定向101项通过。旧证据存为`pre-pagination-*`，上述最终215／356项和构建均在源代码修复后重跑。

## 页面观察

最终构建的Vite本地preview由Playwright Chromium151.0.7922.34验证，视口1440×1100和390×844。每个尺寸35次受控HTTP请求，其中6次主动execute POST全部为0字节、无query，每次HTTP有不同RequestId。两尺寸均完成：首次结果键盘任务入口→原1～10／剩3、7→键盘重试→切页签／设置／返回保持唯一POST→列表及详情仅剩7→最后解决且真实空列表；另覆盖同日两股分开、长原因安全转义、网络未知后显式GET404、409后显式刷新再执行，以及500 COMMIT_UNCONFIRMED的null身份和确认小计。

`browser-check.mjs`捕获请求与断言，`browser-results.json`保存浏览器版本、构建摘要、请求、各状态尺寸及截图，`browser-final.log`为完整运行输出。构建index SHA-256：`21021bdd12fd5f7a5567ae955d85685ca9003386f2bba996275535b6046f9f3e`。16张截图分原始／当前、仅剩7、已解决、两股长原因、unknown404、busy、snapshot-null及第3页八种状态。人工查看了桌面原始／剩7／unknown404及窄屏剩7／两股长原因／unknown404／snapshot-null的代表截图；桌面两列、680px以下单列，长ID／原因换行，完整范围可读。

另以41条、三页受控列表检查：到第2页后hold刷新GET，只发送page2；再hold下一页GET，只发送page3，解锁后仍在第3页且1条记录。两尺寸最后请求页序均为1／2／2／3，没有自动page1；两张`pagination-three-*`截图已人工核对。实际返回index摘要也与磁盘构建摘要相等。

两尺寸所有截图时document宽度分别等于1440／390，无横向溢出，非预期console error和pageerror为空。每尺寸4条受控网络失败／404／409／500的浏览器资源错误单列为预期。首次截图处于已有页签动画中，脚本增加等待动画结算后做唯一确认轮，产品／构建未改。preview初次listen被沙箱限制，使用本地验收权限；4177已有服务，改用4187且只停止本轮服务，现已停止。所有API由脚本拦截，未连接真实后端／Tushare，不替代T18真实进程／socket或T19完整浏览器闭环。

## 最终评审与保护

API／flow独立复审Spec PASS、Quality APPROVED，唯一404 Important关闭，见`api-rereview.md`。组件重复说明和显式首错聚焦I1／M1在`component-rereview.md`确认关闭；其M2与View I1／I2／M1同批修正后，由`view-rereview.md`逐项确认ADDRESSED、Spec／Quality PASS，无遗留。该波真实RED为6失败／99通过，修正后的5文件127项通过；最后指定215项及全量356项对应全部修正。最终集成评审唯一分页I1已在`final-rereview.md`确认ADDRESSED，Spec PASS、Quality APPROVED、Evidence PASS、最终APPROVED；无遗留Critical／Important／Minor。实际旧构建Chromium失败日志和最终215／356项原始输出补足代理定向运行摘要的证据边界。保护检查以806份开始文件、804条原索引、58份生产资源，以及分支`feat/date-range-download`／HEAD`758f940503ded2d1185040bc8e324815c716a300`为基准；保留任务外改动，本轮10个新增正式文件已精确纳入Git，保留804条原索引内容，不提交／发布。

本项仅确认AC-PRD-RANGE-20／21／22／23／25／27的前端适用增量，见[需求追踪](../traceability/tensor-range-requirements.md)。生产Tushare日历／完整来源仍保守关闭，生产fixture批次能力、真实进程／socket中断、完整后端浏览器和真实来源分别留T18～T20；ISSUE-008九项继续“不依赖，未解决”。
