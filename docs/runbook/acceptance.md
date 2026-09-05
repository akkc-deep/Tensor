# Tensor 验收包运行说明

## 1. 构建与识别

验收包供 M14 页面验收使用，复用生产入口、页面、API 和依赖，并附加原 fixture 模块及 V6。只可连接独立验收库，不能作为生产分发物。生产运行仍使用 [首次运行说明](first-run.md) 和 [配置说明](configuration.md)。

构建者在源码根运行：

```sh
mvn -f data-plane/pom.xml -Pacceptance clean verify
```

需要项目既有 Maven、Java 21 和前端构建工具链；首次构建还需要下载 AntRun 3.1.0 及其依赖的网络。构建顺序和原生产测试保持不变，显式构建额外执行三个验收归档合同。

| 内容 | 精确路径 |
|---|---|
| 同次构建的生产包输入 | `data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar` |
| 同次构建的 fixture 输入 | `data-plane/tensor-plugin-fixture/target/tensor-plugin-fixture-1.0-SNAPSHOT.jar` |
| 原始 V6 输入 | `data-plane/tensor-app/src/test/resources/db/migration/V6__create_fixture_tables.sql` |
| 验收包输出 | `data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar` |

验收包文件名带 `-acceptance`，manifest 的 `Tensor-Artifact-Purpose` 为 `acceptance`；不要改名为生产包。嵌套库使用 STORED 存储，因此验收包较大；没有 classpath/layers 索引，也不提供分层镜像提取。产物不作为 Maven 主 artifact 或 classifier 安装/发布。

默认 `mvn -f data-plane/pom.xml clean verify` 只生成原生产包并清除验收目录。Maven `-Pacceptance` 只控制构建，不会激活运行时 Spring profile 或 fixture 开关。

## 2. 创建独立验收库

