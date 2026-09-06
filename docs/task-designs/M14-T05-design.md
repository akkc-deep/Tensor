# M14-T05 真实 Tushare 2000积分档子集页面验收——任务设计

任务编号：`M14-T05`。权威来源：[任务看板](../task-handoffs/tensor-v1/tensor-v1-task-board.md) Order 75 与 [任务卡](../superpowers/plans/tensor-modules/M14-integration-release.md#task-m14-t05-真实-tushare-49-接口页面验收40h)。唯一直接依赖 M14-T04，完成记录 `80a9491`。2026-09-06用户明确要求“先把这两个排除，验证可以满足2000档积分的即可”，本修订是该请求的执行合同；保留原49接口全量目标的未覆盖事实。

## 剩余工作移交（2026-09-06）

**最新执行安排：** 用户明确要求暂不解决 M14-T05，将剩余 9 项登记为 [ISSUE-008](../issues/problems/ISSUE-008-tushare-live-coverage-gap.md)；随后也将 M14-T06 性能验证暂缓、登记 ISSUE-009 并要求看板直接标记完成，性能尚未实测。M14-T09 的完整 40 项已通过，本任务仍保留 BLOCKED 和原 49 未完成事实；下文移交及各旧轮“下一轮”说明仅为历史，本轮不恢复真实调用。当前入口以权威看板为准，最终发布的真实接口门禁保留。

用户明确要求新增任务承接剩余工作。现由 **M14-T09（Order76）** 负责dividend业务裁决与修复、本地验证/新包接入、完整2000档复验及新证据。入口为 [M14-T09设计](M14-T09-design.md) 和 [transfer交接](../task-handoffs/tensor-v1/M14-T09-handoff.md)。本文件以下内容保留为此前执行的技术合同与历史版本记录，不再据其“下一轮”文字重复启动旧控制目录。

M14-T05保留BLOCKED、已测28通过/1失败/11未运行和原49未完成事实；新增任务不等于批准四字段指纹键，也不把部分验收改写为通过。M14-T09继承安全/参数/固定范围合同，并在明确裁决及新包验证后按其设计接入。此次仅移交，不启动修复或真实调用。

## Goal

2026-09-06 当前修复输入更新：6gn542ah 已真实完成 stock_company 三样例/6294 行，ISSUE-005 关闭；全轮 9 通过、stk_holdernumber 失败、30 未运行，证据 `241813c` 保留。[ISSUE-006](../issues/problems/ISSUE-006-holdernumber-announcement-date.md) 独立限定兼容 `stk_holdernumber.ann_date` 的严格合法本地日期时间，以 DATE 合同映射其日历日期，不改通用转换器/元数据/SQL/样例。下一轮唯一冻结 JAR 为 `/private/tmp/tensor-issue-006-build.2rctzavi/data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`，SHA `f2fc35c933e69da5e85690fbabb13d691178538cd6ffb3b94284dfc95b10db89`，替代下文 ISSUE-005 阶段包引用；其余原输入规则继续适用。原包与 ISSUE-005 包均保留，不通过环境允许任意包。

ISSUE-006 包从独立源码快照构建，复用原静态前端；与 ISSUE-005 包展开后同为 364 文件、无增删，仅 `TushareResponseValidator.class` 内容变化，其他产物合同继续适用。新增合成适配正反例与原相关回归通过，独立代码复审无 Critical/Important，唯一 Minor 的同名字段隔离测试已补强并随构建通过；既有 7 唯一打包测试通过。真实失败的字段未保留，本地/历史诊断不冒充该次真实字段；下一轮仍以完整页面结果判定成功或 BLOCKED。

2026-09-06 产品修复接入：用户在真实 `stock_company` 失败后授权“继续修复”。独立 [ISSUE-005](../issues/proposals/ISSUE-005-tushare-decimal-decoding.md) 补齐原精度合同，相关 85 测试及独立复审通过。下一轮使用 `/private/tmp/tensor-issue-005-build.kibqgbn5/data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`，SHA-256 `7f794f3494109c27f134c04846e486bda3fe18beec3a88246b58fbcea719cef9`。此明确修订优先于下文历史“原 JAR”路径/hash引用：运行时将该修复包作为唯一冻结输入，仍严格校验 hash，不允许任意 JAR。原包保留且 hash 不变；真实历史失败记录不改写。

修复包在独立源码快照以既有 acceptance profile 构建，复用原包全部静态前端资源；既有 production/acceptance 打包合同通过。归档展开后无增删，仅 `TushareProClient.class` 内容变化，其他类、依赖、49 份 YAML、6 份 SQL、页面和公开合同与原包逐字节一致。因此沿用 M14-T04 的既有合同证据。Java 修复/合成测试/构建只归属 ISSUE-005，不扩张 M14-T05 的页面验收实施范围；40/48/9、原 manifest、真实 Token 环境、安全门禁与失败停止规则均不变。

从原样验收 JAR 页面执行公开文档明确支持2000积分档的40个接口、48组原manifest合法样例，验证真实上游反馈、非空结果的适配/入库/页面查看，以及合法空结果无占位行。消费 M14-T04 已通过的元数据/表/归档合同，补上其明确未执行的真实下载。对应 PRD 12.2、AC-004/005；不把账户无权限、样例变化或环境失败写成通过。

## Scope

仅修改本任务既有 `control-plane/e2e/tushare-live.spec.js` 与 `docs/verification/M14-T05-tushare-live.md`；设计/任务卡/看板/交接另行记录授权范围。spec 自含最小 Node 标准库辅助、40个串行用例、JAR 生命周期、页面流程、限速和安全证据。fixture 非空适配与空结果补测为同文件独立准备阶段，不增加40项真实接口计数。

不读取 M00～M13 后端生产实现，不修改 Java/Vue/YAML/SQL、POM、package/lock、Playwright全局配置、manifest/模板、其他既有测试或分发 JAR。不构建、不运行工作区 clean，不复制模板 data、不使用 API/SQL 种数、不截获或替换上游/页面响应。所有下载与records请求来自页面；直接HTTP只允许自有JVM的health。不覆盖性能、故障注入全矩阵或发布准入，不新增永久helper/依赖/产品入口。

## Approach

### 输入、既定决定与运行前置条件

- M14-T04：[设计](M14-T04-design.md)、[实际证据](../verification/M14-T04-49-contracts.md)、`scripts/verify-49-contracts.sh`、`control-plane/e2e/tushare-metadata.spec.js`。最终实施 `616d54d`，shell50/52/4、前端120、资源49/49/49，Run7 API/dataset各49/49、零业务调用、13截图及清理通过。保留七组分类、`range -> date_range`、43必填/6无参数和五组filters；不 import 会注册测试的旧spec，不重复其全量元数据断言。
- 公开补充合同：`docs/contracts/openapi-v1.yaml`、PRD 5.6/5.7与12.2、TRD 7.1～7.4与10.4的计数语义、`docs/runbook/acceptance.md`、`docs/runbook/configuration.md`。原验收 JAR 路径 `data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar`，SHA-256 `a69874afa6ce783d4ef4e16a678ddb0ff457f2948b68f509a8e4a2c00440bcac`，运行前后不变；不使用T04新建生产JAR。
- 任务卡显式要求的fixture补测消费 `docs/task-designs/M14-T02-design.md`、`docs/verification/M14-T02-download-outcomes.md` 和 `control-plane/e2e/download-outcomes.spec.js` 的既有SUCCESS/EMPTY页面合同。既有15/15矩阵证明通用适配与回滚路径；本轮单独重做SUCCESS/EMPTY页面闭环，不声称fixture替代12个空接口各自的非空真实数据。T02是任务卡指定的补充合同，不修改看板唯一直接依赖。
- 用户已经说明当前账户“2000+积分”，并授权本轮只验证该档可用接口。公开权限审计见 `docs/verification/M14-T05-tushare-live.md`：普通档200次/分钟、每日100000次/个API，stock_basic单独50次/分钟。采用40个文档明确最低积分不超过2000的固定集合；不再要求账户覆盖被排除的高积分/未确认接口，也不把公开规则当作实际授权或剩余额度证明。原本“全49权限/58次剩余额度全部先确认”的阻塞条件不适用于本轮缩减请求。账户现时权限、用量或上游差异由正式页面结果如实验证，遇错误失败并停止，不先用curl/SDK探测，不自动重试。
- Token只能由用户通过 `TENSOR_TUSHARE_TOKEN` 环境输入；不从聊天、文件、源码、浏览器存储、其他变量或其他进程环境读取，不打印/记录值或哈希。此前私有启动器已因Ctrl+C退出且清理DB，旧ready文件不可复用。新一轮启动前全部本地准备完成，再由用户在已有Token的终端执行一次可直接开始的命令；不设置等待聊天确认的长循环。
- 本轮启动器明确注入 `M14_T05_CALL_INTERVAL_MS=2000`，表示上一真实下载响应完成到下一次点击间隔至少2秒（整体不超过30次/分钟，低于已知最严50次/分钟）；spec仍要求整数环境输入，范围调整为2000～3600000。它仅控制测试调度，不进入JVM。频率或额度失败保留实际错误，不能靠提高积分、放宽限制或换日期偷偷重跑。

### 原49项manifest与固定40项执行集合

先使用既有 `validateManifest` 完整校验未修改的manifest及SHA-256 `37a317f6a2bc3e5113be5f127976d16d8349414c6476c7f6a194b084a5b0f7c2`：49唯一api、58组字符串params、37ok/12empty、filename/参数顺序等原合同不变。只读manifest，不读模板data。

固定排除表必须显式写在spec中，使用安全枚举原因；没有运行时任意include/exclude环境、CLI grep或动态失败后删项：

| apiName | 原因 |
|---|---|
| top_inst、broker_recommend | `higher_points`：用户指定排除；接口页分别要求5000/6000，且总表有差异 |
| share_float | `permission_unverified`：接口页120与总表3000矛盾 |
| hs_const、moneyflow_hsgt、hk_hold、index_member | `permission_unverified`：原官方文档返回文档不存在 |
| hsgt_top10、namechange | `permission_unverified`：当前说明没有明确最低积分 |

纯函数 `selectLiveInterfaces` 在完整manifest校验之后，断言固定9个排除名均存在且唯一，其余按manifest原序保留。断言恰40接口、48样例、28ok/12empty；同时暴露固定安全的排除名/原因，供证据记录。40个精确API为：stock_basic、stock_company、income、balancesheet、cashflow、fina_indicator、fina_audit、fina_mainbz、stk_rewards、stk_holdernumber、trade_cal、margin、daily、weekly、monthly、adj_factor、suspend_d、daily_basic、moneyflow、stk_limit、top_list、margin_detail、block_trade、slb_len、slb_sec、slb_sec_detail、forecast、express、dividend、disclosure_date、repurchase、stk_holdertrade、top10_holders、top10_floatholders、new_share、stk_managers、pledge_stat、pledge_detail、index_classify、index_member_all。

沿这个集合注册40个 `liveTushare:<api_name>`，每API一个串行用例，用例内按原params顺序各提交一次。stock_basic、stock_company、trade_cal、margin各3样例（12次），其余36接口各1样例（36次）；共48次真实下载POST。被排除9项不注册测试、不计为skip/通过/失败，在安全证据中独立列为本轮不覆盖；不把注册40误称执行40。

28个ok接口要求所有样例合法完成，且每API至少一个SUCCESS、合计sourceRowCount>0；多样例其余请求可合法EMPTY，不强加逐样例status。12个empty仍为income、balancesheet、cashflow、fina_indicator、fina_audit、monthly、slb_len、slb_sec、slb_sec_detail、dividend、top10_holders、top10_floatholders，各唯一样例EMPTY、三计数0、末查询0行。manifest的历史row_count不与本轮计数等值比较。所有日期20260807/202608与证券代码保持原样，不换参数、不伪装EMPTY。

### 隔离与生命周期

每次完整运行或修订复跑由运行者创建专用全新空MySQL8.4.6 schema，名称 `^tensor_m14_t05_[a-f0-9]+$`，utf8mb4/utf8mb4_0900_as_cs，应用账号只有该schema的CREATE/SELECT/INSERT/UPDATE并使用真实来源host，不默认 `%`。只给测试三个 `TENSOR_DB_*`、`ACCEPTANCE_JAR`、标准Token环境变量、上述调度输入和测试产物目录 `M14_T05_ARTIFACT_DIR`；管理员凭证不交给spec或JVM。运行者以独立只读CLI采集初始0表、启动后6成功迁移/50业务表/49生产表及fixture全空的安全证据，不打印连接值或种数。

spec检查Java21、原JAR普通绝对文件及哈希、三个DB变量非空、JDBC无嵌入凭证及凭证query、schema命名、8080空闲、PLAYWRIGHT_BASE_URL未设置或恰 `http://127.0.0.1:8080`。不复用未知进程。JVM环境先净化继承的TENSOR_/SPRING_/SERVER_/MYSQL_/M14_与JAVA_TOOL_OPTIONS/JDK_JAVA_OPTIONS/_JAVA_OPTIONS等注入变量，只回填三个DB值和真实Token；浏览器launchOptions.env与辅助子进程使用PATH、HOME、JAVA_HOME、TMPDIR、LANG/LC_ALL白名单，不继承DB/Token。JVM显式使用公开生产上游 `TENSOR_TUSHARE_BASE_URL=https://api.tushare.pro`，不保留T04哨兵或其他地址。本次本地原JAR启动复现已确认Flyway信息日志携带JDBC地址触发写前扫描；作为本任务启动器配置修复，spec在JVM环境固定设置 `LOGGING_LEVEL_ORG_FLYWAYDB=WARN`，不继承外部日志级别输入。只抑制该第三方包的INFO/DEBUG启动日志，保留WARN/ERROR、全部业务完成事件和其他日志；不设置全局静默、不关闭或放宽扫描、不将日志脱敏改判通过。该固定非秘密输入不进入浏览器或其他辅助进程，不修改生产配置文件或原JAR。spawn必须shell:false；argv只含：

```sh
java -jar "$ACCEPTANCE_JAR" \
  --spring.profiles.active=acceptance --tensor.plugins.fixture.enabled=true \
  --server.address=127.0.0.1 --server.port=8080
```

health每次2秒、总90秒等HTTP200/UP。beforeAll显式600秒：前置检查最多30秒、health90秒、fixture页面阶段120秒，失败后的完整清理另留330秒，合计至多570秒。Chromium1440×1000；serial、retries=0（覆盖CI默认重试），只接受单worker；trace/video/自动截图全部off。每个真实POST等待响应135秒，保留产品120秒上游/130秒前端超时；单case timeout为 `240000 + params.length * (150000 + intervalMs)`（额外窗口覆盖页面准备及失败排空/关闭），不以Playwright默认30秒截断真实调用。各case新页面，不分享表单状态；只分享自有JVM、调度时钟与安全累计计数。

beforeAll失败同样独立清理已创建资源。afterAll显式330秒：未结请求/响应扫描最多135秒，超时记录固定网络排空失败并继续清理；随后正常SIGTERM自有JVM并最多150秒等待close与8080释放，余45秒用于关闭自有page/context及扫描/写安全摘要。各清理阶段独立执行，排空失败不能阻止停机；不得日常SIGKILL。主失败与清理失败以AggregateError保留，完整40计数断言只在运行无主失败时判定成功，失败轮仍保留实际已执行/未执行计数。串行失败后剩余用例未运行不得充当通过。

### 独立fixture补测

beforeAll创建并最终关闭自己拥有的浏览器context/page，用真实页面单独完成以下流程；不进入40个live标题或48次真实调用计数，不启动本机上游替身，不需要故障DDL。

1. `/datasets` 选择Fixture/fixture_daily，无筛选点击查询，初始totalElements=0。
2. `/downloads` 选择Fixture/fixture_daily及SUCCESS，页面POST200/SUCCESS，三计数1/1/0，结果panel显示对应计数。经导航进入数据查看，证券代码000001.SZ查询恰一行：ts_code=000001.SZ、trade_date=2026-08-07、amount=`11.230000000000000000`、note=null、source_plugin=fixture、source_api=fixture_daily、ingested_at为本轮有效instant；对应七列页面文本与M14-T02合同一致，null显示`--`，时间独立按Asia/Shanghai格式化。
3. 再经页面选择EMPTY提交，200/EMPTY、0/0/0及“下载成功，0 条数据”/“本次请求没有可写入的数据。”；页面重查，完整fixture行及ingested_at不变。两次POST及三个records GET各自头体requestId一致、唯一。只保留安全计数与结果，不保存行值。

这是任务卡要求的通用fixture适配/入库/查看补测；12个真实EMPTY仍逐一执行，既有M14-T04证明其定义/字段/表合同，不能把fixture结果标为这些接口的真实非空下载。

### 每个真实接口的页面流程

1. 动作前安装T04修订后的request/requestfinished/requestfailed/response/pageerror监视和待完成promise排空边界。打开 `/datasets`，level1“数据查看”，combobox exact“数据源”选Tushare Pro，“数据集”以精确api名边界选当前项。页面metadata GET必须200且身份相符；声明的filters和列以T04已验证合同为输入，不使用后端实现。所有filters保持空，确认“设置筛选条件后查询”且尚无records自动请求；点击“查询”，当前API初查totalElements=0、items=[]。
2. 通过导航link“数据下载”，选择Tushare Pro和当前“数据接口”，确认当前api身份。params按照描述符声明顺序填；ENUM使用真实combobox/option，DATE/DATE_RANGE_MEMBER把compact值转为YYYY-MM-DD、MONTH转YYYY-MM，输入Tab提交后Escape关闭浮层；TS_CODE照样例填写。本轮五个无参数接口不造控件。参数label与type沿M14-T04公开参数表；不读组件props，不写DOM/CSS。
3. 限速器在每次真实POST前检查与上一真实POST完成时钟的间隔，条件等待至满足输入间隔；无并发，无catch重试。先注册当前唯一POST的监听再点击“开始下载”。监听实际请求必须恰 `{pluginId:'tushare_pro',apiName,params:当前manifest对象}`，不携Token/其他键，日期/月提交值保持compact。结果完整结束、页面控件恢复后才能进入下一样例。
4. HTTP200时DownloadResponse精确八键：requestId/outcome/pluginId/apiName/sourceRowCount/insertedRows/updatedRows/message；头X-Request-Id与body非空requestId相同、全轮唯一，身份一致。三计数均为非负安全整数。SUCCESS的sourceRowCount>0，且 `0 < insertedRows + updatedRows <= sourceRowCount`；不能要求与sourceRowCount总相等，TRD10.4按不同业务键计写入数。EMPTY三计数全0。页面role=status显示正确heading、SUCCESS的term/definition三计数，EMPTY的固定文字和无三计数列表；不能只验证HTTP。每个样例结果均真实记录。
5. 完成当前API全部样例后，经“数据查看”导航选择同名dataset，无筛选点击查询（每API恰初查/末查2次，共80个真实dataset records GET）。PageResponse精确九键、头体requestId一致且唯一、page1/pageSize50、totalPages与totalElements相符；columns为definition业务列原序加source_plugin/source_api/ingested_at。初始空表且本轮无其他写入，所以末查totalElements必须等于本API所有成功响应insertedRows之和；允许后续样例更新已有键，不要求total等于上游行数之和。
6. ok接口至少一个SUCCESS、末查totalElements>0且items非空，取第一页第一个业务row核对完整columns键集合、每值string/null、来源恒tushare_pro/当前api、ingested_at落在本case首次下载前至末次下载后时钟区间。此空表前置+唯一页面写入+插入计数+来源时间证明记录来自本轮当前接口。逐列核对该row的页面文本：定义label表头；业务null=`--`、其余保持响应字符串，ingested_at独立Intl/Asia/Shanghai格式化；滚动使末端来源列实际可见，不保存该真实行或截图。empty接口末查items=[]/total0，显示“未找到符合条件的数据”，无占位行。
7. 每个用例结束前排空所有网络扫描，关闭自己拥有的page后再次排空并检查最终失败，才写通过标记。不得仅在response事件登记promise而漏掉未响应请求，不忽略page.close导致的requestfailed。

### 错误、凭证与证据

任何真实下载非200都不能计为SUCCESS或EMPTY。对已登记的当前POST先安全读取ApiError（精确五键、头体requestId、公开code/message/retryable/fieldErrors），核对页面“下载失败”与安全摘要、不出现成功空结果，随后使case失败；不点击“使用原参数重试”。SOURCE_AUTH_FAILED/SOURCE_PERMISSION_DENIED为账户环境阻塞；SOURCE_RATE_LIMITED为频率/额度阻塞；SOURCE_NETWORK_ERROR/SOURCE_TIMEOUT/SOURCE_UNAVAILABLE为环境/上游故障，保留证据再定位。PARAM_*、DATASET_MISCONFIGURED、ADAPTER_*、PERSISTENCE_FAILED、QUERY_FAILED等保留真实产品/配置失败供独立任务处理，不在本任务改生产文件或自行发明任务ID。

浏览器对 `/api/v1/**` 只允许metadata GET、指定API/fixture的records GET和已登记的下载POST；此外允许原JAR的 `/`、`/downloads`、`/datasets` 页面文档与 `/assets/**`、`/favicon.ico`、`/vite.svg` 的正常同源GET，这些资源也必须通过HTTP/网络错误检查，不静默忽略404；任何其他写入、额外POST、非同源请求、pageerror、requestfailed或非当前POST错误HTTP均失败。服务端真实上游由JVM通过HTTPS访问固定域名，不能用浏览器route/mock、代理或伪造成功响应替换它。48是页面提交数；没有出站捕获时不得把它描述为独立测量的上游调用数。通过真实配置、页面响应和实际完成事件关联证明所执行路径。

新建0700临时运行目录，进程日志最多按0600私有文件保留；不tee原日志。所有响应/请求/可见页面在断言或证据写入前扫描真实Token、DB密码/账号/JDBC值及敏感键。失败诊断用固定安全检查名/公开错误码，不将matcher实际响应、行值、环境或底层异常正文交给reporter。业务响应只在内存中校验，不写完整JSON。禁止trace/video/自动截图、完整上游响应和浏览器存储快照；本任务不创建手动截图，以免保存真实行。所有body断言使用固定安全错误，try/catch/finally在抛出给Playwright之前排空并关闭自有page，beforeAll同样关闭自有context；禁止attach或保存可访问性快照。各阶段有自己的有界超时，先于外层hook/test预算失败，使页面关闭发生在Playwright失败产物生成之前。清理错误也转为固定安全错误后与主失败一起保留，不能重新抛出携原响应或行值的matcher错误。

JVM日志流在写文件前检查上述秘密字面值及其JSON转义形式，并拒绝 `"fields"\s*:`、`"items"\s*:`、`"token"\s*:`（大小写不敏感）等上游包络键；分块匹配保留最长秘密长度减1的重叠并按完整行检查键，只有已扫描前缀可落盘。单行超过1MiB安全失败且不落盘，EOF必须检查剩余尾部。命中即终止成功路径、停止继续调用并清理，不写匹配内容。停机后的spec扫描只是第一层，不作为最终产物安全结论；完整终检必须由运行者在npx进程和全部worker退出之后执行下述门禁。失败日志不含完整环境、URL凭证、原响应、SQL值或堆栈回显。

运行者在调用npx前用mktemp创建自己拥有的空0700 `tensor-m14-t05.*` 临时目录并通过 `M14_T05_ARTIFACT_DIR` 给spec；spec校验绝对路径/目录所有者/0700/非符号链接，自己的日志和安全JSON置于其中的run子目录。CLI用 `--output "$M14_T05_ARTIFACT_DIR/playwright"` 隔离自动产物，用0600 runner.log捕获完整stdout/stderr，不直接显示失败输出。不要复用默认输出根目录或他人目录。

**CLI退出后的固定终检算法：** 运行者保存真实npx退出码，然后用Python标准库递归读取这个精确自有目录（不跟随symlink，异常对象直接失败），扫描Token/DB秘密及JSON转义字面值；命中任何文件则删除该文件并使终检失败，仅输出固定泄漏标志。无论npx是否成功，随后删除本轮playwright子目录内全部自动产物（包括error-context.md、附件、可访问性快照及.last-run.json），不保留可能包含真实行的失败上下文；本任务没有允许保留的Playwright附件。任何自动上下文/附件在成功轮出现均视为异常产物并使终检失败。run子目录只允许spec约定的已扫描应用日志与安全JSON，根目录只允许runner.log和终检安全摘要；意外文件删除并失败。不得通过脱敏把失败轮改判通过。终检摘要只记录npx原退出码、文件数、已删除产物数和扫描/清理布尔值，最终退出码优先保留非零npx码，否则扫描或删除失败返回1，否则0。只发布扫描通过后的白名单计数/路径，不打印runner.log全文。待提交证据文档生成后再以同一秘密集合扫描该精确文档，全部门禁通过才允许完成。

纯本地验证必须使用当前Playwright1.62.1，在临时目录生成合成秘密/合成行的故意失败页面探针：验证安全异常和page/context关闭、CLI退出后枚举自动失败上下文/附件，以及最终扫描删除；另模拟afterAll之后生成的文件，证明不会漏扫。探针不用真实Token、不请求上游、不增加live测试或永久文件；只断言安全布尔值和原失败码被保留，不打印合成内容。该合成Chromium终检探针上一版本已实际通过；本次不改其安全流程，不重复执行，除非修订或审查发现相关新问题。

每个下载/查询requestId在最终日志中必须恰一个既有 `tensor.operation.completed`，其plugin/api与实际响应一致，日志outcome按SUCCESS→success、EMPTY→empty、成功query（含0行）→success、ApiError→failure映射，成功计数或查询计数一致，durationMs非负；只在内存检查原日志，不发布paramSummary、filter值或其他原文。按请求ID核对完成事件并排空后再正常停机复查总数。没有测到的值不填0。

安全JSON逐请求只保存 `apiName`（fixture为fixture_daily）、`outcome`（成功/空或固定公开错误码）、实际计数、`requestId`、`durationMs`；阶段分别放fixture/download/query数组。运行级元信息只保留版本、Git/spec/JAR/manifest哈希、时间、命令、实际完成/未执行数、清理/扫描结论和非秘密调用间隔，不包含Token哈希、账户/schema/host、params值、业务行或完整响应。测试stdout只打印固定阶段、实际简洁计数与安全文件路径。安全JSON新增scope对象：id固定points-2000、manifestCases=49、manifestSamples=58、selectedCases=40、selectedSamples=48、excludedInterfaces为9项apiName/reason。totals.manifestSamples保持58并新增selectedSamples=48；其他运行/请求计数只覆盖当前40项。文档记录40个API与48请求的实际安全投影，另列9项范围排除；失败轮按40项列实际完成/失败/未运行，不得与排除项混淆。

运行者末尾独立只读确认6迁移/50业务表，40个已选生产表各自实际行数与页面末查total一致、9个排除API物理表仍0行、fixture1行；只保存API/计数，不保存连接信息或行。正常停机后按精确名称/所有权标签清理本轮schema/账号或专用容器及匿名卷与临时凭证，不触碰其他会话资源。原JAR及manifest哈希不变。修复凭证/权限后保留同一测试与样例，在全新空schema手动重跑完整矩阵；未知外部条件不能由等待或自动重试当作已解决。

## Files

- Modify `control-plane/e2e/tushare-live.spec.js`，100644：上述40用例、固定范围选择和同文件公开生命周期、独立fixture补测、48样例、限速、安全和结果校验。
- Modify `docs/verification/M14-T05-tushare-live.md`，100644：实施时实际版本、输入检查、命令/退出码、40接口/48请求逐项安全结果和9项排除原因、fixture补测、页面行匹配/独立表计数的结论、请求关联、扫描、清理及失败归因。

仅精确两实施路径加入Git，提交 `test(release): verify live Tushare interfaces`；设计/交接/看板独立提交。不提交Token、临时日志/JSON/数据库、生成target/图片或用户并行ISSUE-004文件。

## Tests

本次修复须先以同函数环境探针证明当前缺少固定Flyway日志级别（RED），再实现唯一JVM环境值；GREEN核对其固定为WARN、外部同名/全局日志环境不会继承，浏览器/辅助publicEnvironment不包含该设置且其他JVM输入仍限既定白名单。复用上述同一原JAR启动探针，在另一个全新空schema验证health就绪、写前扫描不失败、6迁移/50表仍全空和正常清理；用合成JDBC/秘密日志另证扫描仍拒绝命中。真实2000档矩阵保持待执行，不将本地启动GREEN写成正式验收通过。

启动失败后的本地诊断不计真实验收：用户已要求由执行者处理启动问题。允许在自有临时目录从当前spec生成不注册live用例的临时探针，仅调用同一前置检查、原JAR启动/health和正常停机；专用新空MySQL8.4.6/权限/来源host及原JAR/manifest规则不变。此诊断由控制器生成明确的合成Token，仅通过该探针进程的TENSOR_TUSHARE_TOKEN环境进入JVM；不读取、替换或复用用户真实Token，不提交业务POST/records请求、不启动上游替身。仅在内存将日志扫描失败投影为固定类别（秘密、包络、长度、写入）及已知框架标识布尔值，命中原文不得落盘或输出；保持写前扫描/失败停止/终检清理。诊断结果只能证明本地启动行为，不能证明实际账户授权、真实接口结果或本轮fixture通过。任何正式live复跑仍必须用用户真实Token和全新空环境，先完成修订及本地检查再交用户一次执行命令。

先为本次范围修订增加同函数纯本地RED反例，再修改spec：从冻结49/58选择精确40/48/28ok/12empty、9项排除身份/原因，缺少固定排除名/异常集合拒绝、完整manifest校验仍拒绝错误hash/49缺项，未选API不注册、缺环境摘要registered40/unexecuted40且manifestSamples58/selectedSamples48，最终完整运行计数不再硬编码49/58/98。保留既有同函数纯本地反例，不用故意损坏Token向真实服务制造RED。标准库临时探针验证：manifest错误hash/少项/多项/未知status/非法params被拒绝；多样例ok允许单次EMPTY但全空不能通过；历史row_count不参与本次计数；不同键计数允许小于sourceRowCount；重复requestId/额外POST/缺末查/行来源错误/插入合计不符/跨chunk秘密/pending请求越界均失败。探针调用正式函数、不mock产品HTTP，不新增永久测试文件或live用例；在受限临时目录运行并删除含合成哨兵的产物。

```sh
# 仓库根；不启动JVM、不访问真实服务
node --check control-plane/e2e/tushare-live.spec.js
cd control-plane
npx playwright test e2e/tushare-live.spec.js --list
# 按本轮授权范围准备新空schema与私有Token/DB环境；专用shell关闭跟踪
export M14_T05_CALL_INTERVAL_MS=2000
set +x
umask 077
M14_T05_ARTIFACT_DIR=$(mktemp -d "${TMPDIR:-/tmp}/tensor-m14-t05.XXXXXXXX") || exit 1
export M14_T05_ARTIFACT_DIR
tensor_m14_t05_exit=0
npx playwright test e2e/tushare-live.spec.js --workers=1 \
  --output "$M14_T05_ARTIFACT_DIR/playwright" \
  >"$M14_T05_ARTIFACT_DIR/runner.log" 2>&1 || tensor_m14_t05_exit=$?
# npx完全退出后，由运行者执行上文固定终检算法；文档记录内联Python命令和原退出码
# 不能直接cat runner.log，不能以终检成功覆盖非零tensor_m14_t05_exit
```

语法/发现exit0、恰40个Chromium标题，纯探针不需要真实Token。本轮完整实跑预期exit0、40 passed、0 failed/skipped/retry；28个ok/12个empty接口符合各自规则，48次真实下载POST与80次dataset查询，fixture另2POST/3查询；所有成功计数/来源记录/EMPTY无行/日志请求关联成立，CLI退出后的终检也为0且无自动上下文/附件残留。T04已运行前端120项且本任务不改产品/前端源码，不重复全套unit/Maven或旧15项故障矩阵；修订spec后用新空schema重跑受影响本地探针及完整live矩阵，任何重跑都需仍满足账户配额。

提交前 `git diff --check`；核对实施base到HEAD仅两指定实施文件，测试哈希与实际证据一致、文件模式正确、原JAR/manifest不变、敏感扫描与所有清理完成。缺Token/账户额度/DB/JAR/Java/端口/网络时保留前置失败，不能以静态检查、T04假Token结果、部分矩阵或历史M14-T02结果代替本轮实跑。

## Acceptance

- 原JAR真实页面完成本轮40接口全部合法样例共48次串行提交，无自动重试、无额外上游探测，Token仅通过规定环境进入JVM。
- 28个ok接口各至少一次非空成功，页面三计数与本次结果一致；独立空库前置、页面同数据集查询、插入累计/来源字段/本轮时间和可见记录共同证明适配入库查看。12个empty均合法0/0/0且对应表与页面0行。
- fixture SUCCESS/EMPTY补测在本轮单独从页面完成非空适配/写入/查看及空结果行不变，不混入40真实接口计数或宣称覆盖12个接口的非空上游。
- 所有鉴权、权限、限流、网络、样例漂移及产品失败真实记录；只有本轮40通过、无跳过/重试、请求/清理/扫描门禁通过，才能报告2000档子集验收完成。原49目标尚有9接口不覆盖，因此M14-T05只记录本轮阶段结果并以pause交接进入PAUSED，不将全量任务标COMPLETED、不自动准备后继。真实运行错误则记录失败并进入BLOCKED；用户以后要求覆盖余项再另行恢复原任务。
- 精确两文件提交，最终证据仅保留允许的安全元信息/投影；没有秘密、真实行、完整上游响应、trace或截图泄漏，无生产/配置/旧测试改动，原分发物不变。

## Risks

- 本轮选择依据用户给出的积分档位和已读公开文档，不是假定上游已授权。实际鉴权、权限、限流和网络错误均使所选用例失败；未知个人用量不由公开日上限代填。
- 两项高积分与七项权限待确认构成本轮固定排除；它们不是成功、EMPTY或测试skip，不可据本轮结果声称PRD全49或最终发布准入通过。
- 历史日期/状态可能漂移；保留失败，不修改manifest、模板、分发物或生产代码。fixture证明通用闭环，不代表12个空接口已取得非空上游数据。
- 独立迁移后空表观察必须捕获真实6迁移/50业务表且全空，末尾再核对40页面计数、9空表和fixture1行；未测量不能填0。
- 新私有终端启动器直接进入已批准的执行流程，提供固定安全阶段/耗时心跳；不要求用户先启动一小时等待脚本。它是本机一次性操作材料，不新增永久helper。前置失败不启动真实请求，CLI结束后必做终检并正常清理自有JVM/DB/卷/临时连接材料。
