# Tensor 首次运行

本说明适用于同版本分发的 `tensor-app-1.0-SNAPSHOT.jar`。配置项及其边界见 [配置说明](configuration.md)。

## 1. 准备运行环境和文件

准备 Java 21、可连接的 MySQL 8.4 LTS，以及能够创建 schema 和应用账号的数据库管理员。应用服务依赖只有 Java 和 MySQL；POSIX `sh`、`curl`、`mktemp`、`grep`、`tr`、`cat`、`rm`、`rmdir` 等系统工具用于运行检查，MySQL 客户端和 `mysqldump` 用于建库及备份。浏览器用于检查页面。

```sh
java -version
mysql --version
curl --version
```

确认 Java 主版本为 21，MySQL 客户端和服务器为 8.4；客户端版本不能代替服务器版本，在后续管理员会话中运行 `SELECT VERSION();` 确认服务器。

将同一版本的分发文件放进新运行目录，保留以下布局，并在该目录执行后续命令：

```text
tensor-app-1.0-SNAPSHOT.jar
docs/runbook/first-run.md
docs/runbook/configuration.md
scripts/smoke-test.sh
```

使用者直接取得已构建 JAR，无需源码、Maven、Node、npm 或 Git。构建者可在源码根运行：

```sh
mvn -f data-plane/pom.xml clean verify
```

构建通过后，分发 `data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar` 及上述说明、脚本。运行不需要独立前端服务器，也不要求容器、Redis、消息队列或 Nginx。

## 2. 创建 schema 和应用账号

由管理员连接目标 MySQL。下例使用本地服务器，远程部署应替换为实际数据库主机；`--password` 不带值，会交互询问管理员密码。禁用 SQL 历史保存：

```sh
printf '数据库管理员账号: '
IFS= read -r tensor_db_admin
MYSQL_HISTFILE=/dev/null mysql --host=127.0.0.1 --user="$tensor_db_admin" --password
```

在这个 MySQL 会话中执行以下 SQL。`<DB_USER>`、`<APP_HOST>`、`<DB_PASSWORD>` 是必须由管理员编辑替换的教学占位符，不是可用凭证。`<APP_HOST>` 必须匹配 MySQL 实际看到的 JDBC 客户端来源（包括容器或网络地址转换后的来源）；默认不要使用允许任意来源的 `%`。密码按 MySQL 字符串规则正确转义，不要把替换后的 SQL 放入 shell 命令行、仓库、共享日志或截图，也不要修改应用源码。

```sql
SELECT VERSION();
CREATE DATABASE IF NOT EXISTS tensor
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
CREATE USER '<DB_USER>'@'<APP_HOST>' IDENTIFIED BY '<DB_PASSWORD>';
GRANT CREATE, SELECT, INSERT, UPDATE ON tensor.* TO '<DB_USER>'@'<APP_HOST>';
SHOW GRANTS FOR '<DB_USER>'@'<APP_HOST>';
SHOW CREATE DATABASE tensor;
```

确认 schema 的字符集和排序规则正确，授权范围为 `tensor.*`。`IF NOT EXISTS` 不会修正已有 schema 的属性；首次运行应使用符合上述要求的空 schema。MySQL 显示的 `USAGE ON *.*` 不授予实际全局操作权限。

当前 V1–V5 migration 只使用 `CREATE TABLE` 和会话 `SET time_zone`，业务读写使用 SELECT、INSERT、UPDATE，所以上述应用账号权限同时支持建表与运行。无需授予全局权限、账号管理或 `GRANT OPTION`。未来版本引入其他 DDL 时，按该版本发布说明单独评估所需权限。完成后退出管理员会话。

## 3. 注入环境

在运行目录打开一个专用 POSIX shell；以下输入及启动都在该 shell 中进行：

```sh
sh
```

先确认当前终端中已有的 Token 或开发 origin 是否有意保留。以下首次运行示例明确清除二者，不执行下载：

