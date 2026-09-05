# M14-T02 下载结果矩阵验收证据

- 任务：[M14-T02](../task-handoffs/tensor-v1-task-board.md)。
- 设计：[M14-T02-design.md](../task-designs/M14-T02-design.md)。
- 测试：[download-outcomes.spec.js](../../control-plane/e2e/download-outcomes.spec.js)。
- 最终运行：2026-09-05 09:37:49Z～09:40:28Z；Playwright 退出 0，`15 passed (2.7m)`，0 failed / skipped / retry。

## 环境和输入

macOS arm64；Java 21.0.11；MySQL server 8.4.6 / client 8.4.11；Node 24.15.0；npm 11.12.1；Playwright 1.62.1；标准安装 Google Chrome for Testing 151.0.7922.34。

使用原样验收 JAR，SHA-256 为 `a69874afa6ce783d4ef4e16a678ddb0ff457f2948b68f509a8e4a2c00440bcac`。运行者为最终轮准备全新空 schema，名称符合 `tensor_m14_t02_<随机十六进制>`，字符集/排序规则为 `utf8mb4/utf8mb4_0900_as_cs`。应用账号只获该 schema 的 CREATE、SELECT、INSERT、UPDATE；管理员凭证只通过当前用户所有、0600、非符号链接的受限 MySQL defaults 文件供测试 CLI 使用，未注入 JVM。测试先核对 JDBC/CLI host、port、schema 一致，初始表数为 0；启动后核对 V1～V6、50 张业务表及 fixture/daily 均为 0 行。

测试生成一次性假 Tushare Token，只放在 JVM 子进程内存环境，并把上游指向测试拥有的回环 Node server。没有使用真实 Token 或外部网络。JVM、stub、临时触发器和 schema 均只属于本轮。

## 最终命令

在 `control-plane` 执行：

| 命令 | 实际结果 |
|---|---|
| `node --check e2e/download-outcomes.spec.js` | 退出 0，无输出。 |
| `npx playwright test e2e/download-outcomes.spec.js --list` | 退出 0，发现 1 个 Chromium 文件、15 个稳定标题。 |
| `npx playwright test e2e/download-outcomes.spec.js` | 退出 0，15 passed (2.7m)，0 failed / skipped / retry；无 warning。 |
| `npm run test:unit -- --run` | 退出 0，20 files / 120 tests passed，5.05s。 |

## 页面结果矩阵

每项均从原 JAR 页面选择数据源/接口、填写参数并点击；下载失败的页面 alert、响应 code/message/retryable、Header/body requestId 和完成事件一致。客户端两项没有业务 POST 或 stub 调用。

