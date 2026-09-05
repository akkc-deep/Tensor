# M14-T03 查询、分页、宽表、竞态和无障碍验收证据

- 任务：[M14-T03](../task-handoffs/tensor-v1-task-board.md)。
- 设计：[M14-T03-design.md](../task-designs/M14-T03-design.md)。
- 测试：[dataset-query.spec.js](../../control-plane/e2e/dataset-query.spec.js)。

## 环境和输入

实际运行环境为 macOS arm64、Java 21.0.11、MySQL server 8.4.6 / client 8.4.11、Node 24.15.0、npm 11.12.1、Playwright 1.62.1 和标准 Playwright Chromium。既有前端回归在本任务运行前实际为 20 files / 120 tests passed，5.19s。

使用原样验收 JAR，SHA-256 为 `a69874afa6ce783d4ef4e16a678ddb0ff457f2948b68f509a8e4a2c00440bcac`。每轮由受限 runner 创建名称符合 `tensor_m14_t03_<随机十六进制>` 的全新空 MySQL schema，字符集/排序规则为 `utf8mb4/utf8mb4_0900_as_cs`；应用账号只获该 schema 的 CREATE、SELECT、INSERT、UPDATE。测试使用与应用账号相同的当前用户所有、0600、非符号链接 defaults 文件执行三次合并只读 batch；凭证文件没有生成、改写或传入 JVM。

测试生成一次性假 Tushare Token，仅注入自有 JVM，并将上游指向测试自有的回环 Node server。页面下载是唯一业务写入入口；测试没有直接调用业务 API、用 SQL 种数、伪造元数据或分页响应，也没有外部网络调用。

## 实施期实际运行

Run1 使用全新空 schema，JAR 启动、V1～V6、50 张业务表和五个目标表初始 0 行均通过。前四项实际通过：第 1 项 6.4s、第 2 项 5.2s、第 3 项 5.2s、第 4 项 2.4s；6 次页面下载和 6 次 stub 调用完成，daily 126、company 1、index 1、balance 1、disclosure 123 行已经由真实页面建立。

第 5 项首次在只填写单边开始日期后失败。页面执行 `fill` 再 Tab 时，焦点进入结束日期控件并打开日期浮层；浮层 header 持续拦截“查询”按钮的真实鼠标点击，因此没有发出预期 records GET，最终触发用例 180s 超时。此轮为 4 passed / 1 failed / 6 did not run，Playwright 退出 1；最终 disclosure 分布仍为 123/0、下载总数仍为 6，afterAll 对最终 1/122 和 7 次下载的失败属于首要用例失败后的预期级联诊断。该轮自有 JVM 和 stub 均完成结构化清理，8080 未残留。

根因修订保留 Tab 提交日期，随后通过公开键盘 Escape 关闭日期浮层；同一修订应用到第 9、10 项相同的单边日期准备。没有使用 force click、DOM 注入、固定 sleep、skip、retry 或删减产品断言。

Run2 使用另一全新空 schema，在第 1 项执行到 balance 页面下载后以 1 failed / 10 did not run 退出。请求中的 `ts_code`、`ann_date` 两个批准参数和值均正确，但 JVM 发出的 JSON 对象键顺序与测试构造对象不同；替身错误地用 `JSON.stringify` 比较对象顺序，记录 `balance:params`。该轮已完成的五次页面下载均由产品返回真实 HTTP 200/SUCCESS 并写入 daily126/company1/index1/balance1；disclosure 尚未执行。自有 JVM/stub 清理成功。

修订改为参数精确键集合和逐值比较，继续对 fields 数组保持顺序严格；并补上每模式只允许一次调用。替身现在只有全部 method/path/body-size/JSON/key/api/token/params/fields/count 检查通过才返回成功，否则 fail closed 为 HTTP 500，不存在校验失败后的成功 fallback。Run2 的 afterAll 曾由 matcher 输出五条私有完成事件原行；修订后事件总数使用固定安全检查名，失败不回显日志内容。