运行者只需分发文件、Java 21、MySQL 8.4 LTS 和 [首跑工具](first-run.md#1-准备运行环境和文件)：POSIX shell、curl、系统文本工具、MySQL 客户端和浏览器；无需源码、Maven、Node 或 Git。本任务验证使用 MySQL 8.4.6。

按 [管理员连接步骤](first-run.md#2-创建-schema-和应用账号) 交互输入密码并设置 `MYSQL_HISTFILE=/dev/null`。以下 SQL 在管理员会话执行；占位符必须按实际值替换，密码遵循 MySQL 转义规则，不能把已替换 SQL 放入 shell 命令行、历史、日志或 Git：

```sql
SELECT VERSION();
CREATE DATABASE tensor_acceptance
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
CREATE USER '<DB_USER>'@'<APP_HOST>' IDENTIFIED BY '<DB_PASSWORD>';
GRANT CREATE, SELECT, INSERT, UPDATE ON tensor_acceptance.*
  TO '<DB_USER>'@'<APP_HOST>';
SHOW GRANTS FOR '<DB_USER>'@'<APP_HOST>';
SHOW CREATE DATABASE tensor_acceptance;
```

首次使用空 schema，不能复用生产 `tensor`。`<APP_HOST>` 对应 MySQL 实际看到的应用来源，包括容器网关/NAT 后的地址；不要默认使用 `%`。应用只用这个 schema 级账号，不用管理员账号启动。V1～V6 所需的建表及业务权限仍为 CREATE、SELECT、INSERT、UPDATE。

## 3. 分发与环境注入

构建者在源码根新建运行目录并复制同版本文件：

```sh
umask 077
tensor_acceptance_dir=$(mktemp -d "${TMPDIR:-/tmp}/tensor-acceptance.XXXXXXXX") || exit 1
mkdir -p "$tensor_acceptance_dir/docs/runbook" "$tensor_acceptance_dir/scripts"
cp data-plane/tensor-app/target/acceptance/tensor-app-1.0-SNAPSHOT-acceptance.jar \
  "$tensor_acceptance_dir/"
cp docs/runbook/first-run.md docs/runbook/configuration.md docs/runbook/acceptance.md \
  "$tensor_acceptance_dir/docs/runbook/"
cp scripts/smoke-test.sh "$tensor_acceptance_dir/scripts/"
cd "$tensor_acceptance_dir"
```

收到分发物的运行者直接进入该目录。保持一个 JAR、三份说明和原 smoke 的目录布局。后续在此目录打开专用 POSIX shell：

```sh
sh
set +x
export TENSOR_DB_URL=jdbc:mysql://127.0.0.1:3306/tensor_acceptance
unset TENSOR_TUSHARE_TOKEN TENSOR_DEV_CORS_ALLOWED_ORIGIN
tensor_tty_state=$(stty -g) || exit 1
trap 'stty "$tensor_tty_state"; unset TENSOR_DB_PASSWORD TENSOR_TUSHARE_TOKEN' 0
trap 'exit 1' HUP INT TERM
printf '验收数据库账号: '
IFS= read -r TENSOR_DB_USERNAME || exit 1
printf '验收数据库密码（隐藏输入）: '
stty -echo || exit 1
IFS= read -r TENSOR_DB_PASSWORD || exit 1
stty "$tensor_tty_state" || exit 1
printf '\n'
export TENSOR_DB_USERNAME TENSOR_DB_PASSWORD
```

远程数据库或非 3306 端口应替换为实际地址，URL 不含凭证。保留 `TENSOR_TUSHARE_ENABLED` 的默认 `true`；专用 shell 不应带入其他 Spring/Tensor 配置覆盖。隐藏输入、禁跟踪、退出恢复回显和秘密保管规则见 [环境注入](first-run.md#3-注入环境)。不增加 `TENSOR_FIXTURE_*` 环境入口。

## 4. 启用与只读验证

在刚才的 shell 前台执行：

```sh
java -jar tensor-app-1.0-SNAPSHOT-acceptance.jar \
  --spring.profiles.active=acceptance --tensor.plugins.fixture.enabled=true \
  --server.address=127.0.0.1 --server.port=8080
```

必须两个运行条件同时满足才注册 fixture。验收包内 V6 对 Flyway 始终可见，首次启动自动执行 V1～V6，建立 49 张 Tushare 表和一张 `fixture__fixture_daily`，另有 history 表；即使禁用 fixture，V6 仍可能执行。

等待根 `/actuator/health` 达到 HTTP 200、`status=UP`，再从新终端进入同一目录运行：

```sh
sh scripts/smoke-test.sh http://127.0.0.1:8080
```

应输出 `Tensor smoke test passed (4 probes).`，退出 0。脚本只发 GET。浏览器直接访问并刷新 <http://127.0.0.1:8080/downloads> 和 <http://127.0.0.1:8080/datasets>，确认页面实际渲染。在下载页选择 `Fixture`、`Fixture 日线 (fixture_daily)`，场景默认为 `SUCCESS`；可选值恰为 `SUCCESS`、`EMPTY`、`SOURCE_FAILURE`、`TYPE_FAILURE`、`PERSISTENCE_FAILURE`。此阶段不点击下载。

在浏览器开发者工具的 Network 中核对以下只读 GET（也可在浏览器直接打开同源 URL），以实际 HTTP 200 和 JSON 为准：

| 路径 | 应观察到的内容 |
|---|---|
| `/api/v1/data-sources` | 恰有 `fixture`、`tushare_pro`。fixture 的 enabled、credentialConfigured、downloadAvailable 全为 true，unavailableReason 为 null。Tushare enabled 为 true，缺 Token 时 credentialConfigured、downloadAvailable 为 false；记录其完整摘要用于重启对比。 |
| `/api/v1/data-sources/fixture/apis` | 恰有 `fixture_daily`，scenario 参数默认 SUCCESS，五个允许值与上文一致。 |
| `/api/v1/data-sources/fixture/datasets` | 恰有 `fixture_daily`，筛选为 ts_code，固定列 ts_code。 |
| `/api/v1/data-sources/fixture/datasets/fixture_daily` | columns 依次为 ts_code/STRING、trade_date/DATE、amount/DECIMAL(38,18)、note/STRING；仅 note 可空。唯一 filter 为 `{field: "ts_code", operator: "EQ", controlType: "TEXT"}`，fixedColumn 为 ts_code。 |

完整定义的 columns 是四个业务列；物理表另有 `source_plugin`、`source_api`、`ingested_at`。这三个来源列由既有 records 响应的行投影提供，不追加到定义 columns，也不在本任务制造记录来证明查询闭环。缺 Token 的 Tushare 接口列表返回 409/PLUGIN_DISABLED 属于预期，不影响 fixture。

管理员在验收库执行以下只读 SQL，不能手工补建表或修改 history：

```sql
SELECT version, success FROM tensor_acceptance.flyway_schema_history
  ORDER BY installed_rank;
SELECT COUNT(*) AS business_tables FROM information_schema.tables
  WHERE table_schema = 'tensor_acceptance' AND table_name <> 'flyway_schema_history';
SHOW COLUMNS FROM tensor_acceptance.fixture__fixture_daily;
```

应恰有版本 1～6 的六条成功记录、50 张业务表及 fixture 七列。保留脱敏版本、命令、HTTP 状态和计数证据；不要分享完整日志、响应、环境或真实凭证。

## 5. 正常停止与开关重启

按 [正常停止](first-run.md#6-正常停止)，前台 Ctrl-C 或向已核实的本次 PID 发 SIGTERM，等待 JVM 自行退出及数据源清理，再启动下一状态。不要并行启动两个占用同端口的实例。

如果 Ctrl-C 触发 trap，或按首跑说明退出了专用 shell，在每次重启前重新执行第 3 节环境注入，使用同一验收 schema 和账号；不要重建数据库。

同一 JAR、同一验收库，先改为：

```sh
java -jar tensor-app-1.0-SNAPSHOT-acceptance.jar \
  --spring.profiles.active=acceptance --tensor.plugins.fixture.enabled=false \
  --server.address=127.0.0.1 --server.port=8080
```

重做 health、四项 smoke、两页刷新及数据源 GET：fixture 应完全缺席，只有与首次完全一致的 Tushare 摘要。再查 history 和表数，应仍为六项成功迁移、50 张业务表；已建 fixture 表保留，开关不撤销迁移。

正常停止后，另验证缺少 acceptance profile 的状态：

```sh
java -jar tensor-app-1.0-SNAPSHOT-acceptance.jar \
  --spring.profiles.active=production --tensor.plugins.fixture.enabled=true \
  --server.address=127.0.0.1 --server.port=8080
```

重复相同只读检查，仍只有 Tushare，迁移和表数不变，证明 profile 与 enabled 缺一不可。完成后正常停止。

生产隔离检查另用原生产 JAR，在另一个**全新空 schema** 和独立运行目录按 [生产首跑说明](first-run.md) 配置 schema 级账号；本项仅验证激活参数不能改变生产包内容：

```sh
java -jar tensor-app-1.0-SNAPSHOT.jar \
  --spring.profiles.active=acceptance --tensor.plugins.fixture.enabled=true \
  --server.address=127.0.0.1 --server.port=8080
```

应仍只有 Tushare、V1～V5 五条成功记录、49 张业务表，无 fixture 表；health、原 smoke 和页面刷新通过。绝不能把生产包连接已执行 V6 的验收库，也不删表、删 history 或运行 Flyway clean 来“回退”。

## 6. 运行边界

继承 [配置说明](configuration.md#超时和停机) 的 `120s < 130s <= proxy`；70 秒是每个停机阶段的上限，不是 JVM 总退出期限，外部终止窗口要覆盖全部阶段与资源清理。不使用常规 SIGKILL。

smoke 的秘密检查限于规定敏感键/头、JDBC 标记及其环境中提供的非空密码/Token 字面值；新终端不自动继承启动终端的秘密。日志仅在本地按需查看，分享前脱敏；分发和提交均不包含日志、响应、数据库文件、凭证或备份。备份与前向兼容回退原则沿用 [生产说明](first-run.md#7-备份与回退)，目标 schema 必须替换为独立验收 schema。

本包提供 fixture 原有五种输入场景；`PERSISTENCE_FAILURE` 只是合法数据的 note 标记，没有完整数据库故障注入。页面 SUCCESS/EMPTY 下载与查询闭环由 M14-T01 验证，失败矩阵和发布验收由后续任务分别完成，打包与只读启动通过不能代替这些结果。
