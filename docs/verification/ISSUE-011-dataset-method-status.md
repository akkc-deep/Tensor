# ISSUE-011 数据集方法拒绝验证

日期：2026-09-07。设计与根因见 [ISSUE-011](../issues/problems/ISSUE-011-dataset-method-status.md)。

## 结论

ISSUE-011 已解决。新生产 JAR 的 S05 **12/12 通过**：三个数据集路径的 POST、PUT、PATCH、DELETE 均返回 405、`Allow: GET`、有效且各不相同的 `X-Request-Id`，响应体为 0 字节。每次请求后均核对全部 49 张业务表的行数和内容指纹不变，stub 调用总数保持为前置步骤产生的 2 次。

本次限定 S05 及其生产环境、页面前置步骤与安全检查，共 19 项通过，0 失败，命令退出 0。未运行安全 Maven 专项门禁、前后端依赖审计、S06、S07；不代表全局安全门禁或发布准入通过，也不关闭其他 issue。

## 构建身份（运行前冻结，运行后复核）

- 工作区基线：`221bf61`。独立快照包含本次修复及工作区已有 ISSUE-010、POM 等变更，未覆盖原工作区改动。
- 构建源码快照：`c9332416fd7ff1fa68a4d28fa26d3340691739ab`。
- 验证副本提交：`b037ea27b35a5f9147c5399f9b733ad9fa885dcd`。
- 生产 JAR：`/private/tmp/tensor-issue-011-t5nw03wu/data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar`。
- JAR SHA-256：`4f5797ae5b3b4cb7fc3ba4ea9cee413083d356b848a9b689d3b691743b34d120`。
- 验证脚本 SHA-256：`798d6b74e18381692e4dc9342234570ddf505d3ed4c5095ffbcb74aaae63f4c4`。
- 机器报告：`/private/tmp/tensor-m14-t07.bZTFDySd/outcome.json`；SHA-256：`1e072ab551bbe198b833b7edc76aa2fe6d830035b5a8b89f58acfe83efdad22b`。
- 实际运行：`2026-09-07T02:51:21.402333+00:00` 至 `2026-09-07T02:51:46.248111+00:00`。
- Java 21.0.11、Maven 3.9.15、Node 24.15.0、npm 11.12.1、Playwright 1.62.1、MySQL Server 8.4.6。

## 自动回归与构建

- RED：新增 12 个参数化请求在修复前全部因 `Status expected:<405> but was:<500>` 失败，0 errors/skipped。第一次沙箱内尝试受 Mockito JVM 附加限制，未作为有效 RED；允许附加后得到上述真实失败。
- GREEN：`GlobalExceptionHandlerTest,DataSourceControllerTest,ProductionWebConfigurationTest` 共 77 项通过，0 failures/errors/skipped。实际 Controller、全局 advice 与过滤器一起装配，验证安全响应头、请求 ID、空体、Allow 及业务依赖零访问。
- 完整 `mvn verify`：后端单测 **397** 项、生产 JAR 合同 **4** 项、前端单测 **170** 项全部通过；Maven 退出 0。并非全部 `*IT` 数据库测试，本问题的真实数据库验证由下面的新生产包黑盒运行提供。
- 验证脚本语法检查通过；范围调整时自检曾因反例仍引用未选中的 S06 报错，将该反例改为 S05 后，**309** 项自检全部通过。
- 独立代码审查未发现 Critical 或 Important 问题。

构建与定向回归均使用 `env -i` 清洁环境，仅传工具路径、HOME、JAVA_HOME、LANG 和本机 Docker 连接，不继承 TENSOR/SPRING 凭证配置。完整构建在上述独立快照执行：

```sh
mvn -o -B -ntp -f data-plane/pom.xml -Dmaven.repo.local=/private/tmp/tensor-m2 verify
```

定向命令将 `verify` 替换为：