Run3 使用第三个全新空 schema，前五项全部通过（6.3s / 5.5s / 5.2s / 2.4s / 2.5s），证明单边日期 Escape 修订和 reset 的可见选中项断言有效。第 6 项的第二页面完成第七次真实下载，返回 123/0/123；最终只读数据库已为 V1～V6、50 张业务表、daily126/company1/index1/balance1/disclosure123，公告日分布精确为 1/122。7 次下载、7 次 stub 和当时 17 个 records 响应均已发生。

该轮随后只因页码定位器把可见数字“3”误设为 button role 而超时，实际分页数字没有 button role。设计只规定分页区域内的精确可见数字；定位修订为 `数据集分页` navigation 内精确文本“3”，继续严格要求页面真实发出 page=3/pageSize=50，并要求服务端响应和 UI 归一为 page1/pages1/total1。自有 JVM/stub 再次均清理成功。

Run4 使用第四个全新空 schema，前六项全部通过（6.4s / 5.5s / 5.2s / 2.5s / 2.6s / 3.5s），证明真实 page3 意图及服务端归一化修订有效。第 7 项已通过 balancesheet 的 152 业务列加 3 来源列、完整行值、真实水平溢出、固定 ts_code 及右端来源列；人工查看该轮左右截图确认高精度值在左端真实省略，滚到右端后首列仍可见且页面无凭证。

随后测试在已滚到最右端时直接对第 8 个业务列的高精度 cell 执行 hover，没有取得 tooltip。修订先把鼠标移出表格，显式将目标滚回可见区域并等待两帧，核对精确公开文本目标的 viewport/Range/scroll 几何确实溢出，再对该可见文本执行真实 pointer hover；tooltip 全文与纯文本断言保持不变。成功或失败前保存目标位置截图和安全几何投影。company 长文采用同一稳定操作。Run4 自有 JVM/stub 清理成功。

Run5 使用第五个全新空 schema，前六项继续通过；第 7 项的高精度目标 viewport/Range 溢出证据和完整 tooltip 已通过。company 长文真实 tooltip 也已出现，但离开的 `stock_company` 选择下拉 popper 同时保留另一个 `role=tooltip`，未限定 locator 因匹配两个元素而失败。修订只按期望公开全文筛选目标 tooltip，继续精确核对 `toHaveText`、`textContent` 及无 strong/em/script/style；没有关闭或忽略任何真实产品断言。该轮自有 JVM/stub 清理成功。

Run6 使用第六个全新空 schema，前六项继续通过；第 7 项的 balancesheet 高精度和 company 长文 tooltip 均通过。index 固定列检查的普通列位移为 0，原因是切换数据集后滚动容器可保留在右端，而测试在没有建立左端基线时直接对已经可见的末列调用 `scrollIntoViewIfNeeded`。修订先在真实 cell 上用横向 wheel 回到左端，以条件轮询确认 scrollLeft≤2 并核对 scrollWidth>clientWidth；再用真实末列位置和横向 wheel 到右端，轮询实际 scrollLeft 增长。最终同时比较首列/普通列位移、角色元素计算样式 sticky/非 sticky，并保存 index 左右截图与实际 scroll 投影。该轮自有 JVM/stub 清理成功。

Run7 使用第七个全新空 schema，前 9 项全部通过；第 7 项的 index 左右滚动、固定/普通列位移和 sticky/非 sticky 证明通过，第 8、9 项两种真实延迟响应释放后的状态隔离也通过。第 10 项唯一失败是测试把“无虚构响应 ID”误解为网络 alert 不得显示请求 ID；实际 alert 显示真实出站 UUID。根据 M10-T03 公开合同，ClientError 优先保留请求 config 的 `X-Request-Id`，只有在请求拦截器前失败才为 null。

修订捕获唯一 `requestfailed` 的真实 Request，精确核对其日期/page2/pageSize50 参数与非空出站 `x-request-id`，并要求 NETWORK alert 显示同一值。该请求明确记录为 response=null、completionEvent=false，且不加入服务端完成事件预期；一次 abort、固定 NETWORK 文案、旧表/分页隐藏和原条件重试恢复断言不变。Run7 自有 JVM/stub 清理成功。

