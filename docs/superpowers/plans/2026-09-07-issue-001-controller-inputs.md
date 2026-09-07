# ISSUE-001 Controller Inputs Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans for inline execution; the independent GET work may use superpowers:subagent-driven-development. Track the steps below.

**Goal:** 聚合三个 Controller 的请求入口并保持 HTTP 与日志兼容。

**Architecture:** GET 使用专用请求对象和窄范围 MVC resolver。下载使用 Spring Jackson Module、描述解析器、13 种显式参数类型及 Codec；进入原 Service 前转换回兼容 Map。

**Tech Stack:** Java 21、Spring MVC、Jackson、JUnit 5、MockMvc、MySQL Testcontainers。

**Spec:** [正式设计](../../task-designs/ISSUE-001-designs.md)

## Global Constraints

- 用最小代码实现；将创建的文件加入 Git 版本控制。
- 不修改 URL、HTTP 方法、查询参数名称、请求体字段或响应 JSON。
- 不修改服务层、核心层、插件层、持久化层或可观测性方法的参数设计。
- 不升级依赖，不增加新功能；保持成功、错误与日志行为。

## Task 1: GET 请求聚合

文件：`tensor-app/src/main/java/com/akkc/tensor/web/{DataSourceController,DatasetController}.java`、新 GET DTO/resolver/config、对应 Controller 测试（均位于 `data-plane/`）。

- [x] 锁定路径覆盖、重复查询参数、默认值及多错误优先级。
- [x] 实现 `DatasetPath(String pluginId, String apiName)` 与 `DatasetRecordsRequest`，MVC resolver 只读取路径和原始查询值。
- [x] 将原端点改成单请求对象，其内部转换和日志顺序保持不变。
- [x] 运行 `DataSourceControllerTest,DatasetControllerIT`。

## Task 2: 下载请求类型与兼容绑定

文件：`tensor-app/src/main/java/com/akkc/tensor/web/dto/DownloadRequest.java`、`web/download/`、下载 Jackson config、`DownloadController`、`GlobalExceptionHandler`；新增 `DownloadRequestBindingTest`、`DownloadParameterResolverTest`，调整 `DownloadControllerIT`。

- [x] 先在旧实现运行 HTTP 特征测试，固定多错误优先级与日志摘要。
- [x] 新增类型解析测试，确认旧实现无法产生明确参数类型。
- [x] 实现 sealed 参数 records、规范化 shape、双向 Codec 和描述解析器。字段名集合保留显式 null。
- [x] 实现反序列化器与 Spring Module，绑定错误仅解包自有异常，失败解析记录一次完成事件。
- [x] 将 Controller 的已解析请求转换回 Map 后调用原接口，更新直接受签名影响的测试。
- [x] 定向测试：`DownloadRequestBindingTest,DownloadParameterResolverTest,DownloadControllerIT,GlobalExceptionHandlerTest,ParameterValidatorTest`。

## Task 3: 完整验证与关闭

文件：`ProductionApplicationContextIT.java`、issue 索引/问题/方案、验收记录。

- [x] 在开启凭据的生产上下文中读取 `{"pluginId":"tushare_pro","apiName":"daily","params":{"trade_date":"20260905"}}`，断言参数是 `TradeDateParameters`。
- [x] 审查所有修改和错误优先级；修复审查发现的兼容性回退。
- [x] 全后端测试：`mvn -o -B -ntp -f data-plane/pom.xml -Dmaven.repo.local=/private/tmp/tensor-m2 -Dskip.installnodenpm -Dskip.npm '-Dtest=*Test,*IT,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' test`。
- [x] 完整构建：`mvn -B -ntp -f data-plane/pom.xml -Dmaven.repo.local=/private/tmp/tensor-m2 -Pacceptance verify`。
- [x] 记录测试数量、命令和范围检查结果，更新 issue 状态，将新增文件加入 Git。

打包合同依赖 JAR，在 `verify` 阶段执行；不在生成包之前的 `test` 阶段提前执行。

命令环境：`JAVA_HOME=/Users/qiangzhiwei/.sdkman/candidates/java/current`；集成测试设置 `DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock` 和 `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`；子进程环境仅保留 PATH/HOME/TMPDIR，再设置 JAVA_HOME/LANG/Docker 变量，避免继承 TENSOR/SPRING 凭据覆盖测试。测试日志写入 `/private/tmp`，不把含环境信息的临时日志加入仓库。
