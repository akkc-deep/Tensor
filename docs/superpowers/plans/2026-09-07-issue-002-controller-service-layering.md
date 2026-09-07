# ISSUE-002 Controller Service Layering Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. Independent Core and observability changes may be delegated with superpowers:dispatching-parallel-agents.

**Goal:** 按已确认方案把元数据、查询规则和稳定错误语义下沉到 Core，保持 HTTP 契约。

**Architecture:** 复用 `DatasetQueryService`、`DownloadService`，新增具体 `MetadataQueryService` 和共享查询能力策略。Controller 执行 Service → DTO → 成功日志；失败由全局异常处理器映射。

**Tech Stack:** Java 21、Spring MVC/JDBC、Maven、JUnit 5、Mockito、ArchUnit。

**Spec:** `docs/issues/proposals/ISSUE-002-controller-service-layering.md`

## Global Constraints

- 保持现有 URL、HTTP 方法、请求字段、响应 JSON、错误码、成功日志和成功/空结果指标兼容；按已确认方案取消操作级失败指标和失败耗时。
- 保持 `tensor-core` 不依赖 Spring MVC 或 Web DTO。
- 使用具体的用例 Service 即可；没有多实现需求时不增加 Service 接口。
- 不创建覆盖所有用例的大一统 Service、通用反射框架或形式化空转发层。
- 保留 ISSUE-001 已实现的输入模型，不重做绑定。
- 2026-09-07 用户确认：按已确认调用顺序先构造输入值对象和 QueryCriteria；数据集缺失同时 page=0 等输入非法时先返回 PARAM_INVALID，不再优先 DATASET_MISCONFIGURED。
- 用最小代码实现，将创建的文件加入 Git 版本控制，在当前 `issue-002` 分支完成。

## Task 1: Core 用例边界

**Files:** `tensor-core` 下 `metadata/MetadataQueryService.java`、`query/QueryCapabilities.java`、`query/DatasetQueryService.java`、`query/QuerySqlFactory.java`、`download/DownloadService.java` 及对应测试（均位于 `data-plane/`）。

**Interfaces:** 元数据提供 `listDataSources()`、`listApis(PluginId)`、`listDatasets(PluginId)`、`getDataset(DatasetKey)`，返回核心描述符/定义；现有查询、下载签名不变。

- [x] 先写回归测试：重复/禁用/未知插件、缺失数据集、元数据过滤能力、未声明请求过滤、分页及 repository/transaction 异常。
- [x] 运行 Core 测试，确认缺失的业务边界导致失败。
- [x] 提取单一查询能力策略；在 Service 抛出 `DATASET_MISCONFIGURED`、`PARAM_INVALID`、`QUERY_FAILED`；仅持久化调用边界转换数据库/事务异常为 `PERSISTENCE_FAILED`。
- [x] 运行 `mvn -o -f data-plane/pom.xml -pl tensor-core -am -Dtest='*Test,*IT' -Dsurefire.failIfNoSpecifiedTests=false test`，记录结果。

## Task 2: 显式成功日志

**Files:** `data-plane/tensor-app/src/main/java/com/akkc/tensor/observability/OperationLogger.java`、对应 `OperationLoggerTest.java` 和 `ObservabilityTest.java`。

**Interfaces:** `void recordQuerySuccess(RequestId, DatasetKey, QueryCriteria, DatasetPage, Duration)`；`void recordDownloadSuccess(RequestId, DatasetKey, Map<String,Object>, DownloadResult, Duration)`。

- [x] 先修改测试，以核心结果直接调用日志器；覆盖 success/empty、显式耗时、过滤摘要、敏感参数过滤及指标故障隔离。
- [x] 确认原接口不满足测试，再实现两个 void 方法。
- [x] 删除 Supplier、Web DTO、MDC、业务异常捕获和失败记录；保持成功日志字段和指标名，查询空结果仍为 success。
- [x] 运行 `ObservabilityTest`，与 Task 3 合并后执行 HTTP 失败路径验证。

## Task 3: Web 适配与架构约束

**Files:** 三个 Controller、`GlobalExceptionHandler`、`ApplicationConfiguration`、相关 Web/架构/上下文测试。

