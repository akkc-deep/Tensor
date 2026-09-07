# ISSUE-012 查询额外参数拒绝验证

日期：2026-09-07。设计与根因见 [ISSUE-012](../issues/problems/ISSUE-012-query-extra-parameters.md)。

## 结论

ISSUE-012 已解决。新冻结生产 JAR 的 S06 **6/6 通过**：`table`、`column`、`columns`、`sort`、`orderBy`、`sql` 均返回 400 / `PARAM_INVALID`。每次拒绝后核对全部 49 张业务表的行数和内容指纹不变，stub 调用数保持前置下载产生的 2 次。随后合法筛选和分页查询返回 200，原合成行完整保留。

本次限定范围共 **20 项通过，0 失败，0 未运行**，脚本退出 0。范围为 S01～S04、S06、S08、合法查询对照及所需扫描、身份与清理检查。未运行安全 Maven 专项门禁、依赖审计、S05、S07；不代表全局安全门禁或发布准入通过。

## 构建身份

- 工作区基线：`221bf6189096f6e66a4abaa0cb4d7d063ed0e716`。独立快照包含本次修复及工作区已有 ISSUE-010、ISSUE-011、POM 等变更。
- 构建源码快照：`e1982b836c7c5ab5066d1f83cff0f31439870655`。
- 最终验证副本提交：`ff2366eded79426f78840ae34bd81cf1417121a8`；相对构建快照只调整临时验证脚本。
- 生产 JAR：`/private/tmp/tensor-issue-012-rz3ojwd0/data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar`。
- JAR SHA-256：`61131bab34adf06501c1480841b971f7cafb619186014d04aff1022289b9bb7d`。
- 验证脚本 SHA-256：`a6638545021a2ea6ae147f14616c94af0d1af614aacef9f0d92c5596ec89f33a`。
- 机器报告：`/private/tmp/tensor-m14-t07.dLEPa1mi/outcome.json`。
- 报告 SHA-256：`c2b3f93dff0d59f149509d7fc9c36f7eb4a4a42161cbdb9c5810fdf3c70a3a5e`。
- 实际运行：`2026-09-07T03:16:19.557055+00:00` 至 `2026-09-07T03:16:44.476725+00:00`。运行前冻结、运行后复核源码、脚本、锁文件和 JAR 身份一致。
- Java 21.0.11、Maven 3.9.15、Node 24.15.0、npm 11.12.1、Python 3.11.5、Playwright 1.62.1、MySQL Server 8.4.6（客户端 8.4.11）。

## 自动回归与构建

- RED：新增 10 组用例修复前均因 `Status expected:<400> but was:<200>` 失败，0 errors/skipped。首次沙箱内尝试无法连接 Docker，未计入有效 RED。
- GREEN：`DatasetControllerIT`、`DatasetQueryServiceIT`、`QuerySqlFactoryTest`、`GlobalExceptionHandlerTest`、`DataSourceControllerTest`、`ProductionWebConfigurationTest` 共 **111 项通过**，0 failures/errors/skipped。其中 Controller 18 项，新增 10 组分别执行单值和重复值请求，断言完整安全错误体、请求 ID、安全头和数据库零访问；覆盖空值、未知字段、大小写及数据库字段名误用。
- 完整 `mvn verify`：**397 项后端单测、4 项生产包合同、170 项前端单测通过**，退出 0。完整构建默认不包含全部 `*IT`；相关 MySQL 集成测试由上述定向命令执行。
- 验证副本语法检查及 **311 项自检通过**。曾修正新增日志统计字段的自检初始化；独立审查指出拒绝请求 ID 与后续合法请求 ID 应共同去重，先用重复 ID 复现漏检，再补充断言及两个自检对照。
- 生产代码独立审查未发现 Critical 或 Important 问题。

构建和回归均使用 `env -i`，仅传工具路径、HOME、JAVA_HOME、LANG 和本机 Docker 连接，不继承 TENSOR/SPRING 凭证配置。完整构建在上述独立快照执行：

```sh
mvn -o -B -ntp -f data-plane/pom.xml -Dmaven.repo.local=/private/tmp/tensor-m2 verify
```

定向命令将 `verify` 替换为：

