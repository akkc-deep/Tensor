# Pause Handoff

## Handoff Type

pause

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Task ID:** `M06-T01`
- **Transition:** `IN_PROGRESS -> BLOCKED`

## Current State

- **Partial:** 设计限定的三个 Java 文件已按严格 TDD 创建并暂存；聚焦测试、reactor `test`/`verify`、第一项静态扫描、范围、格式与清理检查已有通过结果。
- **Blocked:** 设计规定的第二项静态扫描在 Java 源文件上匹配裸分号 `;`，因此与“无输出并退出 1”的预期自相矛盾；独立任务审查将其列为唯一 Important 计划/门禁问题并判定在修正前不能满足全部验收。
- **Unverified:** 尚未取得经批准的替代扫描规则，未创建精确三文件实现提交，未通过修正后的全部门禁、最终整体审查或任务完成转换。

## Changed Files

- `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/SqlIdentifierPolicy.java`：新增固定标识符白名单、安全错误和反引号引用。
- `data-plane/tensor-core/src/main/java/com/akkc/tensor/core/persistence/UpsertSqlFactory.java`：新增确定性的 COMPOSITE/FINGERPRINT 参数化 Upsert SQL 生成。
- `data-plane/tensor-core/src/test/java/com/akkc/tensor/core/persistence/UpsertSqlFactoryTest.java`：新增恰 6 项真实行为测试。

## Verification

- `mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml -pl tensor-core -am test`：受限沙箱因既有 Mockito/Byte Buddy attach 产生 10 个环境错误；在授权 JVM 环境重跑基线后 plugin-api 79/79、core 53/53，共 132/132，三层 Enforcer 通过。
- `mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml -pl tensor-core -am -Dtest=UpsertSqlFactoryTest -Dsurefire.failIfNoSpecifiedTests=false test`：只有测试文件时因 `SqlIdentifierPolicy`/`UpsertSqlFactory` 缺失在 `testCompile` 非零；添加最小实现后 6/6、0 failure、0 error、0 skipped。
- `mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml -pl tensor-core -am test` 与同参数 `verify`：授权 JVM 环境均为 plugin-api 79/79、core 59/59，共 138/138，三层 Enforcer 通过。
- 依赖/禁用能力静态扫描：无输出并退出 1。
- 设计原样 SQL 扫描：命中 Java 必需分号并退出 0；未得到设计要求的无输出/退出 1。
- reactor `clean`、非目标模块差异、scoped status、`git diff --check` 与 `git diff --cached --check`：均得到预期结果；暂存区精确为三个 Java 文件。

## Remaining Work

- 由项目所有者批准并在任务设计中记录可执行的 SQL 静态扫描替代规则。
- 使用批准后的规则重新执行静态门禁。
- 在全部门禁成立后，以固定消息提交精确三个 Java 文件，执行最终整体审查并重新核对结果级验收。
- 验收成立后执行 `IN_PROGRESS -> COMPLETED`，再按看板顺序准备后继任务。

## Resume Task

恢复 `M06-T01`——交付“白名单 SQL 标识符和 Upsert 模板”。

## Start Here

1. 完整读取 `docs/task-designs/M06-T01-design.md`。
2. 读取本暂停交接与 `.superpowers/sdd/task-whole-report.md`。
3. 核对暂存区仍精确包含设计规定的三个 Java 文件且工作树没有冲突改动。

首个动作：取得项目所有者对第二项静态扫描精确替代规则的批准，并把该裁决写入任务设计后再执行 `BLOCKED -> READY`。

## Blocker

- **Reason:** 任务设计把裸 `;` 列为对 `UpsertSqlFactory.java` 源码的禁止匹配，同时要求扫描无输出；合法 Java 源码必须含分号，原门禁不可满足。
- **Resolution condition:** 项目所有者批准一个精确、可执行且能验证“生成 SQL 无末尾分号/危险关键字或注释”的替代扫描规则，并把批准规则记录进 `docs/task-designs/M06-T01-design.md`。

## Risks

- 在未修正门禁时提交或完成任务会虚报既定验收。
- 暂存的三文件实现尚无提交保护；恢复前必须先核对暂存区和工作树。
