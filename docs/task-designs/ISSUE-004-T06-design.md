# ISSUE-004-T06：正式前端回归与视觉验收

## Goal

用最新正式 Vue 构建证明 ISSUE-004 的六项关闭条件，保存可复现的客户端行为、49项元数据、响应式与视觉证据，再完成看板和问题收尾。

## Scope

新增独立 Playwright UI 配置、合成 API fixture 与一个专属 spec，运行前端单测/构建/浏览器验收；定点修正验收揭示的前端缺陷并记录复用。后端、API、业务状态机、日期工具、生产元数据、依赖及旧 e2e 文件只读。不启动旧 spec 的 JVM/数据库/真实 Tushare 生命周期，不扩大为发布、性能或安全验收。

## Approach

### 测试入口和数据

新增 `control-plane/playwright.ui.config.js`，使用现有 defineConfig/devices：testDir=`./e2e`、testMatch=`ui-redesign.spec.js`、workers=1、retries=0、reporter=list、outputDir=`node_modules/.cache/tensor-ui-playwright`。use.baseURL=`http://127.0.0.1:4173`，screenshot=only-on-failure，trace=retain-on-failure，video=off。webServer 启动 `npm run preview -- --host 127.0.0.1 --port 4173 --strictPort`，相同url、reuseExistingServer=false；唯一project为chromium，复用Desktop Chrome。CI forbidOnly。使用 Node24.15.0 和现有 Chromium，无依赖安装。

新增 `control-plane/e2e/ui-redesign.fixtures.js`，只承载本spec的静态契约和route工具：

- PARAMETER、EXPECTED_ROWS、filterDescriptor、expectedFilters 的事实来自 `e2e/tushare-metadata.spec.js` 第61–159行。复制这些纯契约到新helper并标注来源，不import旧spec或修改它，不带入JVM函数。PARAMETER保留原字段名、标签、类型、required、allowedValues、relatedParameter。
- 读取 `docs/data-template/manifest.json` 的 interfaces；断言49个唯一api_name与EXPECTED_ROWS完全一致。读取每个filename的fields作为列名及顺序；每项字段数必须等于EXPECTED_ROWS，balancesheet必须152列。manifest的params不能替代运行时参数元数据。
- 为避免模拟错误类型，从各生产 `data-plane/tensor-plugin-tushare/src/main/resources/datasets/tushare_pro/<api>.yaml` 的现有单行columns声明读取name/label/logicalType/nullable/displayOrder和length/precision/scale/longText/allowedValues（有则保留）。使用无新依赖的限定格式提取，遇到无法识别的声明或与样例fields不一致立即报错；不做通用YAML解析器。生产文件只读，此输入核对只服务UI fixture，不能宣称重新验证后端元数据API。
- 构造data source（tushare_pro、可下载、凭证已配置等现有DTO字段）、ApiDescriptor、DatasetSummary和DefinitionResponse。filters使用上述契约表，fixedColumn有ts_code用ts_code，否则首业务字段；直接以真实字段构造，不增加伪业务列。
- 合成records固定含三列来源；DATE用2026-08-07、MONTH用202608，普通证券代码用000001.SZ。daily/weekly分别保留各自列顺序；daily.change='0.0100'、weekly.pct_chg='-0.0378'，另有负数及'-0.0000'；DECIMAL代表值为'12345678901234567890.123456789012345678'，LONG为'9223372036854775807'。stock_company.introduction含长中文和字面量`<strong>`以验证纯文本tooltip；另有null和空字符串。普通数值保持字符串，不用Number生成/转换业务值。
- 普通成功查询返回8条合成记录、totalElements=8、totalPages=1、page=1、pageSize来自请求，字段与DTO一致。分页专属场景按totalElements=201返回对应页的实际条数（50或100等）；只对分页元信息进行整数计算。下载成功为sourceRowCount=12、insertedRows=10、updatedRows=2；EMPTY三计数为0。

提供 `installApi(page, overrides = {})`：拦截`**/api/**`，返回`{ requests, unexpected }`用于断言。默认只处理以下真实路径；overrides以`METHOD /api/v1/...`为key，值为返回`{status, body}`或其Promise的函数，供测试控制单次错误、延迟和分页，不引入额外mock框架。

| 方法 | 路径 | 返回 |
| --- | --- | --- |
| GET | /api/v1/data-sources | DataSourceSummary[] |
| GET | /api/v1/data-sources/tushare_pro/apis | ApiDescriptor[] |
| GET | /api/v1/data-sources/tushare_pro/datasets | DatasetSummary[] |
| GET | /api/v1/data-sources/tushare_pro/datasets/:apiName | DefinitionResponse |
| GET | /api/v1/data-sources/tushare_pro/datasets/:apiName/records | PageResponse |
| POST | /api/v1/downloads | DownloadResponse |