```sh
-pl tensor-app -am -Dskip.installnodenpm -Dskip.npm \
  -Dtest=DatasetControllerIT,DatasetQueryServiceIT,QuerySqlFactoryTest,GlobalExceptionHandlerTest,DataSourceControllerTest,ProductionWebConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

## S06 逐项结果

路径：`/api/v1/data-sources/tushare_pro/datasets/stock_company/records`。每次仅附加下表参数；全部返回 400 / `PARAM_INVALID`，请求 ID 与响应头一致且各不相同，业务表指纹和 stub 调用数不变。

| 参数 | 固定值 | requestId |
| --- | --- | --- |
| table | other_table | `8e229809-8a54-4e28-9859-6416bd28c76b` |
| column | introduction | `167e737a-1ce5-4d21-a69f-2fea8e2964a6` |
| columns | * | `be91cf90-6f7d-4821-8bbe-effc3a51f78f` |
| sort | ts_code DESC | `f02cb289-4cfb-4156-94c2-fa54fc236274` |
| orderBy | ts_code | `355f601d-da15-4b28-83c6-69271375db3d` |
| sql | SELECT 1 | `9eb173ef-6cda-4123-bc71-8641d1adf4df` |

拒绝后的合法对照请求：`?tsCode=000001.SZ&page=1&pageSize=20`，HTTP 200，requestId 为 `4340d52d-07d6-4f60-9da9-cccde409b902`，精确匹配原合成行且业务表指纹与 stub 调用数不变。

## 前置步骤、安全检查与清理

S02～S04 页面流程完成：合成 stock_company 下载成功 1 行、页面查询与 HTML 文本呈现通过；第二次下载返回安全的上游鉴权错误，独立页面查询仍保留原行。应用只配置回环 stub，预期 2 次调用、意外调用 0 次；未独立抓取 JVM 全部网络流量。

49 个 HTTP 响应安全头检查零失败。619 个已跟踪源码文件、生产及嵌套 JAR、HTTP/页面、数据库、日志、产物和报告的凭证扫描通过。错误体及普通日志未出现提交的 SQL/排序片段或上游内部细节。

正常执行的两次下载与三次查询各有一次完成事件。六次参数拒绝仍在操作日志包装之前发生，其完成事件属于 ISSUE-013，未纳入本次完成事件通过判定；这六次响应仍校验安全头、错误码、requestId 一致与跨请求唯一性、数据及上游状态。

清理通过：JVM 正常 SIGTERM 退出（143），浏览器和 stub 关闭，自有容器及 1 个卷删除，3 个私有凭证输入文件删除，18080 释放。

## 验证副本与复跑

主仓库 `scripts/security/verify-release.sh`、历史包和历史报告保持原样。临时副本只作以下范围调整：固定新 JAR SHA；将 8080 统一替换为 18080；移除 Maven/依赖审计/S05/S07 编排和 REQUIRED 项；保留六项 S06 原判定，增加合法末查；独立核对被拒请求 ID，完成事件仍由 ISSUE-013 处理。未放宽 400/错误码、逐次数据指纹/stub、安全扫描或清理断言。

在上述验证快照执行；自检将最后的脚本命令附加 `--self-test`：

```sh
env -i \
  PATH=/Users/qiangzhiwei/.pyenv/versions/3.11.5/bin:/Users/qiangzhiwei/.sdkman/candidates/java/current/bin:/Users/qiangzhiwei/DevelopSoftware/apache-maven-3.9.15/bin:/Users/qiangzhiwei/.nvm/versions/node/v24.15.0/bin:/usr/local/mysql/bin:/opt/homebrew/bin:/usr/bin:/bin \
  HOME=/Users/qiangzhiwei JAVA_HOME=/Users/qiangzhiwei/.sdkman/candidates/java/current LANG=en_US.UTF-8 \
  DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
  TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  M14_SECURITY_JAR=/private/tmp/tensor-issue-012-rz3ojwd0/data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar \
  M14_MAVEN_REPO=/private/tmp/tensor-m2 sh scripts/security/verify-release.sh
```

首次生产尝试选中系统 Python 3.9，因 `tarfile.extractall` 不支持 `filter` 参数停在源码快照阶段，应用尚未启动；切换 Python 3.11 后通过。随后补齐验收器跨请求 ID 去重并重新冻结脚本，本报告采用最终完整复跑结果。新构建会产生新的二进制身份，需重新登记和验收；长期查询回归已纳入 `DatasetControllerIT`。
