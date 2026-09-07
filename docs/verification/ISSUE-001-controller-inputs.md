# ISSUE-001 Controller 请求聚合验收

日期：2026-09-07；分支：`issue_001`。

依据：[正式设计](../task-designs/ISSUE-001-designs.md) · [实施计划](../superpowers/plans/2026-09-07-issue-001-controller-inputs.md) · [问题](../issues/problems/ISSUE-001-method-input-aggregation.md)。

## 实施结果

- 数据集详情使用 `DatasetPath`；记录查询使用 `DatasetRecordsRequest`，按路径、证券代码、两个日期范围、分页聚合。仅这两个类型使用专用 MVC resolver，路径不能被 query 覆盖；单 pluginId 入口明确保留原签名及错误行为。
- 下载请求使用 `DatasetKey`、sealed `DownloadParameters` 与已提交字段名集合。49 个 Tushare API 和 fixture 唯一匹配 13 种显式 Codec；类型选择同时比较参数名称、类型与约束，不根据输入字段猜测类型。
- Spring 管理解析器和 Jackson Module；生产 ObjectMapper 实测把 daily 绑定为 `TradeDateParameters`。进入原 Service 前转换回 Map，保留原值、缺失字段和显式 null。
- 保留访问错误优先级、必填优先级、字段错误排序及每请求一次完成事件。异常处理仅解包自有下载绑定异常。日期、枚举、证券代码及关联校验仍由原 `ParameterValidator` 处理。
- 修改范围限 `tensor-app` 的请求边界、直接相关测试和 ISSUE-001 文档；Service、核心、插件、持久化、日志公开接口与生产代码不变，无依赖变更。

## 验证证据

| 检查 | 结果 |
| --- | --- |
| 修改前下载 HTTP 特征测试 | 20 项通过 |
| TDD 明确类型要求 | 旧实现失败：期望 `TradeDateParameters`，实际 `UnmodifiableMap`；实现后通过 |
| 审查修复回归 | 116 项通过：下载 HTTP 23、Codec/结构 52、GET resolver 3、异常处理 38 |
| 真实 MySQL Controller 集成 | 查询 48、下载 10 项通过 |
| 生产 Spring 上下文 | 1 项通过，包含容器 ObjectMapper 类型断言 |
| 最终完整后端单测与集成 | 624 项通过，零失败、错误、跳过；退出 0 |
| Maven 完整构建及生产/验收包合同 | 479 项后端单测、170 项前端单测、生产包 4 项与验收包 3 项合同通过；六模块 BUILD SUCCESS，退出 0 |

独立代码审查发现并修复两处兼容性问题：重复顶层 JSON 不得掩盖较早的结构错误；`tsCode[]` 在精确名称不存在时保留原绑定与失败日志摘要。前者先观察到期望 400、实际 200 的失败测试，再修复至通过；合法的早期重复字段仍保持原有最后值行为。复审无剩余阻断项。

## 复跑方式

Java 21；Maven 本地缓存 `/private/tmp/tensor-m2`。子进程环境仅保留当前 PATH、HOME、TMPDIR，并设置以下测试环境项，不继承 TENSOR/SPRING 配置：

```text
JAVA_HOME=/Users/qiangzhiwei/.sdkman/candidates/java/current
LANG=en_US.UTF-8
DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

完整后端功能验证（包合同在包生成后运行）：

```sh
mvn -o -B -ntp -f data-plane/pom.xml \
  -Dmaven.repo.local=/private/tmp/tensor-m2 \
  -Dskip.installnodenpm -Dskip.npm \
  '-Dtest=*Test,*IT,!PackagedJarContractTest,!AcceptancePackagedJarContractTest' test
```

完整构建与生产/验收包合同（最终执行命令）：

```sh
mvn -B -ntp -f data-plane/pom.xml \
  -Dmaven.repo.local=/private/tmp/tensor-m2 -Pacceptance verify
```

本机临时日志：`/private/tmp/issue-001-baseline.log`、`issue-001-red.log`、`issue-001-review-red.log`、`issue-001-review-green.log`、`issue-001-backend-final.log`、`issue-001-verify.log`。不将临时环境日志加入仓库。

测试环境排障已完成：沙箱内 Mockito 无法附加 JVM，因此测试在获准的沙箱外执行；Testcontainers 需显式 Colima socket；生产上下文的无凭据场景需隔离继承配置。首次全量 `test` 误提前执行包合同，缺少 JAR 的四项失败已调整到 `verify` 阶段；未放宽功能断言。

最终构建首次离线尝试因临时缓存缺少既定版本的 `maven-antrun-plugin:3.1.0` 失败，联网补齐缓存后完成，无版本或依赖文件变更。构建期间未修改的 `TushareRestClientFactoryTest.appliesJdkConnectAndRequestReadTimeouts` 出现一次间歇失败；同一最终代码此前完整后端验证通过，随后原构建命令重跑全部通过，未修改或跳过该测试。

全部新增文件纳入 Git 版本控制；本轮实现保留在 `issue_001` 分支。
