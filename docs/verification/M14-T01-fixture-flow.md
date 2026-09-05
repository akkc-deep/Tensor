# M14-T01 fixture 页面主闭环验收证据

- 任务：[权威看板 M14-T01](../task-handoffs/tensor-v1-task-board.md)。
- 设计：[M14-T01-design.md](../task-designs/M14-T01-design.md)。
- 测试：[fixture-flow.spec.js](../../control-plane/e2e/fixture-flow.spec.js)。
- 日期：2026-09-05；最终测试代码的负向对照、正向 3/3、前端 120/120 与静态范围门禁通过。

## 环境与分发物

macOS arm64；Java 21.0.11；Node 24.15.0 / npm 11.12.1；Playwright 1.62.1；标准安装 Chromium 151.0.7922.34（build 1234）；隔离容器中的 MySQL **服务器** 8.4.6。

按原生产者流程构建，使用本机已有 Maven 缓存：

```sh
PATH=/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin:$PATH \
mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml -Pacceptance clean verify
```

退出 0，`BUILD SUCCESS`；前端 120/120、Surefire 368/368、Failsafe 原生产合同 4/4 和验收归档合同 3/3，均无失败、错误或跳过。生产与验收 JAR 从同次构建复制到独立临时分发目录，运行目录只使用分发物和本地日志。

| JAR | SHA-256 |
|---|---|
| `tensor-app-1.0-SNAPSHOT.jar` | `a9dc150a2e411d6479429091ab928f45f4b5159e938a9cec97b72288b062bc08` |
| `tensor-app-1.0-SNAPSHOT-acceptance.jar` | `a69874afa6ce783d4ef4e16a678ddb0ff457f2948b68f509a8e4a2c00440bcac` |

数据库仅供本任务使用，端口仅映射到回环地址。每轮运行创建新的独立空 schema，字符集/排序规则为 `utf8mb4/utf8mb4_0900_as_cs`，应用账号仅有各 schema 的 CREATE、SELECT、INSERT、UPDATE；未使用管理员账号启动应用。随机生成凭证通过私有运行环境注入，未放入 argv、Git、截图或公开日志；不注入真实 Tushare Token。

## 运行方式

依照 [验收说明](../runbook/acceptance.md) 准备独立空库和三个 `TENSOR_DB_*` 环境变量，将非秘密 `ACCEPTANCE_JAR` 设为相应分发 JAR 的绝对路径。使用 Node 24.15.0 后执行：

```sh
cd control-plane
npx playwright test e2e/fixture-flow.spec.js
```

测试拥有本地 8080 上的 Java 子进程，负责启动、根 health 就绪、第三场景同包同库禁用重启和最终 SIGTERM 清理。任何重跑均创建另一空 schema，不 DROP/TRUNCATE、不种业务数据、不删除迁移历史。

## 负向对照

最终代码使用原生产 JAR、空 schema `tensor_production_check_569719e1` 执行同一命令，退出 1：第一项在公开 `getByRole('option', { name: 'Fixture' })` 的可见断言处失败，后两项串行未执行。失败前根 health 为 HTTP 200/UP，两页直接访问与刷新、一级标题均已通过。应用正常 SIGTERM 退出，端口关闭。此对照证明流程依赖真实 Fixture，不是产品缺陷或前置失败。

本地命令记录：`/private/tmp/tensor-m14-t01-negative-final.log`；原生产与验收使用不同 schema，不混用迁移历史。

## 正向验收

最终代码使用验收 JAR、空 schema `tensor_acceptance_864eca62` 执行同一命令，退出 0：**3 passed (15.7s)**，0 failed / skipped / retry。独立建库后的初始表数为 0；第一次真实页面查询 totalElements=0、totalPages=0、items=[]。

| 普通测试 | 本次结果 |
|---|---|
| `downloadsSuccessAndQueriesFixtureFromPages` | 通过，3.2s。页面唯一 SUCCESS POST 请求身份/场景正确；HTTP 200、SUCCESS、响应与页面计数均 1/1/0，requestId 非空且与响应头相等；由导航进入查询页、输入证券代码并查询，page=1/pageSize=50，总记录数/总页数均为 1。 |
| `showsEmptyDownloadWithoutAddingRows` | 通过，2.5s。第二次页面 POST 为 EMPTY，HTTP 200/EMPTY、0/0/0；显示“下载成功，0 条数据”及既定说明。再次页面查询仍是同一行，包含 ingested_at 在内完全不变。 |
| `hidesDisabledFixtureOnBothPagesAfterRestart` | 通过，4.2s。同包同库只把 fixture 开关 true 改为 false；根 health 再次 200/UP，两页直接访问/刷新/标题通过，公开数据源选项只有 Tushare Pro；前后 Tushare 完整摘要相等。 |

实际页面 records 响应唯一行：

```json
{"ts_code":"000001.SZ","trade_date":"2026-08-07","amount":"11.230000000000000000","note":null,"source_plugin":"fixture","source_api":"fixture_daily","ingested_at":"2026-09-05T07:56:00.717Z"}
```

