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

生产由一个 JAR 提供 Vue、静态资源和 `/api/v1`，默认不注册 CORS。开发 origin 只作用于 `/api/v1/**`，允许 GET、POST、OPTIONS；请求头只允许 `Content-Type`、`X-Request-Id`，响应暴露 `X-Request-Id`，`credentials=false`。UI、assets 和 Actuator 没有开发 CORS 映射。

未设置、空字符串、纯空白、逗号列表、末尾斜杠均不开放 CORS；精确 `*` 会导致启动失败。其他不匹配浏览器 Origin 的值不会获得允许响应，不能通过 wildcard 或关闭安全控制解决错误配置。CORS 不提供认证；公网 TLS 和访问控制边界由部署入口承担。

## 超时和停机

| 位置 | 当前约束 | 运行含义 |
|---|---|---|
| Tushare 客户端 | connect 5 秒、read 120 秒 | 同步上游连接与读取上限。 |
| 前端请求 | 130 秒 | 大于上游读取上限。 |
| 部署代理响应 | **至少 130 秒** | 保持 `120s < 130s <= proxy`；组织已有代理必须满足此约束。 |
| 写事务 | 60 秒 | 当前写事务 timeout。 |
| Spring graceful shutdown | 每阶段 70 秒 | Web 阶段覆盖 60 秒写事务并留余量，不是 JVM 总停机期限。 |

同步 Servlet 请求没有本任务可配置的独立应用处理 timeout，连接 timeout 或异步 MVC 参数不能代替它。smoke 的每项检查连接上限 5 秒、总上限 15 秒，只约束相应的轻量 GET 检查，不改变应用或下载超时。

正常停止采用 Ctrl-C 或向已核实的应用 PID 发送 SIGTERM，并等待 JVM 自行退出，不常规使用 SIGKILL。首跑不设置外部强杀倒计时；部署管理器的终止窗口必须包含全部实际停机阶段与清理时间，不能只设为 70 秒。上游读取最长 120 秒而 Web 停机阶段为 70 秒，不能承诺每个完整下载请求均在停机期间完成，未提交事务由数据库回滚。操作步骤见 [正常停止](first-run.md#6-正常停止)。

## 健康和缓存

生产默认只暴露 health 家族，禁用 Actuator discovery；无需开放 `env`、`configprops`、`metrics`。根 `/actuator/health` 包含数据库检查，必须达到 HTTP 200 且根状态 UP 才能开放流量；`/actuator/health/readiness` 只作为辅助，不替代根检查。Token 的配置状态与数据库健康独立。

`/`、`/index.html`、`/api/**` 和 Actuator 使用 `no-store`；`/downloads`、`/datasets` 等 UI fallback 保留 `no-cache`；`/assets/**` 使用 `public, max-age=31536000, immutable`，即一年 immutable 缓存。UI 可以直接刷新，未知 API 或文件资源仍应返回其真实错误状态。

## 数据库权限与版本维护

首次运行创建 `utf8mb4` / `utf8mb4_0900_as_cs` 的 `tensor` schema，并仅向匹配实际 JDBC 客户端来源的应用账号授予 `tensor.*` 上 CREATE、SELECT、INSERT、UPDATE；管理员通过 `SHOW GRANTS` 检查。当前自动迁移为 V1–V5，共 49 张业务表，Flyway history 另计，不启用 fixture 或测试 V6。新版本引入其他 DDL 时，再按发布说明评估 schema 范围内所需权限。

发布前使用管理员或备份账号，将交互密码的 `mysqldump --single-transaction --no-tablespaces --set-gtid-purged=OFF` 备份写入新建的权限受限唯一目录，避免覆盖，并在独立环境验证恢复。完整示例见 [备份与回退](first-run.md#7-备份与回退)。Flyway 只前向，不运行 clean、不删 history、不执行逆向/破坏性 DDL。上一应用版本必须兼容当前 schema 才能回退；删除/缩窄字段先兼容再清理，误写恢复依赖已验证备份。