每次请求记录method/path/query/body及X-Request-Id；请求ID必须非空，响应body（有requestId时）与X-Request-Id响应头一致。未声明路径、方法、apiName或非法下载身份加入unexpected并abort，测试结束assert unexpected为空。错误使用现有严格API契约：SOURCE_TIMEOUT为504/true、QUERY_FAILED为500/true、PARAM_INVALID为400/false，body恰含requestId/code/message/retryable/fieldErrors。元数据失败也用同一合法错误DTO。API请求全程route合成，截图不含真实上游数据。

### 浏览器场景

新建 `e2e/ui-redesign.spec.js`，测试真实构建页面，不stub Vue组件，不调用组件内部状态代替操作。共用简单导航/选择/填日期/请求断言函数。通过原可访问标签或稳定ID定位；date输入提交后blur或Enter，检查控件值已生效再提交。每个测试独立浏览器上下文，避免localStorage和业务缓存跨例污染。

1. **49项元数据矩阵**：每个api一个命名用例。下载页选择该接口，断言实际参数数量、名称、标签、required和控件类型与PARAMETER契约一致，ENUM项与MONTH/DATE控件可操作；用有效参数提交，断言精确原字段及一次POST。DATE/范围值归一化YYYYMMDD、MONTH归一化YYYYMM，无参数为{}。切到查看页选相同数据集，断言可见筛选仅为filters声明的证券代码/独立起止，无筛选也可查询；提交后断言所有业务列加三来源列的顺序和数目，且仅发预期GET。此矩阵包含18个仅trade_date DATE接口、三个原生范围、月份、公告日、枚举与无参数接口。
2. **下载状态与原快照**：延迟metadata显示加载，合法可重试metadata错误显示requestId并由“重新加载”恢复；INITIAL指导可见。daily必填空值提交不请求且聚焦错误字段；有效单日期一次POST，延迟时右侧polite状态可见且所有配置禁用；进入设置，释放响应后返回，表单、成功三计数保持且请求数不变。SOURCE_TIMEOUT失败后改表单再进设置返回，错误保持，“使用原参数重试”仍发送失败时原参数。另验证EMPTY无计数和PARAM_INVALID无重试；new_share倒序/缺失不发请求，修正后一次POST含原起止字段。
3. **查询、分页与缓存**：daily可空/单边tradeDateFrom请求保持YYYY-MM-DD，announcement代表income可仅annDateTo；无筛选trade_cal可查。分页从默认50到100、到第2页，修改未提交草稿再分页仍用已提交快照；进入设置修改主题再返回，草稿/日期/结果/页码/条数保持且无新请求。延迟查询在设置中完成后返回显示结果，不重发；QUERY_FAILED经往返设置后“重新查询”沿用原快照。重置保持选择、恢复50与未查询，延迟旧响应不能覆盖重置状态。查询中动态筛选/查询按钮不可用，保留重置和选择使请求失效的既有行为。
4. **主题与零请求**：新开/settings上下文记录全部API请求，取色器input即时应用、HEX提交、非法HEX关联错误且主题不变、保存/刷新、重置完整默认palette，均零API请求。损坏storage回退；分别模拟getItem/setItem抛错，显示“仅本次预览”并可换色/重置。默认palette逐项与已确认值比对。业务页换色通过设置导航进行，不能直接改根变量代替操作。
5. **精确宽表与tooltip**：balancesheet实测152+3列表头顺序、末列可滚到、固定首业务列位置；daily/weekly精确正负号、负零中性、前收盘价、不同pct_chg单位且weekly原值不乘100；stk_holdernumber大整数完整；stock_company长文本tooltip纯文本且没有strong节点；空值和入库时间仍按原规则显示。数值右对齐与等宽数字取实际计算样式。
6. **五视口与四主题**：分别1440×1080、1024×768、768×1024、390×844、360×800，检查下载/查看/设置的documentElement.scrollWidth<=innerWidth，下载桌面双栏/1000px以下单栏，导航680px以下在上方，表格内部scrollWidth>clientWidth且可滚动，分页所有操作可用而不撑开页面。在相同数据状态下按默认、#b52c63、#ffff00、#000000逐个通过设置应用主题，比较换色前后导航、输入、按钮、面板的boundingClientRect（含settings恢复按钮），容许0.5px测量误差，不允许换色挪动布局。
7. **真实颜色与键盘**：每种主题取实际按钮正常/hover/按下态、链接/选中导航、页面/面板/导航/raised/accentBg表面和白字，主操作对比度>=5.5；正文/提示文本>=4.5。实际打开日期弹层、选择下拉、tooltip、分页和宽表，核对根主题继承、日期选中颜色、固定列背景。360px日期弹层和下拉不越出视口；键盘Tab显示跳转工作区链接，Enter使main聚焦，导航切页、选择器ArrowDown/Enter/Escape、日期编辑、按钮及表格滚动区域均可通过键盘使用且焦点可见。减少动画偏好下操作仍有效。

