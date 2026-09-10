# ISSUE-017：股票下载参数改造验证

日期：2026-09-11。按[已确认方案](../issues/proposals/ISSUE-017-required-stock-parameters.md)完成实现和自动化验证。源码基线为 `5e5f956`，工作分支为 `feature/stock-code-download`。本记录不代表真实 Tushare 下载验收；母 issue 仍保留 `fina_mainbz` 参数纠正及真实接口验收事项。

## 实现结果

- 仅在指定的 26 份生产 YAML 首项增加必填、无默认值的 `ts_code / TS_CODE`，与已有 8 项共计 34 项股票下载。6 项原方式下载及 40 项接口/数据集注册保持可用。
- `TradeDateParameters`、`ExchangeParameters`、`ListStatusParameters` 增加 `tsCode`；新增 `TradeDateOnlyParameters` 服务 `slb_len`。12 个 Codec 保持唯一形状和类型，缺失、null、空白股票值在绑定阶段保留原始含义，不合成未提交字段。
- 页面继续使用现有动态表单；股票字段必填、去除首尾空格并转大写。服务端拒绝缺失或非法股票时不调用上游或持久化；重试保留原请求快照。
- 股票条件进入真实客户端的 HTTP 请求体。股票接口非空响应中任一行股票缺失或不匹配时返回 `SOURCE_PAYLOAD_INVALID`，在适配与持久化前整批拒绝。`new_share`、`repurchase` 的多股票结果及合法空结果保留。
- 新增[40 项当前请求样例](../contracts/download-request-examples.json)，与历史 manifest/响应分离。OpenAPI、数据集定义示例和当前执行器同步更新；历史数据、迁移、验收报告和截图未改写。

## 验证证据

环境为 Java 21.0.11、Maven 3.9.15；完整构建使用项目固定的 Node 24.15.0、npm 11.12.1。Docker 使用本机 Colima，数据库测试由 Testcontainers 创建隔离 MySQL 8.4.6。

| 验证 | 结果与边界 |
| --- | --- |
| `mvn -f data-plane/pom.xml clean verify` | 成功；Surefire 42 个测试类、609 项通过，0 失败/错误/跳过；Failsafe 4 项生产包合同通过；前端 24 文件、170 单测及 Vite 生产构建通过 |
| `DownloadParameterResolverTest` | 77 项通过，含全部 40 项及 fixture 的唯一 Codec/读写往返，以及 34 项股票接口缺失/null/空白与 suppliedFields 保留 |
| `DownloadRequestBindingTest` | 112 项通过，40 项当前请求按 manifest 顺序绑定并规范化；34 项各覆盖 10 类股票输入拒绝；6 项合法旧参数及额外股票字段拒绝；记录上游请求并断言拒绝请求零上游/持久化 |
| `TushareProClientTest` + 元数据合同 | 当前生产元数据独立矩阵一致；HTTP stub 核对 74 份请求体（34×2 只股票＋6 项原方式）；响应归属、非法结构、空结果及非股票多行兼容通过 |
| `StockScopedDownloadTest` | 4 项组合测试通过：全部 34 项匹配首行＋异股次行整批拒绝且零持久化；两只股票分别通过真实客户端、插件、服务和适配器，捕获持久化参数中的目标股票；缺失/null 代码拒绝；两项非股票多行结果正常持久化 |
| `DownloadControllerIT` | 隔离 MySQL 10 项通过，含事务/持久化行为回归 |
| `FlywaySchemaContractIT` | 隔离 MySQL 43 项通过，40 项生产 schema、fixture 及迁移合同一致 |
| `ProductionApplicationContextIT` | 1 项通过；真实生产应用图中绑定新增股票字段，健康检查及元数据回归通过 |
| `stock-download-parameters.spec.js --project=chromium` | 隔离前端开发服务 `127.0.0.1:4173`，3 项通过，逐项遍历 34 个股票表单和 6 个原方式表单，并验证失败后修改可见输入仍按原快照重试；全部 `/api/v1/**` 请求在页面进入前模拟，无真实 Token/后端/数据库 |
| `ui-redesign.spec.js` 定向浏览器回归 | 41 项通过（40 项元数据矩阵＋下载状态/校验/缓存/重试），同样使用 `127.0.0.1:4173` 和模拟 API；未选择写入历史截图的用例，临时服务已停止；Vite 开发日志出现 `ResizeObserver loop completed with undelivered notifications`，未使所选功能用例失败，本轮未改动布局代码 |
| `sh scripts/security/verify-release.sh --self-test` | 318 项通过；仅合成样例/本地浏览器自测，不代表完整安全发布门禁通过 |

