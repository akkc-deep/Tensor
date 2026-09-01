# Pause Handoff

## Handoff Type

pause

## Task Link

- **Task board:** `docs/task-handoffs/tensor-v1-task-board.md`
- **Task ID:** `M04-T01`
- **Transition:** `IN_PROGRESS -> BLOCKED`

## Current State

- Complete: 用户已授权并完成独立基线缺陷修复；提交 `13d599f` 只收窄 ArchUnit 项目模块包匹配，定向测试 1/1、完整 reactor 150/150 与独立审查通过，看板已通过提交 `cba481f` 与 `3932ee3` 记录解阻及恢复。
- Complete: 已按设计创建完整临时 Flyway/loader/`information_schema` harness，并在任何数据库连接前只因缺少 V1 文件取得可归因 RED。
- Partial: 已创建设计 Files 节的唯一 V1 SQL；静态自检为 11 个 `CREATE TABLE`、11 个 PRIMARY 和指定的六个二级索引，但该 SQL 仍未提交。
- Complete: SQL 创建后 reactor `test` 与 `verify` 均为 150/150，六层 Enforcer 通过；JAR 恰含一份 V1 资源，`git diff --check` 通过，Maven 产物与临时 harness/classpath 已清理。
- Blocked: 本机无 `mysql:8.4` 镜像，对 Docker Hub 的三次官方获取均在 HTTPS manifest 请求阶段超时，无法启动设计强制的 MySQL 8.4 容器。
- Unverified: Flyway 首次 migrate、validate、零项二次 migrate、`information_schema` 的 11 表/127 列/键/索引/引擎/排序规则对照，以及 `M04-T01_OK:11:93:127:6` GREEN 输出均尚无证据。

## Changed Files

- `data-plane/tensor-app/src/main/resources/db/migration/V1__create_basic_and_organization_tables.sql`: 未提交的唯一生产实现，已写入 UTC 会话、11 张来源表、93 个业务列、统一来源列、一个内部 `business_key`、11 个主键与六个二级索引；待实际 MySQL 8.4 验证。
- `docs/task-handoffs/M04-T01-handoff.md`: 刷新为本次 Docker Hub 环境阻塞快照。
- `docs/task-handoffs/tensor-v1-task-board.md`: 本次将同步记录 `IN_PROGRESS -> BLOCKED` 与解阻条件。

## Verification

- `mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml -pl tensor-app -am test` — SQL 创建前后均退出 0；150/150、0 failure、0 error、0 skipped，六层 Enforcer 通过。
- `java -Dslf4j.internal.verbosity=ERROR --class-path "$m04_t01_cp" /private/tmp/M04T01SchemaCheck.java 'jdbc:mysql://127.0.0.1:1/tensor' root unavailable` — SQL 创建前退出 1，且只因 `migration file missing: data-plane/tensor-app/src/main/resources/db/migration/V1__create_basic_and_organization_tables.sql` 失败。
- `mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml -pl tensor-app -am verify` — 退出 0；150/150、0 failure、0 error、0 skipped，六层 Enforcer 通过。
- `jar tf data-plane/tensor-app/target/tensor-app-1.0-SNAPSHOT.jar | rg '^db/migration/V1__create_basic_and_organization_tables\.sql$'` — 退出 0，恰输出一行。
- `docker run --detach --rm --name tensor-m04-t01-mysql ... mysql:8.4 ...` — 实现代理运行两次，均退出 125；`Head https://registry-1.docker.io/v2/library/mysql/manifests/8.4` 连接超时，容器未创建。
- `docker image inspect mysql:8.4 --format '{{.Id}}' || docker pull mysql:8.4` — 控制器安全重试仍退出 1；本地镜像不存在，对 Docker Hub manifest 的 HTTPS 请求超时。
- `mvn -Dmaven.repo.local=/private/tmp/tensor-m2 -f data-plane/pom.xml -pl tensor-app -am clean` — 退出 0；已清理 reactor `target/`。
- `git diff --check` — 退出 0；最终范围只有未跟踪的唯一 V1 SQL。

## Remaining Work

- 恢复 Docker Hub 对官方 `mysql:8.4` 的可访问性，或使同一官方镜像在本机可用；不得替换数据库或版本。
- 恢复后重新启动隔离 MySQL 8.4 容器，重建临时 harness/classpath，运行 Flyway migrate/validate/二次 migrate 和完整 `information_schema` 对照。
- 如实际 schema 验证暴露 SQL 问题，只修正唯一 V1 SQL 并重跑相同验证；GREEN 后再按固定消息提交唯一 SQL，并进入任务审查。

## Resume Task

- **Task:** `M04-T01` — V1 基础与组织表。
- **Goal:** 创建唯一生产 Flyway V1 迁移，在 MySQL 8.4 中精确建立 M03-T02 的 11 张表、93 个业务列、来源字段、11 个主键和六个二级索引。

## Start Here

1. `docs/task-handoffs/tensor-v1-task-board.md`
2. `docs/task-designs/M04-T01-design.md`
3. `docs/task-handoffs/M04-T01-handoff.md`
4. `.superpowers/sdd/M04-T01-design/task-1-report.md`
5. `data-plane/tensor-app/src/main/resources/db/migration/V1__create_basic_and_organization_tables.sql`
6. `docs/task-designs/M03-T02-design.md` 与其 11 份运行时 YAML。
7. 首个动作：恢复官方 `mysql:8.4` 镜像的本机可用性，并以 `docker image inspect mysql:8.4` 及设计指定的 `docker run` 确认环境解阻。

## Blocker

- **Reason:** 设计强制使用的官方 `mysql:8.4` 镜像在本机不存在，对 Docker Hub manifest 的三次 HTTPS 获取均超时，因而无法启动隔离容器并产生必需的 Flyway/实际 schema 证据。
- **Resolution condition:** `docker image inspect mysql:8.4` 能确认官方镜像在本机可用，且设计指定的 `docker run` 能创建并启动 `tensor-m04-t01-mysql` 容器，作为 `BLOCKED -> READY` 的可观测解阻证据。

## Risks

- 未经实际 MySQL 8.4 验证的 SQL 可能仍包含语法、保留字、索引长度或 `information_schema` 表达问题；静态自检不能替代设计门禁。
- 改用 H2、SQLite、其他 MySQL 版本或跳过 schema 对照会直接违反任务设计。
- 未提交 SQL 必须保留在当前工作树；在解阻前不得把它当作已完成产物。