Run8 使用第八个全新空 schema，前 10 项全部通过；第 10 项证明被中断请求的出站 ID、alert ID、固定 NETWORK 文案、无响应/无服务端完成事件及 page2/50 原条件重试均一致。第 11 项在来源 combobox 首次 ArrowDown 展开后立即检查 `aria-activedescendant`，当时该属性合法地尚为空，因此 272ms 内由固定安全检查失败。

键盘 helper 修订为最多 80 次方向键导航；每步读取 combobox 的 ARIA 当前活动 option，核对其真实 role 与公开文本，只在精确目标成为活动项时 Enter。来源、键入 `daily` 搜索的数据集和 page-size 20 复用该逻辑，分别覆盖 ArrowDown、ArrowUp、Enter、Escape；不猜 DOM ID 对应的序号，不使用 click/fill/focus、程序化事件或固定 sleep。Run8 自有 JVM/stub 清理成功。

Run9 使用第九个全新空 schema，前 10 项全部通过；第 11 项已经完成来源/数据集键盘选择、首错聚焦、合法查询、切换 20/page 和下一页，并得到正确的 2/7 页结果。最后一个附加断言仍要求 Enter 后旧“下一页”按钮在结果重建后保留焦点；页面加载期间会重建分页子树，旧节点因此不再是活动元素。这不是 PRD/TRD 规定的自动焦点恢复行为。

最终修订先证明 Enter 前旧“下一页”按钮确有原生焦点；响应完成后只用有界真实 Tab 导航到新渲染的“下一页”按钮，并保存可见焦点截图。没有使用程序化 focus、鼠标补位或放松键盘闭环。Run9 的 47 个业务请求、46 个服务完成事件、唯一允许的浏览器中断，以及自有 JVM/stub 清理均符合预期。

Run1 同时证明 beforeAll 的纯合成环境/安全探针实际全部执行成功：

- 合法六行 CRLF defaults 被接受；包含空白的合成密码被固定检查名拒绝，错误不回显该值。
- JDBC 中嵌入合成用户名/密码被固定检查名拒绝。
- 公开响应额外字段和页面可见假 Token 分别被拒绝，诊断不回显命中值。
- 主断言异常与清理异常按顺序共同保留在 `AggregateError.errors`。
- 构造的 JVM 环境不含 `MYSQL_PWD` 或其他 `MYSQL_*`。

## 截图审计修订

Run10 在 2026-09-05T13:26:31.224Z～13:27:14.606Z 首次全绿：11 passed，43.8s。随后顺序执行的 Run11 在 13:27:50.335Z～13:28:33.400Z 再次全绿：11 passed，43.5s；两轮没有重叠，使用各自的新空 schema 和相同冻结 spec。控制器审阅 Run11 的 14 张 PNG 后确认功能断言均成功，但 `company-empty-null-zero-tooltip.png` 的视口只展示完整 LONG_TEXT tooltip、空 business_scope 边缘和若干 null 单元格，`employees=0` 与 `main_business=--` 在视口外，尚不足以作为三者同帧的视觉证据。

Run12 增加 `company-empty-null-zero.png` 并把三个已核对单元格滚入视口；新对照截图成功生成，但该步骤放在既有 LONG_TEXT hover 前，改变了原本稳定的横向滚动/鼠标前置状态，随后 tooltip 未出现。结果为 6 passed / 1 failed / 4 did not run，41.3s。Run13 把对照截图移到既有 tooltip 证据之后，tooltip 恢复通过；对照步骤滚到 business_scope 时只完整显示了前侧单元格，尾侧 main_business 的 viewport ratio 实际为 0。结果为 6 passed / 1 failed / 4 did not run，39.9s。两轮新截图测试问题均在第 7 项发生，7 次页面下载、最终数据库投影和自有 JVM/stub 清理仍成功；没有显示产品缺陷。

最终修订保留 Run10/11 已证明稳定的 tooltip 顺序，只在其后将尾侧 main_business 滚入视口，并继续要求 business_scope、employees、main_business 三个相邻单元格的 viewport ratio 全为 1，再保存对照截图。

## 最终完整矩阵