完整构建的 609 项包括表中的常规 Java 单测，不能重复累计。三个单独执行的 `*IT` 共 54 项，未包含在该默认 Surefire 数量中。组合测试的持久化入口使用 mock，证明被拒绝批次不会调用持久化，不能当作真实 Tushare 入库证据。

完整构建命令采用以下环境隔离：

```sh
env -u TENSOR_TUSHARE_TOKEN -u TENSOR_DB_URL -u TENSOR_DB_USERNAME -u TENSOR_DB_PASSWORD \
  DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
  TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  mvn -f data-plane/pom.xml clean verify
```

数据库定向验证使用相同环境，执行 `-pl tensor-app -am -Dtest=DownloadControllerIT,FlywaySchemaContractIT,ProductionApplicationContextIT -Dsurefire.failIfNoSpecifiedTests=false -Dskip.npm -Dskip.installnodenpm test`。实际各类的通过结果分别取得；这里给出合并后的复现入口。

TDD 证据：元数据测试先复现 26 项缺少股票字段；YAML 增加字段后绑定测试先出现 16 项形状/类型失败，再补齐 Codec 后通过；响应范围测试先复现 2 项未拒绝结果，再实现校验后通过。全构建发现小数适配测试使用旧交易所单参数，补齐对应合成股票代码后原精度断言通过。

测试环境问题均未算作通过：沙箱限制本地端口、Chromium 和 Mockito agent，获得执行权限后重跑；Testcontainers 首次未发现 Colima socket，显式配置后通过；生产上下文首次受继承的 Tushare Token 覆盖测试默认值影响，去除该命令环境变量后通过。浏览器初次发现旧 mock 的 `**/api/**` 路由吞掉 Vite `/src/api/` 模块，收窄为 `/api/v1/**` 后通过。

## 当前输入和产物身份

| 文件 | SHA-256 |
| --- | --- |
| `docs/contracts/download-request-examples.json` | `f9f147c605262ee1e4f04031dc8508470acd0b4a837927495a9aefecf98bf7af` |
| `data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar` | `475e842ff2a9e380d45404c6112e95288133b21e819b85cdb9aab6b9f23963cc` |

该 JAR 是本次 `clean verify` 的生产包，不含 fixture 插件，不可直接冒充后续浏览器执行器所需的 acceptance 包。重新构建后应重新记录实际哈希。

分页查询执行器的合成造数也改为按股票请求：首次 252 次、公告日修正 123 次，共 375 次，保留原始最终行和查询断言。download-outcomes/dataset-query 的迁移断言同步当前 V1–V7；历史表仍保留，表总数不随接口退役缩减。7 个修改或新增 E2E 文件语法检查通过，6 个套件合计发现 160 个测试；此检查不启动正式生命周期。

当前 metadata/live/dataset-query 浏览器执行器以 `ISSUE_017_ACCEPTANCE_JAR_SHA256` 显式校验新 acceptance JAR；安全执行器要求 `M14_SECURITY_JAR` 和 `M14_SECURITY_JAR_SHA256` 对应同一已核验生产包，不再固定历史包哈希。真实下载输入使用新请求文件，合法 SUCCESS/EMPTY 按新返回结果记录，不使用历史全市场样例的成功数量作为当前预期。

## 复核与交付

独立代码复核通过。复核中发现并修正了旧全市场结果预期、历史 JAR 哈希和迁移 V1–V6 断言；补齐实际插件到服务的混股整批拒绝测试。本次交付保留在 `feature/stock-code-download` 功能分支，母 issue 的未完成事项继续单独跟踪。

## 尚未执行及母 issue 边界

- 未执行真实 Tushare 34 项股票下载及 6 项原方式回归。账户权限、停更接口、适合历史日期和新请求实际返回范围仍需独立记录；没有将历史返回数据改写为新下载证据。
- `fina_mainbz` 仍沿用已有参数，官网未支持的 `ann_date` 纠正由母 issue 单独跟踪，本轮方案明确排除该行为变更。
- 当前应用耦合的 metadata、download-outcomes、dataset-query、live 浏览器验收未正式运行；本轮浏览器通过证据来自独立模拟用例。当前执行器更新经过语法/用例发现检查，不能等同正式下载验收。
- `scripts/verify-contracts.sh` 要求已经提交到 `main` 且生产源码无未提交改动，并从 HEAD 创建快照。本次在当前功能分支保留可审阅改动，未运行该脚本；已直接执行当前工作区的元数据、schema 和生产包合同测试，未将旧 HEAD 快照当作新实现验证。
