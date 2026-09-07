# ISSUE-004 正式前端验收

验收日期：2026-09-07。ISSUE-004已解决，六项任务均为COMPLETED。170项单测、构建、60项浏览器用例、独立规格/质量复审、视觉复审及最终项目审查均通过。

权威入口：[任务看板](../task-handoffs/ISSUE-004/ISSUE-004-task-board.md) · [总体设计](../task-designs/ISSUE-004-design.md) · [T06设计](../task-designs/ISSUE-004-T06-design.md) · [关闭条件](../issues/problems/ISSUE-004-ui-visual-redesign.md)。

## 本轮命令与结果

命令在 `control-plane` 执行。实际安装版本：Node24.15.0、Vue3.5.42、Vue Router4.6.4、Element Plus2.14.5、Axios1.20.0、Vite8.2.2、Vitest4.1.11、Playwright1.62.1、Chrome for Testing151.0.7922.34。T06实现提交为`daa7b64`，补充验收断言为`83530b0`。

| 命令 | 结果 |
| --- | --- |
| `npx playwright test --config=playwright.ui.config.js --list` | 退出0；仅发现新spec，60项 |
| `npm test` | 退出0；24文件 / 170项通过 |
| `npm run build` | 退出0；既有主chunk提示见局限 |
| `npx playwright test --config=playwright.ui.config.js --project=chromium --workers=1 --retries=0` | 修正轮退出0；60 / 60通过，2.0分钟，无重试、跳过或unexpected API |
| `git diff --check` | 退出0 |

本机命令通过 `PATH=/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin:$PATH` 选择 Node；机器路径不写入项目配置。独立配置只发现本次 UI spec，API 全部由符合现有 DTO 的 route 响应提供，不运行旧 JVM、数据库或真实 Tushare spec。

首轮浏览器曾60/60通过（1.6分钟），独立审查仍发现五项组合断言不足。修正轮仅改e2e脚本，补齐后再次60/60通过；正式源码未变，沿用已通过的170项单测和同一生产构建。

## 浏览器组合结果

49项元数据矩阵之外，11项用例覆盖下载状态、查询/分页/缓存、五视口、设置/四主题、精确宽表、键盘/弹层及截图生成。下载日期仍为`YYYYMMDD`、月份为`YYYYMM`、查询日期为`YYYY-MM-DD`；有效下载只发一次POST，原参数重试不使用后续未提交草稿。延迟下载/查询在设置期间完成后返回，结果保持且不重发；查询重置使旧响应失效。

五视口为1440×1080、1024×768、768×1024、390×844、360×800。每个视口实际渲染21列、201条分页总数的stock_company结果，检查内部溢出、固定列、上一页/下一页/页码及20/50/100条切换，页面不横向溢出，680px以下导航位于顶部。逐色通过设置应用四主题，对下载/查看/设置相同状态下的导航、输入、按钮、面板及设置恢复按钮逐项比较矩形，变化不超过0.5px；三列下载计数保持稳定。

键盘场景用Tab/Shift+Tab/箭头/Enter/Escape完成跳转工作区、导航、API/数据集选择、日期编辑与弹层、下载/查询、每页条数/分页和表格滚动；该链路不使用click、focus或fill预先定位操作。每类控件均检查focus-visible及可见outline，Element Plus复合控件检查实际显示焦点环的wrapper。360px弹层不越界，减少动画偏好下操作仍有效。

以下取真实浏览器计算样式，覆盖按钮正常/hover/按下态，以及页面、面板、导航、raised、accent表面和白字；正文/提示文字检查均达到4.5:1，主操作最低要求为5.5:1。

| 输入主题 | 实际应用色 | 主操作最低对比度（约） |
| --- | --- | ---: |
| 默认`#2857b4` | `#2857b4` | 5.85:1 |
| `#b52c63` | `#a5285a` | 5.52:1 |
| `#ffff00` | `#6b6b00` | 5.52:1 |
| `#000000` | `#000000` | 15.17:1 |

设置新开、取色、HEX校验、保存/刷新、损坏存储回退、读取/写入失败时仅预览和恢复默认均无API请求。真实日期选中色、固定列表面与纯文本tooltip已验证。宽表显示完整DECIMAL`12345678901234567890.123456789012345678`和LONG`9223372036854775807`；`0.0100`显示`+0.0100`，`-0.0000`中性，weekly的`-0.0378`保持原比率，daily百分数与weekly比率标签独立。

## 关闭条件与结果