```sh
set +x
export TENSOR_DB_URL=jdbc:mysql://127.0.0.1:3306/tensor
unset TENSOR_TUSHARE_TOKEN TENSOR_DEV_CORS_ALLOWED_ORIGIN
tensor_tty_state=$(stty -g) || exit 1
trap 'stty "$tensor_tty_state"; unset TENSOR_DB_PASSWORD TENSOR_TUSHARE_TOKEN' 0
trap 'exit 1' HUP INT TERM
printf '应用数据库账号: '
IFS= read -r TENSOR_DB_USERNAME || exit 1
printf '应用数据库密码（隐藏输入）: '
stty -echo || exit 1
IFS= read -r TENSOR_DB_PASSWORD || exit 1
stty "$tensor_tty_state" || exit 1
printf '\n'
export TENSOR_DB_USERNAME TENSOR_DB_PASSWORD
```

trap 会在失败、正常 shell 退出或信号中断时恢复终端状态；成功读取后也立即恢复回显。输入应用账号及其密码，不能使用管理员账号替代。不要在开启 shell 跟踪或终端录制的会话中输入秘密。生产可由部署平台通过后端环境或外部只读配置注入，不能把密码或 Token 放到 URL、`java -D`、命令行参数、日志或版本控制文件。

首次运行不需要 Tushare Token。以后执行下载时，按 [配置说明中的隐藏输入步骤](configuration.md#秘密注入) 设置 Token，再重启应用。

## 4. 启动同一个 JAR

在刚才的 shell 前台执行：

```sh
java -jar tensor-app-1.0-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=8080
```

这两个 Boot 参数是非秘密运行参数，首跑只绑定回环地址。观察启动结果：Flyway 自动执行 V1–V5，建立 49 张业务表，另有一张 `flyway_schema_history`；应用随后自动检查数据集元数据和表结构。不要手工执行 migration，不启用 fixture 或 acceptance profile，不执行测试 V6，也不另起前端进程。

组织已有网关可承担 TLS 和访问控制；本地首跑不要求网关。公开访问前应按组织部署入口要求配置访问边界。

## 5. 等待就绪并访问页面

启动完成后，在新终端进入同一运行目录并执行：

```sh
sh scripts/smoke-test.sh http://127.0.0.1:8080
```

脚本首先读取 `/actuator/health`，要求 HTTP 200 和根状态 `UP`，随后依次检查两个页面与数据源 JSON；四项通过后输出 `Tensor smoke test passed (4 probes).`，退出 0。脚本只发 GET，不重试，不触发下载，不访问 Tushare，也不启动或停止应用；若执行时尚未就绪，应先定位原因，确认启动完成后再手动运行。

必须等根 health 达到 200/UP 后才开放流量。数据库缺失、不可用或启动校验失败时，进程存在不代表就绪。`http://127.0.0.1:8080/actuator/health/readiness` 可以辅助检查，但不能替代根 health 的数据库检查。

浏览器访问并直接刷新：

- <http://127.0.0.1:8080/downloads>
- <http://127.0.0.1:8080/datasets>

两页均由同一 JAR 提供。缺少 Token 时，数据源返回 `credentialConfigured=false`、`downloadAvailable=false`；数据源列表、数据集元数据及数据查看仍可用，Tushare 下载不可用。下载接口列表 `/api/v1/data-sources/tushare_pro/apis` 返回 HTTP 409、`PLUGIN_DISABLED`，下载页可显示“下载配置加载失败 / Plugin is unavailable”等配置不可用提示，这是缺少 Token 的预期行为。新空库没有业务记录属于正常现象。

脚本无参数时使用 `http://127.0.0.1:8080`，也接受一个 http/https base URL（可以带部署路径前缀），会移除末尾 `/`。URL 不得包含 userinfo、query 或 fragment。每项连接上限 5 秒、总上限 15 秒；网络、非 200、内容不符或秘密检查失败退出 1，参数错误退出 2。输出只有固定标签，不打印原始响应。它检查指定敏感键、响应头、JDBC 标记，以及脚本环境中非空的 `TENSOR_DB_PASSWORD`、`TENSOR_TUSHARE_TOKEN` 字面值；新终端不会自动继承前一个终端的变量，未提供的任意秘密不在已知值检查能力内。JSON 检查仅为 smoke 标记，不代替完整接口合同验证。

## 6. 正常停止

前台运行时按 Ctrl-C，等待 JVM 自行退出。也可以在另一个终端核实 PID 确属本次 Tensor 进程后，对该 PID 发送 SIGTERM，例如 `kill -TERM "$tensor_pid"`；先设置并核实 `tensor_pid`，不要按模糊进程名批量终止。常规停机不用 SIGKILL。应用退出后，在专用 shell 输入 `exit`，清理本次秘密环境。

Spring graceful shutdown 的 **70 秒是每个停机阶段上限，不是整个 JVM 的总停机期限**。现有写事务上限 60 秒，Web 停机阶段为其保留余量。首跑不要设置外部自动强杀倒计时；部署管理器若设置强杀期限，必须覆盖所有实际停机阶段和资源清理时间，不能仅设为 70 秒。

尚未结束的同步上游读取最长 120 秒，超过当前 Web 停机阶段的 70 秒，因此不能保证每个完整下载请求都能在停机期间完成；数据库会回滚未提交事务。再次启动时使用相同 JAR 与数据库配置，Flyway 校验已有迁移，不会重复创建业务表；重新通过 smoke 后再开放流量。

## 7. 备份与回退

每次发布前，用管理员或专用备份账号备份目标 schema。下例在当前目录新建权限受限的唯一目录，避免覆盖既有备份；`mysqldump` 通过终端交互询问密码，密码不进入 argv：

```sh
umask 077
tensor_backup_dir=$(mktemp -d ./tensor-backup.XXXXXXXX) || exit 1
printf '数据库备份账号: '
IFS= read -r tensor_backup_user || exit 1
if mysqldump --host=127.0.0.1 --user="$tensor_backup_user" --password \
    --single-transaction --no-tablespaces --set-gtid-purged=OFF \
    --databases tensor > "$tensor_backup_dir/tensor.sql"; then
    printf '备份命令成功；请在独立环境验证恢复。\n'
else
    printf '备份失败；此目录内文件不能用作恢复依据。\n' >&2
fi
```

远程数据库应使用实际主机和部署要求的 TLS 参数。备份账号按 MySQL 工具及数据库对象所需权限另行配置，不能把应用账号当作备份管理员；不要在并发 DDL 期间把事务快照视为一致性保证。备份包含敏感业务数据，应按组织要求限制访问和保管，禁止提交 Git。在独立环境验证恢复成功后，才把备份作为可用恢复依据；本任务不执行业务数据库恢复。

Flyway 只允许前向迁移，不运行 `clean`、不删除 history、不执行逆向或破坏性 DDL。只有确认上一应用版本兼容**当前 schema**时才能回退应用。删除或缩窄字段必须分阶段发布，先兼容、再清理；误写恢复依赖经过恢复验证的备份。

## 8. 故障定位

只检查需要的状态，不输出整个环境、配置文件、可能含秘密的数据库 URL 或原始失败响应。

| 现象 | 安全检查与应有结果 |
|---|---|
| 缺少数据库配置 | 在启动 shell 用 `[ -n "${TENSOR_DB_URL:-}" ]` 等非输出判断，分别检查三个必填变量；重新交互输入缺项。 |
| 数据库连不通或权限不足 | 核对管理员会话中的服务器版本、`SHOW CREATE DATABASE tensor` 和应用账号 `SHOW GRANTS`；通过 `mysql --host=127.0.0.1 --user="$TENSOR_DB_USERNAME" --password --database=tensor` 交互连接，执行 `SELECT 1;`。确认实际客户端来源匹配授权 host，不扩大到全局权限。 |
| 8080 端口冲突 | 用系统端口查看工具核实占用进程；选择空闲端口并同步修改启动参数、smoke 地址和浏览器地址，不终止身份不明的进程。 |
| JAR 无法启动 | 用 `java -version` 确认 Java 21，核对分发文件名和版本；重新取得完整的同版本 JAR。 |
| health 未达 UP | 核对数据库连接、权限及启动时 Flyway/schema 校验结果；先解决失败，不用 readiness 成功或 JVM 存活代替根 health。仅在本机查看必要日志，分享前清除秘密。 |
| 页面 404 或内容不符 | 确认请求到了该 JAR 的正确端口和部署路径，使用配套分发版本；直接访问并刷新上述两个页面，无需 Vite。 |
| Tushare 下载不可用 | 无 Token 时下载接口列表返回 409、下载页显示配置不可用提示符合预期；数据源列表、数据集元数据及数据查看仍可用。下载前隐藏输入 Token 并重启，不输出 Token 本身。 |
| 开发跨源请求失败 | 按配置说明核对单个精确 origin；生产保持同源，不能通过 wildcard 或关闭安全控制绕过错误。 |