七个表格单元格的完整文本逐项通过断言；null 显示 `--`，入库时间用独立 Node Intl/Asia/Shanghai 算出并核对为 `2026-09-05 15:56:00`。行截图保留实际表格布局，金额列使用既有省略显示；完整精度由 DOM 文本断言和真实响应记录证明。

启用时保存、禁用后逐项深比较的 Tushare 摘要：

```json
{"pluginId":"tushare_pro","displayName":"Tushare Pro","description":"Tushare Pro 证券数据源","enabled":true,"credentialConfigured":false,"downloadAvailable":false,"unavailableReason":"Credentials missing"}
```

禁用后 data-sources 精确为只含以上对象的数组。下载页无 Token 的 409/PLUGIN_DISABLED 配置不可用提示属于既定行为。浏览器监听通过：总共恰两次由按钮点击触发的 fixture POST，其他业务读取由页面发起；无非预期业务 HTTP 失败、pageerror、其他写请求或外部浏览器请求。测试之外仅以 GET 采集 health 和 data-sources 摘要，没有业务 POST、mock 或 SQL 种数。

最终只读数据库复核：版本 1～6 各一条成功迁移、50 张业务表、fixture 恰一行；禁用没有删除 fixture 表或 history。两个最终 JVM 均收到本测试发送的 SIGTERM 并通过 close 事件与端口关闭检查，本地日志另核对 Web graceful shutdown 和 Hikari 资源清理完成。未启动/停止未知进程，未使用 SIGKILL；afterAll 已停机，因此不对已退出实例宣称运行额外 smoke。

本地最终命令记录：`/private/tmp/tensor-m14-t01-positive-final.log`。实现期间一轮在首次业务 POST 前因下拉框内部只读 input 的 value 为空而失败；截图实际显示 SUCCESS，已按设计改为展开并验证可访问的选中 option，没有改变产品或降低默认值断言。修正后 3/3，通过加入本地证据输出后的最终复跑仍为 3/3。

## 回归与范围

最终测试文件完成后，`cd control-plane && npm run test:unit -- --run` 退出 0：20 个文件、120/120 测试通过（4.98s）；`node --check e2e/fixture-flow.spec.js` 退出 0，无输出；Playwright `--list` 恰发现上述三个 Chromium 用例。`git diff --check` 和受保护生产/配置路径无差异检查退出 0。生产实现、全局测试配置、package/lock、Maven、migration 与 runbook 未修改。

实现仅提交本证据文档和 `control-plane/e2e/fixture-flow.spec.js` 两个普通 100644 新文件；看板与交接独立记录。不包含真实 Tushare、幂等/失败注入、分页/宽表、性能、安全或完整 AC 验收。

## 本地产物

五张最终 PNG 已逐张人工查看，无密码、Token、完整环境或原始日志；只含公开 fixture 数据、页面状态及非秘密 requestId。JSON 仅保存实际 fixture 行和公开数据源摘要，权限为 0600。已扫描本任务文档/代码、最终本地产物和本次应用日志，无本次随机数据库凭证字面值。

以下文件在既有忽略产物树中，**只在本机保存，不随 Git 分发，重跑会覆盖并改变哈希**。失败 trace 与原始运行日志也不提交 Git。

| 实际相对路径 | SHA-256 |
|---|---|
| `control-plane/node_modules/.cache/tensor-playwright/fixture-flow-fixture-page--403f0-sAndQueriesFixtureFromPages-chromium/success-counts.png` | `92926458ed51b806abb47ab6009944dbc8824bf884740911f55261023b87df8f` |
| `control-plane/node_modules/.cache/tensor-playwright/fixture-flow-fixture-page--403f0-sAndQueriesFixtureFromPages-chromium/success-row.png` | `c98de6300d2cbf21a6352c76441f9bfe66f5ffa5d5f5a9a7b5797c32ee623f7b` |
| `control-plane/node_modules/.cache/tensor-playwright/fixture-flow-fixture-page--ac687-tureOnBothPagesAfterRestart-chromium/disabled-datasets.png` | `8a6ebf76b224521a7c0e2636ca13ae075172f1210b7d9f525037db30f01433e8` |
| `control-plane/node_modules/.cache/tensor-playwright/fixture-flow-fixture-page--ac687-tureOnBothPagesAfterRestart-chromium/disabled-downloads.png` | `58838918de3ec6092fccc0bb293b38fe226a217122f39f891c176f22d74d5634` |
| `control-plane/node_modules/.cache/tensor-playwright/fixture-flow-fixture-page--ac687-tureOnBothPagesAfterRestart-chromium/flow-evidence.json` | `5d9082000ff0f8b3569a7682f2c60c1e73a827776b2036771bd00fa43e3278be` |
| `control-plane/node_modules/.cache/tensor-playwright/fixture-flow-fixture-page--dab79-tyDownloadWithoutAddingRows-chromium/empty-result.png` | `31d574988e805d480a6348264c3845692d99125b082b30eff7a3b1181cfeca99` |
