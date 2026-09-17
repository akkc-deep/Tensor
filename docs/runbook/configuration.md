# Tensor 运行配置

按 [首次运行说明](first-run.md) 完成 schema、应用账号授权、环境注入、启动和检查。以下名称对应当前分发 JAR 的配置入口，无需修改源码。

| 名称 | 是否必填 | 默认值 | 用途 | 暴露边界 |
|---|---|---|---|---|
| `TENSOR_DB_URL` | 是 | 无 | MySQL JDBC URL；本地例子为 `jdbc:mysql://127.0.0.1:3306/tensor`。 | 只在后端配置；不嵌入密码，生产数据库传输按部署要求使用 TLS，不打印 URL 排障。 |
| `TENSOR_DB_USERNAME` | 是 | 无 | 对目标 schema 授权的应用账号。 | 只在后端环境或外部只读配置；不使用管理员账号运行应用。 |
| `TENSOR_DB_PASSWORD` | 是 | 无 | 应用数据库账号密码。 | 仅后端环境或外部只读配置，禁止放入 argv、日志、响应或版本控制。 |
| `TENSOR_TUSHARE_TOKEN` | 仅下载需要 | 空 | 缺少时数据源列表、数据集元数据及数据查看仍可用；下载接口列表返回 409，下载页可显示配置不可用提示，下载不可用。 | 只返回 `credentialConfigured` 等配置状态，绝不返回 Token 值。 |
| `TENSOR_TUSHARE_ENABLED` | 否 | `true` | 是否注册并展示 Tushare 插件。 | 后端非秘密开关；首跑保持默认，smoke 要求存在 `tushare_pro` 条目。 |
| `TENSOR_TUSHARE_BASE_URL` | 否 | `https://api.tushare.pro` | 上游地址；首跑四项 GET 无需访问上游。 | 后端配置，不在 URL 中携带凭证。 |
| `TENSOR_DISPLAY_ZONE` | 否 | `Asia/Shanghai` | 入库时间的显示时区。 | 后端非秘密配置。 |
| `TENSOR_DEV_CORS_ALLOWED_ORIGIN` | 否 | 空 | 仅开发，例子为 `http://127.0.0.1:5173`。 | 生产同源保持空；只能允许一个精确 origin，不能充当认证。 |

首跑命令中的 `--server.address=127.0.0.1` 和 `--server.port=8080` 是 Spring Boot 的非秘密运行参数，没有新增 Tensor 自定义环境变量。默认示例只绑定本机回环。

缺少 Token 时，`/api/v1/data-sources/tushare_pro/apis` 返回 HTTP 409、`PLUGIN_DISABLED`，下载页可显示“下载配置加载失败 / Plugin is unavailable”；这是配置不可用的预期提示，不妨碍数据源列表、数据集元数据及数据查看。

## 后台下载任务

当前 JAR 使用以下 Spring 属性。未覆盖时采用表中的默认值；除 `enabled` 外，任务限制必须为正数，`max-run-duration` 还必须为可转换为纳秒的正时长，非法值会使应用启动失败。

| 属性 | 默认值 | 运行含义 |
|---|---:|---|
| `tensor.download-tasks.enabled` | `true` | 允许新任务接收、手动控制和 worker 派发。设为 `false` 时仍执行启动恢复并保留历史查询，但不接收、retry、resume 或派发任务。 |
| `tensor.download-tasks.max-queued-tasks` | `100` | 当前排队任务容量；等价 submission 的查询/重放不新建任务。 |
| `tensor.download-tasks.max-range-days` | `36600` | RANGE 请求允许的最大自然日跨度。 |
| `tensor.download-tasks.max-batch-nodes` | `10000` | 一个任务允许持久化的叶子和拆分节点总量上限。 |
| `tensor.download-tasks.max-requests-per-run` | `5000` | 每轮 worker 获得的来源请求许可上限。 |
| `tensor.download-tasks.max-run-duration` | `30m` | 每轮 worker 的截止时长；截止后不再取得新许可。 |
| `tensor.download-tasks.max-source-rows-per-task` | `1000000` | 一个任务跨轮次累计成功提交的 `sourceRows` 上限。 |

