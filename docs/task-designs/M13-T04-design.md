# M13-T04 全新环境运行说明和启动 smoke test——任务设计

任务编号：`M13-T04`

对应任务：[M13-T04](../superpowers/plans/tensor-modules/M13-packaging-runbook.md#task-m13-t04-全新环境运行说明与-smoke-test20hmarkdownshell)

实施产物：两份随单 JAR 分发的运行说明，以及只读、无下载请求的 POSIX Shell smoke 脚本。

## Goal

让首次使用者只凭运行说明、已构建的 `tensor-app-1.0-SNAPSHOT.jar` 和 smoke 脚本，在 Java 21 与 MySQL 8.4 环境完成建库、账号授权、环境注入、启动、健康检查、页面访问和正常停机；不依赖源码目录、Maven、Node、独立前端服务器或真实 Tushare Token。用可重复的 HTTP smoke 检查证明同一个 JAR 提供健康、两个 SPA 页面和数据源 JSON，并且不向响应或检查输出泄漏已知秘密。

## Scope

只创建任务卡指定的三个实施文件：

- `docs/runbook/first-run.md`；
- `docs/runbook/configuration.md`；
- `scripts/smoke-test.sh`，同时作为任务卡指定的被测脚本，Git 模式为 `100755`。

包括 schema/账号、环境变量、JAR 获取与启动、健康就绪、页面地址、停止、备份、向前迁移与兼容版本回退说明；包括 smoke 正常/失败验证、独立临时目录中的真实打包实例验证和 M13 模块门禁。

不修改生产代码、测试 Java、配置 YAML、POM、前端、OpenAPI 或 SQL migration；不提供安装器、容器编排、systemd/代理配置、账号认证、下载/E2E 测试或实际业务数据库恢复操作。Docker 可以供实施者建立一次性 MySQL 验证环境，但不是应用运行要求，不作为用户唯一启动路径。生成物、临时测试服务器、响应、日志、数据库文件和凭证不得提交。

## Approach

### 已固定输入

直接依赖只有 M13-T03。其设计 `docs/task-designs/M13-T03-design.md`、当前 `application.yml`、Servlet Web 配置与已打包 JAR 提供本任务的全部运行行为；本任务只说明和黑盒验证它们。

同时按模块任务卡读取 TRD `docs/design/Tensor_多源证券数据平台_TRD_v1.0.md` 的 14.1–14.3、19.1–19.5、附录 B，以及 `docs/contracts/openapi-v1.yaml` 的数据源列表契约。这些是文档来源，不增加看板直接依赖。当前 V1–V5 是创建 49 张业务表的前向迁移，Flyway 另外维护自己的 history 表；运行说明不得把它们误写为 49 张数据库总表或要求生产执行 V6。

### `first-run.md` 的固定内容与顺序

1. **准备运行环境和文件**：Java 21、可连接的 MySQL 8.4 LTS、数据库管理员创建 schema/应用账号的能力。运行目录含同版本 JAR、两份说明及 smoke 脚本；验证工具为 POSIX `sh`、`curl` 和系统文本工具，明确它们用于检查，不属于应用的服务依赖。文档给出 `java -version`、`mysql --version`、`curl --version`。构建者可在源码根运行 `mvn -f data-plane/pom.xml clean verify`，取得 `data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar`；使用者直接使用已分发 JAR，不需要 Maven/Node/npm/Git。
2. **schema 与账号**：由管理员在 MySQL 会话中创建 `tensor` schema，字符集 `utf8mb4`、排序规则 `utf8mb4_0900_as_cs`；示例使用 `CREATE DATABASE IF NOT EXISTS`。单独创建应用账号，账号的 host 必须对应实际 JDBC 客户端来源，不使用任意来源 `%` 作为默认。当前 V1–V5 只有 `CREATE TABLE` 与会话 `SET time_zone`，运行只需 SELECT、INSERT 和 UPDATE，因此给出目标 `tensor.*` 上 `CREATE, SELECT, INSERT, UPDATE` 授权和 `SHOW GRANTS` 检查；不授予全局权限、账号管理或 `GRANT OPTION`。未来引入其他 DDL 时应在对应版本发布说明中单独评估权限。SQL 的账号/host/密码位置使用明确的 `<DB_USER>`、`<APP_HOST>`、`<DB_PASSWORD>` 编辑占位符，声明它们不是可用凭证、必须在管理员会话中替换，不修改应用源码。禁用客户端 SQL 历史保存，不能把含替换后密码的语句放进 shell 命令行、仓库或截图。
3. **注入环境**：可复制的非秘密例子固定为 `TENSOR_DB_URL=jdbc:mysql://127.0.0.1:3306/tensor`。账号由终端 `read -r TENSOR_DB_USERNAME` 输入；数据库密码用关闭回显的交互输入后导出，提供恢复回显的 trap，不给密码或 Token 的字符串示例。下载不是首次启动前提，默认 `unset TENSOR_TUSHARE_TOKEN TENSOR_DEV_CORS_ALLOWED_ORIGIN`，并明确现有 shell 如有配置需显式检查是否应保留。Token 配置说明使用同样的隐藏输入方式；不把秘密放在 `java -D`、URL、命令行参数、日志或版本控制文件中。生产可由部署平台以环境或外部只读配置注入；本任务不写平台配置。
4. **启动同一个 JAR**：从运行目录执行 `java -jar tensor-app-1.0-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=8080`，在前台观察启动是否成功；该初次运行示例只绑定回环。Flyway 与数据集 schema 校验由应用自动完成，不执行手工 migration，不启用 fixture/acceptance profile，不起第二个前端进程。已有组织网关可承担 TLS/访问控制，但本地首跑不要求网关。
5. **就绪和页面**：新终端读取 `/actuator/health`，要求 HTTP 200 且根状态 UP 后再开放流量；若数据库缺失/不可用或启动校验失败，不把进程存在等同于就绪。给出 `sh scripts/smoke-test.sh http://127.0.0.1:8080`，以及浏览器 `/downloads`、`/datasets` 两个完整地址。直接刷新仍显示页面。没有 Token 时元数据/数据查看仍可用，Tushare 下载不可用；空新库没有业务记录是正常情况。`/actuator/health/readiness` 可辅助检查，但不能替代根 health 的数据库检查。
6. **正常停止**：前台 Ctrl-C，或对已确认属于该应用的 PID 发送 SIGTERM，等待该 JVM 自行退出；不使用常规 SIGKILL。说明 70 秒是 Spring 每个停机阶段上限，涵盖现有 60 秒写事务，不能当作整个 JVM 的总停机期限。首跑不设置外部自动强杀倒计时；部署管理器若设置强杀期限，必须覆盖所有实际停机阶段和清理时间，不能仅设为 70 秒。未结束的同步上游读取最长 120 秒，而当前 Web 停机阶段为 70 秒，因此不承诺每个完整下载请求均能在停机时完成；数据库会回滚未提交事务。
7. **备份与回退**：发布前以管理员/备份账号备份目标 schema，示例用 `mysqldump --single-transaction --no-tablespaces --set-gtid-purged=OFF` 和交互密码，不在 argv 放密码；备份落入新建的权限受限目录，避免覆盖既有文件，并要求独立环境验证恢复。Flyway 只前向；不运行 clean、删除 history、逆向/破坏性 DDL。上一应用版本仅在确认兼容当前 schema 时可回退；删除/缩窄字段遵循先兼容再清理，误写恢复依赖已验证备份。本任务不执行实际业务恢复。
8. **故障定位**：缺数据库变量/连通性/权限、端口冲突、JAR/Java 版本、health 非 UP、页面 404、Token 缺失及开发 origin 填错；只列安全检查方式和应有结果，不让使用者输出整个环境、配置、数据库 URL 中的秘密或原始失败响应。

### `configuration.md` 的固定内容

用一个表逐项列出名称、是否必填、默认值、用途和暴露边界：

| 名称 | 必填与默认 | 用途 |
|---|---|---|
| `TENSOR_DB_URL` | 必填，无默认 | JDBC URL；本地示例使用 `tensor` schema，不把密码嵌入 URL；生产数据库传输按部署要求使用 TLS |
| `TENSOR_DB_USERNAME` | 必填，无默认 | schema 级应用账号 |
| `TENSOR_DB_PASSWORD` | 必填，无默认 | 只在后端环境/只读配置中注入 |
| `TENSOR_TUSHARE_TOKEN` | 仅下载需要，默认空 | 缺少时数据查看仍可用；只返回配置状态，不返回值 |
| `TENSOR_TUSHARE_ENABLED` | 默认 `true` | 是否启用 Tushare 插件 |
| `TENSOR_TUSHARE_BASE_URL` | 默认 `https://api.tushare.pro` | 上游地址；首跑无需访问上游 |
| `TENSOR_DISPLAY_ZONE` | 默认 `Asia/Shanghai` | 入库时间显示时区 |
| `TENSOR_DEV_CORS_ALLOWED_ORIGIN` | 默认空 | 仅开发；示例 `http://127.0.0.1:5173`，生产同源保持空 |

另说明示例启动命令的 `--server.address`/`--server.port` 是 Boot 非秘密运行参数，不新增 Tensor 自定义环境变量。CORS 只作用于 `/api/v1/**`，GET/POST/OPTIONS，两请求头 Content-Type、X-Request-Id，暴露 X-Request-Id，credentials false；blank/逗号列表/末尾斜杠不开放 CORS，精确 `*` 导致启动失败。错误配置不能通过 wildcard 或关闭安全控制解决。

明确四项已验证约束：上游 connect 5s/read 120s；前端 130s；代理响应超时 **至少 130s**；graceful 每阶段 70s 与事务 60s。同步 Servlet 不存在本任务可配置的应用处理 timeout，不用连接 timeout/异步 MVC 参数冒充它。生产默认只暴露 health 家族、禁用 Actuator discovery；不要求开放 env/configprops/metrics。记录 no-store 入口/API/Actuator、no-cache UI fallback 和一年 immutable assets；说明 CORS 不提供认证，公网访问边界属于部署入口。

### `smoke-test.sh` 的固定接口

- 首行为 `#!/bin/sh`，使用 `set -eu`；无参数默认 `http://127.0.0.1:8080`，一个参数为 base URL，其他数量退出 2。只允许 http/https；拒绝空 authority、userinfo、query 和 fragment；移除末尾 `/` 后拼接固定路径。脚本不增加新的应用配置项。
- 只执行 GET，依次检查 health、downloads、datasets、data-sources 四项；不下载、不重试、不启动/停止应用、不访问 Tushare、不改数据库。调用者应先按 runbook 等待就绪。
- `curl` 禁止 URL glob、不跟随重定向，每次连接上限 5 秒、总上限 15 秒。分别捕获 status、header 和 body；只有精确 HTTP 200 成功。网络失败、3xx/4xx/5xx、内容不符或秘密泄漏均立即退出 1，不以重定向隐藏错误。
- 运行前检查 `curl` 可用。用 `umask 077` 和 `mktemp -d` 建立脚本专属响应目录；trap 在正常/失败/信号退出时删除该目录内自己创建的 header/body 文件后 `rmdir`，不使用宽泛递归删除。不得打印 response body、完整响应 header、环境或 curl verbose 输出。
- health 要求 JSON Content-Type 和 `"status"` 对应 `"UP"` 标记。两个页面要求 HTML Content-Type 和真实入口 `<div id="app"></div>` 标记。数据源要求 JSON Content-Type、外层数组起止标记，并含一个 `pluginId` 为 `tushare_pro` 的条目及 boolean `credentialConfigured`/`downloadAvailable`；不要求两个值为 true。JSON 只做这些 smoke 标记检查，完整 DTO 合同由既有 OpenAPI/后端测试负责，不引入 jq、Python 或自制完整 JSON 解析器。
- 四次响应的 body 检查敏感 JSON 键 `token`、`password`、`authorization`、`cookie`、`jdbcUrl`（大小写不敏感，键精确匹配，不能误拒绝 `credentialConfigured`）；header 拒绝 Authorization、Cookie、Set-Cookie 字段。header/body 都检查 `jdbc:mysql:`。当脚本环境内 `TENSOR_DB_PASSWORD` 或 `TENSOR_TUSHARE_TOKEN` 非空时，另用 shell 内建 `case` 和被引用的变量做字面子串检查；不把秘密作为 grep/curl 等子进程参数，不输出命中的值。不声称能检测调用者未提供的任意秘密。
- 只输出固定检查标签和成功/失败，例如 `Tensor smoke test passed (4 probes).`；失败只输出 `Tensor smoke test failed: <固定检查标签>` 到 stderr。URL 参数错误只输出 usage，不回显恶意参数。成功为 0，失败为 1，usage 为 2。

## Files

创建：

- `docs/runbook/first-run.md`：上述八段完整操作说明，相对链接到 configuration；
- `docs/runbook/configuration.md`：八个环境入口及安全、timeout、CORS/缓存/health 边界，相对链接到 first-run；
- `scripts/smoke-test.sh`：上述四个只读 HTTP probe 和失败/秘密检查，所有 helper 留在同一个文件。

不创建其他实施或永久测试文件；临时响应桩/测试日志只放独立临时目录。实现提交消息为 `docs: add reproducible Tensor first-run guide`，精确包含上述三个新增文件。

## Tests

### 语法、只读性与失败路径

在源码根执行：

```sh
sh -n scripts/smoke-test.sh
sh scripts/smoke-test.sh http://127.0.0.1:8080
```

第二条只在独立 packaged test instance 健康后运行，预期四项通过、退出 0。测试前先在临时 HTTP 响应桩上验证下表，桩只记录方法/path并返回指定内容，脚本不加入专用 test mode；测试工具不成为应用运行依赖。先验证正常桩，再每次仅变更一项条件，最后恢复正常桩。

| 场景 | 预期 |
|---|---|
| 四项 200、正确 Content-Type/标记、无 Token 且下载状态 false | 退出 0；恰好四次固定 GET，无下载请求 |
| 连接拒绝或任一 probe 503、302 | 退出 1，停止后续请求；不跟随 Location、不打印响应 |
| health 非 UP、页面缺入口标记、data-sources 为 HTML/缺规定字段 | 分别退出 1 |
| 响应含 token/password 键、JDBC URL 或脚本环境中的非空秘密哨兵 | 退出 1，stdout/stderr 均不出现哨兵 |
| 只有 credentialConfigured/downloadAvailable 布尔字段，秘密环境未设置 | 不误报、不触发 set -u，正常退出 0 |
| 多个参数或包含 userinfo/query/fragment 的 base URL | 退出 2，无 HTTP 请求，参数不回显 |
| 任意失败路径或成功结束 | 临时响应目录被清理，无仓库文件写入 |

### 真正的全新环境验收

先执行任务卡的完整模块门禁：

```sh
mvn -f data-plane/pom.xml clean verify
```

要求前端 120、当前后端 Surefire 368、既有 JAR Failsafe 4 全部通过，零失败/错误/跳过，repackage 成功。若沙箱只阻止既有 Byte Buddy self-attach，在正常 JVM 权限下原命令重跑，不改配置或 skip。

用 `mktemp -d` 建立全新的运行目录，只复制生成 JAR、两份 runbook 和 smoke 脚本；保留文档/脚本相对布局。从此目录按 runbook 操作，不读取源码、不使用构建输出目录作为工作目录。连接独立全新 MySQL 8.4 schema；测试环境可使用一次性 `mysql:8.4.6`，凭证是运行时生成的测试值且不提交/输出。创建具有文档权限的真实应用账号，不用管理员账号运行应用来绕过权限验证。

验证顺序固定：

1. 按文档注入三项数据库配置、保持 Token/CORS 为空，启动同一 JAR，确认根 health 200/UP、Flyway V1–V5 成功、49 张业务表与 history 表可定位，无 V6/fixture。
2. 从运行目录执行 `sh scripts/smoke-test.sh http://127.0.0.1:8080`，四项通过；浏览器打开并直接刷新两个页面，不能要求 Vite 服务。无 Token 数据源返回 credentialConfigured=false/downloadAvailable=false，仍能访问数据源和已有数据目录；新库无业务数据正常。
3. 发送正常 SIGTERM 等待退出，再用同一 JAR/数据库重启，migration 不重复创建表，smoke 再次通过。记录退出和重启结果，不对用户现有进程或数据库做操作。
4. 可在同一个一次性测试实例注入非空测试 Token 哨兵，重新启动并只做四项 GET，证明已知值不泄漏；不发送下载，不访问真实上游。记录仅由环境注入导致的 credential 状态变化。
5. 核对备份与回退说明包含非覆盖备份路径、交互密码、恢复验证要求和前向迁移限制；本任务不执行数据库恢复。若首跑文档缺少步骤，只修正文档/脚本并重验受影响流程；若发现生产缺陷，保留失败证据，按既定缺陷流程处理，不扩张本任务文件范围。

记录 Java/MySQL 版本、所用命令、四项 HTTP 结果、schema/migration数量、无 Token 行为、正常停机/重启和运行说明核对结果到权威看板的完成证据；禁止记录凭证值或含秘密的日志。

### 范围、格式、链接和 Git 门禁

```sh
git diff --check
git status --short -- docs/runbook scripts
git diff -- data-plane control-plane docs/contracts
git diff --cached --name-status
git ls-files --stage -- docs/runbook/first-run.md docs/runbook/configuration.md scripts/smoke-test.sh
```

最终三个实施文件全部跟踪，两个 Markdown 为 100644、脚本为 100755；实现提交只含三项新增，受保护路径无差异，文档链接正确，未混入日志、响应、备份、Token、数据库文件或 target。检查文档不含真实凭证、未填写设计事项或要求编辑源码的步骤；SQL 教学占位符须明确标注用途，不能作为实际验收凭证。

## Acceptance

- 只使用分发文件、Java 21、MySQL 8.4 和文档列出的验证工具，即可在新运行目录完成首跑，不读取源码、不需要 Node/Maven/独立前端服务。
- 运行说明包含精确 schema/权限、三项必填数据库变量、Token 可选行为、两个页面地址、健康就绪、正常停止、备份和前向迁移/兼容回退流程。
- smoke 以四项固定只读 GET 成功证明真实单 JAR 的页面/API/health；对网络、状态、内容、秘密、参数和临时资源清理失败场景均得到规定结果。
- 同一真实打包实例在缺 Token 情况下首跑和重启均通过；应用账号权限、49 张业务表/V1–V5、无 fixture 和正常停机有实际验证证据，备份与回退说明完整。
- 明确保留 `120s < 130s <= proxy`，说明 70s 是每阶段停机上限而非 JVM 总期限，不承诺覆盖整个同步下载时间；默认生产同源、health-only、秘密只在后端。
- 完整模块构建、shell 语法、真实 smoke、范围/格式/链接/Git 门禁通过；没有将生产代码、配置或后续 E2E 纳入修改。

## Risks

- MySQL 服务、端口与运行时凭证由验证环境提供；真实连接/权限证据不足时不得用 stub 的成功替代真实首跑验收，也不得使用用户现有业务库做破坏性测试。
- `sh`/`curl` 是检查工具；只有 Java/MySQL 是服务依赖。smoke 的 JSON 标记检查不是完整 schema 校验，已知秘密检查也不等于全部发布安全验收，后者属于 M14。
- 当前权限集按 V1–V5 与现有 Upsert/查询推导，真实应用账号启动是强制验收；若数据库工具行为需要额外权限，应先用具体失败证据定位并在 schema 范围内最小调整说明，不提升为全局管理员。
- 本任务验证普通停止/重启，不进行在途事务压力试验或真实 Tushare 下载；每阶段超时与外部管理器窗口的区别必须在 runbook 明确保留。