以上可将紧邻行为放进同一用例，但每项验收须有真实断言，不能用截图存在、组件名称、只检查根变量或无条件通过替代。49矩阵使用49个可识别test标题；额外状态、主题、视口测试在名称中写明覆盖。测试timeout可按矩阵操作设置60秒，主题组合120秒，不用无限重试或固定sleep掩盖失败。

### 截图与记录

在最终页面和合成状态稳定后集中保存下列full-page截图（从文档顶部），截图由spec可复现生成：

- `docs/verification/ISSUE-004-ui-redesign/downloads-desktop.png`、`datasets-desktop.png`、`settings-desktop.png`：1440×1080，默认主题，下载成功/查看daily成功/设置默认。
- 同目录 `downloads-mobile.png`、`datasets-mobile.png`、`settings-mobile.png`：390×844，相同状态。
- `datepicker-mobile.png`、`download-error-mobile.png`：360×800，真实日期弹层和长错误/requestId与重试入口。

截图只含合成数据；缓存、trace、失败截图留node_modules缓存，不加入Git。执行者打开每张正式截图确认内容有效，并与已确认HTML参考比较层级、布局、文字、控件、计数、长错误和焦点。一次集中修正，最多再一次针对性截图确认；测试定位或fixture错误按证据定点修正，不修改产品契约来满足错误测试。

`docs/verification/ISSUE-004-ui-redesign.md`记录实际命令/版本/退出码/用例数/截图路径、49项覆盖表、真实颜色与视口结论、缺陷与修正、局限。逐项映射问题关闭条件；记录共享实现及实际调用处：theme.js/useTheme/App、PageHeading三页、WorkbenchPanel下载/查看/设置、MetadataField与useFormValidation两表单、CatalogSelect两包装、AsyncStatePanel所有反馈、DatasetPagination单一结果入口、decimalSign表格。T01–T05看板已有证据作为历史输入，最终本轮结果单独列出；将此前误跟踪的T02 scratch报告证据汇总后移出Git，保留正式验证文档。

## Files

- 新建 `control-plane/playwright.ui.config.js`、`control-plane/e2e/ui-redesign.fixtures.js`、`control-plane/e2e/ui-redesign.spec.js`。
- 新建 `docs/verification/ISSUE-004-ui-redesign.md`及上述8张正式截图。
- 只为真实验收失败定点修改此前前端呈现文件及相关行为测试，不扩展业务/API/后端范围。
- 最后更新 `docs/task-handoffs/ISSUE-004/ISSUE-004-task-board.md`、实施计划复选项、`docs/issues/README.md`、`docs/issues/problems/ISSUE-004-ui-visual-redesign.md`；全部关闭条件通过后才将ISSUE标为已解决。

## Tests

在control-plane使用满足engines的Node24，先确认只发现新spec，随后用最新build运行：

```sh
npx playwright test --config=playwright.ui.config.js --list
npm test
npm run build
npx playwright test --config=playwright.ui.config.js --project=chromium --workers=1 --retries=0
git diff --check
```

全部退出0、无unexpected API，浏览器所有声明场景实际执行，单测不能作为真实视口/弹层的替代证据。若真实缺陷需修正，先记录失败，再跑覆盖该缺陷的检查，最后在最终代码上完成需要更新的构建和UI验收；无代码变更不反复重跑已通过套件。静态复用/范围核对使用明确diff和保护源散列；既有chunk提示单独记录，不追加打包优化。完成独立规格/代码及视觉审查后，明确路径加入Git并提交。

## Acceptance

49个接口的实际控件与表格、一次原参数下载、查询筛选/分页/重试、切页缓存与在途响应、设置零API和持久化降级、精确155列、五视口、四主题、键盘与弹层均有通过证据；共享组件已实际复用，8张截图可追踪；正式验证文档逐项覆盖关闭条件。T06完成后没有更大Order任务，不创建新后继或交接。

## Risks

Chromium启动/preview端口在本机受沙箱限制时可用限定本地测试的权限升级；本轮已成功启动同一Chromium捕获参考HTML。所有API响应均为合成数据，本轮结论限正式前端和客户端契约，不代表真实上游、后端性能、安全或发布门禁。不得把未知/失败场景记为通过。无未解决的需求决定。