任务由单个应用实例、一个 worker 串行领取。HTTP `202` 只确认任务及 submission 身份已经持久化，不表示来源请求或数据写入成功；响应的 `Location`、详情 URL 和近期任务列表用于重新找到任务。SINGLE 只代表一次当前参数请求，不代表完整历史；RANGE 成功只证明已保存计划所覆盖的请求范围完成。NATIVE_RANGE 仍可能动态拆分，所以最终叶子数应以任务详情和批次列表为准。

`requestCount` 是任务跨轮次取得的来源请求许可数，`runRequestCount` 是当前一轮的许可数；`sourceRows` 是成功批次返回并提交的来源行数，`insertedRows` 和 `updatedRows` 是已提交写入操作数。重试、更新或同一业务键跨批出现时，写入操作数不等于最终表净增行数。

正常停止或重启不会自动重发未完成来源请求。启动恢复会保留历史和普通失败，并将未完成工作置为需要人工判断的中断状态；`retry` 重排失败批次并继续尚未执行的工作、保留成功结果，`resume` 只继续因中断而未完成的批次并保留普通失败和成功结果。两者都必须提交详情中的当前 `expectedVersion`，并再次校验当前数据集定义、插件可用性和容量；定义变化时拒绝继续。暂时的详情查询失败不会把任务改成 FAILED，历史查询也不依赖当前插件仍注册。根 health 为 UP 只证明其健康合同，不表示 Token 已配置或某个 RANGE 已开放。

Tushare 客户端还使用下列固定属性；同一客户端上的旧同步入口和后台任务入口共享最小请求间隔。

| 属性 | 默认值 | 约束 |
|---|---:|---|
| `tensor.plugins.tushare-pro.connect-timeout` | `5s` | 必须为正。 |
| `tensor.plugins.tushare-pro.read-timeout` | `120s` | 必须为正且不超过 120 秒。 |
| `tensor.plugins.tushare-pro.max-response-bytes` | `67108864` | 1～67108864 字节。 |
| `tensor.plugins.tushare-pro.min-request-interval` | `1500ms` | 非负且必须可转换为纳秒。 |

## 本地数据完整性检查

页面入口为 `/integrity`（创建与历史）和 `/integrity/checks/:checkId`（保存的报告）。选择数据源、股票、日期及接口，确认口径后开始；未下载过的合法股票也可以检查。报告中的“计算已完成”只表示执行结束，数据结论仍可为 FAIL 或 UNKNOWN。再次检查复制原范围，重新确认当前规则并创建新报告，旧报告保持不变。

| 方法与路径 | 用途 |
| --- | --- |
| `GET /api/v1/data-sources/{pluginId}/integrity-capabilities` | 本地能力、日期轴、规则版本与限额。 |
| `POST /api/v1/integrity-checks` | 首次202；相同submissionId与请求重放200，返回原checkId。 |
| `GET /api/v1/integrity-checks` | 历史；支持pluginId/status/submissionId筛选。 |
| `GET /api/v1/integrity-checks/{checkId}` | 原范围、执行进度与结论。 |
| `GET /api/v1/integrity-checks/{checkId}/results` | 各股票与接口结果。 |
| `GET /api/v1/integrity-checks/{checkId}/issues` | 完整业务键、问题主日期与依据；日期筛选排除日期未知项。 |

列表默认page=1/pageSize=20，pageSize最大100。计数用十进制字符串，无法计算用null；覆盖率为六位十进制字符串，不提供跨接口总百分比。完整请求与响应见[公开合同](../contracts/openapi-v1.yaml)。

`tensor.integrity` 由应用启动时绑定。以下值必须全部为正，`workers` 必须等于 1，非法值阻止启动；没有自动回退或裁剪范围。

| 属性（前缀 `tensor.integrity.`） | 默认值 | 含义 |
| --- | ---: | --- |
| `max-symbols` | 100 | 插件规范化并去重后的股票数。未下载过的合法股票仍保留。 |
| `max-range-days` | 36600 | 起止日期闭区间自然日数。 |
| `max-units` | 4000 | 股票级/缺描述接口按股票计划；NON_STOCK 每接口一条。 |
| `queue-capacity` | 20 | 已预留或已排队任务数；消费者取出后释放名额。 |
| `workers` | 1 | 首版固定单执行线程。 |
| `scan-batch-size` | 500 | 单批读取行数。 |
| `max-scanned-rows-per-unit` | 500000 | 单元累计扫描及生成预期键预算。 |
| `max-issues-per-unit` | 20000 | 单元问题预算。 |
| `unit-timeout-seconds` | 120 | 单元执行时间预算，单位秒。 |
| `task-timeout-seconds` | 1800 | 从任务运行开始计算的时间预算，单位秒。 |