| 关闭条件 | 正式前端证据 | 结论 |
| --- | --- | --- |
| 主要页面统一布局和视觉效果 | 下载/查看/设置桌面及窄屏截图，对照已确认HTML；独立视觉复审ship | 通过 |
| 设置导航、主题、存储、日期及业务场景；设置无后端请求 | 延迟成功日期/12-10-2/POST=1；原快照重试、income仅annDateTo、在途切换和重置50；逐主题组合 | 通过 |
| 后端接口和客户端契约不变 | 214项保护源核对、Git范围、API单测及49项严格route请求断言 | 通过 |
| 桌面/窄屏、宽表与键盘可用 | 五视口真实表格/分页、三页逐主题矩形/弹层、完整键盘链路 | 通过 |
| 同职责UI/逻辑已复用，行为无回归 | 下表实际调用关系，最终170项单测和60项浏览器回归 | 通过 |
| 构建、回归和兼容验证有记录 | 本文命令、覆盖矩阵、截图与独立规格/质量复审 | 通过 |

## 复用核对

以下 `src` 路径相对 `control-plane`，已从实际源码引用核对，未以组件存在代替调用处接入。

| 共享实现 | 实际调用位置 | 验证行为 |
| --- | --- | --- |
| `utils/theme.js` / `composables/useTheme.js` | `App.vue`创建并provide；`SettingsView.vue`使用同一状态；根CSS映射Element Plus | 完整palette、5.5:1、非法HEX原子拒绝、保存/重置、存储失败仍预览 |
| `common/PageHeading.vue` | DownloadView、DatasetView、SettingsView | 三页标题和导航/缓存回归 |
| `common/WorkbenchPanel.vue` | 下载2处、查看2处、设置1处 | 标签关联、内容/元信息和调用方表单行为 |
| `common/MetadataField.vue` | DynamicParameterForm、DynamicFilterForm | 元数据控件、disabled、标签/错误关联；单日期/范围/月/枚举 |
| `composables/useFormValidation.js` | 两个动态表单 | validate后的错误字段聚焦；各自校验和归一化仍由原业务composable持有 |
| `common/CatalogSelect.vue` | ApiSelect、DatasetSelect薄包装 | 49项分组顺序、中英文搜索、空提示、Escape、受控事件和禁用 |
| `common/AsyncStatePanel.vue` | DownloadView、DownloadResult、DatasetView | INITIAL、polite加载/成功/空、alert失败、纯文本错误/requestId、原资格重试 |
| `dataset/DatasetPagination.vue` | DatasetView的EMPTY/SUCCESS共用一个入口 | 原20/50/100与默认50、快照分页、禁用事件保护 |
| `utils/format.js` 的 `decimalSign` / `formatCell` | DatasetTable | 字符串符号与负零、精确LONG/DECIMAL、DATE与入库时间、其他数值不着涨跌色 |

下载与查询的业务状态机分别保留，没有为了共用呈现而合并；DataSourceSelect仍由两个页面共用。重复选择器的搜索/分组和重复分页调用已移除。

## 实施期证据

这些结果属于各任务实施时的实际运行，不代替上面的T06最终浏览器验收。所有任务均在实现前观察新增行为测试失败，完成后通过独立规格和质量审查。

| 任务 | 实现提交 | 已记录验证 |
| --- | --- | --- |
| T01 主题 | `ba043c9` | 针对性29项；全套22文件/148项；构建通过 |
| T02 导航/设置/缓存 | `7f577e4` | 针对性18项；全套23文件/157项；构建通过 |
| T03 日期与共享字段 | `b4e486b`、`decb5c7` | 针对性58项；全套23文件/158项；修正后14项；构建通过 |
| T04 下载工作台 | `c2d4d52`、`f7363d1` | 下载/公共46项、调用方20项；全套24文件/164项；构建通过 |
| T05 查看工作台 | `65fad74`、`a2d2de3` | 查看/数据71项、公共调用方36项；全套24文件/170项；构建通过 |

T02详细证据原误存入Git的scratch报告，现汇总于此：新增具名settings路由、取色与HEX表单、PageHeading三页复用及容量2的具名业务KeepAlive；设置和404不缓存。真实业务组件测试覆盖日期表单往返、在设置期间完成下载/查询、每页100的第2页保留、失败查询原快照重试及设置零API。路由RED为1失败/2通过、设置RED为3失败、缓存RED为3失败/2通过，随后对应功能均GREEN；最终 `npm test` 为157项、`npm run build`及`git diff --check`退出0。正式证据保留在本文和看板，scratch目录不作为交付物。

T04审查移除了额外全局滚动条/链接/光标样式；T05审查将移动端查询面板四边padding统一18px。两次定点修正后构建通过，独立复审通过。

## T06发现与修正

