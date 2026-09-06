# M14-T07 Token、SQL、依赖、网络和运行安全验证——任务设计

任务编号：`M14-T07`。权威来源：[任务看板](../task-handoffs/tensor-v1/tensor-v1-task-board.md) Order 78 与[任务卡](../superpowers/plans/tensor-modules/M14-integration-release.md#task-m14-t07-安全与运行控制验证30h)。本设计供实施前确认；任务状态和批准证据仅记录在看板。

## Goal

用一个可重复执行的发布检查入口，证明当前生产 JAR 的凭证隔离、只读查询、SQL 输入边界、HTML 文本呈现、安全响应头、依赖风险和运行暴露面符合 TRD 11.3、16 与 PRD 10.3。输出逐项、可追踪且经过秘密扫描的实际证据。任一必需检查失败或未完成，都不能报告安全门禁通过。

## Scope

实施只新增 `scripts/security/verify-release.sh` 和 `docs/verification/M14-T07-security.md`；前者编排既有 Maven 测试、依赖扫描、生产包黑盒 HTTP/Playwright 验证及清理，后者记录结果。辅助 Python/Node 程序由脚本写入本轮私有临时目录，不新增永久 helper、Playwright spec、依赖或配置。

使用已打包的生产 JAR、假 Token、自有临时 MySQL 8.4.6 和回环合成上游；业务下载、查询及 HTML 呈现从页面操作。直接 HTTP 安全反例和数据库只读核对属于验证证据，不替代页面闭环。不调用真实 Tushare，不消费继承的真实 Token，不复跑历史真实验收控制器。

不阅读或修改 M00～M13 生产实现；源码扫描只按字节寻找本轮 canary，不解释 Java/Vue/YAML/SQL 内容。不得修改生产代码、POM、package/lock、现有 E2E、manifest、历史证据或原 JAR。允许阅读公开契约、运行说明、既有测试和构建配置，允许在临时源码快照运行既有门禁。

M14-T05 只提供已成立的安全运行输入；原 49 接口缺口由 ISSUE-008 保留，不作为本次独立安全检查的启动前提。ISSUE-009 的性能缺口、其他未解决问题和 M14-T08 的发布准入均不在本任务内；本任务通过不能使发布就绪。

## Approach

### 方案选择与文件职责

采用任务卡规定的单一 shell 入口，在内部运行最小 Node 浏览器驱动和 Python ZIP/报告检查。复用旧 E2E 的公开接口及所有权规则，不导入会自动注册历史用例的 spec。另两种方案分别是只汇总旧测试（缺少当前生产包的黑盒证据），以及新增常驻安全测试服务/公共 helper（超出两文件边界）；均不采用。

### 输入与身份

- 命令入口为仓库根的 `sh scripts/security/verify-release.sh`，仅另提供 `--self-test` 离线反例模式；不提供跳过门禁、接受漏洞或任意目标 URL 参数。
- `M14_SECURITY_JAR` 必填、绝对路径、当前用户可读的普通文件，拒绝符号链接。固定生产包 SHA-256 为 `acbba3d2d0f240a31b526560e80d217f274d432518f96a07459ba9d44d3467ef`，已有位置是 `/private/tmp/tensor-m14-t09-green.MZ4kMkN9/data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar`。可复制到其他绝对路径，但内容必须相同；不自动用 acceptance 包或新构建包替代。
- 同批 acceptance 包 SHA `81adba0dd6500f4aa43b4fa06b18c2c8e7b7454d9e6d4d6c734772cdaef1d002` 仅用于理解历史证据，不作为生产验证目标。生产包预检要求 V1～V5、V7，49 份 Tushare YAML，三个 Tensor 生产模块，无 fixture 模块、V6 或测试资源。
- 运行 Java 21、Node `>=24.15.0 <25`、Maven 3.9.x、npm 11.x、Python 3、Docker 与已有 Playwright 1.62.1 Chromium。预检记录实际版本、JAR/脚本/lockfile/源码提交 SHA；Node 22 不能执行本任务。缺少工具或已安装浏览器即明确失败，不在正式检查中临时下载浏览器。
- 从 Git HEAD 建立私有临时源码快照；保护生产输入无未提交变更。Maven/npm 门禁在快照执行，不清理工作区已有 target、缓存或历史验收目录。`M14_MAVEN_REPO` 沿用既有门禁的绝对路径配置，默认 `/private/tmp/tensor-m2`。

### 本轮环境及最小权限

1. `set -eu`、`set +x`、`umask 077`；创建新的 0700 临时目录。子进程只继承明确需要的 PATH、工具路径和系统运行环境；清除继承的 `TENSOR_*`、`SPRING_*`、`SERVER_*`、Java/Node 注入参数和代理配置。禁止打印环境或原始异常。
2. Node `crypto.randomBytes(24).toString('hex')` 生成独立后缀；Token 为 `M14_T07_TOKEN_<后缀>`，应用密码为 `M14_T07_DB_<另一后缀>`，数据库管理员密码单独随机生成。完整值不进入仓库、argv、报告或终端。Token 只传给 JVM；本机 stub 在内存中核对它且不保存请求体。管理员凭证不进入 JVM。
3. 创建带本轮随机所有权标签的 `mysql:8.4.6` 容器，只映射 `127.0.0.1` 动态端口。管理员密码通过 0600 私有 env 文件，SQL 通过 stdin；管理员连接资料只用 0600 defaults 文件。创建随机 `tensor_m14_t07_<hex>` 空 schema，字符集为 `utf8mb4/utf8mb4_0900_as_cs`。
4. 根据本轮 TCP 连接实测的来源 host 创建独立应用账号，只授该 schema 上 CREATE、SELECT、INSERT、UPDATE、ALTER、INDEX，不授 DELETE、DROP、全局权限或 GRANT OPTION。核对真实服务器版本、来源、字符集和授权后再启动 JVM。
5. 应用只绑定 `127.0.0.1:8080`；端口已占用时退出，不终止未知进程。使用默认生产 profile，清空开发 CORS，不启用 fixture；合成上游绑定回环随机端口。根 health 200/UP 后核对六次成功迁移及 49 张全空业务表。
6. 应用、数据库和 stub 使用回环通信；只有构建/扫描进程访问公共依赖及漏洞数据源。stub 仅接受预计的 `stock_company` 两次调用，无真实上游、重试或重定向；记录 browser 同源请求与 stub 调用数。回环 HTTP/MySQL 是本地合成测试例外，不证明远端 TLS 已部署。

### 页面及 HTTP 验证矩阵

通过已安装的 `control-plane/node_modules/@playwright/test` 库启动一个 Chromium context；临时 Node helper 使用 `createRequire` 从该项目 `package.json` 定位模块，不依赖临时目录中的包解析。单 worker、零重试、无 trace/video/自动截图。每项操作后检查 DOM、HTTP 响应、console/pageerror 和请求 URL 的秘密边界；捕获异常仅输出固定步骤名。默认单次轻量 HTTP 连接 5 秒/总计 15 秒，页面动作 15 秒，正常启动根 health 等待总预算 90 秒；不更改产品的 connect/read timeout。

| 编号 | 操作 | 固定预期 |
|---|---|---|
| S01 | GET 根 health、liveness、readiness；GET `/actuator`、`/actuator/env`、`/actuator/configprops`、`/actuator/metrics`、`/actuator/beans`、`/actuator/heapdump`、`/actuator/logfile`、`/actuator/mappings` | 前三项 200 且仅公开状态，不含 components/details 或配置；其余 404，不能返回 SPA HTML，也不能出现配置值。|
| S02 | 页面进入下载/数据查看；读取数据源与数据集描述符 | 恰一个生产插件 `tushare_pro`，49 数据集，无 fixture；假 Token 配置状态为可下载，JSON 不含 Token/password 值或敏感配置键。|
| S03 | 页面选择 `stock_company`、`exchange=SZSE` 并下载合成一行，再页面查询 `000001.SZ` | 第一次下载 SUCCESS，source/insert/update=1/1/0；查询恰一行，来源正确。`introduction` 中 HTML 在单元格/tooltip 都按文本呈现，无注入元素、脚本执行或攻击产生的网络请求。|
| S04 | 第二次从相同页面参数下载，stub 返回 HTTP 401，正文包含本轮假 Token 和固定 `M14_T07_UPSTREAM_DETAIL` | 产品 HTTP 502、`SOURCE_AUTH_FAILED`、retryable=false；页面显示安全错误与对应 requestId。响应、页面、普通日志无 Token 或上游原文，独立页面查询仍为完整原行。|
| S05 | 对数据集列表、`stock_company` 定义及 records 路径分别发 POST/PUT/PATCH/DELETE，body 为 `{}` | 全部 405，无 2xx、无下载调用、无数据变化；查询页面没有新增/编辑/删除/导出控件。|
| S06 | records 分别附加 `table=other_table`、`column=introduction`、`columns=*`、`sort=ts_code DESC`、`orderBy=ts_code`、`sql=SELECT 1` | 每次 400/`PARAM_INVALID`；即使只是忽略参数而返回 200 也不能满足任务卡的“rejected”，须登记合同缺口/缺陷，不放宽预期。|
| S07 | `tsCode=x' OR 1=1 --`、`page=1 OR 1=1`、`pageSize=101`、`tradeDateFrom=2026-08-07`（stock_company 无该筛选），以及 apiName 为编码后的 SQL 标识符片段 | 查询值和未声明筛选为 400/`PARAM_INVALID`；非法路径为 400 或 404，绝不 2xx/500。无 SQL/堆栈/内部路径或提交文本反射；合法末查完整行不变。|
| S08 | S01～S07 以及 `/downloads`、`/datasets`、真实引用 JS/CSS 上检查安全头；发送 Origin `https://m14-t07.invalid` 的 GET 和 OPTIONS 预检 | 下列六个安全头一致；生产无 Access-Control-Allow-Origin/Credentials，预检不授跨源权限；页面/API/Actuator 缓存按 runbook。|

合成 `stock_company` fields 只读公开模板顶层 `fields`，流式跳过 `data`，对照页面取得的 18 列定义。合成 row 按 fields 投影：`ts_code=000001.SZ`、`exchange=SZSE`、`introduction=<img src=x onerror="window.__m14_t07_xss=1"><script>window.__m14_t07_xss=1</script>`，其他业务字段为 null；不读取真实样例行。验证 introduction 原字符串和 DOM 无 `img/script` 后，断言 `window.__m14_t07_xss` 未定义；初始化脚本只布置攻击观测，不改变产品行为。stub 核对 api_name、Token、fields、参数精确匹配；两次调用以外任何调用即失败。

安全头固定为既有 `ObservabilityTest` 合同：

- `Content-Security-Policy: default-src 'self'; base-uri 'none'; object-src 'none'; frame-ancestors 'none'; form-action 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'`
- `X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY`、`Referrer-Policy: no-referrer`。
- `Permissions-Policy: camera=(), microphone=(), geolocation=()`、`Cross-Origin-Opener-Policy: same-origin`。

### 凭证和日志扫描

完整 canary 仅存在控制进程内存、私有数据库初始化资料和必要的 JVM 环境。扫描目标分别计数：Git 跟踪源码的实际文件、目标 JAR 的所有解压条目（递归展开嵌套 JAR）、公开 HTTP header/body、页面 DOM/console、JVM 普通日志、浏览器驱动输出、所有本轮 Playwright/验证产物和待提交证据。检查三个凭证原字节及 JSON 转义、URL 编码、Base64 表示；不得用只扫描外层压缩 JAR 的 `strings` 冒充递归扫描。

扫描读取原始字节不回显命中值、上下文或含秘密路径，只报告固定类别/编号及命中数。读取失败、畸形 ZIP、未完成扫描均使门禁失败。私有初始化 env/defaults 文件是唯一必然含 canary 的输入，不作为公开输出扫描目标；须单独校验权限、归属、禁止链接、记录删除结果，不能通过宽泛排除目录隐藏日志或报告泄漏。

日志核对本轮下载/查询 requestId，每个恰有一个完成事件；不保存或提交完整请求参数、SQL、上游原文。安全反例中提交的 SQL 片段和 `M14_T07_UPSTREAM_DETAIL` 不应出现在公开响应/页面/普通应用日志。最终只读遍历 49 张业务表检查凭证字面值未持久化，输出匹配计数；页面已展示的合成 HTML 可以在业务表中原样存在。不得用管理员元数据/账号表中的凭证记录作为业务泄漏。

### 既有安全测试与依赖扫描

在临时快照运行聚焦 Maven 门禁，不更改 POM：

```sh
mvn -B -ntp -f data-plane/pom.xml -pl tensor-app -am \
  -Dmaven.repo.local="$M14_MAVEN_REPO" \
  -Dtest=ModuleDependencyTest,ForbiddenGitCapabilityTest,ObservabilityTest,ProductionWebConfigurationTest,QuerySqlFactoryTest,UpsertSqlFactoryTest,DatasetControllerIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Enforcer 必须实际执行；逐个核对上述七个类的新 XML 报告、测试数非零、failures/errors/skipped 为零。`DatasetControllerIT` 用真实 MySQL，SQL factory 测试证明值绑定与固定标识符，HTTP 黑盒补充端到端边界。Git 禁令约束产品/构建能力，编排脚本用于记录身份的 Git 命令不属于被禁的运行时能力。

后端扫描固定 OWASP Dependency-Check Maven `13.0.0`（Maven Central 公开元数据发布版本），执行：

```sh
mvn -B -ntp -f data-plane/pom.xml \
  -Dmaven.repo.local="$M14_MAVEN_REPO" \
  org.owasp:dependency-check-maven:13.0.0:aggregate \
  -DfailBuildOnCVSS=7 -DfailOnError=true -DautoUpdate=true \
  -DskipTestScope=false -DskipProvidedScope=false -DskipRuntimeScope=false \
  -Dformat=JSON -Dodc.outputDirectory="$M14_SECURITY_REPORT_DIR" \
  -DdataDirectory="$M14_SECURITY_CACHE_DIR" \
  -DnvdApiKeyEnvironmentVariable=M14_SECURITY_NVD_API_KEY
```

`M14_SECURITY_REPORT_DIR`、`M14_SECURITY_CACHE_DIR` 由脚本赋为私有临时目录；NVD API key 可选，只从单独同名环境变量交给扫描进程、不进入 argv 或 JVM。若提供，也加入秘密扫描集合。无 key 时允许正常公开下载；网络、更新、解析、分析错误均报告失败，禁止关闭更新/分析器、使用空报告或放宽门槛。

扫描报告记录生成时刻、工具版本、漏洞数据库更新状态，必须覆盖全部 reactor 模块的第三方依赖；生产 JAR 内第三方 Maven 坐标逐个与报告覆盖集合比较，缺失不得算通过。JSON 中未接受的 CVSS >=7 / HIGH / CRITICAL 为失败；漏洞严重性缺失需明确记录并完成评估，否则该项未完成。没有预批准的 suppression 或风险接受，不自动忽略漏洞。扫描器版本依据 [Maven Central 元数据](https://repo.maven.apache.org/maven2/org/owasp/dependency-check-maven/maven-metadata.xml)，参数依据 [aggregate 官方文档](https://dependency-check.github.io/DependencyCheck/dependency-check-maven/aggregate-mojo.html)；不使用浮动版本。

前端在快照 `control-plane` 运行 `npm audit --package-lock-only --audit-level=high --json`，覆盖 production 和 devDependencies，不用 `--omit=dev`；记录 npm/lockfile 身份、审计覆盖数及高危/严重数量。退出码、`error` 对象、报告结构均核对，网络错误不当作漏洞数量零。中低危照实记录；本任务不升级依赖或执行 `npm audit fix`。

### 停机、退出码和证据

各门禁记录 pass/fail/not-run 与原始子命令退出码，独立安全门禁可以继续采集以形成完整报告；JAR 身份/权限/环境失败时不得启动依赖动作，泄漏或异常网络请求时立即停止动态动作。主命令只在全部必需门禁及清理通过时退出 0，检查失败退出 1，参数错误退出 2；不存在 skip-as-pass。

无论成功或失败，等待浏览器/驱动结束，对核实所有权的 JVM 发 SIGTERM，正常等待退出；回环 stub 与 socket 关闭；扫描最终日志和产物，然后按精确容器 ID/所有权标签删除本轮容器及其卷、删除本轮初始化凭证文件。不得按名字模糊杀进程或清理其他容器。JVM 等待每 30 秒输出无秘密状态，180 秒未退出记清理失败，不以强杀当正常停机通过。确认 8080 空闲，canary 集合仅在最终扫描和清理核对后释放。

报告只保留时间、环境版本、输入 SHA、各场景状态/计数、requestId、依赖坐标/CVE/严重性、扫描覆盖与清理结果；不保留 DB URL、账号/密码、Token、完整日志或源数据。输出文档经同一秘密集合终检后才可提交。失败时未执行项明确保留，不能用历史成功补足。

## Files

- 新增 `scripts/security/verify-release.sh`：全部自检、运行、安全探针、漏洞门禁和生命周期；脚本设为可执行，同时支持 `sh` 调用。
- 新增 `docs/verification/M14-T07-security.md`：实际运行证据；所有结果来自执行，不预填通过。
- 控制文档：本设计、看板 M14-T07 精确 Design/状态证据、任务卡 Design 链接；实施/证据和控制文档分别提交。发现实际缺陷时按 `docs/issues/README.md` 登记，完成暂停交接后再按状态机更新，不越界修复生产实现。

## Tests

先用 `sh scripts/security/verify-release.sh --self-test` 验证检查器能拒绝真实反例，再正式运行。反例必须调用正式相同扫描/判定函数；覆盖源码/嵌套 JAR/HTTP/日志/产物注入 canary、编码 canary、缺失或不可读扫描目标、畸形 ZIP、HTTP 200 接受非法字段、漏安全头、Actuator 暴露、浏览器实际解析 HTML、漏洞报告 high/critical、报告缺失/分析错误、空测试报告、任务失败后清理失败。每个反例必须退出非零，干净对照为零；不能只检查脚本文本包含关键词。

```sh
sh -n scripts/security/verify-release.sh
sh scripts/security/verify-release.sh --self-test
M14_SECURITY_JAR=/private/tmp/tensor-m14-t09-green.MZ4kMkN9/data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar \
  sh scripts/security/verify-release.sh
```

语法与自检预期退出 0；正式运行仅在 S01～S08、七类 Maven 测试、后端/前端漏洞扫描、源码/JAR/页面/日志/数据库/产物扫描与清理均通过时退出 0。记录实际通过/失败/未执行数量，不在设计中指定虚假的测试总数。仅新增验证脚本，不为已有生产行为修改测试或生成生产代码。

## Acceptance

1. 两个实施产物形成且可重跑，冻结生产包前后 SHA 一致，保护范围无改动，新增文件已纳入 Git。
2. 页面完成两次 stock_company 下载（成功/鉴权失败）及查询核对，stub 恰两次、真实上游零次，HTML 原样文本呈现；HTTP 输入拒绝及生产暴露面符合 S01～S08。
3. canary 注入反例确实失败，实际所有约定扫描目标完成且零泄漏；完成事件逐 requestId 唯一、业务表无凭证字面值。
4. Enforcer、架构/Git 禁令与指定 SQL/运行安全测试均有新的通过证据；两种依赖扫描有覆盖完整的有效报告，无未接受高危/严重问题。
5. JVM、browser、stub、容器卷和初始化资料清理核对通过；证据文档终检通过，命令实际退出 0。任一未完成不能标记 M14-T07 COMPLETED。
6. 网络结论区分本地已测回环隔离与部署要求：生产入口 HTTPS、受控内网或外层身份代理、数据库 TLS、代理响应至少 130 秒、停机窗口覆盖所有阶段。未提供远端环境就不声称这些部署控制已实测；该任务卡要求的外部访问控制说明由现行 runbook 和本报告共同保留。

## Risks

- Dependency-Check 初次获取漏洞数据库可能较慢或受 NVD 限流；记录实际错误与已完成检查，不以缓存缺失、扫描器失败或不完整报告判安全通过。
- 当前 shell 默认 Node 22，与既有前端引擎不符；正式执行前选择已有 Node 24 工具路径并记录版本。
- 生产包位于临时目录，若被外部清理，只能找回相同 SHA 的产物；更换二进制须先更新并确认设计身份，不使用旧真实验收包冒充。
- 任务卡要求拒绝任意 table/column/sort/SQL 字段；现有 OpenAPI 未逐项展示这些额外参数的错误示例。设计以任务卡的拒绝语义测试，观察到 200 时如实报失败，不推断 SQL 已执行，也不为通过而接受“被忽略”。
- M14-T05 未完成原 49 目标、M14-T06 未实测性能，项目还有既有未解决问题。本任务不会关闭它们；M14-T08 的证据齐备/零未决门禁须独立验证，不能自动将其标为可发布。

直接输入及用途：M14-T02 的安全错误/日志和本机上游证据（`docs/verification/M14-T02-download-outcomes.md`）；M14-T03 的页面文本/tooltip/查询及合成 stock_company 合同（`control-plane/e2e/dataset-query.spec.js`、`docs/verification/M14-T03-dataset-query.md`）；M14-T04 的 49 合同和私有源码快照范式（`scripts/verify-49-contracts.sh`、`docs/verification/M14-T04-49-contracts.md`）；M14-T05 的凭证隔离与生命周期输入（`docs/verification/M14-T05-tushare-live.md`），由 M14-T09 的 V7、六权限及冻结包证据补充（`docs/verification/M14-T09-tushare-live.md`、`docs/verification/M14-T09-tushare-live-rerun-02.md`）。现行 `docs/runbook/first-run.md`、`docs/runbook/configuration.md` 固定生产迁移/网络/暴露边界；`docs/contracts/openapi-v1.yaml` 和既有测试固定接口行为。历史六迁移/50表的 acceptance 证据不覆盖本设计的六迁移/49表生产包检查。
