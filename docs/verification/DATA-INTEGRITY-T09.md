# DATA-INTEGRITY-T09 验收记录

日期：2026-09-17。工作区：`.worktrees/data-integrity`；分支：`feat/data-integrity`。依据：`docs/task-designs/DATA-INTEGRITY-T09-design.md`。

## 交付与边界

- 六端点已接入既有能力、受理和持久报告服务：能力、创建、历史、详情、结果、问题。控制器未增加规则、SQL、迁移或后台生命周期。
- POST 保留原 JSON 字段是否省略及数组顺序；首次 202、重放 200，Location/checkId 不变，重放回执取实际保存状态。解析异常与 service 异常分别处理。
- 所有端点拒绝未知/重复查询参数；列表默认 1/20，pageSize 允许 1..100 任意整数，越末页返回空列表。严格 UUID、标识符、枚举、日期、分页和历史精确 symbol 筛选在读取前验证。
- 报告读取保留原请求/范围、旧规则和证据；Long 计数、页总数、issueId 与 long 配置为字符串，null 不转换成 0，覆盖比例为六位字符串。既有下载 rowLimit 整数合同保持不变。
- 新增 `INTEGRITY_CHECK_NOT_FOUND` 的安全 404 映射；资料不足的 UNKNOWN 是正常 200。统一 requestId、错误消息不包含原异常或内部字段。
- OpenAPI 引用独立 `integrity-check.schema.json`；15 个示例覆盖首次/重放、能力、进行中、FAIL/UNKNOWN/N/A、不完整结果、空日期问题和空页。

## 测试证据

开始前 service/configuration/下载合同/全局异常专项 77 项通过，0 失败/错误/跳过。TDD 首个合法 POST 在无路由时得到 `expected 202 but was 404`；合同测试在缺少 schema/examples/路由时失败，随后实施。开发中测试工具依赖和 URI 构造问题已修正，未新增测试依赖。

最终专项和真实应用联合命令：

```sh
DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
mvn -o -f data-plane/pom.xml \
  '-Dtest=IntegrityCheckControllerTest,IntegrityCheckContractTest,GlobalExceptionHandlerTest,DownloadTaskContractTest,PluginApiSurfaceTest,ProductionApplicationContextIT' \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：BUILD SUCCESS，113 项，0 失败/错误/跳过，38.153 秒。分组为 Controller 37、IntegrityContract 5、GlobalExceptionHandler 53、DownloadTaskContract 9、PluginApiSurface 8、ProductionApplicationContextIT 1。

HTTP 测试包括：旧 hash 原请求重放且不查当前能力；空/负/小数/Integer 与 Long 溢出分页；非法/缩写 UUID；年份边界和单边日期；255 码点 symbol 通过、256 拒绝，非空历史值不规范化；全部列表筛选；父任务 404 与异任务 resultId 空页；Long.MAX_VALUE、0/null、旧证据和不完整标记。

独立 schema 测试读取生产 DTO 的实际序列化结果和示例；检查字段封闭性、精确计数、比例、null、日期、完整键、枚举与 OpenAPI 路由/参数/Location 一致性。另用 Python Draft 2020-12 验证全部 15 个示例通过。

## 真实 MySQL 与 HTTP

`ProductionApplicationContextIT` 实际启动 MySQL 8.4.6 容器和 production Servlet 应用，未跳过。沿用已有受控行情数据，从 HTTP 获取无 Token 的 40 个能力，验证顺序、三行情 @2、其余 @1、四个 NON_STOCK 元数据及下载不可用；通过 HTTP 提交、重放、冲突、错误参数、按 submissionId 查回，并查询完成详情、结果、问题与日期过滤。

空参考与完整受控日历分别执行 daily/weekly/monthly；有数据场景每接口本地一条、疑似缺口一条，问题日期为 2024-02-28，整体 COVERAGE/overallStatus 保持 UNKNOWN，expectedCount/coverageRate 为 null。HTTP 返回原范围与完整业务键，旧报告不因后续参考数据变化而重算。检查前后六张目标/参考证券表全部行内容一致；测试期间只新增检查报告。受控数据不代表 Tushare 生产全集。

测试已有的数据库健康场景主动停止 MySQL，随后健康检查 DOWN 及关闭时保存失败日志属于该故障场景，测试结果仍为 0 失败/错误；未把日志当作真实业务报告失败。

## 全量回归

```sh
mvn -o -f data-plane/pom.xml \
  '-DargLine=-javaagent:/Users/qiangzhiwei/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' test
```

最终结果：BUILD SUCCESS，40.855 秒；本次生成的 82 份 Surefire 报告合计 **1457 项 Java 测试，0 失败/错误/跳过**。37 个前端测试文件的 **609 项测试全部通过**，Vite 生产构建通过。统计仅使用本次执行后更新的报告，未纳入旧打包阶段测试报告。日志：`/tmp/tensor-t09-full-final.log`。

首次全量执行了1455项Java测试，其中既有 `ModuleDependencyTest` 的Controller→Repository架构门禁失败。最终保留门禁，在现有 `IntegrityCheckService` 增加薄只读查询入口和父任务404；HTTP合同、仓库事务和SQL未变，并修订专属设计。修复专项98项通过，其中架构门禁3项、Service测试17项；随后重新执行上述113项真实HTTP/合同专项和1457项完整回归，均通过。

## 审查与交付状态

独立审查未发现 Critical/Important 实现问题。已补齐其三项测试建议：HTTP 重放不刷新能力、查询词法/长度边界、真实能力顺序/版本/NON_STOCK；定向复审确认均已处理，未引入新的问题。架构修复的最终独立复审亦通过：Service只委托已存报告读取，父任务404、查询顺序、历史独立性及重放语义未变；未发现Critical/Important遗留问题。

`scripts/verify-contracts.sh` 的 clean-main/已提交输入要求不适用于当前混合暂存隔离区；未修改或绕过该门禁，未宣称它已通过，留待 T13 集成验收。当前验收的公开 HTTP 合同由上述独立 schema 与既有下载合同测试证明。

原有 Studio/T01–T08 暂存内容保留；本项文件按精确路径加入 Git，工作区保留供后续任务使用，不提交混合基线或合并主分支。
