# Pause Handoff

## Handoff Type

pause

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Task ID:** `M04-T01`
- **Transition:** `IN_PROGRESS -> BLOCKED`

## Current State

- Complete: 已完整读取当前任务设计与原 `next-task` 交接，核对 M04 任务卡、路线图 spec、TRD 8.3/9.1～9.6、M03-T02 设计和 `tensor-app` 依赖；看板已由提交 `91cafc1` 记录 `READY -> IN_PROGRESS`。
- Complete: 已两次稳定复现修改前架构测试失败并完成根因定位：`ModuleDependencyTest` 的 `..core..` 包模式把 `org.springframework.core` 和 `com.fasterxml.jackson.core` 外部依赖误判为项目 `tensor-core`。
- Blocked: M04-T01 设计明确只允许创建一个 SQL，不允许修改 Java 测试；当前基线无法达到设计要求的 reactor 150/150。
- Partial: None.
- Unverified: 未创建临时 Flyway harness 或 V1 SQL，未执行缺文件 RED、MySQL 8.4 GREEN、`information_schema`、`verify`、JAR 或范围门禁。

## Changed Files

- `docs/task-handoffs/tensor-v1-task-board.md`: 提交 `91cafc1` 记录 M04-T01 启动状态与证据；本次将同步记录阻塞状态。
- `docs/task-handoffs/M04-T01-handoff.md`: 将既有 `next-task` 入口快照刷新为本 `pause` 阻塞快照。
- Production files: None.

## Verification

- `mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml -pl tensor-app -am test` — 退出 1；`tensor-plugin-api` 79/79、`tensor-plugin-tushare` 58/58 通过，`tensor-app` 中 `ModuleDependencyTest.enforces_module_dependency_direction` 1/1 失败，报告 11 个对 Spring/Jackson `core` 包的误判违例。
- `mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml -pl tensor-app -am -Dtest=ModuleDependencyTest -Dsurefire.failIfNoSpecifiedTests=false test` — 退出 1；同一失败稳定复现。
- `mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml -pl tensor-app -am clean` — 退出 0；已清理基线运行产生的 `target/`。

## Remaining Work

- 先在 M04-T01 范围外完成并验证 `ModuleDependencyTest` 误判缺陷的授权修复。
- 解阻后将 M04-T01 恢复为 `READY`，再依次执行设计中的完整临时 harness、可归因缺 V1 RED、唯一 SQL 实现、MySQL 8.4 GREEN、reactor/`verify`、JAR、范围和清理门禁。

## Resume Task

- **Task:** `M04-T01` — V1 基础与组织表。
- **Goal:** 创建唯一生产 Flyway V1 迁移，在 MySQL 8.4 中精确建立 M03-T02 的 11 张表、93 个业务列、来源字段、11 个主键和六个二级索引。

## Start Here

1. `docs/task-handoffs/tensor-v1-task-board.md`
2. `docs/task-designs/M04-T01-design.md`
3. `docs/task-handoffs/M04-T01-handoff.md`
4. `data-plane/tensor-app/src/test/java/com/akkc/tensor/architecture/ModuleDependencyTest.java`
5. `data-plane/tensor-plugin-tushare/src/main/java/com/akkc/tensor/plugin/tushare/metadata/DatasetDefinitionLoader.java`
6. 首个动作：获得并完成范围外架构测试缺陷的授权修复，然后重跑 M04-T01 设计的基线 reactor 命令。

## Blocker

- **Reason:** 修改前 reactor 基线因 `ModuleDependencyTest` 的宽泛 `..core..` 匹配而失败，但修复需修改 M04-T01 设计明确排除的 Java 测试文件。
- **Resolution condition:** 项目所有者授权一个独立缺陷修复范围，该修复已经验证不再将第三方 `core` 包误判为项目模块，且 M04-T01 设计的基线 reactor 命令以 150/150、0 failure、0 error、0 skipped 退出 0。

## Risks

- 在未解阻时继续将使 M04-T01 同时混入 Java 与 SQL，违反权威设计的单语言、单文件范围。
- 即使 V1 SQL 自身正确，当前基线也无法提供 M04-T01 完成所需的 150/150 reactor 证据。