Run14 曾在截图修订后以 11 passed / 43.3s 全绿，控制器也完成 15 张 PNG 和数据库的独立审阅。随后的代码审查指出，若页面没有按正确响应重绘，部分 20/100 分页、单边日期、网络恢复和键盘分页断言仍可能通过；两个竞态门闩也缺少独立的 10 秒截获、30 秒最大持有及 finally 实际请求结算证明。修订为每个要求状态同时核对独立期望行、完整表格/行数和摘要；门闩在触发前监听 `requestfinished`/`requestfailed`，finally 按释放、handler 完成、真实请求结算、unroute 顺序清理，并保留主异常和清理异常。更新页面的下载与关闭也改由同一双异常聚合 helper 执行。

最终冻结的 `dataset-query.spec.js` SHA-256 为 `6d491eb623a0f77030195c2676959937b7e410ee698e5fb0322022fdc3281b42`。Run15 使用新的空 schema，于 2026-09-05T14:52:34.288Z～14:53:24.559Z 以单 worker 完成：11 passed，50.7s，退出 0。安全日志凭证扫描为 `no matches`。安全 JSON 位于本机受限临时目录 `tensor-m14-t03-55r9Fl/evidence.json`，SHA-256 为 `d5d2e46ca5788513590b30759bad730634caf883ada4728079859897f03bd3ea`。

| # | 用例 | 耗时 |
| --- | --- | --- |
| 1 | `seedsQueryDatasetsThroughDownloadPages` | 6.5s |
| 2 | `showsOnlyDeclaredFiltersWithoutAutoQuery` | 5.6s |
| 3 | `paginatesAllRowsWithServerTotals` | 5.8s |
| 4 | `combinesTradeDateFiltersAndKeepsEmptyPaging` | 3.8s |
| 5 | `resetsSelectionStateAndRejectsInvalidRanges` | 4.1s |
| 6 | `normalizesLastPageAfterAnnDateCorrection` | 4.7s |
| 7 | `rendersWideColumnsAndExactTextValues` | 3.4s |
| 8 | `ignoresReleasedResponseFromPreviousDataset` | 2.3s |
| 9 | `keepsResetStateAfterPendingQueryCompletes` | 3.2s |
| 10 | `recoversFromQueryNetworkFailureWithoutOldRows` | 2.4s |
| 11 | `queriesAndPaginatesUsingKeyboard` | 2.5s |

Run15 记录 47 个业务请求：7 个下载 POST 和 40 个 records GET。下载均得到真实响应；records 中 39 个得到真实响应，另 1 个是第 10 项预登记且由浏览器实际中断的网络请求。46 个收到服务端响应的业务请求均关联唯一 `tensor.operation.completed` 完成事件，中断请求明确为 `response=null`、`completionEvent=false`。最终计数为 7 次页面下载、7 次替身调用、39 次 records 响应、1 次允许的中断；没有额外写请求、外部网络请求、未预期 HTTP 错误或页面异常。beforeAll 同时实际执行新增 deadline 合成探针：已完成 promise 在期限内返回，永不完成 promise 被固定安全错误名拒绝。

分页实际覆盖 daily 的 50 条每页 1/2/3 页（50/50/26）、20 条每页 1/2 页及 100 条每页 1/2 页（100/26），总数始终为 126。disclosure_date 在第二次页面下载把同一 123 个业务键中的 122 行公告日改为 2026-08-08 后，旧页面发出的 page=3/pageSize=50 请求由服务端归一为 page=1/totalPages=1/totalElements=1；后续单边和 AND 条件分别得到 122、1、0、1 条，页面摘要与响应一致。空结果仍保留 page=1/totalPages=0 与可用页大小控件。

两条竞态均在旧请求真正完成以后验证页面状态。实际释放顺序为：

1. `dataset-switch:daily-held`
2. `dataset-switch:index-visible`
3. `dataset-switch:daily-continued`
4. `dataset-switch:stale-ignored`
5. `reset:page2-held`
6. `reset:state-visible`
7. `reset:page2-continued`
8. `reset:stale-ignored`

第 10 项捕获实际失败 Request 的日期、page=2、pageSize=50 与出站 `X-Request-Id`，NETWORK alert 显示同一 ID；移除窄范围 route 后，“重新查询”用原条件取得真实 200 和 2/3 页的 50 行。第 11 项仅通过键盘完成导航、选择、非法输入首错聚焦、合法查询、切换 20/page 和下一页；分页按钮在 Enter 前具有原生焦点，结果重建后由有界 Tab 导航重新到达新按钮，证据投影为 `nextBeforeEnter=true`、`nextAfterRenderReachedByTab=true`。