```sh
-pl tensor-app -am -Dskip.installnodenpm -Dskip.npm \
  -Dtest=GlobalExceptionHandlerTest,DataSourceControllerTest,ProductionWebConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

## S05 逐项结果

请求正文均为 `{}`，`Content-Type: application/json`。路径：

- list：`/api/v1/data-sources/tushare_pro/datasets`
- definition：`/api/v1/data-sources/tushare_pro/datasets/stock_company`
- records：`/api/v1/data-sources/tushare_pro/datasets/stock_company/records`

以下每项均满足 405、Allow、空体、安全头、数据指纹不变、stub 无新增调用。

| 路径 | 方法 | HTTP | requestId |
| --- | --- | --- | --- |
| list | POST | 405 | `5ab2d417-55f9-4326-b618-958c0c58e589` |
| list | PUT | 405 | `f7ae9886-e717-4bd8-99b5-5cbc51af4f77` |
| list | PATCH | 405 | `2aad2d51-c892-47bc-8968-8b488ab151ee` |
| list | DELETE | 405 | `ef51bc18-0c69-4f89-9e70-5d7645c5234e` |
| definition | POST | 405 | `71fe7e82-14b7-4491-bee1-c5fb1ceae782` |
| definition | PUT | 405 | `8312401b-d8bb-44a7-b1a5-b10dba63311d` |
| definition | PATCH | 405 | `75b457de-a0e7-48fa-ad2f-e9f33c52bcad` |
| definition | DELETE | 405 | `3f45de4b-d532-4592-ab85-35be719a90ea` |
| records | POST | 405 | `9d482058-4274-4084-8f91-323c49a0f35c` |
| records | PUT | 405 | `90735bf9-1b54-4f9c-a4b6-1ae1d190a636` |
| records | PATCH | 405 | `136eb347-cc52-43c8-8652-9f34232240b4` |
| records | DELETE | 405 | `e31e5a1b-68db-4ddb-830b-c8da87b077f7` |

## 前置步骤、安全检查与清理

复用原安全脚本的 S02/S03/S04 页面流程：合成 stock_company 下载成功 1 行、页面查询与 HTML 文本呈现通过；第二次下载返回安全的上游鉴权错误，独立页面查询仍保留原行。查询页面无新增、编辑、删除、导出控件。

54 个 HTTP 响应安全头检查零失败。源码、嵌套 JAR、HTTP/页面、数据库、日志、产物及报告的凭证扫描通过。应用只配置回环 stub，观测到预期 2 次调用、0 次意外 stub 调用；未对 JVM 全部网络流量进行独立抓包。

清理通过：本轮 JVM 正常 SIGTERM 退出（143），浏览器和 stub 关闭，自有容器及 1 个卷删除，3 个私有凭证输入文件删除，18080 释放。源码与 JAR 身份复核一致。

## 验证副本与复跑

主仓库 `scripts/security/verify-release.sh`、历史生产包和历史报告保持原样。临时副本从原脚本作以下范围明确的调整，S05 的状态判定、每次数据库指纹/stub 核对、扫描和清理逻辑保持不变：

1. 固定 JAR SHA 替换为本报告身份；全部 8080 引用统一替换为 18080。
2. 报告标为 ISSUE-011 限定范围；从 REQUIRED 及执行编排移除 maven、backend_audit、frontend_audit、S06、S07，`http_matrix` 在 S05 后结束。
3. S05 额外断言并记录 Allow、响应字节数和响应头 requestId；自检“必需门禁失败”反例从 S06 调整为 S05。

在上述验证快照执行（PATH/JAVA_HOME 选择上面记录的工具版本）：

```sh
env -i PATH="/usr/local/mysql/bin:$PATH" HOME="$HOME" JAVA_HOME="$JAVA_HOME" LANG=en_US.UTF-8 \
  DOCKER_HOST=unix:///Users/qiangzhiwei/.colima/default/docker.sock \
  TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  M14_SECURITY_JAR=/private/tmp/tensor-issue-011-t5nw03wu/data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar \
  M14_MAVEN_REPO=/private/tmp/tensor-m2 sh scripts/security/verify-release.sh
```

新构建会产生新的二进制身份，需重新登记并验证。临时快照用于保留本次原始运行产物；长期回归用例已纳入 `GlobalExceptionHandlerTest`。
