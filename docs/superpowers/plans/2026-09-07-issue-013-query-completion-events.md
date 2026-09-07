# ISSUE-013 Query Completion Events Implementation Plan

**Goal:** 参数错误查询也产生恰一个安全完成事件，恢复 M14-T07 合同。

**Architecture:** Controller 将文本转换及参数校验放入现有 OperationLogger 包装器；日志只接收固定筛选名及可空整数。保留 HTTP 错误字段及低基数数据集白名单。

**Tech Stack:** Java 21、Spring MVC、JUnit 5、MockMvc、MySQL Testcontainers。

**Spec:** [ISSUE-013 修复设计](../../issues/problems/ISSUE-013-query-completion-events.md)、[M14-T07](../../task-designs/M14-T07-design.md)。

## 约束

- 保留工作区已有 ISSUE-010～012 等修改，直接在 main 工作。
- 不记录完整参数、SQL、上游原文、凭证或 Throwable；事件字段集合不变。
- 未注册数据集不产生新日志/指标标签；元数据与非只读方法不属于查询操作。
- 新建文档纳入 Git；历史安全报告与主验证脚本保持原样。

## 任务一：回归与最小修复

文件：`DatasetController.java`、`OperationLogger.java`、`DatasetControllerIT.java`（均在 `data-plane/tensor-app/src`）。

- [x] 为 Controller 集成测试装配已注册 fixture 数据集的真实 OperationLogger 与 Micrometer。
- [x] 参数化覆盖 S07 四反例、所有日期字段格式错误、整数溢出、额外参数、日期区间反转；断言 400、安全字段错误、数据库零访问、唯一完成事件、失败计数和耗时各一次。
- [x] 先运行新增测试，确认失败来自完成事件缺失。
- [x] 将类型转换、输入白名单、目录及 QueryCriteria 校验纳入查询包装器；保留服务异常转换。
- [x] `OperationLogger.query` 的分页入参改为 `Integer`，null 输出 `unavailable`；类型转换异常映射为参数失败。
- [x] 检查正常查询只记一次、规范分页不变、数据库失败仍记 query 失败、未知 key 不新增指标。
- [x] 运行 `mvn -o -B -ntp -f data-plane/pom.xml -Dmaven.repo.local=/private/tmp/tensor-m2 -pl tensor-app -am -Dskip.installnodenpm -Dskip.npm -Dtest=DatasetControllerIT,DatasetQueryServiceIT,QuerySqlFactoryTest,ObservabilityTest,GlobalExceptionHandlerTest,DataSourceControllerTest,ProductionWebConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`。

## 任务二：生产包验收与问题关闭

- [x] 将当前跟踪源码复制到私有独立快照，记录 Git 身份并执行完整 `mvn verify`。
- [x] 冻结新生产 JAR SHA-256；复用安全脚本副本，以独立回环端口运行 S01～S08 和日志/泄漏/身份/清理门禁。Maven 安全专项与依赖漏洞审计保持 ISSUE-014/015 的独立范围。
- [x] 保留 S06、S07 的逐请求 HTTP、数据指纹和 stub 调用数断言，逐 requestId 核对完成事件数及安全字段。
- [x] 写入 `docs/verification/ISSUE-013-query-completion-events.md` 的命令、身份和实际结果；满足关闭条件后更新问题及索引。
- [x] 检查 diff、审查修复，并把新增文件纳入 Git。

## 完成证据

全部任务完成，详见[验收记录](../../verification/ISSUE-013-query-completion-events.md)。独立审查指出并修复重复参数及日期空格兼容性；最终 156 项定向测试、401 项后端/包合同、170 项前端测试、327 项验收器自检及新包 21 项限定门禁全部通过。
