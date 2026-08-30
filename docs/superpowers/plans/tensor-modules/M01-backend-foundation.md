# M01 Backend Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `data-plane` 建成 Java 21、Spring Boot 3.5.x 的五模块 Maven 工程，并建立架构与禁用 Git 能力门禁。

**Architecture:** 父 POM 统一依赖和插件版本；各子模块只声明允许的依赖方向。ArchUnit 和 Maven Enforcer 在业务实现出现前锁定模块边界。

**Tech Stack:** Java 21、Maven 3.9.x、Spring Boot 3.5.x、JUnit 5、ArchUnit、Maven Enforcer。

**Spec:** `docs/superpowers/specs/2026-08-25-tensor-module-roadmap-design.md`

## Global Constraints

- 模块依赖为 `app -> core -> plugin-api`，`app -> plugin-tushare -> plugin-api`，fixture 仅测试/验收启用。
- `tensor-core` 不得依赖 `tensor-plugin-tushare`。
- groupId 为 `com.akkc.tensor`，Java 包根为 `com.akkc.tensor`。
- 不得引入 JGit 或常见代码托管 API。

## Project Inputs

候选输入为 M00 契约、父 POM、模块 POM、构建规则和架构测试。后端工程基线不得依赖前端实现或业务模块内部代码。

---

### Task M01-T01: 创建五模块聚合骨架（1.5h）

**Design:** [M01-T01-designs.md](../../../task-designs/M01-T01-designs.md)

**Context boundary:** Read `data-plane/pom.xml`, M00 contracts and TRD 3.3. Do not read frontend files.

**Files:**
- Modify: `data-plane/pom.xml`
- Create: `data-plane/tensor-plugin-api/pom.xml`
- Create: `data-plane/tensor-core/pom.xml`
- Create: `data-plane/tensor-plugin-tushare/pom.xml`
- Create: `data-plane/tensor-plugin-fixture/pom.xml`
- Create: `data-plane/tensor-app/pom.xml`

**Interfaces:** Produces Maven coordinates `com.akkc.tensor:tensor-*:1.0-SNAPSHOT`.

- [ ] Confirm task design; confirm deleting the old entry is deferred to M09 so this task does not leave an untracked application replacement.
- [ ] Write a failing reactor check: `mvn -q -f data-plane/pom.xml help:evaluate -Dexpression=project.modules -DforceStdout`; expect missing modules before the edit.
- [ ] Set parent packaging to `pom`, add all five `<module>` elements, and make every child inherit the parent with matching artifactId.
- [ ] Run `mvn -q -f data-plane/pom.xml validate`; expect exit 0 and five modules in reactor order.
- [ ] Run `git diff --check` when Git exists, then commit with `git add data-plane && git commit -m "build: create backend Maven modules"`.

### Task M01-T02: 锁定运行时与测试依赖（1.5h）

**Design:** [M01-T02-designs.md](../../../task-designs/M01-T02-designs.md)

**Context boundary:** Read only M01 POMs and TRD section 4.

**Files:**
- Modify: `data-plane/pom.xml`
- Modify: all five child `pom.xml` files

**Interfaces:** Produces Java release 21, Boot BOM 3.5.x, Surefire/Failsafe, JUnit 5, AssertJ, Mockito, Testcontainers, WireMock and ArchUnit version properties.

- [ ] Confirm task design; record exact latest available 3.5.x patch in the parent property before implementation.
- [ ] Add `spring-boot-dependencies` BOM import, compiler release 21 and pinned plugin versions.
- [ ] Add only compile dependencies required by each module: plugin-api has no Spring implementation dependency; core uses JDBC/validation; tushare uses web client/Jackson; app uses web, actuator, Flyway and MySQL runtime.
- [ ] Run `mvn -f data-plane/pom.xml help:effective-pom -DskipTests` and confirm no version-resolution error.
- [ ] Run `mvn -f data-plane/pom.xml test`; expect exit 0 with an initially empty suite.
- [ ] Commit `data-plane/**/pom.xml` with message `build: lock backend runtime and test dependencies` when Git exists.

### Task M01-T03: 建立依赖与 Git 能力门禁（2.0h）

**Design:** [M01-T03-designs.md](../../../task-designs/M01-T03-designs.md)

**Context boundary:** Read M01 POMs and TRD 1.4, 3.3, 16.2, 20.1. Do not read frontend.

**Files:**
- Modify: `data-plane/pom.xml`
- Create: `data-plane/tensor-app/src/test/java/com/akkc/tensor/architecture/ModuleDependencyTest.java`
- Create: `data-plane/tensor-app/src/test/java/com/akkc/tensor/architecture/ForbiddenGitCapabilityTest.java`

**Interfaces:** Produces build failures for reversed module dependencies, JGit/hosting dependencies, `ProcessBuilder`/`Runtime.exec` Git calls.

- [ ] Confirm task design; define allowed package dependency edges exactly as the module graph.
- [ ] Write ArchUnit tests importing `com.akkc.tensor..` and assert `core..` never depends on `plugin.tushare..` or `app..`.
- [ ] Write a source scan test over production Java/resources that rejects `org.eclipse.jgit`, GitHub/GitLab/Bitbucket API packages, `ProcessBuilder` with `git`, and `Runtime.getRuntime().exec` with `git`.
- [ ] Add Maven Enforcer banned dependencies for JGit and known GitHub/GitLab/Bitbucket Java clients.
- [ ] Run `mvn -f data-plane/pom.xml test`; expect both architecture tests to pass.
- [ ] Commit parent POM and architecture tests with message `test: enforce backend architecture boundaries` when Git exists.

## Module Gate

Run `mvn -f data-plane/pom.xml verify`. M01 is complete only when all five modules build on Java 21 and architecture/Git capability gates pass.