- [x] 先扩展 ArchUnit：Controller 禁止 Registry/Catalog/Repository/Plugin 依赖和业务异常；OperationLogger 禁止 Web 依赖；Core 禁止 HTTP/Web 依赖。运行测试确认现状违反边界。
- [x] DataSourceController 使用 `MetadataQueryService` 并直接投影；配置注册 Bean。
- [x] DatasetController 保留 HTTP 参数转换、QueryCriteria 构造，删除目录读取、过滤规则和业务异常类。
- [x] 查询与下载按以下顺序调用，构造 DTO 失败时不会记成功：

```java
long started = System.nanoTime();
DatasetPage result = datasetQueryService.query(key, criteria);
Duration duration = Duration.ofNanos(System.nanoTime() - started);
PageResponse response = PageResponse.from(requestId.value().toString(), key, result);
operationLogger.recordQuerySuccess(requestId, key, criteria, result, duration);
return response;
```

- [x] 全局未识别异常统一 `INTERNAL_ERROR`；移除根据方法/路径推断查询和持久化错误码。
- [x] 调整 Controller 构造及契约测试，断言失败无操作指标、成功 JSON 不变、稳定错误码映射。

## Task 4: 完整验证与验收记录

- [x] 执行所有后端 Test/IT，再执行 Maven `verify`（包含打包契约）；不重复无关检查。
- [x] 执行 `git diff --check`，检查变更范围与新增文件 Git 跟踪。
- [x] 复核已确认方案全部验收项，并记录测试数量、环境限制及最终状态到问题文档。

## Verification evidence

2026-09-07 验收通过，独立代码审查无剩余问题。

### 后端全量 Test/IT

```sh
mvn -o -B -ntp -f data-plane/pom.xml \
  -Dmaven.repo.local=/private/tmp/tensor-m2 \
  -DargLine=-javaagent:/private/tmp/tensor-m2/net/bytebuddy/byte-buddy-agent/1.17.8/byte-buddy-agent-1.17.8.jar \
  -Dskip.installnodenpm -Dskip.npm \
  '-Dtest=*Test,*IT,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：638 项全部通过，0 failures、0 errors、0 skipped。

| 模块 | Test/IT 数量 |
| --- | ---: |
| tensor-plugin-api | 79 |
| tensor-core | 110 |
| tensor-plugin-tushare | 95 |
| tensor-plugin-fixture | 12 |
| tensor-app | 342 |

正式复验前已执行 `clean` 并重新编译；修正新增测试中对 DTO 投影失败异常类型的错误预期后，以上命令通过。架构、HTTP 兼容、生产上下文和真实 MySQL 集成测试均包含在本轮。

### 构建和打包验收

```sh
mvn -B -ntp -f data-plane/pom.xml \
  -Dmaven.repo.local=/private/tmp/tensor-m2 \
  -DargLine=-javaagent:/private/tmp/tensor-m2/net/bytebuddy/byte-buddy-agent/1.17.8/byte-buddy-agent-1.17.8.jar \
  -Pacceptance verify
```

结果：`BUILD SUCCESS`；170 项前端测试、前端生产构建、4 项普通 JAR 契约和 3 项验收 JAR 契约全部通过。

命令环境：`JAVA_HOME=/Users/qiangzhiwei/.sdkman/candidates/java/current`、`LANG=en_US.UTF-8`、`DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock`、`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`。子进程仅继承 PATH/HOME/TMPDIR 后设置以上变量，避免实际 TENSOR/SPRING 凭据影响测试。显式 Java agent 用于避免沙箱内 Mockito 动态挂载失败；集成测试使用本地 Colima/MySQL，不使用真实数据源凭据。日志仅保存在 `/private/tmp`。

### 边界与范围

- `git diff --check`、`git diff --cached --check` 均通过；仓库未配置独立 Java formatter，沿用现有格式。
- 新增文件均已加入 Git；改动限于 Core、Web/观测集成、相应测试和 ISSUE-002 文档。
- `QueryCapabilities` 为唯一过滤能力集合；Controller 不访问 Registry/Catalog/Repository/Plugin，也不定义业务异常。
- OperationLogger 不依赖 Web DTO/MDC/Supplier；仅记录成功/空结果。失败保留全局日志；下载绑定失败也不再产生操作事件或指标。
- `TensorMetrics` 公共方法保持兼容；扫描确认生产操作日志器不再调用 FAILURE 分支。
- 输入优先级按用户同意的方案：先构造值对象/QueryCriteria，再执行资源访问校验。缺失数据集且 `page=0`、未知插件且 API 名非法均先返回 `PARAM_INVALID`。