| # | 用例 | 实际页面、响应和数据结果 | 用例耗时 |
|---:|---|---|---:|
| 1 | `blocksMissingRequiredDate` | 交易日期 `aria-invalid=true`、获得焦点，字段 `role=alert` 经 `aria-describedby` 关联“此项为必填项”；按钮仍可用，无下载成功/空/失败结果；0 POST、0 stub。 | 1.3s |
| 2 | `blocksReversedDateRange` | `2026-08-08`～`2026-08-07` 被客户端拒绝；只有开始日期无效、获得焦点并关联“开始日期不得晚于结束日期”；0 POST、0 stub。 | 1.0s |
| 3 | `upsertsDuplicateFixtureSuccess` | 初查 0 行；第一次 HTTP 200/SUCCESS、1/1/0 后通过“数据查看”链接实际导航并查询，第二次 200/SUCCESS、1/0/1。查询始终恰一行，除 `ingested_at` 外完整相同；时间从 `2026-09-05T09:37:58.676Z` 更新为 `2026-09-05T09:38:00.465Z`。 | 4.7s |
| 4 | `keepsRowsOnFixtureEmpty` | HTTP 200/EMPTY、0/0/0，显示“下载成功，0 条数据”和固定说明；fixture 完整行含时间不变。 | 2.4s |
| 5 | `showsFixtureSourceFailure` | HTTP 502、`SOURCE_UNAVAILABLE`、`Source is unavailable`、retryable=true；fixture 完整行不变。 | 2.4s |
| 6 | `rejectsFixtureTypeFailure` | HTTP 422、`ADAPTER_TYPE_INVALID`、`Source data contains an invalid value`、retryable=false；响应/页面不含 `not-a-decimal` 或内部 field/row 文本，fixture 完整行不变。 | 2.4s |
| 7 | `rollsBackFixturePersistenceFailure` | 已核实的 AFTER UPDATE 触发器实际触发；HTTP 500、`PERSISTENCE_FAILED`、`Persistence failed`、retryable=true、failureStage=persistence。触发器存在时页面查询仍为同一完整行，note=null、金额和入库时间不变。 | 3.3s |
| 8 | `downloadsDailyFromLocalUpstream` | 初查 0 行；页面 POST 使用 `trade_date=20260807`，stub 1 次；HTTP 200/SUCCESS、1/1/0。按证券代码查询唯一 daily 行，14 列顺序、来源、精度、UTC instant 与上海显示时间全部通过。 | 2.8s |
| 9 | `showsSourceAuthFailure` | stub HTTP 401；页面收到 HTTP 502、`SOURCE_AUTH_FAILED`、`Source authentication failed`、retryable=false；daily 完整基线不变。 | 1.9s |
| 10 | `showsSourcePermissionFailure` | stub HTTP 403；页面收到 HTTP 502、`SOURCE_PERMISSION_DENIED`、`Source permission denied`、retryable=false；daily 完整基线不变。 | 1.9s |
| 11 | `showsSourceRateLimitFailure` | stub HTTP 429；页面收到 HTTP 502、`SOURCE_RATE_LIMITED`、`Source rate limit exceeded`、retryable=true；daily 完整基线不变。 | 1.8s |
| 12 | `showsSourceUnavailableFailure` | stub HTTP 503；页面收到 HTTP 502、`SOURCE_UNAVAILABLE`、`Source is unavailable`、retryable=true；daily 完整基线不变。 | 1.9s |
| 13 | `showsSourceNetworkFailure` | stub 返回声明长度后断连；浏览器业务 POST 正常收到 HTTP 502、`SOURCE_NETWORK_ERROR`、`Source network request failed`、retryable=true；daily 完整基线不变。 | 1.9s |
| 14 | `showsSourceTimeoutFailure` | stub 收到请求后不响应；控件锁定、按钮原文且 `aria-busy=true`，无进度/取消/历史 UI。产品自身 read timeout 后页面在 130s 前收到 HTTP 504、`SOURCE_TIMEOUT`、`Source request timed out`、retryable=true；实际响应 120081ms，随后控件解锁且该模式 socket 已关闭；daily 完整基线不变。 | 2.0m |
| 15 | `showsSourcePayloadFailure` | stub HTTP 200 但 fields 顺序非法；页面收到 HTTP 502、`SOURCE_PAYLOAD_INVALID`、`Source returned an invalid payload`、retryable=true，没有误显示 EMPTY；daily 完整基线不变。 | 2.0s |

所有下载开始前、结束后及 timeout 挂起时均断言无 progressbar、进度阶段、取消或历史 UI。完整矩阵实际为 14 次页面下载 POST、17 次页面 records 查询、8 次本机 stub 调用；stub 为 1 成功加 7 失败，每种模式恰一次。

## 数据、幂等与回滚

两次 fixture SUCCESS 的完整页面响应行仅 `ingested_at` 改变：

```json
{"ts_code":"000001.SZ","trade_date":"2026-08-07","amount":"11.230000000000000000","note":null,"source_plugin":"fixture","source_api":"fixture_daily","ingested_at":"2026-09-05T09:38:00.465Z"}
```

第二次比第一次晚 1789ms，第二次计数为 source=1、inserted=0、updated=1；业务键仍只有一行。EMPTY、SOURCE_FAILURE、TYPE_FAILURE 与 PERSISTENCE_FAILURE 后均以页面无筛选查询对完整七列深比较，未出现占位或部分行。

