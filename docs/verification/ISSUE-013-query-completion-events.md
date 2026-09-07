# ISSUE-013 查询参数失败完成事件验证

日期：2026-09-07。修复设计见 [ISSUE-013](../issues/problems/ISSUE-013-query-completion-events.md)。

## 结论

ISSUE-013 已解决。新冻结生产包的 S01～S08 和日志门禁共同通过：本轮 **15 个下载/查询 requestId 各有且仅有 1 个完成事件**，事件总数也是 15，逐项 outcome/errorCode/failureStage 与 HTTP 结果一致。其中 10 个参数拒绝（S06 六项、S07 四项）全部记录 `failure / parameter / PARAM_INVALID`。

限定范围共 **21 项门禁通过，0 失败，0 未运行**，脚本退出 0。安全 Maven 专项及前后端漏洞审计未纳入本次编排；本结果不代表 ISSUE-014、ISSUE-015 或整体发布准入通过。

## 修复与自动回归

Controller 通过 `MultiValueMap` 读取日期、分页原始首值，保留 tsCode 的原有绑定。分页先安全转换成可空整数，转换失败的拒绝及日期转换、参数名白名单、目录/筛选和 QueryCriteria 校验都在同一个 `OperationLogger.query` 包装器内发生。不能解析的 page/pageSize 输出 `unavailable`；类型错误沿用安全字段错误响应，并仅在查询日志中分类为参数失败。

重复日期/分页取首值；缺省或单个空分页使用默认值，重复分页首值为空仍拒绝；保留日期空格、单值逗号和数字解析行为。固定筛选名称、既有成功事件字段、下载逻辑及未注册 key 的低基数约束保持原合同。

- RED：14 个参数错误用例在修复前全部因“期望 1 条完成事件，实际 0 条”失败；正常查询对照通过，0 errors/skipped。首次沙箱尝试无法访问 Docker，未计入有效 RED。
- 独立审查发现标量字符串绑定会拼接重复参数，且日期 trim 改变原有拒绝行为；新增兼容性测试先复现 5 个失败，再修正为原始首值绑定。
- 最终定向 **156 项测试通过**（7 类，0 failures/errors/skipped）；Controller 45 项，包括本次新增 27 项，覆盖错误体、requestId、数据库零访问、日志脱敏和唯一性、指标计数/耗时各一次、正常查询、绑定兼容性和未知 key 无事件/指标。
- 既有查询服务、SQL 构建、全局错误处理、Web 配置及日志测试共同通过。独立复审未发现剩余 Critical/Important 问题；18 组新旧 Spring 绑定探针结果一致。
- 最终独立快照完整 `mvn verify`：**397 项后端单测、4 项生产包合同、170 项前端单测通过**，退出 0。默认完整构建不包含全部 `*IT`，相关集成回归由定向命令执行。
- 临时验收器 Shell/Python 语法检查与 **327 项自检通过**，包括缺失/重复/额外事件、HTTP 分类不匹配、失败计数错误及原始参数泄漏反例。增加 HTTP 对照后，曾发现 4 个旧自检缺少 probes 初始化，补齐后通过；运行判定未放宽。

## 冻结身份

- 工作区基线：`221bf6189096f6e66a4abaa0cb4d7d063ed0e716`。快照包含本修复及工作区已有 ISSUE-010～012、POM 等变更。
- 最终构建源码快照：`38d242c27b6437150f7eda76ff15be323f6883b2`。
- 验证副本提交：`1a36abdda99f35ba039a76666e59100877a62084`，相对构建快照只修改临时安全验证脚本。
- 生产 JAR：`/private/tmp/tensor-issue-013-6lwl8zz3/data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar`。
- JAR SHA-256：`9b4e393997488b431b53250fe49af9f84bd2eb057e9f8a5455554a22c2111ed8`。
- 验证脚本 SHA-256：`817e0141613d18554efc86637499e98dfb243cf076135a6f8eb67362131291ef`。
- 机器报告：`/private/tmp/tensor-m14-t07.klzFSszX/outcome.json`。
- 报告 SHA-256：`851f48055353a6893b3639ee11c95063dbd6eda562aa0d04ae340149356bf51d`。
- 实测时间：`2026-09-07T04:09:10.599702+00:00` 至 `2026-09-07T04:09:37.446217+00:00`。运行前冻结，运行后复核源码、脚本、锁文件和 JAR 身份一致。
- Java 21.0.11、Maven 3.9.15、Node 24.15.0、npm 11.12.1、Python 3.11.5、Playwright 1.62.1、MySQL Server 8.4.6。

## 原缺失事件逐项结果

