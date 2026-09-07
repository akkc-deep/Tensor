# ISSUE-014 安全 Maven 门禁重跑

日期：2026-09-07。问题见 [ISSUE-014](../issues/problems/ISSUE-014-security-maven-verification.md)，命令与判定标准沿用 [M14-T07 批准设计](../task-designs/M14-T07-design.md)。

## 结论

ISSUE-014 已解决。新的 Git HEAD 快照执行原 Maven 命令，七类 **81 项测试通过**；包含工作区已有 ISSUE-010～013 及 POM 等修改的另一个快照，七类 **118 项测试通过**。两次命令均退出 0，所有指定类的 failures/errors/skipped 均为 0。两轮独立判定，不合并测试数，也不使用历史报告补足。

Enforcer 3.6.3 的 `ban-git-capabilities` 在 `data-plane`、`tensor-plugin-api`、`tensor-core`、`tensor-plugin-tushare`、`tensor-plugin-fixture`、`tensor-app` 六个 reactor 项目实际执行，六次 `BannedDependencies passed`。两个快照各自的前端 24 个测试文件、170 项测试和生产前端构建也通过。

本次只关闭 Maven 专项缺口；未执行整体安全脚本或后端漏洞审计，不代表 ISSUE-015、M14-T07 整体验收或发布准入通过。原 `M14-T07-security.md` 历史失败记录保留。

## 依赖与处理

原故障为 Maven Central 传输 `org.testcontainers:testcontainers:jar:1.21.4` 时正文提前结束。本轮运行前，同一版本的本机缓存已完整可用：

- 路径：`/private/tmp/tensor-m2/org/testcontainers/testcontainers/1.21.4/testcontainers-1.21.4.jar`。
- 大小：17,790,267 字节；SHA-256：`ea81a9ca337bbae7ee83f372b92d55140c7ab976bce73ff1b79f2dd151ca9610`。
- SHA-1 与 Maven 缓存的 `.jar.sha1` 一致，ZIP 全条目 CRC 检查通过，无 `.jar.lastUpdated` 失败标记。

本轮无需修改生产代码、POM、依赖版本或验证脚本；没有手工替换缓存、加入跳过参数或改为离线门禁。保留工作区原有修改、构建目录与历史产物，只新增本记录并更新问题详情和索引。

## 执行与报告核对

| 指定测试类 | Git HEAD 快照 | 当前工作区快照 |
| --- | ---: | ---: |
| ModuleDependencyTest | 1 | 1 |
| ForbiddenGitCapabilityTest | 12 | 12 |
| ObservabilityTest | 18 | 18 |
| ProductionWebConfigurationTest | 28 | 28 |
| QuerySqlFactoryTest | 8 | 8 |
| UpsertSqlFactoryTest | 6 | 6 |
| DatasetControllerIT | 8 | 45 |
| 合计 | 81 | 118 |

两个新快照运行前均无 `target` 或 Surefire 报告。运行后分别核对恰好七份新 XML，文件时间在对应运行区间内；逐类测试数非零，`testcase` 数等于声明数，无 failure/error/skipped 节点。直接提取未修改安全脚本中的 `test_verdict`、`require`、`GateError` 和 `TEST_CLASSES` 执行原判定函数，两轮均通过；另核对六个 Enforcer 执行记录及成功规则输出。

`DatasetControllerIT` 两轮均实际启动 Testcontainers MySQL 8.4.6。沙箱预检无法访问 Docker socket，正式测试通过获准权限执行；未将预检当作测试通过或跳过测试。

环境：Java 21.0.11、Maven 3.9.15、Node 24.15.0、npm 11.12.1。子进程使用清理后的环境，仅保留必要工具和 Docker 连接；Maven 用户配置与 npm 配置/缓存使用本轮私有目录，不继承 TENSOR/SPRING 凭证或 Java/Node 注入参数。

## 输入身份与时间

私有运行目录：`/private/tmp/tensor-issue-014-m1gro5xi`。基线 Git HEAD：`221bf6189096f6e66a4abaa0cb4d7d063ed0e716`。以下时间均为 UTC。

| 项目 | Git HEAD 快照 | 当前工作区快照 |
| --- | --- | --- |
| 来源 | `git archive HEAD`，617 个文件 | 复制 623 个已跟踪文件的工作区内容，包含已暂存与未暂存修改 |
| 子目录 | `head-snapshot` | `snapshot` |
| 开始 | 2026-09-07 04:42:21.906565 | 2026-09-07 04:38:18.884501 |
| 结束 | 2026-09-07 04:42:53.551249 | 2026-09-07 04:39:06.934516 |
| Maven 耗时 | 30.522 秒 | 46.929 秒 |
| 原始日志 | `head-maven-security-tests.log` | `maven-security-tests.log` |
| 核对摘要 | `head-verified-summary.json` | `verified-summary.json` |
| 源码清单 | `head-source-manifest.json` | `source-manifest.json` |

每份源码清单记录逐文件 SHA-256，运行后核对快照输入未变；更新本次文档前也核对工作区的 623 个输入文件未变。核对摘要保留逐 XML 路径、SHA-256、计数和命令退出码，原始日志与报告留在上述私有目录。

- HEAD 源码清单 SHA-256：`0af707a5c522ab1b193291f61ee21f9a94c111c1333983e12e70d32fdff36e8c`。
- 工作区源码清单 SHA-256：`da8362c895ca387e7173ea459310d7965f0d340ca7c854b5125482d8d3e289f0`。
- HEAD 日志 SHA-256：`43293554a7e7e631a9477da12a5ea353a42bf681c27b668d898fe0046c7c01a1`。
- 工作区日志 SHA-256：`125216f8aa5ec9154ee1b80c59d1f01dcbf5ee20e3b2c4f4a63bfdea865602f5`。

## 复跑命令

在新的源码快照根目录、上述 Java/Node 工具链与可用 Docker 环境执行。`M14_MAVEN_REPO` 本轮取 `/private/tmp/tensor-m2`；完整命令及实际工作目录分别保存在 `head-invocation.json` 和 `invocation.json`。

```sh
mvn -B -ntp -f data-plane/pom.xml -pl tensor-app -am \
  -Dmaven.repo.local="$M14_MAVEN_REPO" \
  -Dtest=ModuleDependencyTest,ForbiddenGitCapabilityTest,ObservabilityTest,ProductionWebConfigurationTest,QuerySqlFactoryTest,UpsertSqlFactoryTest,DatasetControllerIT \
  -Dsurefire.failIfNoSpecifiedTests=false test
```