受理计数等于上限允许，超过时返回 `INTEGRITY_LIMIT_EXCEEDED`；队列满为可重试的 `INTEGRITY_QUEUE_FULL`，不留下新任务。写入任务与完整计划的事务失败会释放预留名额；提交成功后才发布到独立检查队列。

同一 `submissionId` 与原请求重放在当前插件能力、日期和队列检查之前返回原任务，不重复入队；不同原请求为 `SUBMISSION_CONFLICT`。首次提交核验全插件 `capabilityHash`，规则或接口定义改变为 `INTEGRITY_DEFINITION_CHANGED`。本地能力不要求上游 Token，不筛除本地无记录股票，不联网。Tushare 的 `endDate` 不得晚于受理时刻对应的上海日期。

应用使用独立的单个 `tensor-integrity-worker`。以上扫描、问题和时间预算均用于运行；排队时间不计入任务预算，计数等于上限允许，时间达到 deadline 即停止。每单元在独立的只读一致快照中执行，报告和问题在读取结束后原子保存；进度只统计已提交的 COMPLETED/ERROR 单元。数据 FAIL 与任务运行完成分别展示，不提供跨接口总覆盖率。

启动时先将遗留 QUEUED/RUNNING（含提交后未入队）置为 INTERRUPTED，剩余单元为 NOT_RUN，再开放首次受理。停止时关闭首次受理、等待在途提交和执行退出、处理剩余队列；已提交报告保留。启动/关闭期间相同原请求仍可幂等重放。不会自动重算旧快照，需重新发起检查。取消是规则/扫描边界的协作检查，不强制终止不遵守上下文合同的第三方代码。HTTP与页面均使用已保存的报告；报告长期保留，没有自动清理或删除入口。

验收专用 fixture 只在 acceptance profile 且 `tensor.plugins.fixture.enabled=true` 时注册。`tensor.plugins.fixture.integrity-version` 仅接受 `2`（默认）或 `3`；版本3在相同合成集合上增加一条验收 FIELD 规则，以验证新旧报告各自保存依据。其他版本阻止启动，生产JAR不包含fixture。可靠窗口只用于验收，不能当作Tushare生产基线。

受控真实浏览器回归通过 `python3 scripts/verify-integrity-fixture.py --acceptance-jar /绝对路径/到/本次acceptance.jar` 运行。启动器创建并清理本次专用MySQL容器与五个独立schema，串行执行完整性及既有打包套件；需要Java21、Node24.15.0、MySQL客户端、Docker与空闲的本机8080端口。真实与stub结果分别记录于[T13验收](../verification/DATA-INTEGRITY-T13.md)。

## 已核验的 Tushare 日期区间

2026-09-13 首批核验的下列四项，均要求一只股票及起止日期，版本为 `tushare-range-v2`，按闭区间 `trade_date` 规划原生RANGE。部署后以实际包的能力接口和下载页为准；该源码验收不表示已发布到外部环境。

| 接口 | 原始响应行数上界 | 新干净SOURCE证据 |
| --- | ---: | --- |
| `daily_basic` | 6000 | `issue018-t14-priority-source-20260913T092938Z-daily_basic-range`及两股票两端/跨年 |
| `stk_limit` | 5800 | `issue018-t14-priority-source-20260913T092938Z-stk_limit-range`及两股票两端 |
| `moneyflow` | 6000 | `issue018-t14-priority-source-20260913T092938Z-moneyflow-range`及两股票两端 |
| `margin_detail` | 6000 | `issue018-t14-priority-source-20260913T092938Z-margin_detail-range`及两股票两端 |

