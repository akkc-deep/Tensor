# M14-T02 下载失败、空结果、幂等和回滚矩阵——任务设计

任务编号：`M14-T02`。权威来源：[任务看板](../task-handoffs/tensor-v1-task-board.md) Order 72 与 [任务卡](../superpowers/plans/tensor-modules/M14-integration-release.md#task-m14-t02-下载失败幂等和回滚矩阵40h)。直接依赖只有已完成的 M14-T01。

## Goal

从原样打包的验收 JAR 页面执行确定性下载结果矩阵，证明合法空结果、客户端拒绝、上游失败、适配失败、实际数据库回滚与重复下载有不同且正确的结果。将 AC-004～011 对应的页面、响应、数据和按请求 ID 关联的完成事件形成可复现证据；其中 Tushare 为本机受控替身，不宣称真实服务验收通过。

## Scope

实施只新增 `control-plane/e2e/download-outcomes.spec.js` 与 `docs/verification/M14-T02-download-outcomes.md`。JavaScript 文件内包含必要的 Node 标准库 stub、JAR 子进程管理、MySQL CLI 故障准备和清理、Playwright 页面操作及本地证据投影，不增加依赖、公共 helper 或全局配置。

覆盖 SUCCESS、EMPTY、必填缺失、逆序日期、七个 SOURCE 错误码、fixture TYPE_FAILURE、fixture PERSISTENCE_FAILURE，以及重复 SUCCESS 的业务键/更新时间/计数。数据库故障只发生在本任务独立空库的临时触发器中。

不读取 M00～M13 后端生产实现，不修改 Java、YAML、SQL、Vue、配置、package/lock、POM、runbook、既有 E2E、JAR 或迁移历史；不通过业务 API/SQL 种数替代页面，不拦截或伪造浏览器 API 响应。不测试真实 Token/真实 Tushare、49 接口总验收、查询宽表/分页竞态、性能或完整安全矩阵。不新增进度、取消、历史功能。产品缺陷记录精确失败场景和所属语言，按既定流程准备独立修复任务，不通过删断言、skip、重试或产品改动宣称本任务完成。

## Approach

### 消费的输入与合同

- [M14-T01 设计](M14-T01-design.md)、[测试](../../control-plane/e2e/fixture-flow.spec.js)、[证据](../verification/M14-T01-fixture-flow.md)：实现 `23addbe`，完成记录 `afb3b85`；已记录验收 JAR 的真实页面 SUCCESS/EMPTY、七列行投影、公开选择器、进程所有权和 3/3 结果。消费其事实和最小局部辅助方法模式，不 import 含注册用例的 spec，不重新执行禁用矩阵。
- [验收包说明](../runbook/acceptance.md)、[运行配置](../runbook/configuration.md)：独立验收 JAR、acceptance+true、MySQL 8.4、空 schema、V1～V6/50 业务表、应用账号 CREATE/SELECT/INSERT/UPDATE；connect=5s/read=120s、前端=130s、每阶段停机=70s。保留这些值。
- [OpenAPI](../contracts/openapi-v1.yaml)、[错误码](../contracts/error-codes.md)、[M09-T05 公共错误设计](M09-T05-design.md) 与 [M09-T06 事件设计](M09-T06-design.md)：HTTP/code/retryable、安全固定摘要、requestId 头体一致、一个 `tensor.operation.completed`；API 摘要采用全局 HTTP 映射，不采用插件内部异常文本。
- [M08-T02 场景设计](M08-T02-design.md)：fixture 唯一业务行、五值场景；`PERSISTENCE_FAILURE` 是合法 note 标记，必须另作真实故障准备。
- [M03-T03](M03-T03-design.md)、[M03-T02](M03-T02-design.md)、`docs/data-template/daily.json` 的字段投影，及 [M07-T02](M07-T02-design.md)/[M07-T03](M07-T03-design.md) 协议与分类约定：daily 唯一必填 trade_date，new_share 必填 start_date/end_date，严格 fields 顺序和行宽，上游 HTTP/网络/读取超时映射。
- `DynamicParameterForm.spec.js`、`DownloadResult.spec.js`、`DownloadView.spec.js` 与 [M11-T02](M11-T02-design.md)：可访问标签、必填/范围字段错误、状态/alert、安全文案及按钮锁定。既有测试只作公开行为依据，不复制 mock 到本任务。

上述输入互补：fixture 覆盖确定性场景和同键更新，本机上游覆盖生产插件公开失败分类，独立数据库临时触发器补足实际写入故障。无新增产品合同。

### 环境、凭证与所有权

运行者按验收说明准备一个**本次专用全新空** MySQL 8.4 schema，建议名称 `tensor_m14_t02_<随机十六进制后缀>`，`utf8mb4/utf8mb4_0900_as_cs`。每次完整重跑用另一个新空 schema，不复用 M14-T01 或生产库，不 DROP/TRUNCATE/DELETE 业务行、表或 history。

已有 `ACCEPTANCE_JAR` 为分发验收包绝对路径，`TENSOR_DB_URL`/`TENSOR_DB_USERNAME`/`TENSOR_DB_PASSWORD` 按 runbook 隐藏输入并导出。若原构建产物已清除，由提供方按原 `mvn -f data-plane/pom.xml -Pacceptance clean verify` 重建、复制分发物；本任务不装配或改包。

另提供两个**测试工具输入**（不是产品配置）：

| 名称 | 规则 |
|---|---|
| `M14_MYSQL_DEFAULTS_FILE` | 权限 0600、当前用户所有、非符号链接的绝对普通文件，保存 MySQL CLI 的 `[client]` host、port、user、password、protocol=TCP。连接到与 JDBC 相同的服务器，管理员仅用于验证空库/结构和创建/移除本任务触发器，不传入 JVM。由运行者以隐藏交互输入或已有安全秘密管理方式创建，不在 shell 命令、argv、Git 或共享日志写密码。 |
| `M14_DB_SCHEMA` | 精确目标 schema，须匹配 `^tensor_m14_t02_[a-f0-9]+$`，并与 JDBC URL 的数据库名精确相等。检查 JDBC URL 无嵌入凭证且 CLI host/port 与 JDBC 主机/端口相同。 |

在任何 CLI 调用前，以内存读取并校验默认文件的严格语法：UTF-8、无 BOM，允许 LF/CRLF 和一个末尾换行；第一行必须恰为 `[client]`，随后恰好五行 `key=value`，key 为 `host`、`port`、`user`、`password`、`protocol` 且各出现一次，次序不限。不允许空行、注释、重复键、其他分组（包括 `[mysql]`）、额外键、`!include`/`!includedir` 或其他指令。host 只接受 ASCII 字母/数字/点/短横线且非空；port 为1～65535的十进制整数；protocol 恰为 `TCP`；user/password 为非空单行可打印 ASCII，排除所有空白、单/双引号、反斜线、`#` 和 `;`，因此没有引用、转义或行内注释歧义。按第一个等号拆键值，不对秘密 trim、解码、展开变量或回显。无法满足语法时明确报告默认文件格式不合格，由运行者准备符合此受限格式的专用验收凭证文件，不自动改写现有文件或修改账号密码。

CLI 固定 `spawn('mysql', ['--defaults-file=<绝对路径>', '--no-login-paths', '--host=<已验证host>', '--port=<已验证port>', '--protocol=TCP', '--batch', '--skip-column-names', '--raw', '--database=<已校验schema>'], {shell:false, env:{PATH:process.env.PATH, LANG:'C', LC_ALL:'C'}, ...})`；`--defaults-file` 必须是首个选项。`--no-login-paths` 显式禁止 `.mylogin.cnf` 等登录路径加载；严格文件语法禁止 include、额外客户端组和 init-command/defaults-group-suffix 等选项。CLI 环境只传上面三个键，不继承 `MYSQL_PWD`、`MYSQL_HOST`、`MYSQL_TCP_PORT`、`MYSQL_UNIX_PORT`、`MYSQL_HOME`、`MYSQL_TEST_LOGIN_FILE` 或任何其他环境覆盖。已核实的非秘密 host/port/protocol 在 argv 再显式指定，user/password 仅来自受限默认文件，不放 argv。CLI/JDBC host/port/schema 一致性与空库检查全部通过才允许故障 SQL；不能把单独 `--defaults-file` 视为连接隔离证明。

SQL 从 stdin 发送，不放 argv；每次调用 15 秒上限。只解析预定只读结果，不回显原 stdout/stderr；错误只报步骤名和退出码，敏感输出留在受限临时目录并在分享前扫描。测试不生成/修改用户默认文件；运行者在最终进程停止后清理本次创建的凭证文件和临时环境。管理员身份不注入应用。

`beforeAll` 在启动 JAR 前验证目录/JAR/工具/必填变量、CLI/JDBC 目标一致、MySQL 8.4、该 schema 初始表数为零、8080 无监听，以及 `PLAYWRIGHT_BASE_URL` 未设置或恰为 `http://127.0.0.1:8080`。缺项即失败，不打印秘密，不复用/停止未知应用。启动后只读确认 V1～V6 六条成功迁移、50 张业务表、fixture/daily 表为空、预留触发器名不存在。无法证明空库或目标一致不得注入故障。

测试拥有一个 JVM 和一个 Node HTTP server。JVM 固定参数：

```sh
java -jar "$ACCEPTANCE_JAR" \
  --spring.profiles.active=acceptance --tensor.plugins.fixture.enabled=true \
  --server.address=127.0.0.1 --server.port=8080
```

使用 `spawn`、`shell:false`、独立 `mkdtemp` 工作目录，日志 0600。沿用 M14-T01 环境净化，清除继承 `TENSOR_*`/`SPRING_*`/`SERVER_*` 后仅回填三个 DB 变量；另外由测试生成随机假 Token，仅在内存/子进程环境设置 `TENSOR_TUSHARE_TOKEN`，并将 `TENSOR_TUSHARE_BASE_URL` 设为本测试 server 的 `http://127.0.0.1:<动态端口>/`。不读取/使用调用者的真实 Token，不传递 MySQL 工具凭证/测试工具变量给 JVM，不覆盖任何 timeout 或日志级别。

先 `server.listen(0, '127.0.0.1')`，取得实际端口后启动 JVM。仅轮询根 health，每次 2 秒、总 90 秒，要求 200/UP；进程提前 close 或超时失败。`beforeAll` 上限 **300 秒**，包含 90 秒就绪失败后最多 150 秒正常退出和前置检查；`afterAll` 上限 **180 秒**，覆盖触发器清理、150 秒停机和 stub 关闭。保存本次 ChildProcess 和 socket 集合，失败也进入 finally。只对本次 JVM 发 SIGTERM、等待 exit/close 和 8080 关闭，超时报告非秘密 PID，不自动 SIGKILL；不批量杀进程。stub 等当前请求完成后正常关闭；对超时故障遗留的本测试 socket 可 destroy，释放自己的监听端口。异常清理不能掩盖原失败，两个失败均记录。

### 本机上游替身

只使用 `node:http` 与内存模式变量。仅接受 JVM 发到本机的 `POST /`，请求体上限 64 KiB。检查 JSON 键为 api_name/token/params/fields、api_name=daily、params=`{trade_date:'20260807'}`、fields 为下列逗号串、token 等于生成的假值；比较失败只输出布尔检查名，不转储 body。保存每模式请求计数及已收到信号，不保存 Token 或完整出站请求。未知模式/API/额外调用令测试失败，不自动回退成功。

daily 字段固定为 `[ts_code, trade_date, open, high, low, close, pre_close, change, pct_chg, vol, amount]`，正常 JSON 为 `{code:0,msg:null,data:{fields:<以上数组>,items:[['000001.SZ','20260807','11.23','11.23','11.23','11.23','11.23','0','0','0','0']]}}`。全部为本任务合成值。模式在页面点击前由测试内部切换；不提供 HTTP 控制端点，不用浏览器 route/fetch 代替动作。服务器不访问任何外部地址。

七类失败按矩阵产生；错误响应可包含固定假原文哨兵 `M14_T02_UPSTREAM_RAW_CANARY`，不拼入秘密。真实 read-timeout 模式收到请求后不发送任何响应，等待应用自身 120 秒读取上限结束；不能用返回 504 冒充超时，不能降低产品 timeout。network 模式返回 200、`Content-Length: 1000`，写一个 `{` 字节并在写回调中销毁本连接，制造已收到响应后的不完整传输 IOException；不能使用正常完成的畸形 JSON 冒充网络错误。每个正常或故障模式精确收到一次请求，timeout 结束后清理其 socket，拒绝隐式重复请求。

### 页面动作与共同断言

一个串行 describe，`retries:0`、普通用例默认 180 秒，恰 **15 个**用例按下表顺序；viewport 1440×1000，沿用 Chromium 项目。可以参数表生成最后七项，`--list` 必须逐项显示七个稳定标题。禁止 test.only/skip/fixme；首次失败后的串行未执行应如实报告，完整成功轮必须 15 passed、0 skip/retry。仅测试文件设置 trace=off、video=off、screenshot=off，截图由安全扫描后的显式 screenshot 生成，避免失败 trace 自动收集原文。

| 操作 | 精确公开选择器/值 |
|---|---|
| 入口与导航 | `/downloads` heading level1“数据下载”，`/datasets` heading level1“数据查看”；导航使用 link“数据下载”/“数据查看”。直接入口返回200；至少一次实际导航到查询页。 |
| 源/接口/数据集 | combobox 名称 exact `数据源`/`数据接口`/`数据集`；focus→Enter→可见 option.click。源 `Fixture` 或 `Tushare Pro`；接口/数据集 option `/Fixture 日线.*fixture_daily/`、下载接口与查询数据集均 `/^日线行情daily$/`；范围用 `/IPO 新股发行信息.*new_share/`。不使用 Element Plus CSS、force 或内部 Vue 状态。 |
| fixture 参数 | combobox `/场景/`，option exact 五值之一；默认 SUCCESS 通过打开下拉后的 selected option 验证，不读内部只读 input.value。 |
| 日期参数 | `getByLabel('交易日期', {exact:false})`、`getByLabel('开始日期', {exact:false})`、`getByLabel('结束日期', {exact:false})`；输入 `YYYY-MM-DD` 后 Tab 提交 picker 值；点击下载前核对显示值。 |
| 下载 | button exact“开始下载”；先登记唯一 POST `/api/v1/downloads` response promise 再点击；核对页面实际请求的身份/params。默认响应等待 15 秒，真实 timeout 项显式 135 秒。 |
| 查询 | 选择源/数据集后 getByLabel exact“证券代码 (ts_code)”填 `000001.SZ`，button exact“查询”；先监听对应 `/api/v1/data-sources/<plugin>/datasets/<api>/records` GET 再点击。无 SQL/API 替代。 |
| 全量不变对照 | 同样从页面选择数据集、保持全部筛选为空再点查询；响应 page1/pageSize50、totalElements1/totalPages1，唯一完整行与基线深比较。页面唯一业务 row 及所有 cell 文本同时核对，不能只比响应或证券代码。 |
| 成功/空/失败 | role=status 内 heading“下载成功”、term `[上游返回数,插入数,更新数]` 与 definition 计数；EMPTY heading“下载成功，0 条数据”/说明“本次请求没有可写入的数据。”；FAILURE role=alert 内 heading“下载失败”、精确安全摘要和“请求 ID：<id>”。 |

每个 POST 保存安全投影：场景名、HTTP、requestId、outcome 或 code/retryable、成功三计数；头 `X-Request-Id` 与 body requestId 精确一致且非空，不重复。错误 body 精确核对 code/message/retryable、fieldErrors=[]，不含成功计数或 EMPTY；alert 不显示 code、字段原文、堆栈或 SQL。重试按钮存在性按 retryable true/false 核对，**不点击重试**；下一项由新的明确页面动作发起。所有非预期业务 HTTP>=400、requestfailed、pageerror、其他写请求或非同源浏览器请求都失败；只允许当前预登记 POST 的特定 HTTP 错误，不能全局忽略 5xx。

每项下载前/结束后及 timeout 请求挂起时检查 role=progressbar 不存在，button/link 文本 `/取消|历史/` 不存在，页面不出现 `/下载中|适配中|入库中|百分比|进度/`。正常按钮 loading 是允许状态；timeout 在 stub 确认收到后断言“开始下载”仍原文、disabled、aria-busy=true，源/接口/参数不可编辑；结束后解锁。此等待利用 stub received promise，不用固定 sleep 或 `waitForTimeout`。

### 实际数据库写入故障

只在矩阵第7项，先由页面查回第3项第二次 SUCCESS 的完整 fixture 基线。固定临时对象名 `m14_t02_fixture_update_fail`，确认目标 schema 中不存在后，以管理员 CLI stdin 执行（`<schema>` 只能替换为前述严格验证的本次 schema）：

```sql
DELIMITER //
CREATE TRIGGER `<schema>`.`m14_t02_fixture_update_fail`
AFTER UPDATE ON `<schema>`.`fixture__fixture_daily`
FOR EACH ROW
BEGIN
  IF NEW.note = 'PERSISTENCE_FAILURE' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'M14_T02_FAULT_SQL_CANARY';
  END IF;
END//
DELIMITER ;
```

只读 information_schema.triggers 核对 schema、event=UPDATE、timing=AFTER、表名和 marker/SIGNAL 条件，证明注入已生效。随后从页面选 `PERSISTENCE_FAILURE` 并点击下载，期望500/PERSISTENCE_FAILED。现有业务键确定命中 UPDATE；AFTER UPDATE 的 SIGNAL 只对本次 note 标记生效，说明行更新已执行到触发器后才失败，不是插件拒绝或启动失败。MySQL 必须回滚该语句/事务。

在触发器仍存在时通过页面**无筛选查询**，要求仍恰一行，与第二次 SUCCESS 基线完全相等，尤其 note=null、amount 原值、ingested_at 原值，不能出现 marker 或其他部分行。保存失败响应与完整行对照的安全证据，并结合触发器定义/500与日志 persistence stage 证明已触发真实写故障及回滚。fixture 原场景只有一行；此项证明该实际单行更新事务回滚，不谎称执行过多行/多批次故障。

触发器创建成功后登记所有权，在该用例 finally 执行 `DROP TRIGGER <schema>.m14_t02_fixture_update_fail`；afterAll 只对已登记且尚未成功清理的对象补偿一次并检查其缺席。不得移除未知触发器或 schema/history/业务表。权限或 MySQL trigger/binlog 策略不足属于故障准备阻塞，不能改为假响应、在产品中识别 note、授予应用管理员权限或把合法 marker 的 SUCCESS 当写入失败。运行者应提供具备该独立库故障准备能力的管理员会话；测试不修改服务器全局策略。

## Files

- Create `control-plane/e2e/download-outcomes.spec.js`：上述15项矩阵、最小同文件辅助代码、JAR/stub/临时触发器所有权与安全投影。
- Create `docs/verification/M14-T02-download-outcomes.md`：实际版本、JAR SHA-256、输入准备方式、命令/退出码/用例数、每行结果、页面与响应对照、两个 SUCCESS 的时间及计数、故障准备/回滚/清理、按请求ID完成事件与秘密扫描结论、脱敏截图实际路径/哈希、缺陷/环境失败记录。

无修改或删除。两个新实施文件加入 Git，实现提交消息为 `test(e2e): verify download outcome matrix`；设计/看板/交接由工作流独立处理，不混入实施提交。本地日志、PNG、JSON、凭证和数据库不提交。

## Tests

### 精确矩阵

| # / 稳定标题 | 页面设置与故障准备 | 必须观察的结果 |
|---|---|---|
| 1 `blocksMissingRequiredDate` | Tushare Pro→daily，交易日期留空，点击开始下载。 | 日期 aria-invalid=true、获首错焦点、通过 aria-describedby 关联“此项为必填项”；按钮仍可用。观察整个用例0 POST、stub0调用，无结果成功/空伪反馈。 |
| 2 `blocksReversedDateRange` | Tushare Pro→new_share，开始2026-08-08、结束2026-08-07，Tab后点击。 | 只开始日期 aria-invalid=true、焦点与关联错误“开始日期不得晚于结束日期”；结束不重复范围错误；0 POST/stub0。 |
| 3 `upsertsDuplicateFixtureSuccess` | 页面无筛选初查 fixture 0行；Fixture→fixture_daily，SUCCESS连续两次，参数完全相同，两次之间页面查询保存第一行。 | 第一POST200/SUCCESS、1/1/0；第二200/SUCCESS、1/0/1；每次页面三计数匹配。第二次查询同业务键恰1行、七列顺序和值与M14-T01一致，只有 ingested_at 改变，第二UTC instant严格大于第一。两次操作间先等测试时钟超过第一时间至少1秒（expect.poll，最多5秒）以避免时间精度相同，不改时钟或SQL。第二行作为所有fixture失败对照。 |
| 4 `keepsRowsOnFixtureEmpty` | 同库Fixture→EMPTY。 | 200/EMPTY、0/0/0、既定成功空说明、无失败或重试；全量页面查询整行含时间与第3项基线相等，无占位行。 |
| 5 `showsFixtureSourceFailure` | Fixture→SOURCE_FAILURE。 | 502/SOURCE_UNAVAILABLE、message=`Source is unavailable`、retryable=true；全量页面查询基线完全不变，stub0。 |
| 6 `rejectsFixtureTypeFailure` | Fixture→TYPE_FAILURE，真实amount=`not-a-decimal`由既有场景产生。 | 422/ADAPTER_TYPE_INVALID、`Source data contains an invalid value`、false；全量页面查询原行不变，无部分行，界面/响应不包含not-a-decimal或内部field/row异常文本。 |
| 7 `rollsBackFixturePersistenceFailure` | 按上一节创建AFTER UPDATE触发器→Fixture PERSISTENCE_FAILURE→页面查询→finally移除触发器。 | 500/PERSISTENCE_FAILED、`Persistence failed`、true；整行/总数/入库时间不变、marker不入库、不泄漏SQL哨兵，日志failureStage=persistence。 |
| 8 `downloadsDailyFromLocalUpstream` | 页面初查daily0行；stub正常JSON→Tushare Pro→daily，日期2026-08-07。 | POST params trade_date='20260807'，200/SUCCESS、1/1/0、stub1。按证券代码页面查得唯一daily行，11业务列+source_plugin/source_api/ingested_at顺序正确；来源tushare_pro/daily，日期2026-08-07、前五金额11.230000000000000000、后四数值0.000000000000000000。时间为有效带偏移UTC instant，页面按Asia/Shanghai独立Intl转换相等；保存完整daily基线。 |
| 9 `showsSourceAuthFailure` | daily同参数，stub HTTP401，body含原文哨兵。 | 502/SOURCE_AUTH_FAILED、`Source authentication failed`、false。 |
| 10 `showsSourcePermissionFailure` | 同上，HTTP403。 | 502/SOURCE_PERMISSION_DENIED、`Source permission denied`、false。 |
| 11 `showsSourceRateLimitFailure` | 同上，HTTP429。 | 502/SOURCE_RATE_LIMITED、`Source rate limit exceeded`、true。 |
| 12 `showsSourceUnavailableFailure` | 同上，HTTP503。 | 502/SOURCE_UNAVAILABLE、`Source is unavailable`、true。 |
| 13 `showsSourceNetworkFailure` | 同上，stub按前述固定长度未完整传输后断连。 | 502/SOURCE_NETWORK_ERROR、`Source network request failed`、true；浏览器业务POST有正常API错误响应，不是浏览器网络错误。 |
| 14 `showsSourceTimeoutFailure` | 同上，stub收请求后持续不响应直到真实应用超时。 | 504/SOURCE_TIMEOUT、`Source request timed out`、true；前端在130秒超时前收到后端结构化错误（response等待135秒、用例180秒）。挂起时检查控件锁定与无进度/取消/历史，结束恢复；记录实际耗时，不用短定时假装120秒超时。 |
| 15 `showsSourcePayloadFailure` | 同上，stub HTTP200/application-json，返回`{code:0,msg:'M14_T02_UPSTREAM_RAW_CANARY',data:{fields:['trade_date','ts_code'],items:[]}}`，fields故意不符合daily。 | 502/SOURCE_PAYLOAD_INVALID、`Source returned an invalid payload`、true，不能显示合法EMPTY。 |

第9～15项各自必须 stub恰1调用、唯一页面POST、结构化错误、安全alert、与retryable一致的重试按钮，并在错误后页面**全量查询**daily，与第8项基线完整深比较（含ingested_at），证明每类失败未写库。不将第8项已存在行误判为失败产生的数据。第3～7项全部列按M14-T01七列合同核对；独立 `Intl.DateTimeFormat` 校验时间显示，DECIMAL比较完整文本，null显示`--`。

完整矩阵恰 **14次页面下载POST**、**8次本机上游调用**（1成功+7失败），客户端两项各0。查询数以本文件实际流程记录，所有页面records请求都关联到自己的响应ID和唯一query完成事件。SQL只允许上文环境只读验证、触发器故障准备和清理。

### 日志与脱敏证据

测试从本次0600应用日志按已捕获requestId检查：每个14个POST恰一个 `tensor.operation.completed operation=download`；成功/空 outcome为success/empty、failureStage=none、三计数与响应相同，失败outcome=failure、errorCode与响应相同，source/adapter/persistence阶段匹配，sourceRowCount=unavailable（失败不捏造计数）。同一请求允许既有全局handler的脱敏诊断日志，不能把它当第二个完成事件。页面records GET各有一个operation=query完成事件；metadata/health与客户端未发请求不要求业务完成事件。

在日志落盘后用有界expect.poll（最多5秒）等目标事件出现，按完整requestId字段匹配而非子串，确认每个已知请求恰一次。保留每个请求的白名单字段投影和计数，不分享原日志/原堆栈。最终停机后再次核对总事件数，防止延迟重复日志。

脱敏按表面分开，保持 M09-T06 的公开边界：

- 所有代码/证据、本轮页面可见文本、API/health响应、私有应用日志及待分享JSON/PNG前置文本，均不得包含生成的假Token、应用DB密码或工具凭证密码字面值。不得打印命中内容，布尔失败和文件路径足够。
- 私有应用日志不得包含完整上游响应、上游原文哨兵、SQL故障哨兵或原始SQL语句。按请求ID的完成事件只允许上文白名单字段。原有启动/Flyway/连接池日志可以出现不含秘密的 JDBC 连接标记或URL；**不对整份私有应用日志禁止 `jdbc:mysql`，不把既有正常启动连接信息判为产品失败**。URL若含密码/Token仍按秘密规则失败；该允许不适用于业务完成事件或公开表面。
- 页面、API/health响应和共享证据/JSON/截图不得出现 JDBC URL（包括 `jdbc:mysql` 标记）、实际数据库账号/连接配置值、完整上游响应、原文哨兵、SQL故障哨兵或原始SQL。共享证据只记录本文允许的业务结果/事件投影，不能直接复制私有启动日志。未脱敏本地管理记录不属于共享证据。

代码中的非秘密固定测试哨兵/触发器DDL本身是输入定义，不能对测试源码机械禁止这两个固定字面值；禁止它们泄漏到页面、API响应、app日志与证据投影。数据库管理命令本地记录与应用日志分开，不把注入SQL当成产品日志泄漏。测试代码/共享证据也不得硬编码任何实际DB账号、密码或连接配置；非秘密输入名称和通用命令占位符不算配置值泄漏。

截图只取已扫描安全的结果panel和页面行，最终逐张人工查看后记录路径与SHA-256；受限本地证据只保存公开合成业务值、结果、安全requestId和白名单事件，不保存完整上游响应、原始请求、环境或管理员输出。任何秘密泄漏为失败，删除/隔离含秘密的待分享产物并记录安全失败结论，不通过脱敏把产品泄漏改写成通过。

### 执行命令与预期

环境准备完成、8080空闲、JAR变量为绝对路径后：

```sh
cd control-plane
node --check e2e/download-outcomes.spec.js
npx playwright test e2e/download-outcomes.spec.js --list
npx playwright test e2e/download-outcomes.spec.js
npm run test:unit -- --run
```

依次预期语法退出0、发现15个Chromium用例、**15 passed / 0 failed / 0 skipped / 0 retry**且所有清理成功、既有前端120/120。若缺浏览器先执行既定 `npx playwright install chromium`，不硬编码本机Chrome路径。证据记录开始/结束时间、各项实际耗时（特别120秒读取超时）、Java/MySQL服务器/Node/npm/Playwright/Chromium版本与命令退出码。

先写完整矩阵，再执行；这是一项针对已完成产品的黑盒验证，不制造产品RED或改JAR。任何失败先区分环境准备、测试错误、产品结果；修复测试错误后从新空schema重跑完整15项。若产品缺陷，保留失败断言及安全证据，等待独立修复后重跑精确失败项（准备所需页面基线）和全矩阵。

根目录提交前：

```sh
git diff --check
git diff --quiet -- data-plane control-plane/src control-plane/package.json control-plane/package-lock.json control-plane/playwright.config.js control-plane/e2e/fixture-flow.spec.js docs/contracts docs/runbook
git status --short --untracked-files=all
git add control-plane/e2e/download-outcomes.spec.js docs/verification/M14-T02-download-outcomes.md
git diff --cached --name-status
git diff --cached --check
```

格式/受保护路径无差异；实施暂存精确两个100644新增文件，不能混入构建target或控制器负责的工作流文档。提交后确认两文件范围与固定消息。验收证据不得填入预期结果冒充实际结果。

## Acceptance

- 两个新增实施文件完整可复现上述15项；使用原验收JAR、真实MySQL和本机stub，全部下载/查询均来自页面。
- 客户端缺必填/逆序范围定位错误字段且0 POST；SUCCESS/EMPTY及10项失败请求各呈正确独立状态，七类SOURCE错误逐项分辨，无安全摘要泄漏。
- 相同fixture SUCCESS请求两次后恰一业务行、第二次1/0/1、入库时间严格更新；所有失败/EMPTY的完整行和时间不变，来源字段正确。
- PERSISTENCE_FAILURE经已核实AFTER UPDATE/SIGNAL真实写入故障，500/persistence完成事件与页面完整基线不变共同证明回滚；临时触发器已移除，未破坏业务数据/history。
- 14个页面POST/8个stub调用与每个请求恰一个完成事件相符；timeout采用原120秒上限且收到真实504，未出现进度/取消/历史UI。
- Playwright15/15、前端120/120、范围/格式、脱敏、截图人工核对与本次JVM/stub/trigger清理全部通过；无未解决产品缺陷、伪成功或测试跳过。

## Risks

- 实际120秒读取超时使该矩阵比M14-T01慢；必须使用本文用例/钩子预算，不能缩短产品超时或用HTTP状态替代网络故障。
- MySQL触发器创建可能受管理员权限及服务器binlog策略限制；这是明确的环境前置条件，不是产品改造入口。该能力缺失时保留具体阻塞事实，不能宣称回滚项已通过。
- fixture写入场景固定单行；本任务证据只证明其实际更新事务的回滚，不扩大为多批次或真实Tushare全量验收。
- 无未解决的需求或接口裁决；本设计尚未执行新的矩阵，结果级产品/环境失败只能由实施时真实运行确认。

### 实施时选择器勘误（2026-09-05）

首轮真实 JAR 的公开可访问快照显示下载接口 option 为 `日线行情daily`（标题与 code 连续文本），不是带括号的文本；下拉框已正常展开。第四轮查询页的公开快照同样确认其 option 为 `日线行情daily`，因此下载接口与查询数据集均修正为上述精确名称。此项是测试定位与实际公开组件的一致性修正；API 身份 `daily`、日期参数、结果及全部验收断言保持不变，不修改产品。首次失败发生在第一个业务 POST 前（0 POST），不作为产品缺陷或通过证据。