浏览器检查暴露了两项正式表格呈现缺陷：表格区域聚焦后方向键没有操作内部滚动容器，以及长文本自定义单元格没有弹出完整内容。前者由表格区域将左右键转给实际滚动容器；后者使用纯文本tooltip展示longText，保留原内容转义。精确宽表、键盘场景及DatasetTable的8项单测在修正后通过，最终整套结果见上表。

独立视觉评审发现窄屏下载结果的三项计数被旧媒体规则改成纵列，与T04的三列要求不符。先用360px浏览器检查确认失败，再移除该覆盖，保留三列与大数换行；五视口均加入三列几何断言并通过。同一评审重读更新后的窄屏下载和日期截图，确认原问题已解决且没有引入回归，结论为`disposition: ship`。

首轮测试还发现fixture/定位自身的问题：可搜索选择器选中后搜索input正常清空、ENUM应点击可见wrapper、严格错误DTO的fieldErrors必须是数组，以及分页大小选项须在所属listbox定位。这些修改仅纠正测试对现有组件和DTO的使用，没有调整产品契约。截图动画与长错误证据修正见下文。

## 范围与局限

从本项目实施前 `50eaa24` 到T06最终实现`83530b0`，API客户端、下载/查询业务composable、日期工具、后端和依赖的Git差异为空。214项保护源散列核对只发现工作区原有 `data-plane/tensor-app/pom.xml` 差异；该POM及原有target目录未纳入任何ISSUE-004提交。

Vite存在实施前已记录的主chunk超过500kB提示；本次不改变依赖或增加打包优化。浏览器证据使用合成数据，只证明正式前端与客户端契约，不等于本轮重新验证真实上游、后端性能、安全或发布门禁。

## 49项元数据覆盖

以下49项各有一个以api_name命名的浏览器用例，全部通过实际控件、精确原参数的一次下载、声明筛选的一次查询和完整表头顺序断言。身份/列名来源为manifest及各样例fields，下载/筛选来自既有元数据spec的静态契约，控件与列类型来自只读生产YAML。已核对49个身份唯一、共851个业务字段；balancesheet实测152业务列加3来源列。

| api_name | 下载参数 | 查看筛选 | 业务列 |
| --- | --- | --- | ---: |
| `stock_basic` | list_status | ts_code | 10 |
| `stock_company` | exchange | ts_code | 18 |
| `hs_const` | hs_type | ts_code | 5 |
| `income` | ts_code, ann_date | ts_code, ann_date | 85 |
| `balancesheet` | ts_code, ann_date | ts_code, ann_date | 152 |
| `cashflow` | ts_code, ann_date | ts_code, ann_date | 97 |
| `fina_indicator` | ts_code, ann_date | ts_code, ann_date | 108 |
| `fina_audit` | ts_code, ann_date | ts_code, ann_date | 7 |
| `fina_mainbz` | ts_code, ann_date | ts_code | 8 |
| `stk_rewards` | ts_code | ts_code, ann_date | 7 |
| `stk_holdernumber` | ts_code | ts_code, ann_date | 4 |
| `broker_recommend` | month | ts_code | 4 |
| `trade_cal` | exchange, start_date, end_date | 无 | 4 |
| `margin` | exchange_id, trade_date | trade_date | 9 |
| `daily` | trade_date | ts_code, trade_date | 11 |
| `weekly` | trade_date | ts_code, trade_date | 11 |
| `monthly` | trade_date | ts_code, trade_date | 11 |
| `adj_factor` | trade_date | ts_code, trade_date | 3 |
| `suspend_d` | trade_date | ts_code, trade_date | 4 |
| `daily_basic` | trade_date | ts_code, trade_date | 18 |
| `moneyflow` | trade_date | ts_code, trade_date | 20 |
| `stk_limit` | trade_date | ts_code, trade_date | 4 |
| `moneyflow_hsgt` | trade_date | trade_date | 7 |
| `hsgt_top10` | trade_date | ts_code, trade_date | 11 |
| `hk_hold` | trade_date | ts_code, trade_date | 7 |
| `top_list` | trade_date | ts_code, trade_date | 15 |
| `top_inst` | trade_date | ts_code, trade_date | 10 |
| `margin_detail` | trade_date | ts_code, trade_date | 10 |
| `block_trade` | trade_date | ts_code, trade_date | 7 |
| `slb_len` | trade_date | trade_date | 6 |
| `slb_sec` | trade_date | ts_code, trade_date | 7 |
| `slb_sec_detail` | trade_date | ts_code, trade_date | 6 |
| `forecast` | ann_date | ts_code, ann_date | 13 |
| `express` | ann_date | ts_code, ann_date | 15 |
| `dividend` | ann_date | ts_code, ann_date | 14 |
| `disclosure_date` | ann_date | ts_code, ann_date | 5 |
| `repurchase` | ann_date | ts_code, ann_date | 9 |
| `share_float` | ann_date | ts_code, ann_date | 7 |
| `stk_holdertrade` | ann_date | ts_code, ann_date | 11 |
| `top10_holders` | ann_date | ts_code, ann_date | 9 |
| `top10_floatholders` | ann_date | ts_code, ann_date | 9 |
| `new_share` | start_date, end_date | ts_code | 12 |
| `namechange` | start_date, end_date | ts_code, ann_date | 6 |
| `stk_managers` | 无 | ts_code, ann_date | 11 |
| `pledge_stat` | 无 | ts_code | 7 |
| `pledge_detail` | 无 | ts_code, ann_date | 14 |
| `index_classify` | 无 | 无 | 7 |
| `index_member` | 无 | 无 | 5 |
| `index_member_all` | 无 | ts_code | 11 |