原始行数小于上界才可认定该叶子完整；等于或超过上界继续按日期拆分，最小单日仍满额则报告完整性未确认，不把部分结果算作完整成功。官方依据与完整case引用见 [40项验收](../verification/ISSUE-018-range-acceptance.md)，25项真实RANGE TASK/SQL见 [T14运行](../verification/ISSUE-018-T14-runs.md#四接口-range-task-实际结果)。

2026-09-15本次交付范围为30个RANGE接口、251项已通过TASK/SQL验收；balancesheet、cashflow、repurchase、fina_indicator按[用户决定](../issues/proposals/ISSUE-026-range-scope.md)排除本次区间批量下载。正式处置为30 AVAILABLE、4 EXCLUDED、6 SINGLE_ONLY；原27项失败/未运行及问题保留，不计PASS。四接口RANGE仍为v3 / NEEDS_VERIFICATION并拒绝提交，SINGLE入口保留。032最终回归与母任务收尾已完成，详见[最终验收](../verification/ISSUE-032-range-final-closure.md)；该结论不表示已部署。

当前构建能力为30 AVAILABLE、4 NEEDS_VERIFICATION、6 UNSUPPORTED，其中四项NEEDS_VERIFICATION对应本次EXCLUDED范围；六项UNSUPPORTED只限制RANGE，仍支持SINGLE。十一项RESPONSE_ONLY的采集合同只覆盖本次响应，完整性未确认；其中三个冲突接口保持撤回。BJ top_list保留BJ证券并参照SSE日历，直接trade_cal BSE仍拒绝；margin支持三exchange_id。配置Token不会绕过准入。版本变化后旧任务若返回TASK_DEFINITION_CHANGED，应核实原任务口径，不能盲目retry/resume。


## 秘密注入

数据库账号采用 `read -r TENSOR_DB_USERNAME` 输入，密码采用关闭终端回显后读取并导出的方式，完整命令及恢复回显 trap 见 [首次运行的环境注入](first-run.md#3-注入环境)。默认显式 `unset TENSOR_TUSHARE_TOKEN TENSOR_DEV_CORS_ALLOWED_ORIGIN`；若现有 shell 已设置二者，先确认是否应该保留，不要直接打印变量内容。

需要下载时，在首次运行的专用 shell 中、启动 JAR **之前**执行以下代码。它使用相同的隐藏输入方式，并重新安装退出和信号处理，以保证中断后恢复终端：

```sh
set +x
tensor_tty_state=$(stty -g) || exit 1
trap 'stty "$tensor_tty_state"; unset TENSOR_DB_PASSWORD TENSOR_TUSHARE_TOKEN' 0
trap 'exit 1' HUP INT TERM
printf 'Tushare Token（隐藏输入）: '
stty -echo || exit 1
IFS= read -r TENSOR_TUSHARE_TOKEN || exit 1
stty "$tensor_tty_state" || exit 1
printf '\n'
export TENSOR_TUSHARE_TOKEN
```

环境变量在进程启动时读取，变更后应正常停止再启动。不要启用 shell 跟踪、终端录制或秘密调试日志；不要将密码或 Token 放在 `java -D`、URL、命令行参数、响应、截图或版本控制文件中。生产部署平台可以通过环境或外部只读配置注入，本说明不要求具体平台配置。

smoke 只检查指定敏感键/头、JDBC 标记及调用者提供的两个非空秘密字面值；没有提供给脚本的任意秘密无法据此保证被检测到。它不会打印响应，也不替代完整 JSON 合同或发布安全验收。

## 同源访问和开发 CORS

生产由一个 JAR 提供 Vue、静态资源和 `/api/v1`，默认不注册 CORS。开发 origin 只作用于 `/api/v1/**`，允许 GET、POST、OPTIONS；请求头只允许 `Content-Type`、`X-Request-Id`，响应暴露 `X-Request-Id`、`Location`，`credentials=false`。UI、assets 和 Actuator 没有开发 CORS 映射。

未设置、空字符串、纯空白、逗号列表、末尾斜杠均不开放 CORS；精确 `*` 会导致启动失败。其他不匹配浏览器 Origin 的值不会获得允许响应，不能通过 wildcard 或关闭安全控制解决错误配置。CORS 不提供认证；公网 TLS 和访问控制边界由部署入口承担。

## 超时和停机

| 位置 | 当前约束 | 运行含义 |
|---|---|---|
| Tushare 客户端 | connect 5 秒、read 120 秒 | 同步上游连接与读取上限。 |
| 前端请求 | 130 秒 | 大于上游读取上限。 |
| 部署代理响应 | **至少 130 秒** | 保持 `120s < 130s <= proxy`；组织已有代理必须满足此约束。 |
| 写事务 | 60 秒 | 当前写事务 timeout。 |
| 后台任务每轮 | 30 分钟 | 到达截止时间后不再取得新来源许可；已许可且通过事务边界检查的写入可完成。 |
| Spring graceful shutdown | 每阶段 70 秒 | Web 阶段覆盖 60 秒写事务并留余量，不是 JVM 总停机期限。 |

旧同步 Servlet 请求没有本任务可配置的独立应用处理 timeout，连接 timeout 或异步 MVC 参数不能代替它。后台任务的每轮截止与旧同步 HTTP timeout 是不同边界；截止或停止后不再取得新许可，但已经许可且进入受保护事务的操作仍按事务结果结束。smoke 的每项检查连接上限 5 秒、总上限 15 秒，只约束相应的轻量 GET 检查，不改变应用或下载超时。

正常停止采用 Ctrl-C 或向已核实的应用 PID 发送 SIGTERM，并等待 JVM 自行退出，不常规使用 SIGKILL。首跑不设置外部强杀倒计时；部署管理器的终止窗口必须包含全部实际停机阶段与清理时间，不能只设为 70 秒。上游读取最长 120 秒而 Web 停机阶段为 70 秒，不能承诺每个完整下载请求均在停机期间完成，未提交事务由数据库回滚。关闭浏览器不会取消后台任务，70 秒也不保证任意插件立即退出。操作步骤见 [正常停止](first-run.md#6-正常停止)。

## 健康和缓存

生产默认只暴露 health 家族，禁用 Actuator discovery；无需开放 `env`、`configprops`、`metrics`。根 `/actuator/health` 包含数据库检查，必须达到 HTTP 200 且根状态 UP 才能开放流量；`/actuator/health/readiness` 只作为辅助，不替代根检查。Token 的配置状态与数据库健康独立。

根 health、liveness、readiness 默认只返回 `status`，不公开组件、分组或详情，组件子路径返回 404。数据库中断时根 health 返回 HTTP 503 和 `{"status":"DOWN"}`；探针保持自身的存活/接流量语义。

`/`、`/index.html`、`/api/**` 和 Actuator 使用 `no-store`；`/downloads`、`/datasets` 等 UI fallback 保留 `no-cache`；`/assets/**` 使用 `public, max-age=31536000, immutable`，即一年 immutable 缓存。UI 可以直接刷新，未知 API 或文件资源仍应返回其真实错误状态。

## 数据库权限与版本维护

首次运行创建 `utf8mb4` / `utf8mb4_0900_as_cs` 的 `tensor` schema，并仅向匹配实际 JDBC 客户端来源的应用账号授予 `tensor.*` 上 CREATE、SELECT、INSERT、UPDATE、ALTER、INDEX、REFERENCES；管理员通过 `SHOW GRANTS` 检查。当前生产支持 40 个 Tushare 数据集；自动迁移为 V1～V5、V7、V8、V9，共八次、54 张业务表。49 张证券来源/历史表中有 9 张已下线接口遗留表；V8 另建 `tensor_download_task` 和 `tensor_download_batch`，保存任务身份、状态、计划叶子、计数和固定错误。V9 新增 `tensor_integrity_check_task`、`tensor_integrity_check_result`、`tensor_integrity_check_issue`，保存固定检查范围、历史报告和问题明细，不改证券表。报告长期保留，不自动清理；完整性任务受理、后台执行、启动中断、HTTP与页面均已装配。生产 schema 共 1103 个物理列、54 个主索引和 56 个非主索引；证券业务列与任务字段分别解释。Flyway history 另计，不启用 fixture 或测试 V6；验收/测试库存为九次迁移、55 张业务表、1110 个物理列、55 个主索引和 56 个非主索引。ALTER、INDEX 用于 V7，REFERENCES 用于 V8 批次外键及 V9 报告归属外键；不授予 DROP、DELETE 或全局权限。已有库须先停止所有写入者并验证备份；新包迁移、schema 校验和 health 通过前保持停写。旧 V7 包不兼容 V8 任务 schema 的当前行为，失败或回退按[升级说明](first-run.md#v8-任务基础设施升级)处理，不自动 repair 或只回退 JAR。

发布前使用管理员或备份账号，将交互密码的 `mysqldump --single-transaction --no-tablespaces --set-gtid-purged=OFF` 备份写入新建的权限受限唯一目录，避免覆盖，并在独立环境验证恢复。完整示例见 [备份与回退](first-run.md#7-备份与回退)。Flyway 只前向，不运行 clean、不删 history、不执行逆向/破坏性 DDL。上一应用版本必须兼容当前 schema 才能回退；删除/缩窄字段先兼容再清理，误写恢复依赖已验证备份。