第 7 项先确认固定触发器名不存在，再通过受限管理员 CLI stdin 创建只针对 `fixture__fixture_daily`、AFTER UPDATE 且 note=`PERSISTENCE_FAILURE` 的 SIGNAL 触发器；information_schema 核对对象、表、时机、事件和 marker。500/PERSISTENCE_FAILED 后，在触发器仍存在时页面查询与第二次 SUCCESS 基线完全相等，完成事件为 persistence stage。用例 finally 删除触发器，afterAll 再确认缺席；没有删除业务行、表、schema 或 Flyway history。

daily 成功基线的 UTC 入库时间为 `2026-09-05T09:38:13.699Z`。前五价格列均为 `11.230000000000000000`，后四数值列均为 `0.000000000000000000`，来源为 `tushare_pro/daily`。每个来源失败后页面无筛选查询都与该 14 列基线含时间完全相等。

## 完成事件和安全检查

最终日志实际有 31 个不同 requestId 的 `tensor.operation.completed`：14 个 download 和 17 个 query，每个已捕获 requestId 恰一条。成功/空 download 的 outcome、failureStage=none、errorCode=none 和三计数与响应一致；失败 download 的 outcome=failure、阶段/code 与响应一致，三计数均为 unavailable。17 个 query 均为 outcome=success、page=1/pageSize=50，resultCount/totalElements 与各页面响应一致。

| 下载结果 | requestId | 完成事件投影 |
|---|---|---|
| fixture SUCCESS 1 | `c7c53abf-5afe-49a0-b795-c532b109a45b` | success / none / 1,1,0 |
| fixture SUCCESS 2 | `facb54fd-1ba0-499d-88f1-1b721237a8ad` | success / none / 1,0,1 |
| fixture EMPTY | `f34fad90-e9c5-4f74-ad6a-77bd8f92967f` | empty / none / 0,0,0 |
| fixture source | `d3009fef-aba7-4a55-97e4-b61d1fc58457` | failure / source / SOURCE_UNAVAILABLE |
| fixture adapter | `da730dd5-1f87-49d8-ac64-588c849291b5` | failure / adapter / ADAPTER_TYPE_INVALID |
| fixture persistence | `9d67be5d-a1aa-4c2d-a609-8e8b3561578d` | failure / persistence / PERSISTENCE_FAILED |
| daily SUCCESS | `439ec3c3-9ebb-4257-ac34-195480972f32` | success / none / 1,1,0 |
| auth | `91cce0bf-2597-463b-8750-9fb2794904b0` | failure / source / SOURCE_AUTH_FAILED |
| permission | `94252cf3-f0a6-4e5e-92ab-db56e89c976d` | failure / source / SOURCE_PERMISSION_DENIED |
| rate limit | `7b4b1c6d-50c1-4d5b-ab19-410613e20259` | failure / source / SOURCE_RATE_LIMITED |
| unavailable | `5fb68272-2905-454c-ae2a-2ab1d2b43f0b` | failure / source / SOURCE_UNAVAILABLE |
| network | `4e4b66f3-c8d2-462b-833e-10319ba9da03` | failure / source / SOURCE_NETWORK_ERROR |
| timeout | `d79e41c0-54e5-4f97-a9f0-1b2b0574958f` | failure / source / SOURCE_TIMEOUT |
| payload | `0114223b-a0df-4724-b8f8-bbf8d17f2d58` | failure / source / SOURCE_PAYLOAD_INVALID |

测试逐事件解析实际 key/value，精确核对完整字段集合和值，再把解析后的实际字段写入安全投影；不接受 message/cause/stack/throwable。最终私有应用日志不含假 Token、应用数据库密码、工具凭证密码、上游原文哨兵、SQL 故障哨兵、CREATE TRIGGER 或 SIGNAL SQLSTATE。代码、共享证据和四张截图对本轮随机数据库账号/密码扫描无匹配；页面/API/共享 evidence 不含 JDBC URL、连接配置、原始上游响应或 SQL。