## 实际命令与退出码

| 工作目录 | 命令 | 结果 |
| --- | --- | --- |
| `control-plane` | `node --check e2e/dataset-query.spec.js` | exit 0 |
| `control-plane` | `npx playwright test e2e/dataset-query.spec.js --list` | exit 0；恰 11 个 Chromium 用例 |
| `control-plane` | `npx playwright test e2e/dataset-query.spec.js` | exit 0；11 passed，50.7s |
| `control-plane` | `npm run test:unit -- --run` | exit 0；20 files / 120 tests passed，5.05s |

## 宽表与截图证据

balancesheet 实际渲染 152 个业务列和 3 个来源列。高精度值的单元格 `clientWidth=138`、`scrollWidth=310`、文本宽度 `286.015625`，计算样式为 `overflow:hidden`、`text-overflow:ellipsis`，真实 tooltip 显示完整纯文本。company 长文本单元格 `clientWidth=238`、`scrollWidth=4578`、文本宽度 `4554.34375`，同样得到完整纯文本 tooltip；空字符串、null 和零分别显示为空、`--` 和 `0`。index 表格从 `scrollLeft=0` 实际移动到 `130`，容器 `clientWidth=1310`、`scrollWidth=1440`；首列保持固定，普通列发生位移。

Run15 保存以下 15 张 PNG。路径均位于 Git 忽略的 `control-plane/node_modules/.cache/tensor-playwright/` 下；表中的路径为该目录内实际相对路径。测试生成 JSON 时 15 项均如实保留 `manuallyReviewed:false`，因为自动测试不自证人工审阅；控制器的后续逐张查看是独立证据。

| 截图 | 实际相对路径 | SHA-256 |
| --- | --- | --- |
| `daily-page-2-of-3.png` | `dataset-query-dataset-quer-7a690-atesAllRowsWithServerTotals-chromium/daily-page-2-of-3.png` | `1c06ea4daf6e2759f5f95b800a6bf44110e042d849ecda88c8cc925c21c9170f` |
| `daily-last-page-50.png` | `dataset-query-dataset-quer-7a690-atesAllRowsWithServerTotals-chromium/daily-last-page-50.png` | `521b3526f3f31d612f34dc31192c4b33c1573167ec7b5f8a0dd2f631fd539c61` |
| `disclosure-normalized-page.png` | `dataset-query-dataset-quer-f068e-tPageAfterAnnDateCorrection-chromium/disclosure-normalized-page.png` | `51bd406d82580e036e9074cd160a809c37b047004e40bff07eab0d0c7f4a5917` |
| `balancesheet-left.png` | `dataset-query-dataset-quer-71f5c-deColumnsAndExactTextValues-chromium/balancesheet-left.png` | `5ba9545ef527960147a657d7984a990f3ca60875427f594ed68a55db3ff87dac` |
| `balancesheet-right.png` | `dataset-query-dataset-quer-71f5c-deColumnsAndExactTextValues-chromium/balancesheet-right.png` | `5d82d9eaab4245c5543895e41352057697278fa43da56b06714b5a4cdd2c9a32` |
| `balancesheet-precision-target.png` | `dataset-query-dataset-quer-71f5c-deColumnsAndExactTextValues-chromium/balancesheet-precision-target.png` | `d9cbe5e409c6e96cba76de968b6aed28f2d21a105f667c71b21a01a01b796dff` |
| `balancesheet-precision-tooltip.png` | `dataset-query-dataset-quer-71f5c-deColumnsAndExactTextValues-chromium/balancesheet-precision-tooltip.png` | `836acc16bc84a91d6b4dde1db72c204d859c6edc70515d97844fca947cbeda2c` |
| `company-long-text-target.png` | `dataset-query-dataset-quer-71f5c-deColumnsAndExactTextValues-chromium/company-long-text-target.png` | `b18472aa48690be05a0443aff215861148f5535bc0d0efd9839dfffcda51c9a9` |
| `company-empty-null-zero-tooltip.png` | `dataset-query-dataset-quer-71f5c-deColumnsAndExactTextValues-chromium/company-empty-null-zero-tooltip.png` | `854d3042f3f9138a206659bf84ca0c4e62314f1b76be65b821b3e6781f87861c` |
| `company-empty-null-zero.png` | `dataset-query-dataset-quer-71f5c-deColumnsAndExactTextValues-chromium/company-empty-null-zero.png` | `dd54a5eb81194e326ec2bee51af74476e528dd6d8bf70db9815bb78fec326337` |
| `index-left.png` | `dataset-query-dataset-quer-71f5c-deColumnsAndExactTextValues-chromium/index-left.png` | `35671538c9b53b47e521d14ef5bf27e0f3c40664eb0470e247bd0d0167fda6ac` |
| `index-right.png` | `dataset-query-dataset-quer-71f5c-deColumnsAndExactTextValues-chromium/index-right.png` | `d03e3591099ec79ed74622efd26b8be25464a515b67b04c1a4e0893522939a66` |
| `stale-daily-after-index.png` | `dataset-query-dataset-quer-d29cb-ResponseFromPreviousDataset-chromium/stale-daily-after-index.png` | `a5eb9a51620038822cfb24798e72472a32445106a8349df13c0f4390494b00ee` |
| `keyboard-invalid-focus.png` | `dataset-query-dataset-quer-901cb-esAndPaginatesUsingKeyboard-chromium/keyboard-invalid-focus.png` | `45a76ce6b1b74f1ea55fef9fa0cf55b9308f367f5ce3f540390d3ced039f655d` |
| `keyboard-page-2-focus.png` | `dataset-query-dataset-quer-901cb-esAndPaginatesUsingKeyboard-chromium/keyboard-page-2-focus.png` | `62dd2945d04dd327c020af35ef9ebcd32f6103b061766492bb11317bf2c0d495` |