路径：`/api/v1/data-sources/tushare_pro/datasets/stock_company/records`。S07 固定输入沿用批准设计，全部返回安全 400；请求 ID 与响应头一致。失败的 resultCount/totalElements 均为 `unavailable`，durationMs 非负。

| 参数 | requestId | HTTP / 错误码 | 日志 page / pageSize | 完成事件数 |
| --- | --- | --- | --- | --- |
| tsCode | `7a254db2-3225-4712-b61f-bf97adab248d` | 400 / PARAM_INVALID | 1 / 50 | 1 |
| page | `46cad048-7a17-420f-ae58-4c1578065ef4` | 400 / PARAM_INVALID | unavailable / 50 | 1 |
| pageSize | `b4c3f9bc-f517-41d2-a29d-1c5db5e6e9a2` | 400 / PARAM_INVALID | 1 / 101 | 1 |
| tradeDateFrom | `0c31dbbb-82f4-4258-bcbc-a368adbe7172` | 400 / PARAM_INVALID | 1 / 50 | 1 |

S06 的 `table`、`column`、`columns`、`sort`、`orderBy`、`sql` 六项也全部返回 400 / PARAM_INVALID，各有一次参数失败完成事件。其余 5 个完成事件对应下载成功 1 次、下载鉴权失败 1 次、查询成功 3 次，未重复记录。

S07 非法 apiName 路径返回 400，保持原合同中非法路径不产生业务事件/指标的边界。合法末查返回 200，requestId 为 `f8b39be1-6093-4cb0-9035-0ba6692bbed0`，精确匹配原合成行，完成事件为成功。

每次 S05～S07 反例后核对全部 49 张业务表行数与内容指纹不变，stub 调用数始终为前置两次下载产生的 2 次。S01 11/11、S05 12/12、S06 6/6、S07 6/6（含非法路径和合法末查）通过。

## 安全扫描与清理

66 个 HTTP 响应安全头检查零失败。621 个跟踪文件、生产及嵌套 JAR、HTTP/页面、49 张业务表、应用日志、产物和最终报告的凭证扫描通过；普通日志未出现完整参数、SQL 片段、上游内部细节或凭证。事件字段集合与固定筛选摘要校验通过。

应用配置回环 stub，观察到预期 2 次调用、意外调用 0 次；未独立抓取 JVM 全部外部网络流量。清理通过：JVM 正常 SIGTERM 退出（143）、浏览器和 stub 关闭、自有容器与 1 个卷删除、3 个私有凭证输入文件删除、18080 释放。

## 复跑命令与范围

构建、回归和验收均使用 `env -i`，只传必要工具路径及本机 Docker 连接，不继承 TENSOR/SPRING 凭证。完整构建在上述独立快照执行：

```sh
mvn -o -B -ntp -f data-plane/pom.xml -Dmaven.repo.local=/private/tmp/tensor-m2 verify
```

定向回归将 `verify` 替换为：

```sh
-pl tensor-app -am -Dskip.installnodenpm -Dskip.npm \
  -Dtest=DatasetControllerIT,DatasetQueryServiceIT,QuerySqlFactoryTest,ObservabilityTest,GlobalExceptionHandlerTest,DataSourceControllerTest,ProductionWebConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

生产验收在 `/private/tmp/tensor-issue-013-6lwl8zz3` 执行；自检为末尾添加 `--self-test`：

```sh
env -i \
  PATH=/Users/qiangzhiwei/.pyenv/versions/3.11.5/bin:/Users/qiangzhiwei/.sdkman/candidates/java/current/bin:/Users/qiangzhiwei/DevelopSoftware/apache-maven-3.9.15/bin:/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin:/usr/local/mysql/bin:/opt/homebrew/bin:/usr/bin:/bin \
  HOME=/Users/qiangzhiwei JAVA_HOME=/Users/qiangzhiwei/.sdkman/candidates/java/current LANG=en_US.UTF-8 \
  DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
  TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  M14_SECURITY_JAR=/private/tmp/tensor-issue-013-6lwl8zz3/data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar \
  M14_MAVEN_REPO=/private/tmp/tensor-m2 sh scripts/security/verify-release.sh
```

主仓库安全验证脚本和历史报告保留原样。临时副本固定新 JAR SHA、统一改为端口 18080、移除 Maven/依赖审计的编排和 REQUIRED 项；保留 S01～S08 原断言。日志检查器仅对 `failure/parameter/PARAM_INVALID` 查询允许固定 `[trade_date]` 摘要，以覆盖 stock_company 拒绝的筛选名，并增加逐 HTTP outcome/code/stage、失败计数和分页值的对照。未放宽事件唯一性、脱敏、业务指纹或清理判定。新构建会产生新的包身份，需要重新冻结和验收。