Run7 的安全 JSON 位于 `/var/folders/s5/h3vynqy544lc7vwtz0zjy39m0000gn/T/tensor-m14-t02-vgQmbD/evidence.json`，SHA-256 为 `27d152e87ce8d40ec08801f1453d9e430a031ba800b08fae3c63acfb9b4e3cbb`；其同目录 `application.log` 为私有应用日志，`/private/tmp/tensor-m14-t02-env-l22iq6ck/run-7.log` 为私有 runner 日志。这些本机临时路径只用于当前审计，不随 Git 分发。

最终清理投影为 triggerAbsent=true、jvmStopped=true、stubStopped=true；8080 已关闭。timeout socket 在第 14 项结构化响应后单独关闭，其余自有 socket 由最终 stub 清理补偿。停机后的独立数据库只读复核为 V1～V6 六条成功迁移、50 张业务表、fixture 1 行、daily 1 行、任务触发器 0 个。

## 截图

控制器逐张人工查看四张 Run7 PNG：fixture 成功/回滚行内容和 17:38:00 上海显示时间相同；payload 页面为安全失败面板；未发现密码、Token、连接配置、SQL、上游原文或内部错误。daily 行截图按当前横向 viewport 正常剪裁，只把可见部分作为布局证据；14 列完整顺序和精度由同次页面 DOM 与响应断言证明。

| 本地实际路径 | SHA-256 |
|---|---|
| `control-plane/node_modules/.cache/tensor-playwright/download-outcomes-download-e4218-ertsDuplicateFixtureSuccess-chromium/fixture-upsert-row.png` | `3ceefb0ebd60e8ad3547e97b757670d4ba61db2976e69bf8c012405868857e60` |
| `control-plane/node_modules/.cache/tensor-playwright/download-outcomes-download-b3216-ckFixturePersistenceFailure-chromium/fixture-rollback-row.png` | `3ceefb0ebd60e8ad3547e97b757670d4ba61db2976e69bf8c012405868857e60` |
| `control-plane/node_modules/.cache/tensor-playwright/download-outcomes-download-c3181-loadsDailyFromLocalUpstream-chromium/daily-success-row.png` | `b1cc8c834ccbde2d7046f9f7c9491b2682f937acff45bc2067e97ade3183e3ab` |
| `control-plane/node_modules/.cache/tensor-playwright/download-outcomes-download-ff749-x-showsSourcePayloadFailure-chromium/source-payload-error.png` | `ded4b075e918bdf751f3277d2a6d0dba3886b7de2d24a0d086b4e66988f5b4db` |

这些 PNG、safe evidence JSON、应用日志和运行日志只保留在本机，不提交 Git。

## 实施中失败分类

前五轮均使用各自的新空 schema。Run1 在首项选择器失败后，旧 afterAll 的计数断言先失败，显式 `stopStub` 和 stub 计数证据因此没有执行；worker 退出最终释放了 stub，但该轮没有结构化 stub-cleanup 证明。后续轮次均执行并记录自有 JVM/stub/触发器清理。

这些轮次只暴露测试实现问题：下载/查询 option 的实际可访问文本无设计早期假定的括号；字段错误本身按合同使用 `role=alert`；查询 helper 起初把内部 pluginId 当页面 displayName；daily 成功的 `paramSummary` 一度放错测试预期对象；完成事件投影最初来自预期对象而非解析后的实际日志；最后 payload 截图最初放在离开下载页之后。最终选择器依据公开页面统一为精确 `日线行情daily`，显示名与内部 ID 分离，字段 alert 改为正向验证，截图移到导航前，事件改为精确字段解析，并补足 setup/afterAll 的独立 finally 清理和 timeout socket 清理。没有修改或削弱产品状态、错误码、计数、数据不变性、回滚、超时或安全断言；未发现产品缺陷或环境失败。