## 截图与视觉审查

截图由专属 spec 的“生成八张可复现的正式验收截图”用例生成，全部使用合成数据。桌面截图视口为1440×1080，常规移动端为390×844，日期弹层和长错误为360×800；full-page图片高度可超过视口。

| 场景 | 桌面 | 窄屏 |
| --- | --- | --- |
| 下载成功，响应计数12 / 10 / 2 | [下载桌面](ISSUE-004-ui-redesign/downloads-desktop.png) | [下载窄屏](ISSUE-004-ui-redesign/downloads-mobile.png) |
| daily查询成功，8条合成记录 | [查看桌面](ISSUE-004-ui-redesign/datasets-desktop.png) | [查看窄屏](ISSUE-004-ui-redesign/datasets-mobile.png) |
| 默认主题设置 | [设置桌面](ISSUE-004-ui-redesign/settings-desktop.png) | [设置窄屏](ISSUE-004-ui-redesign/settings-mobile.png) |
| 真实日期弹层 | — | [日期弹层](ISSUE-004-ui-redesign/datepicker-mobile.png) |
| 长错误、请求ID与原参数重试 | — | [下载错误](ISSUE-004-ui-redesign/download-error-mobile.png) |

首批八图已集中打开并对照已确认HTML：保留侧栏/顶部导航、标题层次、下载双栏、查看上下分组、设置面板及冰川白配色。该批发现日期弹层与选择下拉处于动画中，错误截图仅有短文本；统一禁用截图动画、等待弹层稳定/隐藏并补充长合成错误后重拍。针对受影响的四图完成一次确认：残影消失、日期面板不透明且不越界、长错误及请求ID正常换行，字面`<strong>`没有变成HTML。独立视觉审查确认八图证据有效；唯一计数布局问题已修正并复审通过，最终结论为`disposition: ship`。

收尾静态UI扫描使用 `impeccable detect --json`，覆盖应用壳、全局样式、三页及公共/下载/查看组件；退出0，结果为`[]`。之后的窄屏计数修正另由五视口测试与视觉复审验证。

## 独立审查与收尾

T06审查范围为`c7205fb..daa7b64`，最初发现五项重要验收断言缺口；`daa7b64..83530b0`定点复审逐项确认已解决，无新增重要问题，规格符合、质量通过。共享组件与调用处、保护源和日期/API范围由正式源码、任务证据及本轮回归交叉核对。视觉独立审查的唯一窄屏计数问题也已修正并复审ship。

T02误跟踪的scratch报告证据已汇总到本文实施期记录，报告文件移出Git。看板T01–T06均为COMPLETED，计划复选项同步；T06没有后继，不创建新交接。最终项目审查覆盖`50eaa24..041ab9a`的全部16项提交，结论为approved / ship，无Critical或Important问题；最后仅更新问题关闭状态和审查记录。


最终审查接受一项非阻塞Minor：`control-plane/e2e/ui-redesign.fixtures.js`的限定单行YAML解析器尚未严格验证整行消费，未来未知属性可能被吸收到相邻字符串值。现有49项生产输入已核对只使用支持的键，不影响本轮数据或正式前端。后续扩展该helper或列格式时，应完整校验限定语法并拒绝未知/重复键，补充对应反例；本次按非阻塞测试输入维护项延期，不扩大为通用YAML解析器。2026-09-07 已拆为 [ISSUE-016](../issues/problems/ISSUE-016-ui-fixture-yaml-validation.md) 独立跟踪，ISSUE-004 保持已解决。

执行沿用用户授权的main分支及已确认HTML/技术设计，任务提交均使用明确路径，原有后端POM和target目录保持独立。临时工作记录在证据归档后清理，正式文档与八张截图均在Git中。