控制器随后独立确认 15 个路径都存在、文件 SHA-256 与 JSON 逐项一致，并实际查看全部图片。daily 截图可见 2/3 页 50 行和 3/3 页 26 行摘要；disclosure 截图可见归一后的 1/1 页及唯一 `900001.SZ`。balancesheet 左右端可见固定代码和末端来源列，高精度目标确实省略而 tooltip 显示完整值；company 的 LONG_TEXT tooltip 显示完整文本，新增对照帧同时清楚显示 business_scope 空白、employees `0`、main_business `--`。index 左右图显示真实横向移动与固定首列；过期响应图仍只显示 index；两张键盘图分别显示文字错误/首错焦点和“下一页”蓝色可见焦点。全部图片未见凭证。

## 最终只读投影与清理

Run15 启动时确认 schema 为空；Flyway V1～V6 全部成功并建立 50 张业务表，五张目标表初始均为 0 行。完成后测试内只读投影为 daily 126、stock_company 1、index_classify 1、balancesheet 1、disclosure_date 123；disclosure_date 的 ann_date 分布精确为 2026-08-07 一行、2026-08-08 一百二十二行。JAR SHA-256 从准备到最终运行保持为批准值；自有 JVM 和上游替身的结构化清理标志均为 true。

控制器又通过 runner 对 Run15 schema 执行独立只读复核，退出 0：成功迁移数 6、业务表 50、daily 126、stock_company 1、index_classify 1、balancesheet 1、disclosure_date 123，公告日分布为 2026-08-07 一行和 2026-08-08 一百二十二行，与测试内投影完全一致。

既有前端回归在 Run11 后的 2026-09-05 21:33:01 +08:00 执行，20 个测试文件的 120 项测试全部通过，耗时 5.05s。此后 E2E 变动包括截图顺序、渲染状态断言和竞态门闩清理加强；最终均由 Run15 全矩阵覆盖。所有合成安全探针、请求/响应、页面、事件白名单和证据 JSON 扫描均通过，没有把应用/工具密码、假 Token、JDBC 配置、原始 SQL 或完整上游包络写入公共证据。Run1～Run9 与 Run12～Run13 暴露的均是测试交互、定位器、证据构图或合同解释问题；逐项修订并在新空 schema 全量复跑后，Run15 未发现产品缺陷。
